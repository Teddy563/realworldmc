package dev.realworldmc.client;

import dev.realworldmc.EarthRegions;
import dev.realworldmc.Geo;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/** Exact rigid change of local Earth frame; retained tiles need no vertex or texture rebuild. */
final class RegionFrame {
    private RegionFrame() {}
    private static Matrix3d basis(EarthRegions.Region r) {
        double lat=Math.toRadians(r.latitude()),lon=Math.toRadians(r.longitude());
        double sl=Math.sin(lat),cl=Math.cos(lat),so=Math.sin(lon),co=Math.cos(lon);
        return new Matrix3d(-so,co,0,cl*co,cl*so,sl,sl*co,sl*so,-cl);
    }
    static Matrix3d rotation(EarthRegions.Region from,EarthRegions.Region to) {
        return basis(to).transpose().mul(basis(from));
    }
    static Vec3 point(Vec3 point,EarthRegions.Region from,EarthRegions.Region to) {
        var source=Geo.toEcef(from.latitude(),from.longitude(),from.groundHeight());
        var target=Geo.toEcef(to.latitude(),to.longitude(),to.groundHeight());
        var p=basis(from).transform(new Vector3d(point.x-from.offsetX(),point.y-64,point.z-from.offsetZ()));
        p.add(source[0]-target[0],source[1]-target[1],source[2]-target[2]);
        basis(to).transpose().transform(p);
        return new Vec3(p.x+to.offsetX(),p.y+64,p.z+to.offsetZ());
    }
}
