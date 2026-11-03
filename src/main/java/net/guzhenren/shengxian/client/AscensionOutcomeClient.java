package net.guzhenren.shengxian.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.guzhenren.shengxian.network.AscensionOutcomePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Client-thread-only presentation queue. Only explicit server outcomes can open a result. */
public final class AscensionOutcomeClient {
    public static final int SUCCESS_DELAY_TICKS = 140;
    public static final int FAILURE_DELAY_TICKS = 24;
    private static final int MAX_SESSIONS = 16;
    private record Pending(AscensionOutcomePayload payload, long readyAt) {}
    private static final Map<UUID, Long> SEEN = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Pending pending;
    private static long ticks;

    private AscensionOutcomeClient() {}

    public static void accept(AscensionOutcomePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ensureWorld(mc.level);
        if (payload == null || mc.level == null || mc.player == null
                || !payload.owner().equals(mc.player.getUUID())
                || !payload.dimension().equals(mc.level.dimension().location())) return;
        Long previous = SEEN.get(payload.session());
        if (previous != null && payload.stateRevision() <= previous) return;
        SEEN.put(payload.session(), payload.stateRevision());
        while (SEEN.size() > MAX_SESSIONS) SEEN.remove(SEEN.keySet().iterator().next());
        long readyAt = pending != null && pending.payload().session().equals(payload.session())
                ? pending.readyAt() : ticks + (payload.successful() ? SUCCESS_DELAY_TICKS : FAILURE_DELAY_TICKS);
        pending = new Pending(payload, readyAt);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ensureWorld(mc.level);
        if (mc.level == null || mc.player == null) return;
        ticks++;
        if (pending == null) return;
        var payload = pending.payload();
        if (!payload.owner().equals(mc.player.getUUID())
                || !payload.dimension().equals(mc.level.dimension().location())) {
            pending = null;
            return;
        }
        if (ticks < pending.readyAt() || mc.player.isDeadOrDying()
                || VeilApertureTransitionRenderer.isRunning()
                || (mc.screen != null && !(mc.screen instanceof QiControlScreen))) return;
        pending = null;
        mc.setScreen(new AscensionResultScreen(payload));
    }

    private static void ensureWorld(ClientLevel current) {
        if (world != current) {
            clear();
            world = current;
        }
    }

    /** Also called by the disconnect event; old terminal snapshots never repopulate this queue. */
    public static void clear() {
        pending = null;
        SEEN.clear();
        ticks = 0;
        world = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AscensionResultScreen) mc.setScreen(null);
    }
}
