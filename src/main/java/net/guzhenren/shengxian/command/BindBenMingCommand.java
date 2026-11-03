package net.guzhenren.shengxian.command;

import net.guzhenren.shengxian.compat.BenMingGuCustody;
import net.guzhenren.shengxian.compat.BenMingGuFingerprint;
import net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.BenMingGuStore;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.core.SessionStore;
import net.guzhenren.shengxian.server.AscensionLifecycleService;
import net.guzhenren.shengxian.server.AscensionServer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;

/** Binds a verified mirror and inventory item before an ascension session is committed. */
public final class BindBenMingCommand {
    private BindBenMingCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("shengxianbind").executes(context -> {
            var source = context.getSource();
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.literal("只能由玩家执行"));
                return 0;
            }
            var readiness = AscensionLifecycleService.readiness();
            if (!readiness.available()) {
                source.sendFailure(Component.literal(readiness.message()));
                return 0;
            }            BenMingGuStore bindings = AscensionServer.benming();
            SessionStore sessions = AscensionServer.store();
            if (bindings == null || sessions == null) {
                source.sendFailure(Component.literal("升仙数据仓库未加载"));
                return 0;
            }
            ItemStack stack = new GuzhenrenPlayerBridge().vitalGu(player);
            if (stack.isEmpty()) {
                source.sendFailure(Component.literal("本体没有记录本命蛊"));
                return 0;
            }
            String name = stack.getHoverName().getString();
            if (name.length() > 128) name = name.substring(0, 128);
            if (name.isBlank()) name = "本命蛊";
            ApertureKey key = new ApertureKey(player.getUUID(), "first");
            try {
                var existing = sessions.find(key);
                if (existing.isPresent() && existing.get().progress().vitalGu().bound()) {
                    var vital = existing.get().progress().vitalGu();
                    if (!BenMingGuCustody.holds(player, vital.fingerprint())) {
                        throw new IOException("现有会话的本命蛊与镜像或背包不一致");
                    }
                    var saved = bindings.find(player.getUUID());
                    if (saved.isPresent() && !saved.get().fingerprint().equals(vital.fingerprint())) {
                        throw new IOException("本命蛊账本与现有会话不一致，记录已保留");
                    }
                    String savedName = vital.name().isBlank() ? name : vital.name();
                    BenMingGuStore.Binding binding = saved.isPresent()
                            ? saved.get() : bindings.bind(player.getUUID(), vital.fingerprint(), savedName);
                    source.sendSuccess(() -> Component.literal("本命蛊已绑定：" + binding.displayName()
                            + " [" + binding.fingerprint().substring(0, 8) + "]"), false);
                    return 1;
                }
                if (existing.isPresent() && (existing.get().session().phase() != Phase.PREPARING
                        || existing.get().session().broken())) {
                    throw new IOException("只能在碎窍前绑定本命蛊");
                }
                String fingerprint = BenMingGuFingerprint.of(stack, player.level().registryAccess());
                if (!BenMingGuCustody.holds(player, fingerprint)) {
                    throw new IOException("背包中找不到与镜像一致的本命蛊物品");
                }
                BenMingGuStore.Binding binding = bindings.bindOrReplaceUncommitted(
                        player.getUUID(), fingerprint, name);
                if (existing.isPresent()) {
                    var record = existing.get();
                    long tick = player.serverLevel().getServer().overworld().getGameTime();
                    long sequence = Math.max(record.session().lastActionSequence() + 1, tick);
                    sessions.bindVitalGu(key, record.session().stateRevision(), sequence, tick,
                            binding.fingerprint(), binding.displayName());
                }
                source.sendSuccess(() -> Component.literal("本命蛊已绑定：" + binding.displayName()
                        + " [" + binding.fingerprint().substring(0, 8) + "]"), false);
                return 1;
            } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                source.sendFailure(Component.literal("本命蛊绑定失败：" + ex.getMessage()));
                return 0;
            }
        }));
    }
}