package net.guzhenren.shengxian.core;

import java.util.Objects;

/**
 * CANON-013 方正个例：成窍后残余天地二气，支持本命血道凡蛊升炼为第二只血道仙蛊。
 *
 * <p>The witnessed text: after the aperture formed, Fang Zheng put his core Gu and blood-dao Gu
 * into it, and the leftover heaven and earth qi refined his vital blood-dao mortal Gu into a
 * second blood-dao immortal Gu. The text names neither the Gu nor its effect.</p>
 *
 * <p>This is an individual case with an arranged blood-dao setting, so refinement is restricted to
 * {@link TribulationParameters.Profile#FANG_ZHENG_WITNESSED}. It is the only non-operator
 * acquisition path in this module, and it produces a Gu whose effect is explicitly
 * {@link XianGu.Effect#UNSPECIFIED}: ownership is canon-grounded, activation is not.</p>
 */
public final class LeftoverRefinement {
    /** Append-only mark: leftover-qi refinement happens at most once per aperture. */
    public static final String REFINED_MARK = "xiangu:refined:leftover";
    /**
     * Id of the immortal Gu produced from leftover qi. The text names no Gu for this, so the
     * entry is generic rather than one individual's private Gu.
     */
    public static final String LEFTOVER_GU = "leftover_xian_gu";

    private LeftoverRefinement() {}

    public record Result(AscensionProgress progress, boolean refined) { }

    /**
     * No individual-case gate: leftover qi refines whenever it is actually there. The witnessed
     * example supplies the mechanism, not a permission (project decision, see canon-rules 项目决策).
     */
    public static boolean profileAllows(TribulationParameters parameters) {
        return parameters != null;
    }

    /** True once leftover qi has already produced the second Gu. */
    public static boolean alreadyRefined(AscensionProgress current) {
        Objects.requireNonNull(current, "current");
        return current.appliedEffects().contains(REFINED_MARK);
    }

    /**
     * Refines the unnamed blood-dao immortal Gu from leftover ambient qi.
     *
     * <p>Returns {@code refined=false} without change when the case does not apply: aperture not
     * formed, profile not witnessed, already refined, or the two leftover sources are below half
     * the trigger threshold. Both sources must remain, because the text converts both.</p>
     */
    public static Result refine(AscensionProgress current, TribulationParameters parameters) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(parameters, "parameters");
        if (!current.aperture().formed() || !profileAllows(parameters) || alreadyRefined(current)) {
            return new Result(current, false);
        }
        double threshold = parameters.triggerAmbientTotal() / 2.0;
        AscensionProgress.Qi qi = current.qi();
        if (qi.ambientHeaven() < threshold || qi.ambientEarth() < threshold) {
            return new Result(current, false);
        }
        AscensionProgress.Qi consumed = new AscensionProgress.Qi(qi.heaven(), qi.earth(), qi.human(),
                Math.max(0.0, qi.ambientHeaven() - threshold),
                Math.max(0.0, qi.ambientEarth() - threshold), qi.humanReserve());
        XianGu unnamed = XianGuCatalog.find(LEFTOVER_GU).orElseThrow();
        AscensionProgress held = XianGuLedger.hold(current.withQi(consumed), unnamed);
        return new Result(held.withAppliedEffect(REFINED_MARK), true);
    }
}
