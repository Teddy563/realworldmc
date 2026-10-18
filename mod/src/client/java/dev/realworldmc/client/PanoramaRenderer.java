package dev.realworldmc.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.realworldmc.RealWorldMC;
import dev.realworldmc.ServiceClient;
import dev.realworldmc.PanoramaRoom;
import dev.realworldmc.PanoramaPayload;
import dev.realworldmc.PanoramaActionPayload;
import dev.realworldmc.StreetNavigation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

/** A fixed photo origin with approximate ground reprojection. Minecraft owns foreground gameplay. */
public final class PanoramaRenderer {
    public static final PanoramaRenderer INSTANCE=new PanoramaRenderer();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ResourceLocation TEXTURE=RealWorldMC.id("dynamic/streetview_sky");
    private VertexBuffer sphere, fadingSphere;
    private ResourceLocation currentTexture=TEXTURE, fadingTexture;
    private long fadeStarted;
    private net.minecraft.world.phys.Vec3 lastOffset=net.minecraft.world.phys.Vec3.ZERO,fadeOffset=net.minecraft.world.phys.Vec3.ZERO;
    private JsonObject metadata;
    private dev.realworldmc.EarthRegions.Region mapRegion;
    private String copyright="",report="",status="";
    private boolean session,loading,walking,walkingRequested,sourcesVisible;
    private int generation, ticket, pendingTicket;
    private NativeImage prepared;
    private JsonObject preparedMetadata;
    private boolean arrivalGuard;
    private boolean forwardWasDown, approaching, cancelSent, walkBlocked;
    private int walkingDestination = -1;
    private long walkRetryAt;
    private double anchorX,anchorY,anchorZ;
    private double groundY,eyeHeight=2.5;
    private long noticeUntil;
    private final KeyMapping[] movement=new KeyMapping[4];
    private KeyMapping reportKey,walkKey,sourcesKey;
    private final int[] offsets={0,180,-90,90};

