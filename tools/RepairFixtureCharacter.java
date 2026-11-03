import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import java.nio.file.*;
public final class RepairFixtureCharacter {
 public static void main(String[] a)throws Exception{
  Path f=Path.of(a[0]);
  CompoundTag root=NbtIo.readCompressed(f,NbtAccounter.unlimitedHeap());
  CompoundTag attachments=root.getCompound("neoforge:attachments");
  CompoundTag character=attachments.getCompound("guzhenren_library:character");
  character.putBoolean("created",true);
  CompoundTag vars=attachments.getCompound("guzhenren:player_variables");
  vars.putDouble("zhuanshu",6.0);
  vars.putDouble("jieduan",1.0);
  vars.putDouble("shouyuan_miao",90.0);
  NbtIo.writeCompressed(root,f);
  System.out.println("REPAIRED created="+character.getBoolean("created")+" rank="+vars.getDouble("zhuanshu")+" stage="+vars.getDouble("jieduan"));
 }
}
