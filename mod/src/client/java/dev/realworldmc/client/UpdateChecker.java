package dev.realworldmc.client;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.HttpURLConnection;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/** Read-only release notice. Never writes into a running game's mods directory. */
public final class UpdateChecker {
    public static final String RELEASES = "https://github.com/Teddy563/realworldmc/releases/latest";
    private static final String CURRENT = FabricLoader.getInstance().getModContainer("realworldmc").orElseThrow().getMetadata().getVersion().getFriendlyString();
    public record ReleaseInfo(String version, String name, String changelog, String downloadUrl, String jarName, long size, boolean isNewer) {}
    private static volatile ReleaseInfo latest;
    private static CompletableFuture<ReleaseInfo> request;
    private static volatile boolean failed;
    private UpdateChecker() {}
    public static String getCurrentVersion() { return CURRENT; }
    public static ReleaseInfo getLatestRelease() { return latest; }
    public static ReleaseInfo getAvailableUpdate() { return hasUpdate() ? latest : null; }
    public static boolean hasUpdate() { return latest != null && latest.isNewer(); }
    public static boolean failed() { return failed; }
    public static synchronized boolean isChecking() { return request != null && !request.isDone(); }
    public static synchronized CompletableFuture<ReleaseInfo> checkAsync() {
        if (isChecking()) return request;
        failed = false;
        request = CompletableFuture.supplyAsync(() -> {
            try {
                var connection = (HttpURLConnection) URI.create("https://api.github.com/repos/Teddy563/realworldmc/releases/latest").toURL().openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(20_000);
                connection.setRequestProperty("User-Agent", "RealWorldMC/" + CURRENT);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                try {
                    if (connection.getResponseCode() != 200) throw new java.io.IOException("Release lookup failed");
                    try (var stream = connection.getInputStream()) {
                    byte[] bytes = stream.readNBytes(1_048_577);
                    if (bytes.length > 1_048_576) throw new java.io.IOException("Release response too large");
                    var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                    String tag = json.get("tag_name").getAsString();
                    String version = tag.startsWith("v") ? tag.substring(1) : tag;
                    String game = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
                    String wanted = "RealWorldMC-" + version + "-mc" + game + ".jar";
                    String url = null; long size = 0;
                    var asset=dev.realworldmc.ReleaseMetadata.asset(json,version,game);
                    if(asset!=null){url=asset.get("browser_download_url").getAsString();size=asset.has("size")?asset.get("size").getAsLong():0;}
                    String body = json.has("body") && !json.get("body").isJsonNull() ? json.get("body").getAsString() : "";
                    latest = new ReleaseInfo(version, "RealWorldMC " + version, body, url, url == null ? null : wanted, size, isNewer(version, CURRENT));
                    return latest;
                    }
                } finally { connection.disconnect(); }
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                failed = true;
                return null;
            }
        });
        return request;
    }
    public static boolean isNewer(String remote, String current) {
        return dev.realworldmc.ReleaseMetadata.newer(remote,current);
    }
}
