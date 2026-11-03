package net.guzhenren.shengxian.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.server.ApertureSpace;
import net.guzhenren.shengxian.server.AscensionServer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Owner-managed guest list for one formed aperture. */
public final class ApertureGuestCommand {
    private ApertureGuestCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("shengxianinvite")
                .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer owner)) {
                        context.getSource().sendFailure(Component.literal("只能由玩家执行"));
                        return 0;
                    }
                    if (!ShengxianConfig.allowGuests()) {
                        context.getSource().sendFailure(Component.literal("服务器配置已关闭仙窍来访"));
                        return 0;
                    }
                    ServerPlayer guest = EntityArgument.getPlayer(context, "player");
                    if (guest.getUUID().equals(owner.getUUID())) {
                        context.getSource().sendFailure(Component.literal("不能邀请自己"));
                        return 0;
                    }
                    try {
                        var aperture = AscensionServer.apertureContext();
                        if (aperture == null) throw new IllegalStateException("仙窍仓库未加载");
                        var record = aperture.store().load(new ApertureKey(owner.getUUID(), "first"));
                        if (!record.progress().aperture().formed()) throw new IllegalStateException("仙窍未形成");
                        int plot = record.progress().aperture().plotIndex();
                        aperture.spaceStore().addGuest(plot, guest.getUUID());
                        context.getSource().sendSuccess(() -> Component.literal(
                                "已邀请 " + guest.getGameProfile().getName() + " 进入仙窍地块 " + plot), false);
                        return 1;
                    } catch (Exception ex) {
                        context.getSource().sendFailure(Component.literal("邀请失败：" + ex.getMessage()));
                        return 0;
                    }
                })));

        event.getDispatcher().register(Commands.literal("shengxianguardian")
                .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer owner)) {
                        context.getSource().sendFailure(Component.literal("只能由玩家执行"));
                        return 0;
                    }
                    if (!ShengxianConfig.SPEC.isLoaded() || !ShengxianConfig.allowGuardians()) {
                        context.getSource().sendFailure(Component.literal("服务器配置未加载或已关闭护道人"));
                        return 0;
                    }
                    ServerPlayer guardian = EntityArgument.getPlayer(context, "player");
                    if (guardian.getUUID().equals(owner.getUUID())) {
                        context.getSource().sendFailure(Component.literal("不能指定自己为护道人"));
                        return 0;
                    }
                    try {
                        var aperture = AscensionServer.apertureContext();
                        if (aperture == null) throw new IllegalStateException("仙窍仓库未加载");
                        var record = aperture.store().load(new ApertureKey(owner.getUUID(), "first"));
                        var space = aperture.spaceStore();
                        int plot = record.progress().aperture().formed()
                                ? record.progress().aperture().plotIndex() : -1;
                        space.addGuardianForAscension(owner.getUUID(), plot, guardian.getUUID());
                        context.getSource().sendSuccess(() -> Component.literal(
                                "已指定 " + guardian.getGameProfile().getName() + " 为护道人"), false);
                        return 1;
                    } catch (Exception ex) {
                        context.getSource().sendFailure(Component.literal("护道指定失败：" + ex.getMessage()));
                        return 0;
                    }
                })));

        event.getDispatcher().register(Commands.literal("shengxianrevoke")
                .then(Commands.argument("player", StringArgumentType.word()).executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer owner)) {
                        context.getSource().sendFailure(Component.literal("只能由玩家执行"));
                        return 0;
                    }
                    String guestName = StringArgumentType.getString(context, "player");
                    try {
                        var aperture = AscensionServer.apertureContext();
                        if (aperture == null) throw new IllegalStateException("仙窍仓库未加载");
                        var record = aperture.store().load(new ApertureKey(owner.getUUID(), "first"));
                        int plot = record.progress().aperture().plotIndex();
                        aperture.spaceStore().removeGuest(plot, resolvePlayer(context, guestName));
                        context.getSource().sendSuccess(() -> Component.literal(
                                "已撤销 " + guestName + " 的仙窍来访权限"), false);
                        return 1;
                    } catch (Exception ex) {
                        context.getSource().sendFailure(Component.literal("撤销失败：" + ex.getMessage()));
                        return 0;
                    }
                })));

        event.getDispatcher().register(Commands.literal("shengxianguardianrevoke")
                .then(Commands.argument("player", StringArgumentType.word()).executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer owner)) {
                        context.getSource().sendFailure(Component.literal("只能由玩家执行"));
                        return 0;
                    }
                    String name = StringArgumentType.getString(context, "player");
                    try {
                        var aperture = AscensionServer.apertureContext();
                        if (aperture == null) throw new IllegalStateException("仙窍仓库未加载");
                        var record = aperture.store().load(new ApertureKey(owner.getUUID(), "first"));
                        var space = aperture.spaceStore();
                        var guardianId = resolvePlayer(context, name);
                        int plot = record.progress().aperture().formed()
                                ? record.progress().aperture().plotIndex() : -1;
                        space.removeGuardianForAscension(owner.getUUID(), plot, guardianId);
                        context.getSource().sendSuccess(() -> Component.literal(
                                "已撤销 " + name + " 的护道权限"), false);
                        return 1;
                    } catch (Exception ex) {
                        context.getSource().sendFailure(Component.literal("撤销失败：" + ex.getMessage()));
                        return 0;
                    }
                })));

        event.getDispatcher().register(Commands.literal("shengxianvisit")
                .then(Commands.argument("owner", StringArgumentType.word()).executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer guest)) {
                        context.getSource().sendFailure(Component.literal("只能由玩家执行"));
                        return 0;
                    }
                    String ownerName = StringArgumentType.getString(context, "owner");
                    try {
                        ServerPlayer online = context.getSource().getServer().getPlayerList()
                                .getPlayerByName(ownerName);
                        java.util.UUID ownerId = online != null ? online.getUUID()
                                : resolvePlayer(context.getSource().getServer(), ownerName);
                        ApertureKey key = new ApertureKey(ownerId, "first");
                        var aperture = AscensionServer.apertureContext();
                        if (aperture == null) throw new IllegalStateException("仙窍仓库未加载");
                        var record = aperture.store().load(key);
                        int plot = record.progress().aperture().plotIndex();
                        if (!aperture.spaceStore().isGuest(plot, guest.getUUID())
                                && !aperture.spaceStore().isGuardian(plot, guest.getUUID())) {
                            throw new IllegalStateException("没有该仙窍的来访权限");
                        }
                        ApertureSpace.enter(guest, key);
                        context.getSource().sendSuccess(() -> Component.literal("已进入仙窍来访"), false);
                        return 1;
                    } catch (Exception ex) {
                        context.getSource().sendFailure(Component.literal("来访失败：" + ex.getMessage()));
                        return 0;
                    }
                })));
    }

    private static java.util.UUID resolvePlayer(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
            String name) {
        return resolvePlayer(context.getSource().getServer(), name);
    }

    private static java.util.UUID resolvePlayer(net.minecraft.server.MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        var profiles = server.getProfileCache();
        if (profiles == null) throw new IllegalStateException("离线玩家资料缓存不可用，请让该玩家上线后再操作");
        return profiles.get(name)
                .orElseThrow(() -> new IllegalStateException("找不到玩家 " + name))
                .getId();
    }
}