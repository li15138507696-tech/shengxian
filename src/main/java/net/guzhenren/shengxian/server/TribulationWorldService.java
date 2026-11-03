package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.guzhenren.shengxian.core.AscensionRecord;
import net.guzhenren.shengxian.core.CalamityCarrierStats;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.core.SessionStore;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Server-owned, persistent world anchors for the explicitly witnessed calamity profiles.
 * The armor stand is an invisible hit target; only its name and built-in particles are shown.
 * Domain state remains authoritative and chunk absence never counts as a defeated carrier.
 */
public final class TribulationWorldService {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final String MARKER_TAG = "shengxian:calamity_carrier";
    /** Ownerless carriers created by the test command; they attack anybody nearby. */
    static final String DUMMY_TAG = "shengxian:calamity_dummy";
    private static final String ID_TAG_PREFIX = "shengxian:tribulation:";
    private static final double ATTACK_RANGE_SQUARED = 64.0;

    private TribulationWorldService() {}

    private record Identity(UUID owner, String apertureId, String carrierId) {}

    /**
     * Fallback driver: sweeps nearby calamity mobs every server tick so an entity whose own tick
     * event is missed still advances its rotation. The combat service ignores a second call
     * within the same game tick.
     */
    private static void tickMobileCarriers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            for (net.guzhenren.shengxian.entity.CalamityCarrierEntity carrier
                    : level.getEntitiesOfClass(net.guzhenren.shengxian.entity.CalamityCarrierEntity.class,
                    player.getBoundingBox().inflate(96.0))) {
                CalamityCombatService.tick(level, carrier, carrier.variant(), null);
            }
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        tickMobileCarriers(event.getServer());
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        SessionStore store = AscensionServer.store();
        if (store == null || !AscensionLifecycleService.readiness().available()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                store.find(new ApertureKey(player.getUUID(), "first"))
                        .ifPresent(record -> reconcile(player, record));
            } catch (IOException ex) {
                LOGGER.warn("Could not reconcile calamity carriers for {}; session was retained", player.getUUID(), ex);
            }
        }
    }

    /**
     * Phase that can hold this carrier: absorption for outer calamities, immortal for the inner
     * one that CANON-012 brews inside a formed aperture.
     */
    private static boolean phaseSupports(Phase phase, String carrierId) {
        if (phase == Phase.ABSORBING) return true;
        return phase == Phase.IMMORTAL
                && net.guzhenren.shengxian.core.InnerTribulation.isInner(carrierId);
    }

    /** Reconciles committed state after a simulation step or player login. */
    public static void reconcile(ServerPlayer owner, AscensionRecord record) {
        if (owner == null || record == null
                || !AscensionLifecycleService.readiness().available()) return;
        if (record.session().phase() != Phase.ABSORBING && record.session().phase() != Phase.IMMORTAL) return;
        for (AscensionProgress.Tribulation carrier : record.progress().tribulations()) {
            if (!phaseSupports(record.session().phase(), carrier.id())) continue;
            AscensionProgress.CarrierAnchor anchor = carrier.carrierAnchor();
            if (!anchor.placed() || anchor.entityId() == null) continue;
            ServerLevel level = findLevel(owner.server, anchor.dimension());
            if (level == null || !isChunkLoaded(level, anchor)) continue;
            Entity found = level.getEntity(anchor.entityId());
            Entity carrierEntity;
            if (found == null) {
                carrierEntity = spawn(owner.getUUID(), record.session().key().apertureId(), carrier, level, anchor);
                if (carrierEntity == null) continue;
            } else if (isCarrier(found) && matches(found, owner.getUUID(),
                    record.session().key().apertureId(), carrier.id(), anchor)) {
                // Legacy saves still hold the armour-stand form; both forms stay valid.
                carrierEntity = found;
            } else {
                LOGGER.error("Calamity carrier UUID {} is occupied by an unrelated entity; refusing to overwrite it",
                        anchor.entityId());
                continue;
            }
            emitAura(level, carrierEntity, carrier.type());
        }
    }

    /**
     * Removes entities whose committed carrier state ended, then reconciles all still-active carriers.
     * Calling this only after the domain record has been saved makes entity creation crash-safe.
     */
    public static void synchronize(ServerPlayer owner, AscensionRecord before, AscensionRecord after) {
        if (owner == null || before == null || after == null) return;
        for (AscensionProgress.Tribulation oldCarrier : before.progress().tribulations()) {
            UUID oldId = oldCarrier.carrierAnchor().entityId();
            if (oldId == null || stillActive(after, oldId)) continue;
            ServerLevel level = findLevel(owner.server, oldCarrier.carrierAnchor().dimension());
            if (level == null || !isChunkLoaded(level, oldCarrier.carrierAnchor())) continue;
            Entity entity = level.getEntity(oldId);
            if (entity != null && isCarrier(entity)) entity.discard();
        }
        reconcile(owner, after);
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (!(target instanceof Entity stand) || !isCarrier(stand)) return;
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer guardian)) return;
        Identity identity = identity(stand);
        if (identity == null) {
            // Ownerless test carrier: it has no committed state, so its health lives in the
            // combat service and is destroyed there.
            if (stand.getTags().contains(DUMMY_TAG) && guardian.distanceToSqr(stand) <= ATTACK_RANGE_SQUARED) {
                CalamityCombatService.strikeTestCarrier(stand, carrierTypeOf(stand), guardian,
                        guardian.getAttributeValue(Attributes.ATTACK_DAMAGE));
            }
            return;
        }
        if (guardian.distanceToSqr(stand) > ATTACK_RANGE_SQUARED
                || !ShengxianConfig.SPEC.isLoaded()
                || !AscensionLifecycleService.readiness().available()) return;
        // CANON-012 inner calamity: the owner fights it inside their own aperture. The witnessed
        // text does not describe the method, so a direct strike is a game mapping; outer
        // calamities keep the guardian-only rule (CANON-009).
        if (identity.owner().equals(guardian.getUUID())) {
            strikeInnerByOwner(guardian, stand, identity);
            return;
        }
        if (!ShengxianConfig.allowGuardians()) return;
        AscensionServer.ApertureContext context = AscensionServer.apertureContext();
        if (context == null || !context.spaceStore().isAscensionGuardian(identity.owner(), guardian.getUUID())) return;
        SessionStore store = context.store();
        if (store == null) return;
        ServerPlayer owner = guardian.server.getPlayerList().getPlayer(identity.owner());
        if (owner == null || owner.serverLevel() != stand.level()) return;
        ApertureKey key;
        try {
            key = new ApertureKey(identity.owner(), identity.apertureId());
            AscensionRecord current = store.find(key).orElse(null);
            if (current == null || current.session().phase() != Phase.ABSORBING
                    || !matches(stand, identity.owner(), identity.apertureId(),
                    identity.carrierId(), anchor(current, identity.carrierId()))) return;
            AscensionProgress.Tribulation.Type hitType = carrierType(current, identity.carrierId());
            long tick = guardian.server.overworld().getGameTime();
            long sequence = Math.max(current.session().lastActionSequence() + 1L, tick);
            // The health pool is authoritative for combat; committed units only change when the
            // pool empties, so a carrier persists across restarts until it is actually destroyed.
            double strike = CalamityCarrierStats.damageFromStrike(
                    guardian.getAttributeValue(Attributes.ATTACK_DAMAGE));
            boolean destroyed = CalamityCombatService.wouldDestroy(stand, hitType, strike);
            CalamityCombatService.damageCarrier(stand, hitType, strike);
            if (destroyed) {
                AscensionRecord updated = store.strikeTribulation(key, current.session().stateRevision(),
                        sequence, tick, identity.carrierId(), Integer.MAX_VALUE);
                if (!contains(updated, identity.carrierId())) stand.discard();
            } else {
                emitAura(guardian.serverLevel(), stand, hitType);
            }
            guardian.sendSystemMessage(Component.literal("护道击退了" + displayName(hitType) + "。"));
            AscensionLifecycleService.sendSnapshot(owner);
        } catch (Exception ex) {
            LOGGER.warn("Guardian strike was rejected for calamity carrier {}", stand.getUUID(), ex);
        }
    }

    /** Owner-side strike for an inner carrier only; other carriers keep the guardian rule. */
    private static void strikeInnerByOwner(ServerPlayer owner, Entity stand, Identity identity) {
        if (!net.guzhenren.shengxian.core.InnerTribulation.isInner(identity.carrierId())) return;
        SessionStore store = AscensionServer.store();
        if (store == null) return;
        ApertureKey key = new ApertureKey(identity.owner(), identity.apertureId());
        try {
            AscensionRecord current = store.find(key).orElse(null);
            if (current == null
                    || !matches(stand, identity.owner(), identity.apertureId(),
                    identity.carrierId(), anchor(current, identity.carrierId()))) return;
            long tick = owner.server.overworld().getGameTime();
            long sequence = Math.max(current.session().lastActionSequence() + 1L, tick);
            AscensionRecord updated = store.strikeTribulation(key, current.session().stateRevision(),
                    sequence, tick, identity.carrierId(), Integer.MAX_VALUE);
            if (!contains(updated, identity.carrierId())) {
                stand.discard();
                owner.sendSystemMessage(Component.literal("窍内灾劫已散。"));
            }
            AscensionLifecycleService.sendSnapshot(owner);
        } catch (Exception ex) {
            LOGGER.warn("Owner strike on inner calamity {} was rejected", stand.getUUID(), ex);
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Entity entity && isCarrier(entity)) event.setCanceled(true);
    }

    /**
     * Calamity carriers never take vanilla damage: their health is spent through the calamity
     * settlement so a kill always commits exactly once.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Entity entity && isCarrier(entity)) event.setCanceled(true);
    }

    /**
     * Stale persistent entities self-remove after a committed completion or failure, including
     * after restart. The same hook plays the type-specific particle choreography every four
     * ticks, so the calamity stays visually alive between the slower reconciles.
     */
    public static void onEntityTick(EntityTickEvent.Post event) {
        // The mobile calamity form runs the same rotation from its own entity tick.
        if (event.getEntity() instanceof net.guzhenren.shengxian.entity.CalamityCarrierEntity carrier
                && !carrier.level().isClientSide) {
            if (carrier.tickCount % 100 == 0) {
                LOGGER.info("Calamity mob tick {} type {} health {}", carrier.getUUID(),
                        carrier.variant(), carrier.getHealth());
            }
            CalamityCombatService.tick((ServerLevel) carrier.level(), carrier,
                    carrier.variant(), null);
            return;
        }
        if (!(event.getEntity() instanceof ArmorStand stand) || stand.level().isClientSide
                || !isCarrier(stand)) return;
        // Effects ride the carrier's own tick; validation keeps its slower cadence.
        if (stand.tickCount % 4 == 0) {
            emitAura((ServerLevel) stand.level(), stand, carrierTypeFromTags(stand));
        }
        // The attack rotation runs every tick; the warning window is only a few hundred ms.
        AscensionProgress.Tribulation.Type tickType = carrierTypeOf(stand);
        CalamityCombatService.tick((ServerLevel) stand.level(), stand, tickType, carrierOwner(stand));
        if (stand.tickCount % 20 != 0) return;
        Identity identity = identity(stand);
        if (identity == null) {
            // Ownerless test carriers have no ascension identity and must survive validation.
            if (!stand.getTags().contains(DUMMY_TAG)) stand.discard();
            return;
        }
        SessionStore store = AscensionServer.store();
        if (store == null) return;
        try {
            AscensionRecord record = store.find(new ApertureKey(identity.owner(), identity.apertureId())).orElse(null);
            if (record == null || !phaseSupports(record.session().phase(), identity.carrierId())
                    || !matches(stand, identity.owner(), identity.apertureId(), identity.carrierId(),
                    anchor(record, identity.carrierId()))) {
                stand.discard();
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not validate persisted calamity carrier {}; entity was retained",
                    stand.getUUID(), ex);
        }
    }

    /** Creates the committed carrier as a real calamity mob. */
    private static Entity spawn(UUID owner, String apertureId, AscensionProgress.Tribulation carrier,
                                ServerLevel level, AscensionProgress.CarrierAnchor anchor) {
        var mob = net.guzhenren.shengxian.entity.ShengxianEntities.CALAMITY_CARRIER.get().create(level);
        if (mob == null) return null;
        mob.moveTo(anchor.x() + 0.5, anchor.y(), anchor.z() + 0.5, 0.0f, 0.0f);
        mob.setUUID(anchor.entityId());
        mob.applyVariant(carrier.type());
        mob.addTag(MARKER_TAG);
        mob.addTag(identityTag(owner, apertureId, carrier.id()));
        if (!level.addFreshEntity(mob)) {
            LOGGER.warn("Could not add calamity carrier {} to dimension {}", carrier.id(),
                    level.dimension().location());
            return null;
        }
        return mob;
    }

    /** Spawns an ownerless test carrier in front of the player; it strikes any nearby player. */
    public static ArmorStand spawnTestCarrier(ServerPlayer player,
                                              AscensionProgress.Tribulation.Type type) {
        ServerLevel level = player.serverLevel();
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) return null;
        Vec3 look = player.getLookAngle();
        stand.moveTo(player.getX() + look.x * 6.0, player.getY() + 1.0,
                player.getZ() + look.z * 6.0, 0.0f, 0.0f);
        stand.setNoGravity(true);
        stand.setSilent(true);
        stand.setInvisible(true);
        stand.setShowArms(false);
        stand.setNoBasePlate(true);
        stand.setCustomName(Component.literal(
                net.guzhenren.shengxian.core.CarrierLabels.label(type) + " · "
                        + CalamityCarrierStats.describe(CalamityCarrierStats.maxHealth(type)) + " HP"));
        stand.setCustomNameVisible(true);
        stand.addTag(MARKER_TAG);
        stand.addTag(DUMMY_TAG);
        return level.addFreshEntity(stand) ? stand : null;
    }

    /**
     * Strikes every ownerless test carrier near the player with the player's attack damage.
     * Test-only: it exercises the same health conversion a real left-click would.
     */
    public static String hitTestCarriers(ServerPlayer player, double damage) {
        StringBuilder report = new StringBuilder();
        // Covers both carrier forms: the legacy armour stand and the calamity mob.
        for (Entity stand : player.serverLevel().getEntitiesOfClass(Entity.class,
                player.getBoundingBox().inflate(24.0), TribulationWorldService::isCarrier)) {
            AscensionProgress.Tribulation.Type type = carrierTypeOf(stand);
            CalamityCombatService.strikeTestCarrier(stand, type, player, damage);
            if (report.length() > 0) report.append("; ");
            if (stand.isRemoved()) {
                report.append(displayName(type)).append(" 被摧毁");
            } else {
                report.append(displayName(type)).append(" 剩余 ")
                        .append(CalamityCarrierStats.describe(
                                CalamityCombatService.healthOf(stand, type))).append(" HP");
            }
        }
        return report.toString();
    }

    /**
     * Test entry that exercises the committed carrier spawn path (mob form with real identity
     * tags) without needing a full ascension session.
     */
    public static String spawnCommittedTestCarrier(ServerPlayer player,
                                                   AscensionProgress.Tribulation.Type type) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        var anchor = new AscensionProgress.CarrierAnchor(level.dimension().location().toString(),
                (int) Math.floor(player.getX() + look.x * 10.0),
                (int) Math.floor(player.getY()),
                (int) Math.floor(player.getZ() + look.z * 10.0),
                UUID.randomUUID());
        var carrier = new AscensionProgress.Tribulation("test_carrier", type, 3, 200, anchor);
        Entity spawned = spawn(player.getUUID(), "test_aperture", carrier, level, anchor);
        if (spawned == null) return "灾劫载体生成失败";
        String health = spawned instanceof net.minecraft.world.entity.LivingEntity living
                ? String.valueOf(living.getHealth()) : "?";
        return "已按正式路径生成载体 " + displayName(type) + "，实体类型 "
                + spawned.getType().toShortString() + "，生命 " + health;
    }

    /** Spawns a real calamity mob in front of the player (test entry for the entity form). */
    public static String spawnMobCarrier(ServerPlayer player, AscensionProgress.Tribulation.Type type) {
        ServerLevel level = player.serverLevel();
        var entity = net.guzhenren.shengxian.entity.ShengxianEntities.CALAMITY_CARRIER.get().create(level);
        if (entity == null) return "无法创建灾劫生物";
        Vec3 look = player.getLookAngle();
        entity.moveTo(player.getX() + look.x * 10.0, player.getY() + 0.5,
                player.getZ() + look.z * 10.0, player.getYRot(), 0.0f);
        entity.applyVariant(type);
        entity.addTag(DUMMY_TAG);
        if (!level.addFreshEntity(entity)) return "灾劫生物生成失败";
        return "已生成灾劫生物 " + displayName(type) + "（" + entity.getMaxHealth() + " 生命）";
    }

    /** Discards every ownerless test carrier near the player and returns how many were removed. */
    public static int clearTestCarriers(ServerPlayer player) {
        int removed = 0;
        for (ArmorStand stand : player.serverLevel().getEntitiesOfClass(ArmorStand.class,
                player.getBoundingBox().inflate(96.0),
                s -> s.getTags().contains(DUMMY_TAG))) {
            stand.discard();
            removed++;
        }
        return removed;
    }

    /** Calamity type of a carrier: committed tag first, then the synced display name. */
    static AscensionProgress.Tribulation.Type carrierTypeOf(Entity stand) {
        AscensionProgress.Tribulation.Type byTag = carrierTypeFromTags(stand);
        if (byTag != null && byTag != AscensionProgress.Tribulation.Type.NONE) return byTag;
        Component name = stand.getCustomName();
        AscensionProgress.Tribulation.Type byName =
                name == null ? null : net.guzhenren.shengxian.core.CarrierLabels.fromName(name.getString());
        return byName == null ? AscensionProgress.Tribulation.Type.NONE : byName;
    }

    /** Owner of a carrier stand, or null for an ownerless test carrier. */
    static UUID carrierOwner(Entity stand) {
        Identity identity = identity(stand);
        return identity == null ? null : identity.owner();
    }

    private static boolean matches(Entity stand, UUID owner, String apertureId, String carrierId,
                                   AscensionProgress.CarrierAnchor anchor) {
        if (anchor == null || anchor.entityId() == null || !anchor.entityId().equals(stand.getUUID())
                || !(stand.level() instanceof ServerLevel level)
                || !anchor.dimension().equals(level.dimension().location().toString())
                || !isCarrier(stand)) return false;
        var position = stand.blockPosition();
        if (position.getX() != anchor.x() || position.getY() != anchor.y() || position.getZ() != anchor.z()) return false;
        Identity identity = identity(stand);
        return identity != null && identity.owner().equals(owner)
                && identity.apertureId().equals(apertureId) && identity.carrierId().equals(carrierId);
    }

    private static boolean isCarrier(Entity entity) {
        return entity.getTags().contains(MARKER_TAG) || identity(entity) != null;
    }

    private static Identity identity(Entity entity) {
        Identity parsed = null;
        for (String tag : entity.getTags()) {
            if (!tag.startsWith(ID_TAG_PREFIX)) continue;
            if (parsed != null) return null;
            String[] parts = tag.split(":", -1);
            if (parts.length != 5 || !parts[0].equals("shengxian") || !parts[1].equals("tribulation")) return null;
            try {
                parsed = new Identity(UUID.fromString(decode(parts[2])), decode(parts[3]), decode(parts[4]));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return parsed;
    }

    private static String identityTag(UUID owner, String apertureId, String carrierId) {
        return ID_TAG_PREFIX + encode(owner.toString()) + ":" + encode(apertureId) + ":" + encode(carrierId);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static ServerLevel findLevel(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) return level;
        }
        return null;
    }

    private static boolean isChunkLoaded(ServerLevel level, AscensionProgress.CarrierAnchor anchor) {
        return level.getChunkSource().hasChunk(anchor.x() >> 4, anchor.z() >> 4);
    }

    /**
     * Per-type particle choreography, played from the carrier's own entity tick so the calamity
     * stays alive between reconciles. Each type gets a distinct shape rather than a color swap:
     * willow leaves rain down in a slow spiral, the tiger kicks up ground shockwaves, the chimes
     * ring outward in golden rings, the ancient beast breathes soul fire, and the blood rain falls
     * from above and pools at the feet.
     */
    /** Resolves the carrier type for the fast effect path; a miss degrades to a neutral cue. */
    private static AscensionProgress.Tribulation.Type carrierTypeFromTags(Entity stand) {
        Identity identity = identity(stand);
        if (identity == null) return AscensionProgress.Tribulation.Type.NONE;
        SessionStore store = AscensionServer.store();
        if (store == null) return AscensionProgress.Tribulation.Type.NONE;
        try {
            return store.find(new ApertureKey(identity.owner(), identity.apertureId()))
                    .map(record -> carrierType(record, identity.carrierId()))
                    .orElse(AscensionProgress.Tribulation.Type.NONE);
        } catch (Exception ex) {
            return AscensionProgress.Tribulation.Type.NONE;
        }
    }

    private static void emitAura(ServerLevel level, Entity stand, AscensionProgress.Tribulation.Type type) {
        if (!net.guzhenren.shengxian.ShengxianConfig.particleAuras()) return;
        double x = stand.getX(), y = stand.getY() + 0.9, z = stand.getZ();
        long phase = stand.tickCount / 2; // time base for the choreography
        switch (type) {
            case JADE_WILLOW_WIND -> {
                // 垂柳：三层下坠叶幕缓慢旋转，顶端一圈悬浮叶环
                for (int ring = 0; ring < 3; ring++) {
                    double radius = 0.55 + ring * 0.35;
                    double spin = phase * 0.055 + ring * 2.1;
                    for (int i = 0; i < 5; i++) {
                        double angle = spin + Math.PI * 2.0 * i / 5.0;
                        level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                                x + Math.cos(angle) * radius, y + 0.9 - ring * 0.42,
                                z + Math.sin(angle) * radius, 1, 0.02, 0.04, 0.02, 0.012);
                    }
                }
                level.sendParticles(ParticleTypes.GLOW, x, y + 1.35, z, 1, 0.12, 0.02, 0.12, 0.0);
            }
            case SINGLE_HORNED_TIGER -> {
                // 峥虎：地面尘环向外扩散，独角处迸红光，周期性扬尘
                double ringRadius = 0.5 + (phase % 6) * 0.28;
                for (int i = 0; i < 10; i++) {
                    double angle = Math.PI * 2.0 * i / 10.0;
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            x + Math.cos(angle) * ringRadius, y - 0.55,
                            z + Math.sin(angle) * ringRadius, 1, 0, 0.02, 0, 0.002);
                }
                level.sendParticles(new DustParticleOptions(new Vector3f(0.85f, 0.16f, 0.1f), 1.15f),
                        x, y + 0.55, z, 2, 0.08, 0.08, 0.08, 0.0);
                level.sendParticles(ParticleTypes.CRIT, x, y + 0.2, z, 4, 0.3, 0.25, 0.3, 0.06);
            }
            case SOUND_CHIMES -> {
                // 八音磬：八道金环逐圈外扩 + 音符上飘（对应八道音磬的数目）
                int rings = 2;
                for (int ring = 0; ring < rings; ring++) {
                    double radius = 0.45 + ((phase + ring * 3L) % 5) * 0.22;
                    int count = 8;
                    for (int i = 0; i < count; i++) {
                        double angle = Math.PI * 2.0 * i / count + ring * 0.4;
                        level.sendParticles(ParticleTypes.END_ROD,
                                x + Math.cos(angle) * radius, y + 0.2 + ring * 0.25,
                                z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
                    }
                }
                level.sendParticles(ParticleTypes.NOTE, x, y + 1.1, z, 2, 0.15, 0.08, 0.15, 0.4);
            }
            case ANCIENT_BEAST -> {
                // 荒兽：幽蓝魂火盘旋 + 电弧爆点 + 底部黑雾翻涌
                for (int i = 0; i < 6; i++) {
                    double angle = phase * 0.09 + Math.PI * 2.0 * i / 6.0;
                    double radius = 0.5 + 0.18 * Math.sin(phase * 0.11 + i);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            x + Math.cos(angle) * radius, y + 0.35 + 0.3 * Math.sin(phase * 0.07 + i * 1.3),
                            z + Math.sin(angle) * radius, 1, 0, 0, 0, 0.002);
                }
                level.sendParticles(ParticleTypes.SONIC_BOOM, x, y + 0.4, z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y - 0.5, z, 2, 0.4, 0.05, 0.4, 0.004);
            }
            case BLOOD_RAIN -> {
                // 血雨：头顶持续落血 + 地面血泊 + 空中血雾
                ParticleOptions blood = new DustParticleOptions(new Vector3f(0.58f, 0.025f, 0.075f), 0.9f);
                ParticleOptions bloodPool = new DustParticleOptions(new Vector3f(0.42f, 0.01f, 0.05f), 1.2f);
                level.sendParticles(blood, x, y + 1.6, z, 10, 0.65, 0.5, 0.65, -0.16);
                level.sendParticles(bloodPool, x, y - 0.62, z, 6, 0.5, 0.02, 0.5, 0.0);
                level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y + 0.4, z, 3, 0.4, 0.4, 0.4, 0.0);
            }
            case NONE -> level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 3, 0.25, 0.3, 0.25, 0.01);
        }
    }

    private static String displayName(AscensionProgress.Tribulation.Type type) {
        return net.guzhenren.shengxian.core.CarrierLabels.label(type);
    }

    private static String carrierName(String carrierId) {
        return carrierId;
    }

    private static AscensionProgress.CarrierAnchor anchor(AscensionRecord record, String carrierId) {
        return record.progress().tribulations().stream().filter(t -> t.id().equals(carrierId))
                .map(AscensionProgress.Tribulation::carrierAnchor).findFirst().orElse(null);
    }

    private static AscensionProgress.Tribulation.Type carrierType(AscensionRecord record, String carrierId) {
        return record.progress().tribulations().stream().filter(t -> t.id().equals(carrierId))
                .map(AscensionProgress.Tribulation::type).findFirst()
                .orElse(AscensionProgress.Tribulation.Type.NONE);
    }

    private static boolean contains(AscensionRecord record, String carrierId) {
        return record.progress().tribulations().stream().anyMatch(t -> t.id().equals(carrierId));
    }

    private static boolean stillActive(AscensionRecord record, UUID entityId) {
        return record.session().phase() == Phase.ABSORBING && record.progress().tribulations().stream()
                .anyMatch(t -> entityId.equals(t.carrierAnchor().entityId()));
    }
}

