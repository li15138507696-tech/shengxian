package net.guzhenren.shengxian.network;

import net.guzhenren.shengxian.ShengxianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server bounded intent. Absolute qi and outcomes are never accepted. */
public record QiIntentPayload(double heaven, double earth, double human, boolean seek, boolean stop)
        implements CustomPacketPayload {
    public static final Type<QiIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShengxianMod.MOD_ID, "qi_intent"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QiIntentPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, QiIntentPayload::heaven,
            ByteBufCodecs.DOUBLE, QiIntentPayload::earth,
            ByteBufCodecs.DOUBLE, QiIntentPayload::human,
            ByteBufCodecs.BOOL, QiIntentPayload::seek,
            ByteBufCodecs.BOOL, QiIntentPayload::stop,
            QiIntentPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
