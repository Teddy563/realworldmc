package dev.realworldmc.client;

import dev.realworldmc.EarthConfig;
import dev.realworldmc.RWMessages;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.List;

public final class ActionBar {
    private static Component status=Component.empty();
    private static long until;
    private ActionBar() {}
    public static boolean hidden(Component message) {
        return !EarthConfig.get().actionBar && message.getString().startsWith("RW | ");
    }
    public static void show(Component message) { status=message; until=net.minecraft.Util.getMillis()+4000; }
    public static boolean capture(Component message) {
        if(!message.getString().startsWith("RW | "))return false;
        if(!hidden(message))show(message);
        return true;
    }
    public static void clear() { status=Component.empty();until=0; }
    public static String key(net.minecraft.client.KeyMapping key) {return key.getTranslatedKeyMessage().getString();}
    /** Bottom is the last text baseline immediately above the map credits. */
    public static void render(GuiGraphics graphics, int bottom, List<String> controls) {
        var mc=Minecraft.getInstance();
        if(!EarthConfig.get().actionBar || mc.options.hideGui || mc.screen!=null)return;
        var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        int width=Math.min(290,graphics.guiWidth()-12);
        if(until>net.minecraft.Util.getMillis())lines.addAll(mc.font.split(status,width));
        for(String usage:ApiUsageHud.lines())lines.addAll(mc.font.split(RWMessages.message(usage),width));
        for(String control:controls)lines.addAll(mc.font.split(RWMessages.message(control),width));
        int y=bottom-(lines.size()-1)*11;
        for(var line:lines){graphics.drawString(mc.font,line,5,y,0xffffffff,true);y+=11;}
    }
    public static List<String> flightControls() {
        var mc=Minecraft.getInstance();var controls=new ArrayList<String>();
        if(CreativeFlight.enabled()) {
            controls.add(CreativeFlight.status());
            controls.add(key(mc.options.keyUp)+" / "+key(mc.options.keyDown)+": forward / back · "+key(mc.options.keyLeft)+" / "+key(mc.options.keyRight)+": strafe");
            controls.add(key(PanoramaRenderer.INSTANCE.flightSpeedKey(true))+" / "+key(PanoramaRenderer.INSTANCE.flightSpeedKey(false))+": speed · "+key(mc.options.keyJump)+" / "+key(mc.options.keyShift)+": up / down");
            controls.add("/earth fly: off · Rockets: boost");
        }
        if(SpaceFlight.enabled()) {
            controls.add(String.format(java.util.Locale.ROOT,"%s orbit · %.1f km",SpaceFlight.body().title,SpaceFlight.altitude()/1000));
            if(!CreativeFlight.enabled()) {
                controls.add(key(mc.options.keyUp)+" / "+key(mc.options.keyDown)+": cruise · "+key(mc.options.keyLeft)+" / "+key(mc.options.keyRight)+": strafe");
                controls.add(key(mc.options.keyJump)+" / "+key(mc.options.keyShift)+": up / down");
            }
            controls.add("/earth space return: back");
            controls.add("/rw moon · /rw mars · /rw earth");
        }
        if(EarthConfig.get().streetViewShortcut && mc.player!=null && mc.player.onGround())controls.add(StreetShortcut.hint());
        return controls;
    }
    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String[] words = command.strip().split("\\s+");
            if(words.length>=2 && words.length<=3 && (words[0].equals("earth") || words[0].equals("rw")) && words[1].equals("streetkey")) {
                if(words.length==3 && !java.util.Set.of("on","off").contains(words[2]))return true;
                var cfg=EarthConfig.get();cfg.streetViewShortcut=words.length==2?!cfg.streetViewShortcut:words[2].equals("on");cfg.save();
                var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(RWMessages.message("Street View shortcut "+(cfg.streetViewShortcut?"on":"off")+". /earth streetkey on|off"),false);return false;
            }
            if (words.length < 2 || words.length > 3 || !(words[0].equals("earth") || words[0].equals("rw")) || !words[1].equals("hud")) return true;
            if (words.length == 3 && !java.util.Set.of("on", "off").contains(words[2])) return true;
            var cfg = EarthConfig.get();
            cfg.actionBar = words.length == 2 ? !cfg.actionBar : words[2].equals("on");
            cfg.save();
            clear();
            var mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(RWMessages.message("Corner information " + (cfg.actionBar ? "on." : "off. Use /earth hud on to restore it.")), false);
            return false;
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
    }
}
