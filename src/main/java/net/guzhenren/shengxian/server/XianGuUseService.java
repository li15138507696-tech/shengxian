package net.guzhenren.shengxian.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.compat.GuzhenrenPlayerBridge;
import net.guzhenren.shengxian.compat.SavedXianGuUseReceipt;
import net.guzhenren.shengxian.compat.SavedXianGuUseReceipt.Receipt;
import net.guzhenren.shengxian.compat.SavedXianGuUseReceipt.Stage;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.AscensionRecord;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.core.SessionStore;
import net.guzhenren.shengxian.core.XianGuOperations;
import net.guzhenren.shengxian.core.XianGuRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared authoritative checks, atomic essence charge, participant receipts and no-replay recovery. */
public final class XianGuUseService {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final GuzhenrenPlayerBridge UPSTREAM = new GuzhenrenPlayerBridge();
    private static final Set<UUID> LOCKED = new HashSet<>();
    public record Ticket(Receipt prepared, List<UUID> subjects) { }
    private XianGuUseService() { }

    public static ApertureKey key(UUID owner) { return new ApertureKey(owner, "first"); }
    public static long tick(ServerPlayer player) { return player.server.overworld().getGameTime(); }
    public static SessionStore store() throws IOException {
        SessionStore store = AscensionServer.store();
        if (store == null) throw new IOException("升仙会话仓库尚未加载");
        return store;
    }
    public static AscensionRecord check(ServerPlayer caster, String id) throws IOException {
        if (!XianGuRuntime.IDS.contains(id) || !caster.isAlive() || caster.isSpectator()) throw new IOException("当前不能催动仙蛊");
        if (LOCKED.contains(caster.getUUID())) throw new IOException("当前玩家正参与另一项仙蛊操作");
        reconcileParticipant(caster);
        AscensionRecord record = store().find(key(caster.getUUID())).orElseThrow(() -> new IOException("尚无成仙会话"));
        double rank = UPSTREAM.inspect(caster).rank();
        if (record.session().phase() != Phase.IMMORTAL || !record.progress().aperture().formed()
                || !Double.isFinite(rank) || rank < 6) throw new IOException("仙蛊要求已成仙窍且本体至少六转");
        if (tick(caster) < record.session().serverTick()) throw new IOException("世界计时早于仙蛊存档，操作已保留");
        if (!record.progress().xianGu().held().contains(id)) throw new IOException("当前会话未持有这只仙蛊");
        if (!XianGuItemBinding.held(caster, record, id)) throw new IOException("请手持绑定本人当前会话的仙蛊");
        if (record.progress().xianGu().blocked()) throw new IOException("仙蛊存在未确认操作，请查看 /shengxiangu status");
        return record;
    }

    public static boolean activate(ServerPlayer caster, String id, boolean sneak, InteractionHand hand, boolean onBlock) {
        try {
            String message = switch (id) {
                case "ding_xian_you" -> sneak ? (onBlock ? DingXianYouService.recordMark(caster)
                        : DingXianYouService.cycleMark(caster)) : DingXianYouService.start(caster, hand);
                case "ren_ru_gu_immortal" -> RenRuGuService.use(caster, sneak);
                case "jiang_shan_ru_gu" -> sneak ? JiangShanRuGuService.toggle(caster) : JiangShanRuGuService.use(caster);
                default -> throw new IOException("未知仙蛊");
            };
            message(caster, message);
            return true;
        } catch (Exception failure) {
            message(caster, "仙蛊：" + failure.getMessage());
            LOGGER.debug("Immortal Gu {} rejected for {}", id, caster.getUUID(), failure);
            return false;
        }
    }

