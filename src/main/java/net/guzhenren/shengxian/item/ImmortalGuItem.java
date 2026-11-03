package net.guzhenren.shengxian.item;

import java.util.List;
import net.guzhenren.shengxian.server.DingXianYouService;
import net.guzhenren.shengxian.server.XianGuUseService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class ImmortalGuItem extends Item {
    private final String guId;
    public ImmortalGuItem(String guId, Properties properties) { super(properties); this.guId = guId; }
    public String guId() { return guId; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            if (guId.equals("ding_xian_you") && !player.isShiftKeyDown()) player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        boolean accepted = player instanceof ServerPlayer serverPlayer
                && XianGuUseService.activate(serverPlayer, guId, player.isShiftKeyDown(), hand, false);
        return accepted ? InteractionResultHolder.consume(player.getItemInHand(hand))
                : InteractionResultHolder.fail(player.getItemInHand(hand));
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) {
            if (guId.equals("ding_xian_you") && !player.isShiftKeyDown()) player.startUsingItem(context.getHand());
            return InteractionResult.SUCCESS;
        }
        return XianGuUseService.activate((ServerPlayer) player, guId, player.isShiftKeyDown(), context.getHand(), true)
                ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (entity instanceof ServerPlayer player && guId.equals("ding_xian_you")) DingXianYouService.cancel(player);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.shengxian." + guId));
        lines.add(Component.translatable("tooltip.shengxian.immortal_gu_binding"));
    }
}
