package dev.realworldmc.map;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded PNG cache using the provider's permitted seven-day fallback TTL. */
public final class TileStore {
    public static final long TTL = Duration.ofDays(7).toMillis();
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Path directory;
    private final String template, userAgent;
    private final long budget;
    private final AtomicLong retryAfter = new AtomicLong();
    private long bytes;
    private boolean scanned;

    public TileStore(Path root, String template, String version, long budget) {
        this.template = template; this.budget = budget;
        URI probe = URI.create(template.replace("{z}", "17").replace("{x}", "0").replace("{y}", "0"));
        boolean local = "http".equals(probe.getScheme()) && ("127.0.0.1".equals(probe.getHost()) || "localhost".equals(probe.getHost()));
        if (!("https".equals(probe.getScheme()) || local) || probe.getHost() == null || probe.getUserInfo() != null
                || !template.contains("{z}") || !template.contains("{x}") || !template.contains("{y}"))
            throw new IllegalArgumentException("Use an HTTPS tile URL with {z}, {x} and {y}");
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(template.getBytes(StandardCharsets.UTF_8)));
            directory = root.resolve(hash.substring(0, 16));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        userAgent = "RealWorldMC/" + version + " (+https://github.com/Teddy563/realworldmc)";
    }

    public byte[] read(long key) throws IOException, InterruptedException {
        Path target = directory.resolve(Long.toUnsignedString(key) + ".png");
        byte[] cached = null;
        long modified = 0;
        synchronized (this) {
            scan();
            if (Files.isRegularFile(target)) {
                try {
                    cached = limitedFile(target); validate(cached);
                    modified = Files.getLastModifiedTime(target).toMillis();
                    if (System.currentTimeMillis() - modified < TTL) return cached;
                } catch (IOException e) { bytes -= Files.size(target); Files.delete(target); cached = null; }
            }
            if (cached == null && bytes + MAX_BYTES > budget) {
                prune();
                if (bytes + MAX_BYTES > budget) throw new IOException("Tile cache is full; preserving recent tiles");
            }
        }
        if (System.currentTimeMillis() < retryAfter.get()) throw new IOException("Tile provider is cooling down");
        String url = template.replace("{z}", Integer.toString(TileBounds.ZOOM))
                .replace("{x}", Long.toString(key >>> 32)).replace("{y}", Long.toString(key & 0xffffffffL));
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("User-Agent", userAgent);
        if (cached != null) request.header("If-Modified-Since", DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(modified).atZone(ZoneOffset.UTC)));
        var response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var input = response.body()) {
            if (response.statusCode() == 304 && cached != null) {
                Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.from(Instant.now()));
                return cached;
            }
            if (response.statusCode() != 200) {
                long delay = response.statusCode() == 403 ? 300_000 : 30_000;
                String header = response.headers().firstValue("Retry-After").orElse("");
                try { delay = Math.max(delay, Math.min(86_400, Long.parseLong(header)) * 1000); }
                catch (NumberFormatException e) {
                    try { delay = Math.max(delay, Duration.between(Instant.now(), DateTimeFormatter.RFC_1123_DATE_TIME.parse(header, Instant::from)).toMillis()); }
                    catch (java.time.DateTimeException ignored) { }
                }
                retryAfter.accumulateAndGet(System.currentTimeMillis() + delay, Math::max);
                throw new IOException("Tile HTTP " + response.statusCode());
            }
            byte[] result = input.readNBytes(MAX_BYTES + 1);
            validate(result);
            synchronized (this) {
                long old = Files.exists(target) ? Files.size(target) : 0;
                if (bytes - old + result.length > budget) throw new IOException("Tile cache is full");
                Path temporary = Files.createTempFile(directory, "tile-", ".part");
                try {
                    Files.write(temporary, result);
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                    bytes += result.length - old;
                } finally { Files.deleteIfExists(temporary); }
            }
            return result;
        }
    }
    private static byte[] limitedFile(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES) throw new IOException("Oversized tile");
        return Files.readAllBytes(file);
    }
    private static void validate(byte[] png) throws IOException {
        if (png.length < 24 || png.length > MAX_BYTES || java.nio.ByteBuffer.wrap(png).getLong() != 0x89504e470d0a1a0aL
                || java.nio.ByteBuffer.wrap(png, 16, 8).getInt() != 256 || java.nio.ByteBuffer.wrap(png, 20, 4).getInt() != 256)
            throw new IOException("Expected a 256 by 256 PNG tile");
        var image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null) throw new IOException("Invalid PNG tile");
        image.flush();
    }
    private void scan() throws IOException {
        if (scanned) return;
        Files.createDirectories(directory);
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".png")).toList()) bytes += Files.size(file);
        }
        scanned = true;
    }
    private void prune() throws IOException {
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".png")).toList()) {
                if (System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() <= TTL) continue;
                bytes -= Files.size(file); Files.delete(file);
            }
        }
    }
}
