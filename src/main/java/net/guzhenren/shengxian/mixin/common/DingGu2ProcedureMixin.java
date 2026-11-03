package net.guzhenren.shengxian.mixin.common;

import net.guzhenren.procedures.DingGu2YouJiFangKuaiShiFangKuaiDeWeiZhiProcedure;
import net.guzhenren.shengxian.core.GuActivation;
import net.guzhenren.shengxian.server.GuActivationObserver;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stages the accepted branch and commits only when the target block placement succeeds. */
@Mixin(value = net.guzhenren.procedures.DingGu2YouJiFangKuaiShiFangKuaiDeWeiZhiProcedure.class, remap = false)
public abstract class DingGu2ProcedureMixin {
    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/guzhenren/network/GuzhenrenModVariables$PlayerVariables;zhenyuan:D",
                    opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
            require = 1, remap = false)
    private static void shengxian$stageAcceptedGu(net.minecraft.world.level.LevelAccessor level, double x, double y, double z, net.minecraft.world.entity.Entity entity, net.minecraft.world.item.ItemStack stack, CallbackInfo callbackInfo) {
        GuActivationObserver.stageAccepted(entity, stack, GuActivation.Entry.BLOCK_USE_ON);
    }

    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            require = 1, remap = false)
    private static boolean shengxian$confirmBlockPlacement(net.minecraft.world.level.LevelAccessor world,
                                                            net.minecraft.core.BlockPos position,
                                                            net.minecraft.world.level.block.state.BlockState state,
                                                            int flags) {
        boolean placed = world.setBlock(position, state, flags);
        if (placed) {
            GuActivationObserver.completeStaged(GuActivation.Entry.BLOCK_USE_ON,
                    net.guzhenren.shengxian.server.GuBacklashService.TargetRef.inventory());
        } else {
            GuActivationObserver.discardStaged(GuActivation.Entry.BLOCK_USE_ON);
        }
        return placed;
    }
}
