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
        float scale=.8f;
        int width=Math.min(230,(int)((graphics.guiWidth()-12)/scale));
        var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        if(until>net.minecraft.Util.getMillis()) {
            String text=status.getString().replaceFirst("^RW \\| ","");
            if(text.startsWith("Street View ready"))text="Ready";
            var wrapped=mc.font.split(Component.literal(text),width-12);
            lines.addAll(wrapped.subList(0,Math.min(2,wrapped.size())));
        }
        int statusEnd=lines.size();
        for(String control:controls)lines.addAll(mc.font.split(Component.literal(control),width-12));
        int controlsEnd=lines.size();
        for(String usage:ApiUsageHud.lines())lines.addAll(mc.font.split(Component.literal(usage),width-12));
        int height=16+lines.size()*11;
        graphics.pose().pushPose();
        graphics.pose().translate(5,Math.max(5,bottom-height*scale),0);
        graphics.pose().scale(scale,scale,1);
        graphics.fill(0,0,width,height,0x99101e17);
        graphics.fill(0,0,width,14,0xcc173524);
        graphics.fill(0,0,2,height,0xff66dd99);
        graphics.drawString(mc.font,PanoramaRenderer.INSTANCE.active()?"Street View":"RealWorldMC",7,3,0xffb5f5cb,false);
        int y=17;
        for(int i=0;i<lines.size();i++){graphics.drawString(mc.font,lines.get(i),7,y,i<statusEnd?0xffa3dfb6:i<controlsEnd?0xffeeeeee:0xffa3dfb6,false);y+=11;}
        graphics.pose().popPose();
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
            if(words.length>=2 && words.length<=3 && (words[0].equals("earth") || words[0].equals("rw"))) {
                var cfg=EarthConfig.get();var mc=Minecraft.getInstance();
                if(words[1].equals("controls") && (words.length==2 || java.util.Set.of("on","off").contains(words[2]))) {
                    cfg.controlsHud=words.length==2?!cfg.controlsHud:words[2].equals("on");cfg.save();return false;
                }
                if(words[1].equals("walk") && (words.length==2 || java.util.Set.of("on","off").contains(words[2]))) {
                    if(PanoramaRenderer.INSTANCE.active())PanoramaRenderer.INSTANCE.requestWalking(words.length==2?!PanoramaRenderer.INSTANCE.walking():words[2].equals("on"));return false;
                }
                if(words[1].equals("camera") && words.length==3 && java.util.Set.of("first","back","front").contains(words[2])) {
                    mc.options.setCameraType(words[2].equals("first")?net.minecraft.client.CameraType.FIRST_PERSON:words[2].equals("back")?net.minecraft.client.CameraType.THIRD_PERSON_BACK:net.minecraft.client.CameraType.THIRD_PERSON_FRONT);return false;
                }
            }
            if(words.length>=2 && words.length<=3 && (words[0].equals("earth") || words[0].equals("rw")) && words[1].equals("usage")) {
                if(words.length==3 && !java.util.Set.of("on","off").contains(words[2]))return true;
                var cfg=EarthConfig.get();cfg.usageHud=words.length==2?!cfg.usageHud:words[2].equals("on");cfg.save();
                var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(RWMessages.message("Usage panel "+(cfg.usageHud?"on":"off")+"."),false);return false;
            }
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
