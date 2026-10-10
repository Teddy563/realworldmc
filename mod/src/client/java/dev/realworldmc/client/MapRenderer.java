package dev.realworldmc.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.realworldmc.EarthBlocks;
import dev.realworldmc.RealWorldMC;
import dev.realworldmc.EarthRegions;
import dev.realworldmc.RegionsPayload;
import dev.realworldmc.ServiceClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streams render tiles from the service around the camera and draws them into the world.
 * Selection and downloads happen on background threads, GPU work on the render thread.
 */
public final class MapRenderer {
	public static final MapRenderer INSTANCE = new MapRenderer();

	private static final int MAX_UPLOADS_PER_FRAME = 3;
	private static final int MAX_REFRESH_PER_FRAME = 4;
	/** Extra tiles kept on the GPU beyond the current view. */
	private static final int CACHE_EXTRA = 600;

	private final ExecutorService io = Executors.newFixedThreadPool(6, r -> {
		Thread t = new Thread(r, "RealWorldMC tile loader");
		t.setDaemon(true);
		return t;
	});
    // Flight selection must not wait behind a queue of obsolete detail downloads.
    private final ExecutorService selector = Executors.newSingleThreadExecutor(r -> {
        Thread t=new Thread(r,"RealWorldMC view selector");t.setDaemon(true);return t;
    });

	/** All places of the world (from the server); null when not in an RealWorldMC world. */
	private volatile List<EarthRegions.Region> regions;
	/** Region the camera is in; tiles are only streamed for this one. */
	private volatile EarthRegions.Region current;
	/** Bumped whenever the region changes so stale downloads are dropped. */
	private volatile int epoch;

	private final Map<Integer, MapTile> loaded = new HashMap<>();
	private final Set<Integer> loading = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final ConcurrentLinkedQueue<TileData> uploadQueue = new ConcurrentLinkedQueue<>();
	/** Tiles drawn this frame. */
	private List<Integer> displayed = new ArrayList<>();
	/** A tile the service wants drawn; chain = ancestors with content, nearest first. */
	private record Want(int id, boolean fine, int[] chain) {
		int parent() {
			return chain.length > 0 ? chain[0] : 0;
		}
	}

	/** Latest selection from the service. */
	private volatile List<Want> wanted = List.of();
	/** Ids of wanted tiles and their parents; queued downloads for anything else are skipped. */
	private volatile Set<Integer> wantedIds = Set.of();
	/** Frame number a tile was last drawn in (for keeping a cache of recently seen tiles). */
	private final Map<Integer, Long> lastDrawn = new HashMap<>();
	private long frame;
	private final AtomicBoolean selecting = new AtomicBoolean();
	private int tickCounter;

	public volatile String status = "";
	public volatile String attributionText = "Google, RealWorldMC";

	public void setRegions(RegionsPayload payload) {
		regions = payload.regions();
		RealWorldMC.LOGGER.info("RealWorldMC: {} place(s) in this world", regions.size());
	}

	/** True while in an RealWorldMC world. */
	public boolean enabled() {
		return regions != null;
	}

	private void switchRegion(EarthRegions.Region region) {
		resetTiles();
		current = region;
	}

	/** Render thread. */
	public void clear() {
		regions = null;
		resetTiles();
	}

	private void resetTiles() {
		epoch++;
		current = null;
		wanted = List.of();
		displayed = new ArrayList<>();
		uploadQueue.clear();
		RenderSystem.recordRenderCall(() -> {
			synchronized (loaded) {
				for (MapTile t : loaded.values()) t.close();
				loaded.clear();
				lastDrawn.clear();
			}
		});
		attributionText = "Google, RealWorldMC";
		status = "";
	}

	private boolean active(ClientLevel level) {
		return current != null && level != null && level.dimension() == Level.OVERWORLD;
	}

	// ---------------------------------------------------------------- client tick: selection + downloads

