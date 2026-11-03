package net.guzhenren.shengxian.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.InsightCatalog;
import net.guzhenren.shengxian.core.QiParameters;
import net.guzhenren.shengxian.core.SessionStore;
import net.guzhenren.shengxian.server.AscensionLifecycleService;
import net.guzhenren.shengxian.server.AscensionServer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;

/** Fixed, server-validated insight choices; player text is never treated as a knowledge query. */
public final class InsightCommand {
    private InsightCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("shengxianinsight")
                .then(Commands.literal("list").executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                    var readiness = AscensionLifecycleService.readiness();
                    if (!readiness.available()) {
                        context.getSource().sendFailure(Component.literal(readiness.message()));
                        return 0;
                    }
                    SessionStore store = AscensionServer.store();
                    if (store == null) {
                        context.getSource().sendFailure(Component.literal("升仙会话仓库未加载"));
                        return 0;
                    }
                    try {
                        var record = store.load(new ApertureKey(player.getUUID(), "first"));
                        var available = InsightCatalog.available(record.progress(),
                                QiParameters.standard().windowTicksRequired());
                        if (available.isEmpty()) {
                            context.getSource().sendFailure(Component.literal("当前没有可用的师法自然领悟；需处于持续交感窗口并有剩余额度"));
                            return 0;
                        }
                        for (var entry : available) {
                            context.getSource().sendSuccess(() -> Component.literal(entry.id() + " — "
                                    + entry.title() + "（消耗 " + entry.cost() + "）"), false);
                        }
                        return available.size();
                    } catch (IOException ex) {
                        context.getSource().sendFailure(Component.literal("读取升仙记录失败：" + ex.getMessage()));
                        return 0;
                    }
                }))
                .then(Commands.argument("id", StringArgumentType.word()).executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                    var readiness = AscensionLifecycleService.readiness();
                    if (!readiness.available()) {
                        context.getSource().sendFailure(Component.literal(readiness.message()));
                        return 0;
                    }
                    SessionStore store = AscensionServer.store();
                    if (store == null) {
                        context.getSource().sendFailure(Component.literal("升仙会话仓库未加载"));
                        return 0;
                    }
                    ApertureKey key = new ApertureKey(player.getUUID(), "first");
                    try {
                        var current = store.load(key);
                        long tick = player.serverLevel().getGameTime();
                        long sequence = Math.max(current.session().lastActionSequence() + 1, tick);
                        var outcome = store.seekInsight(key, current.session().stateRevision(), sequence,
                                tick, StringArgumentType.getString(context, "id"),
                                QiParameters.standard().windowTicksRequired());
                        context.getSource().sendSuccess(() -> Component.literal(outcome.entry().title()
                                + "：" + outcome.entry().summary()), false);
                        return 1;
                    } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                        context.getSource().sendFailure(Component.literal("无法领悟：" + ex.getMessage()));
                        return 0;
                    }
                })));
    }
}