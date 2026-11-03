package net.guzhenren.shengxian.entity;

import net.guzhenren.shengxian.ShengxianMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 灾劫生物实体类型注册。 */
public final class ShengxianEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ShengxianMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CalamityCarrierEntity>> CALAMITY_CARRIER =
            ENTITIES.register("calamity_carrier",
                    () -> EntityType.Builder.<CalamityCarrierEntity>of(CalamityCarrierEntity::new,
                                    MobCategory.MONSTER)
                            .sized(4.5f, 6.0f)
                            .clientTrackingRange(10)
                            .build("calamity_carrier"));

    private ShengxianEntities() {}
}
