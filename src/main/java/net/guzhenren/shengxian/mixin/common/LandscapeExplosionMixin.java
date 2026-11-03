package net.guzhenren.shengxian.mixin.common;

import java.util.List;
import java.util.function.BiConsumer;
import net.guzhenren.shengxian.server.JiangShanRuGuService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned vanilla explosion drop branch; ordinary explosions fall through unchanged. */
@Mixin(BlockBehaviour.class)
public abstract class LandscapeExplosionMixin {
    @Redirect(method = "onExplosionHit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;getDrops(Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;"), require = 1)
    private List<ItemStack> shengxian$recordNoDrop(BlockState block, LootParams.Builder loot,
            BlockState state, Level level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> output) {
        return JiangShanRuGuService.interceptDrops(level, pos, state) ? List.of() : block.getDrops(loot);
    }

    @Inject(method = "onExplosionHit", at = @At("RETURN"), require = 1)
    private void shengxian$confirmDestroyed(BlockState state, Level level, BlockPos pos,
            Explosion explosion, BiConsumer<ItemStack, BlockPos> output, CallbackInfo callback) {
        JiangShanRuGuService.finishExplosion(level, pos);
    }
}
