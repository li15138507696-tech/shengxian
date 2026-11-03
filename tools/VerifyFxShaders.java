import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Standalone hidden OpenGL probe. Loads no Minecraft/NeoForge classes and starts no game/server. */
public final class VerifyFxShaders {
    static final int W=480,H=270;
    static final Path ROOT=Path.of("src/main/resources/assets/shengxian/pinwheel/shaders/program");
    static final float[][] COLORS={{.75f,.90f,1f},{.19f,.78f,.54f},{1f,.62f,.25f},{.47f,.91f,.69f}};
    static int program,vao,scene,depth,fbo,target;
    static byte[] original;
    public static void main(String[] args) throws Exception {
        var error=GLFWErrorCallback.createPrint(System.err); error.set();
        if(!glfwInit()) throw new IllegalStateException("GLFW init failed");
        long window=0;
        try {
            glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
            glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
            window=glfwCreateWindow(W,H,"Shengxian offline shader verification",0,0);
            if(window==0) throw new IllegalStateException("No hidden OpenGL context");
            glfwMakeContextCurrent(window); GL.createCapabilities();
            System.out.println("Offline OpenGL: "+glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION));
            String vsh="#version 330 core\nout vec2 texCoord; void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2); texCoord=p; gl_Position=vec4(p*2.0-1.0,0,1);}";
            program=link(vsh,Files.readString(ROOT.resolve("ascension_flow_post.fsh")));
            int aura=link(Files.readString(ROOT.resolve("ascension_sanqi_converge.vsh")),
                    Files.readString(ROOT.resolve("ascension_sanqi_converge.fsh")));
            glDeleteProgram(aura);
            System.out.println("PASS post and world aura GLSL compile/link");
            glUseProgram(program);
            for(String required:new String[]{"Strength","Stage","Progress","Age","Time","Instability","Insight",
                    "Calamity","Impact","Resolution","Weights","HeavenColor","EarthColor","HumanColor","JadeColor","DiffuseSampler0","DiffuseDepthSampler"})
                if(glGetUniformLocation(program,required)<0) throw new IllegalStateException("Inactive/missing uniform "+required);
            vao=glGenVertexArrays(); glBindVertexArray(vao);
            glUniform1i(loc("DiffuseSampler0"),0); glUniform1i(loc("DiffuseDepthSampler"),1);
            glUniform2f(loc("Resolution"),W,H);
            glUniform3f(loc("Weights"),1,.7f,.85f);
            for(int i=0;i<4;i++) glUniform3f(loc(new String[]{"HeavenColor","EarthColor","HumanColor","JadeColor"}[i]),
                    COLORS[i][0],COLORS[i][1],COLORS[i][2]);
            original=new byte[W*H*4];
            for(int y=0;y<H;y++) for(int x=0;x<W;x++) {
                float h=y/(float)H;
                double mountain=.43+.09*Math.sin(x*.022)+.04*Math.sin(x*.057);
                boolean ground=h<mountain;
                int r=ground?(int)(46+h*45):(int)(110+h*52);
                int g=ground?(int)(61+h*60):(int)(139+h*53);
                int b=ground?(int)(54+h*42):(int)(155+h*50);
                if(ground && ((x/24+y/20)%2==0)){r+=5;g+=6;b+=4;}
                int o=(y*W+x)*4; original[o]=(byte)r;original[o+1]=(byte)g;original[o+2]=(byte)b;original[o+3]=(byte)255;
            }
            scene=texture(GL_RGBA8,GL_RGBA,GL_UNSIGNED_BYTE,BufferUtils.createByteBuffer(original.length).put(original).flip());
            depth=glGenTextures(); glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D,depth);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            setDepth(.92f);
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,scene);
            target=texture(GL_RGBA8,GL_RGBA,GL_UNSIGNED_BYTE,null);
            fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete probe FBO");
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,scene);
            glViewport(0,0,W,H);
            scalar("Strength",0); scalar("Stage",3);
            byte[] zero=render();
            same(original,zero,false,"strength zero preserves input");
            scalar("Strength",1);setDepth(.0001f);
            same(original,render(),false,"depth guard preserves near pixels");
            setDepth(.92f);
            String[] labels={"SOURCE / QUIET","SHATTER","BROKEN","THREE QI / INSIGHT","MIXED QI","APERTURE FORMING","IMMORTAL ESSENCE","FAILURE DISSIPATION"};
            BufferedImage sheet=new BufferedImage(W*4,(H+28)*2,BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics=sheet.createGraphics();
            graphics.setColor(new Color(20,29,27));graphics.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
            graphics.setFont(new Font("SansSerif",Font.PLAIN,15));
            for(int stage=0;stage<8;stage++) {
                scalar("Stage",stage);scalar("Strength",stage==0?0:1);
                scalar("Progress",.8f);scalar("Age",stage==6?1.3f:1.8f);
                scalar("Time",3.6f);scalar("Instability",.45f);scalar("Insight",1);scalar("Calamity",stage==3?1:0);scalar("Impact",0);
                byte[] pixels=render();
                same(original,pixels,true,"stage "+stage+" preserves central viewing area");
                int changed=0; for(int i=0;i<pixels.length;i+=4)
                    if(Math.abs((pixels[i]&255)-(original[i]&255))>1) changed++;
                if(stage>0 && changed<100) throw new IllegalStateException("Stage "+stage+" had no visible edge response");
                BufferedImage frame=toImage(pixels);
                int col=stage%4,row=stage/4;
                graphics.drawImage(frame,col*W,row*(H+28)+28,null);
                graphics.setColor(new Color(210,223,210));graphics.drawString(labels[stage],col*W+12,row*(H+28)+20);
            }
            graphics.dispose();
            Path out=Path.of("build/fx-offline");Files.createDirectories(out);
            ImageIO.write(sheet,"PNG",out.resolve("ascension-flow-contact.png").toFile());
            if(glGetError()!=GL_NO_ERROR) throw new IllegalStateException("OpenGL reported an error");
            System.out.println("PASS 8 stages, center safety, zero strength, depth guard, all runtime uniforms");
            System.out.println("Preview: "+out.resolve("ascension-flow-contact.png").toAbsolutePath());
            VerifyCloudVolume.verify();
            PreviewAscensionWorld.render();
        } finally {
            if(window!=0) glfwDestroyWindow(window);
            glfwTerminate();error.free();
        }
    }
    static int shader(int type,String source) {
        int s=glCreateShader(type);glShaderSource(s,source.startsWith("#version")?source:"#version 330 core\n"+source);
        glCompileShader(s);if(glGetShaderi(s,GL_COMPILE_STATUS)==0) throw new IllegalStateException(glGetShaderInfoLog(s));
        return s;
    }
    static int link(String v,String f) {
        int vs=shader(GL_VERTEX_SHADER,v),fs=shader(GL_FRAGMENT_SHADER,f),p=glCreateProgram();
        glAttachShader(p,vs);glAttachShader(p,fs);glLinkProgram(p);
        if(glGetProgrami(p,GL_LINK_STATUS)==0) throw new IllegalStateException(glGetProgramInfoLog(p));
        glDeleteShader(vs);glDeleteShader(fs);return p;
    }
    static int loc(String name){return glGetUniformLocation(program,name);}
    static void scalar(String name,float v){glUniform1f(loc(name),v);}
    static int texture(int internal,int format,int type,ByteBuffer data) {
        int t=glGenTextures();glBindTexture(GL_TEXTURE_2D,t);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D,0,internal,W,H,0,format,type,data);
        return t;
    }
    static void setDepth(float value) {
        FloatBuffer values=BufferUtils.createFloatBuffer(W*H);for(int i=0;i<W*H;i++)values.put(value);values.flip();
        glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,depth);
        glTexImage2D(GL_TEXTURE_2D,0,GL_R32F,W,H,0,GL_RED,GL_FLOAT,values);
        glActiveTexture(GL_TEXTURE0);
    }
    static byte[] render() {
        glDrawArrays(GL_TRIANGLES,0,3);
        ByteBuffer b=BufferUtils.createByteBuffer(W*H*4);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,b);
        byte[] bytes=new byte[b.remaining()];b.get(bytes);return bytes;
    }
    static void same(byte[] expected,byte[] actual,boolean center,String label) {
        for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
            if(center && (Math.abs(x-W/2)>W/28 || Math.abs(y-H/2)>H/12))continue;
            for(int c=0;c<4;c++) {
                int i=(y*W+x)*4+c;
                if(Math.abs((expected[i]&255)-(actual[i]&255))>1) throw new IllegalStateException(label+" at "+x+","+y);
            }
        }
    }
    static BufferedImage toImage(byte[] p) {
        BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
            int o=(y*W+x)*4;
            image.setRGB(x,H-y-1,((p[o]&255)<<16)|((p[o+1]&255)<<8)|(p[o+2]&255));
        }
        return image;
    }
}
