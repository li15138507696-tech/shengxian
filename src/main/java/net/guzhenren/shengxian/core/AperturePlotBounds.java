package net.guzhenren.shengxian.core;

import java.util.Objects;
import java.util.Set;

/** Pure geometry for the shared aperture dimension's reserved 16x16 plots. */
public final class AperturePlotBounds {
    private static final int SPACING = 256;
    private static final int HALF_SIZE = 8;

    private AperturePlotBounds() {}

    /** Plot bounds include the minimum edge and exclude the maximum edge. */
    public static boolean contains(int plot, double x, double z) {
        if (!ApertureSpaceStore.isValidPlot(plot) || !Double.isFinite(x) || !Double.isFinite(z)) return false;
        long originX = (long) (plot % 64) * SPACING;
        long originZ = (long) (plot / 64) * SPACING;
        return x >= originX - HALF_SIZE && x < originX + HALF_SIZE
                && z >= originZ - HALF_SIZE && z < originZ + HALF_SIZE;
    }

    /** Returns the claimed plot containing a position, or -1 if it is in shared void space. */
    public static int find(Set<Integer> plots, double x, double z) {
        Objects.requireNonNull(plots, "plots");
        if (!Double.isFinite(x) || !Double.isFinite(z)) return -1;
        long column = (long) Math.floor((x + HALF_SIZE) / SPACING);
        long row = (long) Math.floor((z + HALF_SIZE) / SPACING);
        if (column < 0 || column >= 64 || row < 0
                || row > ApertureSpaceStore.MAX_PLOT_INDEX / 64L) return -1;
        long candidate = row * 64L + column;
        if (candidate > ApertureSpaceStore.MAX_PLOT_INDEX) return -1;
        int plot = (int) candidate;
        return plots.contains(plot) && contains(plot, x, z) ? plot : -1;
    }
}
