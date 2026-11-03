package net.guzhenren.shengxian.network;

import java.util.Objects;
import java.util.UUID;
import net.guzhenren.shengxian.ShengxianMod;
import net.guzhenren.shengxian.core.Phase;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One committed outcome for its owner. The nested snapshot retains its existing wire format. */
public record AscensionOutcomePayload(UUID owner, UUID session, ResourceLocation dimension,
                                      long stateRevision, Grade grade,
                                      AscensionSnapshotPayload snapshot) implements CustomPacketPayload {
    public enum Grade { NONE, SMALL, MEDIUM, UPPER, UNKNOWN }

    public static final Type<AscensionOutcomePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShengxianMod.MOD_ID, "ascension_outcome"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AscensionOutcomePayload> CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeUUID(payload.owner());
                buffer.writeUUID(payload.session());
                buffer.writeResourceLocation(payload.dimension());
                buffer.writeVarLong(payload.stateRevision());
                buffer.writeEnum(payload.grade());
                AscensionSnapshotPayload.CODEC.encode(buffer, payload.snapshot());
            }, buffer -> new AscensionOutcomePayload(buffer.readUUID(), buffer.readUUID(),
                    buffer.readResourceLocation(), buffer.readVarLong(), buffer.readEnum(Grade.class),
                    AscensionSnapshotPayload.CODEC.decode(buffer)));

    public AscensionOutcomePayload {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(snapshot, "snapshot");
        boolean success = snapshot.phase() == Phase.IMMORTAL.ordinal();
        boolean failure = snapshot.phase() == Phase.FAILED.ordinal();
        if (stateRevision < 0 || !snapshot.exists() || (!success && !failure)
                || (success && (snapshot.areaMu() <= 0 || grade == Grade.NONE))
                || (failure && grade != Grade.NONE)) {
            throw new IllegalArgumentException("Invalid committed ascension outcome");
        }
    }

    public boolean successful() { return snapshot.phase() == Phase.IMMORTAL.ordinal(); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
