package dev.realworldmc.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** CPU-side copy of a render tile as sent by the service (format "ECT1", see server.mjs). */
public final class TileData {
	public final int id;
	public final boolean fine;
	public final double anchorX, anchorY, anchorZ;
	public final Part[] parts;
	public double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
	public double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

	public static final class Part {
		/** x, y, z (relative to the anchor), u, v per vertex; 3 vertices per triangle. */
		public final float[] vertices;
		public final int texWidth, texHeight;
		/** ABGR pixels as NativeImage expects them, or null. */
		public int[] pixels; // released after upload
		public final int fallbackAbgr;
		/** Packed block position each triangle belongs to (fine tiles only). */
		public long[] triangleCells;

		Part(float[] vertices, int texWidth, int texHeight, int[] pixels, int fallbackAbgr) {
			this.vertices = vertices;
			this.texWidth = texWidth;
			this.texHeight = texHeight;
			this.pixels = pixels;
			this.fallbackAbgr = fallbackAbgr;
		}

		public int triangleCount() {
			return vertices.length / 15;
		}
	}

	private TileData(int id, boolean fine, double ax, double ay, double az, Part[] parts) {
		this.id = id;
		this.fine = fine;
		this.anchorX = ax;
		this.anchorY = ay;
		this.anchorZ = az;
		this.parts = parts;
	}

	/** offX/offZ: block offset of the region the tile belongs to. */
	public static TileData parse(int id, boolean fine, byte[] bytes, int offX, int offZ) {
		ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		if (b.get() != 'E' || b.get() != 'C' || b.get() != 'T' || b.get() != '1') throw new IllegalArgumentException("bad tile header");
		int count = b.getInt();
		double ax = b.getDouble() + offX, ay = b.getDouble(), az = b.getDouble() + offZ;
		Part[] parts = new Part[count];
		TileData tile = new TileData(id, fine, ax, ay, az, parts);
		for (int p = 0; p < count; p++) {
			int vertexCount = b.getInt(), tw = b.getInt(), th = b.getInt();
			int r = b.get() & 0xff, g = b.get() & 0xff, bl = b.get() & 0xff, a = b.get() & 0xff;
			float[] v = new float[vertexCount * 5];
			b.asFloatBuffer().get(v);
			b.position(b.position() + v.length * 4);
			int[] pixels = null;
			if (tw > 0 && th > 0) {
				pixels = new int[tw * th];
				for (int i = 0; i < pixels.length; i++) {
					int pr = b.get() & 0xff, pg = b.get() & 0xff, pb = b.get() & 0xff, pa = b.get() & 0xff;
					pixels[i] = pa << 24 | pb << 16 | pg << 8 | pr;
				}
			}
			Part part = new Part(v, tw, th, pixels, a << 24 | bl << 16 | g << 8 | r);
			parts[p] = part;
			for (int i = 0; i < v.length; i += 5) {
				double x = v[i] + ax, y = v[i + 1] + ay, z = v[i + 2] + az;
				tile.minX = Math.min(tile.minX, x); tile.maxX = Math.max(tile.maxX, x);
				tile.minY = Math.min(tile.minY, y); tile.maxY = Math.max(tile.maxY, y);
				tile.minZ = Math.min(tile.minZ, z); tile.maxZ = Math.max(tile.maxZ, z);
			}
			if (fine) {
				// Same cell convention as the voxelizer: a block at y covers mesh heights [y+0.5, y+1.5).
				long[] cells = new long[part.triangleCount()];
				for (int t = 0; t < cells.length; t++) {
					int o = t * 15;
					double cx = (v[o] + v[o + 5] + v[o + 10]) / 3 + ax;
					double cy = (v[o + 1] + v[o + 6] + v[o + 11]) / 3 + ay;
					double cz = (v[o + 2] + v[o + 7] + v[o + 12]) / 3 + az;
					cells[t] = net.minecraft.core.BlockPos.asLong((int) Math.floor(cx), (int) Math.floor(cy - 0.5), (int) Math.floor(cz));
				}
				part.triangleCells = cells;
			}
		}
		return tile;
	}
}
