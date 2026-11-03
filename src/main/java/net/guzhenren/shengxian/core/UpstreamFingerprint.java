package net.guzhenren.shengxian.core;

/**
 * Fingerprints of the upstream jar this module was audited against.
 *
 * <p>Every entry-point decision in {@code docs/evidence/gu-activation-api.md} was made against
 * these exact bytes. A different upstream build may have moved or re-ordered the branches, so
 * integrations must refuse to guess when the fingerprint does not match.</p>
 */
public final class UpstreamFingerprint {
    /** Outer shell jar shipped by the user. */
    public static final String OUTER_SHA256 =
            "3733c20ffbd16d9068bb8188d45b24a0d61b279d7cb6fdc8ea341938a7f4cf04";
    /** Nested {@code META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar}. */
    public static final String INNER_SHA256 =
            "b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc";

    private UpstreamFingerprint() {}

    public static boolean matchesOuter(String sha256) {
        return OUTER_SHA256.equalsIgnoreCase(normalize(sha256));
    }

    public static boolean matchesInner(String sha256) {
        return INNER_SHA256.equalsIgnoreCase(normalize(sha256));
    }

    private static String normalize(String sha256) {
        return sha256 == null ? "" : sha256.trim();
    }
}
