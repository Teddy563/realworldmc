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

    private static volatile int fineRadius = DEFAULT_FINE_RADIUS;
    private static volatile int far = DEFAULT_FAR;
    private static volatile int maxTiles = DEFAULT_MAX_TILES;
    private static volatile int maxSse = DEFAULT_MAX_SSE;

    private static boolean loaded = false;

    private EarthSettings() {}

    public static int getFineRadius() { ensureLoaded(); return fineRadius; }
    public static int getFar() { ensureLoaded(); return far; }
    public static int getMaxTiles() { ensureLoaded(); return maxTiles; }
    public static int getMaxSse() { ensureLoaded(); return maxSse; }

    public static void setFineRadius(int v) { fineRadius = Math.max(96, Math.min(640, v)); }
    public static void setFar(int v) { far = Math.max(4000, Math.min(24000, v)); }
    public static void setMaxTiles(int v) { maxTiles = Math.max(600, Math.min(2400, v)); }
    public static void setMaxSse(int v) { maxSse = Math.max(4, Math.min(18, v)); }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path file = getConfigFile();
            if (Files.exists(file)) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (json.has("render") && json.get("render").isJsonObject()) {
                    JsonObject r = json.getAsJsonObject("render");
                    if (r.has("fineRadius")) setFineRadius(r.get("fineRadius").getAsInt());
                    if (r.has("far")) setFar(r.get("far").getAsInt());
                    if (r.has("maxTiles")) setMaxTiles(r.get("maxTiles").getAsInt());
                    if (r.has("maxSse")) setMaxSse(r.get("maxSse").getAsInt());
                }
            }
        } catch (Exception ignored) {}
    }

    public static synchronized boolean save() {
        Path file=getConfigFile(), temporary=null;
        try {
            JsonObject r=new JsonObject();
            r.addProperty("fineRadius", getFineRadius()); r.addProperty("far", getFar());
            r.addProperty("maxTiles", getMaxTiles()); r.addProperty("maxSse", getMaxSse());
            JsonObject json=new JsonObject(); json.add("render",r);
            Files.createDirectories(file.getParent());
            temporary=Files.createTempFile(file.getParent(), "realworldmc-render-", ".tmp");
            Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(json));
            try { Files.move(temporary,file,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch(java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temporary,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch(Exception failure) { return false; }
        finally { if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(Exception ignored){} }
    }

    public static void resetDefaults() {
        fineRadius = DEFAULT_FINE_RADIUS;
        far = DEFAULT_FAR;
        maxTiles = DEFAULT_MAX_TILES;
        maxSse = DEFAULT_MAX_SSE;
    }

    private static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("realworldmc-render.json");
    }
}
