package net.guzhenren.shengxian.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.XianGu;
import net.guzhenren.shengxian.core.XianGuCatalog;
import net.guzhenren.shengxian.core.XianGuLedger;
import net.guzhenren.shengxian.server.AscensionServer;
import net.guzhenren.shengxian.server.XianGuService;
import net.guzhenren.shengxian.server.XianGuUseService;
import net.guzhenren.shengxian.ShengxianConfig;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator grant and player use of the immortal gu this module models. */
public final class XianGuCommand {
    private XianGuCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("shengxiangu")
                .then(Commands.literal("list").executes(context -> {
                    var source = context.getSource();
                    StringBuilder catalog = new StringBuilder("仙蛊目录：");
                    for (XianGu gu : XianGuCatalog.all()) {
                        catalog.append(gu.displayName()).append('(').append(gu.id()).append(", ")
                                .append(gu.reusable() ? ShengxianConfig.immortalGuCost(gu.id()) : gu.essenceCost())
                                .append(" 仙元, ").append(gu.reusable() ? "可反复催动" : "个例损毁").append(") ");
                    }
                    source.sendSuccess(() -> Component.literal(catalog.toString().trim()), false);
                    if (source.getEntity() instanceof ServerPlayer player) {
                        var store = AscensionServer.store();
                        if (store != null) {
                            try {
                                var record = store.find(new ApertureKey(player.getUUID(), "first")).orElse(null);
                                String held = record == null ? "无会话"
                                        : XianGuLedger.held(record.progress()).toString();
                                source.sendSuccess(() -> Component.literal("你持有的仙蛊：" + held), false);
                            } catch (Exception ex) {
                                source.sendFailure(Component.literal("读取仙蛊记录失败：" + ex.getMessage()));
                            }
                        }
                    }
                    source.sendSuccess(() -> Component.literal(
                            "炼成规则未核实，仙蛊只能由管理员发放；唯一性目前只按会话成立。"), false);
                    return 1;
                }))
                .then(Commands.literal("use")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(XianGuCatalog.all().stream().map(XianGu::id), builder))
                                .executes(context -> {
                            var source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("只能由玩家执行"));
                                return 0;
                            }
                            String id = StringArgumentType.getString(context, "id");
                            XianGuService.Outcome outcome = XianGuService.use(player, id);
                            if (outcome.success()) source.sendSuccess(() -> Component.literal(outcome.message()), false);
                            else if (!outcome.message().isEmpty()) source.sendFailure(Component.literal(outcome.message()));
                            return outcome.success() ? 1 : 0;
                        })))
                .then(Commands.literal("refine").executes(context -> {
                    var source = context.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal("只能由玩家执行"));
                        return 0;
                    }
                    XianGuService.Outcome outcome = XianGuService.refineLeftover(player);
                    if (outcome.success()) source.sendSuccess(() -> Component.literal(outcome.message()), false);
                    else if (!outcome.message().isEmpty()) source.sendFailure(Component.literal(outcome.message()));
                    return outcome.success() ? 1 : 0;
                }))
                .then(Commands.literal("force")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        XianGuCatalog.all().stream()
                                                .filter(gu -> gu.effect() == XianGu.Effect.CARRIER_STRIKE)
                                                .map(XianGu::id), builder))
                                .executes(context -> {
                            var source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("只能由玩家执行"));
                                return 0;
                            }
                            String id = StringArgumentType.getString(context, "id");
                            XianGuService.Outcome outcome = XianGuService.useForced(player, id);
                            if (outcome.success()) source.sendSuccess(() -> Component.literal(outcome.message()), false);
                            else if (!outcome.message().isEmpty()) source.sendFailure(Component.literal(outcome.message()));
                            return outcome.success() ? 1 : 0;
                        })))
                .then(Commands.literal("grant")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(XianGuCatalog.all().stream().map(XianGu::id), builder))
                                        .executes(context -> {
                                    var source = context.getSource();
                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                    String id = StringArgumentType.getString(context, "id");
                                    XianGuService.Outcome outcome = XianGuService.grant(target, id);
                                    if (outcome.success()) {
                                        source.sendSuccess(() -> Component.literal(
                                                target.getGameProfile().getName() + "：" + outcome.message()), true);
                                    } else {
                                        source.sendFailure(Component.literal(outcome.message()));
                                    }
                                    return outcome.success() ? 1 : 0;
                                }))))
                .then(Commands.literal("status").executes(context -> {
                    try {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        String status = XianGuUseService.status(player);
                        context.getSource().sendSuccess(() -> Component.literal(status), false);
                        return 1;
                    } catch (Exception failure) {
                        context.getSource().sendFailure(Component.literal(failure.getMessage())); return 0;
                    }
                }))
                .then(Commands.literal("recover").executes(context -> {
                    try {
                        int count = XianGuUseService.recover(context.getSource().getPlayerOrException());
                        context.getSource().sendSuccess(() -> Component.literal("已补交 " + count + " 件仙蛊；背包满时请腾出位置再试。"), false);
                        return 1;
                    } catch (Exception failure) {
                        context.getSource().sendFailure(Component.literal(failure.getMessage())); return 0;
                    }
                }))
                .then(Commands.literal("acknowledge").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("operation", StringArgumentType.word()).executes(context -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                        String result = XianGuUseService.acknowledge(target, java.util.UUID.fromString(
                                                StringArgumentType.getString(context, "operation")));
                                        context.getSource().sendSuccess(() -> Component.literal(result), true); return 1;
                                    } catch (Exception failure) {
                                        context.getSource().sendFailure(Component.literal(failure.getMessage())); return 0;
                                    }
                                })))));
    }
}
