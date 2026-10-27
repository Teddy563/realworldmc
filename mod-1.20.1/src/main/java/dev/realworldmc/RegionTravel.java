package dev.realworldmc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Recenter by geographic coordinates, never by the next occupied Minecraft grid cell. */
public final class RegionTravel {
    public static volatile List<EarthRegions.Region> clientRegions=List.of();
    private static final Map<UUID,Long> retryAfter=new HashMap<>();
    private static final Set<UUID> pending=new HashSet<>();
    private record Destination(EarthRegions.Region existing,double ground) {}
    private static int epoch;
    public static void clear(){pending.clear();retryAfter.clear();epoch++;}
    public static void tick(MinecraftServer server) {
        for(var player:server.getPlayerList().getPlayers()) {
            if(player.level().dimension()!=Level.OVERWORLD || !(server.overworld().getChunkSource().getGenerator() instanceof EarthChunkGenerator))continue;
            var region=EarthRegions.at(player.getX(),player.getZ());
            if(region==null || !Double.isFinite(region.groundHeight()) || Math.hypot(player.getX()-region.offsetX(),player.getZ()-region.offsetZ())<RegionBoundary.PREPARE_RADIUS)continue;
            if(pending.contains(player.getUUID()) || System.nanoTime()<retryAfter.getOrDefault(player.getUUID(),0L))continue;
            begin(server,player,region);
        }
    }
    private static void begin(MinecraftServer server,ServerPlayer player,EarthRegions.Region source) {
        var loading=TravelLoading.start(player.createCommandSourceStack());
        if(loading==null){retryAfter.put(player.getUUID(),System.nanoTime()+Duration.ofSeconds(10).toNanos());return;}
        pending.add(player.getUUID()); int generation=epoch;
        var position=player.position();
        double[] geo=Geo.mcToGeodetic(source.latitude(),source.longitude(),source.groundHeight(),position.x-source.offsetX(),position.y,position.z-source.offsetZ());
        loading.stage("Loading nearby terrain",1);
        CompletableFuture.supplyAsync(()-> {
            try {
                for(var candidate:EarthRegions.all()) {
                    if(candidate.index()==source.index() || !Double.isFinite(candidate.groundHeight()))continue;
                    var local=Geo.geodeticToMc(candidate.latitude(),candidate.longitude(),candidate.groundHeight(),geo[0],geo[1],candidate.groundHeight());
                    if(Math.sqrt(local[0]*local[0]+local[2]*local[2]+Math.pow(local[1]-64,2))<10_000)return new Destination(candidate,candidate.groundHeight());
                }
                double ground=ServiceClient.getJson(String.format(Locale.ROOT,"/ground?lat=%.8f&lon=%.8f",geo[0],geo[1]),Duration.ofSeconds(60)).get("height").getAsDouble();
                if(!Double.isFinite(ground))throw new IllegalStateException("Invalid ground");
                return new Destination(null,ground);
            } catch(Exception failure){throw new java.util.concurrent.CompletionException(failure);}
        }).whenComplete((destination,failure)->server.execute(()-> {
            if(!valid(server,player,source,loading,generation)){finish(player,loading,generation);return;}
            if(failure!=null){failed(player,loading,generation);return;}
            try {
            var target=destination.existing()!=null?destination.existing():EarthRegions.add(geo[0],geo[1],destination.ground());
            prepare(server,player,source,target,loading,generation,0);
            } catch(Exception error){failed(player,loading,generation);}
        }));
    }
    private static boolean valid(MinecraftServer server,ServerPlayer player,EarthRegions.Region source,TravelLoading loading,int generation) {
        return generation==epoch && !player.isRemoved() && player.level()==server.overworld() && loading.active()
            && source.equals(EarthRegions.at(player.getX(),player.getZ()))
            && Math.hypot(player.getX()-source.offsetX(),player.getZ()-source.offsetZ())>=RegionBoundary.PREPARE_RADIUS-1000;
    }
    private static void prepare(MinecraftServer server,ServerPlayer player,EarthRegions.Region source,EarthRegions.Region target,TravelLoading loading,int generation,int attempt) {
        var requested=convert(source,target,player.position());
        int cx=(int)Math.floor(requested.x)>>4, cz=(int)Math.floor(requested.z)>>4;
        loading.stage("Preparing arrival",2);
        try {
        server.overworld().getChunkSource().getChunkFuture(cx,cz,net.minecraft.world.level.chunk.ChunkStatus.FULL,true)
            .orTimeout(60,java.util.concurrent.TimeUnit.SECONDS).whenComplete((chunk,error)->server.execute(()-> {
                if(!valid(server,player,source,loading,generation)){finish(player,loading,generation);return;}
                if(error!=null || server.overworld().getChunkSource().getChunkNow(cx,cz)==null){failed(player,loading,generation);return;}
                Vec3 old=player.position(), velocity=player.getDeltaMovement();
                Vec3 arrival=convert(source,target,old);
                if(((int)Math.floor(arrival.x)>>4)!=cx || ((int)Math.floor(arrival.z)>>4)!=cz) {
                    if(attempt<4)prepare(server,player,source,target,loading,generation,attempt+1);
                    else failed(player,loading,generation);
                    return;
                }
                try {
                    Vec3 transformedVelocity=convert(source,target,old.add(velocity)).subtract(arrival);
                    Vec3 look=convert(source,target,old.add(Vec3.directionFromRotation(player.getXRot(),player.getYRot()))).subtract(arrival).normalize();
                    float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z));
                    float pitch=(float)Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,-look.y))));
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new RegionsPayload(EarthRegions.all()));
                    player.teleportTo(server.overworld(),arrival.x,arrival.y,arrival.z,yaw,pitch);
                    player.setDeltaMovement(transformedVelocity);
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
                    finish(player,loading,generation);
                } catch(Exception failure) {failed(player,loading,generation);}
            }));
        } catch(Exception error){failed(player,loading,generation);}
    }
    private static void finish(ServerPlayer player,TravelLoading loading,int generation) {
        loading.close();
        if(generation==epoch){pending.remove(player.getUUID());retryAfter.put(player.getUUID(),System.nanoTime()+Duration.ofSeconds(10).toNanos());}
    }
    private static void failed(ServerPlayer player,TravelLoading loading,int generation) {
        player.sendSystemMessage(RWMessages.message("Nearby terrain unavailable. Turn back or use /earth goto. Retrying shortly."));
        finish(player,loading,generation);
    }
    private static Vec3 convert(EarthRegions.Region from,EarthRegions.Region to,Vec3 p) {
        var geo=Geo.mcToGeodetic(from.latitude(),from.longitude(),from.groundHeight(),p.x-from.offsetX(),p.y,p.z-from.offsetZ());
        var local=Geo.geodeticToMc(to.latitude(),to.longitude(),to.groundHeight(),geo[0],geo[1],geo[2]);
        return new Vec3(to.offsetX()+local[0],local[1],to.offsetZ()+local[2]);
    }
}
