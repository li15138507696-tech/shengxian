package net.guzhenren.shengxian.server;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.GuActivation;
import net.guzhenren.shengxian.core.GuActivationLedger;
import net.guzhenren.shengxian.core.GuActivationPolicy;
import net.guzhenren.shengxian.core.KillerMoveDurabilityChanges;
import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Records audited accepted branches during ascent; callers and lifecycle cleanup run on the server thread. */
public final class GuActivationObserver {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final GuActivationLedger LEDGER = GuActivationLedger.standard();
    private static final ThreadLocal<Deque<KillerMoveSnapshot>> KILLER_MOVE_SNAPSHOTS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<StagedActivation>> STAGED_ACTIVATIONS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<UUID, Deque<StagedActivation>> DELAYED_ACTIVATIONS = new HashMap<>();

    private record KillerMoveSnapshot(ServerPlayer owner, char keybind, double[][] durability,
                                      ItemStack[][] sourceStacks) {}
    private record StagedActivation(ServerPlayer owner, ItemStack stack, String guId,
                                    GuActivation.Entry entry, UUID sessionId, long serverTick) {}

    private GuActivationObserver() {}

    /** No backlash or item mutation occurs here; the evidence-backed outcome policy remains closed. */
    public static void accepted(Entity actor, ItemStack stack, GuActivation.Entry entry) {
        if (!(actor instanceof ServerPlayer player) || actor.level().isClientSide()
                || stack == null || stack.isEmpty()) return;
        var store = AscensionServer.store();
        if (store == null) return;
        try {
            var record = store.find(new ApertureKey(player.getUUID(), "first"));
            if (record.isEmpty() || !GuActivationPolicy.mayObserveDuringAscension(record.get().session().phase())) return;
            var guId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (guId == null) return;
            long sequence = LEDGER.nextSequence(player.getUUID());
            GuActivation activation = new GuActivation(player.getUUID(), guId.toString(), entry,
                    GuActivation.Outcome.ACCEPTED, player.serverLevel().getGameTime(), sequence);
            if (GuActivationPolicy.countsAsActivation(activation)) LEDGER.record(activation);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not record accepted Gu activation for {}; ascension state was not changed",
                    player.getUUID(), exception);
        }
    }

    /** Saves exact activation identity at the audited cost point; it does not yet add pressure. */
    public static void stageAccepted(Entity actor, ItemStack stack, GuActivation.Entry entry) {
        StagedActivation staged = capture(actor, stack, entry);
        if (staged != null) STAGED_ACTIVATIONS.get().push(staged);
    }

    /** Holds one cost-confirmed use until its delayed server result is accepted. */
    public static void stageDelayed(Entity actor, ItemStack stack, GuActivation.Entry entry) {
        StagedActivation staged = capture(actor, stack, entry);
        if (staged == null) return;
        DELAYED_ACTIVATIONS.computeIfAbsent(staged.owner().getUUID(), ignored -> new ArrayDeque<>()).addLast(staged);
    }

    /** Completes the most recent matching synchronous use after its result point. */
    public static void completeStaged(Entity actor, ItemStack stack, GuActivation.Entry entry,
                                      GuBacklashService.TargetRef target) {
        StagedActivation staged = removeStaged(actor, stack, entry);
        if (staged != null) recordCompleted(staged, target);
    }

    /** Completion for a redirected upstream call that exposes no enclosing Procedure arguments. */
    public static void completeStaged(GuActivation.Entry entry, GuBacklashService.TargetRef target) {
        StagedActivation staged = removeStaged(entry);
        if (staged != null) recordCompleted(staged, target);
    }

    /** Removes an accepted-cost stage whose observable result was rejected. */
    public static void discardStaged(Entity actor, ItemStack stack, GuActivation.Entry entry) {
        removeStaged(actor, stack, entry);
    }

    public static void discardStaged(GuActivation.Entry entry) {
        removeStaged(entry);
    }

    /** Completes a delayed projectile activation only when its owner has one unambiguous source stack. */
    public static void completeDelayed(Entity projectileOwner, GuActivation.Entry entry) {
        if (!(projectileOwner instanceof ServerPlayer player) || projectileOwner.level().isClientSide()) return;
        Deque<StagedActivation> pending = DELAYED_ACTIVATIONS.get(player.getUUID());
        if (pending == null) return;
        long now = player.serverLevel().getGameTime();
        pending.removeIf(source -> source.owner() != player || source.entry() != entry
                || now < source.serverTick() || now - source.serverTick() > 20L);
        if (pending.isEmpty()) {
            DELAYED_ACTIVATIONS.remove(player.getUUID());
            return;
        }
        ItemStack firstStack = pending.getFirst().stack();
        boolean sameSource = pending.stream().allMatch(source -> source.stack() == firstStack && source.entry() == entry);
        if (!sameSource) {
            LOGGER.debug("Skipping ambiguous delayed Gu completion for {}", player.getUUID());
            return;
        }
        StagedActivation staged = pending.removeFirst();
        if (pending.isEmpty()) DELAYED_ACTIVATIONS.remove(player.getUUID());
        recordCompleted(staged, GuBacklashService.TargetRef.inventory());
    }

    /** A rejected delayed spawn cannot leave a candidate for a later projectile. */
    public static void discardDelayed(Entity projectileOwner, GuActivation.Entry entry) {
        if (!(projectileOwner instanceof ServerPlayer player)) return;
        Deque<StagedActivation> pending = DELAYED_ACTIVATIONS.get(player.getUUID());
        if (pending == null) return;
        pending.removeIf(source -> source.owner() == player && source.entry() == entry);
        if (pending.isEmpty()) DELAYED_ACTIVATIONS.remove(player.getUUID());
    }

    /** Captures all four durability groups before one upstream killer-move procedure runs. */
    public static void beginKillerMoveObservation(Entity actor, char keybind) {
        if (!(actor instanceof ServerPlayer player) || actor.level().isClientSide()) return;
        PlayerVariables variables = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        KILLER_MOVE_SNAPSHOTS.get().push(new KillerMoveSnapshot(player, keybind,
                variables == null ? null : durabilitySnapshot(variables),
                variables == null ? null : killerMoveSlots(variables)));
    }

    /** Records only component Gus whose upstream durability changed during the procedure. */
    public static void endKillerMoveObservation(Entity actor, char keybind) {
        if (!(actor instanceof ServerPlayer player) || actor.level().isClientSide()) return;
        Deque<KillerMoveSnapshot> snapshots = KILLER_MOVE_SNAPSHOTS.get();
        if (snapshots.isEmpty()) {
            KILLER_MOVE_SNAPSHOTS.remove();
            return;
        }
        KillerMoveSnapshot snapshot = snapshots.pop();
        if (snapshots.isEmpty()) KILLER_MOVE_SNAPSHOTS.remove();
        if (snapshot.owner() != player || snapshot.keybind() != keybind || snapshot.durability() == null
                || snapshot.sourceStacks() == null) return;
        PlayerVariables variables = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        if (variables == null) return;
        double[][] after = durabilitySnapshot(variables);
        for (KillerMoveDurabilityChanges.Slot slot : KillerMoveDurabilityChanges.changedSlots(snapshot.durability(), after)) {
            ItemStack stack = snapshot.sourceStacks()[slot.group() - 1][slot.slot() - 1];
            if (stack != null && stack != ItemStack.EMPTY) {
                recordCompleted(player, stack, GuActivation.Entry.KEYBIND,
                        GuBacklashService.TargetRef.killerMove(slot.group(), slot.slot()));
            }
        }
    }

    private static StagedActivation capture(Entity actor, ItemStack stack, GuActivation.Entry entry) {
        if (!(actor instanceof ServerPlayer player) || actor.level().isClientSide()
                || stack == null || stack == ItemStack.EMPTY || entry == null) return null;
        var store = AscensionServer.store();
        if (store == null) return null;
        try {
            var record = store.find(new ApertureKey(player.getUUID(), "first")).orElse(null);
            if (record == null || !GuActivationPolicy.mayObserveDuringAscension(record.session().phase())) return null;
            var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key == null) return null;
            return new StagedActivation(player, stack, key.toString(), entry, record.session().id(),
                    player.serverLevel().getGameTime());
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not stage cost-confirmed Gu activation for {}", player.getUUID(), exception);
            return null;
        }
    }

    private static StagedActivation removeStaged(Entity actor, ItemStack stack, GuActivation.Entry entry) {
        if (!(actor instanceof ServerPlayer player)) return null;
        Deque<StagedActivation> pending = STAGED_ACTIVATIONS.get();
        long now = player.serverLevel().getGameTime();
        pending.removeIf(source -> now < source.serverTick() || now - source.serverTick() > 1L);
        Iterator<StagedActivation> iterator = pending.iterator();
        while (iterator.hasNext()) {
            StagedActivation source = iterator.next();
            if (source.owner() == player && source.stack() == stack && source.entry() == entry) {
                iterator.remove();
                if (pending.isEmpty()) STAGED_ACTIVATIONS.remove();
                return source;
            }
        }
        if (pending.isEmpty()) STAGED_ACTIVATIONS.remove();
        return null;
    }

    private static StagedActivation removeStaged(GuActivation.Entry entry) {
        Deque<StagedActivation> pending = STAGED_ACTIVATIONS.get();
        long now = pending.isEmpty() ? 0L : pending.getFirst().owner().serverLevel().getGameTime();
        pending.removeIf(source -> now < source.serverTick() || now - source.serverTick() > 1L);
        Iterator<StagedActivation> iterator = pending.iterator();
        while (iterator.hasNext()) {
            StagedActivation source = iterator.next();
            if (source.entry() == entry) {
                iterator.remove();
                if (pending.isEmpty()) STAGED_ACTIVATIONS.remove();
                return source;
            }
        }
        if (pending.isEmpty()) STAGED_ACTIVATIONS.remove();
        return null;
    }

    private static void recordCompleted(Entity actor, ItemStack stack, GuActivation.Entry entry,
                                        GuBacklashService.TargetRef target) {
        StagedActivation source = capture(actor, stack, entry);
        if (source != null) recordCompleted(source, target);
    }

    private static void recordCompleted(StagedActivation source, GuBacklashService.TargetRef target) {
        ServerPlayer player = source.owner();
        try {
            var store = AscensionServer.store();
            if (store == null) return;
            var record = store.find(new ApertureKey(player.getUUID(), "first")).orElse(null);
            if (record == null || !record.session().id().equals(source.sessionId())
                    || !GuActivationPolicy.mayObserveDuringAscension(record.session().phase())) return;
            long sequence = LEDGER.nextSequence(player.getUUID());
            GuActivation activation = new GuActivation(player.getUUID(), source.guId(), source.entry(),
                    GuActivation.Outcome.COMPLETED, player.serverLevel().getGameTime(), sequence);
            if (!GuActivationPolicy.countsAsActivation(activation) || !LEDGER.record(activation)) return;
            if (GuActivationPolicy.mayApplyBacklash(record.session().phase(), activation)) {
                GuBacklashService.completed(player, source.sessionId(), activation, source.stack(), target);
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not record completed Gu activation for {}", player.getUUID(), exception);
        }
    }

    private static double[][] durabilitySnapshot(PlayerVariables variables) {
        return new double[][]{
                {variables.ShaZhao1_GuChong1_NaiJiu, variables.ShaZhao1_GuChong2_NaiJiu,
                        variables.ShaZhao1_GuChong3_NaiJiu, variables.ShaZhao1_GuChong4_NaiJiu,
                        variables.ShaZhao1_GuChong5_NaiJiu, variables.ShaZhao1_GuChong6_NaiJiu,
                        variables.ShaZhao1_GuChong7_NaiJiu, variables.ShaZhao1_GuChong8_NaiJiu,
                        variables.ShaZhao1_GuChong9_NaiJiu, variables.ShaZhao1_GuChong10_NaiJiu},
                {variables.ShaZhao2_GuChong1_NaiJiu, variables.ShaZhao2_GuChong2_NaiJiu,
                        variables.ShaZhao2_GuChong3_NaiJiu, variables.ShaZhao2_GuChong4_NaiJiu,
                        variables.ShaZhao2_GuChong5_NaiJiu, variables.ShaZhao2_GuChong6_NaiJiu,
                        variables.ShaZhao2_GuChong7_NaiJiu, variables.ShaZhao2_GuChong8_NaiJiu,
                        variables.ShaZhao2_GuChong9_NaiJiu, variables.ShaZhao2_GuChong10_NaiJiu},
                {variables.ShaZhao3_GuChong1_NaiJiu, variables.ShaZhao3_GuChong2_NaiJiu,
                        variables.ShaZhao3_GuChong3_NaiJiu, variables.ShaZhao3_GuChong4_NaiJiu,
                        variables.ShaZhao3_GuChong5_NaiJiu, variables.ShaZhao3_GuChong6_NaiJiu,
                        variables.ShaZhao3_GuChong7_NaiJiu, variables.ShaZhao3_GuChong8_NaiJiu,
                        variables.ShaZhao3_GuChong9_NaiJiu, variables.ShaZhao3_GuChong10_NaiJiu},
                {variables.ShaZhao4_GuChong1_NaiJiu, variables.ShaZhao4_GuChong2_NaiJiu,
                        variables.ShaZhao4_GuChong3_NaiJiu, variables.ShaZhao4_GuChong4_NaiJiu,
                        variables.ShaZhao4_GuChong5_NaiJiu, variables.ShaZhao4_GuChong6_NaiJiu,
                        variables.ShaZhao4_GuChong7_NaiJiu, variables.ShaZhao4_GuChong8_NaiJiu,
                        variables.ShaZhao4_GuChong9_NaiJiu, variables.ShaZhao4_GuChong10_NaiJiu}
        };
    }

    private static ItemStack[][] killerMoveSlots(PlayerVariables variables) {
        return new ItemStack[][]{
                {variables.ShaZhao1_GuChong1, variables.ShaZhao1_GuChong2,
                        variables.ShaZhao1_GuChong3, variables.ShaZhao1_GuChong4, variables.ShaZhao1_GuChong5,
                        variables.ShaZhao1_GuChong6, variables.ShaZhao1_GuChong7, variables.ShaZhao1_GuChong8,
                        variables.ShaZhao1_GuChong9, variables.ShaZhao1_GuChong10},
                {variables.ShaZhao2_GuChong1, variables.ShaZhao2_GuChong2,
                        variables.ShaZhao2_GuChong3, variables.ShaZhao2_GuChong4, variables.ShaZhao2_GuChong5,
                        variables.ShaZhao2_GuChong6, variables.ShaZhao2_GuChong7, variables.ShaZhao2_GuChong8,
                        variables.ShaZhao2_GuChong9, variables.ShaZhao2_GuChong10},
                {variables.ShaZhao3_GuChong1, variables.ShaZhao3_GuChong2,
                        variables.ShaZhao3_GuChong3, variables.ShaZhao3_GuChong4, variables.ShaZhao3_GuChong5,
                        variables.ShaZhao3_GuChong6, variables.ShaZhao3_GuChong7, variables.ShaZhao3_GuChong8,
                        variables.ShaZhao3_GuChong9, variables.ShaZhao3_GuChong10},
                {variables.ShaZhao4_GuChong1, variables.ShaZhao4_GuChong2,
                        variables.ShaZhao4_GuChong3, variables.ShaZhao4_GuChong4, variables.ShaZhao4_GuChong5,
                        variables.ShaZhao4_GuChong6, variables.ShaZhao4_GuChong7, variables.ShaZhao4_GuChong8,
                        variables.ShaZhao4_GuChong9, variables.ShaZhao4_GuChong10}
        };
    }

    public static int count(UUID owner, GuActivation.Entry entry) {
        return LEDGER.count(owner, entry);
    }

    public static int count(UUID owner) {
        return LEDGER.size(owner);
    }

    public static void clear() {
        LEDGER.clear();
        KILLER_MOVE_SNAPSHOTS.remove();
        STAGED_ACTIVATIONS.remove();
        DELAYED_ACTIVATIONS.clear();
        GuBacklashService.clear();
    }
}
