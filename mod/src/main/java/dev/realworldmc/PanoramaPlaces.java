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

/** Permanent geographic anchors, independent of panorama IDs (which Google can change). */
public final class PanoramaPlaces {
    private static final List<EarthRegions.Region> PLACES = new CopyOnWriteArrayList<>();
    private static Storage storage;
    private PanoramaPlaces() {}
    public static void load(MinecraftServer server) {
        storage = server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Storage::new, Storage::load, null), "realworldmc_street_places");
        PLACES.clear(); PLACES.addAll(storage.saved);
    }
    public static void clear() { PLACES.clear(); storage = null; }
    public static EarthRegions.Region at(double x, double z) {
        int index = EarthRegions.indexAt(x, z) - 1;
        return index >= 0 && index < PLACES.size() ? PLACES.get(index) : null;
    }
    public static EarthRegions.Region nearby(double lat, double lon) {
        EarthRegions.Region best = null; double distance = 2000;
        for (var r : PLACES) {
            double[] local = local(r, lat, lon); double d = Math.hypot(local[0], local[2]);
            if (d < distance) { best = r; distance = d; }
        }
        return best;
    }
    public static double[] local(EarthRegions.Region r, double lat, double lon) {
        double h = Double.isFinite(r.groundHeight()) ? r.groundHeight() : 0;
        return Geo.geodeticToMc(r.latitude(), r.longitude(), h, lat, lon, h);
    }
    public static EarthRegions.Region add(double lat, double lon, double ground) {
        if (storage == null || PLACES.size() >= 100_000) throw new IllegalStateException("Street world unavailable");
        var r = new EarthRegions.Region(PLACES.size() + 1, lat, lon, ground);
        storage.saved.add(r); storage.setDirty(); PLACES.add(r); return r;
    }
    public static final class Storage extends SavedData {
        final List<EarthRegions.Region> saved = new ArrayList<>();
        static Storage load(CompoundTag tag, HolderLookup.Provider provider) {
            Storage s = new Storage(); ListTag list = tag.getList("places", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                s.saved.add(new EarthRegions.Region(i + 1, t.getDouble("lat"), t.getDouble("lon"), t.contains("height") ? t.getDouble("height") : Double.NaN));
            }
            return s;
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            for (var r : saved) {
                CompoundTag t = new CompoundTag(); t.putDouble("lat", r.latitude()); t.putDouble("lon", r.longitude());
                if (Double.isFinite(r.groundHeight())) t.putDouble("height", r.groundHeight()); list.add(t);
            }
            tag.put("places", list); return tag;
        }
    }
}
