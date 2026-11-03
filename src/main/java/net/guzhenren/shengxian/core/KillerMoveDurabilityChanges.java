package net.guzhenren.shengxian.core;

import java.util.List;

/** Identifies killer-move slots whose upstream durability changed during one procedure call. */
public final class KillerMoveDurabilityChanges {
    public record Slot(int group, int slot) {}

    private KillerMoveDurabilityChanges() {}

    public static List<Slot> changedSlots(double[][] before, double[][] after) {
        validate(before, "before");
        validate(after, "after");
        List<Slot> changed = new java.util.ArrayList<>();
        for (int group = 0; group < 4; group++) {
            for (int slot = 0; slot < 10; slot++) {
                if (Double.doubleToLongBits(before[group][slot]) != Double.doubleToLongBits(after[group][slot])) {
                    changed.add(new Slot(group + 1, slot + 1));
                }
            }
        }
        return List.copyOf(changed);
    }

    private static void validate(double[][] values, String name) {
        if (values == null || values.length != 4) throw new IllegalArgumentException(name + " must contain four Gu groups");
        for (double[] group : values) {
            if (group == null || group.length != 10) throw new IllegalArgumentException(name + " groups must contain ten slots");
        }
    }
}