	public void tick(Minecraft mc) {
		List<EarthRegions.Region> all = regions;
		if (all == null || mc.level == null || mc.gameRenderer == null || mc.level.dimension() != Level.OVERWORLD) return;
		if (!status.isEmpty()) mc.gui.setOverlayMessage(dev.realworldmc.RWMessages.message(status), false);
		Vec3 camAbs = mc.gameRenderer.getMainCamera().getPosition();
		int index = EarthRegions.indexAt(camAbs.x, camAbs.z);
		EarthRegions.Region region = index >= 0 && index < all.size() && !Double.isNaN(all.get(index).groundHeight()) ? all.get(index) : null;
		if (!java.util.Objects.equals(region, current)) switchRegion(region);
		if (region == null) return;
		if (++tickCounter % (mc.player!=null && (mc.player.isFallFlying() || mc.player.getAbilities().flying)?5:10) != 0 || !selecting.compareAndSet(false, true)) return;
		Vec3 cam = camAbs.subtract(region.offsetX(), 0, region.offsetZ());
		EarthRegions.Region o = region;
		int myEpoch = epoch;
		selector.execute(() -> {
			try {
				String q = String.format(Locale.ROOT, "/render/select?%s&x=%.2f&y=%.2f&z=%.2f",
					ServiceClient.originQuery(o.latitude(), o.longitude(), o.groundHeight()), cam.x, cam.y, cam.z);
				JsonObject res = ServiceClient.getJson(q, Duration.ofSeconds(30));
				if (myEpoch != epoch) return;
				List<Want> list = new ArrayList<>();
				for (JsonElement e : res.getAsJsonArray("tiles")) {
					JsonObject t = e.getAsJsonObject();
					JsonArray c = t.getAsJsonArray("chain");
					int[] chain = new int[c == null ? 0 : c.size()];
					for (int i = 0; i < chain.length; i++) chain[i] = c.get(i).getAsInt();
					list.add(new Want(t.get("id").getAsInt(), t.get("fine").getAsBoolean(), chain));
				}
				List<String> attr = new ArrayList<>();
				JsonArray a = res.getAsJsonArray("attribution");
				if (a != null) for (JsonElement e : a) attr.add(e.getAsString());
                if (!attr.contains("Google")) attr.add(0, "Google");
                if (!attr.contains("RealWorldMC")) attr.add("RealWorldMC");
                attributionText = String.join(", ", attr);
				Set<Integer> ids = new HashSet<>();
				for (Want t : list) {
					ids.add(t.id());
                    for(int i=0;i<Math.min(2,t.chain().length);i++)ids.add(t.chain()[i]);
				}
				wantedIds = ids;
				wanted = list;
				status = "";
				// Parents first: they are coarse, load fast and fill the view while the detail streams in.
                for (Want t : list) if(t.chain().length>1)requestTile(t.chain()[1],false,o,myEpoch);
				for (Want t : list) if (t.parent() != 0) requestTile(t.parent(), false, o, myEpoch);
				for (Want t : list) requestTile(t.id(), t.fine(), o, myEpoch);
			} catch (Exception e) {
				if(myEpoch!=epoch)return;
				status = "Map connection interrupted. Check /earth setup.";
				ServiceClient.ensureRunning();
			} finally {
				selecting.set(false);
			}
		});
	}

	private void requestTile(int id, boolean fine, EarthRegions.Region o, int myEpoch) {
		synchronized (loaded) {
			if (loaded.containsKey(id)) return;
		}
		if (!loading.add(id)) return;
		io.execute(() -> {
			try {
				if (myEpoch != epoch || !wantedIds.contains(id)) return; // flew past it already
				byte[] bytes = ServiceClient.getBytes(String.format(Locale.ROOT, "/render/tile?%s&id=%d",
					ServiceClient.originQuery(o.latitude(), o.longitude(), o.groundHeight()), id), Duration.ofSeconds(60));
				if (myEpoch == epoch) uploadQueue.add(TileData.parse(id, fine, bytes, o.offsetX(), o.offsetZ()));
			} catch (Exception e) {
				RealWorldMC.LOGGER.warn("Tile {} failed: {}", id, e.toString());
			} finally {
				loading.remove(id);
			}
		});
	}

	// ---------------------------------------------------------------- render thread

