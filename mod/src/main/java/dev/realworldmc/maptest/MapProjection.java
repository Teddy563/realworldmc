package dev.realworldmc.maptest;

import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
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
    private static final String USER_AGENT = "RealWorldMC/1.0.2 (+https://github.com/Teddy563/realworldmc)";
    private static final Path TILE_DIR = Path.of(String.valueOf(FabricLoader.getInstance().getGameDir()), "realworldmc-tiles");
    private static final Pattern TILE_URL = Pattern.compile(".*/(\\d+)/(\\d+)/(\\d+)\\.png$");


    public static Path downloadTile(String url, double latitude, double longitude) throws IOException, InterruptedException {
        Matcher m = TILE_URL.matcher(url);
        if (!m.matches()) throw new IllegalArgumentException("Not a z/x/y.png tile URL: " + url);
        Path target = TILE_DIR.resolve(m.group(1)).resolve(m.group(2)).resolve(m.group(3) + ".png");
        if (Files.isRegularFile(target)) return target;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
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
            BufferedImage image = tryDrawPixel(ImageIO.read(temp.toFile()), latitude, longitude);
            ImageIO.write(image, "png", temp.toFile());

            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        return target;
    }

    public static BufferedImage tryDrawPixel(BufferedImage image, double latitude, double longitude) throws IOException {
        Graphics2D graphics = image.createGraphics();
        Double[] pixels = getPixelsOnPng(latitude, longitude, 17);

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            double radius = 3.0;

            graphics.setColor(Color.PINK);
            graphics.fill(new Ellipse2D.Double(
                    pixels[0] - radius,
                    pixels[1] - radius,
                    radius * 2,
                    radius * 2
            ));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    public static String getCurrentLocation(double latitude, double longitude) {
        int zoom = 17;

        double lat = Math.clamp(latitude, -85.05112878, 85.05112878);
        double latRad = Math.toRadians(lat);
        int count = 1 << zoom;

        double tileX = (longitude + 180.0) / 360.0 * count;
        double tileY = (1.0 - Math.log(
                Math.tan(latRad) + 1.0 / Math.cos(latRad)
        ) / Math.PI) / 2.0 * count;

        int x = Math.floorMod((int) Math.floor(tileX), count);
        int y = Math.clamp((int) Math.floor(tileY), 0, count - 1);

        String url = "https://tile.openstreetmap.org/"
                + zoom + "/" + x + "/" + y + ".png";

        return url;
    }

    public static Double[] getPixelsOnPng(double latitude, double longitude, int zoom) {
        double lat = Math.clamp(latitude, -85.05112878, 85.05112878);
        double latRad = Math.toRadians(lat);
        double count = Math.pow(2, zoom);
        Double[] result = new Double[2];

        double tileX = (longitude + 180.0) / 360.0 * count;
        double tileY = (1.0 - Math.log(
                Math.tan(latRad) + 1.0 / Math.cos(latRad)
        ) / Math.PI) / 2.0 * count;

        tileY = Math.clamp(tileY, 0.0, Math.nextDown(count));

        double pixelX = (tileX - Math.floor(tileX)) * 256.0;
        double pixelY = (tileY - Math.floor(tileY)) * 256.0;

        result[0] = pixelX;
        result[1] = pixelY;

        return result;
    }



}
