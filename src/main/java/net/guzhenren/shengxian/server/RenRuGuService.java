package net.guzhenren.shengxian.server;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.guzhenren.shengxian.ShengxianConfig;
import net.guzhenren.shengxian.core.RecentHealthHistory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Five-second living-player restoration, including only explicitly recorded recent harmful states. */
public final class RenRuGuService {
    public static final String LAST_HEAL_KEY = net.guzhenren.shengxian.compat.SavedXianGuUseReceipt.LAST_HEAL_KEY;
    private static final Map<UUID, RecentHealthHistory> HISTORY = new HashMap<>();
    private static final Map<UUID, String> DIMENSIONS = new HashMap<>();
    private RenRuGuService() { }

    public static void sample(MinecraftServer server) {
        var present = new HashSet<UUID>();
        long tick = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            present.add(player.getUUID());
            if (!player.isAlive() || player.isSpectator()) { clear(player.getUUID()); continue; }
            String dimension = player.level().dimension().location().toString();
            String old = DIMENSIONS.put(player.getUUID(), dimension);
            if (old != null && !old.equals(dimension)) HISTORY.remove(player.getUUID());
            HISTORY.computeIfAbsent(player.getUUID(), id -> new RecentHealthHistory(ShengxianConfig.healingGuHistory()))
                    .sample(tick, player.getHealth(), player.getRemainingFireTicks(),
                            player.hasEffect(MobEffects.POISON), player.hasEffect(MobEffects.WITHER));
        }
        HISTORY.keySet().removeIf(id -> !present.contains(id));
        DIMENSIONS.keySet().removeIf(id -> !present.contains(id));
    }

    public static String use(ServerPlayer caster, boolean targetOther) throws IOException {
        XianGuUseService.check(caster, "ren_ru_gu_immortal");
        ServerPlayer target = targetOther ? select(caster, ShengxianConfig.healingGuRange()) : caster;
        if (target == null) throw new IOException("视线内 24 格范围没有可救治的玩家");
        String recorded = DIMENSIONS.get(target.getUUID());
        if (recorded == null) throw new IOException("尚无近期伤势记录");
        if (!recorded.equals(target.level().dimension().location().toString())) {
            clear(target.getUUID());
            throw new IOException("跨维度后需要重新记录伤势");
        }
        long tick = XianGuUseService.tick(caster);
        var persisted = target.getPersistentData().getCompound("PlayerPersisted");
        if (persisted.contains(LAST_HEAL_KEY)) {
            if (!persisted.contains(LAST_HEAL_KEY, net.minecraft.nbt.Tag.TAG_LONG)) throw new IOException("目标受治记录损坏");
            long last = persisted.getLong(LAST_HEAL_KEY);
            if (tick < last || tick - last < 40) throw new IOException("目标刚受过人如故救治，请稍候");
        }
        RecentHealthHistory history = HISTORY.get(target.getUUID());
        if (history == null) throw new IOException("尚无近期伤势记录");
        var restore = history.plan(tick, target.getHealth(), target.getMaxHealth(), target.getRemainingFireTicks(),
                target.hasEffect(MobEffects.POISON), target.hasEffect(MobEffects.WITHER))
                .orElseThrow(() -> new IOException("近期没有可恢复的伤势"));
        JsonObject plan = new JsonObject(), after = new JsonObject();
        plan.addProperty("kind", "health"); plan.addProperty("target", target.getStringUUID());
        after.addProperty("health", restore.health()); after.addProperty("extinguish", restore.extinguish());
        after.addProperty("clearPoison", restore.clearPoison()); after.addProperty("clearWither", restore.clearWither());
        plan.add("after", after);
        XianGuUseService.execute(caster, "ren_ru_gu_immortal", List.of(target), plan.toString(), () -> {
            if (!target.isAlive() || !target.level().dimension().equals(caster.level().dimension())
                    || target != caster && (caster.distanceToSqr(target) > Math.pow(ShengxianConfig.healingGuRange(), 2)
                    || !caster.hasLineOfSight(target))) throw new IllegalStateException("救治目标位置或角色已变化");
            target.setHealth(restore.health());
            if (restore.extinguish()) target.clearFire();
            if (restore.clearPoison()) target.removeEffect(MobEffects.POISON);
            if (restore.clearWither()) target.removeEffect(MobEffects.WITHER);
        }, () -> target.isAlive() && target.getHealth() == restore.health()
                && (!restore.extinguish() || !target.isOnFire())
                && (!restore.clearPoison() || !target.hasEffect(MobEffects.POISON))
                && (!restore.clearWither() || !target.hasEffect(MobEffects.WITHER)));
        history.complete(tick, target.getHealth(), target.getRemainingFireTicks(),
                target.hasEffect(MobEffects.POISON), target.hasEffect(MobEffects.WITHER));
        XianGuEffects.heal(target);
        if (target != caster) XianGuUseService.message(target, caster.getGameProfile().getName() + "以人如故复原了你的近期伤势。");
        return "人如故已复原" + (target == caster ? "自身" : target.getGameProfile().getName()) + "的近期伤势。";
    }

    private static ServerPlayer select(ServerPlayer caster, double range) {
        Vec3 start = caster.getEyePosition(), direction = caster.getLookAngle().scale(range), end = start.add(direction);
        double blockDistance = caster.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster)).getLocation().distanceToSqr(start);
        AABB area = caster.getBoundingBox().expandTowards(direction).inflate(1);
        EntityHitResult result = ProjectileUtil.getEntityHitResult(caster, start, end, area,
                entity -> entity instanceof ServerPlayer player && player != caster && player.isAlive() && !player.isSpectator(),
                range * range);
        return result != null && result.getLocation().distanceToSqr(start) <= blockDistance + 0.001
                ? (ServerPlayer) result.getEntity() : null;
    }
    public static void clear(UUID owner) { HISTORY.remove(owner); DIMENSIONS.remove(owner); }
    public static void clear() { HISTORY.clear(); DIMENSIONS.clear(); }
}
