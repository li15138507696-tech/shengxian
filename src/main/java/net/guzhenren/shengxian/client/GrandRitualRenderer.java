package net.guzhenren.shengxian.client;

import net.guzhenren.shengxian.ShengxianMod;
import net.guzhenren.shengxian.ShengxianVisualConfig;
import net.guzhenren.shengxian.core.AtmosphereColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

/** Turbulent self-shadowed storm fronts with volumetric qi, no hard tubes or sprite particles. */
@EventBusSubscriber(modid=ShengxianMod.MOD_ID,value=Dist.CLIENT)
public final class GrandRitualRenderer {
 private static final Map<UUID,Presence> carriers=new LinkedHashMap<>();
 private static ClientLevel world;
 private static Vec3 preview;
 private static long previewUntil;
 private static final RenderType ORNATE_MATERIAL=RenderType.entityTranslucent(
   ResourceLocation.fromNamespaceAndPath("shengxian","textures/effect/ornate_gilt.png"));
 private static class Presence {
  Vec3 position; int palette; float alpha; boolean seen;
  Presence(Vec3 p,int c){position=p;palette=c;}
 }
 private GrandRitualRenderer() {}
 @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
  event.getDispatcher().register(Commands.literal("shengxiangrand")
   .then(Commands.literal("show").executes(c->{
    var mc=Minecraft.getInstance(); if(mc.player==null||mc.level==null) return 0;
    world=mc.level;
    preview=mc.player.position().add(Vec3.directionFromRotation(0,mc.player.getYRot()).scale(80));
    if(mc.level.hasChunkAt(net.minecraft.core.BlockPos.containing(preview)))
     preview=new Vec3(preview.x,mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
       net.minecraft.util.Mth.floor(preview.x),net.minecraft.util.Mth.floor(preview.z)),preview.z);
    previewUntil=mc.level.getGameTime()+3600;
    c.getSource().sendSuccess(()->Component.literal("三维天地异象预览：前方80格，持续180秒；/shengxiangrand clear 清除。仅本地视觉。"),false);
    return 1;
   }))
   .then(Commands.literal("clear").executes(c->{preview=null;return 1;})));
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  var mc=Minecraft.getInstance();
  if(world!=mc.level){world=mc.level;carriers.clear();preview=null;}
  if(mc.level==null||mc.player==null||!ShengxianVisualConfig.enabled()||!ShengxianVisualConfig.cinematic()){
   carriers.clear();preview=null;return;
  }
  if(mc.level.getGameTime()>previewUntil) preview=null;
  carriers.values().forEach(p->p.seen=false);
  var nearby=new ArrayList<net.minecraft.world.entity.Entity>();
  for(var entity:mc.level.entitiesForRendering())
   if(entity.distanceToSqr(mc.player)<96*96&&CarrierTagReader.readType(entity)!=null) nearby.add(entity);
  nearby.sort(Comparator.comparingDouble(e->e.distanceToSqr(mc.player)));
  for(var entity:nearby.subList(0,Math.min(2,nearby.size()))){
   var type=CarrierTagReader.readType(entity);
   int palette=type==net.guzhenren.shengxian.core.AscensionProgress.Tribulation.Type.BLOOD_RAIN?1:0;
   var p=carriers.computeIfAbsent(entity.getUUID(),id->new Presence(entity.position(),palette));
   p.position=entity.position();p.palette=palette;p.seen=true;
  }
  carriers.values().forEach(p->p.alpha=AtmosphereColor.approach(p.alpha,p.seen?1:0));
  carriers.values().removeIf(p->!p.seen&&p.alpha<.003f);
 }
 private static boolean depthReady;
 @SubscribeEvent public static void render(RenderLevelStageEvent event){
  boolean capture=event.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES;
  if(!capture&&event.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
  var mc=Minecraft.getInstance();
  if(mc.level==null||mc.player==null||!ShengxianVisualConfig.enabled()||!ShengxianVisualConfig.cinematic()) return;
  float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
  double time=mc.level.getGameTime()+partial;
  Vec3 camera=event.getCamera().getPosition();
  var sources=new ArrayList<>(ClientAscensionEffects.grandSources(partial));
  for(var p:carriers.values()) {
   boolean duplicate=sources.stream().anyMatch(s->s.position().distanceToSqr(p.position)<12*12);
   if(!duplicate) sources.add(new ClientAscensionEffects.GrandSource(p.position,p.alpha,p.palette));
  }
  if(preview!=null) {sources.clear();sources.add(new ClientAscensionEffects.GrandSource(preview,1,0));}
  sources.removeIf(s->s.strength()<.003f||s.position().distanceToSqr(camera)>128*128);
  sources.sort(Comparator.comparingDouble(s->s.position().distanceToSqr(camera)));
  int limit=ShengxianVisualConfig.lowPerformance()?1:2;
  if(sources.size()>limit) sources.subList(limit,sources.size()).clear();
  if(sources.isEmpty()) {depthReady=false;VeilAscensionCloudRenderer.clear();return;}
  // Vanilla clouds render AFTER_PARTICLES. Retain terrain depth first, then composite
  // the volume after weather so opaque-looking cloud proxy quads cannot cut holes in it.
  if(capture) {
   VeilAscensionCloudRenderer.beginFrame(event.getProjectionMatrix());
   depthReady=VeilAscensionCloudRenderer.captureOpaqueDepth();return;
  }
  if(!depthReady)return;
  depthReady=false;
  var buffers=mc.renderBuffers().bufferSource();
  for(int i=sources.size()-1;i>=0;i--){
   var source=sources.get(i);
   var stack=event.getPoseStack();stack.pushPose();
   try {
    stack.translate(source.position().x-camera.x,source.position().y-camera.y,source.position().z-camera.z);
    if(source.formed()&&!ShengxianVisualConfig.lowPerformance()) {
     var visionType=VeilApertureVisionRenderer.renderType();
     if(visionType!=null) {
      VeilApertureVisionRenderer.render(buffers.getBuffer(visionType),stack.last(),
       Math.max(0,source.age()-24),source.strength(),time,source.yaw(),camera.subtract(source.position()));
      buffers.endBatch(visionType);
     }
    }
    if(i==0) {
     VeilAscensionCloudRenderer.renderTempest(buffers,stack.last(),
      source.strength(),source.palette()==1?1:0,time,camera.subtract(source.position()));
     // The gilt canopy reads well from afar but fragments badly when the camera is
     // inside it. Fade the solid structure out as the player approaches the source,
     // leaving only the soft volume close-up.
     double dist=camera.distanceTo(source.position());
     float structureAlpha=source.strength()*
       net.minecraft.util.Mth.clamp((float)((dist-24.0)/24.0),0f,1f);
     if(structureAlpha>.01f) {
      var out=buffers.getBuffer(ORNATE_MATERIAL);
      net.guzhenren.shengxian.core.OrnateStructureMesh.draw(time,structureAlpha,
       ShengxianVisualConfig.lowPerformance(),source.palette(),(x,y,z,u,v,r,g,b,a,nx,ny,nz)->
        out.addVertex(stack.last(),x,y,z).setColor(r,g,b,a)
         .setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
         .setNormal(stack.last(),nx,ny,nz));
      buffers.endBatch(ORNATE_MATERIAL);
     }
    }
   } finally {stack.popPose();}
  }
  VeilAscensionCloudRenderer.endFrame();
 }
}