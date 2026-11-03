package net.guzhenren.shengxian.core;

/** Counts irreversible shatter time using only server ticks observed while the player is online. */
public final class ShatterClock {
    private ShatterClock() {}

    public record State(long phaseRevision, long lastServerTick, int elapsedTicks) {
        public State {
            if (phaseRevision <= 0 || lastServerTick < 0 || elapsedTicks < 0
                    || elapsedTicks > AscensionProgress.MAX_WINDOW_TICKS) {
                throw new IllegalArgumentException("Invalid shatter clock state");
            }
        }

        public boolean complete(int durationTicks) {
            if (durationTicks <= 0) throw new IllegalArgumentException("Shatter duration must be positive");
            return elapsedTicks >= durationTicks;
        }
    }

    public static State start(long phaseRevision, long serverTick) {
        return new State(phaseRevision, serverTick, 0);
    }

    /** Resets the interval baseline on login without counting ticks spent disconnected. */
    public static State rebase(State current, long serverTick) {
        if (current == null) throw new NullPointerException("current");
        if (serverTick < current.lastServerTick()) {
            throw new IllegalArgumentException("Server tick moved backwards");
        }
        return new State(current.phaseRevision(), serverTick, current.elapsedTicks());
    }

    /** Adds only the interval since the previous online server update, capped at the duration. */
    public static State advance(State current, long serverTick, int durationTicks) {
        if (current == null) throw new NullPointerException("current");
        if (durationTicks <= 0 || durationTicks > AscensionProgress.MAX_WINDOW_TICKS) {
            throw new IllegalArgumentException("Invalid shatter duration");
        }
        if (serverTick < current.lastServerTick()) {
            throw new IllegalArgumentException("Server tick moved backwards");
        }
        long elapsed = Math.min(durationTicks, (long) current.elapsedTicks()
                + serverTick - current.lastServerTick());
        return new State(current.phaseRevision(), serverTick, (int) elapsed);
    }
}
