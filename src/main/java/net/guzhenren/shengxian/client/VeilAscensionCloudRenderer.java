package net.guzhenren.shengxian.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import static org.lwjgl.opengl.GL33C.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Method;
import net.guzhenren.shengxian.ShengxianVisualConfig;
import net.guzhenren.shengxian.core.AscensionCloudMesh;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Optional Veil volume. Every source is flushed while its own local-camera uniforms are current. */
final class VeilAscensionCloudRenderer {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            "shengxian", "ascension_cloud_volume");
    private static final ResourceLocation TEMPEST = ResourceLocation.fromNamespaceAndPath("shengxian", "ascension_tempest_volume");
    private static Api api;
    private static boolean warned, copied;
    private static TextureTarget depthCopy;
    private static final Matrix4f inverseProjection=new Matrix4f();

    static void beginFrame(Matrix4fc projection) { copied=false;inverseProjection.set(projection).invert(); }
    static void endFrame() { if(!copied) clear(); }
    /** Capture before vanilla clouds/weather write their proxy surfaces into scene depth. */
    static boolean captureOpaqueDepth() {
        try { sceneDepth(); return true; }
        catch(RuntimeException e) {
            if(!warned) {warned=true;LoggerFactory.getLogger("shengxian").warn("Volume scene depth unavailable",e);}
            clear();return false;
        }
    }

    static void clear() {
        copied=false;
        if(depthCopy==null) return;
        State state=State.capture(); int deletedColor=-1,deletedDepth=-1;
        try { deletedColor=depthCopy.getColorTextureId();deletedDepth=depthCopy.getDepthTextureId();depthCopy.destroyBuffers();depthCopy=null; }
        finally { state.restore(deletedColor,deletedDepth); }
    }

    /** Private copied texture avoids feedback from sampling the currently attached depth buffer. */
    private static int sceneDepth() {
        if(copied && depthCopy!=null) return depthCopy.getDepthTextureId();
        var main=Minecraft.getInstance().getMainRenderTarget();
        if(!main.useDepth || main.getDepthTextureId()<0) throw new IllegalStateException("Missing scene depth");
        State state=State.capture(); int deletedColor=-1,deletedDepth=-1;
        try {
            if(depthCopy==null || depthCopy.width!=main.width || depthCopy.height!=main.height
                    || depthCopy.isStencilEnabled()!=main.isStencilEnabled()) {
                if(depthCopy!=null) { deletedColor=depthCopy.getColorTextureId();deletedDepth=depthCopy.getDepthTextureId();depthCopy.destroyBuffers();depthCopy=null; }
                depthCopy=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);
                if(main.isStencilEnabled()) depthCopy.enableStencil();
            }
            depthCopy.copyDepthFrom(main);
            copied=true;
            return depthCopy.getDepthTextureId();
        } finally { state.restore(deletedColor,deletedDepth); }
    }

    /** Allocation and vanilla copyDepthFrom alter framebuffer and texture/viewport state. */
    private record State(int read,int draw,int active,int texture,int x,int y,int width,int height,
                         boolean depth,float red,float green,float blue,float alpha,double clearDepth) {
        static State capture() {
            int[] viewport=new int[4];float[] color=new float[4];
            glGetIntegerv(GL_VIEWPORT,viewport);glGetFloatv(GL_COLOR_CLEAR_VALUE,color);
            return new State(glGetInteger(GL_READ_FRAMEBUFFER_BINDING),glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
                    glGetInteger(GL_ACTIVE_TEXTURE),glGetInteger(GL_TEXTURE_BINDING_2D),
                    viewport[0],viewport[1],viewport[2],viewport[3],glIsEnabled(GL_DEPTH_TEST),
                    color[0],color[1],color[2],color[3],glGetDouble(GL_DEPTH_CLEAR_VALUE));
        }
        void restore(int deletedColor,int deletedDepth) {
            GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER,read);
            GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw);
            RenderSystem.activeTexture(active);RenderSystem.bindTexture(texture==deletedColor || texture==deletedDepth?0:texture);
            RenderSystem.viewport(x,y,width,height);
            if(depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            GlStateManager._clearColor(red,green,blue,alpha);GlStateManager._clearDepth(clearDepth);
        }
    }

    private VeilAscensionCloudRenderer() {}

    /**
     * pose must translate by sourceOrigin-cameraPosition only, with world axes preserved.
     * The global ModelViewMat supplies view rotation; camera is cameraPosition-sourceOrigin.
     * strength/storm are visual 0..1 values, and ticks is an animation clock in ticks.
     */
    static void render(MultiBufferSource.BufferSource buffers, PoseStack.Pose pose,
                       float strength, float storm, double ticks, Vec3 camera) {
        render(buffers,pose,strength,storm,ticks,camera,1f);
    }

    static void render(MultiBufferSource.BufferSource buffers, PoseStack.Pose pose,
                       float strength, float storm, double ticks, Vec3 camera, float volumeScale) {
        renderVolume(buffers,pose,strength,storm,ticks,camera,volumeScale,false);
    }
    static void renderTempest(MultiBufferSource.BufferSource buffers, PoseStack.Pose pose,
                             float strength,float storm,double ticks,Vec3 camera) {
        renderVolume(buffers,pose,strength,storm,ticks,camera,1f,true);
    }
    private static void renderVolume(MultiBufferSource.BufferSource buffers, PoseStack.Pose pose,
                       float strength,float storm,double ticks,Vec3 camera,float volumeScale,boolean tempest) {
        if (!Float.isFinite(strength) || !Float.isFinite(storm) || !Double.isFinite(ticks)
                || camera == null || !Double.isFinite(camera.x) || !Double.isFinite(camera.y)
                || !Double.isFinite(camera.z) || strength <= .001f) return;
        try {
            if (api == null) api = new Api();
            Api a = api;
            Object renderer = a.renderer.invoke(null);
            if (renderer == null) return;
            Object manager = a.shaderManager.invoke(renderer);
            ResourceLocation shaderId = tempest ? TEMPEST : ID;
            Object program = a.shader.invoke(manager, shaderId);
            Object candidate = a.renderType.invoke(null, shaderId, new Object[0]);
            if (program == null || !(candidate instanceof RenderType type)) return;

            int sceneTexture=sceneDepth();
            a.texture.invoke(program,"SceneDepthSampler",GL_TEXTURE_2D,sceneTexture);
            a.matrix.invoke(a.uniform(program,"InverseProjection"),inverseProjection);
            a.setVector2.invoke(a.uniform(program,"FrameSize"),(float)depthCopy.width,(float)depthCopy.height);
            // Drain this type BEFORE changing the values for the next source.
            buffers.endBatch(type);
            a.vector(program, "CameraLocal", (float) camera.x, (float) camera.y, (float) camera.z);
            a.scalar(program, "Strength", unit(strength));
            a.scalar(program, "Storm", unit(storm));
            a.scalar(program, "Time", (float) ((ticks % 1048576.0) / 20.0)
                    * (ShengxianVisualConfig.reduced() ? .35f : 1.0f));
            boolean lowQuality = ShengxianVisualConfig.lowPerformance();
            a.scalar(program, "VolumeScale", Math.max(1f,volumeScale));
            a.scalar(program, "LowQuality", lowQuality ? 1.0f : 0.0f);
            a.integer(program, "Steps", tempest ? (lowQuality ? 36 : 64) : (lowQuality ? 16 : 24));
            VertexConsumer out = buffers.getBuffer(type);
            net.guzhenren.shengxian.core.AscensionAuraMesh.Sink sink = (x, y, z, u, v, r, g, b, alpha, nx, ny, nz) ->
                    out.addVertex(pose, x, y, z).setColor(r, g, b, alpha).setUv(u, v)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                            .setNormal(pose, nx, ny, nz);
            if(tempest) net.guzhenren.shengxian.core.TempestVolumeMesh.draw(strength,sink);
            else AscensionCloudMesh.draw(strength,sink);
            buffers.endBatch(type);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (!warned) {
                warned = true;
                LoggerFactory.getLogger("shengxian").warn("Veil ascension cloud volume unavailable", exception);
            }
        }
    }

    private static float unit(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /** Only public API contracts are reflected, never Veil's private uniform implementation classes. */
    private static final class Api {
        final Method renderer, shaderManager, shader, renderType;
        final Method getUniform, isValid, setFloat, setVector, setInt, texture, matrix, setVector2;

        Api() throws ReflectiveOperationException {
            renderer = Class.forName("foundry.veil.api.client.render.VeilRenderSystem").getMethod("renderer");
            shaderManager = Class.forName("foundry.veil.api.client.render.VeilRenderer").getMethod("getShaderManager");
            shader = Class.forName("foundry.veil.api.client.render.shader.ShaderManager")
                    .getMethod("getShader", ResourceLocation.class);
            renderType = Class.forName("foundry.veil.api.client.render.rendertype.VeilRenderType")
                    .getMethod("get", ResourceLocation.class, Object[].class);
            getUniform = Class.forName("foundry.veil.api.client.render.shader.program.UniformAccess")
                    .getMethod("getUniform", CharSequence.class);
            Class<?> access = Class.forName("foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess");
            isValid = access.getMethod("isValid");
            setFloat = access.getMethod("setFloat", float.class);
            setVector = access.getMethod("setVector", float.class, float.class, float.class);
            setInt = access.getMethod("setInt", int.class);
            texture=Class.forName("foundry.veil.api.client.render.shader.program.TextureUniformAccess")
                    .getMethod("setTexture",CharSequence.class,int.class,int.class);
            matrix=access.getMethod("setMatrix",Matrix4fc.class);
            setVector2=access.getMethod("setVector",float.class,float.class);
        }

        Object uniform(Object program, String name) throws ReflectiveOperationException {
            Object value = getUniform.invoke(program, name);
            if (value == null || !Boolean.TRUE.equals(isValid.invoke(value))) {
                throw new IllegalStateException("Missing cloud uniform " + name);
            }
            return value;
        }

        void scalar(Object program, String name, float value) throws ReflectiveOperationException {
            setFloat.invoke(uniform(program, name), value);
        }

        void vector(Object program, String name, float x, float y, float z) throws ReflectiveOperationException {
            setVector.invoke(uniform(program, name), x, y, z);
        }

        void integer(Object program, String name, int value) throws ReflectiveOperationException {
            setInt.invoke(uniform(program, name), value);
        }
    }
}
