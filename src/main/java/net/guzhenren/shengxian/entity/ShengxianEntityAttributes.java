package net.guzhenren.shengxian.entity;

import net.guzhenren.shengxian.ShengxianMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/** Registers the carrier's attributes so its million-point health is real entity state. */
@EventBusSubscriber(modid = ShengxianMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ShengxianEntityAttributes {
    private ShengxianEntityAttributes() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(ShengxianEntities.CALAMITY_CARRIER.get(),
                CalamityCarrierEntity.createAttributes().build());
    }
}
