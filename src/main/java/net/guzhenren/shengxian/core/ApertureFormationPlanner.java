package net.guzhenren.shengxian.core;

import java.util.Objects;
import java.util.Random;

/**
 * Maps the amount of fused heaven, earth and human qi to an aperture grade. Human qi balance
 * gates survival in QiEngine; the amount of all three qi fused into the aperture determines
 * its canon grade. Thresholds and within-band sampling are game parameters.
 * Area is stored in actual mu; the 16x16 Minecraft plot is only a spatial scale mapping.
 * The medium time-ratio range is a game mapping that includes a verified 1:16 example; it is not a canon formula.
 */
public final class ApertureFormationPlanner {
    private ApertureFormationPlanner() {}

    public static final double MEDIUM_GRADE_QI = 80.0;
    public static final double UPPER_GRADE_QI = 200.0;

    public enum Grade { SMALL, MEDIUM, UPPER }

    public record Plan(double areaMu, double timeRatio, double initialEssence, int plotIndex, Grade grade) {
        public Plan {
            Objects.requireNonNull(grade, "grade");
            if (!Double.isFinite(areaMu) || areaMu <= 0) throw new IllegalArgumentException("Invalid area");
            if (!Double.isFinite(timeRatio) || timeRatio <= 0) throw new IllegalArgumentException("Invalid ratio");
            if (!Double.isFinite(initialEssence) || initialEssence <= 0) {
                throw new IllegalArgumentException("Invalid essence");
            }
            if (plotIndex < 0) throw new IllegalArgumentException("Invalid plot");
        }
    }

    public static Grade gradeFor(AscensionProgress progress) {
        Objects.requireNonNull(progress, "progress");
        // CANON-006: grade depends on all qi fused into the aperture; unused human reserve is excluded.
        double absorbedThreeQi = progress.qi().heaven() + progress.qi().earth() + progress.qi().human();
        if (absorbedThreeQi >= UPPER_GRADE_QI) return Grade.UPPER;
        if (absorbedThreeQi >= MEDIUM_GRADE_QI) return Grade.MEDIUM;
        return Grade.SMALL;
    }

    public static Plan plan(AscensionProgress progress, int plotIndex, Random random) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(random, "random");
        Grade grade = gradeFor(progress);
        return switch (grade) {
            case SMALL -> new Plan(random.nextLong(1, 3_000_001), 1 + random.nextInt(4),
                    11 + random.nextInt(9), plotIndex, grade);
            case MEDIUM -> new Plan(4_000_000 + random.nextInt(2_000_001), 5 + random.nextInt(12),
                    21 + random.nextInt(9), plotIndex, grade);
            case UPPER -> new Plan(7_000_000 + random.nextInt(2_000_001), 11 + random.nextInt(6),
                    31 + random.nextInt(10), plotIndex, grade);
        };
    }
}