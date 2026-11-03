package net.guzhenren.shengxian.mixin.common;

import net.guzhenren.procedures.BaiXiangSheGuYouJiKongQiShiShiTiDeWeiZhiProcedure;
import net.guzhenren.shengxian.core.GuActivation;
import net.guzhenren.shengxian.server.GuActivationObserver;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stages the accepted branch and commits only after the summoned snake exists. */
@Mixin(value = net.guzhenren.procedures.BaiXiangSheGuYouJiKongQiShiShiTiDeWeiZhiProcedure.class, remap = false)
public abstract class BaiXiangSheGuProcedureMixin {
    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/guzhenren/network/GuzhenrenModVariables$PlayerVariables;zhenyuan:D",
                    opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
            require = 1, remap = false)
    private static void shengxian$stageAcceptedGu(net.minecraft.world.level.LevelAccessor level, double x, double y, double z, net.minecraft.world.entity.Entity entity, net.minecraft.world.item.ItemStack stack, CallbackInfo callbackInfo) {
        GuActivationObserver.stageAccepted(entity, stack, GuActivation.Entry.RIGHT_CLICK_USE);
    }

    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(DDD)V",
                    shift = At.Shift.AFTER),
            require = 1, remap = false)
    private static void shengxian$completeAfterSnakeSpawn(net.minecraft.world.level.LevelAccessor level,
                                                           double x, double y, double z,
                                                           net.minecraft.world.entity.Entity entity,
                                                           net.minecraft.world.item.ItemStack stack,
                                                           CallbackInfo callbackInfo) {
        GuActivationObserver.completeStaged(entity, stack, GuActivation.Entry.RIGHT_CLICK_USE,
                net.guzhenren.shengxian.server.GuBacklashService.TargetRef.inventory());
    }
}
