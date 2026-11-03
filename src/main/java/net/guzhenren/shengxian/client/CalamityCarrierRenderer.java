package net.guzhenren.shengxian.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.guzhenren.shengxian.entity.CalamityCarrierEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 灾劫生物渲染：直接复用原创 geo/atlas/animation，不再依附盔甲架。
 */
public class CalamityCarrierRenderer extends EntityRenderer<CalamityCarrierEntity> {
    public CalamityCarrierRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CalamityCarrierEntity entity) {
        CalamityArtModel model = modelOf(entity);
        return model == null
                ? ResourceLocation.fromNamespaceAndPath("shengxian", "textures/entity/horned_tiger.png")
                : model.texture();
    }

    private static CalamityArtModel modelOf(CalamityCarrierEntity entity) {
        String id = CalamityArtRenderer.artworkId(entity.variant());
        return id == null ? null : CalamityArtRenderer.modelFor(id);
    }

    @Override
    public void render(CalamityCarrierEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        CalamityArtModel model = modelOf(entity);
        if (model == null) return;
        pose.pushPose();
        try {
            // Bedrock models are Y-down; the entity pose is already translated and rotated.
            pose.scale(1.0f, -1.0f, 1.0f);
            // While dying the model plays its death clip; otherwise it idles.
            boolean dying = entity.isDying();
            String animation = dying ? "death" : "idle";
            double seconds = dying
                    ? (entity.deathTicks() + partialTick) / 20.0
                    : (entity.tickCount + partialTick) / 20.0;
            model.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(model.texture())),
                    packedLight, OverlayTexture.NO_OVERLAY, animation, seconds);
        } finally {
            pose.popPose();
        }
        super.render(entity, yaw, partialTick, pose, buffer, packedLight);
    }
}
