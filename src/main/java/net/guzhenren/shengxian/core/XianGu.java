package net.guzhenren.shengxian.core;

import java.util.Objects;

/**
 * One immortal gu the aperture ledger can hold.
 *
 * <p><b>Evidence status:</b> CANON-009 shows a six-turn immortal gu (排难仙蛊) destroying the
 * eight chimes with "排难之光", and shows that forcing it destroyed the gu itself. Nothing read
 * so far defines how a player refines one, how many exist in the world, or what other immortal
 * gu do. This type therefore models only what the text shows: an owned gu, a cost paid in
 * green-tipped essence, and one carrier-destroying effect.</p>
 */
public record XianGu(String id, String displayName, double essenceCost, Effect effect, String canonRef,
                     boolean reusable) {

    /**
     * Effects this module implements, plus {@link #UNSPECIFIED}.
     *
     * <p>{@code UNSPECIFIED} exists for one canon-cited case: CANON-013 says Fang Zheng's leftover
     * qi refined his vital blood-dao mortal Gu into a second blood-dao immortal Gu, but the text
     * gives neither its name nor its effect. Such a Gu can be owned and recorded, and cannot be
     * activated - inventing an effect would not be faithful.</p>
     */
    public enum Effect { CARRIER_STRIKE, FIXED_TRAVEL, RECENT_RESTORE, LANDSCAPE_RESTORE, UNSPECIFIED }

    public XianGu(String id, String displayName, double essenceCost, Effect effect, String canonRef) {
        this(id, displayName, essenceCost, effect, canonRef, false);
    }

    private static final int MAX_ID_LENGTH = 48;
    private static final int MAX_NAME_LENGTH = 64;

    public XianGu {
        Objects.requireNonNull(id, "xian gu id");
        Objects.requireNonNull(displayName, "xian gu display name");
        Objects.requireNonNull(effect, "xian gu effect");
        Objects.requireNonNull(canonRef, "xian gu canon reference");
        if (id.isBlank() || id.length() > MAX_ID_LENGTH) throw new IllegalArgumentException("Invalid xian gu id");
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_') {
                throw new IllegalArgumentException("Xian gu id must be lowercase snake case");
            }
        }
        if (displayName.isBlank() || displayName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid xian gu display name");
        }
        if (!Double.isFinite(essenceCost) || essenceCost <= 0) {
            throw new IllegalArgumentException("Invalid xian gu essence cost");
        }
        if (canonRef.isBlank()) throw new IllegalArgumentException("Invalid canon reference");
    }
}
