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
    private boolean opened;
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
            } else if(path.equals("/ground")) body="{\"height\":0}".getBytes();
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
            if(stage==0) {
                mc.setScreen(null);server(mc,p->{p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);PanoramaRoom.enter(p.createCommandSourceStack(),0,0);});stage=1;ticks=0;return;
            }
            if(stage==1) {
                if(!PanoramaRenderer.INSTANCE.active())return;
                require(PanoramaRenderer.INSTANCE.walking(),"Street View starts in walking mode");
                anchorX=PanoramaRenderer.INSTANCE.cameraAnchor().x;anchorZ=PanoramaRenderer.INSTANCE.cameraAnchor().z;
                require(photographs.get()==1,"One initial photograph");move(mc,2,0);stage=2;return;
            }
            if(++ticks<30)return;
            if(stage==2) {
                require(Math.abs(mc.player.getX()-anchorX-2)<.1,"Free movement is not pinned");
                require(photographs.get()==1,"No requests inside the 5x5 area");move(mc,2.7,0);stage=3;return;
            }
            if(stage==3) {
                if(photographs.get()<2)return;
                require(Math.abs(PanoramaRenderer.INSTANCE.cameraAnchor().x-anchorX)<.1,"Prepared destination does not move the photograph early");
                move(mc,3.2,0);stage=4;return;
            }
            if(stage==4) {
                if(Math.abs(PanoramaRenderer.INSTANCE.cameraAnchor().x-anchorX)<5)return;
                require(photographs.get()==2,"Prepared photograph reused at transition");
                anchorX=PanoramaRenderer.INSTANCE.cameraAnchor().x;anchorZ=PanoramaRenderer.INSTANCE.cameraAnchor().z;
                move(mc,0,3.4);stage=5;return;
            }
            if(stage==5) {
                require(photographs.get()==2,"Missing south link causes no photo request");
                require(Math.abs(PanoramaRenderer.INSTANCE.cameraAnchor().x-anchorX)<.1,"Missing link keeps the current stop");
                move(mc,-2.7,0);stage=6;return;
            }
            if(stage==6) {
                if(photographs.get()<3)return;
                move(mc,0,0);stage=7;return;
            }
            if(stage==7) {
                require(Math.abs(PanoramaRenderer.INSTANCE.cameraAnchor().x-anchorX)<.1,"Retreat cancels a prepared destination");
                require(photographs.get()==3,"Cancelled destination does not start a retry loop");
                server(mc,p->PanoramaRoom.exit(p.createCommandSourceStack()));stage=8;ticks=0;return;
            }
            if(stage==8) {
                require(!PanoramaRenderer.INSTANCE.active(),"Back clears the Street View session");
                require(mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD),"Back restores original dimension");
                Files.writeString(Path.of("walking-check-result.txt"),"PASS: default free walking, 5x5 request boundary, delayed 6x6 transition, single prepared photo reuse, missing-link fallback, retreat cancellation and return cleanup");
                fixture.stop(0);mc.stop();
            }
        }catch(Throwable failure){fail(mc,failure);}
    }
    private void fail(Minecraft mc,Throwable failure) {
        failure.printStackTrace();try{Files.writeString(Path.of("walking-check-result.txt"),"FAIL stage "+stage+": "+failure);}catch(Exception ignored){}
        if(fixture!=null)fixture.stop(0);mc.stop();
    }
}
