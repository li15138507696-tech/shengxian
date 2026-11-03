package net.guzhenren.shengxian.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure recovery rules for the one-time true-essence debit performed when shattering begins. */
public final class ShatterEssenceTransaction {
    private ShatterEssenceTransaction() { }

    public enum Stage { PREPARED, COMMITTED, UNCERTAIN }

    public enum Decision {
        WRITE_PREPARED,
        APPLY_TARGET,
        COMMIT_ALREADY_APPLIED,
        ALREADY_COMMITTED,
        QUARANTINE
    }

    /**
     * Player-side durable receipt. A legacy isolated attempt may have no known baseline;
     * that is represented only by UNCERTAIN and is never eligible for recovery writes.
     */
    public record Receipt(UUID sessionId, long phaseRevision, Stage stage,
                          Double before, double target, Double observed) {
        public Receipt {
            Objects.requireNonNull(sessionId, "session id");
            Objects.requireNonNull(stage, "stage");
            if (target == 0.0) target = 0.0;
            if (before != null && before == 0.0) before = 0.0;
            if (observed != null && observed == 0.0) observed = 0.0;
            if (phaseRevision <= 0 || !finiteEssence(target)) {
                throw new IllegalArgumentException("Invalid shatter receipt identity or target");
            }
            if (before != null && (!finiteEssence(before) || target >= before)) {
                throw new IllegalArgumentException("Invalid shatter receipt baseline");
            }
            if (observed != null && !finiteEssence(observed)) {
                throw new IllegalArgumentException("Invalid observed true essence");
            }
            switch (stage) {
                case PREPARED -> {
                    if (before == null || observed != null) {
                        throw new IllegalArgumentException("PREPARED receipt requires a baseline and no observation");
                    }
                }
                case COMMITTED -> {
                    if (before == null || observed == null || observed.doubleValue() != target) {
                        throw new IllegalArgumentException("COMMITTED receipt must confirm the fixed target");
                    }
                }
                case UNCERTAIN -> { }
            }
        }

        public static Receipt prepared(UUID sessionId, long phaseRevision, double before, double target) {
            return new Receipt(sessionId, phaseRevision, Stage.PREPARED, before, target, null);
        }

        public static Receipt committed(UUID sessionId, long phaseRevision, double before, double target) {
            return new Receipt(sessionId, phaseRevision, Stage.COMMITTED, before, target, target);
        }

        public Receipt uncertain() {
            return new Receipt(sessionId, phaseRevision, Stage.UNCERTAIN, before, target, observed);
        }
    }

    /**
     * Chooses a recovery action without mutating data. Exact equality is intentional: an
     * unexpected current value cannot prove whether this transaction or later gameplay wrote it.
     */
    public static Decision decide(Optional<Receipt> receipt, UUID sessionId, long phaseRevision,
                                  Double worldBefore, double target, double current) {
        Objects.requireNonNull(receipt, "receipt");
        if (target == 0.0) target = 0.0;
        if (current == 0.0) current = 0.0;
        if (worldBefore != null && worldBefore == 0.0) worldBefore = 0.0;
        if (sessionId == null || phaseRevision <= 0 || !finiteEssence(target)
                || !finiteEssence(current)) return Decision.QUARANTINE;
        if (worldBefore != null && (!finiteEssence(worldBefore) || target >= worldBefore)) {
            return Decision.QUARANTINE;
        }

        if (receipt.isEmpty()) {
            if (worldBefore != null && current == worldBefore) return Decision.WRITE_PREPARED;
            return Decision.QUARANTINE;
        }

        Receipt saved = receipt.get();
        if (!saved.sessionId().equals(sessionId) || saved.phaseRevision() != phaseRevision
                || saved.target() != target || saved.stage() == Stage.UNCERTAIN) {
            return Decision.QUARANTINE;
        }
        if (worldBefore != null && (saved.before() == null || saved.before().doubleValue() != worldBefore)) {
            return Decision.QUARANTINE;
        }
        // Legacy target-only world records can only be confirmed by an already committed,
        // matching player receipt. A PREPARED receipt alone must not reconstruct their debit.
        if (worldBefore == null && saved.stage() != Stage.COMMITTED) return Decision.QUARANTINE;

        return switch (saved.stage()) {
            case PREPARED -> {
                if (saved.before() == null) yield Decision.QUARANTINE;
                if (current == saved.before()) yield Decision.APPLY_TARGET;
                if (current == saved.target()) yield Decision.COMMIT_ALREADY_APPLIED;
                yield Decision.QUARANTINE;
            }
            case COMMITTED -> saved.before() != null && saved.observed() != null
                    && saved.observed().doubleValue() == saved.target()
                    ? Decision.ALREADY_COMMITTED : Decision.QUARANTINE;
            case UNCERTAIN -> Decision.QUARANTINE;
        };
    }

    private static boolean finiteEssence(double value) {
        return Double.isFinite(value) && value >= 0 && value <= AscensionProgress.MAX_QI;
    }
}
