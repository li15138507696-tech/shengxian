package net.guzhenren.shengxian.client;

import net.guzhenren.shengxian.core.AscensionProgress;
import net.guzhenren.shengxian.core.CarrierLabels;

/**
 * Client-safe carrier识别：定制名会被同步，记分板 tag 不会。
 *
 * <p>Entity tags never reach the client, so the carrier type has to be read from the synced custom
 * name through the shared {@link CarrierLabels} table.</p>
 */
final class CarrierTagReader {
    private CarrierTagReader() {}

    /** Returns the carrier type from the synced custom name, or null when this is not a carrier. */
    static AscensionProgress.Tribulation.Type readType(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.decoration.ArmorStand stand)) return null;
        var name = stand.getCustomName();
        return name == null ? null : CarrierLabels.fromName(name.getString());
    }
}
