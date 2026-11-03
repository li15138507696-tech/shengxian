package net.guzhenren.shengxian.network;

import java.util.UUID;
import net.guzhenren.shengxian.core.AscensionVisuals;
import net.guzhenren.shengxian.core.Phase;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded S2C state; no gameplay mutation or client authority. Identity survives entity-ID reuse. */
public record AscensionVisualPayload(UUID owner, UUID session, int entityId, ResourceLocation dimension,
                                     boolean stop, AscensionVisuals.Frame frame, int phaseAgeTicks) implements CustomPacketPayload {
    public static final Type<AscensionVisualPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shengxian", "ascension_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AscensionVisualPayload> CODEC = new StreamCodec<>() {
        public AscensionVisualPayload decode(RegistryFriendlyByteBuf b) {
            UUID owner = b.readUUID();
            UUID session = b.readUUID();
            int entity = b.readVarInt();
            ResourceLocation dimension = b.readResourceLocation();
            boolean stop = b.readBoolean();
            var frame = new AscensionVisuals.Frame(b.readEnum(Phase.class), b.readVarLong(),
                    b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readBoolean(), b.readVarInt(), b.readBoolean());
            return new AscensionVisualPayload(owner, session, entity, dimension, stop, frame, b.readVarInt());
        }
        public void encode(RegistryFriendlyByteBuf b, AscensionVisualPayload p) {
            b.writeUUID(p.owner()); b.writeUUID(p.session()); b.writeVarInt(p.entityId());
            b.writeResourceLocation(p.dimension()); b.writeBoolean(p.stop());
            var f = p.frame();
            b.writeEnum(f.phase()); b.writeVarLong(f.revision()); b.writeFloat(f.progress());
            b.writeFloat(f.energy()); b.writeFloat(f.heaven()); b.writeFloat(f.earth());
            b.writeFloat(f.human()); b.writeFloat(f.instability()); b.writeBoolean(f.insight());
            b.writeVarInt(f.calamityMask()); b.writeBoolean(f.confirmedFormed()); b.writeVarInt(p.phaseAgeTicks());
        }
    };
    public AscensionVisualPayload(UUID owner, UUID session, int entityId, ResourceLocation dimension,
                                  boolean stop, AscensionVisuals.Frame frame) {
        this(owner,session,entityId,dimension,stop,frame,0);
    }
    public AscensionVisualPayload {
        java.util.Objects.requireNonNull(owner);
        java.util.Objects.requireNonNull(session);
        java.util.Objects.requireNonNull(dimension);
        java.util.Objects.requireNonNull(frame);
        if (phaseAgeTicks < 0 || phaseAgeTicks > 72000) throw new IllegalArgumentException("Invalid phase age");
        if (entityId < 0) throw new IllegalArgumentException("Invalid visual entity");
    }
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
