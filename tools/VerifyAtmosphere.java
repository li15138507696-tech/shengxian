import net.guzhenren.shengxian.core.AtmosphereColor;
public final class VerifyAtmosphere {
 public static void main(String[] args) {
  check(AtmosphereColor.weight(0,64)==1,"near");
  check(AtmosphereColor.weight(64,64)==0,"edge");
  check(AtmosphereColor.weight(128,64)==0,"outside");
  check(AtmosphereColor.weight(Double.NaN,64)==0,"invalid");
  float x=0; for(int i=0;i<200;i++) x=AtmosphereColor.approach(x,1);
  check(x>.99f && x<=1,"fade in");
  for(int i=0;i<200;i++) x=AtmosphereColor.approach(x,0);
  check(x<.001f && x>=0,"fade out");
  check(AtmosphereColor.mix(.7f,.1f,0)==.7f,"no change");
  check(AtmosphereColor.mix(.7f,.1f,1)==.1f,"full blend");
  System.out.println("PASS atmosphere: 8 distance, blending and fade checks");
 }
 static void check(boolean ok,String message){if(!ok) throw new AssertionError(message);}
}
