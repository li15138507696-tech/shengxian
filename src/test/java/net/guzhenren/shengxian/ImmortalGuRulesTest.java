package net.guzhenren.shengxian;

import java.util.UUID;
import net.guzhenren.shengxian.core.RecentHealthHistory;
import net.guzhenren.shengxian.core.TravelChannel;
import net.guzhenren.shengxian.core.LandscapeHistory;
import net.guzhenren.shengxian.core.XianGuRuntime;

final class ImmortalGuRulesTest {
    static void health() {
        var history = new RecentHealthHistory();
        history.sample(100, 20, 0, false, false);
        history.sample(150, 5, 80, true, true);
        var restore = history.plan(180, 5, 16, 80, true, true).orElseThrow();
        require(restore.health() == 16 && restore.extinguish() && restore.clearPoison() && restore.clearWither());
        history.complete(180, 16, 0, false, false);
        require(history.plan(181, 8, 20, 0, false, false).isEmpty());
        require(history.plan(400, 8, 20, 0, false, false).isEmpty());
        history.clear();
        history.sample(500, 20, 0, false, false);
        require(history.plan(501, 0, 20, 0, false, false).isEmpty());
        require(history.plan(501, 20, 20, 0, false, false).isEmpty());
    }

    static void travel() {
        var mark = new XianGuRuntime.Mark("minecraft:overworld", 2.5, 65, 3.5, 0, 0, "a".repeat(64));
        var channel = new TravelChannel(100, 60, "minecraft:overworld", "MAIN_HAND", mark);
        require(channel.status(159, "minecraft:overworld", "MAIN_HAND", true, true, false)
                == TravelChannel.Status.PENDING);
        require(channel.status(160, "minecraft:overworld", "MAIN_HAND", true, true, false)
                == TravelChannel.Status.READY);
        require(channel.status(160, "minecraft:the_nether", "MAIN_HAND", true, true, false)
                == TravelChannel.Status.CANCELLED);
        require(channel.status(160, "minecraft:overworld", "MAIN_HAND", false, true, false)
                == TravelChannel.Status.CANCELLED);
        require(channel.status(160, "minecraft:overworld", "MAIN_HAND", true, true, true)
                == TravelChannel.Status.CANCELLED);
    }

    static void landscape() {
        UUID owner = UUID.randomUUID();
        var history = new LandscapeHistory(2, 3, 12000);
        UUID first = history.reserve(owner, 0, "shengxian:aperture", 0, 64, 0, "stone", 1).orElseThrow();
        require(history.plan(owner, 0, "shengxian:aperture", 0, 65, 0, 32, 1024, 1, e -> true).isEmpty());
        require(history.publish(first, true, true, 2));
        UUID second = history.reserve(owner, 0, "shengxian:aperture", 1, 64, 0, "dirt", 2).orElseThrow();
        require(!history.publish(second, true, false, 2));
        UUID third = history.reserve(owner, 0, "shengxian:aperture", 1, 64, 0, "dirt", 3).orElseThrow();
        require(history.publish(third, true, true, 3));
        require(history.reserve(owner, 0, "shengxian:aperture", 2, 64, 0, "stone", 4).isEmpty());
        require(history.plan(owner, 0, "shengxian:aperture", 0, 65, 0, 32, 1024, 4,
                e -> e.x() == 0).size() == 1);
        require(history.plan(UUID.randomUUID(), 0, "shengxian:aperture", 0, 65, 0, 32, 1024, 4, e -> true).isEmpty());
        require(history.consume(first) && !history.consume(first));
        require(history.plan(owner, 0, "shengxian:aperture", 0, 65, 0, 32, 1024, 12004, e -> true).isEmpty());
        require(LandscapeHistory.stepBudget(1024, 512) == 64);
        require(LandscapeHistory.stepBudget(1024, 13) == 13);
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Immortal Gu rule failed");
    }
}
