package net.guzhenren.shengxian.client;

import net.guzhenren.shengxian.ShengxianMod;
import net.guzhenren.shengxian.entity.ShengxianEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-side renderer wiring for the calamity mobs. */
@EventBusSubscriber(modid = ShengxianMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CalamityClientSetup {
    private CalamityClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ShengxianEntities.CALAMITY_CARRIER.get(),
                CalamityCarrierRenderer::new);
    }
}
