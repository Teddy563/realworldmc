package dev.realworldmc.client;

import dev.realworldmc.RWMessages;
import dev.realworldmc.EarthRegions;
import dev.realworldmc.EarthConfig;
import dev.realworldmc.OrbitLanding;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Creative filming controls with a stable player anchor and a rotating geographic viewpoint. */
public final class SpaceFlight {
    private static volatile boolean enabled;
    private static Vec3 departure;
    private static final GlobeOrbit orbit = new GlobeOrbit();
    private static final GlobeOrbit planetaryOrbit = new GlobeOrbit();
    private static CelestialBody body=CelestialBody.EARTH;
    private static double earthHeight;
    private static final FlightPose pose = new FlightPose();
    private static volatile int session;
    private static boolean landing, anchored, autoSuppressed;
    private static double height, previousHeight, verticalVelocity;
    private static OrbitLanding.Arrival arrival;
    private SpaceFlight() {}

    public static boolean enabled() { return enabled; }
    public static CelestialBody body() { return body; }
    public static boolean anchored() { return enabled && anchored; }
    public static boolean posed(net.minecraft.world.entity.Entity entity) { return (enabled || CreativeFlight.enabled()) && entity == Minecraft.getInstance().player; }
    public static FlightPose pose() { return CreativeFlight.enabled() ? CreativeFlight.pose() : pose; }
    public static org.joml.Quaterniond orbitRotation(float partial) { return enabled ? activeOrbit().rotation(partial) : new org.joml.Quaterniond(); }
    private static GlobeOrbit activeOrbit() { return body==CelestialBody.EARTH ? orbit : planetaryOrbit; }
    public static Vec3 mapCamera(Vec3 camera, EarthRegions.Region region, float partial) {
        var player = Minecraft.getInstance().player;
        return anchored() && player != null ? GlobeOrbit.cameraInMap(camera, player.getPosition(partial), region, orbit.rotation(partial), altitude(partial)) : camera;
    }
    public static double altitude() { return altitude(1); }
    public static double altitude(float partial) {
        var mc = Minecraft.getInstance();
        return anchored() ? previousHeight+(height-previousHeight)*Math.clamp(partial,0,1) : mc.player==null ? 0 : physicalHeight(mc);
    }
    private static double physicalHeight(Minecraft mc) {
        var region=EarthRegions.at(mc.player.getX(),mc.player.getZ());
        if(region==null || !Double.isFinite(region.groundHeight()))return Math.max(0,mc.player.getY()-64);
        return Math.max(0,dev.realworldmc.Geo.mcToGeodetic(region.latitude(),region.longitude(),region.groundHeight(),
            mc.player.getX()-region.offsetX(),mc.player.getY(),mc.player.getZ()-region.offsetZ())[2]-region.groundHeight());
    }
    public static double exitHeight() { return EarthConfig.get().spaceEntryHeight()*.8; }
    public static double verticalSpeed(double altitude) {
        // Metres per tick: precise near terrain, fast enough to reach a full globe in about a minute.
        return Math.min(250_000, Math.max(1, (Math.max(0, altitude) + 500) / 45));
    }
    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String[] words = command.strip().split("\\s+");
            if(words.length==2 && (words[0].equals("rw") || words[0].equals("earth"))) {
                for(var target:CelestialBody.values())if(words[1].equals(target.id())) {visit(target);return false;}
            }
            if (words.length < 2 || !(words[0].equals("earth") || words[0].equals("rw")) || !words[1].equals("space")) return true;
            if (words.length > 3 || (words.length == 3 && !java.util.Set.of("on", "off", "return").contains(words[2]))) return true;
            change(words.length == 2 ? "on" : words[2]);
            return false;
        });
        // Before vanilla movement, after the previous frame's add-on controls.
        ClientTickEvents.START_CLIENT_TICK.register(SpaceFlight::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { stop(client); departure = null; autoSuppressed=false; });
    }
    public static void visit(CelestialBody target) {
        var mc=Minecraft.getInstance();
        if(mc.player==null)return;
        if(!enabled)change("on");
        if(!enabled || landing || target==body)return;
        if(body==CelestialBody.EARTH)earthHeight=Math.max(altitude(),EarthConfig.get().spaceEntryHeight());
        body=target;anchored=true;verticalVelocity=0;
        if(target==CelestialBody.EARTH)height=previousHeight=earthHeight;
        else {planetaryOrbit.reset();height=previousHeight=target.radius*.8;}
        pose.reset(mc.player.getYRot());mc.player.setDeltaMovement(Vec3.ZERO);
        ActionBar.show(RWMessages.message(target.title+" orbit · /earth space return: launch point"));
    }
    private static void change(String action) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (action.equals("off") && !anchored) { stop(mc); autoSuppressed=true; return; }
        if (!mc.hasSingleplayerServer() || !mc.player.isCreative() || mc.level == null || mc.level.dimension() != Level.OVERWORLD || !MapRenderer.INSTANCE.enabled()) {
            mc.player.displayClientMessage(RWMessages.message("Experimental space flight needs a Creative, singleplayer Real World world."), false);
            return;
        }
        if (action.equals("return") || action.equals("off")) {
            if (departure == null) return;
            Vec3 target = departure;
            MapRenderer.INSTANCE.cancelLanding();stop(mc); autoSuppressed=true;
            // The integrated server performs the return, not a client-only position change.
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                if (player != null && player.level().dimension() == Level.OVERWORLD)
                    player.teleportTo(target.x, target.y, target.z);
            });
            departure = null;
            return;
        }
        if (!enabled) {
            if (mc.player.isPassenger()) {
                mc.player.displayClientMessage(RWMessages.message("Leave your vehicle before starting /earth space."), false);
                return;
            }
            start(mc);
        }
    }
    private static void start(Minecraft mc) {
        departure=mc.player.position();body=CelestialBody.EARTH;orbit.reset();planetaryOrbit.reset();pose.reset(mc.player.getYRot());autoSuppressed=false;
        height=previousHeight=physicalHeight(mc); verticalVelocity=mc.player.getDeltaMovement().y;
        enabled=true; anchored=height>=EarthConfig.get().spaceEntryHeight();
        mc.player.getAbilities().flying=true; mc.player.onUpdateAbilities();
        if(anchored)mc.player.setDeltaMovement(Vec3.ZERO);
        ActionBar.show(RWMessages.message("Space flight · /earth space return: back"));
    }
    private static void stop(Minecraft mc) {
        if (enabled && mc.player != null) {
            mc.player.setDeltaMovement(Vec3.ZERO);
            // Keep ordinary Creative flight on: leaving orbit must not start a long fall.
        }
        enabled = false; anchored=false; arrival=null; verticalVelocity=0;
        session++; landing = false;
        orbit.reset();
        planetaryOrbit.reset();body=CelestialBody.EARTH;
    }
    static void completeArrival(Minecraft mc) {
        if(landing && arrival!=null && mc.player!=null && mc.player.position().distanceTo(arrival.position())<1) {
            MapRenderer.INSTANCE.finishLanding(arrival.region());
            stop(mc);autoSuppressed=true;ActionBar.show(RWMessages.message("Local flight · Shift: down"));
        }
    }
    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !mc.hasSingleplayerServer() || mc.level.dimension() != Level.OVERWORLD || !mc.player.isCreative() || !MapRenderer.INSTANCE.enabled()) {
            if(enabled)stop(mc); departure=null; return;
        }
        if(!enabled) {
            double localHeight=physicalHeight(mc);
            if(localHeight<exitHeight())autoSuppressed=false;
            var region=EarthRegions.at(mc.player.getX(),mc.player.getZ());
            if(!EarthConfig.get().autoSpaceFlight || autoSuppressed || mc.screen!=null || mc.isPaused() || mc.player.isPassenger()
                || !(mc.player.getAbilities().flying || mc.player.isFallFlying()) || region==null || !Double.isFinite(region.groundHeight())
                || localHeight<EarthConfig.get().spaceEntryHeight())return;
            start(mc);
        }
        if (landing) {
            mc.player.setDeltaMovement(Vec3.ZERO); orbit.hold(); previousHeight=height;
            // Server completion may precede its teleport packet. Keep the view until the client arrives.
            completeArrival(mc);
            return;
        }
        if(departure == null || mc.player.isPassenger() || EarthRegions.indexAt(mc.player.getX(),mc.player.getZ())!=EarthRegions.indexAt(departure.x,departure.z)) {stop(mc);departure=null;return;}
        if (mc.isPaused()) return;
        if (mc.screen != null) { mc.player.setDeltaMovement(Vec3.ZERO);activeOrbit().hold();verticalVelocity=0;previousHeight=height;return; }
        mc.player.getAbilities().flying = true;
        mc.player.fallDistance = 0;
        int direction = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
        int forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        int right=(mc.options.keyRight.isDown()?1:0)-(mc.options.keyLeft.isDown()?1:0);
        forward=CreativeFlight.rocketForward(forward,right,direction);
        pose.tick(forward,right,direction,mc.player.getYRot());
        if(!anchored && physicalHeight(mc)>=EarthConfig.get().spaceEntryHeight()) {
            anchored=true;height=previousHeight=physicalHeight(mc);mc.player.setDeltaMovement(Vec3.ZERO);
        }
        double currentHeight=altitude();
        double lift = Math.clamp(direction - (CreativeFlight.enabled() ? Math.sin(Math.toRadians(mc.player.getXRot())) * forward : 0), -1, 1);
        verticalVelocity+=(lift*verticalSpeed(currentHeight)*CreativeFlight.speedFactor()-verticalVelocity)*.2;
        if(direction==0 && Math.abs(verticalVelocity)<.01)verticalVelocity=0;
        if (anchored) {
            previousHeight=height;
            double floor=body==CelestialBody.EARTH ? exitHeight()-50 : body.minimumAltitude;
            height=Math.clamp(height+verticalVelocity,floor,20_000_000);
            if(height==floor || height==20_000_000)verticalVelocity=0;
            activeOrbit().tick(height,mc.player.getYRot(),forward,right,body.radius,CreativeFlight.speedFactor());
            // Geographic altitude changes; Steve stays at a stable Minecraft position for filming.
            mc.player.setDeltaMovement(Vec3.ZERO);
            if(body==CelestialBody.EARTH && lift<0 && height<=exitHeight())beginLanding(mc);
        } else {
            double yaw = Math.toRadians(mc.player.getYRot());
            double cruise=Math.min(12,Math.max(.4,currentHeight/100))*CreativeFlight.speedFactor()/Math.max(1,Math.hypot(forward,right));
            double movement=Math.clamp(verticalVelocity,mc.level.getMinBuildHeight()+2-mc.player.getY(),20_000_000-mc.player.getY());
            mc.player.setDeltaMovement((-Math.sin(yaw)*forward+Math.cos(yaw)*right)*cruise,movement,(Math.cos(yaw)*forward+Math.sin(yaw)*right)*cruise);
        }
    }
    private static boolean beginLanding(Minecraft mc) {
        var region = EarthRegions.at(mc.player.getX(), mc.player.getZ());
        if (region == null || !Double.isFinite(region.groundHeight())) return false;
        var point = mapCamera(mc.player.position(), region, 1);
        var location = dev.realworldmc.Geo.mcToGeodetic(region.latitude(),region.longitude(),region.groundHeight(),
            point.x-region.offsetX(),point.y,point.z-region.offsetZ());
        // Preserve geographic heading when the destination's tangent frame changes.
        double heading=Math.toRadians(mc.player.getYRot());
        var ahead=mapCamera(mc.player.position().add(-Math.sin(heading)*100,0,Math.cos(heading)*100),region,1);
        var aheadGeo=dev.realworldmc.Geo.mcToGeodetic(region.latitude(),region.longitude(),region.groundHeight(),
            ahead.x-region.offsetX(),ahead.y,ahead.z-region.offsetZ());
        var localAhead=dev.realworldmc.Geo.geodeticToMc(location[0],location[1],location[2],aheadGeo[0],aheadGeo[1],aheadGeo[2]);
        float yaw=(float)Math.toDegrees(Math.atan2(-localAhead[0],localAhead[2])),pitch=mc.player.getXRot();
        landing=true; orbit.hold(); verticalVelocity=0;previousHeight=height;mc.player.setDeltaMovement(Vec3.ZERO);
        MapRenderer.INSTANCE.preserveForLanding();
        int ticket=++session; var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
        server.execute(() -> {
            var player=server.getPlayerList().getPlayer(id);
            if(player==null || ticket!=session)return;
            dev.realworldmc.OrbitLanding.begin(player,location,yaw,pitch,()->ticket==session, (result,failure)->mc.execute(()->{
                if(ticket!=session)return;
                if(failure==null) arrival=result;
                else { landing=false;MapRenderer.INSTANCE.cancelLanding(); mc.options.keyShift.setDown(false); ActionBar.show(RWMessages.message("Descent unavailable · retry Shift or /earth space return")); }
            }));
        });
        return true;
    }
}
