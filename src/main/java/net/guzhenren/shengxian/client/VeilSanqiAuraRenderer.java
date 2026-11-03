package net.guzhenren.shengxian.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Method;
import net.guzhenren.shengxian.ShengxianVisualConfig;
import net.guzhenren.shengxian.core.AscensionAuraMesh;
import net.guzhenren.shengxian.core.AscensionVisuals;
import net.guzhenren.shengxian.core.Phase;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Thin Minecraft adapter for the same rich geometry used by the offline GPU preview. */
final class VeilSanqiAuraRenderer {
    static final float[][] QI={array(AscensionAuraMesh.HEAVEN),array(AscensionAuraMesh.EARTH),array(AscensionAuraMesh.HUMAN)};
    static final float[] JADE=array(AscensionAuraMesh.JADE);
    private static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("shengxian","ascension_sanqi_converge");
    private static Method getter;
    private static boolean warned;
    private VeilSanqiAuraRenderer() {}
    private static float[] array(AscensionAuraMesh.Color c) { return new float[]{c.r(),c.g(),c.b()}; }

    static RenderType renderType() {
        try {
            if(getter==null) getter=Class.forName("foundry.veil.api.client.render.rendertype.VeilRenderType")
                    .getMethod("get",ResourceLocation.class,Object[].class);
            Object value=getter.invoke(null,ID,new Object[0]);
            if(value instanceof RenderType type) return type;
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) {
            if(!warned) {warned=true;LoggerFactory.getLogger("shengxian").warn("Veil aura unavailable; using vanilla ribbons",e);}
        }
        return RenderType.entityTranslucent(ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png"));
    }


    static void render(VertexConsumer out,PoseStack.Pose pose,AscensionVisuals.Frame f,
                       float fade,float age,float burst,double ticks,Vec3 camera) {
        int segments=ShengxianVisualConfig.segments();
        if(camera.lengthSqr()>16*16) segments=Math.max(12,segments/2);
        AscensionAuraMesh.draw(f,age,burst,fade,ticks,camera.x,camera.y,camera.z,
                segments,ShengxianVisualConfig.reduced() || ShengxianVisualConfig.lowPerformance(),ShengxianVisualConfig.cinematic() && !ShengxianVisualConfig.lowPerformance(),(x,y,z,u,v,r,g,b,a,nx,ny,nz)->
                        out.addVertex(pose,x,y,z).setColor(r,g,b,a).setUv(u,v)
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                                .setNormal(pose,nx,ny,nz));
    }
}
