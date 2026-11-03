package net.guzhenren.shengxian.client;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import net.guzhenren.shengxian.ShengxianVisualConfig;
import net.guzhenren.shengxian.core.AscensionVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;

/** One local pipeline; writes live uniforms in Veil's pre-stage callback, including zero when inactive. */
final class VeilAscensionPostRenderer {
    private static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("shengxian","ascension_flow");
    private static AscensionVisuals.Frame frame;
    private static float strength,age,impact;
    private static double time,retryAfter;
    private static Object manager,callback;
    private static boolean registered,active,warned,uniformFailure;
    private VeilAscensionPostRenderer() {}

    static boolean update(AscensionVisuals.Frame next,float alpha,float stageAge,double ticks,float shock) {
        frame=next; strength=alpha; age=stageAge; time=ticks; impact=shock;
        if(alpha<=0) { clear(); return true; }
        if(ticks<retryAfter) return false;
        try {
            if(uniformFailure) { uniformFailure=false; throw new IllegalStateException("Required post uniform unavailable"); }
            register();
            Class<?> system=Class.forName("foundry.veil.api.client.render.VeilRenderSystem");
            Object renderer=call(system,"renderer",null,new Class<?>[0]);
            manager=call(renderer.getClass(),"getPostProcessingManager",renderer,new Class<?>[0]);
            if(call(manager.getClass(),"getPipeline",manager,new Class<?>[]{ResourceLocation.class},ID)==null)
                throw new IllegalStateException("Ascension pipeline resource unavailable");
            if(!Boolean.TRUE.equals(call(manager.getClass(),"isActive",manager,new Class<?>[]{ResourceLocation.class},ID)))
                call(manager.getClass(),"add",manager,new Class<?>[]{ResourceLocation.class},ID);
            active=true;
            return true;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError exception) {
            clear(); retryAfter=ticks+100; warn(exception); return false;
        }
    }

    static void clear() {
        frame=null; strength=0;
        if(active && manager!=null) try {
            call(manager.getClass(),"remove",manager,new Class<?>[]{ResourceLocation.class},ID);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError exception) { warn(exception); }
        active=false;
    }

    private static void register() throws ReflectiveOperationException {
        if(registered) return;
        Class<?> platformType=Class.forName("foundry.veil.platform.VeilEventPlatform");
        Object platform=platformType.getField("INSTANCE").get(null);
        Class<?> event=Class.forName("foundry.veil.api.event.VeilPostProcessingEvent$Pre");
        callback=Proxy.newProxyInstance(event.getClassLoader(),new Class<?>[]{event},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class) return switch(method.getName()) {
                case "toString" -> "shengxian-ascension-flow";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> args!=null && args.length==1 && args[0]==proxy;
                default -> null;
            };
            if(args!=null && args.length>=2 && ID.toString().equals(String.valueOf(args[0]))) {
                try { uniforms(args[1]); }
                catch(ReflectiveOperationException | RuntimeException | LinkageError e) {
                    uniformFailure=true;
                    try { scalar(args[1],"Strength",0); } catch(Exception ignored) {}
                    warn(e);
                }
            }
            return null;
        });
        call(platformType,"preVeilPostProcessing",platform,new Class<?>[]{event},callback);
        registered=true;
    }

    private static void uniforms(Object pipeline) throws ReflectiveOperationException {
        var f=frame;
        scalar(pipeline,"Strength",f==null?0:strength*(ShengxianVisualConfig.reduced()?0.45f:1));
        if(f==null) return;
        scalar(pipeline,"Stage",f.phase().ordinal()); scalar(pipeline,"Progress",f.progress());
        scalar(pipeline,"Age",age/20); scalar(pipeline,"Time",(float)(time/20)*(ShengxianVisualConfig.reduced()?0.3f:1));
        scalar(pipeline,"Instability",f.instability()); scalar(pipeline,"Insight",f.insight()?1:0);
        scalar(pipeline,"Calamity",f.calamityMask()!=0?1:0); scalar(pipeline,"Impact",impact);
        vector(pipeline,"Weights",new float[]{f.heaven(),f.earth(),f.human()});
        vector(pipeline,"HeavenColor",VeilSanqiAuraRenderer.QI[0]);
        vector(pipeline,"EarthColor",VeilSanqiAuraRenderer.QI[1]);
        vector(pipeline,"HumanColor",VeilSanqiAuraRenderer.QI[2]);
        vector(pipeline,"JadeColor",VeilSanqiAuraRenderer.JADE);
        Object resolution=uniform(pipeline,"Resolution");
        call(resolution.getClass(),"setVector",resolution,new Class<?>[]{float.class,float.class},
                (float)Math.max(1,Minecraft.getInstance().getWindow().getWidth()),
                (float)Math.max(1,Minecraft.getInstance().getWindow().getHeight()));
    }

    private static Object uniform(Object pipeline,String name) throws ReflectiveOperationException {
        Object u=call(pipeline.getClass(),"getUniform",pipeline,new Class<?>[]{CharSequence.class},name);
        if(u==null || !Boolean.TRUE.equals(call(u.getClass(),"isValid",u,new Class<?>[0])))
            throw new IllegalStateException("Missing ascension uniform: "+name);
        return u;
    }
    private static void scalar(Object p,String name,float value) throws ReflectiveOperationException {
        Object u=uniform(p,name); call(u.getClass(),"setFloat",u,new Class<?>[]{float.class},value);
    }
    private static void vector(Object p,String name,float[] v) throws ReflectiveOperationException {
        Object u=uniform(p,name);
        call(u.getClass(),"setVector",u,new Class<?>[]{float.class,float.class,float.class},v[0],v[1],v[2]);
    }
    private static Object call(Class<?> type,String name,Object receiver,Class<?>[] types,Object... args)
            throws ReflectiveOperationException { return publicMethod(type,name,types).invoke(receiver,args); }

    private static Method publicMethod(Class<?> type,String name,Class<?>[] types) throws NoSuchMethodException {
        Method found=null;
        try {
            found=type.getMethod(name,types);
            if(Modifier.isPublic(found.getDeclaringClass().getModifiers())) return found;
        } catch(NoSuchMethodException ignored) {}
        for(Class<?> contract:type.getInterfaces()) try { return publicMethod(contract,name,types); }
            catch(NoSuchMethodException ignored) {}
        if(type.getSuperclass()!=null) try { return publicMethod(type.getSuperclass(),name,types); }
            catch(NoSuchMethodException ignored) {}
        if(found!=null) return found;
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    private static void warn(Throwable e) {
        if(!warned) { warned=true; LoggerFactory.getLogger("shengxian").warn("Ascension post unavailable; using edge overlay",e); }
    }
}
