package net.guzhenren.shengxian.core;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable identity for the one permitted settlement attempt of a terminal failure. */
public final class FailureSettlementIdentity {
    private FailureSettlementIdentity() {}

    public static UUID operationId(UUID sessionId, long phaseRevision) {
        if (sessionId == null || phaseRevision < 0) {
            throw new IllegalArgumentException("Invalid failure settlement identity");
        }
        return UUID.nameUUIDFromBytes((sessionId + ":failure:" + phaseRevision)
                .getBytes(StandardCharsets.UTF_8));
    }
}
