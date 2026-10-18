package dev.realworldmc;

import dev.realworldmc.map.*;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Offline regression checks; no requests reach public providers. */
public final class MapOverlayChecks {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void waitFor(java.util.function.BooleanSupplier value) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!value.getAsBoolean() && System.nanoTime() < end) Thread.sleep(5);
        check(value.getAsBoolean(), "Asynchronous check timed out");
    }
    public static void main(String[] args) throws Exception {
        double[] nw = Geo.mcToGeodetic(0, 179.999, 0, 0, 64, 0);
        double[] se = Geo.mcToGeodetic(0, 179.999, 0, 512, 64, 512);
        var crossing = TileBounds.of(nw[0], nw[1], se[0], se[1]);
        check(crossing != null && crossing.columns() <= 3, "Date-line rectangle stays small");
        check(TileBounds.key(131072, 10) == TileBounds.key(0, 10), "Wrapped tiles share a key");
        check(TileBounds.of(Double.NaN, 0, 0, 0) == null, "Invalid coordinates rejected");
        check(TileBounds.of(90, 0, 90, 1) == null, "Collapsed polar rectangle rejected");
        check(TileBounds.of(80, -100, -80, 100) == null, "Large rectangle rejected before allocation");
        for (String mini : new String[]{"26.6.0", "unsupported", ""}) for (String world : new String[]{"1.47.0", "unsupported", ""}) {
            check(MapCompatibility.apply("MinimapFBORendererMixin", mini, world) == mini.equals("26.6.0"), "Independent minimap gate");
            check(MapCompatibility.apply("GuiMapMixin", mini, world) == world.equals("1.47.0"), "Independent world-map gate");
            check(MapCompatibility.apply("SupportXaeroWorldmapMixin", mini, world) == (mini.equals("26.6.0") && world.equals("1.47.0")), "Bridge requires both");
        }
        check(!MapCompatibility.apply("GuiMapMixin", null, null), "Absent mods skipped");
        AtomicLong clock = new AtomicLong(); AtomicInteger closed = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var downloads = new TileDownloads<AutoCloseable>(4, 2000, key -> {
            entered.countDown();
            // Simulate a decoder that completes even after cancellation.
            while (true) try { release.await(); break; } catch (InterruptedException ignored) { }
            return closed::incrementAndGet;
        }, clock::get)) {
            for (int i = 0; i < 100; i++) downloads.take(i);
            check(downloads.size() == 4, "Queued/running/completed work is bounded");
            check(entered.await(5, TimeUnit.SECONDS), "Worker started");
            clock.set(3000); downloads.sweep(); release.countDown();
            waitFor(() -> closed.get() > 0);
            check(downloads.size() == 0, "Abandoned results released");
        }
        AtomicInteger calls = new AtomicInteger();
        try (var downloads = new TileDownloads<AutoCloseable>(2, 2000, key -> { calls.incrementAndGet(); return closed::incrementAndGet; }, clock::get)) {
            downloads.take(1); waitFor(() -> calls.get() == 1);
            AutoCloseable result = null;
            for (int i = 0; i < 100 && result == null; i++) { result = downloads.take(1); if(result == null) Thread.sleep(5); }
            check(result != null && calls.get() == 1, "One request per tile and ownership transferred"); result.close();
            downloads.take(2); waitFor(() -> calls.get() == 2); downloads.clear();
            check(downloads.size() == 0, "Disconnect clears all work");
        }
        network();
        System.out.println("PASS: map bounds, optional dependency matrix, bounded native resources, cache and HTTP failures");
    }
    private static void network() throws Exception {
        var server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger(), status = new AtomicInteger(200);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB), "png", output);
        byte[] png = output.toByteArray();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            check(exchange.getRequestHeaders().getFirst("User-Agent").contains("RealWorldMC/1.0.3"), "Identifying User-Agent");
            int code = status.get();
            if (code == 200 && exchange.getRequestHeaders().containsKey("If-Modified-Since")) code = 304;
            byte[] body = code == 201 ? new byte[]{1, 2, 3} : png;
            if(code == 201) code = 200;
            exchange.getResponseHeaders().set("Retry-After", "60");
            exchange.sendResponseHeaders(code, code == 304 ? -1 : body.length);
            if(code != 304) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var root = Files.createTempDirectory("rwmc-map-check-");
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{x}/{y}.png";
            var store = new TileStore(root, url, "1.0.3", 8 * 1024 * 1024);
            check(store.read(1).length == png.length, "Download PNG");
            store.read(1); check(calls.get() == 1, "Repeat hit makes no request");
            var restarted = new TileStore(root, url, "1.0.3", 8 * 1024 * 1024);
            restarted.read(1); check(calls.get() == 1, "Cache survives restart");
            java.nio.file.Path file;
            try(var files=Files.walk(root)) { file = files.filter(p -> p.toString().endsWith(".png")).findFirst().orElseThrow(); }
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()-TileStore.TTL-1000));
            store.read(1); check(calls.get() == 2, "Expired tile revalidated");
            Files.write(file, new byte[]{1,2,3}); store.read(1); check(calls.get()==3, "Corrupt cache repaired");
            status.set(429);
            try { store.read(2); throw new AssertionError("Expected HTTP failure"); } catch(java.io.IOException expected) { }
            int before = calls.get();
            try { store.read(3); throw new AssertionError("Expected cooldown"); } catch(java.io.IOException expected) { }
            check(calls.get() == before, "429 backoff applies across all tiles");
            status.set(201);
            try { new TileStore(root.resolve("invalid"), url, "1.0.3", 8*1024*1024).read(1); throw new AssertionError("Invalid PNG accepted"); } catch(java.io.IOException expected) { }
            var full = new TileStore(root.resolve("full"), url, "1.0.3", 1);
            before=calls.get();
            try { full.read(1); throw new AssertionError("Disk budget ignored"); } catch(java.io.IOException expected) { }
            check(calls.get()==before,"Full cache does not repeatedly fetch uncachable tiles");
        } finally {
            server.stop(0);
            try(var paths=Files.walk(root)) { for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
}
