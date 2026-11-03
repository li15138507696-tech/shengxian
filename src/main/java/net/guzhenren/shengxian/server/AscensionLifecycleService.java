package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.command.FormApertureCommand;
import net.guzhenren.shengxian.compat.BenMingGuCustody;
import net.guzhenren.shengxian.compat.BenMingGuFingerprint;
import net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge;
import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.guzhenren.shengxian.core.AscensionRecord;
import net.guzhenren.shengxian.core.AscensionReadiness;
import net.guzhenren.shengxian.core.BenMingGuStore;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.core.QiParameters;
import net.guzhenren.shengxian.core.RankSixSyncDecision;
import net.guzhenren.shengxian.core.RuleProfile;
import net.guzhenren.shengxian.core.SessionStore;
import net.guzhenren.shengxian.core.ShatterClock;
import net.guzhenren.shengxian.core.ShatterEssenceReconciler;
import net.guzhenren.shengxian.core.ShatterEssenceTransaction;
import net.guzhenren.shengxian.network.AscensionSnapshotPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Owns the ordinary five-peak route transitions. All checks and results run on the server. */
public final class AscensionLifecycleService {
    public static final int ACTION_PREPARE = 0;
    public static final int ACTION_SHATTER = 1;
    public static final int ACTION_OPEN = 2;
    public static final int ACTION_OFFER_GU = 3;
    public static final int ACTION_FORM = 4;
    public static final int ACTION_CANCEL = 5;


    private static final GuzhenrenPlayerBridge UPSTREAM = new GuzhenrenPlayerBridge();
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");

    private AscensionLifecycleService() {}

    public static void perform(ServerPlayer player, int action) {
        try {

            String message = switch (action) {
                case ACTION_PREPARE -> prepare(player);
                case ACTION_SHATTER -> shatter(player);
                case ACTION_OPEN -> open(player);
                case ACTION_OFFER_GU -> offerVitalGu(player);
                case ACTION_FORM -> FormApertureCommand.form(player);
                case ACTION_CANCEL -> cancel(player);

                default -> throw new IllegalArgumentException("Unknown ascension action");
            };
            player.sendSystemMessage(Component.literal(message));
        } catch (Exception ex) {
            LOGGER.warn("Ascension action {} failed for {}", action, player.getUUID(), ex);
            player.sendSystemMessage(Component.literal("升仙操作未完成：" + ex.getMessage()));
        } finally {
            sendSnapshot(player);
        }
    }

    /** Sends only the requesting player's server-owned session. */
    public static void sendSnapshot(ServerPlayer player) {
        AscensionVisualService.refresh(player);
        AscensionReadiness.Decision readiness = readiness();
        AscensionSnapshotPayload snapshot = AscensionSnapshotPayload.empty(readiness.message());
        SessionStore store = AscensionServer.store();
        if (store != null) {
            try {
                var found = store.find(key(player));
                if (found.isPresent()) {
                    var record = found.get();
                    snapshot = snapshotOf(record, readiness);
                }
            } catch (IOException ignored) {
                snapshot = AscensionSnapshotPayload.empty(readiness.message());
            }
        }
        PacketDistributor.sendToPlayer(player, snapshot);
    }

