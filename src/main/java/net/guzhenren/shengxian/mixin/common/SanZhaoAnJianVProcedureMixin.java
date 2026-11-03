package net.guzhenren.shengxian.mixin.common;

import net.guzhenren.shengxian.server.GuActivationObserver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the exact V kill-move durability changes across one procedure call. */
@Mixin(value = net.guzhenren.procedures.SanZhaoAnJianVAnXiaAnJianShiProcedure.class, remap = false)
public abstract class SanZhaoAnJianVProcedureMixin {
    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            require = 1, remap = false)
    private static void shengxian$beginGuObservation(net.minecraft.world.level.LevelAccessor level, double x, double y, double z,
                                                      net.minecraft.world.entity.Entity entity, CallbackInfo callbackInfo) {
        GuActivationObserver.beginKillerMoveObservation(entity, 'V');
    }

    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At("RETURN"),
            require = 1, remap = false)
    private static void shengxian$recordChangedGu(net.minecraft.world.level.LevelAccessor level, double x, double y, double z,
                                                   net.minecraft.world.entity.Entity entity, CallbackInfo callbackInfo) {
        GuActivationObserver.endKillerMoveObservation(entity, 'V');
    }
}
