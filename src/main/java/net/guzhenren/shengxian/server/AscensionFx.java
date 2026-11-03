package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.network.FxPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Small server-side bridge that sends visual cues to nearby clients. */
public final class AscensionFx {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");

    private AscensionFx() {}

    public static void send(ServerLevel level, Vec3 pos, String effect, float intensity) {
        send(level, pos, -1, effect, intensity);
    }

    public static void send(ServerLevel level, Vec3 pos, int entityId, String effect, float intensity) {
        LOGGER.debug("shengxian-fx-send {} entity={} at {},{},{}", effect, entityId, pos.x, pos.y, pos.z);
        // Server-side audio depth for the same event; the client keeps the foreground cue.
        AscensionSounds.fx(level, pos, effect, intensity);
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, 64.0,
                new FxPayload(effect, entityId, pos.x, pos.y, pos.z, intensity));
    }
}
