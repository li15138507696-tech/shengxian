package net.guzhenren.shengxian.server;

import java.util.UUID;
import net.guzhenren.shengxian.core.AscensionRecord;
import net.guzhenren.shengxian.item.ShengxianItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Physical projections share the owner's one authoritative cooldown and operation record. */
public final class XianGuItemBinding {
    private static final String KEY = "shengxian:immortal_gu";
    private XianGuItemBinding() { }
    public static ItemStack create(UUID owner, UUID session, String id) {
        ItemStack stack = new ItemStack(ShengxianItems.item(id));
        CompoundTag root = new CompoundTag(), tag = new CompoundTag();
        tag.putInt("version", 1); tag.putUUID("owner", owner); tag.putUUID("session", session); tag.putString("gu", id);
        root.put(KEY, tag); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return stack;
    }
    public static boolean matches(ItemStack stack, UUID owner, UUID session, String id) {
        if (stack.isEmpty() || !stack.is(ShengxianItems.item(id))) return false;
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag tag = root.getCompound(KEY);
        return tag.getInt("version") == 1 && tag.hasUUID("owner") && tag.hasUUID("session")
                && tag.getUUID("owner").equals(owner) && tag.getUUID("session").equals(session) && id.equals(tag.getString("gu"));
    }
    public static boolean held(ServerPlayer player, AscensionRecord record, String id) {
        return matches(player.getMainHandItem(), player.getUUID(), record.session().id(), id)
                || matches(player.getOffhandItem(), player.getUUID(), record.session().id(), id);
    }
    public static boolean inInventory(ServerPlayer player, AscensionRecord record, String id) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (matches(player.getInventory().getItem(slot), player.getUUID(), record.session().id(), id)) return true;
        }
        return false;
    }
    public static boolean deliver(ServerPlayer player, AscensionRecord record, String id) {
        if (inInventory(player, record, id)) return true;
        boolean delivered = player.getInventory().add(create(player.getUUID(), record.session().id(), id));
        player.containerMenu.broadcastChanges();
        return delivered;
    }
}
