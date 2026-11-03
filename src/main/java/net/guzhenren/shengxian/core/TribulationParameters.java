package net.guzhenren.shengxian.core;

import java.util.Objects;

/** Game-mapped timing and carrier durability for explicitly named ascension examples. */
public record TribulationParameters(
        double triggerAmbientTotal,
        int heavenCarrierUnits,
        int heavenImpactInterval,
        int earthCarrierUnits,
        int earthImpactInterval,
        double impactRisk,
        Profile profile) {

    public enum Profile {
        /** Internal marker used only until the first server simulation step freezes a choice. */
        NOT_FROZEN,
        /** No verified general calamity catalogue exists for arbitrary ordinary ascensions. */
        UNRESOLVED_ORDINARY,
        /** Taibai Shouchun's witnessed weather-willow and earth-tiger example. */
        TAI_BAI_WITNESSED,
        /** Hei Loulan's witnessed eight-chime event followed by an ancient-beast earth tribulation. */
        BLACK_LOU_LAN_WITNESSED,
        /**
         * Fang Zheng's witnessed blood-rain example (CANON-013). The text converts both qi
         * sources into blood qi and blood rain under an arranged blood-dao setting, so this
         * profile stays an individual case and never a general ordinary-route rule (GAP-001).
         */
        FANG_ZHENG_WITNESSED,
        /**
         * CANON-007: "十绝升仙，天灾地劫非常恐怖". The text states the calamities are far worse but
         * gives no quantities, so this profile is a harsher game mapping chosen by the player's
         * physique at preparation time — never an operator setting, and never a canon figure.
         */
        TEN_EXTREMES,
        /** Compatibility profile retained only for old framework fixtures. */
        LEGACY_TEST_FIXTURE
    }

    /** Existing six-argument test fixtures retain their historical domain behavior. */
    public TribulationParameters(double triggerAmbientTotal, int heavenCarrierUnits,
                                 int heavenImpactInterval, int earthCarrierUnits,
                                 int earthImpactInterval, double impactRisk) {
        this(triggerAmbientTotal, heavenCarrierUnits, heavenImpactInterval, earthCarrierUnits,
                earthImpactInterval, impactRisk, Profile.LEGACY_TEST_FIXTURE);
    }

    public TribulationParameters {
        if (!Double.isFinite(triggerAmbientTotal) || triggerAmbientTotal <= 0) {
            throw new IllegalArgumentException("triggerAmbientTotal must be positive");
        }
        if (heavenCarrierUnits <= 0 || heavenImpactInterval <= 0
                || earthCarrierUnits <= 0 || earthImpactInterval <= 0) {
            throw new IllegalArgumentException("Invalid carrier parameters");
        }
        if (!Double.isFinite(impactRisk) || impactRisk <= 0) {
            throw new IllegalArgumentException("impactRisk must be positive");
        }
        Objects.requireNonNull(profile, "profile");
    }

    /** Default profile stays inert until a general ordinary-route catalogue is verified. */
    public static TribulationParameters standard() {
        return new TribulationParameters(60.0, 8, 20, 3, 40, 2.0,
                Profile.UNRESOLVED_ORDINARY);
    }

    /** Creates only a production profile; internal and legacy test profiles are rejected. */
    public static TribulationParameters forProfile(Profile profile) {
        Objects.requireNonNull(profile, "profile");
        return switch (profile) {
            case UNRESOLVED_ORDINARY -> standard();
            case TAI_BAI_WITNESSED -> taiBaiWitnessed();
            case BLACK_LOU_LAN_WITNESSED -> heiLoulanWitnessed();
            case FANG_ZHENG_WITNESSED -> fangZhengWitnessed();
            case TEN_EXTREMES -> tenExtremes();
            case NOT_FROZEN, LEGACY_TEST_FIXTURE ->
                    throw new IllegalArgumentException("Profile is not selectable in production");
        };
    }

    /** Parses a server setting conservatively; invalid or internal names resolve to the inert profile. */
    public static Profile configuredProfile(String value) {
        if (value == null || value.isBlank()) return Profile.UNRESOLVED_ORDINARY;
        try {
            Profile profile = Profile.valueOf(value);
            return switch (profile) {
                case UNRESOLVED_ORDINARY, TAI_BAI_WITNESSED, BLACK_LOU_LAN_WITNESSED, FANG_ZHENG_WITNESSED -> profile;
                // Physique-driven, so an operator cannot select it by name; an operator typing it
                // falls back to the inert profile instead of silently changing a route's difficulty.
                case TEN_EXTREMES, NOT_FROZEN, LEGACY_TEST_FIXTURE -> Profile.UNRESOLVED_ORDINARY;
            };
        } catch (IllegalArgumentException ex) {
            return Profile.UNRESOLVED_ORDINARY;
        }
    }

    public static TribulationParameters taiBaiWitnessed() {
        return new TribulationParameters(60.0, 8, 20, 3, 40, 2.0,
                Profile.TAI_BAI_WITNESSED);
    }

    public static TribulationParameters heiLoulanWitnessed() {
        return new TribulationParameters(60.0, 8, 20, 3, 40, 2.0,
                Profile.BLACK_LOU_LAN_WITNESSED);
    }

    /**
     * Fang Zheng's individual blood-rain case (CANON-013). Carrier units and impact timing
     * reuse the shared game mapping; they are not canon quantities for the blood rain.
     */
    public static TribulationParameters fangZhengWitnessed() {
        return new TribulationParameters(60.0, 8, 20, 3, 40, 2.0,
                Profile.FANG_ZHENG_WITNESSED);
    }

    /**
     * CANON-007 ten-extremes calamities. The text only says they are far more terrifying and gives
     * no quantities, so every number here is a game mapping deliberately harsher than
     * {@link #standard()}: the ambient threshold is lower (the calamity forms sooner), there are
     * more carriers, they strike on shorter intervals, and each impact is riskier.
     */
    public static TribulationParameters tenExtremes() {
        return new TribulationParameters(45.0, 12, 15, 5, 30, 3.0, Profile.TEN_EXTREMES);
    }
}
