package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.core.AscensionRecord;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.network.AscensionOutcomePayload;
import net.guzhenren.shengxian.network.AscensionOutcomePayload.Grade;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Projects a committed record into a private result; never applies rewards or player mutations. */
public final class AscensionOutcomeService {
    private AscensionOutcomeService() {}

    /** Called only when the visual observer sees an active session enter its terminal phase. */
    public static void send(ServerPlayer player, AscensionRecord record) {
        var session = record.session();
        if (!session.key().owner().equals(player.getUUID())) return;
        boolean formed = session.phase() == Phase.IMMORTAL && record.progress().aperture().formed();
        if (!formed && session.phase() != Phase.FAILED) return;
        // Terminal phase: the cue is deduped per player so the visual heartbeat cannot repeat it.
        AscensionSounds.phaseOnce(player, session.phase());
        Grade grade = formed ? gradeForArea(record.progress().aperture().areaMu()) : Grade.NONE;
        PacketDistributor.sendToPlayer(player, new AscensionOutcomePayload(player.getUUID(), session.id(),
                player.level().dimension().location(), session.stateRevision(), grade,
                AscensionLifecycleService.snapshotOf(record, AscensionLifecycleService.readiness())));
    }

    /** Uses the saved aperture, so later qi thresholds cannot change an already formed grade. */
    public static Grade gradeForArea(double areaMu) {
        if (!Double.isFinite(areaMu) || areaMu <= 0) return Grade.UNKNOWN;
        if (areaMu <= 3_000_000) return Grade.SMALL;
        if (areaMu >= 4_000_000 && areaMu <= 6_000_000) return Grade.MEDIUM;
        if (areaMu >= 7_000_000 && areaMu <= 9_000_000) return Grade.UPPER;
        return Grade.UNKNOWN;
    }
}
