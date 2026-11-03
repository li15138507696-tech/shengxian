package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.AscensionRecord;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.core.QiIntent;
import net.guzhenren.shengxian.core.QiParameters;
import net.guzhenren.shengxian.core.SessionStore;
import net.guzhenren.shengxian.core.TribulationParameters;
import net.guzhenren.shengxian.core.TribulationFx;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal server-side control channel for the three-qi prototype. A player sets bounded
 * intent fractions through the client screen or /shengxianqi; the server advances one
 * simulation step per second. The client never supplies absolute qi or results.
 */
public final class AscensionControlService {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final Map<UUID, QiIntent> INTENTS = new ConcurrentHashMap<>();
    private static final QiParameters QI = QiParameters.standard();

    public enum IntentResult {
        ACCEPTED("调气意图已设置。"),
        ROUTE_CLOSED("升仙流程尚未开放；执行 /shengxian status 查看缺失机制。"),
        STORE_UNAVAILABLE("升仙会话仓库尚未就绪。"),
        SESSION_MISSING("当前没有升仙会话；先通过“备妥”和“冲击窍壁”进入纳气阶段。"),
        NOT_ABSORBING("只有处于纳气阶段时才能调气。"),
        SESSION_ERROR("升仙会话读取失败，调气未启动。请查看服务器日志。"),
        INVALID_INTENT("调气参数无效，请使用 0 到 1 之间的数值。");

        private final String message;

        IntentResult(String message) { this.message = message; }

        public String message() { return message; }

        public boolean accepted() { return this == ACCEPTED; }
    }

    private AscensionControlService() {}

