package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.AperturePlotPolicy;
import net.guzhenren.shengxian.core.ApertureSpaceStore;
import net.guzhenren.shengxian.core.ApertureSpaceStore.ReturnPoint;
import net.guzhenren.shengxian.core.AscensionRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.Set;

/** Shared bounded aperture plots; server-side protection enforces their owner boundaries. */
public final class ApertureSpace {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("shengxian", "aperture"));
    private static final int SPACING = 256;
    private static final int FLOOR_Y = 64;

    private ApertureSpace() {}

    public static BlockPos origin(int plot) {
        if (!ApertureSpaceStore.isValidPlot(plot)) throw new IllegalArgumentException("Invalid plot");
        return new BlockPos((plot % 64) * SPACING, FLOOR_Y, (plot / 64) * SPACING);
    }

    /** Enter only a formed and authorized aperture; refuse to overwrite occupied terrain. */
    public static void enter(ServerPlayer player, ApertureKey key) throws IOException {
        AscensionServer.ApertureContext context = AscensionServer.apertureContext();
        if (context == null) throw new IOException("Aperture space is not ready");
        AscensionRecord record = context.store().load(key);
        if (!record.progress().aperture().formed()) throw new IOException("Aperture not formed");
        int plot = record.progress().aperture().plotIndex();
        if (!ApertureSpaceStore.isValidPlot(plot)) throw new IOException("Aperture has no reserved plot");
        ApertureKey claim = AscensionServer.formedPlotOwners().get(plot);
        if (!key.equals(claim)) throw new IOException("Aperture plot claim does not match the session");
        ApertureSpaceStore plots = context.spaceStore();
        boolean owner = key.owner().equals(player.getUUID());
        if (!owner && !(ShengxianConfig.allowGuests() && plots.isGuest(plot, player.getUUID()))
                && !(ShengxianConfig.allowGuardians() && plots.isGuardian(plot, player.getUUID()))) {
            throw new IOException("Not an authorized aperture visitor");
        }
        ServerLevel level = player.server.getLevel(LEVEL);
        if (level == null) throw new IOException("Aperture dimension is missing");

        BlockPos origin = origin(plot);
        level.getChunkAt(origin);
        boolean built = plots.isBuilt(plot);
        if (!built) {
            boolean floorEmpty = isPlatformEmpty(level, origin);
            boolean landingClear = hasHeadroom(level, origin);
            if (!AperturePlotPolicy.mayGeneratePlatform(plot, built, floorEmpty, landingClear)) {
                if (!floorEmpty) throw new IOException("Aperture plot is occupied; refusing to replace existing blocks");
                throw new IOException("Aperture landing space is obstructed");
            }
        } else {
            requireSafeLanding(level, origin);
        }
        level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
        if (!player.level().dimension().equals(LEVEL)) {
            plots.rememberReturn(player.getUUID(), new ReturnPoint(
                    player.level().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        }
        if (!built) buildPlatform(level, origin, plots, plot);
        player.teleportTo(level, origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        AscensionFx.send(level, Vec3.atCenterOf(origin), player.getId(), "aperture_open", 1.0f);
    }

    private static boolean isPlatformEmpty(ServerLevel level, BlockPos origin) {
        for (int dx = -8; dx < 8; dx++) {
            for (int dz = -8; dz < 8; dz++) {
                if (!level.isEmptyBlock(origin.offset(dx, 0, dz))) return false;
            }
        }
        return true;
    }

    private static boolean hasHeadroom(ServerLevel level, BlockPos origin) {
        return level.isEmptyBlock(origin.above()) && level.isEmptyBlock(origin.above(2));
    }

    private static void requireHeadroom(ServerLevel level, BlockPos origin) throws IOException {
        if (!hasHeadroom(level, origin)) throw new IOException("Aperture landing space is obstructed");
    }

    private static void requireSafeLanding(ServerLevel level, BlockPos origin) throws IOException {
        if (!level.getBlockState(origin).isFaceSturdy(level, origin, Direction.UP)) {
            throw new IOException("Aperture landing floor is missing or unsafe");
        }
        requireHeadroom(level, origin);
    }

    private static void buildPlatform(ServerLevel level, BlockPos origin,
                                      ApertureSpaceStore plots, int plot) throws IOException {
        try {
            for (int dx = -8; dx < 8; dx++) {
                for (int dz = -8; dz < 8; dz++) {
                    if (!level.setBlockAndUpdate(origin.offset(dx, 0, dz), Blocks.STONE.defaultBlockState())) {
                        throw new IOException("Could not place aperture platform");
                    }
                }
            }
            plots.markBuilt(plot);
        } catch (IOException | RuntimeException ex) {
            for (int dx = -8; dx < 8; dx++) {
                for (int dz = -8; dz < 8; dz++) {
                    BlockPos pos = origin.offset(dx, 0, dz);
                    if (level.getBlockState(pos).is(Blocks.STONE)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            }
            throw ex;
        }
    }

    /** Leave to a valid remembered point, otherwise return to overworld spawn. */
    public static void leave(ServerPlayer player) {
        ApertureSpaceStore.ReturnPoint point = null;
        AscensionServer.ApertureContext context = AscensionServer.apertureContext();
        if (context != null) point = context.spaceStore().returnPoint(player.getUUID());
        if (point != null) {
            try {
                ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        ResourceLocation.parse(point.dimension()));
                ServerLevel target = player.server.getLevel(key);
                if (target != null) {
                    player.teleportTo(target, point.x(), point.y(), point.z(),
                            Set.<RelativeMovement>of(), point.yaw(), point.pitch());
                    AscensionFx.send(target, player.position(), player.getId(), "aperture_close", 0.6f);
                    return;
                }
            } catch (RuntimeException ignored) {
                // Corrupt or removed dimensions fall through to the safe overworld destination.
            }
        }
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
        AscensionFx.send(overworld, player.position(), player.getId(), "aperture_close", 0.6f);
    }
}