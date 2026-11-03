import java.nio.FloatBuffer;
import java.nio.file.Files;
import net.guzhenren.shengxian.core.AscensionCloudMesh;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL33C.*;

/** Tests the actual cloud shader without a game, world or server. */
public final class VerifyCloudVolume {
    static final int SIZE=128;
    static float[] inverseProjection(float[] p) {
        return new float[]{1/p[0],0,0,0,0,1/p[5],0,0,0,0,0,1/p[14],0,0,-1,p[10]/p[14]};
    }
    static void setDepth(float depth) {
        FloatBuffer data=BufferUtils.createFloatBuffer(SIZE*SIZE);
        for(int i=0;i<SIZE*SIZE;i++)data.put(depth);
        glTexImage2D(GL_TEXTURE_2D,0,GL_R32F,SIZE,SIZE,0,GL_RED,GL_FLOAT,data.flip());
    }
    public static void verify() throws Exception {
        int program=VerifyFxShaders.link(Files.readString(VerifyFxShaders.ROOT.resolve("ascension_cloud_volume.vsh")),
                Files.readString(VerifyFxShaders.ROOT.resolve("ascension_cloud_volume.fsh")));
        for(String name:new String[]{"CameraLocal","Strength","Storm","Time","Steps","LowQuality","FrameSize","InverseProjection","SceneDepthSampler"})
            if(glGetUniformLocation(program,name)<0)throw new IllegalStateException("Missing cloud uniform "+name);
        int tex=glGenTextures();glBindTexture(GL_TEXTURE_2D,tex);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA16F,SIZE,SIZE,0,GL_RGBA,GL_FLOAT,(FloatBuffer)null);
        int fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,tex,0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Cloud test FBO");
        int outputDepth=glGenTextures();glBindTexture(GL_TEXTURE_2D,outputDepth);
        glTexImage2D(GL_TEXTURE_2D,0,GL_DEPTH_COMPONENT24,SIZE,SIZE,0,GL_DEPTH_COMPONENT,GL_FLOAT,(FloatBuffer)null);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,outputDepth,0);
        int sceneDepth=glGenTextures();glBindTexture(GL_TEXTURE_2D,sceneDepth);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glViewport(0,0,SIZE,SIZE);glEnable(GL_DEPTH_TEST);glDepthFunc(GL_ALWAYS);glDepthMask(false);
        glDisable(GL_BLEND);glEnable(GL_CULL_FACE);glCullFace(GL_FRONT);
        var mesh=new PreviewAscensionWorld.Mesh();
        float[][] cameras={{18,10,24},{-24,10,3},{2,10,-24},{0,1.62f,0},{0,13,0}};
        int visible=0;
        for(int i=0;i<cameras.length;i++) {
            float[] c=cameras[i];
            mesh.reset();
            AscensionCloudMesh.draw(1,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->
                    mesh.vertex(x-c[0],y-c[1],z-c[2],u,v,r,g,b,a,nx,ny,nz));
            mesh.upload();glUseProgram(program);
            float[] view=PreviewAscensionWorld.lookAt(c[0],c[1],c[2],0,i==4?14:12.8f,i==4?6:0);
            view[12]=view[13]=view[14]=0;
            glUniformMatrix4fv(glGetUniformLocation(program,"ModelViewMat"),false,view);
            float[] projection=PreviewAscensionWorld.perspective(65,1,.05f,100);
            glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,projection);
            glUniformMatrix4fv(glGetUniformLocation(program,"InverseProjection"),false,inverseProjection(projection));
            glUniform2f(glGetUniformLocation(program,"FrameSize"),SIZE,SIZE);
            glUniform1i(glGetUniformLocation(program,"SceneDepthSampler"),3);
            glActiveTexture(GL_TEXTURE3);glBindTexture(GL_TEXTURE_2D,sceneDepth);setDepth(1);
            glDepthMask(true);glClearDepth(1);glClear(GL_DEPTH_BUFFER_BIT);glDepthMask(false);
            glUniform3f(glGetUniformLocation(program,"CameraLocal"),c[0],c[1],c[2]);
            glUniform1f(glGetUniformLocation(program,"Time"),17);
            glUniform1f(glGetUniformLocation(program,"Storm"),i%2);
            glUniform1f(glGetUniformLocation(program,"LowQuality"),i%2);
            glUniform1i(glGetUniformLocation(program,"Steps"),i%2==0?24:16);
            for(int enabled=0;enabled<2;enabled++){
                glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
                glUniform1f(glGetUniformLocation(program,"Strength"),enabled);
                mesh.draw();
                FloatBuffer data=BufferUtils.createFloatBuffer(SIZE*SIZE*4);
                glReadPixels(0,0,SIZE,SIZE,GL_RGBA,GL_FLOAT,data);
                int nonzero=0;
                for(int at=0;at<data.capacity();at++){
                    float value=data.get(at);
                    if(!Float.isFinite(value)||value<0||value>2.01f)throw new IllegalStateException("Invalid cloud pixel");
                    if(value>.003f)nonzero++;
                }
                if(enabled==0&&nonzero!=0)throw new IllegalStateException("Disabled cloud still writes");
                if(enabled==1){
                    if(nonzero<30)throw new IllegalStateException("Cloud invisible for camera "+i);
                    visible++;
                }
            }
        }
        // Opaque wall cuts the volume midway. Near-side mist must survive; a wall before the box must hide it.
        mesh.reset();AscensionCloudMesh.draw(1,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->mesh.vertex(x,y-13,z-20,u,v,r,g,b,a,nx,ny,nz));
        mesh.upload();glUseProgram(program);
        float[] rotation=PreviewAscensionWorld.lookAt(0,13,20,0,13,0);rotation[12]=rotation[13]=rotation[14]=0;
        glUniformMatrix4fv(glGetUniformLocation(program,"ModelViewMat"),false,rotation);
        glUniform3f(glGetUniformLocation(program,"CameraLocal"),0,13,20);
        glUniform1f(glGetUniformLocation(program,"Strength"),1);
        glUniform1f(glGetUniformLocation(program,"Storm"),0);glUniform1f(glGetUniformLocation(program,"LowQuality"),0);
        glUniform1i(glGetUniformLocation(program,"Steps"),24);
        String type=Files.readString(java.nio.file.Path.of("src/main/resources/assets/shengxian/pinwheel/rendertypes/ascension_cloud_volume.json"));
        boolean always=type.matches("(?s).*\"mode\"\\s*:\\s*\"always\".*");
        glDepthFunc(Boolean.getBoolean("shengxian.cloudLegacy") || !always?GL_LEQUAL:GL_ALWAYS);
        for(float scale:new float[]{1,4}) {
            float[] scaledProjection=PreviewAscensionWorld.perspective(65,1,.05f,400);
            glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,scaledProjection);
            glUniformMatrix4fv(glGetUniformLocation(program,"InverseProjection"),false,inverseProjection(scaledProjection));
            mesh.reset();
            AscensionCloudMesh.draw(1,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->
                mesh.vertex(x*scale,(y-13)*scale,(z-20)*scale,u,v,r,g,b,a,nx,ny,nz));
            mesh.upload();
            glUniform1f(glGetUniformLocation(program,"VolumeScale"),scale);
            for(float wall:new float[]{20,2}) {
                float worldDistance=wall*scale;
                float depth=(400f/(400-.05f))-(400*.05f/((400-.05f)*worldDistance));
                glActiveTexture(GL_TEXTURE3);glBindTexture(GL_TEXTURE_2D,sceneDepth);setDepth(depth);
                glDepthMask(true);glClearDepth(depth);glClear(GL_DEPTH_BUFFER_BIT);glDepthMask(false);
                glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);mesh.draw();
                FloatBuffer pixel=BufferUtils.createFloatBuffer(4);
                glReadPixels(SIZE/2,SIZE/2,1,1,GL_RGBA,GL_FLOAT,pixel);
                if(wall==20 && pixel.get(3)<.03f)throw new IllegalStateException("Foreground cloud erased at scale "+scale);
                if(wall==2 && pixel.get(3)>.001f)throw new IllegalStateException("Wall leak at scale "+scale);
            }
        }
        glDepthFunc(GL_LEQUAL);glDisable(GL_DEPTH_TEST);
        glDeleteTextures(outputDepth);glDeleteTextures(sceneDepth);
        if(glGetError()!=GL_NO_ERROR)throw new IllegalStateException("Cloud GPU error");
        glDisable(GL_CULL_FACE);glDepthMask(true);
        glDeleteProgram(program);glDeleteFramebuffers(fbo);glDeleteTextures(tex);
        System.out.println("PASS volume GPU: "+visible+" exterior/first-person/interior cameras, 16/24 steps, zero strength, finite HDR output, mid-volume/foreground wall occlusion");
    }
}