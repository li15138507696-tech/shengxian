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

public final class DumpFxTree3 {
    private DumpFxTree3() {}

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        byte[] bytes = Files.readAllBytes(file);
        InputStream src = new GZIPInputStream(new ByteArrayInputStream(bytes));
        CompoundTag root = NbtIo.read(new DataInputStream(new BufferedInputStream(src)));
        CompoundTag fxData = root.getCompound("fxData");
        System.out.println("fxData keys=" + fxData.getAllKeys());
        for (String key : fxData.getAllKeys()) {
            Tag t = fxData.get(key);
            System.out.println("  " + key + " -> " + t.getClass().getSimpleName());
        }
    }
}
