package dev.realworldmc.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.realworldmc.EarthBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.util.BitSet;

/** GPU side of a tile: one vertex buffer + texture per glTF primitive. Render thread only. */
public final class MapTile {
	public final TileData data;
	public final AABB bounds;
	private final VertexBuffer[] buffers;
	private final DynamicTexture[] textures;
	private final BitSet[] hidden;
	private final boolean[] empty;
	public boolean dirty;

	public MapTile(TileData data, ClientLevel level) {
		this.data = data;
		this.bounds = new AABB(data.minX, data.minY, data.minZ, data.maxX, data.maxY, data.maxZ);
		int n = data.parts.length;
		buffers = new VertexBuffer[n];
		textures = new DynamicTexture[n];
		hidden = new BitSet[n];
		empty = new boolean[n];
		for (int i = 0; i < n; i++) {
			TileData.Part part = data.parts[i];
			NativeImage image;
			if (part.pixels != null) {
				image = new NativeImage(part.texWidth, part.texHeight, false);
				for (int y = 0; y < part.texHeight; y++) {
					for (int x = 0; x < part.texWidth; x++) image.setPixelRGBA(x, y, part.pixels[y * part.texWidth + x]);
				}
			} else {
				image = new NativeImage(1, 1, false);
				image.setPixelRGBA(0, 0, part.fallbackAbgr);
			}
			textures[i] = new DynamicTexture(image);
			enableMipmaps(textures[i].getId(), image.getWidth(), image.getHeight());
			part.pixels = null;
			buffers[i] = new VertexBuffer(VertexBuffer.Usage.STATIC);
			hidden[i] = computeHidden(part, level);
			upload(i);
		}
	}

	private static float maxAnisotropy = -1;

	/** Trilinear mipmaps + anisotropic filtering: keeps photo textures sharp and flicker-free at grazing angles. */
	private static void enableMipmaps(int id, int w, int h) {
		GlStateManager._bindTexture(id);
		int levels = 31 - Integer.numberOfLeadingZeros(Math.max(w, h));
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		if (maxAnisotropy < 0) {
			maxAnisotropy = GL.getCapabilities().GL_EXT_texture_filter_anisotropic
				? GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT) : 0;
		}
		if (maxAnisotropy > 0) {
			GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, Math.min(16f, maxAnisotropy));
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
			if (chunkLoaded && pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()
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
		float[] v = part.vertices;
		int tris = part.triangleCount() - hidden[i].cardinality();
		empty[i] = tris == 0;
		if (empty[i]) return;
		try (ByteBufferBuilder bytes = new ByteBufferBuilder(tris * 3 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
			BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
			for (int t = 0, n = part.triangleCount(); t < n; t++) {
				if (hidden[i].get(t)) continue;
				for (int k = 0; k < 3; k++) {
					int o = (t * 3 + k) * 5;
					bb.addVertex(v[o], v[o + 1], v[o + 2]).setUv(v[o + 3], v[o + 4]);
				}
			}
			try (MeshData mesh = bb.buildOrThrow()) {
				buffers[i].bind();
				buffers[i].upload(mesh);
				VertexBuffer.unbind();
			}
		}
	}

	public int partCount() {
		return buffers.length;
	}

	public boolean isEmpty(int i) {
		return empty[i];
	}

	public VertexBuffer buffer(int i) {
		return buffers[i];
	}

	public int textureId(int i) {
		return textures[i].getId();
	}

	public boolean contains(BlockPos pos) {
		return pos.getX() >= bounds.minX - 1 && pos.getX() <= bounds.maxX && pos.getY() >= bounds.minY - 2 && pos.getY() <= bounds.maxY
			&& pos.getZ() >= bounds.minZ - 1 && pos.getZ() <= bounds.maxZ;
	}

	public void close() {
		for (VertexBuffer b : buffers) b.close();
		for (DynamicTexture t : textures) t.close();
	}
}
