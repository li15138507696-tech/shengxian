package net.guzhenren.shengxian.core;

import java.util.ArrayDeque;
import java.util.Optional;

/** A single online player's bounded five-second injury history. */
public final class RecentHealthHistory {
    private record Sample(long tick, float health, int fire, boolean poison, boolean wither) { }
    public record Restore(float health, boolean extinguish, boolean clearPoison, boolean clearWither) { }
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private final int window;
    private long lastTreated = -1;

    public RecentHealthHistory() { this(100); }
    public RecentHealthHistory(int window) {
        if (window < 1 || window > 100) throw new IllegalArgumentException("Invalid recent-health window");
        this.window = window;
    }

    public void sample(long tick, float health, int fire, boolean poison, boolean wither) {
        if (tick < 0 || !Float.isFinite(health) || health <= 0 || health > 1_000_000) {
            clear();
            return;
        }
        if (!samples.isEmpty() && tick < samples.getLast().tick()) clear();
        if (!samples.isEmpty() && tick == samples.getLast().tick()) samples.removeLast();
        samples.addLast(new Sample(tick, health, Math.max(0, fire), poison, wither));
        trim(tick);
        while (samples.size() > window + 1) samples.removeFirst();
    }

    public Optional<Restore> plan(long tick, float current, float maximum, int fire, boolean poison, boolean wither) {
        if (tick < 0 || current <= 0 || !Float.isFinite(current) || !Float.isFinite(maximum) || maximum <= 0
                || lastTreated >= 0 && (tick < lastTreated || tick - lastTreated < 40)) return Optional.empty();
        trim(tick);
        Sample best = null;
        for (Sample sample : samples) {
            if (sample.tick() > tick) return Optional.empty();
            if (best == null || sample.health() >= best.health()) best = sample;
        }
        if (best == null) return Optional.empty();
        float health = Math.max(current, Math.min(maximum, best.health()));
        Restore restore = new Restore(health, fire > 0 && best.fire() == 0,
                poison && !best.poison(), wither && !best.wither());
        return health > current || restore.extinguish() || restore.clearPoison() || restore.clearWither()
                ? Optional.of(restore) : Optional.empty();
    }

    public void complete(long tick, float health, int fire, boolean poison, boolean wither) {
        samples.clear();
        sample(tick, health, fire, poison, wither);
        lastTreated = tick;
    }

    public void clear() { samples.clear(); lastTreated = -1; }
    private void trim(long tick) {
        while (!samples.isEmpty() && samples.getFirst().tick() < tick - window) samples.removeFirst();
    }
}