    public static Ticket begin(ServerPlayer caster, String id, List<ServerPlayer> affected, String payload) throws IOException {
        AscensionRecord record = check(caster, id);
        long tick = tick(caster);
        record.progress().xianGu().requireAvailable(id, tick);
        double cost = ShengxianConfig.immortalGuCost(id);
        long cooldown = ShengxianConfig.immortalGuCooldown(id);
        if (record.progress().aperture().essence() < cost) throw new IOException("青提仙元不足，需要 " + cost);
        LinkedHashMap<UUID, ServerPlayer> participants = new LinkedHashMap<>();
        participants.put(caster.getUUID(), caster);
        for (ServerPlayer player : affected) participants.put(player.getUUID(), player);
        for (var player : participants.values()) if (!player.isAlive() || player.isSpectator()
                || LOCKED.contains(player.getUUID())) throw new IOException("相关玩家正参与其他仙蛊操作");
        preflightSources(caster.server, participants.values());
        for (var player : participants.values()) {
            reconcileParticipant(player);
            Optional<Receipt> old = SavedXianGuUseReceipt.read(player.getPersistentData());
            if (old.isPresent() && !old.get().terminal()) throw new IOException("相关玩家的上次仙蛊操作尚未确认");
        }
        // reconcileParticipant can save all players when it confirms an earlier operation.
        // Check every participant again after reconciliation and before the first PREPARED write.
        preflightSources(caster.server, participants.values());
        JsonObject plan = JsonParser.parseString(payload).getAsJsonObject();
        JsonArray subjects = new JsonArray(); participants.keySet().forEach(uuid -> subjects.add(uuid.toString()));
        plan.add("subjects", subjects);
        Receipt prepared = new Receipt(caster.getUUID(), record.session().id(), UUID.randomUUID(),
                record.progress().xianGu().sequence() + 1, tick, id, Stage.PREPARED, plan.toString());
        Ticket ticket = new Ticket(prepared, List.copyOf(participants.keySet()));
        LOCKED.addAll(participants.keySet());
        try {
            for (ServerPlayer player : participants.values()) persist(player, prepared, false, true);
            store().mutate(record.session().key(), record.session().stateRevision(), sequence(record, tick), tick,
                    progress -> XianGuOperations.charge(progress, prepared.operation(), id, tick, cost, cooldown, prepared.payload()));
            return ticket;
        } catch (Exception failure) {
            settleFailedPreparation(caster.server, ticket);
            unlock(ticket);
            throw io("仙蛊准备或扣费未完成", failure);
        }
    }

    public static void execute(ServerPlayer caster, String id, List<ServerPlayer> affected, String payload,
                                Runnable effect, BooleanSupplier verify) throws IOException {
        Ticket ticket = begin(caster, id, affected, payload);
        try {
            effect.run();
            if (!verify.getAsBoolean()) throw new IOException("仙蛊效果读回与预期不符");
            complete(caster.server, ticket);
        } catch (Exception failure) {
            fail(caster.server, ticket, failure);
            throw io("仙蛊效果未确认，操作已隔离", failure);
        }
    }

