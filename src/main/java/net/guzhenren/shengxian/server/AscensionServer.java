package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.ApertureSpaceStore;
import net.guzhenren.shengxian.core.BenMingGuStore;
import net.guzhenren.shengxian.core.SessionStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Server lifecycle binding for the session store. The directory is derived from the world
 * save path, never from the process working directory. If the store cannot be opened the
 * server fails fast instead of silently running without persistence.
 */
public final class AscensionServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static volatile SessionStore store;
    private static volatile ApertureSpaceStore spaceStore;
    private static volatile BenMingGuStore benmingStore;
    private static volatile Map<Integer, ApertureKey> plotOwners = Map.of();

    private AscensionServer() {}

    public static void onServerStarted(ServerStartedEvent event) {
        AscensionControlService.clearAll();
        FailureSettlementService.clearAll();
        AscensionVisualService.clear();
        GuActivationObserver.clear();
        GuBacklashService.clear();
        Path sessions = event.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("shengxian").resolve("sessions");
        try {
            // Publish stores only after all sessions and plot reservations reconcile safely.
            SessionStore loadedSessions = new SessionStore(sessions,
                    GuzhenrenPlayerBridge.capabilities(), GuzhenrenPlayerBridge.requiredCapabilities());
            ApertureSpaceStore loadedSpace = new ApertureSpaceStore(
                    sessions.getParent().resolve("aperture-space.properties"));
            BenMingGuStore loadedBenming = new BenMingGuStore(
                    sessions.getParent().resolve("benminggu.properties"));
            Map<Integer, ApertureKey> claims = loadedSessions.formedPlotClaimsLenient();
            Set<Integer> oldReservations = loadedSpace.reservedPlots();
            Set<Integer> restored = new TreeSet<>(claims.keySet());
            restored.removeAll(oldReservations);
            Set<Integer> unclaimed = new TreeSet<>(oldReservations);
            unclaimed.addAll(loadedSpace.builtPlots());
            unclaimed.removeAll(claims.keySet());
            loadedSpace.reconcileReservations(claims.keySet());
            plotOwners = Map.copyOf(claims);
            store = loadedSessions;
            spaceStore = loadedSpace;
            benmingStore = loadedBenming;
            LOGGER.info("shengxian session store opened at {}; formed plots={}, restored reservations={}",
                    sessions, claims.size(), restored.size());
            if (!unclaimed.isEmpty()) {
                LOGGER.warn("Retaining {} unclaimed aperture plots; first entries: {}",
                        unclaimed.size(), unclaimed.stream().limit(10).toList());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot open shengxian session store at " + sessions, ex);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        AscensionControlService.clearAll();
        FailureSettlementService.onServerStopping();
        AscensionVisualService.clear();
        GuActivationObserver.clear();
        GuBacklashService.clear();
        SessionStore current = store;
        store = null;
        spaceStore = null;
        benmingStore = null;
        plotOwners = Map.of();
        if (current != null) current.flush();
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SessionStore current = store;
        if (event.getEntity() instanceof ServerPlayer player) {
            FailureSettlementService.onLogout(player);
            AscensionControlService.clear(player.getUUID());
            AscensionVisualService.stop(player.getUUID());
            if (current != null) current.evict(player.getUUID());
        }
    }

    /** Replays a committed six-turn projection after a player returns to the world. */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        GuBacklashService.reconcileOnLogin(player);
        try {
            AscensionLifecycleService.resumeShatterClock(player);
        } catch (Exception ex) {
            LOGGER.warn("Could not resume shatter clock for {}; session was retained", player.getUUID(), ex);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "碎窍计时恢复失败；会话已保留，暂未推进。"));
        }
        try {
            SessionStore current = store;
            ApertureSpaceStore currentSpace = spaceStore;
            if (current != null) {
                current.find(new ApertureKey(player.getUUID(), "first")).ifPresent(record -> {
                    if (record.session().phase() == net.guzhenren.shengxian.core.Phase.IMMORTAL
                            && record.progress().aperture().formed()) {
                        int plot = record.progress().aperture().plotIndex();
                        try {
                            if (currentSpace != null && currentSpace.reservation(plot).isPresent()) {
                                currentSpace.commitPlotReservation(plot, record.session().key(), record.session().id());
                            }
                            registerFormedPlot(record.session().key(), plot);
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                        if (currentSpace != null) {
                            try {
                                currentSpace.reconcileAscensionGuardians(player.getUUID(), plot);
                            } catch (IOException ex) {
                                LOGGER.warn("Could not reconcile formed-aperture guardian permissions for {}; durable records were retained",
                                        player.getUUID(), ex);
                            }
                        }
                    }
                });
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not reconcile formed aperture plot for {}; durable session was retained",
                    player.getUUID(), ex);
        }
        try {
            SessionStore current = store;
            if (current != null) current.find(new ApertureKey(player.getUUID(), "first"))
                    .ifPresent(record -> TribulationWorldService.reconcile(player, record));
        } catch (Exception ex) {
            LOGGER.warn("Could not reconcile calamity carriers for {}; durable session was retained",
                    player.getUUID(), ex);
        }        try {
            String result = AscensionLifecycleService.reconcileRankSix(player);
            if (result != null) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result));
        } catch (Exception ex) {
            LOGGER.warn("Could not reconcile rank-six projection for {}; pending intent was retained",
                    player.getUUID(), ex);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "仙窍已成，但六转同步暂未完成；记录已保留，将继续尝试恢复。"));
        }
        try {
            String result = AscensionLifecycleService.reconcileBenMingGu(player);
            if (result != null) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result));
        } catch (Exception ex) {
            LOGGER.warn("Could not reconcile BenMingGu escrow for {}; item copy was retained",
                    player.getUUID(), ex);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "本命蛊托管暂未完成对账；物品副本已保留，请整理背包空间后重登。"));
        }
        reconcileFailure(player);
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        FailureSettlementService.onPlayerRespawn(event);
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        FailureSettlementService.onClone(event);
    }

    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        FailureSettlementService.onLivingDeath(event);
    }

    private static void reconcileFailure(ServerPlayer player) {
        try {
            FailureSettlementService.reconcile(player);
        } catch (Exception ex) {
            LOGGER.warn("Could not reconcile persisted ascension failure for {}; record was retained",
                    player.getUUID(), ex);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "升仙失败结算未完成核验；记录已保留，不会自动重试伤害。"));
        }
    }

    /** Null before server start and after server stop. */
    public static SessionStore store() {
        return store;
    }

    public static Map<Integer, ApertureKey> formedPlotOwners() {
        return plotOwners;
    }

    /** Publishes an already committed formation to the live protection index. */
    public static synchronized void registerFormedPlot(ApertureKey key, int plot) throws IOException {
        if (key == null || !ApertureSpaceStore.isValidPlot(plot)) throw new IOException("Invalid formed plot claim");
        ApertureKey existing = plotOwners.get(plot);
        if (existing != null && !existing.equals(key)) throw new IOException("Aperture plot is already claimed");
        ApertureSpaceStore currentSpace = spaceStore;
        if (currentSpace != null) currentSpace.reconcileReservations(Set.of(plot));
        if (existing != null) return;
        java.util.HashMap<Integer, ApertureKey> updated = new java.util.HashMap<>(plotOwners);
        updated.put(plot, key);
        plotOwners = Map.copyOf(updated);
    }
    public static BenMingGuStore benming() {
        return benmingStore;
    }

    public record ApertureContext(SessionStore store, ApertureSpaceStore spaceStore) {}

    /** Null unless both the session store and the plot store are open. */
    public static ApertureContext apertureContext() {
        SessionStore sessions = store;
        ApertureSpaceStore plots = spaceStore;
        return sessions == null || plots == null ? null : new ApertureContext(sessions, plots);
    }
}
