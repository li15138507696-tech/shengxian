import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.guzhenren.shengxian.compat.SavedGuBacklash;
import net.guzhenren.shengxian.core.GuActivation;
import net.guzhenren.shengxian.core.GuBacklashState;
import net.guzhenren.shengxian.core.Phase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

/** Offline compressed player-NBT checks for the Gu backlash transaction receipt. */
public final class VerifyGuBacklashReceipt {
    private static int checks;
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION = UUID.fromString("11111111-2222-3333-4444-555555555551");
    private static final String ITEM = "guzhenren:test_gu";
    private static final String FINGERPRINT = "a".repeat(64);

    private interface Checked { void run() throws Exception; }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Gu backlash receipt check failed");
        checks++;
    }

    private static void rejects(Checked operation) throws Exception {
        try {
            operation.run();
            throw new AssertionError("Invalid Gu backlash receipt was accepted");
        } catch (IOException | IllegalArgumentException expected) {
            checks++;
        }
    }

    private static GuBacklashState state() {
        GuBacklashState state = GuBacklashState.empty(SESSION, 1);
        GuActivation activation = new GuActivation(OWNER, ITEM, GuActivation.Entry.KEYBIND,
                GuActivation.Outcome.COMPLETED, 100L, 1L);
        return GuBacklashState.observe(state, SESSION, Phase.ABSORBING, 1, activation,
                true, false).state();
    }

    private static SavedGuBacklash.Transaction transaction(SavedGuBacklash.Stage stage) {
        GuBacklashState state = state();
        return new SavedGuBacklash.Transaction(SESSION, 1L, state.lastEventKey(),
                GuBacklashState.Decision.LOSS_DUE, stage, "inventory:0", ITEM, FINGERPRINT, 2, 1);
    }

    private static CompoundTag item(int count) {
        CompoundTag item = new CompoundTag();
        item.putString("id", ITEM);
        item.putInt("count", count);
        item.put("components", new CompoundTag());
        return item;
    }

    private static CompoundTag player(GuBacklashState state, SavedGuBacklash.Transaction transaction,
                                      int inventoryCount) throws Exception {
        return player(state, transaction, inventoryCount, 1);
    }

    private static CompoundTag player(GuBacklashState state, SavedGuBacklash.Transaction transaction,
                                      int inventoryCount, int killerCount) throws Exception {
        CompoundTag persistent = new CompoundTag();
        CompoundTag persisted = new CompoundTag();
        persisted.putString("unrelated", "preserved");
        persistent.put("PlayerPersisted", persisted);
        SavedGuBacklash.record(persistent, state, transaction);
        CompoundTag root = new CompoundTag();
        root.putUUID("UUID", OWNER);
        root.put("NeoForgeData", persistent);
        ListTag inventory = new ListTag();
        if (inventoryCount > 0) {
            CompoundTag entry = item(inventoryCount);
            entry.putByte("Slot", (byte) 0);
            inventory.add(entry);
        }
        root.put("Inventory", inventory);
        CompoundTag variables = new CompoundTag();
        CompoundTag killerGu = killerCount > 0 ? item(killerCount) : new CompoundTag();
        variables.put("ShaZhao1_GuChong1", killerGu);
        CompoundTag attachments = new CompoundTag();
        attachments.put("guzhenren:player_variables", variables);
        root.put("neoforge:attachments", attachments);
        return root;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("shengxian-gu-backlash-");
        Path playerFile = dir.resolve(OWNER + ".dat");
        Path hostFile = dir.resolve("level.dat");
        try {
            GuBacklashState state = state();
            SavedGuBacklash.Transaction prepared = transaction(SavedGuBacklash.Stage.PREPARED);
            rejects(() -> new SavedGuBacklash.Stored(state,
                    new SavedGuBacklash.Transaction(SESSION, 2L, prepared.eventKey(),
                            GuBacklashState.Decision.LOSS_DUE, SavedGuBacklash.Stage.COMMITTED,
                            "inventory:0", ITEM, FINGERPRINT, 2, 1)));
            rejects(() -> new SavedGuBacklash.Stored(state,
                    new SavedGuBacklash.Transaction(SESSION, 1L, "different-event",
                            GuBacklashState.Decision.TARGET_UNAVAILABLE, SavedGuBacklash.Stage.COMMITTED,
                            "", "", "", -1, -1)));
            rejects(() -> new SavedGuBacklash.Stored(state, transaction(SavedGuBacklash.Stage.UNCERTAIN)));
            CompoundTag memory = new CompoundTag();
            CompoundTag persisted = new CompoundTag();
            persisted.putString("unrelated", "preserved");
            memory.put("PlayerPersisted", persisted);
            SavedGuBacklash.record(memory, state, prepared);
            check("preserved".equals(memory.getCompound("PlayerPersisted").getString("unrelated")));
            check(SavedGuBacklash.read(memory).orElseThrow().state().equals(state));
            check(SavedGuBacklash.read(memory).orElseThrow().transaction().equals(prepared));
            SavedGuBacklash.Transaction committedMemory = transaction(SavedGuBacklash.Stage.COMMITTED);
            SavedGuBacklash.record(memory, state, committedMemory);
            check(SavedGuBacklash.read(memory).orElseThrow().transaction().equals(committedMemory));
            rejects(() -> SavedGuBacklash.record(memory, state, prepared));

            CompoundTag zeroSequenceMemory = new CompoundTag();
            GuBacklashState emptyState = GuBacklashState.empty(SESSION, 8);
            SavedGuBacklash.record(zeroSequenceMemory, emptyState, null);
            GuBacklashState substitutedEmpty = new GuBacklashState(SESSION, 0, 8, 0L,
                    "forged-event", null, false, "");
            rejects(() -> SavedGuBacklash.record(zeroSequenceMemory, substitutedEmpty, null));

            CompoundTag uncertainMemory = new CompoundTag();
            SavedGuBacklash.Transaction uncertainReceipt = transaction(SavedGuBacklash.Stage.UNCERTAIN);
            SavedGuBacklash.record(uncertainMemory, state.quarantine("save-uncertain"), uncertainReceipt);
            UUID replacementSession = UUID.fromString("22222222-3333-4444-5555-666666666662");
            rejects(() -> SavedGuBacklash.record(uncertainMemory,
                    GuBacklashState.empty(replacementSession, 8), null));

            CompoundTag root = player(state, prepared, 2);
            NbtIo.writeCompressed(root, playerFile);
            SavedGuBacklash.verify(playerFile, OWNER, state, prepared);
            checks++;
            rejects(() -> SavedGuBacklash.verify(playerFile, UUID.randomUUID(), state, prepared));
            rejects(() -> SavedGuBacklash.verify(playerFile, OWNER,
                    GuBacklashState.empty(SESSION, 8), prepared));
            rejects(() -> SavedGuBacklash.verify(playerFile, OWNER, state,
                    new SavedGuBacklash.Transaction(SESSION, 2L, prepared.eventKey(),
                            prepared.decision(), prepared.stage(), prepared.targetLocator(), ITEM,
                            FINGERPRINT, 2, 1)));
            rejects(() -> SavedGuBacklash.verify(playerFile, OWNER, state,
                    new SavedGuBacklash.Transaction(SESSION, 1L, prepared.eventKey(),
                            prepared.decision(), prepared.stage(), "inventory:1", ITEM, FINGERPRINT, 2, 1)));
            rejects(() -> new SavedGuBacklash.Transaction(SESSION, 1L, prepared.eventKey(),
                    prepared.decision(), prepared.stage(), "inventory:36", ITEM, FINGERPRINT, 2, 1));
            rejects(() -> new SavedGuBacklash.Transaction(SESSION, 1L, prepared.eventKey(),
                    GuBacklashState.Decision.VITAL_PROTECTED, SavedGuBacklash.Stage.COMMITTED,
                    "", ITEM, FINGERPRINT, 2, 2));
            rejects(() -> new SavedGuBacklash.Transaction(SESSION, 1L, prepared.eventKey(),
                    GuBacklashState.Decision.TARGET_UNAVAILABLE, SavedGuBacklash.Stage.PREPARED,
                    "", "", "", -1, -1));

            SavedGuBacklash.Transaction committed = transaction(SavedGuBacklash.Stage.COMMITTED);
            root = player(state, committed, 1);
            NbtIo.writeCompressed(root, playerFile);
            SavedGuBacklash.verify(playerFile, OWNER, state, committed);
            checks++;
            rejects(() -> SavedGuBacklash.verify(playerFile, OWNER, state, prepared));
            root = player(state, committed, 2);
            NbtIo.writeCompressed(root, playerFile);
            rejects(() -> SavedGuBacklash.verify(playerFile, OWNER, state, committed));

            CompoundTag level = new CompoundTag();
            CompoundTag data = new CompoundTag();
            data.put("Player", player(state, committed, 1));
            level.put("Data", data);
            NbtIo.writeCompressed(level, hostFile);
            SavedGuBacklash.verifyHost(hostFile, OWNER, state, committed);
            checks++;
            data.put("Player", player(state, committed, 2));
            NbtIo.writeCompressed(level, hostFile);
            rejects(() -> SavedGuBacklash.verifyHost(hostFile, OWNER, state, committed));
            data.remove("Player");
            NbtIo.writeCompressed(level, hostFile);
            rejects(() -> SavedGuBacklash.verifyHost(hostFile, OWNER, state, committed));
            Files.writeString(playerFile, "corrupt");
            rejects(() -> SavedGuBacklash.verify(playerFile, OWNER, state, committed));

            CompoundTag malformed = new CompoundTag();
            malformed.putString("PlayerPersisted", "wrong-type");
            rejects(() -> SavedGuBacklash.record(malformed, state, null));
            SavedGuBacklash.Transaction killerReceipt = new SavedGuBacklash.Transaction(SESSION, 1L,
                    state.lastEventKey(), GuBacklashState.Decision.LOSS_DUE, SavedGuBacklash.Stage.COMMITTED,
                    "killer:ShaZhao1_GuChong1", ITEM, FINGERPRINT, 2, 1);
            CompoundTag killerRoot = player(state, killerReceipt, 0, 1);
            NbtIo.writeCompressed(killerRoot, playerFile);
            SavedGuBacklash.verify(playerFile, OWNER, state, killerReceipt);
            checks++;
            killerRoot = player(state, killerReceipt, 0, 2);
            NbtIo.writeCompressed(killerRoot, playerFile);
            rejects(() -> SavedGuBacklash.verify(playerFile, OWNER, state, killerReceipt));

            SavedGuBacklash.Transaction vitalReceipt = new SavedGuBacklash.Transaction(SESSION, 1L,
                    state.lastEventKey(), GuBacklashState.Decision.VITAL_PROTECTED,
                    SavedGuBacklash.Stage.COMMITTED, "inventory:0", ITEM, FINGERPRINT, 2, 2);
            GuActivation activation = new GuActivation(OWNER, ITEM, GuActivation.Entry.KEYBIND,
                    GuActivation.Outcome.COMPLETED, 100L, 1L);
            GuBacklashState vitalState = GuBacklashState.observe(GuBacklashState.empty(SESSION, 1),
                    SESSION, Phase.ABSORBING, 1, activation, true, true).state();
            CompoundTag vitalRoot = player(vitalState, vitalReceipt, 2);
            NbtIo.writeCompressed(vitalRoot, playerFile);
            SavedGuBacklash.verify(playerFile, OWNER, vitalState, vitalReceipt);
            checks++;
            vitalRoot = player(vitalState, vitalReceipt, 1);
            NbtIo.writeCompressed(vitalRoot, playerFile);
            rejects(() -> SavedGuBacklash.verify(playerFile, OWNER, vitalState, vitalReceipt));
            check(SavedGuBacklash.present(memory));
            System.out.println("PASS saved Gu backlash: " + checks + " compressed-NBT checks");
        } finally {
            Files.deleteIfExists(hostFile);
            Files.deleteIfExists(playerFile);
            Files.deleteIfExists(dir);
        }
    }
}
