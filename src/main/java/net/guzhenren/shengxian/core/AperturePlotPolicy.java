package net.guzhenren.shengxian.core;

import java.util.UUID;

/** Owner-only world mutation policy for a claimed aperture plot. */
public final class AperturePlotPolicy {
    private AperturePlotPolicy() {}

    public static boolean mayGeneratePlatform(int plot, boolean alreadyBuilt,
                                               boolean floorEmpty, boolean landingClear) {
        return ApertureSpaceStore.isValidPlot(plot) && !alreadyBuilt && floorEmpty && landingClear;
    }

    /** Projectiles and explosions may affect only the same claimed plot they originated in. */
    public static boolean mayAffect(int originPlot, int affectedPlot) {
        return ApertureSpaceStore.isValidPlot(originPlot) && originPlot == affectedPlot;
    }

    public static boolean mayModify(int plot, UUID owner, UUID actor, double x, double z) {
        return owner != null && owner.equals(actor) && AperturePlotBounds.contains(plot, x, z);
    }

    /** Guests may invoke the server-validated, non-block-mutating travel mark action. */
    public static boolean mayUseBoundItem(boolean owner, String guId, boolean sneaking) {
        return owner || sneaking && "ding_xian_you".equals(guId);
    }
}
