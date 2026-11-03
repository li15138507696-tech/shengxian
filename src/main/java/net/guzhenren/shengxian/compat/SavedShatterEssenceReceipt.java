package net.guzhenren.shengxian.compat;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.guzhenren.shengxian.core.ShatterEssenceTransaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Durable PlayerPersisted receipt for one BREAKING true-essence debit. */
public final class SavedShatterEssenceReceipt {
    private static final String PERSISTED = "PlayerPersisted";
    private static final String KEY = "shengxian:shatter_essence";
    private static final String ATTACHMENTS = "neoforge:attachments";
    private static final String VARIABLES = "guzhenren:player_variables";
    private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;
    private static final int VERSION = 1;

    /** Consistent receipt and body value read from one independent load source. */
    public record FileState(Optional<ShatterEssenceTransaction.Receipt> receipt, double trueEssence) { }

    private SavedShatterEssenceReceipt() { }

    /** True even for a malformed namespace so corruption is never mistaken for an empty slot. */
    public static boolean present(CompoundTag persistentData) {
        if (persistentData == null || !persistentData.contains(PERSISTED)) return false;
        if (!persistentData.contains(PERSISTED, Tag.TAG_COMPOUND)) return true;
        return persistentData.getCompound(PERSISTED).contains(KEY);
    }

