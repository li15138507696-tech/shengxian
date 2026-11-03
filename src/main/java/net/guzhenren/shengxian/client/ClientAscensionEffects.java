package net.guzhenren.shengxian.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.guzhenren.shengxian.ShengxianVisualConfig;
import net.guzhenren.shengxian.core.AscensionVisuals;
import net.guzhenren.shengxian.network.AscensionVisualPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Render-thread-only state, bounded even for a busy multiplayer scene. */
public final class ClientAscensionEffects {
    private record Source(UUID session, int entity, AscensionVisuals.Track track, float visionYaw) {}
    private static final Map<UUID,Source> SOURCES = new LinkedHashMap<>();
    private static final Map<UUID,Long> IMPACTS = new LinkedHashMap<>();
    private static final int MAX_SOURCES=16, MAX_RENDERED=8;
    private static ClientLevel world;
    private static long ticks;
    private static boolean fallback;
    private ClientAscensionEffects() {}

    public static void accept(AscensionVisualPayload p) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || !mc.level.dimension().location().equals(p.dimension())) return;
        ensureWorld(mc.level);
        if(!ShengxianVisualConfig.enabled()) return;
        Source previous=SOURCES.get(p.owner());
        if(p.stop()) {
            if(previous!=null && previous.session().equals(p.session())
                    && p.frame().revision()>=previous.track().frame().revision()) previous.track().stop(ticks);
            return;
        }
        if(previous!=null && previous.session().equals(p.session()) && previous.entity()==p.entityId()) {
            var oldPhase = previous.track().frame().phase();
            if(previous.track().update(p.frame(),ticks)) {
                previous.track().alignAge(p.phaseAgeTicks(),ticks);
                if(oldPhase != p.frame().phase() && p.frame().confirmedFormed()
                        && mc.level.getEntity(p.entityId()) instanceof Player actor)
                    SOURCES.put(p.owner(),new Source(p.session(),p.entityId(),previous.track(),actor.getYRot()));
                if(oldPhase != p.frame().phase() && mc.player != null && mc.player.getUUID().equals(p.owner()))
                    RitualSoundscape.transition(p.frame().phase());
            }
            return;
        }
        // New streams require a real matching player. Unknown/removed entities never grow the cache.
        if(!(mc.level.getEntity(p.entityId()) instanceof Player player) || !player.getUUID().equals(p.owner())) return;
        if(mc.player!=null && player.distanceToSqr(mc.player)>64*64) return;
        if(SOURCES.size()>=MAX_SOURCES && !SOURCES.containsKey(p.owner())) {
            UUID discard=SOURCES.keySet().stream().filter(id->mc.player==null || !id.equals(mc.player.getUUID()))
                    .findFirst().orElse(null);
            if(discard==null) return;
            SOURCES.remove(discard);
        }
        var track = new AscensionVisuals.Track(p.frame(),ticks);
        track.alignAge(p.phaseAgeTicks(),ticks);
        SOURCES.put(p.owner(),new Source(p.session(),p.entityId(),track,player.getYRot()));
        if(p.phaseAgeTicks()==0 && mc.player!=null && mc.player.getUUID().equals(p.owner()))
            RitualSoundscape.transition(p.frame().phase());
    }

    public record GrandSource(Vec3 position, float strength, int palette, boolean formed, float age, float yaw) {
        public GrandSource(Vec3 position,float strength,int palette) {this(position,strength,palette,false,0,0);}
    }
    public static java.util.List<GrandSource> grandSources(float partial) {
        var mc=Minecraft.getInstance();
        if(mc.level==null) return java.util.List.of();
        var sources=new java.util.ArrayList<GrandSource>();
        for(var s:SOURCES.values()) {
            var actor=mc.level.getEntity(s.entity());
            if(actor==null) continue;
            float stage=switch(s.track().frame().phase()) {
                case PREPARING -> .15f;
                case BREAKING, BROKEN -> .45f;
                case ABSORBING -> .75f;
                case OPENING, FORMING -> 1f;
                case IMMORTAL -> .85f;
                case FAILED -> .4f;
            };
            sources.add(new GrandSource(actor.getPosition(partial),stage*s.track().alpha(ticks+partial),
                    s.track().frame().calamityMask()!=0?2:0,s.track().frame().confirmedFormed(),
                    s.track().age(ticks+partial),s.visionYaw()));
        }
        return sources;
    }

    /** Nearby ritual pressure used by the shared Photon atmosphere. */
    public static float atmosphereStrength() {
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null) return 0;
        float strongest=0;
        for(var s:SOURCES.values()) {
            var actor=mc.level.getEntity(s.entity());
            if(actor==null) continue;
            float stage=switch(s.track().frame().phase()) {
                case PREPARING -> .16f;
                case BREAKING, BROKEN -> .48f;
                case ABSORBING -> .70f;
                case OPENING, FORMING -> .92f;
                case IMMORTAL -> .60f;
                case FAILED -> .35f;
            };
            float distance=net.guzhenren.shengxian.core.AtmosphereColor.weight(actor.distanceTo(mc.player),64);
            strongest=Math.max(strongest,stage*distance*s.track().alpha(ticks));
        }
        return strongest;
    }

    public static boolean has(UUID owner) { return SOURCES.containsKey(owner); }
    public static void impact(int entity) {
        for(var entry:SOURCES.entrySet()) if(entry.getValue().entity()==entity) IMPACTS.put(entry.getKey(),ticks);
    }

    public static void tick() {
        ticks++;
        Minecraft mc=Minecraft.getInstance();
        ensureWorld(mc.level);
        if(mc.level==null || !ShengxianVisualConfig.enabled()) { clear(); return; }
        SOURCES.entrySet().removeIf(e -> {
            Source source=e.getValue();
            var entity=mc.level.getEntity(source.entity());
            return source.track().expired(ticks) || !(entity instanceof Player)
                    || !entity.getUUID().equals(e.getKey())
                    || (mc.player!=null && entity.distanceToSqr(mc.player)>72*72);
        });
        IMPACTS.entrySet().removeIf(e -> !SOURCES.containsKey(e.getKey()) || ticks-e.getValue()>24);
        if(SOURCES.isEmpty()) { VeilAscensionPostRenderer.clear(); if(!ShengxianVisualConfig.cinematic()) VeilAscensionCloudRenderer.clear(); }
    }

    public static void renderWorld(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || !ShengxianVisualConfig.enabled()) return;
        if(ShengxianVisualConfig.cinematic()) { VeilAscensionPostRenderer.clear(); return; }
        if(SOURCES.isEmpty()) { VeilAscensionPostRenderer.clear();VeilAscensionCloudRenderer.clear(); return; }
        VeilAscensionCloudRenderer.beginFrame(event.getProjectionMatrix());
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time=ticks+partial;
        Vec3 camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();
        var type=VeilSanqiAuraRenderer.renderType();
        net.minecraft.client.renderer.RenderType visionType=null;
        boolean drewVision=false;
        int maximum=ShengxianVisualConfig.lowPerformance()?2:MAX_RENDERED;
        double distanceLimit=ShengxianVisualConfig.lowPerformance()?32*32:48*48;
        int rendered=0, cloudCount=0;
        // Nearby sources first, so the limit never hides the local ascender.
        var ordered=SOURCES.entrySet().stream().sorted(java.util.Comparator.<Map.Entry<UUID,Source>>comparingInt(e -> mc.player != null && e.getKey().equals(mc.player.getUUID()) ? 0 : 1).thenComparingDouble(e -> {
            var entity=mc.level.getEntity(e.getValue().entity());
            return entity==null?Double.MAX_VALUE:entity.distanceToSqr(camera);
        })).toList();
        for(var entry:ordered) {
            Source s=entry.getValue();
            if(!(mc.level.getEntity(s.entity()) instanceof Player player) || !player.getUUID().equals(entry.getKey())) continue;
            if(player.distanceToSqr(camera)>distanceLimit || rendered++>=maximum) continue;
            var track=s.track();
            float fade=track.alpha(time);
            if(fade<=0) continue;
            Vec3 position=player.getPosition(partial);
            var stack=event.getPoseStack();
            stack.pushPose();
            try {
                stack.translate(position.x-camera.x,position.y-camera.y,position.z-camera.z);
                VeilSanqiAuraRenderer.render(buffers.getBuffer(type),stack.last(),track.frame(),
                        fade,track.age(time),track.shatterAlpha(time),time,camera.subtract(position));
                Long impact=IMPACTS.get(entry.getKey());
                if(impact!=null) net.guzhenren.shengxian.core.AscensionAuraMesh.impact((float)(time-impact),fade,time,
                        camera.x-position.x,camera.y-position.y,camera.z-position.z,ShengxianVisualConfig.segments(),
                        (x,y,z,u,v,r,g,b,a,nx,ny,nz)->buffers.getBuffer(type).addVertex(stack.last(),x,y,z)
                                .setColor(r,g,b,a).setUv(u,v).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                                .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).setNormal(stack.last(),nx,ny,nz));
                float cloudStrength=AscensionVisuals.cloudStrength(track.frame(),track.age(time));
                if(cloudStrength>0 && cloudCount<2 && ShengxianVisualConfig.cinematic()
                        && !ShengxianVisualConfig.lowPerformance() && player.distanceToSqr(camera)<32*32) {
                    // Flush the source-local volume before another actor changes its uniforms.
                    buffers.endBatch(type);
                    VeilAscensionCloudRenderer.render(buffers,stack.last(),cloudStrength*fade,
                            track.frame().calamityMask()!=0?1:0,time,camera.subtract(position));
                    cloudCount++;
                }
                if(track.frame().confirmedFormed() && ShengxianVisualConfig.cinematic() && !ShengxianVisualConfig.lowPerformance()
                        && player.distanceToSqr(camera)<32*32) {
                    if(visionType==null) visionType=VeilApertureVisionRenderer.renderType();
                    if(visionType!=null) {
                        VeilApertureVisionRenderer.render(buffers.getBuffer(visionType),stack.last(),Math.max(0,track.age(time)-24),
                                fade,time,s.visionYaw()+(player==mc.player && mc.options.getCameraType().isFirstPerson()?180:0),camera.subtract(position));
                        drewVision=true;
                    }
                }
            } finally { stack.popPose(); }
        }
        if(rendered>0) buffers.endBatch(type);
        if(drewVision && visionType!=null) buffers.endBatch(visionType);
        VeilAscensionCloudRenderer.endFrame();
        Source self=mc.player==null?null:SOURCES.get(mc.player.getUUID());
        if(self==null || !ShengxianVisualConfig.screen() || VeilApertureTransitionRenderer.isRunning()) {
            VeilAscensionPostRenderer.clear();
            fallback=false;
        } else {
            var t=self.track();
            Long impact=IMPACTS.get(mc.player.getUUID());
            float shock=impact==null?0:net.guzhenren.shengxian.core.AscensionVisuals.unit(1-(time-impact)/24);
            fallback=!VeilAscensionPostRenderer.update(t.frame(),t.alpha(time),t.age(time),time,shock);
        }
    }

    public static void renderFallback(GuiGraphics g) {
        if(!fallback || !ShengxianVisualConfig.enabled() || !ShengxianVisualConfig.screen()) return;
        var mc=Minecraft.getInstance();
        if(mc.player==null) return;
        Source s=SOURCES.get(mc.player.getUUID());
        if(s==null) return;
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        int alpha=(int)(s.track().alpha(ticks)*(ShengxianVisualConfig.reduced()?12:24));
        int color=(alpha<<24)|0x718A7C;
        int band=Math.max(2,h/18);
        g.fill(0,0,w,band,color); g.fill(0,h-band,w,h,color);
        g.fill(0,band,band,h-band,color); g.fill(w-band,band,w,h-band,color);
    }

    private static void ensureWorld(ClientLevel current) {
        if(world!=current) { clear(); world=current; }
    }
    public static void clear() {
        SOURCES.clear(); IMPACTS.clear(); fallback=false;VeilAscensionCloudRenderer.clear();
        VeilAscensionPostRenderer.clear();
    }
}
