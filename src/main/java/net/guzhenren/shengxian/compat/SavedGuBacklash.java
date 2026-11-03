package net.guzhenren.shengxian.compat;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.guzhenren.shengxian.core.GuBacklashState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Player-persisted pressure and fail-closed receipt for one Gu backlash transaction. */
public final class SavedGuBacklash {
    private static final String PERSISTED = "PlayerPersisted";
    private static final String KEY = "shengxian:gu_backlash";
    private static final String ATTACHMENTS = "neoforge:attachments";
    private static final String VARIABLES = "guzhenren:player_variables";
    private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    public enum Stage { PREPARED, COMMITTED, UNCERTAIN }

    /** Exact stack location and before/after count verified from the serialized player. */
    public record Transaction(UUID sessionId, long sequence, String eventKey,
                              GuBacklashState.Decision decision, Stage stage,
                              String targetLocator, String itemId, String fingerprint,
                              int countBefore, int countAfter) {
        public Transaction {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(stage, "stage");
            if (sequence < 1) throw new IllegalArgumentException("Invalid Gu backlash transaction sequence");
            if (eventKey == null || eventKey.isBlank() || eventKey.length() > 512) {
                throw new IllegalArgumentException("Invalid Gu backlash transaction event");
            }
            if (targetLocator == null || targetLocator.length() > 96
                    || itemId == null || itemId.length() > 128 || fingerprint == null) {
                throw new IllegalArgumentException("Invalid Gu backlash target identity");
            }
            if (decision == GuBacklashState.Decision.LOSS_DUE) {
                if (!validLocator(targetLocator) || itemId.isBlank() || !FINGERPRINT.matcher(fingerprint).matches()
                        || countBefore < 1 || countAfter != countBefore - 1) {
                    throw new IllegalArgumentException("Invalid Gu backlash loss target");
                }
            } else if (decision == GuBacklashState.Decision.TARGET_UNAVAILABLE) {
                if (!targetLocator.isEmpty() || !itemId.isEmpty() || !fingerprint.isEmpty()
                        || countBefore != -1 || countAfter != -1 || stage != Stage.COMMITTED) {
                    throw new IllegalArgumentException("Unavailable Gu target must not claim a stack");
                }
            } else if (decision == GuBacklashState.Decision.VITAL_PROTECTED) {
                if (!validLocator(targetLocator) || itemId.isBlank()
                        || !FINGERPRINT.matcher(fingerprint).matches()
                        || countBefore < 1 || countAfter != countBefore
                        || stage != Stage.COMMITTED) {
                    throw new IllegalArgumentException("Invalid protected Gu target receipt");
                }
            } else {
                throw new IllegalArgumentException("Only threshold outcomes have transaction receipts");
            }
        }

        public Transaction withStage(Stage next) {
            return new Transaction(sessionId, sequence, eventKey, decision, next, targetLocator,
                    itemId, fingerprint, countBefore, countAfter);
        }

        private static boolean validLocator(String locator) {
            if (locator.startsWith("inventory:")) {
                try {
                    int slot = Integer.parseInt(locator.substring("inventory:".length()));
                    return slot >= 0 && slot <= 35
                            || slot >= 100 && slot <= 103
                            || slot == 150;
                } catch (NumberFormatException invalid) {
                    return false;
                }
            }
            if (!locator.startsWith("killer:")) return false;
            String field = locator.substring("killer:".length());
            return field.matches("ShaZhao[1-4]_GuChong([1-9]|10)");
        }
    }

