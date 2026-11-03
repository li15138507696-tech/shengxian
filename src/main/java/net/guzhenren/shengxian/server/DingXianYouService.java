package net.guzhenren.shengxian.server;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.AperturePlotBounds;
import net.guzhenren.shengxian.core.TravelChannel;
import net.guzhenren.shengxian.core.XianGuRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Eight server-recorded destinations and a three-second channel that permits movement and damage. */
public final class DingXianYouService {
    private static final Map<UUID, TravelChannel> CHANNELS = new HashMap<>();
    private DingXianYouService() { }

    public static String recordMark(ServerPlayer caster) throws IOException {
        var record = XianGuUseService.check(caster, "ding_xian_you");
        var provisional = new XianGuRuntime.Mark(caster.level().dimension().location().toString(),
                caster.getX(), caster.getY(), caster.getZ(), caster.getYRot(), caster.getXRot(), "0".repeat(64));
        requireSafe(caster, caster.serverLevel(), provisional);
        var mark = new XianGuRuntime.Mark(provisional.dimension(), provisional.x(), provisional.y(), provisional.z(),
                provisional.yaw(), provisional.pitch(), scene(caster.serverLevel(), BlockPos.containing(caster.position())));
        int slot = record.progress().xianGu().selectedSlot();
        long tick = XianGuUseService.tick(caster);
        XianGuUseService.store().mutate(record.session().key(), record.session().stateRevision(),
                XianGuUseService.sequence(record, tick), tick, progress -> progress.withXianGu(progress.xianGu().withMark(slot, mark)));
        return "定仙游已记住落点 " + (slot + 1) + "：" + mark.dimension() + " " + BlockPos.containing(caster.position()).toShortString();
    }

    public static String cycleMark(ServerPlayer caster) throws IOException {
        var record = XianGuUseService.check(caster, "ding_xian_you");
        int slot = (record.progress().xianGu().selectedSlot() + 1) % 8;
        long tick = XianGuUseService.tick(caster);
        XianGuUseService.store().mutate(record.session().key(), record.session().stateRevision(),
                XianGuUseService.sequence(record, tick), tick, progress -> progress.withXianGu(progress.xianGu().selectSlot(slot)));
        var mark = record.progress().xianGu().marks().get(slot);
        return "落点 " + (slot + 1) + (mark == null ? " 尚未记录；潜行右键方块记住当前位置。"
                : "：" + mark.dimension() + " " + BlockPos.containing(mark.x(), mark.y(), mark.z()).toShortString());
    }

    public static String start(ServerPlayer caster, InteractionHand hand) throws IOException {
        var record = XianGuUseService.check(caster, "ding_xian_you");
        record.progress().xianGu().requireAvailable("ding_xian_you", XianGuUseService.tick(caster));
        if (caster.isPassenger() || !XianGuItemBinding.matches(caster.getItemInHand(hand), caster.getUUID(),
                record.session().id(), "ding_xian_you")) throw new IOException("当前手持或骑乘状态不能催动定仙游");
        var mark = record.progress().xianGu().marks().get(record.progress().xianGu().selectedSlot());
        if (mark == null) throw new IOException("当前落点尚未记录；潜行右键方块记住安全位置");
        if (CHANNELS.containsKey(caster.getUUID())) throw new IOException("定仙游已经在蓄力");
        CHANNELS.put(caster.getUUID(), new TravelChannel(XianGuUseService.tick(caster), ShengxianConfig.travelGuChannel(),
                caster.level().dimension().location().toString(), hand.name(), mark));
        caster.startUsingItem(hand);
        return "定仙游开始蓄力，可移动和承受伤害；松手或换手取消。";
    }

    public static void tick(MinecraftServer server) {
        for (var entry : new ArrayList<>(CHANNELS.entrySet())) {
            ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
            if (caster == null) { CHANNELS.remove(entry.getKey()); continue; }
            TravelChannel channel = entry.getValue();
            try {
                var record = XianGuUseService.check(caster, "ding_xian_you");
                String hand = caster.isUsingItem() ? caster.getUsedItemHand().name() : "";
                if (caster.isUsingItem() && !XianGuItemBinding.matches(caster.getUseItem(), caster.getUUID(),
                        record.session().id(), "ding_xian_you")) hand = "";
                var status = channel.status(XianGuUseService.tick(caster), caster.level().dimension().location().toString(),
                        hand, caster.isUsingItem(), caster.isAlive(), caster.isPassenger());
                if (status == TravelChannel.Status.CANCELLED) {
                    cancel(caster); XianGuUseService.message(caster, "定仙游蓄力已取消，未扣仙元。");
                } else if (status == TravelChannel.Status.READY) {
                    CHANNELS.remove(caster.getUUID()); caster.stopUsingItem();
                    perform(caster, channel.mark());
                } else if (XianGuUseService.tick(caster) % 2 == 0) {
                    XianGuEffects.channel(caster, XianGuUseService.tick(caster) - channel.startedTick());
                }
            } catch (Exception failure) {
                cancel(caster); XianGuUseService.message(caster, "定仙游未完成：" + failure.getMessage());
            }
        }
    }

