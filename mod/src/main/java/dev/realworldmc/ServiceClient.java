package dev.realworldmc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/** HTTP client for the local RealWorldMC tile service (see service/src/server.mjs). */
public final class ServiceClient {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private static final ServiceRuntime RUNTIME = new ServiceRuntime();

	private ServiceClient() {}

	/** Only the service's explicit coverage response is safe to treat as empty terrain. */
	public static final class ServiceException extends IOException {
		public final int status;
		private final String body;
		public ServiceException(int status, String body) {
			super("service " + status + ": " + body);
			this.status = status;
			this.body = body;
		}
		public boolean noTerrainCoverage() {
			if (status != 404) return false;
			try {
				JsonObject error = JsonParser.parseString(body).getAsJsonObject();
				return error.has("code") && error.get("code").getAsString().equals("NO_TERRAIN_COVERAGE")
					|| error.has("error") && error.get("error").getAsString().startsWith("No usable 3D terrain in this area.");
			} catch (RuntimeException invalid) { return false; }
		}
	}

	public static String base() {
        String configured = configuredBase();
        return configured.equals("http://127.0.0.1:8765") ? "http://127.0.0.1:" + RUNTIME.mapPort() : configured;
	}

    private static String configuredBase() { return EarthConfig.get().serviceUrl.replaceAll("/+$", ""); }
    public static String photoBase() { return "http://127.0.0.1:" + RUNTIME.photoPort(); }

	public static String originQuery(double lat, double lon, double h) {
		return String.format(Locale.ROOT, "lat=%.8f&lon=%.8f&h=%.4f", lat, lon, h);
	}

	public static byte[] getBytes(String pathAndQuery, Duration timeout) throws IOException, InterruptedException {
        if (configuredBase().equals("http://127.0.0.1:8765") && !startupError().isEmpty()) throw new IOException(startupError());
		HttpRequest req = HttpRequest.newBuilder(URI.create(base() + pathAndQuery)).timeout(timeout).GET().build();
		HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
		if (res.statusCode() != 200) {
			throw new ServiceException(res.statusCode(), new String(res.body(), StandardCharsets.UTF_8).trim());
		}
		return res.body();
	}

	public static JsonObject getJson(String pathAndQuery, Duration timeout) throws IOException, InterruptedException {
		return JsonParser.parseString(new String(getBytes(pathAndQuery, timeout), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	public static boolean healthy() {
		try {
			return getJson("/health", Duration.ofSeconds(2)).get("ok").getAsBoolean();
		} catch (Exception e) {
			return false;
		}
	}

    public static java.nio.file.Path directory() {
        return ServiceRuntime.directory(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir(), EarthConfig.get().serviceDir);
    }
    public static String startupError() { return RUNTIME.error(); }
    /** Called on a worker thread, never from the render thread. */
    public static void ensureRunning() {
        if (!configuredBase().equals("http://127.0.0.1:8765")) return; // Explicit remote service stays externally managed.
        var config = EarthConfig.get();
        boolean bundled = config.serviceDir == null || config.serviceDir.isBlank() || config.serviceDir.equals("realworldmc-service");
        RUNTIME.start(directory(), config.nodeExecutable, bundled);
    }
    public static boolean restartManaged() {
        RUNTIME.stop();
        ensureRunning();
        return startupError().isEmpty();
    }
}
