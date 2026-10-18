package dev.realworldmc.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.realworldmc.EarthConfig;
import dev.realworldmc.EarthRegions;
import dev.realworldmc.client.CelestialBody;
import dev.realworldmc.client.SpaceFlight;
import dev.realworldmc.map.TileBounds;
import dev.realworldmc.map.TileDownloads;
import dev.realworldmc.map.TileStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;

/** All GPU state is owned by the client/render thread. Downloads own their native images. */
public final class OsmTileCache {
    private static final int MAX_TEXTURES = 256;
    private record Texture(DynamicTexture value, long touched) { }
    private static final Map<Long, Texture> TEXTURES = new LinkedHashMap<>(64, .75f, true);
    private static TileDownloads<NativeImage> downloads;
    private static String provider;
    private static long lastDraw;
    private OsmTileCache() { }

    public static boolean active(ResourceKey<Level> mapDimension) {
        var level = Minecraft.getInstance().level;
        return EarthConfig.get().osmMap && level != null && Level.OVERWORLD.equals(level.dimension())
                && Level.OVERWORLD.equals(mapDimension) && SpaceFlight.body() == CelestialBody.EARTH;
    }

    public static void tick(Minecraft client) {
        if (!active(client.level == null ? null : client.level.dimension())) { clear(); return; }
        if (downloads != null) downloads.sweep();
        long now = System.currentTimeMillis();
        var entries = TEXTURES.values().iterator();
        while (entries.hasNext()) {
            Texture entry = entries.next();
            if (now - entry.touched() > 10_000) { entries.remove(); entry.value().close(); }
        }
    }

    public static void clear() {
        if (downloads != null) { downloads.close(); downloads = null; }
        TEXTURES.values().forEach(t -> t.value().close());
        TEXTURES.clear(); lastDraw = 0; provider = null;
    }

    public static void credits(GuiGraphics graphics) {
        if (lastDraw == 0) return;
        var font = Minecraft.getInstance().font;
        String text = "© OpenStreetMap contributors · openstreetmap.org/copyright";
        graphics.pose().pushPose();
        float scale = Math.min(1f, (graphics.guiWidth() - 8f) / font.width(text));
        graphics.pose().scale(scale, scale, 1);
        int y = (int) ((graphics.guiHeight() - 42) / scale);
        graphics.fill(2, y - 2, font.width(text) + 6, y + 10, 0xc0000000);
        graphics.drawString(font, text, 4, y, 0xffffffff, false);
        graphics.pose().popPose();
    }

    public static boolean draw(ResourceKey<Level> dimension, Matrix4f matrix, double worldX, double worldZ,
                               float x, float z, float width, float height, IntFunction<BufferBuilder> begin, float[] rgba) {
        if (!active(dimension) || !(width > 0 && width <= 512 && height > 0 && height <= 512)) return false;
        EarthRegions.Region region = MapProjection.regionAt(worldX, worldZ);
        if (region == null || !region.equals(MapProjection.regionAt(worldX + width, worldZ + height))) return false;
        double[] nw = MapProjection.latLon(region, worldX, worldZ), se = MapProjection.latLon(region, worldX + width, worldZ + height);
        TileBounds bounds = TileBounds.of(nw[0], nw[1], se[0], se[1]);
        if (bounds == null) return false;
        String requested = EarthConfig.get().osmTileUrl;
        if (downloads == null || !java.util.Objects.equals(provider, requested)) {
            clear(); provider = requested;
            try {
                String version = FabricLoader.getInstance().getModContainer("realworldmc").orElseThrow().getMetadata().getVersion().getFriendlyString();
                TileStore store = new TileStore(FabricLoader.getInstance().getGameDir().resolve("realworldmc-tiles"), requested, version, 256L * 1024 * 1024);
                downloads = new TileDownloads<>(64, 2000, key -> NativeImage.read(new ByteArrayInputStream(store.read(key))), System::currentTimeMillis);
            } catch (IllegalArgumentException | NullPointerException e) { return false; }
        }
        DynamicTexture[] tiles = new DynamicTexture[bounds.columns() * bounds.rows()];
        boolean ready = true;
        for (int row = 0; row < bounds.rows(); row++) for (int col = 0; col < bounds.columns(); col++) {
            long key = TileBounds.key(bounds.minX() + col, bounds.minY() + row);
            Texture entry = TEXTURES.get(key);
            if (entry == null) {
                NativeImage image = downloads.take(key);
                if (image != null) {
                    // Do not evict a texture already referenced by this frame's batched geometry.
                    if (TEXTURES.size() >= MAX_TEXTURES) image.close();
                    else { entry = new Texture(new DynamicTexture(image), System.currentTimeMillis()); TEXTURES.put(key, entry); }
                }
            }
            if (entry == null) ready = false;
            else { TEXTURES.put(key, new Texture(entry.value(), System.currentTimeMillis())); tiles[row * bounds.columns() + col] = entry.value(); }
        }
        if (!ready) return false;
        for (int row = 0; row < bounds.rows(); row++) for (int col = 0; col < bounds.columns(); col++) {
            int tx = bounds.minX() + col, ty = bounds.minY() + row;
            double u0 = Math.max(bounds.west(), tx), u1 = Math.min(bounds.east(), tx + 1);
            double v0 = Math.max(bounds.north(), ty), v1 = Math.min(bounds.south(), ty + 1);
            float x0 = (float) (x + (u0 - bounds.west()) / (bounds.east() - bounds.west()) * width);
            float x1 = (float) (x + (u1 - bounds.west()) / (bounds.east() - bounds.west()) * width);
            float z0 = (float) (z + (v0 - bounds.north()) / (bounds.south() - bounds.north()) * height);
            float z1 = (float) (z + (v1 - bounds.north()) / (bounds.south() - bounds.north()) * height);
            BufferBuilder buffer = begin.apply(tiles[row * bounds.columns() + col].getId());
            vertex(buffer, matrix, x0, z1, rgba, u0 - tx, v1 - ty);
            vertex(buffer, matrix, x1, z1, rgba, u1 - tx, v1 - ty);
            vertex(buffer, matrix, x1, z0, rgba, u1 - tx, v0 - ty);
            vertex(buffer, matrix, x0, z0, rgba, u0 - tx, v0 - ty);
        }
        lastDraw = System.currentTimeMillis();
        return true;
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float z, float[] rgba, double u, double v) {
        VertexConsumer vertex = buffer.addVertex(matrix, x, z, 0);
        if (rgba != null) vertex.setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
        vertex.setUv((float) u, (float) v);
    }
}
