package net.guzhenren.shengxian.network;

import net.guzhenren.shengxian.ShengxianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client requests its own snapshot; no player id or state is accepted from the client. */
public record AscensionSnapshotRequestPayload(boolean request) implements CustomPacketPayload {
    public static final Type<AscensionSnapshotRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShengxianMod.MOD_ID, "ascension_snapshot_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AscensionSnapshotRequestPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, AscensionSnapshotRequestPayload::request,
                    AscensionSnapshotRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
