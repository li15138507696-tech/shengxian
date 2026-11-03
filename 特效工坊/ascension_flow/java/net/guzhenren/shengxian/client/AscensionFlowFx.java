package net.guzhenren.shengxian.client;

// 由几页特效工坊（jiye-fx）生成。
// API 依据 Veil 1.21 wiki + 源码（VeilRenderType.get / ShaderManager.getShader /
// ShaderUniformAccess / VeilEventPlatform.preVeilPostProcessing）。
// 如果你的 Veil 版本不同，以 jar 内方法签名为准；所有 getUniform 都必须判空。

import net.minecraft.resources.ResourceLocation;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.platform.VeilEventPlatform;

/**
 * ascension_flow（screen_post）—— 屏幕后处理。
 * JSON 管线必须在客户端初始化时 add() 一次（见下），之后在 after_level 阶段执行；这里只负责推 uniform。
 */
public final class AscensionFlowFx {
    public static final String MODID = "shengxian";
    public static final ResourceLocation PIPELINE = ResourceLocation.fromNamespaceAndPath(MODID, "ascension_flow");

    private static float AscensionFlowStrength = 0.8000f;
    private static float AscensionFlowPhase = 0.0000f;
    private static float AscensionFlowProgress = 0.0000f;
    private static float AscensionFlowFog = 0.1000f;
    private static float AscensionFlowDisturbance = 0.5000f;
    private static float AscensionFlowFlash = 0.0000f;
    private static float AscensionFlowRecovery = 0.0000f;
    private static float AscensionFlowTintX = 0.9373f, AscensionFlowTintY = 0.9569f, AscensionFlowTintZ = 0.9137f;

    private AscensionFlowFx() {}

    /** 在客户端初始化时调用一次。 */
    public static void register() {
        // ★ 必须 add()：Veil 每帧只遍历 activePipelines，JSON 里写 renderStage **不会**自动激活，
        //   它只是「在哪个阶段跑」的过滤条件。漏了这一步 = 日志显示已加载、画面永远不动。
        VeilRenderSystem.renderer().getPostProcessingManager().add(1210, PIPELINE);

        VeilEventPlatform.INSTANCE.preVeilPostProcessing((pipelineName, pipeline, context) -> {
            if (!PIPELINE.equals(pipelineName)) return;
            setFloat(pipeline, "AscensionFlowStrength", AscensionFlowStrength);
            setFloat(pipeline, "AscensionFlowPhase", AscensionFlowPhase);
            setFloat(pipeline, "AscensionFlowProgress", AscensionFlowProgress);
            setFloat(pipeline, "AscensionFlowFog", AscensionFlowFog);
            setFloat(pipeline, "AscensionFlowDisturbance", AscensionFlowDisturbance);
            setFloat(pipeline, "AscensionFlowFlash", AscensionFlowFlash);
            setFloat(pipeline, "AscensionFlowRecovery", AscensionFlowRecovery);
            setVec3(pipeline, "AscensionFlowTint", AscensionFlowTintX, AscensionFlowTintY, AscensionFlowTintZ);
        });
    }

    public static void setAscensionFlowStrength(float v) { AscensionFlowStrength = v; }

    public static void setAscensionFlowPhase(float v) { AscensionFlowPhase = v; }

    public static void setAscensionFlowProgress(float v) { AscensionFlowProgress = v; }

    public static void setAscensionFlowFog(float v) { AscensionFlowFog = v; }

    public static void setAscensionFlowDisturbance(float v) { AscensionFlowDisturbance = v; }

    public static void setAscensionFlowFlash(float v) { AscensionFlowFlash = v; }

    public static void setAscensionFlowRecovery(float v) { AscensionFlowRecovery = v; }

    public static void setAscensionFlowTint(float x, float y, float z) {
        AscensionFlowTintX = x; AscensionFlowTintY = y; AscensionFlowTintZ = z;
    }

    private static void setFloat(foundry.veil.api.client.render.post.PostPipeline p, String n, float v) {
        ShaderUniformAccess u = p.getUniform(n);
        if (u != null && u.isValid()) u.setFloat(v);
    }

    private static void setInt(foundry.veil.api.client.render.post.PostPipeline p, String n, int v) {
        ShaderUniformAccess u = p.getUniform(n);
        if (u != null && u.isValid()) u.setInt(v);
    }

    private static void setVec3(foundry.veil.api.client.render.post.PostPipeline p, String n, float x, float y, float z) {
        ShaderUniformAccess u = p.getUniform(n);
        if (u != null && u.isValid()) u.setVector(x, y, z);
    }
}
