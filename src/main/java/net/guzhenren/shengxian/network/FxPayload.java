package net.guzhenren.shengxian.network;

import net.guzhenren.shengxian.ShengxianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client visual cue. The server owns the trigger; the client only renders. */
public record FxPayload(String effect, int entityId, double x, double y, double z, float intensity)
        implements CustomPacketPayload {
    public static final Type<FxPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShengxianMod.MOD_ID, "fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FxPayload::effect,
            ByteBufCodecs.INT, FxPayload::entityId,
            ByteBufCodecs.DOUBLE, FxPayload::x,
            ByteBufCodecs.DOUBLE, FxPayload::y,
            ByteBufCodecs.DOUBLE, FxPayload::z,
            ByteBufCodecs.FLOAT, FxPayload::intensity,
            FxPayload::new);

    /** Compatibility constructor for location-only cues such as aperture transitions. */
    public FxPayload(String effect, double x, double y, double z, float intensity) {
        this(effect, -1, x, y, z, intensity);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
