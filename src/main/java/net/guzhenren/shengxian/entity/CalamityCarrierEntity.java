package net.guzhenren.shengxian.entity;

import net.guzhenren.shengxian.core.AscensionProgress;
import net.guzhenren.shengxian.core.CalamityCarrierStats;
import net.guzhenren.shengxian.core.CarrierLabels;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * 灾劫本体：真正存在于世界里的生物，而不是隐形盔甲架。
 *
 * <p>It carries its own hitbox, its million-point health from
 * {@link CalamityCarrierStats} and its own synced variant, so damage, knockback immunity and
 * targeting all run through vanilla entity rules instead of a command hook.</p>
 *
 * <p>Vanilla pathfinding goals stay empty on purpose: the attack rotation is driven by
 * {@code CalamityCombatService}, which owns the warning window and the damage roll.</p>
 */
public class CalamityCarrierEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> VARIANT =
            SynchedEntityData.defineId(CalamityCarrierEntity.class, EntityDataSerializers.STRING);
    /** Ticks since death started; the client uses it as the death animation clock. */
    private static final EntityDataAccessor<Integer> DEATH_TICKS =
            SynchedEntityData.defineId(CalamityCarrierEntity.class, EntityDataSerializers.INT);
    /** Longest model death clip is 2.2 s, so the corpse stays this long before it is removed. */
    public static final int DEATH_DURATION_TICKS = 44;

    public CalamityCarrierEntity(EntityType<? extends CalamityCarrierEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, CalamityCarrierStats.maxHealth(
                        AscensionProgress.Tribulation.Type.ANCIENT_BEAST))
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND.name());
        builder.define(DEATH_TICKS, 0);
    }

    public AscensionProgress.Tribulation.Type variant() {
        String stored = entityData.get(VARIANT);
        for (AscensionProgress.Tribulation.Type type : AscensionProgress.Tribulation.Type.values()) {
            if (type.name().equals(stored)) return type;
        }
        return AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND;
    }

    /** Sets the variant, its hitbox, its maximum health and its displayed name. */
    public void applyVariant(AscensionProgress.Tribulation.Type type) {
        applyVariantBasics(type);
        setHealth(getMaxHealth());
        setCustomName(Component.literal(CarrierLabels.label(type) + " · "
                + CalamityCarrierStats.describe(getMaxHealth()) + " HP"));
    }

    /**
     * Variant, hitbox and maximum health only. Loading a save must not touch current health or the
     * saved nameplate, otherwise a partly fought carrier would heal to full on every restart.
     */
    private void applyVariantBasics(AscensionProgress.Tribulation.Type type) {
        entityData.set(VARIANT, type.name());
        double max = CalamityCarrierStats.maxHealth(type);
        if (getAttribute(Attributes.MAX_HEALTH) != null) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        }
        refreshDimensions();
    }

    /** True once the health pool emptied and the death clip is playing. */
    public boolean isDying() {
        return entityData.get(DEATH_TICKS) > 0;
    }

    /**
     * Starts the death clip. Health stays at one so vanilla never runs its own 20-tick removal,
     * which would cut a 2.2 s model clip in half.
     */
    public void beginDying() {
        if (isDying()) return;
        entityData.set(DEATH_TICKS, 1);
        setHealth(1.0f);
        setInvulnerable(true);
        setDeltaMovement(0.0, 0.0, 0.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isDying()) return;
        int elapsed = entityData.get(DEATH_TICKS) + 1;
        entityData.set(DEATH_TICKS, elapsed);
        if (elapsed >= DEATH_DURATION_TICKS) {
            discard();
        }
    }

    /** Elapsed death ticks, used by the renderer to drive the death clip. */
    public int deathTicks() {
        return entityData.get(DEATH_TICKS);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        AscensionProgress.Tribulation.Type type = variant();
        return switch (type) {
            case SINGLE_HORNED_TIGER -> EntityDimensions.scalable(4.6f, 7.0f);
            case ANCIENT_BEAST -> EntityDimensions.scalable(7.5f, 12.0f);
            case SOUND_CHIMES -> EntityDimensions.scalable(3.0f, 6.0f);
            case BLOOD_RAIN -> EntityDimensions.scalable(3.2f, 3.3f);
            case JADE_WILLOW_WIND -> EntityDimensions.scalable(4.5f, 3.8f);
            case NONE -> EntityDimensions.scalable(2.0f, 2.0f);
        };
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    protected void registerGoals() {
        // Attack rotation belongs to CalamityCombatService, not to vanilla AI.
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("CalamityVariant", variant().name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("CalamityVariant")) {
            for (AscensionProgress.Tribulation.Type type : AscensionProgress.Tribulation.Type.values()) {
                if (type.name().equals(tag.getString("CalamityVariant"))) {
                    applyVariantBasics(type);
                    break;
                }
            }
        }
    }
}
