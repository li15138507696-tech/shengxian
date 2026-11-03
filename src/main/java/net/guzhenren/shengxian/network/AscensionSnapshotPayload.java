package net.guzhenren.shengxian.network;

import net.guzhenren.shengxian.ShengxianMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Server-owned, read-only view of the player's entire ordinary ascension session. */
public record AscensionSnapshotPayload(
        boolean exists,
        boolean routeAvailable,
        String readinessMessage,
        int phase,
        boolean broken,
        double heaven,
        double earth,
        double human,
        double humanReserve,
        double ambientHeaven,
        double ambientEarth,
        double maximumRisk,
        int windowTicks,
        int tribulationCount,
        String tribulationSummary,
        int insightEarned,
        int insightSpent,
        boolean vitalGuBound,
        String vitalGuName,
        boolean failurePending,
        String failureSummary,
        double areaMu,
        double timeRatio,
        double essence,
        double apertureUpkeep,
        double backlashThreshold) implements CustomPacketPayload {
    public static final Type<AscensionSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShengxianMod.MOD_ID, "ascension_snapshot"));
    private static final StreamCodec<ByteBuf, String> STRING_512 = ByteBufCodecs.stringUtf8(512);
    private static final StreamCodec<ByteBuf, String> STRING_256 = ByteBufCodecs.stringUtf8(256);
    private static final StreamCodec<ByteBuf, String> STRING_128 = ByteBufCodecs.stringUtf8(128);

    public static final StreamCodec<RegistryFriendlyByteBuf, AscensionSnapshotPayload> CODEC =
            StreamCodec.of((buffer, payload) -> payload.encode(buffer), AscensionSnapshotPayload::decode);

    public AscensionSnapshotPayload {
        Objects.requireNonNull(readinessMessage, "readinessMessage");
        Objects.requireNonNull(tribulationSummary, "tribulationSummary");
        Objects.requireNonNull(vitalGuName, "vitalGuName");
        Objects.requireNonNull(failureSummary, "failureSummary");
        if (phase < -1 || phase > 32 || windowTicks < 0 || tribulationCount < 0
                || tribulationCount > 16 || insightEarned < 0 || insightSpent < 0
                || insightSpent > insightEarned || readinessMessage.length() > 512
                || tribulationSummary.length() > 512 || vitalGuName.length() > 128
                || failureSummary.length() > 256) {
            throw new IllegalArgumentException("Invalid ascension snapshot counters or text");
        }
        requireAmount(heaven);
        requireAmount(earth);
        requireAmount(human);
        requireAmount(humanReserve);
        requireAmount(ambientHeaven);
        requireAmount(ambientEarth);
        requireAmount(maximumRisk);
        requireAmount(areaMu);
        requireAmount(timeRatio);
        requireAmount(essence);
        requireAmount(apertureUpkeep);
        requireAmount(backlashThreshold);
        if (timeRatio <= 0) throw new IllegalArgumentException("Invalid aperture time ratio");
    }

    public static AscensionSnapshotPayload empty() { return empty("等待服务器状态"); }

    public static AscensionSnapshotPayload empty(String readinessMessage) {
        return new AscensionSnapshotPayload(false, false, readinessMessage, -1, false,
                0, 0, 0, 0, 0, 0, 0, 0, 0, "",
                0, 0, false, "", false, "", 0, 1, 0, 0, 0);
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(exists);
        buffer.writeBoolean(routeAvailable);
        STRING_512.encode(buffer, readinessMessage);
        buffer.writeVarInt(phase);
        buffer.writeBoolean(broken);
        buffer.writeDouble(heaven);
        buffer.writeDouble(earth);
        buffer.writeDouble(human);
        buffer.writeDouble(humanReserve);
        buffer.writeDouble(ambientHeaven);
        buffer.writeDouble(ambientEarth);
        buffer.writeDouble(maximumRisk);
        buffer.writeVarInt(windowTicks);
        buffer.writeVarInt(tribulationCount);
        STRING_512.encode(buffer, tribulationSummary);
        buffer.writeVarInt(insightEarned);
        buffer.writeVarInt(insightSpent);
        buffer.writeBoolean(vitalGuBound);
        STRING_128.encode(buffer, vitalGuName);
        buffer.writeBoolean(failurePending);
        STRING_256.encode(buffer, failureSummary);
        buffer.writeDouble(areaMu);
        buffer.writeDouble(timeRatio);
        buffer.writeDouble(essence);
        buffer.writeDouble(apertureUpkeep);
        buffer.writeDouble(backlashThreshold);
    }

    private static AscensionSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AscensionSnapshotPayload(
                buffer.readBoolean(),
                buffer.readBoolean(),
                STRING_512.decode(buffer),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                STRING_512.decode(buffer),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                STRING_128.decode(buffer),
                buffer.readBoolean(),
                STRING_256.decode(buffer),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble());
    }

    private static void requireAmount(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1_000_000_000.0) {
            throw new IllegalArgumentException("Invalid ascension snapshot amount");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}