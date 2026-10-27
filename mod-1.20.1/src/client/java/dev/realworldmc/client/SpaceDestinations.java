package dev.realworldmc.client;

import dev.realworldmc.EarthConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.EnumMap;

/** Navigation signs, not astronomical positions. Hold forward while aiming to confirm a transfer. */
public final class SpaceDestinations {
    private static final EnumMap<CelestialBody,Vector4f> markers=new EnumMap<>(CelestialBody.class);
    private static CelestialBody selected;
    private static int ticks;
    private static boolean releaseRequired;
    private SpaceDestinations() {}
    public static Vector3f direction(CelestialBody body) {
        return switch(body) {
            case EARTH -> new Vector3f(0,.4f,-1).normalize();
            case MOON -> new Vector3f(-1,.4f,.4f).normalize();
            case MARS -> new Vector3f(1,.4f,.4f).normalize();
        };
    }
    public static void clear() { selected=null;ticks=0;releaseRequired=false;markers.clear(); }
    private static boolean visible() {
        return SpaceFlight.anchored() && SpaceFlight.altitude()>=100_000 && EarthConfig.get().actionBar;
    }
    public static void tick(Minecraft mc) {
        if(!SpaceFlight.enabled()) {clear();return;}
        if(!mc.options.keyUp.isDown())releaseRequired=false;
        if(!visible() || mc.screen!=null || mc.options.hideGui || mc.isPaused() || releaseRequired) {selected=null;ticks=0;return;}
        CelestialBody target=null;
        for(var body:CelestialBody.values())if(body!=SpaceFlight.body()
            && mc.gameRenderer.getMainCamera().getLookVector().dot(direction(body))>.996f)target=body;
        if(target!=selected || !mc.options.keyUp.isDown())ticks=0;
        selected=target;
        if(target!=null && mc.options.keyUp.isDown() && ++ticks>=50) {
            SpaceFlight.visit(target);releaseRequired=true;selected=null;ticks=0;
        }
    }
    public static void project(WorldRenderContext ctx) {
        markers.clear();
        if(!visible())return;
        var view=new org.joml.Matrix4f(ctx.matrixStack().last().pose()).setTranslation(0,0,0);
        for(var body:CelestialBody.values())if(body!=SpaceFlight.body()) {
            var d=direction(body).mul(100);
            var clip=new Vector4f(d.x,d.y,d.z,1).mul(view).mul(ctx.projectionMatrix());
            if(clip.w>0 && Math.abs(clip.x/clip.w)<.88 && Math.abs(clip.y/clip.w)<.8)markers.put(body,clip);
        }
    }
    public static void hud(GuiGraphics g) {
        var mc=Minecraft.getInstance();
        if(!visible() || mc.options.hideGui || mc.screen!=null)return;
        for(var entry:markers.entrySet()) {
            var body=entry.getKey();var p=entry.getValue();
            int x=(int)((p.x/p.w*.5+.5)*g.guiWidth()),y=(int)((.5-p.y/p.w*.5)*g.guiHeight());
            String label=body.title+" · "+(body==selected?"Hold "+ActionBar.key(mc.options.keyUp):"Travel");
            int color=body==selected?0xffffee77:0xffeeeeee;
            g.drawCenteredString(mc.font,"◇",x,y-13,color);
            g.drawCenteredString(mc.font,label,x,y,color);
            if(body==selected && ticks>0) {
                g.fill(x-25,y+12,x+25,y+14,0x80555555);
                g.fill(x-25,y+12,x-25+ticks,y+14,0xff50bf88);
            }
        }
    }
}
