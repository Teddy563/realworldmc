package dev.realworldmc.client.map;

import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapProjection {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Path TILE_DIR = Path.of(String.valueOf(FabricLoader.getInstance().getGameDir()), "realworldmc-tiles");
    private static final Pattern TILE_URL = Pattern.compile(".*/(\\d+)/(\\d+)/(\\d+)\\.png$");


    // i think i need to put it in another file, i was testing stuff here
    public static Path downloadTile(String url, double latitude, double longitude) throws IOException, InterruptedException {
        Matcher m = TILE_URL.matcher(url);
        if (!m.matches()) throw new IllegalArgumentException("Not a z/x/y.png tile URL: " + url);
        Path target = TILE_DIR.resolve(m.group(1)).resolve(m.group(2)).resolve(m.group(3) + ".png");
        if (Files.isRegularFile(target)) return target;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "RealWorldMC/1.0.2 (+https://github.com/Teddy563/realworldmc)")
                .GET()
                .build();
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "tile", ".part");
        try {
            HttpResponse<Path> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() != 200) {
                throw new IOException("Tile download failed with HTTP " + response.statusCode() + ": " + url);
            }
            // idk if this the most efficient way to save it, but it works
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        return target;
    }





}
