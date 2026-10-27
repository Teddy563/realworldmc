package dev.realworldmc.client;

import dev.realworldmc.RWTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;

/** Lift around the existing avatar, conceal the dimension switch, then settle over Earth. */
public final class StreetExitTransition {
    private static CameraType previous;
    private static long started,arrived,reveal;
    private static boolean active, entering;
    private StreetExitTransition(){}
    public static boolean active(){return active;}
    public static boolean entering(){return active && entering;}
    public static boolean concealed(){return active && net.minecraft.Util.getMillis()-started>=700;}
    public static void beginEntry(){begin();entering=true;}
    public static void begin(){
        if(active)return;
        var mc=Minecraft.getInstance();previous=mc.options.getCameraType();mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        active=true;entering=false;started=net.minecraft.Util.getMillis();arrived=0;reveal=0;
    }
    public static void arrive(){if(active)arrived=net.minecraft.Util.getMillis();}
    public static void clear(){
        if(active && previous!=null)Minecraft.getInstance().options.setCameraType(previous);
        active=false;entering=false;previous=null;started=0;arrived=0;reveal=0;
    }
    private static double ease(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static double backDistance(){double lift=lift();return reveal>0 && previous==CameraType.FIRST_PERSON?4.5*lift:2.5+2*lift;}
    public static double lift(){return reveal>0?1-ease((net.minecraft.Util.getMillis()-reveal)/700.0):ease((net.minecraft.Util.getMillis()-started)/700.0);}
    public static void tick(Minecraft mc){
        if(!active)return;
        if(mc.player==null || mc.level==null){clear();return;}
        long now=net.minecraft.Util.getMillis();
        if(arrived==0){mc.player.setDeltaMovement(0,0,0);if(now-started>75000)clear();return;}
        if(now-started>80000){clear();return;}
        if(reveal==0 && (entering ? PanoramaRenderer.INSTANCE.active()
            : mc.level.dimension().equals(Level.OVERWORLD) && mc.level.hasChunkAt(mc.player.blockPosition())
            && (!MapRenderer.INSTANCE.enabled() || MapRenderer.INSTANCE.readyAt(mc.player.getX(),mc.player.getZ()) || now-arrived>4000)))reveal=now;
        if(reveal>0 && now-reveal>=700)clear();
    }
    public static void hud(GuiGraphics g){
        if(!active)return;
        long now=net.minecraft.Util.getMillis();double alpha=reveal>0?1-ease((now-reveal)/700.0):ease((now-started-350)/350.0);
        int a=(int)(255*alpha);if(a>0)g.fill(0,0,g.guiWidth(),g.guiHeight(),(a<<24)|0x151715);
        if(alpha>.95 && reveal==0){var mc=Minecraft.getInstance();String label=entering?"Opening Street View…":"Opening Earth view…";g.drawCenteredString(mc.font,label,g.guiWidth()/2,g.guiHeight()/2,RWTheme.GREEN);}
    }
}
