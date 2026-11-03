package net.guzhenren.shengxian.core;

/**
 * 灾劫载体的血量口径。
 *
 * <p>The witnessed texts name the calamities and their attacks but state no health numbers, so all
 * of these figures are game parameters. Health is deliberately in the millions: a calamity must
 * survive ordinary weapons for a long fight instead of dying to one swing.</p>
 *
 * <p>A player strike is multiplied by {@link #PLAYER_DAMAGE_SCALE} before it is applied, so a normal
 * sword still visibly drains a million-point pool.</p>
 */
public final class CalamityCarrierStats {
    private CalamityCarrierStats() {}

    /** Minimum health of any calamity carrier. */
    public static final double MINIMUM_HEALTH = 1_000_000.0;

    /** Player weapon damage is amplified by this factor when striking a carrier. */
    public static final double PLAYER_DAMAGE_SCALE = 1000.0;

    /** Full health of one calamity carrier. */
    public static double maxHealth(AscensionProgress.Tribulation.Type type) {
        // The base mod's AttributeFix raises generic.max_health to exactly 1,000,000, so a larger
        // figure would be silently clamped and the nameplate would stop matching the real health.
        double health = switch (type) {
            case JADE_WILLOW_WIND -> 1_000_000.0;
            case SINGLE_HORNED_TIGER -> 1_000_000.0;
            case SOUND_CHIMES -> 1_000_000.0;
            case ANCIENT_BEAST -> 1_000_000.0;
            case BLOOD_RAIN -> 1_000_000.0;
            case NONE -> 1_000_000.0;
        };
        return Math.max(MINIMUM_HEALTH, health);
    }

    /** Health removed from a carrier by one player strike of {@code weaponDamage}. */
    public static double damageFromStrike(double weaponDamage) {
        if (!Double.isFinite(weaponDamage) || weaponDamage <= 0) return PLAYER_DAMAGE_SCALE;
        return weaponDamage * PLAYER_DAMAGE_SCALE;
    }

    /** Compact display form, for example 1.50M. */
    public static String describe(double health) {
        if (health >= 1_000_000.0) return String.format("%.2fM", health / 1_000_000.0);
        if (health >= 1_000.0) return String.format("%.1fK", health / 1_000.0);
        return Long.toString(Math.round(health));
    }
}
