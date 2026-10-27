package dev.realworldmc.map;

public final class MapCompatibility {
    private MapCompatibility() { }
    public static boolean apply(String mixin, String minimap, String worldmap) {
        return switch (mixin.substring(mixin.lastIndexOf('.') + 1)) {
            case "MinimapFBORendererMixin" -> "26.6.0".equals(minimap);
            case "GuiMapMixin" -> "1.47.0".equals(worldmap);
            case "SupportXaeroWorldmapMixin" -> "26.6.0".equals(minimap) && "1.47.0".equals(worldmap);
            default -> true;
        };
    }
}
