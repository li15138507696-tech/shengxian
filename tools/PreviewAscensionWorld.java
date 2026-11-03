import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import net.guzhenren.shengxian.core.AscensionAuraMesh;
import net.guzhenren.shengxian.core.AscensionVisuals;
import net.guzhenren.shengxian.core.Phase;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL33C.*;

/** Actual shared world geometry and shaders on a synthetic scene; approximate bloom, no Minecraft. */
public final class PreviewAscensionWorld {
    static final int W=640,H=450;
    static float EX=7.5f,EY=4.4f,EZ=10.5f;
    static float[] VIEW=lookAt(EX,EY,EZ,0,2.7f,0); static final float[] PROJECTION=perspective(46,(float)W/H,.1f,100);
    static int flat,aura,vision,cloud,blur,combine,post,fullVao;
    record Target(int fbo,int color) {}
    static Target scene,glow,blurA,blurB,combined,output;
    static int depth,depthCopy;
    static final String FULL="#version 330 core\nout vec2 texCoord;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texCoord=p;gl_Position=vec4(p*2.-1.,0,1);}";
    static final class Mesh implements AscensionAuraMesh.Sink {
        final FloatBuffer data=BufferUtils.createFloatBuffer(800000);
        final float[] quad=new float[48];
        int pending,vertices,vao=glGenVertexArrays(),vbo=glGenBuffers();
        public void vertex(float x,float y,float z,float u,float v,float r,float g,float b,float a,float nx,float ny,float nz) {
            int at=pending*12;
            float[] values={x,y,z,r,g,b,a,u,v,nx,ny,nz};
            System.arraycopy(values,0,quad,at,12);
            if(++pending==4) {
                for(int index:new int[]{0,1,2,2,3,0}) data.put(quad,index*12,12);
                vertices+=6;pending=0;
            }
        }
        void reset(){data.clear();pending=vertices=0;}
        void upload(){
            if(pending!=0) throw new IllegalStateException("Incomplete quad");
            data.flip();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
            glBufferData(GL_ARRAY_BUFFER,data,GL_STREAM_DRAW);
            int[] locations={0,1,2,5},sizes={3,4,2,3},offsets={0,12,28,36};
            for(int i=0;i<4;i++){glEnableVertexAttribArray(locations[i]);glVertexAttribPointer(locations[i],sizes[i],GL_FLOAT,false,48,offsets[i]);}
        }
        void draw(){glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,vertices);}
    }
    public static void render() throws Exception {
        var root=VerifyFxShaders.ROOT;
        aura=VerifyFxShaders.link(Files.readString(root.resolve("ascension_sanqi_converge.vsh")),
                Files.readString(root.resolve("ascension_sanqi_converge.fsh")));
        vision=VerifyFxShaders.link(Files.readString(root.resolve("ascension_aperture_vision.vsh")), Files.readString(root.resolve("ascension_aperture_vision.fsh")));
        cloud=VerifyFxShaders.link(Files.readString(root.resolve("ascension_cloud_volume.vsh")),Files.readString(root.resolve("ascension_cloud_volume.fsh")));
        for(String name:new String[]{"CameraLocal","Strength","Storm","Time","Steps","LowQuality"})
            if(glGetUniformLocation(cloud,name)<0)throw new IllegalStateException("Missing active cloud uniform "+name);
        flat=VerifyFxShaders.link("#version 330 core\nlayout(location=0)in vec3 Position;layout(location=1)in vec4 Color;uniform mat4 ModelViewMat;uniform mat4 ProjMat;out vec4 color;void main(){color=Color;gl_Position=ProjMat*ModelViewMat*vec4(Position,1);}",
                "#version 330 core\nin vec4 color;out vec4 fragColor;void main(){fragColor=color;}");
        blur=VerifyFxShaders.link(FULL,"#version 330 core\nuniform sampler2D Image;uniform vec2 Direction;in vec2 texCoord;out vec4 fragColor;void main(){vec3 c=texture(Image,texCoord).rgb*.227027;float w[4]=float[4](.1945946,.1216216,.054054,.016216);for(int i=1;i<=4;i++){c+=(texture(Image,texCoord+Direction*float(i)).rgb+texture(Image,texCoord-Direction*float(i)).rgb)*w[i-1];}fragColor=vec4(c,1);}");
        combine=VerifyFxShaders.link(FULL,"#version 330 core\nuniform sampler2D Scene;uniform sampler2D Glow;in vec2 texCoord;out vec4 fragColor;void main(){vec3 c=texture(Scene,texCoord).rgb+texture(Glow,texCoord).rgb*.72;c=vec3(1)-exp(-c*.94);fragColor=vec4(pow(max(c,vec3(0)),vec3(.78)),1);}");
        post=VerifyFxShaders.link(FULL,Files.readString(root.resolve("ascension_flow_post.fsh")));
        fullVao=glGenVertexArrays();depth=depthTexture();depthCopy=depthTexture();
        scene=target(true);glow=target(true);blurA=target(false);blurB=target(false);combined=target(false);output=target(false);
        Mesh background=new Mesh(),effect=new Mesh(),visionMesh=new Mesh(),cloudMesh=new Mesh();
        box(background,-12,-.1f,-12,12,0,12,.022f,.036f,.039f);
        for(int i=-10;i<=10;i++) {
            box(background,i-.006f,.002f,-10,i+.006f,.005f,10,.052f,.077f,.073f);
            box(background,-10,.002f,i-.006f,10,.005f,i+.006f,.052f,.077f,.073f);
        }
        box(background,-.24f,1.48f,-.23f,.24f,1.96f,.23f,.38f,.46f,.48f);
        box(background,-.25f,.72f,-.15f,.25f,1.46f,.15f,.25f,.35f,.39f);
        box(background,-.43f,.78f,-.13f,-.27f,1.43f,.13f,.26f,.37f,.4f);
        box(background,.27f,.78f,-.13f,.43f,1.43f,.13f,.26f,.37f,.4f);
        box(background,-.22f,.02f,-.13f,-.035f,.7f,.13f,.20f,.28f,.31f);
        box(background,.035f,.02f,-.13f,.22f,.7f,.13f,.20f,.28f,.31f);
        background.upload();
        Phase[] phases={Phase.PREPARING,Phase.BREAKING,Phase.ABSORBING,Phase.ABSORBING,Phase.ABSORBING,Phase.ABSORBING,
                Phase.OPENING,Phase.FORMING,Phase.ABSORBING,Phase.IMMORTAL,Phase.IMMORTAL,Phase.FAILED};
        float[] ages={80,70,60,140,240,330,50,48,360,100,164,25};
        String[] labels={"凡躯蓄势 · 阵纹流光","凡窍震裂 · 空间碎光","人气升腾 · 赤金丝线","地气喷涌 · 翡翠龙脉",
                "天气降临 · 高空云涡","三气交汇 · 交错光轮","混元凝聚 · 等待投蛊","本命投入 · 气团定型",
                "真实音磬劫 · 劫云声波","已成仙窍 · 七层山海异象","仙光归寂 · 余辉消散","实际失败 · 气机逸散"};
        BufferedImage contact=new BufferedImage(W*3,(H+36)*4,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=contact.createGraphics();
        graphics.setColor(new Color(13,22,27));graphics.fillRect(0,0,contact.getWidth(),contact.getHeight());
        graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,19));
        int maxVertices=0;
        for(int i=0;i<phases.length;i++) {
            float age=ages[i];
            boolean wide=phases[i]==Phase.ABSORBING && age>=200;
            EX=wide?17f:7.5f; EY=wide?10f:4.4f; EZ=wide?24f:10.5f;
            VIEW=lookAt(EX,EY,EZ,0,wide?6.5f:2.7f,0);
            var frame=new AscensionVisuals.Frame(phases[i],10,.82f,1,1,.94f,.90f,.18f,phases[i]==Phase.ABSORBING && age>=300,i==8?2:0,phases[i]==Phase.IMMORTAL);
            effect.reset();
            AscensionAuraMesh.draw(frame,age,0,1,340,EX,EY,EZ,40,false,effect);
            effect.upload();
            visionMesh.reset();
            if(frame.confirmedFormed()) net.guzhenren.shengxian.core.ApertureVisionMesh.draw(Math.max(0,age-24),
                    age>140?(200-age)/60:1,340,0,EX,EY,EZ,visionMesh);
            visionMesh.upload();
            cloudMesh.reset();
            float cloudStrength=net.guzhenren.shengxian.core.AscensionVisuals.cloudStrength(frame,age);
            net.guzhenren.shengxian.core.AscensionCloudMesh.draw(cloudStrength,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->
                    cloudMesh.vertex(x-EX,y-EY,z-EZ,u,v,r,g,b,a,nx,ny,nz));
            cloudMesh.upload();
            maxVertices=Math.max(maxVertices,effect.vertices+visionMesh.vertices+cloudMesh.vertices);
            glViewport(0,0,W,H);glBindFramebuffer(GL_FRAMEBUFFER,scene.fbo());
            glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);glDepthMask(true);glDisable(GL_CULL_FACE);
            glDisable(GL_BLEND);glClearColor(.022f,.033f,.055f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            matrices(flat);background.draw();
            matrices(aura);uniform(aura,"VeilRenderTime",17);
            glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);effect.draw();
            if(visionMesh.vertices>0) {matrices(vision);uniform(vision,"VeilRenderTime",17);visionMesh.draw();}
            if(cloudMesh.vertices>0) {
                glActiveTexture(GL_TEXTURE3);glBindTexture(GL_TEXTURE_2D,depthCopy);
                glCopyTexSubImage2D(GL_TEXTURE_2D,0,0,0,0,0,W,H);
                glEnable(GL_CULL_FACE);glCullFace(GL_FRONT);glDepthFunc(GL_ALWAYS);
                // Cloud vertices are already camera-relative, matching the live source pose.
                float[] rotation=VIEW.clone();rotation[12]=rotation[13]=rotation[14]=0;
                glUseProgram(cloud);glUniformMatrix4fv(glGetUniformLocation(cloud,"ModelViewMat"),false,rotation);
                glUniformMatrix4fv(glGetUniformLocation(cloud,"ProjMat"),false,PROJECTION);
                glUniform3f(glGetUniformLocation(cloud,"CameraLocal"),EX,EY,EZ);
                glUniform1i(glGetUniformLocation(cloud,"SceneDepthSampler"),3);
                glUniform2f(glGetUniformLocation(cloud,"FrameSize"),W,H);
                glUniformMatrix4fv(glGetUniformLocation(cloud,"InverseProjection"),false,VerifyCloudVolume.inverseProjection(PROJECTION));
                uniform(cloud,"Strength",cloudStrength);uniform(cloud,"Storm",frame.calamityMask()!=0?1:0);
                uniform(cloud,"Time",17);uniform(cloud,"LowQuality",0);glUniform1i(glGetUniformLocation(cloud,"Steps"),24);
                cloudMesh.draw();glDisable(GL_CULL_FACE);glDepthFunc(GL_LEQUAL);
            }
            glBindFramebuffer(GL_FRAMEBUFFER,glow.fbo());glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
            glBlendFunc(GL_SRC_ALPHA,GL_ONE);matrices(aura);effect.draw();
            if(visionMesh.vertices>0) {matrices(vision);uniform(vision,"VeilRenderTime",17);visionMesh.draw();}

            glDepthMask(true);glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);
            fullscreen(blur,blurA);texture(0,glow.color());glUniform1i(glGetUniformLocation(blur,"Image"),0);
            glUniform2f(glGetUniformLocation(blur,"Direction"),1.5f/W,0);glDrawArrays(GL_TRIANGLES,0,3);
            fullscreen(blur,blurB);texture(0,blurA.color());glUniform2f(glGetUniformLocation(blur,"Direction"),0,1.5f/H);glDrawArrays(GL_TRIANGLES,0,3);
            fullscreen(combine,combined);texture(0,scene.color());texture(1,blurB.color());
            glUniform1i(glGetUniformLocation(combine,"Scene"),0);glUniform1i(glGetUniformLocation(combine,"Glow"),1);
            glDrawArrays(GL_TRIANGLES,0,3);
            fullscreen(post,output);texture(0,combined.color());texture(1,depth);
            glUniform1i(glGetUniformLocation(post,"DiffuseSampler0"),0);glUniform1i(glGetUniformLocation(post,"DiffuseDepthSampler"),1);
            uniform(post,"Strength",1);uniform(post,"Stage",phases[i].ordinal());uniform(post,"Progress",frame.progress());
            uniform(post,"Age",age/20);uniform(post,"Time",17);uniform(post,"Instability",frame.instability());
            uniform(post,"Insight",frame.insight()?1:0);uniform(post,"Calamity",frame.calamityMask()!=0?1:0);uniform(post,"Impact",i==8?.7f:0);
            glUniform2f(glGetUniformLocation(post,"Resolution"),W,H);glUniform3f(glGetUniformLocation(post,"Weights"),1,.94f,.9f);
            AscensionAuraMesh.Color[] colors={AscensionAuraMesh.HEAVEN,AscensionAuraMesh.EARTH,AscensionAuraMesh.HUMAN,AscensionAuraMesh.JADE};
            String[] names={"HeavenColor","EarthColor","HumanColor","JadeColor"};
            for(int c=0;c<4;c++)glUniform3f(glGetUniformLocation(post,names[c]),colors[c].r(),colors[c].g(),colors[c].b());
            glDrawArrays(GL_TRIANGLES,0,3);
            BufferedImage image=read();
            int col=i%3,row=i/3;
            graphics.drawImage(image,col*W,row*(H+36)+36,null);
            graphics.setColor(new Color(202,222,215));graphics.drawString(labels[i],col*W+14,row*(H+36)+26);
            if(i==5) ImageIO.write(image,"PNG",Path.of("build/fx-offline/ascension-rich-sanqi.png").toFile());
            if(i==9) ImageIO.write(image,"PNG",Path.of("build/fx-offline/ascension-aperture-vision.png").toFile());
            if(i==11) ImageIO.write(image,"PNG",Path.of("build/fx-offline/ascension-ritual-failure.png").toFile());
            int err=glGetError();if(err!=GL_NO_ERROR)throw new IllegalStateException("Preview GL error "+err+" stage "+phases[i]);
        }
        graphics.dispose();
        ImageIO.write(contact,"PNG",Path.of("build/fx-offline/ascension-rich-world-contact.png").toFile());
        System.out.println("PASS shared world mesh + actual shaders + approximate bloom; peak triangle vertices="+maxVertices);
        System.out.println("World preview: "+Path.of("build/fx-offline/ascension-rich-world-contact.png").toAbsolutePath());
    }
    static void uniform(int program,String name,float value){glUniform1f(glGetUniformLocation(program,name),value);}
    static void texture(int unit,int texture){glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,texture);}
    static void matrices(int program){
        glUseProgram(program);
        glUniformMatrix4fv(glGetUniformLocation(program,"ModelViewMat"),false,VIEW);
        glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,PROJECTION);
    }
    static void fullscreen(int program,Target target){glBindFramebuffer(GL_FRAMEBUFFER,target.fbo());glUseProgram(program);glBindVertexArray(fullVao);}
    static int depthTexture(){
        int t=glGenTextures();glBindTexture(GL_TEXTURE_2D,t);
        glTexImage2D(GL_TEXTURE_2D,0,GL_DEPTH_COMPONENT24,W,H,0,GL_DEPTH_COMPONENT,GL_UNSIGNED_INT,(ByteBuffer)null);
        params();return t;
    }
    static void params(){glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);}
    static Target target(boolean attachDepth){
        int t=glGenTextures();glBindTexture(GL_TEXTURE_2D,t);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA16F,W,H,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);params();
        int f=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,f);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,t,0);
        if(attachDepth)glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,depth,0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Preview FBO incomplete");
        return new Target(f,t);
    }
    static BufferedImage read(){
        ByteBuffer bytes=BufferUtils.createByteBuffer(W*H*4);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
        BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int at=(y*W+x)*4;
            image.setRGB(x,H-y-1,((bytes.get(at)&255)<<16)|((bytes.get(at+1)&255)<<8)|(bytes.get(at+2)&255));
        }
        return image;
    }
    static float[] perspective(float angle,float aspect,float near,float far){
        float f=(float)(1/Math.tan(Math.toRadians(angle)/2));
        return new float[]{f/aspect,0,0,0,0,f,0,0,0,0,(far+near)/(near-far),-1,0,0,2*far*near/(near-far),0};
    }
    static float[] lookAt(float ex,float ey,float ez,float tx,float ty,float tz){
        float fx=tx-ex,fy=ty-ey,fz=tz-ez,len=(float)Math.sqrt(fx*fx+fy*fy+fz*fz);fx/=len;fy/=len;fz/=len;
        float sx=-fz,sy=0,sz=fx;len=(float)Math.sqrt(sx*sx+sz*sz);if(len<1e-6f){sx=1;sz=0;len=1;}sx/=len;sz/=len;
        float ux=sy*fz-sz*fy,uy=sz*fx-sx*fz,uz=sx*fy-sy*fx;
        return new float[]{sx,ux,-fx,0,sy,uy,-fy,0,sz,uz,-fz,0,
                -(sx*ex+sy*ey+sz*ez),-(ux*ex+uy*ey+uz*ez),fx*ex+fy*ey+fz*ez,1};
    }
    static void box(Mesh m,float x0,float y0,float z0,float x1,float y1,float z1,float r,float g,float b){
        quad(m,new float[]{x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0},r*.72f,g*.72f,b*.72f);
        quad(m,new float[]{x0,y0,z1,x0,y1,z1,x1,y1,z1,x1,y0,z1},r*.9f,g*.9f,b*.9f);
        quad(m,new float[]{x0,y1,z0,x1,y1,z0,x1,y1,z1,x0,y1,z1},r,g,b);
        quad(m,new float[]{x0,y0,z0,x0,y0,z1,x1,y0,z1,x1,y0,z0},r*.5f,g*.5f,b*.5f);
        quad(m,new float[]{x0,y0,z0,x0,y1,z0,x0,y1,z1,x0,y0,z1},r*.65f,g*.65f,b*.65f);
        quad(m,new float[]{x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0},r*.8f,g*.8f,b*.8f);
    }
    static void quad(Mesh m,float[] p,float r,float g,float b){
        for(int i=0;i<4;i++)m.vertex(p[i*3],p[i*3+1],p[i*3+2],i%2,i/2,r,g,b,1,0,1,0);
    }
}
