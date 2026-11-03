package net.guzhenren.shengxian;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client preferences, safe to load on either physical side; contains no client classes. */
public final class ShengxianVisualConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED, SCREEN, REDUCED, CINEMATIC, LOW_PERFORMANCE;
    private static final ModConfigSpec.IntValue DETAIL;
    private static final ModConfigSpec.BooleanValue PARTICLES;
    static {
        var b = new ModConfigSpec.Builder();
        b.push("visuals");
        PARTICLES = b.comment("Legacy Photon sprites; solid volumetric scenes are the default.").define("legacyParticleEffects", false);
        ENABLED = b.comment("Enable ascension world effects.").define("enabled", true);
        SCREEN = b.comment("Enable local ascender ink post-processing.").define("screenEffects", true);
        REDUCED = b.comment("Use slower flows and weaker screen blending.").define("reducedMotion", false);
        CINEMATIC = b.comment("Enable large sky vortices, seals and the layered aperture vision.").define("cinematicEffects", true);
        LOW_PERFORMANCE = b.comment("Limit geometry detail and render at most two nearby rituals.").define("lowPerformance", false);
        DETAIL = b.comment("Ribbon segments; affects client geometry cost only.")
                .defineInRange("segments", 32, 12, 48);
        SPEC = b.pop().build();
    }
    private ShengxianVisualConfig() {}
    public static boolean particles() { return SPEC.isLoaded() && PARTICLES.get(); }
    public static boolean enabled() { return !SPEC.isLoaded() || ENABLED.get(); }
    public static boolean screen() { return !SPEC.isLoaded() || SCREEN.get(); }
    public static boolean reduced() { return SPEC.isLoaded() && REDUCED.get(); }
    public static int segments() { return lowPerformance() ? 16 : SPEC.isLoaded() ? DETAIL.get() : 32; }
    public static boolean cinematic() { return !SPEC.isLoaded() || CINEMATIC.get(); }
    public static boolean lowPerformance() { return SPEC.isLoaded() && LOW_PERFORMANCE.get(); }
}
