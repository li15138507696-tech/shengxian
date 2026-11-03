package net.guzhenren.shengxian.item;

import net.guzhenren.shengxian.ShengxianMod;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ShengxianItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShengxianMod.MOD_ID);
    public static final DeferredItem<ImmortalGuItem> DING_XIAN_YOU = ITEMS.register("ding_xian_you",
            () -> new ImmortalGuItem("ding_xian_you", new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ImmortalGuItem> REN_RU_GU = ITEMS.register("ren_ru_gu_immortal",
            () -> new ImmortalGuItem("ren_ru_gu_immortal", new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ImmortalGuItem> JIANG_SHAN_RU_GU = ITEMS.register("jiang_shan_ru_gu",
            () -> new ImmortalGuItem("jiang_shan_ru_gu", new Item.Properties().stacksTo(1)));
    private ShengxianItems() { }
    public static Item item(String id) {
        return switch (id) {
            case "ding_xian_you" -> DING_XIAN_YOU.get();
            case "ren_ru_gu_immortal" -> REN_RU_GU.get();
            case "jiang_shan_ru_gu" -> JIANG_SHAN_RU_GU.get();
            default -> throw new IllegalArgumentException("Unknown immortal Gu item");
        };
    }
}
