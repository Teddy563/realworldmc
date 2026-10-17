package dev.realworldmc.client.map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.realworldmc.EarthChunkGenerator;
import dev.realworldmc.EarthRegions;
import dev.realworldmc.Geo;
import dev.realworldmc.RealWorldMC;
import dev.realworldmc.client.MapRenderer;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapProjection {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Path TILE_DIR = Path.of(String.valueOf(FabricLoader.getInstance().getGameDir()), "realworldmc-tiles");
    private static final Pattern TILE_URL = Pattern.compile(".*/(\\d+)/(\\d+)/(\\d+)\\.png$");


    public static Path downloadTile(String url) throws IOException, InterruptedException {
        Matcher m = TILE_URL.matcher(url);
        if (!m.matches()) throw new IllegalArgumentException("Not a z/x/y.png tile URL: " + url);
        Path target = TILE_DIR.resolve(m.group(1)).resolve(m.group(2)).resolve(m.group(3) + ".png");
        if (Files.isRegularFile(target)) return target;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "RealWorldMC/1.0.2 (+https://github.com/Teddy563/realworldmc)")
                .GET()
                .build();
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "tile", ".part");
        try {
            HttpResponse<Path> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() != 200) {
                throw new IOException("Tile download failed with HTTP " + response.statusCode() + ": " + url);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        return target;
    }

    public static EarthRegions.Region regionAt(double worldX, double worldZ) {
        List<EarthRegions.Region> regions = MapRenderer.INSTANCE.getRegions();
        int index = EarthRegions.indexAt(worldX, worldZ);
        return regions != null && index >= 0 && index < regions.size() ? regions.get(index) : null;
    }

    public static double[] latLon(EarthRegions.Region region, double worldX, double worldZ) {
        return Geo.mcToGeodetic(region.latitude(), region.longitude(), region.groundHeight(),
                worldX - region.offsetX(), EarthChunkGenerator.GROUND_Y, worldZ - region.offsetZ());
    }

    // "Country, City" names from OSM Nominatim.
    // One line per cell: "<cell>\t<name>", an empty name means nothing there (e.g. open sea).
    private static final Path CITY_FILE = TILE_DIR.resolveSibling("realworldmc-cities.txt");
    private static final Map<Long, String> CITIES = loadCities();
    private static final ExecutorService GEOCODER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "geocoder");
        t.setDaemon(true);
        return t;
    });
    private static volatile long wantedCity = Long.MIN_VALUE;

    public static String cityAt(double latitude, double longitude) {
        long row = (long) Math.floor(latitude * 100), column = (long) Math.floor(longitude * 100);
        long cell = (row << 32) | (column & 0xFFFFFFFFL);
        String name = CITIES.get(cell);
        if (name == null && wantedCity != cell) {
            wantedCity = cell;
            GEOCODER.execute(() -> {
                if (wantedCity != cell || CITIES.containsKey(cell)) return; // cursor already moved on
                try {
                    String found = fetchCity((row + 0.5) / 100, (column + 0.5) / 100);
                    CITIES.put(cell, found);
                    Files.writeString(CITY_FILE, cell + "\t" + found + "\n", StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (Exception e) {
                    RealWorldMC.LOGGER.warn("City lookup failed: {}", e.toString());
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                if (wantedCity == cell) wantedCity = Long.MIN_VALUE;

            });
        }
        return name == null || name.isEmpty() ? null : name;
    }

    private static String fetchCity(double latitude, double longitude) throws IOException, InterruptedException {
        String url = String.format(Locale.ROOT,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&accept-language=en&lat=%.5f&lon=%.5f", latitude, longitude);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "RealWorldMC/1.0.2 (+https://github.com/Teddy563/realworldmc)")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("address")) return "";
        JsonObject address = json.getAsJsonObject("address");
        String city = null;
        for (String key : new String[] {"city", "town", "village", "municipality", "county", "state"}) {
            if (address.has(key)) {
                city = address.get(key).getAsString();
                break;
            }
        }
        String country = address.has("country") ? address.get("country").getAsString() : null;
        if (country == null) return city == null ? "" : city;
        return city == null ? country : country + ", " + city;
    }

    private static Map<Long, String> loadCities() {
        Map<Long, String> cities = new ConcurrentHashMap<>();
        try {
            if (Files.isRegularFile(CITY_FILE)) {
                for (String line : Files.readAllLines(CITY_FILE, StandardCharsets.UTF_8)) {
                    int tab = line.indexOf('\t');
                    if (tab > 0) cities.put(Long.parseLong(line.substring(0, tab)), line.substring(tab + 1));
                }
            }
        } catch (IOException | NumberFormatException e) {
            RealWorldMC.LOGGER.warn("Could not read {}: {}", CITY_FILE, e.toString());
        }
        return cities;
    }


}
