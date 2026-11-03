package net.guzhenren.shengxian.core;

/**
 * Tunable parameters for one server simulation step.
 *
 * <p><b>Evidence status: game mapping.</b> CANON-002/003/004 fix the shape (continuous
 * three-qi control, finite human qi, more ambient absorption raises future attainment but
 * greed causes imbalance, and insight happens in a transient 交感 window with distraction
 * risk). The numbers below are NOT canon constants; they are a first playable calibration
 * and must stay labelled as game parameters until a later balance pass.</p>
 */
public record QiParameters(
        double ambientMaxHeaven,
        double ambientMaxEarth,
        double ambientRegenPerTick,
        double maxAbsorbHeavenPerTick,
        double maxAbsorbEarthPerTick,
        double maxHumanReleasePerTick,
        double balanceTolerance,
        double riskGainPerTick,
        double riskDecayPerTick,
        double riskLimit,
        double windowMinQi,
        int windowTicksRequired,
        int insightPerWindow,
        double distractionRiskFactor) {

    public QiParameters {
        requireNonNegative(ambientMaxHeaven, "ambientMaxHeaven");
        requireNonNegative(ambientMaxEarth, "ambientMaxEarth");
        requireNonNegative(ambientRegenPerTick, "ambientRegenPerTick");
        requireNonNegative(maxAbsorbHeavenPerTick, "maxAbsorbHeavenPerTick");
        requireNonNegative(maxAbsorbEarthPerTick, "maxAbsorbEarthPerTick");
        requireNonNegative(maxHumanReleasePerTick, "maxHumanReleasePerTick");
        if (!Double.isFinite(balanceTolerance) || balanceTolerance <= 0 || balanceTolerance > 1) {
            throw new IllegalArgumentException("balanceTolerance must be in (0,1]");
        }
        requireNonNegative(riskGainPerTick, "riskGainPerTick");
        requireNonNegative(riskDecayPerTick, "riskDecayPerTick");
        if (!Double.isFinite(riskLimit) || riskLimit <= 0) {
            throw new IllegalArgumentException("riskLimit must be positive");
        }
        requireNonNegative(windowMinQi, "windowMinQi");
        if (windowTicksRequired <= 0) throw new IllegalArgumentException("windowTicksRequired must be positive");
        if (insightPerWindow <= 0) throw new IllegalArgumentException("insightPerWindow must be positive");
        requireNonNegative(distractionRiskFactor, "distractionRiskFactor");
    }

    /** First calibration; every value is a game parameter, not a canon number. */
    public static QiParameters standard() {
        return new QiParameters(120.0, 120.0, 1.5, 4.0, 4.0, 3.0, 0.18,
                0.8, 0.35, 12.0, 20.0, 40, 2, 0.9);
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Invalid parameter: " + name);
        }
    }
}
