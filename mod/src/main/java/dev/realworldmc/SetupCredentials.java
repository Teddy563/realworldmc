package dev.realworldmc;

import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;

/** Local-only onboarding. Provider responses and credentials never enter logs or chat. */
public final class SetupCredentials {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public record Result(boolean ok, String message) {}
    public interface Transport { JsonObject request(String url, String body) throws Exception; }
    public static final Transport NETWORK = (url, body) -> {
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20));
        if (body == null) builder.GET(); else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new ProviderFailure(response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    };
    private static final class ProviderFailure extends Exception {
        final int status;
        ProviderFailure(int status) { this.status = status; }
    }
    private SetupCredentials() {}
    public static JsonObject read(Path file) throws Exception {
        return Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject();
    }
    public static String value(JsonObject config, String name) {
        return config.has(name) && !config.get(name).isJsonNull() ? config.get(name).getAsString() : "";
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    public static Result test(boolean cesium, String key, String streetKey, long asset, Transport transport) {
        if (key.isBlank() || key.startsWith("YOUR_") || key.startsWith("PASTE_")) return new Result(false, "Enter your map key or Cesium token first.");
        if (asset <= 0) return new Result(false, "The Cesium asset ID must be positive.");
        try {
            String url = "https://tile.googleapis.com/v1/3dtiles/root.json?key=" + encode(key);
            if (cesium) {
                var endpoint = transport.request("https://api.cesium.com/v1/assets/" + asset + "/endpoint?access_token=" + encode(key), null);
                var options = endpoint.has("options") ? endpoint.getAsJsonObject("options") : endpoint;
                var root = URI.create(value(options, "url"));
                if (!"https".equals(root.getScheme()) || !"tile.googleapis.com".equals(root.getHost()) || root.getPort() != -1 || root.getUserInfo() != null)
                    return new Result(false, "This Cesium asset is not Google 3D Tiles. Check asset access in the guide.");
                url = root.toString();
            }
            if (!transport.request(url, null).has("root")) return new Result(false, "Map response was incomplete. Check the provider guide.");
        } catch (Exception error) { return failure("Map", error); }
        if (!streetKey.isBlank()) {
            try {
                var session = transport.request("https://tile.googleapis.com/v1/createSession?key=" + encode(streetKey), "{\"mapType\":\"streetview\",\"language\":\"en-US\",\"region\":\"US\"}");
                if (!session.has("session")) return new Result(false, "Map passed. Street View session failed. Check Map Tiles API access.");
            } catch (Exception error) { return failure("Map passed. Street View", error); }
        }
        return new Result(true, streetKey.isBlank() ? "Map access passed. Street View skipped (optional)." : "Map and Street View session passed. Photo coverage varies by place.");
    }
    private static Result failure(String provider, Exception error) {
        if (error instanceof ProviderFailure failure) {
            String help = switch (failure.status) {
                case 401, 403 -> "Check key, enabled API, restrictions, billing and regional eligibility.";
                case 429 -> "Quota reached. Check usage; waiting may help.";
                default -> "Provider unavailable. Retry later or open the guide.";
            };
            return new Result(false, provider + " failed (HTTP " + failure.status + "). " + help);
        }
        return new Result(false, provider + " could not connect. Check internet and the setup guide.");
    }
    public record SaveResult(boolean mapChanged, boolean photoChanged) {
        public String guidance(boolean folderChanged, boolean inWorld) {
            boolean map = mapChanged || folderChanged, photo = photoChanged || folderChanged;
            if (!map && !photo) return "Saved. Settings unchanged; no restart needed. You can continue playing.";
            String services = map && photo ? "map and photo services" : map ? "map service" : "photo service";
            return "Saved locally. No Minecraft restart needed. The mod reloads the " + services + ". "
                + (inWorld ? "Then return to the title screen and reopen your world to reload its map connections." : "Then create or open a Real World world.");
        }
    }
    public static SaveResult save(Path directory, boolean cesium, String key, String streetKey) throws Exception {
        Files.createDirectories(directory);
        Path mapFile = directory.resolve("config.json"), streetFile = directory.resolve("streetview.config.json");
        byte[] oldMap = Files.exists(mapFile) ? Files.readAllBytes(mapFile) : null;
        byte[] oldStreet = Files.exists(streetFile) ? Files.readAllBytes(streetFile) : null;
        var map = read(mapFile); var street = read(streetFile);
        var previousMap = map.deepCopy(); var previousStreet = street.deepCopy();
        map.addProperty("cesiumToken", cesium ? key : "");
        map.addProperty("apiKey", cesium ? "" : key);
        // A custom endpoint must not receive the new credential.
        map.remove("rootUrl");
        street.addProperty("tilesKey", streetKey);
        try { write(mapFile, JSON.toJson(map)); write(streetFile, JSON.toJson(street)); }
        catch (Exception failure) {
            if (oldMap == null) Files.deleteIfExists(mapFile); else Files.write(mapFile, oldMap);
            if (oldStreet == null) Files.deleteIfExists(streetFile); else Files.write(streetFile, oldStreet);
            throw failure;
        }
        return new SaveResult(!map.equals(previousMap), !street.equals(previousStreet));
    }
    /** Optional terrain setting; does not change provider credentials. */
    public static void saveWater(Path directory, boolean enabled) throws Exception {
        Files.createDirectories(directory);
        Path file = directory.resolve("config.json");
        var config = read(file);
        config.addProperty("water", enabled);
        write(file, JSON.toJson(config));
    }
    private static void write(Path file, String text) throws Exception {
        Path temp = Files.createTempFile(file.getParent(), ".rwmc-setup-", ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
