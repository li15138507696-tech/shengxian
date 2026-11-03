package net.guzhenren.shengxian.core;

/** Bounded, frame-rate independent atmosphere math. */
public final class AtmosphereColor {
 private AtmosphereColor() {}
 public static float weight(double distance,double radius) {
  if(!Double.isFinite(distance)||!Double.isFinite(radius)||radius<=0) return 0;
  float t=(float)Math.max(0,Math.min(1,1-distance/radius));
  return t*t*(3-2*t);
 }
 public static float approach(float value,float target) {
  return mix(value,target,target>value?.085f:.06f);
 }
 public static float mix(float a,float b,float t) {
  t=Float.isFinite(t)?Math.max(0,Math.min(1,t)):0;
  if(t==0) return a; if(t==1) return b;
  return a+(b-a)*t;
 }
}
