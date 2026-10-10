package dev.realworldmc;
/** Headings are clockwise from geographic north. */
public final class StreetNavigation {
    public static final double WALK_HALF_WIDTH = 1.5;
    private StreetNavigation() {}
    public static double angleDistance(double a, double b) { return Math.abs(Math.IEEEremainder(a - b, 360)); }
    public static int closest(double[] headings, double heading, double limit) {
        int best = -1;
        for (int i = 0; i < headings.length; i++) {
            double distance = angleDistance(headings[i], heading);
            if (Double.isFinite(distance) && distance <= limit) { limit = distance; best = i; }
        }
        return best;
    }
    public static boolean coordinates(double lat, double lon) { return Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180; }
    public static boolean validPano(String value) { return value != null && value.matches("[\\w-]{1,256}"); }
    /** Shared by client prediction and the authoritative server, at full geographic precision. */
    public static double walkingCoordinate(double value, double anchor) {
        return Double.isFinite(value) ? Math.max(anchor-WALK_HALF_WIDTH, Math.min(anchor+WALK_HALF_WIDTH,value)) : anchor;
    }
    public static boolean outsideWalkingHeight(double y, double ground) {
        return !Double.isFinite(y) || y < ground-3 || y > ground+2;
    }
}
