package net.guzhenren.shengxian.client.fx;

// 由几页特效工坊（jiye-fx）生成。
// API 依据 Veil 1.21 wiki + 源码（VeilRenderType.get / ShaderManager.getShader /
// ShaderUniformAccess / VeilEventPlatform.preVeilPostProcessing）。
// 如果你的 Veil 版本不同，以 jar 内方法签名为准；所有 getUniform 都必须判空。

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * ascension_sanqi_converge（entity_aura）—— 几何提交 + uniform 推送。
 */
public final class AscensionSanqiConvergeFx {
    public static final String MODID = "shengxian";
    public static final ResourceLocation RENDER_TYPE = ResourceLocation.fromNamespaceAndPath(MODID, "ascension_sanqi_converge");
    public static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(MODID, "ascension_sanqi_converge");

    private static float AscensionSanqiConvergePalette = 0.0000f;
    private static float AscensionSanqiConvergePulse = 0.5000f;
    private static float AscensionSanqiConvergeAlpha = 0.8000f;
    private static float AscensionSanqiConvergeTintX = 0.4588f, AscensionSanqiConvergeTintY = 0.8039f, AscensionSanqiConvergeTintZ = 0.7255f;

    private AscensionSanqiConvergeFx() {}

    /**
     * 提交一个面向相机的四边形（示例拓扑；真实模型请换成你的 mesh）。
     * NEW_ENTITY 顶点：pos + color + uv + light + overlay + normal。
     */
    public static void quad(PoseStack pose, MultiBufferSource buffers,
                            float x, float y, float z, float halfSize,
                            float r, float g, float b, float a, int light, int overlay) {
        RenderType rt = VeilRenderType.get(RENDER_TYPE);
        if (rt == null) return;
        VertexConsumer vc = buffers.getBuffer(rt);
        PoseStack.Pose p = pose.last();
        vc.addVertex(p, x - halfSize, y - halfSize, z).setColor(r, g, b, a)
          .setUv(0.0f, 1.0f).setLight(light).setOverlay(overlay)
          .setNormal(p, 0.0f, 0.0f, 1.0f);
        vc.addVertex(p, x - halfSize, y + halfSize, z).setColor(r, g, b, a)
          .setUv(0.0f, 0.0f).setLight(light).setOverlay(overlay)
          .setNormal(p, 0.0f, 0.0f, 1.0f);
        vc.addVertex(p, x + halfSize, y + halfSize, z).setColor(r, g, b, a)
          .setUv(1.0f, 0.0f).setLight(light).setOverlay(overlay)
          .setNormal(p, 0.0f, 0.0f, 1.0f);
        vc.addVertex(p, x + halfSize, y - halfSize, z).setColor(r, g, b, a)
          .setUv(1.0f, 1.0f).setLight(light).setOverlay(overlay)
          .setNormal(p, 0.0f, 0.0f, 1.0f);
    }

    /** 每帧在提交几何之前推 uniform。 */
    public static void pushUniforms() {
        ShaderProgram shader = VeilRenderSystem.renderer().getShaderManager().getShader(SHADER);
        if (shader == null) return;
        setFloat(shader, "AscensionSanqiConvergePalette", AscensionSanqiConvergePalette);
        setFloat(shader, "AscensionSanqiConvergePulse", AscensionSanqiConvergePulse);
        setFloat(shader, "AscensionSanqiConvergeAlpha", AscensionSanqiConvergeAlpha);
        setVec3(shader, "AscensionSanqiConvergeTint", AscensionSanqiConvergeTintX, AscensionSanqiConvergeTintY, AscensionSanqiConvergeTintZ);
    }

    public static void setAscensionSanqiConvergePalette(float v) { AscensionSanqiConvergePalette = v; }

    public static void setAscensionSanqiConvergePulse(float v) { AscensionSanqiConvergePulse = v; }

    public static void setAscensionSanqiConvergeAlpha(float v) { AscensionSanqiConvergeAlpha = v; }

    public static void setAscensionSanqiConvergeTint(float x, float y, float z) {
        AscensionSanqiConvergeTintX = x; AscensionSanqiConvergeTintY = y; AscensionSanqiConvergeTintZ = z;
    }

    private static void setFloat(ShaderProgram s, String n, float v) {
        ShaderUniformAccess u = s.getUniform(n);
        if (u != null && u.isValid()) u.setFloat(v);
    }

    private static void setInt(ShaderProgram s, String n, int v) {
        ShaderUniformAccess u = s.getUniform(n);
        if (u != null && u.isValid()) u.setInt(v);
    }

    private static void setVec3(ShaderProgram s, String n, float x, float y, float z) {
        ShaderUniformAccess u = s.getUniform(n);
        if (u != null && u.isValid()) u.setVector(x, y, z);
    }
}
