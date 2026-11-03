package net.guzhenren.shengxian.client;

import net.guzhenren.shengxian.core.AscensionProgress;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灾劫特效入口：Photon 特效工坊为主，原版粒子兜底。
 *
 * <p>This class deliberately imports nothing from Photon. Photon is an optional runtime mod, so a
 * hard reference here would make this class itself unloadable without it and the fallback could
 * never run. The Photon-touching implementation lives in {@link #BRIDGE_CLASS} and is only
 * instantiated by reflection after the Photon class probe succeeds; if anything fails, every call
 * becomes a no-op and the server-side vanilla choreography stays the visual.</p>
 */
public final class TribulationFxWorkshop {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final String BRIDGE_CLASS =
            "net.guzhenren.shengxian.client.PhotonTribulationFx";

    private static final Map<UUID, AscensionProgress.Tribulation.Type> ACTIVE = new ConcurrentHashMap<>();
    private static Object bridge;
    private static boolean bridgeResolved;

    private TribulationFxWorkshop() {}

    /** True when Photon is installed and its bridge loaded successfully. */
    public static boolean photonAvailable() {
        return bridge() != null;
    }

    /** Starts or refreshes the effect for one carrier; a no-op without Photon. */
    public static void play(UUID carrierId, Level level, Vec3 pos, AscensionProgress.Tribulation.Type type) {
        Object impl = bridge();
        if (impl == null) return;
        AscensionProgress.Tribulation.Type current = ACTIVE.get(carrierId);
        if (current == type) return;
        ACTIVE.put(carrierId, type);
        LOGGER.info("Tribulation Photon effect started: {} for carrier {}", type, carrierId);
        try {
            impl.getClass()
                    .getMethod("play", UUID.class, Level.class, Vec3.class, AscensionProgress.Tribulation.Type.class)
                    .invoke(impl, carrierId, level, pos, type);
        } catch (ReflectiveOperationException failed) {
            disable(failed);
        }
    }

    /** Stops one carrier's effect (strike, expiry, or chunk unload). */
    public static void stop(UUID carrierId) {
        ACTIVE.remove(carrierId);
        Object impl = bridge();
        if (impl == null) return;
        try {
            impl.getClass().getMethod("stop", UUID.class).invoke(impl, carrierId);
        } catch (ReflectiveOperationException failed) {
            disable(failed);
        }
    }

    /** Retires every effect whose carrier was not seen in the latest client scan. */
    public static void retainAll(Set<UUID> seen) {
        for (UUID id : ACTIVE.keySet().toArray(new UUID[0])) {
            if (!seen.contains(id)) stop(id);
        }
    }

    /** Per-tick follow-up so each effect tracks its carrier. */
    public static void tick() {
        Object impl = bridge();
        if (impl == null || ACTIVE.isEmpty()) return;
        try {
            impl.getClass().getMethod("tick").invoke(impl);
        } catch (ReflectiveOperationException failed) {
            disable(failed);
        }
    }

    /** Resolves the Photon bridge once; a missing Photon is a normal, silent outcome. */
    private static Object bridge() {
        if (bridgeResolved) return bridge;
        bridgeResolved = true;
        try {
            Class.forName("com.lowdragmc.photon.client.fx.FX");
            bridge = Class.forName(BRIDGE_CLASS).getDeclaredConstructor().newInstance();
            LOGGER.info("Tribulation Photon workshop active");
        } catch (ReflectiveOperationException | LinkageError absent) {
            bridge = null;
            LOGGER.info("Photon absent; tribulation effects fall back to server-side particles");
        }
        return bridge;
    }

    /** After any reflective failure the workshop stays off for this session. */
    private static void disable(Throwable failed) {
        bridge = null;
        ACTIVE.clear();
        LOGGER.warn("Tribulation Photon workshop disabled; falling back to vanilla particles", failed);
    }
}