    /** Builds the same read-only snapshot for an already loaded authoritative record. */
    public static AscensionSnapshotPayload snapshotOf(AscensionRecord record,
                                                       AscensionReadiness.Decision readiness) {
        var progress = record.progress();
        var qi = progress.qi();
        var aperture = progress.aperture();
        String tribulationSummary = progress.tribulations().stream().limit(4)
                .map(t -> {
                    var anchor = t.carrierAnchor();
                    String location = anchor.placed()
                            ? " @" + anchor.dimension() + " " + anchor.x() + "," + anchor.y() + "," + anchor.z()
                            : " @待定位";
                    return tribulationName(t.type()) + "×" + t.remaining()
                            + " [" + t.id() + "]" + location;
                })
                .collect(java.util.stream.Collectors.joining("；"));
        String failureSummary = progress.failureCauses().stream().map(Enum::name)
                .collect(java.util.stream.Collectors.joining("、"));
        return new AscensionSnapshotPayload(true, readiness.available(), readiness.message(),
                record.session().phase().ordinal(), record.session().broken(),
                qi.heaven(), qi.earth(), qi.human(), qi.humanReserve(),
                qi.ambientHeaven(), qi.ambientEarth(), progress.risk().maximum(), progress.window().ticks(),
                progress.tribulations().size(), tribulationSummary, progress.insightEarned(),
                progress.insightSpent(), progress.vitalGu().bound(), progress.vitalGu().name(),
                progress.failureConsequenceAvailable(), failureSummary, aperture.areaMu(),
                aperture.timeRatio(), aperture.essence(),
                net.guzhenren.shengxian.ShengxianConfig.apertureUpkeepPerInternalTick(),
                net.guzhenren.shengxian.ShengxianConfig.guBacklashThreshold());
    }

    private static String tribulationName(AscensionProgress.Tribulation.Type type) {
        return switch (type) {
            case JADE_WILLOW_WIND -> "柳风";
            case SINGLE_HORNED_TIGER -> "独角峥虎";
            case SOUND_CHIMES -> "音磬";
            case ANCIENT_BEAST -> "荒兽";
            case BLOOD_RAIN -> "血雨";
            case NONE -> "无";
        };
    }
    /** Sessions whose shatter debit was skipped because the base mod keeps rewriting zhenyuan. */
    private static final java.util.Set<java.util.UUID> SHATTER_ESSENCE_WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static AscensionReadiness.Decision readiness() {
        return AscensionReadiness.evaluate(
                net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge.capabilities(),
                net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge.requiredCapabilities());
    }

