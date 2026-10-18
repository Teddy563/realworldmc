package dev.realworldmc.client;

import dev.realworldmc.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Full client/server handshake against a local synthetic photograph, never a provider key. */
final class StreetWalkingChecks {
    private com.sun.net.httpserver.HttpServer fixture;
    private final AtomicInteger photographs = new AtomicInteger();
    private int stage, ticks, total;
    private boolean opened, mapChecked;
    private volatile net.minecraft.core.BlockPos damagePosition;
    private double anchorX, anchorZ;
    private volatile Throwable serverFailure;
    void register() {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            mc.options.pauseOnLostFocus=false; mc.options.renderDistance().set(2); mc.options.simulationDistance().set(2);
            EarthConfig.get().setupComplete=true; EarthConfig.get().autoSpaceFlight=false; EarthConfig.get().osmMap=false;
            try { startFixture(); } catch(Exception e) { fail(mc,e); }
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void startFixture() throws Exception {
        fixture=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var image=new java.awt.image.BufferedImage(64,32,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<32;y++)for(int x=0;x<64;x++)image.setRGB(x,y,y<16?0x80b0ff:0x608040);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        fixture.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath(),query=exchange.getRequestURI().getQuery();
            byte[] body;int status=200;
            if(path.equals("/native/panorama")) { photographs.incrementAndGet();body=bytes.toByteArray(); }
            else if(path.equals("/native/metadata")) {
                boolean east=query!=null && query.contains("pano=B");
                body=("{\"panoId\":\""+(east?"B":"A")+"\",\"lat\":0,\"lng\":"+(east?"0.0001":"0")+",\"heading\":0,\"tilt\":90,\"roll\":0,\"copyright\":\"Synthetic test photograph\",\"links\":[{\"panoId\":\""+(east?"A":"B")+"\",\"heading\":"+(east?270:90)+"}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else if(path.equals("/usage")) body="{\"available\":true,\"configured\":true,\"directGoogle\":true,\"requests\":24,\"tiles\":16,\"roots\":1,\"freeStreetTiles\":100000,\"free3dRoots\":1000}".getBytes();
            else if(path.equals("/ground")) body="{\"height\":0}".getBytes();
            else if(path.equals("/render/select")) body="{\"tiles\":[],\"attribution\":[]}".getBytes();
            else {status=404;body="{\"code\":\"NO_TERRAIN_COVERAGE\"}".getBytes();}
            exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        fixture.start();
        EarthConfig.get().serviceUrl="http://127.0.0.1:"+fixture.getAddress().getPort();
        var field=ServiceClient.class.getDeclaredField("RUNTIME");field.setAccessible(true);Object runtime=field.get(null);
        var photo=ServiceRuntime.class.getDeclaredField("photoPort");photo.setAccessible(true);photo.setInt(runtime,fixture.getAddress().getPort());
        var error=ServiceRuntime.class.getDeclaredField("error");error.setAccessible(true);error.set(runtime,"");
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    private void server(Minecraft mc,java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        server.execute(()->{try{action.accept(server.getPlayerList().getPlayer(id));}catch(Throwable e){serverFailure=e;}});
    }
    private void move(Minecraft mc,double dx,double dz) {
        server(mc,p->p.teleportTo(anchorX+dx,64,anchorZ+dz));ticks=0;
    }
    private void tick(Minecraft mc) {
        try {
            require(++total<2400,"Walking check timed out at stage "+stage);
            if(serverFailure!=null)throw new AssertionError("Server fixture failed",serverFailure);
            if(!opened && mc.getOverlay()==null) {
                opened=true;mc.createWorldOpenFlows().openWorld("walking-check",()->fail(mc,new AssertionError("Missing test world")));
            }
            if(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen || mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
                for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b) {
                    String label=b.getMessage().getString();
                    boolean skip=b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text && text.getKey().equals("selectWorld.backupJoinSkipButton");
                    if(skip || label.equals("Proceed") || label.equals("Yes")){b.onPress();return;}
                }
            }
            if(mc.player==null || mc.getSingleplayerServer()==null || mc.getOverlay()!=null)return;
            if(stage==-1) {
                if(damagePosition==null || !MapDamage.tracked(damagePosition))return;
                var pos=damagePosition;
                var bounds=new net.minecraft.world.phys.AABB(pos).inflate(1);
                require(MapDamage.cuts(bounds,mc.level,0,0,0).size()==1,"Confirmed server damage reaches the actual client cut list");
                server(mc,p->p.serverLevel().setBlockAndUpdate(pos,EarthBlocks.MAP_BRICK.defaultBlockState()));
                stage=-2;return;
            }
            if(stage==-2) {
                if(!mc.level.getBlockState(damagePosition).is(EarthBlocks.MAP_BRICK))return;
                require(MapDamage.cuts(new net.minecraft.world.phys.AABB(damagePosition).inflate(1),mc.level,0,0,0).isEmpty(),"Restored map blocks remove their mesh cuts");
                mapChecked=true;stage=0;
            }
            if(stage==0 && !mapChecked) {
                server(mc,p->{
                    var pos=p.blockPosition().above(2);damagePosition=pos;
                    p.serverLevel().setBlockAndUpdate(pos,EarthBlocks.MAP_BRICK.defaultBlockState());
                    p.serverLevel().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    MapEdits.snapshot(p);
                });
                stage=-1;return;
            }
            if(stage==0) {
                mc.setScreen(null);server(mc,p->{p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);PanoramaRoom.enter(p.createCommandSourceStack(),0,0);});stage=1;ticks=0;return;
            }
            if(stage==1) {
                if(!PanoramaRenderer.INSTANCE.active())return;
                require(PanoramaRenderer.INSTANCE.walking(),"Street View starts in walking mode");
                var region=PanoramaRenderer.INSTANCE.mapRegion();require(region!=null,"Street View sends its own geographic map anchor");
                var projected=dev.realworldmc.client.map.MapProjection.regionAt(mc.player.getX(),mc.player.getZ());
                require(region.equals(projected),"Street View uses photo places, not Overworld places");
                var coords=dev.realworldmc.client.map.MapProjection.latLon(projected,PanoramaRenderer.INSTANCE.cameraAnchor().x,PanoramaRenderer.INSTANCE.cameraAnchor().z);
                require(Math.abs(coords[0])<.00001 && Math.abs(coords[1])<.00001,"Map pin matches the Street View coordinate");
                EarthConfig.get().osmMap=true;
                require(dev.realworldmc.client.map.OsmTileCache.active(PanoramaRoom.ROOM),"Street View map dimension enabled");
                require(!dev.realworldmc.client.map.OsmTileCache.active(net.minecraft.world.level.Level.OVERWORLD),"Other Xaero dimensions remain isolated");
                EarthConfig.get().osmMap=false;
                anchorX=PanoramaRenderer.INSTANCE.cameraAnchor().x;anchorZ=PanoramaRenderer.INSTANCE.cameraAnchor().z;
                require(photographs.get()==1,"One initial photograph");move(mc,1.49,0);stage=2;return;
            }
            if(stage==10 && Math.abs(PanoramaRenderer.INSTANCE.cameraAnchor().x-anchorX)>5) {
                var fade=PanoramaRenderer.class.getDeclaredField("fadingSphere");fade.setAccessible(true);
                require(fade.get(PanoramaRenderer.INSTANCE)!=null,"Fixed-center travel crossfades the previous photo");
                require(Math.abs(mc.player.getY()-PanoramaRenderer.INSTANCE.groundY())<.01,"Fixed-center avatar remains grounded");
                stage=11;ticks=0;
            }
            if(++ticks<30)return;
            if(stage==2) {
                require(Math.abs(mc.player.getX()-anchorX-1.49)<.1,"Free movement is not pinned");
                require(photographs.get()==1,"No requests before the early preparation threshold");
                server(mc,p->{
                    var start=p.position();var wall=p.blockPosition().east();
                    p.serverLevel().setBlockAndUpdate(wall,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    p.serverLevel().setBlockAndUpdate(wall.above(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    p.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(1,1,0));
                    require(Math.abs(p.getX()-start.x-1)<.01 && Math.abs(p.getY()-start.y)<.01,"Walking crosses blocks without vertical drift");
                    p.setPos(start);
                });move(mc,1.6,0);stage=3;return;
            }
            if(stage==3) {
                if(photographs.get()<2)return;
                try(var screenshot=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){screenshot.writeToFile(Path.of("street-hud-check.png"));}
                require(Math.abs(PanoramaRenderer.INSTANCE.cameraAnchor().x-anchorX)<.1,"Prepared destination does not move the photograph early");
                move(mc,1.9,0);stage=4;return;
            }
            if(stage==4) {
                if(Math.abs(PanoramaRenderer.INSTANCE.cameraAnchor().x-anchorX)<5)return;
                require(photographs.get()==2,"Prepared photograph reused at transition");
                require(Math.abs(mc.player.getX()-(PanoramaRenderer.INSTANCE.cameraAnchor().x-1))<.15,"Walking arrival is one metre behind the next center");
                anchorX=PanoramaRenderer.INSTANCE.cameraAnchor().x;anchorZ=PanoramaRenderer.INSTANCE.cameraAnchor().z;
                move(mc,0,2.5);stage=5;return;
            }
            if(stage==5) {
                require(photographs.get()==2,"Missing south link causes no photo request");
                require(Math.abs(PanoramaRenderer.INSTANCE.cameraAnchor().x-anchorX)<.1,"Missing link keeps the current stop");
                move(mc,-1.6,0);stage=6;return;
            }
            if(stage==6) {
                if(photographs.get()<3)return;
                move(mc,0,0);stage=7;return;
            }
            if(stage==7) {
                require(Math.abs(PanoramaRenderer.INSTANCE.cameraAnchor().x-anchorX)<.1,"Retreat cancels a prepared destination");
                require(photographs.get()==3,"Cancelled destination does not start a retry loop");
                require(ApiUsageHud.lines().stream().anyMatch(line->line.contains("100,000")),"Local usage HUD displays the monthly tile allowance");
                EarthConfig.get().usageHud=false;require(ApiUsageHud.lines().isEmpty(),"Usage can be hidden while controls remain enabled");require(EarthConfig.get().actionBar,"Usage toggle leaves the HUD enabled");EarthConfig.get().usageHud=true;
                PanoramaRenderer.INSTANCE.requestWalking(false);stage=9;ticks=0;return;
            }
            if(stage==9) {
                if(PanoramaRenderer.INSTANCE.walking())return;
                var ticket=PanoramaRenderer.class.getDeclaredField("ticket");ticket.setAccessible(true);
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new PanoramaActionPayload(ticket.getInt(PanoramaRenderer.INSTANCE),"next","A"));
                stage=10;ticks=0;return;
            }
            if(stage==11) {
                mc.options.keyShift.setDown(true);EarthConfig.get().streetViewShortcut=false;
                require(!StreetShortcut.press(org.lwjgl.glfw.GLFW.GLFW_KEY_F,0,org.lwjgl.glfw.GLFW.GLFW_PRESS),"Disabled shortcut does not consume offhand input");
                EarthConfig.get().streetViewShortcut=true;
                require(StreetShortcut.press(org.lwjgl.glfw.GLFW.GLFW_KEY_F,0,org.lwjgl.glfw.GLFW.GLFW_PRESS),"Shortcut returns from Street View");
                mc.options.keyShift.setDown(false);stage=8;ticks=0;return;
            }
            if(stage==8) {
                require(!PanoramaRenderer.INSTANCE.active(),"Back clears the Street View session");
                require(!mc.player.noPhysics && !mc.player.isNoGravity(),"Return restores normal physics");
                require(mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD),"Back restores original dimension");
                Files.writeString(Path.of("walking-check-result.txt"),"PASS: server/client map damage sync and restoration, default free walking, early preparation, 3x3 transition and 5x5 waiting boundary, single prepared photo reuse, missing-link fallback, retreat cancellation and return cleanup");
                fixture.stop(0);mc.stop();
            }
        }catch(Throwable failure){fail(mc,failure);}
    }
    private void fail(Minecraft mc,Throwable failure) {
        failure.printStackTrace();try{Files.writeString(Path.of("walking-check-result.txt"),"FAIL stage "+stage+": "+failure);}catch(Exception ignored){}
        if(fixture!=null)fixture.stop(0);mc.stop();
    }
}
