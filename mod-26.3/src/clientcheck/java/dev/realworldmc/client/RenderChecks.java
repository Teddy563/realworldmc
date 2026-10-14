package dev.realworldmc.client;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.List;

/** Exercises the real tile upload, renderer, shader and GPU readback without a world or API key. */
public final class RenderChecks implements ClientModInitializer {
    private boolean started, finished;
    private long startTime;
    private MapTile tile;
    private GpuBuffer readback, projection;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (finished) return;
            if (started) {
                if (System.currentTimeMillis() - startTime > 15000) finish(mc, "FAIL: GPU readback timed out");
                return;
            }
            if (mc.gui.overlay() != null || RenderSystem.tryGetDevice() == null) return;
            started = true; startTime = System.currentTimeMillis();
            try { draw(mc); }
            catch (Throwable e) { e.printStackTrace(); finish(mc, "FAIL: " + e); }
        });
    }
    private void draw(Minecraft mc) throws Exception {
        // One red triangle using the service's actual binary tile format.
        var bytes = ByteBuffer.allocate(32 + 16 + 60).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(new byte[]{'E','C','T','1'}).putInt(1).putDouble(0).putDouble(0).putDouble(0);
        bytes.putInt(3).putInt(0).putInt(0).put(new byte[]{(byte)255,32,16,(byte)255});
        for (float v : new float[]{-1,-1,0,0,0, 1,-1,0,0,0, 0,1,0,0,0}) bytes.putFloat(v);
        tile = new MapTile(TileData.parse(1, false, bytes.array(), 0, 0), null);
        var drawField = MapRenderer.class.getDeclaredField("drawTiles"); drawField.setAccessible(true);
        drawField.set(MapRenderer.INSTANCE, List.of(tile));
        var state = new LevelRenderState(); state.cameraRenderState = new CameraRenderState();
        state.cameraRenderState.pos = Vec3.ZERO;
        state.cameraRenderState.viewRotationMatrix = new Matrix4f();
        var ctx = (LevelRenderContext) Proxy.newProxyInstance(LevelRenderContext.class.getClassLoader(), new Class<?>[]{LevelRenderContext.class},
            (proxy, method, args) -> method.getName().equals("levelState") ? state : null);
        var device = RenderSystem.getDevice();
        var target = mc.gameRenderer.mainRenderTarget();
        var encoder = device.createCommandEncoder();
        // Minecraft 26.3 uses reversed depth: zero is the far plane.
        encoder.clearColorAndDepthTextures(target.getColorTexture(), new Vector4f(0,0,0,1), target.getDepthTexture(), 0);
        encoder.submit();
        var matrix = MemoryUtil.memAlloc(64);
        try { new Matrix4f().get(0, matrix); projection = device.createBuffer(() -> "RWMC check projection", GpuBuffer.USAGE_UNIFORM, matrix); }
        finally { MemoryUtil.memFree(matrix); }
        RenderSystem.backupProjectionMatrix();
        try {
            RenderSystem.setProjectionMatrix(projection.slice(), ProjectionType.ORTHOGRAPHIC);
            MapRenderer.INSTANCE.render(ctx);
        } finally { RenderSystem.restoreProjectionMatrix(); drawField.set(MapRenderer.INSTANCE, List.of()); }
        int width = target.getColorTexture().getWidth(0), height = target.getColorTexture().getHeight(0);
        readback = device.createBuffer(() -> "RWMC check pixels", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long)width * height * 4);
        var copy = device.createCommandEncoder();
        copy.copyTextureToBuffer(target.getColorTexture(), readback, 0, () -> {
            String result;
            try (var mapped = readback.map(true, false)) {
                var pixels = mapped.data(); int i = ((height / 2) * width + width / 2) * 4;
                int red = pixels.get(i) & 255, green = pixels.get(i + 1) & 255, blue = pixels.get(i + 2) & 255;
                if (red < 180 || green > 100 || blue > 100) throw new AssertionError("Expected red map triangle; got " + red + "," + green + "," + blue);
                result = "PASS: 26.3 client startup, map shader compilation, texture/vertex upload, real tile rendering and GPU pixel readback. No world or provider requests.";
            } catch (Throwable e) { e.printStackTrace(); result = "FAIL: " + e; }
            String outcome = result;
            mc.execute(() -> finish(mc, outcome));
        }, 0);
        copy.submit();
    }
    private void finish(Minecraft mc, String result) {
        if (finished) return;
        finished = true;
        try { Files.writeString(Path.of("render-check-result.txt"), result); }
        catch (Exception e) { e.printStackTrace(); }
        System.out.println(result);
        if (tile != null) tile.close();
        if (readback != null) readback.close();
        if (projection != null) projection.close();
        mc.stop();
    }
}
