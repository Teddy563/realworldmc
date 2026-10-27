package dev.realworldmc;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Authoritative damage history. Never infer destruction from a different terrain LOD. */
public final class MapEdits {
    private static final Map<ServerPlayer,Long> sent = new IdentityHashMap<>();
    private MapEdits() { }
    public static void clear() { sent.clear(); }
    public static StreetEdits.Storage storage(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StreetEdits.Storage::load, StreetEdits.Storage::new, "realworldmc_map_edits");
    }
    public static void changed(ServerLevel level,BlockPos pos,BlockState before,BlockState after) {
        if(!level.dimension().equals(Level.OVERWORLD) || before==after)return;
        boolean placement=before.isAir() && after.canOcclude() && level.getChunkSource().getGenerator() instanceof EarthChunkGenerator;
        if(!EarthBlocks.isMapBlock(before) && !placement)return;
        var data=storage(level);data.record(pos,before);
        var edit=new MapEditsPayload.Edit(pos.asLong(),Block.getId(data.original.get(pos.asLong())));
        for(var player:level.players())if(pos.distToCenterSqr(player.position())<160*160 && ServerPlayNetworking.canSend(player,MapEditsPayload.TYPE))
            ServerPlayNetworking.send(player,new MapEditsPayload(false,List.of(edit)));
    }
    public static void tick(MinecraftServer server) {
        if(server.getTickCount()%20!=0)return;
        Set<ServerPlayer> live=new HashSet<>();
        for(var player:server.getPlayerList().getPlayers()) {
            live.add(player);
            if(!player.level().dimension().equals(Level.OVERWORLD)){sent.remove(player);continue;}
            long key=net.minecraft.core.SectionPos.of(player.blockPosition()).asLong();
            if(!Objects.equals(sent.get(player),key)){snapshot(player);sent.put(player,key);}
        }
        sent.keySet().retainAll(live);
    }
    public static void snapshot(ServerPlayer player) {
        if(!ServerPlayNetworking.canSend(player,MapEditsPayload.TYPE))return;
        ServerPlayNetworking.send(player,new MapEditsPayload(true,List.of()));
        List<MapEditsPayload.Edit> batch=new ArrayList<>();
        storage(player.serverLevel()).original.forEach((key,state)->{
            if(BlockPos.of(key).distToCenterSqr(player.position())>160*160)return;
            batch.add(new MapEditsPayload.Edit(key,Block.getId(state)));
            if(batch.size()==MapEditsPayload.MAX_BATCH){ServerPlayNetworking.send(player,new MapEditsPayload(false,List.copyOf(batch)));batch.clear();}
        });
        if(!batch.isEmpty())ServerPlayNetworking.send(player,new MapEditsPayload(false,List.copyOf(batch)));
        // Commit the complete snapshot atomically on the client, avoiding a frame of repaired mesh.
        ServerPlayNetworking.send(player,new MapEditsPayload(false,List.of()));
    }
}