	private void uploadAndSwitch(ClientLevel level) {
		for (int i = 0; i < MAX_UPLOADS_PER_FRAME; i++) {
			TileData d = uploadQueue.poll();
			if (d == null) break;
			synchronized (loaded) {
				if (!loaded.containsKey(d.id)) loaded.put(d.id, new MapTile(d, level));
			}
		}

		// Wanted tiles are grouped by their parent. A complete group is drawn as is; an incomplete
		// one is covered by its nearest already loaded ancestor ("cover"), so the view never has
		// holes. Everything below a drawn cover is hidden to avoid two LODs on top of each other.
		List<Want> want = wanted;
		Map<Integer, List<Want>> groups = new HashMap<>();
		Map<Integer, int[]> ancestorsOf = new HashMap<>();
		for (Want w : want) {
			groups.computeIfAbsent(w.parent(), k -> new ArrayList<>()).add(w);
			int[] c = w.chain();
			for (int i = 0; i < c.length; i++) ancestorsOf.putIfAbsent(c[i], java.util.Arrays.copyOfRange(c, i + 1, c.length));
		}
		LinkedHashSet<Integer> draw = new LinkedHashSet<>();
		synchronized (loaded) {
			Set<Integer> covers = new HashSet<>();
			List<Want> members = new ArrayList<>();
			for (Map.Entry<Integer, List<Want>> g : groups.entrySet()) {
				boolean complete = true;
				for (Want w : g.getValue()) if (!loaded.containsKey(w.id())) complete = false;
				int cover = 0;
				if (!complete) {
					for (int a : g.getValue().get(0).chain()) {
						if (loaded.containsKey(a)) {
							cover = a;
							break;
						}
					}
				}
				if (cover != 0) covers.add(cover);
				else for (Want w : g.getValue()) if (loaded.containsKey(w.id())) members.add(w);
			}
			for (Integer c : covers) {
				boolean underAnother = false;
				for (int a : ancestorsOf.getOrDefault(c, new int[0])) if (covers.contains(a)) underAnother = true;
				if (!underAnother) draw.add(c);
			}
			for (Want w : members) {
				boolean covered = false;
				for (int a : w.chain()) if (draw.contains(a)) covered = true;
				if (!covered) draw.add(w.id());
			}
			frame++;
			for (Integer id : draw) lastDrawn.put(id, frame);
			displayed = new ArrayList<>(draw);

			// Keep recently drawn tiles around (turning back must not reload), evict the oldest beyond the budget.
			if (frame % 60 == 0 && loaded.size() > want.size() * 2 + CACHE_EXTRA) {
				Set<Integer> keep = new HashSet<>(draw);
				for (Want w : want) {
					keep.add(w.id());
                    for(int i=0;i<Math.min(2,w.chain().length);i++)keep.add(w.chain()[i]);
				}
				List<Integer> candidates = new ArrayList<>();
				for (Integer id : loaded.keySet()) if (!keep.contains(id)) candidates.add(id);
				candidates.sort((a, b) -> Long.compare(lastDrawn.getOrDefault(a, 0L), lastDrawn.getOrDefault(b, 0L)));
				int excess = loaded.size() - (want.size() * 2 + CACHE_EXTRA);
				for (int i = 0; i < Math.min(excess, candidates.size()); i++) {
					Integer id = candidates.get(i);
					loaded.remove(id).close();
					lastDrawn.remove(id);
				}
			}
		}
	}

	public void render(WorldRenderContext ctx) {
		ClientLevel level = ctx.world();
		if (!active(level)) return;
		uploadAndSwitch(level);

		int refreshed = 0;
		for (Integer id : displayed) {
			MapTile t = loaded.get(id);
			if (t != null && t.dirty && refreshed++ < MAX_REFRESH_PER_FRAME) t.refresh(level);
		}

		Vec3 cam = ctx.camera().getPosition();
		Frustum frustum = ctx.frustum();
		Matrix4f proj = ctx.projectionMatrix();
		Matrix4f view = ctx.positionMatrix();
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float light = level.getSkyDarken(partial);

		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		ShaderInstance shader = RenderSystem.getShader();
		if (shader == null) return;
		RenderSystem.setShaderColor(light, light, light, 1f);
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		RenderSystem.disableCull();

		for (Integer id : displayed) {
			MapTile t = loaded.get(id);
			if (t == null || (frustum != null && !frustum.isVisible(t.bounds))) continue;
			Matrix4f mv = new Matrix4f(view).translate(
				(float) (t.data.anchorX - cam.x), (float) (t.data.anchorY - cam.y), (float) (t.data.anchorZ - cam.z));
			for (int i = 0; i < t.partCount(); i++) {
				if (t.isEmpty(i)) continue;
				RenderSystem.setShaderTexture(0, t.textureId(i));
				VertexBuffer vb = t.buffer(i);
				vb.bind();
				vb.drawWithShader(mv, proj, shader);
			}
		}
		VertexBuffer.unbind();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		RenderSystem.enableCull();
	}

	/** A block changed on the client: if a map block appeared or vanished, re-mask affected tiles. */
	public void onBlockChanged(BlockPos pos, BlockState before, BlockState after) {
		if (current == null || EarthBlocks.isMapBlock(before) == EarthBlocks.isMapBlock(after)) return;
		markDirty(t -> t.contains(pos));
	}

	public void onChunkLoaded(ChunkPos chunk) {
		if (current == null) return;
		double x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ();
		markDirty(t -> t.bounds.maxX >= x0 && t.bounds.minX <= x0 + 16 && t.bounds.maxZ >= z0 && t.bounds.minZ <= z0 + 16);
	}

	private void markDirty(java.util.function.Predicate<MapTile> test) {
		RenderSystem.recordRenderCall(() -> {
			for (MapTile t : loaded.values()) if (t.data.fine && test.test(t)) t.dirty = true;
		});
	}

	public int displayedCount() {
		return displayed.size();
	}
}
