import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import net.minecraft.nbt.*;
import net.guzhenren.shengxian.compat.SavedRankProjection;

/** Real compressed player-NBT validation; starts no Minecraft process or world. */
public final class VerifyRankPersistence {
    static int checks;
    static final UUID SESSION=UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000001");
    interface Checked {void run() throws Exception;}
    static void check(boolean ok){if(!ok)throw new AssertionError();checks++;}
    static void rejects(Checked call)throws Exception{
        try{call.run();throw new AssertionError("Invalid saved projection accepted");}
        catch(IOException expected){checks++;}
    }
    static CompoundTag data(double rank,double stage){
        CompoundTag root=new CompoundTag(),attachments=new CompoundTag(),variables=new CompoundTag();
        CompoundTag persistent=new CompoundTag();SavedRankProjection.recordReceipt(persistent,SESSION);root.put("NeoForgeData",persistent);
        root.putUUID("UUID",OWNER);variables.putDouble("zhuanshu",rank);variables.putDouble("jieduan",stage);
        attachments.put("guzhenren:player_variables",variables);root.put("neoforge:attachments",attachments);
        return root;
    }
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("shengxian-rank-file-"),file=dir.resolve(OWNER+".dat");
        try{
            rejects(()->SavedRankProjection.verify(file,OWNER,6,1,SESSION));
            CompoundTag root=data(6,1);NbtIo.writeCompressed(root,file);
            byte[] before=Files.readAllBytes(file);
            SavedRankProjection.verify(file,OWNER,6,1,SESSION);checks++;
            check(Arrays.equals(before,Files.readAllBytes(file)));
            rejects(()->SavedRankProjection.verify(file,UUID.randomUUID(),6,1,SESSION));
            rejects(()->SavedRankProjection.verify(file,OWNER,7,1,SESSION));
            rejects(()->SavedRankProjection.verify(file,OWNER,6,2,SESSION));
            root=data(5,1);NbtIo.writeCompressed(root,file);
            rejects(()->SavedRankProjection.verify(file,OWNER,6,1,SESSION));
            root=data(6,1);root.remove("UUID");NbtIo.writeCompressed(root,file);
            rejects(()->SavedRankProjection.verify(file,OWNER,6,1,SESSION));
            root=data(6,1);root.remove("neoforge:attachments");NbtIo.writeCompressed(root,file);
            rejects(()->SavedRankProjection.verify(file,OWNER,6,1,SESSION));
            root=data(6,1);root.getCompound("neoforge:attachments").remove("guzhenren:player_variables");NbtIo.writeCompressed(root,file);
            rejects(()->SavedRankProjection.verify(file,OWNER,6,1,SESSION));
            root=data(6,1);root.getCompound("neoforge:attachments").getCompound("guzhenren:player_variables").remove("jieduan");NbtIo.writeCompressed(root,file);
            rejects(()->SavedRankProjection.verify(file,OWNER,6,1,SESSION));
            root=data(Double.NaN,1);NbtIo.writeCompressed(root,file);
            rejects(()->SavedRankProjection.verify(file,OWNER,6,1,SESSION));
            Files.writeString(file,"truncated player data");
            rejects(()->SavedRankProjection.verify(file,OWNER,6,1,SESSION));
            NbtIo.writeCompressed(data(7,4),file);
            SavedRankProjection.verify(file,OWNER,7,4,SESSION);checks++;
            root=data(6,1);root.remove("NeoForgeData");NbtIo.writeCompressed(root,file);
            rejects(()->SavedRankProjection.verify(file,OWNER,6,1,SESSION));
            root=data(6,1);NbtIo.writeCompressed(root,file);
            rejects(()->SavedRankProjection.verify(file,OWNER,6,1,UUID.randomUUID()));
            Path host=dir.resolve("level.dat");
            try {
                CompoundTag level=new CompoundTag(),worldData=new CompoundTag();
                worldData.put("Player",data(5,1));level.put("Data",worldData);NbtIo.writeCompressed(level,host);
                // A fresh UUID.dat cannot make the stale level.dat owner projection authoritative.
                SavedRankProjection.verify(file,OWNER,6,1,SESSION);
                rejects(()->SavedRankProjection.verifyHost(host,OWNER,6,1,SESSION));
                worldData.put("Player",data(6,1));NbtIo.writeCompressed(level,host);
                SavedRankProjection.verifyHost(host,OWNER,6,1,SESSION);checks++;
                worldData.remove("Player");NbtIo.writeCompressed(level,host);
                rejects(()->SavedRankProjection.verifyHost(host,OWNER,6,1,SESSION));
            } finally {Files.deleteIfExists(host);}
            CompoundTag persistent=new CompoundTag(),persisted=new CompoundTag();
            persistent.putString("other_mod","outside");persisted.putString("other_quest","inside");
            persistent.put("PlayerPersisted",persisted);
            SavedRankProjection.recordReceipt(persistent,SESSION);SavedRankProjection.recordReceipt(persistent,SESSION);
            check(persistent.getString("other_mod").equals("outside")
                    && persistent.getCompound("PlayerPersisted").getString("other_quest").equals("inside"));
            check(SavedRankProjection.hasReceipt(persistent,SESSION));
            UUID second=UUID.randomUUID();SavedRankProjection.recordReceipt(persistent,second);
            check(SavedRankProjection.hasReceipt(persistent,SESSION) && SavedRankProjection.hasReceipt(persistent,second));
            System.out.println("PASS saved rank projection: "+checks+" compressed-NBT checks");
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
    }
}