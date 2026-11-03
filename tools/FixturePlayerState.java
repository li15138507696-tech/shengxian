import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Offline test fixture: writes the GuZhenRen player variables needed to exercise the ordinary
 * ascension route on an isolated server (rank/stage, and an optional vital Gu mirror).
 * Never shipped; never pointed at a real save.
 */
public final class FixturePlayerState {
    private FixturePlayerState() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 8) {
            throw new IllegalArgumentException("usage: <uuid> <zhuanshu> <jieduan> <vitalGuItemId|-> "
                    + "<dafuId|-> <zhenyuan> <maxZhenyuan> <playerdata-dir>");
        }
        UUID owner = UUID.fromString(args[0]);
        double rank = Double.parseDouble(args[1]);
        double stage = Double.parseDouble(args[2]);
        String guId = args[3];
        String daoId = args[4];
        double zhenyuan = Double.parseDouble(args[5]);
        double maxZhenyuan = Double.parseDouble(args[6]);
        Path file = Path.of(args[7]).resolve(owner + ".dat");
        if (!Files.isRegularFile(file)) throw new IllegalStateException("Missing player data: " + file);

        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        CompoundTag attachments = root.getCompound("neoforge:attachments");
        CompoundTag vars = attachments.getCompound("guzhenren:player_variables").copy();
        vars.putDouble("zhuanshu", rank);
        vars.putDouble("jieduan", stage);
        // 碎窍 charges a fraction of maximum true essence, so the character needs real essence.
        vars.putDouble("zuida_zhenyuan", maxZhenyuan);
        vars.putDouble("zhenyuan", zhenyuan);
        // A failed ascension writes the irreversible lost-aperture flag and raises kongqiao, so a
        // repeatable test needs both restored to the five-turn peak baseline.
        vars.putBoolean("shiqiao", false);
        vars.putDouble("kongqiao", 1.0);
        if (!"-".equals(guId)) {
            CompoundTag stack = new CompoundTag();
            stack.putString("id", guId);
            stack.putInt("count", 1);
            if (!"-".equals(daoId)) {
                // The real Gu item carries components.minecraft:custom_data.gc; the mirror must
                // match it or the custody fingerprint will not match the inventory item.
                CompoundTag custom = new CompoundTag();
                custom.putString("gc", daoId);
                CompoundTag components = new CompoundTag();
                components.put("minecraft:custom_data", custom);
                stack.put("components", components);
            }
            vars.put("BenMingGu", stack);
        }
        CompoundTag nextAttachments = attachments.copy();
        nextAttachments.put("guzhenren:player_variables", vars);
        root.put("neoforge:attachments", nextAttachments);

        Path backup = file.resolveSibling(owner + ".dat.fixture-backup");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        NbtIo.writeCompressed(root, file);

        CompoundTag verify = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())
                .getCompound("neoforge:attachments").getCompound("guzhenren:player_variables");
        System.out.println("OK zhuanshu=" + verify.getDouble("zhuanshu")
                + " jieduan=" + verify.getDouble("jieduan")
                + " vitalGu=" + verify.getCompound("BenMingGu").getString("id")
                + " keys=" + verify.getAllKeys().size());
    }
}
