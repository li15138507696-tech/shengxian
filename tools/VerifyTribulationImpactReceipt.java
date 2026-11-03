import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.guzhenren.shengxian.compat.SavedTribulationImpactReceipt;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.guzhenren.shengxian.core.TribulationParameters;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Offline compressed-NBT checks for the independent named-impact receipt. */
public final class VerifyTribulationImpactReceipt {
    private static int checks;
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OPERATION = UUID.fromString("11111111-2222-3333-4444-555555555556");
    private static final TribulationParameters.Profile PROFILE = TribulationParameters.Profile.TAI_BAI_WITNESSED;
    private static final AscensionProgress.Tribulation.Type TYPE =
            AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND;
    private static final String CARRIER = "heaven-proof";
    private static final long IMPACT_SEQUENCE = 2L;
    private interface Checked { void run() throws Exception; }

    private static SavedTribulationImpactReceipt.Receipt receipt(SavedTribulationImpactReceipt.Stage stage) {
        return new SavedTribulationImpactReceipt.Receipt(OPERATION, PROFILE, TYPE, CARRIER, IMPACT_SEQUENCE, stage);
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Impact receipt check failed");
        checks++;
    }

    private static void rejects(Checked operation) throws Exception {
        try {
            operation.run();
            throw new AssertionError("Invalid impact receipt was accepted");
        } catch (java.io.IOException expected) {
            checks++;
        }
    }

