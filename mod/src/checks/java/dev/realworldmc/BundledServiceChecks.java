package dev.realworldmc;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

public final class BundledServiceChecks {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static byte[] bundle(String extra) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String name : List.of("src/launcher.mjs","src/server.mjs","node_modules/jpeg-js/package.json","runtime/win-x64/node.exe","runtime/win-x64/LICENSE",extra)) {
                zip.putNextEntry(new ZipEntry(name)); zip.write("fixture".getBytes()); zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    public static void main(String[] args) throws Exception {
        Path game = Files.createTempDirectory("RWMC JAR checks ");
        int mapPort=freePort(), photoPort=freePort();
        while (photoPort==mapPort) photoPort=freePort();
        var runtime = new ServiceRuntime(mapPort, photoPort);
        try {
            // Exercise a launcher path with an alias even when the system temp path is canonical.
            Path data = game.resolve(".").resolve("realworldmc-service");
            Files.createDirectories(data);
            Files.writeString(data.resolve("config.json"), "{\"apiKey\":\"\"}");
            Files.writeString(data.resolve("streetview.config.json"), "{\"tilesKey\":\"TEST_ONLY_NOT_A_REAL_KEY\"}");
            String privateState = Files.readString(data.resolve("streetview.config.json"));
            byte[] good = bundle("config.example.json");
            Path installed = BundledService.unpack(data, new ByteArrayInputStream(good), hash(good));
            check(Files.isRegularFile(installed.resolve(".complete")), "Complete marker after extraction");
            check(Files.readString(data.resolve("streetview.config.json")).equals(privateState), "Existing keys must survive installation");
            check(BundledService.unpack(data, InputStream.nullInputStream(), hash(good)).equals(installed), "Same bundle must be reused without unpacking");
            for (String bad : List.of("../outside", "src/../../outside", "config.json", "C:/escape", "src\\escape")) {
                byte[] bytes = bundle(bad);
                try { BundledService.unpack(data, new ByteArrayInputStream(bytes), hash(bytes)); throw new AssertionError("Unsafe archive accepted: " + bad); }
                catch (IOException expected) { }
                check(!Files.exists(data.resolve(".bundles").resolve(hash(bytes))), "Failed install must not be published");
            }
            try { BundledService.unpack(data, new ByteArrayInputStream(good), "0".repeat(64)); throw new AssertionError("Bad digest accepted"); }
            catch (IOException expected) { }
            if (args.length > 0 && args[0].equals("release")) {
                check(BundledService.class.getResourceAsStream("/realworldmc-service.zip") != null, "Release must bundle its service");
                Path code = BundledService.prepare(data);
                check(Files.isSameFile(code.getParent(), data.resolve(".bundles")), "Release code must use a versioned directory");
                check(!code.equals(installed), "New release installs beside the older version");
                check(runtime.start(data, "node"), "Release must start both real services: " + runtime.error());
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + photoPort + "/health")).build();
                var health = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                check(health.contains("\"hasKey\":true"), "Photo service must read keys from the persistent data directory");
                check(Files.readString(data.resolve("streetview.config.json")).equals(privateState), "Service must keep keys in the data folder");
                runtime.stop();
                check(runtime.start(data, "node"), "Standalone services must restart");
                runtime.stop();
                System.out.println("Full release JAR extracted and started both bundled services; restart and shutdown passed. No provider calls.");
            }
            System.out.println("Bundle checksum, safe paths, atomic install, reuse, upgrade and credential preservation checks passed.");
        } finally {
            runtime.stop();
            try (var paths = Files.walk(game)) { for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
        }
    }
    private static int freePort() throws Exception { try (var socket = new java.net.ServerSocket(0)) { return socket.getLocalPort(); } }
}
