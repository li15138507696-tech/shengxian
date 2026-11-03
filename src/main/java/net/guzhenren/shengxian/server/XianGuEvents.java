package net.guzhenren.shengxian.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.LoggerFactory;

/** World/session boundaries clear transient histories and never replay historical effects. */
public final class XianGuEvents {
    private XianGuEvents() { }
    public static void started(ServerStartedEvent event) {
        XianGuUseService.clear(); RenRuGuService.clear(); DingXianYouService.clear(); JiangShanRuGuService.reset();
    }
    public static void stopping(ServerStoppingEvent event) {
        JiangShanRuGuService.clear(event.getServer()); DingXianYouService.clear(); RenRuGuService.clear(); XianGuUseService.clear();
    }
    public static void tick(ServerTickEvent.Post event) {
        RenRuGuService.sample(event.getServer());
        DingXianYouService.tick(event.getServer());
        JiangShanRuGuService.tick(event.getServer());
    }
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RenRuGuService.clear(player.getUUID());
            try { XianGuUseService.reconcileParticipant(player); }
            catch (Exception failure) {
                LoggerFactory.getLogger("shengxian").warn("Immortal Gu receipt retained on login for {}", player.getUUID(), failure);
                XianGuUseService.message(player, "仙蛊回执需核对：" + failure.getMessage());
            }
        }
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JiangShanRuGuService.logout(player); DingXianYouService.cancel(player); RenRuGuService.clear(player.getUUID());
        }
    }
    public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) {
            JiangShanRuGuService.logout(player); DingXianYouService.cancel(player); RenRuGuService.clear(player.getUUID());
        }
    }
    public static void unload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var pos = event.getChunk().getPos(); JiangShanRuGuService.clearChunk(level, pos.x, pos.z);
        }
    }
}
