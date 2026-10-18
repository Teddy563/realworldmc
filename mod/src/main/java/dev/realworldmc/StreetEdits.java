package dev.realworldmc;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Keeps the first state of edited blocks, separately from vanilla's persistent current state. */
public final class StreetEdits {
    private static final Set<Long> importing=new HashSet<>();
    public static void clear(){importing.clear();}
    public static Storage storage(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Storage::new,Storage::load,null),"realworldmc_street_edits");
    }
    public static void changed(ServerLevel level,BlockPos pos,BlockState before,BlockState after){
        if(!level.dimension().equals(PanoramaRoom.ROOM) || before==after)return;
        Storage data=storage(level);
        data.record(pos,before);
        var edit=new StreetEditsPayload.Edit(pos.asLong(),Block.getId(data.original.get(pos.asLong())));
        for(ServerPlayer p:level.players())if(pos.distToCenterSqr(p.position())<128*128 && ServerPlayNetworking.canSend(p,StreetEditsPayload.TYPE))
            ServerPlayNetworking.send(p,new StreetEditsPayload(false,List.of(edit)));
    }
    public static void snapshot(ServerPlayer player){
        if(!ServerPlayNetworking.canSend(player,StreetEditsPayload.TYPE))return;
        ServerPlayNetworking.send(player,new StreetEditsPayload(true,List.of()));
        List<StreetEditsPayload.Edit> batch=new ArrayList<>();
        storage(player.serverLevel()).original.forEach((position,state)->{
            if(BlockPos.of(position).distToCenterSqr(player.position())>128*128)return;
            batch.add(new StreetEditsPayload.Edit(position,Block.getId(state)));
            if(batch.size()==StreetEditsPayload.MAX_BATCH){ServerPlayNetworking.send(player,new StreetEditsPayload(false,List.copyOf(batch)));batch.clear();}
        });
        if(!batch.isEmpty())ServerPlayNetworking.send(player,new StreetEditsPayload(false,List.copyOf(batch)));
    }
    // A later provider response is not an original-state baseline. Never infer edits
    // by comparing it with a saved chunk: coverage, LOD and fallback terrain change.
    public static void importNearby(ServerPlayer player) { }
    public static void importChunk(ServerLevel level,LevelChunk chunk) { }
    public static final class Storage extends SavedData {
        public final Map<Long,BlockState> original=new HashMap<>();
        public final Set<Long> importedChunks=new HashSet<>();
        private ListTag unverified = new ListTag();
        public void record(BlockPos pos,BlockState before){if(original.putIfAbsent(pos.asLong(),before)==null)setDirty();}
        public static Storage load(CompoundTag tag,HolderLookup.Provider provider){
            Storage data=new Storage();ListTag list=tag.getList("edits",Tag.TAG_COMPOUND);
            data.unverified=tag.getList("unverified_legacy_edits",Tag.TAG_COMPOUND);
            if (tag.getInt("trackingVersion") < 2 && tag.getLongArray("importedChunks").length > 0) {
                // Preserve ambiguous historical records for recovery, but do not draw them.
                // Saved chunk blocks (including builds and excavations) are untouched.
                data.unverified=list; list=new ListTag(); data.setDirty();
            }
            for(int i=0;i<list.size();i++){CompoundTag entry=list.getCompound(i);data.original.put(entry.getLong("pos"),NbtUtils.readBlockState(provider.lookupOrThrow(Registries.BLOCK),entry.getCompound("original")));}
            for(long chunk:tag.getLongArray("importedChunks"))data.importedChunks.add(chunk);
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider){
            ListTag list=new ListTag();original.forEach((pos,state)->{CompoundTag entry=new CompoundTag();entry.putLong("pos",pos);entry.put("original",NbtUtils.writeBlockState(state));list.add(entry);});
            tag.putInt("trackingVersion",2);tag.put("unverified_legacy_edits",unverified);tag.put("edits",list);tag.putLongArray("importedChunks",importedChunks.stream().mapToLong(Long::longValue).toArray());return tag;
        }
    }
}
