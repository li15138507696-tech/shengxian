package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.ApertureEngine;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.AscensionRecord;
import net.guzhenren.shengxian.core.SessionStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Advances the virtual aperture clock for online owners inside their aperture, once per
 * second. It does not accrue offline time and does not change lifespan. Essence production
 * (0.01 per internal tick) and the upkeep charged per internal tick are both game parameters.
 * Upkeep is charged first; a balance that cannot cover it stalls the clock instead of going
 * negative, so the ledger never prints essence it did not earn.
 */
public final class ApertureTickService {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final double ESSENCE_PER_INTERNAL_TICK = 0.01;
    public static final long ONLINE_STEP_SERVER_TICKS = 20L;

    public static ApertureEngine.AdvanceProjection projectOnlineStep(
            net.guzhenren.shengxian.core.AscensionProgress.Aperture aperture) {
        return ApertureEngine.projectAdvance(aperture, ONLINE_STEP_SERVER_TICKS,
                ESSENCE_PER_INTERNAL_TICK, ShengxianConfig.apertureUpkeepPerInternalTick());
    }

    /** How often an existing eye 道痕 flares again; a game mapping, not a canon duration. */
    private static final long DAO_TAO_PERIOD_TICKS = 1200L;

    private ApertureTickService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % ONLINE_STEP_SERVER_TICKS != 0) return;
        SessionStore store = AscensionServer.store();
        if (store == null) return;
        long gameTime = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().equals(ApertureSpace.LEVEL)) continue;
            ApertureKey key = new ApertureKey(player.getUUID(), "first");
            try {
                var found = store.find(key);
                if (found.isEmpty()) continue;
                AscensionRecord record = found.get();
                if (record.session().phase() != net.guzhenren.shengxian.core.Phase.IMMORTAL
                        || !record.progress().aperture().formed()) continue;
                // CANON-009 道痕: the mark is permanent, so the burst recurs while it exists.
                if (net.guzhenren.shengxian.core.XianGuForcedUse.eyesDamaged(record.progress())
                        && gameTime % DAO_TAO_PERIOD_TICKS == 0) {
                    XianGuService.applyDaoTao(player);
                }
                var origin = ApertureSpace.origin(record.progress().aperture().plotIndex());
                if (player.getX() < origin.getX() - 8 || player.getX() >= origin.getX() + 8
                        || player.getZ() < origin.getZ() - 8 || player.getZ() >= origin.getZ() + 8) continue;
                long sequence = Math.max(record.session().lastActionSequence() + 1, gameTime);
                store.mutate(key, record.session().stateRevision(), sequence, gameTime,
                        progress -> ApertureEngine.advanceInternal(progress, ONLINE_STEP_SERVER_TICKS,
                                ESSENCE_PER_INTERNAL_TICK, ShengxianConfig.apertureUpkeepPerInternalTick()));
            } catch (IOException | IllegalStateException ex) {
                LOGGER.warn("aperture tick skipped for {}", player.getUUID(), ex);
            }
        }
    }
}
