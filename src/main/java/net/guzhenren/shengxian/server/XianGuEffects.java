package net.guzhenren.shengxian.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Small local effects with an independent terrain particle budget.
 *
 * <p>Sounds go through {@link AscensionSounds} so the three immortal gu keep distinct vanilla
 * timbres: 定仙游 (travel/channel), 人如故 (heal) and 江山如故 (terrain). The two hot paths -
 * {@link #channel} every other tick and {@link #terrain} once per restored block - are throttled
 * inside that layer rather than by the particle budget, which used to let the terrain rustle fire
 * several times per tick.
 */
public final class XianGuEffects {
    private static long terrainTick = -1;
    private static int terrainParticles;
    private XianGuEffects() { }
    private static void dust(ServerLevel level, Vec3 pos, float r, float g, float b, float size) {
        level.sendParticles(new DustParticleOptions(new Vector3f(r, g, b), size),
                pos.x, pos.y, pos.z, 1, 0.015, 0.015, 0.015, 0);
    }
    public static void channel(ServerPlayer player, long age) {
        for (int wing = 0; wing < 2; wing++) {
            double angle = age * 0.19 + wing * Math.PI;
            dust(player.serverLevel(), player.position().add(Math.cos(angle) * 0.65,
                    0.8 + Math.sin(age * 0.1) * 0.3, Math.sin(angle) * 0.65), 0.08f, 0.5f, 0.25f, 0.7f);
        }
        AscensionSounds.guChannel(player.serverLevel(), player.position());
    }
    public static void travel(ServerLevel level, Vec3 position, boolean arriving) {
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 8, radius = 0.15 + i / 48.0;
            dust(level, position.add(Math.cos(a) * radius, i / 24.0, Math.sin(a) * radius),
                    0.08f, 0.65f, 0.3f, 0.8f);
        }
        AscensionSounds.guTravel(level, position, arriving);
    }
    public static void heal(ServerPlayer target) {
        float[][] colors = {{0.7f,0.15f,0.3f},{0.7f,0.55f,0.12f},{0.15f,0.55f,0.25f},{0.12f,0.4f,0.7f},{0.5f,0.2f,0.65f}};
        for (int i = 0; i < 35; i++) {
            float[] c = colors[i % 5]; double a = i * 0.8;
            dust(target.serverLevel(), target.position().add(Math.cos(a) * 0.65, i / 25.0 + 0.2,
                    Math.sin(a) * 0.65), c[0], c[1], c[2], 0.65f);
        }
        AscensionSounds.guHeal(target.serverLevel(), target.position());
    }
    public static void terrain(ServerLevel level, BlockPos pos) {
        long tick = level.getServer().overworld().getGameTime();
        if (terrainTick != tick) { terrainTick = tick; terrainParticles = 0; }
        if (terrainParticles++ >= 64) return;
        dust(level, Vec3.atCenterOf(pos).add(0, 0.2, 0), 0.25f, 0.42f, 0.16f, 0.7f);
        AscensionSounds.guTerrain(level, Vec3.atCenterOf(pos));
    }
}
