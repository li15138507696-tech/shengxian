package net.guzhenren.shengxian.core;

import java.util.UUID;

/** One atomic domain mutation for a reusable Gu debit and its cooldown/operation receipt. */
public final class XianGuOperations {
    private XianGuOperations() { }

    public static AscensionProgress charge(AscensionProgress progress, UUID operation, String gu,
                                            long tick, double cost, long cooldownTicks, String payload) {
        var previous = progress.xianGu().operation();
        if (previous.isPresent() && previous.get().operationId().equals(operation)) {
            progress.xianGu().begin(operation, gu, tick, previous.get().essenceBefore(), cost, cooldownTicks, payload);
            return progress;
        }
        var runtime = progress.xianGu().begin(operation, gu, tick, progress.aperture().essence(),
                cost, cooldownTicks, payload);
        return ApertureEngine.spendEssence(progress, cost).withXianGu(runtime);
    }
}
