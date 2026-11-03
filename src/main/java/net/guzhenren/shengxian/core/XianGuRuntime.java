package net.guzhenren.shengxian.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Bounded reusable-Gu ownership, cooldowns, marks and the latest debit receipt. */
public record XianGuRuntime(Set<String> held, Map<String, Long> cooldowns, Map<Integer, Mark> marks,
                            int selectedSlot, boolean recording, long sequence, Optional<Use> operation) {
    public static final Set<String> IDS = Set.of("ding_xian_you", "ren_ru_gu_immortal", "jiang_shan_ru_gu");
    public static final int MAX_PAYLOAD = 262_144;
    public enum Stage { CHARGED, COMMITTED, UNCERTAIN, ACKNOWLEDGED }

    public record Mark(String dimension, double x, double y, double z, float yaw, float pitch, String scene) {
        public Mark {
            if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || dimension.length() > 128
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 4096
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90
                    || scene == null || !scene.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid immortal travel mark");
            }
        }
    }

    public record Use(UUID operationId, String guId, long sequence, Stage stage, long tick,
                      double essenceBefore, double essenceAfter, long cooldownUntil, String payload) {
        public Use {
            Objects.requireNonNull(operationId, "operation id");
            Objects.requireNonNull(stage, "stage");
            requireId(guId);
            if (sequence <= 0 || tick < 0 || cooldownUntil < tick || !finiteEssence(essenceBefore)
                    || !finiteEssence(essenceAfter) || essenceAfter >= essenceBefore
                    || payload == null || payload.isEmpty() || payload.length() > MAX_PAYLOAD) {
                throw new IllegalArgumentException("Invalid immortal Gu use receipt");
            }
        }
        public Use withStage(Stage next) {
            return new Use(operationId, guId, sequence, next, tick, essenceBefore, essenceAfter, cooldownUntil, payload);
        }
    }

    public XianGuRuntime {
        Objects.requireNonNull(held, "held");
        Objects.requireNonNull(cooldowns, "cooldowns");
        Objects.requireNonNull(marks, "marks");
        Objects.requireNonNull(operation, "operation");
        held.forEach(XianGuRuntime::requireId);
        if (held.size() > IDS.size() || cooldowns.size() > IDS.size() || marks.size() > 8
                || selectedSlot < 0 || selectedSlot >= 8 || sequence < 0) {
            throw new IllegalArgumentException("Invalid immortal Gu runtime bounds");
        }
        for (var entry : cooldowns.entrySet()) {
            requireId(entry.getKey());
            if (entry.getValue() == null || entry.getValue() < 0 || !held.contains(entry.getKey())) {
                throw new IllegalArgumentException("Invalid Gu cooldown");
            }
        }
        marks.forEach((slot, mark) -> {
            if (slot == null || slot < 0 || slot >= 8 || mark == null) throw new IllegalArgumentException("Invalid mark slot");
        });
        if ((!marks.isEmpty() && !held.contains("ding_xian_you"))
                || (recording && !held.contains("jiang_shan_ru_gu"))) {
            throw new IllegalArgumentException("Gu state has no matching ownership");
        }
        if ((sequence == 0) != operation.isEmpty()) throw new IllegalArgumentException("Gu sequence has no receipt");
        if (operation.isPresent() && (operation.get().sequence() != sequence || !held.contains(operation.get().guId())
                || cooldowns.getOrDefault(operation.get().guId(), 0L) < operation.get().cooldownUntil())) {
            throw new IllegalArgumentException("Gu receipt does not match its runtime");
        }
        held = Set.copyOf(held);
        cooldowns = Map.copyOf(cooldowns);
        marks = Map.copyOf(marks);
    }

    public static XianGuRuntime empty() {
        return new XianGuRuntime(Set.of(), Map.of(), Map.of(), 0, false, 0, Optional.empty());
    }

    public boolean blocked() {
        return operation.filter(use -> use.stage() == Stage.CHARGED || use.stage() == Stage.UNCERTAIN).isPresent();
    }

    public XianGuRuntime grant(String id) {
        requireId(id);
        if (held.contains(id)) return this;
        requireIdle();
        Set<String> next = new LinkedHashSet<>(held);
        next.add(id);
        return new XianGuRuntime(next, cooldowns, marks, selectedSlot, recording, sequence, operation);
    }

    public void requireAvailable(String id, long tick) {
        requireId(id);
        if (!held.contains(id)) throw new IllegalStateException("仙蛊未绑定当前会话");
        requireIdle();
        if (tick < 0 || (operation.isPresent() && tick < operation.get().tick())) {
            throw new IllegalStateException("服务器计时早于仙蛊记录");
        }
        if (tick < cooldowns.getOrDefault(id, 0L)) throw new IllegalStateException("仙蛊尚在冷却");
    }

    public XianGuRuntime begin(UUID id, String gu, long tick, double before, double cost, long cooldown, String payload) {
        if (!Double.isFinite(cost) || cost <= 0 || !finiteEssence(before) || before < cost
                || cooldown < 0 || cooldown > 72_000 || tick > Long.MAX_VALUE - cooldown) {
            throw new IllegalArgumentException("Invalid immortal Gu debit");
        }
        if (operation.isPresent() && operation.get().operationId().equals(id)) {
            Use prior = operation.get();
            if (!prior.guId().equals(gu) || prior.tick() != tick || prior.essenceBefore() != before
                    || prior.essenceAfter() != before - cost || prior.cooldownUntil() != tick + cooldown
                    || !prior.payload().equals(payload)) throw new IllegalStateException("Operation identity changed");
            return this;
        }
        requireAvailable(gu, tick);
        if (sequence == Long.MAX_VALUE) throw new IllegalStateException("Gu sequence exhausted");
        Use next = new Use(id, gu, sequence + 1, Stage.CHARGED, tick, before, before - cost, tick + cooldown, payload);
        Map<String, Long> times = new LinkedHashMap<>(cooldowns);
        times.put(gu, next.cooldownUntil());
        return new XianGuRuntime(held, times, marks, selectedSlot, recording, sequence + 1, Optional.of(next));
    }

    public XianGuRuntime finish(UUID id, Stage stage) {
        Use prior = operation.orElseThrow(() -> new IllegalStateException("No Gu operation"));
        if (!prior.operationId().equals(id)) throw new IllegalStateException("Wrong Gu operation");
        if (prior.stage() == stage) return this;
        boolean allowed = prior.stage() == Stage.CHARGED && (stage == Stage.COMMITTED || stage == Stage.UNCERTAIN)
                || prior.stage() == Stage.UNCERTAIN && stage == Stage.ACKNOWLEDGED;
        if (!allowed) throw new IllegalStateException("Illegal Gu receipt transition");
        return new XianGuRuntime(held, cooldowns, marks, selectedSlot, recording, sequence,
                Optional.of(prior.withStage(stage)));
    }

    public XianGuRuntime withMark(int slot, Mark mark) {
        if (slot < 0 || slot >= 8 || mark == null) throw new IllegalArgumentException("Invalid mark slot");
        if (!held.contains("ding_xian_you")) throw new IllegalStateException("No fixed immortal travel Gu");
        requireIdle();
        Map<Integer, Mark> next = new LinkedHashMap<>(marks);
        next.put(slot, mark);
        return new XianGuRuntime(held, cooldowns, next, selectedSlot, recording, sequence, operation);
    }

    public XianGuRuntime selectSlot(int slot) {
        if (slot < 0 || slot >= 8) throw new IllegalArgumentException("Invalid mark slot");
        if (!held.contains("ding_xian_you")) throw new IllegalStateException("No fixed immortal travel Gu");
        requireIdle();
        return new XianGuRuntime(held, cooldowns, marks, slot, recording, sequence, operation);
    }

    public XianGuRuntime withRecording(boolean enabled) {
        if (!held.contains("jiang_shan_ru_gu")) throw new IllegalStateException("No landscape restoration Gu");
        requireIdle();
        return new XianGuRuntime(held, cooldowns, marks, selectedSlot, enabled, sequence, operation);
    }

    /** Repository-side validation; a new operation must accompany its exact single essence debit. */
    public void validateFrom(XianGuRuntime previous, double beforeEssence, double afterEssence) {
        if (!held.containsAll(previous.held)) throw new IllegalStateException("Reusable Gu ownership cannot shrink");
        for (var entry : previous.cooldowns.entrySet()) {
            if (cooldowns.getOrDefault(entry.getKey(), 0L) < entry.getValue()) throw new IllegalStateException("Cooldown moved backwards");
        }
        if (sequence == previous.sequence) {
            if (!cooldowns.equals(previous.cooldowns)) throw new IllegalStateException("Cooldown changed without a new use");
            if (operation.equals(previous.operation)) {
                if (previous.blocked() && (!held.equals(previous.held) || !marks.equals(previous.marks)
                        || selectedSlot != previous.selectedSlot || recording != previous.recording)) {
                    throw new IllegalStateException("Pending Gu state cannot be replaced");
                }
                return;
            }
            Use old = previous.operation.orElseThrow(() -> new IllegalStateException("Missing prior Gu receipt"));
            Use next = operation.orElseThrow(() -> new IllegalStateException("Gu receipt removed"));
            if (!next.withStage(old.stage()).equals(old)
                    || !previous.finish(old.operationId(), next.stage()).operation.equals(operation)
                    || !held.equals(previous.held) || !marks.equals(previous.marks)
                    || selectedSlot != previous.selectedSlot || recording != previous.recording) {
                throw new IllegalStateException("Gu receipt identity changed");
            }
            return;
        }
        if (previous.sequence == Long.MAX_VALUE || sequence != previous.sequence + 1 || previous.blocked()) {
            throw new IllegalStateException("Invalid Gu operation sequence");
        }
        Use next = operation.orElseThrow();
        previous.requireAvailable(next.guId(), next.tick());
        if (next.stage() != Stage.CHARGED || next.essenceBefore() != beforeEssence
                || next.essenceAfter() != afterEssence || !held.equals(previous.held)
                || !marks.equals(previous.marks) || selectedSlot != previous.selectedSlot || recording != previous.recording) {
            throw new IllegalStateException("Gu operation and essence debit are not atomic");
        }
        Map<String, Long> expected = new LinkedHashMap<>(previous.cooldowns);
        expected.put(next.guId(), next.cooldownUntil());
        if (!expected.equals(cooldowns)) throw new IllegalStateException("Unrelated Gu cooldown changed");
    }

    private void requireIdle() {
        if (blocked()) throw new IllegalStateException("仙蛊有未确认操作，请查看 status");
    }
    private static void requireId(String id) {
        if (id == null || !IDS.contains(id)) throw new IllegalArgumentException("Unknown reusable immortal Gu");
    }
    private static boolean finiteEssence(double value) {
        return Double.isFinite(value) && value >= 0 && value <= AscensionProgress.MAX_QI;
    }
}
