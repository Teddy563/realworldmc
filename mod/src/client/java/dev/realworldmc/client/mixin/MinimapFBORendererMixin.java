package dev.realworldmc.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.realworldmc.EarthChunkGenerator;
import dev.realworldmc.EarthRegions;
import dev.realworldmc.Geo;
import dev.realworldmc.RealWorldMC;
import dev.realworldmc.client.MapRenderer;
import dev.realworldmc.client.map.MapProjection;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRenderer;
import xaero.common.minimap.render.MinimapFBORenderer;
import xaero.common.minimap.render.MinimapRendererHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mixin(value = MinimapFBORenderer.class, remap = false)
public abstract class MinimapFBORendererMixin {

    // for now i'll just put in variable in case you want to change zoom (might be buggy)
    @Unique
    private static final int TILE_ZOOM = 17;
    @Unique
    private static final long TILE_RETRY_MS = 30_000;
    // OSM allows to download stuff at multiple threads
    @Unique
    private static final ExecutorService TILE_LOADER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "tiles");
        t.setDaemon(true);
        return t;
    });
    @Unique
    private static final Map<Long, DynamicTexture> TILE_TEXTURES = new HashMap<>();
    @Unique
    private static final Map<Long, CompletableFuture<NativeImage>> TILE_DOWNLOADS = new ConcurrentHashMap<>();
    @Unique
    private static final Map<Long, Long> TILE_FAILED_AT = new HashMap<>();


    @WrapOperation(
            method = "renderChunksToFBO",
            at = @At(value = "INVOKE", target = "Lxaero/common/minimap/render/MinimapRendererHelper;prepareMyTexturedColoredModalRect(Lorg/joml/Matrix4f;FFIIFFFFIFFFFLxaero/common/graphics/renderer/multitexture/MultiTextureRenderTypeRenderer;)V"
            ),
            remap = false
    )
    private void prepareMyTexturedColoredModalRect(MinimapRendererHelper helper, Matrix4f matrix, float x, float z, int textureX, int textureY, float width, float height, float textureWidth, float textureHeight, int texture, float red, float green, float blue, float alpha, MultiTextureRenderTypeRenderer renderer, Operation<Void> original,
            @Local(name = "xFloored") int xFloored,
            @Local(name = "zFloored") int zFloored)
    {
        double worldX = x + xFloored;
        double worldZ = z + zFloored;
        EarthRegions.Region region = regionAt(worldX + width / 2, worldZ + height / 2);
        if (region == null) {
            original.call(helper, matrix, x, z, textureX, textureY, width, height, textureWidth, textureHeight, texture, red, green, blue, alpha, renderer);
            return;
        }
        // northwest corner
        double[] nw = tileCoords(region, worldX, worldZ);
        // southeastern corner
        double[] se = tileCoords(region, worldX + width, worldZ + height);
        int minTx = (int) Math.floor(nw[0]), maxTx = (int) Math.ceil(se[0]) - 1;
        int minTy = (int) Math.floor(nw[1]), maxTy = (int) Math.ceil(se[1]) - 1;

        boolean ready = true;
        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                if (tile(tx, ty, region, xFloored, zFloored) == null) ready = false;
            }
        }
        if (!ready) {
            original.call(helper, matrix, x, z, textureX, textureY, width, height, textureWidth, textureHeight, texture, red, green, blue, alpha, renderer);
            return;
        }

        double spanX = se[0] - nw[0], spanY = se[1] - nw[1];
        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                double u0 = Math.max(nw[0], tx), u1 = Math.min(se[0], tx + 1);
                double v0 = Math.max(nw[1], ty), v1 = Math.min(se[1], ty + 1);
                float x0 = (float) (x + (u0 - nw[0]) / spanX * width);
                float x1 = (float) (x + (u1 - nw[0]) / spanX * width);
                float z0 = (float) (z + (v0 - nw[1]) / spanY * height);
                float z1 = (float) (z + (v1 - nw[1]) / spanY * height);
                // same vertex order as MinimapRendererHelper.prepareMyTexturedColoredModalRect
                BufferBuilder buffer = renderer.begin(TILE_TEXTURES.get(key(tx, ty)).getId());
                buffer.addVertex(matrix, x0, z1, 0).setColor(red, green, blue, alpha).setUv((float) (u0 - tx), (float) (v1 - ty));
                buffer.addVertex(matrix, x1, z1, 0).setColor(red, green, blue, alpha).setUv((float) (u1 - tx), (float) (v1 - ty));
                buffer.addVertex(matrix, x1, z0, 0).setColor(red, green, blue, alpha).setUv((float) (u1 - tx), (float) (v0 - ty));
                buffer.addVertex(matrix, x0, z0, 0).setColor(red, green, blue, alpha).setUv((float) (u0 - tx), (float) (v0 - ty));
            }
        }
    }

    @Unique
    private static EarthRegions.Region regionAt(double worldX, double worldZ) {
        List<EarthRegions.Region> regions = MapRenderer.INSTANCE.getRegions();
        int index = EarthRegions.indexAt(worldX, worldZ);
        return regions != null && index >= 0 && index < regions.size() ? regions.get(index) : null;
    }


    @Unique
    private static double[] latLon(EarthRegions.Region region, double worldX, double worldZ) {
        return Geo.mcToGeodetic(region.latitude(), region.longitude(), region.groundHeight(),
                worldX - region.offsetX(), EarthChunkGenerator.GROUND_Y, worldZ - region.offsetZ());
    }

    @Unique
    private static double[] tileCoords(EarthRegions.Region region, double worldX, double worldZ) {
        double[] geo = latLon(region, worldX, worldZ);
        double latRad = Math.toRadians(Math.clamp(geo[0], -85.05112878, 85.05112878));
        double count = 1 << TILE_ZOOM;
        return new double[] {
                (geo[1] + 180.0) / 360.0 * count,
                (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * count
        };
    }

    @Unique
    private static long key(int tx, int ty) {
        return ((long) tx << 32) | (ty & 0xFFFFFFFFL);
    }

    // returns the ready to use texture or, if it is not yet ready, initiates its loading and returns null
    @Unique
    private static DynamicTexture tile(int tx, int ty, EarthRegions.Region region, int playerX, int playerZ) {
        long key = key(tx, ty);
        DynamicTexture texture = TILE_TEXTURES.get(key);
        if (texture != null) return texture;

        CompletableFuture<NativeImage> download = TILE_DOWNLOADS.get(key);
        if (download == null) {
            Long failedAt = TILE_FAILED_AT.get(key);
            if (failedAt != null && System.currentTimeMillis() - failedAt < TILE_RETRY_MS) return null;
            int count = 1 << TILE_ZOOM;
            String url = String.format(Locale.ROOT, "https://tile.openstreetmap.org/%d/%d/%d.png",
                    TILE_ZOOM, Math.floorMod(tx, count), Math.clamp(ty, 0, count - 1));
            // downloadTile marks this position on the saved tile
            double[] player = latLon(region, playerX + 0.5, playerZ + 0.5);
            TILE_DOWNLOADS.put(key, CompletableFuture.supplyAsync(() -> {
                try {
                    Path png = MapProjection.downloadTile(url, player[0], player[1]);
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

        TILE_DOWNLOADS.remove(key);
        try {
            texture = new DynamicTexture(download.join());
            TILE_TEXTURES.put(key, texture);
            TILE_FAILED_AT.remove(key);
            return texture;
        } catch (CompletionException e) {
            RealWorldMC.LOGGER.warn("Minimap tile {}/{} failed: {}", tx, ty, e.getCause() == null ? e : e.getCause().toString());
            TILE_FAILED_AT.put(key, System.currentTimeMillis());
            return null;
        }
    }
}
