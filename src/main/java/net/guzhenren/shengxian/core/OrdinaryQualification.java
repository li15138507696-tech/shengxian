package net.guzhenren.shengxian.core;

/** Audited upstream stage encoding for the ordinary five-turn route. */
public final class OrdinaryQualification {
    private OrdinaryQualification() {}

    public static boolean fivePeak(double rank, double stage, boolean shifted,
                                   boolean tenExtremes, boolean apertureLost) {
        return Double.compare(rank, 5.0) == 0
                && Double.compare(stage, shifted ? 5.0 : 4.0) == 0
                && !tenExtremes && !apertureLost;
    }

    /** The upstream temporary effect shifts each displayed stage by one. */
    public static double sixInitialStage(boolean shifted) {
        return shifted ? 2.0 : 1.0;
    }
}