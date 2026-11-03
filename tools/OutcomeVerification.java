import java.lang.reflect.*;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.guzhenren.shengxian.core.*;
import net.guzhenren.shengxian.network.AscensionSnapshotPayload;

public final class OutcomeVerification {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Class<?> service;
        Class<?> payload;
        try {
            service=Class.forName("net.guzhenren.shengxian.server.AscensionOutcomeService");
            payload=Class.forName("net.guzhenren.shengxian.network.AscensionOutcomePayload");
        } catch (ClassNotFoundException ex) { throw new AssertionError("Authoritative outcome transport is missing",ex); }
        Method grade=service.getMethod("gradeForArea",double.class);
        double[] values={1,999999,1000000,3000000,3000000.1,3999999,4000000,6000000,6000000.1,6999999,7000000,9000000,9000001,0,-1,Double.NaN};
        String[] expected={"SMALL","SMALL","SMALL","SMALL","UNKNOWN","UNKNOWN","MEDIUM","MEDIUM","UNKNOWN","UNKNOWN","UPPER","UPPER","UNKNOWN","UNKNOWN","UNKNOWN","UNKNOWN"};
        for(int i=0;i<values.length;i++) check(grade.invoke(null,values[i]).toString().equals(expected[i]),"Saved area grade "+values[i]);
        UUID owner=UUID.randomUUID(), session=UUID.randomUUID();
        var snapshot=new AscensionSnapshotPayload(true,true,"ready",Phase.IMMORTAL.ordinal(),true,
                21,22,23,4,5,6,7,8,0,"",9,2,true,"本命蛊",false,"",7500000,16,31);
        Class<?> gradeType=Class.forName("net.guzhenren.shengxian.network.AscensionOutcomePayload$Grade");
        @SuppressWarnings({"unchecked","rawtypes"}) Object upper=Enum.valueOf((Class)gradeType,"UPPER");
        @SuppressWarnings({"unchecked","rawtypes"}) Object none=Enum.valueOf((Class)gradeType,"NONE");
        Constructor<?> constructor=payload.getConstructor(UUID.class,UUID.class,ResourceLocation.class,long.class,gradeType,AscensionSnapshotPayload.class);
        Object result=constructor.newInstance(owner,session,ResourceLocation.parse("minecraft:overworld"),123L,upper,snapshot);
        check(payload.getMethod("snapshot").invoke(result).equals(snapshot),"Exact server snapshot preserved");
        @SuppressWarnings("unchecked") StreamCodec<RegistryFriendlyByteBuf,Object> codec=(StreamCodec<RegistryFriendlyByteBuf,Object>)payload.getField("CODEC").get(null);
        var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { codec.encode(bytes,result); check(result.equals(codec.decode(bytes)),"Outcome codec round trip"); } finally { bytes.release(); }
        rejects(constructor,owner,session,ResourceLocation.parse("minecraft:overworld"),-1L,upper,snapshot);
        rejects(constructor,owner,session,ResourceLocation.parse("minecraft:overworld"),123L,none,snapshot);
        var active=new AscensionSnapshotPayload(true,true,"ready",Phase.FORMING.ordinal(),true,
                21,22,23,4,5,6,7,8,0,"",9,2,true,"本命蛊",false,"",0,1,0);
        rejects(constructor,owner,session,ResourceLocation.parse("minecraft:overworld"),123L,none,active);
        var failed=new AscensionSnapshotPayload(true,true,"ready",Phase.FAILED.ordinal(),true,
                21,22,23,4,5,6,7,8,0,"",9,2,true,"本命蛊",true,"HUMAN_OVERLOAD",0,1,0);
        Object failure=constructor.newInstance(owner,session,ResourceLocation.parse("minecraft:overworld"),124L,none,failed);
        check(payload.getMethod("snapshot").invoke(failure).equals(failed),"Failure data preserved without rewards");
        rejects(constructor,owner,session,ResourceLocation.parse("minecraft:overworld"),124L,upper,failed);
        System.out.println("Outcome verification passed: "+checks+" checks");
    }
    private static void rejects(Constructor<?> c,Object... values) throws Exception {
        try { c.newInstance(values); throw new AssertionError("Invalid outcome accepted"); }
        catch (InvocationTargetException ex) { check(ex.getCause() instanceof IllegalArgumentException,"Invalid outcome rejected"); }
    }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message);checks++; }
}