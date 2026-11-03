package net.guzhenren.shengxian.client;

import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 特效工坊实现：灾劫载体的 Photon 特效。
 *
 * <p>Loaded only by {@link TribulationFxWorkshop} through reflection, so nothing here is touched
 * when Photon is absent.</p>
 *
 * <p>Effects ride {@link EntityEffectExecutor}, the in-world executor Photon keeps in its own
 * {@code CACHE} map and ticks/follows every client tick. A plain {@code FXEffectExecutor} subclass
 * starts once but is never ticked by Photon, which showed up in testing as a handful of frozen
 * particles per carrier instead of a stream.</p>
 */
final class PhotonTribulationFx {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final String TEXTURE = "photon:textures/particle/circle.png";

    private final Map<UUID, EntityEffectExecutor> active = new ConcurrentHashMap<>();
    private int diagnoseTicks;

    // ---- called reflectively by TribulationFxWorkshop -------------------------------

    public void play(UUID carrierId, Level level, Vec3 pos, AscensionProgress.Tribulation.Type type) {
        EntityEffectExecutor previous = active.remove(carrierId);
        if (previous != null) previous.setForcedDeath(true);
        Entity entity = resolve(level, carrierId);
        if (entity == null) return;
        EntityEffectExecutor executor = new EntityEffectExecutor(build(type), level, entity,
                EntityEffectExecutor.AutoRotate.NONE);
        active.put(carrierId, executor);
        executor.start();
        LOGGER.info("Tribulation Photon effect started: {} for carrier {}", type, carrierId);
    }

    public void stop(UUID carrierId) {
        EntityEffectExecutor removed = active.remove(carrierId);
        if (removed != null) removed.setForcedDeath(true);
    }

    public void tick() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            active.values().forEach(executor -> executor.setForcedDeath(true));
            active.clear();
            return;
        }
        diagnose();
    }

    /** Resolves the carrier entity from the client's rendered list. */
    private static Entity resolve(Level level, UUID carrierId) {
        if (!(level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel)) return null;
        for (Entity entity : clientLevel.entitiesForRendering()) {
            if (entity.getUUID().equals(carrierId)) return entity;
        }
        return null;
    }

    /** Logs live particle counts for a short window; proves emission without looking at pixels. */
    private void diagnose() {
        if (active.isEmpty() || diagnoseTicks > 240) return;
        if (++diagnoseTicks % 40 != 0) return;
        for (Map.Entry<UUID, EntityEffectExecutor> entry : active.entrySet()) {
            var runtime = entry.getValue().getRuntime();
            if (runtime == null) continue;
            for (var object : runtime.objects.values()) {
                if (object instanceof com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter pe) {
                    LOGGER.info("Photon particle amount for {}: {}", entry.getKey(), pe.getParticleAmount());
                }
            }
        }
    }

    // ---- one effect per calamity type -----------------------------------------------

    private static FX build(AscensionProgress.Tribulation.Type type) {
        FX fx = new FX();
        switch (type) {
            case JADE_WILLOW_WIND -> fx.getFxData().objects().add(willow());
            case SINGLE_HORNED_TIGER -> {
                fx.getFxData().objects().add(tigerDust());
                fx.getFxData().objects().add(tigerHorn());
            }
            case SOUND_CHIMES -> fx.getFxData().objects().add(chimes());
            case ANCIENT_BEAST -> fx.getFxData().objects().add(beast());
            case BLOOD_RAIN -> fx.getFxData().objects().add(bloodRain());
            case NONE -> fx.getFxData().objects().add(willow());
        }
        return fx;
    }

    /** 柳风天灾：叶幕螺旋下坠。 */
    private static ParticleEmitter willow() {
        return emitter("willow_curtain", 240, 60,
                new RandomConstant(0.9f, 1.6f), new RandomConstant(0.15f, 0.35f),
                0.22f, 0xFF2E806F, 0xFF9FE8C8, 1.5f, 0.25f);
    }

    /** 独角峥虎：地面尘环。 */
    private static ParticleEmitter tigerDust() {
        return emitter("tiger_dust", 200, 45,
                new RandomConstant(0.6f, 1.1f), new RandomConstant(0.5f, 1.1f),
                0.34f, 0xFF6B5636, 0xFFC9A86A, 0.45f, 0.1f);
    }

    /** 峥虎独角：红色迸光。 */
    private static ParticleEmitter tigerHorn() {
        return emitter("tiger_horn", 120, 30,
                new RandomConstant(0.35f, 0.7f), new RandomConstant(0.05f, 0.15f),
                0.16f, 0xFFB24B3E, 0xFFFFC9A0, 0.14f, 1.0f);
    }

    /** 音磬天灾：八道金环扩散。 */
    private static ParticleEmitter chimes() {
        return emitter("chime_rings", 260, 70,
                new RandomConstant(0.7f, 1.2f), new RandomConstant(0.25f, 0.55f),
                0.14f, 0xFFC9A86A, 0xFFFFE9B8, 0.7f, 0.05f);
    }

    /** 荒兽地劫：幽蓝魂火盘旋。 */
    private static ParticleEmitter beast() {
        return emitter("beast_soulfire", 240, 65,
                new RandomConstant(0.8f, 1.5f), new RandomConstant(0.1f, 0.28f),
                0.26f, 0xFF1E3A5F, 0xFF6FC3E8, 1.1f, 0.4f);
    }

    /** 血雨灾劫：自上而下的血幕。 */
    private static ParticleEmitter bloodRain() {
        return emitter("blood_fall", 320, 90,
                new RandomConstant(0.7f, 1.3f), new RandomConstant(0.1f, 0.3f),
                0.18f, 0xFF94051A, 0xFFD84A5A, 2.2f, 0.2f);
    }

    /** Shared emitter skeleton: looping local-space particles with a sphere volume and a texture. */
    private static ParticleEmitter emitter(String name, int maxParticles, int rate,
                                           RandomConstant lifetime, RandomConstant speed,
                                           float size, int colorFrom, int colorTo,
                                           float radius, float thickness) {
        ParticleEmitter emitter = new ParticleEmitter();
        emitter.setName(name);
        emitter.config.setDuration(200);
        emitter.config.setLooping(true);
        float quality = net.guzhenren.shengxian.ShengxianVisualConfig.lowPerformance() ? .35f
                : net.guzhenren.shengxian.ShengxianVisualConfig.reduced() ? .65f : 1f;
        emitter.config.setMaxParticles(Math.max(16, (int)(maxParticles * quality)));
        emitter.config.setParallelUpdate(true);
        emitter.config.setSimulationSpace(ParticleConfig.Space.Local);
        emitter.config.setStartLifetime(lifetime);
        emitter.config.setStartSpeed(speed);
        emitter.config.setStartSize(new NumberFunction3(size, size, size));
        emitter.config.setStartColor(new Gradient(new GradientColor(colorFrom, colorTo)));
        emitter.config.emission.setEmissionMode(EmissionSetting.Mode.Random);
        emitter.config.emission.setEmissionRate(NumberFunction.constant(Math.max(1, (int)(rate * quality))));
        Sphere sphere = new Sphere();
        sphere.setRadius(radius);
        sphere.setRadiusThickness(thickness);
        emitter.config.shape.setShape(sphere);
        // Without a material the emitter simulates but draws nothing.
        emitter.config.renderer.getMaterials().add(new MaterialSetting(
                new com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial(
                        ResourceLocation.parse(TEXTURE))));
        return emitter;
    }
}