    public void registerKeys(){
        String[] names={"forward","backward","left","right"};int[] keys={GLFW.GLFW_KEY_UP,GLFW.GLFW_KEY_DOWN,GLFW.GLFW_KEY_LEFT,GLFW.GLFW_KEY_RIGHT};
        for(int i=0;i<4;i++)movement[i]=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.realworldmc.photo."+names[i],InputConstants.Type.KEYSYM,keys[i],"key.categories.realworldmc"));
        reportKey=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.realworldmc.photo.report",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F8,"key.categories.realworldmc"));
        walkKey=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.realworldmc.photo.walk",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.realworldmc"));
        sourcesKey=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.realworldmc.photo.sources",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F7,"key.categories.realworldmc"));
    }
    public boolean active(){var level=Minecraft.getInstance().level;return session && level!=null && level.dimension().equals(PanoramaRoom.ROOM);}
    public boolean walking(){return walking;}
    public dev.realworldmc.EarthRegions.Region mapRegion(){return active()?mapRegion:null;}
    public int tilesPerPhoto(){return metadata!=null && metadata.has("rwmcTileCount")?metadata.get("rwmcTileCount").getAsInt():0;}
    /** Reuse the existing arrow bindings; duplicate registrations would steal photo navigation keys. */
    public KeyMapping flightSpeedKey(boolean faster) { return movement[faster ? 0 : 1]; }
    public double groundY(){return groundY;}
    public void clear(){
        if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.noPhysics=Minecraft.getInstance().player.isSpectator();
        arrivalGuard=false;mapRegion=null;generation++;session=false;loading=false;metadata=null;status="";report="";copyright="";forwardWasDown=false;
        walking=false;walkingRequested=false;sourcesVisible=false;noticeUntil=0;approaching=false;cancelSent=false;walkingDestination=-1;walkRetryAt=0;walkBlocked=false;
        pendingTicket=0;ticket=0;discardPrepared();
        if(sphere!=null){sphere.close();sphere=null;}
        finishFade();currentTexture=TEXTURE;lastOffset=net.minecraft.world.phys.Vec3.ZERO;
        Minecraft.getInstance().getTextureManager().release(TEXTURE);
        Minecraft.getInstance().getTextureManager().release(RealWorldMC.id("dynamic/streetview_next"));
        PhotoEditsRenderer.INSTANCE.clear();
    }
    private void discardPrepared(){if(prepared!=null){prepared.close();prepared=null;}preparedMetadata=null;}
    private void action(int id,String type,String pano){
        if(ClientPlayNetworking.canSend(PanoramaActionPayload.TYPE))ClientPlayNetworking.send(new PanoramaActionPayload(id,type,pano));
    }
    public void receive(PanoramaPayload payload){
        Minecraft mc=Minecraft.getInstance();
        switch(payload.phase()){
            case "clear" -> clear();
            case "anchor" -> {
                if (session && payload.ticket()==ticket) {
                    viewSettings(payload);
                    VertexBuffer next=buildSphere(metadata);if(sphere!=null)sphere.close();sphere=next;
                }
            }
            case "movement" -> {
                if (session && payload.ticket()==ticket) {
                    viewSettings(payload);walkingRequested=false;
                    mc.player.setNoGravity(true);mc.player.setDeltaMovement(0,0,0);
                    mc.player.setPos(anchorX,walking?groundY:anchorY,anchorZ);
                    notice(walking?"Nearby walk: 3×3 blocks; cross the edge to travel • F5: experimental third person":"Photo viewpoint restored • V: nearby walk");
                }
            }
            case "loading" -> {generation++;discardPrepared();pendingTicket=payload.ticket();loading=true;status=payload.metadata();}
            case "error" -> {if(payload.ticket()==pendingTicket || payload.ticket()==ticket){generation++;discardPrepared();loading=false;walkingRequested=false;approaching=false;cancelSent=false;walkingDestination=-1;walkRetryAt=net.minecraft.Util.getMillis()+2000;walkBlocked=true;notice(payload.metadata());}}
            case "prepare" -> {
                if(payload.ticket()!=pendingTicket)return;
                try{load(JsonParser.parseString(payload.metadata()).getAsJsonObject(),payload.ticket());}
                catch(Exception e){loading=false;action(payload.ticket(),"failed","");}
            }
            case "commit" -> {
                if(payload.ticket()!=pendingTicket || prepared==null || preparedMetadata==null)return;
                try{
                    viewSettings(payload);
                    VertexBuffer next=buildSphere(metadata);
                    finishFade();
                    ResourceLocation nextTexture=currentTexture.equals(TEXTURE)?RealWorldMC.id("dynamic/streetview_next"):TEXTURE;
                    mc.getTextureManager().release(nextTexture);
                    DynamicTexture texture=new DynamicTexture(prepared);prepared=null;texture.setFilter(true,false);mc.getTextureManager().register(nextTexture,texture);
                    if(sphere!=null && walking){fadingSphere=sphere;fadingTexture=currentTexture;fadeOffset=lastOffset;fadeStarted=net.minecraft.Util.getMillis();}
                    else {if(sphere!=null)sphere.close();mc.getTextureManager().release(currentTexture);}
                    sphere=next;currentTexture=nextTexture;preparedMetadata=null;
                    copyright=text(metadata,"copyright","Google");report=text(metadata,"reportProblemLink","");
                    arrivalGuard=session && walking;ticket=payload.ticket();session=true;loading=false;approaching=false;cancelSent=false;walkingDestination=-1;walkRetryAt=net.minecraft.Util.getMillis()+500;walkBlocked=false;forwardWasDown=mc.options.keyUp.isDown();
                    anchorX=payload.x();anchorY=payload.y();anchorZ=payload.z();
                    walkingRequested=false;notice("Street View ready · /earth back returns");
                    RealWorldMC.LOGGER.debug("Street View photograph committed at {}, {}, {}",anchorX,anchorY,anchorZ);
                }catch(Exception e){discardPrepared();loading=false;notice("Photo upload failed; /earth back returns.");RealWorldMC.LOGGER.error("Street View texture upload failed",e);}
            }
        }
    }
    private void viewSettings(PanoramaPayload payload){
        metadata=JsonParser.parseString(payload.metadata()).getAsJsonObject();
        anchorX=payload.x();anchorY=payload.y();anchorZ=payload.z();
        eyeHeight=number(metadata,"rwmcEyeHeight",2.5f);
        groundY=metadata.has("rwmcGroundY")?metadata.get("rwmcGroundY").getAsDouble():anchorY+1.62-eyeHeight;
        if(metadata.has("rwmcRegionIndex"))mapRegion=new dev.realworldmc.EarthRegions.Region(metadata.get("rwmcRegionIndex").getAsInt(),metadata.get("rwmcRegionLat").getAsDouble(),metadata.get("rwmcRegionLon").getAsDouble(),metadata.get("rwmcRegionHeight").getAsDouble());else mapRegion=null;
        walking=metadata.has("rwmcWalking") && metadata.get("rwmcWalking").getAsBoolean();
    }
    private void notice(String text){status=text;noticeUntil=net.minecraft.Util.getMillis()+6000;}
    private void requestWalking(boolean enabled){
        if(loading || walkingRequested)return;
        walkingRequested=true;action(ticket,"walk",enabled?"on":"off");
    }
    private static byte[] fetch(String path) throws Exception {
        var r=HTTP.send(HttpRequest.newBuilder(URI.create(ServiceClient.photoBase() + "/native/"+path)).timeout(Duration.ofSeconds(90)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        if(r.statusCode()!=200 || r.body().length>20_000_000)throw new IllegalStateException("Street View unavailable");return r.body();
    }
    private void load(JsonObject data,int request){
        loading=true;status="Loading high-resolution Street View…";int generationNow=++generation;
        Minecraft mc=Minecraft.getInstance();
        CompletableFuture.runAsync(()->{
            NativeImage image=null;
            try{
                String pano=URLEncoder.encode(data.get("panoId").getAsString(),StandardCharsets.UTF_8);
                var decoded=ImageIO.read(new ByteArrayInputStream(fetch("panorama?pano="+pano)));
                if(decoded==null || decoded.getWidth()>4096 || decoded.getHeight()>2048)throw new IllegalStateException("Invalid panorama size");
                image=new NativeImage(decoded.getWidth(),decoded.getHeight(),false);
                for(int y=0;y<decoded.getHeight();y++)for(int x=0;x<decoded.getWidth();x++){
                    int c=decoded.getRGB(x,y);image.setPixelRGBA(x,y,(c&0xff00ff00)|((c>>16)&255)|((c&255)<<16));
                }
                NativeImage ready=image;image=null;
                mc.execute(()->{
                    if(generationNow!=generation || request!=pendingTicket){ready.close();return;}
                    discardPrepared();prepared=ready;preparedMetadata=data;status="Photo ready; preparing destination terrain…";
                    action(request,"ready","");
                });
            }catch(Exception e){RealWorldMC.LOGGER.warn("Street View photo download/decode failed",e);if(image!=null)image.close();mc.execute(()->{
                if(generationNow==generation && request==pendingTicket){loading=false;status="Photo loading failed. Previous stop retained.";action(request,"failed","");}
            });}
        });
    }
    private static String text(JsonObject data,String key,String fallback){return data.has(key)?data.get(key).getAsString():fallback;}
    private static float number(JsonObject data,String key,float fallback){return data.has(key)?data.get(key).getAsFloat():fallback;}
    private static VertexBuffer buildSphere(JsonObject data){
        // Metadata heading points at the centre of the equirectangular image.
        Matrix3f orientation=new Matrix3f().rotateY((float)Math.toRadians(-number(data,"heading",0)))
            .rotateX((float)Math.toRadians(90-number(data,"tilt",90))).rotateZ((float)Math.toRadians(number(data,"roll",0)));
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
        int columns=128,rows=64;
        float height=number(data,"rwmcEyeHeight",2.5f);
        for(int y=0;y<rows;y++)for(int x=0;x<columns;x++){
            vertex(b,orientation,height,(float)x/columns,(float)y/rows);vertex(b,orientation,height,(float)(x+1)/columns,(float)y/rows);
            vertex(b,orientation,height,(float)(x+1)/columns,(float)(y+1)/rows);vertex(b,orientation,height,(float)x/columns,(float)(y+1)/rows);
        }
        VertexBuffer buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);buffer.bind();buffer.upload(b.buildOrThrow());VertexBuffer.unbind();return buffer;
    }
    static Vector3f photoPoint(Matrix3f orientation,float height,float u,float v){
        double yaw=(u-.5)*Math.PI*2,pitch=v*Math.PI;
        Vector3f p=orientation.transform(new Vector3f((float)(Math.sin(yaw)*Math.sin(pitch)),(float)Math.cos(pitch),(float)(-Math.cos(yaw)*Math.sin(pitch))));
        // A flat road is the only depth assumption. Distant objects remain on the photo shell.
        return p.mul(p.y<-.0001f?Math.min(100,height/-p.y):100);
    }
    private static void vertex(BufferBuilder b,Matrix3f orientation,float height,float u,float v){
        Vector3f p=photoPoint(orientation,height,u,v);
        b.addVertex(p.x,p.y,p.z).setUv(u,v);
    }
    private void finishFade(){if(fadingSphere!=null){fadingSphere.close();fadingSphere=null;}if(fadingTexture!=null){Minecraft.getInstance().getTextureManager().release(fadingTexture);fadingTexture=null;}}
    static net.minecraft.world.phys.Vec3 walkOffset(net.minecraft.world.phys.Vec3 delta,boolean walking){
        if(!walking)return net.minecraft.world.phys.Vec3.ZERO;
        // Limited parallax gives movement feedback without the large flat-floor stretch.
        double length=Math.hypot(delta.x,delta.z),scale=length>.001?Math.min(.2,.3/length):0;
        return new net.minecraft.world.phys.Vec3(delta.x*scale,0,delta.z*scale);
    }
    public void renderSky(WorldRenderContext ctx){
        if(!active() || sphere==null)return;
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        float[] color=RenderSystem.getShaderColor().clone();
        int srcRgb=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB),srcAlpha=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        try {
            RenderSystem.disableCull();RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderTexture(0,currentTexture);
            var offset=walkOffset(cameraAnchor().subtract(ctx.camera().getPosition()),walking);
            lastOffset=offset;
            float fade=Math.min(1,(net.minecraft.Util.getMillis()-fadeStarted)/250f);fade=fade*fade*(3-2*fade);
            if(fadingSphere!=null && fade<1){
                RenderSystem.setShaderTexture(0,fadingTexture);
                fadingSphere.bind();fadingSphere.drawWithShader(new Matrix4f(ctx.positionMatrix()).setTranslation(0,0,0).translate((float)fadeOffset.x,0,(float)fadeOffset.z),ctx.projectionMatrix(),RenderSystem.getShader());
                RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,fade);
            } else if(fadingSphere!=null)finishFade();
            RenderSystem.setShaderTexture(0,currentTexture);
            sphere.bind();sphere.drawWithShader(new Matrix4f(ctx.positionMatrix()).setTranslation(0,0,0).translate((float)offset.x,(float)offset.y,(float)offset.z),ctx.projectionMatrix(),RenderSystem.getShader());
        }finally{
            VertexBuffer.unbind();RenderSystem.depthMask(mask);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            com.mojang.blaze3d.platform.GlStateManager._blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
        }
    }
    private double[] headings(){
        if(metadata==null || !metadata.has("links"))return new double[0];
        JsonArray links=metadata.getAsJsonArray("links");double[] result=new double[links.size()];
        for(int i=0;i<result.length;i++)result[i]=links.get(i).getAsJsonObject().get("heading").getAsDouble();return result;
    }
    private int selection(double heading,double limit){return StreetNavigation.closest(headings(),heading,limit);}
    private void travel(int index){
        if(index<0 || loading || metadata==null)return;
        String next=metadata.getAsJsonArray("links").get(index).getAsJsonObject().get("panoId").getAsString();
        loading=true;status="Loading connected photo…";action(ticket,"next",next);
    }
    /** A held W never chains teleports. In nearby-walk mode W belongs to vanilla movement. */
    public boolean forward(boolean down,boolean sneaking){
        Minecraft mc=Minecraft.getInstance();boolean pressed=down && !forwardWasDown;forwardWasDown=down;
        if(!active() || walking || mc.screen!=null)return false;
        int selected=selection(mc.gameRenderer.getMainCamera().getYRot()+180,25);
        if(pressed && selected>=0 && !loading)travel(selected);
        return down;
    }
    private void walkingTravel(Minecraft mc) {
        if (mc.screen != null) return;
        double dx=mc.player.getX()-anchorX, dz=mc.player.getZ()-anchorZ;
        double distance=Math.max(Math.abs(dx),Math.abs(dz));
        if(arrivalGuard) { if(distance<.8 || distance>=StreetNavigation.WALK_TRANSITION)arrivalGuard=false;else return; }
        if (Math.max(Math.abs(dx),Math.abs(dz))<StreetNavigation.WALK_RETREAT) walkBlocked=false;
        int next=StreetNavigation.walkingLink(headings(),dx,dz,StreetNavigation.WALK_PREPARE);
        if (approaching && loading) {
            boolean withdrew=Math.max(Math.abs(dx),Math.abs(dz))<StreetNavigation.WALK_RETREAT;
            boolean changed=next>=0 && next!=walkingDestination;
            if (!cancelSent && pendingTicket!=ticket && (withdrew || changed)) { cancelSent=true; action(pendingTicket,"cancel",""); }
            return;
        }
        if (loading || walkBlocked || net.minecraft.Util.getMillis()<walkRetryAt || next<0) return;
        // Crossing the free-walk boundary explicitly selects one connected destination.
        // Its photo loads while the player covers the remaining half-metre to the transition.
        approaching=true; cancelSent=false; walkingDestination=next; loading=true; pendingTicket=ticket;
        status="Preparing the next photo in your walking direction…";
        action(ticket,"approach",metadata.getAsJsonArray("links").get(next).getAsJsonObject().get("panoId").getAsString());
    }
    private boolean validReport(){try{URI u=URI.create(report);String h=u.getHost();return "https".equals(u.getScheme()) && h!=null && (h.equals("google.com")||h.endsWith(".google.com")||h.endsWith(".googleapis.com"));}catch(Exception e){return false;}}
    public void tick(Minecraft mc){
        // Runs before entering the photo dimension too; no hidden first-load status.
        if (mc.player != null && (loading || net.minecraft.Util.getMillis() < noticeUntil))
            mc.gui.setOverlayMessage(dev.realworldmc.RWMessages.message(status), false);
        if(session && !active()){clear();return;}
        if(active() && mc.player!=null){
            mc.player.setNoGravity(true);mc.player.fallDistance=0;
            mc.player.getAbilities().flying=false;
            if(walking){
                walkingTravel(mc);
                double x=StreetNavigation.walkingCoordinate(mc.player.getX(),anchorX),z=StreetNavigation.walkingCoordinate(mc.player.getZ(),anchorZ);
                if(x!=mc.player.getX() || z!=mc.player.getZ()){
                    var velocity=mc.player.getDeltaMovement();mc.player.setPos(x,mc.player.getY(),z);mc.player.setDeltaMovement(0,velocity.y,0);
                }
            }else{
                mc.player.setPos(anchorX,anchorY,anchorZ);mc.player.setDeltaMovement(0,0,0);
            }
        }
        for(int i=0;i<4;i++)while(movement[i].consumeClick()){
            if(!active() || mc.screen!=null || loading)continue;
            int next=selection(mc.gameRenderer.getMainCamera().getYRot()+180+offsets[i],60);
            if(next>=0)travel(next);else notice("No connected photo in that direction");
        }
        while(walkKey.consumeClick())if(active() && mc.screen==null)requestWalking(!walking);
        while(sourcesKey.consumeClick())if(active() && mc.screen==null)sourcesVisible=!sourcesVisible;
        while(reportKey.consumeClick())if(active() && mc.screen==null && validReport())net.minecraft.Util.getPlatform().openUri(URI.create(report));
    }
    public net.minecraft.world.phys.Vec3 cameraAnchor(){
        return new net.minecraft.world.phys.Vec3(anchorX,groundY+eyeHeight,anchorZ);
    }
    private void arrows(GuiGraphics g){
        Minecraft mc=Minecraft.getInstance();double heading=mc.gameRenderer.getMainCamera().getYRot()+180;
        double[] directions=headings();
        double walkX=mc.player.getX()-anchorX,walkZ=mc.player.getZ()-anchorZ;
        int selected=walking && Math.max(Math.abs(walkX),Math.abs(walkZ))>.2?StreetNavigation.walkingLink(directions,walkX,walkZ,0):selection(heading,25),cx=g.guiWidth()/2,cy=g.guiHeight()-77;
        // Deliberately draw square pixels without texture filtering. The flattened ring resembles floor controls.
        String[] glyph={"0001000","0011100","0111110","1101011","1001001","0001000","0001000"};
        for(int i=0;i<directions.length;i++){
            double angle=Math.toRadians(directions[i]-heading),sin=Math.sin(angle),cos=Math.cos(angle);
            int ax=cx+(int)Math.round(sin*62),ay=cy-(int)Math.round(cos*25);
            int color=loading?0x55ffffff:i==selected?0xccffdd35:0x88ffffff;
            for(int y=0;y<glyph.length;y++)for(int x=0;x<glyph[y].length();x++)if(glyph[y].charAt(x)=='1'){
                double dx=(x-3)*3,dy=(y-3)*3;int px=ax+(int)Math.round(dx*cos-dy*sin),py=ay+(int)Math.round(dx*sin+dy*cos);
                g.fill(px+1,py+1,px+4,py+4,0x66000000);g.fill(px,py,px+3,py+3,color);
            }
        }
    }
    public void hud(GuiGraphics g){
        if(!active())return;Minecraft mc=Minecraft.getInstance();int w=g.guiWidth(),h=g.guiHeight();
        String credits="Google, OpenStreetMap, RealWorldMC";
        int color=dev.realworldmc.CreditFade.HUD.color(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
        g.fill(2,h-29,mc.font.width(credits)+8,h-14,((color>>>24)*136/255)<<24);
        g.drawString(mc.font,credits,5,h-26,color,true);
        if(dev.realworldmc.EarthConfig.get().actionBar)g.drawString(mc.font,"Sources [F7]",5,h-12,(color&0xff000000)|0xdddddd,true);
        String label="Report image [F8]";g.drawString(mc.font,label,Math.max(4,w-mc.font.width(label)-4),h-12,0xffffffff,true);
        if(sourcesVisible){
            String details="Street View photo: "+copyright+"\nGround/terrain: Google 3D data where available; OpenStreetMap contributors (water data).\nRealWorldMC: Minecraft integration. Nearby walk uses limited parallax and fades between stops; the 5×5 boundary is only for waiting. Photo walls have no measured depth.\nUsage is a local monthly estimate, not Google billing; earlier and other-app usage is excluded. /earth usage off hides counts.\nF7: close • F8: report photo";
            var lines=mc.font.split(Component.literal(details),Math.min(340,w-20));
            int top=h-38-lines.size()*10;
            g.fill(2,top-5,Math.min(350,w-8),h-33,0xd0202020);
            for(var line:lines){g.drawString(mc.font,line,7,top,0xffeeeeee,true);top+=10;}
        }
        if(dev.realworldmc.EarthConfig.get().actionBar && !mc.options.hideGui && mc.screen==null)arrows(g);
        if(!sourcesVisible)ActionBar.render(g,h-40,java.util.List.of(
            walking?"WASD: Walk 3×3 · Arrows: Travel":ActionBar.key(mc.options.keyUp)+" / Arrows: Travel",
            ActionBar.key(walkKey)+(walking?": Center":": Walk")+" · "+ActionBar.key(mc.options.keyTogglePerspective)+": Camera",
            dev.realworldmc.EarthConfig.get().streetViewShortcut?"Shift + "+ActionBar.key(mc.options.keySwapOffhand)+": Return":"/earth back: Return"));
    }
}
