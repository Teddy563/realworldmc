package dev.realworldmc.client;

import dev.realworldmc.EarthConfig;
import dev.realworldmc.client.map.OsmTileCache;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;

final class MapClientChecks {
    private boolean ran;
    void register() {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            EarthConfig.get().setupComplete = true;
            mc.options.pauseOnLostFocus = false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (ran || mc.getOverlay()!=null) return;
            ran=true;
            try {
                if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
                    Class.forName("xaero.common.minimap.render.MinimapFBORenderer");
                    if(FabricLoader.getInstance().isModLoaded("xaeroworldmap")) Class.forName("xaero.common.mods.SupportXaeroWorldmap");
                }
                if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) Class.forName("xaero.map.gui.GuiMap");
                if(OsmTileCache.active(net.minecraft.world.level.Level.OVERWORLD)) throw new AssertionError("No overlay outside a world");
                OsmTileCache.clear();
                Files.writeString(Path.of("map-check-result.txt"),"PASS: optional map classes transform and client initializes; no overlay outside a world");
            } catch(Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(Path.of("map-check-result.txt"),"FAIL: "+failure); } catch(Exception ignored) { }
            } finally { mc.stop(); }
        });
    }
}
