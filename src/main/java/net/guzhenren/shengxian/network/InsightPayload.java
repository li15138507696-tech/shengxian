package net.guzhenren.shengxian.network;

import net.guzhenren.shengxian.ShengxianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Sends one fixed catalog id. The server rechecks phase, window, budget, and duplicate status. */
public record InsightPayload(String id) implements CustomPacketPayload {
    public static final Type<InsightPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShengxianMod.MOD_ID, "insight_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InsightPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), InsightPayload::id,
            InsightPayload::new);

    public InsightPayload {
        Objects.requireNonNull(id, "id");
        if (id.isBlank() || id.length() > 32 || id.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid insight id");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}