package net.guzhenren.shengxian.mixin.common;

import net.guzhenren.procedures.YueGuangGuDangYouJiKongQiShiShiTiDeWeiZhiProcedure;
import net.guzhenren.shengxian.core.GuActivation;
import net.guzhenren.shengxian.server.GuActivationObserver;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stages one accepted branch and commits only after its delayed projectile was added. */
@Mixin(value = net.guzhenren.procedures.YueGuangGuDangYouJiKongQiShiShiTiDeWeiZhiProcedure.class, remap = false)
public abstract class YueGuangGuProcedureMixin {
    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/guzhenren/network/GuzhenrenModVariables$PlayerVariables;zhenyuan:D",
                    opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
            require = 1, remap = false)
    private static void shengxian$stageAcceptedGu(net.minecraft.world.level.LevelAccessor level, double x, double y, double z, net.minecraft.world.entity.Entity entity, net.minecraft.world.item.ItemStack stack, CallbackInfo callbackInfo) {
        GuActivationObserver.stageDelayed(entity, stack, GuActivation.Entry.RIGHT_CLICK_USE);
    }

    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "lambda$execute$1(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            require = 1, remap = false)
    private static boolean shengxian$confirmProjectileSpawn(net.minecraft.world.level.Level level,
                                                             net.minecraft.world.entity.Entity projectile) {
        boolean added = level.addFreshEntity(projectile);
        if (projectile instanceof net.minecraft.world.entity.projectile.Projectile arrow) {
            if (added) GuActivationObserver.completeDelayed(arrow.getOwner(), GuActivation.Entry.RIGHT_CLICK_USE);
            else GuActivationObserver.discardDelayed(arrow.getOwner(), GuActivation.Entry.RIGHT_CLICK_USE);
        }
        return added;
    }
}
