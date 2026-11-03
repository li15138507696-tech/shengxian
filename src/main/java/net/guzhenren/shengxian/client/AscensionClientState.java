package net.guzhenren.shengxian.client;

import net.guzhenren.shengxian.network.AscensionSnapshotPayload;

/** Last server snapshot for the local player's ascension screen. */
public final class AscensionClientState {
    private static volatile AscensionSnapshotPayload latest = AscensionSnapshotPayload.empty();

    private AscensionClientState() {}

    public static void accept(AscensionSnapshotPayload payload) {
        latest = payload == null ? AscensionSnapshotPayload.empty() : payload;
    }

    public static AscensionSnapshotPayload latest() {
        return latest;
    }

    public static void clear() {
        latest = AscensionSnapshotPayload.empty();
    }
}
