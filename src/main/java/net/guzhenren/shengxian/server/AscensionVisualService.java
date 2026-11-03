package net.guzhenren.shengxian.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.AscensionVisuals;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.network.AscensionVisualPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.LoggerFactory;

/** Observes committed sessions. Never advances phases, bypasses readiness or writes player data. */
public final class AscensionVisualService {
    private record Sent(ServerLevel level, Vec3 position, AscensionVisualPayload payload, long tick, long phaseStarted) {}
    private static final Map<UUID, Sent> SENT = new HashMap<>();
    private static final Set<UUID> WARNED = new HashSet<>();
    private AscensionVisualService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % AscensionVisuals.HEARTBEAT_TICKS != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) refresh(player);
    }

    public static void refresh(ServerPlayer player) {
        var store = AscensionServer.store();
        if (store == null || !AscensionLifecycleService.readiness().available()) {
            stop(player.getUUID());
            return;
        }
        try {
            var found = store.find(new ApertureKey(player.getUUID(), "first"));
            if (found.isEmpty()) { stop(player.getUUID()); return; }
            var record = found.get();
            var frame = AscensionVisuals.from(record, ShengxianConfig.shatterTicks());
            Sent previous = SENT.get(player.getUUID());
            if (previous != null && (previous.level() != player.serverLevel()
                    || previous.payload().entityId() != player.getId()
                    || !previous.payload().session().equals(record.session().id()))) {
                stop(player.getUUID());
                previous = null;
            }
            if (frame.phase() == Phase.IMMORTAL && !frame.confirmedFormed()) { stop(player.getUUID()); return; }
            // Existing terminal saves do not replay on login, maintenance ticks or recovery.
            if (frame.terminal() && !AscensionVisuals.announceTerminal(
                    previous == null ? null : previous.payload().frame().phase(), frame.phase())) return;
            long tick = player.serverLevel().getGameTime();
            if (previous != null && previous.payload().frame().equals(frame)
                    && tick - previous.tick() < AscensionVisuals.HEARTBEAT_TICKS) return;
            long started = previous != null && previous.payload().frame().phase() == frame.phase()
                    ? previous.phaseStarted() : tick - (previous == null ? resumeAge(frame) : 0);
            int phaseAge = (int) Math.max(0, Math.min(72000, tick - started));
            var payload = new AscensionVisualPayload(player.getUUID(), record.session().id(),
                    player.getId(), player.level().dimension().location(), false, frame, phaseAge);
            var next = new Sent(player.serverLevel(), player.position(), payload, tick, started);
            send(next, payload);
            SENT.put(player.getUUID(), next);
            WARNED.remove(player.getUUID());
            if (frame.terminal()) AscensionOutcomeService.send(player, record);
        } catch (Exception exception) {
            stop(player.getUUID());
            if (WARNED.add(player.getUUID()))
                LoggerFactory.getLogger("shengxian").warn("Visual session read failed for {}", player.getUUID(), exception);
        }
    }

    public static void stop(UUID owner) {
        Sent previous = SENT.remove(owner);
        if (previous != null) {
            var p = previous.payload();
            send(previous, new AscensionVisualPayload(p.owner(), p.session(), p.entityId(), p.dimension(), true, p.frame()));
        }
    }

    public static void clear() { SENT.clear(); WARNED.clear(); }

    private static int resumeAge(AscensionVisuals.Frame f) {
        return switch(f.phase()) {
            case ABSORBING -> 280;
            case OPENING -> 100;
            case FORMING -> 80;
            case BREAKING -> (int)(f.progress()*ShengxianConfig.shatterTicks());
            default -> 0;
        };
    }

    private static void send(Sent source, AscensionVisualPayload payload) {
        Vec3 p = source.position();
        PacketDistributor.sendToPlayersNear(source.level(), null, p.x, p.y, p.z, 64, payload);
    }
}