    public static void complete(MinecraftServer server, Ticket ticket) throws IOException {
        try {
            for (UUID uuid : ticket.subjects()) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player == null || !player.isAlive()) throw new IOException("相关玩家离线或角色已变化");
                persist(player, ticket.prepared().withStage(Stage.COMMITTED), true, false);
            }
            finishWorld(server, ticket.prepared(), XianGuRuntime.Stage.COMMITTED);
            awardCommittedUse(server, ticket);
        } finally { unlock(ticket); }
    }

    /** Only the caster earns the first-use node, and only after the use is durably committed. */
    private static void awardCommittedUse(MinecraftServer server, Ticket ticket) {
        ServerPlayer caster = server.getPlayerList().getPlayer(ticket.prepared().caster());
        if (caster != null) AscensionAdvancements.grantImmortalGuUse(caster);
    }

    public static void fail(MinecraftServer server, Ticket ticket, Throwable failure) {
        try { finishWorld(server, ticket.prepared(), XianGuRuntime.Stage.UNCERTAIN); }
        catch (Exception secondary) { LOGGER.warn("Could not isolate immortal Gu operation {}", ticket.prepared().operation(), secondary); }
        for (UUID uuid : ticket.subjects()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            try {
                var saved = SavedXianGuUseReceipt.read(player.getPersistentData());
                if (saved.filter(r -> r.sameOperation(ticket.prepared()) && r.stage() == Stage.PREPARED).isPresent()) {
                    persist(player, ticket.prepared().withStage(Stage.UNCERTAIN), false, false);
                }
            } catch (Exception secondary) { LOGGER.warn("Could not save uncertain Gu participant {}", uuid, secondary); }
        }
        unlock(ticket);
        LOGGER.warn("Immortal Gu operation {} retained after failure", ticket.prepared().operation(), failure);
    }

    private static void settleFailedPreparation(MinecraftServer server, Ticket ticket) {
        try {
            SessionStore store = store();
            store.evict(ticket.prepared().caster());
            var record = store.find(key(ticket.prepared().caster())).orElseThrow();
            if (record.progress().xianGu().operation().filter(use -> use.operationId().equals(ticket.prepared().operation())).isPresent()) {
                fail(server, ticket, new IOException("Charge may already be durable"));
            } else if (record.progress().xianGu().sequence() < ticket.prepared().sequence()) {
                for (UUID uuid : ticket.subjects()) {
                    var player = server.getPlayerList().getPlayer(uuid);
                    if (player != null && SavedXianGuUseReceipt.read(player.getPersistentData())
                            .filter(ticket.prepared()::equals).isPresent()) {
                        persist(player, ticket.prepared().withStage(Stage.ABORTED), false, false);
                    }
                }
            }
        } catch (Exception failure) { LOGGER.warn("Unfinished Gu preparation retained: {}", ticket.prepared().operation(), failure); }
    }

    private static void finishWorld(MinecraftServer server, Receipt receipt, XianGuRuntime.Stage stage) throws IOException {
        SessionStore store = store();
        AscensionRecord record = store.load(key(receipt.caster()));
        var use = record.progress().xianGu().operation().orElseThrow(() -> new IOException("世界仙蛊回执缺失"));
        if (!record.session().id().equals(receipt.session()) || !use.operationId().equals(receipt.operation())) {
            throw new IOException("世界仙蛊回执身份冲突");
        }
        if (use.stage() == stage || use.stage() == XianGuRuntime.Stage.COMMITTED
                && stage == XianGuRuntime.Stage.UNCERTAIN) return;
        long tick = Math.max(server.overworld().getGameTime(), record.session().serverTick());
        store.mutate(record.session().key(), record.session().stateRevision(), sequence(record, tick), tick,
                progress -> progress.withXianGu(progress.xianGu().finish(receipt.operation(), stage)));
    }

    /** New live requests explain normal movement since the prior save; source receipt conflicts are never overwritten. */
    private static void persist(ServerPlayer player, Receipt next, boolean effect, boolean replacement) throws IOException {
        Optional<Receipt> previous = SavedXianGuUseReceipt.read(player.getPersistentData());
        Path file = playerFile(player.server, player.getUUID());
        SavedXianGuUseReceipt.verifySource(file, player.getUUID(), previous, false);
        boolean host = player.server.isSingleplayerOwner(player.getGameProfile());
        Path levelFile = player.server.getWorldPath(LevelResource.LEVEL_DATA_FILE);
        if (host) SavedXianGuUseReceipt.verifySource(levelFile, player.getUUID(), previous, true);
        SavedXianGuUseReceipt.recordParticipant(player.getPersistentData(), player.getUUID(), next, replacement);
        player.server.getPlayerList().saveAll();
        SavedXianGuUseReceipt.verify(file, player.getUUID(), next, effect);
        if (host) {
            player.server.saveAllChunks(true, false, true);
            SavedXianGuUseReceipt.verifyHost(levelFile, player.getUUID(), next, effect);
        }
    }

    private static void preflightSources(MinecraftServer server, java.util.Collection<ServerPlayer> participants) throws IOException {
        List<SavedXianGuUseReceipt.Source> sources = new ArrayList<>();
        for (ServerPlayer player : participants) {
            Optional<Receipt> previous = SavedXianGuUseReceipt.read(player.getPersistentData());
            sources.add(new SavedXianGuUseReceipt.Source(playerFile(server, player.getUUID()), player.getUUID(), previous, false));
            if (server.isSingleplayerOwner(player.getGameProfile())) {
                sources.add(new SavedXianGuUseReceipt.Source(server.getWorldPath(LevelResource.LEVEL_DATA_FILE),
                        player.getUUID(), previous, true));
            }
        }
        SavedXianGuUseReceipt.verifySources(sources);
    }

    /** Confirms finished receipts; never repeats a past heal, teleport or terrain write. */
    public static void reconcileParticipant(ServerPlayer player) throws IOException {
        var saved = SavedXianGuUseReceipt.read(player.getPersistentData());
        if (saved.isEmpty() || LOCKED.contains(player.getUUID())) return;
        Receipt receipt = saved.get();
        var found = store().find(key(receipt.caster()));
        if (found.isEmpty() && receipt.terminal()) return;
        var record = found.orElseThrow(() -> new IOException("相关仙蛊会话缺失"));
        if (!record.session().id().equals(receipt.session())) throw new IOException("仙蛊旧会话身份冲突");
        var world = record.progress().xianGu();
        if (world.sequence() < receipt.sequence() && receipt.stage() == Stage.PREPARED) {
            persist(player, receipt.withStage(Stage.ABORTED), false, false);
            return;
        }
        var use = world.operation().filter(value -> value.operationId().equals(receipt.operation())).orElse(null);
        if (use == null) {
            if (receipt.terminal()) return;
            throw new IOException("仙蛊回执缺少相同世界操作，记录已保留");
        }
        if (!receipt.matchesWorld(record.session().id(), use)) throw new IOException("玩家与世界仙蛊回执内容冲突");
        if (use.stage() == XianGuRuntime.Stage.ACKNOWLEDGED) {
            if (receipt.stage() == Stage.ACKNOWLEDGED) return;
            persist(player, receipt.withStage(Stage.ACKNOWLEDGED), false, true);
            return;
        }
        if (use.stage() == XianGuRuntime.Stage.CHARGED) {
            JsonArray subjects = JsonParser.parseString(use.payload()).getAsJsonObject().getAsJsonArray("subjects");
            Receipt committed = receipt.withStage(Stage.COMMITTED);
            boolean allCommitted = true;
            for (var subject : subjects) {
                UUID uuid = UUID.fromString(subject.getAsString());
                try {
                    SavedXianGuUseReceipt.verify(playerFile(player.server, uuid), uuid, committed, false);
                    var profile = player.server.getSingleplayerProfile();
                    if (profile != null && uuid.equals(profile.getId())) {
                        SavedXianGuUseReceipt.verifyHost(player.server.getWorldPath(LevelResource.LEVEL_DATA_FILE), uuid, committed, false);
                    }
                } catch (IOException conflict) { allCommitted = false; }
            }
            finishWorld(player.server, receipt, allCommitted ? XianGuRuntime.Stage.COMMITTED : XianGuRuntime.Stage.UNCERTAIN);
            if (allCommitted) {
                if (receipt.stage() == Stage.PREPARED) SavedXianGuUseReceipt.record(player.getPersistentData(), committed, false);
                return;
            }
        }
        if (receipt.terminal()) return;
        throw new IOException("上次仙蛊效果未确认；请管理员查看 operation " + receipt.operation());
    }

    public static String acknowledge(ServerPlayer target, UUID operation) throws IOException {
        AscensionRecord record = store().find(key(target.getUUID())).orElseThrow(() -> new IOException("没有仙蛊会话"));
        var use = record.progress().xianGu().operation().filter(value -> value.operationId().equals(operation))
                .orElseThrow(() -> new IOException("operation ID 不匹配"));
        var plan = JsonParser.parseString(use.payload()).getAsJsonObject();
        List<ServerPlayer> participants = new ArrayList<>();
        for (var value : plan.getAsJsonArray("subjects")) {
            ServerPlayer player = target.server.getPlayerList().getPlayer(UUID.fromString(value.getAsString()));
            if (player == null) throw new IOException("所有关联玩家需上线以核对回执");
            participants.add(player);
        }
        Receipt receipt = new Receipt(target.getUUID(), record.session().id(), operation, use.sequence(), use.tick(),
                use.guId(), Stage.PREPARED, use.payload());
        if (use.stage() == XianGuRuntime.Stage.CHARGED) finishWorld(target.server, receipt, XianGuRuntime.Stage.UNCERTAIN);
        if (use.stage() != XianGuRuntime.Stage.COMMITTED) finishWorld(target.server, receipt, XianGuRuntime.Stage.ACKNOWLEDGED);
        for (ServerPlayer player : participants) {
            var prior = SavedXianGuUseReceipt.read(player.getPersistentData());
            if (prior.filter(value -> value.sameOperation(receipt)).isPresent() && !prior.get().terminal()) {
                persist(player, prior.get().withStage(Stage.ACKNOWLEDGED), false, true);
            }
        }
        return "已按管理员指令接受该操作现状；没有重放效果或补发仙元。";
    }

    public static String status(ServerPlayer player) throws IOException {
        AscensionRecord record = store().find(key(player.getUUID())).orElseThrow(() -> new IOException("无升仙会话"));
        StringBuilder text = new StringBuilder("仙元=" + record.progress().aperture().essence());
        for (String id : record.progress().xianGu().held()) {
            long left = Math.max(0, record.progress().xianGu().cooldowns().getOrDefault(id, 0L) - tick(player));
            text.append(" | ").append(id).append(" 冷却 ").append((left + 19) / 20).append(" 秒");
        }
        record.progress().xianGu().operation().ifPresent(use -> text.append(" | operation=").append(use.operationId()).append(' ').append(use.stage()));
        return text.toString();
    }
    public static int recover(ServerPlayer player) throws IOException {
        AscensionRecord record = store().find(key(player.getUUID())).orElseThrow(() -> new IOException("无升仙会话"));
        int delivered = 0;
        for (String id : record.progress().xianGu().held()) {
            if (!XianGuItemBinding.inInventory(player, record, id) && XianGuItemBinding.deliver(player, record, id)) delivered++;
        }
        player.server.getPlayerList().saveAll();
        return delivered;
    }
    public static void clear() { LOCKED.clear(); }
    public static void message(ServerPlayer player, String text) { player.displayClientMessage(Component.literal(text), true); }
    public static long sequence(AscensionRecord record, long tick) { return Math.max(record.session().lastActionSequence() + 1, tick); }
    private static Path playerFile(MinecraftServer server, UUID owner) { return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(owner + ".dat"); }
    private static void unlock(Ticket ticket) { LOCKED.removeAll(ticket.subjects()); }
    private static IOException io(String message, Exception cause) { return cause instanceof IOException value ? value : new IOException(message, cause); }
}
