package net.guzhenren.shengxian.core;

/**
 * Solid structural ornament layer for the grand ritual sky: layered petal canopy,
 * hanging qi curtains, halo rings and a ground array ring. Double-sided quads only;
 * visible shapes come from real geometry, not sprite particles.
 */
public final class OrnateStructureMesh {
 private OrnateStructureMesh() {}
 private static final double TAU=Math.PI*2;
 public static void draw(double ticks,float strength,boolean low,int palette,AscensionAuraMesh.Sink sink) {
  if(!Float.isFinite(strength)||strength<=0.001f) return;
  strength=Math.min(1f,Math.max(0f,strength));
  if(!Double.isFinite(ticks)) ticks=0;
  double t=ticks%1048576.0/20.0;
  float[][] colors=paletteColors(palette);
  Faces f=new Faces(sink,strength,colors,low);
  canopy(f,t,low);
  curtains(f,t,low);
  haloRings(f,t);
  groundArray(f,t,low);
 }
 private static float[][] paletteColors(int palette) {
  return switch(palette) {
   case 1->new float[][]{{.62f,.14f,.18f},{.85f,.30f,.22f},{.95f,.72f,.35f},{.35f,.06f,.08f}};
   case 2->new float[][]{{.30f,.14f,.50f},{.55f,.30f,.85f},{.90f,.72f,.30f},{.12f,.05f,.22f}};
   default->new float[][]{{.30f,.62f,.66f},{.45f,.82f,.66f},{.95f,.75f,.34f},{.12f,.24f,.28f}};
  };
 }
 private static void canopy(Faces f,double t,boolean low) {
  int seg=low?20:36;
  float[] radii={46,56,66};
  float[] heights={86,98,110};
  for(int layer=0;layer<3;layer++) {
   float rad=radii[layer],y=heights[layer];
   float thick=1.8f-layer*0.3f;
   for(int i=0;i<seg;i++) {
    double a0=TAU*i/seg+t*0.008*(layer%2==0?1:-1);
    double a1=TAU*(i+1)/seg+t*0.008*(layer%2==0?1:-1);
    double mid=(a0+a1)/2;
    // 波浪外缘：每个环层外半径随角度起伏
    double swell=rad+3.5*Math.sin(mid*6+layer*2.1)+1.8*Math.sin(mid*13+layer*4.3);
    double inR=rad*0.42;
    double[] in0=pt(a0,inR,y-thick*0.5);
    double[] in1=pt(a1,inR,y-thick*0.5);
    double[] out0=pt(a0,swell,y+thick*0.5);
    double[] out1=pt(a1,swell,y+thick*0.5);
    // 环面顶底双面
    f.quad(in0,in1,out1,out0,layer%2==0?0:1,.78f+.20f*(float)Math.sin(mid*3+layer*2));
    f.quad(in0,out0,out1,in1,layer%2==0?0:1,.78f+.20f*(float)Math.sin(mid*3+layer*2));
    // 外缘竖面
    double[] e0=pt(a0,swell,y-thick*0.5);
    double[] e1=pt(a1,swell,y-thick*0.5);
    f.quad(out0,e0,e1,out1,2,.88f);
   }
  }
 }
 private static void curtains(Faces f,double t,boolean low) {
  int n=low?4:8;
  for(int i=0;i<n;i++) {
   double phase=TAU*i/n;
   // 螺旋垂落飘带：从 y=88 到 y=4
   for(int seg=0;seg<10;seg++) {
    double h0=84.0-80.0*seg/10,h1=84.0-80.0*(seg+1)/10;
    double s0=(double)seg/10,s1=(double)(seg+1)/10;
    // 螺旋半径随高度变化
    double r0=24+16*Math.sin(s0*Math.PI)+3.5*Math.sin(phase*3+t*.02+s0*4);
    double r1=24+16*Math.sin(s1*Math.PI)+3.5*Math.sin(phase*3+t*.02+s1*4);
    double a0=phase+s0*2.2+t*.01;
    double a1=phase+s1*2.2+t*.01;
    double sw0=Math.sin(s0*9+phase*2)*3.2;
    double sw1=Math.sin(s1*9+phase*2)*3.2;
    double[] v00=pt(a0,r0-sw0,h0),v01=pt(a0,r0+sw0,h0);
    double[] v10=pt(a1,r1-sw1,h1),v11=pt(a1,r1+sw1,h1);
    f.quad(v00,v01,v11,v10,(i+seg)%3,.55f+.35f*(float)Math.sin(s0*7+phase*3));
    f.quad(v00,v10,v11,v01,(i+seg)%3,.55f+.35f*(float)Math.sin(s0*7+phase*3));
   }
  }
 }
 private static void haloRings(Faces f,double t) {
  float[] radii={15,22};
  float[] heights={88,90};
  for(int k=0;k<2;k++) {
   int seg=24;
   float rad=radii[k],y=heights[k];
   for(int i=0;i<seg;i++) {
    double a0=TAU*i/seg+t*.02;
    double a1=TAU*(i+1)/seg+t*.02;
    double mid=(a0+a1)/2;
    float thick=1.0f;
    double[] in0=pt(a0,rad-thick,y-thick*.4),in1=pt(a1,rad-thick,y-thick*.4);
    double[] out0=pt(a0,rad+thick,y+thick*.4),out1=pt(a1,rad+thick,y+thick*.4);
    f.quad(in0,in1,out1,out0,2,.90f);
    f.quad(in0,out0,out1,in1,2,.90f);
   }
  }
 }
 private static void groundArray(Faces f,double t,boolean low) {
  int seg=low?20:32;
  float[] radii={26,36};
  for(int k=0;k<2;k++) {
   float rad=radii[k],y=1.2f+k*.8f;
   for(int i=0;i<seg;i++) {
    double a0=TAU*i/seg+t*.006*(k%2==0?1:-1);
    double a1=TAU*(i+1)/seg+t*.006*(k%2==0?1:-1);
    float thick=1.4f;
    double[] in0=pt(a0,rad-thick,y),in1=pt(a1,rad-thick,y);
    double[] out0=pt(a0,rad+thick,y),out1=pt(a1,rad+thick,y);
    f.quad(in0,in1,out1,out0,3,.62f+.30f*(float)Math.sin(a0*4));
    f.quad(in0,out0,out1,in1,3,.62f+.30f*(float)Math.sin(a0*4));
   }
  }
 }
 private static double[] pt(double a,double r,double y) {
  return new double[]{r*Math.cos(a),y,r*Math.sin(a)};
 }
 private static final class Faces {
  private final AscensionAuraMesh.Sink sink;
  private final float alpha;
  private final float[][] colors;
  private final boolean low;
  Faces(AscensionAuraMesh.Sink sink,float alpha,float[][] colors,boolean low) {
   this.sink=sink;this.alpha=alpha;this.colors=colors;this.low=low;
  }
  void quad(double[] a,double[] b,double[] c,double[] d,int ci,float shade) {
   // 计算面法线（叉积）
   double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
   double vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
   double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
   double len=Math.sqrt(nx*nx+ny*ny+nz*nz);
   if(len<1e-8){nx=0;ny=1;nz=0;len=1;}
   nx/=len;ny/=len;nz/=len;
   float r=colors[ci][0]*shade,g=colors[ci][1]*shade,bl=colors[ci][2]*shade;
   float[] uv={0,0,1,0,1,1,0,1};
   double[][] pts={a,b,c,d};
   for(int i=0;i<4;i++) {
    sink.vertex((float)pts[i][0],(float)pts[i][1],(float)pts[i][2],
     uv[i*2],uv[i*2+1],r,g,bl,alpha,(float)nx,(float)ny,(float)nz);
   }
  }
 }
}
