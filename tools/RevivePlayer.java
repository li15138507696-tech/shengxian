import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import java.nio.file.*;
public final class RevivePlayer {
 public static void main(String[] a)throws Exception{
  Path f=Path.of(a[0]);
  CompoundTag root=NbtIo.readCompressed(f,NbtAccounter.unlimitedHeap());
  root.putFloat("Health",20f);
  root.putShort("DeathTime",(short)0);
  root.putShort("HurtTime",(short)0);
  root.putShort("Fire",(short)-1);
  root.putShort("Air",(short)300);
  root.putInt("foodLevel",20);
  root.putFloat("SaturationLevel",5f);
  NbtIo.writeCompressed(root,f);
  System.out.println("REVIVED "+f);
 }
}
