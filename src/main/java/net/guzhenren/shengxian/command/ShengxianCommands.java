package net.guzhenren.shengxian.command;

import net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge;
import net.guzhenren.shengxian.compat.SavedFailureReceipt;
import net.guzhenren.shengxian.compat.SavedTribulationImpactReceipt;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.AscensionRecord;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.guzhenren.shengxian.core.FailureSettlementDiagnostic;
import net.guzhenren.shengxian.core.FailureSettlementIdentity;
import net.guzhenren.shengxian.core.FailureSettlementPolicy;
import net.guzhenren.shengxian.core.QiIntent;
import net.guzhenren.shengxian.core.SessionStore;

import net.guzhenren.shengxian.server.ApertureSpace;
import net.guzhenren.shengxian.server.ApertureTickService;
import net.guzhenren.shengxian.server.AscensionLifecycleService;
import net.guzhenren.shengxian.server.AscensionControlService;
import net.guzhenren.shengxian.server.AscensionFx;
import net.guzhenren.shengxian.server.AscensionServer;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ShengxianCommands {
    private ShengxianCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("shengxian")
                .then(Commands.literal("status").executes(context -> {
                    var source = context.getSource();
                    var bridge = new GuzhenrenPlayerBridge();
                    var readiness = AscensionLifecycleService.readiness();
                    
                    source.sendSuccess(() -> Component.literal(readiness.message()), false);
                    SessionStore store = AscensionServer.store();
                    source.sendSuccess(() -> Component.literal("世界会话仓库："
                            + (store == null ? "未加载（服务器未启动或已停止）" : store.directory().toString())), false);
                    if (source.getEntity() instanceof ServerPlayer player) {
                        var snapshot = bridge.inspect(player);
                        source.sendSuccess(() -> Component.literal("本体原始数据：转数=" + snapshot.rank()
                                + "，阶段编码=" + snapshot.stage() + "，本体 shouyuan 原始值=" + snapshot.upstreamShouyuanRaw()
                                + "，本体派生 renqi（非升仙人气）=" + snapshot.upstreamRenqiDerived()
                                + "，本命蛊=" + (snapshot.hasVitalGu() ? "已记录" : "未记录")
                                + "，真元=" + essence(snapshot.currentTrueEssence()) + "/" + essence(snapshot.maxTrueEssence())), false);
                        source.sendSuccess(() -> Component.literal("普通五转路线资格="
                                + (snapshot.ordinaryFivePeak() && readiness.available() ? "可发起" : "条件不符或门禁关闭")), false);
                        if (store != null) {
                            var key = new ApertureKey(player.getUUID(), "first");
                            try {
                                Optional<AscensionRecord> found = store.find(key);
                                if (found.isEmpty()) {
                                    source.sendSuccess(() -> Component.literal("空窍 first：无会话"), false);
                                } else {
                                    AscensionRecord record = found.get();
                                    source.sendSuccess(() -> Component.literal("空窍 first：阶段="
                                            + record.session().phase()
                                            + "，阶段修订=" + record.session().phaseRevision()
                                            + "，状态修订=" + record.session().stateRevision()
                                            + "，动作序号=" + record.session().lastActionSequence()
                                            + "，时钟=" + record.session().serverTick()
                                            + "，幂等键=" + record.progress().appliedEffects().size()), false);
                                    if (record.session().phase() == net.guzhenren.shengxian.core.Phase.FAILED
                                            && !record.progress().failureCauses().isEmpty()) {
                                        var causes = record.progress().failureCauses();
                                        UUID operation = FailureSettlementIdentity.operationId(record.session().id(),
                                                record.session().phaseRevision());
                                        String settlement;
                                        if (causes.equals(Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT))) {
                                            var savedImpact = SavedTribulationImpactReceipt.read(player.getPersistentData());
                                            var impactReceipt = savedImpact.map(receipt ->
                                                    new FailureSettlementPolicy.TribulationImpactPlayerReceipt(
                                                            receipt.operationId(), receipt.context(),
                                                            receipt.stage() == SavedTribulationImpactReceipt.Stage.STARTED
                                                                    ? FailureSettlementPolicy.ReceiptStage.STARTED
                                                                    : FailureSettlementPolicy.ReceiptStage.RESPAWN_RECEIPT));
                                            settlement = FailureSettlementDiagnostic.formatTribulationImpact(
                                                    record.progress().failureSettlementState(), operation,
                                                    record.progress().tribulationImpactFailure(), impactReceipt,
                                                    SavedTribulationImpactReceipt.present(player.getPersistentData()));
                                        } else {
                                            var cause = causes.size() == 1
                                                    ? Optional.of(causes.iterator().next())
                                                    : Optional.<AscensionProgress.FailureCause>empty();
                                            var savedReceipt = SavedFailureReceipt.read(player.getPersistentData());
                                            var diagnosticReceipt = savedReceipt.map(receipt ->
                                                    new FailureSettlementPolicy.PlayerReceipt(receipt.operationId(),
                                                            receipt.cause(), receipt.stage() == SavedFailureReceipt.Stage.STARTED
                                                            ? FailureSettlementPolicy.ReceiptStage.STARTED
                                                            : FailureSettlementPolicy.ReceiptStage.RESPAWN_RECEIPT));
                                            settlement = FailureSettlementDiagnostic.format(
                                                    record.progress().failureSettlementState(), operation, cause,
                                                    diagnosticReceipt, SavedFailureReceipt.present(player.getPersistentData()));
                                        }
                                        source.sendSuccess(() -> Component.literal(settlement), false);
                                    }
                                }
                            } catch (IOException ex) {
                                source.sendSuccess(() -> Component.literal("空窍 first：读取失败（记录已保留）: "
                                        + ex.getMessage()), false);
                            }
                        }
                    }
                    source.sendSuccess(() -> Component.literal("特殊路线保持关闭；核心领域模拟不代表完整原著流程。"), false);
                    return 1;
                }))
                .then(Commands.literal("aperture")
                        .then(Commands.literal("enter").executes(context -> {
                            var source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("只能由玩家执行"));
                                return 0;
                            }
                            try {
                                ApertureSpace.enter(player, new ApertureKey(player.getUUID(), "first"));
                                source.sendSuccess(() -> Component.literal("已进入仙窍"), false);
                                return 1;
                            } catch (IOException | IllegalArgumentException ex) {
                                source.sendFailure(Component.literal("无法进入仙窍：" + ex.getMessage()));
                                return 0;
                            }
                        }))
                        .then(Commands.literal("leave").executes(context -> {
                            var source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("只能由玩家执行"));
                                return 0;
                            }
                            if (!player.level().dimension().equals(ApertureSpace.LEVEL)) {
                                source.sendFailure(Component.literal("当前不在仙窍空间"));
                                return 0;
                            }
                            ApertureSpace.leave(player);
                            source.sendSuccess(() -> Component.literal("已离开仙窍"), false);
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(context -> {
                            var source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("只能由玩家执行"));
                                return 0;
                            }
                            SessionStore store = AscensionServer.store();
                            if (store == null) {
                                source.sendFailure(Component.literal("世界会话仓库未加载"));
                                return 0;
                            }
                            try {
                                AscensionRecord record = store.load(new ApertureKey(player.getUUID(), "first"));
                                var a = record.progress().aperture();
                                if (!a.formed()) {
                                    source.sendSuccess(() -> Component.literal("仙窍：尚未形成"), false);
                                    return 1;
                                }
                                source.sendSuccess(() -> Component.literal("仙窍：面积=" + a.areaMu()
                                        + " 亩，时间比=" + a.timeRatio() + "，仙元=" + a.essence()
                                        + "，内部时钟=" + a.internalTicks() + "，地块=" + a.plotIndex()), false);
                                var projection = ApertureTickService.projectOnlineStep(a);
                                String advance = projection.internalTicks() == 0
                                        ? "本步没有内部刻进展"
                                        : projection.upkeepCovered() ? "余额足以支付维持" : "余额不足，下一步将停摆";
                                source.sendSuccess(() -> Component.literal("仙元经济（游戏参数）：每内部刻产出="
                                        + projection.productionPerInternalTick() + "，维持="
                                        + projection.upkeepPerInternalTick() + "，净变化="
                                        + projection.netEssencePerInternalTick() + "；单次在线步进（"
                                        + ApertureTickService.ONLINE_STEP_SERVER_TICKS + " 服务端刻）预计内部刻+"
                                        + projection.internalTicks() + "，产出+" + projection.essenceProduction()
                                        + "，维持-" + projection.upkeepCost() + "，净变化="
                                        + projection.netEssence() + "；" + advance), false);
                                return 1;
                            } catch (IOException ex) {
                                source.sendFailure(Component.literal("仙窍读取失败：" + ex.getMessage()));
                                return 0;
                            }
                        }))));

        event.getDispatcher().register(Commands.literal("shengxianqi")
                .then(Commands.literal("stop").executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Component.literal("只能由玩家执行"));
                        return 0;
                    }
                    AscensionControlService.clear(player.getUUID());
                    AscensionFx.send(player.serverLevel(), player.position(), player.getId(), "qi_stop", 0.0f);
                    context.getSource().sendSuccess(() -> Component.literal("已停止主动抽纳；气机和灾劫仍继续推进。"), false);
                    return 1;
                }))
                .then(Commands.argument("heaven", DoubleArgumentType.doubleArg(0, 1))
                        .then(Commands.argument("earth", DoubleArgumentType.doubleArg(0, 1))
                                .then(Commands.argument("human", DoubleArgumentType.doubleArg(0, 1))
                                        .then(Commands.argument("seek", BoolArgumentType.bool()).executes(context -> {
                                            if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                                                context.getSource().sendFailure(Component.literal("只能由玩家执行"));
                                                return 0;
                                            }
                                            double heaven = DoubleArgumentType.getDouble(context, "heaven");
                                            double earth = DoubleArgumentType.getDouble(context, "earth");
                                            double human = DoubleArgumentType.getDouble(context, "human");
                                            boolean seek = BoolArgumentType.getBool(context, "seek");
                                            var result = AscensionControlService.set(player,
                                                    new QiIntent(heaven, earth, human, seek));
                                            if (!result.accepted()) {
                                                context.getSource().sendFailure(Component.literal(result.message()));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    result.message() + "天=" + heaven + " 地=" + earth + " 人=" + human
                                                            + " 求知=" + seek), false);
                                            return 1;
                                        }))))));
        event.getDispatcher().register(Commands.literal("shengxiansim").executes(context -> {
            if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                context.getSource().sendFailure(Component.literal("只能由玩家执行"));
                return 0;
            }
            SessionStore store = AscensionServer.store();
            if (store == null) {
                context.getSource().sendFailure(Component.literal("世界会话仓库未加载"));
                return 0;
            }
            try {
                var record = store.load(new ApertureKey(player.getUUID(), "first"));
                var p = record.progress();
                String tribulationSummary = p.tribulations().isEmpty() ? "无" : p.tribulations().stream()
                        .map(t -> t.type() + "[" + t.id() + "]剩余=" + t.remaining()
                                + "，冲击倒计时=" + t.ticksUntilImpact())
                        .collect(java.util.stream.Collectors.joining("；"));
                context.getSource().sendSuccess(() -> Component.literal("阶段=" + record.session().phase()
                        + " 状态修订=" + record.session().stateRevision()
                        + " 时钟=" + record.session().serverTick()
                        + " 气=" + p.qi().heaven() + "/" + p.qi().earth() + "/" + p.qi().human()
                        + " 外部=" + p.qi().ambientHeaven() + "/" + p.qi().ambientEarth()
                        + " 储备=" + p.qi().humanReserve()
                        + " 风险=" + p.risk().heaven() + "/" + p.risk().earth() + "/" + p.risk().human()
                        + " 窗口=" + p.window().ticks() + "/" + p.window().id()
                        + " 领悟=" + p.insightEarned()
                        + " 灾劫=" + tribulationSummary), false);
                return 1;
            } catch (IOException ex) {
                context.getSource().sendFailure(Component.literal("读取失败：" + ex.getMessage()));
                return 0;
            }
        }));
    }

    private static String essence(double value) {
        return Double.isInfinite(value) && value > 0 ? "∞" : Double.toString(value);
    }
}