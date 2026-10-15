package dev.realworldmc.client;

import net.minecraft.util.Mth;

/** Render-only motion. Looking around never changes the direction of a vertical climb. */
public final class FlightPose {
    private float pitch, oldPitch, yaw, oldYaw, thrust, oldThrust, bank, oldBank;
    public void reset(float heading) {
        pitch=oldPitch=thrust=oldThrust=bank=oldBank=0;
        yaw=oldYaw=heading;
    }
    public void tick(int forward, int right, int vertical, float heading) {
        oldPitch=pitch; oldYaw=yaw; oldThrust=thrust; oldBank=bank;
        double horizontal=Math.hypot(forward,right);
        boolean moving=horizontal>0 || vertical!=0;
        float targetPitch=moving ? (float)Math.toDegrees(Math.atan2(horizontal,vertical)) : 0;
        float targetYaw=horizontal>0 ? heading-(float)Math.toDegrees(Math.atan2(right,forward)) : yaw;
        pitch=Mth.lerp(.22f,pitch,targetPitch);
        yaw+=Mth.wrapDegrees(targetYaw-yaw)*.22f;
        thrust=Mth.lerp(.18f,thrust,moving?1:0);
        bank=Mth.lerp(.18f,bank,right*-12f);
    }
    /** Film flight has a flat glide posture or an upright hover, without changing the hitbox. */
    public void film(boolean glide, net.minecraft.world.phys.Vec3 movement, float heading) {
        oldPitch=pitch; oldYaw=yaw; oldThrust=thrust; oldBank=bank;
        boolean moving=movement.lengthSqr()>.0001;
        float targetYaw=moving && movement.horizontalDistanceSqr()>.0001
            ? (float)Math.toDegrees(Math.atan2(-movement.x,movement.z)) : heading;
        float targetPitch=glide && moving ? (float)Math.toDegrees(Math.atan2(movement.horizontalDistance(),movement.y)) : 0;
        pitch=Mth.lerp(.22f,pitch,targetPitch);
        yaw+=Mth.wrapDegrees(targetYaw-yaw)*.22f;
        thrust=Mth.lerp(.18f,thrust,glide && moving?1:0);
        bank=Mth.lerp(.18f,bank,glide?Mth.clamp(Mth.wrapDegrees(targetYaw-yaw)*.12f,-12,12):0);
    }
    public float pitch(float partial) { return Mth.lerp(partial,oldPitch,pitch); }
    public float yaw(float partial) { return Mth.rotLerp(partial,oldYaw,yaw); }
    public float thrust(float partial) { return Mth.lerp(partial,oldThrust,thrust); }
    public float bank(float partial) { return Mth.lerp(partial,oldBank,bank); }
}
