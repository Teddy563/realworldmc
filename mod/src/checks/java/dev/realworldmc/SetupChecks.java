package dev.realworldmc;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class SetupChecks {
    private static int checks;
    private static void check(boolean ok) { if (!ok) throw new AssertionError("Setup check " + (checks + 1)); checks++; }
    public static void main(String[] args) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SetupCredentials.Transport good = (url, body) -> {
            calls.incrementAndGet();
            check(url.startsWith("https://"));
            return JsonParser.parseString(body == null ? "{\"root\":{}}" : "{\"session\":\"test\"}").getAsJsonObject();
        };
        check(!SetupCredentials.test(false, "", "", 1, good).ok()); check(calls.get() == 0);
        check(SetupCredentials.test(false, "test key", "street", 1, good).ok()); check(calls.get() == 2);
        check(SetupCredentials.test(false, "map", "", 1, good).ok()); check(calls.get() == 3);
        check(!SetupCredentials.test(true, "token", "", 1, (url, body) -> JsonParser.parseString("{\"url\":\"https://attacker.invalid/?key=secret\"}").getAsJsonObject()).ok());
        check(!SetupCredentials.test(false, "secret", "", 1, (url, body) -> { throw new Exception("secret"); }).message().contains("secret"));
        check(!SetupCredentials.test(false, "map", "bad-photo", 1, (url, body) -> JsonParser.parseString(body == null ? "{\"root\":{}}" : "{}").getAsJsonObject()).ok());
        Path dir = Files.createTempDirectory("rwmc-setup-check-");
        try {
            Files.createDirectories(dir.resolve("src")); Files.writeString(dir.resolve("src/server.mjs"), "// fixture");
            Files.writeString(dir.resolve("config.json"), "{\"port\":8765,\"cesiumAsset\":2275207,\"rootUrl\":\"https://old.invalid\"}");
            var firstSave = SetupCredentials.save(dir, true, "token", "street");
            check(firstSave.mapChanged() && firstSave.photoChanged());
            var unchanged = SetupCredentials.save(dir, true, "token", "street");
            check(!unchanged.mapChanged() && !unchanged.photoChanged());
            check(unchanged.guidance(false, true).contains("no restart needed"));
            check(unchanged.guidance(true, true).contains("reopen your world"));
            var photoOnly = SetupCredentials.save(dir, true, "token", "new-photo");
            check(!photoOnly.mapChanged() && photoOnly.photoChanged());
            check(photoOnly.guidance(false, true).contains("reloads the photo service"));
            SetupCredentials.save(dir, true, "token", "street");
            var config = SetupCredentials.read(dir.resolve("config.json"));
            check(config.get("port").getAsInt() == 8765); check(!config.has("rootUrl"));
            check(config.get("cesiumToken").getAsString().equals("token")); check(config.get("apiKey").getAsString().isEmpty());
            check(SetupCredentials.read(dir.resolve("streetview.config.json")).get("tilesKey").getAsString().equals("street"));
            check(!SetupCredentials.read(dir.resolve("streetview.config.json")).has("browserKey"));
            SetupCredentials.save(dir, false, "google", "");
            check(SetupCredentials.read(dir.resolve("config.json")).get("cesiumToken").getAsString().isEmpty());
            check(SetupCredentials.read(dir.resolve("streetview.config.json")).get("tilesKey").getAsString().isEmpty());
            var beforeWater = SetupCredentials.read(dir.resolve("config.json"));
            String photoBefore = Files.readString(dir.resolve("streetview.config.json"));
            SetupCredentials.saveWater(dir, false);
            var afterWater = SetupCredentials.read(dir.resolve("config.json"));
            check(!afterWater.get("water").getAsBoolean());
            afterWater.remove("water"); check(afterWater.equals(beforeWater));
            check(Files.readString(dir.resolve("streetview.config.json")).equals(photoBefore));
            SetupCredentials.saveWater(dir, true);
            check(SetupCredentials.read(dir.resolve("config.json")).get("water").getAsBoolean());
            Files.writeString(dir.resolve("config.json"), "broken");
            try { SetupCredentials.save(dir, true, "new", "new"); throw new AssertionError("Malformed config accepted"); } catch (com.google.gson.JsonParseException | IllegalStateException expected) { check(Files.readString(dir.resolve("config.json")).equals("broken")); }
        } finally { try (var paths = Files.walk(dir)) { for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); } }
        System.out.println(checks + " onboarding checks passed; no real provider requests.");
    }
}
