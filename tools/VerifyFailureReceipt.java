import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.guzhenren.shengxian.compat.SavedFailureReceipt;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Compressed player-NBT checks for failure receipts; starts no Minecraft process or world. */
public final class VerifyFailureReceipt {
    private static int checks;
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OPERATION = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final AscensionProgress.FailureCause CAUSE = AscensionProgress.FailureCause.HEAVEN_OVERLOAD;
    private interface Checked { void run() throws Exception; }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Failure receipt check failed");
        checks++;
    }

    private static void rejects(Checked operation) throws Exception {
        try {
            operation.run();
            throw new AssertionError("Invalid failure receipt was accepted");
        } catch (java.io.IOException expected) {
            checks++;
        }
    }

    private static CompoundTag player(SavedFailureReceipt.Stage stage) throws Exception {
        CompoundTag persistent = new CompoundTag();
        CompoundTag persisted = new CompoundTag();
        persisted.putString("unrelated", "preserved");
        persistent.put("PlayerPersisted", persisted);
        SavedFailureReceipt.record(persistent, OPERATION, CAUSE, SavedFailureReceipt.Stage.STARTED);
        if (stage == SavedFailureReceipt.Stage.RESPAWN_RECEIPT) {
            SavedFailureReceipt.record(persistent, OPERATION, CAUSE, stage);
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
        Path dir = Files.createTempDirectory("shengxian-failure-receipt-");
        Path file = dir.resolve(OWNER + ".dat");
        Path host = dir.resolve("level.dat");
        try {
            rejects(() -> SavedFailureReceipt.verify(file, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.STARTED));
            CompoundTag root = player(SavedFailureReceipt.Stage.STARTED);
            NbtIo.writeCompressed(root, file);
            SavedFailureReceipt.verify(file, OWNER, OPERATION, CAUSE, SavedFailureReceipt.Stage.STARTED);
            checks++;
            rejects(() -> SavedFailureReceipt.verify(file, UUID.randomUUID(), OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.STARTED));
            rejects(() -> SavedFailureReceipt.verify(file, OWNER, UUID.randomUUID(), CAUSE,
                    SavedFailureReceipt.Stage.STARTED));
            rejects(() -> SavedFailureReceipt.verify(file, OWNER, OPERATION,
                    AscensionProgress.FailureCause.EARTH_OVERLOAD, SavedFailureReceipt.Stage.STARTED));
            rejects(() -> SavedFailureReceipt.verify(file, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.RESPAWN_RECEIPT));

            root = player(SavedFailureReceipt.Stage.RESPAWN_RECEIPT);
            NbtIo.writeCompressed(root, file);
            SavedFailureReceipt.verify(file, OWNER, OPERATION, CAUSE, SavedFailureReceipt.Stage.RESPAWN_RECEIPT);
            checks++;
            rejects(() -> SavedFailureReceipt.verify(file, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.STARTED));

            root = player(SavedFailureReceipt.Stage.STARTED);
            root.remove("NeoForgeData");
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedFailureReceipt.verify(file, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.STARTED));
            root = player(SavedFailureReceipt.Stage.STARTED);
            root.getCompound("NeoForgeData").remove("PlayerPersisted");
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedFailureReceipt.verify(file, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.STARTED));
            root = player(SavedFailureReceipt.Stage.STARTED);
            root.getCompound("NeoForgeData").getCompound("PlayerPersisted").remove("shengxian:failure");
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedFailureReceipt.verify(file, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.STARTED));
            root = player(SavedFailureReceipt.Stage.STARTED);
            root.getCompound("NeoForgeData").getCompound("PlayerPersisted")
                    .getCompound("shengxian:failure").putString("stage", "BROKEN");
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedFailureReceipt.verify(file, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.STARTED));
            root = player(SavedFailureReceipt.Stage.STARTED);
            root.getCompound("neoforge:attachments").getCompound("guzhenren:player_variables").putBoolean("shiqiao", false);
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedFailureReceipt.verify(file, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.STARTED));
            root = player(SavedFailureReceipt.Stage.STARTED);
            root.getCompound("neoforge:attachments").remove("guzhenren:player_variables");
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedFailureReceipt.verify(file, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.STARTED));
            Files.writeString(file, "corrupt");
            rejects(() -> SavedFailureReceipt.verify(file, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.STARTED));

            CompoundTag persistent = player(SavedFailureReceipt.Stage.STARTED).getCompound("NeoForgeData");
            check("preserved".equals(persistent.getCompound("PlayerPersisted").getString("unrelated")));
            check(SavedFailureReceipt.read(persistent).orElseThrow().stage() == SavedFailureReceipt.Stage.STARTED);
            rejects(() -> SavedFailureReceipt.record(persistent, UUID.randomUUID(), CAUSE,
                    SavedFailureReceipt.Stage.STARTED));
            SavedFailureReceipt.record(persistent, OPERATION, CAUSE, SavedFailureReceipt.Stage.RESPAWN_RECEIPT);
            check(SavedFailureReceipt.read(persistent).orElseThrow().stage() == SavedFailureReceipt.Stage.RESPAWN_RECEIPT);

            CompoundTag level = new CompoundTag();
            CompoundTag worldData = new CompoundTag();
            worldData.put("Player", player(SavedFailureReceipt.Stage.RESPAWN_RECEIPT));
            level.put("Data", worldData);
            NbtIo.writeCompressed(level, host);
            SavedFailureReceipt.verifyHost(host, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.RESPAWN_RECEIPT);
            checks++;
            worldData.put("Player", player(SavedFailureReceipt.Stage.STARTED));
            NbtIo.writeCompressed(level, host);
            rejects(() -> SavedFailureReceipt.verifyHost(host, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.RESPAWN_RECEIPT));
            worldData.remove("Player");
            NbtIo.writeCompressed(level, host);
            rejects(() -> SavedFailureReceipt.verifyHost(host, OWNER, OPERATION, CAUSE,
                    SavedFailureReceipt.Stage.STARTED));
            System.out.println("PASS saved failure receipt: " + checks + " compressed-NBT checks");
        } finally {
            Files.deleteIfExists(host);
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
