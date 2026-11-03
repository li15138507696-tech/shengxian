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

/** Dumps first fxObject tree to depth 6. */
public final class DumpFxTree2 {
    private DumpFxTree2() {}

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        byte[] bytes = Files.readAllBytes(file);
        InputStream src = new GZIPInputStream(new ByteArrayInputStream(bytes));
        CompoundTag root = NbtIo.read(new DataInputStream(new BufferedInputStream(src)));
        CompoundTag data = root.getCompound("fxData").getCompound("data");
        var objects = data.getList("fxObjects", 10);
        System.out.println("objects=" + objects.size());
        for (int i = 0; i < Math.min(2, objects.size()); i++) {
            walk(objects.getCompound(i), 0);
            System.out.println("==== end object " + i + " ====");
        }
    }

    private static void walk(CompoundTag tag, int depth) {
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < depth; i++) line.append("  ");
            line.append(key);
            if (value instanceof CompoundTag nested) {
                String type = nested.contains("type") ? nested.getString("type") : "";
                System.out.println(line + (type.isEmpty() ? "" : "  (" + type + ")"));
                if (depth < 5) walk(nested, depth + 1);
            } else {
                String s = String.valueOf(value);
                if (s.length() > 70) s = s.substring(0, 70) + "...";
                System.out.println(line + " = " + s);
            }
        }
    }
}
