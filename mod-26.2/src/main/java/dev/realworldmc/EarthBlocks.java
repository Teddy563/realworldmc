package dev.realworldmc;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * "Map blocks" are solid, invisible blocks standing in for the Google mesh: Minecraft
 * collides with them, mines them and drops normal items, while the client draws the
 * photorealistic mesh wherever they are.
 */
public final class EarthBlocks {
	/** Invisible but full and occluding, so neighbouring faces stay culled behind the mesh. */
	public static class MapBlock extends Block {
		public MapBlock(Properties properties) {
			super(properties);
		}

		@Override
		protected RenderShape getRenderShape(BlockState state) {
			return RenderShape.INVISIBLE;
		}
	}

	public static final Block MAP_GRASS = register("map_grass", props(MapColor.GRASS, 0.6f, SoundType.GRASS, false));
	public static final Block MAP_LEAVES = register("map_leaves", props(MapColor.PLANT, 0.2f, SoundType.GRASS, false));
	public static final Block MAP_DIRT = register("map_dirt", props(MapColor.DIRT, 0.5f, SoundType.GRAVEL, false));
	public static final Block MAP_STONE = register("map_stone", props(MapColor.STONE, 1.5f, SoundType.STONE, true));
	public static final Block MAP_ASPHALT = register("map_asphalt", props(MapColor.COLOR_GRAY, 1.5f, SoundType.STONE, true));
	public static final Block MAP_PLASTER = register("map_plaster", props(MapColor.SNOW, 1.2f, SoundType.STONE, true));
	public static final Block MAP_BRICK = register("map_brick", props(MapColor.COLOR_RED, 2.0f, SoundType.STONE, true));
	public static final Block MAP_WOOD = register("map_wood", props(MapColor.WOOD, 2.0f, SoundType.WOOD, false));
	public static final Block MAP_SAND = register("map_sand", props(MapColor.SAND, 0.5f, SoundType.SAND, false));

	private static final BlockState[] BY_CODE = new BlockState[256];

	private EarthBlocks() {}

	private static BlockBehaviour.Properties props(MapColor color, float strength, SoundType sound, boolean needsTool) {
		BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(color).strength(strength, strength * 3).sound(sound);
		return needsTool ? p.requiresCorrectToolForDrops() : p;
	}

	private static Block register(String name, BlockBehaviour.Properties props) {
		return Registry.register(BuiltInRegistries.BLOCK, RealWorldMC.id(name), new MapBlock(props.setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, RealWorldMC.id(name)))));
	}

	public static void init() {
		// Codes must match service/src/voxel.mjs (M).
		BY_CODE[0] = Blocks.AIR.defaultBlockState();
		BY_CODE[1] = MAP_GRASS.defaultBlockState();
		BY_CODE[2] = MAP_LEAVES.defaultBlockState();
		BY_CODE[3] = MAP_DIRT.defaultBlockState();
		BY_CODE[4] = MAP_STONE.defaultBlockState();
		BY_CODE[5] = MAP_ASPHALT.defaultBlockState();
		BY_CODE[6] = MAP_PLASTER.defaultBlockState();
		BY_CODE[7] = MAP_BRICK.defaultBlockState();
		BY_CODE[8] = MAP_WOOD.defaultBlockState();
		BY_CODE[9] = MAP_SAND.defaultBlockState();
		BY_CODE[10] = MAP_DIRT.defaultBlockState(); // invisible filler
		BY_CODE[20] = Blocks.DIRT.defaultBlockState();
		BY_CODE[21] = Blocks.STONE.defaultBlockState();
		BY_CODE[22] = Blocks.BEDROCK.defaultBlockState();
		BY_CODE[23] = Blocks.WATER.defaultBlockState();
		BY_CODE[24] = Blocks.GRAVEL.defaultBlockState();
		BY_CODE[25] = Blocks.GRASS_BLOCK.defaultBlockState();
		BY_CODE[26] = Blocks.SAND.defaultBlockState();
	}

	public static BlockState byCode(int code) {
		BlockState s = BY_CODE[code & 0xff];
		return s != null ? s : BY_CODE[4];
	}

	public static boolean isMapBlock(BlockState state) {
		return state.getBlock() instanceof MapBlock;
	}
}
