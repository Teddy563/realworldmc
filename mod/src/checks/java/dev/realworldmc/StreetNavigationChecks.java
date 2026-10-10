package dev.realworldmc;

/** Dependency-free geographic/navigation regressions; run by Gradle check. */
public final class StreetNavigationChecks {
    private static void check(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(StreetNavigation.closest(new double[]{359,90,180},1,25)==0,"North must wrap through zero");
        check(StreetNavigation.closest(new double[]{90,270},0,25)==-1,"No invented forward link at a T junction");
        check(StreetNavigation.closest(new double[]{Double.NaN,90},450,25)==1,"Finite headings and wrapped camera yaw");
        check(StreetNavigation.closest(new double[]{},180,60)==-1,"Dead-end panorama");
        check(!StreetNavigation.coordinates(Double.NaN,26) && !StreetNavigation.coordinates(44,181),"Reject invalid coordinates");
        check(!StreetNavigation.validPano("../../secrets?key=test") && StreetNavigation.validPano("AbC-123_x"),"Panorama IDs cannot inject URLs");
        double lat=44.428582856832655,lon=26.10111463938365,h=90;
        double[] a=Geo.geodeticToMc(lat,lon,h,lat,lon,h);
        double[] b=Geo.geodeticToMc(lat,lon,h,lat+.0001,lon+.0001,h);
        check(Math.abs(a[0])<1e-7 && Math.abs(a[2])<1e-7,"Original stop must stay at its origin");
        check(b[0]>7 && b[0]<9 && b[2]<-10 && b[2]>-12,"One block per metre, east +X and north -Z");
        double[] back=Geo.mcToGeodetic(lat,lon,h,b[0],b[1],b[2]);
        check(Math.abs(back[0]-lat-.0001)<1e-8 && Math.abs(back[1]-lon-.0001)<1e-8,"Forward/back travel must not accumulate geographic drift");
        // A placed object keeps its absolute coordinate; only the observer moves to the next photo.
        double objectX=50000+a[0]+3,objectZ=a[2]-2;
        check(Math.abs((objectX-(50000+b[0]))-(3-b[0]))<1e-7 && Math.abs((objectZ-b[2])-(-2-b[2]))<1e-7,"Object offset must move opposite the camera, not follow it");
        double[] dateline=Geo.geodeticToMc(0,179.999,0,0,-179.999,0);
        check(dateline[0]>200 && dateline[0]<230,"Date-line neighbours must stay nearby");
        check(StreetNavigation.walkingCoordinate(100000.7,100000)==100000.7,"Walking inside the area stays smooth at a distant origin");
        check(StreetNavigation.walkingCoordinate(100003,100000)==100001.5 && StreetNavigation.walkingCoordinate(99996,100000)==99998.5,"Both walk boundaries remain 3 metres apart");
        check(StreetNavigation.walkingCoordinate(Double.NaN,100000)==100000,"Invalid motion cannot escape the anchor");
        check(!StreetNavigation.outsideWalkingHeight(65.2,64) && StreetNavigation.outsideWalkingHeight(60,64),"Normal jumps work and deep craters trigger rescue");
        var fade=new CreditFade();fade.color(false,0);
        int opening=fade.color(true,60)>>>24;
        check(opening>96 && opening<255,"Opening chat must fade credits gradually");
        check((fade.color(true,2000)>>>24)==96,"Credits stay faintly visible while chat is open");
        check((fade.color(false,2060)>>>24)>96 && (fade.color(false,4000)>>>24)==255,"Closing chat must restore credits smoothly");
        var aFade=new CreditFade();var bFade=new CreditFade();aFade.color(false,0);bFade.color(false,0);
        for(int t=10;t<=120;t+=10)aFade.color(true,t);
        check(aFade.color(true,120)==bFade.color(true,120),"Fade must not depend on frame rate");
        System.out.println("19 geographic, navigation, walking and credit-fade checks passed.");
    }
}
