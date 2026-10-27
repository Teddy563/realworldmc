package dev.realworldmc.client;

import dev.realworldmc.RealWorldMC;
import dev.realworldmc.PanoramaRoom;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import java.lang.reflect.Method;

/** Temporary DH API override: do not change the user's saved DH or shader settings. */
public final class DistantHorizonsCompat {
    private static boolean unavailable, owned, warned;
    private static Object value, previous;
    private static Method getApiValue, setValue, clearValue;
    public static void update(Minecraft mc) {
        boolean hide = mc.level != null && (PanoramaRoom.isRoom(mc.level)
            || (mc.level.dimension().equals(Level.OVERWORLD) && MapRenderer.INSTANCE.enabled()));
        if(unavailable || (!hide && !owned)) return;
        try {
            if(value == null) {
                if(!FabricLoader.getInstance().isModLoaded("distanthorizons")){unavailable=true;return;}
                Object configs=Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed").getField("configs").get(null);
                if(configs==null)return; // DH initializes its API after Minecraft starts.
                Object graphics=Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig").getMethod("graphics").invoke(configs);
                value=Class.forName("com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig").getMethod("renderingEnabled").invoke(graphics);
                Class<?> type=Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue");
                getApiValue=type.getMethod("getApiValue");setValue=type.getMethod("setValue",Object.class);clearValue=type.getMethod("clearValue");
            }
            if(hide && !owned) {
                previous=getApiValue.invoke(value);
                owned=Boolean.TRUE.equals(setValue.invoke(value,Boolean.FALSE));
                if(!owned && !warned){warned=true;RealWorldMC.LOGGER.warn("DH refused the temporary LOD rendering override.");}
            } else if(!hide && owned) release();
        } catch(ReflectiveOperationException e) {
            if(!warned){warned=true;RealWorldMC.LOGGER.warn("DH compatibility API unavailable; disable DH rendering manually in Earth worlds.");}
            unavailable=true;
        }
    }
    public static void release() {
        if(!owned)return;
        try {
            // Do not overwrite an override changed by another mod while we were active.
            if(Boolean.FALSE.equals(getApiValue.invoke(value))) {
                if(previous==null)clearValue.invoke(value);else setValue.invoke(value,previous);
            }
        }catch(ReflectiveOperationException e){RealWorldMC.LOGGER.warn("Could not restore the temporary DH override.");}
        owned=false;previous=null;
    }
}