    /**
     * CANON-012: once the aperture exists, leftover ambient qi may brew another calamity inside
     * it. Only the witnessed Taibai example is enabled; the ordinary route has no verified inner
     * calamity (GAP-001), so this returns silently for every other profile.
     */
    public static void brewInnerTribulation(ServerPlayer player, ApertureKey key) throws IOException {
        SessionStore store = AscensionServer.store();
        if (store == null) return;
        AscensionRecord record = store.find(key).orElse(null);
        if (record == null) return;
        var progress = record.progress();
        if (!progress.aperture().formed() || progress.aperture().plotIndex() < 0) return;
        var parameters = net.guzhenren.shengxian.core.TribulationParameters
                .forProfile(ShengxianConfig.tribulationProfile());
        if (!net.guzhenren.shengxian.core.InnerTribulation.profileAllows(parameters)) return;
        var origin = net.guzhenren.shengxian.server.ApertureSpace.origin(progress.aperture().plotIndex());
        long tick = worldTick(player);
        var anchor = new net.guzhenren.shengxian.core.AscensionProgress.CarrierAnchor(
                net.guzhenren.shengxian.server.ApertureSpace.LEVEL.location().toString(),
                origin.getX() + 4, origin.getY() + 1, origin.getZ() + 4, java.util.UUID.randomUUID());
        var brewed = net.guzhenren.shengxian.core.InnerTribulation.brew(progress, tick, parameters, anchor);
        if (brewed == progress) return;
        store.mutate(key, record.session().stateRevision(),
                Math.max(record.session().lastActionSequence() + 1L, tick), tick, current -> brewed);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "仙窍已成，但残余二气在窍内酝酿出另一场灾劫——进入仙窍处理它。"));
    }

    public static void requireGameplayReady() throws IOException {
        AscensionReadiness.Decision decision = readiness();
        if (!decision.available()) throw new IOException(decision.message());
    }
    public static String prepare(ServerPlayer player) throws IOException {
        requireGameplayReady();
        SessionStore store = requireStore();
        ApertureKey key = key(player);
        if (store.find(key).isPresent()) throw new IOException("该空窍已有升仙记录");
        GuzhenrenPlayerBridge.Snapshot snapshot = UPSTREAM.inspect(player);
        if (!snapshot.ordinaryFivePeak()) {
            throw new IOException("普通路线要求五转四至五阶段、未失窍且不是十绝体");
        }
        var item = UPSTREAM.vitalGu(player);
        if (item.isEmpty()) throw new IOException("本体没有记录本命蛊");
        String fingerprint = BenMingGuFingerprint.of(item, player.level().registryAccess());
        if (!BenMingGuCustody.holds(player, fingerprint)) {
            throw new IOException("背包中找不到与本体镜像一致的本命蛊");
        }
        String name = item.getHoverName().getString();
        if (name.length() > 128) name = name.substring(0, 128);
        var binding = AscensionServer.benming().bindOrReplaceUncommitted(player.getUUID(), fingerprint, name);
        double reserve = UPSTREAM.initialHumanQiReserve(player);
        store.start(key, true, Optional.of(RuleProfile.ordinary()), worldTick(player),
                reserve, new AscensionProgress.VitalGu(binding.fingerprint(), binding.displayName()));
        AscensionAdvancements.syncPhase(player, Phase.PREPARING);
        AscensionSounds.phaseOnce(player, Phase.PREPARING);
        return "已备妥升仙会话。有限人气储备=" + reserve
                + "（本体魂魄、体质、气运的游戏映射；不使用 renqi）。准备好后执行“碎窍”。";
    }

    public static String shatter(ServerPlayer player) throws IOException {
        requireGameplayReady();
        SessionStore store = requireStore();
        ApertureKey key = key(player);
        var record = store.load(key);
        if (record.session().phase() != Phase.PREPARING) throw new IOException("当前阶段不能开始碎窍");
        var snapshot = UPSTREAM.inspect(player);
        if (!snapshot.ordinaryFivePeak()) throw new IOException("境界或路线条件已变化");
        var binding = record.progress().vitalGu();
        if (!binding.bound() || !BenMingGuCustody.holds(player, binding.fingerprint())) {
            throw new IOException("本命蛊与准备记录不一致");
        }
        double before = UPSTREAM.currentTrueEssence(player);
        double cost = Math.max(1.0, Math.ceil(snapshot.maxTrueEssence() * ShengxianConfig.shatterEssenceFraction()));
        if (before < cost) throw new IOException("真元不足以冲击窍壁");
        double target = before - cost;
        long tick = worldTick(player);
        AscensionRecord breaking = store.beginShatter(key, record.session().stateRevision(),
                nextSequence(record.session(), tick), tick, before, target);
        ensureShatterEssenceCommitted(player, breaking);
        return "已耗费 " + cost + " 真元开始冲击窍壁。过程持续 5 秒，开始后不可取消；掉线期间暂停计时。";
    }

    /** No safe cancellation is asserted after the original-aperture impact has begun. */
    public static String cancel(ServerPlayer player) throws IOException {
        SessionStore store = requireStore();
        var record = store.find(key(player)).orElse(null);
        if (record == null || record.session().phase() == Phase.PREPARING) {
            return "尚未开始碎窍冲击，无需取消。";
        }
        if (record.session().phase() == Phase.BREAKING) {
            throw new IOException("碎窍冲击一旦开始，现有原著依据不支持安全取消；会话与真元记录均已保留。");
        }
        throw new IOException("当前阶段没有可取消的碎窍冲击。");
    }

    /** Re-bases an in-progress shatter timer on login so disconnected ticks do not count. */
    public static void resumeShatterClock(ServerPlayer player) throws IOException {
        SessionStore store = AscensionServer.store();
        if (store == null) return;
        ApertureKey key = key(player);
        var found = store.find(key);
        if (found.isEmpty() || found.get().session().phase() != Phase.BREAKING) return;
        var record = found.get();
        ensureShatterEssenceCommitted(player, record);
        long tick = worldTick(player);
        store.rebaseShatterClock(key, record.session().stateRevision(),
                nextSequence(record.session(), tick), tick);
    }

    /** Resolves the saved player receipt before allowing the BREAKING timer to move. */
    private static void ensureShatterEssenceCommitted(ServerPlayer player, AscensionRecord record) throws IOException {
        long phaseRevision = record.session().phaseRevision();
        double target;
        Double worldBefore;
        try {
            target = record.progress().shatterEssenceTarget(phaseRevision)
                    .orElseThrow(() -> new IOException("当前碎窍尝试缺少扣费目标，记录已保留"));
            worldBefore = record.progress().shatterEssenceIntent(phaseRevision)
                    .map(AscensionProgress.ShatterEssenceIntent::before).orElse(null);
        } catch (IllegalStateException malformed) {
            throw new IOException("当前碎窍意图损坏或冲突，记录已隔离", malformed);
        }

        // Reference behaviour: the witnessed implementation only *checks* true essence and starts;
        // it never performs a durable debit, which is why a continuously recomputed zhenyuan can
        // never deadlock it. This module keeps the debit as a best-effort bonus: when the durable
        // receipt cannot be reconciled (the base mod rewrote zhenyuan between capture and check),
        // the shatter continues without the charge instead of freezing the attempt forever.
        try {
            reconcileShatterEssence(player, record, phaseRevision, worldBefore, target);
        } catch (IOException blocked) {
            warnShatterEssenceOnce(player, blocked);
        }
    }

    /** Logs one skipped-debit warning per session so a drifting value cannot spam the console. */
    private static void warnShatterEssenceOnce(ServerPlayer player, IOException blocked) {
        if (SHATTER_ESSENCE_WARNED.add(player.getUUID())) {
            LOGGER.warn("Shatter true-essence debit skipped for {}: the base mod recomputes zhenyuan, "
                    + "so the durable baseline cannot be proven; the attempt continues uncharged. Cause: {}",
                    player.getUUID(), blocked.getMessage());
        }
    }

    private static void reconcileShatterEssence(ServerPlayer player, AscensionRecord record,
                                                long phaseRevision, Double worldBefore, double target)
            throws IOException {
        ShatterEssenceReconciler.reconcile(record.session().id(), phaseRevision, worldBefore, target,
                new ShatterEssenceReconciler.Port() {
                    @Override public Optional<ShatterEssenceTransaction.Receipt> readReceipt() throws IOException {
                        return UPSTREAM.shatterEssenceReceipt(player);
                    }

                    @Override public double currentTrueEssence() throws IOException {
                        return UPSTREAM.currentTrueEssence(player);
                    }

                    @Override public void persist(ShatterEssenceTransaction.Receipt receipt,
                                                  Double expectedEssence) throws IOException {
                        UPSTREAM.persistShatterEssenceReceipt(player, receipt, expectedEssence);
                    }

                    @Override public void applyTarget(ShatterEssenceTransaction.Receipt prepared) throws IOException {
                        UPSTREAM.applyTrueEssenceTarget(player, prepared);
                    }

                    @Override public void verify(ShatterEssenceTransaction.Receipt receipt,
                                                 double expectedEssence) throws IOException {
                        UPSTREAM.verifyShatterEssence(player, receipt, expectedEssence);
                    }

                    @Override public void verifyCommitted(ShatterEssenceTransaction.Receipt receipt)
                            throws IOException {
                        UPSTREAM.verifyShatterEssenceState(player, receipt);
                    }

                    @Override public void markUncertain(UUID sessionId, long phase,
                                                        Double before, double fixedTarget) {
                        markShatterEssenceUncertain(player, record, before, fixedTarget);
                    }
                });
    }

    /** Persists uncertainty only when the current receipt belongs to this exact transaction. */
    private static void markShatterEssenceUncertain(ServerPlayer player, AscensionRecord record,
                                                     Double worldBefore, double target) {
        try {
            long phaseRevision = record.session().phaseRevision();
            var current = UPSTREAM.shatterEssenceReceipt(player);
            ShatterEssenceTransaction.Receipt uncertain;
            if (current.isEmpty()) {
                uncertain = new ShatterEssenceTransaction.Receipt(record.session().id(), phaseRevision,
                        ShatterEssenceTransaction.Stage.UNCERTAIN, worldBefore, target, null);
            } else {
                ShatterEssenceTransaction.Receipt prior = current.get();
                if (!prior.sessionId().equals(record.session().id())
                        || prior.phaseRevision() != phaseRevision || prior.target() != target
                        || prior.stage() == ShatterEssenceTransaction.Stage.UNCERTAIN) return;
                uncertain = prior.uncertain();
            }
            double currentEssence = UPSTREAM.currentTrueEssence(player);
            UPSTREAM.persistShatterEssenceReceipt(player, uncertain, currentEssence);
        } catch (Exception failure) {
            LOGGER.warn("Could not persist uncertain shatter receipt for {}", player.getUUID(), failure);
        }
    }
    public static String open(ServerPlayer player) throws IOException {
        requireGameplayReady();
        SessionStore store = requireStore();
        ApertureKey key = key(player);
        var record = store.load(key);
        if (record.session().phase() != Phase.ABSORBING) throw new IOException("当前不在三气纳气阶段");
        var progress = record.progress();
        var qi = progress.qi();
        if (!progress.tribulations().isEmpty()) throw new IOException("外劫尚未结束，不能收束三气");
        double mean = (qi.heaven() + qi.earth() + qi.human()) / 3.0;
        double tolerance = QiParameters.standard().balanceTolerance();
        if (mean <= 0 || qi.heaven() < QiParameters.standard().windowMinQi()
                || qi.earth() < QiParameters.standard().windowMinQi()
                || qi.human() < QiParameters.standard().windowMinQi()
                || excess(qi.heaven(), mean) > tolerance
                || excess(qi.earth(), mean) > tolerance
                || excess(qi.human(), mean) > tolerance) {
            throw new IOException("三气尚未达到稳定交感；继续调和或吸纳");
        }
        long tick = worldTick(player);
        store.advance(key, Phase.OPENING, record.session().stateRevision(),
                nextSequence(record.session(), tick), tick);
        AscensionControlService.clear(player.getUUID());
        AscensionAdvancements.syncPhase(player, Phase.OPENING);
        AscensionSounds.phaseOnce(player, Phase.OPENING);
        return "三气已达稳定交感，进入开窍阶段。";
    }

    public static String offerVitalGu(ServerPlayer player) throws IOException {
        requireGameplayReady();
        SessionStore store = requireStore();
        ApertureKey key = key(player);
        var record = store.load(key);
        if (record.session().phase() != Phase.OPENING && record.session().phase() != Phase.FORMING) {
            throw new IOException("只有开窍或仙窍定型阶段可以投入本命蛊");
        }
        var vital = record.progress().vitalGu();
        if (!vital.bound() || !UPSTREAM.inspect(player).hasVitalGu()) throw new IOException("找不到准备时记录的本命蛊镜像");
        BenMingGuStore custody = AscensionServer.benming();
        if (custody == null || !BenMingGuCustody.ensureOffered(player, custody,
                vital.fingerprint(), vital.name().isBlank() ? "本命蛊" : vital.name())) {
            throw new IOException("找不到可托管的真实本命蛊物品；记录未推进");
        }
        if (record.session().phase() == Phase.OPENING) {
            long tick = worldTick(player);
            store.advance(key, Phase.FORMING, record.session().stateRevision(),
                    nextSequence(record.session(), tick), tick);
        }
        AscensionAdvancements.syncPhase(player, Phase.FORMING);
        AscensionSounds.phaseOnce(player, Phase.FORMING);
        return "本命蛊已投入三气团，仙窍正在定型；点击“形成仙窍”完成结算。";
    }

    public static String syncRank(ServerPlayer player) throws IOException {
        String result = reconcileRankSix(player);
        return result == null ? "没有待恢复的六转同步记录。" : result;
    }

    /** Replays the committed rank projection once, then durably acknowledges it in the session. */
    public static String reconcileRankSix(ServerPlayer player) throws IOException {
        SessionStore store = AscensionServer.store();
        if (store == null) throw new IOException("升仙数据尚未加载");
        ApertureKey key = key(player);
        var found = store.find(key);
        if (found.isEmpty()) return null;
        var record = found.get();
        if (record.progress().rankSixSyncAcknowledged()) {
            double currentRank = UPSTREAM.inspect(player).rank();
            if (!Double.isFinite(currentRank) || currentRank < 6.0) {
                LOGGER.warn("Upstream rank drift detected for {} (recorded immortal aperture, current rank={}); preserving upstream state",
                        player.getUUID(), currentRank);
                return "检测到本体转数低于已成仙窍记录；保留本体现状，不自动覆盖。";
            }
            return null;
        }
        if (!record.progress().rankSixSyncRecoveryAvailable()) return null;
        if (record.session().phase() != Phase.IMMORTAL || !record.progress().aperture().formed()) {
            throw new IOException("六转同步记录与仙窍状态不一致，保留待处理记录");
        }
        long tick = worldTick(player);
        RankSixSyncDecision.Action action = RankSixSyncDecision.commit(
                () -> UPSTREAM.inspect(player).rank(),
                () -> UPSTREAM.rankProjectionRecorded(player,record.session().id()),
                record.progress().rankSixSyncProjectionV1(),
                () -> UPSTREAM.applyOrdinaryImmortalRank(player),
                () -> UPSTREAM.persistRankProjection(player,record.session().id()),
                () -> store.acknowledgeRankSixSync(key, record.session().stateRevision(),
                        nextSequence(record.session(), tick), tick));
        if (action == RankSixSyncDecision.Action.DEFER) {
            throw new IOException("本体修为与待恢复记录不符，或已执行后发生降转；保留现状，不重复升转");
        }
        return action == RankSixSyncDecision.Action.APPLY_RANK_SIX
                ? "已恢复本体六转同步。"
                : "本体已达六转或更高，已确认同步记录。";
    }

    /** Reconciles the two-file Gu escrow against the session's commit phase after reconnect. */
    public static String reconcileBenMingGu(ServerPlayer player) throws IOException {
        SessionStore store = AscensionServer.store();
        BenMingGuStore custody = AscensionServer.benming();
        if (store == null || custody == null) throw new IOException("升仙数据尚未加载");
        ApertureKey key = key(player);
        var found = store.find(key);
        var escrow = custody.findEscrow(player.getUUID());
        if (escrow.isEmpty()) return null;
        if (found.isPresent() && found.get().session().phase() == Phase.IMMORTAL
                && found.get().progress().aperture().formed()) {
            var vital = found.get().progress().vitalGu();
            if (!vital.bound() || !vital.fingerprint().equals(escrow.get().fingerprint())) {
                throw new IOException("成窍记录与本命蛊托管不一致；原物保留，未覆盖");
            }
            return switch (escrow.get().state()) {
                case EMBEDDED -> null;
                case STORED -> {
                    custody.markEmbedded(player.getUUID());
                    yield "本命蛊已随仙窍形成记入托管；原著未给出通用返还/升炼映射，暂不伪造结果。";
                }
                case OFFER_PENDING -> {
                    if (!BenMingGuCustody.ensureOffered(player, custody, vital.fingerprint(), vital.name())) {
                        throw new IOException("已成仙窍，但本命蛊托管意图无法完成；物品与副本均保留");
                    }
                    custody.markEmbedded(player.getUUID());
                    yield "已恢复本命蛊投入记录；原著未给出通用返还/升炼映射，暂不伪造结果。";
                }
                case RETURN_PENDING, RETURNED -> {
                    boolean restored = BenMingGuCustody.restoreAfterFormation(player, custody);
                    yield restored ? "已完成此前版本已开始的本命蛊返还事务。"
                            : "此前版本的本命蛊返还仍在托管中；背包满时原物不会丢失。";
                }
            };
        }
        if (found.isPresent() && found.get().session().phase() == Phase.FORMING
                && !found.get().progress().aperture().formed()) {
            var vital = found.get().progress().vitalGu();
            if (!vital.bound() || !vital.fingerprint().equals(escrow.get().fingerprint())
                    || !BenMingGuCustody.ensureOffered(player, custody, vital.fingerprint(), vital.name())) {
                throw new IOException("正在成窍的本命蛊托管状态无法对账；原物与托管副本均保留");
            }
            return "本命蛊仍处于仙窍定型托管中。";
        }
        boolean returned = BenMingGuCustody.restoreAfterFormation(player, custody);
        return returned ? "已核对并恢复本命蛊托管。" : "背包空间不足；本命蛊仍安全保存在托管中。";
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        SessionStore store = AscensionServer.store();
        if (store == null) return;
        long tick = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ApertureKey key = key(player);
            try {
                var found = store.find(key);
                if (found.isEmpty()) continue;
                var record = found.get();
                Phase phase = record.session().phase();
                // IMMORTAL and FAILED are committed outside this service, so the server reconciles them here.
                AscensionAdvancements.syncPhase(player, phase);
                if (!AscensionReadiness.mayAdvanceSession(readiness(), phase)) continue;
                if (phase == Phase.BREAKING) {
                    ensureShatterEssenceCommitted(player, record);
                    record = store.advanceShatterClock(key, record.session().stateRevision(),
                            nextSequence(record.session(), tick), tick, ShengxianConfig.shatterTicks());
                    ShatterClock.State clock = record.progress().shatterClock(record.session().phaseRevision())
                            .orElseThrow(() -> new IOException("当前碎窍尝试缺少计时记录，记录已保留"));
                    if (!clock.complete(ShengxianConfig.shatterTicks())) continue;
                }
                if (phase == Phase.BREAKING) {
                    player.server.getPlayerList().saveAll();
                    var broken = store.advance(key, Phase.BROKEN, record.session().stateRevision(),
                            nextSequence(record.session(), tick), tick);
                    player.sendSystemMessage(Component.literal("窍壁已彻底碎裂，升仙不可逆。"));
                    AscensionAdvancements.syncPhase(player, Phase.BROKEN);
                    AscensionSounds.phaseOnce(player, Phase.BROKEN);
                    store.advance(key, Phase.ABSORBING, broken.session().stateRevision(),
                            nextSequence(broken.session(), tick), tick);
                    AscensionAdvancements.syncPhase(player, Phase.ABSORBING);
                    AscensionSounds.phaseOnce(player, Phase.ABSORBING);
                    player.sendSystemMessage(Component.literal("天地二气涌入，进入三气纳气阶段。按 K 调气。"));
                    continue;
                }
                if (phase == Phase.BROKEN) {
                    AscensionAdvancements.syncPhase(player, Phase.BROKEN);
                    AscensionSounds.phaseOnce(player, Phase.BROKEN);
                    store.advance(key, Phase.ABSORBING, record.session().stateRevision(),
                            nextSequence(record.session(), tick), tick);
                    AscensionAdvancements.syncPhase(player, Phase.ABSORBING);
                    AscensionSounds.phaseOnce(player, Phase.ABSORBING);
                    player.sendSystemMessage(Component.literal("进入三气纳气阶段。按 K 调气。"));
                    continue;
                }
            } catch (Exception ex) {
                player.sendSystemMessage(Component.literal("升仙状态恢复失败，记录已保留，请联系管理员查看日志。"));
            }
        }
    }

    private static double excess(double value, double mean) {
        return Math.max(0.0, (value - mean) / mean);
    }

    private static long worldTick(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getGameTime();
    }

    private static long nextSequence(net.guzhenren.shengxian.core.AscensionSession session, long tick) {
        return Math.max(session.lastActionSequence() + 1, tick);
    }

    private static ApertureKey key(ServerPlayer player) {
        return new ApertureKey(player.getUUID(), "first");
    }

    private static SessionStore requireStore() throws IOException {
        SessionStore store = AscensionServer.store();
        if (store == null || AscensionServer.benming() == null) throw new IOException("升仙数据尚未加载");
        return store;
    }
}
