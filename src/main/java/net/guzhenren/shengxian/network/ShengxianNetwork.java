package net.guzhenren.shengxian.network;

import net.guzhenren.shengxian.ShengxianMod;
import net.guzhenren.shengxian.core.QiIntent;
import net.guzhenren.shengxian.server.AscensionControlService;
import net.guzhenren.shengxian.server.AscensionFx;
import net.guzhenren.shengxian.server.AscensionLifecycleService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = ShengxianMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ShengxianNetwork {
    private static Consumer<FxPayload> clientFxSink = payload -> {};
    private static Consumer<AscensionVisualPayload> clientVisualSink = payload -> {};
    private static Consumer<AscensionOutcomePayload> clientOutcomeSink = payload -> {};
    public static void setClientOutcomeSink(Consumer<AscensionOutcomePayload> sink) {
        clientOutcomeSink = sink == null ? payload -> {} : sink;
    }
    public static void setClientVisualSink(Consumer<AscensionVisualPayload> sink) {
        clientVisualSink = sink == null ? payload -> {} : sink;
    }
    private static Consumer<AscensionSnapshotPayload> clientStateSink = payload -> {};

    private ShengxianNetwork() {}

    public static void setClientFxSink(Consumer<FxPayload> sink) {
        clientFxSink = sink == null ? payload -> {} : sink;
    }

    public static void setClientStateSink(Consumer<AscensionSnapshotPayload> sink) {
        clientStateSink = sink == null ? payload -> {} : sink;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("6");
        registrar.playToClient(AscensionOutcomePayload.TYPE, AscensionOutcomePayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> clientOutcomeSink.accept(payload)));
        registrar.playToClient(AscensionVisualPayload.TYPE, AscensionVisualPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> clientVisualSink.accept(payload)));
        registrar.playToClient(FxPayload.TYPE, FxPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> clientFxSink.accept(payload)));
        registrar.playToClient(AscensionSnapshotPayload.TYPE, AscensionSnapshotPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> clientStateSink.accept(payload)));
        registrar.playToServer(AscensionSnapshotRequestPayload.TYPE, AscensionSnapshotRequestPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (payload.request() && context.player() instanceof ServerPlayer player) {
                        AscensionLifecycleService.sendSnapshot(player);
                    }
                }));
        registrar.playToServer(InsightPayload.TYPE, InsightPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        AscensionControlService.seekInsight(player, payload.id());
                    }
                }));
        registrar.playToServer(QiIntentPayload.TYPE, QiIntentPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;
                    if (payload.stop()) {
                        AscensionControlService.clear(player.getUUID());
                        AscensionFx.send(player.serverLevel(), player.position(), player.getId(), "qi_stop", 0.0f);
                        return;
                    }
                    try {
                        var result = AscensionControlService.set(player,
                                new QiIntent(payload.heaven(), payload.earth(), payload.human(), payload.seek()));
                        if (!result.accepted()) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result.message()));
                        }
                    } catch (IllegalArgumentException ignored) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                AscensionControlService.IntentResult.INVALID_INTENT.message()));
                    }
                }));
        registrar.playToServer(AscensionActionPayload.TYPE, AscensionActionPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        AscensionLifecycleService.perform(player, payload.action());
                    }
                }));
    }
}
