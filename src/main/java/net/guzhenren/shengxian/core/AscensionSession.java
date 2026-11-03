package net.guzhenren.shengxian.core;

import java.util.Objects;
import java.util.UUID;

/**
 * Phase identity of one aperture's ascension.
 *
 * <p>Five independent counters exist on purpose:
 * phaseRevision counts accepted phase transitions, stateRevision counts accepted persisted
 * mutations (including same-phase updates), lastActionSequence rejects replayed client
 * actions, and serverTick is the simulation clock. Side-effect idempotency keys live in
 * {@link AscensionProgress}. Do not collapse these back into one revision.</p>
 *
 * <p>Reaching IMMORTAL here is a domain state only; it never mutates a Minecraft player.</p>
 */
public record AscensionSession(UUID id, ApertureKey key, Phase phase, boolean broken,
                               long phaseRevision, long stateRevision,
                               long lastActionSequence, long serverTick) {
    public AscensionSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(phase, "phase");
        if (phaseRevision < 0 || stateRevision < 0 || lastActionSequence < 0 || serverTick < 0) {
            throw new IllegalArgumentException("Negative session counter");
        }
        if (stateRevision < phaseRevision) {
            throw new IllegalArgumentException("State revision cannot be older than phase revision");
        }
        boolean beforeBreak = phase == Phase.PREPARING || phase == Phase.BREAKING;
        if ((beforeBreak && broken) || (!beforeBreak && phase != Phase.FAILED && !broken)) {
            throw new IllegalArgumentException("Phase and broken-aperture marker disagree");
        }
        long minimum = switch (phase) {
            case PREPARING -> 0;
            case BREAKING -> 1;
            case BROKEN -> 2;
            case ABSORBING -> 3;
            case OPENING -> 4;
            case FORMING -> 5;
            case IMMORTAL -> 6;
            case FAILED -> broken ? 3 : 2;
        };
        if (phaseRevision < minimum) {
            throw new IllegalArgumentException("Unreachable phase revision");
        }
        // Only the pre-break cycle PREPARING -> BREAKING -> PREPARING can repeat. Its parity is
        // a property of the phase graph, not of stateRevision; same-phase updates are free.
        boolean parityChecked = phase == Phase.PREPARING || phase == Phase.BREAKING
                || (phase == Phase.FAILED && !broken);
        boolean parityMismatch = parityChecked && (phaseRevision - minimum) % 2 != 0;
        if (parityMismatch) {
            throw new IllegalArgumentException("Unreachable phase revision");
        }
    }

    public static AscensionSession prepare(ApertureKey key) {
        return prepare(key, 0);
    }

    public static AscensionSession prepare(ApertureKey key, long serverTick) {
        return new AscensionSession(UUID.randomUUID(), key, Phase.PREPARING, false, 0, 0, 0, serverTick);
    }

    /** FAILED is terminal; IMMORTAL has no more ascension phases but its aperture ledger still ticks. */
    public boolean isTerminal() {
        return phase == Phase.FAILED;
    }
}