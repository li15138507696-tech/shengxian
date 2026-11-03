package net.guzhenren.shengxian.core;

import java.util.Objects;

/**
 * Domain ledger for a formed immortal aperture. It does not create a Minecraft dimension or
 * space; it only preserves area, internal time, essence and the plot reservation so the
 * later space layer has a single source of truth.
 *
 * <p>CANON-006 fixes ranges and time-ratio variation, not universal constants. No automatic
 * lifespan refill is applied here (project constraint; GAP-008).</p>
 */
public final class ApertureEngine {
    private ApertureEngine() {}
    public record AdvanceProjection(long internalTicks, double productionPerInternalTick,
                                    double upkeepPerInternalTick, double essenceProduction,
                                    double upkeepCost, boolean upkeepCovered) {
        public double netEssence() {
            return essenceProduction - upkeepCost;
        }

        public double netEssencePerInternalTick() {
            return productionPerInternalTick - upkeepPerInternalTick;
        }
    }

    /** Projects one bounded ledger step without mutating the aperture. */
    public static AdvanceProjection projectAdvance(AscensionProgress.Aperture aperture, long serverTicks,
                                                    double essencePerInternalTick,
                                                    double upkeepPerInternalTick) {
        Objects.requireNonNull(aperture, "aperture");
        if (serverTicks < 0) throw new IllegalArgumentException("Negative tick delta");
        if (!Double.isFinite(essencePerInternalTick) || essencePerInternalTick < 0) {
            throw new IllegalArgumentException("Invalid essence rate");
        }
        if (!Double.isFinite(upkeepPerInternalTick) || upkeepPerInternalTick < 0) {
            throw new IllegalArgumentException("Invalid upkeep rate");
        }
        if (!aperture.formed() || serverTicks == 0) {
            return new AdvanceProjection(0, essencePerInternalTick, upkeepPerInternalTick, 0, 0, true);
        }
        long delta = (long) Math.floor(aperture.timeRatio() * serverTicks);
        if (delta <= 0) {
            return new AdvanceProjection(0, essencePerInternalTick, upkeepPerInternalTick, 0, 0, true);
        }
        double upkeep = delta * upkeepPerInternalTick;
        double produced = delta * essencePerInternalTick;
        return new AdvanceProjection(delta, essencePerInternalTick, upkeepPerInternalTick,
                produced, upkeep, aperture.essence() >= upkeep);
    }

    /** Forms the aperture once. Repeating formation is rejected; the marker is idempotent. */
    public static AscensionProgress form(AscensionProgress current, double areaMu, double timeRatio,
                                         double initialEssence, int plotIndex) {
        Objects.requireNonNull(current, "current");
        if (current.aperture().formed()) throw new IllegalStateException("Aperture already formed");
        if (areaMu <= 0) throw new IllegalArgumentException("Area must be positive");
        AscensionProgress next = current.withAperture(
                new AscensionProgress.Aperture(areaMu, timeRatio, initialEssence, 0, plotIndex));
        if (!next.appliedEffects().contains("aperture:formed")) {
            next = next.withAppliedEffect("aperture:formed");
        }
        return next;
    }

    /** Advances the virtual internal clock and essence production by server ticks. */
    public static AscensionProgress advanceInternal(AscensionProgress current, long serverTicks,
                                                    double essencePerInternalTick) {
        return advanceInternal(current, serverTicks, essencePerInternalTick, 0.0);
    }

    /**
     * Advances the internal clock, charges upkeep and then produces essence.
     *
     * <p><b>Evidence status: game mapping.</b> CANON-005/006 describe a formed blessed land
     * producing green-tipped immortal essence; no text read so far states a running cost.
     * The upkeep rate is a balance parameter that gives the ledger a real sink, and it must
     * not be presented as a canon rule.</p>
     *
     * <p>Upkeep is charged before production for the same internal span. When the balance
     * cannot cover it, the aperture stalls: neither the internal clock nor production advances,
     * so a stalled aperture never goes negative and never silently prints essence.</p>
     */
    public static AscensionProgress advanceInternal(AscensionProgress current, long serverTicks,
                                                    double essencePerInternalTick,
                                                    double upkeepPerInternalTick) {
        Objects.requireNonNull(current, "current");
        AscensionProgress.Aperture aperture = current.aperture();
        AdvanceProjection projection = projectAdvance(aperture, serverTicks,
                essencePerInternalTick, upkeepPerInternalTick);
        if (!aperture.formed() || projection.internalTicks() <= 0 || !projection.upkeepCovered()) return current;
        long internal = aperture.internalTicks() > Long.MAX_VALUE - projection.internalTicks()
                ? Long.MAX_VALUE : aperture.internalTicks() + projection.internalTicks();
        double essence = Math.min(AscensionProgress.MAX_QI,
                aperture.essence() - projection.upkeepCost() + projection.essenceProduction());
        return current.withAperture(new AscensionProgress.Aperture(
                aperture.areaMu(), aperture.timeRatio(), essence, internal, aperture.plotIndex()));
    }

    /** Spends real essence; an unformed aperture or insufficient balance is rejected. */
    public static AscensionProgress spendEssence(AscensionProgress current, double amount) {
        Objects.requireNonNull(current, "current");
        if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Invalid essence amount");
        if (!current.aperture().formed()) throw new IllegalStateException("Aperture not formed");
        if (current.aperture().essence() < amount) throw new IllegalStateException("Insufficient essence");
        AscensionProgress.Aperture a = current.aperture();
        return current.withAperture(new AscensionProgress.Aperture(
                a.areaMu(), a.timeRatio(), a.essence() - amount, a.internalTicks(), a.plotIndex()));
    }
}
