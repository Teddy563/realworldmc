package dev.realworldmc.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.realworldmc.EarthChunkGenerator;
import dev.realworldmc.EarthRegions;
import dev.realworldmc.Geo;
import dev.realworldmc.RealWorldMC;
import dev.realworldmc.client.MapRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix4f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;

// tiles saver (cache)
public final class OsmTileCache {
    // for now i'll just put in variable in case you want to change zoom (changing ts was very buggy in my tests, so just use 17)
    private static final int TILE_ZOOM = 17;
    // OSM allows to download stuff at multiple threads
    private static final ExecutorService TILE_LOADER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "tiles");
        t.setDaemon(true);
        return t;
    });
    // There was a bug: whenever an OSM tile wasn't on the GPU for a frame the map drew Xaero's texture instead
    // Now it always keeps 256 tiles. Above that, it only frees tiles that haven't been drawn for 2 seconds. Tiles on screen stay loaded, up to a hard limit of 2048 tiles.
    private static final int MIN_TEXTURES = 256;
    private static final int MAX_TEXTURES = 2048;
    private static final long STALE_MS = 2_000;
    private static final Map<Long, Long> LAST_USED = new HashMap<>();
    private static final Map<Long, DynamicTexture> TEXTURES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, DynamicTexture> eldest) {
            if (size() <= MIN_TEXTURES) return false;
            long idle = System.currentTimeMillis() - LAST_USED.getOrDefault(eldest.getKey(), 0L);
            if (idle < STALE_MS && size() <= MAX_TEXTURES) return false;
            LAST_USED.remove(eldest.getKey());
            DynamicTexture texture = eldest.getValue();
            RenderSystem.recordRenderCall(texture::close);
            return true;
        }
    };
    private static final Map<Long, CompletableFuture<NativeImage>> DOWNLOADS = new ConcurrentHashMap<>();
    private static final Map<Long, Long> FAILED_AT = new HashMap<>();


    public static boolean draw(Matrix4f matrix, double worldX, double worldZ, float x, float z, float width, float height, IntFunction<BufferBuilder> begin, float[] rgba) {
        EarthRegions.Region region = regionAt(worldX + width / 2, worldZ + height / 2);
        if (region == null) return false;
        // northwest and southeast corners
        double[] nw = tileCoords(region, worldX, worldZ);
        double[] se = tileCoords(region, worldX + width, worldZ + height);
        int minTx = (int) Math.floor(nw[0]), maxTx = (int) Math.ceil(se[0]) - 1;
        int minTy = (int) Math.floor(nw[1]), maxTy = (int) Math.ceil(se[1]) - 1;
        int columns = maxTx - minTx + 1;

        DynamicTexture[] textures = new DynamicTexture[columns * (maxTy - minTy + 1)];
        boolean ready = true, failed = false;
        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                DynamicTexture texture = tile(tx, ty);
                if (texture == null) {
                    ready = false;
                    if (FAILED_AT.containsKey(key(tx, ty))) failed = true;
                }
                textures[(ty - minTy) * columns + (tx - minTx)] = texture;
            }
        }
        if (!ready) return !failed;

        double spanX = se[0] - nw[0], spanY = se[1] - nw[1];
        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                double u0 = Math.max(nw[0], tx), u1 = Math.min(se[0], tx + 1);
                double v0 = Math.max(nw[1], ty), v1 = Math.min(se[1], ty + 1);
                float x0 = (float) (x + (u0 - nw[0]) / spanX * width);
                float x1 = (float) (x + (u1 - nw[0]) / spanX * width);
                float z0 = (float) (z + (v0 - nw[1]) / spanY * height);
                float z1 = (float) (z + (v1 - nw[1]) / spanY * height);
                // same vertex order as Xaero's textured rect helpers
                BufferBuilder buffer = begin.apply(textures[(ty - minTy) * columns + (tx - minTx)].getId());
                vertex(buffer, matrix, x0, z1, rgba, u0 - tx, v1 - ty);
                vertex(buffer, matrix, x1, z1, rgba, u1 - tx, v1 - ty);
                vertex(buffer, matrix, x1, z0, rgba, u1 - tx, v0 - ty);
                vertex(buffer, matrix, x0, z0, rgba, u0 - tx, v0 - ty);
            }
        }
        return true;
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float z, float[] rgba, double u, double v) {
        VertexConsumer vertex = buffer.addVertex(matrix, x, z, 0);
        if (rgba != null) vertex.setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
        vertex.setUv((float) u, (float) v);
    }

    private static EarthRegions.Region regionAt(double worldX, double worldZ) {
        List<EarthRegions.Region> regions = MapRenderer.INSTANCE.getRegions();
        int index = EarthRegions.indexAt(worldX, worldZ);
        return regions != null && index >= 0 && index < regions.size() ? regions.get(index) : null;
    }

    private static double[] latLon(EarthRegions.Region region, double worldX, double worldZ) {
        return Geo.mcToGeodetic(region.latitude(), region.longitude(), region.groundHeight(),
                worldX - region.offsetX(), EarthChunkGenerator.GROUND_Y, worldZ - region.offsetZ());
    }

    private static double[] tileCoords(EarthRegions.Region region, double worldX, double worldZ) {
        double[] geo = latLon(region, worldX, worldZ);
        double latRad = Math.toRadians(Math.clamp(geo[0], -85.05112878, 85.05112878));
        double count = 1 << TILE_ZOOM;
        return new double[] {
                (geo[1] + 180.0) / 360.0 * count,
                (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * count
        };
    }

    private static long key(int tx, int ty) {
        return ((long) tx << 32) | (ty & 0xFFFFFFFFL);
    }

    // returns the ready to use texture or, if it is not yet ready, initiates its loading and returns null
    private static DynamicTexture tile(int tx, int ty) {
        long key = key(tx, ty);
        DynamicTexture texture = TEXTURES.get(key);
        if (texture != null) {
            LAST_USED.put(key, System.currentTimeMillis());
            return texture;
        }

        CompletableFuture<NativeImage> download = DOWNLOADS.get(key);
        if (download == null) {
            Long failedAt = FAILED_AT.get(key);
            if (failedAt != null && System.currentTimeMillis() - failedAt < 30_000) return null;
            int count = 1 << TILE_ZOOM;
            String url = String.format(Locale.ROOT, "https://tile.openstreetmap.org/%d/%d/%d.png",
                    TILE_ZOOM, Math.floorMod(tx, count), Math.clamp(ty, 0, count - 1));
            DOWNLOADS.put(key, CompletableFuture.supplyAsync(() -> {
                try {
                    Path png = MapProjection.downloadTile(url, Double.NaN, Double.NaN);
                    try (var stream = Files.newInputStream(png)) {
                        return NativeImage.read(stream);
                    }
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, TILE_LOADER));
            return null;
        }
        if (!download.isDone()) return null;

        DOWNLOADS.remove(key);
        try {
            texture = new DynamicTexture(download.join());
            LAST_USED.put(key, System.currentTimeMillis());
            TEXTURES.put(key, texture);
            FAILED_AT.remove(key);
            return texture;
        } catch (CompletionException e) {
            RealWorldMC.LOGGER.warn("Map tile {}/{} failed: {}", tx, ty, e.getCause() == null ? e : e.getCause().toString());
            FAILED_AT.put(key, System.currentTimeMillis());
            return null;
        }
    }
}
