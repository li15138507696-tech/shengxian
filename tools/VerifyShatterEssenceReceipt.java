import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.guzhenren.shengxian.compat.SavedShatterEssenceReceipt;
import net.guzhenren.shengxian.core.ShatterEssenceTransaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Offline compressed-NBT checks for the one-time shatter true-essence debit. */
public final class VerifyShatterEssenceReceipt {
    private static int checks;
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final long PHASE = 3L;
    private static final double BEFORE = 100.0;
    private static final double TARGET = 80.0;

    @FunctionalInterface
    private interface Checked { void run() throws Exception; }

    private static ShatterEssenceTransaction.Receipt receipt(ShatterEssenceTransaction.Stage stage) {
        return switch (stage) {
            case PREPARED -> ShatterEssenceTransaction.Receipt.prepared(SESSION, PHASE, BEFORE, TARGET);
            case COMMITTED -> ShatterEssenceTransaction.Receipt.committed(SESSION, PHASE, BEFORE, TARGET);
            case UNCERTAIN -> new ShatterEssenceTransaction.Receipt(SESSION, PHASE, stage, BEFORE, TARGET, null);
        };
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Shatter essence receipt check failed");
        checks++;
    }

    private static void rejects(Checked operation) throws Exception {
        try {
            operation.run();
            throw new AssertionError("Invalid shatter essence receipt was accepted");
        } catch (java.io.IOException expected) {
            checks++;
        }
    }

    private static CompoundTag player(double essence, ShatterEssenceTransaction.Stage stage) throws Exception {
        CompoundTag persistent = new CompoundTag();
        CompoundTag persisted = new CompoundTag();
        persisted.putString("unrelated", "preserved");
        CompoundTag otherReceipt = new CompoundTag();
        otherReceipt.putString("marker", "untouched");
        persisted.put("shengxian:failure", otherReceipt);
        persistent.put("PlayerPersisted", persisted);
        if (stage == ShatterEssenceTransaction.Stage.COMMITTED) {
            SavedShatterEssenceReceipt.record(persistent, receipt(ShatterEssenceTransaction.Stage.PREPARED));
            SavedShatterEssenceReceipt.record(persistent, receipt(stage));
        } else {
            SavedShatterEssenceReceipt.record(persistent, receipt(stage));
        }
        CompoundTag root = new CompoundTag();
        root.putUUID("UUID", OWNER);
        root.put("NeoForgeData", persistent);
        CompoundTag variables = new CompoundTag();
        variables.putDouble("zhenyuan", essence);
        CompoundTag attachments = new CompoundTag();
        attachments.put("guzhenren:player_variables", variables);
        root.put("neoforge:attachments", attachments);
        return root;
    }

