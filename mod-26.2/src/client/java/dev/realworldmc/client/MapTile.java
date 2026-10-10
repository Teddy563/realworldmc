package dev.realworldmc.client;


import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.realworldmc.EarthBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;


import java.util.BitSet;

/** GPU side of a tile: one vertex buffer + texture per glTF primitive. Render thread only. */
public final class MapTile {
	public final TileData data;
	public final AABB bounds;
	private final GpuBuffer[] buffers;
	private final DynamicTexture[] textures;
	private final BitSet[] hidden;
	private final boolean[] empty;
	public boolean dirty;

	public MapTile(TileData data, ClientLevel level) {
		this.data = data;
		this.bounds = new AABB(data.minX, data.minY, data.minZ, data.maxX, data.maxY, data.maxZ);
		int n = data.parts.length;
		buffers = new GpuBuffer[n];
		textures = new DynamicTexture[n];
		hidden = new BitSet[n];
		empty = new boolean[n];
		for (int i = 0; i < n; i++) {
			TileData.Part part = data.parts[i];
			NativeImage image;
			if (part.pixels != null) {
				image = new NativeImage(part.texWidth, part.texHeight, false);
				for (int y = 0; y < part.texHeight; y++) {
					for (int x = 0; x < part.texWidth; x++) image.setPixelABGR(x, y, part.pixels[y * part.texWidth + x]);
				}
			} else {
				image = new NativeImage(1, 1, false);
				image.setPixelABGR(0, 0, part.fallbackAbgr);
			}
			textures[i] = new DynamicTexture(() -> "RealWorldMC map tile", image);

			part.pixels = null;

			hidden[i] = computeHidden(part, level);
			upload(i);
		}
	}

	/** Triangles whose block is no longer a map block (mined) are not drawn. */
	private BitSet computeHidden(TileData.Part part, ClientLevel level) {
		BitSet set = new BitSet();
		if (part.triangleCells == null || level == null) return set;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
		boolean chunkLoaded = false;
		for (int t = 0; t < part.triangleCells.length; t++) {
			pos.set(part.triangleCells[t]);
			int cx = SectionPos.blockToSectionCoord(pos.getX()), cz = SectionPos.blockToSectionCoord(pos.getZ());
			if (cx != lastCx || cz != lastCz) {
				lastCx = cx;
				lastCz = cz;
				chunkLoaded = level.hasChunk(cx, cz);
			}
			if (chunkLoaded && pos.getY() >= level.getMinY() && pos.getY() < level.getMaxY()
				&& !EarthBlocks.isMapBlock(level.getBlockState(pos))) {
				set.set(t);
			}
		}
		return set;
	}

	/** Re-evaluates mined triangles; re-uploads only parts that changed. */
	public void refresh(ClientLevel level) {
		dirty = false;
		if (!data.fine) return;
		for (int i = 0; i < data.parts.length; i++) {
			BitSet now = computeHidden(data.parts[i], level);
			if (!now.equals(hidden[i])) {
				hidden[i] = now;
				upload(i);
			}
		}
	}

	private void upload(int i) {
        TileData.Part part = data.parts[i];
        int count = (part.triangleCount() - hidden[i].cardinality()) * 3;
        empty[i] = count == 0;
        if (buffers[i] != null) { buffers[i].close(); buffers[i] = null; }
        if (empty[i]) return;
        ByteBuffer bytes = MemoryUtil.memAlloc(Math.multiplyExact(count, 20));
        try {
            for (int t = 0; t < part.triangleCount(); t++) {
                if (hidden[i].get(t)) continue;
                for (int k = 0; k < 15; k++) bytes.putFloat(part.vertices[t * 15 + k]);
            }
            bytes.flip();
            buffers[i] = RenderSystem.getDevice().createBuffer(() -> "RealWorldMC vertices", GpuBuffer.USAGE_VERTEX, bytes);
        } finally { MemoryUtil.memFree(bytes); }
    }

    public int partCount() { return buffers.length; }
    public boolean isEmpty(int i) { return empty[i]; }
    public GpuBuffer buffer(int i) { return buffers[i]; }
    public int vertexCount(int i) { return (data.parts[i].triangleCount() - hidden[i].cardinality()) * 3; }
    public DynamicTexture texture(int i) { return textures[i]; }

	public boolean contains(BlockPos pos) {
		return pos.getX() >= bounds.minX - 1 && pos.getX() <= bounds.maxX && pos.getY() >= bounds.minY - 2 && pos.getY() <= bounds.maxY
			&& pos.getZ() >= bounds.minZ - 1 && pos.getZ() <= bounds.maxZ;
	}

	public void close() {
		for (GpuBuffer b : buffers) if (b != null) b.close();
		for (DynamicTexture t : textures) t.close();
	}
}
