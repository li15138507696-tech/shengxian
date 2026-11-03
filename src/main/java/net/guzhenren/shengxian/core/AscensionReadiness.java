package net.guzhenren.shengxian.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Blocks live ascension until every required ordinary-route capability is implemented. */
public final class AscensionReadiness {
    private static final Set<AscensionProgress.FailureCause> CANONICALLY_FATAL_QI_CAUSES = Set.of(
            AscensionProgress.FailureCause.HUMAN_OVERLOAD,
            AscensionProgress.FailureCause.HEAVEN_OVERLOAD,
            AscensionProgress.FailureCause.EARTH_OVERLOAD);
    public record Decision(boolean available, Set<RuleProfile.Capability> missingCapabilities,
                           List<String> messages) {
        public Decision {
            missingCapabilities = Set.copyOf(missingCapabilities);
            messages = List.copyOf(messages);
        }

        public String message() {
            return available ? "普通升仙路线已就绪。"
                    : "升仙暂未开放，尚缺必要机制：" + String.join("；", messages);
        }
    }

    private AscensionReadiness() {}

    /**
     * Route capabilities this module actually implements.
     *
     * <p>Everything except {@link RuleProfile.Capability#UPSTREAM_APERTURE_WRITE} is implemented
     * and covered by regression plus static verification: shatter, qi control, aperture formation,
     * essence ledger, the ambient-qi calamity engine, failure settlement and Gu backlash. Reporting
     * an empty set was a blanket "nothing works" that stopped being true long ago.</p>
     *
     * <p>{@code UPSTREAM_APERTURE_WRITE} is not missing work but a field boundary: UPSTREAM-010
     * found no upstream player field for an immortal aperture or essence — reconfirmed by a full
     * re-audit of {@code PlayerVariables} on 2026-11-02 — so the module keeps its own world-side
     * ledger instead (PROJECT-003). It is therefore also absent from
     * {@link RuleProfile#ordinaryRequired(boolean)} in the default mode, which is why the ordinary
     * route is open by default. Operators who refuse the substitution can demand the write through
     * {@code ascension.requireUpstreamApertureWrite}.</p>
     */
    public static Set<RuleProfile.Capability> implementedCapabilities() {
        EnumSet<RuleProfile.Capability> implemented =
                EnumSet.complementOf(EnumSet.of(RuleProfile.Capability.UPSTREAM_APERTURE_WRITE));
        return Set.copyOf(implemented);
    }
    /** Only the three canonically fatal qi imbalances may reach the upstream death side effect. */
    public static boolean hasCanonicalFatalQiCause(Set<AscensionProgress.FailureCause> causes) {
        Objects.requireNonNull(causes, "failure causes");
        return causes.size() == 1 && CANONICALLY_FATAL_QI_CAUSES.containsAll(causes);
    }

    /** Validates the distinct, source-bound impact cause without adding it to the qi whitelist. */
    public static boolean hasVerifiedTribulationImpactCause(
            Set<AscensionProgress.FailureCause> causes,
            AscensionProgress.TribulationImpactFailure context) {
        if (causes == null || context == null
                || !causes.equals(Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT))) return false;
        // No per-case permission list: every profile runs the same engine now, so an impact
        // failure is source-bound by the cause and context themselves, not by which example it was.
        return context.profile() != TribulationParameters.Profile.NOT_FROZEN;
    }

    /** Upstream failure consequences stay quarantined until the route and exact cause are verified. */
    public static boolean mayApplyFailure(Decision readiness, Set<AscensionProgress.FailureCause> causes) {
        Objects.requireNonNull(readiness, "readiness");
        return readiness.available() && hasCanonicalFatalQiCause(causes);
    }

    /** A witnessed impact has its own source check and still requires the complete ordinary-route gate. */
    public static boolean mayApplyTribulationImpact(
            Decision readiness, Set<AscensionProgress.FailureCause> causes,
            AscensionProgress.TribulationImpactFailure context) {
        Objects.requireNonNull(readiness, "readiness");
        return readiness.available() && hasVerifiedTribulationImpactCause(causes, context);
    }
    /** Evaluates with the default (open) ordinary-route requirement set. */
    public static Decision evaluate(Set<RuleProfile.Capability> implemented) {
        return evaluate(implemented, RuleProfile.ordinaryRequired(false));
    }

    /**
     * Evaluates against an explicit requirement set, so a strict deployment can demand the
     * upstream aperture write without core code reading a config it cannot load in pure tests.
     */
    public static Decision evaluate(Set<RuleProfile.Capability> implemented,
                                    Set<RuleProfile.Capability> required) {
        Objects.requireNonNull(implemented, "implemented");
        Objects.requireNonNull(required, "required");
        EnumSet<RuleProfile.Capability> missing = EnumSet.noneOf(RuleProfile.Capability.class);
        for (RuleProfile.Capability capability : required) {
            if (!implemented.contains(capability)) missing.add(capability);
        }
        List<String> messages = new ArrayList<>();
        for (RuleProfile.Capability capability : missing) messages.add(description(capability));
        return new Decision(missing.isEmpty(), missing, messages);
    }

    /** A closed route may replay a committed formation, but may not start or finish a new one. */
    public static boolean mayAttemptFormation(Decision readiness, boolean committedFormation) {
        Objects.requireNonNull(readiness, "readiness");
        return readiness.available() || committedFormation;
    }

    /**
     * Background phase transitions are irreversible once the aperture is broken, so they
     * may run only while the complete route is open. Read-only reconciliation is unaffected.
     */
    public static boolean mayAdvanceSession(Decision readiness, Phase phase) {
        Objects.requireNonNull(readiness, "readiness");
        Objects.requireNonNull(phase, "phase");
        return readiness.available() && (phase == Phase.BREAKING || phase == Phase.BROKEN);
    }
    private static String description(RuleProfile.Capability capability) {
        return switch (capability) {
            case BREAK_APERTURE -> "碎窍还没有接入本体空窍";
            case CONTROL_QI -> "本人持续调和三气的完整操作还未接入";
            case FORM_APERTURE -> "本体仙窍形成尚未接入";
            case IMMORTAL_ESSENCE -> "仙元结算尚未接入本体";
            case UPSTREAM_APERTURE_WRITE -> "本体空窍状态写入尚未接入";
            case CANONICAL_TRIBULATION -> "有原著依据的灾劫载体与应对尚未接入";
            case CANONICAL_FAILURE -> "原著失败后果尚未接入";
            case GU_BACKLASH -> "纳气期间凡蛊与仙蛊反噬规则尚未接入";
        };
    }
}
