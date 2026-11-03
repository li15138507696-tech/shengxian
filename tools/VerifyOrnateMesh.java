import net.guzhenren.shengxian.core.OrnateStructureMesh;
public final class VerifyOrnateMesh {
 public static void main(String[] args) {
  int[] highCount={0},lowCount={0};
  // 高细节测试：有限值、法线单位、边界、UV、颜色
  OrnateStructureMesh.draw(280,1,false,0,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->{
   if(!Float.isFinite(x+y+z+u+v+r+g+b+a+nx+ny+nz))throw new AssertionError("non-finite vertex");
   if(Math.abs(x)>78||Math.abs(z)>78)throw new AssertionError("xz out of bounds: "+x+","+z);
   if(y<0||y>130)throw new AssertionError("y out of bounds: "+y);
   if(u<0||u>1||v<0||v>1)throw new AssertionError("UV out of range: "+u+","+v);
   double nl=Math.sqrt(nx*nx+ny*ny+nz*nz);
   if(Math.abs(nl-1)>.01)throw new AssertionError("normal not unit: "+nl);
   if(a<=0||a>1)throw new AssertionError("alpha out of range: "+a);
   if(r<0||g<0||b<0)throw new AssertionError("negative color");
   highCount[0]++;
  });
  // 低细节： fewer vertices
  OrnateStructureMesh.draw(280,1,true,0,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->lowCount[0]++);
  if(lowCount[0]>=highCount[0])throw new AssertionError("low should have fewer vertices");
  // 强度为零无输出
  OrnateStructureMesh.draw(280,0,false,0,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->{throw new AssertionError("zero strength emitted");});
  // 非有限强度无输出
  for(float s:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY})
   OrnateStructureMesh.draw(280,s,false,0,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->{throw new AssertionError("nonfinite strength emitted");});
  // 强度大于1被钳制
  final boolean[] emitted={false};
  OrnateStructureMesh.draw(280,3.0f,false,0,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->emitted[0]=true);
  if(!emitted[0])throw new AssertionError("strength>1 should clamp to 1 and emit");
  // 三种调色板均有输出
  for(int p=0;p<3;p++) {
   final int count[]={0};
   OrnateStructureMesh.draw(280,1,false,p,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->count[0]++);
   if(count[0]<50)throw new AssertionError("palette "+p+" too few vertices: "+count[0]);
  }
  // 确定性
  final String[] hashes=new String[2];
  for(int run=0;run<2;run++) {
   StringBuilder sb=new StringBuilder();
   OrnateStructureMesh.draw(280,1,false,0,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->
    sb.append(x).append(y).append(z).append(u).append(v).append(r).append(g).append(b).append(a));
   hashes[run]=sb.toString();
  }
  if(!hashes[0].equals(hashes[1]))throw new AssertionError("non-deterministic output");
  // 极端时间戳
  for(double t:new double[]{0,-1e10,1e10,Double.NaN})
   OrnateStructureMesh.draw(t,1,false,0,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->{
    if(!Float.isFinite(x+y+z))throw new AssertionError("extreme time produced nonfinite");
   });
  System.out.println("PASS ornate mesh: high="+highCount[0]+" low="+lowCount[0]+" vertices; "
   +"finite, unit normals, bounds, UV, palettes, clamps, zero-strength, determinism, extreme time");
 }
}
