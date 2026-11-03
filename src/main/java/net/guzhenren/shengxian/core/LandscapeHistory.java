package net.guzhenren.shengxian.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Transient, bounded, confirmed no-drop explosion history. It is never reconstructed after restart. */
public final class LandscapeHistory {
    public record Entry(UUID token, UUID owner, int plot, String dimension, int x, int y, int z,
                         String state, long tick, boolean available) {
        private Entry confirmed() { return new Entry(token, owner, plot, dimension, x, y, z, state, tick, true); }
        public double distanceSquared(double px, double py, double pz) {
            return square(x + 0.5 - px) + square(y + 0.5 - py) + square(z + 0.5 - pz);
        }
    }
    private record Position(String dimension, int x, int y, int z) { }
    private final int ownerLimit;
    private final int globalLimit;
    private final long lifetime;
    private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<Position, UUID> positions = new HashMap<>();
    private final Map<UUID, Integer> counts = new HashMap<>();
    private long lastExpiryTick = -1;

    public LandscapeHistory(int ownerLimit, int globalLimit, long lifetime) {
        if (ownerLimit < 1 || ownerLimit > 8192 || globalLimit < ownerLimit || globalLimit > 65536
                || lifetime < 1 || lifetime > 12000) throw new IllegalArgumentException("Invalid landscape bounds");
        this.ownerLimit = ownerLimit;
        this.globalLimit = globalLimit;
        this.lifetime = lifetime;
    }

    public Optional<UUID> reserve(UUID owner, int plot, String dimension, int x, int y, int z,
                                   String state, long tick) {
        expire(tick);
        if (owner == null || dimension == null || state == null || state.isBlank() || state.length() > 2048
                || tick < 0 || !AperturePlotBounds.contains(plot, x + 0.5, z + 0.5)) return Optional.empty();
        invalidate(dimension, x, y, z);
        if (entries.size() >= globalLimit || counts.getOrDefault(owner, 0) >= ownerLimit) return Optional.empty();
        UUID token = UUID.randomUUID();
        Entry entry = new Entry(token, owner, plot, dimension, x, y, z, state, tick, false);
        entries.put(token, entry);
        positions.put(new Position(dimension, x, y, z), token);
        counts.merge(owner, 1, Integer::sum);
        return Optional.of(token);
    }

    public boolean publish(UUID token, boolean destroyed, boolean noDrops, long tick) {
        expire(tick);
        Entry entry = entries.get(token);
        if (entry == null) return false;
        if (!destroyed || !noDrops) { consume(token); return false; }
        entries.put(token, entry.confirmed());
        return true;
    }

    public List<Entry> plan(UUID owner, int plot, String dimension, double x, double y, double z,
                              double radius, int maximum, long tick, Predicate<Entry> valid) {
        expire(tick);
        if (owner == null || dimension == null || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || !Double.isFinite(radius) || radius < 0 || radius > 64
                || maximum < 1 || maximum > 1024 || valid == null) throw new IllegalArgumentException("Invalid landscape plan");
        return entries.values().stream().filter(e -> e.available() && e.owner().equals(owner) && e.plot() == plot
                && e.dimension().equals(dimension) && e.distanceSquared(x, y, z) <= radius * radius && valid.test(e))
                .sorted(Comparator.comparingDouble((Entry e) -> e.distanceSquared(x, y, z))
                        .thenComparingInt(Entry::x).thenComparingInt(Entry::y).thenComparingInt(Entry::z))
                .limit(maximum).toList();
    }

    public boolean consume(UUID token) {
        Entry entry = entries.remove(token);
        if (entry == null) return false;
        positions.remove(new Position(entry.dimension(), entry.x(), entry.y(), entry.z()), token);
        counts.computeIfPresent(entry.owner(), (owner, count) -> count <= 1 ? null : count - 1);
        return true;
    }
    public boolean available(UUID token) { return entries.containsKey(token) && entries.get(token).available(); }
    public void invalidate(String dimension, int x, int y, int z) {
        UUID token = positions.get(new Position(dimension, x, y, z));
        if (token != null) consume(token);
    }
    public void clearOwner(UUID owner) {
        for (Entry entry : new ArrayList<>(entries.values())) if (entry.owner().equals(owner)) consume(entry.token());
    }
    public void clearChunk(String dimension, int chunkX, int chunkZ) {
        for (Entry entry : new ArrayList<>(entries.values())) if (entry.dimension().equals(dimension)
                && entry.x() >> 4 == chunkX && entry.z() >> 4 == chunkZ) consume(entry.token());
    }
    public void clear() { entries.clear(); positions.clear(); counts.clear(); }
    public static int stepBudget(int remaining, int globalRemaining) {
        return Math.max(0, Math.min(Math.min(remaining, 64), globalRemaining));
    }
    private void expire(long tick) {
        if (tick == lastExpiryTick) return;
        if (tick < lastExpiryTick) clear();
        lastExpiryTick = tick;
        while (!entries.isEmpty()) {
            Entry first = entries.values().iterator().next();
            if (tick - first.tick() <= lifetime) break;
            consume(first.token());
        }
    }
    private static double square(double value) { return value * value; }
}
