package net.guzhenren.shengxian.core;

import java.util.Objects;

/**
 * CANON-009: 黑楼兰强催排难仙蛊的个例。
 *
 * <p>The witnessed event: with the six-turn Pai Nan Gu he destroyed the eight chime carriers one
 * by one with 排难之光, but forcing it burst both eyes, left 道痕 in the sockets that could never
 * be restored, and the gu itself finally exploded. The text presents one carrier destroyed per
 * activation; it does not state a general cost formula for other Gu.</p>
 *
 * <p>This helper only covers the witnessed case: an immortal Gu whose effect is
 * {@link XianGu.Effect#CARRIER_STRIKE}, activated while the aperture cannot pay its full cost.
 * The player still gets the strike and the gu is consumed, but the remainder is paid in
 * 道痕 - a permanent, append-only ledger mark. Repeated forcing never stacks extra marks.</p>
 */
public final class XianGuForcedUse {
    /** Permanent 道痕 mark left by a forced strike; the ledger is append-only so this never clears. */
    public static final String EYES_DAO_TAO = "xiangu:daohen:eyes";

    private XianGuForcedUse() {}

    public record Result(AscensionProgress progress, boolean firstTimeDamaged) { }

    /**
     * Applies one forced strike.
     *
     * <p>Requires a held, consumable {@code CARRIER_STRIKE} gu, an active carrier and at least
     * some essence: canon shows the strike still working, but a completely empty aperture has
     * nothing to ignite, so that case stays a rejection rather than a free action.</p>
     */
    public static Result force(AscensionProgress current, XianGu gu, String carrierId, int strikeUnits) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(gu, "gu");
        Objects.requireNonNull(carrierId, "carrier id");
        if (gu.effect() != XianGu.Effect.CARRIER_STRIKE) {
            throw new IllegalArgumentException("Only a carrier-strike immortal Gu can be forced");
        }
        if (gu.reusable()) throw new IllegalArgumentException("Reusable immortal Gu is not consumed");
        if (strikeUnits <= 0) throw new IllegalArgumentException("Invalid strike units");
        if (!XianGuLedger.holds(current, gu.id())) throw new IllegalStateException("Not held: " + gu.id());
        if (current.tribulations().isEmpty()) throw new IllegalStateException("No active carrier");
        double essence = current.aperture().essence();
        if (!(essence > 0)) throw new IllegalStateException("Aperture holds no essence to ignite");
        boolean firstTime = !current.appliedEffects().contains(EYES_DAO_TAO);

        AscensionProgress struck = TribulationEngine.strike(
                ApertureEngine.spendEssence(current, essence), carrierId, strikeUnits).progress();
        AscensionProgress consumed = XianGuLedger.consume(struck, gu);
        AscensionProgress damaged = firstTime ? consumed.withAppliedEffect(EYES_DAO_TAO) : consumed;
        return new Result(damaged, firstTime);
    }

    /** Whether this ledger already carries the irreversible eye 道痕. */
    public static boolean eyesDamaged(AscensionProgress current) {
        Objects.requireNonNull(current, "current");
        return current.appliedEffects().contains(EYES_DAO_TAO);
    }
}
