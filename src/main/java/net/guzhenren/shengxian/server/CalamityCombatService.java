package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.guzhenren.shengxian.core.CalamityCarrierStats;
import net.guzhenren.shengxian.core.CalamityCombat;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灾劫招式在服务端真正执行：预警 -> 单帧打击 -> 后摇。
 *
 * <p>Works for both carrier forms: the legacy invisible armour stand and the real calamity mob.
 * The carrier is the attack origin and the damage is decided here, never by the client, with a
 * per-attack sequence so one strike can damage a given player at most once.</p>
 */
final class CalamityCombatService {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final Set<CalamityCombat.HitId> APPLIED = new LinkedHashSet<>();
    private static final Map<UUID, Double> HEALTH = new ConcurrentHashMap<>();
    private static int sweeps;

    private CalamityCombatService() {}

    private static final class State {
        CalamityCombat.Cycle cycle = CalamityCombat.start();
        int sequence;
        long lastTick;
    }

    /** One server tick of one carrier: telegraph, strike, then advance the rotation. */
    static void tick(ServerLevel level, Entity carrier, AscensionProgress.Tribulation.Type type,
                     UUID owner) {
        if (!ShengxianConfig.calamityCombat()) return;
        List<CalamityCombat.Attack> attacks = CalamityCombat.attacksFor(type);
        if (attacks.isEmpty() || carrier.isRemoved()
                || (carrier instanceof net.guzhenren.shengxian.entity.CalamityCarrierEntity mob
                && mob.isDying())) {
            STATES.remove(carrier.getUUID());
            return;
        }
        if (++sweeps % 400 == 0) prune(level);
        State state = STATES.computeIfAbsent(carrier.getUUID(), id -> new State());
        long now = level.getGameTime();
        // Idempotent per tick: the entity tick and the server-tick sweep may both reach a carrier.
        if (state.lastTick == now) return;
        state.lastTick = now;
        CalamityCombat.Attack attack = CalamityCombat.currentAttack(state.cycle, attacks);
        switch (state.cycle.stage()) {
            case TELEGRAPH -> telegraph(level, carrier, attack);
            case STRIKE -> {
                strike(level, carrier, attack, state.sequence, owner);
                state.sequence++;
            }
            case COOLDOWN -> { }
        }
        state.cycle = CalamityCombat.advance(state.cycle, attacks);
    }

    static void forget(UUID carrier) {
        STATES.remove(carrier);
        HEALTH.remove(carrier);
    }

    /** Persistent-data key so a partly damaged carrier keeps its health across a restart. */
    private static final String HEALTH_KEY = "shengxian_carrier_health";

    /** Remaining health of a carrier, restored from the entity's own saved data. */
    static double healthOf(Entity carrier, AscensionProgress.Tribulation.Type type) {
        Double cached = HEALTH.get(carrier.getUUID());
        if (cached != null) return cached;
        double restored;
        var data = carrier.getPersistentData();
        if (data.contains(HEALTH_KEY)) {
            restored = data.getDouble(HEALTH_KEY);
        } else if (carrier instanceof net.minecraft.world.entity.LivingEntity living
                && living.getHealth() > 0.0f) {
            restored = living.getHealth();
        } else {
            restored = CalamityCarrierStats.maxHealth(type);
        }
        HEALTH.put(carrier.getUUID(), restored);
        return restored;
    }

    static boolean wouldDestroy(Entity carrier, AscensionProgress.Tribulation.Type type, double damage) {
        return healthOf(carrier, type) - damage <= 0;
    }

    /** Applies one instance of damage to a carrier and destroys it at zero. */
    static void damageCarrier(Entity carrier, AscensionProgress.Tribulation.Type type, double damage) {
        double health = healthOf(carrier, type) - damage;
        if (health <= 0) {
            forget(carrier.getUUID());
            destroy(carrier, type);
            return;
        }
        HEALTH.put(carrier.getUUID(), health);
        // Saved on the entity itself, so a restart cannot heal a partly fought calamity.
        carrier.getPersistentData().putDouble(HEALTH_KEY, health);
        if (carrier instanceof net.minecraft.world.entity.LivingEntity living) {
            living.setHealth((float) Math.max(0.0, health));
        }
        showHealth(carrier, type, health);
        hurtFeedback((ServerLevel) carrier.level(), carrier);
    }

    static void strikeTestCarrier(Entity carrier, AscensionProgress.Tribulation.Type type,
                                  ServerPlayer attacker, double damage) {
        if (carrier instanceof net.minecraft.world.entity.decoration.ArmorStand && type == null) {
            type = TribulationWorldService.carrierTypeOf((net.minecraft.world.entity.decoration.ArmorStand) carrier);
        }
        damageCarrier(carrier, type, CalamityCarrierStats.damageFromStrike(damage));
    }

    private static void showHealth(Entity carrier, AscensionProgress.Tribulation.Type type,
                                   double health) {
        carrier.setCustomName(Component.literal(net.guzhenren.shengxian.core.CarrierLabels.label(type)
                + " · " + CalamityCarrierStats.describe(health) + " HP"));
        carrier.setCustomNameVisible(true);
    }

