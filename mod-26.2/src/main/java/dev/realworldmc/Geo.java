package dev.realworldmc;

/** WGS84 helpers; mirror of service/src/geo.mjs (MC x = east, y = up + 64, z = south). */
public final class Geo {
	private static final double A = 6378137.0, F = 1 / 298.257223563, E2 = F * (2 - F);
	private static final double B = A * (1 - F), EP2 = (A * A - B * B) / (B * B);

	private Geo() {}

	public static double[] toEcef(double latDeg, double lonDeg, double h) {
		double lat = Math.toRadians(latDeg), lon = Math.toRadians(lonDeg);
		double sl = Math.sin(lat), cl = Math.cos(lat);
		double n = A / Math.sqrt(1 - E2 * sl * sl);
		return new double[] {(n + h) * cl * Math.cos(lon), (n + h) * cl * Math.sin(lon), (n * (1 - E2) + h) * sl};
	}

	public static double[] toGeodetic(double x, double y, double z) {
		double p = Math.hypot(x, y);
		double th = Math.atan2(z * A, p * B);
		double st = Math.sin(th), ct = Math.cos(th);
		double lat = Math.atan2(z + EP2 * B * st * st * st, p - E2 * A * ct * ct * ct);
		double lon = Math.atan2(y, x);
		double sl = Math.sin(lat);
		double n = A / Math.sqrt(1 - E2 * sl * sl);
		return new double[] {Math.toDegrees(lat), Math.toDegrees(lon), p / Math.cos(lat) - n};
	}

	/** Minecraft block coordinates -> {lat, lon, ellipsoid height}. */
	public static double[] mcToGeodetic(double originLat, double originLon, double originH, double mx, double my, double mz) {
		double lat = Math.toRadians(originLat), lon = Math.toRadians(originLon);
		double sl = Math.sin(lat), cl = Math.cos(lat), so = Math.sin(lon), co = Math.cos(lon);
		double[] o = toEcef(originLat, originLon, originH);
		double e = mx, n = -mz, u = my - EarthChunkGenerator.GROUND_Y;
		double x = o[0] - so * e - sl * co * n + cl * co * u;
		double y = o[1] + co * e - sl * so * n + cl * so * u;
		double z = o[2] + cl * n + sl * u;
		return toGeodetic(x, y, z);
	}

	/** {lat, lon, ellipsoid height} -> Minecraft block coordinates of a region with the given origin (without region offset). */
	public static double[] geodeticToMc(double originLat, double originLon, double originH, double lat, double lon, double h) {
		double la = Math.toRadians(originLat), lo = Math.toRadians(originLon);
		double sl = Math.sin(la), cl = Math.cos(la), so = Math.sin(lo), co = Math.cos(lo);
		double[] o = toEcef(originLat, originLon, originH);
		double[] p = toEcef(lat, lon, h);
		double dx = p[0] - o[0], dy = p[1] - o[1], dz = p[2] - o[2];
		double e = -so * dx + co * dy;
		double n = -sl * co * dx - sl * so * dy + cl * dz;
		double u = cl * co * dx + cl * so * dy + sl * dz;
		return new double[] {e, u + EarthChunkGenerator.GROUND_Y, -n};
	}
}
