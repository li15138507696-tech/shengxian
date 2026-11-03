package net.guzhenren.shengxian.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/**
 * Bounded, owner-scoped record of real gu activations. Owner and event limits are both finite.
 *
 * <p>It answers "what did this player actually activate, through which branch, and did the
 * branch accept it". De-duplication lasts only while an event key remains retained. It never
 * decides that a gu was lost: GAP-004 has no verified rule for loss, probability or BenMingGu protection.</p>
 */
public final class GuActivationLedger {
    private static final int DEFAULT_MAXIMUM_OWNERS = 4096;

    private static final class OwnerState {
        private final Deque<GuActivation> activations = new ArrayDeque<>();
        private long sequence;
    }

    private final int capacityPerOwner;
    private final int maximumOwners;
    private final Map<UUID, OwnerState> byOwner = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<String> keys = new HashSet<>();

    public GuActivationLedger(int capacityPerOwner) {
        this(capacityPerOwner, DEFAULT_MAXIMUM_OWNERS);
    }

    public GuActivationLedger(int capacityPerOwner, int maximumOwners) {
        if (capacityPerOwner <= 0) throw new IllegalArgumentException("Capacity must be positive");
        if (maximumOwners <= 0) throw new IllegalArgumentException("Maximum owners must be positive");
        this.capacityPerOwner = capacityPerOwner;
        this.maximumOwners = maximumOwners;
    }

    public static GuActivationLedger standard() {
        return new GuActivationLedger(64, DEFAULT_MAXIMUM_OWNERS);
    }

    /** Allocates an owner-scoped monotonic sequence while that owner's state is retained. */
    public long nextSequence(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        OwnerState state = stateFor(owner, true);
        state.sequence = Math.addExact(state.sequence, 1L);
        return state.sequence;
    }

    /** Records one observation; a retained duplicate key is ignored and returns false. */
    public boolean record(GuActivation activation) {
        Objects.requireNonNull(activation, "activation");
        if (!activation.accepted()) return false;
        OwnerState state = stateFor(activation.owner(), true);
        if (!keys.add(activation.key())) return false;
        state.activations.addLast(activation);
        while (state.activations.size() > capacityPerOwner) {
            GuActivation removed = state.activations.removeFirst();
            keys.remove(removed.key());
        }
        return true;
    }

    /** Newest-first snapshot for one owner. */
    public List<GuActivation> recent(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        OwnerState state = stateFor(owner, false);
        if (state == null) return List.of();
        List<GuActivation> copy = new ArrayList<>(state.activations);
        Collections.reverse(copy);
        return List.copyOf(copy);
    }

    public int count(UUID owner, GuActivation.Entry entry) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(entry, "entry");
        OwnerState state = stateFor(owner, false);
        if (state == null) return 0;
        int total = 0;
        for (GuActivation activation : state.activations) {
            if (activation.entry() == entry) total++;
        }
        return total;
    }

    /** Total observations across every owner, used by tests and diagnostics. */
    public int size() {
        int total = 0;
        for (OwnerState state : byOwner.values()) total += state.activations.size();
        return total;
    }

    public int size(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        OwnerState state = stateFor(owner, false);
        return state == null ? 0 : state.activations.size();
    }

    /** Releases one owner's observations and sequence. */
    public void removeOwner(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        OwnerState removed = byOwner.remove(owner);
        if (removed == null) return;
        for (GuActivation activation : removed.activations) keys.remove(activation.key());
    }

    public void clear() {
        byOwner.clear();
        keys.clear();
    }

    private OwnerState stateFor(UUID owner, boolean create) {
        OwnerState existing = byOwner.get(owner);
        if (existing != null || !create) return existing;
        if (byOwner.size() >= maximumOwners) evictLeastRecentlyUsed();
        OwnerState created = new OwnerState();
        byOwner.put(owner, created);
        return created;
    }

    private void evictLeastRecentlyUsed() {
        Iterator<Map.Entry<UUID, OwnerState>> iterator = byOwner.entrySet().iterator();
        if (!iterator.hasNext()) return;
        OwnerState evicted = iterator.next().getValue();
        iterator.remove();
        for (GuActivation activation : evicted.activations) keys.remove(activation.key());
    }
}