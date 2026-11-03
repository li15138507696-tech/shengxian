package net.guzhenren.shengxian.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.AperturePlotBounds;
import net.guzhenren.shengxian.core.LandscapeHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Explicit opt-in, no-drop explosion history and bounded owner-plot reconstruction. */
public final class JiangShanRuGuService {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final Set<Block> TERRAIN = Set.of(Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK,
            Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.MUD, Blocks.CLAY);
    private record Position(String dimension, BlockPos pos) { }
    private record Pending(UUID token, long tick) { }
    private static LandscapeHistory history = new LandscapeHistory(8192, 65536, 12000);
    private static final Map<Position, Pending> PENDING = new HashMap<>();
    private static final LinkedHashMap<UUID, Job> JOBS = new LinkedHashMap<>();
    private static final class Job {
        final XianGuUseService.Ticket ticket;
        final List<LandscapeHistory.Entry> cells;
        int index, restored;
        Job(XianGuUseService.Ticket ticket, List<LandscapeHistory.Entry> cells) { this.ticket = ticket; this.cells = cells; }
    }
    private JiangShanRuGuService() { }

    public static String toggle(ServerPlayer caster) throws IOException {
        var record = XianGuUseService.check(caster, "jiang_shan_ru_gu");
        boolean enabled = !record.progress().xianGu().recording();
        long tick = XianGuUseService.tick(caster);
        XianGuUseService.store().mutate(record.session().key(), record.session().stateRevision(),
                XianGuUseService.sequence(record, tick), tick,
                progress -> progress.withXianGu(progress.xianGu().withRecording(enabled)));
        return enabled ? "灾毁留景已开启：本人仙窍内被记录的自然材质爆炸不掉落材料，可在十分钟内复原。"
                : "灾毁留景已关闭；已有未过期记录仍可复原。";
    }

    /** Called only at the pinned vanilla explosion getDrops invocation, before any ItemStack reaches its consumer. */
    public static boolean interceptDrops(Level rawLevel, BlockPos position, BlockState state) {
        if (!(rawLevel instanceof ServerLevel level) || !level.dimension().equals(ApertureSpace.LEVEL)
                || !TERRAIN.contains(state.getBlock()) || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        UUID token = null;
        try {
            int plot = AperturePlotBounds.find(AscensionServer.formedPlotOwners().keySet(), position.getX() + 0.5, position.getZ() + 0.5);
            var claim = AscensionServer.formedPlotOwners().get(plot);
            if (claim == null) return false;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(claim.owner());
            if (caster == null || caster.serverLevel() != level
                    || !AperturePlotBounds.contains(plot, caster.getX(), caster.getZ())) return false;
            var record = XianGuUseService.check(caster, "jiang_shan_ru_gu");
            if (!record.progress().xianGu().recording() || record.progress().aperture().plotIndex() != plot) return false;
            String encoded = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).result().orElseThrow().toString();
            long tick = level.getServer().overworld().getGameTime();
            var reserved = history.reserve(caster.getUUID(), plot, level.dimension().location().toString(),
                    position.getX(), position.getY(), position.getZ(), encoded, tick);
            if (reserved.isEmpty()) return false;
            token = reserved.get();
            PENDING.put(new Position(level.dimension().location().toString(), position.immutable()), new Pending(token, tick));
            return true;
        } catch (Exception rejected) {
            if (token != null) history.consume(token);
            return false;
        }
    }

    public static void finishExplosion(Level rawLevel, BlockPos position) {
        if (!(rawLevel instanceof ServerLevel level)) return;
        Pending pending = PENDING.remove(new Position(level.dimension().location().toString(), position));
        if (pending != null) history.publish(pending.token(), level.getBlockState(position).isAir(), true,
                level.getServer().overworld().getGameTime());
    }

