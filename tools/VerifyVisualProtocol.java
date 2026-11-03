import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.guzhenren.shengxian.core.AscensionVisuals;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.network.AscensionVisualPayload;

/** Standalone packet roundtrip; does not start any game, server, world or connection. */
public final class VerifyVisualProtocol {
    public static void main(String[] args) {
        UUID owner=UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID session=UUID.fromString("00000000-0000-0000-0000-000000000002");
        var dimension=ResourceLocation.fromNamespaceAndPath("minecraft","overworld");
        for(Phase phase:Phase.values()) {
            var f=new AscensionVisuals.Frame(phase,10,.6f,.8f,1,.75f,.9f,.3f,true,5,phase==Phase.IMMORTAL);
            var packet=new AscensionVisualPayload(owner,session,42,dimension,false,f,180);
            var raw=Unpooled.buffer();
            try {
                var buffer=new RegistryFriendlyByteBuf(raw,RegistryAccess.EMPTY);
                AscensionVisualPayload.CODEC.encode(buffer,packet);
                var decoded=AscensionVisualPayload.CODEC.decode(buffer);
                if(!packet.equals(decoded)||raw.isReadable())throw new IllegalStateException("Packet mismatch "+phase);
            } finally {raw.release();}
        }
        boolean badId=false,badAge=false;
        var f=new AscensionVisuals.Frame(Phase.ABSORBING,3,.3f,.8f,1,1,1,0,false);
        try {new AscensionVisualPayload(owner,session,-1,dimension,false,f,0);}catch(IllegalArgumentException e){badId=true;}
        try {new AscensionVisualPayload(owner,session,1,dimension,false,f,72001);}catch(IllegalArgumentException e){badAge=true;}
        if(!badId||!badAge)throw new IllegalStateException("Protocol bounds not enforced");
        System.out.println("PASS visual packet roundtrip for 8 phases, new metadata, phase-age and constructor bounds");
    }
}
