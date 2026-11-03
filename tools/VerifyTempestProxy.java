import net.guzhenren.shengxian.core.TempestVolumeMesh;
public final class VerifyTempestProxy {
 public static void main(String[] args){
  int[] count={0};
  TempestVolumeMesh.draw(1,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->{
   if(!Float.isFinite(x+y+z+u+v+a+nx+ny+nz)||Math.abs(x)>104||Math.abs(z)>104||y<0||y>130)throw new AssertionError("proxy bounds");
   if(Math.abs(nx*nx+ny*ny+nz*nz-1)>.001||a!=1)throw new AssertionError("normal or fade");
   count[0]++;
  });
  if(count[0]!=24)throw new AssertionError("six quad faces");
  for(float fade:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})
   TempestVolumeMesh.draw(fade,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->{throw new AssertionError("invalid fade emits geometry");});
  System.out.println("PASS tempest proxy: six faces, expanded bounds, unit normals, invalid fades suppressed");
 }
}