    /**
     * Accepts player intent only for an existing, server-owned ABSORBING session.
     * Missing or unreadable sessions never leave a ticking intent behind.
     */
    public static IntentResult set(ServerPlayer player, QiIntent intent) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(intent, "intent");
        if (!AscensionLifecycleService.readiness().available()) return reject(player, IntentResult.ROUTE_CLOSED);
        UUID owner = player.getUUID();
        SessionStore store = AscensionServer.store();
        if (store == null) {
            return reject(player, IntentResult.STORE_UNAVAILABLE);
        }
        try {
            var record = store.find(new ApertureKey(owner, "first"));
            if (record.isEmpty()) {
                return reject(player, IntentResult.SESSION_MISSING);
            }
            if (record.get().session().phase() != Phase.ABSORBING) {
                return reject(player, IntentResult.NOT_ABSORBING);
            }
            INTENTS.put(owner, intent);
            return IntentResult.ACCEPTED;
        } catch (Exception ex) {
            LOGGER.warn("qi intent rejected because the session could not be read for {}", owner, ex);
            return reject(player, IntentResult.SESSION_ERROR);
        }
    }

    private static IntentResult reject(ServerPlayer player, IntentResult result) {
        INTENTS.remove(player.getUUID());
        AscensionFx.send(player.serverLevel(), player.position(), player.getId(), "qi_stop", 0.0f);
        return result;
    }

    public static void seekInsight(ServerPlayer player, String insightId) {
        Objects.requireNonNull(player, "player");
        if (!AscensionLifecycleService.readiness().available()) {
            player.sendSystemMessage(Component.literal(AscensionLifecycleService.readiness().message()));
            return;
        }
        SessionStore store = AscensionServer.store();
        if (store == null) {
            player.sendSystemMessage(Component.literal("升仙会话仓库未加载。"));
            return;
        }
        ApertureKey key = new ApertureKey(player.getUUID(), "first");
        try {
            var current = store.load(key);
            long tick = player.serverLevel().getGameTime();
            long sequence = Math.max(current.session().lastActionSequence() + 1, tick);
            var result = store.seekInsight(key, current.session().stateRevision(), sequence, tick,
                    insightId, QI.windowTicksRequired());
            player.sendSystemMessage(Component.literal(result.entry().title() + "：" + result.entry().summary()));
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            player.sendSystemMessage(Component.literal("无法领悟：" + ex.getMessage()));
        }
    }
    public static void clear(UUID player) {
        INTENTS.remove(player);
    }

    public static void clearAll() {
        INTENTS.clear();
    }

    public static QiIntent intent(UUID player) {
        return INTENTS.get(player);
    }

    /**
     * 0..1 closeness of the three qi to their mean, using the same tolerance the 开窍 gate applies.
     * The audio layer turns a weak or idle step into silence, so this is a pure measurement here.
     */
    private static float harmonyOf(net.guzhenren.shengxian.core.AscensionRecord record) {
        var qi = record.progress().qi();
        double mean = (qi.heaven() + qi.earth() + qi.human()) / 3.0;
        double spread = Math.max(Math.abs(qi.heaven() - mean),
                Math.max(Math.abs(qi.earth() - mean), Math.abs(qi.human() - mean)));
        double tolerance = net.guzhenren.shengxian.core.QiParameters.standard().balanceTolerance();
        if (!(tolerance > 0) || !Double.isFinite(spread)) return 0.0f;
        return (float) Math.max(0.0, Math.min(1.0, 1.0 - spread / tolerance));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % Math.max(1, ShengxianConfig.qiTickInterval()) != 0) return;
        if (!AscensionLifecycleService.readiness().available()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (INTENTS.remove(player.getUUID()) != null) {
                    AscensionFx.send(player.serverLevel(), player.position(), player.getId(), "qi_stop", 0.0f);
                }
            }
            return;
        }
        SessionStore store = AscensionServer.store();
        if (store == null) return;
        long gameTime = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            QiIntent intent = INTENTS.getOrDefault(player.getUUID(), QiIntent.idle());
            ApertureKey key = new ApertureKey(player.getUUID(), "first");
            try {
                ServerLevel level = player.serverLevel();
                var existing = store.find(key);
                if (existing.isEmpty()) {
                    if (INTENTS.remove(player.getUUID()) != null) {
                        AscensionFx.send(level, player.position(), player.getId(), "qi_stop", 0.0f);
                    }
                    continue;
                }
                AscensionRecord record = existing.get();
                if (record.session().phase() != Phase.ABSORBING) {
                    AscensionFx.send(level, player.position(), player.getId(), "qi_stop", 0.0f);
                    INTENTS.remove(player.getUUID());
                    continue;
                }
                long sequence = Math.max(record.session().lastActionSequence() + 1, gameTime);
                var spawnOrigin = new net.guzhenren.shengxian.core.AscensionProgress.CarrierAnchor(
                        level.dimension().location().toString(), player.blockPosition().getX(),
                        player.blockPosition().getY(), player.blockPosition().getZ(), null);
                var outcome = store.stepSimulation(key, record.session().stateRevision(), sequence,
                        gameTime, intent, QI,
                        TribulationParameters.forProfile(ShengxianConfig.tribulationProfile()), spawnOrigin);
                TribulationWorldService.synchronize(player, record, outcome.record());
                for (var tribulationEvent : outcome.tribulationEvents()) {
                    switch (tribulationEvent.type()) {
                        case STARTED_WILLOW ->
                                AscensionFx.send(level, player.position(), player.getId(), "tribulation_willow_start", 0.7f);
                        case STARTED_TIGER ->
                                AscensionFx.send(level, player.position(), player.getId(), "tribulation_tiger_start", 1.0f);
                        case STARTED_SOUND ->
                                AscensionFx.send(level, player.position(), player.getId(), "tribulation_sound_start", 1.0f);
                        case STARTED_BEAST -> AscensionFx.send(level, player.position(), player.getId(), "tribulation_beast_start", 1.0f);
                        case STARTED_BLOOD_RAIN ->
                                AscensionFx.send(level, player.position(), player.getId(), "tribulation_blood_rain_start", 1.0f);
                        case IMPACT -> {
                            var impactType = record.progress().tribulations().stream()
                                    .filter(carrier -> carrier.id().equals(tribulationEvent.detail()))
                                    .map(net.guzhenren.shengxian.core.AscensionProgress.Tribulation::type)
                                    .findFirst().orElse(net.guzhenren.shengxian.core.AscensionProgress.Tribulation.Type.NONE);
                            AscensionFx.send(level, player.position(), player.getId(),
                                    TribulationFx.impactCue(impactType), 0.8f);
                        }
                        default -> { }
                    }
                }
                AscensionSounds.harmony(level, player.position(), harmonyOf(outcome.record()));
                AscensionLifecycleService.sendSnapshot(player);
                if (outcome.fatal()) {
                    AscensionFx.send(level, player.position(), player.getId(), "tribulation_fatal", 1.0f);
                    try {
                        FailureSettlementService.reconcile(player);
                    } catch (IOException failureEx) {
                        LOGGER.warn("Fatal ascension outcome was committed but its player consequence is pending for {}",
                                player.getUUID(), failureEx);
                    }
                }
                boolean finished = outcome.fatal() || outcome.record().session().phase() != Phase.ABSORBING;
                if (finished) {
                    AscensionFx.send(level, player.position(), player.getId(), "qi_stop", 0.0f);
                }
                if (finished) {
                    INTENTS.remove(player.getUUID());
                }
            } catch (Exception ex) {
                if (INTENTS.remove(player.getUUID()) != null) {
                    AscensionFx.send(player.serverLevel(), player.position(), player.getId(), "qi_stop", 0.0f);
                    player.sendSystemMessage(Component.literal("会话读取或保存失败，调气已停止。"));
                }
                LOGGER.warn("qi control stopped after a session step failed for {}", player.getUUID(), ex);
            }
        }
    }
}
