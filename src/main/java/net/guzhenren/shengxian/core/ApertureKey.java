package net.guzhenren.shengxian.core;

import java.util.Objects;
import java.util.UUID;

public record ApertureKey(UUID owner, String apertureId) {
    public ApertureKey {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(apertureId, "apertureId");
        if (apertureId.isBlank() || apertureId.length() > 64) {
            throw new IllegalArgumentException("Invalid aperture ID");
        }
    }
}
