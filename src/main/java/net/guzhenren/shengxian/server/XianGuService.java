package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.ApertureEngine;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.AscensionRecord;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.core.SessionStore;
import net.guzhenren.shengxian.core.TribulationEngine;
import net.guzhenren.shengxian.core.XianGu;
import net.guzhenren.shengxian.core.XianGuCatalog;
import net.guzhenren.shengxian.core.XianGuForcedUse;
import net.guzhenren.shengxian.core.XianGuLedger;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Server-owned immortal gu actions.
 *
 * <p>The original destructive Pai Nan path remains a witnessed case. The three approved
 * reusable Gu dispatch to bound physical items and the separate persistent debit service.
 * Refining and world-wide uniqueness remain outside this implementation.</p>
 */
public final class XianGuService {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    /** A single use removes the whole carrier; the engine still clamps at zero. */
    private static final int STRIKE_UNITS = 1_000_000;

    public enum Outcome {
        USED(true, "排难之光击退了灾劫载体；该仙蛊已损毁。"),
        /** CANON-009 黑楼兰个例：仙元不足仍强催，仙蛊自爆，双目留下不可复原的道痕。 */
        FORCED(true, "仙元不足，你强行催动：排难之光击退了灾劫载体，仙蛊当场自爆，双目爆裂留下无法复原的道痕。"),
        ACTIVATED(true, "仙蛊请求已由服务器处理。"),
        GRANTED(true, "已将该仙蛊交给目标。"),
        GRANTED_PENDING(true, "持有权已登记，背包满时请腾出位置后使用 /shengxiangu recover 补交。"),
        ALREADY_HELD(false, "目标已持有该仙蛊。"),
        NOT_HELD(false, "你没有持有该仙蛊。"),
        NO_CARRIER(false, "当前没有可击退的灾劫载体。"),
        NO_APERTURE(false, "仙窍尚未形成，无法支出仙元。"),
        INSUFFICIENT_ESSENCE(false, "青提仙元不足。"),
        /** Only the witnessed 排难 case is forceable; reusable Gu have their own debit path. */
        NOT_FORCEABLE(false, "只有个例损毁型的仙蛊可以强行催动；可反复催动的仙蛊请用普通催动。"),
        /** CANON-013 方正个例：余气已把本命血道凡蛊炼成第二只仙蛊。 */
        REFINED(true, "残余天地二气把本命血道凡蛊升炼成了第二只血道仙蛊（原文未给名称与效果）。"),
        NOTHING_TO_REFINE(false, "当前没有可升炼的余气，或这次余气已经用过了。"),
        NOT_ABSORBING(false, "只有纳气阶段才能催动仙蛊。"),
        SESSION_MISSING(false, "目标没有升仙会话。"),
        UNKNOWN_GU(false, "未知仙蛊。"),
        STORE_UNAVAILABLE(false, "升仙会话仓库未加载。"),
        /** The callee already told the player the specific reason; the command must stay silent. */
        REJECTED(false, ""),
        ERROR(false, "仙蛊操作失败，记录已保留。");

        private final boolean success;
        private final String message;

        Outcome(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean success() { return success; }
        public String message() { return message; }
    }

    private XianGuService() {}

    /** Destroys one active carrier with the held immortal gu and consumes both gu and essence. */
    public static Outcome use(ServerPlayer player, String guId) {
        XianGu gu = XianGuCatalog.find(guId).orElse(null);
        if (gu == null) return Outcome.UNKNOWN_GU;
        if (gu.reusable()) {
            var hand = player.getMainHandItem().getItem() instanceof net.guzhenren.shengxian.item.ImmortalGuItem item
                    && item.guId().equals(guId) ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND;
            return XianGuUseService.activate(player, guId, player.isShiftKeyDown(), hand, false)
                    ? Outcome.ACTIVATED : Outcome.REJECTED;
        }
        SessionStore store = AscensionServer.store();
        if (store == null) return Outcome.STORE_UNAVAILABLE;
        ApertureKey key = new ApertureKey(player.getUUID(), "first");
        try {
            AscensionRecord record = store.find(key).orElse(null);
            if (record == null) return Outcome.SESSION_MISSING;
            if (record.session().phase() != Phase.ABSORBING) return Outcome.NOT_ABSORBING;
            var progress = record.progress();
            if (!XianGuLedger.holds(progress, gu.id())) return Outcome.NOT_HELD;
            if (!progress.aperture().formed()) return Outcome.NO_APERTURE;
            if (progress.tribulations().isEmpty()) return Outcome.NO_CARRIER;
            if (progress.aperture().essence() < gu.essenceCost()) return Outcome.INSUFFICIENT_ESSENCE;
            String carrierId = progress.tribulations().get(0).id();
            long tick = player.server.overworld().getGameTime();
            long sequence = Math.max(record.session().lastActionSequence() + 1L, tick);
            AscensionRecord updated = store.mutate(key, record.session().stateRevision(), sequence, tick,
                    current -> XianGuLedger.consume(
                            TribulationEngine.strike(
                                    ApertureEngine.spendEssence(current, gu.essenceCost()),
                                    carrierId, STRIKE_UNITS).progress(),
                            gu));
            TribulationWorldService.synchronize(player, record, updated);
            AscensionLifecycleService.sendSnapshot(player);
            return Outcome.USED;
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            LOGGER.warn("xian gu use was rejected for {}", player.getUUID(), ex);
            return Outcome.ERROR;
        }
    }

