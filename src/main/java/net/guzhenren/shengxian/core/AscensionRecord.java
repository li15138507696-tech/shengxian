package net.guzhenren.shengxian.core;

import java.util.Objects;

/** A session plus its same-phase progress; both are persisted in one atomic record. */
public record AscensionRecord(AscensionSession session, AscensionProgress progress) {
    public AscensionRecord {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(progress, "progress");
    }
}
