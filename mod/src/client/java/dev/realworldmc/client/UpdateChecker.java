package dev.realworldmc.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Checks GitHub Releases for updates in the background and can download updated mod JARs.
 */
public final class UpdateChecker {
    private static final String RELEASES_API = "https://api.github.com/repos/Teddy563/realworldmc/releases/latest";
    private static final String CURRENT_VERSION = FabricLoader.getInstance()
            .getModContainer("realworldmc")
            .map(m -> m.getMetadata().getVersion().getFriendlyString())
            .orElse("1.0.3");

    public record ReleaseInfo(String version, String name, String changelog, String downloadUrl, String jarName, long size, boolean isNewer) {}

    private static volatile ReleaseInfo latestRelease = null;
    private static volatile boolean checked = false;
    private static volatile boolean checking = false;

    private UpdateChecker() {}

    public static String getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public static ReleaseInfo getLatestRelease() {
        return latestRelease;
    }

    public static ReleaseInfo getAvailableUpdate() {
        return (latestRelease != null && latestRelease.isNewer()) ? latestRelease : null;
    }

    public static boolean hasUpdate() {
        return latestRelease != null && latestRelease.isNewer();
    }

    public static boolean isChecking() {
        return checking;
    }

    public static CompletableFuture<ReleaseInfo> checkAsync() {
        if (checking) return CompletableFuture.completedFuture(latestRelease);
        checking = true;

        return CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = URI.create(RELEASES_API);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestProperty("User-Agent", "RealWorldMC-Updater/" + CURRENT_VERSION);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
                conn.setReadTimeout((int) Duration.ofSeconds(15).toMillis());

                if (conn.getResponseCode() != 200) {
                    return null;
                }

                try (InputStream in = conn.getInputStream();
                     InputStreamReader reader = new InputStreamReader(in)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : "";
                    String ver = tag.startsWith("v") ? tag.substring(1) : tag;

                    boolean newer = isNewer(ver, CURRENT_VERSION);
                    String name = json.has("name") && !json.get("name").isJsonNull() ? json.get("name").getAsString() : "RealWorldMC v" + ver;
                    String body = json.has("body") && !json.get("body").isJsonNull() ? json.get("body").getAsString() : "";

                    String downloadUrl = null;
                    String jarName = null;
                    long size = 0;

                    if (json.has("assets") && json.get("assets").isJsonArray()) {
                        JsonArray assets = json.getAsJsonArray("assets");
                        for (JsonElement el : assets) {
                            JsonObject asset = el.getAsJsonObject();
                            String aName = asset.get("name").getAsString();
                            // Prefer mc1.21.1 JAR asset
                            if (aName.endsWith(".jar") && !aName.endsWith("-sources.jar") && !aName.endsWith("-dev.jar")) {
                                if (aName.contains("1.21.1") || downloadUrl == null) {
                                    downloadUrl = asset.get("browser_download_url").getAsString();
                                    jarName = aName;
                                    size = asset.has("size") ? asset.get("size").getAsLong() : 0;
                                    if (aName.contains("1.21.1")) break;
                                }
                            }
                        }
                    }

                    latestRelease = new ReleaseInfo(ver, name, body, downloadUrl, jarName, size, newer);
                    return latestRelease;
                }
            } catch (Exception ignored) {
            } finally {
                checked = true;
                checking = false;
            }
            return latestRelease;
        });
    }

    /**
     * Downloads the update JAR into the mods folder.
     */
    public static CompletableFuture<Path> downloadUpdateAsync(ReleaseInfo release, ProgressCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (release.downloadUrl() == null) throw new IllegalStateException("No download URL for this release");
                Path gameDir = FabricLoader.getInstance().getGameDir();
                Path modsDir = gameDir.resolve("mods");
                if (!Files.exists(modsDir)) Files.createDirectories(modsDir);

                String targetFileName = release.jarName() != null ? release.jarName() : ("RealWorldMC-" + release.version() + "-mc1.21.1.jar");
                Path tempFile = modsDir.resolve(targetFileName + ".tmp");
                Path targetFile = modsDir.resolve(targetFileName);

                URI uri = URI.create(release.downloadUrl());
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestProperty("User-Agent", "RealWorldMC-Updater/" + CURRENT_VERSION);
                conn.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
                conn.setReadTimeout((int) Duration.ofSeconds(60).toMillis());

                long totalBytes = conn.getContentLengthLong();
                long downloadedBytes = 0;

                try (InputStream in = conn.getInputStream();
                     var out = Files.newOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        downloadedBytes += read;
                        if (callback != null && totalBytes > 0) {
                            callback.onProgress(downloadedBytes, totalBytes);
                        }
                    }
                }

                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return targetFile;
            } catch (Exception e) {
                throw new RuntimeException("Failed to download update: " + e.getMessage(), e);
            }
        });
    }

    public static boolean isNewer(String remote, String current) {
        if (remote == null || remote.isBlank() || current == null || current.isBlank()) return false;
        String[] rParts = remote.split("-")[0].split("\\.");
        String[] cParts = current.split("-")[0].split("\\.");
        int len = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < len; i++) {
            int r = i < rParts.length ? parseSafeInt(rParts[i]) : 0;
            int c = i < cParts.length ? parseSafeInt(cParts[i]) : 0;
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }

    private static int parseSafeInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(long bytesRead, long totalBytes);
    }
}
