package net.guzhenren.shengxian.network;

import net.guzhenren.shengxian.ShengxianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client asks for an action; the server revalidates phase, player identity and all requirements. */
public record AscensionActionPayload(int action) implements CustomPacketPayload {
    public static final Type<AscensionActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShengxianMod.MOD_ID, "ascension_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AscensionActionPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, AscensionActionPayload::action,
                    AscensionActionPayload::new);

    public AscensionActionPayload {
        if (action < 0 || action > 5) throw new IllegalArgumentException("Invalid ascension action");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
