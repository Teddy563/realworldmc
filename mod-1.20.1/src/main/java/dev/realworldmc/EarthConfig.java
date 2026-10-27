package dev.realworldmc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** RealWorldMC configuration with automatic import of existing settings. */
public class EarthConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static EarthConfig instance;

	/** Where the RealWorldMC tile service listens. */
	public String serviceUrl = "http://127.0.0.1:8765";
	/** Service folder, relative to the game instance or an existing absolute path. Blank uses the portable pack. */
	public String serviceDir = "";
	public String nodeExecutable = "node";
    /** Completed first-run setup; credentials live only in the local service configuration. */
    public boolean setupComplete = false;
    /** Show RealWorldMC controls/status above map credits; vanilla and other mods are unaffected. */
    public boolean actionBar = true;
    public boolean usageHud = true;
    public boolean controlsHud = true;
    /** Sneak + Swap Offhand, with empty hands, enters Street View from the ground. */
    public boolean streetViewShortcut = true;
    /** Optional Xaero overlay, independent of OSM water generation. */
    public boolean osmMap = true;
    public String osmTileUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
    /** Creative flight switches to an anchored globe view above this height over the region datum. */
    public boolean autoSpaceFlight = true;
    public double spaceEntryHeight = 2000;
    public double spaceEntryHeight() {
        return Double.isFinite(spaceEntryHeight) ? net.minecraft.util.Mth.clamp(spaceEntryHeight, 2000, 100000) : 2000;
    }

	/** Location used for NEW RealWorldMC worlds. Existing worlds keep the location they were created with. */
	public boolean randomSpawn = true;
	public double latitude = 44.427454;
	public double longitude = 26.087287;

	/** Render distance of the 3D map in blocks (Minecraft's own far plane is raised to this). */
	public int mapRenderDistance = 8000;

	/** Approximate outdoor photo camera height in metres; a photo supplies no measured depth. */
	public double photoEyeHeight = 2.5;
	public double photoEyeHeight() {
		return Double.isFinite(photoEyeHeight) && photoEyeHeight >= 1 && photoEyeHeight <= 4 ? photoEyeHeight : 2.5;
	}

	public static EarthConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("realworldmc.json");
	}

	private static EarthConfig load() {
		return load(FabricLoader.getInstance().getConfigDir());
	}

	static EarthConfig load(Path directory) {
		Path p = directory.resolve("realworldmc.json");
		Path previous = directory.resolve("earthcraft.json");
		EarthConfig cfg = new EarthConfig();
		try {
			Files.createDirectories(directory);
			Path source = Files.exists(p) ? p : previous;
			if (Files.exists(source)) {
				var json = com.google.gson.JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8));
                EarthConfig loaded = GSON.fromJson(json, EarthConfig.class);
				if (loaded == null) throw new IOException("Configuration must be a JSON object");
                // Retain deliberate custom defaults from old configs; upgrade the former Palace default.
                if (!json.getAsJsonObject().has("randomSpawn") &&
                    (loaded.latitude != 44.427454 || loaded.longitude != 26.087287)) loaded.randomSpawn = false;
				cfg = loaded;
				if (!Files.exists(p)) Files.copy(source, p);
			} else {
				Files.writeString(p, GSON.toJson(cfg), StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			RealWorldMC.LOGGER.error("Could not load configuration in {}; using defaults and preserving the file", directory, e);
		}
		return cfg;
	}

	public void save() {
		try {
			Files.writeString(path(), GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			RealWorldMC.LOGGER.error("Could not write {}", path(), e);
		}
	}
}
