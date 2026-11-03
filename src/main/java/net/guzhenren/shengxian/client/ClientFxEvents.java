package net.guzhenren.shengxian.client;

import net.guzhenren.shengxian.ShengxianMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = ShengxianMod.MOD_ID, value = Dist.CLIENT)
public final class ClientFxEvents {
    private ClientFxEvents() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientFx.tick();
        ClientAscensionEffects.tick();
        AscensionOutcomeClient.tick();
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        ClientFx.render(event.getGuiGraphics());
        ClientAscensionEffects.renderFallback(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void renderWorld(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        ClientAscensionEffects.renderWorld(event);
    }
    @SubscribeEvent
    public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAscensionEffects.clear();
        AscensionOutcomeClient.clear();
        AscensionClientState.clear();
        VeilApertureTransitionRenderer.clear();
    }
}
