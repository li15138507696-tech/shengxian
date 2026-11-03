package net.guzhenren.shengxian.core;

import net.guzhenren.shengxian.core.AscensionProgress.Tribulation.Type;

import java.util.Objects;

/** Stable cue identifiers for client-side calamity feedback. */
public final class TribulationFx {
    private TribulationFx() {}

    public static String impactCue(Type type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case JADE_WILLOW_WIND -> "tribulation_impact";
            case SINGLE_HORNED_TIGER -> "tribulation_impact";
            case SOUND_CHIMES -> "tribulation_impact_sound";
            case ANCIENT_BEAST -> "tribulation_impact_beast";
            case BLOOD_RAIN -> "tribulation_impact_blood_rain";
            case NONE -> "tribulation_impact";
        };
    }
}