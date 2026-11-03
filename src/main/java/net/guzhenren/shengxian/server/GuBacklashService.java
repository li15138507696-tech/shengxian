package net.guzhenren.shengxian.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.compat.BenMingGuFingerprint;
import net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge;
import net.guzhenren.shengxian.compat.SavedGuBacklash;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.GuActivation;
import net.guzhenren.shengxian.core.GuBacklashState;
import net.guzhenren.shengxian.core.GuBacklashTargetLocator;
import net.guzhenren.shengxian.core.GuActivationPolicy;
import net.guzhenren.shengxian.core.Phase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Applies the narrowly scoped, persistent Gu-backlash game mapping on the server thread. */
public final class GuBacklashService {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final Set<UUID> RUNTIME_QUARANTINE = ConcurrentHashMap.newKeySet();

    public enum TargetKind { INVENTORY, KILLER_MOVE }

    public record TargetRef(TargetKind kind, int group, int slot) {
        public TargetRef {
            if (kind == null) throw new IllegalArgumentException("Missing Gu target kind");
            if (kind == TargetKind.INVENTORY && (group != 0 || slot != 0)) {
                throw new IllegalArgumentException("Inventory target does not carry a killer-move slot");
            }
            if (kind == TargetKind.KILLER_MOVE && (group < 1 || group > 4 || slot < 1 || slot > 10)) {
                throw new IllegalArgumentException("Invalid killer-move Gu slot");
            }
        }

        public static TargetRef inventory() { return new TargetRef(TargetKind.INVENTORY, 0, 0); }
        public static TargetRef killerMove(int group, int slot) {
            return new TargetRef(TargetKind.KILLER_MOVE, group, slot);
        }
    }

    private record ResolvedTarget(String locator, ItemStack stack, boolean upstreamVariable) {}

    private GuBacklashService() {}

    /** Persists one completion event; called only after the observer has deduplicated its source. */
    public static void completed(ServerPlayer player, UUID sessionId, GuActivation activation,
                                  ItemStack usedStack, TargetRef targetRef) {
        if (player == null || sessionId == null || activation == null || usedStack == null
                || usedStack == ItemStack.EMPTY || targetRef == null || player.level().isClientSide()
                || RUNTIME_QUARANTINE.contains(player.getUUID())) return;
        try {
            var store = AscensionServer.store();
            if (store == null) return;
            var record = store.find(new ApertureKey(player.getUUID(), "first")).orElse(null);
            if (record == null || !record.session().id().equals(sessionId)
                    || !GuActivationPolicy.mayApplyBacklash(record.session().phase(), activation)) return;

            CompoundTag persistentData = player.getPersistentData();
            SavedGuBacklash.Stored stored = null;
            if (SavedGuBacklash.present(persistentData)) {
                stored = SavedGuBacklash.read(persistentData).orElse(null);
                if (stored == null) {
                    quarantine(player, null, null, "malformed-player-state");
                    return;
                }
                if (stored.transaction() != null && stored.transaction().stage() != SavedGuBacklash.Stage.COMMITTED
                        || stored.state().quarantined()) {
                    RUNTIME_QUARANTINE.add(player.getUUID());
                    return;
                }
            }

            GuBacklashState current = stored == null ? null : stored.state();
            int threshold = current != null && current.sessionId().equals(sessionId)
                    ? current.threshold() : ShengxianConfig.guBacklashThreshold();
            boolean thresholdDue = current == null || !current.sessionId().equals(sessionId)
                    ? threshold <= 1 : current.pressure() + 1 >= current.threshold();
            ResolvedTarget resolved = thresholdDue ? resolve(player, usedStack, targetRef) : null;
            VitalIdentity vital = thresholdDue ? vitalIdentity(player) : VitalIdentity.unknown();
            boolean targetIdentityConfirmed = !thresholdDue || resolved != null && vital.confirmed();
            boolean vitalProtected = thresholdDue && resolved != null && vital.confirmed()
                    && fingerprint(resolved.stack(), player).equals(vital.fingerprint());

            GuBacklashState.Transition transition = GuBacklashState.observe(current, sessionId,
                    record.session().phase(), ShengxianConfig.guBacklashThreshold(), activation,
                    targetIdentityConfirmed, vitalProtected);
            if (transition.decision() == GuBacklashState.Decision.IGNORED_PHASE
                    || transition.decision() == GuBacklashState.Decision.IGNORED_UNCONFIRMED
                    || transition.decision() == GuBacklashState.Decision.DUPLICATE
                    || transition.decision() == GuBacklashState.Decision.QUARANTINED) return;

            GuBacklashState next = transition.state();
            SavedGuBacklash.Transaction receipt = stored == null
                    || !stored.state().sessionId().equals(sessionId) ? null : stored.transaction();
            if (transition.decision() == GuBacklashState.Decision.LOSS_DUE) {
                if (resolved == null || !vital.confirmed()) {
                    next = next.quarantine("target-identity-lost");
                    quarantine(player, next, receipt, "target-identity-lost");
                    return;
                }
                String itemId = itemId(resolved.stack());
                String fingerprint = fingerprint(resolved.stack(), player);
                int before = resolved.stack().getCount();
                receipt = new SavedGuBacklash.Transaction(sessionId, next.sequence(), activation.key(),
                        GuBacklashState.Decision.LOSS_DUE, SavedGuBacklash.Stage.PREPARED,
                        resolved.locator(), itemId, fingerprint, before, before - 1);
                persistAndVerify(player, next, receipt, true);
                resolved.stack().shrink(1);
                markChanged(player, resolved);
                SavedGuBacklash.Transaction committed = receipt.withStage(SavedGuBacklash.Stage.COMMITTED);
                persistAndVerify(player, next, committed, true);
                RUNTIME_QUARANTINE.remove(player.getUUID());
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "蛊虫反噬：纳气期催动累积达到阈值，损失了触发本次催动的一只非本命蛊。"));
                return;
            }

