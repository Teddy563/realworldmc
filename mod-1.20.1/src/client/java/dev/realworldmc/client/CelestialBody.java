package dev.realworldmc.client;

/** Mean radii in metres. Planetary maps are bundled and require no map-provider credentials. */
public enum CelestialBody {
    EARTH("Earth",6_378_137,0,""),
    MOON("Moon",1_737_400,20_000,"NASA Scientific Visualization Studio / LROC, RealWorldMC"),
    MARS("Mars",3_389_500,40_000,"NASA / JPL-Caltech / USGS Viking, RealWorldMC");

    public final String title, credits;
    public final double radius, minimumAltitude;
    CelestialBody(String title,double radius,double minimumAltitude,String credits) {
        this.title=title;this.radius=radius;this.minimumAltitude=minimumAltitude;this.credits=credits;
    }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
}