    private static CompoundTag host(CompoundTag player) {
        CompoundTag level = new CompoundTag();
        CompoundTag data = new CompoundTag();
        data.put("Player", player);
        level.put("Data", data);
        return level;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("shengxian-shatter-receipt-");
        Path file = dir.resolve(OWNER + ".dat");
        Path hostFile = dir.resolve("level.dat");
        try {
            rejects(() -> SavedShatterEssenceReceipt.verify(file, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.PREPARED), BEFORE));

            CompoundTag root = player(BEFORE, ShatterEssenceTransaction.Stage.PREPARED);
            NbtIo.writeCompressed(root, file);
            SavedShatterEssenceReceipt.verify(file, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.PREPARED), BEFORE);
            checks++;
            var preparedFileState = SavedShatterEssenceReceipt.readState(file, OWNER, false);
            check(preparedFileState.receipt().orElseThrow().equals(receipt(
                    ShatterEssenceTransaction.Stage.PREPARED)));
            check(preparedFileState.trueEssence() == BEFORE);
            rejects(() -> SavedShatterEssenceReceipt.verify(file, UUID.randomUUID(), receipt(
                    ShatterEssenceTransaction.Stage.PREPARED), BEFORE));
            rejects(() -> SavedShatterEssenceReceipt.verify(file, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.PREPARED), TARGET));
            rejects(() -> SavedShatterEssenceReceipt.verify(file, OWNER,
                    ShatterEssenceTransaction.Receipt.prepared(UUID.randomUUID(), PHASE, BEFORE, TARGET), BEFORE));

            CompoundTag persistent = root.getCompound("NeoForgeData");
            check("preserved".equals(persistent.getCompound("PlayerPersisted").getString("unrelated")));
            check("untouched".equals(persistent.getCompound("PlayerPersisted")
                    .getCompound("shengxian:failure").getString("marker")));
            check(SavedShatterEssenceReceipt.present(persistent));
            check(SavedShatterEssenceReceipt.read(persistent).orElseThrow().equals(receipt(
                    ShatterEssenceTransaction.Stage.PREPARED)));
            rejects(() -> SavedShatterEssenceReceipt.record(persistent,
                    ShatterEssenceTransaction.Receipt.prepared(UUID.randomUUID(), PHASE, BEFORE, TARGET)));
            rejects(() -> SavedShatterEssenceReceipt.record(persistent,
                    ShatterEssenceTransaction.Receipt.prepared(SESSION, PHASE, 110.0, TARGET)));
            SavedShatterEssenceReceipt.record(persistent, receipt(ShatterEssenceTransaction.Stage.COMMITTED));
            check(SavedShatterEssenceReceipt.read(persistent).orElseThrow().stage()
                    == ShatterEssenceTransaction.Stage.COMMITTED);
            rejects(() -> SavedShatterEssenceReceipt.record(persistent, receipt(
                    ShatterEssenceTransaction.Stage.PREPARED)));

            root = player(TARGET, ShatterEssenceTransaction.Stage.COMMITTED);
            NbtIo.writeCompressed(root, file);
            SavedShatterEssenceReceipt.verify(file, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.COMMITTED), TARGET);
            checks++;
            root.getCompound("neoforge:attachments").getCompound("guzhenren:player_variables")
                    .putDouble("zhenyuan", 73.0);
            NbtIo.writeCompressed(root, file);
            SavedShatterEssenceReceipt.verifyState(file, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.COMMITTED));
            checks++;
            rejects(() -> SavedShatterEssenceReceipt.verify(file, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.COMMITTED), TARGET));

            root = player(BEFORE, ShatterEssenceTransaction.Stage.PREPARED);
            root.getCompound("NeoForgeData").getCompound("PlayerPersisted")
                    .getCompound("shengxian:shatter_essence").putString("stage", "INVALID");
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedShatterEssenceReceipt.verifyState(file, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.PREPARED)));
            root = player(BEFORE, ShatterEssenceTransaction.Stage.PREPARED);
            root.getCompound("neoforge:attachments").getCompound("guzhenren:player_variables")
                    .remove("zhenyuan");
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedShatterEssenceReceipt.verify(file, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.PREPARED), BEFORE));
            root = player(Double.NaN, ShatterEssenceTransaction.Stage.PREPARED);
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedShatterEssenceReceipt.verify(file, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.PREPARED), BEFORE));
            Files.writeString(file, "corrupt");
            rejects(() -> SavedShatterEssenceReceipt.verifyState(file, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.PREPARED)));

            NbtIo.writeCompressed(host(player(TARGET, ShatterEssenceTransaction.Stage.COMMITTED)), hostFile);
            SavedShatterEssenceReceipt.verifyHost(hostFile, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.COMMITTED), TARGET);
            checks++;
            var committedHostState = SavedShatterEssenceReceipt.readState(hostFile, OWNER, true);
            check(committedHostState.receipt().orElseThrow().equals(receipt(
                    ShatterEssenceTransaction.Stage.COMMITTED)));
            check(committedHostState.trueEssence() == TARGET);
            NbtIo.writeCompressed(host(player(73.0, ShatterEssenceTransaction.Stage.COMMITTED)), hostFile);
            SavedShatterEssenceReceipt.verifyHostState(hostFile, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.COMMITTED));
            checks++;
            NbtIo.writeCompressed(host(player(TARGET, ShatterEssenceTransaction.Stage.PREPARED)), hostFile);
            rejects(() -> SavedShatterEssenceReceipt.verifyHostState(hostFile, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.COMMITTED)));
            CompoundTag noPlayer = new CompoundTag();
            noPlayer.put("Data", new CompoundTag());
            NbtIo.writeCompressed(noPlayer, hostFile);
            rejects(() -> SavedShatterEssenceReceipt.verifyHostState(hostFile, OWNER, receipt(
                    ShatterEssenceTransaction.Stage.COMMITTED)));

            CompoundTag uncertain = new CompoundTag();
            uncertain.put("PlayerPersisted", new CompoundTag());
            SavedShatterEssenceReceipt.record(uncertain, new ShatterEssenceTransaction.Receipt(
                    SESSION, PHASE, ShatterEssenceTransaction.Stage.UNCERTAIN, null, TARGET, null));
            check(SavedShatterEssenceReceipt.read(uncertain).orElseThrow().stage()
                    == ShatterEssenceTransaction.Stage.UNCERTAIN);
            rejects(() -> SavedShatterEssenceReceipt.record(uncertain, receipt(
                    ShatterEssenceTransaction.Stage.PREPARED)));
            System.out.println("PASS saved shatter essence receipt: " + checks + " compressed-NBT checks");
        } finally {
            Files.deleteIfExists(hostFile);
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