    private static void perform(ServerPlayer caster, XianGuRuntime.Mark mark) throws IOException {
        ServerLevel destination = caster.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(mark.dimension())));
        if (destination == null) throw new IOException("落点维度不存在");
        loadSceneChunks(destination, BlockPos.containing(mark.x(), mark.y(), mark.z()));
        requireSafe(caster, destination, mark);
        if (!mark.scene().equals(scene(destination, BlockPos.containing(mark.x(), mark.y(), mark.z())))) {
            throw new IOException("落点周围景象已改变，请重新记录");
        }
        JsonObject plan = new JsonObject();
        plan.addProperty("kind", "travel"); plan.addProperty("dimension", mark.dimension());
        plan.addProperty("x", mark.x()); plan.addProperty("y", mark.y()); plan.addProperty("z", mark.z());
        ServerLevel source = caster.serverLevel(); Vec3 oldPosition = caster.position();
        XianGuUseService.execute(caster, "ding_xian_you", List.of(caster), plan.toString(), () -> {
            XianGuEffects.travel(source, oldPosition, false);
            caster.teleportTo(destination, mark.x(), mark.y(), mark.z(), Set.<RelativeMovement>of(), mark.yaw(), mark.pitch());
            caster.fallDistance = 0;
        }, () -> caster.serverLevel() == destination && caster.position().distanceToSqr(new Vec3(mark.x(), mark.y(), mark.z())) < 0.0000001);
        XianGuEffects.travel(destination, caster.position(), true);
        XianGuUseService.message(caster, "定仙游已抵达记忆中的落点。");
    }

    private static void requireSafe(ServerPlayer caster, ServerLevel level, XianGuRuntime.Mark mark) throws IOException {
        BlockPos pos = BlockPos.containing(mark.x(), mark.y(), mark.z());
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() + 2 >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(pos)) throw new IOException("落点超出世界边界");
        loadSceneChunks(level, pos);
        var floor = level.getBlockState(pos.below());
        if (!floor.isFaceSturdy(level, pos.below(), Direction.UP) || floor.is(Blocks.MAGMA_BLOCK)
                || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE) || floor.is(Blocks.CACTUS)) {
            throw new IOException("落点没有安全的实心地面");
        }
        AABB box = caster.getBoundingBox().move(mark.x() - caster.getX(), mark.y() - caster.getY(), mark.z() - caster.getZ());
        if (!level.noCollision(caster, box) || !level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty() || level.getBlockState(pos).is(Blocks.FIRE)
                || level.getBlockState(pos).is(Blocks.SOUL_FIRE) || level.getBlockState(pos).is(Blocks.POWDER_SNOW)
                || !level.getEntities(caster, box, entity -> entity.isAlive() && !entity.isSpectator()).isEmpty()) {
            throw new IOException("落点被方块、流体或实体占用");
        }
        if (level.dimension().equals(ApertureSpace.LEVEL)) {
            int plot = AperturePlotBounds.find(AscensionServer.formedPlotOwners().keySet(), mark.x(), mark.z());
            var owner = AscensionServer.formedPlotOwners().get(plot);
            var context = AscensionServer.apertureContext();
            if (owner == null || context == null || !(owner.owner().equals(caster.getUUID())
                    || ShengxianConfig.allowGuests() && context.spaceStore().isGuest(plot, caster.getUUID())
                    || ShengxianConfig.allowGuardians() && context.spaceStore().isGuardian(plot, caster.getUUID()))) {
                throw new IOException("当前无权进入此仙窍落点");
            }
        }
    }

    private static void loadSceneChunks(ServerLevel level, BlockPos pos) {
        for (int x = (pos.getX() - 2) >> 4; x <= (pos.getX() + 2) >> 4; x++) {
            for (int z = (pos.getZ() - 2) >> 4; z <= (pos.getZ() + 2) >> 4; z++) level.getChunk(x, z);
        }
    }
    private static String scene(ServerLevel level, BlockPos center) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int x = -2; x <= 2; x++) for (int y = -1; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                digest.update(level.getBlockState(center.offset(x, y, z)).toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static void cancel(ServerPlayer player) { CHANNELS.remove(player.getUUID()); }
    public static void clear() { CHANNELS.clear(); }
}
