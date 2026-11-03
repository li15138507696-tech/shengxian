package net.guzhenren.shengxian.core;

/**
 * 灾劫载体的显示名，服务端写进盔甲架的定制名，客户端据此识别特效类型。
 *
 * <p>Entity scoreboard tags are not synced to clients, so the carrier type cannot ride on a tag.
 * The custom name is synced, so both sides share this one table instead of duplicating strings.</p>
 */
public final class CarrierLabels {
    private CarrierLabels() {}

    /** Human-readable calamity name, also the key the client matches on. */
    public static String label(AscensionProgress.Tribulation.Type type) {
        return switch (type) {
            case JADE_WILLOW_WIND -> "柳风天灾";
            case SINGLE_HORNED_TIGER -> "独角峥虎地劫";
            case SOUND_CHIMES -> "音磬天灾";
            case ANCIENT_BEAST -> "荒兽地劫";
            case BLOOD_RAIN -> "血雨灾劫";
            case NONE -> "灾劫载体";
        };
    }

    /** Resolves the type from a carrier custom name, or null when it is not a carrier name. */
    public static AscensionProgress.Tribulation.Type fromName(String customName) {
        if (customName == null) return null;
        for (AscensionProgress.Tribulation.Type type : AscensionProgress.Tribulation.Type.values()) {
            if (customName.startsWith(label(type))) return type;
        }
        return null;
    }
}
