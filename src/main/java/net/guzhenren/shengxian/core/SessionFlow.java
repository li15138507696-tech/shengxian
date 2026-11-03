package net.guzhenren.shengxian.core;

/** Enforces phase ordering and same-phase revisions. Rule modules must still authorize transitions. */
public final class SessionFlow {
    private SessionFlow() {}

    /**
     * Accepts a phase transition. The caller must have loaded the exact session identified by
     * {@code expectedStateRevision}; both the phase revision and the state revision advance.
     */
    public static AscensionSession advance(AscensionSession current, Phase target,
                                           long expectedStateRevision, long sequence, long serverTick) {
        checkAction(current, expectedStateRevision, sequence, serverTick);
        if (target == current.phase()) {
            throw new IllegalStateException("Phase transition requires a different phase");
        }
        boolean permitted = switch (current.phase()) {
            case PREPARING -> target == Phase.BREAKING;
            case BREAKING -> target == Phase.BROKEN || target == Phase.FAILED;
            case BROKEN -> target == Phase.ABSORBING || target == Phase.FAILED;
            case ABSORBING -> target == Phase.OPENING || target == Phase.FAILED;
            case OPENING -> target == Phase.FORMING || target == Phase.FAILED;
            case FORMING -> target == Phase.IMMORTAL || target == Phase.FAILED;
            case IMMORTAL, FAILED -> false;
        };
        if (!permitted) throw new IllegalStateException("Illegal phase transition");
        if (current.phaseRevision() == Long.MAX_VALUE || current.stateRevision() == Long.MAX_VALUE) {
            throw new IllegalStateException("Revision exhausted");
        }
        return new AscensionSession(current.id(), current.key(), target,
                current.broken() || target == Phase.BROKEN,
                current.phaseRevision() + 1, current.stateRevision() + 1, sequence, serverTick);
    }

    /**
     * Accepts a same-phase update. Failed sessions are frozen; an IMMORTAL session may update
     * only its formed aperture ledger. The repository enforces that progress boundary.
     * The state revision advances so the repository can use it as a CAS token.
     */
    public static AscensionSession update(AscensionSession current, long expectedStateRevision,
                                          long sequence, long serverTick) {
        checkAction(current, expectedStateRevision, sequence, serverTick);
        if (current.isTerminal()) {
            throw new IllegalStateException("Failed session cannot be updated in place");
        }
        if (current.stateRevision() == Long.MAX_VALUE) {
            throw new IllegalStateException("Revision exhausted");
        }
        return new AscensionSession(current.id(), current.key(), current.phase(), current.broken(),
                current.phaseRevision(), current.stateRevision() + 1, sequence, serverTick);
    }

    /** Same-phase bookkeeping for one already committed FAILED consequence; gameplay stays frozen. */
    public static AscensionSession acknowledgeFailure(AscensionSession current, long expectedStateRevision,
                                                      long sequence, long serverTick) {
        checkAction(current, expectedStateRevision, sequence, serverTick);
        if (current.phase() != Phase.FAILED) throw new IllegalStateException("Session is not failed");
        if (current.stateRevision() == Long.MAX_VALUE) throw new IllegalStateException("Revision exhausted");
        return new AscensionSession(current.id(), current.key(), Phase.FAILED, current.broken(),
                current.phaseRevision(), current.stateRevision() + 1, sequence, serverTick);
    }

    private static void checkAction(AscensionSession current, long expectedStateRevision,
                                    long sequence, long serverTick) {
        if (current.stateRevision() != expectedStateRevision) {
            throw new IllegalStateException("Stale state revision");
        }
        if (sequence <= current.lastActionSequence()) {
            throw new IllegalStateException("Repeated or out-of-order action");
        }
        if (serverTick < current.serverTick()) {
            throw new IllegalStateException("Server clock moved backwards");
        }
    }
}
