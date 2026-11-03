import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.guzhenren.shengxian.compat.SavedXianGuUseReceipt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.NbtIo;

/** Offline checks for the actual player/host receipt and the fields affected by reusable immortal Gu. */
public final class VerifyXianGuUseReceipt {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SESSION = UUID.randomUUID(), OPERATION = UUID.randomUUID();
    private static int checks;
    private interface Checked { void run() throws Exception; }
    private static void check(boolean value) { if (!value) throw new AssertionError("Gu receipt assertion"); checks++; }
    private static void rejects(Checked call) throws Exception {
        try { call.run(); throw new AssertionError("Invalid Gu receipt accepted"); }
        catch (java.io.IOException expected) { checks++; }
    }
    private static SavedXianGuUseReceipt.Receipt receipt(SavedXianGuUseReceipt.Stage stage) {
        String payload = "{\"kind\":\"health\",\"target\":\"" + OWNER
                + "\",\"after\":{\"health\":20,\"extinguish\":true,\"clearPoison\":true,\"clearWither\":true}}";
        return new SavedXianGuUseReceipt.Receipt(OWNER, SESSION, OPERATION, 1L, 100L,
                "ren_ru_gu_immortal", stage, payload);
    }
    private static CompoundTag player() {
        return player(OWNER);
    }
    private static CompoundTag player(UUID owner) {
        CompoundTag root = new CompoundTag();
        root.putUUID("UUID", owner);
        root.putFloat("Health", 5);
        root.putShort("Fire", (short) 80);
        CompoundTag persistent = new CompoundTag(), persisted = new CompoundTag();
        persisted.putString("unrelated", "kept");
        persistent.put("PlayerPersisted", persisted);
        root.put("NeoForgeData", persistent);
        return root;
    }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("shengxian-reusable-gu-nbt-");
        Path file = directory.resolve("player.dat"), host = directory.resolve("level.dat");
        try {
            CompoundTag root = player();
            NbtIo.writeCompressed(root, file);
            SavedXianGuUseReceipt.verifySource(file, OWNER, Optional.empty(), false); checks++;
            var prepared = receipt(SavedXianGuUseReceipt.Stage.PREPARED);
            SavedXianGuUseReceipt.record(root.getCompound("NeoForgeData"), prepared, false);
            check(root.getCompound("NeoForgeData").getCompound("PlayerPersisted").getString("unrelated").equals("kept"));
            NbtIo.writeCompressed(root, file);
            SavedXianGuUseReceipt.verify(file, OWNER, prepared, false); checks++;
            rejects(() -> SavedXianGuUseReceipt.verifySource(file, OWNER, Optional.empty(), false));
            rejects(() -> SavedXianGuUseReceipt.verify(file, UUID.randomUUID(), prepared, false));
            var committed = receipt(SavedXianGuUseReceipt.Stage.COMMITTED);
            SavedXianGuUseReceipt.recordParticipant(root.getCompound("NeoForgeData"), OWNER, committed, false);
            check(root.getCompound("NeoForgeData").getCompound("PlayerPersisted")
                    .getLong("shengxian:last_immortal_heal") == committed.tick());
            root.putFloat("Health", 20); root.putShort("Fire", (short) 0);
            NbtIo.writeCompressed(root, file);
            SavedXianGuUseReceipt.verify(file, OWNER, committed, true); checks++;
            root.getCompound("NeoForgeData").getCompound("PlayerPersisted").remove("shengxian:last_immortal_heal");
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedXianGuUseReceipt.verify(file, OWNER, committed, true));
            root.getCompound("NeoForgeData").getCompound("PlayerPersisted").putLong("shengxian:last_immortal_heal", committed.tick());
            rejects(() -> SavedXianGuUseReceipt.record(root.getCompound("NeoForgeData"), prepared, false));
            root.putFloat("Health", 7); NbtIo.writeCompressed(root, file);
            SavedXianGuUseReceipt.verify(file, OWNER, committed, false); checks++;
            rejects(() -> SavedXianGuUseReceipt.verify(file, OWNER, committed, true));
            root.putFloat("Health", 20);
            ListTag effects = new ListTag(); CompoundTag poison = new CompoundTag();
            poison.putString("id", "minecraft:poison"); effects.add(poison); root.put("active_effects", effects);
            NbtIo.writeCompressed(root, file);
            rejects(() -> SavedXianGuUseReceipt.verify(file, OWNER, committed, true));
            root.remove("active_effects");
            CompoundTag level = new CompoundTag(), data = new CompoundTag();
            data.put("Player", root); level.put("Data", data); NbtIo.writeCompressed(level, host);
            SavedXianGuUseReceipt.verifyHost(host, OWNER, committed, true); checks++;
            rejects(() -> SavedXianGuUseReceipt.verifySource(host, OWNER, Optional.of(prepared), true));
            String travel = "{\"kind\":\"travel\",\"dimension\":\"minecraft:overworld\",\"x\":2.5,\"y\":65,\"z\":3.5}";
            var travelPrepared = new SavedXianGuUseReceipt.Receipt(OWNER, SESSION, UUID.randomUUID(), 2, 200,
                    "ding_xian_you", SavedXianGuUseReceipt.Stage.PREPARED, travel);
            SavedXianGuUseReceipt.record(root.getCompound("NeoForgeData"), travelPrepared, true);
            var travelCommitted = travelPrepared.withStage(SavedXianGuUseReceipt.Stage.COMMITTED);
            SavedXianGuUseReceipt.record(root.getCompound("NeoForgeData"), travelCommitted, false);
            root.putString("Dimension", "minecraft:overworld"); root.putFloat("FallDistance", 0);
            ListTag position = new ListTag(); position.add(DoubleTag.valueOf(2.5));
            position.add(DoubleTag.valueOf(65)); position.add(DoubleTag.valueOf(3.5)); root.put("Pos", position);
            NbtIo.writeCompressed(root, file);
            SavedXianGuUseReceipt.verify(file, OWNER, travelCommitted, true); checks++;
            root.putString("Dimension", "minecraft:the_nether"); NbtIo.writeCompressed(root, file);
            rejects(() -> SavedXianGuUseReceipt.verify(file, OWNER, travelCommitted, true));
            CompoundTag large = player();
            var cells = new com.google.gson.JsonArray();
            for (int i = 0; i < 1024; i++) {
                var cell = new com.google.gson.JsonObject();
                cell.addProperty("token", UUID.randomUUID().toString()); cell.addProperty("x", 30000000 - i);
                cell.addProperty("y", -64); cell.addProperty("z", -30000000);
                cell.addProperty("state", "{\"Name\":\"minecraft:grass_block\",\"Properties\":{\"snowy\":\"false\"}}");
                cells.add(cell);
            }
            var landscape = new com.google.gson.JsonObject(); landscape.addProperty("kind", "landscape");
            landscape.add("cells", cells);
            String largePayload = landscape.toString();
            check(largePayload.length() > 65535);
            var terrain = new SavedXianGuUseReceipt.Receipt(OWNER, SESSION, UUID.randomUUID(), 3, 300,
                    "jiang_shan_ru_gu", SavedXianGuUseReceipt.Stage.PREPARED, largePayload);
            SavedXianGuUseReceipt.record(large.getCompound("NeoForgeData"), terrain, false);
            NbtIo.writeCompressed(large, file);
            SavedXianGuUseReceipt.verify(file, OWNER, terrain, false); checks++;
            Path secondFile = directory.resolve("target.dat");
            CompoundTag firstSource = player(), secondSource = player(OTHER);
            var conflicting = new SavedXianGuUseReceipt.Receipt(OWNER, SESSION, UUID.randomUUID(), 4, 400,
                    "ren_ru_gu_immortal", SavedXianGuUseReceipt.Stage.PREPARED,
                    "{\"kind\":\"health\",\"target\":\"" + OTHER + "\",\"after\":{\"health\":4,\"extinguish\":false,\"clearPoison\":false,\"clearWither\":false}}");
            SavedXianGuUseReceipt.record(secondSource.getCompound("NeoForgeData"), conflicting, false);
            NbtIo.writeCompressed(firstSource, file); NbtIo.writeCompressed(secondSource, secondFile);
            byte[] firstBefore = Files.readAllBytes(file), secondBefore = Files.readAllBytes(secondFile);
            rejects(() -> SavedXianGuUseReceipt.verifySources(java.util.List.of(
                    new SavedXianGuUseReceipt.Source(file, OWNER, Optional.empty(), false),
                    new SavedXianGuUseReceipt.Source(secondFile, OTHER, Optional.empty(), false))));
            check(java.util.Arrays.equals(firstBefore, Files.readAllBytes(file))
                    && java.util.Arrays.equals(secondBefore, Files.readAllBytes(secondFile)));
            var worldUse = new net.guzhenren.shengxian.core.XianGuRuntime.Use(OPERATION, "ren_ru_gu_immortal", 1,
                    net.guzhenren.shengxian.core.XianGuRuntime.Stage.ACKNOWLEDGED, 100, 20, 18, 160, receipt(SavedXianGuUseReceipt.Stage.ACKNOWLEDGED).payload());
            var acknowledged = receipt(SavedXianGuUseReceipt.Stage.ACKNOWLEDGED);
            check(acknowledged.matchesWorld(SESSION, worldUse));
            check(!acknowledged.matchesWorld(UUID.randomUUID(), worldUse));
            Files.writeString(file, "corrupt");
            rejects(() -> SavedXianGuUseReceipt.verify(file, OWNER, travelCommitted, false));
            System.out.println("PASS reusable immortal Gu receipt: " + checks + " compressed-NBT checks");
        } finally {
            Files.deleteIfExists(file); Files.deleteIfExists(host); Files.deleteIfExists(directory.resolve("target.dat")); Files.deleteIfExists(directory);
        }
    }
}
