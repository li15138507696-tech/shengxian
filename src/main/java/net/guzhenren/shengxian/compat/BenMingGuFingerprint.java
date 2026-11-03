package net.guzhenren.shengxian.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeSet;

/** Content fingerprints for matching stacks; they do not identify a unique Gu instance. */
public final class BenMingGuFingerprint {
    private BenMingGuFingerprint() {}

    /**
     * Hashes the registry-aware persistent item representation after normalizing count and
     * DAMAGE. Compound keys are sorted recursively; list order and NBT types are preserved.
     * Non-persistent transient components cannot be included in this stored fingerprint.
     */
    public static String of(ItemStack stack, HolderLookup.Provider registries) {
        requireStack(stack);
        Objects.requireNonNull(registries, "registries");
        ItemStack normalized = stack.copyWithCount(1);
        normalized.remove(DataComponents.DAMAGE);
        Tag encoded = normalized.save(registries);

        MessageDigest digest = newDigest();
        try (DataOutputStream out = new DataOutputStream(
                new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            writeString(out, "shengxian:benminggu:nbt-v2");
            writeTag(out, encoded);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot hash BenMingGu item data", ex);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Original full-component fingerprint, retained only for existing records. */
    public static String legacyOf(ItemStack stack) {
        requireStack(stack);
        String payload = BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + stack.getComponentsPatch();
        return HexFormat.of().formatHex(newDigest().digest(payload.getBytes(StandardCharsets.UTF_8)));
    }

    /** Exact live comparison of the item ID and every patch component except DAMAGE. */
    public static boolean sameExceptDamage(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) return false;
        var firstId = BuiltInRegistries.ITEM.getKey(first.getItem());
        var secondId = BuiltInRegistries.ITEM.getKey(second.getItem());
        return firstId != null && firstId.equals(secondId)
                && withoutDamage(first).equals(withoutDamage(second));
    }

    private static DataComponentPatch withoutDamage(ItemStack stack) {
        return stack.getComponentsPatch().forget(type -> type == DataComponents.DAMAGE);
    }

    private static void writeTag(DataOutputStream out, Tag tag) throws IOException {
        Objects.requireNonNull(tag, "tag");
        out.writeByte(tag.getId());
        switch (tag.getId()) {
            case Tag.TAG_END -> { }
            case Tag.TAG_BYTE -> out.writeByte(((NumericTag) tag).getAsByte());
            case Tag.TAG_SHORT -> out.writeShort(((NumericTag) tag).getAsShort());
            case Tag.TAG_INT -> out.writeInt(((NumericTag) tag).getAsInt());
            case Tag.TAG_LONG -> out.writeLong(((NumericTag) tag).getAsLong());
            case Tag.TAG_FLOAT -> out.writeFloat(((NumericTag) tag).getAsFloat());
            case Tag.TAG_DOUBLE -> out.writeDouble(((NumericTag) tag).getAsDouble());
            case Tag.TAG_BYTE_ARRAY -> {
                byte[] values = ((ByteArrayTag) tag).getAsByteArray();
                out.writeInt(values.length);
                out.write(values);
            }
            case Tag.TAG_STRING -> writeString(out, tag.getAsString());
            case Tag.TAG_LIST -> {
                ListTag values = (ListTag) tag;
                out.writeByte(values.getElementType());
                out.writeInt(values.size());
                for (int i = 0; i < values.size(); i++) writeTag(out, values.get(i));
            }
            case Tag.TAG_COMPOUND -> {
                CompoundTag values = (CompoundTag) tag;
                var keys = new TreeSet<>(values.getAllKeys());
                out.writeInt(keys.size());
                for (String key : keys) {
                    writeString(out, key);
                    writeTag(out, values.get(key));
                }
            }
            case Tag.TAG_INT_ARRAY -> {
                int[] values = ((IntArrayTag) tag).getAsIntArray();
                out.writeInt(values.length);
                for (int value : values) out.writeInt(value);
            }
            case Tag.TAG_LONG_ARRAY -> {
                long[] values = ((LongArrayTag) tag).getAsLongArray();
                out.writeInt(values.length);
                for (long value : values) out.writeLong(value);
            }
            default -> throw new IllegalArgumentException("Unknown BenMingGu NBT type: " + tag.getId());
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void requireStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Empty BenMingGu");
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError("SHA-256 unavailable", ex);
        }
    }
}