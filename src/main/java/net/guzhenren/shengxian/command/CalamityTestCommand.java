package net.guzhenren.shengxian.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.guzhenren.shengxian.server.TribulationWorldService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Ownerless calamity carriers for isolated attack testing.
 *
 * <p>They need no ascension session, so the warning window, the strike and the damage roll can be
 * exercised on their own. They never touch committed ascension state and are removed by clear.</p>
 */
public final class CalamityTestCommand {
    private CalamityTestCommand() {}

    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("shengxiantest")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("clear").executes(context -> clear(context.getSource())))
                .then(Commands.literal("committed")
                        .then(Commands.literal("tiger").executes(x -> committed(x.getSource(), AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER)))
                        .then(Commands.literal("chimes").executes(x -> committed(x.getSource(), AscensionProgress.Tribulation.Type.SOUND_CHIMES)))
                        .then(Commands.literal("beast").executes(x -> committed(x.getSource(), AscensionProgress.Tribulation.Type.ANCIENT_BEAST))))
                .then(Commands.literal("mob")
                        .then(Commands.literal("jade_willow").executes(x -> mob(x.getSource(), AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND)))
                        .then(Commands.literal("tiger").executes(x -> mob(x.getSource(), AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER)))
                        .then(Commands.literal("chimes").executes(x -> mob(x.getSource(), AscensionProgress.Tribulation.Type.SOUND_CHIMES)))
                        .then(Commands.literal("beast").executes(x -> mob(x.getSource(), AscensionProgress.Tribulation.Type.ANCIENT_BEAST)))
                        .then(Commands.literal("blood").executes(x -> mob(x.getSource(), AscensionProgress.Tribulation.Type.BLOOD_RAIN))))
                .then(Commands.literal("hit")
                        .executes(context -> hit(context.getSource(), -1.0))
                        .then(Commands.argument("damage", com.mojang.brigadier.arguments.DoubleArgumentType
                                        .doubleArg(0.5, 1_000_000.0))
                                .executes(context -> hit(context.getSource(), com.mojang.brigadier.arguments
                                        .DoubleArgumentType.getDouble(context, "damage")))));
        add(root, "jade_willow", AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND);
        add(root, "tiger", AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER);
        add(root, "chimes", AscensionProgress.Tribulation.Type.SOUND_CHIMES);
        add(root, "beast", AscensionProgress.Tribulation.Type.ANCIENT_BEAST);
        add(root, "blood", AscensionProgress.Tribulation.Type.BLOOD_RAIN);
        event.getDispatcher().register(root);
    }

    private static void add(LiteralArgumentBuilder<CommandSourceStack> root, String name,
                            AscensionProgress.Tribulation.Type type) {
        root.then(Commands.literal(name).executes(context -> spawn(context.getSource(), type)));
    }

    private static int spawn(CommandSourceStack source, AscensionProgress.Tribulation.Type type) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("只能由玩家执行"));
            return 0;
        }
        if (TribulationWorldService.spawnTestCarrier(player, type) == null) {
            source.sendFailure(Component.literal("测试载体生成失败"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已生成测试载体：先地面预警，再造成伤害。clear 可清除。"), false);
        return 1;
    }

    private static int committed(CommandSourceStack source, AscensionProgress.Tribulation.Type type) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("只能由玩家执行"));
            return 0;
        }
        String result = TribulationWorldService.spawnCommittedTestCarrier(player, type);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int mob(CommandSourceStack source, AscensionProgress.Tribulation.Type type) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("只能由玩家执行"));
            return 0;
        }
        String result = TribulationWorldService.spawnMobCarrier(player, type);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    /** Test hook: applies the player's attack damage to nearby test carriers. */
    private static int hit(CommandSourceStack source, double damage) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("只能由玩家执行"));
            return 0;
        }
        double effective = damage > 0 ? damage
                : player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        String report = TribulationWorldService.hitTestCarriers(player, effective);
        source.sendSuccess(() -> Component.literal("造成 " + Math.round(effective) + " 点载体伤害: "
                + (report.isEmpty() ? "附近没有测试载体" : report)), false);
        return report.isEmpty() ? 0 : 1;
    }

    private static int clear(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("只能由玩家执行"));
            return 0;
        }
        int removed = TribulationWorldService.clearTestCarriers(player);
        source.sendSuccess(() -> Component.literal("已清除 " + removed + " 个测试载体。"), false);
        return removed;
    }
}
