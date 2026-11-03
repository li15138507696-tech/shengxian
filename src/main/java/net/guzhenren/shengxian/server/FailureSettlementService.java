package net.guzhenren.shengxian.server;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge;
import net.guzhenren.shengxian.compat.SavedFailureReceipt;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.guzhenren.shengxian.core.AscensionReadiness;
import net.guzhenren.shengxian.core.AscensionRecord;
import net.guzhenren.shengxian.core.FailureSettlementIdentity;
import net.guzhenren.shengxian.core.FailureSettlementPolicy;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.core.SessionStore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One-attempt failure settlement with player NBT receipt verification and fail-closed recovery. */
public final class FailureSettlementService {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final GuzhenrenPlayerBridge UPSTREAM = new GuzhenrenPlayerBridge();
    private static final Map<AttemptKey, ActiveAttempt> ACTIVE = new ConcurrentHashMap<>();

    private FailureSettlementService() {}

    /** Called after a fatal result, on login, and after a respawn; all evidence remains server-authoritative. */
    public static void reconcile(ServerPlayer player) throws IOException {
        SessionStore store = AscensionServer.store();
        if (store == null) throw new IOException("升仙数据尚未加载");
        ApertureKey key = new ApertureKey(player.getUUID(), "first");
        Optional<AscensionRecord> found = store.find(key);
        if (found.isEmpty() || found.get().session().phase() != Phase.FAILED) return;
        AscensionRecord record = found.get();
        Set<AscensionProgress.FailureCause> causes = record.progress().failureCauses();
        if (causes.equals(Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT))) {
            TribulationImpactSettlementService.reconcile(player);
            return;
        }
        if (!AscensionReadiness.hasCanonicalFatalQiCause(causes)) {
            player.sendSystemMessage(Component.literal("升仙失败原因未通过原著白名单核验；记录保留并隔离，未重复施加后果。"));
            if (record.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.PREPARED
                    || record.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.STARTED) {
                markUncertain(store, key, record, player);
            }
            return;
        }
        AscensionProgress.FailureCause cause = causes.iterator().next();
        UUID operationId = FailureSettlementIdentity.operationId(record.session().id(), record.session().phaseRevision());
        Optional<SavedFailureReceipt.Receipt> saved = SavedFailureReceipt.read(player.getPersistentData());
        boolean receiptPresent = SavedFailureReceipt.present(player.getPersistentData());
        Optional<FailureSettlementPolicy.PlayerReceipt> policyReceipt = saved.map(receipt ->
                new FailureSettlementPolicy.PlayerReceipt(receipt.operationId(), receipt.cause(),
                        receipt.stage() == SavedFailureReceipt.Stage.STARTED
                                ? FailureSettlementPolicy.ReceiptStage.STARTED
                                : FailureSettlementPolicy.ReceiptStage.RESPAWN_RECEIPT));
        FailureSettlementPolicy.Action action = FailureSettlementPolicy.decide(
                record.progress().failureSettlementState(), operationId, cause, policyReceipt, receiptPresent);
        switch (action) {
            case ATTEMPT_ONCE -> {
                if (!AscensionReadiness.mayApplyFailure(
                        AscensionReadiness.evaluate(UPSTREAM.capabilities(),
                                net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge.requiredCapabilities()),
                        causes)) {
                    player.sendSystemMessage(Component.literal(
                            "升仙失败已记录；普通升仙门禁仍关闭，当前不会自动施加本体后果。"));
                    return;
                }
                attemptOnce(player, store, key, record, operationId, cause);
            }
            case MARK_UNCERTAIN -> markUncertain(store, key, record, player);
            case ACKNOWLEDGE -> acknowledgeFromReceipt(player, store, key, record, operationId, cause);
            case NOOP -> {
                if (record.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.UNCERTAIN) {
                    player.sendSystemMessage(Component.literal(
                            "升仙失败结算结果不确定；已隔离且不会自动重复致死，需人工核定。"));
                } else if (record.progress().failureSettlementState()
                        == AscensionProgress.FailureSettlementState.RESPAWN_RECEIPT) {
                    player.sendSystemMessage(Component.literal(
                            "世界记录有重生回执，但玩家存档核对未通过；保留双方记录且不施加伤害。"));
                }
            }
        }
    }

    private static void attemptOnce(ServerPlayer player, SessionStore store, ApertureKey key,
                                    AscensionRecord prepared, UUID operationId,
                                    AscensionProgress.FailureCause cause) throws IOException {
        try {
            UPSTREAM.persistFailureReceipt(player, operationId, cause, SavedFailureReceipt.Stage.STARTED);
        } catch (Exception saveFailure) {
            markUncertain(store, key, prepared, player);
            throw new IOException("Player STARTED receipt did not verify; no damage was applied", saveFailure);
        }

        AscensionRecord started;
        try {
            started = store.startFailure(key, prepared.session().stateRevision(),
                    nextSequence(prepared), actionTick(player, prepared));
        } catch (Exception worldFailure) {
            markUncertainBestEffort(store, key, player);
            throw new IOException("World STARTED transition did not verify; no damage was applied", worldFailure);
        }

        DamageSource source = UPSTREAM.failureDamageSource(player, cause);
        ActiveAttempt attempt = new ActiveAttempt(operationId, cause, source);
        AttemptKey attemptKey = new AttemptKey(player.getUUID(), operationId);
        ActiveAttempt previous = ACTIVE.putIfAbsent(attemptKey, attempt);
        if (previous != null) {
            markUncertainBestEffort(store, key, player);
            throw new IOException("Another in-memory failure attempt is active; no damage was applied");
        }
        boolean accepted;
        try {
            accepted = player.hurt(source, net.guzhenren.shengxian.core.CalamityDamage.impactDamage());
        } catch (RuntimeException damageFailure) {
            ACTIVE.remove(attemptKey, attempt);
            markUncertainBestEffort(store, key, player);
            throw new IOException("Failure damage outcome is uncertain", damageFailure);
        }
        attempt.deathWasCanceled = attempt.deathEvent != null && attempt.deathEvent.isCanceled();
        if (!accepted || !attempt.deathObserved || attempt.deathWasCanceled || player.isAlive()) {
            ACTIVE.remove(attemptKey, attempt);
            markUncertainBestEffort(store, key, player);
            player.sendSystemMessage(Component.literal(
                    "失败结算未能确认死亡；记录已隔离，不会自动重试。"));
            return;
        }
        LOGGER.info("Failure attempt {} entered death flow for {}; waiting for verified death clone and respawn receipt",
                operationId, player.getUUID());
    }

    private static void acknowledgeFromReceipt(ServerPlayer player, SessionStore store, ApertureKey key,
                                               AscensionRecord record, UUID operationId,
                                               AscensionProgress.FailureCause cause) throws IOException {
        try {
            UPSTREAM.persistFailureReceipt(player, operationId, cause, SavedFailureReceipt.Stage.RESPAWN_RECEIPT);
            AscensionRecord current = store.find(key).orElseThrow(() -> new IOException("Failure record disappeared"));
            if (current.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.PREPARED) {
                current = store.startFailure(key, current.session().stateRevision(),
                        nextSequence(current), actionTick(player, current));
            }
            if (current.progress().failureSettlementState() != AscensionProgress.FailureSettlementState.RESPAWN_RECEIPT
                    && current.progress().failureSettlementState() != AscensionProgress.FailureSettlementState.ACKNOWLEDGED) {
                current = store.recordFailureRespawnReceipt(key, current.session().stateRevision(),
                        nextSequence(current), actionTick(player, current), operationId);
            }
            current = store.acknowledgeFailure(key, current.session().stateRevision(),
                    nextSequence(current), actionTick(player, current), operationId);
            removeOwnerAttempts(player.getUUID());
            player.sendSystemMessage(Component.literal("升仙失败结算回执已核对；碎窍终态记录已确认。"));
        } catch (Exception failure) {
            throw failure instanceof IOException io ? io : new IOException("Could not verify failure receipt", failure);
        }
    }

    private static void markUncertain(SessionStore store, ApertureKey key, AscensionRecord record,
                                      ServerPlayer player) throws IOException {
        var state = record.progress().failureSettlementState();
        if (state != AscensionProgress.FailureSettlementState.PREPARED
                && state != AscensionProgress.FailureSettlementState.STARTED) {
            player.sendSystemMessage(Component.literal("升仙失败结算证据冲突；原记录保留并等待人工核定。"));
            return;
        }
        store.markFailureUncertain(key, record.session().stateRevision(),
                nextSequence(record), actionTick(player, record));
        removeOwnerAttempts(player.getUUID());
        player.sendSystemMessage(Component.literal(
                "升仙失败结算已标记为不确定；系统不会自动再次施加伤害。"));
    }

    private static void markUncertainBestEffort(SessionStore store, ApertureKey key, ServerPlayer player) {
        try {
            store.find(key).ifPresent(record -> {
                try {
                    if (record.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.PREPARED
                            || record.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.STARTED) {
                        store.markFailureUncertain(key, record.session().stateRevision(),
                                nextSequence(record), actionTick(player, record));
                    }
                } catch (IOException exception) {
                    LOGGER.error("Could not quarantine uncertain failure attempt for {}", player.getUUID(), exception);
                }
            });
        } catch (IOException exception) {
            LOGGER.error("Could not reload failure attempt for quarantine: {}", player.getUUID(), exception);
        }
        removeOwnerAttempts(player.getUUID());
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ActiveAttempt attempt = activeForOwner(player.getUUID());
        if (attempt != null && event.getSource() == attempt.damageSource) {
            attempt.deathObserved = true;
            attempt.deathEvent = event;
        }
        TribulationImpactSettlementService.onLivingDeath(event);
    }

    public static void onClone(PlayerEvent.Clone event) {
        TribulationImpactSettlementService.onClone(event);
        if (!event.isWasDeath() || !(event.getOriginal() instanceof ServerPlayer original)
                || !(event.getEntity() instanceof ServerPlayer clone)) return;
        ActiveAttempt attempt = activeForOwner(original.getUUID());
        if (attempt == null || !attempt.deathObserved || attempt.deathWasCanceled) return;
        Optional<SavedFailureReceipt.Receipt> oldReceipt = SavedFailureReceipt.read(original.getPersistentData());
        if (oldReceipt.filter(receipt -> receipt.operationId().equals(attempt.operationId)
                && receipt.cause() == attempt.cause && receipt.stage() == SavedFailureReceipt.Stage.STARTED).isEmpty()) {
            return;
        }
        try {
            SavedFailureReceipt.record(clone.getPersistentData(), attempt.operationId,
                    attempt.cause, SavedFailureReceipt.Stage.RESPAWN_RECEIPT);
        } catch (IOException failure) {
            ACTIVE.remove(new AttemptKey(original.getUUID(), attempt.operationId), attempt);
            LOGGER.warn("Could not mark matching failure death clone for {}", original.getUUID(), failure);
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            reconcile(player);
        } catch (Exception failure) {
            LOGGER.warn("Failure respawn receipt could not be verified for {}; world record retained",
                    player.getUUID(), failure);
            player.sendSystemMessage(Component.literal("失败结算回执尚未完成核验；记录已保留且不会重复施加伤害。"));
        }
    }

    public static void onLogout(ServerPlayer player) {
        quarantineActive(player.getUUID(), "player logout");
        TribulationImpactSettlementService.onLogout(player);
    }

    public static void onServerStopping() {
        Set<UUID> owners = ACTIVE.keySet().stream().map(AttemptKey::owner).collect(java.util.stream.Collectors.toSet());
        for (UUID owner : owners) quarantineActive(owner, "server stopping");
        ACTIVE.clear();
        TribulationImpactSettlementService.onServerStopping();
    }

    public static void clearAll() {
        ACTIVE.clear();
        TribulationImpactSettlementService.clearAll();
    }

    private static void quarantineActive(UUID owner, String reason) {
        if (removeOwnerAttempts(owner).isEmpty()) return;
        SessionStore store = AscensionServer.store();
        if (store == null) return;
        try {
            ApertureKey key = new ApertureKey(owner, "first");
            store.find(key).ifPresent(record -> {
                try {
                    if (record.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.STARTED
                            || record.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.PREPARED) {
                        store.markFailureUncertain(key, record.session().stateRevision(),
                                nextSequence(record), record.session().serverTick());
                    }
                } catch (IOException exception) {
                    LOGGER.error("Could not quarantine failure attempt during {} for {}", reason, owner, exception);
                }
            });
        } catch (IOException exception) {
            LOGGER.error("Could not load failure record during {} for {}", reason, owner, exception);
        }
    }

    private static long worldTick(ServerPlayer player) {
        return Math.max(player.server.overworld().getGameTime(), 0L);
    }

    private static long actionTick(ServerPlayer player, AscensionRecord record) {
        return Math.max(worldTick(player), record.session().serverTick());
    }

    private static long nextSequence(AscensionRecord record) throws IOException {
        if (record.session().lastActionSequence() == Long.MAX_VALUE) throw new IOException("Session sequence exhausted");
        return record.session().lastActionSequence() + 1L;
    }

    private static ActiveAttempt activeForOwner(UUID owner) {
        return ACTIVE.entrySet().stream().filter(entry -> entry.getKey().owner().equals(owner))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private static Set<ActiveAttempt> removeOwnerAttempts(UUID owner) {
        Set<ActiveAttempt> removed = ConcurrentHashMap.newKeySet();
        ACTIVE.entrySet().removeIf(entry -> {
            if (!entry.getKey().owner().equals(owner)) return false;
            removed.add(entry.getValue());
            return true;
        });
        return removed;
    }

    private record AttemptKey(UUID owner, UUID operationId) {}

    private static final class ActiveAttempt {
        private final UUID operationId;
        private final AscensionProgress.FailureCause cause;
        private final DamageSource damageSource;
        private volatile boolean deathObserved;
        private volatile boolean deathWasCanceled;
        private volatile LivingDeathEvent deathEvent;

        private ActiveAttempt(UUID operationId, AscensionProgress.FailureCause cause, DamageSource damageSource) {
            this.operationId = operationId;
            this.cause = cause;
            this.damageSource = damageSource;
        }
    }
}
