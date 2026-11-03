import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Offline test fixture: sets the GuZhenRen player variables (zhuanshu, jieduan) for one
 * player-data file so the representative immortal Gu preconditions can be exercised on an
 * isolated server. Never shipped; never pointed at a real save.
 */
public final class FixtureUpstreamRank {
    private FixtureUpstreamRank() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 4) throw new IllegalArgumentException("usage: <uuid> <zhuanshu> <jieduan> <playerdata-dir>");
        UUID owner = UUID.fromString(args[0]);
        double rank = Double.parseDouble(args[1]);
        double stage = Double.parseDouble(args[2]);
        Path file = Path.of(args[3]).resolve(owner + ".dat");
        if (!Files.isRegularFile(file)) throw new IOException("Missing player data: " + file);

        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        CompoundTag bukkit = root.getCompound("BukkitValues"); // unused; guards accidental root swap
        if (bukkit.isEmpty() && !root.contains("BukkitValues") && !root.contains("neoforge:attachments")) {
            throw new IOException("Unexpected player data layout");
        }
        CompoundTag attachments = root.getCompound("neoforge:attachments");
        CompoundTag variables = attachments.getCompound("guzhenren:player_variables");
        CompoundTag next = variables.copy();
        next.putDouble("zhuanshu", rank);
        next.putDouble("jieduan", stage);
        CompoundTag nextAttachments = attachments.copy();
        nextAttachments.put("guzhenren:player_variables", next);
        root.put("neoforge:attachments", nextAttachments);

        Path backup = file.resolveSibling(owner + ".dat.fixture-backup");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        NbtIo.writeCompressed(root, file);
        CompoundTag verify = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        double written = verify.getCompound("neoforge:attachments")
                .getCompound("guzhenren:player_variables").getDouble("zhuanshu");
        if (Double.compare(written, rank) != 0) throw new IOException("Readback mismatch: " + written);
        System.out.println("OK zhuanshu=" + written + " jieduan=" + stage + " backup=" + backup.getFileName());
    }
}
