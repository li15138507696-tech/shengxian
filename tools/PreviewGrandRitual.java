import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import net.guzhenren.shengxian.core.*;

/** Shared production mesh + real cloud GLSL; synthetic scene, never a game screenshot. */
public final class PreviewGrandRitual {
 static final int W=1200,H=800;
 static int loc(int p,String n){return glGetUniformLocation(p,n);}
 public static void main(String[] args)throws Exception{
  if(!glfwInit()) throw new IllegalStateException("GLFW init");
  glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
  glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
  long win=glfwCreateWindow(W,H,"Solid ritual preview",0,0);if(win==0)throw new IllegalStateException("context");
  try{
   glfwMakeContextCurrent(win);GL.createCapabilities();
   System.out.println("GPU preview: "+glGetString(GL_RENDERER));
   String v="layout(location=0) in vec3 Position;layout(location=1) in vec4 Color;layout(location=2) in vec2 UV;uniform mat4 View;uniform mat4 Projection;out vec4 color;out vec2 uv;void main(){color=Color;uv=UV;gl_Position=Projection*View*vec4(Position,1);}";
   String f="in vec4 color;in vec2 uv;uniform sampler2D Material;out vec4 fragColor;void main(){vec3 grain=texture(Material,uv).rgb;fragColor=vec4(color.rgb*grain*1.35,color.a);}";
   int solid=VerifyFxShaders.link(v,f);
   int cloud=VerifyFxShaders.link(Files.readString(VerifyFxShaders.ROOT.resolve("ascension_cloud_volume.vsh")),Files.readString(VerifyFxShaders.ROOT.resolve("ascension_cloud_volume.fsh")));
   var image=ImageIO.read(Path.of("src/main/resources/assets/shengxian/textures/effect/qi_marble.png").toFile());
   ByteBuffer tex=BufferUtils.createByteBuffer(image.getWidth()*image.getHeight()*4);
   for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=image.getRGB(x,y);tex.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)255);}tex.flip();
   int material=glGenTextures();glBindTexture(GL_TEXTURE_2D,material);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,image.getWidth(),image.getHeight(),0,GL_RGBA,GL_UNSIGNED_BYTE,tex);
   glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
   int depth=glGenTextures();glBindTexture(GL_TEXTURE_2D,depth);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
   var mesh=new PreviewAscensionWorld.Mesh();
   float[] projection=PreviewAscensionWorld.perspective(52,(float)W/H,.1f,300);
   float[][] cameras={{64,24,88},{-58,7,64}};
   Files.createDirectories(Path.of("build/fx-offline"));
   for(int shot=0;shot<cameras.length;shot++){
    float[] c=cameras[shot],view=PreviewAscensionWorld.lookAt(c[0],c[1],c[2],0,25,0);
    glViewport(0,0,W,H);glClearColor(.15f,.22f,.28f,1);glDepthMask(true);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
    glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);glDisable(GL_CULL_FACE);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
    glUseProgram(solid);glUniformMatrix4fv(loc(solid,"View"),false,view);glUniformMatrix4fv(loc(solid,"Projection"),false,projection);
    glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,material);glUniform1i(loc(solid,"Material"),0);
    mesh.reset();
    for(int x=-100;x<100;x+=4)for(int z=-100;z<100;z+=4){float q=((x+z)/4%2==0)?.19f:.16f;PreviewAscensionWorld.quad(mesh,new float[]{x,-1,z,x+4,-1,z,x+4,-1,z+4,x,-1,z+4},q,q*1.2f,q*1.05f);}
    // Two-block marker makes the 40-block structure's scale readable.
    PreviewAscensionWorld.box(mesh,1,0,1,1.6f,1.8f,1.6f,.9f,.73f,.48f);
    GrandRitualMesh.draw(280,1,false,shot==0?0:1,mesh);
    mesh.upload();mesh.draw();
    glBindTexture(GL_TEXTURE_2D,depth);glCopyTexImage2D(GL_TEXTURE_2D,0,GL_DEPTH_COMPONENT,0,0,W,H,0);
    mesh.reset();AscensionCloudMesh.draw(1,(x,y,z,u,vv,r,g,b,a,nx,ny,nz)->mesh.vertex(x*4-c[0],y*4-c[1],z*4-c[2],u,vv,r,g,b,a,nx,ny,nz));mesh.upload();
    glUseProgram(cloud);view[12]=view[13]=view[14]=0;
    glUniformMatrix4fv(loc(cloud,"ModelViewMat"),false,view);glUniformMatrix4fv(loc(cloud,"ProjMat"),false,projection);
    glUniformMatrix4fv(loc(cloud,"InverseProjection"),false,VerifyCloudVolume.inverseProjection(projection));
    glUniform3f(loc(cloud,"CameraLocal"),c[0]/4,c[1]/4,c[2]/4);glUniform1f(loc(cloud,"VolumeScale"),4);
    glUniform1f(loc(cloud,"Strength"),1);glUniform1f(loc(cloud,"Storm"),shot);glUniform1f(loc(cloud,"Time"),280);
    glUniform1f(loc(cloud,"LowQuality"),0);glUniform1i(loc(cloud,"Steps"),24);glUniform2f(loc(cloud,"FrameSize"),W,H);
    glUniform1i(loc(cloud,"SceneDepthSampler"),0);glBindTexture(GL_TEXTURE_2D,depth);
    glEnable(GL_CULL_FACE);glCullFace(GL_FRONT);glDepthFunc(GL_ALWAYS);glDepthMask(false);mesh.draw();
    ByteBuffer pixels=BufferUtils.createByteBuffer(W*H*4);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
    var output=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
    for(int y=0;y<H;y++)for(int x=0;x<W;x++){int at=(y*W+x)*4;output.setRGB(x,H-y-1,((pixels.get(at)&255)<<16)|((pixels.get(at+1)&255)<<8)|(pixels.get(at+2)&255));}
    Graphics2D g=output.createGraphics();g.setColor(new Color(0,0,0,190));g.fillRect(0,H-38,W,38);g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.PLAIN,16));g.drawString("OFFLINE GPU PREVIEW | Shared solid mesh + cloud shader | Not an in-game capture",18,H-13);g.dispose();
    Path out=Path.of("build/fx-offline/grand-ritual-"+shot+".png");ImageIO.write(output,"PNG",out.toFile());
    if(glGetError()!=GL_NO_ERROR) throw new IllegalStateException("GPU error");
    System.out.println("Rendered "+out);
   }
  }finally{glfwDestroyWindow(win);glfwTerminate();}
 }
}
