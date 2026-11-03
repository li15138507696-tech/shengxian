package net.guzhenren.shengxian;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.guzhenren.shengxian.core.AscensionReadiness;
import net.guzhenren.shengxian.core.CalamityCombat;
import net.guzhenren.shengxian.core.CalamityCarrierStats;
import net.guzhenren.shengxian.core.CalamityDamage;
import net.guzhenren.shengxian.core.XianGuForcedUse;
import net.guzhenren.shengxian.core.LeftoverRefinement;
import net.guzhenren.shengxian.core.InnerTribulation;
import net.guzhenren.shengxian.core.AscensionCloudMesh;
import net.guzhenren.shengxian.core.AscensionVisuals;
import net.guzhenren.shengxian.core.AperturePlotBounds;
import net.guzhenren.shengxian.core.AperturePlotPolicy;
import net.guzhenren.shengxian.core.ApertureEngine;
import net.guzhenren.shengxian.core.ApertureFormationPlanner;
import net.guzhenren.shengxian.core.ApertureKey;
import net.guzhenren.shengxian.core.ApertureSpaceStore;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.guzhenren.shengxian.core.AscensionRecord;
import net.guzhenren.shengxian.core.AscensionService;
import net.guzhenren.shengxian.core.AscensionSession;
import net.guzhenren.shengxian.core.BenMingGuStore;
import net.guzhenren.shengxian.core.FileSessionRepository;
import net.guzhenren.shengxian.core.FailureSettlementIdentity;
import net.guzhenren.shengxian.core.FailureSettlementDiagnostic;
import net.guzhenren.shengxian.core.FailureSettlementPolicy;
import net.guzhenren.shengxian.core.GuActivation;
import net.guzhenren.shengxian.core.GuActivationLedger;
import net.guzhenren.shengxian.core.GuActivationPolicy;
import net.guzhenren.shengxian.core.GuBacklashState;
import net.guzhenren.shengxian.core.GuBacklashTargetLocator;
import net.guzhenren.shengxian.core.KillerMoveDurabilityChanges;
import net.guzhenren.shengxian.core.OrdinaryQualification;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.core.QiEngine;
import net.guzhenren.shengxian.core.QiIntent;
import net.guzhenren.shengxian.core.QiParameters;
import net.guzhenren.shengxian.core.RuleProfile;
import net.guzhenren.shengxian.core.RankSixSyncDecision;
import net.guzhenren.shengxian.core.SessionFlow;
import net.guzhenren.shengxian.core.SessionStore;
import net.guzhenren.shengxian.core.ShatterClock;
import net.guzhenren.shengxian.core.ShatterEssenceTransaction;
import net.guzhenren.shengxian.core.ShatterEssenceReconciler;
import net.guzhenren.shengxian.core.StartGate;
import net.guzhenren.shengxian.core.TribulationFx;
import net.guzhenren.shengxian.core.TribulationEngine;
import net.guzhenren.shengxian.core.TribulationParameters;
import net.guzhenren.shengxian.core.UpstreamFingerprint;
import net.guzhenren.shengxian.core.XianGu;
import net.guzhenren.shengxian.core.XianGuCatalog;
import net.guzhenren.shengxian.core.XianGuRuntime;
import net.guzhenren.shengxian.core.XianGuStateCodec;
import net.guzhenren.shengxian.core.XianGuOperations;
import net.guzhenren.shengxian.core.XianGuLedger;

@SuppressWarnings({"unchecked","rawtypes"})
public final class FrameworkTest {
   private static int passed;
   private static int failed;
   private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
   private static final Set<RuleProfile.Capability> ALL = Set.of(RuleProfile.Capability.values());

   private static net.guzhenren.shengxian.core.AscensionVisuals.Frame visualFrame(Phase phase, long revision) {
      return new net.guzhenren.shengxian.core.AscensionVisuals.Frame(phase, revision, 0.5f, 0.8f, 1, 1, 1, 0, false);
   }

   public FrameworkTest() {
   }

