package dev.realworldmc.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import java.nio.file.Path;

final class SpaceViewChecks {
    static void run(Minecraft mc) throws Exception {
        MapContinuityChecks.run();
        var orbit = new GlobeOrbit();
        var region = new dev.realworldmc.EarthRegions.Region(1,44.4,26.1,120);
        var centre = GlobeOrbit.centre(region);
        for(var destination:java.util.List.of(new dev.realworldmc.EarthRegions.Region(3,-44.4,-153.9,50),
            new dev.realworldmc.EarthRegions.Region(4,89,170,800),new dev.realworldmc.EarthRegions.Region(5,44.5,26.2,120))) {
            var p=new net.minecraft.world.phys.Vec3(region.offsetX()+12000,2064,region.offsetZ()+7000);
            var changed=RegionFrame.point(p,region,destination);
            if(RegionFrame.point(changed,destination,region).distanceTo(p)>.00001)throw new AssertionError("Terrain handoff frame moved the scenery");
            var direction=new org.joml.Vector3d(10,2,-5);
            var actual=RegionFrame.point(p.add(direction.x,direction.y,direction.z),region,destination).subtract(changed);
            var expected=RegionFrame.rotation(region,destination).transform(direction);
            if(actual.distanceTo(new net.minecraft.world.phys.Vec3(expected.x,expected.y,expected.z))>.00001)throw new AssertionError("Retained terrain rotation disagrees with its camera");
            var geo=dev.realworldmc.Geo.mcToGeodetic(region.latitude(),region.longitude(),region.groundHeight(),p.x-region.offsetX(),p.y,p.z-region.offsetZ());
            var initial=GlobeOrbit.cameraInMap(p,p,region,new org.joml.Quaterniond(),geo[2]-region.groundHeight());
            if(initial.distanceTo(p)>.001)throw new AssertionError("Automatic entry jumps off-centre in a region");
        }
        var camera = new net.minecraft.world.phys.Vec3(50000,1000064,0);
        if(GlobeOrbit.cameraInMap(camera,centre,orbit.rotation(1)).distanceTo(camera)>.00001)throw new AssertionError("Identity orbit moved the view");
        double radius = camera.distanceTo(centre);
        orbit.tick(1000000,0,1);
        var forward=GlobeOrbit.cameraInMap(camera,centre,orbit.rotation(1));
        if(forward.z<=camera.z || Math.abs(forward.distanceTo(centre)-radius)>.00001)throw new AssertionError("W must move south at yaw 0 without changing altitude");
        var midway=GlobeOrbit.cameraInMap(camera,centre,orbit.rotation(.5f));
        if(!(midway.z>camera.z && midway.z<forward.z))throw new AssertionError("Orbit did not interpolate between ticks");
        orbit.reset();orbit.tick(1000000,90,1);
        if(GlobeOrbit.cameraInMap(camera,centre,orbit.rotation(1)).x>=camera.x)throw new AssertionError("Mouse heading did not steer west");
        orbit.reset();orbit.tick(1000000,0,-1);
        if(GlobeOrbit.cameraInMap(camera,centre,orbit.rotation(1)).z>=camera.z)throw new AssertionError("S did not reverse orbit");
        orbit.reset();orbit.tick(1000000,0,0,1);
        if(GlobeOrbit.cameraInMap(camera,centre,orbit.rotation(1)).x>=camera.x)throw new AssertionError("D must strafe west when facing south");
        for(int i=0;i<10000;i++)orbit.tick(12000000,i*.3f,1);
        if(Math.abs(GlobeOrbit.cameraInMap(camera,centre,orbit.rotation(1)).distanceTo(centre)-radius)>.001)throw new AssertionError("Repeated globe travel drifted off its sphere");
        for(double angle:new double[]{0,Math.PI/2,Math.PI}) {
            var q=new org.joml.Quaterniond().rotationX(angle);
            var subject=new net.minecraft.world.phys.Vec3(region.offsetX(),1664,region.offsetZ());
            var mapped=GlobeOrbit.cameraInMap(subject,subject,region,q);
            var geo=dev.realworldmc.Geo.mcToGeodetic(region.latitude(),region.longitude(),region.groundHeight(),mapped.x-region.offsetX(),mapped.y,mapped.z-region.offsetZ());
            if(Math.abs(geo[2]-region.groundHeight()-1600)>.1)throw new AssertionError("Orbital height intersects the ellipsoid: "+geo[2]);
            var farCamera=GlobeOrbit.cameraInMap(subject.add(0,2,-32),subject,region,q);
            if(Math.abs(farCamera.distanceTo(mapped)-Math.sqrt(1028))>.001)throw new AssertionError("Far follow-camera offset changed during orbit");
        }
        for(int i=0;i<100;i++)orbit.tick(12000000,0,0);
        var stopped=orbit.rotation(1);orbit.tick(12000000,0,0);
        if(!stopped.equals(orbit.rotation(1),1e-12))throw new AssertionError("Releasing W did not stop cruise");
        var original = new Matrix4f().perspective((float)Math.toRadians(70),1,.05f,8000);
        var zoomed=new Matrix4f().perspective((float)Math.toRadians(14),1,.05f,8000);
        var orbitalZoom=SpaceView.projection(zoomed,1000000);
        if(orbitalZoom.m00()!=zoomed.m00() || orbitalZoom.m11()!=zoomed.m11())throw new AssertionError("Orbit overrides optical zoom");
        for(double h : new double[]{1500,10000,100000,1000000,20000000}) {
            var projection = SpaceView.projection(original,h);
            var ground = new Vector4f(0,0,(float)-h,1).mul(projection);
            if(Math.abs(ground.z/ground.w)>=1)throw new AssertionError("Earth was clipped at altitude "+h);
            if(projection.m00()!=original.m00() || projection.m11()!=original.m11())throw new AssertionError("Space view changed the camera FOV");
        }
        if(SpaceView.atmosphere(0)!=0 || SpaceView.atmosphere(80000)!=1 || !original.equals(new Matrix4f().perspective((float)Math.toRadians(70),1,.05f,8000)))throw new AssertionError("Near-world projection or atmosphere changed");
        double previousSpeed=0;
        for(double altitude:new double[]{0,2000,12000,80000,1000000,10000000,20000000}) {
            double speed=SpaceFlight.verticalSpeed(altitude);
            if(speed<previousSpeed || speed>20000)throw new AssertionError("Ascent must accelerate with altitude and remain bounded");
            previousSpeed=speed;
        }
        double entry=dev.realworldmc.EarthConfig.get().spaceEntryHeight();
        if(Math.abs(SpaceFlight.verticalSpeed(entry)-CreativeFlight.MAX_SPEED)>.0001
            || SpaceFlight.verticalSpeed(entry+1)-SpaceFlight.verticalSpeed(entry-1)>.003)
            throw new AssertionError("Atmosphere entry must not jump in speed");
        double h=2000,velocity=0;int ascentTicks=0;
        while(h<12000000 && ascentTicks++<6000){velocity+=(SpaceFlight.verticalSpeed(h)-velocity)*.2;h+=velocity;}
        if(ascentTicks>=6000)throw new AssertionError("Full Earth view should remain reachable within five minutes");
        var pose=new FlightPose();pose.reset(0);
        for(int i=0;i<60;i++)pose.tick(0,0,1,i*11);
        if(Math.abs(pose.pitch(1))>.01 || Math.abs(pose.yaw(1))>.01 || pose.thrust(1)<.99)throw new AssertionError("Vertical climb must remain upright regardless of look direction");
        for(int i=0;i<60;i++)pose.tick(0,0,-1,90);
        if(Math.abs(pose.pitch(1)-180)>.01)throw new AssertionError("Shift descent must face down");
        for(int i=0;i<60;i++)pose.tick(1,0,0,90);
        if(Math.abs(pose.pitch(1)-90)>.01 || Math.abs(pose.yaw(1)-90)>.01)throw new AssertionError("Cruise must follow movement heading");
        for(int i=0;i<60;i++)pose.tick(0,0,0,90);
        if(Math.abs(pose.pitch(1))>.01 || pose.thrust(1)>.01)throw new AssertionError("Hover must relax out of the flight pose");
        var target = new TextureTarget(256,256,true,Minecraft.ON_OSX);
        try {
            target.bindWrite(true); RenderSystem.colorMask(true,true,true,true); RenderSystem.depthMask(true);
            RenderSystem.clearColor(.5f,.75f,1,1); RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
            SpaceView.sky(new Matrix4f(),original,1000000);
            {
                var pixels=java.nio.ByteBuffer.allocateDirect(256*256*4);GL11.glReadPixels(0,0,256,256,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                int dark=0,bright=0;
                for(int i=0;i<256*256;i++) { int r=pixels.get(i*4)&255; if(r<30)dark++; if(r>80)bright++; }
                if(dark<60000 || bright<10)throw new AssertionError("Orbital sky must show dark space and visible stars: dark="+dark+", bright="+bright);
            }
            // Capture the test framebuffer, without a world, provider connection or personal keys.
            try(var image=new NativeImage(256,256,false)) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,target.getColorTextureId());
                image.downloadTexture(0,false); image.flipY(); image.writeToFile(Path.of("space-sky-check.png"));
            }
        } finally { target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true); }
        SpaceGlobeChecks.run(mc);
        PlanetRenderingChecks.run(mc);
    }
}
