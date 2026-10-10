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

	public static String base() {
		return EarthConfig.get().serviceUrl.replaceAll("/+$", "");
	}

	public static String originQuery(double lat, double lon, double h) {
		return String.format(Locale.ROOT, "lat=%.8f&lon=%.8f&h=%.4f", lat, lon, h);
	}

	public static byte[] getBytes(String pathAndQuery, Duration timeout) throws IOException, InterruptedException {
        if (base().equals("http://127.0.0.1:8765") && !startupError().isEmpty()) throw new IOException(startupError());
		HttpRequest req = HttpRequest.newBuilder(URI.create(base() + pathAndQuery)).timeout(timeout).GET().build();
		HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
		if (res.statusCode() != 200) {
			throw new IOException("service " + res.statusCode() + ": " + new String(res.body(), StandardCharsets.UTF_8).trim());
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
        if (!base().equals("http://127.0.0.1:8765")) return; // Explicit remote service stays externally managed.
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
