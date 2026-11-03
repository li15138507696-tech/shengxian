package net.guzhenren.shengxian.core;

import java.util.Objects;

/**
 * CANON-012 太白云生个例：仙窍形成期间，其内部也酝酿出另一场天劫地灾。
 *
 * <p>The witnessed text says leftover heaven and earth qi that could not be absorbed are pushed
 * outside and later raise calamities again, and that while the aperture is forming another
 * heaven/earth calamity is brewing <em>inside</em> it. That second, inner calamity is what this
 * class models: once the aperture exists, leftover ambient qi condenses into an inner carrier
 * anchored inside the owner's aperture space.</p>
 *
 * <p>Individual case only: the text presents this for Taibai Shouchun's ascension, so brewing is
 * restricted to {@link TribulationParameters.Profile#TAI_BAI_WITNESSED}. A general ordinary-route
 * rule is still unverified (GAP-001).</p>
 *
 * <p>The carrier reuses the ordinary carrier machinery: an anchored, hit-target entity that is
 * struck once and recorded as survived. Its id carries the {@link #PREFIX} marker so the world
 * layer can tell an inner carrier (aperture dimension, immortal phase) from an outer one
 * (absorption phase).</p>
 */
public final class InnerTribulation {
    /** Id prefix that marks a carrier as living inside the formed aperture. */
    public static final String PREFIX = "inner-";
    /** Ledger key written once the inner carrier is destroyed; append-only. */
    public static final String SURVIVED_SUFFIX = ":survived";

    private InnerTribulation() {}

    public static boolean isInner(String carrierId) {
        return carrierId != null && carrierId.startsWith(PREFIX);
    }

    /**
     * No individual-case gate: the inner calamity follows the leftover qi itself, so any
     * ascension that leaves enough unabsorbed qi brews one. The witnessed example supplies the
     * carrier vocabulary, not a permission (project decision, see canon-rules 项目决策).
     */
    public static boolean profileAllows(TribulationParameters parameters) {
        return parameters != null;
    }

    /**
     * Breaks leftover ambient qi into one inner carrier.
     *
     * <p>Returns the same progress when the case does not apply: aperture not formed, another
     * carrier is already active, the profile is not the witnessed one, the leftover qi is below
     * half the trigger threshold, or an inner carrier already survived once.</p>
     */
    public static AscensionProgress brew(AscensionProgress current, long serverTick,
                                         TribulationParameters parameters,
                                         AscensionProgress.CarrierAnchor anchor) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(anchor, "anchor");
        if (!current.aperture().formed() || !current.tribulations().isEmpty()) return current;
        if (!profileAllows(parameters)) return current;
        if (alreadySurvived(current)) return current;
        if (!anchor.placed()) return current;
        double threshold = parameters.triggerAmbientTotal() / 2.0;
        AscensionProgress.Qi qi = current.qi();
        boolean heavenFuels = qi.ambientHeaven() >= threshold;
        boolean earthFuels = qi.ambientEarth() >= threshold;
        if (!heavenFuels && !earthFuels) return current;
        // The leftover qi that could not be absorbed is what fuels the inner calamity.
        var type = heavenFuels ? AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND
                : AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER;
        int units = heavenFuels ? parameters.heavenCarrierUnits() : parameters.earthCarrierUnits();
        int interval = heavenFuels ? parameters.heavenImpactInterval() : parameters.earthImpactInterval();
        AscensionProgress.Qi consumed = new AscensionProgress.Qi(qi.heaven(), qi.earth(), qi.human(),
                heavenFuels ? Math.max(0.0, qi.ambientHeaven() - threshold) : qi.ambientHeaven(),
                earthFuels && !heavenFuels ? Math.max(0.0, qi.ambientEarth() - threshold) : qi.ambientEarth(),
                qi.humanReserve());
        AscensionProgress.Tribulation carrier = new AscensionProgress.Tribulation(
                PREFIX + serverTick, type, units, interval, anchor);
        return current.withQi(consumed).withTribulations(java.util.List.of(carrier));
    }

    /** True when this ledger already resolved an inner carrier. */
    public static boolean alreadySurvived(AscensionProgress current) {
        Objects.requireNonNull(current, "current");
        for (String key : current.appliedEffects()) {
            if (key.startsWith("tribulation:" + PREFIX) && key.endsWith(SURVIVED_SUFFIX)) return true;
        }
        return false;
    }
}
