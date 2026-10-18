package dev.realworldmc.client.map;

import dev.realworldmc.EarthChunkGenerator;
import dev.realworldmc.EarthRegions;
import dev.realworldmc.Geo;
import dev.realworldmc.client.MapRenderer;
import java.util.List;

public final class MapProjection {
    private MapProjection() { }
    public static EarthRegions.Region regionAt(double worldX, double worldZ) {
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ)) return null;
        List<EarthRegions.Region> regions = MapRenderer.INSTANCE.getRegions();
        int index = EarthRegions.indexAt(worldX, worldZ);
        if (regions == null || index < 0 || index >= regions.size()) return null;
        EarthRegions.Region region = regions.get(index);
        return Double.isFinite(region.groundHeight()) ? region : null;
    }
    public static double[] latLon(EarthRegions.Region region, double worldX, double worldZ) {
        return Geo.mcToGeodetic(region.latitude(), region.longitude(), region.groundHeight(),
                worldX - region.offsetX(), EarthChunkGenerator.GROUND_Y, worldZ - region.offsetZ());
    }
}
