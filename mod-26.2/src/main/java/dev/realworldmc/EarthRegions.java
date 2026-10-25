package dev.realworldmc;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A world contains many places ("regions"), each with its own geographic origin, laid out on
 * a grid far apart in Minecraft coordinates. Keeping every region small keeps the flat
 * Minecraft frame accurate on the round Earth. Region 0 is the world's starting location.
 */
public final class EarthRegions {
	public static final int SPACING = 50_000;   // blocks between region centers (multiple of 16)
	public static final int GRID = 400;         // regions per row; 400 * 50k = 20M < world border
	/** Places closer than this to an existing region center reuse that region. */
	public static final double REUSE_RADIUS = 15_000;
	/** Maximum block radius from region center; beyond this lies the natural buffer zone between places. */
	public static final double MAX_REGION_RADIUS = 22_000;

	public record Region(int index, double latitude, double longitude, double groundHeight) {
		public int offsetX() {
			return (index % GRID) * SPACING;
		}

		public int offsetZ() {
			return (index / GRID) * SPACING;
		}
	}

	private static final List<Region> regions = new CopyOnWriteArrayList<>();
	private static Storage storage;
	/** Bumped on every change so clients can be resynced. */
	public static volatile int version;

	private EarthRegions() {}

	/** Region whose cell contains the block position within active radius, or null in the buffer zone. */
	public static Region at(double x, double z) {
		int index = indexAt(x, z);
		if (index < 0 || index >= regions.size()) return null;
		Region r = regions.get(index);
		double dx = x - r.offsetX(), dz = z - r.offsetZ();
		return (dx * dx + dz * dz <= MAX_REGION_RADIUS * MAX_REGION_RADIUS) ? r : null;
	}

	/** Grid cell index for a block position (-1 outside the grid). Same on client and server. */
	public static int indexAt(double x, double z) {
		long gx = Math.round(x / SPACING), gz = Math.round(z / SPACING);
		if (gx < 0 || gz < 0 || gx >= GRID) return -1;
		return (int) (gz * GRID + gx);
	}

	public static List<Region> all() {
		return List.copyOf(regions);
	}

	/** Called when a server starts: loads the saved regions (region 0 comes from the generator). */
	public static void load(MinecraftServer server, EarthChunkGenerator gen) {
		regions.clear();
		storage = server.overworld().getDataStorage().computeIfAbsent(
			new net.minecraft.world.level.saveddata.SavedDataType<>(net.minecraft.resources.Identifier.withDefaultNamespace("earthcraft_regions"), Storage::new, CompoundTag.CODEC.xmap(Storage::load, Storage::save), null));
		if (storage.saved.isEmpty()) {
			// Region 0 is the generator's own origin; its ground height may still be unknown.
			storage.saved.add(new Region(0, gen.latitude, gen.longitude, gen.groundHeightOrNaN()));
			storage.setDirty();
		}
		regions.addAll(storage.saved);
		version++;
		if (!Double.isNaN(gen.groundHeightOrNaN())) setGroundOfFirst(gen.groundHeightOrNaN());
	}

	public static void unload() {
		regions.clear();
		storage = null;
	}

	public static synchronized void setGroundOfFirst(double h) {
		if (!regions.isEmpty() && Double.isNaN(regions.get(0).groundHeight())) {
			Region r = regions.get(0);
			replace(new Region(0, r.latitude(), r.longitude(), h));
		}
	}

	public static synchronized Region add(double lat, double lon, double ground) {
		Region r = new Region(regions.size(), lat, lon, ground);
		regions.add(r);
		version++;
		if (storage != null) {
			storage.saved.add(r);
			storage.setDirty();
		}
		return r;
	}

	private static void replace(Region r) {
		regions.set(r.index(), r);
		version++;
		if (storage != null) {
			storage.saved.set(r.index(), r);
			storage.setDirty();
		}
	}

	public static final class Storage extends SavedData {
		final List<Region> saved = new ArrayList<>();

		static Storage load(CompoundTag tag) {
			Storage s = new Storage();
			ListTag list = tag.getListOrEmpty("regions");
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = list.getCompoundOrEmpty(i);
				s.saved.add(new Region(i, t.getDoubleOr("lat", 0), t.getDoubleOr("lon", 0), t.contains("h") ? t.getDoubleOr("h", Double.NaN) : Double.NaN));
			}
			return s;
		}

		public CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			ListTag list = new ListTag();
			for (Region r : saved) {
				CompoundTag t = new CompoundTag();
				t.putDouble("lat", r.latitude());
				t.putDouble("lon", r.longitude());
				if (!Double.isNaN(r.groundHeight())) t.putDouble("h", r.groundHeight());
				list.add(t);
			}
			tag.put("regions", list);
			return tag;
		}
	}
}