    public record Stored(GuBacklashState state, Transaction transaction) {
        public Stored {
            Objects.requireNonNull(state, "state");
            if (transaction != null) {
                if (!transaction.sessionId().equals(state.sessionId())) {
                    throw new IllegalArgumentException("Gu backlash receipt belongs to another session");
                }
                if (transaction.sequence() > state.sequence()) {
                    throw new IllegalArgumentException("Gu backlash receipt is ahead of its state");
                }
                if (transaction.sequence() == state.sequence()) {
                    GuBacklashState.Receipt receipt = state.lastReceipt();
                    if (receipt == null || !receipt.eventKey().equals(transaction.eventKey())
                            || receipt.decision() != transaction.decision()) {
                        throw new IllegalArgumentException("Gu backlash receipt does not match the latest event");
                    }
                }
                if (transaction.stage() == Stage.UNCERTAIN && !state.quarantined()) {
                    throw new IllegalArgumentException("Uncertain Gu transaction requires quarantined state");
                }
                if (transaction.stage() == Stage.PREPARED && state.quarantined()) {
                    throw new IllegalArgumentException("Quarantined Gu transaction must use the uncertain stage");
                }
            }
        }
    }

    private SavedGuBacklash() {}

    public static Optional<Stored> read(CompoundTag persistentData) {
        if (persistentData == null || !persistentData.contains(PERSISTED, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag persisted = persistentData.getCompound(PERSISTED);
        if (!persisted.contains(KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        try {
            CompoundTag root = persisted.getCompound(KEY);
            if (!root.contains("state", Tag.TAG_COMPOUND)) return Optional.empty();
            GuBacklashState state = readState(root.getCompound("state"));
            if (root.contains("transaction") && !root.contains("transaction", Tag.TAG_COMPOUND)) return Optional.empty();
            Transaction transaction = root.contains("transaction", Tag.TAG_COMPOUND)
                    ? readTransaction(root.getCompound("transaction")) : null;
            return Optional.of(new Stored(state, transaction));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    /** Distinguishes absent state from malformed state so callers can fail closed. */
    public static boolean present(CompoundTag persistentData) {
        if (persistentData == null || !persistentData.contains(PERSISTED)) return false;
        if (!persistentData.contains(PERSISTED, Tag.TAG_COMPOUND)) return true;
        return persistentData.getCompound(PERSISTED).contains(KEY);
    }

    /** Writes state and transaction while preserving unrelated PlayerPersisted data. */
    public static void record(CompoundTag persistentData, GuBacklashState state,
                              Transaction transaction) throws IOException {
        Objects.requireNonNull(persistentData, "persistentData");
        Objects.requireNonNull(state, "state");
        if (persistentData.contains(PERSISTED) && !persistentData.contains(PERSISTED, Tag.TAG_COMPOUND)) {
            throw new IOException("PlayerPersisted data has an invalid type");
        }
        CompoundTag persisted = persistentData.getCompound(PERSISTED);
        Optional<Stored> prior = read(persistentData);
        if (persisted.contains(KEY) && prior.isEmpty()) throw new IOException("Malformed Gu backlash state is already present");

        Transaction nextTransaction = transaction;
        if (prior.isPresent()) {
            Stored old = prior.get();
            if (old.state().sessionId().equals(state.sessionId())) {
                if (state.sequence() < old.state().sequence()) throw new IOException("Gu backlash sequence moved backwards");
                if (state.sequence() == old.state().sequence()
                        && !sameEventStateOrQuarantine(old.state(), state)) {
                    throw new IOException("Gu backlash receipt identity changed at the same sequence");
                }
                if (old.transaction() != null) {
                    if (old.transaction().stage() == Stage.PREPARED
                            && state.sequence() != old.transaction().sequence()) {
                        throw new IOException("Gu backlash state cannot advance with a prepared loss");
                    }
                    if (old.transaction().stage() == Stage.UNCERTAIN
                            && (!state.equals(old.state())
                            || transaction != null && !transaction.equals(old.transaction()))) {
                        throw new IOException("Uncertain Gu backlash transaction cannot be replaced or advanced");
                    }
                    if (transaction == null) nextTransaction = old.transaction();
                    else validateTransactionAdvance(old.transaction(), transaction);
                }
            } else {
                if (old.transaction() != null && old.transaction().stage() != Stage.COMMITTED) {
                    throw new IOException("Cannot replace a session with an unfinished Gu transaction");
                }
            }
        }
        if (nextTransaction != null && !nextTransaction.sessionId().equals(state.sessionId())) {
            throw new IOException("Gu backlash transaction does not match state session");
        }
        if (nextTransaction != null && nextTransaction.sequence() > state.sequence()) {
            throw new IOException("Gu backlash transaction is ahead of its state sequence");
        }

        CompoundTag root = new CompoundTag();
        root.put("state", writeState(state));
        if (nextTransaction != null) root.put("transaction", writeTransaction(nextTransaction));
        persisted.put(KEY, root);
        persistentData.put(PERSISTED, persisted);
    }

    public static void verify(Path file, UUID owner, GuBacklashState state,
                              Transaction transaction) throws IOException {
        verifyFile(file, owner, state, transaction, false);
    }

    public static void verifyHost(Path file, UUID owner, GuBacklashState state,
                                  Transaction transaction) throws IOException {
        verifyFile(file, owner, state, transaction, true);
    }

    /** Verifies durable state and receipt identity after unrelated gameplay may have changed the stack. */
    public static void verifyState(Path file, UUID owner, GuBacklashState state,
                                   Transaction transaction) throws IOException {
        verifyFile(file, owner, state, transaction, false, false);
    }

    public static void verifyHostState(Path file, UUID owner, GuBacklashState state,
                                       Transaction transaction) throws IOException {
        verifyFile(file, owner, state, transaction, true, false);
    }

    private static void verifyFile(Path file, UUID owner, GuBacklashState expectedState,
                                   Transaction expectedTransaction, boolean host) throws IOException {
        verifyFile(file, owner, expectedState, expectedTransaction, host, true);
    }

    private static void verifyFile(Path file, UUID owner, GuBacklashState expectedState,
                                   Transaction expectedTransaction, boolean host,
                                   boolean verifyTargetCount) throws IOException {
        if (owner == null || expectedState == null) throw new IOException("Gu backlash verification identity is missing");
        if (verifyTargetCount && expectedTransaction != null && expectedTransaction.stage() == Stage.UNCERTAIN) {
            throw new IOException("Uncertain Gu backlash transaction cannot be confirmed");
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(MAX_NBT_BYTES));
            if (host) {
                if (!root.contains("Data", Tag.TAG_COMPOUND)
                        || !root.getCompound("Data").contains("Player", Tag.TAG_COMPOUND)) {
                    throw new IOException("Saved singleplayer owner data is missing");
                }
                root = root.getCompound("Data").getCompound("Player");
            }
            if (!root.hasUUID("UUID") || !owner.equals(root.getUUID("UUID"))) {
                throw new IOException("Saved player identity does not match Gu backlash owner");
            }
            if (!root.contains("NeoForgeData", Tag.TAG_COMPOUND)) throw new IOException("Saved NeoForge player data is missing");
            Optional<Stored> stored = read(root.getCompound("NeoForgeData"));
            if (stored.isEmpty() || !stored.get().state().equals(expectedState)
                    || !Objects.equals(stored.get().transaction(), expectedTransaction)) {
                throw new IOException("Saved Gu backlash state or receipt does not match expected values");
            }
            if (verifyTargetCount && expectedTransaction != null) verifyTarget(root, expectedTransaction);
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
        } catch (RuntimeException malformed) {
            throw new IOException("Saved Gu backlash state could not be verified", malformed);
        }
    }

    private static void verifyTarget(CompoundTag player, Transaction transaction) throws IOException {
        if (transaction.decision() == GuBacklashState.Decision.TARGET_UNAVAILABLE) return;
        if (transaction.targetLocator().isEmpty()) {
            if (transaction.decision() == GuBacklashState.Decision.VITAL_PROTECTED) return;
            throw new IOException("Gu backlash target locator is missing");
        }
        int expectedCount = transaction.stage() == Stage.PREPARED
                ? transaction.countBefore() : transaction.countAfter();
        String expectedItem = transaction.itemId();
        CompoundTag stack;
        if (transaction.targetLocator().startsWith("inventory:")) {
            int slot = Integer.parseInt(transaction.targetLocator().substring("inventory:".length()));
            stack = inventoryStack(player, slot);
        } else {
            if (!player.contains(ATTACHMENTS, Tag.TAG_COMPOUND)
                    || !player.getCompound(ATTACHMENTS).contains(VARIABLES, Tag.TAG_COMPOUND)) {
                throw new IOException("Saved upstream player variables are missing");
            }
            String field = transaction.targetLocator().substring("killer:".length());
            CompoundTag variables = player.getCompound(ATTACHMENTS).getCompound(VARIABLES);
            stack = variables.contains(field, Tag.TAG_COMPOUND) ? variables.getCompound(field) : new CompoundTag();
        }
        int count = stack.contains("count", Tag.TAG_ANY_NUMERIC) ? stack.getInt("count")
                : stack.contains("Count", Tag.TAG_ANY_NUMERIC) ? stack.getInt("Count") : 0;
        String itemId = count > 0 ? stack.getString("id") : "";
        if (count != expectedCount || (count > 0 && !expectedItem.equals(itemId))) {
            throw new IOException("Saved Gu stack at the exact target slot does not match the receipt");
        }
    }

    private static CompoundTag inventoryStack(CompoundTag player, int slot) throws IOException {
        if (!player.contains("Inventory", Tag.TAG_LIST)) throw new IOException("Saved player inventory is missing");
        ListTag inventory = player.getList("Inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < inventory.size(); i++) {
            CompoundTag entry = inventory.getCompound(i);
            if (entry.contains("Slot", Tag.TAG_BYTE) && entry.getByte("Slot") == (byte) slot) return entry;
        }
        return new CompoundTag();
    }

    private static void validateTransactionAdvance(Transaction old, Transaction next) throws IOException {
        if (old.equals(next)) return;
        if (old.stage() == Stage.PREPARED) {
            boolean commit = old.sessionId().equals(next.sessionId()) && old.sequence() == next.sequence()
                    && old.eventKey().equals(next.eventKey()) && old.decision() == next.decision()
                    && old.stage() == Stage.PREPARED
                    && (next.stage() == Stage.COMMITTED || next.stage() == Stage.UNCERTAIN)
                    && old.targetLocator().equals(next.targetLocator()) && old.itemId().equals(next.itemId())
                    && old.fingerprint().equals(next.fingerprint()) && old.countBefore() == next.countBefore()
                    && old.countAfter() == next.countAfter();
            if (!commit) throw new IOException("Prepared Gu loss can only move to its matching committed receipt");
            return;
        }
        if (old.sessionId().equals(next.sessionId()) && old.sequence() == next.sequence()) {
            throw new IOException("Gu backlash receipt cannot roll back or change identity");
        }
        if (!old.sessionId().equals(next.sessionId()) || next.sequence() <= old.sequence()) {
            throw new IOException("Gu backlash transaction sequence moved backwards");
        }
    }

    private static boolean sameEventStateOrQuarantine(GuBacklashState first, GuBacklashState second) {
        boolean quarantineMatches = first.quarantined()
                ? second.quarantined() && first.quarantineReason().equals(second.quarantineReason())
                : second.quarantined() ? !second.quarantineReason().isBlank()
                : first.quarantineReason().equals(second.quarantineReason());
        return first.lastEventKey().equals(second.lastEventKey())
                && Objects.equals(first.lastReceipt(), second.lastReceipt())
                && first.pressure() == second.pressure() && first.threshold() == second.threshold()
                && first.sequence() == second.sequence()
                && quarantineMatches;
    }

    private static CompoundTag writeState(GuBacklashState state) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("session", state.sessionId());
        tag.putInt("pressure", state.pressure());
        tag.putInt("threshold", state.threshold());
        tag.putLong("sequence", state.sequence());
        tag.putString("lastEvent", state.lastEventKey());
        tag.putBoolean("quarantined", state.quarantined());
        tag.putString("quarantineReason", state.quarantineReason());
        if (state.lastReceipt() != null) {
            CompoundTag receipt = new CompoundTag();
            receipt.putLong("sequence", state.lastReceipt().sequence());
            receipt.putString("event", state.lastReceipt().eventKey());
            receipt.putString("decision", state.lastReceipt().decision().name());
            tag.put("lastReceipt", receipt);
        }
        return tag;
    }

    private static GuBacklashState readState(CompoundTag tag) {
        if (!tag.hasUUID("session") || !tag.contains("pressure", Tag.TAG_INT)
                || !tag.contains("threshold", Tag.TAG_INT) || !tag.contains("sequence", Tag.TAG_LONG)
                || !tag.contains("lastEvent", Tag.TAG_STRING) || !tag.contains("quarantined", Tag.TAG_BYTE)
                || !tag.contains("quarantineReason", Tag.TAG_STRING)
                || (tag.contains("lastReceipt") && !tag.contains("lastReceipt", Tag.TAG_COMPOUND))) {
            throw new IllegalArgumentException("Malformed Gu backlash state");
        }
        UUID session = tag.getUUID("session");
        GuBacklashState.Receipt receipt = null;
        if (tag.contains("lastReceipt", Tag.TAG_COMPOUND)) {
            CompoundTag savedReceipt = tag.getCompound("lastReceipt");
            if (!savedReceipt.contains("sequence", Tag.TAG_LONG) || !savedReceipt.contains("event", Tag.TAG_STRING)
                    || !savedReceipt.contains("decision", Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Malformed Gu backlash event receipt");
            }
            receipt = new GuBacklashState.Receipt(savedReceipt.getLong("sequence"), savedReceipt.getString("event"),
                    GuBacklashState.Decision.valueOf(savedReceipt.getString("decision")));
        }
        return new GuBacklashState(session, tag.getInt("pressure"), tag.getInt("threshold"),
                tag.getLong("sequence"), tag.getString("lastEvent"), receipt,
                tag.getBoolean("quarantined"), tag.getString("quarantineReason"));
    }

    private static CompoundTag writeTransaction(Transaction transaction) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("session", transaction.sessionId());
        tag.putLong("sequence", transaction.sequence());
        tag.putString("event", transaction.eventKey());
        tag.putString("decision", transaction.decision().name());
        tag.putString("stage", transaction.stage().name());
        tag.putString("target", transaction.targetLocator());
        tag.putString("item", transaction.itemId());
        tag.putString("fingerprint", transaction.fingerprint());
        tag.putInt("countBefore", transaction.countBefore());
        tag.putInt("countAfter", transaction.countAfter());
        return tag;
    }

    private static Transaction readTransaction(CompoundTag tag) {
        if (!tag.hasUUID("session") || !tag.contains("sequence", Tag.TAG_LONG)
                || !tag.contains("event", Tag.TAG_STRING) || !tag.contains("decision", Tag.TAG_STRING)
                || !tag.contains("stage", Tag.TAG_STRING) || !tag.contains("target", Tag.TAG_STRING)
                || !tag.contains("item", Tag.TAG_STRING) || !tag.contains("fingerprint", Tag.TAG_STRING)
                || !tag.contains("countBefore", Tag.TAG_INT) || !tag.contains("countAfter", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Malformed Gu backlash transaction");
        }
        return new Transaction(tag.getUUID("session"), tag.getLong("sequence"), tag.getString("event"),
                GuBacklashState.Decision.valueOf(tag.getString("decision")), Stage.valueOf(tag.getString("stage")),
                tag.getString("target"), tag.getString("item"), tag.getString("fingerprint"),
                tag.getInt("countBefore"), tag.getInt("countAfter"));
    }
}
