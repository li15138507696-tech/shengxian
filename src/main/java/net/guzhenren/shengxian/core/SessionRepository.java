package net.guzhenren.shengxian.core;

import java.io.IOException;
import java.util.Optional;

public interface SessionRepository {
    Optional<AscensionRecord> find(ApertureKey key) throws IOException;

    /**
     * expectedStateRevision=-1 creates an absent record; updates compare the saved
     * stateRevision, not the phase count.
     */
    void save(AscensionRecord record, long expectedStateRevision) throws IOException;
}
