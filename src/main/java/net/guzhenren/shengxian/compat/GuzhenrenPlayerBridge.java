package net.guzhenren.shengxian.compat;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.init.GuzhenrenModMobEffects;
import net.guzhenren.shengxian.core.AscensionReadiness;
import net.guzhenren.shengxian.core.OrdinaryQualification;
import net.guzhenren.shengxian.core.RuleProfile;
import net.guzhenren.shengxian.core.ShatterEssenceTransaction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/** Narrow adapter for the audited upstream player fields used by the ordinary route. */
public final class GuzhenrenPlayerBridge {
    /**
     * Capabilities this integration can actually satisfy. Computed on demand so a config reload or
     * a fresh load order cannot freeze the route into the wrong state.
     *
     * <p>The audited upstream field boundary is deliberately not padded in here: the ordinary route
     * no longer requires it by default (PROJECT-003), and strict mode is expressed by
     * {@link #requiredCapabilities()} rather than by inventing an adapter capability.</p>
     */
    public static Set<RuleProfile.Capability> capabilities() {
        return AscensionReadiness.implementedCapabilities();
    }

    /** The requirement set for this deployment, honouring the strict operator switch. */
    public static Set<RuleProfile.Capability> requiredCapabilities() {
        return RuleProfile.ordinaryRequired(
                net.guzhenren.shengxian.ShengxianConfig.requireUpstreamApertureWrite());
    }

    public record Snapshot(double rank, double stage, double upstreamShouyuanRaw,
                           double upstreamRenqiDerived, double aperture, double physique,
                           boolean hasVitalGu, boolean tenExtremes, boolean apertureLost,
                           boolean stageShifted, double currentTrueEssence, double maxTrueEssence) {
        public boolean ordinaryFivePeak() {
            return OrdinaryQualification.fivePeak(rank, stage, stageShifted, tenExtremes, apertureLost);
        }
    }

    /** Reads the upstream lifespan value without assigning units or applying ascension rules. */
    public Snapshot inspect(ServerPlayer player) {
        var data = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        return new Snapshot(data.zhuanshu, data.jieduan, data.shouyuan, data.renqi, data.kongqiao,
                data.tizhi, data.BenMingGu != null && !data.BenMingGu.isEmpty(),
                Double.compare(data.kongqiao, 3.0) == 0, data.shiqiao,
                player.hasEffect(GuzhenrenModMobEffects.JIU_CHONG_JIN_LIAN_ZHEN_YUAN),
                nonNegativeOrInfinity(data.zhenyuan), nonNegativeOrInfinity(data.zuida_zhenyuan));
    }

    /** A copy for inspection; the upstream mirrored stack is never mutated. */
    public ItemStack vitalGu(ServerPlayer player) {
        var data = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        return data.BenMingGu == null ? ItemStack.EMPTY : data.BenMingGu.copy();
    }

