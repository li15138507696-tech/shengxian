package net.guzhenren.shengxian.core;

/**
 * 灾劫伤害口径。
 *
 * <p>User-directed tuning: every calamity damage figure is multiplied by
 * {@link #MULTIPLIER}. The base numbers stay readable in one place, so this
 * multiplier is the only knob that scales the whole calamity threat.</p>
 */
public final class CalamityDamage {
    private CalamityDamage() {}

    /** Global calamity damage scale requested by the user (x500). */
    public static final double MULTIPLIER = 500.0;

    /** Base impact damage before scaling; already lethal before the multiplier existed. */
    public static final double BASE_IMPACT_DAMAGE = 2048.0;

    /** Impact damage actually applied to the player. */
    public static float impactDamage() {
        return (float) (BASE_IMPACT_DAMAGE * MULTIPLIER);
    }

    /** Scales one base attack figure. */
    public static double scaled(double base) {
        return base * MULTIPLIER;
    }
}
