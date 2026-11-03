package net.guzhenren.shengxian.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.guzhenren.shengxian.core.XianGuRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;

/** Participant receipt co-saved with actual health, harmful effects or destination position. */
public final class SavedXianGuUseReceipt {
    private static final String KEY = "shengxian:immortal_gu_use";
    public static final String LAST_HEAL_KEY = "shengxian:last_immortal_heal";
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    public enum Stage { PREPARED, COMMITTED, UNCERTAIN, ABORTED, ACKNOWLEDGED }

    public record Receipt(UUID caster, UUID session, UUID operation, long sequence, long tick,
                           String gu, Stage stage, String payload) {
        public Receipt {
            Objects.requireNonNull(caster); Objects.requireNonNull(session); Objects.requireNonNull(operation);
            Objects.requireNonNull(stage);
            if (sequence < 1 || tick < 0 || !XianGuRuntime.IDS.contains(gu)
                    || payload == null || payload.isBlank() || payload.length() > XianGuRuntime.MAX_PAYLOAD) {
                throw new IllegalArgumentException("Invalid reusable Gu receipt");
            }
            JsonObject plan = JsonParser.parseString(payload).getAsJsonObject();
            String expected = gu.equals("ding_xian_you") ? "travel"
                    : gu.equals("ren_ru_gu_immortal") ? "health" : "landscape";
            if (!plan.has("kind") || !expected.equals(plan.get("kind").getAsString())) {
                throw new IllegalArgumentException("Gu and effect plan disagree");
            }
        }
        public Receipt withStage(Stage next) {
            return new Receipt(caster, session, operation, sequence, tick, gu, next, payload);
        }
        public boolean sameOperation(Receipt other) { return other != null && withStage(other.stage()).equals(other); }
        public boolean terminal() { return stage == Stage.COMMITTED || stage == Stage.ABORTED || stage == Stage.ACKNOWLEDGED; }
        public boolean matchesWorld(UUID sessionId, XianGuRuntime.Use use) {
            return use != null && session.equals(sessionId) && operation.equals(use.operationId())
                    && sequence == use.sequence() && tick == use.tick() && gu.equals(use.guId()) && payload.equals(use.payload());
        }
    }

    public record Source(Path file, UUID owner, Optional<Receipt> previous, boolean host) { }

    private SavedXianGuUseReceipt() { }

    public static Optional<Receipt> read(CompoundTag persistentData) throws IOException {
        if (persistentData == null || !persistentData.contains("PlayerPersisted")) return Optional.empty();
        if (!persistentData.contains("PlayerPersisted", Tag.TAG_COMPOUND)) throw new IOException("Invalid PlayerPersisted type");
        CompoundTag persisted = persistentData.getCompound("PlayerPersisted");
        if (!persisted.contains(KEY)) return Optional.empty();
        if (!persisted.contains(KEY, Tag.TAG_COMPOUND)) throw new IOException("Invalid immortal Gu receipt type");
        CompoundTag tag = persisted.getCompound(KEY);
        try {
            int version = tag.getInt("version");
            if (!tag.contains("version", Tag.TAG_INT) || version < 1 || version > 2 || !tag.hasUUID("caster")
                    || !tag.hasUUID("session") || !tag.hasUUID("operation") || !tag.contains("sequence", Tag.TAG_LONG)
                    || !tag.contains("tick", Tag.TAG_LONG) || !tag.contains("gu", Tag.TAG_STRING)
                    || !tag.contains("stage", Tag.TAG_STRING)
                    || !tag.contains("payload", version == 1 ? Tag.TAG_STRING : Tag.TAG_LIST)) {
                throw new IOException("Incomplete or unsupported immortal Gu receipt");
            }
            String payload;
            if (version == 1) payload = tag.getString("payload");
            else {
                ListTag parts = tag.getList("payload", Tag.TAG_STRING);
                if (parts.isEmpty() || parts.size() > 32) throw new IOException("Invalid Gu payload chunks");
                StringBuilder joined = new StringBuilder();
                for (int i = 0; i < parts.size(); i++) {
                    String part = parts.getString(i);
                    if (part.isEmpty() || part.length() > 8192) throw new IOException("Invalid Gu payload chunk length");
                    joined.append(part);
                }
                payload = joined.toString();
            }
            return Optional.of(new Receipt(tag.getUUID("caster"), tag.getUUID("session"), tag.getUUID("operation"),
                    tag.getLong("sequence"), tag.getLong("tick"), tag.getString("gu"),
                    Stage.valueOf(tag.getString("stage")), payload));
        } catch (RuntimeException malformed) { throw new IOException("Malformed immortal Gu receipt", malformed); }
    }

