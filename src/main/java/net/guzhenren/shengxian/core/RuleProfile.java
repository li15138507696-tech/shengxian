package net.guzhenren.shengxian.core;

import java.util.EnumSet;
import java.util.Set;
import java.util.Objects;

public record RuleProfile(String id, boolean evidenceVerified, Set<Capability> capabilities) {
    public RuleProfile {
        Objects.requireNonNull(id, "id");
        if (id.isBlank() || id.length() > 64) throw new IllegalArgumentException("Invalid rule profile");
        capabilities = Set.copyOf(capabilities);
    }

    /**
     * Required capabilities of the ordinary route.
     *
     * <p>{@link Capability#UPSTREAM_APERTURE_WRITE} is excluded by default because it is an
     * audited field boundary, not unfinished work. UPSTREAM-010 recorded that the base mod exposes
     * no immortal-aperture or immortal-essence player field, and a full re-audit of
     * {@code GuzhenrenModVariables$PlayerVariables} on 2026-11-02 confirmed it: the only related
     * storage is the mortal {@code zhenyuan}/{@code zuida_zhenyuan}, the {@code kongqiao}
     * classifier and the {@code shiqiao} flag, none of which may be reused as aperture state
     * (UPSTREAM-006/008). This module therefore keeps its own world-side ledger — the substitution
     * recorded as PROJECT-003. Requiring the upstream write would gate the whole route on a field
     * the integration can never possess.</p>
     *
     * <p>{@code requireUpstreamApertureWrite} restores the previous strict behaviour for operators
     * who will not accept the ledger substitution; that mode can never open, which is its point.</p>
     */
    public static Set<Capability> ordinaryRequired(boolean requireUpstreamApertureWrite) {
        return requireUpstreamApertureWrite
                ? Set.copyOf(EnumSet.allOf(Capability.class))
                : Set.copyOf(EnumSet.complementOf(EnumSet.of(Capability.UPSTREAM_APERTURE_WRITE)));
    }

    /** Ordinary rank-five route only. Special bodies remain disabled pending their own evidence. */
    public static RuleProfile ordinary() {
        return new RuleProfile("ordinary-five-to-six", true, ordinaryRequired(false));
    }

    public enum Capability { BREAK_APERTURE, CONTROL_QI, FORM_APERTURE, IMMORTAL_ESSENCE, UPSTREAM_APERTURE_WRITE, CANONICAL_TRIBULATION, CANONICAL_FAILURE, GU_BACKLASH }
}
