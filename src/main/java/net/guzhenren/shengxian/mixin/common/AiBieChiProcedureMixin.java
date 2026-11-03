package net.guzhenren.shengxian.mixin.common;

import net.guzhenren.procedures.AiBieChiDangShengWuShiTiBeiGaiWuPinJiZhongProcedure;
import net.guzhenren.shengxian.core.GuActivation;
import net.guzhenren.shengxian.server.GuActivationObserver;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stages the accepted hit and commits after its synchronous target effects return. */
@Mixin(value = net.guzhenren.procedures.AiBieChiDangShengWuShiTiBeiGaiWuPinJiZhongProcedure.class, remap = false)
public abstract class AiBieChiProcedureMixin {
    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/guzhenren/network/GuzhenrenModVariables$PlayerVariables;zhenyuan:D",
                    opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
            require = 1, remap = false)
    private static void shengxian$stageAcceptedGu(net.minecraft.world.level.LevelAccessor level, double x, double y, double z, net.minecraft.world.entity.Entity target, net.minecraft.world.entity.Entity attacker, net.minecraft.world.item.ItemStack stack, CallbackInfo callbackInfo) {
        GuActivationObserver.stageAccepted(attacker, stack, GuActivation.Entry.ATTACK_HURT_ENEMY);
    }

    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("RETURN"), require = 1, remap = false)
    private static void shengxian$completeAfterHitEffects(net.minecraft.world.level.LevelAccessor level,
                                                           double x, double y, double z,
                                                           net.minecraft.world.entity.Entity target,
                                                           net.minecraft.world.entity.Entity attacker,
                                                           net.minecraft.world.item.ItemStack stack,
                                                           CallbackInfo callbackInfo) {
        GuActivationObserver.completeStaged(attacker, stack, GuActivation.Entry.ATTACK_HURT_ENEMY,
                net.guzhenren.shengxian.server.GuBacklashService.TargetRef.inventory());
    }
}
