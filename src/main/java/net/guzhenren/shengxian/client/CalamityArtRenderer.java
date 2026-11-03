package net.guzhenren.shengxian.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.guzhenren.shengxian.ShengxianMod;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.slf4j.Logger;

import java.util.*;

/** Local visual previews and carrier-anchored art; this does not create gameplay entities/collisions. */
@EventBusSubscriber(modid = ShengxianMod.MOD_ID, value = Dist.CLIENT)
public final class CalamityArtRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> IDS = List.of("jade_willow", "horned_tiger", "eight_chimes", "ancient_beast", "blood_core");
    private static final List<String> ANIMATIONS = List.of("idle", "walk", "charge", "attack", "hurt", "death");
    private static final Map<String, CalamityArtModel> CACHE = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final List<Preview> GALLERY = new ArrayList<>();
    private static final int MAX_MODELS = 16;
    private static final double MAX_DISTANCE_SQUARED = 128 * 128;
    private static ClientLevel galleryLevel;
    private static ClientLevel clockLevel;
    private static String animation = "idle";
    private static double animationStart;

    private CalamityArtRenderer() {}

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        var command = Commands.literal("shengxianart")
                .executes(c -> gallery(c.getSource()))
                .then(Commands.literal("gallery").executes(c -> gallery(c.getSource())))
                .then(Commands.literal("clear").executes(c -> {
                    GALLERY.clear(); galleryLevel = null;
                    feedback(c.getSource(), "原创美术画廊已清除（仅本地视觉）。"); return 1;
                }))
                .then(Commands.literal("reload").executes(c -> {
                    clearCache();
                    feedback(c.getSource(), "原创美术缓存已重载；F3+T 同样可重载资源包。");
                    diagnose(c.getSource()); return 1;
                }));
        var animate = Commands.literal("animate");
        for (String name : ANIMATIONS) animate.then(Commands.literal(name).executes(c -> {
            animation = name; animationStart = now();
            feedback(c.getSource(), "本地原创模型动画: " + name + "（非循环动画结束后保持末帧）");
            diagnose(c.getSource()); return 1;
        }));
        command.then(animate);
        // Client command event, deliberately no server permission predicate.
        event.getDispatcher().register(command);
    }

    private static int gallery(CommandSourceStack source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return 0;
        Vec3 forward = Vec3.directionFromRotation(0, mc.player.getYRot());
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 center = mc.player.position().add(forward.scale(30));
        GALLERY.clear();
        for (int i = 0; i < IDS.size(); i++) {
            // Existing artwork is large: 24 blocks between anchors, no extra model scale.
            GALLERY.add(new Preview(IDS.get(i), center.add(right.scale((i - 2) * 24)), mc.player.getYRot() + 180));
        }
        galleryLevel = mc.level;
        animation = "idle"; animationStart = now();
        feedback(source, "已在前方生成五类原创美术画廊，间隔24格；纯本地视觉，无实体/伤害/碰撞。/shengxianart animate <动画>，clear 清除。");
        diagnose(source);
        return 1;
    }

    private static void diagnose(CommandSourceStack source) {
        for (String id : IDS) {
            CalamityArtModel model = model(id);
            feedback(source, id + ": " + (model == null ? "资源失败，已隔离；修复后 reload/F3+T" : model.diagnostics()
                    + (model.hasAnimation(animation) ? "" : " / 缺少当前动画，回退idle")));
        }
    }
    private static void feedback(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
    private static double now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() / 20.0;
    }
    /** Mapping from calamity type to artwork id, shared with the mob renderer. */
    static String artworkId(AscensionProgress.Tribulation.Type type) {
        return artwork(type);
    }

    /** Cached model for one artwork id, or null when that asset failed to load. */
    static CalamityArtModel modelFor(String id) {
        return model(id);
    }

    private static CalamityArtModel model(String id) {
        if (FAILED.contains(id)) return null;
        CalamityArtModel cached = CACHE.get(id);
        if (cached != null) return cached;
        try {
            CalamityArtModel loaded = CalamityArtModel.load(Minecraft.getInstance().getResourceManager(), id);
            CACHE.put(id, loaded);
            return loaded;
        } catch (Exception failure) {
            isolate(id, failure);
            return null;
        }
    }
    private static void isolate(String id, Exception failure) {
        CACHE.remove(id);
        if (FAILED.add(id)) LOGGER.error("Isolated calamity artwork {}; reload resources to retry", id, failure);
    }
    private static void clearCache() {
        CACHE.clear(); FAILED.clear(); animationStart = now();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (clockLevel != mc.level) {
            clockLevel = mc.level; animation = "idle"; animationStart = now();
        }
        if (galleryLevel != mc.level) { GALLERY.clear(); galleryLevel = null; }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = (mc.level.getGameTime() + partial) / 20.0 - animationStart;
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Set<RenderType> renderedTypes = new HashSet<>();
        int count = 0;
        for (Preview p : GALLERY) {
            if (count >= MAX_MODELS) break;
            if (p.position.distanceToSqr(camera) > MAX_DISTANCE_SQUARED) continue;
            draw(p.id, p.position, p.yaw, time, camera, event.getPoseStack(), buffers, renderedTypes, true);
            count++;
        }
        for (var entity : mc.level.entitiesForRendering()) {
            if (count >= MAX_MODELS) break;
            if (!(entity instanceof ArmorStand stand) || stand.isRemoved()) continue;
            Vec3 position = stand.getPosition(partial);
            if (position.distanceToSqr(camera) > MAX_DISTANCE_SQUARED) continue;
            String id = artwork(CarrierTagReader.readType(stand));
            if (id == null) continue;
            draw(id, position, stand.getYRot(), time, camera, event.getPoseStack(), buffers, renderedTypes, false);
            count++;
        }
        // Flush only this pass's entity textures, not unrelated renderers' pending buffers.
        for (RenderType type : renderedTypes) buffers.endBatch(type);
    }

    private static void draw(String id, Vec3 position, float yaw, double time, Vec3 camera,
                             PoseStack pose, MultiBufferSource.BufferSource buffers, Set<RenderType> types, boolean preview) {
        CalamityArtModel model = model(id);
        if (model == null) return;
        pose.pushPose();
        try {
            pose.translate(position.x-camera.x, position.y-camera.y, position.z-camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
            // ModelPart divides all pixel coordinates by 16. Y-down -> world Y-up, no extra enlargement.
            pose.scale(1, -1, 1);
            RenderType type = RenderType.entityCutoutNoCull(model.texture());
            types.add(type);
            int light = preview ? 0xF000F0 : LevelRenderer.getLightColor(Minecraft.getInstance().level, BlockPos.containing(position));
            model.render(pose, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY, animation, time);
        } catch (Exception failure) {
            isolate(id, failure);
        } finally {
            pose.popPose();
        }
    }

    private static String artwork(AscensionProgress.Tribulation.Type type) {
        if (type == null) return null;
        return switch (type) {
            case JADE_WILLOW_WIND -> "jade_willow";
            case SINGLE_HORNED_TIGER -> "horned_tiger";
            case SOUND_CHIMES -> "eight_chimes";
            case ANCIENT_BEAST -> "ancient_beast";
            case BLOOD_RAIN -> "blood_core";
            default -> null;
        };
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        GALLERY.clear(); galleryLevel = null; clockLevel = null; animation = "idle"; animationStart = 0;
    }

    /** Resource registration is MOD bus; commands/render/logout above use the GAME bus. */
    @EventBusSubscriber(modid = ShengxianMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ReloadEvents {
        private ReloadEvents() {}
        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> clearCache());
        }
    }
    private record Preview(String id, Vec3 position, float yaw) {}
}
