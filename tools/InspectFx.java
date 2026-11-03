import com.lowdragmc.photon.client.fx.FX;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Offline inspector: reads a .fx file (gzip NBT), prints its emitter skeleton and,
 * if Photon classes are present, round-trips it through FX to prove it parses.
 */
public final class InspectFx {
    private InspectFx() {}

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        byte[] bytes = Files.readAllBytes(file);
        boolean gzipped = bytes.length > 2 && bytes[0] == (byte) 0x1f && bytes[1] == (byte) 0x8b;
        InputStream source = gzipped ? new GZIPInputStream(new ByteArrayInputStream(bytes))
                : new ByteArrayInputStream(bytes);
        DataInputStream in = new DataInputStream(new BufferedInputStream(source));
        CompoundTag tag = NbtIo.read(in);
        System.out.println("root keys: " + tag.getAllKeys());
        CompoundTag data = tag.getCompound("fxData");
        System.out.println("fxData keys: " + data.getAllKeys());
        System.out.println("objects: " + data.getCompound("data").getList("fxObjects", 10).size());
        try {
            FX fx = new FX();
            fx.deserializeNBT(null, tag);
            System.out.println("FX parse OK, location=" + fx.getFxLocation()
                    + " runtime objects=" + fx.getFxData().objects().size());
        } catch (Throwable t) {
            System.out.println("FX parse failed: " + t);
        }
    }
}
