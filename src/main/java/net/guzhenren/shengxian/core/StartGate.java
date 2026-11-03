package net.guzhenren.shengxian.core;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

public final class StartGate {
    private StartGate() {}

    public record Decision(boolean allowed, List<String> reasons) {
        public Decision { reasons = List.copyOf(reasons); }
    }

    public static Decision check(boolean verifiedFivePeak, boolean alreadyBroken,
                                 Optional<RuleProfile> profile,
                                 Set<RuleProfile.Capability> adapterCapabilities) {
        return check(verifiedFivePeak, alreadyBroken, profile, adapterCapabilities,
                RuleProfile.ordinaryRequired(false));
    }

    /**
     * Checks against an explicit requirement set. The set is a parameter rather than a read of
     * {@link RuleProfile.Capability#values()} so the audited upstream field boundary can stay out of
     * the default requirements (PROJECT-003) while a strict deployment can still demand it.
     */
    public static Decision check(boolean verifiedFivePeak, boolean alreadyBroken,
                                 Optional<RuleProfile> profile,
                                 Set<RuleProfile.Capability> adapterCapabilities,
                                 Set<RuleProfile.Capability> requiredCapabilities) {
        var reasons = new ArrayList<String>();
        if (!verifiedFivePeak) reasons.add("FIVE_PEAK_NOT_VERIFIED");
        if (alreadyBroken) reasons.add("APERTURE_ALREADY_BROKEN");
        if (profile.isEmpty()) {
            reasons.add("UNKNOWN_RULE_PROFILE");
        } else {
            RuleProfile rule = profile.get();
            if (!rule.evidenceVerified()) reasons.add("RULE_EVIDENCE_UNVERIFIED");
            for (RuleProfile.Capability capability : requiredCapabilities) {
                if (!rule.capabilities().contains(capability)) reasons.add("RULE_MISSING_" + capability);
                if (!adapterCapabilities.contains(capability)) reasons.add("ADAPTER_MISSING_" + capability);
            }
        }
        return new Decision(reasons.isEmpty(), reasons);
    }
}
