package net.guzhenren.shengxian.core;

/** Outward-wound proxy only. Visible shapes come entirely from volume density. */
public final class TempestVolumeMesh {
 private TempestVolumeMesh() {}
 public static void draw(float alpha,AscensionAuraMesh.Sink sink) {
  if(!Float.isFinite(alpha)||alpha<=0) return;
  alpha=Math.min(1,alpha);
  face(sink,alpha,0,-1,0,-104,0,-104,104,0,-104,104,0,104,-104,0,104);
  face(sink,alpha,0,1,0,-104,130,104,104,130,104,104,130,-104,-104,130,-104);
  face(sink,alpha,-1,0,0,-104,0,-104,-104,0,104,-104,130,104,-104,130,-104);
  face(sink,alpha,1,0,0,104,0,104,104,0,-104,104,130,-104,104,130,104);
  face(sink,alpha,0,0,1,-104,0,104,104,0,104,104,130,104,-104,130,104);
  face(sink,alpha,0,0,-1,104,0,-104,-104,0,-104,-104,130,-104,104,130,-104);
 }
 private static void face(AscensionAuraMesh.Sink s,float a,float nx,float ny,float nz,float... p){
  for(int i=0;i<4;i++)s.vertex(p[i*3],p[i*3+1],p[i*3+2],i==1||i==2?1:0,i>=2?1:0,1,1,1,a,nx,ny,nz);
 }
}