    /** Reads absence as empty and rejects malformed receipt data instead of dropping its evidence. */
    public static Optional<ShatterEssenceTransaction.Receipt> read(CompoundTag persistentData) throws IOException {
        if (persistentData == null || !persistentData.contains(PERSISTED)) return Optional.empty();
        if (!persistentData.contains(PERSISTED, Tag.TAG_COMPOUND)) {
            throw new IOException("PlayerPersisted data has an invalid type");
        }
        CompoundTag persisted = persistentData.getCompound(PERSISTED);
        if (!persisted.contains(KEY)) return Optional.empty();
        if (!persisted.contains(KEY, Tag.TAG_COMPOUND)) throw new IOException("Shatter receipt has an invalid type");
        CompoundTag tag = persisted.getCompound(KEY);
        try {
            if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != VERSION
                    || !tag.hasUUID("session") || !tag.contains("phaseRevision", Tag.TAG_LONG)
                    || !tag.contains("stage", Tag.TAG_STRING) || !tag.contains("target", Tag.TAG_DOUBLE)) {
                throw new IOException("Shatter receipt is incomplete or has an unsupported version");
            }
            String stageName = tag.getString("stage");
            ShatterEssenceTransaction.Stage stage = ShatterEssenceTransaction.Stage.valueOf(stageName);
            Double before = optionalDouble(tag, "before");
            Double observed = optionalDouble(tag, "observed");
            return Optional.of(new ShatterEssenceTransaction.Receipt(tag.getUUID("session"),
                    tag.getLong("phaseRevision"), stage, before, tag.getDouble("target"), observed));
        } catch (IOException malformed) {
            throw malformed;
        } catch (RuntimeException malformed) {
            throw new IOException("Shatter receipt is malformed", malformed);
        }
    }

    private static Double optionalDouble(CompoundTag tag, String name) throws IOException {
        if (!tag.contains(name)) return null;
        if (!tag.contains(name, Tag.TAG_DOUBLE)) throw new IOException("Shatter receipt has invalid " + name);
        return tag.getDouble(name);
    }

    /** Preserves other PlayerPersisted values and permits only idempotent or forward transitions. */
    public static void record(CompoundTag persistentData,
                              ShatterEssenceTransaction.Receipt receipt) throws IOException {
        if (persistentData == null || receipt == null) throw new IOException("Missing shatter receipt data");
        if (persistentData.contains(PERSISTED) && !persistentData.contains(PERSISTED, Tag.TAG_COMPOUND)) {
            throw new IOException("PlayerPersisted data has an invalid type");
        }
        CompoundTag persisted = persistentData.getCompound(PERSISTED);
        Optional<ShatterEssenceTransaction.Receipt> previous = read(persistentData);
        if (previous.isPresent()) requireForwardTransition(previous.get(), receipt);
        else if (persisted.contains(KEY)) throw new IOException("Malformed shatter receipt is already present");
        else if (receipt.stage() == ShatterEssenceTransaction.Stage.COMMITTED) {
            throw new IOException("COMMITTED requires a matching PREPARED receipt");
        }

        CompoundTag saved = new CompoundTag();
        saved.putInt("version", VERSION);
        saved.putUUID("session", receipt.sessionId());
        saved.putLong("phaseRevision", receipt.phaseRevision());
        saved.putString("stage", receipt.stage().name());
        saved.putDouble("target", receipt.target());
        if (receipt.before() != null) saved.putDouble("before", receipt.before());
        if (receipt.observed() != null) saved.putDouble("observed", receipt.observed());
        persisted.put(KEY, saved);
        persistentData.put(PERSISTED, persisted);
    }

    private static void requireForwardTransition(ShatterEssenceTransaction.Receipt previous,
                                                ShatterEssenceTransaction.Receipt next) throws IOException {
        if (previous.equals(next)) return;
        if (!sameIntent(previous, next)) throw new IOException("A different shatter essence intent is already present");
        if (previous.stage() == ShatterEssenceTransaction.Stage.UNCERTAIN) {
            throw new IOException("An uncertain shatter receipt cannot resume automatically");
        }
        if (next.stage() == ShatterEssenceTransaction.Stage.UNCERTAIN) return;
        if (previous.stage() == ShatterEssenceTransaction.Stage.PREPARED
                && next.stage() == ShatterEssenceTransaction.Stage.COMMITTED) return;
        throw new IOException("Shatter receipt stage cannot move backwards or be replaced");
    }

    private static boolean sameIntent(ShatterEssenceTransaction.Receipt first,
                                      ShatterEssenceTransaction.Receipt second) {
        return first.sessionId().equals(second.sessionId())
                && first.phaseRevision() == second.phaseRevision()
                && first.target() == second.target()
                && first.before() != null && first.before().equals(second.before());
    }

    /** Immediate save verification checks identity, receipt and exact upstream zhenyuan value. */
    public static void verify(Path file, UUID owner, ShatterEssenceTransaction.Receipt expected,
                              double expectedEssence) throws IOException {
        verifyFile(file, owner, expected, expectedEssence, false, true);
    }

    /** Singleplayer owners also load level.dat/Data/Player. */
    public static void verifyHost(Path file, UUID owner, ShatterEssenceTransaction.Receipt expected,
                                  double expectedEssence) throws IOException {
        verifyFile(file, owner, expected, expectedEssence, true, true);
    }

    /** Recovery verification checks receipt identity only; COMMITTED must not clamp later gameplay changes. */
    public static void verifyState(Path file, UUID owner,
                                   ShatterEssenceTransaction.Receipt expected) throws IOException {
        verifyFile(file, owner, expected, Double.NaN, false, false);
    }

    public static void verifyHostState(Path file, UUID owner,
                                       ShatterEssenceTransaction.Receipt expected) throws IOException {
        verifyFile(file, owner, expected, Double.NaN, true, false);
    }

    /** Reads one real load source before an overwrite, including its current receipt slot and zhenyuan. */
    public static FileState readState(Path file, UUID owner, boolean host) throws IOException {
        if (file == null || owner == null) throw new IOException("Invalid shatter source identity");
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
                throw new IOException("Saved player identity does not match shatter owner");
            }
            Optional<ShatterEssenceTransaction.Receipt> receipt = Optional.empty();
            if (root.contains("NeoForgeData")) {
                if (!root.contains("NeoForgeData", Tag.TAG_COMPOUND)) {
                    throw new IOException("Saved NeoForge player data has an invalid type");
                }
                receipt = read(root.getCompound("NeoForgeData"));
            }
            if (!root.contains(ATTACHMENTS, Tag.TAG_COMPOUND)
                    || !root.getCompound(ATTACHMENTS).contains(VARIABLES, Tag.TAG_COMPOUND)) {
                throw new IOException("Saved upstream player variables are missing");
            }
            CompoundTag variables = root.getCompound(ATTACHMENTS).getCompound(VARIABLES);
            if (!variables.contains("zhenyuan", Tag.TAG_DOUBLE)) {
                throw new IOException("Saved upstream true essence is missing");
            }
            double trueEssence = variables.getDouble("zhenyuan");
            if (!Double.isFinite(trueEssence) || trueEssence < 0
                    || trueEssence > AscensionProgress.MAX_QI) {
                throw new IOException("Saved upstream true essence is invalid");
            }
            return new FileState(receipt, trueEssence);
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException malformed) {
            throw new IOException("Saved shatter source could not be read", malformed);
        }
    }

    private static void verifyFile(Path file, UUID owner, ShatterEssenceTransaction.Receipt expected,
                                   double expectedEssence, boolean host, boolean verifyEssence) throws IOException {
        if (file == null || owner == null || expected == null) throw new IOException("Invalid shatter receipt identity");
        if (verifyEssence && (!Double.isFinite(expectedEssence) || expectedEssence < 0
                || expectedEssence > AscensionProgress.MAX_QI)) {
            throw new IOException("Invalid expected true essence");
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
                throw new IOException("Saved player identity does not match shatter owner");
            }
            if (!root.contains("NeoForgeData", Tag.TAG_COMPOUND)
                    || read(root.getCompound("NeoForgeData")).filter(expected::equals).isEmpty()) {
                throw new IOException("Saved player shatter receipt does not match session, phase or stage");
            }
            if (verifyEssence) {
                if (!root.contains(ATTACHMENTS, Tag.TAG_COMPOUND)
                        || !root.getCompound(ATTACHMENTS).contains(VARIABLES, Tag.TAG_COMPOUND)) {
                    throw new IOException("Saved upstream player variables are missing");
                }
                CompoundTag variables = root.getCompound(ATTACHMENTS).getCompound(VARIABLES);
                if (!variables.contains("zhenyuan", Tag.TAG_DOUBLE)) {
                    throw new IOException("Saved upstream true essence is missing");
                }
                double savedEssence = variables.getDouble("zhenyuan");
                if (!Double.isFinite(savedEssence) || savedEssence < 0
                        || Double.compare(savedEssence, expectedEssence) != 0) {
                    throw new IOException("Saved upstream true essence does not match the receipt stage");
                }
            }
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException malformed) {
            throw new IOException("Saved shatter receipt could not be verified", malformed);
        }
    }
}
