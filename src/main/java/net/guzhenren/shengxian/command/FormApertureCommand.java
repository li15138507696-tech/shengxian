package net.guzhenren.shengxian.command;

import net.guzhenren.shengxian.compat.BenMingGuCustody;
import net.guzhenren.shengxian.server.AscensionLifecycleService;
import net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge;
import net.guzhenren.shengxian.core.ApertureFormationPlanner;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.ApertureSpaceStore;
import net.guzhenren.shengxian.core.AscensionReadiness;

import net.guzhenren.shengxian.core.BenMingGuStore;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.server.AscensionServer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;

/** Forms the aperture only after the server commits the vital-Gu offering stage. */
public final class FormApertureCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");

    private FormApertureCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("shengxianform").executes(context -> {
            var source = context.getSource();
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.literal("只能由玩家执行"));
                return 0;
            }
            try {
                String message = form(player);
                source.sendSuccess(() -> Component.literal(message), false);
                net.guzhenren.shengxian.server.AscensionLifecycleService.sendSnapshot(player);
                return 1;
            } catch (Exception ex) {
                source.sendFailure(Component.literal("形成失败：" + ex.getMessage()));
                return 0;
            }
        }));
    }

    public static String form(ServerPlayer player) throws IOException {
        var aperture = AscensionServer.apertureContext();
        BenMingGuStore benming = AscensionServer.benming();
        GuzhenrenPlayerBridge bridge = new GuzhenrenPlayerBridge();
        if (aperture == null || benming == null) throw new IOException("仙窍或本命蛊仓库未加载");
        ApertureKey key = new ApertureKey(player.getUUID(), "first");
        var found = aperture.store().find(key);
        boolean committedFormation = found.filter(record -> record.session().phase() == Phase.IMMORTAL
                && record.progress().aperture().formed()).isPresent();
        var readiness = net.guzhenren.shengxian.server.AscensionLifecycleService.readiness();
        if (!AscensionReadiness.mayAttemptFormation(readiness, committedFormation)) {
            net.guzhenren.shengxian.server.AscensionLifecycleService.requireGameplayReady();
        }
        if (committedFormation) {
            return recoverCommittedFormation(player, key, found.orElseThrow(), benming, aperture.spaceStore());
        }
        var record = found.orElseThrow(() -> new IOException("Session missing"));
        if (record.session().phase() != Phase.FORMING) throw new IOException("当前阶段不能形成仙窍");
        if (!bridge.inspect(player).ordinaryFivePeak()) throw new IOException("本体境界已改变，拒绝写入六转");
        var binding = benming.find(player.getUUID());
        if (binding.isEmpty() || !record.progress().vitalGu().bound()
                || !binding.get().fingerprint().equals(record.progress().vitalGu().fingerprint())) {
            throw new IOException("本命蛊托管记录与本次升仙不一致");
        }
        var escrow = benming.findEscrow(player.getUUID()).orElseThrow(() -> new IOException("本命蛊尚未投入并托管"));
        if (!escrow.fingerprint().equals(binding.get().fingerprint())
                || escrow.state() != BenMingGuStore.EscrowState.STORED) {
            throw new IOException("本命蛊托管事务尚未完成或与会话不一致");
        }

        int plot = aperture.spaceStore().reservePlotForAscension(key, record.session().id());
        boolean committed = false;
        try {
            long tick = player.serverLevel().getServer().overworld().getGameTime();
            long sequence = Math.max(record.session().lastActionSequence() + 1, tick);
            var plan = ApertureFormationPlanner.plan(record.progress(), plot,
                    new Random(player.getUUID().getMostSignificantBits() ^ tick));
            var formed = aperture.store().formAperture(key, record.session().stateRevision(),
                    sequence, tick, plan.areaMu(), plan.timeRatio(), plan.initialEssence(),
                    plan.plotIndex(), binding.get().fingerprint());
            committed = true;
            aperture.spaceStore().commitPlotReservation(plot, key, record.session().id());
            AscensionServer.registerFormedPlot(key, plot);
            // CANON-012: the witnessed example brews a second calamity inside the new aperture.
            try {
                AscensionLifecycleService.brewInnerTribulation(player, key);
            } catch (IOException | RuntimeException ex) {
                LOGGER.warn("Aperture formed but the inner calamity could not be committed for {}",
                        player.getUUID(), ex);
            }
            try {
                aperture.spaceStore().reconcileAscensionGuardians(player.getUUID(), plot);
            } catch (IOException | RuntimeException ex) {
                LOGGER.warn("Aperture formed but prior ascension guardian plot permissions remain pending for {}",
                        player.getUUID(), ex);
            }
            String rankResult;
            try {
                rankResult = net.guzhenren.shengxian.server.AscensionLifecycleService.reconcileRankSix(player);
                if (rankResult == null) rankResult = "六转同步待恢复。";
            } catch (IOException | RuntimeException ex) {
                LOGGER.warn("Aperture formed but rank-six sync remains pending for {}", player.getUUID(), ex);
                rankResult = "六转同步待恢复（下次登录自动重试）。";
            }
            boolean embedded;
            try {
                embedded = benming.markEmbedded(player.getUUID()).state()
                        == BenMingGuStore.EscrowState.EMBEDDED;
            } catch (IOException | RuntimeException ex) {
                LOGGER.warn("Aperture formed but the BenMingGu embed acknowledgement remains pending for {}",
                        player.getUUID(), ex);
                embedded = false;
            }
            return "仙窍已成：" + plan.grade()
                    + " 面积=" + formed.progress().aperture().areaMu()
                    + " 亩，时间比=" + formed.progress().aperture().timeRatio()
                    + "，青提仙元=" + formed.progress().aperture().essence()
                    + "，地块=" + formed.progress().aperture().plotIndex()
                    + "；" + rankResult + (embedded
                    ? "本命蛊已随仙窍形成记入托管；未伪造通用返还或升炼结果。"
                    : "本命蛊成窍托管确认待恢复；原物仍保留在托管账本中。")
                    + "可执行 /shengxian aperture enter。";
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Retaining aperture plot {} for owner {} after formation failure (session committed={}); reconciliation required",
                    plot, player.getUUID(), committed, ex);
            throw ex;
        }
    }

    private static String recoverCommittedFormation(ServerPlayer player, ApertureKey key,
                                                     net.guzhenren.shengxian.core.AscensionRecord record,
                                                     BenMingGuStore benming, ApertureSpaceStore spaceStore) throws IOException {
        int plot = record.progress().aperture().plotIndex();
        if (spaceStore.reservation(plot).isPresent()) {
            spaceStore.commitPlotReservation(plot, key, record.session().id());
        }
        AscensionServer.registerFormedPlot(key, plot);
        try {
            AscensionServer.apertureContext().spaceStore().reconcileAscensionGuardians(
                    player.getUUID(), record.progress().aperture().plotIndex());
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Committed aperture recovery left prior guardian permissions pending for {}",
                    player.getUUID(), ex);
        }
        String rankResult;
        try {
            rankResult = net.guzhenren.shengxian.server.AscensionLifecycleService.reconcileRankSix(player);
            if (rankResult == null) rankResult = "六转同步已确认。";
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Committed aperture recovery left rank-six sync pending for {}", player.getUUID(), ex);
            rankResult = "六转同步待恢复（下次登录自动重试）。";
        }
        String guResult = resolveCommittedGu(player, benming, record);
        var apertureState = record.progress().aperture();
        return "仙窍已成：面积=" + apertureState.areaMu() + " 亩，时间比=" + apertureState.timeRatio()
                + "，青提仙元=" + apertureState.essence() + "，地块=" + apertureState.plotIndex()
                + "；" + rankResult + guResult + "可执行 /shengxian aperture enter。";
    }

    private static String resolveCommittedGu(ServerPlayer player, BenMingGuStore store,
                                             net.guzhenren.shengxian.core.AscensionRecord record) {
        var escrow = store.findEscrow(player.getUUID());
        if (escrow.isEmpty()) return "该旧记录没有可恢复的本命蛊托管副本。";
        try {
            switch (escrow.get().state()) {
                case EMBEDDED -> { return "本命蛊已随仙窍形成保存在托管账本；未伪造通用返还或升炼结果。"; }
                case STORED -> {
                    store.markEmbedded(player.getUUID());
                    return "本命蛊已随仙窍形成保存在托管账本；未伪造通用返还或升炼结果。";
                }
                case OFFER_PENDING -> {
                    var vital = record.progress().vitalGu();
                    if (!vital.bound() || !BenMingGuCustody.ensureOffered(player, store,
                            vital.fingerprint(), vital.name())) {
                        return "本命蛊投入对账待恢复；托管原物保留。";
                    }
                    store.markEmbedded(player.getUUID());
                    return "已恢复本命蛊投入记录；原著未给出通用返还/升炼映射，原物继续托管。";
                }
                case RETURN_PENDING, RETURNED -> {
                    boolean restored = BenMingGuCustody.restoreAfterFormation(player, store);
                    return restored ? "已完成此前版本已开始的本命蛊返还事务。"
                            : "此前版本的本命蛊返还仍在托管中；背包满时原物不会丢失。";
                }
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Committed aperture recovery could not reconcile BenMingGu for {}", player.getUUID(), ex);
            return "本命蛊成窍托管待恢复；原物仍保留在托管账本中。";
        }
        return "本命蛊状态未知；托管原物保留。";
    }
}
