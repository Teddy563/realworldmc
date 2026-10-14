package dev.realworldmc;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Owns only the child it started. Never stops a manually started or other-profile service. */
public final class ServiceRuntime {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private volatile int mapPort, photoPort;
    private final boolean allowPortFallback;
    private Process process;
    private Path activeDirectory;
    private String error = "";
    private long lastAttempt;
    public ServiceRuntime() { this(8765, 8766, true); }
    ServiceRuntime(int mapPort, int photoPort) { this(mapPort, photoPort, false); }
    ServiceRuntime(int mapPort, int photoPort, boolean allowPortFallback) { this.allowPortFallback=allowPortFallback; this.mapPort=mapPort; this.photoPort=photoPort; Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "RealWorldMC service cleanup")); }
    public static Path directory(Path game, String configured) {
        return game.resolve(configured == null || configured.isBlank() ? "realworldmc-service" : configured).toAbsolutePath().normalize();
    }
    public static String executable(Path directory, String configured) throws IOException {
        return NodeRuntime.executable(directory, configured);
    }
    private static String identity(Path directory) { return UUID.nameUUIDFromBytes(directory.toString().getBytes(StandardCharsets.UTF_8)).toString(); }
    private static String health(int port) {
        try {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).timeout(Duration.ofSeconds(1)).GET().build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return "occupied";
            var json = JsonParser.parseString(response.body()).getAsJsonObject();
            return json.has("ok") && json.get("ok").getAsBoolean() && json.has("instanceId") ? json.get("instanceId").getAsString() : "occupied";
        } catch (java.net.ConnectException e) { return null; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return "occupied"; }
        catch (Exception e) { return "occupied"; }
    }
    public int mapPort() { return mapPort; }
    public int photoPort() { return photoPort; }
    private void selectFreePorts() throws IOException {
        var loopback = java.net.InetAddress.getByName("127.0.0.1");
        try (var map = new java.net.ServerSocket(0, 1, loopback);
             var photo = new java.net.ServerSocket(0, 1, loopback)) {
            mapPort = map.getLocalPort();
            photoPort = photo.getLocalPort();
        }
    }
    public synchronized String error() { return error; }
    public synchronized boolean start(Path directory, String node) {
        return start(directory, node, true);
    }
    public synchronized boolean start(Path directory, String node, boolean useBundle) {
        if (process != null && process.isAlive() && directory.equals(activeDirectory)) {
            boolean ready = ready(directory);
            if (ready) error = "";
            return ready;
        }
        if (System.currentTimeMillis() - lastAttempt < 15000) return false;
        lastAttempt = System.currentTimeMillis();
        String map = health(mapPort), photo = health(photoPort);
        if (map != null || photo != null) {
            if (!allowPortFallback) {
                error = "Another local map/photo service is using ports " + mapPort + " or " + photoPort + ". Close the other RealWorldMC game or manual service, then retry.";
                return false;
            }
            try { selectFreePorts(); }
            catch (IOException e) { error = "Could not find free local map/photo ports. Retry setup."; return false; }
        }
        Path code;
        try { code = useBundle ? BundledService.prepare(directory) : directory; }
        catch (IOException | java.nio.channels.OverlappingFileLockException e) {
            error = "Could not unpack the bundled map service. Check folder permissions and reinstall the full release JAR, then retry setup."; return false;
        }
        if (!Files.isRegularFile(code.resolve("src/launcher.mjs"))) {
            error = "Map service missing. Download the full release JAR or .mrpack, not a developer JAR."; return false;
        }
        try { node = executable(code, node); }
        catch (IOException e) { error = e.getMessage(); return false; }
        try {
            var builder = new ProcessBuilder(node, "--max-old-space-size=8192", code.resolve("src/launcher.mjs").toString())
                .directory(directory.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(directory.resolve("service.log").toFile()));
            builder.environment().put("RWMC_MANAGED", "1");
            builder.environment().put("RWMC_DATA_DIR", directory.toString());
            builder.environment().put("RWMC_PORT", Integer.toString(mapPort));
            builder.environment().put("RWMC_STREETVIEW_PORT", Integer.toString(photoPort));
            builder.environment().put("RWMC_INSTANCE_ID", identity(directory));
            process = builder.start(); activeDirectory = directory;
            for (int i=0; i<100 && process.isAlive(); i++) {
                if (ready(directory)) { error=""; return true; }
                Thread.sleep(200);
            }
            error = "Map service did not become ready. Check your keys and realworldmc-service/service.log, then retry setup.";
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); error="Service startup interrupted. Retry setup."; }
        catch (IOException e) { error="Cannot start the map service. Reinstall the Windows x64 release JAR or pack, or configure Node 22+ for an advanced installation."; }
        return false;
    }
    private boolean ready(Path directory) {
        String id = identity(directory);
        return id.equals(health(mapPort)) && id.equals(health(photoPort));
    }
    public synchronized void stop() {
        if (process != null) {
            try {
                process.getOutputStream().close();
                if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); process.destroy(); }
            catch (IOException e) { process.destroy(); }
            process = null;
        }
        lastAttempt=0;
    }
}
