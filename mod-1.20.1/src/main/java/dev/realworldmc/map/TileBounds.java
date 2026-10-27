package dev.realworldmc.map;

/** A bounded, eastward Web Mercator rectangle. X may cross the date line. */
public record TileBounds(double west, double north, double east, double south,
                         int minX, int minY, int columns, int rows) {
    public static final int ZOOM = 17;
    public static final int COUNT = 1 << ZOOM;
    public static final int MAX_TILES = 64;

    public static TileBounds of(double northLat, double westLon, double southLat, double eastLon) {
        if (!Double.isFinite(northLat) || !Double.isFinite(westLon)
                || !Double.isFinite(southLat) || !Double.isFinite(eastLon)) return null;
        double west = (westLon + 180) / 360 * COUNT;
        double east = (eastLon + 180) / 360 * COUNT;
        if (east < west && west - east > COUNT / 2.0) east += COUNT;
        double north = y(northLat), south = y(southLat);
        if (!(east > west && south > north) || east - west > MAX_TILES || south - north > MAX_TILES) return null;
        int minX = (int) Math.floor(west), minY = (int) Math.floor(north);
        int columns = (int) Math.ceil(east) - minX, rows = (int) Math.ceil(south) - minY;
        if (columns <= 0 || rows <= 0 || (long) columns * rows > MAX_TILES) return null;
        return new TileBounds(west, north, east, south, minX, minY, columns, rows);
    }

    private static double y(double latitude) {
        double r = Math.toRadians(net.minecraft.util.Mth.clamp(latitude, -85.05112878, 85.05112878));
        return net.minecraft.util.Mth.clamp((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * COUNT, 0, COUNT);
    }

    public static long key(int x, int y) {
        return ((long) Math.floorMod(x, COUNT) << 32) | (net.minecraft.util.Mth.clamp(y, 0, COUNT - 1) & 0xffffffffL);
    }
}
