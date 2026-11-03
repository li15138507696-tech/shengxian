package net.guzhenren.shengxian.core;

/**
 * CANON-007 qualification for the ten-extremes route.
 *
 * <p>The text says an ordinary aperture has to be shocked with true essence until it breaks, while a
 * ten-extremes aperture's wall is already barely holding: once the gu seal inside the body is
 * released it ruptures on its own. The three ascension steps are otherwise unchanged, so the only
 * qualification difference is that the physique must actually be ten-extremes — the audited stage
 * encoding is the same one the ordinary route uses.</p>
 *
 * <p>This is deliberately a separate predicate instead of a flag on the ordinary one: the ordinary
 * route must keep rejecting ten-extremes bodies (they used to reach it through
 * {@code !tenExtremes}), and a shared predicate with a boolean switch would make it easy to widen
 * one route by accident.</p>
 */
public final class TenExtremesQualification {
    private TenExtremesQualification() {}

    /** Five-turn peak of a ten-extremes body: same stage encoding, but the physique must match. */
    public static boolean fivePeak(double rank, double stage, boolean shifted,
                                   boolean tenExtremes, boolean apertureLost) {
        return Double.compare(rank, 5.0) == 0
                && Double.compare(stage, shifted ? 5.0 : 4.0) == 0
                && tenExtremes && !apertureLost;
    }
}
