package net.guzhenren.shengxian.client;

import net.guzhenren.shengxian.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.joml.Vector3f;

import java.util.Random;

/** Client-side rendering for server-owned ascension cues. */
public final class ClientFx {
    private static final Random RANDOM = new Random();
    private static int overlayTicks;
    private static int overlayColor;
    private static long clientTicks, particleTick=-1;
    private static int particlesLeft;
    private static Level fxWorld;
    private static boolean edgeOverlay;

    private ClientFx() {}

    public static void play(FxPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) return;
        syncWorld(level);
        if (!net.guzhenren.shengxian.ShengxianVisualConfig.enabled()) return;
        int range=net.guzhenren.shengxian.ShengxianVisualConfig.lowPerformance()?32:48;
        if(minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(
                new net.minecraft.world.phys.Vec3(payload.x(),payload.y(),payload.z()))>range*range) return;
        int previousTicks = overlayTicks, previousColor = overlayColor;
        boolean previousEdge = edgeOverlay;
        double x = payload.x();
        double y = payload.y();
        double z = payload.z();
        switch (payload.effect()) {
            case "tribulation_willow_start" -> {
                overlayTicks = 48;
                overlayColor = 0x222E806F;
                level.playLocalSound(x, y, z, SoundEvents.BEACON_ACTIVATE,
                        SoundSource.WEATHER, 0.7f, 1.4f, false);
                for (int i = 0; i < 54; i++) {
                    particle(level,ParticleTypes.HAPPY_VILLAGER,
                            x + spread(5), y + 1 + spread(3), z + spread(5),
                            spread(0.05), 0.08 + RANDOM.nextDouble() * 0.1, spread(0.05));
                    particle(level,ParticleTypes.CLOUD,
                            x + spread(4), y + spread(2), z + spread(4), 0, 0.03, 0);
                }
            }
            case "tribulation_tiger_start" -> {
                overlayTicks = 60;
                overlayColor = 0x334C3624;
                level.playLocalSound(x, y, z, SoundEvents.RAVAGER_ROAR,
                        SoundSource.HOSTILE, 0.9f, 0.8f, false);
                for (int i = 0; i < 48; i++) {
                    particle(level,ParticleTypes.CRIT,
                            x + spread(3), y + spread(1.5), z + spread(3),
                            spread(0.12), 0.03, spread(0.12));
                    particle(level,ParticleTypes.CLOUD,
                            x + spread(2), y + spread(1), z + spread(2), 0, 0.01, 0);
                }
            }
            case "tribulation_beast_start" -> {
                overlayTicks = 54;
                overlayColor = 0x22342B20;
                level.playLocalSound(x, y, z, SoundEvents.RAVAGER_ROAR,
                        SoundSource.HOSTILE, 0.62f, 0.72f, false);
                for (int i = 0; i < 10; i++) {
                    particle(level, ParticleTypes.ELECTRIC_SPARK,
                            x + spread(2.5), y + spread(1.0), z + spread(2.5),
                            spread(0.025), 0.02, spread(0.025));
                    particle(level, ParticleTypes.CLOUD,
                            x + spread(1.8), y - 0.15 + spread(0.55), z + spread(1.8), 0, 0.006, 0);
                }
            }
            case "tribulation_sound_start" -> {
                overlayTicks = 56;
                overlayColor = 0x22354255;
                level.playLocalSound(x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.WEATHER, 0.56f, 0.92f, false);
                for (int i = 0; i < 12; i++) {
                    double angle = Math.PI * 2.0 * i / 12.0;
                    particle(level, ParticleTypes.END_ROD,
                            x + Math.cos(angle) * 2.0, y + 0.45, z + Math.sin(angle) * 2.0,
                            Math.cos(angle) * 0.015, 0.01, Math.sin(angle) * 0.015);
                }
            }
            case "tribulation_start" -> {
                overlayTicks = 160;
                overlayColor = 0x55000000;
                level.playLocalSound(x, y, z, SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.WEATHER, 1.0f, 0.8f, false);
                for (int i = 0; i < 70; i++) {
                    particle(level,ParticleTypes.END_ROD,
                            x + spread(4), y + spread(3), z + spread(4), 0, 0.2, 0);
                }
            }
            case "tribulation_blood_rain_start" -> {
                overlayTicks = 72;
                overlayColor = 0x32920B18;
                level.playLocalSound(x, y, z, SoundEvents.WEATHER_RAIN,
                        SoundSource.WEATHER, 0.64f, 0.82f, false);
                DustParticleOptions bloodDust = new DustParticleOptions(
                        new Vector3f(0.58f, 0.025f, 0.075f), 0.76f);
                for (int i = 0; i < 28; i++) {
                    particle(level, bloodDust,
                            x + spread(5), y + 1 + RANDOM.nextDouble() * 3, z + spread(5),
                            spread(0.01), -0.11, spread(0.01));
                }
            }
            case "tribulation_impact",
                    "tribulation_impact_sound", "tribulation_impact_beast",
                    "tribulation_impact_blood_rain" -> {
                ClientAscensionEffects.impact(payload.entityId());
                playTribulationImpact(level, payload.effect(), x, y, z);
            }
            case "tribulation_fatal" -> {
                overlayTicks = 220;
                overlayColor = 0x88000000;
                level.playLocalSound(x, y, z, SoundEvents.WITHER_DEATH,
                        SoundSource.HOSTILE, 1.0f, 0.7f, false);
            }
            case "aperture_open" -> {
                startApertureTransition(minecraft, payload, true);
                level.playLocalSound(x, y, z, SoundEvents.BEACON_ACTIVATE,
                        SoundSource.BLOCKS, 1.0f, 1.2f, false);
                for (int i = 0; i < 60; i++) {
                    particle(level,ParticleTypes.PORTAL,
                            x + spread(5), y + spread(4), z + spread(5), 0, 0.15, 0);
                    particle(level,ParticleTypes.END_ROD,
                            x + spread(5), y + spread(4), z + spread(5), 0, 0.05, 0);
                }
            }
            case "aperture_close" -> {
                startApertureTransition(minecraft, payload, false);
                level.playLocalSound(x, y, z, SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 0.8f, 1.0f, false);
                for (int i = 0; i < 25; i++) {
                    particle(level,ParticleTypes.REVERSE_PORTAL,
                            x + spread(3), y + spread(2), z + spread(3), 0, 0.05, 0);
                }
            }
            case "qi", "qi_stop" -> { /* Phase stream owns all persistent auras. */ }
            default -> { }
        }
        if (payload.effect().startsWith("tribulation_")) {
            if (minecraft.player == null || payload.entityId() != minecraft.player.getId()) {
                overlayTicks = previousTicks; overlayColor = previousColor; edgeOverlay = previousEdge;
            } else {
                overlayTicks = Math.min(48, overlayTicks); edgeOverlay = true;
            }
        }
    }

    private static void playTribulationImpact(Level level, String cue, double x, double y, double z) {
        switch (cue) {
            case "tribulation_impact_sound" -> {
                overlayTicks = 36;
                overlayColor = 0x1E405070;
                level.playLocalSound(x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.WEATHER, 0.58f, 1.35f, false);
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * 2.0 * i / 8.0;
                    particle(level, ParticleTypes.END_ROD,
                            x + Math.cos(angle) * 1.2, y + 0.35, z + Math.sin(angle) * 1.2,
                            Math.cos(angle) * 0.025, 0.015, Math.sin(angle) * 0.025);
                }
            }
            case "tribulation_impact_beast" -> {
                overlayTicks = 40;
                overlayColor = 0x20342B20;
                level.playLocalSound(x, y, z, SoundEvents.RAVAGER_ROAR,
                        SoundSource.HOSTILE, 0.64f, 0.72f, false);
                for (int i = 0; i < 9; i++) {
                    particle(level, ParticleTypes.ELECTRIC_SPARK,
                            x + spread(1.8), y + spread(1.0), z + spread(1.8),
                            spread(0.025), 0.025, spread(0.025));
                    particle(level, ParticleTypes.CLOUD,
                            x + spread(1.3), y + spread(0.6), z + spread(1.3), 0, 0.008, 0);
                }
            }
            case "tribulation_impact_blood_rain" -> {
                overlayTicks = 48;
                overlayColor = 0x32920B18;
                level.playLocalSound(x, y, z, SoundEvents.WEATHER_RAIN,
                        SoundSource.WEATHER, 0.62f, 0.82f, false);
                DustParticleOptions bloodDust = new DustParticleOptions(
                        new Vector3f(0.58f, 0.025f, 0.075f), 0.78f);
                for (int i = 0; i < 16; i++) {
                    particle(level, bloodDust,
                            x + spread(1.8), y + 1.2 + RANDOM.nextDouble() * 1.5, z + spread(1.8),
                            spread(0.01), -0.12, spread(0.01));
                }
            }
            default -> {
                overlayTicks = 42;
                overlayColor = 0x22AA0000;
                level.playLocalSound(x, y, z, SoundEvents.PLAYER_HURT,
                        SoundSource.WEATHER, 0.72f, 1.0f, false);
                for (int i = 0; i < 18; i++) {
                    particle(level, ParticleTypes.CRIT,
                            x + spread(1.5), y + spread(1.2), z + spread(1.5), 0, 0, 0);
                }
            }
        }
    }

    private static void startApertureTransition(Minecraft minecraft, FxPayload payload, boolean entering) {
        edgeOverlay = false;
        Player localPlayer = minecraft.player;
        if (localPlayer == null || payload.entityId() != localPlayer.getId()) return;
        if (VeilApertureTransitionRenderer.start(clientTicks, entering)) {
            overlayTicks = 0;
            return;
        }
        overlayTicks = 16;
        overlayColor = entering ? 0x44FFD700 : 0x3340A0FF;
    }

    public static void tick() {
        clientTicks++;
        syncWorld(Minecraft.getInstance().level);
        if (Minecraft.getInstance().level == null) {
            overlayTicks = 0;
            VeilApertureTransitionRenderer.clear();
            return;
        }
        VeilApertureTransitionRenderer.tick(clientTicks);
        if (overlayTicks > 0) overlayTicks--;
    }


    private static void syncWorld(Level current) {
        if (fxWorld == current) return;
        fxWorld = current; overlayTicks = 0;
        VeilApertureTransitionRenderer.clear();
    }

    public static void render(GuiGraphics graphics) {
        if (overlayTicks <= 0 || !net.guzhenren.shengxian.ShengxianVisualConfig.enabled()
                || !net.guzhenren.shengxian.ShengxianVisualConfig.screen()) return;
        Minecraft minecraft = Minecraft.getInstance();
        int w = minecraft.getWindow().getGuiScaledWidth(), h = minecraft.getWindow().getGuiScaledHeight();
        int limit = net.guzhenren.shengxian.ShengxianVisualConfig.reduced() ? 18 : 42;
        int alpha = (int) (Math.min(limit, overlayColor >>> 24) * Math.min(1, overlayTicks / 12f));
        int color = (alpha << 24) | (overlayColor & 0xFFFFFF);
        if (edgeOverlay) {
            int b = Math.max(2, h / 14);
            graphics.fill(0, 0, w, b, color); graphics.fill(0, h - b, w, h, color);
            graphics.fill(0, b, b, h - b, color); graphics.fill(w - b, b, w, h - b, color);
        } else graphics.fill(0, 0, w, h, color);
    }
    private static void particle(Level level,net.minecraft.core.particles.ParticleOptions options,
                                 double x,double y,double z,double vx,double vy,double vz) {
        if(!net.guzhenren.shengxian.ShengxianVisualConfig.particles()) return;
        if(particleTick!=clientTicks) {
            particleTick=clientTicks;
            particlesLeft=net.guzhenren.shengxian.ShengxianVisualConfig.lowPerformance()?24:96;
        }
        if(particlesLeft-- > 0) level.addParticle(options,x,y,z,vx,vy,vz);
    }
    private static double spread(double radius) {
        return (RANDOM.nextDouble() - 0.5) * 2.0 * radius;
    }

}
