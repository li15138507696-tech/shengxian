package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.core.Phase;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-authoritative grants for the ascension advancement tab.
 *
 * <p>Every node under {@code data/shengxian/advancement} uses the vanilla {@code minecraft:impossible}
 * trigger, so the datapack can never advance itself: a node is earned only after the committed
 * ascension phase (or a committed immortal-Gu use) is observed on the server. All grants are
 * idempotent, so the immediate transition call sites and the periodic reconciliation poll can both
 * ask for the same node without producing a duplicate toast.</p>
 */
public final class AscensionAdvancements {
    public static final String NAMESPACE = "shengxian";
    /** Criterion key shared by every ascension node; it must match the datapack JSON. */
    public static final String CRITERION = "granted";

    public static final ResourceLocation ROOT = id("ascension/root");
    public static final ResourceLocation SHATTER = id("ascension/shatter");
    public static final ResourceLocation ABSORB = id("ascension/absorb");
    public static final ResourceLocation OPENING = id("ascension/opening");
    public static final ResourceLocation FORMING = id("ascension/forming");
    public static final ResourceLocation IMMORTAL = id("ascension/immortal");
    public static final ResourceLocation FAILED = id("ascension/failed");
    public static final ResourceLocation XIAN_GU = id("ascension/xian_gu");

    /** Main-line nodes in route order, so a late phase also fills in every earlier node. */
    private static final ResourceLocation[] ROUTE = {ROOT, SHATTER, ABSORB, OPENING, FORMING, IMMORTAL};

    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");

    private AscensionAdvancements() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    /** Main-line index reached by one committed phase, or {@code -1} for the off-line failure branch. */
    private static int routeIndex(Phase phase) {
        return switch (phase) {
            case PREPARING, BREAKING -> 0;
            case BROKEN -> 1;
            case ABSORBING -> 2;
            case OPENING -> 3;
            case FORMING -> 4;
            case IMMORTAL -> 5;
            case FAILED -> -1;
        };
    }

    /** Awards every main-line node implied by {@code phase}; a later phase fills the earlier nodes too. */
    public static void syncPhase(ServerPlayer player, Phase phase) {
        if (player == null || phase == null) return;
        int reached = routeIndex(phase);
        if (reached < 0) {
            grant(player, FAILED);
            return;
        }
        for (int index = 0; index <= reached; index++) grant(player, ROUTE[index]);
    }

    /** Awards the off-line failure node only; the main line keeps whatever it already earned. */
    public static void grantFailure(ServerPlayer player) {
        grant(player, FAILED);
    }

    /** Called only after a Gu use is durably committed, so failed or aborted uses never count. */
    public static void grantImmortalGuUse(ServerPlayer player) {
        if (player == null) return;
        syncPhase(player, Phase.IMMORTAL);
        grant(player, XIAN_GU);
    }

    /**
     * Awards one node through its {@code impossible} criterion. A node missing from the loaded
     * datapack (disabled pack, reload in progress) is skipped instead of throwing into the tick loop.
     */
    public static boolean grant(ServerPlayer player, ResourceLocation advancementId) {
        if (player == null || advancementId == null) return false;
        MinecraftServer server = player.server;
        if (server == null) return false;
        ServerAdvancementManager manager = server.getAdvancements();
        if (manager == null) return false;
        AdvancementHolder holder = manager.get(advancementId);
        if (holder == null) {
            LOGGER.debug("Ascension advancement {} is not loaded; grant skipped", advancementId);
            return false;
        }
        PlayerAdvancements advancements = player.getAdvancements();
        if (advancements.getOrStartProgress(holder).isDone()) return false;
        if (!advancements.award(holder, CRITERION)) return false;
        advancements.flushDirty(player);
        return true;
    }
}