   public static void main(String[] var0) throws Exception {
      test("cloud geometry has six outward faces and obeys committed visual phases", () -> {
          int[] n={0}; float[] q=new float[9];
          AscensionCloudMesh.draw(1,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->{
              check(Float.isFinite(x+y+z+a));
              check(Math.abs(x)<=10 && Math.abs(z)<=10 && y>=10 && y<=16);
              int at=n[0]%4;
              if(at<3){q[at*3]=x;q[at*3+1]=y;q[at*3+2]=z;}
              if(at==2){
                  float ax=q[3]-q[0],ay=q[4]-q[1],az=q[5]-q[2];
                  float bx=q[6]-q[0],by=q[7]-q[1],bz=q[8]-q[2];
                  check((ay*bz-az*by)*nx+(az*bx-ax*bz)*ny+(ax*by-ay*bx)*nz>0);
              }
              n[0]++;
          });
          check(n[0]==24);
          AscensionCloudMesh.draw(0,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->{throw new AssertionError("disabled cloud");});
          var absorb=new AscensionVisuals.Frame(Phase.ABSORBING,1,0,1,1,1,1,0,false,0,false);
          check(AscensionVisuals.cloudStrength(absorb,50)==0);
          check(AscensionVisuals.cloudStrength(absorb,260)>.7f);
          var empty=new AscensionVisuals.Frame(Phase.ABSORBING,1,0,1,0,1,1,0,false,0,false);
          check(AscensionVisuals.cloudStrength(empty,260)==0);
          var failed=new AscensionVisuals.Frame(Phase.FAILED,1,1,1,1,1,1,0,false,0,false);
          check(AscensionVisuals.cloudStrength(failed,1)==0);
      });
      test("cinematic switch removes sky-scale geometry independently of motion setting", () -> {
         float[] maxY={-100}; int[] count={0};
         net.guzhenren.shengxian.core.AscensionAuraMesh.draw(visualFrame(Phase.ABSORBING,3),340,0,1,340,
                 8,5,12,48,false,false,(x,y,z,u,v,r,g,b,a,nx,ny,nz)->{
                    count[0]++;maxY[0]=Math.max(maxY[0],y);
                 });
         check(count[0]>0&&maxY[0]<6);
      });

      test("aperture vision has bounded layers real depth and no output when disabled", () -> {
         int[] count={0}, off={0}; Set<Integer> layers=new HashSet<>();
         float[] depths={Float.MAX_VALUE,-Float.MAX_VALUE};
         net.guzhenren.shengxian.core.ApertureVisionMesh.draw(80,1,340,0,7.5,4.4,10.5,
                 (x,y,z,u,v,r,g,b,a,nx,ny,nz)->{
                    count[0]++; layers.add((int)Math.floor(u));
                    for(float value:new float[]{x,y,z,u,v,r,g,b,a,nx,ny,nz}) check(Float.isFinite(value));
                    depths[0]=Math.min(depths[0],z); depths[1]=Math.max(depths[1],z);
                    check(a>=0&&a<=1&&u>=0&&u<7&&v>=0&&v<=1);
                 });
         net.guzhenren.shengxian.core.ApertureVisionMesh.draw(80,0,340,0,0,1,0,
                 (x,y,z,u,v,r,g,b,a,nx,ny,nz)->off[0]++);
         check(count[0]>0&&count[0]<5000&&count[0]%4==0&&layers.size()==7);
         check(depths[1]-depths[0]>2&&off[0]==0);
      });

      test("ritual metadata uses committed calamities and requires formed success", () -> {
         var p=AscensionProgress.empty().withTribulations(List.of(
                 new AscensionProgress.Tribulation("wind", AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND,8,20),
                 new AscensionProgress.Tribulation("beast", AscensionProgress.Tribulation.Type.ANCIENT_BEAST,3,40)));
         var f=net.guzhenren.shengxian.core.AscensionVisuals.from(
                 new AscensionRecord(session(Phase.ABSORBING,true,3,3,3),p),100);
         check(f.calamityMask()==5 && !f.confirmedFormed());
         var unformed=net.guzhenren.shengxian.core.AscensionVisuals.from(
                 new AscensionRecord(session(Phase.IMMORTAL,true,6,6,6),AscensionProgress.empty()),100);
         check(!unformed.confirmedFormed());
      });
      test("ritual visual age catches up from authoritative phase heartbeat", () -> {
         var t=new net.guzhenren.shengxian.core.AscensionVisuals.Track(visualFrame(Phase.ABSORBING,4),50);
         t.alignAge(180,50);
         check(t.age(50)==180 && t.age(60)==190);
         t.alignAge(100,60);
         check(t.age(60)==190);
      });

      test("rich ascension mesh remains finite at camera singularities and within vertex budget", () -> {
         for (Phase phase : Phase.values()) for (int segments : new int[]{12, 48}) {
            int[] count = {0};
            net.guzhenren.shengxian.core.AscensionAuraMesh.draw(visualFrame(phase, 10),
                    35, 0.5f, 1, 180, 0, 1, 0, segments, false,
                    (x,y,z,u,v,r,g,b,a,nx,ny,nz) -> {
                       count[0]++;
                       for(float n : new float[]{x,y,z,u,v,r,g,b,a,nx,ny,nz}) check(Float.isFinite(n));
                       check(Math.abs(x)<16 && y>-5 && y<20 && Math.abs(z)<16);
                       check(a>=0 && a<=1 && u>=0 && u<=1 && v>=0 && v<=1);
                    });
            check(count[0]>0 && count[0]<40000 && count[0]%4==0);
         }
      });
      test("rich ascension mesh turns off completely and reduced mode limits geometry", () -> {
         int[] off={0}, normal={0}, reduced={0};
         var f=visualFrame(Phase.ABSORBING, 3);
         net.guzhenren.shengxian.core.AscensionAuraMesh.draw(f,50,0,0,200,8,5,12,32,false,
                 (x,y,z,u,v,r,g,b,a,nx,ny,nz)->off[0]++);
         net.guzhenren.shengxian.core.AscensionAuraMesh.draw(f,50,0,1,200,8,5,12,32,false,
                 (x,y,z,u,v,r,g,b,a,nx,ny,nz)->normal[0]++);
         net.guzhenren.shengxian.core.AscensionAuraMesh.draw(f,50,0,1,200,8,5,12,32,true,
                 (x,y,z,u,v,r,g,b,a,nx,ny,nz)->reduced[0]++);
         check(off[0]==0 && normal[0]>0 && reduced[0]>0 && reduced[0]<normal[0]);
      });

      test("observed shatter skips still emit one burst without replaying on login", () -> {
         var t = new net.guzhenren.shengxian.core.AscensionVisuals.Track(visualFrame(Phase.BREAKING, 1), 0);
         check(t.update(visualFrame(Phase.ABSORBING, 3), 10));
         check(t.shatterAlpha(10) > 0.99f);
         t.update(visualFrame(Phase.ABSORBING, 4), 20);
         check(t.shatterAlpha(24) == 0);
         var resumed = new net.guzhenren.shengxian.core.AscensionVisuals.Track(visualFrame(Phase.ABSORBING, 4), 30);
         check(resumed.shatterAlpha(30) == 0);
      });

      test("visual projection excludes unreleased human reserve", () -> {
         var record = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "first")),
                 AscensionProgress.empty(99999));
         var frame = net.guzhenren.shengxian.core.AscensionVisuals.from(record, 100);
         check(frame.heaven() == 0 && frame.earth() == 0 && frame.human() == 0);
         check(frame.phase() == Phase.PREPARING && frame.energy() <= 1);
      });
      test("visual frames reject nonfinite and out of range values", () -> {
         expect(IllegalArgumentException.class, () -> new net.guzhenren.shengxian.core.AscensionVisuals.Frame(
                 Phase.ABSORBING, 1, Float.NaN, 1, 1, 1, 1, 0, false));
         expect(IllegalArgumentException.class, () -> new net.guzhenren.shengxian.core.AscensionVisuals.Frame(
                 Phase.ABSORBING, -1, 0, 1, 1, 1, 1, 0, false));
      });
      test("visual heartbeat preserves phase age and rejects stale revision", () -> {
         var track = new net.guzhenren.shengxian.core.AscensionVisuals.Track(visualFrame(Phase.ABSORBING, 4), 10);
         check(track.update(visualFrame(Phase.ABSORBING, 5), 20));
         check(track.age(25) == 15);
         check(!track.update(visualFrame(Phase.BREAKING, 3), 25));
         check(track.frame().phase() == Phase.ABSORBING);
         check(track.update(visualFrame(Phase.OPENING, 6), 30));
         check(track.age(35) == 5);
      });
      test("visual sources expire and repeated stops cannot extend fading", () -> {
         var track = new net.guzhenren.shengxian.core.AscensionVisuals.Track(visualFrame(Phase.ABSORBING, 4), 0);
         check(track.alpha(15) > 0.99f && !track.expired(49));
         check(track.expired(60));
         var stopped = new net.guzhenren.shengxian.core.AscensionVisuals.Track(visualFrame(Phase.ABSORBING, 4), 0);
         stopped.stop(10);
         stopped.stop(15);
         check(stopped.expired(22));
         check(!stopped.update(visualFrame(Phase.ABSORBING, 4), 23));
      });
      test("visual terminal heartbeat never restarts the result", () -> {
         var track = new net.guzhenren.shengxian.core.AscensionVisuals.Track(visualFrame(Phase.FORMING, 5), 0);
         check(track.update(visualFrame(Phase.IMMORTAL, 6), 10));
         check(track.update(visualFrame(Phase.IMMORTAL, 7), 50));
         check(!track.expired(209) && track.expired(210));
         check(!net.guzhenren.shengxian.core.AscensionVisuals.announceTerminal(null, Phase.IMMORTAL));
         check(net.guzhenren.shengxian.core.AscensionVisuals.announceTerminal(Phase.FORMING, Phase.IMMORTAL));
         check(!net.guzhenren.shengxian.core.AscensionVisuals.announceTerminal(Phase.IMMORTAL, Phase.IMMORTAL));
      });

      test("three steps keep irreversible broken marker", () -> {
         AscensionSession var0x = AscensionSession.prepare(new ApertureKey(OWNER, "first"));
         long var1 = 0L;

         for (Phase var4 : List.of(Phase.BREAKING, Phase.BROKEN, Phase.ABSORBING, Phase.OPENING, Phase.FORMING, Phase.IMMORTAL)) {
            var0x = advance(var0x, var4, ++var1);
         }

         check(var0x.phase() == Phase.IMMORTAL && var0x.broken());
         check(var0x.phaseRevision() == 6L && var0x.stateRevision() == 6L && var0x.lastActionSequence() == 6L);
      });
      test("cannot skip absorption and formation", () -> {
         AscensionSession var0x = AscensionSession.prepare(new ApertureKey(OWNER, "first"));
         expect(IllegalStateException.class, () -> SessionFlow.advance(var0x, Phase.IMMORTAL, 0L, 1L, 0L));
      });
      test("broken aperture cannot return to preparation", () -> {
         AscensionSession var0x = session(Phase.BROKEN, true, 2L, 2L, 2L);
         expect(IllegalStateException.class, () -> SessionFlow.advance(var0x, Phase.PREPARING, 2L, 3L, 3L));
      });
      test("failed state cannot retry the same aperture", () -> {
         AscensionSession var0x = session(Phase.FAILED, true, 4L, 4L, 4L);
         expect(IllegalStateException.class, () -> SessionFlow.advance(var0x, Phase.BREAKING, 4L, 5L, 5L));
      });
      test("stale revision and repeated sequence reject changes", () -> {
         AscensionSession var0x = AscensionSession.prepare(new ApertureKey(OWNER, "first"));
         AscensionSession var1 = SessionFlow.advance(var0x, Phase.BREAKING, 0L, 1L, 1L);
         expect(IllegalStateException.class, () -> SessionFlow.advance(var1, Phase.BROKEN, 0L, 2L, 2L));
         expect(IllegalStateException.class, () -> SessionFlow.advance(var1, Phase.BROKEN, 1L, 1L, 2L));
      });
      test("restored illegal phase and marker combination rejects", () -> {
         expect(IllegalArgumentException.class, () -> session(Phase.IMMORTAL, false, 6L, 6L, 6L));
         expect(IllegalArgumentException.class, () -> session(Phase.PREPARING, true, 0L, 0L, 0L));
      });
      test("unverified missing and incapable profiles cannot start", () -> {
         check(!StartGate.check(true, false, Optional.empty(), ALL).allowed());
         RuleProfile var0x = new RuleProfile("ten-extreme", false, ALL);
         check(!StartGate.check(true, false, Optional.of(var0x), ALL).allowed());
         RuleProfile var1 = new RuleProfile("test-only", true, ALL);
         check(!StartGate.check(true, false, Optional.of(var1), Set.of()).allowed());
         check(!StartGate.check(false, false, Optional.of(var1), ALL).allowed());
         check(!StartGate.check(true, true, Optional.of(var1), ALL).allowed());
         check(StartGate.check(true, false, Optional.of(var1), ALL).allowed());
      });
      test("ordinary readiness no longer requires the unsatisfiable upstream aperture write", () -> {
         // PROJECT-003: UPSTREAM_APERTURE_WRITE is an audited field boundary (UPSTREAM-010, re-audited
         // 2026-11-02 against the full PlayerVariables field list), not missing work. The default
         // requirement set therefore excludes it, so what the build implements satisfies the route.
         check(!RuleProfile.ordinaryRequired(false).contains(
               RuleProfile.Capability.UPSTREAM_APERTURE_WRITE));
         check(RuleProfile.ordinaryRequired(true).contains(
               RuleProfile.Capability.UPSTREAM_APERTURE_WRITE));
         check(!RuleProfile.ordinary().capabilities().contains(
               RuleProfile.Capability.UPSTREAM_APERTURE_WRITE));

         AscensionReadiness.Decision blocked = AscensionReadiness.evaluate(Set.of(
               RuleProfile.Capability.CONTROL_QI));
         check(!blocked.available());
         check(blocked.missingCapabilities().contains(RuleProfile.Capability.BREAK_APERTURE));
         check(!blocked.missingCapabilities().contains(RuleProfile.Capability.UPSTREAM_APERTURE_WRITE));
         check(blocked.missingCapabilities().contains(RuleProfile.Capability.CANONICAL_TRIBULATION));
         check(blocked.missingCapabilities().contains(RuleProfile.Capability.CANONICAL_FAILURE));
         check(!blocked.messages().isEmpty());
         expect(UnsupportedOperationException.class,
               () -> blocked.missingCapabilities().add(RuleProfile.Capability.FORM_APERTURE));
         // The module reports what it actually implements: everything except the one upstream
         // field boundary (UPSTREAM-010). A blanket empty set was no longer true.
         AscensionReadiness.Decision current = AscensionReadiness.evaluate(AscensionReadiness.implementedCapabilities());
         check(current.available());
         check(current.missingCapabilities().isEmpty());
         AscensionReadiness.Decision ready = AscensionReadiness.evaluate(ALL);
         check(ready.available() && ready.missingCapabilities().isEmpty());
      });
      test("strict mode keeps the audited field boundary blocking the route", () -> {
         // Migration: the old closed-gate semantics survive as an explicit opt-in so an operator who
         // refuses the ledger substitution can still demand a write no configuration can satisfy.
         AscensionReadiness.Decision strict = AscensionReadiness.evaluate(
               AscensionReadiness.implementedCapabilities(), RuleProfile.ordinaryRequired(true));
         check(!strict.available());
         check(strict.missingCapabilities().equals(
               java.util.EnumSet.of(RuleProfile.Capability.UPSTREAM_APERTURE_WRITE)));
         check(strict.messages().size() == 1 && strict.messages().getFirst().contains("本体空窍状态写入"));
         check(!StartGate.check(true, false, Optional.of(RuleProfile.ordinary()),
               AscensionReadiness.implementedCapabilities(),
               RuleProfile.ordinaryRequired(true)).allowed());
         check(StartGate.check(true, false, Optional.of(RuleProfile.ordinary()),
               AscensionReadiness.implementedCapabilities(),
               RuleProfile.ordinaryRequired(false)).allowed());
      });
       test("start gate re-reads the requirement set instead of capturing it", () -> {
          // Regression: the server captured the requirement set once at start-up while readiness()
          // re-read the operator config on every call, so a config reload could report "closed"
          // while this gate still admitted a new session. The supplier is consulted per attempt.
          Path dir = Files.createTempDirectory("shengxian-gate-reload-");
          boolean[] strict = {false};
          AscensionService service = new AscensionService(new FileSessionRepository(dir),
                AscensionReadiness.implementedCapabilities(),
                () -> RuleProfile.ordinaryRequired(strict[0]));
          RuleProfile profile = RuleProfile.ordinary();
          AscensionRecord opened = service.start(new ApertureKey(OWNER, "before-reload"), true,
                Optional.of(profile), 10L);
          check(opened.session().phase() == Phase.PREPARING);
          strict[0] = true;
          ApertureKey afterReload = new ApertureKey(OWNER, "after-reload");
          expect(IOException.class,
                () -> service.start(afterReload, true, Optional.of(profile), 20L));
          check(new FileSessionRepository(dir).find(afterReload).isEmpty());
       });
       test("failure recovery requires the complete route and a canonically fatal qi cause", () -> {
         AscensionReadiness.Decision closed = AscensionReadiness.evaluate(Set.of());
         check(!AscensionReadiness.mayApplyFailure(closed,
               Set.of(AscensionProgress.FailureCause.HUMAN_OVERLOAD)));
         AscensionReadiness.Decision open = AscensionReadiness.evaluate(ALL);
         check(AscensionReadiness.mayApplyFailure(open,
               Set.of(AscensionProgress.FailureCause.HUMAN_OVERLOAD)));
         check(AscensionReadiness.mayApplyFailure(open,
               Set.of(AscensionProgress.FailureCause.HEAVEN_OVERLOAD)));
         check(AscensionReadiness.mayApplyFailure(open,
               Set.of(AscensionProgress.FailureCause.EARTH_OVERLOAD)));
         check(!AscensionReadiness.mayApplyFailure(open,
               Set.of(AscensionProgress.FailureCause.UNCLASSIFIED)));
         check(!AscensionReadiness.mayApplyFailure(open,
               Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT)));
         var impactSource = new AscensionProgress.TribulationImpactFailure(
               TribulationParameters.Profile.TAI_BAI_WITNESSED,
               AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND, "heaven-proof", 1L);
         check(AscensionReadiness.hasVerifiedTribulationImpactCause(
               Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT), impactSource));
         check(!AscensionReadiness.mayApplyTribulationImpact(closed,
               Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT), impactSource));
         check(AscensionReadiness.mayApplyTribulationImpact(open,
               Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT), impactSource));
         check(!AscensionReadiness.hasCanonicalFatalQiCause(
               Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT)));
         check(!AscensionReadiness.mayApplyFailure(open,
               Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT)));
         check(!AscensionReadiness.hasVerifiedTribulationImpactCause(
               Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT,
                     AscensionProgress.FailureCause.HUMAN_OVERLOAD), impactSource));
         check(!AscensionReadiness.hasVerifiedTribulationImpactCause(
               Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT), null));
         check(!AscensionReadiness.mayApplyFailure(open,
               Set.of(AscensionProgress.FailureCause.HUMAN_OVERLOAD,
                     AscensionProgress.FailureCause.TRIBULATION_IMPACT)));
         check(!AscensionReadiness.mayApplyFailure(open,
               Set.of(AscensionProgress.FailureCause.HUMAN_OVERLOAD,
                     AscensionProgress.FailureCause.HEAVEN_OVERLOAD)));
      });      test("aperture plot geometry uses bounded half-open cells", () -> {
         check(AperturePlotBounds.contains(0, -8.0, -8.0));
         check(AperturePlotBounds.contains(0, 7.999, 7.999));
         check(!AperturePlotBounds.contains(0, 8.0, 0.0));
         check(!AperturePlotBounds.contains(0, 0.0, 8.0));
         check(AperturePlotBounds.contains(1, 248.0, -8.0));
         check(AperturePlotBounds.find(Set.of(0, 1), 0.0, 0.0) == 0);
         check(AperturePlotBounds.find(Set.of(1), 0.0, 0.0) == -1);
         check(AperturePlotBounds.find(Set.of(0), 8.0, 0.0) == -1);
         check(AperturePlotBounds.find(Set.of(ApertureSpaceStore.MAX_PLOT_INDEX), 16128.0, 29999872.0)
               == ApertureSpaceStore.MAX_PLOT_INDEX);
         check(AperturePlotBounds.find(Set.of(0), Double.MAX_VALUE, 0.0) == -1);
         check(AperturePlotBounds.find(Set.of(0), Double.NaN, 0.0) == -1);
         check(!AperturePlotBounds.contains(-1, 0.0, 0.0));
         check(!AperturePlotBounds.contains(ApertureSpaceStore.MAX_PLOT_INDEX + 1, 0.0, 0.0));
      });
      test("closed readiness blocks new aperture formation but permits committed recovery", () -> {
         AscensionReadiness.Decision closed = AscensionReadiness.evaluate(Set.of());
         AscensionReadiness.Decision ready = AscensionReadiness.evaluate(ALL);
         check(!AscensionReadiness.mayAttemptFormation(closed, false));
         check(AscensionReadiness.mayAttemptFormation(closed, true));
         check(AscensionReadiness.mayAttemptFormation(ready, false));
      });      test("closed readiness does not advance breaking or broken sessions", () -> {
         AscensionReadiness.Decision closed = AscensionReadiness.evaluate(Set.of());
         check(!AscensionReadiness.mayAdvanceSession(closed, Phase.BREAKING));
         check(!AscensionReadiness.mayAdvanceSession(closed, Phase.BROKEN));
         check(AscensionReadiness.mayAdvanceSession(AscensionReadiness.evaluate(ALL), Phase.BREAKING));
         check(!AscensionReadiness.mayAdvanceSession(AscensionReadiness.evaluate(ALL), Phase.IMMORTAL));
      });
      test("shatter clock accumulates only rebased online ticks", () -> {
         ShatterClock.State beforeDisconnect = new ShatterClock.State(3L, 500L, 60);
         ShatterClock.State resumed = ShatterClock.rebase(beforeDisconnect, 1_000_000L);
         ShatterClock.State afterOnlineTick = ShatterClock.advance(resumed, 1_000_020L, 100);
         check(afterOnlineTick.phaseRevision() == 3L);
         check(afterOnlineTick.elapsedTicks() == 80);
         check(afterOnlineTick.lastServerTick() == 1_000_020L);
         ShatterClock.State complete = ShatterClock.advance(afterOnlineTick, 1_000_100L, 100);
         check(complete.elapsedTicks() == 100);
         check(complete.complete(100));
         expect(IllegalArgumentException.class,
               () -> ShatterClock.advance(afterOnlineTick, 1_000_019L, 100));
      });
      test("shatter clock is durable and cannot move its baseline backwards", () -> {
         Path root = Files.createTempDirectory("shengxian-shatter-clock-store-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         AscensionService service = new AscensionService(new FileSessionRepository(root), ALL);
         AscensionRecord record = service.start(key, true,
               Optional.of(new RuleProfile("fixture", true, ALL)), 500L);
         record = service.beginShatter(key, record.session().stateRevision(), 1L, 500L, 100.0, 80.0);
         long revision = record.session().phaseRevision();
         ShatterClock.State clock = record.progress().shatterClock(revision).orElseThrow();
         ShatterClock.State afterTick = ShatterClock.advance(clock, 560L, 100);
         record = service.mutate(key, record.session().stateRevision(), 2L, 560L,
               progress -> progress.withShatterClock(afterTick));
         AscensionRecord persisted = new FileSessionRepository(root).find(key).orElseThrow();
         check(persisted.progress().shatterClock(revision).orElseThrow().equals(afterTick));
         ShatterClock.State backwards = new ShatterClock.State(revision, 559L, afterTick.elapsedTicks());
         expect(IllegalStateException.class, () -> persisted.progress().withShatterClock(backwards));
      });
      test("new shatter intent binds debit baseline and target to the BREAKING phase", () -> {
         Path root = Files.createTempDirectory("shengxian-shatter-intent-v10-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         AscensionService service = new AscensionService(new FileSessionRepository(root), ALL);
         AscensionRecord prepared = service.start(key, true,
               Optional.of(new RuleProfile("fixture", true, ALL)), 500L);
         AscensionRecord breaking = service.beginShatter(key, prepared.session().stateRevision(),
               1L, 500L, 100.0, 80.0);
         long phaseRevision = breaking.session().phaseRevision();
         AscensionProgress.ShatterEssenceIntent intent = breaking.progress()
               .shatterEssenceIntent(phaseRevision).orElseThrow();
         check(intent.phaseRevision() == phaseRevision);
         check(intent.before() == 100.0 && intent.target() == 80.0);
         AscensionRecord restored = new FileSessionRepository(root).find(key).orElseThrow();
         check(restored.progress().shatterEssenceIntent(phaseRevision).orElseThrow().equals(intent));
         check("11".equals(read(recordFile(root)).getProperty("schema")));
         expect(IllegalArgumentException.class,
               () -> breaking.progress().withShatterEssenceIntent(phaseRevision, 80.0, 80.0));
         expect(IllegalArgumentException.class,
               () -> breaking.progress().withShatterEssenceIntent(phaseRevision, 80.0, 81.0));
         expect(IllegalArgumentException.class,
               () -> breaking.progress().withShatterEssenceIntent(phaseRevision, Double.NaN, 1.0));
         expect(IllegalArgumentException.class,
               () -> breaking.progress().withShatterEssenceIntent(phaseRevision, Double.POSITIVE_INFINITY, 1.0));
      });
      test("schema 9 target-only shatter remains readable without inventing its baseline", () -> {
         Path root = Files.createTempDirectory("shengxian-shatter-schema9-target-only-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         FileSessionRepository repository = new FileSessionRepository(root);
         AscensionRecord prepared = new AscensionService(repository, ALL).start(key, true,
               Optional.of(new RuleProfile("fixture", true, ALL)), 1L);
         AscensionSession breaking = SessionFlow.advance(prepared.session(), Phase.BREAKING,
               prepared.session().stateRevision(), 1L, 1L);
         AscensionProgress legacy = AscensionProgress.empty().withShatterEssenceTarget(
               breaking.phaseRevision(), 80.0).withShatterClock(ShatterClock.start(breaking.phaseRevision(), 1L));
         repository.save(new AscensionRecord(breaking, legacy), prepared.session().stateRevision());
         Path file = recordFile(root);
         Properties schema9 = read(file);
         schema9.setProperty("schema", "9");
         write(file, schema9);
         AscensionRecord restored = repository.find(key).orElseThrow();
         check(restored.session().phase() == Phase.BREAKING);
         check(restored.progress().shatterEssenceTarget(breaking.phaseRevision()).orElseThrow() == 80.0);
         check(restored.progress().shatterEssenceIntent(breaking.phaseRevision()).isEmpty());
         check("9".equals(read(file).getProperty("schema")));
      });
      test("shatter recovery decisions never guess across an unrecorded or third value", () -> {
         UUID sessionId = UUID.randomUUID();
         long phaseRevision = 3L;
         check(ShatterEssenceTransaction.decide(Optional.empty(), sessionId, phaseRevision,
               100.0, 80.0, 100.0) == ShatterEssenceTransaction.Decision.WRITE_PREPARED);
         check(ShatterEssenceTransaction.decide(Optional.empty(), sessionId, phaseRevision,
               null, 80.0, 80.0) == ShatterEssenceTransaction.Decision.QUARANTINE);
         var legacyPrepared = new ShatterEssenceTransaction.Receipt(sessionId, phaseRevision,
               ShatterEssenceTransaction.Stage.PREPARED, 100.0, 80.0, null);
         check(ShatterEssenceTransaction.decide(Optional.of(legacyPrepared), sessionId, phaseRevision,
               null, 80.0, 100.0) == ShatterEssenceTransaction.Decision.QUARANTINE);
         check(ShatterEssenceTransaction.decide(Optional.empty(), sessionId, phaseRevision,
               100.0, 80.0, 80.0) == ShatterEssenceTransaction.Decision.QUARANTINE);
         var prepared = new ShatterEssenceTransaction.Receipt(sessionId, phaseRevision,
               ShatterEssenceTransaction.Stage.PREPARED, 100.0, 80.0, null);
         check(ShatterEssenceTransaction.decide(Optional.of(prepared), sessionId, phaseRevision,
               100.0, 80.0, 100.0) == ShatterEssenceTransaction.Decision.APPLY_TARGET);
         check(ShatterEssenceTransaction.decide(Optional.of(prepared), sessionId, phaseRevision,
               100.0, 80.0, 80.0) == ShatterEssenceTransaction.Decision.COMMIT_ALREADY_APPLIED);
         check(ShatterEssenceTransaction.decide(Optional.of(prepared), sessionId, phaseRevision,
               100.0, 80.0, 90.0) == ShatterEssenceTransaction.Decision.QUARANTINE);
         var committed = new ShatterEssenceTransaction.Receipt(sessionId, phaseRevision,
               ShatterEssenceTransaction.Stage.COMMITTED, 100.0, 80.0, 80.0);
         check(ShatterEssenceTransaction.decide(Optional.of(committed), sessionId, phaseRevision,
               100.0, 80.0, 73.0) == ShatterEssenceTransaction.Decision.ALREADY_COMMITTED);
         check(ShatterEssenceTransaction.decide(Optional.of(committed), sessionId, phaseRevision,
               null, 80.0, 73.0) == ShatterEssenceTransaction.Decision.ALREADY_COMMITTED);
         var uncertain = new ShatterEssenceTransaction.Receipt(sessionId, phaseRevision,
               ShatterEssenceTransaction.Stage.UNCERTAIN, null, 80.0, null);
         check(ShatterEssenceTransaction.decide(Optional.of(uncertain), sessionId, phaseRevision,
               null, 80.0, 100.0) == ShatterEssenceTransaction.Decision.QUARANTINE);
         check(ShatterEssenceTransaction.decide(Optional.of(committed), UUID.randomUUID(), phaseRevision,
               100.0, 80.0, 80.0) == ShatterEssenceTransaction.Decision.QUARANTINE);
         check(ShatterEssenceTransaction.decide(Optional.of(committed), sessionId, phaseRevision,
               100.0, 79.0, 80.0) == ShatterEssenceTransaction.Decision.QUARANTINE);
         check(ShatterEssenceTransaction.decide(Optional.of(committed), sessionId, phaseRevision,
               100.0, 80.0, Double.NaN) == ShatterEssenceTransaction.Decision.QUARANTINE);
         expect(IllegalArgumentException.class, () -> new ShatterEssenceTransaction.Receipt(
               sessionId, phaseRevision, ShatterEssenceTransaction.Stage.PREPARED,
               null, 80.0, null));
         expect(IllegalArgumentException.class, () -> new ShatterEssenceTransaction.Receipt(
               sessionId, phaseRevision, ShatterEssenceTransaction.Stage.COMMITTED,
               100.0, 80.0, 79.0));
      });
      test("shatter reconciliation persists before one debit and never reapplies COMMITTED", () -> {
         UUID sessionId = UUID.randomUUID();
         long phaseRevision = 3L;
         TestShatterPort first = new TestShatterPort(sessionId, phaseRevision, null, 100.0);
         ShatterEssenceReconciler.reconcile(sessionId, phaseRevision, 100.0, 80.0, first);
         check(first.current == 80.0);
         check(first.saved.stage() == ShatterEssenceTransaction.Stage.COMMITTED);
         check(first.calls.equals(List.of("persist:PREPARED", "apply", "persist:COMMITTED")));

         var prepared = ShatterEssenceTransaction.Receipt.prepared(sessionId, phaseRevision, 100.0, 80.0);
         TestShatterPort beforeWriteCrash = new TestShatterPort(sessionId, phaseRevision, prepared, 100.0);
         ShatterEssenceReconciler.reconcile(sessionId, phaseRevision, 100.0, 80.0, beforeWriteCrash);
         check(beforeWriteCrash.current == 80.0);
         check(beforeWriteCrash.calls.equals(List.of("verify:100.0", "apply", "persist:COMMITTED")));

         TestShatterPort afterWriteCrash = new TestShatterPort(sessionId, phaseRevision, prepared, 80.0);
         ShatterEssenceReconciler.reconcile(sessionId, phaseRevision, 100.0, 80.0, afterWriteCrash);
         check(afterWriteCrash.saved.stage() == ShatterEssenceTransaction.Stage.COMMITTED);
         check(afterWriteCrash.calls.equals(List.of("verify:80.0", "persist:COMMITTED")));

         var committed = ShatterEssenceTransaction.Receipt.committed(sessionId, phaseRevision, 100.0, 80.0);
         TestShatterPort laterGameplay = new TestShatterPort(sessionId, phaseRevision, committed, 73.0);
         ShatterEssenceReconciler.reconcile(sessionId, phaseRevision, 100.0, 80.0, laterGameplay);
         check(laterGameplay.current == 73.0);
         check(laterGameplay.calls.equals(List.of("verifyCommitted")));

         TestShatterPort legacy = new TestShatterPort(sessionId, phaseRevision, prepared, 100.0);
         expect(IOException.class, () -> ShatterEssenceReconciler.reconcile(
               sessionId, phaseRevision, null, 80.0, legacy));
         check(legacy.saved.stage() == ShatterEssenceTransaction.Stage.UNCERTAIN);
         check(!legacy.calls.contains("apply"));

         TestShatterPort failedPrepare = new TestShatterPort(sessionId, phaseRevision, null, 100.0);
         failedPrepare.failNextPersist = true;
         expect(IOException.class, () -> ShatterEssenceReconciler.reconcile(
               sessionId, phaseRevision, 100.0, 80.0, failedPrepare));
         check(failedPrepare.saved.stage() == ShatterEssenceTransaction.Stage.UNCERTAIN);
         check(!failedPrepare.calls.contains("apply"));
      });
      test("shatter service pauses offline ticks and resumes from saved elapsed time", () -> {
         Path root = Files.createTempDirectory("shengxian-shatter-resume-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         AscensionService service = new AscensionService(new FileSessionRepository(root), ALL);
         AscensionRecord record = service.start(key, true,
               Optional.of(new RuleProfile("fixture", true, ALL)), 500L);
         record = service.beginShatter(key, record.session().stateRevision(), 1L, 500L, 100.0, 80.0);
         record = service.advanceShatterClock(key, record.session().stateRevision(), 2L, 560L, 100);
         check(record.progress().shatterClock(record.session().phaseRevision()).orElseThrow().elapsedTicks() == 60);
         record = service.rebaseShatterClock(key, record.session().stateRevision(), 3L, 1_000_000L);
         check(record.progress().shatterClock(record.session().phaseRevision()).orElseThrow()
               .equals(new ShatterClock.State(record.session().phaseRevision(), 1_000_000L, 60)));
         AscensionService reopened = new AscensionService(new FileSessionRepository(root), ALL);
         AscensionRecord resumed = reopened.advanceShatterClock(key, record.session().stateRevision(), 4L,
               1_000_020L, 100);
         check(resumed.progress().shatterClock(resumed.session().phaseRevision()).orElseThrow().elapsedTicks() == 80);
      });
      test("platform generation refuses built, occupied, or obstructed plots", () -> {
         check(AperturePlotPolicy.mayGeneratePlatform(0, false, true, true));
         check(!AperturePlotPolicy.mayGeneratePlatform(0, true, true, true));
         check(!AperturePlotPolicy.mayGeneratePlatform(0, false, false, true));
         check(!AperturePlotPolicy.mayGeneratePlatform(0, false, true, false));
         check(!AperturePlotPolicy.mayGeneratePlatform(-1, false, true, true));
      });      test("only the owner can mutate blocks inside the claimed plot", () -> {
         UUID guest = UUID.fromString("00000000-0000-0000-0000-000000000002");
         check(AperturePlotPolicy.mayModify(0, OWNER, OWNER, 0.0, 0.0));
         check(!AperturePlotPolicy.mayModify(0, OWNER, guest, 0.0, 0.0));
         check(!AperturePlotPolicy.mayModify(0, OWNER, OWNER, 8.0, 0.0));
         check(!AperturePlotPolicy.mayModify(0, null, OWNER, 0.0, 0.0));
      });      test("projectile and explosion effects stay inside one claimed plot", () -> {
         check(AperturePlotPolicy.mayAffect(0, 0));
         check(!AperturePlotPolicy.mayAffect(0, 1));
         check(!AperturePlotPolicy.mayAffect(0, -1));
         check(!AperturePlotPolicy.mayAffect(-1, 0));
      });      test("ordinary five-peak qualification respects the shifted stage", () -> {
         check(OrdinaryQualification.fivePeak(5.0, 4.0, false, false, false));
         check(!OrdinaryQualification.fivePeak(5.0, 5.0, false, false, false));
         check(!OrdinaryQualification.fivePeak(5.0, 4.0, true, false, false));
         check(OrdinaryQualification.fivePeak(5.0, 5.0, true, false, false));
         check(!OrdinaryQualification.fivePeak(5.0, 3.0, false, false, false));
         check(!OrdinaryQualification.fivePeak(5.0, 4.5, false, false, false));
         check(!OrdinaryQualification.fivePeak(4.0, 4.0, false, false, false));
         check(!OrdinaryQualification.fivePeak(6.0, 5.0, true, false, false));
         check(!OrdinaryQualification.fivePeak(5.0, 4.0, false, true, false));
         check(!OrdinaryQualification.fivePeak(5.0, 4.0, false, false, true));
         check(!OrdinaryQualification.fivePeak(Double.NaN, 4.0, false, false, false));
         check(!OrdinaryQualification.fivePeak(5.0, Double.NaN, false, false, false));
      });
      test("six-turn initial stage preserves the active ninefold offset", () -> {
         check(OrdinaryQualification.sixInitialStage(false) == 1.0);
         check(OrdinaryQualification.sixInitialStage(true) == 2.0);
      });
      test("profile declaration cannot be changed after validation", () -> {
         HashSet var0x = new HashSet<>(ALL);
         RuleProfile var1 = new RuleProfile("fixture", true, var0x);
         var0x.clear();
         check(StartGate.check(true, false, Optional.of(var1), ALL).allowed());
      });
      test("repository roundtrip preserves separate apertures and revisions", () -> {
         Path var0x = Files.createTempDirectory("shengxian-repository-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         AscensionRecord var2 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "first")), AscensionProgress.empty());
         AscensionRecord var3 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "../second")), AscensionProgress.empty());
         var1.save(var2, -1L);
         var1.save(var3, -1L);
         AscensionRecord var4 = new AscensionRecord(advance(var2.session(), Phase.BREAKING, 1L), var2.progress());
         var1.save(var4, 0L);
         FileSessionRepository var5 = new FileSessionRepository(var0x);
         check(var5.find(var2.session().key()).orElseThrow().equals(var4));
         check(var5.find(var3.session().key()).orElseThrow().equals(var3));
         check(var5.find(new ApertureKey(UUID.randomUUID(), "first")).isEmpty());
      });
      test("stale writes and duplicate create leave existing record intact", () -> {
         Path var0x = Files.createTempDirectory("shengxian-conflict-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         AscensionRecord var2 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "first")), AscensionProgress.empty());
         var1.save(var2, -1L);
         AscensionRecord var3 = new AscensionRecord(advance(var2.session(), Phase.BREAKING, 1L), var2.progress());
         var1.save(var3, 0L);
         expect(IOException.class, () -> var1.save(var2, -1L));
         expect(IOException.class, () -> var1.save(var3, 0L));
         check(var1.find(var2.session().key()).orElseThrow().equals(var3));
      });
      test("case folding cannot merge distinct aperture identifiers", () -> {
         Path var0x = Files.createTempDirectory("shengxian-case-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         AscensionRecord var2 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "AA@")), AscensionProgress.empty());
         AscensionRecord var3 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "AAZ")), AscensionProgress.empty());
         var1.save(var2, -1L);
         var1.save(var3, -1L);
         check(var1.find(var2.session().key()).orElseThrow().equals(var2));
         check(var1.find(var3.session().key()).orElseThrow().equals(var3));
      });
      test("maximum length unicode aperture remains persistable", () -> {
         Path var0x = Files.createTempDirectory("shengxian-unicode-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         AscensionRecord var2 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "\u7a8d".repeat(64))), AscensionProgress.empty());
         var1.save(var2, -1L);
         check(var1.find(var2.session().key()).orElseThrow().equals(var2));
      });
      test("future schema refuses read and overwrite without losing evidence", () -> {
         Path var0x = Files.createTempDirectory("shengxian-future-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         AscensionRecord var2 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "first")), AscensionProgress.empty());
         var1.save(var2, -1L);
         Path var3 = recordFile(var0x);
         Properties var4 = read(var3);
         var4.setProperty("schema", "999");
         write(var3, var4);
         byte[] var5 = Files.readAllBytes(var3);
         expect(IOException.class, () -> var1.find(var2.session().key()));
         expect(IOException.class, () -> var1.save(new AscensionRecord(advance(var2.session(), Phase.BREAKING, 1L), var2.progress()), 0L));
         check(Arrays.equals(var5, Files.readAllBytes(var3)));
      });
      test("corrupt boolean cannot silently restore an unbroken aperture", () -> {
         Path var0x = Files.createTempDirectory("shengxian-corrupt-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         AscensionRecord var2 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "first")), AscensionProgress.empty());
         var1.save(var2, -1L);
         Path var3 = recordFile(var0x);
         Properties var4 = read(var3);
         var4.setProperty("broken", "maybe");
         write(var3, var4);
         expect(IOException.class, () -> var1.find(var2.session().key()));
      });
      test("malformed property escapes respect repository failure boundary", () -> {
         Path var0x = Files.createTempDirectory("shengxian-escape-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         AscensionRecord var2 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "first")), AscensionProgress.empty());
         var1.save(var2, -1L);
         Path var3 = recordFile(var0x);
         Files.writeString(var3, "schema=2\ninvalid=\\uZZZZ\n");
         byte[] var4 = Files.readAllBytes(var3);
         expect(IOException.class, () -> var1.find(var2.session().key()));
         expect(IOException.class, () -> var1.save(new AscensionRecord(advance(var2.session(), Phase.BREAKING, 1L), var2.progress()), 0L));
         check(Arrays.equals(var4, Files.readAllBytes(var3)));
      });
      test("restored impossible progress cannot claim a completed ascension", () -> {
         Path var0x = Files.createTempDirectory("shengxian-unreachable-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         AscensionRecord var2 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "first")), AscensionProgress.empty());
         var1.save(var2, -1L);
         Path var3 = recordFile(var0x);
         Properties var4 = read(var3);
         var4.setProperty("phase", "IMMORTAL");
         var4.setProperty("broken", "true");
         write(var3, var4);
         byte[] var5 = Files.readAllBytes(var3);
         expect(IOException.class, () -> var1.find(var2.session().key()));
         check(Arrays.equals(var5, Files.readAllBytes(var3)));
         expect(IllegalArgumentException.class, () -> session(Phase.BROKEN, true, 0L, 0L, 0L));
      });
      test("same-phase updates persist without advancing the phase revision", () -> {
         Path var0x = Files.createTempDirectory("shengxian-same-phase-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         AscensionRecord var2 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "first")), AscensionProgress.empty());
         var1.save(var2, -1L);
         AscensionProgress.Qi var3 = new AscensionProgress.Qi(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
         AscensionProgress var4 = AscensionProgress.empty().withQi(var3).withInsight(2, 1).withAppliedEffect("insight:1").withApertureDraft("draft-a");
         AscensionRecord var5 = new AscensionRecord(SessionFlow.update(var2.session(), 0L, 1L, 1L), var4);
         var1.save(var5, 0L);
         FileSessionRepository var6 = new FileSessionRepository(var0x);
         AscensionRecord var7 = var6.find(var2.session().key()).orElseThrow();
         check(var7.session().phase() == Phase.PREPARING);
         check(var7.session().phaseRevision() == 0L && var7.session().stateRevision() == 1L);
         check(var7.session().lastActionSequence() == 1L && var7.session().serverTick() == 1L);
         check(var7.progress().equals(var4));
         AscensionRecord var8 = new AscensionRecord(SessionFlow.update(var7.session(), 1L, 2L, 2L), var7.progress().withAppliedEffect("insight:2"));
         var6.save(var8, 1L);
         check(new FileSessionRepository(var0x).find(var2.session().key()).orElseThrow().progress().appliedEffects().equals(Set.of("insight:1", "insight:2")));
      });
      test("same-phase updates reject stale revisions and repeated sequences", () -> {
         AscensionSession var0x = session(Phase.ABSORBING, true, 3L, 3L, 7L, 10L);
         expect(IllegalStateException.class, () -> SessionFlow.update(var0x, 2L, 8L, 10L));
         expect(IllegalStateException.class, () -> SessionFlow.update(var0x, 3L, 7L, 10L));
         check(SessionFlow.update(var0x, 3L, 8L, 10L).stateRevision() == 4L);
      });
      test("server clock cannot move backwards and may stay equal", () -> {
         AscensionSession var0x = session(Phase.ABSORBING, true, 3L, 5L, 2L, 10L);
         expect(IllegalStateException.class, () -> SessionFlow.update(var0x, 5L, 3L, 9L));
         expect(IllegalStateException.class, () -> SessionFlow.advance(var0x, Phase.OPENING, 5L, 3L, 9L));
         check(SessionFlow.update(var0x, 5L, 3L, 10L).serverTick() == 10L);
      });
      test("effect keys are idempotent and cannot be removed", () -> {
         AscensionProgress var0x = AscensionProgress.empty().withAppliedEffect("reward:a");
         expect(IllegalStateException.class, () -> var0x.withAppliedEffect("reward:a"));
         Path var1 = Files.createTempDirectory("shengxian-effects-");
         FileSessionRepository var2 = new FileSessionRepository(var1);
         AscensionRecord var3 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "first")), AscensionProgress.empty());
         var2.save(var3, -1L);
         AscensionRecord var4 = new AscensionRecord(SessionFlow.update(var3.session(), 0L, 1L, 0L), AscensionProgress.empty().withAppliedEffect("reward:a"));
         var2.save(var4, 0L);
         AscensionRecord var5 = new AscensionRecord(SessionFlow.update(var4.session(), 1L, 2L, 0L), AscensionProgress.empty());
         expect(IOException.class, () -> var2.save(var5, 1L));
         check(var2.find(var3.session().key()).orElseThrow().progress().appliedEffects().contains("reward:a"));
      });
      test("schema 1 records migrate, then save as schema 11", () -> {
         Path var0x = Files.createTempDirectory("shengxian-migrate-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         UUID var2 = UUID.randomUUID();
         writeSchema1(var0x, var1, var2, Phase.BREAKING, false, 1L, 1L);
         FileSessionRepository var3 = new FileSessionRepository(var0x);
         AscensionRecord var4 = var3.find(var1).orElseThrow();
         check(var4.session().id().equals(var2));
         check(var4.session().phaseRevision() == 1L && var4.session().stateRevision() == 1L);
         check(var4.session().lastActionSequence() == 1L && var4.session().serverTick() == 0L);
         check(var4.progress().equals(AscensionProgress.empty()));
         AscensionRecord var5 = new AscensionRecord(SessionFlow.update(var4.session(), 1L, 2L, 0L), AscensionProgress.empty().withAppliedEffect("migrated"));
         var3.save(var5, 1L);
         AscensionRecord var6 = new FileSessionRepository(var0x).find(var1).orElseThrow();
         check(var6.equals(var5));
         check("11".equals(read(recordFile(var0x)).getProperty("schema")));
      });
      test("invalid schema 1 records stay preserved and cannot be overwritten", () -> {
         Path var0x = Files.createTempDirectory("shengxian-migrate-bad-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         writeSchema1(var0x, var1, UUID.randomUUID(), Phase.IMMORTAL, true, 0L, 0L);
         Path var2 = recordFile(var0x);
         byte[] var3 = Files.readAllBytes(var2);
         FileSessionRepository var4 = new FileSessionRepository(var0x);
         expect(IOException.class, () -> var4.find(var1));
         expect(IOException.class, () -> var4.save(new AscensionRecord(AscensionSession.prepare(var1), AscensionProgress.empty()), -1L));
         check(Arrays.equals(var3, Files.readAllBytes(var2)));
      });
      test("schema 2 rejects state revisions older than phase revisions", () -> {
         Path var0x = Files.createTempDirectory("shengxian-state-revision-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         AscensionRecord var2 = new AscensionRecord(session(Phase.ABSORBING, true, 3L, 3L, 3L), AscensionProgress.empty());
         var1.save(new AscensionRecord(AscensionSession.prepare(var2.session().key()), AscensionProgress.empty()), -1L);
         Properties var3 = read(recordFile(var0x));
         var3.setProperty("phase", "ABSORBING");
         var3.setProperty("broken", "true");
         var3.setProperty("phaseRevision", "3");
         var3.setProperty("stateRevision", "2");
         var3.setProperty("sequence", "3");
         write(recordFile(var0x), var3);
         expect(IOException.class, () -> var1.find(var2.session().key()));
      });
      test("failed sessions freeze while immortal sessions cannot change phase", () -> {
         AscensionSession var0x = session(Phase.FAILED, true, 4L, 4L, 4L);
         expect(IllegalStateException.class, () -> SessionFlow.update(var0x, 4L, 5L, 5L));
         AscensionSession var1 = session(Phase.IMMORTAL, true, 6L, 6L, 6L);
         AscensionSession var2 = SessionFlow.update(var1, 6L, 7L, 7L);
         check(var2.phase() == Phase.IMMORTAL && var2.phaseRevision() == 6L);
         check(var2.stateRevision() == 7L && var2.lastActionSequence() == 7L);
         expect(IllegalStateException.class, () -> SessionFlow.advance(var1, Phase.FAILED, 6L, 7L, 7L));
      });
      test("qi values reject NaN, negative and oversized magnitudes", () -> {
         expect(IllegalArgumentException.class, () -> new AscensionProgress.Qi(Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0));
         expect(IllegalArgumentException.class, () -> new AscensionProgress.Qi(0.0, -1.0, 0.0, 0.0, 0.0, 0.0));
         expect(IllegalArgumentException.class, () -> new AscensionProgress.Qi(0.0, 0.0, 0.0, 0.0, 0.0, 1.000000001E9));
         Path var0x = Files.createTempDirectory("shengxian-nan-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         AscensionRecord var2 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "first")), AscensionProgress.empty());
         var1.save(var2, -1L);
         Properties var3 = read(recordFile(var0x));
         var3.setProperty("heavenQi", "NaN");
         write(recordFile(var0x), var3);
         expect(IOException.class, () -> var1.find(var2.session().key()));
      });
      test("schema 1 sequence no longer has to exceed revision", () -> {
         Path var0x = Files.createTempDirectory("shengxian-old-sequence-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         writeSchema1(var0x, var1, UUID.randomUUID(), Phase.BROKEN, true, 3L, 1L);
         FileSessionRepository var2 = new FileSessionRepository(var0x);
         AscensionRecord var3 = var2.find(var1).orElseThrow();
         check(var3.session().stateRevision() == 3L && var3.session().lastActionSequence() == 1L);
         var2.save(new AscensionRecord(SessionFlow.update(var3.session(), 3L, 2L, 0L), var3.progress()), 3L);
      });
      test(
         "many absorption ticks persist without a phase jump",
         () -> {
            Path var0x = Files.createTempDirectory("shengxian-absorbing-");
            FileSessionRepository var1 = new FileSessionRepository(var0x);
            AscensionRecord var2 = new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "first")), AscensionProgress.empty());
            var1.save(var2, -1L);
            long var3 = 0L;

            for (Phase var6 : List.of(Phase.BREAKING, Phase.BROKEN, Phase.ABSORBING)) {
               AscensionRecord var7 = new AscensionRecord(advance(var2.session(), var6, ++var3), var2.progress());
               var1.save(var7, var2.session().stateRevision());
               var2 = var7;
            }

            for (int var9 = 1; var9 <= 3; var9++) {
               AscensionProgress.Qi var11 = new AscensionProgress.Qi(var9, var9 * 2, var9 * 3, 10.0, 20.0, 30.0);
               AscensionProgress var12 = var2.progress().withQi(var11).withAppliedEffect("absorb:" + var9);
               AscensionRecord var8 = new AscensionRecord(
                  SessionFlow.update(var2.session(), var2.session().stateRevision(), ++var3, var2.session().serverTick() + 1L), var12
               );
               var1.save(var8, var2.session().stateRevision());
               var2 = var8;
            }

            AscensionRecord var10 = new FileSessionRepository(var0x).find(var2.session().key()).orElseThrow();
            check(var10.session().phase() == Phase.ABSORBING && var10.session().broken());
            check(var10.session().phaseRevision() == 3L && var10.session().stateRevision() == 6L);
            check(var10.session().lastActionSequence() == 6L && var10.session().serverTick() == 6L);
            check(var10.progress().qi().equals(new AscensionProgress.Qi(3.0, 6.0, 9.0, 10.0, 20.0, 30.0)));
            check(var10.progress().appliedEffects().equals(Set.of("absorb:1", "absorb:2", "absorb:3")));
            expect(IllegalStateException.class, () -> SessionFlow.update(var10.session(), 6L, 6L, 7L));
         }
      );
      test("schema 1 PREPARING record migrates as an empty preparation", () -> {
         Path var0x = Files.createTempDirectory("shengxian-prepare-migrate-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         UUID var2 = UUID.randomUUID();
         writeSchema1(var0x, var1, var2, Phase.PREPARING, false, 0L, 0L);
         FileSessionRepository var3 = new FileSessionRepository(var0x);
         AscensionRecord var4 = var3.find(var1).orElseThrow();
         check(var4.session().id().equals(var2));
         check(var4.session().phase() == Phase.PREPARING && !var4.session().broken());
         check(var4.session().phaseRevision() == 0L && var4.session().stateRevision() == 0L);
         check(var4.progress().equals(AscensionProgress.empty()));
      });
      test("schema 1 active absorption migrates to the inert unresolved profile", () -> {
         Path var0x = Files.createTempDirectory("shengxian-active-migrate-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         writeSchema1(var0x, var1, UUID.randomUUID(), Phase.ABSORBING, true, 3L, 3L);
         AscensionRecord migrated = new FileSessionRepository(var0x).find(var1).orElseThrow();
         check(migrated.session().phase() == Phase.ABSORBING);
         check(migrated.progress().tribulationProfile() == TribulationParameters.Profile.UNRESOLVED_ORDINARY);
         check(migrated.progress().tribulationPressure() == 0.0);
         check(migrated.progress().tribulationImpactSequence() == 0L);
         check(migrated.progress().tribulationImpactFailure().isEmpty());
      });
      test(
         "creation preserves the server clock and rejects preloaded effects",
         () -> {
            Path var0x = Files.createTempDirectory("shengxian-create-");
            FileSessionRepository var1 = new FileSessionRepository(var0x);
            ApertureKey var2 = new ApertureKey(OWNER, "first");
            AscensionRecord var3 = new AscensionRecord(AscensionSession.prepare(var2, 500L), AscensionProgress.empty());
            var1.save(var3, -1L);
            check(var1.find(var2).orElseThrow().session().serverTick() == 500L);
            expect(
               IOException.class,
               () -> var1.save(
                  new AscensionRecord(AscensionSession.prepare(new ApertureKey(OWNER, "second"), 1L), AscensionProgress.empty().withAppliedEffect("smuggled")),
                  -1L
               )
            );
         }
      );
      test("ordinary start persists bounded human reserve and vital Gu", () -> {
         Path var0x = Files.createTempDirectory("shengxian-start-seed-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         AscensionProgress.VitalGu var2 = new AscensionProgress.VitalGu("a".repeat(64), "\u672c\u547d\u86ca");
         AscensionService var3 = new AscensionService(new FileSessionRepository(var0x), ALL);
         AscensionRecord var4 = var3.start(var1, true, Optional.of(new RuleProfile("fixture", true, ALL)), 500L, 84.5, var2);
         check(var4.session().phase() == Phase.PREPARING);
         check(var4.session().serverTick() == 500L);
         check(var4.progress().qi().humanReserve() == 84.5);
         check(var4.progress().vitalGu().equals(var2));
         check(var4.progress().equals(AscensionProgress.empty(84.5).withVitalGu(var2)));
         check(new FileSessionRepository(var0x).find(var1).orElseThrow().equals(var4));
      });
      test(
         "initial records reject injected gameplay progress and invalid reserves",
         () -> {
            Path var0x = Files.createTempDirectory("shengxian-start-invalid-");
            FileSessionRepository var1 = new FileSessionRepository(var0x);
            AscensionProgress.VitalGu var2 = new AscensionProgress.VitalGu("a".repeat(64), "\u672c\u547d\u86ca");
            AscensionProgress var3 = AscensionProgress.empty(84.5).withVitalGu(var2);
            List var4 = List.of(
               var3.withQi(new AscensionProgress.Qi(1.0, 0.0, 0.0, 0.0, 0.0, 84.5)),
               var3.withRisk(new AscensionProgress.Risk(1.0, 0.0, 0.0)),
               var3.withWindow(new AscensionProgress.Window(1, "injected-window")),
               var3.withTribulation(new AscensionProgress.Tribulation("injected", AscensionProgress.Tribulation.Type.SOUND_CHIMES, 1, 1)),
               var3.withInsight(1, 0),
               var3.withAppliedEffect("injected-effect"),
               var3.withAperture(new AscensionProgress.Aperture(100.0, 1.0, 1.0, 0L, 1))
            );

            for (int var5 = 0; var5 < var4.size(); var5++) {
               ApertureKey var6 = new ApertureKey(OWNER, "invalid-" + var5);
               AscensionRecord var7 = new AscensionRecord(AscensionSession.prepare(var6), (AscensionProgress)var4.get(var5));
               expect(IOException.class, () -> var1.save(var7, -1L));
               check(var1.find(var6).isEmpty());
            }

            AscensionService var13 = new AscensionService(var1, ALL);
            Optional var14 = Optional.of(new RuleProfile("fixture", true, ALL));

            for (double var10 : new double[]{Double.NaN, -1.0, 1.000000001E9}) {
               ApertureKey var12 = new ApertureKey(UUID.randomUUID(), "first");
               expect(IllegalArgumentException.class, () -> var13.start(var12, true, var14, 0L, var10, var2));
               check(var1.find(var12).isEmpty());
            }
         }
      );
      test("service refuses to start when the gate is closed", () -> {
         Path var0x = Files.createTempDirectory("shengxian-service-gate-");
         AscensionService var1 = new AscensionService(new FileSessionRepository(var0x), Set.of());
         ApertureKey var2 = new ApertureKey(OWNER, "first");
         RuleProfile var3 = new RuleProfile("fixture", true, ALL);
         expect(IOException.class, () -> var1.start(var2, true, Optional.of(var3), 0L));
         check(new FileSessionRepository(var0x).find(var2).isEmpty());
      });
      test("service starts once and rejects a duplicate start", () -> {
         Path var0x = Files.createTempDirectory("shengxian-service-start-");
         AscensionService var1 = new AscensionService(new FileSessionRepository(var0x), ALL);
         ApertureKey var2 = new ApertureKey(OWNER, "first");
         RuleProfile var3 = new RuleProfile("fixture", true, ALL);
         AscensionRecord var4 = var1.start(var2, true, Optional.of(var3), 100L);
         check(var4.session().phase() == Phase.PREPARING && var4.session().serverTick() == 100L);
         expect(IOException.class, () -> var1.start(var2, true, Optional.of(var3), 100L));
      });
      test("shatter cannot roll back to preparation after the irreversible attempt begins", () -> {
         Path directory = Files.createTempDirectory("shengxian-shatter-no-cancel-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         AscensionService service = new AscensionService(new FileSessionRepository(directory), ALL);
         AscensionRecord prepared = service.start(key, true, Optional.of(new RuleProfile("fixture", true, ALL)), 0L);
         AscensionRecord breaking = service.beginShatter(key, prepared.session().stateRevision(), 1L, 1L, 100.0, 80.0);
         expect(IllegalStateException.class, () -> service.advance(key, Phase.PREPARING,
               breaking.session().stateRevision(), 2L, 2L));
         check(new FileSessionRepository(directory).find(key).orElseThrow().equals(breaking));
      });      test("restarted shatter keeps its committed target and rejects a second attempt", () -> {
         Path directory = Files.createTempDirectory("shengxian-shatter-restart-no-retry-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         AscensionService service = new AscensionService(new FileSessionRepository(directory), ALL);
         AscensionRecord prepared = service.start(key, true, Optional.of(new RuleProfile("fixture", true, ALL)), 0L);
         AscensionRecord breaking = service.beginShatter(key, prepared.session().stateRevision(), 1L, 1L, 100.0, 80.0);
         AscensionService restored = new AscensionService(new FileSessionRepository(directory), ALL);
         AscensionRecord current = restored.load(key);
         check(current.equals(breaking));
         check(current.progress().shatterEssenceTarget().orElseThrow() == 80.0);
         expect(IOException.class, () -> restored.beginShatter(key, current.session().stateRevision(), 2L, 2L, 110.0, 70.0));
         check(new FileSessionRepository(directory).find(key).orElseThrow().equals(current));
      });      test("same-phase shatter target tampering is rejected without overwriting", () -> {
         Path var0x = Files.createTempDirectory("shengxian-shatter-tamper-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         FileSessionRepository var2 = new FileSessionRepository(var0x);
         AscensionService var3 = new AscensionService(var2, ALL);
         AscensionRecord var4 = var3.start(var1, true, Optional.of(new RuleProfile("fixture", true, ALL)), 0L);
         AscensionRecord var5 = var3.beginShatter(var1, var4.session().stateRevision(), 1L, 1L, 100.0, 80.0);
         String var6 = var5.progress().appliedEffects().stream().filter(var0xx -> var0xx.contains(Double.toHexString(80.0))).findFirst().orElseThrow();
         String var7 = var6.replace(Double.toHexString(80.0), Double.toHexString(70.0));
         check(!var7.equals(var6));
         AscensionSession var8 = SessionFlow.update(var5.session(), var5.session().stateRevision(), 2L, 2L);
         LinkedHashSet var9 = new LinkedHashSet<>(var5.progress().appliedEffects());
         var9.remove(var6);
         var9.add(var7);
         AscensionRecord var10 = new AscensionRecord(var8, testProgressWithEffects(var5.progress(), var9));
         expect(IOException.class, () -> var2.save(var10, var5.session().stateRevision()));
         LinkedHashSet var11 = new LinkedHashSet<>(var5.progress().appliedEffects());
         var11.add(var7);
         AscensionRecord var12 = new AscensionRecord(var8, testProgressWithEffects(var5.progress(), var11));
         expect(IOException.class, () -> var2.save(var12, var5.session().stateRevision()));
         check(new FileSessionRepository(var0x).find(var1).orElseThrow().equals(var5));
      });
      test("a single legacy shatter target remains readable", () -> {
         Path var0x = Files.createTempDirectory("shengxian-shatter-legacy-one-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         FileSessionRepository var2 = new FileSessionRepository(var0x);
         AscensionService var3 = new AscensionService(var2, ALL);
         AscensionRecord var4 = var3.start(var1, true, Optional.of(new RuleProfile("fixture", true, ALL)), 0L);
         var3.beginShatter(var1, var4.session().stateRevision(), 1L, 1L, 100.0, 80.0);
         Path var5 = recordFile(var0x);
         Properties var6 = read(var5);
          var6.remove("effect.1");
         var6.setProperty("effect.count", "1");
         var6.setProperty("effect.0", "shatter-zhenyuan-target:" + Double.toHexString(80.0));
         write(var5, var6);
         AscensionRecord var7 = new FileSessionRepository(var0x).find(var1).orElseThrow();
         check(var7.session().phase() == Phase.BREAKING);
         check(var7.progress().shatterEssenceTarget().orElseThrow() == 80.0);
      });
      test("ambiguous legacy shatter targets refuse reading without changing the file", () -> {
         Path var0x = Files.createTempDirectory("shengxian-shatter-legacy-ambiguous-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         FileSessionRepository var2 = new FileSessionRepository(var0x);
         AscensionService var3 = new AscensionService(var2, ALL);
         AscensionRecord var4 = var3.start(var1, true, Optional.of(new RuleProfile("fixture", true, ALL)), 0L);
         var3.beginShatter(var1, var4.session().stateRevision(), 1L, 1L, 100.0, 80.0);
         Path var5 = recordFile(var0x);
         Properties var6 = read(var5);
         var6.setProperty("effect.count", "2");
         var6.setProperty("effect.0", "shatter-zhenyuan-target:" + Double.toHexString(80.0));
         var6.setProperty("effect.1", "shatter-zhenyuan-target:" + Double.toHexString(70.0));
         write(var5, var6);
         byte[] var7 = Files.readAllBytes(var5);
         expect(IOException.class, () -> new FileSessionRepository(var0x).find(var1));
         check(Arrays.equals(var7, Files.readAllBytes(var5)));
      });
      test("service effect keys stay idempotent across a restart", () -> {
         Path var0x = Files.createTempDirectory("shengxian-service-effect-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         RuleProfile var2 = new RuleProfile("fixture", true, ALL);
         AscensionService var3 = new AscensionService(new FileSessionRepository(var0x), ALL);
         AscensionRecord var4 = var3.start(var1, true, Optional.of(var2), 0L);
         var4 = var3.applyEffect(var1, var4.session().stateRevision(), 1L, 1L, "reward:start");
         check(var4.session().stateRevision() == 1L);
         AscensionService var5 = new AscensionService(new FileSessionRepository(var0x), ALL);
         expect(IllegalStateException.class, () -> var5.applyEffect(var1, 1L, 2L, 2L, "reward:start"));
         check(var5.load(var1).session().stateRevision() == 1L);
         AscensionRecord var6 = var5.applyEffect(var1, 1L, 2L, 2L, "reward:next");
         check(var6.progress().appliedEffects().equals(Set.of("reward:start", "reward:next")));
      });
      test("service advance carries progress and stays behind the start gate", () -> {
         Path var0x = Files.createTempDirectory("shengxian-service-advance-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         RuleProfile var2 = new RuleProfile("fixture", true, ALL);
         AscensionService var3 = new AscensionService(new FileSessionRepository(var0x), ALL);
         AscensionRecord var4 = var3.start(var1, true, Optional.of(var2), 0L);
         var4 = var3.applyEffect(var1, 0L, 1L, 0L, "prep");
         AscensionRecord var5 = var3.advance(var1, Phase.BREAKING, 1L, 2L, 1L);
         check(var5.session().phase() == Phase.BREAKING && var5.session().phaseRevision() == 1L);
         check(var5.progress().appliedEffects().contains("prep"));
         expect(IllegalStateException.class, () -> var3.advance(var1, Phase.IMMORTAL, 2L, 3L, 2L));
      });
      test("service rejects a stale expected revision", () -> {
         Path var0x = Files.createTempDirectory("shengxian-service-stale-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         RuleProfile var2 = new RuleProfile("fixture", true, ALL);
         AscensionService var3 = new AscensionService(new FileSessionRepository(var0x), ALL);
         AscensionRecord var4 = var3.start(var1, true, Optional.of(var2), 0L);
         var3.applyEffect(var1, 0L, 1L, 0L, "a");
         expect(IllegalStateException.class, () -> var3.applyEffect(var1, 0L, 2L, 0L, "b"));
      });
      test("session store survives a restart and eviction", () -> {
         Path var0x = Files.createTempDirectory("shengxian-store-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         RuleProfile var2 = new RuleProfile("fixture", true, ALL);
         SessionStore var3 = new SessionStore(var0x, ALL);
         AscensionRecord var4 = var3.start(var1, true, Optional.of(var2), 0L);
         var4 = var3.applyEffect(var1, 0L, 1L, 1L, "store:first");
         check(var3.find(var1).orElseThrow().equals(var4));
         SessionStore var5 = new SessionStore(var0x, ALL);
         check(var5.find(var1).orElseThrow().equals(var4));
         var5.evict(OWNER);
         check(var5.find(var1).orElseThrow().equals(var4));
         var5.flush();
         check(var5.find(var1).orElseThrow().equals(var4));
      });
      test("session store refuses to start when the adapter is incapable", () -> {
         Path var0x = Files.createTempDirectory("shengxian-store-gate-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         RuleProfile var2 = new RuleProfile("fixture", true, ALL);
         SessionStore var3 = new SessionStore(var0x, Set.of());
         expect(IOException.class, () -> var3.start(var1, true, Optional.of(var2), 0L));
         check(new FileSessionRepository(var0x).find(var1).isEmpty());
      });
      test("session store keeps CAS semantics for mutations", () -> {
         Path var0x = Files.createTempDirectory("shengxian-store-cas-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         RuleProfile var2 = new RuleProfile("fixture", true, ALL);
         SessionStore var3 = new SessionStore(var0x, ALL);
         AscensionRecord var4 = var3.start(var1, true, Optional.of(var2), 0L);
         var3.applyEffect(var1, 0L, 1L, 1L, "a");
         expect(IllegalStateException.class, () -> var3.applyEffect(var1, 0L, 2L, 2L, "b"));
         AscensionRecord var5 = var3.find(var1).orElseThrow();
         check(var5.session().stateRevision() == 1L);
         check(var5.progress().appliedEffects().equals(Set.of("a")));
      });
      test(
         "schema 2 records migrate with empty risk and window",
         () -> {
            Path var0x = Files.createTempDirectory("shengxian-schema2-");
            FileSessionRepository var1 = new FileSessionRepository(var0x);
            ApertureKey var2 = new ApertureKey(OWNER, "first");
            AscensionProgress var3 = AscensionProgress.empty()
               .withQi(new AscensionProgress.Qi(1.0, 2.0, 3.0, 4.0, 5.0, 6.0))
               .withInsight(2, 1)
               .withAppliedEffect("old");
            AscensionRecord var4 = new AscensionRecord(AscensionSession.prepare(var2), AscensionProgress.empty());
            var1.save(var4, -1L);
            AscensionRecord var5 = new AscensionRecord(SessionFlow.update(var4.session(), 0L, 1L, 0L), var3);
            var1.save(var5, 0L);
            Path var6 = recordFile(var0x);
            Properties var7 = read(var6);
            var7.setProperty("schema", "2");
            var7.remove("riskHeaven");
            var7.remove("riskEarth");
            var7.remove("riskHuman");
            var7.remove("windowTicks");
            var7.remove("windowId");
            write(var6, var7);
            AscensionRecord var8 = var1.find(var2).orElseThrow();
            check(var8.progress().qi().equals(var3.qi()));
            check(var8.progress().risk().equals(AscensionProgress.Risk.empty()));
            check(var8.progress().window().equals(AscensionProgress.Window.empty()));
            check(var8.progress().insightEarned() == 2 && var8.progress().appliedEffects().contains("old"));
            AscensionRecord var9 = new AscensionRecord(SessionFlow.update(var8.session(), 1L, 2L, 0L), var8.progress());
            var1.save(var9, 1L);
            check("11".equals(read(var6).getProperty("schema")));
         }
      );
      test("qi engine keeps human release finite", () -> {
         QiParameters var0x = params(100.0);
         AscensionProgress var1 = AscensionProgress.empty().withQi(new AscensionProgress.Qi(0.0, 0.0, 0.0, 0.0, 0.0, 5.0));
         QiEngine.Result var2 = QiEngine.step(var1, new QiIntent(0.0, 0.0, 1.0, false), 1L, var0x);
         check(!var2.fatal());
         check(var2.progress().qi().human() == 5.0 && var2.progress().qi().humanReserve() == 0.0);
         QiEngine.Result var3 = QiEngine.step(var2.progress(), new QiIntent(0.0, 0.0, 1.0, false), 2L, var0x);
         check(var3.progress().qi().human() == 5.0 && var3.progress().qi().humanReserve() == 0.0);
         check(var3.events().stream().noneMatch(var0xx -> var0xx.type() == QiEngine.EventType.INSIGHT_GRANTED));
      });
      test("qi engine applies directional lethal imbalance", () -> {
         QiParameters var0x = params(5.0);

         for (QiEngine.EventType var2 : List.of(QiEngine.EventType.IMBALANCE_HEAVEN, QiEngine.EventType.IMBALANCE_EARTH, QiEngine.EventType.IMBALANCE_HUMAN)) {
            AscensionProgress.Qi var3 = switch (var2) {
               case IMBALANCE_HEAVEN -> new AscensionProgress.Qi(100.0, 10.0, 10.0, 0.0, 0.0, 0.0);
               case IMBALANCE_EARTH -> new AscensionProgress.Qi(10.0, 100.0, 10.0, 0.0, 0.0, 0.0);
               case IMBALANCE_HUMAN -> new AscensionProgress.Qi(10.0, 10.0, 100.0, 0.0, 0.0, 0.0);
               default -> throw new AssertionError();
            };
            AscensionProgress var4 = AscensionProgress.empty().withQi(var3);
            boolean var5 = false;

            for (int var6 = 1; var6 <= 20 && !var5; var6++) {
               QiEngine.Result var7 = QiEngine.step(var4, QiIntent.idle(), var6, var0x);
               var4 = var7.progress();
               var5 = var7.fatal();
               if (var5) {
                  check(var7.events().stream().anyMatch(var1 -> var1.type() == var2));
               }

               var4 = var4.withQi(var3);
            }

            check(var5);
         }
      });
      test(
         "qi engine decays risk while balanced and opens a window",
         () -> {
            QiParameters var0x = params(20.0, 1.0);
            AscensionProgress var1 = AscensionProgress.empty()
               .withQi(new AscensionProgress.Qi(30.0, 30.0, 30.0, 0.0, 0.0, 100.0))
               .withRisk(new AscensionProgress.Risk(9.0, 9.0, 9.0));
            QiEngine.Result var2 = QiEngine.step(var1, QiIntent.idle(), 1L, var0x);
            check(var2.progress().window().ticks() == 1);
            check(var2.progress().window().id().equals("w1"));
            check(var2.progress().risk().heaven() < 9.0);
            QiEngine.Result var3 = QiEngine.step(var2.progress(), QiIntent.idle(), 2L, var0x);
            check(var3.progress().window().ticks() == 2);
         }
      );
      test("qi engine grants insight once per balanced window", () -> {
         QiParameters var0x = params(100.0);
         AscensionProgress var1 = AscensionProgress.empty().withQi(new AscensionProgress.Qi(30.0, 30.0, 30.0, 0.0, 0.0, 100.0));
         QiIntent var2 = new QiIntent(0.0, 0.0, 0.0, true);
         QiEngine.Result var3 = QiEngine.step(var1, var2, 1L, var0x);
         check(var3.progress().insightEarned() == 0);
         QiEngine.Result var4 = QiEngine.step(var3.progress(), var2, 2L, var0x);
         check(var4.progress().insightEarned() == 1);
         check(var4.progress().appliedEffects().contains("insight:w1"));
         QiEngine.Result var5 = QiEngine.step(var4.progress(), var2, 3L, var0x);
         check(var5.progress().insightEarned() == 1);
         QiEngine.Result var6 = QiEngine.step(var5.progress().withQi(AscensionProgress.Qi.empty()), QiIntent.idle(), 4L, var0x);
         check(var6.progress().window().equals(AscensionProgress.Window.empty()));
         AscensionProgress var7 = var6.progress().withQi(new AscensionProgress.Qi(30.0, 30.0, 30.0, 0.0, 0.0, 100.0));
         QiEngine.Result var8 = QiEngine.step(var7, QiIntent.idle(), 10L, var0x);
         check(var8.progress().window().id().equals("w10"));
         QiEngine.Result var9 = QiEngine.step(var8.progress(), var2, 11L, var0x);
         check(var9.progress().insightEarned() == 2);
         check(var9.progress().appliedEffects().contains("insight:w10"));
      });
      test("qi engine distraction raises risk without pausing qi", () -> {
         QiParameters var0x = params(3.0);
         AscensionProgress var1 = AscensionProgress.empty().withQi(new AscensionProgress.Qi(30.0, 30.0, 30.0, 0.0, 0.0, 100.0));
         QiIntent var2 = new QiIntent(0.0, 0.0, 0.0, true);
         QiEngine.Result var3 = QiEngine.step(var1, var2, 1L, var0x);
         check(var3.progress().risk().heaven() == 1.0);
         QiEngine.Result var4 = QiEngine.step(var3.progress(), var2, 2L, var0x);
         check(var4.progress().risk().heaven() == 2.0);
         QiEngine.Result var5 = QiEngine.step(var4.progress(), var2, 3L, var0x);
         check(var5.fatal());
         check(var5.progress().qi().heaven() == 30.0);
      });
      test("empty qi does not decay risk for free", () -> {
         AscensionProgress var0x = AscensionProgress.empty().withRisk(new AscensionProgress.Risk(5.0, 5.0, 5.0));
         QiEngine.Result var1 = QiEngine.step(var0x, QiIntent.idle(), 1L, params(100.0, 1.0));
         check(var1.progress().risk().equals(new AscensionProgress.Risk(5.0, 5.0, 5.0)));
         check(var1.progress().window().equals(AscensionProgress.Window.empty()));
      });
      test(
         "qi risk and window persist across repository restart",
         () -> {
            Path var0x = Files.createTempDirectory("shengxian-qi-persist-");
            FileSessionRepository var1 = new FileSessionRepository(var0x);
            ApertureKey var2 = new ApertureKey(OWNER, "first");
            AscensionRecord var3 = new AscensionRecord(AscensionSession.prepare(var2), AscensionProgress.empty());
            var1.save(var3, -1L);
            AscensionProgress var4 = AscensionProgress.empty()
               .withQi(new AscensionProgress.Qi(11.0, 12.0, 13.0, 14.0, 15.0, 16.0))
               .withRisk(new AscensionProgress.Risk(1.0, 2.0, 3.0))
               .withWindow(new AscensionProgress.Window(7, "w99"))
               .withInsight(4, 2)
               .withAppliedEffect("insight:w99");
            AscensionRecord var5 = new AscensionRecord(SessionFlow.update(var3.session(), 0L, 1L, 1L), var4);
            var1.save(var5, 0L);
            AscensionRecord var6 = new FileSessionRepository(var0x).find(var2).orElseThrow();
            check(var6.equals(var5));
            check(var6.progress().risk().equals(new AscensionProgress.Risk(1.0, 2.0, 3.0)));
            check(var6.progress().window().equals(new AscensionProgress.Window(7, "w99")));
         }
      );
      test("service steps qi only while absorbing and persists insight", () -> {
         Path var0x = Files.createTempDirectory("shengxian-service-qi-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         AscensionService var2 = new AscensionService(new FileSessionRepository(var0x), ALL);
         AscensionRecord var3 = absorbing(var2, var1);
         var3 = var2.mutate(var1, var3.session().stateRevision(), 4L, 3L, var0xx -> var0xx.withQi(new AscensionProgress.Qi(30.0, 30.0, 30.0, 0.0, 0.0, 100.0)));
         QiParameters var4 = params(100.0, 0.0);
         QiIntent var5 = new QiIntent(0.0, 0.0, 0.0, true);
         AscensionRecord var6 = var2.stepQi(var1, 4L, 5L, 4L, var5, var4);
         check(var6.progress().insightEarned() == 0);
         AscensionRecord var7 = var2.stepQi(var1, 5L, 6L, 5L, var5, var4);
         check(var7.progress().insightEarned() == 1);
         check(var7.progress().appliedEffects().contains("insight:w4"));
         AscensionRecord var8 = new AscensionService(new FileSessionRepository(var0x), ALL).load(var1);
         check(var8.equals(var7));
         AscensionRecord var9 = var2.start(new ApertureKey(OWNER, "second"), true, Optional.of(new RuleProfile("fixture", true, ALL)), 0L);
         expect(IllegalStateException.class, () -> var2.stepQi(var9.session().key(), 0L, 1L, 1L, var5, var4));
      });
      test(
         "lethal qi step commits FAILED and stops simulation",
         () -> {
            Path var0x = Files.createTempDirectory("shengxian-service-fatal-");
            ApertureKey var1 = new ApertureKey(OWNER, "first");
            AscensionService var2 = new AscensionService(new FileSessionRepository(var0x), ALL);
            AscensionRecord var3 = absorbing(var2, var1);
            var3 = var2.mutate(
               var1,
               var3.session().stateRevision(),
               4L,
               3L,
               var0xx -> var0xx.withQi(new AscensionProgress.Qi(100.0, 10.0, 10.0, 0.0, 0.0, 0.0))
                  .withTribulation(new AscensionProgress.Tribulation("t99", AscensionProgress.Tribulation.Type.SOUND_CHIMES, 8, 5))
            );
            QiParameters var4 = params(2.0, 0.0);
            AscensionRecord var5 = var2.stepQi(var1, 4L, 5L, 4L, QiIntent.idle(), var4);
            check(var5.session().phase() == Phase.ABSORBING && var5.progress().risk().heaven() == 1.0);
            AscensionRecord var6 = var2.stepQi(var1, 5L, 6L, 5L, QiIntent.idle(), var4);
            check(var6.session().phase() == Phase.FAILED && var6.session().broken());
            check(var6.progress().risk().heaven() == 2.0);
            check(!var6.progress().tribulation().active());
            check(var6.progress().failureConsequenceAvailable());
            check(var6.progress().failureCauses().contains(AscensionProgress.FailureCause.HEAVEN_OVERLOAD));
            long phaseRevision = var6.session().phaseRevision();
            long stateRevision = var6.session().stateRevision();
            SessionStore store = new SessionStore(var0x, ALL);
            UUID operationId = FailureSettlementIdentity.operationId(var6.session().id(), phaseRevision);
            expect(IOException.class, () -> store.acknowledgeFailure(var1, stateRevision, 7L, 6L, operationId));
            expect(IOException.class, () -> store.recordFailureRespawnReceipt(var1, stateRevision,
               7L, 6L, UUID.randomUUID()));
            AscensionRecord started = store.startFailure(var1, stateRevision, 7L, 6L);
            check(started.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.STARTED);
            check(started.session().phaseRevision() == phaseRevision);
            check(started.session().stateRevision() == stateRevision + 1L);
            AscensionRecord uncertain = store.markFailureUncertain(var1, started.session().stateRevision(), 8L, 7L);
            check(uncertain.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.UNCERTAIN);
            SessionStore restarted = new SessionStore(var0x, ALL);
            AscensionRecord receipt = restarted.recordFailureRespawnReceipt(var1,
               uncertain.session().stateRevision(), 9L, 8L, operationId);
            check(receipt.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.RESPAWN_RECEIPT);
            AscensionRecord acknowledged = restarted.acknowledgeFailure(var1,
               receipt.session().stateRevision(), 10L, 9L, operationId);
            check(acknowledged.session().phase() == Phase.FAILED);
            check(acknowledged.session().phaseRevision() == phaseRevision);
            check(acknowledged.session().stateRevision() == stateRevision + 4L);
            check(!acknowledged.progress().failureConsequenceAvailable());
            check(restarted.acknowledgeFailure(var1, stateRevision, 7L, 6L, operationId).equals(acknowledged));
            expect(IllegalStateException.class, () -> var2.stepQi(var1, var6.session().stateRevision(), 7L, 6L, QiIntent.idle(), var4));
         }
      );
      test("new failure records begin in PREPARED settlement state", () -> {
         AscensionProgress pending = AscensionProgress.empty().withFailurePending(
            Set.of(AscensionProgress.FailureCause.HUMAN_OVERLOAD));
         check(pending.appliedEffects().contains("failure:settlement:prepared"));
      });
      test("failure settlement state resolves markers conservatively", () -> {
         AscensionProgress empty = AscensionProgress.empty();
         check("NONE".equals(failureSettlementStateName(empty)));
         AscensionProgress pending = empty.withFailurePending(
            Set.of(AscensionProgress.FailureCause.HUMAN_OVERLOAD));
         check("PREPARED".equals(failureSettlementStateName(pending)));
         expect(IllegalStateException.class, pending::withFailureRespawnReceipt);
         AscensionProgress started = pending.withAppliedEffect("failure:settlement:started");
         check("STARTED".equals(failureSettlementStateName(started)));
         AscensionProgress uncertain = started.withAppliedEffect("failure:settlement:uncertain");
         check("UNCERTAIN".equals(failureSettlementStateName(uncertain)));
         AscensionProgress receipt = uncertain.withAppliedEffect("failure:settlement:respawn-receipt");
         check("RESPAWN_RECEIPT".equals(failureSettlementStateName(receipt)));
         AscensionProgress acknowledged = receipt.withAppliedEffect(AscensionProgress.FAILURE_ACKNOWLEDGED_EFFECT);
         check("ACKNOWLEDGED".equals(failureSettlementStateName(acknowledged)));
         AscensionProgress legacy = empty.withAppliedEffect("failure:cause:human_overload");
         check("UNCERTAIN".equals(failureSettlementStateName(legacy)));
      });
      test("failure operation identity is stable and phase-bound", () -> {
         UUID session = UUID.fromString("11111111-2222-3333-4444-555555555555");
         UUID first = FailureSettlementIdentity.operationId(session, 7L);
         check(first.equals(FailureSettlementIdentity.operationId(session, 7L)));
         check(!first.equals(FailureSettlementIdentity.operationId(session, 8L)));
         check(!first.equals(FailureSettlementIdentity.operationId(UUID.randomUUID(), 7L)));
         expect(IllegalArgumentException.class,
            () -> FailureSettlementIdentity.operationId(session, -1L));
         expect(IllegalArgumentException.class,
            () -> FailureSettlementIdentity.operationId(null, 7L));
      });
      test("failure recovery policy never retries ambiguous settlements", () -> {
         UUID operation = UUID.fromString("11111111-2222-3333-4444-555555555555");
         var cause = AscensionProgress.FailureCause.HEAVEN_OVERLOAD;
         check(FailureSettlementPolicy.decide(AscensionProgress.FailureSettlementState.PREPARED,
            operation, cause, Optional.empty(), false) == FailureSettlementPolicy.Action.ATTEMPT_ONCE);
         var started = new FailureSettlementPolicy.PlayerReceipt(operation, cause,
            FailureSettlementPolicy.ReceiptStage.STARTED);
         check(FailureSettlementPolicy.decide(AscensionProgress.FailureSettlementState.PREPARED,
            operation, cause, Optional.of(started), true) == FailureSettlementPolicy.Action.MARK_UNCERTAIN);
         check(FailureSettlementPolicy.decide(AscensionProgress.FailureSettlementState.STARTED,
            operation, cause, Optional.of(started), true) == FailureSettlementPolicy.Action.MARK_UNCERTAIN);
         check(FailureSettlementPolicy.decide(AscensionProgress.FailureSettlementState.UNCERTAIN,
            operation, cause, Optional.empty(), false) == FailureSettlementPolicy.Action.NOOP);
         var receipt = new FailureSettlementPolicy.PlayerReceipt(operation, cause,
            FailureSettlementPolicy.ReceiptStage.RESPAWN_RECEIPT);
         check(FailureSettlementPolicy.decide(AscensionProgress.FailureSettlementState.UNCERTAIN,
            operation, cause, Optional.of(receipt), true) == FailureSettlementPolicy.Action.ACKNOWLEDGE);
         check(FailureSettlementPolicy.decide(AscensionProgress.FailureSettlementState.STARTED,
            operation, cause, Optional.of(new FailureSettlementPolicy.PlayerReceipt(UUID.randomUUID(), cause,
               FailureSettlementPolicy.ReceiptStage.RESPAWN_RECEIPT)), true)
            == FailureSettlementPolicy.Action.MARK_UNCERTAIN);
         check(FailureSettlementPolicy.decide(AscensionProgress.FailureSettlementState.ACKNOWLEDGED,
            operation, cause, Optional.of(receipt), true) == FailureSettlementPolicy.Action.NOOP);
      });
      test("tribulation impact settlement needs matching provenance and never retries uncertainty", () -> {
         UUID operation = UUID.fromString("11111111-2222-3333-4444-555555555556");
         var context = new AscensionProgress.TribulationImpactFailure(
               TribulationParameters.Profile.TAI_BAI_WITNESSED,
               AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND, "heaven-proof", 2L);
         check(FailureSettlementPolicy.decideTribulationImpact(
               AscensionProgress.FailureSettlementState.PREPARED, operation, context,
               Optional.empty(), false) == FailureSettlementPolicy.Action.ATTEMPT_ONCE);
         var started = new FailureSettlementPolicy.TribulationImpactPlayerReceipt(operation, context,
               FailureSettlementPolicy.ReceiptStage.STARTED);
         check(FailureSettlementPolicy.decideTribulationImpact(
               AscensionProgress.FailureSettlementState.PREPARED, operation, context,
               Optional.of(started), true) == FailureSettlementPolicy.Action.MARK_UNCERTAIN);
         var completed = new FailureSettlementPolicy.TribulationImpactPlayerReceipt(operation, context,
               FailureSettlementPolicy.ReceiptStage.RESPAWN_RECEIPT);
         check(FailureSettlementPolicy.decideTribulationImpact(
               AscensionProgress.FailureSettlementState.UNCERTAIN, operation, context,
               Optional.of(completed), true) == FailureSettlementPolicy.Action.ACKNOWLEDGE);
         check(FailureSettlementPolicy.decideTribulationImpact(
               AscensionProgress.FailureSettlementState.STARTED, operation, context,
               Optional.empty(), false) == FailureSettlementPolicy.Action.MARK_UNCERTAIN);
         check(FailureSettlementPolicy.decideTribulationImpact(
               AscensionProgress.FailureSettlementState.UNCERTAIN, operation, context,
               Optional.empty(), false) == FailureSettlementPolicy.Action.NOOP);
         check(FailureSettlementPolicy.decideTribulationImpact(
               AscensionProgress.FailureSettlementState.PREPARED, operation, context,
               Optional.of(new FailureSettlementPolicy.TribulationImpactPlayerReceipt(
                     UUID.randomUUID(), context, FailureSettlementPolicy.ReceiptStage.RESPAWN_RECEIPT)), true)
               == FailureSettlementPolicy.Action.MARK_UNCERTAIN);
         var otherSource = new AscensionProgress.TribulationImpactFailure(
               TribulationParameters.Profile.FANG_ZHENG_WITNESSED,
               AscensionProgress.Tribulation.Type.BLOOD_RAIN, "fang-zheng-blood", 2L);
         check(FailureSettlementPolicy.decideTribulationImpact(
               AscensionProgress.FailureSettlementState.PREPARED, operation, context,
               Optional.of(new FailureSettlementPolicy.TribulationImpactPlayerReceipt(
                     operation, otherSource, FailureSettlementPolicy.ReceiptStage.RESPAWN_RECEIPT)), true)
               == FailureSettlementPolicy.Action.MARK_UNCERTAIN);
      });
      test("failure status formatter exposes both persisted settlement sides", () -> {
         UUID operation = UUID.fromString("11111111-2222-3333-4444-555555555555");
         var cause = AscensionProgress.FailureCause.HEAVEN_OVERLOAD;
         String absent = FailureSettlementDiagnostic.format(AscensionProgress.FailureSettlementState.PREPARED,
            operation, Optional.of(cause), Optional.empty(), false);
         check(absent.contains("世界=PREPARED") && absent.contains("操作ID=" + operation)
            && absent.endsWith("玩家收据=NONE"));
         var receipt = new FailureSettlementPolicy.PlayerReceipt(operation, cause,
            FailureSettlementPolicy.ReceiptStage.RESPAWN_RECEIPT);
         check(FailureSettlementDiagnostic.format(AscensionProgress.FailureSettlementState.ACKNOWLEDGED,
            operation, Optional.of(cause), Optional.of(receipt), true).endsWith("玩家收据=RESPAWN_RECEIPT"));
         check(FailureSettlementDiagnostic.format(AscensionProgress.FailureSettlementState.UNCERTAIN,
            operation, Optional.of(cause), Optional.of(new FailureSettlementPolicy.PlayerReceipt(UUID.randomUUID(), cause,
               FailureSettlementPolicy.ReceiptStage.STARTED)), true).endsWith("玩家收据=MISMATCH"));
         check(FailureSettlementDiagnostic.format(AscensionProgress.FailureSettlementState.UNCERTAIN,
            operation, Optional.of(cause), Optional.empty(), true).endsWith("玩家收据=MALFORMED"));
         var impactContext = new AscensionProgress.TribulationImpactFailure(
               TribulationParameters.Profile.TAI_BAI_WITNESSED,
               AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND, "heaven-proof", 2L);
         var impactReceipt = new FailureSettlementPolicy.TribulationImpactPlayerReceipt(operation,
               impactContext, FailureSettlementPolicy.ReceiptStage.RESPAWN_RECEIPT);
         String impact = FailureSettlementDiagnostic.formatTribulationImpact(
               AscensionProgress.FailureSettlementState.ACKNOWLEDGED, operation, Optional.of(impactContext),
               Optional.of(impactReceipt), true);
         check(impact.contains("profile=TAI_BAI_WITNESSED")
               && impact.contains("carrier=JADE_WILLOW_WIND/heaven-proof")
               && impact.contains("冲击序号=2") && impact.endsWith("玩家回执=RESPAWN_RECEIPT"));
         String mismatch = FailureSettlementDiagnostic.formatTribulationImpact(
               AscensionProgress.FailureSettlementState.PREPARED, operation, Optional.of(impactContext),
               Optional.of(new FailureSettlementPolicy.TribulationImpactPlayerReceipt(UUID.randomUUID(),
                     impactContext, FailureSettlementPolicy.ReceiptStage.STARTED)), true);
         check(mismatch.endsWith("玩家回执=MISMATCH"));
      });
      test("failed session persists PREPARED to STARTED before effect", () -> {
         Path root = Files.createTempDirectory("shengxian-failure-settlement-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         FileSessionRepository repository = new FileSessionRepository(root);
         AscensionService service = new AscensionService(repository, ALL);
         AscensionRecord active = absorbing(service, key);
         active = service.mutate(key, active.session().stateRevision(), 4L, 3L,
            progress -> progress.withQi(new AscensionProgress.Qi(100.0, 10.0, 10.0, 0.0, 0.0, 0.0)));
         service.stepQi(key, 4L, 5L, 4L, QiIntent.idle(), params(2.0, 0.0));
         AscensionRecord prepared = service.stepQi(key, 5L, 6L, 5L, QiIntent.idle(), params(2.0, 0.0));
         check(prepared.session().phase() == Phase.FAILED);
         AscensionSession skippedSession = SessionFlow.acknowledgeFailure(prepared.session(),
            prepared.session().stateRevision(), 7L, 6L);
         AscensionProgress skippedProgress = prepared.progress()
            .withAppliedEffect(AscensionProgress.FAILURE_RESPAWN_RECEIPT_EFFECT)
            .withAppliedEffect(AscensionProgress.FAILURE_ACKNOWLEDGED_EFFECT);
         expect(IOException.class, () -> repository.save(
            new AscensionRecord(skippedSession, skippedProgress), prepared.session().stateRevision()));
         AscensionProgress changedCause = prepared.progress()
            .withAppliedEffect("failure:cause:earth_overload")
            .withAppliedEffect(AscensionProgress.FAILURE_STARTED_EFFECT);
         AscensionSession changedSession = SessionFlow.acknowledgeFailure(prepared.session(),
            prepared.session().stateRevision(), 7L, 6L);
         expect(IOException.class, () -> repository.save(
            new AscensionRecord(changedSession, changedCause), prepared.session().stateRevision()));
         AscensionRecord started = service.startFailure(key, prepared.session().stateRevision(), 7L, 6L);
         check(repository.find(key).orElseThrow().progress().failureSettlementState()
            == AscensionProgress.FailureSettlementState.STARTED);
         AscensionRecord uncertain = service.markFailureUncertain(key,
            started.session().stateRevision(), 8L, 7L);
         check(new FileSessionRepository(root).find(key).orElseThrow().equals(uncertain));
         UUID operationId = FailureSettlementIdentity.operationId(uncertain.session().id(),
            uncertain.session().phaseRevision());
         AscensionRecord receipt = service.recordFailureRespawnReceipt(key,
            uncertain.session().stateRevision(), 9L, 8L, operationId);
         check(new FileSessionRepository(root).find(key).orElseThrow().equals(receipt));
         AscensionRecord acknowledged = service.acknowledgeFailure(key,
            receipt.session().stateRevision(), 10L, 9L, operationId);
         check(new FileSessionRepository(root).find(key).orElseThrow().equals(acknowledged));
      });
      test(
         "schema 3 records migrate with an empty tribulation",
         () -> {
            Path var0x = Files.createTempDirectory("shengxian-schema3-");
            ApertureKey var1 = new ApertureKey(OWNER, "first");
            FileSessionRepository var2 = new FileSessionRepository(var0x);
            AscensionRecord var3 = new AscensionRecord(AscensionSession.prepare(var1), AscensionProgress.empty());
            var2.save(var3, -1L);
            AscensionProgress var4 = AscensionProgress.empty()
               .withQi(new AscensionProgress.Qi(1.0, 2.0, 3.0, 4.0, 5.0, 6.0))
               .withRisk(new AscensionProgress.Risk(1.0, 2.0, 3.0))
               .withWindow(new AscensionProgress.Window(2, "w2"))
               .withAppliedEffect("old3");
            var2.save(new AscensionRecord(SessionFlow.update(var3.session(), 0L, 1L, 1L), var4), 0L);
            Path var5 = recordFile(var0x);
            Properties var6 = read(var5);
            var6.setProperty("schema", "3");
            var6.remove("tribulationId");
            var6.remove("tribulationType");
            var6.remove("tribulationRemaining");
            var6.remove("tribulationTicks");
            write(var5, var6);
            AscensionRecord var7 = var2.find(var1).orElseThrow();
            check(var7.progress().tribulation().equals(AscensionProgress.Tribulation.empty()));
            check(var7.progress().risk().equals(var4.risk()));
            check(var7.progress().window().equals(var4.window()));
            check(var7.progress().appliedEffects().contains("old3"));
            var2.save(new AscensionRecord(SessionFlow.update(var7.session(), 1L, 2L, 1L), var7.progress()), 1L);
            check("11".equals(read(var5).getProperty("schema")));
         }
      );
      test("tribulation starts only from dense ambient qi", () -> {
         TribulationParameters var0x = new TribulationParameters(10.0, 2, 3, 1, 4, 5.0);
         AscensionProgress var1 = AscensionProgress.empty().withQi(new AscensionProgress.Qi(0.0, 0.0, 0.0, 4.0, 5.0, 0.0));
         TribulationEngine.Result var2 = TribulationEngine.startIfDue(var1, 5L, var0x);
         check(!var2.progress().tribulation().active());
         AscensionProgress var3 = AscensionProgress.empty().withQi(new AscensionProgress.Qi(0.0, 0.0, 0.0, 8.0, 7.0, 0.0));
         TribulationEngine.Result var4 = TribulationEngine.startIfDue(var3, 5L, var0x);
         check(var4.progress().tribulation().active());
         check(var4.progress().tribulation().type() == AscensionProgress.Tribulation.Type.SOUND_CHIMES);
         check(var4.progress().tribulation().remaining() == 2);
         AscensionProgress var5 = AscensionProgress.empty().withQi(new AscensionProgress.Qi(0.0, 0.0, 0.0, 4.0, 9.0, 0.0));
         TribulationEngine.Result var6 = TribulationEngine.startIfDue(var5, 6L, var0x);
         check(var6.progress().tribulation().type() == AscensionProgress.Tribulation.Type.ANCIENT_BEAST);
         TribulationEngine.Result var7 = TribulationEngine.startIfDue(var6.progress(), 7L, var0x);
         check(var7.events().isEmpty() && var7.progress().tribulation().id().equals("t6"));
      });
      test("Taibai witnessed weather and earth carriers start independently and can coexist", () -> {
         AscensionProgress mixedSupply = AscensionProgress.empty().withQi(
               new AscensionProgress.Qi(0.0, 0.0, 0.0, 40.0, 40.0, 0.0));
         // No individual case is privileged: the ordinary route runs the same ambient-qi rule.
         check(TribulationEngine.startIfDue(mixedSupply, 40L, TribulationParameters.standard())
               .progress().tribulations().size() == 2);
         TribulationParameters parameters = TribulationParameters.taiBaiWitnessed();
         AscensionProgress balancedSupply = AscensionProgress.empty().withQi(
               new AscensionProgress.Qi(0.0, 0.0, 0.0, 40.0, 40.0, 0.0));
         AscensionProgress both = TribulationEngine.startIfDue(balancedSupply, 41L, parameters).progress();
         check(both.tribulations().size() == 2);
         check(both.tribulations().stream().anyMatch(t -> t.type() == AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND));
         check(both.tribulations().stream().anyMatch(t -> t.type() == AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER));
         String heavenId = both.tribulations().stream()
               .filter(t -> t.type() == AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND)
               .findFirst().orElseThrow().id();
         AscensionProgress afterGuardian = TribulationEngine.strike(both, heavenId, 100).progress();
         check(afterGuardian.tribulations().size() == 1);
         check(afterGuardian.tribulations().getFirst().type() == AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER);
         check(afterGuardian.qi().equals(both.qi()));
         AscensionProgress heavenOnly = AscensionProgress.empty().withQi(
               new AscensionProgress.Qi(0.0, 0.0, 0.0, 40.0, 0.0, 0.0));
         AscensionProgress onlyWeather = TribulationEngine.startIfDue(heavenOnly, 42L, parameters).progress();
         check(onlyWeather.tribulations().size() == 1);
         check(onlyWeather.tribulations().getFirst().type() == AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND);
      });      test("Hei Loulan witnessed profile sequences eight chimes before its earth beast", () -> {
         TribulationParameters parameters = TribulationParameters.heiLoulanWitnessed();
         AscensionProgress supply = AscensionProgress.empty().withQi(
               new AscensionProgress.Qi(0, 0, 0, 30, 30, 0));
         AscensionProgress chimes = TribulationEngine.startIfDue(supply, 55L, parameters).progress();
         check(chimes.tribulation().type() == AscensionProgress.Tribulation.Type.SOUND_CHIMES);
         check(chimes.tribulation().remaining() == 8);
         check(TribulationEngine.startIfDue(chimes, 56L, parameters).events().isEmpty());
         AscensionProgress cleared = TribulationEngine.strike(chimes, chimes.tribulation().id(), 8).progress();
         AscensionProgress beast = TribulationEngine.startIfDue(cleared, 57L, parameters).progress();
         check(beast.tribulation().type() == AscensionProgress.Tribulation.Type.ANCIENT_BEAST);
      });
      test("ordinary route runs the same ambient-qi calamity rule as the witnessed examples", () -> {
         // Project decision (canon-rules 项目决策): the ambient-qi rule is the engine for every
         // ascension. The witnessed examples are instances, not permissions, so the ordinary
         // profile is no longer inert.
         check(TribulationParameters.standard().profile() == TribulationParameters.Profile.UNRESOLVED_ORDINARY);
         AscensionProgress dense = AscensionProgress.empty().withQi(new AscensionProgress.Qi(0, 0, 0, 500, 500, 0));
         TribulationEngine.Result started = TribulationEngine.startIfDue(dense, 900L, TribulationParameters.standard());
         check(started.progress().tribulations().size() == 2);
         check(started.events().size() == 2);
         check(started.progress().qi().ambientHeaven() < dense.qi().ambientHeaven());
         check(started.progress().qi().ambientEarth() < dense.qi().ambientEarth());
         // The same input through the witnessed profile must behave identically: no privileged case.
         TribulationEngine.Result witnessed = TribulationEngine.startIfDue(dense, 900L,
               TribulationParameters.taiBaiWitnessed());
         check(witnessed.progress().tribulations().size() == started.progress().tribulations().size());
         check(witnessed.progress().qi().equals(started.progress().qi()));
         // A thin supply still starts nothing, so the rule stays threshold-driven rather than free.
         AscensionProgress thin = AscensionProgress.empty().withQi(new AscensionProgress.Qi(0, 0, 0, 5, 5, 0));
         check(TribulationEngine.startIfDue(thin, 901L, TribulationParameters.standard())
               .progress().tribulations().isEmpty());
      });
      test("server calamity profile defaults unresolved and excludes internal profiles", () -> {
         check(TribulationParameters.configuredProfile(null)
               == TribulationParameters.Profile.UNRESOLVED_ORDINARY);
         check(TribulationParameters.configuredProfile("LEGACY_TEST_FIXTURE")
               == TribulationParameters.Profile.UNRESOLVED_ORDINARY);
         check(TribulationParameters.forProfile(TribulationParameters.Profile.UNRESOLVED_ORDINARY)
               .profile() == TribulationParameters.Profile.UNRESOLVED_ORDINARY);
         check(TribulationParameters.forProfile(TribulationParameters.Profile.TAI_BAI_WITNESSED)
               .profile() == TribulationParameters.Profile.TAI_BAI_WITNESSED);
         check(TribulationParameters.forProfile(TribulationParameters.Profile.BLACK_LOU_LAN_WITNESSED)
               .profile() == TribulationParameters.Profile.BLACK_LOU_LAN_WITNESSED);
         check(TribulationParameters.forProfile(TribulationParameters.Profile.FANG_ZHENG_WITNESSED)
               .profile() == TribulationParameters.Profile.FANG_ZHENG_WITNESSED);
         expect(IllegalArgumentException.class, () -> TribulationParameters.forProfile(
               TribulationParameters.Profile.NOT_FROZEN));
         expect(IllegalArgumentException.class, () -> TribulationParameters.forProfile(
               TribulationParameters.Profile.LEGACY_TEST_FIXTURE));
      });
      test("session freezes its first witnessed profile and ignores later selection changes", () -> {
         AscensionProgress supply = AscensionProgress.empty().withQi(
               new AscensionProgress.Qi(0, 0, 0, 60, 60, 0));
         AscensionProgress first = TribulationEngine.startIfDue(supply, 100L,
               TribulationParameters.forProfile(TribulationParameters.Profile.TAI_BAI_WITNESSED)).progress();
         check(first.tribulationProfile() == TribulationParameters.Profile.TAI_BAI_WITNESSED);
         for (AscensionProgress.Tribulation carrier : List.copyOf(first.tribulations())) {
            first = TribulationEngine.strike(first, carrier.id(), 100).progress();
         }
         AscensionProgress changedConfig = first.withQi(new AscensionProgress.Qi(0, 0, 0, 60, 60, 0));
         AscensionProgress resumed = TribulationEngine.startIfDue(changedConfig, 101L,
               TribulationParameters.forProfile(TribulationParameters.Profile.FANG_ZHENG_WITNESSED)).progress();
         check(resumed.tribulationProfile() == TribulationParameters.Profile.TAI_BAI_WITNESSED);
         check(resumed.tribulations().stream().anyMatch(t ->
               t.type() == AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND));
         check(resumed.tribulations().stream().anyMatch(t ->
               t.type() == AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER));
         check(resumed.tribulations().stream().noneMatch(t ->
               t.type() == AscensionProgress.Tribulation.Type.BLOOD_RAIN));
      });
      test("witnessed impact progress tracks pressure and source separately from qi risk", () -> {
         AscensionProgress progress = AscensionProgress.empty()
               .withTribulationProfile(TribulationParameters.Profile.TAI_BAI_WITNESSED)
               .withRisk(new AscensionProgress.Risk(1.0, 2.0, 3.0))
               .withTribulations(List.of(new AscensionProgress.Tribulation(
                     "heaven-proof", AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND, 2, 1)));
         AscensionProgress.Tribulation carrier = progress.tribulation();

         AscensionProgress first = progress.withTribulationImpact(carrier.id(), 2.0, false);
         check(first.tribulationPressure() == 2.0);
         check(first.tribulationImpactSequence() == 1L);
         check(first.risk().equals(progress.risk()));
         check(first.tribulationImpactFailure().isEmpty());

         AscensionProgress terminal = first.withTribulationImpact(carrier.id(), 3.0, true);
         AscensionProgress.TribulationImpactFailure source = terminal.tribulationImpactFailure().orElseThrow();
         check(terminal.tribulationPressure() == 5.0);
         check(terminal.tribulationImpactSequence() == 2L);
         check(terminal.risk().equals(progress.risk()));
         check(source.profile() == TribulationParameters.Profile.TAI_BAI_WITNESSED);
         check(source.carrierType() == carrier.type());
         check(source.carrierId().equals(carrier.id()));
         check(source.impactSequence() == 2L);
      });
      test("Fang Zheng witnessed blood rain needs both qi sources and never restarts", () -> {
         TribulationParameters parameters = TribulationParameters.fangZhengWitnessed();
         AscensionProgress heavenOnly = AscensionProgress.empty().withQi(new AscensionProgress.Qi(0, 0, 0, 40, 0, 0));
         check(TribulationEngine.startIfDue(heavenOnly, 70L, parameters).progress().tribulations().isEmpty());
         AscensionProgress earthOnly = AscensionProgress.empty().withQi(new AscensionProgress.Qi(0, 0, 0, 0, 40, 0));
         check(TribulationEngine.startIfDue(earthOnly, 71L, parameters).progress().tribulations().isEmpty());
         AscensionProgress both = AscensionProgress.empty().withQi(new AscensionProgress.Qi(0, 0, 0, 40, 40, 0));
         TribulationEngine.Result started = TribulationEngine.startIfDue(both, 72L, parameters);
         check(started.progress().tribulations().size() == 1);
         AscensionProgress.Tribulation rain = started.progress().tribulations().getFirst();
         check(rain.type() == AscensionProgress.Tribulation.Type.BLOOD_RAIN);
         check(rain.remaining() == 3);
         check(started.progress().qi().ambientHeaven() == 10.0);
         check(started.progress().qi().ambientEarth() == 10.0);
         check(TribulationEngine.startIfDue(started.progress(), 73L, parameters).events().isEmpty());
         AscensionProgress cleared = TribulationEngine.strike(started.progress(), rain.id(), 100).progress();
         check(cleared.tribulations().isEmpty());
         check(cleared.qi().equals(started.progress().qi()));
         AscensionProgress denseAgain = cleared.withQi(new AscensionProgress.Qi(0, 0, 0, 40, 40, 0));
         check(TribulationEngine.startIfDue(denseAgain, 74L, parameters).progress().tribulations().isEmpty());
      });
      test("Fang Zheng blood rain is weakened by guardian strikes without touching qi or risk", () -> {
         TribulationParameters parameters = TribulationParameters.fangZhengWitnessed();
         AscensionProgress supply = AscensionProgress.empty().withQi(new AscensionProgress.Qi(0, 0, 0, 40, 40, 0));
         AscensionProgress raining = TribulationEngine.startIfDue(supply, 80L, parameters).progress();
         String id = raining.tribulations().getFirst().id();
         AscensionProgress struck = TribulationEngine.strike(raining, id, 1).progress();
         check(struck.tribulations().getFirst().remaining() == 2);
         check(struck.qi().equals(raining.qi()));
         check(struck.risk().equals(raining.risk()));
         check(TribulationEngine.strike(struck, "missing-carrier", 1).progress().equals(struck));
      });
      test("the selected calamities have distinct impact cues", () -> {
         List<String> cues = List.of(
               TribulationFx.impactCue(AscensionProgress.Tribulation.Type.SOUND_CHIMES),
               TribulationFx.impactCue(AscensionProgress.Tribulation.Type.ANCIENT_BEAST),
               TribulationFx.impactCue(AscensionProgress.Tribulation.Type.BLOOD_RAIN));
         check(new HashSet<>(cues).size() == 3);
         check(TribulationFx.impactCue(AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND)
               .equals("tribulation_impact"));
         check(TribulationFx.impactCue(AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER)
               .equals("tribulation_impact"));
         check(TribulationFx.impactCue(AscensionProgress.Tribulation.Type.NONE).equals("tribulation_impact"));
      });
      test("witnessed tribulation pressure can be lethal without changing qi risk", () -> {
         TribulationParameters parameters = new TribulationParameters(10.0, 2, 3, 1, 4, 5.0,
               TribulationParameters.Profile.TAI_BAI_WITNESSED);
         AscensionProgress initial = AscensionProgress.empty()
               .withRisk(new AscensionProgress.Risk(1.0, 2.0, 3.0))
               .withQi(new AscensionProgress.Qi(0.0, 0.0, 0.0, 8.0, 0.0, 0.0));
         AscensionProgress started = TribulationEngine.startIfDue(initial, 1L, parameters).progress();
         TribulationEngine.Result first = TribulationEngine.tick(started, parameters, 10.0);
         TribulationEngine.Result second = TribulationEngine.tick(first.progress(), parameters, 10.0);
         TribulationEngine.Result third = TribulationEngine.tick(second.progress(), parameters, 10.0);
         check(third.progress().tribulationPressure() == 5.0);
         check(third.progress().tribulationImpactSequence() == 1L);
         check(third.progress().risk().equals(initial.risk()));
         check(third.progress().qi().equals(started.qi()));
         TribulationEngine.Result fourth = TribulationEngine.tick(third.progress(), parameters, 10.0);
         TribulationEngine.Result fifth = TribulationEngine.tick(fourth.progress(), parameters, 10.0);
         TribulationEngine.Result sixth = TribulationEngine.tick(fifth.progress(), parameters, 10.0);
         check(sixth.fatal());
         check(sixth.progress().tribulations().isEmpty());
         check(sixth.progress().tribulationPressure() == 10.0);
         check(sixth.progress().tribulationImpactSequence() == 2L);
         check(sixth.progress().risk().equals(initial.risk()));
         check(sixth.impactFailure().orElseThrow().profile()
               == TribulationParameters.Profile.TAI_BAI_WITNESSED);
         check(sixth.impactFailure().orElseThrow().impactSequence() == 2L);
      });
      test("guardian strikes carriers without touching qi and grant no loot", () -> {
         TribulationParameters var0x = new TribulationParameters(10.0, 2, 3, 1, 4, 5.0);
         AscensionProgress var1 = AscensionProgress.empty().withQi(new AscensionProgress.Qi(30.0, 30.0, 30.0, 8.0, 8.0, 100.0));
         var1 = TribulationEngine.startIfDue(var1, 1L, var0x).progress();
         TribulationEngine.Result var2 = TribulationEngine.strike(var1, 1);
         check(var2.progress().tribulation().remaining() == 1);
         check(var2.progress().qi().equals(var1.qi()));
         TribulationEngine.Result var3 = TribulationEngine.strike(var2.progress(), 1);
         check(!var3.progress().tribulation().active());
         check(var3.progress().appliedEffects().contains("tribulation:t1:survived"));
         TribulationEngine.Result var4 = TribulationEngine.strike(var3.progress(), 1);
         check(var4.events().isEmpty());
         check(var4.progress().appliedEffects().size() == 1);
      });
      test("schema 8 persists world carrier anchors and migrates schema 7 without losing disasters", () -> {
         Path directory = Files.createTempDirectory("shengxian-tribulation-schema8-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         FileSessionRepository repository = new FileSessionRepository(directory);
         AscensionRecord created = new AscensionRecord(AscensionSession.prepare(key), AscensionProgress.empty());
         repository.save(created, -1L);
         var heavenAnchor = new AscensionProgress.CarrierAnchor("minecraft:overworld", 12, 78, -4,
               UUID.fromString("00000000-0000-0000-0000-000000000123"));
         var earthAnchor = new AscensionProgress.CarrierAnchor("minecraft:the_nether", -30, 64, 19,
               UUID.fromString("00000000-0000-0000-0000-000000000456"));
         var heaven = new AscensionProgress.Tribulation("heaven-11",
               AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND, 8, 17, heavenAnchor);
         var earth = new AscensionProgress.Tribulation("earth-11",
               AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER, 3, 23, earthAnchor);
         AscensionProgress progress = AscensionProgress.empty().withTribulations(List.of(heaven, earth));
         AscensionRecord update = new AscensionRecord(SessionFlow.update(created.session(), 0L, 1L, 1L), progress);
         repository.save(update, 0L);
         check(new FileSessionRepository(directory).find(key).orElseThrow().equals(update));
         Path file = recordFile(directory);
         check("11".equals(read(file).getProperty("schema")));

         Properties legacy = read(file);
         legacy.setProperty("schema", "7");
         for (int i = 0; i < 2; i++) {
            String prefix = "tribulation." + i + ".";
            legacy.remove(prefix + "dimension");
            legacy.remove(prefix + "x");
            legacy.remove(prefix + "y");
            legacy.remove(prefix + "z");
            legacy.remove(prefix + "entity");
         }
         write(file, legacy);
         AscensionRecord migrated = repository.find(key).orElseThrow();
         check(migrated.progress().tribulations().size() == 2);
         check(migrated.progress().tribulations().stream().allMatch(t ->
               t.carrierAnchor().equals(AscensionProgress.CarrierAnchor.unplaced())));
      });
      test("schema 11 persists impact state and migrates active schema 8 sessions inertly", () -> {
         Path directory = Files.createTempDirectory("shengxian-tribulation-schema9-");
         ApertureKey key = new ApertureKey(OWNER, "impact");
         FileSessionRepository repository = new FileSessionRepository(directory);
         AscensionRecord created = new AscensionRecord(AscensionSession.prepare(key), AscensionProgress.empty());
         repository.save(created, -1L);
         var anchor = new AscensionProgress.CarrierAnchor("minecraft:overworld", 14, 72, 9,
               UUID.fromString("00000000-0000-0000-0000-000000000789"));
         var carrier = new AscensionProgress.Tribulation("heaven-schema9",
               AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND, 3, 5, anchor);
         AscensionProgress witnessed = AscensionProgress.empty()
               .withTribulationProfile(TribulationParameters.Profile.TAI_BAI_WITNESSED)
               .withTribulations(List.of(carrier))
               .withTribulationImpact(carrier.id(), 2.0, false)
               .withTribulationImpact(carrier.id(), 3.0, true);
         AscensionRecord update = new AscensionRecord(SessionFlow.update(created.session(), 0L, 1L, 1L), witnessed);
         repository.save(update, 0L);
         check("11".equals(read(recordFile(directory)).getProperty("schema")));
         check(repository.find(key).orElseThrow().equals(update));

         Path file = recordFile(directory);
         Properties legacy = read(file);
         legacy.setProperty("schema", "8");
         legacy.setProperty("phase", Phase.ABSORBING.name());
         legacy.setProperty("broken", "true");
         legacy.setProperty("phaseRevision", "3");
         legacy.setProperty("stateRevision", "3");
         legacy.setProperty("sequence", "3");
         for (String name : List.of("tribulationProfile", "tribulationPressure", "tribulationImpactSequence",
               "tribulationImpactFailurePresent", "tribulationImpactFailure.profile",
               "tribulationImpactFailure.carrierType", "tribulationImpactFailure.carrierId",
               "tribulationImpactFailure.sequence")) legacy.remove(name);
         write(file, legacy);

         AscensionRecord migrated = repository.find(key).orElseThrow();
         check(migrated.session().phase() == Phase.ABSORBING);
         check(migrated.progress().tribulationProfile() == TribulationParameters.Profile.UNRESOLVED_ORDINARY);
         check(migrated.progress().tribulationPressure() == 0.0);
         check(migrated.progress().tribulationImpactSequence() == 0L);
         check(migrated.progress().tribulationImpactFailure().isEmpty());
         check(migrated.progress().tribulations().equals(List.of(carrier)));
      });
      test("schema 11 rejects stray impact provenance when its marker is false", () -> {
         Path directory = Files.createTempDirectory("shengxian-tribulation-stray-context-");
         ApertureKey key = new ApertureKey(OWNER, "stray-impact-context");
         FileSessionRepository repository = new FileSessionRepository(directory);
         repository.save(new AscensionRecord(AscensionSession.prepare(key), AscensionProgress.empty()), -1L);
         Path file = recordFile(directory);
         Properties malformed = read(file);
         malformed.setProperty("tribulationImpactFailure.profile", "TAI_BAI_WITNESSED");
         write(file, malformed);
         byte[] preserved = Files.readAllBytes(file);
         expect(IOException.class, () -> repository.find(key));
         check(java.util.Arrays.equals(preserved, Files.readAllBytes(file)));
      });
      test("carrier anchors cannot move or change their persistent entity identity", () -> {
         AscensionProgress progress = AscensionProgress.empty().withTribulation(
               new AscensionProgress.Tribulation("anchor-1",
                     AscensionProgress.Tribulation.Type.SOUND_CHIMES, 2, 10));
         var uuid = UUID.fromString("00000000-0000-0000-0000-000000000abc");
         var anchor = new AscensionProgress.CarrierAnchor("minecraft:overworld", 10, 70, -5, uuid);
         progress = progress.withCarrierAnchor("anchor-1", anchor);
         var committed = progress;
         expect(IllegalStateException.class, () -> committed.withCarrierAnchor("anchor-1",
               new AscensionProgress.CarrierAnchor("minecraft:overworld", 11, 70, -5, uuid)));
         expect(IllegalStateException.class, () -> committed.withCarrierAnchor("anchor-1",
               new AscensionProgress.CarrierAnchor("minecraft:overworld", 10, 70, -5,
                     UUID.fromString("00000000-0000-0000-0000-000000000abd"))));
         check(committed.tribulation().carrierAnchor().equals(anchor));
      });
      test("session store strikes a named carrier without changing another active carrier", () -> {
         Path directory = Files.createTempDirectory("shengxian-named-carrier-strike-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         AscensionService service = new AscensionService(new FileSessionRepository(directory), ALL);
         AscensionRecord current = absorbing(service, key);
         var qi = new AscensionProgress.Qi(30, 30, 30, 8, 8, 100);
         var sky = new AscensionProgress.Tribulation("sky-guard",
               AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND, 8, 17);
         var earth = new AscensionProgress.Tribulation("earth-guard",
               AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER, 3, 23);
         current = service.mutate(key, current.session().stateRevision(), 4L, 3L,
               progress -> progress.withQi(qi).withTribulations(List.of(sky, earth)));
         SessionStore store = new SessionStore(directory, ALL);
         AscensionRecord struck = store.strikeTribulation(key, current.session().stateRevision(),
               5L, 4L, "earth-guard", 99);
         check(struck.progress().tribulations().size() == 1);
         check(struck.progress().tribulation().id().equals("sky-guard"));
         check(struck.progress().qi().equals(qi));
      });      test("active tribulation persists with its carrier state", () -> {
         Path var0x = Files.createTempDirectory("shengxian-tribulation-persist-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         FileSessionRepository var2 = new FileSessionRepository(var0x);
         AscensionRecord var3 = new AscensionRecord(AscensionSession.prepare(var1), AscensionProgress.empty());
         var2.save(var3, -1L);
         AscensionProgress.Tribulation var4 = new AscensionProgress.Tribulation("t9", AscensionProgress.Tribulation.Type.ANCIENT_BEAST, 2, 17);
         AscensionProgress var5 = AscensionProgress.empty().withQi(new AscensionProgress.Qi(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)).withTribulation(var4);
         AscensionRecord var6 = new AscensionRecord(SessionFlow.update(var3.session(), 0L, 1L, 1L), var5);
         var2.save(var6, 0L);
         AscensionRecord var7 = new FileSessionRepository(var0x).find(var1).orElseThrow();
         check(var7.equals(var6));
         check(var7.progress().tribulation().equals(var4));
      });
      test("concurrent tribulations persist and schema 6 single carrier migrates to schema 11", () -> {
         Path directory = Files.createTempDirectory("shengxian-tribulation-schema7-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         FileSessionRepository repository = new FileSessionRepository(directory);
         AscensionRecord created = new AscensionRecord(AscensionSession.prepare(key), AscensionProgress.empty());
         repository.save(created, -1L);
         AscensionProgress.Tribulation willow = new AscensionProgress.Tribulation("heaven-9",
               AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND, 8, 17);
         AscensionProgress.Tribulation tiger = new AscensionProgress.Tribulation("earth-9",
               AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER, 3, 23);
         AscensionProgress concurrent = AscensionProgress.empty()
               .withTribulations(List.of(willow, tiger));
         AscensionRecord update = new AscensionRecord(SessionFlow.update(created.session(), 0L, 1L, 1L), concurrent);
         repository.save(update, 0L);
         AscensionRecord restored = new FileSessionRepository(directory).find(key).orElseThrow();
         check(restored.equals(update));
         check(restored.progress().tribulations().size() == 2);

         Path file = recordFile(directory);
         Properties legacy = read(file);
         legacy.setProperty("schema", "6");
         for (String name : List.copyOf(legacy.stringPropertyNames())) {
            if (name.startsWith("tribulation.")) legacy.remove(name);
         }
         write(file, legacy);
         AscensionRecord migrated = repository.find(key).orElseThrow();
         check(migrated.progress().tribulations().size() == 1);
         check(migrated.progress().tribulation().equals(willow));
         repository.save(new AscensionRecord(SessionFlow.update(migrated.session(), 1L, 2L, 2L),
               migrated.progress()), 1L);
         check("11".equals(read(file).getProperty("schema")));
      });      test("simulation commits deterministic world anchors before carrier spawning", () -> {
         Path directory = Files.createTempDirectory("shengxian-carrier-anchor-service-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         AscensionService service = new AscensionService(new FileSessionRepository(directory), ALL);
         AscensionRecord current = absorbing(service, key);
         current = service.mutate(key, current.session().stateRevision(), 4L, 3L, progress ->
               progress.withQi(new AscensionProgress.Qi(30, 30, 30, 8, 8, 100)));
         var spawnOrigin = new AscensionProgress.CarrierAnchor("minecraft:overworld", 0, 64, 0, null);
         TribulationParameters parameters = new TribulationParameters(10, 2, 3, 1, 4, 5);
         var first = service.stepSimulation(key, current.session().stateRevision(), 5L, 4L,
               QiIntent.idle(), params(100, 0), parameters, spawnOrigin).record();
         check(first.progress().tribulations().size() == 1);
         var firstCarrier = first.progress().tribulation();
         check(firstCarrier.carrierAnchor().placed());
         check(firstCarrier.carrierAnchor().dimension().equals("minecraft:overworld"));
         check(firstCarrier.carrierAnchor().entityId() != null);

         var second = service.stepSimulation(key, first.session().stateRevision(), 6L, 5L,
               QiIntent.idle(), params(100, 0), parameters, spawnOrigin).record();
         check(second.progress().tribulation().carrierAnchor().equals(firstCarrier.carrierAnchor()));
         check(second.progress().tribulation().ticksUntilImpact() < firstCarrier.ticksUntilImpact());
      });      test("service simulation starts and resolves a tribulation concurrently", () -> {
         Path var0x = Files.createTempDirectory("shengxian-service-tribulation-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         AscensionService var2 = new AscensionService(new FileSessionRepository(var0x), ALL);
         AscensionRecord var3 = absorbing(var2, var1);
         var3 = var2.mutate(var1, var3.session().stateRevision(), 4L, 3L, var0xx -> var0xx.withQi(new AscensionProgress.Qi(30.0, 30.0, 30.0, 8.0, 8.0, 100.0)));
         QiParameters var4 = params(100.0, 0.0);
         TribulationParameters var5 = new TribulationParameters(10.0, 2, 3, 1, 4, 5.0);
         AscensionService.SimulationOutcome var6 = var2.stepSimulation(var1, 4L, 5L, 4L, QiIntent.idle(), var4, var5);
         check(var6.record().session().phase() == Phase.ABSORBING);
         check(var6.record().progress().tribulation().active());
         check(var6.record().progress().tribulation().type() == AscensionProgress.Tribulation.Type.SOUND_CHIMES);
         check(var6.tribulationEvents().stream().anyMatch(var0xx -> var0xx.type() == TribulationEngine.EventType.STARTED_SOUND));
         AscensionProgress.Qi var7 = var6.record().progress().qi();
         AscensionRecord var8 = var2.strikeTribulation(var1, 5L, 6L, 5L, 2);
         check(!var8.progress().tribulation().active());
         check(var8.progress().qi().equals(var7));
         check(var8.progress().appliedEffects().contains("tribulation:t4:survived"));
      });
      test("service simulation commits source-bound FAILED on a lethal witnessed impact", () -> {
         Path var0x = Files.createTempDirectory("shengxian-service-tribulation-fatal-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         AscensionService var2 = new AscensionService(new FileSessionRepository(var0x), ALL);
         AscensionRecord var3 = absorbing(var2, var1);
         var3 = var2.mutate(var1, var3.session().stateRevision(), 4L, 3L, var0xx -> var0xx.withQi(new AscensionProgress.Qi(30.0, 30.0, 30.0, 8.0, 0.0, 100.0)));
         QiParameters var4 = params(5.0, 0.0);
         TribulationParameters var5 = new TribulationParameters(10.0, 2, 1, 1, 1, 3.0,
               TribulationParameters.Profile.TAI_BAI_WITNESSED);
         AscensionService.SimulationOutcome var6 = var2.stepSimulation(var1, 4L, 5L, 4L, QiIntent.idle(), var4, var5);
         check(var6.record().session().phase() == Phase.ABSORBING);
         check(var6.record().progress().tribulationPressure() == 3.0);
         check(var6.record().progress().risk().equals(AscensionProgress.Risk.empty()));
         AscensionService.SimulationOutcome var7 = var2.stepSimulation(var1, 5L, 6L, 5L, QiIntent.idle(), var4, var5);
         check(var7.fatal());
         check(var7.record().session().phase() == Phase.FAILED && var7.record().session().broken());
         check(var7.record().progress().tribulationPressure() == 6.0);
         check(var7.record().progress().risk().equals(AscensionProgress.Risk.empty()));
         check(var7.record().progress().failureCauses().equals(Set.of(
               AscensionProgress.FailureCause.TRIBULATION_IMPACT)));
         check(AscensionReadiness.hasVerifiedTribulationImpactCause(
               var7.record().progress().failureCauses(),
               var7.record().progress().tribulationImpactFailure().orElseThrow()));
         var context = var7.record().progress().tribulationImpactFailure().orElseThrow();
         UUID operation = FailureSettlementIdentity.operationId(var7.record().session().id(),
               var7.record().session().phaseRevision());
         expect(IOException.class, () -> var2.startFailure(var1,
               var7.record().session().stateRevision(), 7L, 6L));
         expect(IOException.class, () -> var2.startTribulationImpactFailure(var1,
               var7.record().session().stateRevision(), 7L, 6L, UUID.randomUUID(), context));
         var started = var2.startTribulationImpactFailure(var1,
               var7.record().session().stateRevision(), 7L, 6L, operation, context);
         check(started.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.STARTED);
         expect(IOException.class, () -> var2.startTribulationImpactFailure(var1,
               started.session().stateRevision(), 8L, 7L, operation, context));
         var uncertain = var2.markTribulationImpactUncertain(var1,
               started.session().stateRevision(), 8L, 7L, operation, context);
         check(uncertain.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.UNCERTAIN);
         var receipt = var2.recordTribulationImpactRespawnReceipt(var1,
               uncertain.session().stateRevision(), 9L, 8L, operation, context);
         check(receipt.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.RESPAWN_RECEIPT);
         var acknowledged = var2.acknowledgeTribulationImpact(var1,
               receipt.session().stateRevision(), 10L, 9L, operation, context);
         check(acknowledged.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.ACKNOWLEDGED);
      });
      test(
         "schema 4 records migrate with an empty aperture",
         () -> {
            Path var0x = Files.createTempDirectory("shengxian-schema4-");
            ApertureKey var1 = new ApertureKey(OWNER, "first");
            FileSessionRepository var2 = new FileSessionRepository(var0x);
            AscensionRecord var3 = new AscensionRecord(AscensionSession.prepare(var1), AscensionProgress.empty());
            var2.save(var3, -1L);
            AscensionProgress var4 = AscensionProgress.empty()
               .withQi(new AscensionProgress.Qi(1.0, 2.0, 3.0, 4.0, 5.0, 6.0))
               .withTribulation(new AscensionProgress.Tribulation("t1", AscensionProgress.Tribulation.Type.SOUND_CHIMES, 3, 7))
               .withAppliedEffect("old4");
            var2.save(new AscensionRecord(SessionFlow.update(var3.session(), 0L, 1L, 1L), var4), 0L);
            Path var5 = recordFile(var0x);
            Properties var6 = read(var5);
            var6.setProperty("schema", "4");
            var6.remove("apertureAreaMu");
            var6.remove("apertureTimeRatio");
            var6.remove("apertureEssence");
            var6.remove("apertureInternalTicks");
            var6.remove("aperturePlot");
            write(var5, var6);
            AscensionRecord var7 = var2.find(var1).orElseThrow();
            check(var7.progress().aperture().equals(AscensionProgress.Aperture.empty()));
            check(var7.progress().tribulation().equals(var4.tribulation()));
            var2.save(new AscensionRecord(SessionFlow.update(var7.session(), 1L, 2L, 1L), var7.progress()), 1L);
            check("11".equals(read(var5).getProperty("schema")));
         }
      );
      test("aperture forms once and keeps area/time/essence", () -> {
         AscensionProgress var0x = AscensionProgress.empty();
         AscensionProgress var1 = ApertureEngine.form(var0x, 518.0, 16.0, 22.0, 3);
         check(var1.aperture().formed());
         check(var1.aperture().areaMu() == 518.0 && var1.aperture().timeRatio() == 16.0);
         check(var1.aperture().essence() == 22.0 && var1.aperture().plotIndex() == 3);
         check(var1.appliedEffects().contains("aperture:formed"));
         expect(IllegalStateException.class, () -> ApertureEngine.form(var1, 1.0, 1.0, 1.0, 4));
      });
      test("rank-six recovery persists player rank before acknowledging world state", () -> {
          Path playerFile=Files.createTempFile("shengxian-rank-order-",".txt");
          Files.writeString(playerFile,"5");
          double[] rank={5};
          int[] writes={0},acks={0};
          var action=RankSixSyncDecision.commit(()->rank[0],()->{rank[0]=6;writes[0]++;},
                ()->Files.writeString(playerFile,Double.toString(rank[0])),
                ()->{check(Double.parseDouble(Files.readString(playerFile))==6);acks[0]++;});
          check(action==RankSixSyncDecision.Action.APPLY_RANK_SIX && writes[0]==1 && acks[0]==1);
      });
      test("rank-six failed player save leaves world pending and retry does not repeat upgrade", () -> {
          double[] rank={5};int[] writes={0},acks={0},saves={0};
          expect(IOException.class,()->RankSixSyncDecision.commit(()->rank[0],()->{rank[0]=6;writes[0]++;},
                ()->{saves[0]++;throw new IOException("player file cannot be saved");},()->acks[0]++));
          check(writes[0]==1 && acks[0]==0 && rank[0]==6);
          var action=RankSixSyncDecision.commit(()->rank[0],()->{writes[0]++;rank[0]=6;},
                ()->saves[0]++,()->acks[0]++);
          check(action==RankSixSyncDecision.Action.ACKNOWLEDGE_ONLY && writes[0]==1 && saves[0]==2 && acks[0]==1);
      });
      test("rank-six failed world acknowledgement recovers from durable player rank", () -> {
          Path file=Files.createTempFile("shengxian-rank-restart-",".txt");
          double[] rank={5};int[] writes={0};
          expect(IOException.class,()->RankSixSyncDecision.commit(()->rank[0],()->{rank[0]=6;writes[0]++;},
                ()->Files.writeString(file,Double.toString(rank[0])),()->{throw new IOException("world save interrupted");}));
          rank[0]=Double.parseDouble(Files.readString(file));
          int[] acks={0};
          check(RankSixSyncDecision.commit(()->rank[0],()->writes[0]++,()->{},()->acks[0]++)
                ==RankSixSyncDecision.Action.ACKNOWLEDGE_ONLY);
          check(writes[0]==1 && acks[0]==1 && rank[0]==6);
      });
      test("rank-six commit rejects unsafe drift and refuses unconfirmed bridge writes", () -> {
          int[] touched={0};
          check(RankSixSyncDecision.commit(()->4,()->touched[0]++,()->touched[0]++,()->touched[0]++)
                ==RankSixSyncDecision.Action.DEFER);
          check(touched[0]==0);
          expect(IOException.class,()->RankSixSyncDecision.commit(()->5,()->{},()->touched[0]++,()->touched[0]++));
          check(touched[0]==0);
          double[] rank={7};
          check(RankSixSyncDecision.commit(()->rank[0],()->{rank[0]=6;},()->{},()->touched[0]++)
                ==RankSixSyncDecision.Action.ACKNOWLEDGE_ONLY);
          check(rank[0]==7 && touched[0]==1);
      });
      test("legacy pending rank-six intent never overwrites a rank-five character", () -> {
          double[] rank={5};int[] changed={0};
          check(RankSixSyncDecision.commit(()->rank[0],()->false,false,()->{rank[0]=6;changed[0]++;},
                ()->changed[0]++,()->changed[0]++)==RankSixSyncDecision.Action.DEFER);
          check(rank[0]==5 && changed[0]==0);
      });
      test("legacy pending rank-six intent may verify an already higher rank", () -> {
          double[] rank={7};int[] saves={0},acks={0},writes={0};
          check(RankSixSyncDecision.commit(()->rank[0],()->false,false,()->{writes[0]++;rank[0]=6;},
                ()->saves[0]++,()->acks[0]++)==RankSixSyncDecision.Action.ACKNOWLEDGE_ONLY);
          check(rank[0]==7 && writes[0]==0 && saves[0]==1 && acks[0]==1);
      });
      test("current rank-six intent keeps automatic first projection", () -> {
          double[] rank={5};int[] writes={0},acks={0};
          check(RankSixSyncDecision.commit(()->rank[0],()->false,true,()->{rank[0]=6;writes[0]++;},
                ()->{},()->acks[0]++)==RankSixSyncDecision.Action.APPLY_RANK_SIX);
          check(rank[0]==6 && writes[0]==1 && acks[0]==1);
      });      test("rank-six player receipt prevents replay after a legitimate downgrade before world ack", () -> {
          double[] rank={5};int[] changed={0};
          check(RankSixSyncDecision.commit(()->rank[0],()->true,()->{rank[0]=6;changed[0]++;},
                ()->changed[0]++,()->changed[0]++)==RankSixSyncDecision.Action.DEFER);
          check(rank[0]==5 && changed[0]==0);
      });
      test("rank-six commit cannot acknowledge a projection changed during persistence", () -> {
          double[] rank={6};int[] acknowledged={0};
          expect(IOException.class,()->RankSixSyncDecision.commit(()->rank[0],()->{},
                ()->rank[0]=5,()->acknowledged[0]++));
          check(rank[0]==5 && acknowledged[0]==0);
      });
      test("rank-six recovery distinguishes write, acknowledge, and unsafe drift", () -> {
         check(RankSixSyncDecision.evaluate(5.0) == RankSixSyncDecision.Action.APPLY_RANK_SIX);
         check(RankSixSyncDecision.evaluate(6.0) == RankSixSyncDecision.Action.ACKNOWLEDGE_ONLY);
         check(RankSixSyncDecision.evaluate(7.0) == RankSixSyncDecision.Action.ACKNOWLEDGE_ONLY);
         check(RankSixSyncDecision.evaluate(4.0) == RankSixSyncDecision.Action.DEFER);
         check(RankSixSyncDecision.evaluate(5.5) == RankSixSyncDecision.Action.DEFER);
         check(RankSixSyncDecision.evaluate(Double.NaN) == RankSixSyncDecision.Action.DEFER);
      });
      test("rank-six acknowledgement is durable, same-phase, and idempotent", () -> {
         Path root = Files.createTempDirectory("shengxian-rank-six-ack-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         SessionStore store = new SessionStore(root, ALL);
         AscensionService service = new AscensionService(new FileSessionRepository(root), ALL);
         AscensionRecord record = absorbing(service, key);
         record = service.advance(key, Phase.OPENING, record.session().stateRevision(), 4L, 4L);
         record = service.advance(key, Phase.FORMING, record.session().stateRevision(), 5L, 5L);
         record = service.bindVitalGu(key, record.session().stateRevision(), 6L, 6L, "a".repeat(64), "本命蛊");
         record = service.formAperture(key, record.session().stateRevision(), 7L, 7L, 500.0, 4.0, 20.0, 1, "a".repeat(64));
         long phaseRevision = record.session().phaseRevision();
         long stateRevision = record.session().stateRevision();
         check(record.progress().rankSixSyncRecoveryAvailable());
         check(record.progress().rankSixSyncProjectionV1());
         AscensionRecord acknowledged = store.acknowledgeRankSixSync(key, stateRevision, 8L, 8L);
         check(!acknowledged.progress().rankSixSyncRecoveryAvailable());
         check(acknowledged.session().phase() == Phase.IMMORTAL);
         check(acknowledged.session().phaseRevision() == phaseRevision);
         check(acknowledged.session().stateRevision() == stateRevision + 1L);
         check(new FileSessionRepository(root).find(key).orElseThrow().equals(acknowledged));
         check(store.acknowledgeRankSixSync(key, stateRevision, 8L, 8L).equals(acknowledged));
      });
      test("service forms aperture only at FORMING and completes IMMORTAL", () -> {
         Path var0x = Files.createTempDirectory("shengxian-form-aperture-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         AscensionService var2 = new AscensionService(new FileSessionRepository(var0x), ALL);
         AscensionRecord var3 = absorbing(var2, var1);
         long var4 = var3.session().stateRevision();
         expect(IOException.class, () -> var2.formAperture(var1, var4, 4L, 4L, 518.0, 16.0, 22.0, 1));
         var3 = var2.advance(var1, Phase.OPENING, var3.session().stateRevision(), 4L, 4L);
         var3 = var2.advance(var1, Phase.FORMING, var3.session().stateRevision(), 5L, 5L);
         long var6 = var3.session().stateRevision();
         expect(IOException.class, () -> var2.formAperture(var1, var6, 6L, 6L, 518.0, 16.0, 22.0, 1));
         var3 = var2.bindVitalGu(var1, var6, 6L, 6L, "a".repeat(64), "\u672c\u547d\u86ca");
         long var8 = var3.session().stateRevision();
         expect(IOException.class, () -> var2.formAperture(var1, var8, 7L, 7L, 518.0, 16.0, 22.0, 1, "b".repeat(64)));
         AscensionRecord var10 = var2.formAperture(var1, var8, 7L, 7L, 518.0, 16.0, 22.0, 1, "a".repeat(64));
         check(var10.session().phase() == Phase.IMMORTAL);
         check(var10.progress().aperture().formed());
         check(var10.progress().aperture().plotIndex() == 1);
         check(var10.progress().appliedEffects().contains("aperture:formed"));
         expect(IOException.class, () -> var2.formAperture(var1, var10.session().stateRevision(), 8L, 8L, 518.0, 16.0, 22.0, 1));
      });
      test(
         "immortal session persists aperture clock and only aperture ledger changes",
         () -> {
            Path var0x = Files.createTempDirectory("shengxian-immortal-update-");
            ApertureKey var1 = new ApertureKey(OWNER, "first");
            AscensionService var2 = new AscensionService(new FileSessionRepository(var0x), ALL);
            AscensionRecord var3 = absorbing(var2, var1);
            var3 = var2.advance(var1, Phase.OPENING, var3.session().stateRevision(), 4L, 4L);
            var3 = var2.advance(var1, Phase.FORMING, var3.session().stateRevision(), 5L, 5L);
            var3 = var2.bindVitalGu(var1, var3.session().stateRevision(), 6L, 6L, "a".repeat(64), "\u672c\u547d\u86ca");
            AscensionRecord var4 = var2.formAperture(var1, var3.session().stateRevision(), 7L, 7L, 500.0, 4.0, 20.0, 1, "a".repeat(64));
            check(var4.progress().rankSixSyncRecoveryAvailable());
            AscensionRecord var5 = var2.mutate(var1, var4.session().stateRevision(), 8L, 8L, var0xx -> ApertureEngine.advanceInternal(var0xx, 20L, 0.01));
            check(var5.session().phase() == Phase.IMMORTAL);
            check(var5.session().phaseRevision() == var4.session().phaseRevision());
            check(var5.session().stateRevision() == var4.session().stateRevision() + 1L);
            check(var5.progress().aperture().internalTicks() == 80L);
            check(Math.abs(var5.progress().aperture().essence() - 20.8) < 1.0E-9);
            check(new FileSessionRepository(var0x).find(var1).orElseThrow().equals(var5));
            AscensionRecord var6 = var2.mutate(var1, var5.session().stateRevision(), 9L, 9L, var0xx -> ApertureEngine.spendEssence(var0xx, 5.0));
            check(Math.abs(var6.progress().aperture().essence() - 15.8) < 1.0E-9);
            long var7 = var6.session().stateRevision();
            expect(
               IOException.class,
               () -> var2.mutate(
                  var1, var7, 10L, 10L, var0xxx -> var0xxx.withQi(new AscensionProgress.Qi(1.0, 0.0, 0.0, 0.0, 0.0, var0xxx.qi().humanReserve()))
               )
            );
            expect(IOException.class, () -> var2.mutate(var1, var7, 10L, 10L, var0xxx -> var0xxx.withAppliedEffect("injected-after-formation")));
            expect(IOException.class, () -> var2.mutate(var1, var7, 10L, 10L, var0xxx -> var0xxx.withApertureDraft("injected-after-formation")));
            check(new FileSessionRepository(var0x).find(var1).orElseThrow().equals(var6));
         }
      );
      test(
         "failed session rejects same-phase progress updates after restart",
         () -> {
            Path var0x = Files.createTempDirectory("shengxian-failed-freeze-");
            ApertureKey var1 = new ApertureKey(OWNER, "first");
            AscensionService var2 = new AscensionService(new FileSessionRepository(var0x), ALL);
            AscensionRecord var3 = var2.start(var1, true, Optional.of(new RuleProfile("fixture", true, ALL)), 0L);
            var3 = var2.beginShatter(var1, var3.session().stateRevision(), 1L, 1L, 100.0, 80.0);
            AscensionRecord var4 = var2.advance(var1, Phase.FAILED, var3.session().stateRevision(), 2L, 2L);
            AscensionService var5 = new AscensionService(new FileSessionRepository(var0x), ALL);
            expect(
               IllegalStateException.class,
               () -> var5.mutate(var1, var4.session().stateRevision(), 3L, 3L, var0xxx -> var0xxx.withAppliedEffect("should-not-apply"))
            );
            check(var5.load(var1).equals(var4));
         }
      );
      test("aperture internal clock and essence follow the time ratio", () -> {
         AscensionProgress var0x = ApertureEngine.form(AscensionProgress.empty(), 100.0, 4.0, 0.0, 1);
         AscensionProgress var1 = ApertureEngine.advanceInternal(var0x, 10L, 0.5);
         check(var1.aperture().internalTicks() == 40L);
         check(var1.aperture().essence() == 20.0);
         AscensionProgress var2 = ApertureEngine.spendEssence(var1, 5.0);
         check(var2.aperture().essence() == 15.0);
         expect(IllegalStateException.class, () -> ApertureEngine.spendEssence(var2, 100.0));
      });
      test(
         "aperture essence cannot be spent before formation",
         () -> expect(IllegalStateException.class, () -> ApertureEngine.spendEssence(AscensionProgress.empty(), 1.0))
      );
      test("formed aperture persists across repository restart", () -> {
         Path var0x = Files.createTempDirectory("shengxian-aperture-persist-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         FileSessionRepository var2 = new FileSessionRepository(var0x);
         AscensionRecord var3 = new AscensionRecord(AscensionSession.prepare(var1), AscensionProgress.empty());
         var2.save(var3, -1L);
         AscensionProgress var4 = ApertureEngine.form(AscensionProgress.empty(), 300.0, 2.5, 7.0, 2);
         AscensionRecord var5 = new AscensionRecord(SessionFlow.update(var3.session(), 0L, 1L, 1L), var4);
         var2.save(var5, 0L);
         AscensionRecord var6 = new FileSessionRepository(var0x).find(var1).orElseThrow();
         check(var6.equals(var5));
         check(var6.progress().aperture().equals(var4.aperture()));
      });
      test("aperture plot store persists built plots", () -> {
         Path var0x = Files.createTempDirectory("shengxian-aperture-space-");
         Path var1 = var0x.resolve("aperture-space.properties");
         ApertureSpaceStore var2 = new ApertureSpaceStore(var1);
         check(!var2.isBuilt(3));
         var2.markBuilt(3);
         ApertureSpaceStore var3 = new ApertureSpaceStore(var1);
         check(var3.isBuilt(3));
         check(!var3.isBuilt(4));
      });
      test("aperture plot store rejects malformed plot entries", () -> {
         Path var0x = Files.createTempDirectory("shengxian-aperture-space-bad-");
         Path var1 = var0x.resolve("aperture-space.properties");
         Files.writeString(var1, "built.oops=true\n");
         expect(IOException.class, () -> new ApertureSpaceStore(var1));
      });
      test("aperture space store remembers return points", () -> {
         Path var0x = Files.createTempDirectory("shengxian-aperture-return-");
         Path var1 = var0x.resolve("aperture-space.properties");
         ApertureSpaceStore.ReturnPoint var2 = new ApertureSpaceStore.ReturnPoint("minecraft:overworld", 1.5, 64.0, 2.5, 90.0F, 0.0F);
         new ApertureSpaceStore(var1).rememberReturn(OWNER, var2);
         ApertureSpaceStore var3 = new ApertureSpaceStore(var1);
         check(var3.returnPoint(OWNER).equals(var2));
      });
      test("aperture space store rejects malformed return points", () -> {
         Path var0x = Files.createTempDirectory("shengxian-aperture-return-bad-");
         Path var1 = var0x.resolve("aperture-space.properties");
         Files.writeString(var1, "return.not-a-uuid.x=1\n");
         expect(IOException.class, () -> new ApertureSpaceStore(var1));
      });
      test("BenMingGu custody binds once and persists", () -> {
         Path var0x = Files.createTempDirectory("shengxian-benming-");
         Path var1 = var0x.resolve("benminggu.properties");
         String var2 = "a".repeat(64);
         new BenMingGuStore(var1).bind(OWNER, var2, "\u672c\u547d\u86ca");
         BenMingGuStore var3 = new BenMingGuStore(var1);
         check(var3.find(OWNER).orElseThrow().fingerprint().equals(var2));
         check(var3.find(OWNER).orElseThrow().displayName().equals("\u672c\u547d\u86ca"));
      });
      test("BenMingGu custody rejects a different Gu", () -> {
         Path var0x = Files.createTempDirectory("shengxian-benming-different-");
         Path var1 = var0x.resolve("benminggu.properties");
         BenMingGuStore var2 = new BenMingGuStore(var1);
         var2.bind(OWNER, "a".repeat(64), "\u672c\u547d\u86ca");
         check(var2.bind(OWNER, "a".repeat(64), "\u672c\u547d\u86ca").fingerprint().equals("a".repeat(64)));
         expect(IOException.class, () -> var2.bind(OWNER, "b".repeat(64), "\u53e6\u4e00\u53ea\u86ca"));
      });
      test("BenMingGu content fingerprints are bound independently per owner", () -> {
         Path var0x = Files.createTempDirectory("shengxian-benming-multiplayer-");
         Path var1 = var0x.resolve("benminggu.properties");
         UUID var2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
         String var3 = "a".repeat(64);
         BenMingGuStore var4 = new BenMingGuStore(var1);
         var4.bind(OWNER, var3, "\u672c\u547d\u86ca\u7532");
         var4.bind(var2, var3, "\u672c\u547d\u86ca\u4e59");
         BenMingGuStore var5 = new BenMingGuStore(var1);
         check(var5.find(OWNER).orElseThrow().fingerprint().equals(var3));
         check(var5.find(var2).orElseThrow().fingerprint().equals(var3));
         check(var5.find(OWNER).orElseThrow().displayName().equals("\u672c\u547d\u86ca\u7532"));
         check(var5.find(var2).orElseThrow().displayName().equals("\u672c\u547d\u86ca\u4e59"));
         expect(IOException.class, () -> var5.bind(OWNER, "b".repeat(64), "\u4e0d\u540c\u86ca"));
         check(new BenMingGuStore(var1).find(var2).orElseThrow().fingerprint().equals(var3));
      });
      test("an uncommitted orphan binding can be replaced and survives restart", () -> {
         Path var0x = Files.createTempDirectory("shengxian-benming-orphan-");
         Path var1 = var0x.resolve("benminggu.properties");
         FileSessionRepository var2 = new FileSessionRepository(var0x.resolve("sessions"));
         ApertureKey var3 = new ApertureKey(OWNER, "first");
         UUID var4 = UUID.fromString("00000000-0000-0000-0000-000000000002");
         BenMingGuStore var5 = new BenMingGuStore(var1);
         var5.bind(OWNER, "a".repeat(64), "\u539f\u672c\u547d\u86ca");
         var5.bind(var4, "a".repeat(64), "\u53e6\u4e00\u4f4d\u73a9\u5bb6\u7684\u86ca");
         check(var2.find(var3).isEmpty());
         var5.bindOrReplaceUncommitted(OWNER, "b".repeat(64), "\u65b0\u672c\u547d\u86ca");
         BenMingGuStore var6 = new BenMingGuStore(var1);
         check(var6.find(OWNER).orElseThrow().fingerprint().equals("b".repeat(64)));
         check(var6.find(OWNER).orElseThrow().displayName().equals("\u65b0\u672c\u547d\u86ca"));
         check(var6.find(var4).orElseThrow().fingerprint().equals("a".repeat(64)));
         expect(IOException.class, () -> var6.bind(OWNER, "a".repeat(64), "\u539f\u672c\u547d\u86ca"));
         check(new BenMingGuStore(var1).find(OWNER).orElseThrow().fingerprint().equals("b".repeat(64)));
      });
      test("BenMingGu custody rejects malformed records", () -> {
         Path var0x = Files.createTempDirectory("shengxian-benming-bad-");
         Path var1 = var0x.resolve("benminggu.properties");
         Files.writeString(var1, "binding.not-a-uuid.fingerprint=" + "a".repeat(64) + "\n");
         expect(IOException.class, () -> new BenMingGuStore(var1));
      });
      test("schema 5 records migrate with an empty BenMingGu", () -> {
         Path var0x = Files.createTempDirectory("shengxian-schema5-benming-");
         ApertureKey var1 = new ApertureKey(OWNER, "first");
         FileSessionRepository var2 = new FileSessionRepository(var0x);
         var2.save(new AscensionRecord(AscensionSession.prepare(var1), AscensionProgress.empty()), -1L);
         Properties var3 = read(recordFile(var0x));
         var3.setProperty("schema", "5");
         var3.remove("vitalGuFingerprint");
         var3.remove("vitalGuName");
         write(recordFile(var0x), var3);
         AscensionRecord var4 = new FileSessionRepository(var0x).find(var1).orElseThrow();
         check(!var4.progress().vitalGu().bound());
      });
      test(
         "aperture planner uses canon area bands and varies within them",
         () -> {
            ApertureFormationPlanner.Plan var0x = ApertureFormationPlanner.plan(AscensionProgress.empty(), 0, new Random(1L));
            check(var0x.grade() == ApertureFormationPlanner.Grade.SMALL);
            check(var0x.areaMu() > 0.0 && var0x.areaMu() <= 3000000.0);
            check(var0x.timeRatio() >= 1.0 && var0x.timeRatio() <= 4.0);
            check(var0x.initialEssence() >= 11.0 && var0x.initialEssence() <= 19.0);
            ApertureFormationPlanner.Plan var1 = ApertureFormationPlanner.plan(
               AscensionProgress.empty().withQi(new AscensionProgress.Qi(40.0, 40.0, 0.0, 0.0, 0.0, 0.0)), 1, new Random(2L)
            );
            check(var1.grade() == ApertureFormationPlanner.Grade.MEDIUM);
            check(var1.areaMu() >= 4000000.0 && var1.areaMu() <= 6000000.0);
            check(var1.timeRatio() >= 5.0 && var1.timeRatio() <= 16.0);
            check(var1.initialEssence() >= 21.0 && var1.initialEssence() <= 29.0);
            AscensionProgress mediumSupply = AscensionProgress.empty().withQi(
                    new AscensionProgress.Qi(40, 40, 0, 0, 0, 0));
            boolean includesObservedSixteen = false;
            for (int seed = 0; seed < 1024 && !includesObservedSixteen; seed++) {
                includesObservedSixteen = ApertureFormationPlanner.plan(mediumSupply, 1, new Random(seed)).timeRatio() == 16.0;
            }
            check(includesObservedSixteen);
            ApertureFormationPlanner.Plan var2 = ApertureFormationPlanner.plan(
               AscensionProgress.empty().withQi(new AscensionProgress.Qi(100.0, 100.0, 0.0, 0.0, 0.0, 0.0)), 2, new Random(3L)
            );
            check(var2.grade() == ApertureFormationPlanner.Grade.UPPER);
            check(var2.areaMu() >= 7000000.0 && var2.areaMu() <= 9000000.0);
            check(var2.initialEssence() >= 30.0 && var2.initialEssence() <= 40.0);
         }
      );
      test("formation grade counts all three balanced qi toward aperture potential", () -> {
         AscensionProgress lower = AscensionProgress.empty().withQi(
               new AscensionProgress.Qi(20.0, 20.0, 20.0, 0.0, 0.0, 0.0));
         AscensionProgress medium = AscensionProgress.empty().withQi(
               new AscensionProgress.Qi(30.0, 30.0, 30.0, 0.0, 0.0, 0.0));
         AscensionProgress upper = AscensionProgress.empty().withQi(
               new AscensionProgress.Qi(70.0, 70.0, 70.0, 0.0, 0.0, 0.0));
         check(ApertureFormationPlanner.gradeFor(lower) == ApertureFormationPlanner.Grade.SMALL);
         check(ApertureFormationPlanner.gradeFor(medium) == ApertureFormationPlanner.Grade.MEDIUM);
         check(ApertureFormationPlanner.gradeFor(upper) == ApertureFormationPlanner.Grade.UPPER);
      });
      test("formation grade excludes unabsorbed human reserve", () -> {
         AscensionProgress reserveOnly = AscensionProgress.empty(1000000.0);
         AscensionProgress fusedWithReserve = AscensionProgress.empty().withQi(
               new AscensionProgress.Qi(30.0, 30.0, 30.0, 0.0, 0.0, 1000000.0));
         check(ApertureFormationPlanner.gradeFor(reserveOnly) == ApertureFormationPlanner.Grade.SMALL);
         check(ApertureFormationPlanner.gradeFor(fusedWithReserve) == ApertureFormationPlanner.Grade.MEDIUM);
      });
      test("upper aperture initial essence is strictly greater than thirty", () -> {
         AscensionProgress upper = AscensionProgress.empty().withQi(
               new AscensionProgress.Qi(70.0, 70.0, 70.0, 0.0, 0.0, 0.0));
         for (int seed = 0; seed < 128; seed++) {
            ApertureFormationPlanner.Plan plan = ApertureFormationPlanner.plan(upper, 2, new Random(seed));
            check(plan.initialEssence() > 30.0 && plan.initialEssence() <= 40.0);
         }
      });
      test("pending plot reservation resumes after restart and commits once", () -> {
         Path file = Files.createTempDirectory("shengxian-plot-reservation-recovery-")
               .resolve("aperture-space.properties");
         ApertureKey key = new ApertureKey(OWNER, "first");
         UUID session = UUID.fromString("10000000-0000-0000-0000-000000000001");
         ApertureSpaceStore store = new ApertureSpaceStore(file);
         int plot = store.reservePlotForAscension(key, session);
         check(plot == 0);
         check(store.reservePlotForAscension(key, session) == plot);

         ApertureSpaceStore restarted = new ApertureSpaceStore(file);
         check(restarted.reservePlotForAscension(key, session) == plot);
         ApertureKey otherKey = new ApertureKey(
               UUID.fromString("00000000-0000-0000-0000-000000000002"), "first");
         UUID otherSession = UUID.fromString("20000000-0000-0000-0000-000000000002");
         int otherPlot = restarted.reservePlotForAscension(otherKey, otherSession);
         check(otherPlot == 1);
         expect(IOException.class, () -> restarted.commitPlotReservation(otherPlot, key, session));
         check(!restarted.reservation(otherPlot).orElseThrow().committed());

         restarted.commitPlotReservation(plot, key, session);
         ApertureSpaceStore committed = new ApertureSpaceStore(file);
         check(committed.isReserved(plot));
         check(committed.reservation(plot).orElseThrow().committed());
         committed.commitPlotReservation(plot, key, session);
         check(new ApertureSpaceStore(file).reservation(plot).orElseThrow().committed());
      });
      test("aperture plot reservation is unique and persists", () -> {
         Path var0x = Files.createTempDirectory("shengxian-plot-reserve-");
         Path var1 = var0x.resolve("aperture-space.properties");
         ApertureSpaceStore var2 = new ApertureSpaceStore(var1);
         check(var2.reservePlot() == 0);
         check(var2.reservePlot() == 1);
         var2.markBuilt(0);
         var2.releasePlot(0);
         check(var2.reservePlot() == 2);
         ApertureSpaceStore var3 = new ApertureSpaceStore(var1);
         check(var3.reservePlot() == 3);
      });
      test("session enumeration rejects filename and embedded identity mismatch", () -> {
         Path var0x = Files.createTempDirectory("shengxian-enumerate-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         ApertureKey var2 = new ApertureKey(OWNER, "first");
         ApertureKey var3 = new ApertureKey(UUID.fromString("00000000-0000-0000-0000-000000000002"), "first");
         AscensionRecord var4 = new AscensionRecord(AscensionSession.prepare(var2), AscensionProgress.empty());
         AscensionRecord var5 = new AscensionRecord(AscensionSession.prepare(var3), AscensionProgress.empty());
         var1.save(var4, -1L);
         var1.save(var5, -1L);
         check(new HashSet<>(var1.listAll()).equals(Set.of(var4, var5)));
         Path var6 = recordFile(var0x);
         Properties var7 = read(var6);
         var7.setProperty("owner", "00000000-0000-0000-0000-000000000003");
         write(var6, var7);
         byte[] var8 = Files.readAllBytes(var6);
         expect(IOException.class, () -> var1.listAll());
         check(Arrays.equals(var8, Files.readAllBytes(var6)));
      });
      test("formed claims restore missing reservations and preserve orphan plots", () -> {
         Path var0x = Files.createTempDirectory("shengxian-plot-reconcile-");
         Path var1 = var0x.resolve("sessions");
         FileSessionRepository var2 = new FileSessionRepository(var1);
         AscensionService var3 = new AscensionService(var2, ALL);
         ApertureKey var4 = new ApertureKey(OWNER, "first");
         formedFixture(var3, var4, 3);
         ApertureSpaceStore var5 = new ApertureSpaceStore(var0x.resolve("space.properties"));
         check(var5.reservePlot() == 0);
         var5.markBuilt(1);
         Map var6 = new SessionStore(var1, ALL).formedPlotClaims();
         check(var6.equals(Map.of(3, var4)));
         var5.reconcileReservations(var6.keySet());
         ApertureSpaceStore var7 = new ApertureSpaceStore(var0x.resolve("space.properties"));
         check(var7.isReserved(0) && var7.isReserved(3));
         check(var7.isBuilt(1));
         check(var7.reservePlot() == 2);
      });
      test("duplicate formed plot claims block reconciliation", () -> {
         Path var0x = Files.createTempDirectory("shengxian-plot-conflict-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         AscensionService var2 = new AscensionService(var1, ALL);
         ApertureKey var3 = new ApertureKey(OWNER, "first");
         ApertureKey var4 = new ApertureKey(UUID.fromString("00000000-0000-0000-0000-000000000002"), "first");
         formedFixture(var2, var3, 7);
         formedFixture(var2, var4, 7);
         expect(IOException.class, () -> new SessionStore(var0x, ALL).formedPlotClaims());
         check(var1.listAll().size() == 2);
      });
      test("formed plot claim outside immortal phase is rejected", () -> {
         Path var0x = Files.createTempDirectory("shengxian-plot-bad-phase-");
         FileSessionRepository var1 = new FileSessionRepository(var0x);
         ApertureKey var2 = new ApertureKey(OWNER, "first");
         AscensionRecord var3 = new AscensionRecord(AscensionSession.prepare(var2), AscensionProgress.empty());
         var1.save(var3, -1L);
         AscensionRecord var4 = new AscensionRecord(SessionFlow.update(var3.session(), 0L, 1L, 1L), ApertureEngine.form(var3.progress(), 100.0, 2.0, 0.0, 4));
         var1.save(var4, 0L);
         expect(IOException.class, () -> new SessionStore(var0x, ALL).formedPlotClaims());
         check(new SessionStore(var0x, ALL).formedPlotClaimsLenient().containsKey(4));
         check(var1.find(var2).orElseThrow().equals(var4));
      });
      test("plot store write failures roll back in-memory reservations and built state", () -> {
         Path var0x = Files.createTempDirectory("shengxian-plot-write-failure-");
         Path var1 = var0x.resolve("space.properties");
         ApertureSpaceStore var2 = new ApertureSpaceStore(var1);
         check(var2.reservePlot() == 0);
         Files.delete(var1);
         Files.createDirectory(var1);
         Path var3 = var1.resolve("keep");
         Files.writeString(var3, "preserve");
         expect(IOException.class, () -> var2.reservePlot());
         check(var2.reservedPlots().equals(Set.of(0)));
         expect(IOException.class, () -> var2.markBuilt(2));
         check(var2.builtPlots().isEmpty());
         expect(IOException.class, () -> var2.reconcileReservations(Set.of(3)));
         check(var2.reservedPlots().equals(Set.of(0)));
         expect(IOException.class, () -> var2.releasePlot(0));
         check(var2.reservedPlots().equals(Set.of(0)));
         check(Files.readString(var3).equals("preserve"));
      });
      test("aperture guest list persists and revokes", () -> {
         Path var0x = Files.createTempDirectory("shengxian-guest-");
         Path var1 = var0x.resolve("aperture-space.properties");
         ApertureSpaceStore var2 = new ApertureSpaceStore(var1);
         check(!var2.isGuest(1, OWNER));
         var2.addGuest(1, OWNER);
         check(var2.isGuest(1, OWNER));
         ApertureSpaceStore var3 = new ApertureSpaceStore(var1);
         check(var3.isGuest(1, OWNER));
         var3.removeGuest(1, OWNER);
         check(!var3.isGuest(1, OWNER));
      });
      test("aperture guest record rejects malformed entries", () -> {
         Path var0x = Files.createTempDirectory("shengxian-guest-bad-");
         Path var1 = var0x.resolve("aperture-space.properties");
         Files.writeString(var1, "guest.bad.uuid=true\n");
         expect(IOException.class, () -> new ApertureSpaceStore(var1));
      });
      test("ascension guardian invitations are owner-scoped and survive restart", () -> {
         Path directory = Files.createTempDirectory("shengxian-ascension-guardians-");
         Path file = directory.resolve("aperture-space.properties");
         UUID owner = UUID.randomUUID();
         UUID otherOwner = UUID.randomUUID();
         UUID guardian = UUID.randomUUID();
         ApertureSpaceStore store = new ApertureSpaceStore(file);
         store.addAscensionGuardian(owner, guardian);
         check(store.isAscensionGuardian(owner, guardian));
         check(!store.isAscensionGuardian(otherOwner, guardian));
         check(new ApertureSpaceStore(file).isAscensionGuardian(owner, guardian));
         expect(IllegalArgumentException.class, () -> store.addAscensionGuardian(owner, owner));
         store.removeAscensionGuardian(owner, guardian);
         check(!new ApertureSpaceStore(file).isAscensionGuardian(owner, guardian));
      });
      test("ascension guardian grant and revoke persist with formed plot permission", () -> {
         Path directory = Files.createTempDirectory("shengxian-guardian-transaction-");
         Path file = directory.resolve("aperture-space.properties");
         UUID owner = UUID.randomUUID();
         UUID guardian = UUID.randomUUID();
         ApertureSpaceStore store = new ApertureSpaceStore(file);
         store.addGuardianForAscension(owner, 3, guardian);
         ApertureSpaceStore granted = new ApertureSpaceStore(file);
         check(granted.isAscensionGuardian(owner, guardian));
         check(granted.isGuardian(3, guardian));
         granted.removeGuardianForAscension(owner, 3, guardian);
         ApertureSpaceStore revoked = new ApertureSpaceStore(file);
         check(!revoked.isAscensionGuardian(owner, guardian));
         check(!revoked.isGuardian(3, guardian));
      });
      test("failed guardian grant persistence rolls back both permission scopes", () -> {
         Path directory = Files.createTempDirectory("shengxian-guardian-write-failure-");
         Path file = directory.resolve("aperture-space.properties");
         ApertureSpaceStore store = new ApertureSpaceStore(file);
         Files.createDirectory(file);
         UUID owner = UUID.randomUUID();
         UUID guardian = UUID.randomUUID();
         expect(IOException.class, () -> store.addGuardianForAscension(owner, 6, guardian));
         check(!store.isAscensionGuardian(owner, guardian));
         check(!store.isGuardian(6, guardian));
      });
      test("ascension guardian invitation is promoted into the formed plot", () -> {
         Path directory = Files.createTempDirectory("shengxian-guardian-promotion-");
         Path file = directory.resolve("aperture-space.properties");
         UUID owner = UUID.randomUUID();
         UUID guardian = UUID.randomUUID();
         ApertureSpaceStore store = new ApertureSpaceStore(file);
         store.addGuardianForAscension(owner, -1, guardian);
         check(store.isAscensionGuardian(owner, guardian));
         check(!store.isGuardian(5, guardian));
         store.reconcileAscensionGuardians(owner, 5);
         ApertureSpaceStore reopened = new ApertureSpaceStore(file);
         check(reopened.isAscensionGuardian(owner, guardian));
         check(reopened.isGuardian(5, guardian));
      });
      test("aperture guardian list persists and revokes", () -> {
         Path var0x = Files.createTempDirectory("shengxian-guardian-");
         Path var1 = var0x.resolve("aperture-space.properties");
         ApertureSpaceStore var2 = new ApertureSpaceStore(var1);
         check(!var2.isGuardian(2, OWNER));
         var2.addGuardian(2, OWNER);
         check(var2.isGuardian(2, OWNER));
         ApertureSpaceStore var3 = new ApertureSpaceStore(var1);
         check(var3.isGuardian(2, OWNER));
         var3.removeGuardian(2, OWNER);
         check(!var3.isGuardian(2, OWNER));
      });
      test("aperture guardian record rejects malformed entries", () -> {
         Path var0x = Files.createTempDirectory("shengxian-guardian-bad-");
         Path var1 = var0x.resolve("aperture-space.properties");
         Files.writeString(var1, "guardian.bad.uuid=true\n");
         expect(IOException.class, () -> new ApertureSpaceStore(var1));
      });
      test("consumed BenMingGu record is idempotent and rejects a different Gu", () -> {
         Path var0x = Files.createTempDirectory("shengxian-consumed-");
         Path var1 = var0x.resolve("benminggu.properties");
         BenMingGuStore var2 = new BenMingGuStore(var1);
         var2.bind(OWNER, "a".repeat(64), "\u672c\u547d\u86ca");
         var2.markConsumed(OWNER, "a".repeat(64), "\u672c\u547d\u86ca");
         check(var2.isConsumed(OWNER, "a".repeat(64)));
         var2.markConsumed(OWNER, "a".repeat(64), "\u672c\u547d\u86ca");
         expect(IOException.class, () -> var2.markConsumed(OWNER, "b".repeat(64), "\u53e6\u4e00\u53ea\u86ca"));
      });
      test("consumed BenMingGu record survives restart and rejects malformed entries", () -> {
         Path var0x = Files.createTempDirectory("shengxian-consumed-restart-");
         Path var1 = var0x.resolve("benminggu.properties");
         new BenMingGuStore(var1).markConsumed(OWNER, "a".repeat(64), "\u672c\u547d\u86ca");
         check(new BenMingGuStore(var1).isConsumed(OWNER, "a".repeat(64)));
         Files.writeString(var1, "consumed.not-a-uuid.fingerprint=" + "a".repeat(64) + "\n");
         expect(IOException.class, () -> new BenMingGuStore(var1));
      });
      test("formed aperture embeds the original BenMingGu without inventing a return or upgrade", () -> {
         Path dir = Files.createTempDirectory("shengxian-benming-embedded-");
         Path file = dir.resolve("benminggu.properties");
         String fingerprint = "a".repeat(64);
         String originalStack = "{id:\"guzhenren:test_gu\",Count:1b}";
         BenMingGuStore store = new BenMingGuStore(file);
         store.bind(OWNER, fingerprint, "本命蛊");
         store.beginOffer(OWNER, fingerprint, "本命蛊", originalStack);
         store.markOfferStored(OWNER);
         BenMingGuStore.Escrow embedded = store.markEmbedded(OWNER);
         check(embedded.state() == BenMingGuStore.EscrowState.EMBEDDED);
         check(embedded.itemSnbt().equals(originalStack));
         BenMingGuStore restored = new BenMingGuStore(file);
         check(restored.findEscrow(OWNER).orElseThrow().equals(embedded));
         check(restored.markEmbedded(OWNER).equals(embedded));
         expect(IOException.class, () -> restored.beginReturn(OWNER));
         check(new BenMingGuStore(file).findEscrow(OWNER).orElseThrow().state()
               == BenMingGuStore.EscrowState.EMBEDDED);
      });      test("BenMingGu escrow survives restart and moves through offer and return idempotently", () -> {
         Path root = Files.createTempDirectory("shengxian-benming-escrow-");
         Path file = root.resolve("benminggu.properties");
         String fingerprint = "a".repeat(64);
         BenMingGuStore store = new BenMingGuStore(file);
         store.bind(OWNER, fingerprint, "本命蛊");
         var intent = store.beginOffer(OWNER, fingerprint, "本命蛊", "{id:\"minecraft:stone\",count:1b}");
         check(intent.state() == BenMingGuStore.EscrowState.OFFER_PENDING);
         BenMingGuStore reopened = new BenMingGuStore(file);
         check(reopened.findEscrow(OWNER).orElseThrow().equals(intent));
         var held = reopened.markOfferStored(OWNER);
         check(held.state() == BenMingGuStore.EscrowState.STORED);
         var returning = reopened.beginReturn(OWNER);
         check(returning.state() == BenMingGuStore.EscrowState.RETURN_PENDING);
         BenMingGuStore reopenedAgain = new BenMingGuStore(file);
         check(reopenedAgain.markReturned(OWNER).state() == BenMingGuStore.EscrowState.RETURNED);
         check(reopenedAgain.markReturned(OWNER).state() == BenMingGuStore.EscrowState.RETURNED);
         expect(IOException.class, () -> reopenedAgain.beginOffer(OWNER, "b".repeat(64), "另一只蛊", "{id:\"minecraft:stone\",count:1b}"));
      });
      test("concurrent plot reservations are unique", () -> {
         Path var0x = Files.createTempDirectory("shengxian-plot-concurrent-");
         Path var1 = var0x.resolve("aperture-space.properties");
         var store = new ApertureSpaceStore(var1);
         var pool = Executors.newFixedThreadPool(8);
         try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 50; i++) futures.add(pool.submit(store::reservePlot));
            java.util.Set<Integer> plots = new java.util.TreeSet<>();
            for (var future : futures) plots.add(future.get(10, TimeUnit.SECONDS));
            check(plots.size() == 50);
            check(plots.contains(0) && plots.contains(49));
         } finally {
            pool.shutdownNow();
         }
         check(new ApertureSpaceStore(var1).isReserved(49));
      });
      test("killer move observer reports only durability slots changed by the procedure", () -> {
         double[][] before = new double[4][10];
         double[][] after = new double[4][10];
         before[0][0] = after[0][0] = 7.0;
         before[0][1] = 5.0;
         after[0][1] = 10.0;
         before[2][8] = 101.0;
         after[2][8] = 102.0;
         check(KillerMoveDurabilityChanges.changedSlots(before, after).equals(List.of(
               new KillerMoveDurabilityChanges.Slot(1, 2),
               new KillerMoveDurabilityChanges.Slot(3, 9))));
      });
      test("gu activation ledger records only observations the server accepted", () -> {
         GuActivationLedger ledger = new GuActivationLedger(8);
         UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
         check(!ledger.record(new GuActivation(owner, "guzhenren:yue_guang_gu",
               GuActivation.Entry.RIGHT_CLICK_USE, GuActivation.Outcome.REJECTED, 10L, 1L)));
         check(ledger.size() == 0);
         check(ledger.record(new GuActivation(owner, "guzhenren:yue_guang_gu",
               GuActivation.Entry.RIGHT_CLICK_USE, GuActivation.Outcome.ACCEPTED, 10L, 1L)));
         check(ledger.size(owner) == 1);
         check(ledger.recent(owner).getFirst().accepted());
      });
      test("gu activation replay never counts twice", () -> {
         GuActivationLedger ledger = new GuActivationLedger(8);
         UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
         GuActivation once = new GuActivation(owner, "guzhenren:ai_bie_chi",
               GuActivation.Entry.ATTACK_HURT_ENEMY, GuActivation.Outcome.COMPLETED, 20L, 2L);
         check(ledger.record(once));
         check(!ledger.record(once));
         check(ledger.count(owner, GuActivation.Entry.ATTACK_HURT_ENEMY) == 1);
         check(ledger.size() == 1);
      });
      test("gu activation never crosses owners", () -> {
         GuActivationLedger ledger = new GuActivationLedger(8);
         UUID first = UUID.fromString("00000000-0000-0000-0000-0000000000ac");
         UUID second = UUID.fromString("00000000-0000-0000-0000-0000000000ad");
         check(ledger.record(new GuActivation(first, "guzhenren:ding_gu_2",
               GuActivation.Entry.BLOCK_USE_ON, GuActivation.Outcome.ACCEPTED, 30L, 3L)));
         check(ledger.size(second) == 0);
         check(ledger.recent(second).isEmpty());
         check(ledger.count(second, GuActivation.Entry.BLOCK_USE_ON) == 0);
         check(ledger.count(first, GuActivation.Entry.BLOCK_USE_ON) == 1);
      });
      test("every audited activation entry is distinguishable and feeding is not activation", () -> {
         GuActivationLedger ledger = new GuActivationLedger(16);
         UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000ae");
         long tick = 40L;
         for (GuActivation.Entry entry : GuActivation.Entry.values()) {
            check(ledger.record(new GuActivation(owner, "guzhenren:xiaoguanggu", entry,
                  GuActivation.Outcome.ACCEPTED, tick++, 1L)));
            check(ledger.count(owner, entry) == 1);
            check(GuActivationPolicy.countsAsActivation(new GuActivation(owner,
                  "guzhenren:xiaoguanggu", entry, GuActivation.Outcome.ACCEPTED, tick, 1L)));
         }
         check(!GuActivationPolicy.countsAsActivation(new GuActivation(owner,
               "guzhenren:xiaoguanggu", GuActivation.Entry.INVENTORY_TICK,
               GuActivation.Outcome.REJECTED, 90L, 1L)));
         check(!ledger.record(new GuActivation(owner, "guzhenren:xiaoguanggu",
               GuActivation.Entry.INVENTORY_TICK, GuActivation.Outcome.REJECTED, 91L, 1L)));
      });
      test("gu activation ledger stays bounded per owner", () -> {
         GuActivationLedger ledger = new GuActivationLedger(3);
         UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000af");
         for (int i = 0; i < 6; i++) {
            check(ledger.record(new GuActivation(owner, "guzhenren:yue_guang_gu",
                  GuActivation.Entry.RIGHT_CLICK_USE, GuActivation.Outcome.ACCEPTED, 100L + i, 1L)));
         }
         check(ledger.size(owner) == 3);
         check(ledger.recent(owner).getFirst().serverTick() == 105L);
      });
      test("gu activation ledger bounds retained owners and resets evicted sequence", () -> {
         GuActivationLedger ledger = new GuActivationLedger(2, 2);
         UUID oldest = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
         UUID second = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
         UUID newest = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
         check(ledger.nextSequence(oldest) == 1L);
         check(ledger.record(new GuActivation(oldest, "guzhenren:yue_guang_gu",
               GuActivation.Entry.RIGHT_CLICK_USE, GuActivation.Outcome.ACCEPTED, 200L, 1L)));
         check(ledger.nextSequence(second) == 1L);
         check(ledger.record(new GuActivation(second, "guzhenren:ding_gu_2",
               GuActivation.Entry.BLOCK_USE_ON, GuActivation.Outcome.ACCEPTED, 201L, 1L)));
         check(ledger.nextSequence(newest) == 1L);
         check(ledger.size(oldest) == 0);
         check(ledger.size(second) == 1);
         check(ledger.nextSequence(oldest) == 1L);
         check(ledger.size(second) == 1);
      });      test("gu activation observation is limited to irreversible ascension phases", () -> {
         check(!GuActivationPolicy.mayObserveDuringAscension(Phase.PREPARING));
         check(GuActivationPolicy.mayObserveDuringAscension(Phase.BREAKING));
         check(GuActivationPolicy.mayObserveDuringAscension(Phase.BROKEN));
         check(GuActivationPolicy.mayObserveDuringAscension(Phase.ABSORBING));
         check(GuActivationPolicy.mayObserveDuringAscension(Phase.OPENING));
         check(GuActivationPolicy.mayObserveDuringAscension(Phase.FORMING));
         check(!GuActivationPolicy.mayObserveDuringAscension(Phase.IMMORTAL));
         check(!GuActivationPolicy.mayObserveDuringAscension(Phase.FAILED));
      });      test("Gu backlash counts only completed activations during absorption", () -> {
         UUID session = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
         GuBacklashState state = GuBacklashState.empty(session, 8);
         GuActivation completed = new GuActivation(OWNER, "guzhenren:test_gu", GuActivation.Entry.KEYBIND,
               GuActivation.Outcome.COMPLETED, 1L, 1L);
         var outside = GuBacklashState.observe(state, session, Phase.OPENING, 8, completed, true, false);
         check(outside.decision() == GuBacklashState.Decision.IGNORED_PHASE);
         check(outside.state().pressure() == 0);
         var acceptedOnly = GuBacklashState.observe(state, session, Phase.ABSORBING, 8,
               new GuActivation(OWNER, "guzhenren:test_gu", GuActivation.Entry.KEYBIND,
                     GuActivation.Outcome.ACCEPTED, 2L, 2L), true, false);
         check(acceptedOnly.decision() == GuBacklashState.Decision.IGNORED_UNCONFIRMED);
         check(acceptedOnly.state().pressure() == 0);
         check(!GuActivationPolicy.mayApplyBacklash(Phase.ABSORBING,
               new GuActivation(OWNER, "guzhenren:test_gu", GuActivation.Entry.INVENTORY_TICK,
                     GuActivation.Outcome.COMPLETED, 3L, 3L)));
      });
      test("Gu backlash threshold consumes one activation and preserves remainder", () -> {
         UUID session = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
         GuBacklashState state = GuBacklashState.empty(session, 8);
         for (int i = 1; i <= 10; i++) {
            GuActivation activation = new GuActivation(OWNER, "guzhenren:test_gu", GuActivation.Entry.KEYBIND,
                  GuActivation.Outcome.COMPLETED, i, i);
            var transition = GuBacklashState.observe(state, session, Phase.ABSORBING, 8,
                  activation, true, false);
            state = transition.state();
            check(transition.decision() == (i == 8
                  ? GuBacklashState.Decision.LOSS_DUE
                  : i == 10 ? GuBacklashState.Decision.PRESSURE_ONLY
                  : i == 9 ? GuBacklashState.Decision.PRESSURE_ONLY
                  : GuBacklashState.Decision.PRESSURE_ONLY));
         }
         check(state.pressure() == 2);
         check(state.sequence() == 10L);
         check(state.threshold() == 8);
      });
      test("Gu backlash freezes threshold, resets on session change and ignores duplicate events", () -> {
         UUID first = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");
         UUID second = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000004");
         GuBacklashState state = GuBacklashState.empty(first, 2);
         for (int i = 1; i <= 2; i++) {
            GuActivation activation = new GuActivation(OWNER, "guzhenren:test_gu", GuActivation.Entry.KEYBIND,
                  GuActivation.Outcome.COMPLETED, 10L + i, i);
            state = GuBacklashState.observe(state, first, Phase.ABSORBING, 8,
                  activation, true, false).state();
         }
         check(state.pressure() == 0 && state.threshold() == 2 && state.sequence() == 2L);
         GuActivation replay = new GuActivation(OWNER, "guzhenren:test_gu", GuActivation.Entry.KEYBIND,
               GuActivation.Outcome.COMPLETED, 20L, 3L);
         var firstUse = GuBacklashState.observe(state, first, Phase.ABSORBING, 8, replay, true, false);
         var duplicate = GuBacklashState.observe(firstUse.state(), first, Phase.ABSORBING, 8, replay, true, false);
         check(duplicate.decision() == GuBacklashState.Decision.DUPLICATE);
         check(duplicate.state().pressure() == 1);
         var newSession = GuBacklashState.observe(duplicate.state(), second, Phase.ABSORBING, 8,
               new GuActivation(OWNER, "guzhenren:test_gu", GuActivation.Entry.KEYBIND,
                     GuActivation.Outcome.COMPLETED, 21L, 4L), true, false);
         check(newSession.state().sessionId().equals(second));
         check(newSession.state().pressure() == 1 && newSession.state().threshold() == 8);
         check(newSession.state().sequence() == 1L);
      });
      test("Gu backlash consumes threshold without substituting a protected or missing target", () -> {
         UUID session = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");
         GuBacklashState protectedState = GuBacklashState.empty(session, 1);
         GuActivation activation = new GuActivation(OWNER, "guzhenren:test_gu", GuActivation.Entry.KEYBIND,
               GuActivation.Outcome.COMPLETED, 30L, 1L);
         var protectedTarget = GuBacklashState.observe(protectedState, session, Phase.ABSORBING, 1,
               activation, true, true);
         check(protectedTarget.decision() == GuBacklashState.Decision.VITAL_PROTECTED);
         check(protectedTarget.state().pressure() == 0 && protectedTarget.state().sequence() == 1L);
         var absentTarget = GuBacklashState.observe(GuBacklashState.empty(session, 1), session,
               Phase.ABSORBING, 1, activation, false, false);
         check(absentTarget.decision() == GuBacklashState.Decision.TARGET_UNAVAILABLE);
         check(absentTarget.state().pressure() == 0 && absentTarget.state().sequence() == 1L);
      });
      test("Gu backlash quarantine blocks subsequent pressure and loss", () -> {
         UUID session = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000006");
         GuBacklashState quarantined = GuBacklashState.empty(session, 1).quarantine("uncertain-save");
         GuActivation activation = new GuActivation(OWNER, "guzhenren:test_gu", GuActivation.Entry.KEYBIND,
               GuActivation.Outcome.COMPLETED, 40L, 1L);
         var transition = GuBacklashState.observe(quarantined, session, Phase.ABSORBING, 1,
               activation, true, false);
         check(transition.decision() == GuBacklashState.Decision.QUARANTINED);
         check(transition.state().pressure() == 0 && transition.state().sequence() == 0L);
      });
      test("Gu backlash target resolution requires one exact source slot", () -> {
         Object target = new Object();
         Object other = new Object();
         check(GuBacklashTargetLocator.inventorySlot(target, List.of(other, target), List.of(), List.of()).orElseThrow() == 1);
         check(GuBacklashTargetLocator.inventorySlot(target, List.of(other), List.of(), List.of()).isEmpty());
         check(GuBacklashTargetLocator.inventorySlot(target, List.of(target), List.of(), List.of(target)).isEmpty());
         check(GuBacklashTargetLocator.inventorySlot(target, List.of(other), List.of(target), List.of()).orElseThrow() == 100);
         check(GuBacklashTargetLocator.inventorySlot(target, List.of(other), List.of(), List.of(target)).orElseThrow() == 150);
         check(GuBacklashTargetLocator.killerMoveField(3, 9).equals("ShaZhao3_GuChong9"));
         expect(IllegalArgumentException.class, () -> GuBacklashTargetLocator.killerMoveField(5, 1));
         expect(IllegalArgumentException.class, () -> GuBacklashTargetLocator.killerMoveField(1, 11));
      });
      test("upstream fingerprints pin the audited jar", () -> {
         check(UpstreamFingerprint.matchesOuter(UpstreamFingerprint.OUTER_SHA256));
         check(UpstreamFingerprint.matchesInner(UpstreamFingerprint.INNER_SHA256));
         check(UpstreamFingerprint.matchesOuter(UpstreamFingerprint.OUTER_SHA256.toUpperCase()));
         check(!UpstreamFingerprint.matchesOuter("0000000000000000000000000000000000000000000000000000000000000000"));
         check(!UpstreamFingerprint.matchesInner(null));
      });
      test("aperture online-step projection reports net essence and stall conditions", () -> {
         AscensionProgress funded = ApertureEngine.form(AscensionProgress.empty(), 1000.0, 16.0, 10.0, 1);
         var projection = ApertureEngine.projectAdvance(funded.aperture(), 20L, 0.01, 0.004);
         check(projection.internalTicks() == 320L);
         check(Math.abs(projection.upkeepCost() - 1.28) < 1e-9);
         check(Math.abs(projection.essenceProduction() - 3.2) < 1e-9);
         check(Math.abs(projection.netEssence() - 1.92) < 1e-9);
         check(projection.upkeepCovered());

         AscensionProgress poor = ApertureEngine.form(AscensionProgress.empty(), 1000.0, 1.0, 0.001, 1);
         var stalled = ApertureEngine.projectAdvance(poor.aperture(), 20L, 0.01, 0.004);
         check(stalled.internalTicks() == 20L);
         check(!stalled.upkeepCovered());
      });
      test("aperture upkeep is charged before production", () -> {
         AscensionProgress formed = ApertureEngine.form(AscensionProgress.empty(), 1000.0, 16.0, 10.0, 1);
         AscensionProgress next = ApertureEngine.advanceInternal(formed, 1L, 0.01, 0.004);
         check(next.aperture().internalTicks() == 16L);
         check(Math.abs(next.aperture().essence() - 10.096) < 1e-9);
         check(next.aperture().essence() < formed.aperture().essence() + 16 * 0.01);
      });
      test("aperture stalls when the balance cannot cover upkeep", () -> {
         AscensionProgress poor = ApertureEngine.form(AscensionProgress.empty(), 1000.0, 1.0, 0.001, 1);
         AscensionProgress stalled = ApertureEngine.advanceInternal(poor, 1L, 0.01, 0.004);
         check(stalled.aperture().internalTicks() == poor.aperture().internalTicks());
         check(stalled.aperture().essence() == poor.aperture().essence());
         AscensionProgress exactly = ApertureEngine.form(AscensionProgress.empty(), 1000.0, 1.0, 0.004, 1);
         AscensionProgress paid = ApertureEngine.advanceInternal(exactly, 1L, 0.01, 0.004);
         check(paid.aperture().internalTicks() == 1L);
         check(Math.abs(paid.aperture().essence() - 0.01) < 1e-9);
         check(paid.aperture().essence() >= 0.0);
      });
      test("zero upkeep keeps the previous production path", () -> {
         AscensionProgress formed = ApertureEngine.form(AscensionProgress.empty(), 2000.0, 8.0, 5.0, 2);
         AscensionProgress legacy = ApertureEngine.advanceInternal(formed, 3L, 0.01);
         AscensionProgress explicit = ApertureEngine.advanceInternal(formed, 3L, 0.01, 0.0);
         check(legacy.aperture().equals(explicit.aperture()));
         check(Math.abs(legacy.aperture().essence() - (5.0 + 24 * 0.01)) < 1e-9);
      });
      test("aperture upkeep rejects invalid rates and unformed ledgers", () -> {
         AscensionProgress formed = ApertureEngine.form(AscensionProgress.empty(), 1000.0, 4.0, 1.0, 1);
         expect(IllegalArgumentException.class, () -> ApertureEngine.advanceInternal(formed, 1L, 0.01, -0.1));
         expect(IllegalArgumentException.class, () -> ApertureEngine.advanceInternal(formed, 1L, 0.01, Double.NaN));
         check(ApertureEngine.advanceInternal(AscensionProgress.empty(), 1L, 0.01, 0.5)
               .aperture().equals(AscensionProgress.empty().aperture()));
      });
      test("immortal gu catalog only lists canon-backed entries", () -> {
         check(XianGuCatalog.all().size() == 5);
         // Every entry must cite an evidence id; the 13th-century blood-dao Gu has no name or
         // effect in the text, so it is recorded as UNSPECIFIED rather than invented.
         check(XianGuCatalog.all().stream().allMatch(entry -> !entry.canonRef().isBlank()));
         XianGu unnamed = XianGuCatalog.find(LeftoverRefinement.LEFTOVER_GU).orElseThrow();
         check(unnamed.effect() == XianGu.Effect.UNSPECIFIED && !unnamed.reusable());
         XianGu gu = XianGuCatalog.find("pai_nan").orElseThrow();
         check(gu.canonRef().equals("CANON-009"));
         check(gu.essenceCost() > 0);
         check(gu.effect() == XianGu.Effect.CARRIER_STRIKE);
         check(XianGuCatalog.find("does_not_exist").isEmpty());
         check(XianGuCatalog.find(null).isEmpty());
      });
      test("man as before restores bounded recent health and new injury states", ImmortalGuRulesTest::health);
      test("fixed immortal travel completes channel and cancels on invalid holder state", ImmortalGuRulesTest::travel);
      test("landscape restoration requires confirmed no-drop damage and consumes each record once", ImmortalGuRulesTest::landscape);
      test("representative immortal gu are reusable and keep ownership across repeated uses", () -> {
         for (String id : List.of("ding_xian_you", "ren_ru_gu_immortal", "jiang_shan_ru_gu")) {
            check(XianGuCatalog.find(id).orElseThrow().reusable());
         }
         XianGuRuntime runtime = XianGuRuntime.empty().grant("ding_xian_you");
         UUID operation = UUID.randomUUID();
         runtime = runtime.begin(operation, "ding_xian_you", 100L, 10.0, 3.0, 200L, "{}");
         check(runtime.operation().orElseThrow().essenceAfter() == 7.0);
         check(runtime.held().contains("ding_xian_you"));
         XianGuRuntime charged = runtime;
         check(charged.begin(operation, "ding_xian_you", 100L, 10.0, 3.0, 200L, "{}").equals(charged));
         expect(IllegalStateException.class, () -> charged.begin(UUID.randomUUID(),
               "ding_xian_you", 101L, 7.0, 3.0, 200L, "{}"));
         runtime = runtime.finish(operation, XianGuRuntime.Stage.COMMITTED);
         XianGuRuntime committed = runtime;
         expect(IllegalStateException.class, () -> committed.begin(UUID.randomUUID(),
               "ding_xian_you", 299L, 7.0, 3.0, 200L, "{}"));
         runtime = runtime.begin(UUID.randomUUID(), "ding_xian_you", 300L, 7.0, 3.0, 200L, "{}");
         check(runtime.operation().orElseThrow().essenceAfter() == 4.0);
         check(runtime.sequence() == 2L && runtime.held().size() == 1);
         UUID second = runtime.operation().orElseThrow().operationId();
         runtime = runtime.finish(second, XianGuRuntime.Stage.UNCERTAIN);
         XianGuRuntime uncertain = runtime;
         expect(IllegalStateException.class, () -> uncertain.begin(UUID.randomUUID(),
               "ding_xian_you", 1000L, 4.0, 3.0, 200L, "{}"));
         runtime = runtime.finish(second, XianGuRuntime.Stage.ACKNOWLEDGED);
         check(runtime.held().contains("ding_xian_you"));
      });
      test("immortal gu runtime persists eight marks and rejects corrupt cooldown state", () -> {
         XianGuRuntime runtime = XianGuRuntime.empty().grant("ding_xian_you")
               .grant("jiang_shan_ru_gu").withRecording(true);
         for (int slot = 0; slot < 8; slot++) {
            runtime = runtime.withMark(slot, new XianGuRuntime.Mark("minecraft:overworld",
                  slot + 0.5, 65.0, -2.5, 90.0f, 0.0f, "a".repeat(64)));
         }
         runtime = runtime.selectSlot(7);
         Properties saved = new Properties();
         XianGuStateCodec.write(saved, runtime);
         check(XianGuStateCodec.read(saved).equals(runtime));
         XianGuRuntime ready = runtime;
         expect(IllegalArgumentException.class, () -> ready.selectSlot(8));
         expect(IllegalArgumentException.class, () -> ready.grant("does_not_exist"));
         saved.setProperty("xiangu.version", "999");
         expect(IOException.class, () -> XianGuStateCodec.read(saved));
      });
      test("aperture visitors can record a travel mark without gaining block interaction", () -> {
         check(AperturePlotPolicy.mayUseBoundItem(false, "ding_xian_you", true));
         check(!AperturePlotPolicy.mayUseBoundItem(false, "ding_xian_you", false));
         check(!AperturePlotPolicy.mayUseBoundItem(false, "jiang_shan_ru_gu", true));
         check(AperturePlotPolicy.mayUseBoundItem(true, "jiang_shan_ru_gu", false));
      });
      test("immortal reusable Gu debit survives restart and aperture upkeep without replay", () -> {
         Path root = Files.createTempDirectory("shengxian-immortal-gu-v11-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         FileSessionRepository repository = new FileSessionRepository(root);
         AscensionService service = new AscensionService(repository, ALL);
         AscensionRecord formed = formedFixture(service, key, 1);
         formed = service.mutate(key, formed.session().stateRevision(), 8L, 8L,
               p -> p.withXianGu(p.xianGu().grant("ren_ru_gu_immortal")));
         UUID operation = UUID.randomUUID();
         AscensionRecord charged = service.mutate(key, formed.session().stateRevision(), 9L, 9L,
               p -> XianGuOperations.charge(p, operation, "ren_ru_gu_immortal", 9L, 2.0, 60L, "{}"));
         double balance = charged.progress().aperture().essence();
         check(balance == 18.0);
         check(XianGuOperations.charge(charged.progress(), operation, "ren_ru_gu_immortal",
               9L, 2.0, 60L, "{}").aperture().essence() == balance);
         AscensionRecord restored = repository.find(key).orElseThrow();
         check(restored.progress().xianGu().equals(charged.progress().xianGu()));
         check(ApertureEngine.advanceInternal(restored.progress(), 20L, 0.01, 0.004)
               .xianGu().equals(restored.progress().xianGu()));
         check("11".equals(read(recordFile(root)).getProperty("schema")));
         AscensionRecord done = service.mutate(key, restored.session().stateRevision(), 10L, 10L,
               p -> p.withXianGu(p.xianGu().finish(operation, XianGuRuntime.Stage.COMMITTED)));
         check(done.progress().xianGu().held().contains("ren_ru_gu_immortal"));
         expect(IOException.class, () -> service.mutate(key, done.session().stateRevision(), 11L, 11L,
               p -> p.withQi(new AscensionProgress.Qi(1, 1, 1, 0, 0, 0))));
      });
      test("schema 11 rejects reusable ownership outside immortal phase and preserves schema 10", () -> {
         Path root = Files.createTempDirectory("shengxian-gu-phase-");
         ApertureKey key = new ApertureKey(OWNER, "first");
         FileSessionRepository repository = new FileSessionRepository(root);
         AscensionService service = new AscensionService(repository, ALL);
         service.start(key, true, Optional.of(new RuleProfile("fixture", true, ALL)), 1L);
         Path file = recordFile(root);
         Properties data = read(file);
         XianGuStateCodec.write(data, XianGuRuntime.empty().grant("ding_xian_you"));
         write(file, data);
         byte[] original = Files.readAllBytes(file);
         expect(IOException.class, () -> repository.find(key));
         check(java.util.Arrays.equals(original, Files.readAllBytes(file)));
         data.keySet().removeIf(name -> name.toString().startsWith("xiangu."));
         data.setProperty("schema", "10");
         write(file, data);
         check(repository.find(key).orElseThrow().progress().xianGu().equals(XianGuRuntime.empty()));
         check("10".equals(read(file).getProperty("schema")));
      });
      test("immortal gu ledger counts grants and uses without shrinking", () -> {
         XianGu gu = XianGuCatalog.find("pai_nan").orElseThrow();
         AscensionProgress held = XianGuLedger.hold(AscensionProgress.empty(), gu);
         check(XianGuLedger.holds(held, "pai_nan"));
         check(XianGuLedger.held(held).equals(Set.of("pai_nan")));
         check(XianGuLedger.owned(held, "pai_nan") == 1 && XianGuLedger.used(held, "pai_nan") == 0);
         AscensionProgress used = XianGuLedger.consume(held, gu);
         check(!XianGuLedger.holds(used, "pai_nan"));
         check(XianGuLedger.held(used).isEmpty());
         expect(IllegalStateException.class, () -> XianGuLedger.consume(used, gu));
         AscensionProgress regranted = XianGuLedger.hold(used, gu);
         check(XianGuLedger.holds(regranted, "pai_nan"));
         check(XianGuLedger.owned(regranted, "pai_nan") == 2);
      });
      test("immortal gu use spends essence and destroys one carrier", () -> {
         XianGu gu = XianGuCatalog.find("pai_nan").orElseThrow();
         AscensionProgress progress = ApertureEngine.form(AscensionProgress.empty(), 1000.0, 1.0, 10.0, 1);
         progress = progress.withQi(new AscensionProgress.Qi(0, 0, 0, 40, 40, 0));
         progress = TribulationEngine.startIfDue(progress, 10L, TribulationParameters.taiBaiWitnessed()).progress();
         check(progress.tribulations().size() == 2);
         progress = XianGuLedger.hold(progress, gu);
         String carrierId = progress.tribulations().getFirst().id();
         AscensionProgress used = XianGuLedger.consume(TribulationEngine.strike(
               ApertureEngine.spendEssence(progress, gu.essenceCost()), carrierId, 1_000_000).progress(), gu);
         check(Math.abs(used.aperture().essence() - 5.0) < 1e-9);
         check(used.tribulations().size() == 1);
         check(used.tribulations().getFirst().type() == AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER);
         check(!XianGuLedger.holds(used, "pai_nan"));
         expect(IllegalStateException.class, () -> ApertureEngine.spendEssence(used, 99.0));
      });
      test("forced immortal gu strike drains essence and leaves an irreversible dao mark", () -> {
         // CANON-009: 黑楼兰 forced a six-turn Pai Nan Gu, still destroyed a chime carrier,
         // burst both eyes and could never restore the 道痕 in the sockets.
         XianGu gu = XianGuCatalog.find("pai_nan").orElseThrow();
         AscensionProgress progress = ApertureEngine.form(AscensionProgress.empty(), 1000.0, 1.0, 3.0, 1);
         progress = progress.withQi(new AscensionProgress.Qi(0, 0, 0, 40, 40, 0));
         progress = TribulationEngine.startIfDue(progress, 10L, TribulationParameters.taiBaiWitnessed()).progress();
         progress = XianGuLedger.hold(progress, gu);
         int carriers = progress.tribulations().size();
         String carrierId = progress.tribulations().getFirst().id();
         check(progress.aperture().essence() < gu.essenceCost());
         check(!XianGuForcedUse.eyesDamaged(progress));
         var forced = XianGuForcedUse.force(progress, gu, carrierId, 1_000_000);
         check(forced.firstTimeDamaged());
         check(Math.abs(forced.progress().aperture().essence()) < 1e-9);
         check(forced.progress().tribulations().size() == carriers - 1);
         check(!XianGuLedger.holds(forced.progress(), "pai_nan"));
         check(XianGuForcedUse.eyesDamaged(forced.progress()));
         // The mark is append-only: forcing again after a re-grant must not stack a second one.
         AscensionProgress regranted = XianGuLedger.hold(forced.progress(), gu);
         // Refill a little essence through the normal internal clock instead of re-forming.
         regranted = ApertureEngine.advanceInternal(regranted, 100L, 1.0, 0.0);
         check(regranted.aperture().essence() > 0);
         var again = XianGuForcedUse.force(regranted, gu, regranted.tribulations().getFirst().id(), 1_000_000);
         check(!again.firstTimeDamaged());
         check(again.progress().appliedEffects().stream()
               .filter(key -> key.equals(XianGuForcedUse.EYES_DAO_TAO)).count() == 1);
      });
      test("forced strike is rejected without essence, carrier or a consumable strike gu", () -> {
         XianGu gu = XianGuCatalog.find("pai_nan").orElseThrow();
         AscensionProgress progress = ApertureEngine.form(AscensionProgress.empty(), 1000.0, 1.0, 3.0, 1);
         progress = progress.withQi(new AscensionProgress.Qi(0, 0, 0, 40, 40, 0));
         progress = TribulationEngine.startIfDue(progress, 10L, TribulationParameters.taiBaiWitnessed()).progress();
         AscensionProgress held = XianGuLedger.hold(progress, gu);
         String carrierId = held.tribulations().getFirst().id();
         AscensionProgress empty = ApertureEngine.form(AscensionProgress.empty(), 1000.0, 1.0, 0.0, 1);
         expect(IllegalStateException.class, () -> XianGuForcedUse.force(empty, gu, carrierId, 1_000_000));
         AscensionProgress noCarrier = ApertureEngine.form(AscensionProgress.empty(), 1000.0, 1.0, 3.0, 1);
         expect(IllegalStateException.class, () -> XianGuForcedUse.force(noCarrier, gu, carrierId, 1_000_000));
         XianGu reusable = XianGuCatalog.find("ding_xian_you").orElseThrow();
         expect(IllegalArgumentException.class, () -> XianGuForcedUse.force(held, reusable, carrierId, 1_000_000));
      });
      test("inner calamity brews inside a formed aperture only for the witnessed example", () -> {
         // CANON-012: 太白云生 的仙窍形成期间，内部酝酿出另一场天劫地灾。
         AscensionProgress formed = ApertureEngine.form(AscensionProgress.empty(), 1_000_000.0, 6.0, 20.0, 1);
         AscensionProgress loaded = formed.withQi(new AscensionProgress.Qi(0, 0, 0, 40, 0, 0));
         var anchor = new AscensionProgress.CarrierAnchor("shengxian:aperture", 1, 64, 1,
               java.util.UUID.randomUUID());
         AscensionProgress brewed = InnerTribulation.brew(loaded, 500L,
               TribulationParameters.taiBaiWitnessed(), anchor);
         check(brewed.tribulations().size() == 1);
         check(InnerTribulation.isInner(brewed.tribulations().getFirst().id()));
         check(brewed.tribulations().getFirst().type() == AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND);
         check(brewed.tribulations().getFirst().carrierAnchor().dimension().equals("shengxian:aperture"));
         // The leftover heaven qi that fuelled it is consumed.
         check(brewed.qi().ambientHeaven() < loaded.qi().ambientHeaven());
         // A general ordinary route has no verified inner calamity (GAP-001).
         check(InnerTribulation.brew(loaded, 500L, TribulationParameters.standard(), anchor)
               .tribulations().size() == 1);
         // An unformed aperture cannot hold one, and a second brew never stacks.
         check(InnerTribulation.brew(AscensionProgress.empty().withQi(loaded.qi()), 500L,
               TribulationParameters.taiBaiWitnessed(), anchor).tribulations().isEmpty());
         check(InnerTribulation.brew(brewed, 900L, TribulationParameters.taiBaiWitnessed(), anchor)
               .tribulations().size() == 1);
      });
      test("leftover qi refines the unnamed blood-dao immortal gu once", () -> {
         // CANON-013: 方正 成窍后，残余天地二气把本命血道凡蛊升炼为第二只血道仙蛊。
         AscensionProgress formed = ApertureEngine.form(AscensionProgress.empty(), 1_000_000.0, 6.0, 20.0, 1);
         AscensionProgress loaded = formed.withQi(new AscensionProgress.Qi(0, 0, 0, 40, 40, 0));
         check(!LeftoverRefinement.alreadyRefined(loaded));
         var result = LeftoverRefinement.refine(loaded, TribulationParameters.fangZhengWitnessed());
         check(result.refined());
         check(LeftoverRefinement.alreadyRefined(result.progress()));
         check(XianGuLedger.holds(result.progress(), LeftoverRefinement.LEFTOVER_GU));
         check(result.progress().qi().ambientHeaven() < loaded.qi().ambientHeaven());
         // Once only: the mark is append-only and a second attempt changes nothing.
         check(!LeftoverRefinement.refine(result.progress(),
               TribulationParameters.fangZhengWitnessed()).refined());
         // Other profiles and other routes never refine.
         // Any ascension that leaves enough leftover qi refines; no profile permission exists.
         check(LeftoverRefinement.refine(loaded, TribulationParameters.standard()).refined());
         check(LeftoverRefinement.refine(loaded, TribulationParameters.taiBaiWitnessed()).refined());
         AscensionProgress starved = formed.withQi(new AscensionProgress.Qi(0, 0, 0, 5, 40, 0));
         check(!LeftoverRefinement.refine(starved, TribulationParameters.fangZhengWitnessed()).refined());
      });
      test("immortal gu definitions reject malformed entries", () -> {
         expect(IllegalArgumentException.class, () -> new XianGu("Pai Nan", "x", 1.0, XianGu.Effect.CARRIER_STRIKE, "CANON-009"));
         expect(IllegalArgumentException.class, () -> new XianGu("pai_nan", " ", 1.0, XianGu.Effect.CARRIER_STRIKE, "CANON-009"));
         expect(IllegalArgumentException.class, () -> new XianGu("pai_nan", "排难仙蛊", 0.0, XianGu.Effect.CARRIER_STRIKE, "CANON-009"));
         expect(IllegalArgumentException.class, () -> new XianGu("pai_nan", "排难仙蛊", 1.0, XianGu.Effect.CARRIER_STRIKE, " "));
      });
      test("calamity attacks warn between 0.8 and 1.5 seconds and carry positive damage", () -> {
         for (var type : AscensionProgress.Tribulation.Type.values()) {
            var attacks = CalamityCombat.attacksFor(type);
            if (type == AscensionProgress.Tribulation.Type.NONE) {
               check(attacks.isEmpty());
               continue;
            }
            check(attacks.size() >= 2 && attacks.size() <= 4);
            for (var attack : attacks) {
               check(attack.damage() > 0 && attack.damage() <= CalamityDamage.scaled(20));
               check(attack.radius() > 0);
               check(attack.telegraphTicks() >= 16 && attack.telegraphTicks() <= 30);
               check(attack.cooldownTicks() > 0);
            }
         }
      });
      test("calamity cycle runs telegraph then one strike tick then cooldown then the next attack", () -> {
         var attacks = CalamityCombat.attacksFor(AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER);
         var first = attacks.get(0);
         var cycle = CalamityCombat.start();
         check(cycle.stage() == CalamityCombat.Stage.TELEGRAPH && cycle.attackIndex() == 0);
         for (int i = 1; i < first.telegraphTicks(); i++) {
            cycle = CalamityCombat.advance(cycle, attacks);
            check(cycle.stage() == CalamityCombat.Stage.TELEGRAPH);
         }
         cycle = CalamityCombat.advance(cycle, attacks);
         check(cycle.stage() == CalamityCombat.Stage.STRIKE);
         cycle = CalamityCombat.advance(cycle, attacks);
         check(cycle.stage() == CalamityCombat.Stage.COOLDOWN && cycle.attackIndex() == 0);
         for (int i = 1; i < first.cooldownTicks(); i++) {
            cycle = CalamityCombat.advance(cycle, attacks);
            check(cycle.stage() == CalamityCombat.Stage.COOLDOWN);
         }
         cycle = CalamityCombat.advance(cycle, attacks);
         check(cycle.stage() == CalamityCombat.Stage.TELEGRAPH && cycle.attackIndex() == 1);
         check(CalamityCombat.currentAttack(cycle, attacks).kind() == attacks.get(1).kind());
      });
      test("one attack instance damages a target at most once", () -> {
         var ledger = new java.util.HashSet<CalamityCombat.HitId>();
         java.util.UUID carrier = java.util.UUID.randomUUID();
         java.util.UUID target = java.util.UUID.randomUUID();
         var id = new CalamityCombat.HitId(carrier, 7, target);
         check(CalamityCombat.applyOnce(ledger, id));
         check(!CalamityCombat.applyOnce(ledger, id));
         check(CalamityCombat.applyOnce(ledger, new CalamityCombat.HitId(carrier, 8, target)));
         check(CalamityCombat.applyOnce(ledger, new CalamityCombat.HitId(carrier, 7,
                 java.util.UUID.randomUUID())));
      });
      test("furious phase shortens telegraphs but never reaches zero", () -> {
         for (var type : AscensionProgress.Tribulation.Type.values()) {
            for (var attack : CalamityCombat.attacksFor(type)) {
               check(CalamityCombat.telegraphFor(attack, false) == attack.telegraphTicks());
               int furious = CalamityCombat.telegraphFor(attack, true);
               check(furious >= 1 && furious <= attack.telegraphTicks());
            }
         }
         check(CalamityCombat.BLOOD_RAIN_DAMAGE_PER_SECOND == CalamityDamage.scaled(2.0));
         check(CalamityDamage.MULTIPLIER == 500.0);
         check(Math.abs(CalamityDamage.impactDamage() - 1024000.0F) < 1.0F);
         check(CalamityCombat.FURIOUS_HEALTH_FRACTION == 0.5);
      });
      test("every calamity carrier holds at least a million health", () -> {
         for (var type : AscensionProgress.Tribulation.Type.values()) {
            check(CalamityCarrierStats.maxHealth(type) >= CalamityCarrierStats.MINIMUM_HEALTH);
         }
         check(CalamityCarrierStats.MINIMUM_HEALTH >= 1_000_000.0);
         check(CalamityCarrierStats.damageFromStrike(10.0)
                 == 10.0 * CalamityCarrierStats.PLAYER_DAMAGE_SCALE);
         check(CalamityCarrierStats.describe(1_500_000.0).endsWith("M"));
      });
      System.out.println("Framework tests: " + passed + " passed, " + failed + " failed");
      if (failed > 0) {
         System.exit(1);
      }
   }

   private static AscensionSession advance(AscensionSession var0, Phase var1, long var2) {
      return SessionFlow.advance(var0, var1, var0.stateRevision(), var2, var0.serverTick() + 1L);
   }

   private static AscensionSession session(Phase var0, boolean var1, long var2, long var4, long var6) {
      return new AscensionSession(UUID.randomUUID(), new ApertureKey(OWNER, "first"), var0, var1, var2, var4, var6, 0L);
   }

   private static AscensionSession session(Phase var0, boolean var1, long var2, long var4, long var6, long var8) {
      return new AscensionSession(UUID.randomUUID(), new ApertureKey(OWNER, "first"), var0, var1, var2, var4, var6, var8);
   }

   private static AscensionRecord absorbing(AscensionService var0, ApertureKey var1) throws IOException {
      AscensionRecord var2 = var0.start(var1, true, Optional.of(new RuleProfile("fixture", true, ALL)), 0L);
      var2 = var0.advance(var1, Phase.BREAKING, var2.session().stateRevision(), 1L, 1L);
      var2 = var0.advance(var1, Phase.BROKEN, var2.session().stateRevision(), 2L, 2L);
      return var0.advance(var1, Phase.ABSORBING, var2.session().stateRevision(), 3L, 3L);
   }

   private static AscensionRecord formedFixture(AscensionService var0, ApertureKey var1, int var2) throws IOException {
      AscensionRecord var3 = absorbing(var0, var1);
      var3 = var0.advance(var1, Phase.OPENING, var3.session().stateRevision(), 4L, 4L);
      var3 = var0.advance(var1, Phase.FORMING, var3.session().stateRevision(), 5L, 5L);
      var3 = var0.bindVitalGu(var1, var3.session().stateRevision(), 6L, 6L, "a".repeat(64), "\u672c\u547d\u86ca");
      return var0.formAperture(var1, var3.session().stateRevision(), 7L, 7L, 500.0, 4.0, 20.0, var2, "a".repeat(64));
   }

   private static AscensionProgress testProgressWithEffects(AscensionProgress var0, Set<String> var1) {
      return new AscensionProgress(
         var0.qi(),
         var0.risk(),
         var0.window(),
         var0.tribulations(),
         var0.aperture(),
         var0.vitalGu(),
         var0.insightEarned(),
         var0.insightSpent(),
         var1,
         var0.apertureDraftId(),
         var0.tribulationProfile(),
         var0.tribulationPressure(),
         var0.tribulationImpactSequence(),
         var0.tribulationImpactFailure()
      );
   }

   private static QiParameters params(double var0) {
      return params(var0, 0.0);
   }

   private static QiParameters params(double var0, double var2) {
      return new QiParameters(100.0, 100.0, 0.0, 0.0, 0.0, 10.0, 0.2, 1.0, var2, var0, 10.0, 2, 1, 1.0);
   }

   private static Path writeSchema1(Path var0, ApertureKey var1, UUID var2, Phase var3, boolean var4, long var5, long var7) throws IOException {
      FileSessionRepository var9 = new FileSessionRepository(var0);
      var9.save(new AscensionRecord(AscensionSession.prepare(var1), AscensionProgress.empty()), -1L);
      Path var10 = recordFile(var0);
      Properties var11 = new Properties();
      var11.setProperty("schema", "1");
      var11.setProperty("id", var2.toString());
      var11.setProperty("owner", var1.owner().toString());
      var11.setProperty("aperture", var1.apertureId());
      var11.setProperty("phase", var3.name());
      var11.setProperty("broken", Boolean.toString(var4));
      var11.setProperty("revision", Long.toString(var5));
      var11.setProperty("sequence", Long.toString(var7));
      write(var10, var11);
      return var10;
   }

   private static Path recordFile(Path var0) throws IOException {
      Path var1;
      try (Stream<Path> var2 = Files.walk(var0)) {
         var1 = var2.filter(var0x -> var0x.toString().endsWith(".properties")).findFirst().orElseThrow();
      }

      return var1;
   }

   private static Properties read(Path var0) throws IOException {
      Properties var1 = new Properties();

      try (InputStream var2 = Files.newInputStream(var0)) {
         var1.load(var2);
      }

      return var1;
   }

   private static void write(Path var0, Properties var1) throws IOException {
      try (OutputStream var2 = Files.newOutputStream(var0)) {
         var1.store(var2, "test fixture");
      }
   }

   private static String failureSettlementStateName(AscensionProgress progress) {
      try {
         return String.valueOf(progress.getClass().getMethod("failureSettlementState").invoke(progress));
      } catch (ReflectiveOperationException exception) {
         return "MISSING";
      }
   }

   private static final class TestShatterPort implements ShatterEssenceReconciler.Port {
      private final UUID sessionId;
      private final long phaseRevision;
      private final List<String> calls = new java.util.ArrayList<>();
      private ShatterEssenceTransaction.Receipt saved;
      private double current;
      private boolean failNextPersist;

      private TestShatterPort(UUID sessionId, long phaseRevision,
                              ShatterEssenceTransaction.Receipt saved, double current) {
         this.sessionId = sessionId;
         this.phaseRevision = phaseRevision;
         this.saved = saved;
         this.current = current;
      }

      @Override public Optional<ShatterEssenceTransaction.Receipt> readReceipt() {
         return Optional.ofNullable(saved);
      }

      @Override public double currentTrueEssence() { return current; }

      @Override public void persist(ShatterEssenceTransaction.Receipt receipt, Double expectedEssence)
            throws IOException {
         calls.add("persist:" + receipt.stage());
         if (failNextPersist) {
            failNextPersist = false;
            throw new IOException("fixture save failure");
         }
         if (expectedEssence != null && Double.compare(current, expectedEssence) != 0) {
            throw new IOException("fixture true essence mismatch");
         }
         saved = receipt;
      }

      @Override public void applyTarget(ShatterEssenceTransaction.Receipt prepared) throws IOException {
         calls.add("apply");
         if (prepared.stage() != ShatterEssenceTransaction.Stage.PREPARED
               || !prepared.equals(saved) || Double.compare(current, prepared.before()) != 0) {
            throw new IOException("fixture PREPARED state mismatch");
         }
         current = prepared.target();
      }

      @Override public void verify(ShatterEssenceTransaction.Receipt receipt, double expectedEssence)
            throws IOException {
         calls.add("verify:" + expectedEssence);
         if (!receipt.equals(saved) || Double.compare(current, expectedEssence) != 0) {
            throw new IOException("fixture readback mismatch");
         }
      }

      @Override public void verifyCommitted(ShatterEssenceTransaction.Receipt receipt) throws IOException {
         calls.add("verifyCommitted");
         if (!receipt.equals(saved) || receipt.stage() != ShatterEssenceTransaction.Stage.COMMITTED) {
            throw new IOException("fixture committed receipt mismatch");
         }
      }

      @Override public void markUncertain(UUID sessionId, long phaseRevision,
                                          Double worldBefore, double target) {
         calls.add("uncertain");
         if (saved == null) {
            saved = new ShatterEssenceTransaction.Receipt(sessionId, phaseRevision,
                  ShatterEssenceTransaction.Stage.UNCERTAIN, worldBefore, target, null);
         } else if (saved.sessionId().equals(sessionId) && saved.phaseRevision() == phaseRevision
               && saved.target() == target && saved.stage() != ShatterEssenceTransaction.Stage.UNCERTAIN) {
            saved = saved.uncertain();
         }
      }
   }

   private static void check(boolean var0) {
      if (!var0) {
         throw new AssertionError("Behavior assertion failed");
      }
   }

   private static void expect(Class<? extends Throwable> var0, FrameworkTest.Checked var1) throws Exception {
      try {
         var1.run();
      } catch (Throwable var3) {
         if (var0.isInstance(var3)) {
            return;
         }

         throw new AssertionError("Expected " + var0.getSimpleName() + ", got " + var3, var3);
      }

      throw new AssertionError("Expected " + var0.getSimpleName());
   }

   private static void test(String var0, FrameworkTest.Checked var1) {
      try {
         var1.run();
         passed++;
         System.out.println("PASS " + var0);
      } catch (Throwable var3) {
         failed++;
         System.out.println("FAIL " + var0 + ": " + var3);
         var3.printStackTrace(System.out);
      }
   }

   @FunctionalInterface
   interface Checked {
      void run() throws Exception;
   }
}
