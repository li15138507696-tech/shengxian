package net.guzhenren.shengxian.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Method;
import net.guzhenren.shengxian.core.ApertureVisionMesh;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Minecraft adapter; the caller gates rendering on the server-confirmed successful frame. */
final class VeilApertureVisionRenderer {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            "shengxian", "ascension_aperture_vision");
    private static Method getter;
    private static boolean warned;

    private VeilApertureVisionRenderer() {}

    /** Resolve each frame so a resource reload cannot leave an obsolete RenderType cached. */
    static RenderType renderType() {
        try {
            if (getter == null) {
                getter = Class.forName("foundry.veil.api.client.render.rendertype.VeilRenderType")
                        .getMethod("get", ResourceLocation.class, Object[].class);
            }
            Object value = getter.invoke(null, ID, new Object[0]);
            if (value instanceof RenderType type) return type;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (!warned) {
                warned = true;
                LoggerFactory.getLogger("shengxian").warn("Veil aperture vision unavailable", exception);
            }
        }
        // These slices require their procedural shader; the caller skips a missing render type.
        return null;
    }

    /** pose is translated to source feet; yaw is degrees; camera is source-relative in world axes. */
    static void render(VertexConsumer out, PoseStack.Pose pose, float age, float fade,
                       double ticks, float yaw, Vec3 camera) {
        ApertureVisionMesh.draw(age, fade, ticks, yaw, camera.x, camera.y, camera.z,
                (x, y, z, u, v, r, g, b, a, nx, ny, nz) ->
                        out.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v)
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                                .setNormal(pose, nx, ny, nz));
    }
}