    /**
     * CANON-009 individual case: forcing the strike when the aperture cannot pay.
     *
     * <p>The witnessed scene still destroys one carrier per activation, so the strike is granted;
     * the shortfall is paid in a permanent 道痕 mark plus a burst of blindness. A completely empty
     * aperture has nothing to ignite and is rejected instead of becoming a free action.</p>
     */
    public static Outcome useForced(ServerPlayer player, String guId) {
        XianGu gu = XianGuCatalog.find(guId).orElse(null);
        if (gu == null) return Outcome.UNKNOWN_GU;
        if (gu.reusable()) return Outcome.NOT_FORCEABLE;
        SessionStore store = AscensionServer.store();
        if (store == null) return Outcome.STORE_UNAVAILABLE;
        ApertureKey key = new ApertureKey(player.getUUID(), "first");
        try {
            AscensionRecord record = store.find(key).orElse(null);
            if (record == null) return Outcome.SESSION_MISSING;
            if (record.session().phase() != Phase.ABSORBING) return Outcome.NOT_ABSORBING;
            var progress = record.progress();
            if (!XianGuLedger.holds(progress, gu.id())) return Outcome.NOT_HELD;
            if (!progress.aperture().formed()) return Outcome.NO_APERTURE;
            if (progress.tribulations().isEmpty()) return Outcome.NO_CARRIER;
            if (progress.aperture().essence() >= gu.essenceCost()) return Outcome.ACTIVATED;
            if (!(progress.aperture().essence() > 0)) return Outcome.INSUFFICIENT_ESSENCE;
            String carrierId = progress.tribulations().get(0).id();
            long tick = player.server.overworld().getGameTime();
            long sequence = Math.max(record.session().lastActionSequence() + 1L, tick);
            AscensionRecord updated = store.mutate(key, record.session().stateRevision(), sequence, tick,
                    current -> XianGuForcedUse.force(current, gu, carrierId, STRIKE_UNITS).progress());
            TribulationWorldService.synchronize(player, record, updated);
            applyDaoTao(player);
            AscensionLifecycleService.sendSnapshot(player);
            return Outcome.FORCED;
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            LOGGER.warn("forced immortal gu use was rejected for {}", player.getUUID(), ex);
            return Outcome.ERROR;
        }
    }

    /**
     * CANON-013 individual case: leftover heaven/earth qi refines the vital blood-dao mortal Gu
     * into a second blood-dao immortal Gu. This is the module's only non-operator acquisition
     * path, and the produced Gu is recorded as UNSPECIFIED because the text names no effect.
     */
    public static Outcome refineLeftover(ServerPlayer player) {
        SessionStore store = AscensionServer.store();
        if (store == null) return Outcome.STORE_UNAVAILABLE;
        ApertureKey key = new ApertureKey(player.getUUID(), "first");
        try {
            AscensionRecord record = store.find(key).orElse(null);
            if (record == null) return Outcome.SESSION_MISSING;
            if (record.session().phase() != Phase.IMMORTAL) return Outcome.NOT_ABSORBING;
            var result = net.guzhenren.shengxian.core.LeftoverRefinement.refine(record.progress(),
                    net.guzhenren.shengxian.core.TribulationParameters
                            .forProfile(ShengxianConfig.tribulationProfile()));
            if (!result.refined()) return Outcome.NOTHING_TO_REFINE;
            long tick = player.server.overworld().getGameTime();
            long sequence = Math.max(record.session().lastActionSequence() + 1L, tick);
            store.mutate(key, record.session().stateRevision(), sequence, tick, current -> result.progress());
            AscensionLifecycleService.sendSnapshot(player);
            return Outcome.REFINED;
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            LOGGER.warn("leftover-qi refinement was rejected for {}", player.getUUID(), ex);
            return Outcome.ERROR;
        }
    }

    /**
     * Game mapping of the burst: the text gives permanent 道痕 in the sockets, not a duration.
     * A finite blindness burst keeps the consequence visible without making the character blind.
     */
    public static void applyDaoTao(ServerPlayer player) {
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.BLINDNESS, 200, 0, false, true, true));
    }

    /** Operator grant; refining rules are unverified, so this is the only acquisition path. */
    public static Outcome grant(ServerPlayer target, String guId) {
        XianGu gu = XianGuCatalog.find(guId).orElse(null);
        if (gu == null) return Outcome.UNKNOWN_GU;
        SessionStore store = AscensionServer.store();
        if (store == null) return Outcome.STORE_UNAVAILABLE;
        ApertureKey key = new ApertureKey(target.getUUID(), "first");
        try {
            AscensionRecord record = store.find(key).orElse(null);
            if (record == null) return Outcome.SESSION_MISSING;
            if (gu.reusable() && (record.session().phase() != Phase.IMMORTAL || !record.progress().aperture().formed())) {
                return Outcome.NO_APERTURE;
            }
            boolean alreadyHeld = XianGuLedger.holds(record.progress(), gu.id());
            long tick = target.server.overworld().getGameTime();
            long sequence = Math.max(record.session().lastActionSequence() + 1L, tick);
            AscensionRecord granted = store.mutate(key, record.session().stateRevision(), sequence, tick,
                    current -> XianGuLedger.hold(current, gu));
            AscensionLifecycleService.sendSnapshot(target);
            if (gu.reusable()) {
                // ALREADY_HELD must still deliver: the ledger says owned, but the bound item can
                // be lost to a full inventory or a clear, and only this path can re-issue it.
                boolean delivered = XianGuItemBinding.deliver(target, alreadyHeld ? record : granted, gu.id());
                target.server.getPlayerList().saveAll();
                if (alreadyHeld) return delivered ? Outcome.ALREADY_HELD : Outcome.GRANTED_PENDING;
                return delivered ? Outcome.GRANTED : Outcome.GRANTED_PENDING;
            }
            if (alreadyHeld) return Outcome.ALREADY_HELD;
            return Outcome.GRANTED;
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            LOGGER.warn("xian gu grant was rejected for {}", target.getUUID(), ex);
            return Outcome.ERROR;
        }
    }
}