            if (transition.decision() == GuBacklashState.Decision.VITAL_PROTECTED && resolved != null) {
                receipt = new SavedGuBacklash.Transaction(sessionId, next.sequence(), activation.key(),
                        GuBacklashState.Decision.VITAL_PROTECTED, SavedGuBacklash.Stage.COMMITTED,
                        resolved.locator(), itemId(resolved.stack()), fingerprint(resolved.stack(), player),
                        resolved.stack().getCount(), resolved.stack().getCount());
            } else if (transition.decision() == GuBacklashState.Decision.TARGET_UNAVAILABLE) {
                receipt = new SavedGuBacklash.Transaction(sessionId, next.sequence(), activation.key(),
                        GuBacklashState.Decision.TARGET_UNAVAILABLE, SavedGuBacklash.Stage.COMMITTED,
                        "", "", "", -1, -1);
            }
            persistAndVerify(player, next, receipt, receipt != null
                    && receipt.decision() == GuBacklashState.Decision.VITAL_PROTECTED);
        } catch (Exception exception) {
            quarantine(player, null, null, "save-or-target-uncertain");
            LOGGER.warn("Could not safely apply Gu backlash for {}; item loss will not be retried",
                    player.getUUID(), exception);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "蛊反噬存档核验未完成；已隔离本次损失，不会自动重试。"));
        }
    }

    /** Cross-checks player data and, for a local owner, level.dat before new pressure is accepted. */
    public static void reconcileOnLogin(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!SavedGuBacklash.present(persistentData)) return;
        SavedGuBacklash.Stored stored = SavedGuBacklash.read(persistentData).orElse(null);
        if (stored == null) {
            RUNTIME_QUARANTINE.add(player.getUUID());
            LOGGER.warn("Malformed Gu backlash state for {}; source remains quarantined", player.getUUID());
            return;
        }
        try {
            SavedGuBacklash.Transaction transaction = stored.transaction();
            if (stored.state().quarantined() || transaction != null
                    && transaction.stage() != SavedGuBacklash.Stage.COMMITTED) {
                quarantine(player, stored.state(), transaction, "unfinished-transaction-recovery");
                return;
            }
            verifyPlayerAndHostState(player, stored.state(), transaction);
            RUNTIME_QUARANTINE.remove(player.getUUID());
        } catch (IOException exception) {
            RUNTIME_QUARANTINE.add(player.getUUID());
            LOGGER.warn("Gu backlash player/host data disagree for {}; no further loss will be attempted",
                    player.getUUID(), exception);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "蛊反噬玩家与世界存档不一致；已隔离，不会重放损失。"));
        }
    }

    public static void clear() { RUNTIME_QUARANTINE.clear(); }

    private static void persistAndVerify(ServerPlayer player, GuBacklashState state,
                                         SavedGuBacklash.Transaction transaction,
                                         boolean verifyTarget) throws IOException {
        SavedGuBacklash.record(player.getPersistentData(), state, transaction);
        SavedGuBacklash.Stored expected = SavedGuBacklash.read(player.getPersistentData())
                .orElseThrow(() -> new IOException("Gu backlash state was not readable after write"));
        player.server.getPlayerList().saveAll();
        Path playerFile = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(player.getStringUUID() + ".dat");
        verifySavedFile(playerFile, player.getUUID(), expected.state(), expected.transaction(), verifyTarget);
        if (player.server.isSingleplayerOwner(player.getGameProfile())) {
            player.server.saveAllChunks(true, false, true);
            Path hostFile = player.server.getWorldPath(LevelResource.LEVEL_DATA_FILE);
            verifySavedHost(hostFile, player.getUUID(), expected.state(), expected.transaction(), verifyTarget);
        }
    }

    private static void verifyPlayerAndHostState(ServerPlayer player, GuBacklashState state,
                                                  SavedGuBacklash.Transaction transaction) throws IOException {
        Path playerFile = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(player.getStringUUID() + ".dat");
        SavedGuBacklash.verifyState(playerFile, player.getUUID(), state, transaction);
        if (player.server.isSingleplayerOwner(player.getGameProfile())) {
            SavedGuBacklash.verifyHostState(
                    player.server.getWorldPath(LevelResource.LEVEL_DATA_FILE), player.getUUID(), state, transaction);
        }
    }

    private static void verifySavedFile(Path file, UUID owner, GuBacklashState state,
                                       SavedGuBacklash.Transaction transaction,
                                       boolean verifyTarget) throws IOException {
        if (verifyTarget) SavedGuBacklash.verify(file, owner, state, transaction);
        else SavedGuBacklash.verifyState(file, owner, state, transaction);
    }

    private static void verifySavedHost(Path file, UUID owner, GuBacklashState state,
                                        SavedGuBacklash.Transaction transaction,
                                        boolean verifyTarget) throws IOException {
        if (verifyTarget) SavedGuBacklash.verifyHost(file, owner, state, transaction);
        else SavedGuBacklash.verifyHostState(file, owner, state, transaction);
    }

    private static void quarantine(ServerPlayer player, GuBacklashState state,
                                   SavedGuBacklash.Transaction transaction, String reason) {
        RUNTIME_QUARANTINE.add(player.getUUID());
        try {
            GuBacklashState current = state;
            SavedGuBacklash.Stored stored = SavedGuBacklash.read(player.getPersistentData()).orElse(null);
            if (current == null && stored != null) current = stored.state();
            if (current == null) return;
            current = current.quarantine(reason);
            SavedGuBacklash.Transaction receipt = transaction;
            if (receipt == null && stored != null) receipt = stored.transaction();
            if (receipt != null && receipt.stage() == SavedGuBacklash.Stage.PREPARED) {
                receipt = receipt.withStage(SavedGuBacklash.Stage.UNCERTAIN);
            }
            SavedGuBacklash.record(player.getPersistentData(), current, receipt);
            player.server.getPlayerList().saveAll();
            Path playerFile = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                    .resolve(player.getStringUUID() + ".dat");
            SavedGuBacklash.verifyState(playerFile, player.getUUID(), current, receipt);
            if (player.server.isSingleplayerOwner(player.getGameProfile())) {
                player.server.saveAllChunks(true, false, true);
                SavedGuBacklash.verifyHostState(
                        player.server.getWorldPath(LevelResource.LEVEL_DATA_FILE), player.getUUID(), current, receipt);
            }
        } catch (Exception quarantineFailure) {
            LOGGER.warn("Could not persist Gu backlash quarantine for {}; runtime quarantine remains active",
                    player.getUUID(), quarantineFailure);
        }
    }

    private static ResolvedTarget resolve(ServerPlayer player, ItemStack used, TargetRef ref) {
        if (used.isEmpty()) return null;
        if (ref.kind() == TargetKind.KILLER_MOVE) {
            PlayerVariables variables = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
            if (variables == null) return null;
            ItemStack stack = killerMoveSlots(variables)[ref.group() - 1][ref.slot() - 1];
            if (stack == null || stack.isEmpty() || stack != used) return null;
            return new ResolvedTarget("killer:" + killerMoveField(ref.group(), ref.slot()), stack, true);
        }
        Inventory inventory = player.getInventory();
        var slot = GuBacklashTargetLocator.inventorySlot(used,
                inventory.items, inventory.armor, inventory.offhand);
        return slot.isPresent() ? new ResolvedTarget("inventory:" + slot.getAsInt(), used, false) : null;
    }

    private static void markChanged(ServerPlayer player, ResolvedTarget target) {
        if (target.upstreamVariable()) {
            PlayerVariables variables = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
            if (variables != null) variables.markSyncDirty();
        } else {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    private record VitalIdentity(boolean confirmed, String fingerprint) {
        private static VitalIdentity unknown() { return new VitalIdentity(false, ""); }
    }

    private static VitalIdentity vitalIdentity(ServerPlayer player) throws IOException {
        var binding = AscensionServer.benming();
        if (binding == null) return VitalIdentity.unknown();
        var saved = binding.find(player.getUUID()).orElse(null);
        if (saved == null) return VitalIdentity.unknown();
        String live = new GuzhenrenPlayerBridge().vitalGuFingerprint(player).orElse("");
        if (live.isEmpty() || !saved.fingerprint().equals(live)) return VitalIdentity.unknown();
        return new VitalIdentity(true, live);
    }

    private static String fingerprint(ItemStack stack, ServerPlayer player) {
        return BenMingGuFingerprint.of(stack, player.registryAccess());
    }

    private static String itemId(ItemStack stack) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) throw new IllegalStateException("Gu item has no registry key");
        return key.toString();
    }

    private static String killerMoveField(int group, int slot) {
        return GuBacklashTargetLocator.killerMoveField(group, slot);
    }

    private static ItemStack[][] killerMoveSlots(PlayerVariables variables) {
        return new ItemStack[][]{
                {variables.ShaZhao1_GuChong1, variables.ShaZhao1_GuChong2, variables.ShaZhao1_GuChong3,
                        variables.ShaZhao1_GuChong4, variables.ShaZhao1_GuChong5, variables.ShaZhao1_GuChong6,
                        variables.ShaZhao1_GuChong7, variables.ShaZhao1_GuChong8, variables.ShaZhao1_GuChong9,
                        variables.ShaZhao1_GuChong10},
                {variables.ShaZhao2_GuChong1, variables.ShaZhao2_GuChong2, variables.ShaZhao2_GuChong3,
                        variables.ShaZhao2_GuChong4, variables.ShaZhao2_GuChong5, variables.ShaZhao2_GuChong6,
                        variables.ShaZhao2_GuChong7, variables.ShaZhao2_GuChong8, variables.ShaZhao2_GuChong9,
                        variables.ShaZhao2_GuChong10},
                {variables.ShaZhao3_GuChong1, variables.ShaZhao3_GuChong2, variables.ShaZhao3_GuChong3,
                        variables.ShaZhao3_GuChong4, variables.ShaZhao3_GuChong5, variables.ShaZhao3_GuChong6,
                        variables.ShaZhao3_GuChong7, variables.ShaZhao3_GuChong8, variables.ShaZhao3_GuChong9,
                        variables.ShaZhao3_GuChong10},
                {variables.ShaZhao4_GuChong1, variables.ShaZhao4_GuChong2, variables.ShaZhao4_GuChong3,
                        variables.ShaZhao4_GuChong4, variables.ShaZhao4_GuChong5, variables.ShaZhao4_GuChong6,
                        variables.ShaZhao4_GuChong7, variables.ShaZhao4_GuChong8, variables.ShaZhao4_GuChong9,
                        variables.ShaZhao4_GuChong10}
        };
    }
}
