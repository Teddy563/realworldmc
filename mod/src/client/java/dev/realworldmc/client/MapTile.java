package dev.realworldmc.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;


/** GPU side of a tile: one vertex buffer + texture per glTF primitive. Render thread only. */
public final class MapTile {
	public final TileData data;
	public final AABB bounds;
	private final VertexBuffer[] buffers;
	private final DynamicTexture[] textures;
	private java.util.List<dev.realworldmc.MeshCut.Box> cuts;
    private dev.realworldmc.MeshCut.Index cutIndex;
	private final boolean[] empty;
	public boolean dirty;

	public MapTile(TileData data, ClientLevel level) {
        this(data,MapDamage.cuts(new AABB(data.minX,data.minY,data.minZ,data.maxX,data.maxY,data.maxZ),level,data.anchorX,data.anchorY,data.anchorZ));
    }
    static MapTile withCuts(TileData data,java.util.List<dev.realworldmc.MeshCut.Box> damage) {return new MapTile(data,damage);}
    private MapTile(TileData data, java.util.List<dev.realworldmc.MeshCut.Box> damage) {
		this.data = data;
		this.bounds = new AABB(data.minX, data.minY, data.minZ, data.maxX, data.maxY, data.maxZ);
		int n = data.parts.length;
		buffers = new VertexBuffer[n];
		textures = new DynamicTexture[n];
		cuts=java.util.List.copyOf(damage);
        cutIndex=new dev.realworldmc.MeshCut.Index(cuts);
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

    /** Rebuild only when confirmed damage volumes change, including coarse fallback tiles. */
    public void refresh(ClientLevel level) {
        dirty=false;
        var now=MapDamage.cuts(bounds,level,data.anchorX,data.anchorY,data.anchorZ);
        if(now.equals(cuts))return;
        cuts=now;cutIndex=new dev.realworldmc.MeshCut.Index(cuts);
        for(int i=0;i<data.parts.length;i++)upload(i);
    }

    private void upload(int i) {
        float[] source=data.parts[i].vertices;
        var vertices=cuts.isEmpty()?null:new it.unimi.dsi.fastutil.floats.FloatArrayList(source.length);
        for(int offset=0;vertices!=null && offset<source.length;offset+=15) {
            double minX=Math.min(source[offset],Math.min(source[offset+5],source[offset+10]));
            double maxX=Math.max(source[offset],Math.max(source[offset+5],source[offset+10]));
            double minY=Math.min(source[offset+1],Math.min(source[offset+6],source[offset+11]));
            double maxY=Math.max(source[offset+1],Math.max(source[offset+6],source[offset+11]));
            double minZ=Math.min(source[offset+2],Math.min(source[offset+7],source[offset+12]));
            double maxZ=Math.max(source[offset+2],Math.max(source[offset+7],source[offset+12]));
            var candidates=cutIndex.query(new dev.realworldmc.MeshCut.Box(minX,minY,minZ,maxX,maxY,maxZ));
            if(candidates.isEmpty()){vertices.addElements(vertices.size(),source,offset,15);continue;}
            var triangle=new java.util.ArrayList<dev.realworldmc.MeshCut.Vertex>(3);
            for(int k=0;k<3;k++){int o=offset+k*5;triangle.add(new dev.realworldmc.MeshCut.Vertex(source[o],source[o+1],source[o+2],source[o+3],source[o+4]));}
            java.util.List<java.util.List<dev.realworldmc.MeshCut.Vertex>> polygons=java.util.List.of(triangle);
            for(var box:candidates){
                var remaining=new java.util.ArrayList<java.util.List<dev.realworldmc.MeshCut.Vertex>>();
                for(var polygon:polygons)remaining.addAll(dev.realworldmc.MeshCut.subtract(polygon,box));
                polygons=remaining;if(polygons.isEmpty())break;
            }
            for(var polygon:polygons)for(int t=1;t<polygon.size()-1;t++)for(var vertex:java.util.List.of(polygon.getFirst(),polygon.get(t),polygon.get(t+1))) {
                vertices.add((float)vertex.x());vertices.add((float)vertex.y());vertices.add((float)vertex.z());vertices.add((float)vertex.u());vertices.add((float)vertex.v());
            }
        }
        int length=vertices==null?source.length:vertices.size();
        float[] draw=vertices==null?source:vertices.elements();
        empty[i]=length==0;if(empty[i])return;
        try(ByteBufferBuilder bytes=new ByteBufferBuilder(length/5*DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder bb=new BufferBuilder(bytes,VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
            for(int o=0;o<length;o+=5)bb.addVertex(draw[o],draw[o+1],draw[o+2]).setUv(draw[o+3],draw[o+4]);
            try(MeshData mesh=bb.buildOrThrow()){buffers[i].bind();buffers[i].upload(mesh);VertexBuffer.unbind();}
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
