package net.guzhenren.shengxian.client;

import net.guzhenren.shengxian.ShengxianMod;
import net.guzhenren.shengxian.network.ShengxianNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = ShengxianMod.MOD_ID, value = Dist.CLIENT)
public final class ShengxianClientTick {
    static {
        ShengxianNetwork.setClientFxSink(ClientFx::play);
        net.guzhenren.shengxian.network.ShengxianNetwork.setClientVisualSink(ClientAscensionEffects::accept);
        net.guzhenren.shengxian.network.ShengxianNetwork.setClientOutcomeSink(AscensionOutcomeClient::accept);
        ShengxianNetwork.setClientStateSink(AscensionClientState::accept);
    }

    private ShengxianClientTick() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        while (ShengxianClientKeys.OPEN.consumeClick()) {
            minecraft.setScreen(new QiControlScreen());
        }
        driveTribulationFx(minecraft);
        TribulationFxWorkshop.tick();
    }

    /**
     * Scans nearby carrier armor stands and drives the Photon workshop: starts effects for new
     * carriers, follows existing ones, and retires effects whose entity has left. Photon absent
     * means this is a no-op and the server-side vanilla choreography stays the visual.
     */
    private static void driveTribulationFx(Minecraft minecraft) {
        if (!net.guzhenren.shengxian.ShengxianVisualConfig.particles()) {
            TribulationFxWorkshop.retainAll(java.util.Set.of());
            return;
        }
        if (!TribulationFxWorkshop.photonAvailable()) return;
        var seen = new java.util.HashSet<java.util.UUID>();
        var level = minecraft.level;
        if (level == null) return;
        if (!(level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel)) return;
        for (var entity : clientLevel.entitiesForRendering()) {
            if (!(entity instanceof net.minecraft.world.entity.decoration.ArmorStand stand)) continue;
            if (entity.distanceToSqr(minecraft.player) > 64 * 64) continue;
            var type = net.guzhenren.shengxian.client.CarrierTagReader.readType(stand);
            if (type == null) continue;
            seen.add(entity.getUUID());
            TribulationFxWorkshop.play(entity.getUUID(), level, entity.position(), type);
        }
        TribulationFxWorkshop.retainAll(seen);
    }
}
