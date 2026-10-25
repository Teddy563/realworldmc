package dev.realworldmc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds chunks from the voxelized Google 3D tiles. The location (latitude/longitude and the
 * ellipsoid height of the ground there) is stored with the world in level.dat.
 */
public class EarthChunkGenerator extends ChunkGenerator {
	public static final MapCodec<EarthChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
		Codec.DOUBLE.optionalFieldOf("latitude").forGetter(g -> Optional.of(g.latitude)),
		Codec.DOUBLE.optionalFieldOf("longitude").forGetter(g -> Optional.of(g.longitude)),
		Codec.DOUBLE.optionalFieldOf("ground_height").forGetter(g -> Double.isNaN(g.groundHeight) ? Optional.empty() : Optional.of(g.groundHeight))
	).apply(i, EarthChunkGenerator::new));

	public static final int GROUND_Y = 64;
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(6, r -> {
		Thread t = new Thread(r, "RealWorldMC chunk loader");
		t.setDaemon(true);
		return t;
	});

	public final double latitude;
	public final double longitude;
	private volatile double groundHeight;
	private volatile long lastFailureMs;
	private volatile long lastCoverageWarningMs;

	public EarthChunkGenerator(BiomeSource biomeSource, Optional<Double> lat, Optional<Double> lon, Optional<Double> ground) {
		super(biomeSource);
        EarthConfig cfg = EarthConfig.get();
        var origin = SpawnLocations.choose(lat, lon, cfg.randomSpawn, cfg.latitude, cfg.longitude,
            java.util.concurrent.ThreadLocalRandom.current());
        this.latitude = origin.latitude();
        this.longitude = origin.longitude();
		this.groundHeight = ground.orElse(Double.NaN);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	public double groundHeightOrNaN() {
		return groundHeight;
	}

	public long lastFailureMs() {
		return lastFailureMs;
	}

	/** Ellipsoid height of the ground at the origin; asked from the service once per world. */
	public synchronized double groundHeight() {
		if (!Double.isNaN(groundHeight)) return groundHeight;
		Exception last = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			ServiceClient.ensureRunning();
			try {
				var json = ServiceClient.getJson(String.format(Locale.ROOT, "/ground?lat=%.8f&lon=%.8f", latitude, longitude), Duration.ofSeconds(20));
				if (json.has("height")) {
					double measured = json.get("height").getAsDouble();
					if (!Double.isFinite(measured)) throw new IllegalStateException("Invalid map ground height");
					groundHeight = measured;
					RealWorldMC.LOGGER.info("RealWorldMC origin {}, {} ground at {} m", latitude, longitude, groundHeight);
					return groundHeight;
				}
				last = new Exception(json.toString());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new java.util.concurrent.CancellationException("RealWorldMC terrain loading interrupted");
			} catch (Exception e) {
				last = e;
			}
			sleep(2000);
		}
		lastFailureMs = System.currentTimeMillis();
		throw new IllegalStateException("RealWorldMC could not determine ground height. Check the map service and credentials, then reopen this world. No replacement height was saved.", last);
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
		return CompletableFuture.supplyAsync(() -> {
			byte[] data = fetch(chunk);
			if (data.length > 0) fill(chunk, data);
			else fillFallback(chunk);
			Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
			return chunk;
		}, EXECUTOR);
	}

	/** The region a block column belongs to, or null outside of all regions. */
	public EarthRegions.Region regionFor(double x, double z) {
		EarthRegions.Region r = EarthRegions.at(x, z);
		if (r != null && !Double.isNaN(r.groundHeight())) return r;
		if (x * x + z * z <= EarthRegions.MAX_REGION_RADIUS * EarthRegions.MAX_REGION_RADIUS) {
			// Region 0 = this generator's own origin (also before the region list is loaded).
			double h = groundHeight();
			EarthRegions.setGroundOfFirst(h);
			return new EarthRegions.Region(0, latitude, longitude, h);
		}
		return r;
	}

	protected byte[] fetch(ChunkAccess chunk) {
		EarthRegions.Region region = regionFor(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ());
		if (region == null) return new byte[0]; // between places: plain filler
		if (!Double.isFinite(region.groundHeight())) throw new IllegalStateException("Map region is not ready; generation stopped to protect terrain.");
		int minY = chunk.getMinBuildHeight(), height = chunk.getHeight();
		int cx = chunk.getPos().x - region.offsetX() / 16, cz = chunk.getPos().z - region.offsetZ() / 16;
		String q = String.format(Locale.ROOT, "/chunk?%s&cx=%d&cz=%d&minY=%d&height=%d",
			ServiceClient.originQuery(region.latitude(), region.longitude(), region.groundHeight()), cx, cz, minY, height);
		// If the service just failed for another chunk, don't block world loading for long.
		int attempts = System.currentTimeMillis() - lastFailureMs < 30000 ? 1 : 3;
		Exception last = null;
		for (int attempt = 0; attempt < attempts; attempt++) {
			try {
				byte[] data = ServiceClient.getBytes(q, Duration.ofSeconds(30));
				if (data.length == 16 * 16 * height) return data;
				last = new Exception("unexpected size " + data.length);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new java.util.concurrent.CancellationException("RealWorldMC terrain loading interrupted");
			} catch (Exception e) {
				if (e instanceof ServiceClient.ServiceException service && service.noTerrainCoverage()) return noCoverage(chunk);
				last = e;
				ServiceClient.ensureRunning();
			}
			sleep(1000L * Math.min(attempt + 1, 5));
		}
		lastFailureMs = System.currentTimeMillis();
		throw new IllegalStateException("RealWorldMC could not load chunk " + chunk.getPos() + ". Check the map service, then reopen this world. Flat replacement terrain was not saved.", last);
	}

	/** Confirmed absent map coverage is an empty chunk, never invented flat terrain. */
	protected byte[] noCoverage(ChunkAccess chunk) {
		long now = System.currentTimeMillis();
		if (now - lastCoverageWarningMs > 30000) {
			lastCoverageWarningMs = now;
			RealWorldMC.LOGGER.warn("No 3D collision coverage near chunk {}. Keeping this area empty so world loading can continue. Use /earth goto to reach another place; saved empty chunks are not automatically regenerated.", chunk.getPos());
		}
		return new byte[chunk.getHeight() * 256];
	}

	protected BlockState blockForCode(int code) { return EarthBlocks.byCode(code); }

	private void fill(ChunkAccess chunk, byte[] data) {
		int minY = chunk.getMinBuildHeight(), height = chunk.getHeight();
		for (int y = 0; y < height; y++) {
			int wy = minY + y;
			LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(wy));
			for (int z = 0; z < 16; z++) {
				int row = (y * 16 + z) * 16;
				for (int x = 0; x < 16; x++) {
					int code = data[row + x] & 0xff;
					if (code != 0) section.setBlockState(x, wy & 15, z, blockForCode(code), false);
				}
			}
		}
	}

	private static void fillFallback(ChunkAccess chunk) {
		int minY = chunk.getMinBuildHeight();
		for (int wy = minY; wy < GROUND_Y; wy++) {
			BlockState s = wy == minY ? Blocks.BEDROCK.defaultBlockState() : wy == GROUND_Y - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
			LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(wy));
			for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) section.setBlockState(x, wy & 15, z, s, false);
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new java.util.concurrent.CancellationException("RealWorldMC terrain loading interrupted");
		}
	}

	// ---------------------------------------------------------------- no vanilla terrain features

	@Override
	public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving carving) {
	}

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
	}

	@Override
	public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager) {
	}

	@Override
	public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
	}

	@Override
	public int getGenDepth() {
		return 704;
	}

	@Override
	public int getSeaLevel() {
		return GROUND_Y - 1;
	}

	@Override
	public int getMinY() {
		return -64;
	}

	@Override
	public int getSpawnHeight(LevelHeightAccessor level) {
		return GROUND_Y + 1;
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types types, LevelHeightAccessor level, RandomState randomState) {
		return GROUND_Y;
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
		BlockState[] states = new BlockState[level.getHeight()];
		Arrays.fill(states, Blocks.AIR.defaultBlockState());
		for (int i = 0; i < Math.min(states.length, GROUND_Y - level.getMinBuildHeight()); i++) states[i] = Blocks.STONE.defaultBlockState();
		return new NoiseColumn(level.getMinBuildHeight(), states);
	}

	@Override
	public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos pos) {
		list.add(String.format(Locale.ROOT, "RealWorldMC origin %.6f, %.6f (ground %.1f m)", latitude, longitude, groundHeight));
	}
}
