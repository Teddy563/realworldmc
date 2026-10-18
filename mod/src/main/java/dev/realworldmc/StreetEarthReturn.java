package dev.realworldmc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Prepare Earth at the current geographic position before leaving the photo world. */
public final class StreetEarthReturn {
    private static final Map<UUID,Pending> pending=new HashMap<>();
    private static final class Pending {
        final Vec3 origin;final int started;Vec3 destination;
        Pending(ServerPlayer p){origin=p.position();started=p.server.getTickCount();}
    }
    private StreetEarthReturn(){}
    public static boolean active(ServerPlayer p){return pending.containsKey(p.getUUID());}
    public static void cancel(ServerPlayer p){pending.remove(p.getUUID());}
    public static void clear(){pending.clear();}
    private static void signal(ServerPlayer p,String phase){
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,new PanoramaPayload(0,phase,"",0,0,0));
    }
    public static void start(ServerPlayer p,double lat,double lon){
        if(active(p))return;
        Pending state=new Pending(p);pending.put(p.getUUID(),state);signal(p,"depart");
        var server=p.server;var regions=EarthRegions.all();
        CompletableFuture.runAsync(()->{
            try {
                EarthRegions.Region nearest=null;double[] local=null;
                for(var r:regions){
                    if(!Double.isFinite(r.groundHeight()))continue;
                    double[] candidate=Geo.geodeticToMc(r.latitude(),r.longitude(),r.groundHeight(),lat,lon,r.groundHeight());
                    if(Math.hypot(candidate[0],candidate[2])<EarthRegions.REUSE_RADIUS && (local==null || Math.hypot(candidate[0],candidate[2])<Math.hypot(local[0],local[2]))){nearest=r;local=candidate;}
                }
                double height=nearest==null?ServiceClient.getJson(String.format(Locale.ROOT,"/ground?lat=%.8f&lon=%.8f",lat,lon),Duration.ofSeconds(60)).get("height").getAsDouble():nearest.groundHeight();
                if(!Double.isFinite(height))throw new IllegalStateException("No terrain height");
                var chosen=nearest;var offset=local;
                server.execute(()->{
                    if(!live(p,state))return;
                    try {
                        var region=chosen!=null?chosen:EarthRegions.add(lat,lon,height);
                        double x=region.offsetX()+(offset==null?0:offset[0]),z=region.offsetZ()+(offset==null?0:offset[2]);
                        var level=server.overworld();int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
                        level.getChunkSource().getChunkFuture(bx>>4,bz>>4,ChunkStatus.FULL,true).whenComplete((chunk,error)->server.execute(()->{
                            if(!live(p,state))return;
                            if(error!=null || chunk==null || !chunk.isSuccess()){fail(p);return;}
                            int y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,bx,bz);
                            if(y<=level.getMinBuildHeight()){fail(p);return;}
                            state.destination=new Vec3(x,y,z);
                        }));
                    }catch(Exception e){fail(p);}
                });
            }catch(Exception e){server.execute(()->{if(live(p,state))fail(p);});}
        });
    }
    private static boolean live(ServerPlayer p,Pending state){return !p.isRemoved() && p.isAlive() && pending.get(p.getUUID())==state && p.level().dimension().equals(PanoramaRoom.ROOM);}
    private static void fail(ServerPlayer p){cancel(p);signal(p,"depart-cancel");p.sendSystemMessage(RWMessages.message("Earth terrain could not load. Still at this Street View stop; retry /earth back or use /earth back original."));}
    public static void tick(MinecraftServer server){
        for(var p:server.getPlayerList().getPlayers()){
            var state=pending.get(p.getUUID());if(state==null)continue;
            if(!live(p,state)){cancel(p);signal(p,"depart-cancel");continue;}
            p.setDeltaMovement(Vec3.ZERO);
            if(p.position().distanceToSqr(state.origin)>.0001)p.teleportTo(state.origin.x,state.origin.y,state.origin.z);
            if(server.getTickCount()-state.started>1400){fail(p);continue;}
            if(state.destination!=null && server.getTickCount()-state.started>=16){
                cancel(p);
                if(PanoramaRoom.finishEarthReturn(p,state.destination))signal(p,"arrive");else signal(p,"depart-cancel");
            }
        }
    }
}
