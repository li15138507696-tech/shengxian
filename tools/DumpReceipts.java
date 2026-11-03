import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import java.nio.file.Path;

/** Offline dump of the shengxian player receipts (keys only). */
public final class DumpReceipts {
    private DumpReceipts() {}
    public static void main(String[] args) throws Exception {
        CompoundTag root = NbtIo.readCompressed(Path.of(args[0]), NbtAccounter.unlimitedHeap());
        CompoundTag persisted = root.getCompound("PlayerPersisted");
        System.out.println("PlayerPersisted keys=" + persisted.getAllKeys());
        for (String key : persisted.getAllKeys()) {
            CompoundTag c = persisted.getCompound(key);
            System.out.println("  " + key + " -> " + c.getAllKeys());
        }
    }
}
