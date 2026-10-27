package dev.realworldmc;
public final class RegionBoundaryChecks {
    public static void main(String[] args) {
        int count=0;
        for(double angle=0;angle<Math.PI*2;angle+=.13) {
            double x=Math.cos(angle)*20_000,z=Math.sin(angle)*20_000;
            for(double speed:new double[]{1,100,5_000,50_000,1_000_000}) {
                double dx=Math.cos(angle)*speed,dz=Math.sin(angle)*speed;
                double t=RegionBoundary.fraction(x,z,dx,dz);
                if(Math.hypot(x+dx*t,z+dz*t)>RegionBoundary.MOVE_RADIUS+1e-6)throw new AssertionError("Crossed region boundary");
                count++;
            }
        }
        if(RegionBoundary.fraction(21_000,0,-100,0)!=1)throw new AssertionError("Must allow turning back");
        if(RegionBoundary.fraction(0,0,Double.NaN,0)!=0)throw new AssertionError("Nonfinite movement");
        System.out.println("PASS: "+count+" radial, negative, diagonal and high-speed boundary moves; return and invalid motion");
    }
}
