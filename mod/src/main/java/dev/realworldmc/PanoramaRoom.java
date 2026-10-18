package dev.realworldmc;

import com.google.gson.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Server-owned travel. Photographs and chunks must be ready before a teleport is committed. */
public final class PanoramaRoom {
    public static final ResourceKey<Level> ROOM = ResourceKey.create(Registries.DIMENSION, RealWorldMC.id("streetworld"));
    public static final ResourceKey<Level> LEGACY = ResourceKey.create(Registries.DIMENSION, RealWorldMC.id("panorama"));
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int nextTicket;
    private static final class Session {
        JsonObject current, pending;
        int ticket, request, started, lastAction = -100;
        boolean busy, preparing, walking = true, approach, terrainReady;
        EarthRegions.Region place;
        double x, z;
        double anchorX,anchorY,anchorZ;
        double groundY, eyeHeight = EarthConfig.get().photoEyeHeight();
        ResourceKey<Level> origin;
    }
    private record ReturnPoint(String dimension,double x,double y,double z,float yaw,float pitch,boolean invulnerable,boolean noGravity,boolean flying) {}
    private static Path file(ServerPlayer p) { return p.server.getWorldPath(LevelResource.ROOT).resolve("data/realworldmc-panorama/"+p.getUUID()+".json"); }
    public static boolean walking(ServerPlayer p) { var s=SESSIONS.get(p.getUUID()); return s!=null && s.current!=null && s.walking && p.level().dimension().equals(ROOM); }
    public static boolean isRoom(Level level) { return level.dimension().equals(ROOM) || level.dimension().equals(LEGACY); }
    private static void say(ServerPlayer p, String text) { p.sendSystemMessage(RWMessages.message(text)); }
    private static void send(ServerPlayer p, int ticket, String phase, String data, double x, double y, double z) {
        ServerPlayNetworking.send(p, new PanoramaPayload(ticket, phase, data, x, y, z));
    }
    private static boolean live(ServerPlayer p, Session s, int request) {
        return !p.isRemoved() && p.isAlive() && SESSIONS.get(p.getUUID()) == s && s.busy && s.request == request && p.level().dimension().equals(s.origin);
    }
    private static void fail(ServerPlayer p, Session s, int ticket, String message) {
        if (SESSIONS.get(p.getUUID()) != s || s.request != ticket) return;
        s.busy = false; s.preparing = false; s.pending = null; s.approach = false; s.terrainReady = false;
        if (!p.isRemoved()) { send(p,ticket,"error",message,0,0,0); say(p,message); }
    }
    public static int enter(CommandSourceStack src, double lat, double lon) {
        return enter(src,lat,lon,50);
    }
    public static int enter(CommandSourceStack src, double lat, double lon,int radius) {
        ServerPlayer p = src.getPlayer();
        if (p == null || !ServerPlayNetworking.canSend(p,PanoramaPayload.TYPE)) { src.sendFailure(RWMessages.message("Street View requires the updated RealWorldMC client.")); return 0; }
        if (p.server.getLevel(ROOM) == null) { src.sendFailure(RWMessages.message("Street world missing. Restart Minecraft to load the new dimension.")); return 0; }
        if (!StreetNavigation.coordinates(lat,lon) || radius<1 || radius>1000) return 0;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) {
            if (Files.exists(file(p))) { say(p,"Use /earth back to recover your saved return point first."); return 0; }
            s = new Session(); SESSIONS.put(p.getUUID(),s);
        }
        if (s.busy) { say(p,"A Street View destination is already loading. /earth back cancels it."); return 0; }
        request(p,s,"lat="+lat+"&lng="+lon+"&radius="+radius);
        return 1;
    }
    public static void action(ServerPlayer p, PanoramaActionPayload action) {
        Session s = SESSIONS.get(p.getUUID()); if (s == null) return;
        if (action.action().equals("cancel") && s.approach && s.busy && action.ticket() == s.request) {
            fail(p,s,s.request,"Walking destination cancelled."); return;
        }
        if (action.action().equals("failed") && s.busy && action.ticket() == s.request) {
            fail(p,s,s.request,"Photo loading failed. Your previous location and photograph have been kept."); return;
        }
        if (action.action().equals("ready") && live(p,s,action.ticket()) && s.pending != null && !s.preparing) {
            s.preparing = true; prepareChunk(p,s,action.ticket()); return;
        }
        if (s.busy || s.current == null || action.ticket() != s.ticket || !p.level().dimension().equals(ROOM)) return;
        if (action.action().equals("walk")) {
            if (!action.pano().equals("on") && !action.pano().equals("off")) return;
            s.walking = action.pano().equals("on");
            p.teleportTo(s.anchorX,s.walking ? s.groundY : s.anchorY,s.anchorZ);
            movement(p,s);
            send(p,s.ticket,"movement",viewMetadata(s).toString(),s.anchorX,s.anchorY,s.anchorZ);
            return;
        }
        boolean approaching = action.action().equals("approach");
        if (!action.action().equals("next") && !approaching) return;
        if (approaching && (!s.walking || !walkingTarget(p, s, action.pano(), StreetNavigation.WALK_PREPARE - .2))) {
            send(p,s.ticket,"error","Move toward a connected photo at the walking edge.",0,0,0); return;
        }
        int now = p.server.getTickCount();
        if (now - s.lastAction < 10) { send(p,s.ticket,"error","Please wait a moment before the next photo.",0,0,0); return; }
        if (!StreetNavigation.validPano(action.pano())) return;
        boolean linked = false;
        for (JsonElement e : s.current.getAsJsonArray("links")) if (e.getAsJsonObject().get("panoId").getAsString().equals(action.pano())) linked = true;
        if (!linked) return;
        s.lastAction = now; s.approach = approaching; request(p,s,"pano="+action.pano());
    }
    private static final class NoPanoramaException extends Exception {}
    private static JsonObject metadata(String query) throws Exception {
        ServiceClient.ensureRunning();
        if (!ServiceClient.startupError().isEmpty()) throw new IllegalStateException(ServiceClient.startupError());
        var response = HTTP.send(HttpRequest.newBuilder(URI.create(ServiceClient.photoBase() + "/native/metadata?"+query)).timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==404)throw new NoPanoramaException();
        if (response.statusCode()!=200 || response.body().length()>32767) throw new IllegalStateException("Metadata unavailable");
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!StreetNavigation.validPano(data.get("panoId").getAsString()) || !StreetNavigation.coordinates(data.get("lat").getAsDouble(),data.get("lng").getAsDouble())) throw new IllegalArgumentException("Invalid photo location");
        JsonArray links = new JsonArray();
        if (data.has("links")) for (JsonElement e : data.getAsJsonArray("links")) {
            JsonObject link = e.getAsJsonObject();
            if (links.size()<64 && link.has("panoId") && link.has("heading") && StreetNavigation.validPano(link.get("panoId").getAsString()) && Double.isFinite(link.get("heading").getAsDouble())) links.add(link);
        }
        data.add("links",links); return data;
    }
    private static void request(ServerPlayer p, Session s, String query) {
        s.busy = true; s.preparing = false; s.terrainReady = false; s.origin = p.level().dimension(); s.started = p.server.getTickCount(); int ticket = s.request = ++nextTicket;
        send(p,ticket,"loading","Loading photo location and physical ground…",0,0,0);
        CompletableFuture.supplyAsync(() -> {
            try { return metadata(query); } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        }).whenComplete((data,error) -> p.server.execute(() -> {
            if (!live(p,s,ticket)) return;
            if (error != null) {
                fail(p,s,ticket,error.getCause() instanceof NoPanoramaException
                    ? "No Street View photo found nearby. Move closer to a covered road or try coordinates; your location has been kept."
                    : "Street View metadata unavailable. Check the local photo service and Map Tiles API."); return;
            }
            double lat = data.get("lat").getAsDouble(), lon = data.get("lng").getAsDouble();
            var place = PanoramaPlaces.nearby(lat,lon);
            if (place != null) { preparePhoto(p,s,ticket,data,place); return; }
            CompletableFuture.supplyAsync(() -> {
                try {
                    ServiceClient.ensureRunning();
                    double h = ServiceClient.getJson(String.format(Locale.ROOT,"/ground?lat=%.8f&lon=%.8f",lat,lon),Duration.ofSeconds(60)).get("height").getAsDouble();
                    return Double.isFinite(h) && Math.abs(h)<15000 ? h : Double.NaN;
                } catch (Exception e) { return Double.NaN; }
            }).whenComplete((height,groundError) -> p.server.execute(() -> {
                if (!live(p,s,ticket)) return;
                var existing = PanoramaPlaces.nearby(lat,lon);
                var created = existing != null ? existing : PanoramaPlaces.add(lat,lon,height == null ? Double.NaN : height);
                preparePhoto(p,s,ticket,data,created);
            }));
        }));
    }
    private static void preparePhoto(ServerPlayer p, Session s, int ticket, JsonObject data, EarthRegions.Region place) {
        s.pending = data; s.place = place;
        double[] local = PanoramaPlaces.local(place,data.get("lat").getAsDouble(),data.get("lng").getAsDouble());
        s.x = place.offsetX()+local[0]; s.z = place.offsetZ()+local[2];
        send(p,ticket,"prepare",data.toString(),s.x,64,s.z);
    }
    private static void prepareChunk(ServerPlayer p, Session s, int ticket) {
        ServerLevel room = p.server.getLevel(ROOM);
        int x = (int)Math.floor(s.x), z = (int)Math.floor(s.z);
        room.getChunkSource().getChunkFuture(x>>4,z>>4,ChunkStatus.FULL,true).whenComplete((result,error) -> p.server.execute(() -> {
            if (!live(p,s,ticket)) return;
            if (error != null || result == null || !result.isSuccess()) { fail(p,s,ticket,"Destination terrain could not load. Your previous location has been kept."); return; }
            s.terrainReady = true;
            if (!s.approach || walkingTarget(p,s,s.pending.get("panoId").getAsString(),StreetNavigation.WALK_TRANSITION)) commitPhoto(p,s,ticket);
        }));
    }
    private static void commitPhoto(ServerPlayer p, Session s, int ticket) {
        ServerLevel room = p.server.getLevel(ROOM);
        int x = (int)Math.floor(s.x), z = (int)Math.floor(s.z);
            try {
                saveReturn(p);
                // Prefer street-level free space over a roof. Do not delete obstructions or repair craters.
                s.groundY = landingY(room,x,z);
                double y = s.groundY + s.eyeHeight - p.getEyeHeight(net.minecraft.world.entity.Pose.STANDING);
                double entryX=s.x,entryZ=s.z;
                var velocity=p.getDeltaMovement();
                boolean continuing=s.walking && s.approach && s.current!=null;
                if(continuing) {
                    double dx=p.getX()-s.anchorX,dz=p.getZ()-s.anchorZ,length=Math.hypot(dx,dz);
                    if(length>.001){entryX-=dx/length;entryZ-=dz/length;}
                }
                p.stopRiding(); p.teleportTo(room,entryX,s.walking ? s.groundY : y,entryZ,p.getYRot(),p.getXRot());
                movement(p,s);
                if(continuing)p.setDeltaMovement(velocity.x,0,velocity.z);
                s.anchorX=s.x;s.anchorY=y;s.anchorZ=s.z;
                s.current = s.pending; s.pending = null; s.ticket = ticket; s.busy = false; s.preparing = false; s.approach = false; s.terrainReady = false;
                StreetEdits.snapshot(p);
                StreetEdits.importNearby(p);
                send(p,ticket,"commit",viewMetadata(s).toString(),s.x,y,s.z);
            } catch (Exception e) { fail(p,s,ticket,"Could not enter Street View. Use /earth back if return-point recovery is needed."); }
    }
    private static boolean walkingTarget(ServerPlayer p, Session s, String pano, double threshold) {
        var links = s.current.getAsJsonArray("links");
        double[] headings = new double[links.size()];
        for (int i=0;i<headings.length;i++) headings[i]=links.get(i).getAsJsonObject().get("heading").getAsDouble();
        int index=StreetNavigation.walkingLink(headings,p.getX()-s.anchorX,p.getZ()-s.anchorZ,threshold);
        return index>=0 && links.get(index).getAsJsonObject().get("panoId").getAsString().equals(pano);
    }
    private static JsonObject viewMetadata(Session s) {
        JsonObject data = s.current.deepCopy();
        data.addProperty("rwmcEyeHeight",s.eyeHeight);
        data.addProperty("rwmcGroundY",s.groundY);
        data.addProperty("rwmcWalking",s.walking);
        return data;
    }
    private static void movement(ServerPlayer p, Session s) {
        p.stopRiding();p.stopFallFlying();p.setNoGravity(true);
        p.setDeltaMovement(0,0,0);p.fallDistance=0;
        if (p.getAbilities().flying) { p.getAbilities().flying=false;p.onUpdateAbilities(); }
    }
    static double landingY(ServerLevel room, int x, int z) {
        var originals = StreetEdits.storage(room).original;
        for (int distance=0;distance<=24;distance++) for (int sign : new int[]{1,-1}) {
            int y = 64+distance*sign; BlockPos floor = new BlockPos(x,y-1,z);
            // Keep the photo's ground baseline even after TNT removes it or a block is placed above it.
            var ground = originals.getOrDefault(floor.asLong(),room.getBlockState(floor));
            var lower = originals.getOrDefault(floor.above().asLong(),room.getBlockState(floor.above()));
            var upper = originals.getOrDefault(floor.above(2).asLong(),room.getBlockState(floor.above(2)));
            if (ground.isCollisionShapeFullBlock(room,floor) && lower.getCollisionShape(room,floor.above()).isEmpty()
                && upper.getCollisionShape(room,floor.above(2)).isEmpty()
                && lower.getFluidState().isEmpty() && upper.getFluidState().isEmpty()) return y;
        }
        // Missing or occluded ground must not send a photo stop to a building's roof.
        return 64;
    }
    public static int cameraHeight(CommandSourceStack src, double height) {
        ServerPlayer p = src.getPlayer();
        Session s = p == null ? null : SESSIONS.get(p.getUUID());
        if (s == null || s.current == null || s.busy || !p.level().dimension().equals(ROOM)) {
            src.sendFailure(RWMessages.message("Enter Street View and wait for the photo to finish loading first.")); return 0;
        }
        if (!Double.isFinite(height) || height < 1 || height > 4) return 0;
        s.eyeHeight = height;
        s.anchorY = s.groundY + height - p.getEyeHeight(net.minecraft.world.entity.Pose.STANDING);
        if (!s.walking) { p.teleportTo(s.anchorX,s.anchorY,s.anchorZ);p.setDeltaMovement(0,0,0);p.fallDistance=0; }
        send(p,s.ticket,"anchor",viewMetadata(s).toString(),s.anchorX,s.anchorY,s.anchorZ);
        say(p,String.format(Locale.ROOT,"Photo camera height: %.2f m. Block size remains 1 metre. This height follows you between photo stops until /earth back.",height));
        return 1;
    }
    private static void saveReturn(ServerPlayer p) throws Exception {
        if (Files.exists(file(p))) return;
        ReturnPoint point = new ReturnPoint(p.level().dimension().location().toString(),p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot(),p.isInvulnerable(),p.isNoGravity(),p.getAbilities().flying);
        Files.createDirectories(file(p).getParent()); Files.writeString(file(p),GSON.toJson(point),StandardOpenOption.CREATE_NEW);
    }
    public static int legacy(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer(); if (p==null) return 0;
        try {
            var level = p.server.getLevel(LEGACY); if (level==null) return 0;
            saveReturn(p); SESSIONS.remove(p.getUUID());
        p.noPhysics=p.isSpectator(); send(p,++nextTicket,"clear","",0,0,0);
            ReturnPoint saved = GSON.fromJson(Files.readString(file(p)),ReturnPoint.class);
            p.setNoGravity(saved.noGravity);
            p.getAbilities().flying=saved.flying && p.getAbilities().mayfly;p.onUpdateAbilities();
            p.teleportTo(level,.5,level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,0,0),.5,p.getYRot(),0);
            say(p,"Previous flat sandbox. Existing builds are preserved. /earth back returns."); return 1;
        } catch (Exception e) { src.sendFailure(RWMessages.message("Could not enter the previous sandbox.")); return 0; }
    }
    public static int exit(CommandSourceStack src) { if (src.getPlayer()!=null) restore(src.getPlayer()); return 1; }
    public static void restore(ServerPlayer p) {
        SESSIONS.remove(p.getUUID());
        if (ServerPlayNetworking.canSend(p,PanoramaPayload.TYPE)) send(p,++nextTicket,"clear","",0,0,0);
        try {
            if (!Files.exists(file(p))) return;
            ReturnPoint r = GSON.fromJson(Files.readString(file(p)),ReturnPoint.class);
            var level = p.server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(r.dimension)));
            if (level==null) { say(p,"Return dimension unavailable. Recovery file retained."); return; }
            p.teleportTo(level,r.x,r.y,r.z,r.yaw,r.pitch); p.setInvulnerable(r.invulnerable); p.setNoGravity(r.noGravity); p.setDeltaMovement(0,0,0); p.fallDistance=0;
            p.getAbilities().flying=r.flying && p.getAbilities().mayfly;p.onUpdateAbilities();
            p.server.getPlayerList().saveAll(); Files.delete(file(p));
        } catch (Exception e) { say(p,"Could not restore your return point. Use /earth back to retry."); }
    }
    public static void tick(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Session s = SESSIONS.get(p.getUUID());
            if(s!=null && s.current!=null && p.isAlive() && p.level().dimension().equals(ROOM)){
                p.stopRiding();p.stopFallFlying();p.setNoGravity(true);p.fallDistance=0;
                if (p.getAbilities().flying) { p.getAbilities().flying=false;p.onUpdateAbilities(); }
                if (s.approach && s.busy && s.terrainReady && s.pending != null
                        && walkingTarget(p,s,s.pending.get("panoId").getAsString(),StreetNavigation.WALK_TRANSITION)) commitPhoto(p,s,s.request);
                if (s.walking) {
                    double x=StreetNavigation.walkingCoordinate(p.getX(),s.anchorX), z=StreetNavigation.walkingCoordinate(p.getZ(),s.anchorZ);
                    if (StreetNavigation.outsideWalkingHeight(p.getY(),s.groundY)) {
                        // Never repair a crater to rescue the player. Return to the stable photo viewpoint.
                        s.walking=false;movement(p,s);p.teleportTo(s.anchorX,s.anchorY,s.anchorZ);
                        send(p,s.ticket,"movement",viewMetadata(s).toString(),s.anchorX,s.anchorY,s.anchorZ);
                    } else if (x!=p.getX() || z!=p.getZ()) {
                        var velocity=p.getDeltaMovement();p.teleportTo(x,p.getY(),z);p.setDeltaMovement(0,velocity.y,0);
                    }
                } else {
                    p.setDeltaMovement(0,0,0);
                    if(p.distanceToSqr(s.anchorX,s.anchorY,s.anchorZ)>0.0001)p.teleportTo(s.anchorX,s.anchorY,s.anchorZ);
                }
                if (server.getTickCount()%5==0) collectNearby(p);
            }
            if (s != null && s.busy && (server.getTickCount()-s.started>(s.approach ? 1200 : 6000) || !p.level().dimension().equals(s.origin))) fail(p,s,s.request,"Street View travel cancelled or timed out. Your existing blocks have been kept.");
        }
    }
    static void collectNearby(ServerPlayer p) {
        if(p.isSpectator())return;
        // Vanilla pickup keeps delay, ownership and inventory limits; never duplicate or pull through walls.
        for (var item : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(3))) {
            // Creative vanilla pickup can discard an item when completely full. Assistance must not.
            if(p.getInventory().getFreeSlot()<0 && p.getInventory().getSlotWithRemainingSpace(item.getItem())<0)continue;
            if (p.distanceToSqr(item)<=9 && p.hasLineOfSight(item)) item.playerTouch(p);
        }
    }
    public static void clear() { SESSIONS.clear(); PanoramaPlaces.clear(); }
    public static void disconnected(ServerPlayer p) { SESSIONS.remove(p.getUUID()); }
}
