import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/** Dumps a .fx NBT tree to depth 3 with key names only. */
public final class DumpFxTree {
    private DumpFxTree() {}

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        byte[] bytes = Files.readAllBytes(file);
        boolean gz = bytes.length > 2 && bytes[0] == (byte) 0x1f && bytes[1] == (byte) 0x8b;
        InputStream src = gz ? new GZIPInputStream(new ByteArrayInputStream(bytes))
                : new ByteArrayInputStream(bytes);
        CompoundTag tag = NbtIo.read(new DataInputStream(new BufferedInputStream(src)));
        walk(tag, 0);
    }

    private static void walk(CompoundTag tag, int depth) {
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            String kind = value == null ? "?" : value.getClass().getSimpleName().replace("Tag", "");
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < depth; i++) line.append("  ");
            line.append(key).append("  [").append(kind).append(']');
            if (value instanceof CompoundTag nested) {
                System.out.println(line);
                if (depth < 3) walk(nested, depth + 1);
                else System.out.println(line + " ...keys=" + nested.getAllKeys().size());
            } else {
                String s = String.valueOf(value);
                if (s.length() > 60) s = s.substring(0, 60) + "...";
                System.out.println(line + "  " + s);
            }
        }
    }
}
