package net.guzhenren.shengxian.compat;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.guzhenren.shengxian.core.AscensionProgress;
import net.guzhenren.shengxian.core.TribulationParameters;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Player-side durable receipt dedicated to a witnessed tribulation impact operation. */
public final class SavedTribulationImpactReceipt {
    private static final String PERSISTED = "PlayerPersisted";
    private static final String KEY = "shengxian:tribulation_impact";
    private static final String ATTACHMENTS = "neoforge:attachments";
    private static final String VARIABLES = "guzhenren:player_variables";
    private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;

    public enum Stage { STARTED, RESPAWN_RECEIPT }

    public record Receipt(UUID operationId, TribulationParameters.Profile profile,
                          AscensionProgress.Tribulation.Type carrierType, String carrierId,
                          long impactSequence, Stage stage) {
        public Receipt {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(stage, "stage");
            new AscensionProgress.TribulationImpactFailure(profile, carrierType, carrierId, impactSequence);
        }

        public AscensionProgress.TribulationImpactFailure context() {
            return new AscensionProgress.TribulationImpactFailure(profile, carrierType, carrierId, impactSequence);
        }
    }

    private SavedTribulationImpactReceipt() {}

    public static Optional<Receipt> read(CompoundTag persistentData) {
        if (persistentData == null || !persistentData.contains(PERSISTED, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag persisted = persistentData.getCompound(PERSISTED);
        if (!persisted.contains(KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag tag = persisted.getCompound(KEY);
        try {
            return Optional.of(new Receipt(tag.getUUID("operation"),
                    TribulationParameters.Profile.valueOf(tag.getString("profile")),
                    AscensionProgress.Tribulation.Type.valueOf(tag.getString("carrierType")),
                    tag.getString("carrierId"), tag.getLong("impactSequence"),
                    Stage.valueOf(tag.getString("stage"))));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    public static boolean present(CompoundTag persistentData) {
        if (persistentData == null || !persistentData.contains(PERSISTED)) return false;
        if (!persistentData.contains(PERSISTED, Tag.TAG_COMPOUND)) return true;
        return persistentData.getCompound(PERSISTED).contains(KEY);
    }

    /** Preserves unrelated PlayerPersisted values and refuses another source or operation. */
    public static void record(CompoundTag persistentData, Receipt receipt) throws IOException {
        requireIdentity(receipt);
        if (persistentData.contains(PERSISTED) && !persistentData.contains(PERSISTED, Tag.TAG_COMPOUND)) {
            throw new IOException("PlayerPersisted data has an invalid type");
        }
        CompoundTag persisted = persistentData.getCompound(PERSISTED);
        Optional<Receipt> prior = read(persistentData);
        if (prior.isPresent()) {
            Receipt existing = prior.get();
            if (!sameIdentity(existing, receipt)) {
                throw new IOException("A different impact receipt is already present");
            }
            if (existing.stage() == Stage.RESPAWN_RECEIPT && receipt.stage() == Stage.STARTED) return;
        } else if (persisted.contains(KEY)) {
            throw new IOException("Malformed impact receipt is already present");
        } else if (receipt.stage() != Stage.STARTED) {
            throw new IOException("RESPAWN_RECEIPT requires a matching STARTED receipt");
        }
        CompoundTag tag = new CompoundTag();
        tag.putUUID("operation", receipt.operationId());
        tag.putString("profile", receipt.profile().name());
        tag.putString("carrierType", receipt.carrierType().name());
        tag.putString("carrierId", receipt.carrierId());
        tag.putLong("impactSequence", receipt.impactSequence());
        tag.putString("stage", receipt.stage().name());
        persisted.put(KEY, tag);
        persistentData.put(PERSISTED, persisted);
    }

    public static void verify(Path file, UUID owner, Receipt expected) throws IOException {
        verifyFile(file, owner, expected, false);
    }

    public static void verifyHost(Path file, UUID owner, Receipt expected) throws IOException {
        verifyFile(file, owner, expected, true);
    }

    private static void verifyFile(Path file, UUID owner, Receipt expected, boolean host) throws IOException {
        requireIdentity(expected);
        if (owner == null) throw new IOException("Impact receipt owner is missing");
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
                throw new IOException("Saved player identity does not match impact owner");
            }
            if (!root.contains("NeoForgeData", Tag.TAG_COMPOUND)) {
                throw new IOException("Saved NeoForge player data is missing");
            }
            if (read(root.getCompound("NeoForgeData")).filter(expected::equals).isEmpty()) {
                throw new IOException("Saved impact receipt does not match operation or source");
            }
            if (!root.contains(ATTACHMENTS, Tag.TAG_COMPOUND)
                    || !root.getCompound(ATTACHMENTS).contains(VARIABLES, Tag.TAG_COMPOUND)) {
                throw new IOException("Saved upstream player variables are missing");
            }
            CompoundTag variables = root.getCompound(ATTACHMENTS).getCompound(VARIABLES);
            if (!variables.contains("shiqiao", Tag.TAG_BYTE) || !variables.getBoolean("shiqiao")) {
                throw new IOException("Saved aperture state does not match impact receipt");
            }
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
        } catch (RuntimeException malformed) {
            throw new IOException("Saved impact receipt could not be verified", malformed);
        }
    }

    private static boolean sameIdentity(Receipt first, Receipt second) {
        return first.operationId().equals(second.operationId())
                && first.profile() == second.profile()
                && first.carrierType() == second.carrierType()
                && first.carrierId().equals(second.carrierId())
                && first.impactSequence() == second.impactSequence();
    }

    private static void requireIdentity(Receipt receipt) throws IOException {
        if (receipt == null || receipt.operationId() == null || receipt.stage() == null) {
            throw new IOException("Invalid impact receipt identity");
        }
        try {
            receipt.context();
        } catch (RuntimeException invalidContext) {
            throw new IOException("Invalid witnessed impact source", invalidContext);
        }
    }
}
