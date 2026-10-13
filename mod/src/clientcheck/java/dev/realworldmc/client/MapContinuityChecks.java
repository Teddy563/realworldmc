package dev.realworldmc.client;

import dev.realworldmc.EarthRegions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** Empty mesh tiles exercise selection/ownership without provider calls or image fixtures. */
final class MapContinuityChecks {
    private static Object get(Object owner,String name) throws Exception {
        var field=MapRenderer.class.getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static void set(Object owner,String name,Object value) throws Exception {
        var field=MapRenderer.class.getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
    }
    private static TileData data(int id) {
        var bytes=ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(new byte[]{'E','C','T','1'}).putInt(0).putDouble(0).putDouble(0).putDouble(0);
        return TileData.parse(id,false,bytes.array(),0,0);
    }
    private static Object want(int id,int... parents) throws Exception {
        var type=Class.forName(MapRenderer.class.getName()+"$Want");
        var constructor=type.getDeclaredConstructor(int.class,boolean.class,int[].class);constructor.setAccessible(true);
        return constructor.newInstance(id,false,parents);
    }
    @SuppressWarnings("unchecked") static void run() throws Exception {
        var renderer=new MapRenderer();
        var upload=MapRenderer.class.getDeclaredMethod("uploadAndSwitch",net.minecraft.client.multiplayer.ClientLevel.class);upload.setAccessible(true);
        var loaded=(Map<Integer,MapTile>)get(renderer,"loaded");
        try {
            loaded.put(1,new MapTile(data(1),null));set(renderer,"wanted",List.of(want(1)));
            upload.invoke(renderer,new Object[]{null});
            set(renderer,"wanted",List.of(want(2)));
            upload.invoke(renderer,new Object[]{null});
            if(!get(renderer,"displayed").equals(List.of(1)))throw new AssertionError("Unloaded replacement blanked the map");
            loaded.put(2,new MapTile(data(2),null));
            set(renderer,"wanted",List.of(want(2,1),want(3,1)));
            upload.invoke(renderer,new Object[]{null});
            if(!get(renderer,"displayed").equals(List.of(1)))throw new AssertionError("Incomplete children lost their coarse cover");
            loaded.put(3,new MapTile(data(3),null));upload.invoke(renderer,new Object[]{null});
            var displayed=(List<Integer>)get(renderer,"displayed");
            if(displayed.size()!=2 || displayed.contains(1))throw new AssertionError("Parent overlaps completed children and causes depth flicker");
            set(renderer,"current",new EarthRegions.Region(0,44.4,26.1,120));
            renderer.preserveForLanding();renderer.finishLanding(new EarthRegions.Region(1,40.7,-74,30));
            if(get(renderer,"retained")==null || !loaded.isEmpty())throw new AssertionError("Geographic handoff discarded the old scenery");
            set(renderer,"wanted",List.of(want(4)));upload.invoke(renderer,new Object[]{null});
            if(get(renderer,"retained")==null)throw new AssertionError("Handoff cover disappeared before destination tiles loaded");
            var type=Class.forName(MapRenderer.class.getName()+"$Upload");
            var constructor=type.getDeclaredConstructor(int.class,TileData.class);constructor.setAccessible(true);
            ((Queue<Object>)get(renderer,"uploadQueue")).add(constructor.newInstance(-1,data(99)));
            upload.invoke(renderer,new Object[]{null});
            if(loaded.containsKey(99))throw new AssertionError("Old-origin tile uploaded into the new region");
            loaded.put(4,new MapTile(data(4),null));upload.invoke(renderer,new Object[]{null});
            if(get(renderer,"retained")!=null || !get(renderer,"displayed").equals(List.of(4)))throw new AssertionError("Complete destination did not release its temporary cover");
        } finally {renderer.clear();}
    }
}
