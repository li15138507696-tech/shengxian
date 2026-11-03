package net.guzhenren.shengxian.server;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge;
import net.guzhenren.shengxian.compat.SavedTribulationImpactReceipt;
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

/** One-attempt, source-bound lethal settlement for named-profile tribulation impacts. */
public final class TribulationImpactSettlementService {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final GuzhenrenPlayerBridge UPSTREAM = new GuzhenrenPlayerBridge();
    private static final Map<AttemptKey, ActiveAttempt> ACTIVE = new ConcurrentHashMap<>();

    private TribulationImpactSettlementService() {}

    /** Called after a fatal impact, on login, and after respawn; every decision uses persisted evidence. */
    public static void reconcile(ServerPlayer player) throws IOException {
        SessionStore store = AscensionServer.store();
        if (store == null) throw new IOException("升仙数据尚未加载");
        ApertureKey key = new ApertureKey(player.getUUID(), "first");
        Optional<AscensionRecord> found = store.find(key);
        if (found.isEmpty() || found.get().session().phase() != Phase.FAILED) return;
        AscensionRecord record = found.get();
        Set<AscensionProgress.FailureCause> causes = record.progress().failureCauses();
        if (!causes.equals(Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT))) return;

        Optional<AscensionProgress.TribulationImpactFailure> storedContext =
                record.progress().tribulationImpactFailure();
        if (storedContext.isEmpty()
                || !AscensionReadiness.hasVerifiedTribulationImpactCause(causes, storedContext.orElse(null))) {
            quarantineMissingSource(player, store, key, record);
            return;
        }
        AscensionProgress.TribulationImpactFailure context = storedContext.orElseThrow();
        UUID operationId = FailureSettlementIdentity.operationId(record.session().id(),
                record.session().phaseRevision());
        Optional<SavedTribulationImpactReceipt.Receipt> saved =
                SavedTribulationImpactReceipt.read(player.getPersistentData());
        boolean receiptPresent = SavedTribulationImpactReceipt.present(player.getPersistentData());
        Optional<FailureSettlementPolicy.TribulationImpactPlayerReceipt> policyReceipt = saved.map(receipt ->
                new FailureSettlementPolicy.TribulationImpactPlayerReceipt(receipt.operationId(),
                        receipt.context(), receipt.stage() == SavedTribulationImpactReceipt.Stage.STARTED
                        ? FailureSettlementPolicy.ReceiptStage.STARTED
                        : FailureSettlementPolicy.ReceiptStage.RESPAWN_RECEIPT));
        FailureSettlementPolicy.Action action = FailureSettlementPolicy.decideTribulationImpact(
                record.progress().failureSettlementState(), operationId, context, policyReceipt, receiptPresent);
        switch (action) {
            case ATTEMPT_ONCE -> {
                if (!AscensionReadiness.mayApplyTribulationImpact(
                        AscensionReadiness.evaluate(AscensionReadiness.implementedCapabilities()), causes, context)) {
                    player.sendSystemMessage(Component.literal(
                            "灾劫冲击失败已记录；升仙总门禁仍关闭，当前不施加本体后果。"));
                    return;
                }
                attemptOnce(player, store, key, record, operationId, context);
            }
            case MARK_UNCERTAIN -> markUncertain(player, store, key, record, operationId, context);
            case ACKNOWLEDGE -> acknowledgeFromReceipt(player, store, key, record, operationId, context);
            case NOOP -> {
                if (record.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.UNCERTAIN) {
                    player.sendSystemMessage(Component.literal(
                            "灾劫冲击结算结果不确定；记录已隔离，不会自动再次致死。"));
                } else if (record.progress().failureSettlementState()
                        == AscensionProgress.FailureSettlementState.RESPAWN_RECEIPT) {
                    player.sendSystemMessage(Component.literal(
                            "世界记录有灾劫重生回执，但玩家存档核对未通过；不施加伤害。"));
                }
            }
        }
    }

    private static void attemptOnce(ServerPlayer player, SessionStore store, ApertureKey key,
                                    AscensionRecord prepared, UUID operationId,
                                    AscensionProgress.TribulationImpactFailure context) throws IOException {
        SavedTribulationImpactReceipt.Receipt startedReceipt = toReceipt(
                operationId, context, SavedTribulationImpactReceipt.Stage.STARTED);
        try {
            UPSTREAM.persistTribulationImpactReceipt(player, startedReceipt);
        } catch (Exception saveFailure) {
            markUncertainBestEffort(store, key, player, operationId, context);
            throw new IOException("Impact STARTED receipt did not verify; no damage was applied", saveFailure);
        }

        try {
            prepared = store.startTribulationImpactFailure(key, prepared.session().stateRevision(),
                    nextSequence(prepared), actionTick(player, prepared), operationId, context);
        } catch (Exception worldFailure) {
            markUncertainBestEffort(store, key, player, operationId, context);
            throw new IOException("Impact world STARTED transition did not verify; no damage was applied", worldFailure);
        }

        DamageSource source;
        try {
            source = UPSTREAM.tribulationImpactDamageSource(player);
        } catch (RuntimeException gateFailure) {
            markUncertainBestEffort(store, key, player, operationId, context);
            throw new IOException("Impact damage source was not enabled", gateFailure);
        }
        AttemptKey attemptKey = new AttemptKey(player.getUUID(), operationId);
        ActiveAttempt attempt = new ActiveAttempt(operationId, context, source);
        ActiveAttempt previous = ACTIVE.putIfAbsent(attemptKey, attempt);
        if (previous != null) {
            markUncertainBestEffort(store, key, player, operationId, context);
            throw new IOException("Another impact attempt is active; no damage was applied");
        }
        boolean accepted;
        try {
            accepted = player.hurt(source, net.guzhenren.shengxian.core.CalamityDamage.impactDamage());
        } catch (RuntimeException damageFailure) {
            ACTIVE.remove(attemptKey, attempt);
            markUncertainBestEffort(store, key, player, operationId, context);
            throw new IOException("Impact damage result is uncertain", damageFailure);
        }
        attempt.deathWasCanceled = attempt.deathEvent != null && attempt.deathEvent.isCanceled();
        if (!accepted || !attempt.deathObserved || attempt.deathWasCanceled || player.isAlive()) {
            ACTIVE.remove(attemptKey, attempt);
            markUncertainBestEffort(store, key, player, operationId, context);
            player.sendSystemMessage(Component.literal("灾劫冲击后果未能确认；记录已隔离，不会自动重试。"));
            return;
        }
        LOGGER.info("Impact attempt {} entered death flow for {}; waiting for matching clone and respawn receipt",
                operationId, player.getUUID());
    }

    private static void acknowledgeFromReceipt(ServerPlayer player, SessionStore store, ApertureKey key,
                                               AscensionRecord record, UUID operationId,
                                               AscensionProgress.TribulationImpactFailure context) throws IOException {
        try {
            UPSTREAM.persistTribulationImpactReceipt(player, toReceipt(operationId, context,
                    SavedTribulationImpactReceipt.Stage.RESPAWN_RECEIPT));
            AscensionRecord current = store.find(key).orElseThrow(() -> new IOException("Impact record disappeared"));
            if (current.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.PREPARED) {
                current = store.startTribulationImpactFailure(key, current.session().stateRevision(),
                        nextSequence(current), actionTick(player, current), operationId, context);
            }
            if (current.progress().failureSettlementState() != AscensionProgress.FailureSettlementState.RESPAWN_RECEIPT
                    && current.progress().failureSettlementState() != AscensionProgress.FailureSettlementState.ACKNOWLEDGED) {
                current = store.recordTribulationImpactRespawnReceipt(key, current.session().stateRevision(),
                        nextSequence(current), actionTick(player, current), operationId, context);
            }
            store.acknowledgeTribulationImpact(key, current.session().stateRevision(),
                    nextSequence(current), actionTick(player, current), operationId, context);
            removeOwnerAttempts(player.getUUID());
            player.sendSystemMessage(Component.literal("灾劫冲击失败的玩家回执已核对；世界记录已确认。"));
        } catch (Exception failure) {
            throw failure instanceof IOException io ? io : new IOException("Could not verify impact respawn receipt", failure);
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ActiveAttempt attempt = activeForOwner(player.getUUID());
        if (attempt != null && event.getSource() == attempt.damageSource) {
            attempt.deathObserved = true;
            attempt.deathEvent = event;
        }
    }

    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getOriginal() instanceof ServerPlayer original)
                || !(event.getEntity() instanceof ServerPlayer clone)) return;
        ActiveAttempt attempt = activeForOwner(original.getUUID());
        if (attempt == null || !attempt.deathObserved || attempt.deathWasCanceled) return;
        SavedTribulationImpactReceipt.Receipt started = toReceipt(attempt.operationId, attempt.context,
                SavedTribulationImpactReceipt.Stage.STARTED);
        if (SavedTribulationImpactReceipt.read(original.getPersistentData()).filter(started::equals).isEmpty()) return;
        try {
            SavedTribulationImpactReceipt.record(clone.getPersistentData(), toReceipt(attempt.operationId,
                    attempt.context, SavedTribulationImpactReceipt.Stage.RESPAWN_RECEIPT));
        } catch (IOException failure) {
            ACTIVE.remove(new AttemptKey(original.getUUID(), attempt.operationId), attempt);
            markUncertainBestEffort(AscensionServer.store(), new ApertureKey(original.getUUID(), "first"),
                    original, attempt.operationId, attempt.context);
            LOGGER.warn("Could not mark matching impact death clone for {}", original.getUUID(), failure);
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            reconcile(player);
        } catch (Exception failure) {
            LOGGER.warn("Impact respawn receipt could not be verified for {}; world record retained",
                    player.getUUID(), failure);
            player.sendSystemMessage(Component.literal("灾劫回执尚未完成核验；记录已保留且不会重复施加伤害。"));
        }
    }

    public static void onLogout(ServerPlayer player) {
        quarantineActive(player.getUUID(), "player logout");
    }

    public static void onServerStopping() {
        Set<UUID> owners = ACTIVE.keySet().stream().map(AttemptKey::owner)
                .collect(java.util.stream.Collectors.toSet());
        for (UUID owner : owners) quarantineActive(owner, "server stopping");
        ACTIVE.clear();
    }

    public static void clearAll() { ACTIVE.clear(); }

    private static void quarantineMissingSource(ServerPlayer player, SessionStore store, ApertureKey key,
                                                AscensionRecord record) throws IOException {
        var state = record.progress().failureSettlementState();
        if (state == AscensionProgress.FailureSettlementState.PREPARED
                || state == AscensionProgress.FailureSettlementState.STARTED) {
            store.quarantineUnverifiedTribulationImpact(key, record.session().stateRevision(),
                    nextSequence(record), actionTick(player, record));
        }
        player.sendSystemMessage(Component.literal(
                "灾劫冲击来源缺失或无法核验；旧记录已隔离，不会自动施加致死后果。"));
    }

    private static void markUncertain(ServerPlayer player, SessionStore store, ApertureKey key,
                                      AscensionRecord record, UUID operationId,
                                      AscensionProgress.TribulationImpactFailure context) throws IOException {
        var state = record.progress().failureSettlementState();
        if (state != AscensionProgress.FailureSettlementState.PREPARED
                && state != AscensionProgress.FailureSettlementState.STARTED) return;
        store.markTribulationImpactUncertain(key, record.session().stateRevision(),
                nextSequence(record), actionTick(player, record), operationId, context);
        player.sendSystemMessage(Component.literal("灾劫冲击结算证据冲突；原记录已隔离，不会重试。"));
    }

    private static void markUncertainBestEffort(SessionStore store, ApertureKey key, ServerPlayer player,
                                                UUID operationId,
                                                AscensionProgress.TribulationImpactFailure context) {
        if (store == null) return;
        try {
            store.find(key).ifPresent(record -> {
                try {
                    markUncertain(player, store, key, record, operationId, context);
                } catch (IOException failure) {
                    LOGGER.error("Could not quarantine impact settlement for {}", player.getUUID(), failure);
                }
            });
        } catch (IOException failure) {
            LOGGER.error("Could not reload impact settlement for {}", player.getUUID(), failure);
        }
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
                        Optional<AscensionProgress.TribulationImpactFailure> context =
                                record.progress().tribulationImpactFailure();
                        if (context.isPresent()) {
                            UUID operation = FailureSettlementIdentity.operationId(record.session().id(),
                                    record.session().phaseRevision());
                            store.markTribulationImpactUncertain(key, record.session().stateRevision(),
                                    nextSequence(record), record.session().serverTick(), operation, context.get());
                        } else {
                            store.quarantineUnverifiedTribulationImpact(key, record.session().stateRevision(),
                                    nextSequence(record), record.session().serverTick());
                        }
                    }
                } catch (IOException failure) {
                    LOGGER.error("Could not quarantine impact attempt during {} for {}", reason, owner, failure);
                }
            });
        } catch (IOException failure) {
            LOGGER.error("Could not load impact attempt during {} for {}", reason, owner, failure);
        }
    }

    private static SavedTribulationImpactReceipt.Receipt toReceipt(
            UUID operationId, AscensionProgress.TribulationImpactFailure context,
            SavedTribulationImpactReceipt.Stage stage) {
        return new SavedTribulationImpactReceipt.Receipt(operationId, context.profile(), context.carrierType(),
                context.carrierId(), context.impactSequence(), stage);
    }

    private static long actionTick(ServerPlayer player, AscensionRecord record) {
        return Math.max(player.server.overworld().getGameTime(), record.session().serverTick());
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
        private final AscensionProgress.TribulationImpactFailure context;
        private final DamageSource damageSource;
        private volatile boolean deathObserved;
        private volatile boolean deathWasCanceled;
        private volatile LivingDeathEvent deathEvent;

        private ActiveAttempt(UUID operationId, AscensionProgress.TribulationImpactFailure context,
                              DamageSource damageSource) {
            this.operationId = operationId;
            this.context = context;
            this.damageSource = damageSource;
        }
    }
}
