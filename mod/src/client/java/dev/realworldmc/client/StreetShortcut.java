package dev.realworldmc.client;

import dev.realworldmc.EarthConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

public final class StreetShortcut {
    private static long next;
    private StreetShortcut() { }
    public static boolean press(int key,int scan,int action) {
        if(action==GLFW.GLFW_RELEASE)return false; // Always let vanilla clear a previously held key.
        var mc=Minecraft.getInstance();var player=mc.player;
        if(!EarthConfig.get().streetViewShortcut || mc.screen!=null || player==null || mc.getConnection()==null
            || !mc.options.keyShift.isDown() || !mc.options.keySwapOffhand.matches(key,scan))return false;
        boolean photo=PanoramaRenderer.INSTANCE.active();
        if(!photo && (!player.onGround() || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()
            || !player.level().dimension().equals(Level.OVERWORLD) || !MapRenderer.INSTANCE.enabled()))return false;
        // Consume repeats so a held key never causes repeated API requests or offhand swaps.
        if(action==GLFW.GLFW_PRESS && System.currentTimeMillis()>=next) {
            next=System.currentTimeMillis()+2500;
            mc.getConnection().sendCommand(photo?"earth back":"earth streetview");
        }
        return true;
    }
    public static String hint(){var mc=Minecraft.getInstance();return ActionBar.key(mc.options.keyShift)+" + "+ActionBar.key(mc.options.keySwapOffhand)+": Street View (empty hands)";}
}
