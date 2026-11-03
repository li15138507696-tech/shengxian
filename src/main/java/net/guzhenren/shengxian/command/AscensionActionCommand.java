package net.guzhenren.shengxian.command;

import net.guzhenren.shengxian.server.AscensionLifecycleService;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Explicit command access to the same validated server actions exposed by the screen. */
public final class AscensionActionCommand {
    private AscensionActionCommand() {}

    public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("shengxian")
                .then(action("prepare", AscensionLifecycleService.ACTION_PREPARE))
                .then(action("shatter", AscensionLifecycleService.ACTION_SHATTER))
                .then(action("cancel", AscensionLifecycleService.ACTION_CANCEL))
                .then(action("open", AscensionLifecycleService.ACTION_OPEN))
                .then(action("offer", AscensionLifecycleService.ACTION_OFFER_GU))
                .then(action("form", AscensionLifecycleService.ACTION_FORM))
                .then(Commands.literal("sync")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                                    try {
                                        String message = AscensionLifecycleService.syncRank(target);
                                        context.getSource().sendSuccess(() -> Component.literal(message), false);
                                        target.sendSystemMessage(Component.literal(message));
                                        AscensionLifecycleService.sendSnapshot(target);
                                        return 1;
                                    } catch (Exception ex) {
                                        context.getSource().sendFailure(Component.literal("六转修复失败：" + ex.getMessage()));
                                        return 0;
                                    }
                                })));
        event.getDispatcher().register(root);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
    action(String name, int action) {
        return Commands.literal(name).executes(context -> {
            if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                context.getSource().sendFailure(Component.literal("只能由玩家执行"));
                return 0;
            }
            AscensionLifecycleService.perform(player, action);
            return 1;
        });
    }
}