    /** Returns the normalized content fingerprint of the live upstream BenMingGu mirror. */
    public Optional<String> vitalGuFingerprint(ServerPlayer player) {
        ItemStack stack = vitalGu(player);
        if (stack.isEmpty()) return Optional.empty();
        try {
            return Optional.of(BenMingGuFingerprint.of(stack, player.registryAccess()));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    /**
     * Human-qi reserve is a bounded game mapping of audited personal fields. It never reads
     * renqi, mingsheng or meilizhi. The weighting is deliberately not presented as canon.
     */
    public double initialHumanQiReserve(ServerPlayer player) {
        var data = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        double soul = ratio(data.hunpo, data.zuida_hunpo);
        double physique = Math.min(1.0, finiteNonNegative(data.tizhi) / 100.0);
        double luck = ratio(data.qiyun, data.qiyun_shangxian);
        return 60.0 + 30.0 * soul + 20.0 * physique + 10.0 * luck;
    }

    /** Reads the raw audited field and refuses malformed values instead of normalizing them. */
    public double currentTrueEssence(ServerPlayer player) throws IOException {
        double value = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES).zhenyuan;
        boolean infinite = Double.isInfinite(value) && value > 0;
        if ((!Double.isFinite(value) && !infinite) || value < 0
                || (!infinite && value > net.guzhenren.shengxian.core.AscensionProgress.MAX_QI)) {
            throw new IOException("Upstream true essence is outside the verified transaction range");
        }
        return value;
    }

    /** Immortal true essence is represented by positive infinity, not a finite refill. */
    public boolean refillImmortalTrueEssence(ServerPlayer player) {
        var data = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        if (Double.isInfinite(data.zhenyuan) && data.zhenyuan > 0
                && Double.isInfinite(data.zuida_zhenyuan) && data.zuida_zhenyuan > 0) {
            return false;
        }
        data.zhenyuan = Double.POSITIVE_INFINITY;
        data.zuida_zhenyuan = Double.POSITIVE_INFINITY;
        data.markSyncDirty();
        return true;
    }

    /** Reads the single durable receipt; malformed data is an error, never an empty transaction. */
    public Optional<ShatterEssenceTransaction.Receipt> shatterEssenceReceipt(ServerPlayer player)
            throws IOException {
        return SavedShatterEssenceReceipt.read(player.getPersistentData());
    }

    /** Mutates only when the exact before value still matches the saved PREPARED intent. */
    public void applyTrueEssenceTarget(ServerPlayer player, ShatterEssenceTransaction.Receipt prepared)
            throws IOException {
        if (prepared == null || prepared.stage() != ShatterEssenceTransaction.Stage.PREPARED) {
            throw new IOException("Invalid true-essence debit values");
        }
        double expectedBefore = prepared.before();
        double target = prepared.target();
        if (SavedShatterEssenceReceipt.read(player.getPersistentData()).filter(prepared::equals).isEmpty()) {
            throw new IOException("Matching PREPARED receipt is not present in player data");
        }
        var data = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        if (!Double.isFinite(data.zhenyuan) || Double.compare(data.zhenyuan, expectedBefore) != 0) {
            throw new IOException("Upstream true essence changed after PREPARED was saved");
        }
        data.zhenyuan = target;
        data.markSyncDirty();
    }

    /** Saves and reads back both player data and the singleplayer owner's loading source. */
    public void persistShatterEssenceReceipt(ServerPlayer player,
                                             ShatterEssenceTransaction.Receipt receipt,
                                             Double expectedEssence) throws IOException {
        Optional<ShatterEssenceTransaction.Receipt> previous =
                SavedShatterEssenceReceipt.read(player.getPersistentData());
        // Flush first: the base mod recomputes zhenyuan continuously, so the on-disk copy lags the
        // live value. Comparing the stale file against the live baseline rejected every attempt
        // and left the shatter transaction unable to commit. Writing the current state first makes
        // the comparison below meaningful (file == memory == expected) without weakening it.
        player.server.getPlayerList().saveAll();
        verifyShatterPreWriteSources(player, previous, receipt, expectedEssence);
        SavedShatterEssenceReceipt.record(player.getPersistentData(), receipt);
        player.server.getPlayerList().saveAll();
        Path file = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                .resolve(player.getStringUUID() + ".dat");
        verifyShatterPlayerFile(file, player.getUUID(), receipt, expectedEssence);
        if (player.server.isSingleplayerOwner(player.getGameProfile())) {
            player.server.saveAllChunks(true, false, true);
            Path levelFile = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.LEVEL_DATA_FILE);
            verifyShatterHostFile(levelFile, player.getUUID(), receipt, expectedEssence);
        }
    }

