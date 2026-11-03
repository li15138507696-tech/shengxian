package net.guzhenren.shengxian.core;

import java.util.Objects;

/** Read-only artistic projection. Durations and scales here are visual parameters, not canon rules. */
public final class AscensionVisuals {
    public static final int HEARTBEAT_TICKS = 10;
    public static final int TIMEOUT_TICKS = 60;
    public static final int FADE_TICKS = 12;
    public static final int RESULT_TICKS = 200;
    private AscensionVisuals() {}

    public record Frame(Phase phase, long revision, float progress, float energy,
                        float heaven, float earth, float human, float instability, boolean insight, int calamityMask, boolean confirmedFormed) {
        public Frame(Phase phase,long revision,float progress,float energy,float heaven,float earth,float human,
                     float instability,boolean insight) {
            this(phase,revision,progress,energy,heaven,earth,human,instability,insight,0,false);
        }
        public Frame {
            if (calamityMask < 0 || calamityMask > 15 || (confirmedFormed && phase != Phase.IMMORTAL))
                throw new IllegalArgumentException("Invalid ritual metadata");
            Objects.requireNonNull(phase);
            if (revision < 0) throw new IllegalArgumentException("Negative visual revision");
            for (float value : new float[]{progress, energy, heaven, earth, human, instability}) {
                if (!Float.isFinite(value) || value < 0 || value > 1)
                    throw new IllegalArgumentException("Visual values must be finite and normalized");
            }
        }
        public boolean terminal() { return phase == Phase.IMMORTAL || phase == Phase.FAILED; }
    }

    public static Frame from(AscensionRecord record, int shatterTicks) {
        var s = record.session();
        var p = record.progress();
        var q = p.qi();
        double maximum = Math.max(1, Math.max(q.heaven(), Math.max(q.earth(), q.human())));
        float progress = switch (s.phase()) {
            case BREAKING -> p.shatterClock(s.phaseRevision())
                    .map(c -> unit(c.elapsedTicks() / (double) Math.max(1, shatterTicks))).orElse(0f);
            case ABSORBING -> unit(p.window().ticks() / (double) QiParameters.standard().windowTicksRequired());
            case IMMORTAL, FAILED -> 1f;
            default -> 0f;
        };
        return new Frame(s.phase(), s.stateRevision(), progress,
                unit(0.18 + (q.heaven() + q.earth() + q.human()) / 300.0),
                unit(q.heaven() / maximum), unit(q.earth() / maximum), unit(q.human() / maximum),
                unit(p.risk().maximum() / QiParameters.standard().riskLimit()),
                s.phase() == Phase.ABSORBING && p.window().ticks() >= QiParameters.standard().windowTicksRequired(),
                calamityMask(p), s.phase() == Phase.IMMORTAL && p.aperture().formed());
    }

    /** Visual-only sky density; never creates or resolves a calamity. */
    public static float cloudStrength(Frame frame,float age) {
        if(!Float.isFinite(age) || age<0) throw new IllegalArgumentException("Invalid cloud age");
        if(frame.phase()==Phase.ABSORBING) {
            float reveal=unit((age-145)/100.0);
            reveal=reveal*reveal*(3-2*reveal);
            return Math.max(frame.calamityMask()!=0?.55f:0,frame.heaven()*reveal*.75f);
        }
        if(frame.phase()==Phase.OPENING || frame.phase()==Phase.FORMING) return frame.heaven()*.55f;
        if(frame.confirmedFormed()) return unit(1-age/110.0)*.5f;
        return 0;
    }

    public static int calamityMask(AscensionProgress progress) {
        int mask=0;
        for(var t:progress.tribulations()) mask |= switch(t.type()) {
            case JADE_WILLOW_WIND -> 1;
            case SOUND_CHIMES -> 2;
            case SINGLE_HORNED_TIGER, ANCIENT_BEAST -> 4;
            case BLOOD_RAIN -> 8;
            case NONE -> 0;
        };
        return mask;
    }

    public static boolean announceTerminal(Phase previous, Phase current) {
        return previous != null && previous != Phase.IMMORTAL && previous != Phase.FAILED
                && (current == Phase.IMMORTAL || current == Phase.FAILED);
    }

    public static float unit(double value) {
        return Double.isFinite(value) ? (float) Math.max(0, Math.min(1, value)) : 0;
    }

    /** One session, one source. Heartbeats cannot restart phase animation or prolong result cues. */
    public static final class Track {
        private Frame frame;
        private long phaseStarted;
        private long lastSeen;
        private long stoppedAt = -1;
        private long shatteredAt = -1;
        public Track(Frame frame, long tick) {
            this.frame = Objects.requireNonNull(frame);
            phaseStarted = lastSeen = tick;
            if (frame.phase() == Phase.BROKEN) shatteredAt = tick;
        }
        public boolean update(Frame next, long tick) {
            if (next.revision() < frame.revision() || tick < lastSeen
                    || (next.revision() == frame.revision() && !next.equals(frame))
                    || (stoppedAt >= 0 && next.revision() <= frame.revision())) return false;
            if (frame.terminal() && next.phase() != frame.phase()) return false;
            if (frame.phase() == Phase.BREAKING && (next.phase() == Phase.BROKEN || next.phase() == Phase.ABSORBING))
                shatteredAt = tick;
            if (next.phase() != frame.phase()) phaseStarted = tick;
            frame = next;
            lastSeen = tick;
            stoppedAt = -1;
            return true;
        }
        public Frame frame() { return frame; }
        public void alignAge(int serverAge,long tick) {
            if (serverAge < 0 || serverAge > 72000) throw new IllegalArgumentException("Invalid phase age");
            phaseStarted = Math.min(phaseStarted, tick - serverAge);
        }
        public float shatterAlpha(double tick) {
            return shatteredAt < 0 ? 0 : unit(1 - (tick - shatteredAt) / 14.0);
        }
        public float age(double tick) { return (float) Math.max(0, tick - phaseStarted); }
        public void stop(long tick) { if (stoppedAt < 0) stoppedAt = tick; }
        private long deadline() {
            long end = frame.terminal() ? phaseStarted + (frame.phase() == Phase.FAILED ? 90 : RESULT_TICKS) : lastSeen + TIMEOUT_TICKS;
            return stoppedAt < 0 ? end : Math.min(end, stoppedAt + FADE_TICKS);
        }
        public float alpha(double tick) {
            return unit(Math.min((age(tick) + 1) / 8.0, (deadline() - tick) / (double) (stoppedAt < 0 && frame.phase() == Phase.IMMORTAL ? 60 : FADE_TICKS)));
        }
        public boolean expired(long tick) { return tick >= deadline(); }
    }
}
