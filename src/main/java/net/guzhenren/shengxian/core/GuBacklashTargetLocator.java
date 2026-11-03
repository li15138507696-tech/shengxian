package net.guzhenren.shengxian.core;

import java.util.List;
import java.util.OptionalInt;

/** Pure identity-based storage location helpers used by the server adapter and regressions. */
public final class GuBacklashTargetLocator {
    private GuBacklashTargetLocator() {}

    /** Returns the serialized Minecraft inventory slot only when one list contains the exact object once. */
    public static OptionalInt inventorySlot(Object target, List<?> main, List<?> armor, List<?> offhand) {
        if (target == null || main == null || armor == null || offhand == null) return OptionalInt.empty();
        int found = -1;
        for (int i = 0; i < main.size(); i++) {
            if (main.get(i) == target) {
                if (found >= 0) return OptionalInt.empty();
                found = i;
            }
        }
        for (int i = 0; i < armor.size(); i++) {
            if (armor.get(i) == target) {
                if (found >= 0) return OptionalInt.empty();
                found = 100 + i;
            }
        }
        for (int i = 0; i < offhand.size(); i++) {
            if (offhand.get(i) == target) {
                if (found >= 0) return OptionalInt.empty();
                found = 150 + i;
            }
        }
        return found < 0 ? OptionalInt.empty() : OptionalInt.of(found);
    }

    /** Maps a proven killer-move group and slot to its upstream serialized ItemStack field. */
    public static String killerMoveField(int group, int slot) {
        if (group < 1 || group > 4 || slot < 1 || slot > 10) {
            throw new IllegalArgumentException("Invalid killer-move Gu slot");
        }
        return "ShaZhao" + group + "_GuChong" + slot;
    }
}
