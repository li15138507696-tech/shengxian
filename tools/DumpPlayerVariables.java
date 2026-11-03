import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.nio.file.Path;
import java.util.TreeSet;

/** Offline dump of one player's GuZhenRen variable keys (names and tag types only). */
public final class DumpPlayerVariables {
    private DumpPlayerVariables() {}

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        CompoundTag vars = root.getCompound("neoforge:attachments").getCompound("guzhenren:player_variables");
        System.out.println("vars.keys=" + vars.getAllKeys().size());
        for (String key : new TreeSet<>(vars.getAllKeys())) {
            Tag tag = vars.get(key);
            String extra = tag == null ? "?" : tag.getId() + ":" + (tag instanceof CompoundTag c
                    ? c.getAllKeys().toString() : tag.getAsString());
            if (extra.length() > 90) extra = extra.substring(0, 90);
            System.out.println("  " + key + " = " + extra);
        }
    }
}
