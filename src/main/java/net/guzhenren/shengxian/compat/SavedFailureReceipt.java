package net.guzhenren.shengxian.compat;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Durable player-side receipt for the single attempted failure settlement. */
public final class SavedFailureReceipt {
    private static final String PERSISTED = "PlayerPersisted";
    private static final String KEY = "shengxian:failure";
    private static final String ATTACHMENTS = "neoforge:attachments";
    private static final String VARIABLES = "guzhenren:player_variables";
    private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;
    private static final Set<AscensionProgress.FailureCause> ALLOWED_CAUSES = Set.of(
            AscensionProgress.FailureCause.HUMAN_OVERLOAD,
            AscensionProgress.FailureCause.HEAVEN_OVERLOAD,
            AscensionProgress.FailureCause.EARTH_OVERLOAD);

    public enum Stage { STARTED, RESPAWN_RECEIPT }
    public record Receipt(UUID operationId, AscensionProgress.FailureCause cause, Stage stage) {}

    private SavedFailureReceipt() {}

    public static Optional<Receipt> read(CompoundTag persistentData) {
        if (persistentData == null || !persistentData.contains(PERSISTED, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag persisted = persistentData.getCompound(PERSISTED);
        if (!persisted.contains(KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag tag = persisted.getCompound(KEY);
        try {
            UUID operationId = tag.getUUID("operation");
            AscensionProgress.FailureCause cause = AscensionProgress.FailureCause.valueOf(tag.getString("cause"));
            Stage stage = Stage.valueOf(tag.getString("stage"));
            if (!ALLOWED_CAUSES.contains(cause)) return Optional.empty();
            return Optional.of(new Receipt(operationId, cause, stage));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    public static boolean present(CompoundTag persistentData) {
        if (persistentData == null || !persistentData.contains(PERSISTED)) return false;
        if (!persistentData.contains(PERSISTED, Tag.TAG_COMPOUND)) return true;
        return persistentData.getCompound(PERSISTED).contains(KEY);
    }

    /** Preserves unrelated PlayerPersisted data and refuses identity changes or stage rollback. */
    public static void record(CompoundTag persistentData, UUID operationId,
                              AscensionProgress.FailureCause cause, Stage stage) throws IOException {
        requireIdentity(operationId, cause, stage);
        if (persistentData.contains(PERSISTED) && !persistentData.contains(PERSISTED, Tag.TAG_COMPOUND)) {
            throw new IOException("PlayerPersisted data has an invalid type");
        }
        CompoundTag persisted = persistentData.getCompound(PERSISTED);
        Optional<Receipt> prior = read(persistentData);
        if (prior.isPresent()) {
            Receipt receipt = prior.get();
            if (!receipt.operationId().equals(operationId) || receipt.cause() != cause) {
                throw new IOException("A different failure settlement receipt is already present");
            }
            if (receipt.stage() == Stage.RESPAWN_RECEIPT && stage == Stage.STARTED) return;
        } else if (persisted.contains(KEY)) {
            throw new IOException("Malformed failure receipt is already present");
        } else if (stage != Stage.STARTED) {
            throw new IOException("RESPAWN_RECEIPT requires a matching STARTED receipt");
        }
        CompoundTag receipt = new CompoundTag();
        receipt.putUUID("operation", operationId);
        receipt.putString("cause", cause.name());
        receipt.putString("stage", stage.name());
        persisted.put(KEY, receipt);
        persistentData.put(PERSISTED, persisted);
    }

    public static void verify(Path file, UUID owner, UUID operationId,
                              AscensionProgress.FailureCause cause, Stage stage) throws IOException {
        verifyFile(file, owner, operationId, cause, stage, false);
    }

    public static void verifyHost(Path file, UUID owner, UUID operationId,
                                  AscensionProgress.FailureCause cause, Stage stage) throws IOException {
        verifyFile(file, owner, operationId, cause, stage, true);
    }

    private static void verifyFile(Path file, UUID owner, UUID operationId,
                                   AscensionProgress.FailureCause cause, Stage stage,
                                   boolean host) throws IOException {
        requireIdentity(operationId, cause, stage);
        if (owner == null) throw new IOException("Failure receipt owner is missing");
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
                throw new IOException("Saved player identity does not match failure owner");
            }
            if (!root.contains("NeoForgeData", Tag.TAG_COMPOUND)) {
                throw new IOException("Saved NeoForge player data is missing");
            }
            Receipt expected = new Receipt(operationId, cause, stage);
            if (read(root.getCompound("NeoForgeData")).filter(expected::equals).isEmpty()) {
                throw new IOException("Saved player failure receipt does not match expected identity or stage");
            }
            if (!root.contains(ATTACHMENTS, Tag.TAG_COMPOUND)
                    || !root.getCompound(ATTACHMENTS).contains(VARIABLES, Tag.TAG_COMPOUND)) {
                throw new IOException("Saved upstream player variables are missing");
            }
            CompoundTag variables = root.getCompound(ATTACHMENTS).getCompound(VARIABLES);
            if (!variables.contains("shiqiao", Tag.TAG_BYTE) || !variables.getBoolean("shiqiao")) {
                throw new IOException("Saved aperture-shattered flag does not match failure receipt");
            }
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
        } catch (RuntimeException malformed) {
            throw new IOException("Saved player failure receipt could not be verified", malformed);
        }
    }

    private static void requireIdentity(UUID operationId, AscensionProgress.FailureCause cause, Stage stage)
            throws IOException {
        if (operationId == null || cause == null || stage == null || !ALLOWED_CAUSES.contains(cause)) {
            throw new IOException("Invalid failure receipt identity");
        }
    }
}