    /** Refuses to normalize a player-file/host-file mismatch while advancing a receipt. */
    private void verifyShatterPreWriteSources(ServerPlayer player,
                                              Optional<ShatterEssenceTransaction.Receipt> previous,
                                              ShatterEssenceTransaction.Receipt next,
                                              Double expectedEssence) throws IOException {
        Path playerFile = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                .resolve(player.getStringUUID() + ".dat");
        SavedShatterEssenceReceipt.FileState savedPlayer =
                SavedShatterEssenceReceipt.readState(playerFile, player.getUUID(), false);
        verifyPreviousShatterSource(savedPlayer, previous, next, expectedEssence);
        if (player.server.isSingleplayerOwner(player.getGameProfile())) {
            Path levelFile = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.LEVEL_DATA_FILE);
            SavedShatterEssenceReceipt.FileState savedHost =
                    SavedShatterEssenceReceipt.readState(levelFile, player.getUUID(), true);
            verifyPreviousShatterSource(savedHost, previous, next, expectedEssence);
            if (Double.compare(savedPlayer.trueEssence(), savedHost.trueEssence()) != 0) {
                throw new IOException("Player file and singleplayer host true essence disagree before receipt update");
            }
        }
    }

    private static void verifyPreviousShatterSource(SavedShatterEssenceReceipt.FileState source,
                                                    Optional<ShatterEssenceTransaction.Receipt> previous,
                                                    ShatterEssenceTransaction.Receipt next,
                                                    Double expectedEssence) throws IOException {
        if (!source.receipt().equals(previous)) {
            throw new IOException("Saved shatter receipt changed before its next transaction stage");
        }
        if (previous.isEmpty()) {
            if (next.stage() == ShatterEssenceTransaction.Stage.COMMITTED) {
                throw new IOException("COMMITTED cannot replace an absent saved receipt");
            }
            if (expectedEssence != null && Double.compare(source.trueEssence(), expectedEssence) != 0) {
                throw new IOException("Saved true essence changed before PREPARED was recorded");
            }
            return;
        }
        ShatterEssenceTransaction.Receipt prior = previous.get();
        if (prior.stage() == ShatterEssenceTransaction.Stage.PREPARED
                && next.stage() == ShatterEssenceTransaction.Stage.COMMITTED) {
            if (Double.compare(source.trueEssence(), prior.before()) != 0
                    && Double.compare(source.trueEssence(), prior.target()) != 0) {
                throw new IOException("Saved true essence is neither the prepared baseline nor its target");
            }
            return;
        }
        if (expectedEssence != null && Double.compare(source.trueEssence(), expectedEssence) != 0) {
            throw new IOException("Saved true essence changed before receipt update");
        }
    }

    /** Read-only confirmation of both persisted sources before recovery performs another write. */
    public void verifyShatterEssence(ServerPlayer player,
                                     ShatterEssenceTransaction.Receipt receipt,
                                     Double expectedEssence) throws IOException {
        Path file = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                .resolve(player.getStringUUID() + ".dat");
        verifyShatterPlayerFile(file, player.getUUID(), receipt, expectedEssence);
        if (player.server.isSingleplayerOwner(player.getGameProfile())) {
            Path levelFile = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.LEVEL_DATA_FILE);
            verifyShatterHostFile(levelFile, player.getUUID(), receipt, expectedEssence);
        }
    }

    private static void verifyShatterPlayerFile(Path file, java.util.UUID owner,
                                                ShatterEssenceTransaction.Receipt receipt,
                                                Double expectedEssence) throws IOException {
        if (expectedEssence == null) SavedShatterEssenceReceipt.verifyState(file, owner, receipt);
        else SavedShatterEssenceReceipt.verify(file, owner, receipt, expectedEssence);
    }

    private static void verifyShatterHostFile(Path file, java.util.UUID owner,
                                              ShatterEssenceTransaction.Receipt receipt,
                                              Double expectedEssence) throws IOException {
        if (expectedEssence == null) SavedShatterEssenceReceipt.verifyHostState(file, owner, receipt);
        else SavedShatterEssenceReceipt.verifyHost(file, owner, receipt, expectedEssence);
    }

    /** Verifies an already committed receipt without comparing against later legitimate zhenyuan changes. */
    public void verifyShatterEssenceState(ServerPlayer player,
                                          ShatterEssenceTransaction.Receipt receipt) throws IOException {
        var file = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                .resolve(player.getStringUUID() + ".dat");
        SavedShatterEssenceReceipt.verifyState(file, player.getUUID(), receipt);
        if (player.server.isSingleplayerOwner(player.getGameProfile())) {
            SavedShatterEssenceReceipt.verifyHostState(
                    player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.LEVEL_DATA_FILE),
                    player.getUUID(), receipt);
        }
    }

    /** Idempotent repair after the world session has already committed IMMORTAL. */
    public void applyOrdinaryImmortalRank(ServerPlayer player) {
        var data = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        if (data.zhuanshu >= 6.0) return;
        if (Double.compare(data.zhuanshu, 5.0) != 0) {
            throw new IllegalStateException("Upstream rank changed during ascension");
        }
        data.zhuanshu = 6.0;
        data.jieduan = OrdinaryQualification.sixInitialStage(
                player.hasEffect(GuzhenrenModMobEffects.JIU_CHONG_JIN_LIAN_ZHEN_YUAN));
        data.markSyncDirty();
    }

    public boolean rankProjectionRecorded(ServerPlayer player,java.util.UUID session) {
        return SavedRankProjection.hasReceipt(player.getPersistentData(),session);
    }

    /** Saves and confirms both standalone player data and the singleplayer owner's loading source. */
    public void persistRankProjection(ServerPlayer player,java.util.UUID session) throws java.io.IOException {
        Snapshot projection=inspect(player);
        if(!Double.isFinite(projection.rank()) || projection.rank()<6 || !Double.isFinite(projection.stage()))
            throw new java.io.IOException("Upstream immortal projection is not confirmed");
        SavedRankProjection.recordReceipt(player.getPersistentData(),session);
        player.server.getPlayerList().saveAll();
        var file=player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                .resolve(player.getStringUUID()+".dat");
        SavedRankProjection.verify(file,player.getUUID(),projection.rank(),projection.stage(),session);
        if(player.server.isSingleplayerOwner(player.getGameProfile())) {
            player.server.saveAllChunks(true,false,true);
            SavedRankProjection.verifyHost(
                    player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.LEVEL_DATA_FILE),
                    player.getUUID(),projection.rank(),projection.stage(),session);
        }
        Snapshot after=inspect(player);
        if(Double.compare(after.rank(),projection.rank())!=0 || Double.compare(after.stage(),projection.stage())!=0)
            throw new java.io.IOException("Upstream projection changed during player persistence");
    }
    public void persistFailureReceipt(ServerPlayer player, java.util.UUID operationId,
                                      net.guzhenren.shengxian.core.AscensionProgress.FailureCause cause,
                                      SavedFailureReceipt.Stage stage) throws java.io.IOException {
        var data = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        data.shiqiao = true;
        data.markSyncDirty();
        SavedFailureReceipt.record(player.getPersistentData(), operationId, cause, stage);
        player.server.getPlayerList().saveAll();
        var file = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                .resolve(player.getStringUUID() + ".dat");
        SavedFailureReceipt.verify(file, player.getUUID(), operationId, cause, stage);
        if (player.server.isSingleplayerOwner(player.getGameProfile())) {
            player.server.saveAllChunks(true, false, true);
            SavedFailureReceipt.verifyHost(
                    player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.LEVEL_DATA_FILE),
                    player.getUUID(), operationId, cause, stage);
        }
        if (!inspect(player).apertureLost()) throw new java.io.IOException("Shattered-aperture flag changed during save");
    }

    /** Saves and verifies an independent impact receipt without touching the qi receipt key. */
    public void persistTribulationImpactReceipt(ServerPlayer player,
                                                SavedTribulationImpactReceipt.Receipt receipt) throws IOException {
        SavedTribulationImpactReceipt.record(player.getPersistentData(), receipt);
        player.server.getPlayerList().saveAll();
        var file = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                .resolve(player.getStringUUID() + ".dat");
        SavedTribulationImpactReceipt.verify(file, player.getUUID(), receipt);
        if (player.server.isSingleplayerOwner(player.getGameProfile())) {
            player.server.saveAllChunks(true, false, true);
            SavedTribulationImpactReceipt.verifyHost(
                    player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.LEVEL_DATA_FILE),
                    player.getUUID(), receipt);
        }
        if (!inspect(player).apertureLost()) throw new IOException("Shattered-aperture flag changed during impact save");
    }

    /** Builds the existing cause-mapped source; the lifecycle service owns its one attempted application. */
    public net.minecraft.world.damagesource.DamageSource failureDamageSource(ServerPlayer player,
                                        net.guzhenren.shengxian.core.AscensionProgress.FailureCause cause) {
        if (!AscensionReadiness.mayApplyFailure(AscensionReadiness.evaluate(capabilities()), Set.of(cause))) {
            throw new IllegalStateException("Ascension failure consequence is not enabled for this canon-backed cause");
        }
        return switch (cause) {
            case HUMAN_OVERLOAD -> player.damageSources().explosion(null, null);
            case HEAVEN_OVERLOAD -> player.damageSources().fellOutOfWorld();
            case EARTH_OVERLOAD -> player.damageSources().inWall();
            default -> throw new IllegalArgumentException("Unsupported failure cause: " + cause);
        };
    }

    /** Separate damage source for named-profile impacts; never maps an impact to a qi overload. */
    public DamageSource tribulationImpactDamageSource(ServerPlayer player) {
        if (!AscensionReadiness.evaluate(capabilities()).available()) {
            throw new IllegalStateException("Tribulation impact consequence is not enabled while readiness is closed");
        }
        return player.damageSources().magic();
    }

    private static double ratio(double value, double maximum) {
        double v = finiteNonNegative(value);
        double m = finiteNonNegative(maximum);
        if (m <= 0.0) return Math.min(1.0, v / 100.0);
        return Math.min(1.0, v / m);
    }

    private static double nonNegativeOrInfinity(double value) {
        if (Double.isInfinite(value) && value > 0) return Double.POSITIVE_INFINITY;
        return finiteNonNegative(value);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}