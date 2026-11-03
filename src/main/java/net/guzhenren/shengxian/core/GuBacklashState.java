package net.guzhenren.shengxian.core;

import java.util.Objects;
import java.util.UUID;

/** Persistable, session-bound game state for the witnessed Gu-backlash mapping. */
public record GuBacklashState(UUID sessionId, int pressure, int threshold, long sequence,
                              String lastEventKey, Receipt lastReceipt,
                              boolean quarantined, String quarantineReason) {
    public static final int MIN_THRESHOLD = 1;
    public static final int MAX_THRESHOLD = 64;

    public enum Decision {
        IGNORED_PHASE,
        IGNORED_UNCONFIRMED,
        DUPLICATE,
        PRESSURE_ONLY,
        LOSS_DUE,
        VITAL_PROTECTED,
        TARGET_UNAVAILABLE,
        QUARANTINED
    }

    public record Receipt(long sequence, String eventKey, Decision decision) {
        public Receipt {
            if (sequence < 0) throw new IllegalArgumentException("Negative Gu backlash sequence");
            if (eventKey == null || eventKey.isBlank() || eventKey.length() > 512) {
                throw new IllegalArgumentException("Invalid Gu backlash event key");
            }
            Objects.requireNonNull(decision, "decision");
        }
    }

    public record Transition(GuBacklashState state, Decision decision) {
        public Transition {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(decision, "decision");
        }
    }

    public GuBacklashState {
        Objects.requireNonNull(sessionId, "sessionId");
        if (threshold < MIN_THRESHOLD || threshold > MAX_THRESHOLD) {
            throw new IllegalArgumentException("Gu backlash threshold must be between 1 and 64");
        }
        if (pressure < 0 || pressure >= threshold) throw new IllegalArgumentException("Invalid Gu backlash pressure");
        if (sequence < 0) throw new IllegalArgumentException("Negative Gu backlash sequence");
        if (lastEventKey == null) lastEventKey = "";
        if (lastEventKey.length() > 512) throw new IllegalArgumentException("Gu backlash event key is too long");
        if (quarantineReason == null) quarantineReason = "";
        if (quarantineReason.length() > 256) throw new IllegalArgumentException("Gu backlash quarantine reason is too long");
        if (quarantined && quarantineReason.isBlank()) quarantineReason = "uncertain";
        if (!quarantined && !quarantineReason.isBlank()) throw new IllegalArgumentException("Quarantine reason without quarantine");
        if (lastReceipt != null && lastReceipt.sequence() > sequence) {
            throw new IllegalArgumentException("Receipt sequence is ahead of Gu backlash state");
        }
    }

    public static GuBacklashState empty(UUID sessionId, int threshold) {
        return new GuBacklashState(sessionId, 0, threshold, 0L, "", null, false, "");
    }

    public GuBacklashState quarantine(String reason) {
        return new GuBacklashState(sessionId, pressure, threshold, sequence, lastEventKey,
                lastReceipt, true, reason == null || reason.isBlank() ? "uncertain" : reason);
    }

    /** Applies one already-completed, exact-source activation. Uncertain targets never redirect. */
    public static Transition observe(GuBacklashState current, UUID sessionId, Phase phase,
                                     int configuredThreshold, GuActivation activation,
                                     boolean targetConfirmed, boolean vitalProtected) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(activation, "activation");
        validateThreshold(configuredThreshold);

        GuBacklashState state = current == null || !current.sessionId().equals(sessionId)
                ? empty(sessionId, configuredThreshold) : current;
        if (state.quarantined()) return new Transition(state, Decision.QUARANTINED);
        if (phase != Phase.ABSORBING) return new Transition(state, Decision.IGNORED_PHASE);
        if (!GuActivationPolicy.mayApplyBacklash(phase, activation)) {
            return new Transition(state, Decision.IGNORED_UNCONFIRMED);
        }
        String eventKey = activation.key();
        if (eventKey.equals(state.lastEventKey())) return new Transition(state, Decision.DUPLICATE);

        if (state.sequence() == Long.MAX_VALUE) throw new IllegalStateException("Gu backlash sequence exhausted");
        long nextSequence = state.sequence() + 1L;
        int nextPressure = state.pressure() + 1;
        if (nextPressure < state.threshold()) {
            Receipt receipt = new Receipt(nextSequence, eventKey, Decision.PRESSURE_ONLY);
            return new Transition(new GuBacklashState(sessionId, nextPressure, state.threshold(), nextSequence,
                    eventKey, receipt, false, ""), Decision.PRESSURE_ONLY);
        }

        Decision decision = vitalProtected ? Decision.VITAL_PROTECTED
                : targetConfirmed ? Decision.LOSS_DUE : Decision.TARGET_UNAVAILABLE;
        Receipt receipt = new Receipt(nextSequence, eventKey, decision);
        return new Transition(new GuBacklashState(sessionId, nextPressure - state.threshold(), state.threshold(),
                nextSequence, eventKey, receipt, false, ""), decision);
    }

    private static void validateThreshold(int threshold) {
        if (threshold < MIN_THRESHOLD || threshold > MAX_THRESHOLD) {
            throw new IllegalArgumentException("Gu backlash threshold must be between 1 and 64");
        }
    }
}
