package net.guzhenren.shengxian.compat;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Verifies the actual loading source and the receipt stored atomically with the upstream rank. */
public final class SavedRankProjection {
    private static final String ATTACHMENTS="neoforge:attachments",VARIABLES="guzhenren:player_variables";
    private static final String PERSISTED="PlayerPersisted",RECEIPT_PREFIX="shengxian:rank_six:";
    private static final long MAX_NBT_BYTES=64L*1024*1024;
    private SavedRankProjection() {}

    public static boolean hasReceipt(CompoundTag persistentData,UUID session) {
        CompoundTag persisted=persistentData.getCompound(PERSISTED);
        String key=RECEIPT_PREFIX+session;
        return persisted.contains(key,Tag.TAG_BYTE) && persisted.getBoolean(key);
    }

    /** NeoForge's PlayerPersisted subtree is copied by ServerPlayer.restoreFrom after death. */
    public static void recordReceipt(CompoundTag persistentData,UUID session) {
        CompoundTag persisted=persistentData.getCompound(PERSISTED);
        persisted.putBoolean(RECEIPT_PREFIX+session,true);
        persistentData.put(PERSISTED,persisted);
    }

    public static void verify(Path file,UUID owner,double rank,double stage,UUID session) throws IOException {
        verifyFile(file,owner,rank,stage,session,false);
    }

    /** Singleplayer owners load level.dat/Data/Player before their standalone UUID.dat. */
    public static void verifyHost(Path file,UUID owner,double rank,double stage,UUID session) throws IOException {
        verifyFile(file,owner,rank,stage,session,true);
    }

    private static void verifyFile(Path file,UUID owner,double rank,double stage,UUID session,
                                   boolean host) throws IOException {
        if(owner==null || session==null || !Double.isFinite(rank) || rank<6 || !Double.isFinite(stage))
            throw new IOException("Invalid upstream immortal projection");
        try {
            CompoundTag root=NbtIo.readCompressed(file,NbtAccounter.create(MAX_NBT_BYTES));
            if(host) {
                if(!root.contains("Data",Tag.TAG_COMPOUND)
                        || !root.getCompound("Data").contains("Player",Tag.TAG_COMPOUND))
                    throw new IOException("Saved singleplayer owner data is missing");
                root=root.getCompound("Data").getCompound("Player");
            }
            if(!root.hasUUID("UUID") || !owner.equals(root.getUUID("UUID")))
                throw new IOException("Saved player identity does not match the ascension owner");
            if(!root.contains(ATTACHMENTS,Tag.TAG_COMPOUND))
                throw new IOException("Saved player attachment data is missing");
            CompoundTag attachments=root.getCompound(ATTACHMENTS);
            if(!attachments.contains(VARIABLES,Tag.TAG_COMPOUND))
                throw new IOException("Saved upstream player variables are missing");
            CompoundTag variables=attachments.getCompound(VARIABLES);
            if(!variables.contains("zhuanshu",Tag.TAG_DOUBLE) || !variables.contains("jieduan",Tag.TAG_DOUBLE)
                    || Double.compare(variables.getDouble("zhuanshu"),rank)!=0
                    || Double.compare(variables.getDouble("jieduan"),stage)!=0)
                throw new IOException("Saved upstream rank/stage did not confirm the in-memory projection");
            if(!root.contains("NeoForgeData",Tag.TAG_COMPOUND)
                    || !hasReceipt(root.getCompound("NeoForgeData"),session))
                throw new IOException("Saved player projection receipt does not match the committed session");
            // Vanilla's save call catches I/O errors. Confirm the file contents and force this file before acknowledging.
            try(FileChannel channel=FileChannel.open(file,StandardOpenOption.WRITE)) { channel.force(true); }
        } catch(RuntimeException exception) {
            throw new IOException("Saved player projection could not be verified",exception);
        }
    }
}