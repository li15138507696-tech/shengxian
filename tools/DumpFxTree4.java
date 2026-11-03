import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

public final class DumpFxTree4 {
    private DumpFxTree4() {}

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        InputStream src = new GZIPInputStream(new ByteArrayInputStream(Files.readAllBytes(file)));
        CompoundTag root = NbtIo.read(new DataInputStream(new BufferedInputStream(src)));
        ListTag objects = root.getList("fxObjects", Tag.TAG_COMPOUND);
        if (objects.isEmpty()) objects = root.getCompound("fxData").getList("fxObjects", Tag.TAG_COMPOUND);
        System.out.println("objects=" + objects.size());
        for (int i = 0; i < Math.min(1, objects.size()); i++) {
            CompoundTag obj = objects.getCompound(i);
            System.out.println("obj" + i + " keys=" + obj.getAllKeys());
            walk(obj, 0);
        }
    }

    private static void walk(CompoundTag tag, int depth) {
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < depth; i++) line.append("  ");
            line.append(key);
            if (value instanceof CompoundTag nested) {
                String type = nested.contains("type") ? " (" + nested.getString("type") + ")" : "";
                System.out.println(line + type);
                if (depth < 4) walk(nested, depth + 1);
            } else if (value instanceof ListTag list) {
                System.out.println(line + "  [list " + list.size() + "]");
            } else {
                String s = String.valueOf(value);
                if (s.length() > 60) s = s.substring(0, 60) + "...";
                System.out.println(line + " = " + s);
            }
        }
    }
}
