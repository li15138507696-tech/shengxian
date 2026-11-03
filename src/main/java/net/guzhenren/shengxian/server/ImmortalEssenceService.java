package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.Phase;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;

/** Keeps a formed immortal's upstream true essence at its maximum as a game mapping. */
public final class ImmortalEssenceService {
    private static final GuzhenrenPlayerBridge BRIDGE = new GuzhenrenPlayerBridge();

    private ImmortalEssenceService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ShengxianConfig.immortalInfiniteTrueEssence()) return;
        if (event.getServer().getTickCount() % 20 != 0) return;
        var store = AscensionServer.store();
        if (store == null) return;
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            try {
                var record = store.find(new ApertureKey(player.getUUID(), "first"));
                if (record.isPresent() && record.get().session().phase() == Phase.IMMORTAL
                        && record.get().progress().aperture().formed()) {
                    BRIDGE.refillImmortalTrueEssence(player);
                }
            } catch (IOException ignored) {
                // Damaged records are surfaced by status/commands; never crash the tick loop.
            }
        }
    }
}
