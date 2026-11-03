package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.AperturePlotBounds;
import net.guzhenren.shengxian.core.AperturePlotPolicy;
import net.guzhenren.shengxian.core.ApertureSpaceStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.util.TriState;

import java.util.Map;
import java.util.Set;

/** Server-side boundary and read-only access enforcement for the shared aperture dimension. */
public final class ApertureProtection {
    private static final String ITEM_PLOT_TAG = "shengxian:aperture_plot:";

    private ApertureProtection() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(ApertureSpace.LEVEL) && !mayRemain(player)) {
                ApertureSpace.leave(player);
            }
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isAperture(player.level())) {
            event.setCanceled(!mayModify(player, event.getPos().getX() + 0.5, event.getPos().getZ() + 0.5));
        }
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isAperture(player.level())) {
            event.setCanceled(!mayModify(player, event.getPos().getX() + 0.5, event.getPos().getZ() + 0.5));
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && isAperture(player.level())) {
            if (!mayModify(player, event.getPos().getX() + 0.5, event.getPos().getZ() + 0.5)) {
                var held = player.getItemInHand(event.getHand()).getItem();
                String guId = held instanceof net.guzhenren.shengxian.item.ImmortalGuItem item ? item.guId() : "";
                if (net.guzhenren.shengxian.core.AperturePlotPolicy.mayUseBoundItem(false, guId, player.isShiftKeyDown())) {
                    event.setUseBlock(TriState.FALSE);
                } else event.setCanceled(true);
            }
        }
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isAperture(player.level())) return;
        Entity target = event.getTarget();
        int attackerPlot = plotAt(player.getX(), player.getZ());
        int targetPlot = plotAt(target.getX(), target.getZ());
        if (!mayModify(player, player.getX(), player.getZ()) || attackerPlot < 0 || attackerPlot != targetPlot) {
            event.setCanceled(true);
        }
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!isAperture(projectile.level())) return;
        Vec3 impact = event.getRayTraceResult().getLocation();
        Entity owner = projectile.getOwner();
        int originPlot = owner == null ? -1 : plotAt(owner.getX(), owner.getZ());
        int impactPlot = plotAt(impact.x, impact.z);
        boolean ownerMayAct = owner != null && (!(owner instanceof ServerPlayer player)
                || mayModify(player, player.getX(), player.getZ()));
        if (!ownerMayAct || !AperturePlotPolicy.mayAffect(originPlot, impactPlot)) {
            event.setCanceled(true);
            projectile.discard();
        }
    }

    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !isAperture(player.level())) return;
        Entity thrown = event.getEntity();
        if (!(thrown instanceof ItemEntity item)
                || !mayModify(player, player.getX(), player.getZ())
                || !AperturePlotPolicy.mayAffect(plotAt(player.getX(), player.getZ()),
                        plotAt(item.getX(), item.getZ()))) {
            event.setCanceled(true);
        } else {
            bindItemToPlot(item, plotAt(player.getX(), player.getZ()));
        }
    }

    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !isAperture(player.level())) return;
        ItemEntity item = event.getItemEntity();
        int itemPlot = plotAt(item.getX(), item.getZ());
        int boundPlot = itemPlotOf(item);
        if (boundPlot < 0 && ApertureSpaceStore.isValidPlot(itemPlot)) {
            bindItemToPlot(item, itemPlot);
            boundPlot = itemPlot;
        }
        int playerPlot = plotAt(player.getX(), player.getZ());
        if (!mayModify(player, player.getX(), player.getZ())
                || !AperturePlotPolicy.mayAffect(boundPlot, itemPlot)
                || !AperturePlotPolicy.mayAffect(playerPlot, itemPlot)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    public static void onItemTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity item) || item.level().isClientSide
                || !isAperture(item.level())) return;
        int currentPlot = plotAt(item.getX(), item.getZ());
        int boundPlot = itemPlotOf(item);
        if (boundPlot < 0) {
            if (ApertureSpaceStore.isValidPlot(currentPlot)) bindItemToPlot(item, currentPlot);
        } else if (currentPlot >= 0 && !AperturePlotPolicy.mayAffect(boundPlot, currentPlot)) {
            item.discard();
        }
    }

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!isAperture(target.level())) return;
        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();
        if (attacker instanceof Projectile projectile) attacker = projectile.getOwner();
        if (attacker == null) {
            attacker = direct instanceof Projectile projectile ? projectile.getOwner() : direct;
        }
        if (attacker == null) {
            if (direct instanceof Projectile) event.setCanceled(true);
            return;
        }

        int sourcePlot = plotAt(attacker.getX(), attacker.getZ());
        int targetPlot = plotAt(target.getX(), target.getZ());
        boolean ownerMayAct = !(attacker instanceof ServerPlayer player)
                || mayModify(player, player.getX(), player.getZ());
        if (!ownerMayAct || !AperturePlotPolicy.mayAffect(sourcePlot, targetPlot)) {
            event.setCanceled(true);
        }
    }

    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!isAperture(event.getLevel())) return;
        var explosion = event.getExplosion();
        Vec3 center = explosion.center();
        int originPlot = plotAt(center.x, center.z);
        Entity owner = explosion.getIndirectSourceEntity();
        if (owner == null) owner = explosion.getDirectSourceEntity();
        boolean mayExplode = ApertureSpaceStore.isValidPlot(originPlot) && owner != null;
        if (owner != null) {
            int ownerPlot = plotAt(owner.getX(), owner.getZ());
            mayExplode &= AperturePlotPolicy.mayAffect(ownerPlot, originPlot);
            if (owner instanceof ServerPlayer player) {
                mayExplode &= mayModify(player, player.getX(), player.getZ());
            }
        }
        if (!mayExplode) {
            event.getAffectedBlocks().clear();
            event.getAffectedEntities().clear();
            return;
        }

        event.getAffectedBlocks().removeIf(pos -> !AperturePlotPolicy.mayAffect(originPlot,
                plotAt(pos.getX() + 0.5, pos.getZ() + 0.5)));
        event.getAffectedEntities().removeIf(entity -> !AperturePlotPolicy.mayAffect(originPlot,
                plotAt(entity.getX(), entity.getZ())));
    }

    private static boolean mayRemain(ServerPlayer player) {
        int plot = plotAt(player.getX(), player.getZ());
        if (plot < 0) return false;
        ApertureKey claim = AscensionServer.formedPlotOwners().get(plot);
        if (claim == null) return false;
        if (claim.owner().equals(player.getUUID())) return true;
        AscensionServer.ApertureContext context = AscensionServer.apertureContext();
        if (context == null) return false;
        ApertureSpaceStore permissions = context.spaceStore();
        return (ShengxianConfig.allowGuests() && permissions.isGuest(plot, player.getUUID()))
                || (ShengxianConfig.allowGuardians() && permissions.isGuardian(plot, player.getUUID()));
    }

    private static boolean mayModify(ServerPlayer player, double x, double z) {
        int plot = plotAt(x, z);
        if (plot < 0) return false;
        ApertureKey claim = AscensionServer.formedPlotOwners().get(plot);
        return claim != null && AperturePlotPolicy.mayModify(plot, claim.owner(), player.getUUID(), x, z);
    }

    private static int plotAt(double x, double z) {
        Map<Integer, ApertureKey> claims = AscensionServer.formedPlotOwners();
        return AperturePlotBounds.find(claims.keySet(), x, z);
    }

    private static int itemPlotOf(ItemEntity item) {
        for (String tag : item.getTags()) {
            if (!tag.startsWith(ITEM_PLOT_TAG)) continue;
            try {
                int plot = Integer.parseInt(tag.substring(ITEM_PLOT_TAG.length()));
                if (ApertureSpaceStore.isValidPlot(plot)) return plot;
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static void bindItemToPlot(ItemEntity item, int plot) {
        if (!ApertureSpaceStore.isValidPlot(plot)) return;
        for (String tag : Set.copyOf(item.getTags())) {
            if (tag.startsWith(ITEM_PLOT_TAG)) item.removeTag(tag);
        }
        item.addTag(ITEM_PLOT_TAG + plot);
    }

    private static boolean isAperture(Level level) {
        return level.dimension().equals(ApertureSpace.LEVEL);
    }
}
