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
    /** Read-only comparison for saves made before edit tracking existed. Never regenerates live chunks. */
    public static void importNearby(ServerPlayer player){
        ServerLevel level=player.serverLevel();var center=player.chunkPosition();
        for(int z=center.z-1;z<=center.z+1;z++)for(int x=center.x-1;x<=center.x+1;x++){
            var chunk=level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);
            if(chunk instanceof LevelChunk loaded)importChunk(level,loaded);
        }
    }
    public static void importChunk(ServerLevel level,LevelChunk chunk){
        if(!level.dimension().equals(PanoramaRoom.ROOM) || !(level.getChunkSource().getGenerator() instanceof StreetChunkGenerator generator))return;
        long key=chunk.getPos().toLong();Storage data=storage(level);
        if(data.importedChunks.contains(key) || importing.contains(key) || level.players().stream().noneMatch(p->chunk.getPos().getMiddleBlockPosition((int)p.getY()).distToCenterSqr(p.position())<96*96))return;
        importing.add(key);
        boolean mapped=generator.regionFor(chunk.getPos().getMiddleBlockX(),chunk.getPos().getMiddleBlockZ())!=null;
        CompletableFuture.supplyAsync(()->generator.fetch(chunk)).whenComplete((bytes,error)->level.getServer().execute(()->{
            importing.remove(key);
            if(error!=null || bytes==null || (mapped && bytes.length==0) || !level.hasChunk(chunk.getPos().x,chunk.getPos().z))return;
            if(bytes.length!=0 && bytes.length!=chunk.getHeight()*256)return;
            BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();int minY=chunk.getMinBuildHeight();
            for(int y=minY;y<chunk.getMaxBuildHeight();y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                pos.set(chunk.getPos().getMinBlockX()+x,y,chunk.getPos().getMinBlockZ()+z);
                BlockState original=bytes.length==0
                    ? (y>=64?Blocks.AIR:y==minY?Blocks.BEDROCK:y==63?Blocks.GRASS_BLOCK:Blocks.STONE).defaultBlockState()
                    : generator.blockForCode(bytes[((y-minY)*16+z)*16+x]&255);
                if(original!=chunk.getBlockState(pos))data.record(pos,original);
            }
            data.importedChunks.add(key);data.setDirty();
            for(ServerPlayer p:level.players())if(chunk.getPos().getMiddleBlockPosition((int)p.getY()).distToCenterSqr(p.position())<96*96)snapshot(p);
        }));
    }
    public static final class Storage extends SavedData {
        public final Map<Long,BlockState> original=new HashMap<>();
        public final Set<Long> importedChunks=new HashSet<>();
        public void record(BlockPos pos,BlockState before){if(original.putIfAbsent(pos.asLong(),before)==null)setDirty();}
        public static Storage load(CompoundTag tag,HolderLookup.Provider provider){
            Storage data=new Storage();ListTag list=tag.getList("edits",Tag.TAG_COMPOUND);
            for(int i=0;i<list.size();i++){CompoundTag entry=list.getCompound(i);data.original.put(entry.getLong("pos"),NbtUtils.readBlockState(provider.lookupOrThrow(Registries.BLOCK),entry.getCompound("original")));}
            for(long chunk:tag.getLongArray("importedChunks"))data.importedChunks.add(chunk);
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider){
            ListTag list=new ListTag();original.forEach((pos,state)->{CompoundTag entry=new CompoundTag();entry.putLong("pos",pos);entry.put("original",NbtUtils.writeBlockState(state));list.add(entry);});
            tag.put("edits",list);tag.putLongArray("importedChunks",importedChunks.stream().mapToLong(Long::longValue).toArray());return tag;
        }
    }
}