    /** LevelChunk hook sees direct chunk mutations too, including place-then-mine attempts. */
    public static void blockChanged(Level rawLevel, BlockPos pos, BlockState state) {
        if (!(rawLevel instanceof ServerLevel level) || !level.dimension().equals(ApertureSpace.LEVEL) || state.isAir()) return;
        history.invalidate(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static String use(ServerPlayer caster) throws IOException {
        var record = XianGuUseService.check(caster, "jiang_shan_ru_gu");
        if (!caster.level().dimension().equals(ApertureSpace.LEVEL)) throw new IOException("江山如故目前只修复本人仙窍地形");
        int plot = record.progress().aperture().plotIndex();
        if (!AperturePlotBounds.contains(plot, caster.getX(), caster.getZ())) throw new IOException("请进入本人仙窍地块");
        var cells = history.plan(caster.getUUID(), plot, caster.level().dimension().location().toString(),
                caster.getX(), caster.getY(), caster.getZ(), ShengxianConfig.landscapeGuRange(),
                ShengxianConfig.landscapeGuBatch(), XianGuUseService.tick(caster), entry -> validCell(caster, entry));
        if (cells.isEmpty()) throw new IOException("没有可复原的无掉落灾毁记录；开启留景后才记录新的自然材质爆炸");
        JsonObject payload = new JsonObject(); JsonArray positions = new JsonArray();
        payload.addProperty("kind", "landscape"); payload.addProperty("dimension", caster.level().dimension().location().toString());
        payload.addProperty("plot", plot);
        for (var cell : cells) {
            JsonObject json = new JsonObject();
            json.addProperty("token", cell.token().toString()); json.addProperty("x", cell.x());
            json.addProperty("y", cell.y()); json.addProperty("z", cell.z()); json.addProperty("state", cell.state());
            positions.add(json);
        }
        payload.add("cells", positions);
        var ticket = XianGuUseService.begin(caster, "jiang_shan_ru_gu", List.of(caster), payload.toString());
        JOBS.put(caster.getUUID(), new Job(ticket, cells));
        return "江山如故开始复原 " + cells.size() + " 格山水。";
    }

    public static void tick(MinecraftServer server) {
        long tick = server.overworld().getGameTime();
        for (var entry : new ArrayList<>(PENDING.entrySet())) {
            if (entry.getValue().tick() < tick) {
                history.consume(entry.getValue().token()); PENDING.remove(entry.getKey());
            }
        }
        int budget = 512;
        for (UUID owner : new ArrayList<>(JOBS.keySet())) {
            if (budget <= 0) break;
            Job job = JOBS.remove(owner);
            ServerPlayer caster = server.getPlayerList().getPlayer(owner);
            try {
                if (caster == null || !caster.isAlive() || !caster.level().dimension().equals(ApertureSpace.LEVEL)) {
                    throw new IOException("修复者离线或离开仙窍");
                }
                var record = XianGuUseService.store().load(XianGuUseService.key(owner));
                if (record.progress().xianGu().operation().filter(use -> use.operationId().equals(job.ticket.prepared().operation())
                        && use.stage() == net.guzhenren.shengxian.core.XianGuRuntime.Stage.CHARGED).isEmpty()
                        || !XianGuItemBinding.held(caster, record, "jiang_shan_ru_gu")) throw new IOException("修复操作或手持身份变化");
                int steps = LandscapeHistory.stepBudget(job.cells.size() - job.index, budget);
                for (int i = 0; i < steps; i++) {
                    var cell = job.cells.get(job.index++); budget--;
                    if (!history.available(cell.token()) || !validCell(caster, cell)) continue;
                    BlockState state = decode(cell.state());
                    BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
                    if (caster.serverLevel().setBlockAndUpdate(pos, state)
                            && caster.serverLevel().getBlockState(pos).equals(state)) {
                        history.consume(cell.token()); job.restored++;
                        XianGuEffects.terrain(caster.serverLevel(), pos);
                    }
                }
                if (job.index >= job.cells.size()) {
                    server.saveAllChunks(true, false, true);
                    XianGuUseService.complete(server, job.ticket);
                    XianGuUseService.message(caster, "江山如故已复原 " + job.restored + " 格；被新方块或实体占用的位置保留现状。");
                } else JOBS.put(owner, job);
            } catch (Exception failure) {
                XianGuUseService.fail(server, job.ticket, failure);
                if (caster != null) XianGuUseService.message(caster, "山水复原中断，已完成位置保留，操作已隔离。");
            }
        }
    }

    private static boolean validCell(ServerPlayer caster, LandscapeHistory.Entry cell) {
        try {
            ServerLevel level = caster.serverLevel();
            if (!level.dimension().location().toString().equals(cell.dimension()) || !caster.getUUID().equals(cell.owner())) return false;
            var claim = AscensionServer.formedPlotOwners().get(cell.plot());
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (claim == null || !claim.owner().equals(caster.getUUID())
                    || !AperturePlotBounds.contains(cell.plot(), cell.x() + 0.5, cell.z() + 0.5)
                    || !level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).isAir() || !level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos), entity -> entity.isAlive()).isEmpty()) return false;
            BlockState state = decode(cell.state());
            return TERRAIN.contains(state.getBlock()) && !state.hasBlockEntity() && state.getFluidState().isEmpty();
        } catch (Exception malformed) { return false; }
    }
    private static BlockState decode(String json) throws IOException {
        return BlockState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result()
                .orElseThrow(() -> new IOException("地形留景状态损坏"));
    }
    public static void clearChunk(Level level, int chunkX, int chunkZ) {
        if (level instanceof ServerLevel) history.clearChunk(level.dimension().location().toString(), chunkX, chunkZ);
    }
    public static void logout(ServerPlayer caster) {
        Job job = JOBS.remove(caster.getUUID());
        if (job != null) XianGuUseService.fail(caster.server, job.ticket, new IOException("Restoration owner logged out"));
        history.clearOwner(caster.getUUID());
    }
    public static void clear(MinecraftServer server) {
        for (Job job : JOBS.values()) XianGuUseService.fail(server, job.ticket, new IOException("Server stopped"));
        JOBS.clear(); PENDING.clear(); history.clear();
    }
    public static void reset() {
        JOBS.clear(); PENDING.clear();
        history = new LandscapeHistory(8192, 65536, ShengxianConfig.landscapeGuHistory());
    }
}
