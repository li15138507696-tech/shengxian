package net.guzhenren.shengxian.mixin.common;

import net.guzhenren.shengxian.server.JiangShanRuGuService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents an old no-drop record from surviving a later block placement, including direct chunk writes. */
@Mixin(LevelChunk.class)
public abstract class LandscapeBlockChangeMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"), require = 1)
    private void shengxian$invalidateReplaced(BlockPos pos, BlockState state, boolean moving,
                                              CallbackInfoReturnable<BlockState> callback) {
        if (callback.getReturnValue() != null && !state.isAir()) {
            JiangShanRuGuService.blockChanged(((LevelChunk) (Object) this).getLevel(), pos, state);
        }
    }
}