    private static CompoundTag player(SavedTribulationImpactReceipt.Stage stage) throws Exception {
        CompoundTag persistent = new CompoundTag();
        CompoundTag persisted = new CompoundTag();
        persisted.putString("unrelated", "preserved");
        CompoundTag oldFailure = new CompoundTag();
        oldFailure.putString("marker", "untouched");
        persisted.put("shengxian:failure", oldFailure);
        persistent.put("PlayerPersisted", persisted);
        SavedTribulationImpactReceipt.record(persistent, receipt(SavedTribulationImpactReceipt.Stage.STARTED));
        if (stage == SavedTribulationImpactReceipt.Stage.RESPAWN_RECEIPT) {
            SavedTribulationImpactReceipt.record(persistent, receipt(stage));
        }
        CompoundTag root = new CompoundTag();
        root.putUUID("UUID", OWNER);
        root.put("NeoForgeData", persistent);
        CompoundTag variables = new CompoundTag();
        variables.putBoolean("shiqiao", true);
        CompoundTag attachments = new CompoundTag();
        attachments.put("guzhenren:player_variables", variables);
        root.put("neoforge:attachments", attachments);
        return root;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("shengxian-impact-receipt-");
        Path file = dir.resolve(OWNER + ".dat");
        Path host = dir.resolve("level.dat");
        try {
            rejects(() -> SavedTribulationImpactReceipt.verify(file, OWNER, receipt(
                    SavedTribulationImpactReceipt.Stage.STARTED)));
            CompoundTag root = player(SavedTribulationImpactReceipt.Stage.STARTED);
            NbtIo.writeCompressed(root, file);
            SavedTribulationImpactReceipt.verify(file, OWNER, receipt(SavedTribulationImpactReceipt.Stage.STARTED));
            checks++;
            rejects(() -> SavedTribulationImpactReceipt.verify(file, UUID.randomUUID(), receipt(
                    SavedTribulationImpactReceipt.Stage.STARTED)));
            rejects(() -> SavedTribulationImpactReceipt.verify(file, OWNER,
                    new SavedTribulationImpactReceipt.Receipt(UUID.randomUUID(), PROFILE, TYPE, CARRIER,
                            IMPACT_SEQUENCE, SavedTribulationImpactReceipt.Stage.STARTED)));
            rejects(() -> SavedTribulationImpactReceipt.verify(file, OWNER,
                    new SavedTribulationImpactReceipt.Receipt(OPERATION, PROFILE,
                            AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER, CARRIER,
                            IMPACT_SEQUENCE, SavedTribulationImpactReceipt.Stage.STARTED)));
            rejects(() -> SavedTribulationImpactReceipt.verify(file, OWNER,
                    new SavedTribulationImpactReceipt.Receipt(OPERATION, PROFILE, TYPE, "forged-carrier",
                            IMPACT_SEQUENCE, SavedTribulationImpactReceipt.Stage.STARTED)));
            rejects(() -> SavedTribulationImpactReceipt.verify(file, OWNER,
                    new SavedTribulationImpactReceipt.Receipt(OPERATION, PROFILE, TYPE, CARRIER,
                            IMPACT_SEQUENCE + 1, SavedTribulationImpactReceipt.Stage.STARTED)));
            rejects(() -> SavedTribulationImpactReceipt.verify(file, OWNER, receipt(
                    SavedTribulationImpactReceipt.Stage.RESPAWN_RECEIPT)));

            root = player(SavedTribulationImpactReceipt.Stage.RESPAWN_RECEIPT);
            NbtIo.writeCompressed(root, file);
            SavedTribulationImpactReceipt.verify(file, OWNER, receipt(
                    SavedTribulationImpactReceipt.Stage.RESPAWN_RECEIPT));
            checks++;
            rejects(() -> SavedTribulationImpactReceipt.verify(file, OWNER, receipt(
                    SavedTribulationImpactReceipt.Stage.STARTED)));

            CompoundTag persistent = player(SavedTribulationImpactReceipt.Stage.STARTED).getCompound("NeoForgeData");
            check("preserved".equals(persistent.getCompound("PlayerPersisted").getString("unrelated")));
            check("untouched".equals(persistent.getCompound("PlayerPersisted")
                    .getCompound("shengxian:failure").getString("marker")));
            check(SavedTribulationImpactReceipt.present(persistent));
            check(SavedTribulationImpactReceipt.read(persistent).orElseThrow().equals(receipt(
                    SavedTribulationImpactReceipt.Stage.STARTED)));
            rejects(() -> SavedTribulationImpactReceipt.record(persistent,
                    new SavedTribulationImpactReceipt.Receipt(UUID.randomUUID(), PROFILE, TYPE, CARRIER,
                            IMPACT_SEQUENCE, SavedTribulationImpactReceipt.Stage.STARTED)));
            rejects(() -> SavedTribulationImpactReceipt.record(persistent,
                    new SavedTribulationImpactReceipt.Receipt(OPERATION, PROFILE, TYPE, CARRIER,
                            IMPACT_SEQUENCE + 1, SavedTribulationImpactReceipt.Stage.STARTED)));
            SavedTribulationImpactReceipt.record(persistent, receipt(
                    SavedTribulationImpactReceipt.Stage.RESPAWN_RECEIPT));
            check(SavedTribulationImpactReceipt.read(persistent).orElseThrow().stage()
                    == SavedTribulationImpactReceipt.Stage.RESPAWN_RECEIPT);

            root = player(SavedTribulationImpactReceipt.Stage.STARTED);
            root.getCompound("NeoForgeData").getCompound("PlayerPersisted")
                    .getCompound("shengxian:tribulation_impact").putString("stage", "BROKEN");
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedTribulationImpactReceipt.verify(file, OWNER, receipt(
                    SavedTribulationImpactReceipt.Stage.STARTED)));
            root = player(SavedTribulationImpactReceipt.Stage.STARTED);
            root.getCompound("neoforge:attachments").getCompound("guzhenren:player_variables")
                    .putBoolean("shiqiao", false);
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedTribulationImpactReceipt.verify(file, OWNER, receipt(
                    SavedTribulationImpactReceipt.Stage.STARTED)));
            Files.writeString(file, "corrupt");
            rejects(() -> SavedTribulationImpactReceipt.verify(file, OWNER, receipt(
                    SavedTribulationImpactReceipt.Stage.STARTED)));

            CompoundTag level = new CompoundTag();
            CompoundTag worldData = new CompoundTag();
            worldData.put("Player", player(SavedTribulationImpactReceipt.Stage.RESPAWN_RECEIPT));
            level.put("Data", worldData);
            NbtIo.writeCompressed(level, host);
            SavedTribulationImpactReceipt.verifyHost(host, OWNER, receipt(
                    SavedTribulationImpactReceipt.Stage.RESPAWN_RECEIPT));
            checks++;
            worldData.put("Player", player(SavedTribulationImpactReceipt.Stage.STARTED));
            NbtIo.writeCompressed(level, host);
            rejects(() -> SavedTribulationImpactReceipt.verifyHost(host, OWNER, receipt(
                    SavedTribulationImpactReceipt.Stage.RESPAWN_RECEIPT)));
            System.out.println("PASS saved tribulation impact receipt: " + checks + " compressed-NBT checks");
        } finally {
            Files.deleteIfExists(host);
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
