package dev.realworldmc.client;

import dev.realworldmc.EarthRegions;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Moves the geographic viewpoint while keeping Minecraft's player in one loaded region. */
public final class GlobeOrbit {
    private final Quaterniond rotation = new Quaterniond(), previous = new Quaterniond();
    private double speed, sideSpeed;
    private boolean travelled;

    public void reset() { rotation.identity(); previous.identity(); speed = sideSpeed = 0; travelled = false; }
    public boolean travelled() { return travelled; }
    public void hold() { previous.set(rotation); speed = sideSpeed = 0; }
    public Quaterniond rotation(float partial) { return previous.slerp(rotation, Math.clamp(partial, 0, 1), new Quaterniond()); }
    public void tick(double altitude, float yaw, int forward) {
        tick(altitude,yaw,forward,0);
    }
    public void tick(double altitude, float yaw, int forward, int right) {
        tick(altitude,yaw,forward,right,6_378_137);
    }
    public void tick(double altitude, float yaw, int forward, int right, double radius) {
        tick(altitude, yaw, forward, right, radius, 1);
    }
    public void tick(double altitude, float yaw, int forward, int right, double radius, double speedMultiplier) {
        previous.set(rotation);
        double rate = Math.min(500_000, Math.max(2, altitude * .055)) * Math.clamp(speedMultiplier, 0, 12) / Math.max(1,Math.hypot(forward,right));
        speed += (forward * rate - speed) * .3;
        sideSpeed += (right * rate - sideSpeed) * .3;
        if (forward == 0 && Math.abs(speed) < .01) speed = 0;
        if (right == 0 && Math.abs(sideSpeed) < .01) sideSpeed = 0;
        double length = Math.hypot(speed,sideSpeed);
        double angle = Math.clamp(length / (radius + altitude), 0, .02);
        if (angle == 0) return;
        double heading = Math.toRadians(yaw);
        double dx=(-Math.sin(heading)*speed+Math.cos(heading)*sideSpeed)/length;
        double dz=(Math.cos(heading)*speed+Math.sin(heading)*sideSpeed)/length;
        rotation.premul(new Quaterniond().rotationAxis(-angle, dz, 0, -dx)).normalize();
        travelled = true;
    }
    public static Vec3 centre(EarthRegions.Region region) {
        if (region == null) return new Vec3(0, 64 - 6_378_137, 0);
        double lat = Math.toRadians(region.latitude()), sin = Math.sin(lat), cos = Math.cos(lat);
        double e2 = 6.6943799901413165e-3, n = 6_378_137 / Math.sqrt(1 - e2 * sin * sin);
        return new Vec3(region.offsetX(), 64 - n * (1 - e2 * sin * sin) - region.groundHeight(),
            region.offsetZ() - n * e2 * sin * cos);
    }
    public static Vec3 cameraInMap(Vec3 camera, Vec3 centre, Quaterniond rotation) {
        Vec3 offset = camera.subtract(centre);
        var point = rotation.transformInverse(new Vector3d(offset.x, offset.y, offset.z));
        return centre.add(point.x, point.y, point.z);
    }
    /** Keep the subject above the WGS84 ellipsoid, including when crossing the poles. */
    public static Vec3 cameraInMap(Vec3 camera, Vec3 subject, EarthRegions.Region region, Quaterniond rotation) {
        return cameraInMap(camera,subject,region,rotation,subject.y-64);
    }
    public static Vec3 cameraInMap(Vec3 camera, Vec3 subject, EarthRegions.Region region, Quaterniond rotation, double altitude) {
        if (region == null) return cameraInMap(camera, centre(null), rotation);
        var raw = cameraInMap(subject, centre(region), rotation);
        var geo = dev.realworldmc.Geo.mcToGeodetic(region.latitude(), region.longitude(), region.groundHeight(),
            raw.x-region.offsetX(), raw.y, raw.z-region.offsetZ());
        var corrected = dev.realworldmc.Geo.geodeticToMc(region.latitude(), region.longitude(), region.groundHeight(),
            geo[0], geo[1], region.groundHeight()+altitude);
        var offset = camera.subtract(subject);
        var rotatedOffset = rotation.transformInverse(new Vector3d(offset.x,offset.y,offset.z));
        return new Vec3(region.offsetX()+corrected[0]+rotatedOffset.x, corrected[1]+rotatedOffset.y,
            region.offsetZ()+corrected[2]+rotatedOffset.z);
    }
}
