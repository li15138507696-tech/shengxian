package net.guzhenren.shengxian.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Continuous three-qi step. It is deliberately deterministic and free of Minecraft types.
 *
 * <p>Rules implemented (with evidence IDs in the design docs): ambient heaven/earth qi is
 * finite and regenerates; the player draws fractions of it; human qi comes only from the
 * finite personal reserve; excess of any one qi accumulates directional risk; risk decays
 * only while balanced; insight is available only inside a sustained balanced window and is
 * keyed for idempotency; seeking insight adds distraction risk and never pauses the qi.
 * There is no total success roll.</p>
 */
public final class QiEngine {
    private QiEngine() {}

    public enum EventType {
        IMBALANCE_HEAVEN, IMBALANCE_EARTH, IMBALANCE_HUMAN,
        WINDOW_OPENED, WINDOW_CLOSED, INSIGHT_GRANTED
    }

    public record Event(EventType type, String detail) {}

    public record Result(AscensionProgress progress, List<Event> events, boolean fatal) {
        public Result { events = List.copyOf(events); }
    }

    public static Result step(AscensionProgress current, QiIntent intent, long serverTick, QiParameters p) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(p, "parameters");
        if (serverTick < 0) throw new IllegalArgumentException("Negative server tick");
        List<Event> events = new ArrayList<>();

        if (current.risk().maximum() >= p.riskLimit()) {
            List<Event> existing = new ArrayList<>();
            if (current.risk().heaven() >= p.riskLimit()) existing.add(new Event(EventType.IMBALANCE_HEAVEN, "already-fatal"));
            if (current.risk().earth() >= p.riskLimit()) existing.add(new Event(EventType.IMBALANCE_EARTH, "already-fatal"));
            if (current.risk().human() >= p.riskLimit()) existing.add(new Event(EventType.IMBALANCE_HUMAN, "already-fatal"));
            return new Result(current.withWindow(AscensionProgress.Window.empty()), existing, true);
        }

        AscensionProgress.Qi qi = current.qi();
        double ambientHeaven = Math.min(p.ambientMaxHeaven(), qi.ambientHeaven() + p.ambientRegenPerTick());
        double ambientEarth = Math.min(p.ambientMaxEarth(), qi.ambientEarth() + p.ambientRegenPerTick());
        double heavenDraw = Math.min(ambientHeaven, p.maxAbsorbHeavenPerTick() * intent.heavenDraw());
        double earthDraw = Math.min(ambientEarth, p.maxAbsorbEarthPerTick() * intent.earthDraw());
        double humanDraw = Math.min(qi.humanReserve(), p.maxHumanReleasePerTick() * intent.humanRelease());

        AscensionProgress.Qi nextQi = new AscensionProgress.Qi(
                Math.min(AscensionProgress.MAX_QI, qi.heaven() + heavenDraw),
                Math.min(AscensionProgress.MAX_QI, qi.earth() + earthDraw),
                Math.min(AscensionProgress.MAX_QI, qi.human() + humanDraw),
                ambientHeaven - heavenDraw, ambientEarth - earthDraw, qi.humanReserve() - humanDraw);

        double mean = (nextQi.heaven() + nextQi.earth() + nextQi.human()) / 3.0;
        double excessHeaven = mean <= 0 ? 0 : (nextQi.heaven() - mean) / mean;
        double excessEarth = mean <= 0 ? 0 : (nextQi.earth() - mean) / mean;
        double excessHuman = mean <= 0 ? 0 : (nextQi.human() - mean) / mean;

        AscensionProgress.Risk risk = new AscensionProgress.Risk(
                nextRisk(current.risk().heaven(), excessHeaven, mean, p),
                nextRisk(current.risk().earth(), excessEarth, mean, p),
                nextRisk(current.risk().human(), excessHuman, mean, p));
        if (intent.seekInsight()) {
            double distraction = p.riskGainPerTick() * p.distractionRiskFactor();
            risk = new AscensionProgress.Risk(
                    clampRisk(risk.heaven() + distraction),
                    clampRisk(risk.earth() + distraction),
                    clampRisk(risk.human() + distraction));
        }

        boolean fatal = risk.heaven() >= p.riskLimit() || risk.earth() >= p.riskLimit()
                || risk.human() >= p.riskLimit();
        if (fatal) {
            if (risk.heaven() >= p.riskLimit()) events.add(new Event(EventType.IMBALANCE_HEAVEN, "limit"));
            if (risk.earth() >= p.riskLimit()) events.add(new Event(EventType.IMBALANCE_EARTH, "limit"));
            if (risk.human() >= p.riskLimit()) events.add(new Event(EventType.IMBALANCE_HUMAN, "limit"));
            AscensionProgress next = current.withQi(nextQi).withRisk(risk)
                    .withWindow(AscensionProgress.Window.empty());
            return new Result(next, events, true);
        }

        AscensionProgress next = current.withQi(nextQi).withRisk(risk);
        boolean balanced = mean > 0
                && nextQi.heaven() >= p.windowMinQi()
                && nextQi.earth() >= p.windowMinQi()
                && nextQi.human() >= p.windowMinQi()
                && excessHeaven <= p.balanceTolerance()
                && excessEarth <= p.balanceTolerance()
                && excessHuman <= p.balanceTolerance();

        if (!balanced) {
            if (!current.window().id().isEmpty()) events.add(new Event(EventType.WINDOW_CLOSED, current.window().id()));
            return new Result(next.withWindow(AscensionProgress.Window.empty()), events, false);
        }

        AscensionProgress.Window window;
        if (current.window().id().isEmpty()) {
            window = new AscensionProgress.Window(1, "w" + serverTick);
            events.add(new Event(EventType.WINDOW_OPENED, window.id()));
        } else {
            window = current.window().advance(AscensionProgress.MAX_WINDOW_TICKS);
        }
        next = next.withWindow(window);
        if (intent.seekInsight() && window.ticks() >= p.windowTicksRequired()) {
            String key = "insight:" + window.id();
            if (!next.appliedEffects().contains(key)) {
                next = next.withAppliedEffect(key)
                        .withInsight(next.insightEarned() + p.insightPerWindow(), next.insightSpent());
                events.add(new Event(EventType.INSIGHT_GRANTED, key));
            }
        }
        return new Result(next, events, false);
    }

    private static double nextRisk(double risk, double excess, double mean, QiParameters p) {
        if (mean <= 0) return risk; // no qi is not "balanced"; risk neither grows nor decays for free
        if (excess > p.balanceTolerance()) {
            return clampRisk(risk + p.riskGainPerTick());
        }
        return clampRisk(Math.max(0, risk - p.riskDecayPerTick()));
    }

    private static double clampRisk(double value) {
        return Math.min(AscensionProgress.MAX_RISK, value);
    }
}