package dev.realworldmc;

public final class RegionBoundary {
    public static final double PREPARE_RADIUS=18_000, MOVE_RADIUS=21_000, DATA_RADIUS=22_000;
    private RegionBoundary() {}
    /** Fraction of this horizontal move that remains inside the current geographic frame. */
    public static double fraction(double x,double z,double dx,double dz) {
        if (!Double.isFinite(x+z+dx+dz)) return 0;
        double end=Math.hypot(x+dx,z+dz);
        if(end<=MOVE_RADIUS) return 1;
        double a=dx*dx+dz*dz;
        if(a==0) return 1;
        double b=2*(x*dx+z*dz), c=x*x+z*z-MOVE_RADIUS*MOVE_RADIUS;
        if(c>0) return end<Math.hypot(x,z)?1:0;
        return Math.max(0,Math.min(1,(-b+Math.sqrt(Math.max(0,b*b-4*a*c)))/(2*a)));
    }
}
