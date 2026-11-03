package net.guzhenren.shengxian;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.guzhenren.shengxian.core.TribulationParameters;

import java.util.List;

/** Server-owned gameplay mapping values; the 0.2.5 ascension gate keeps these prototypes inactive. */
public final class ShengxianConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue PARTICLE_AURAS;
    public static final ModConfigSpec.IntValue SHATTER_TICKS;
    public static final ModConfigSpec.DoubleValue SHATTER_ESSENCE_FRACTION;
    public static final ModConfigSpec.IntValue QI_TICK_INTERVAL;
    public static final ModConfigSpec.BooleanValue ALLOW_GUESTS;
    public static final ModConfigSpec.BooleanValue ALLOW_GUARDIANS;
    public static final ModConfigSpec.BooleanValue IMMORTAL_INFINITE_TRUE_ESSENCE;
    public static final ModConfigSpec.DoubleValue APERTURE_UPKEEP;
    public static final ModConfigSpec.IntValue GU_BACKLASH_THRESHOLD;
    public static final ModConfigSpec.BooleanValue REQUIRE_UPSTREAM_APERTURE_WRITE;
    public static final ModConfigSpec.ConfigValue<String> TRIBULATION_PROFILE;
    public static final ModConfigSpec.BooleanValue CALAMITY_COMBAT;
    public static final ModConfigSpec.DoubleValue CALAMITY_DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue TRAVEL_GU_COST, HEAL_GU_COST, LANDSCAPE_GU_COST;
    public static final ModConfigSpec.IntValue TRAVEL_GU_COOLDOWN, HEAL_GU_COOLDOWN, LANDSCAPE_GU_COOLDOWN;
    public static final ModConfigSpec.IntValue TRAVEL_GU_CHANNEL, HEAL_GU_RANGE, HEAL_GU_HISTORY;
    public static final ModConfigSpec.IntValue LANDSCAPE_GU_RANGE, LANDSCAPE_GU_BATCH, LANDSCAPE_GU_HISTORY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Ordinary ascension gameplay mapping. These are game parameters, not canon formulas.")
                .push("ascension");
        PARTICLE_AURAS = builder.comment("Legacy decorative disaster particles; solid client scenes replace them by default.").define("legacyParticleAuras", false);
        SHATTER_TICKS = builder.comment("Ticks the shatter attempt takes before it completes.")
                .defineInRange("shatterWaitTicks", 100, 20, 1200);
        SHATTER_ESSENCE_FRACTION = builder.comment("Fraction of maximum true essence charged once when shattering starts.")
                .defineInRange("shatterEssenceFraction", 0.10, 0.01, 1.0);
        QI_TICK_INTERVAL = builder.comment("Server ticks between absorption simulation steps.")
                .defineInRange("qiTickInterval", 20, 1, 200);
        ALLOW_GUESTS = builder.comment("Allow owners to invite ordinary visitors.")
                .define("allowGuests", false);
        ALLOW_GUARDIANS = builder.comment("Allow owners to appoint guardians.")
                .define("allowGuardians", false);
        IMMORTAL_INFINITE_TRUE_ESSENCE = builder.comment(
                        "Refill a formed immortal's true essence to its upstream maximum every second.",
                        "Game mapping: costs are still charged, then the current value refills.")
                .define("immortalInfiniteTrueEssence", true);
        APERTURE_UPKEEP = builder.comment(
                        "Immortal essence charged per internal tick while the aperture is running.",
                        "Game parameter only: no read chapter states a running cost for a blessed land.")
                .defineInRange("apertureUpkeepPerInternalTick", 0.004, 0.0, 1.0);
        GU_BACKLASH_THRESHOLD = builder.comment(
                        "Confirmed Gu activations during absorption required before one source Gu is lost.",
                        "Game balance parameter, not a canon probability or formula.")
                .defineInRange("guBacklashThreshold", 8, 1, 64);
        // Plain string on purpose: defineInList validates through List.contains, and NeoForge probes
        // that validator with null for a key that is absent from an existing file, which throws
        // ImmutableCollections.indexOf(null). The accessor maps unknown values to the inert profile.
        REQUIRE_UPSTREAM_APERTURE_WRITE = builder.comment(
                        "Demand a write of the aperture into the base mod's player data before the route opens (strict mode).",
                        "UPSTREAM-010 found no upstream player field for an immortal aperture or its essence, reconfirmed 2026-11-02,",
                        "so the module keeps its own world-side ledger (PROJECT-003) and the ordinary route is open by default.",
                        "Default false. Setting true restores the old blocked gate; no configuration can satisfy it.")
                .define("requireUpstreamApertureWrite", false);
        TRIBULATION_PROFILE = builder.comment(
                        "Select one individually witnessed calamity example. Ordinary ascensions remain unresolved by default.",
                        "Allowed values: UNRESOLVED_ORDINARY, TAI_BAI_WITNESSED, BLACK_LOU_LAN_WITNESSED, FANG_ZHENG_WITNESSED.",
                        "This is a game mapping, not a general canon calamity catalogue.")
                .define("tribulationProfile", "UNRESOLVED_ORDINARY");
        CALAMITY_COMBAT = builder.comment(
                        "Let calamity carriers run their attack rotation and damage participants.",
                        "Game parameter: the witnessed texts describe the attacks but state no damage values.")
                .define("calamityCombat", true);
        CALAMITY_DAMAGE_SCALE = builder.comment(
                        "Extra multiplier applied on top of the base calamity damage table.",
                        "The base table already carries the user-requested x500 scale; this scales it further.")
                .defineInRange("calamityDamageScale", 1.0, 0.0, 1000.0);
        builder.push("immortalGu");
        TRAVEL_GU_COST = builder.defineInRange("fixedTravelCost", 3.0, 0.001, 1_000_000);
        HEAL_GU_COST = builder.defineInRange("manAsBeforeCost", 2.0, 0.001, 1_000_000);
        LANDSCAPE_GU_COST = builder.defineInRange("landscapeAsBeforeCost", 5.0, 0.001, 1_000_000);
        TRAVEL_GU_COOLDOWN = builder.defineInRange("fixedTravelCooldownTicks", 200, 0, 72000);
        HEAL_GU_COOLDOWN = builder.defineInRange("manAsBeforeCooldownTicks", 60, 0, 72000);
        LANDSCAPE_GU_COOLDOWN = builder.defineInRange("landscapeAsBeforeCooldownTicks", 200, 0, 72000);
        TRAVEL_GU_CHANNEL = builder.defineInRange("fixedTravelChannelTicks", 60, 20, 1200);
        HEAL_GU_RANGE = builder.defineInRange("manAsBeforeRange", 24, 1, 64);
        HEAL_GU_HISTORY = builder.defineInRange("manAsBeforeHistoryTicks", 100, 1, 100);
        LANDSCAPE_GU_RANGE = builder.defineInRange("landscapeAsBeforeRange", 32, 1, 64);
        LANDSCAPE_GU_BATCH = builder.defineInRange("landscapeAsBeforeBatch", 1024, 1, 1024);
        LANDSCAPE_GU_HISTORY = builder.defineInRange("landscapeAsBeforeHistoryTicks", 12000, 20, 12000);
        SPEC = builder.pop().pop().build();
    }

    private ShengxianConfig() {}

    public static int shatterTicks() {
        return SPEC.isLoaded() ? SHATTER_TICKS.get() : 100;
    }

    public static double shatterEssenceFraction() {
        return SPEC.isLoaded() ? SHATTER_ESSENCE_FRACTION.get() : 0.10;
    }

    public static boolean particleAuras() { return SPEC.isLoaded() && PARTICLE_AURAS.get(); }

    public static int qiTickInterval() {
        return SPEC.isLoaded() ? QI_TICK_INTERVAL.get() : 20;
    }

    public static boolean allowGuests() {
        return !SPEC.isLoaded() || ALLOW_GUESTS.get();
    }

    public static boolean allowGuardians() {
        return !SPEC.isLoaded() || ALLOW_GUARDIANS.get();
    }

    public static boolean immortalInfiniteTrueEssence() {
        return !SPEC.isLoaded() || IMMORTAL_INFINITE_TRUE_ESSENCE.get();
    }

    /** Balance parameter for the aperture ledger sink; never presented as a canon number. */
    public static double apertureUpkeepPerInternalTick() {
        return SPEC.isLoaded() ? APERTURE_UPKEEP.get() : 0.004;
    }

    /**
     * Whether an operator demands the impossible upstream aperture write (strict mode, PROJECT-003).
     * False by default: the module's own ledger is the recorded substitution, so the ordinary route
     * opens without pretending the base mod stores an aperture it has no field for.
     */
    public static boolean requireUpstreamApertureWrite() {
        return SPEC.isLoaded() && REQUIRE_UPSTREAM_APERTURE_WRITE.get();
    }

    /** Server-side cumulative backlash threshold; the value is a game parameter. */
    public static int guBacklashThreshold() {
        return SPEC.isLoaded() ? GU_BACKLASH_THRESHOLD.get() : 8;
    }

    /** Whether carriers execute attacks; an unloaded config keeps the feature on. */
    public static boolean calamityCombat() {
        return !SPEC.isLoaded() || CALAMITY_COMBAT.get();
    }

    /** Additional scale on top of the base calamity damage table. */
    public static double calamityDamageScale() {
        return SPEC.isLoaded() ? CALAMITY_DAMAGE_SCALE.get() : 1.0;
    }

    /** Server-owned profile selection; an unavailable config resolves to the inert default. */
    public static TribulationParameters.Profile tribulationProfile() {
        return TribulationParameters.configuredProfile(
                SPEC.isLoaded() ? TRIBULATION_PROFILE.get() : "UNRESOLVED_ORDINARY");
    }

    public static double immortalGuCost(String id) {
        return switch (id) {
            case "ding_xian_you" -> SPEC.isLoaded() ? TRAVEL_GU_COST.get() : 3.0;
            case "ren_ru_gu_immortal" -> SPEC.isLoaded() ? HEAL_GU_COST.get() : 2.0;
            case "jiang_shan_ru_gu" -> SPEC.isLoaded() ? LANDSCAPE_GU_COST.get() : 5.0;
            default -> throw new IllegalArgumentException("Unknown reusable immortal Gu");
        };
    }
    public static int immortalGuCooldown(String id) {
        return switch (id) {
            case "ding_xian_you" -> SPEC.isLoaded() ? TRAVEL_GU_COOLDOWN.get() : 200;
            case "ren_ru_gu_immortal" -> SPEC.isLoaded() ? HEAL_GU_COOLDOWN.get() : 60;
            case "jiang_shan_ru_gu" -> SPEC.isLoaded() ? LANDSCAPE_GU_COOLDOWN.get() : 200;
            default -> throw new IllegalArgumentException("Unknown reusable immortal Gu");
        };
    }
    public static int travelGuChannel() { return SPEC.isLoaded() ? TRAVEL_GU_CHANNEL.get() : 60; }
    public static int healingGuRange() { return SPEC.isLoaded() ? HEAL_GU_RANGE.get() : 24; }
    public static int healingGuHistory() { return SPEC.isLoaded() ? HEAL_GU_HISTORY.get() : 100; }
    public static int landscapeGuRange() { return SPEC.isLoaded() ? LANDSCAPE_GU_RANGE.get() : 32; }
    public static int landscapeGuBatch() { return SPEC.isLoaded() ? LANDSCAPE_GU_BATCH.get() : 1024; }
    public static int landscapeGuHistory() { return SPEC.isLoaded() ? LANDSCAPE_GU_HISTORY.get() : 12000; }

}