    private static void hurtFeedback(ServerLevel level, Entity carrier) {
        level.sendParticles(ParticleTypes.CRIT, carrier.getX(), carrier.getY() + 1.0, carrier.getZ(),
                10, 0.4, 0.4, 0.4, 0.2);
        level.playSound(null, carrier.blockPosition(), SoundEvents.IRON_GOLEM_HURT,
                SoundSource.HOSTILE, 0.9f, 1.15f);
    }

    private static void destroy(Entity carrier, AscensionProgress.Tribulation.Type type) {
        ServerLevel level = (ServerLevel) carrier.level();
        level.playSound(null, carrier.blockPosition(), SoundEvents.WITHER_DEATH,
                SoundSource.HOSTILE, 1.2f, 0.8f);
        LOGGER.info("Calamity carrier {} of type {} was destroyed", carrier.getUUID(), type);
        carrier.getPersistentData().putDouble(HEALTH_KEY, 0.0);
        if (carrier instanceof net.guzhenren.shengxian.entity.CalamityCarrierEntity mob) {
            // Let the model's death clip play; the entity removes itself when it finishes.
            mob.beginDying();
            return;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, carrier.getX(), carrier.getY() + 1.0,
                carrier.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        if (carrier instanceof net.minecraft.world.entity.LivingEntity living) {
            living.setHealth(0.0f);
        }
        carrier.discard();
    }

    private static void prune(ServerLevel level) {
        long now = level.getGameTime();
        STATES.entrySet().removeIf(entry -> {
            Entity carrier = level.getEntity(entry.getKey());
            if (carrier == null || carrier.isRemoved()) return true;
            return now - entry.getValue().lastTick > 200;
        });
    }

    /** Ground warning ring: the only cue a player gets before a strike lands. */
    private static void telegraph(ServerLevel level, Entity carrier, CalamityCombat.Attack attack) {
        if (carrier.tickCount % 3 != 0) return;
        double radius = attack.radius();
        int points = Math.min(40, 10 + (int) (radius * 2));
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.85f, 0.2f, 0.12f), 1.1f);
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 / points) * i;
            level.sendParticles(dust,
                    carrier.getX() + Math.cos(angle) * radius,
                    carrier.getY() + 0.25,
                    carrier.getZ() + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Applies one strike to every valid participant inside the radius with a clear line of sight. */
    private static void strike(ServerLevel level, Entity carrier, CalamityCombat.Attack attack,
                               int sequence, UUID owner) {
        double radiusSq = attack.radius() * attack.radius();
        float damage = (float) (attack.damage() * ShengxianConfig.calamityDamageScale());
        int hit = 0;
        for (ServerPlayer target : level.players()) {
            if (target.isCreative() || target.isSpectator()) continue;
            if (!participates(target, owner)) continue;
            if (target.distanceToSqr(carrier) > radiusSq) continue;
            if (!hasLineOfSight(level, carrier, target)) continue;
            CalamityCombat.HitId id = new CalamityCombat.HitId(carrier.getUUID(), sequence, target.getUUID());
            synchronized (APPLIED) {
                if (!CalamityCombat.applyOnce(APPLIED, id)) continue;
                while (APPLIED.size() > 512) {
                    APPLIED.remove(APPLIED.iterator().next());
                }
            }
            target.hurt(level.damageSources().magic(), damage);
            Vec3 away = target.position().subtract(carrier.position());
            if (away.lengthSqr() > 1.0E-4) {
                away = away.normalize();
                target.push(away.x * attack.knockback(), 0.22 + 0.3 * attack.knockback(),
                        away.z * attack.knockback());
                target.hurtMarked = true;
            }
            hit++;
        }
        LOGGER.info("Calamity strike {} from {} hit {} target(s) for {} damage each (radius {})",
                attack.kind(), carrier.getUUID(), hit, damage, attack.radius());
        level.sendParticles(ParticleTypes.CRIT, carrier.getX(), carrier.getY() + 1.0, carrier.getZ(),
                12 + hit * 4, 0.6, 0.5, 0.6, 0.15);
        level.playSound(null, carrier.blockPosition(), SoundEvents.WITHER_HURT,
                SoundSource.HOSTILE, 1.1f, 0.65f);
    }

    private static boolean participates(ServerPlayer player, UUID owner) {
        if (owner == null) return true;
        if (player.getUUID().equals(owner)) return true;
        if (!ShengxianConfig.allowGuardians()) return false;
        AscensionServer.ApertureContext context = AscensionServer.apertureContext();
        return context != null && context.spaceStore().isAscensionGuardian(owner, player.getUUID());
    }

    private static boolean hasLineOfSight(ServerLevel level, Entity carrier, ServerPlayer target) {
        HitResult hit = level.clip(new ClipContext(carrier.getEyePosition(), target.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, carrier));
        return hit.getType() == HitResult.Type.MISS;
    }
}
