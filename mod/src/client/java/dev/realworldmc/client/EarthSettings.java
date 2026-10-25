package dev.realworldmc.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.realworldmc.ServiceClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores and manages dynamic Earth rendering quality and distance settings.
 */
public final class EarthSettings {
    public static final int DEFAULT_FINE_RADIUS = 160;
    public static final int DEFAULT_FAR = 8000;
    public static final int DEFAULT_MAX_TILES = 900;
    public static final int DEFAULT_MAX_SSE = 12;

    public static final int[] FINE_RADIUS_PRESETS = { 96, 160, 240, 320, 480, 640 };
    public static final int[] FAR_PRESETS = { 4000, 8000, 12000, 16000, 24000 };
    public static final int[] MAX_TILES_PRESETS = { 600, 900, 1200, 1600, 2400 };
    public static final int[] MAX_SSE_PRESETS = { 18, 12, 8, 4 };

    private static int fineRadius = DEFAULT_FINE_RADIUS;
    private static int far = DEFAULT_FAR;
    private static int maxTiles = DEFAULT_MAX_TILES;
    private static int maxSse = DEFAULT_MAX_SSE;

    private static boolean loaded = false;

    private EarthSettings() {}

    public static int getFineRadius() { ensureLoaded(); return fineRadius; }
    public static int getFar() { ensureLoaded(); return far; }
    public static int getMaxTiles() { ensureLoaded(); return maxTiles; }
    public static int getMaxSse() { ensureLoaded(); return maxSse; }

    public static void setFineRadius(int v) { fineRadius = v; }
    public static void setFar(int v) { far = v; }
    public static void setMaxTiles(int v) { maxTiles = v; }
    public static void setMaxSse(int v) { maxSse = v; }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path file = getConfigFile();
            if (Files.exists(file)) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (json.has("render") && json.get("render").isJsonObject()) {
                    JsonObject r = json.getAsJsonObject("render");
                    if (r.has("fineRadius")) fineRadius = r.get("fineRadius").getAsInt();
                    if (r.has("far")) far = r.get("far").getAsInt();
                    if (r.has("maxTiles")) maxTiles = r.get("maxTiles").getAsInt();
                    if (r.has("maxSse")) maxSse = r.get("maxSse").getAsInt();
                }
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void save() {
        try {
            Path file = getConfigFile();
            JsonObject json = Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject();
            JsonObject r = json.has("render") && json.get("render").isJsonObject() ? json.getAsJsonObject("render") : new JsonObject();
            r.addProperty("fineRadius", fineRadius);
            r.addProperty("far", far);
            r.addProperty("maxTiles", maxTiles);
            r.addProperty("maxSse", maxSse);
            json.add("render", r);

            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json));

            try {
                Path serviceConfig = ServiceClient.directory().resolve("config.json");
                if (Files.exists(serviceConfig)) {
                    JsonObject sc = JsonParser.parseString(Files.readString(serviceConfig)).getAsJsonObject();
                    sc.add("render", r);
                    Files.writeString(serviceConfig, new GsonBuilder().setPrettyPrinting().create().toJson(sc));
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    public static void resetDefaults() {
        fineRadius = DEFAULT_FINE_RADIUS;
        far = DEFAULT_FAR;
        maxTiles = DEFAULT_MAX_TILES;
        maxSse = DEFAULT_MAX_SSE;
        save();
    }

    private static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("realworldmc-render.json");
    }
}
