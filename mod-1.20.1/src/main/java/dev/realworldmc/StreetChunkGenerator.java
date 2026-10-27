package dev.realworldmc;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Editable blocks generated once by vanilla chunk storage. Never repaint visited terrain. */
public final class StreetChunkGenerator extends EarthChunkGenerator {
    // 1.20.1 looks generators up by codec identity: register and return this one instance.
    public static final com.mojang.serialization.Codec<StreetChunkGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source").xmap(StreetChunkGenerator::new, g -> g.biomeSource).codec();
    private final Set<Integer> missingCoverage = ConcurrentHashMap.newKeySet();
    public StreetChunkGenerator(BiomeSource biomeSource) { super(biomeSource, Optional.of(0d), Optional.of(0d), Optional.of(0d)); }
    @Override protected com.mojang.serialization.Codec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getGenDepth() { return 384; }
    @Override public EarthRegions.Region regionFor(double x, double z) {
        var r = PanoramaPlaces.at(x, z); return r != null && Double.isFinite(r.groundHeight()) ? r : null;
    }
    @Override protected byte[] fetch(ChunkAccess chunk) {
        try { return super.fetch(chunk); }
        catch (java.util.concurrent.CancellationException e) { throw e; }
        catch (Exception e) {
            // No 3D coverage: keep usable deep ground. Saved chunks are never regenerated.
            missingCoverage.add(EarthRegions.indexAt(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ()));
            return new byte[0];
        }
    }
    public boolean incomplete(int region) { return missingCoverage.contains(region); }
    @Override protected byte[] noCoverage(ChunkAccess chunk) {
        missingCoverage.add(EarthRegions.indexAt(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ()));
        return new byte[0]; // Street View deliberately supplies deep simulated ground.
    }
    @Override protected BlockState blockForCode(int code) {
        return switch (code) {
            case 1, 25 -> Blocks.GRASS_BLOCK.defaultBlockState();
            case 2 -> Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
            case 3, 10, 20 -> Blocks.DIRT.defaultBlockState();
            case 4, 21 -> Blocks.STONE.defaultBlockState();
            case 5 -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case 6 -> Blocks.WHITE_TERRACOTTA.defaultBlockState();
            case 7 -> Blocks.BRICKS.defaultBlockState();
            case 8 -> Blocks.OAK_PLANKS.defaultBlockState();
            case 9 -> Blocks.SANDSTONE.defaultBlockState();
            default -> EarthBlocks.byCode(code);
        };
    }
}
