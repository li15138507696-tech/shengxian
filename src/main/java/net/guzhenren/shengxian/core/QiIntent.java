package net.guzhenren.shengxian.core;

/**
 * One tick of player intent. Values are fractions of the per-tick maximum; the server never
 * accepts absolute qi amounts from the client. Human release can only consume the stored
 * finite reserve.
 */
public record QiIntent(double heavenDraw, double earthDraw, double humanRelease, boolean seekInsight) {
    public QiIntent {
        requireFraction(heavenDraw, "heavenDraw");
        requireFraction(earthDraw, "earthDraw");
        requireFraction(humanRelease, "humanRelease");
    }

    public static QiIntent idle() {
        return new QiIntent(0, 0, 0, false);
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("Intent must be in [0,1]: " + name);
        }
    }
}
