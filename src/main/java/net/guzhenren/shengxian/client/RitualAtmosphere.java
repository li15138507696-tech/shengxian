package net.guzhenren.shengxian.client;

import net.guzhenren.shengxian.ShengxianMod;
import net.guzhenren.shengxian.ShengxianVisualConfig;
import net.guzhenren.shengxian.core.AtmosphereColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client-only atmosphere accompanying Photon; never changes world weather or time. */
@EventBusSubscriber(modid=ShengxianMod.MOD_ID,value=Dist.CLIENT)
public final class RitualAtmosphere {
 private static ClientLevel world;
 private static float strength,red=.18f,green=.32f,blue=.46f;
 private RitualAtmosphere() {}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
  var mc=Minecraft.getInstance();
  if(world!=mc.level){world=mc.level;strength=0;}
  if(mc.level==null||mc.player==null||!ShengxianVisualConfig.enabled()||!ShengxianVisualConfig.cinematic()){
   strength=0;return;
  }
  float target=ClientAscensionEffects.atmosphereStrength();
  float r=.16f,g=.33f,b=.48f;
  // Strongest source wins: overlapping multiplayer rituals never accumulate brightness.
  for(var entity:mc.level.entitiesForRendering()){
   var type=CarrierTagReader.readType(entity);
   if(type==null) continue;
   float w=.85f*AtmosphereColor.weight(entity.distanceTo(mc.player),64);
   if(w<=target) continue;
   target=w;
   switch(type){
    case BLOOD_RAIN -> {r=.42f;g=.055f;b=.085f;}
    case SOUND_CHIMES -> {r=.18f;g=.14f;b=.36f;}
    case ANCIENT_BEAST -> {r=.36f;g=.23f;b=.09f;}
    default -> {r=.13f;g=.29f;b=.35f;}
   }
  }
  float cap=ShengxianVisualConfig.reduced()?.16f:.38f;
  strength=AtmosphereColor.approach(strength,Math.min(cap,target));
  red=AtmosphereColor.approach(red,r);green=AtmosphereColor.approach(green,g);blue=AtmosphereColor.approach(blue,b);
 }
 @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor event){
  if(strength<.002f||event.getCamera().getFluidInCamera()!=FogType.NONE) return;
  event.setRed(AtmosphereColor.mix(event.getRed(),red,strength*.42f));
  event.setGreen(AtmosphereColor.mix(event.getGreen(),green,strength*.42f));
  event.setBlue(AtmosphereColor.mix(event.getBlue(),blue,strength*.42f));
 }
 @SubscribeEvent public static void overlay(RenderGuiEvent.Pre event){
  var mc=Minecraft.getInstance();
  if(strength<.002f||!ShengxianVisualConfig.screen()||mc.screen!=null||mc.player==null
    ||mc.gameRenderer.getMainCamera().getFluidInCamera()!=FogType.NONE) return;
  int alpha=(int)(strength*8);
  int sky=(alpha<<24)|((int)(red*255)<<16)|((int)(green*255)<<8)|(int)(blue*255);
  int earth=((int)(alpha*.35f)<<24)|0xB58B48;
  var gui=event.getGuiGraphics();
  gui.fillGradient(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),sky,earth);
 }
}