    /** New operations require a prior terminal receipt; no active operation may be silently replaced. */
    public static void record(CompoundTag persistentData, Receipt next, boolean allowReplacement) throws IOException {
        Optional<Receipt> previous = read(persistentData);
        if (previous.isPresent()) {
            Receipt prior = previous.get();
            if (prior.equals(next)) return;
            if (prior.sameOperation(next)) {
                boolean allowed = prior.stage() == Stage.PREPARED && (next.stage() == Stage.COMMITTED
                        || next.stage() == Stage.UNCERTAIN || next.stage() == Stage.ABORTED)
                        || allowReplacement && next.stage() == Stage.ACKNOWLEDGED;
                if (!allowed) throw new IOException("Gu receipt stage cannot roll back");
            } else if (!allowReplacement || !prior.terminal() || next.stage() != Stage.PREPARED) {
                throw new IOException("A different unfinished immortal Gu receipt is present");
            }
        } else if (next.stage() != Stage.PREPARED) throw new IOException("Gu receipt must start PREPARED");
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 2); tag.putUUID("caster", next.caster()); tag.putUUID("session", next.session());
        tag.putUUID("operation", next.operation()); tag.putLong("sequence", next.sequence()); tag.putLong("tick", next.tick());
        tag.putString("gu", next.gu()); tag.putString("stage", next.stage().name());
        ListTag parts = new ListTag();
        for (int start = 0; start < next.payload().length(); start += 8192) {
            parts.add(StringTag.valueOf(next.payload().substring(start, Math.min(start + 8192, next.payload().length()))));
        }
        tag.put("payload", parts);
        CompoundTag persisted = persistentData.getCompound("PlayerPersisted");
        persisted.put(KEY, tag); persistentData.put("PlayerPersisted", persisted);
    }

    public static void recordParticipant(CompoundTag persistentData, UUID owner, Receipt next, boolean replacement) throws IOException {
        record(persistentData, next, replacement);
        if (next.stage() == Stage.COMMITTED && next.gu().equals("ren_ru_gu_immortal")
                && owner.toString().equals(JsonParser.parseString(next.payload()).getAsJsonObject().get("target").getAsString())) {
            CompoundTag persisted = persistentData.getCompound("PlayerPersisted");
            persisted.putLong(LAST_HEAL_KEY, next.tick());
            persistentData.put("PlayerPersisted", persisted);
        }
    }

    public static void verifySource(Path file, UUID owner, Optional<Receipt> previous, boolean host) throws IOException {
        CompoundTag root = playerRoot(file, owner, host);
        if (!readPersistent(root).equals(previous)) throw new IOException("Saved Gu receipt differs from the live previous receipt");
    }
    public static void verifySources(java.util.List<Source> sources) throws IOException {
        for (Source source : sources) verifySource(source.file(), source.owner(), source.previous(), source.host());
    }
    public static void verify(Path file, UUID owner, Receipt expected, boolean effect) throws IOException {
        verifyFile(file, owner, expected, effect, false);
    }
    public static void verifyHost(Path file, UUID owner, Receipt expected, boolean effect) throws IOException {
        verifyFile(file, owner, expected, effect, true);
    }
    private static void verifyFile(Path file, UUID owner, Receipt expected, boolean effect, boolean host) throws IOException {
        CompoundTag root = playerRoot(file, owner, host);
        if (readPersistent(root).filter(expected::equals).isEmpty()) throw new IOException("Saved Gu receipt identity or stage differs");
        if (effect) {
            if (expected.stage() != Stage.COMMITTED) throw new IOException("Only COMMITTED can confirm a Gu effect");
            verifyEffect(root, owner, expected);
        }
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.force(true); }
    }

    private static void verifyEffect(CompoundTag root, UUID owner, Receipt receipt) throws IOException {
        try {
            JsonObject plan = JsonParser.parseString(receipt.payload()).getAsJsonObject();
            if (receipt.gu().equals("ren_ru_gu_immortal") && UUID.fromString(plan.get("target").getAsString()).equals(owner)) {
                JsonObject after = plan.getAsJsonObject("after");
                CompoundTag persisted = root.getCompound("NeoForgeData").getCompound("PlayerPersisted");
                if (!persisted.contains(LAST_HEAL_KEY, Tag.TAG_LONG) || persisted.getLong(LAST_HEAL_KEY) != receipt.tick()) {
                    throw new IOException("Saved treatment interval differs");
                }
                float health = after.get("health").getAsFloat();
                if (!Float.isFinite(health) || !root.contains("Health", Tag.TAG_FLOAT)
                        || Float.compare(root.getFloat("Health"), health) != 0) throw new IOException("Saved restored health differs");
                if (after.get("extinguish").getAsBoolean()
                        && (!root.contains("Fire", Tag.TAG_ANY_NUMERIC) || root.getShort("Fire") > 0)) {
                    throw new IOException("Saved burning state was not cleared");
                }
                if (root.contains("active_effects") && !root.contains("active_effects", Tag.TAG_LIST)) {
                    throw new IOException("Saved harmful-effect list is malformed");
                }
                ListTag effects = root.getList("active_effects", Tag.TAG_COMPOUND);
                for (Tag tag : effects) {
                    String id = ((CompoundTag) tag).getString("id");
                    if (after.get("clearPoison").getAsBoolean() && id.equals("minecraft:poison")
                            || after.get("clearWither").getAsBoolean() && id.equals("minecraft:wither")) {
                        throw new IOException("Saved recent harmful effect was not cleared");
                    }
                }
            } else if (receipt.gu().equals("ding_xian_you") && receipt.caster().equals(owner)) {
                if (!root.contains("Dimension", Tag.TAG_STRING)
                        || !root.getString("Dimension").equals(plan.get("dimension").getAsString())) {
                    throw new IOException("Saved travel dimension differs");
                }
                ListTag position = root.getList("Pos", Tag.TAG_DOUBLE);
                if (position.size() != 3) throw new IOException("Saved travel position is missing");
                String[] keys = {"x", "y", "z"};
                for (int i = 0; i < 3; i++) {
                    double value = position.getDouble(i), expected = plan.get(keys[i]).getAsDouble();
                    if (!Double.isFinite(value) || !Double.isFinite(expected) || Math.abs(value - expected) > 0.00001) {
                        throw new IOException("Saved travel coordinate differs");
                    }
                }
                if (!root.contains("FallDistance", Tag.TAG_FLOAT) || root.getFloat("FallDistance") != 0) {
                    throw new IOException("Saved travel fall distance was not reset");
                }
            }
        } catch (RuntimeException malformed) { throw new IOException("Invalid Gu effect verification plan", malformed); }
    }

    private static CompoundTag playerRoot(Path file, UUID owner, boolean host) throws IOException {
        if (file == null || owner == null) throw new IOException("Missing saved Gu source identity");
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(MAX_BYTES));
            if (host) {
                if (!root.contains("Data", Tag.TAG_COMPOUND)
                        || !root.getCompound("Data").contains("Player", Tag.TAG_COMPOUND)) throw new IOException("Missing host player data");
                root = root.getCompound("Data").getCompound("Player");
            }
            if (!root.hasUUID("UUID") || !root.getUUID("UUID").equals(owner)) throw new IOException("Saved Gu participant identity differs");
            return root;
        } catch (RuntimeException malformed) { throw new IOException("Unreadable saved Gu participant", malformed); }
    }
    private static Optional<Receipt> readPersistent(CompoundTag root) throws IOException {
        if (root.contains("NeoForgeData") && !root.contains("NeoForgeData", Tag.TAG_COMPOUND)) {
            throw new IOException("Malformed NeoForgeData");
        }
        return read(root.getCompound("NeoForgeData"));
    }
}
