package dev.realworldmc.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;

/** Optional coarse-globe provider check; keys stay in the separate localhost fixture. */
final class SpaceGlobeChecks {
    static void run(Minecraft mc) throws Exception {
        if(!Boolean.getBoolean("realworldmc.spaceGlobeCheck"))return;
        var http=HttpClient.newHttpClient();
        var ids=JsonParser.parseString(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18889/ids")).build(),HttpResponse.BodyHandlers.ofString()).body()).getAsJsonArray();
        var target=new TextureTarget(512,512,true,Minecraft.ON_OSX);
        var tiles=new java.util.ArrayList<MapTile>();
        try {
            for(var id:ids) {
                byte[] data=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18889/"+id.getAsInt())).build(),HttpResponse.BodyHandlers.ofByteArray()).body();
                tiles.add(new MapTile(TileData.parse(id.getAsInt(),false,data,0,0),null));
            }
            for(int angle=0;angle<=2;angle++) {
            target.bindWrite(true);RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
            RenderSystem.clearColor(0,0,0,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
            double height=12_000_000;
            var view=new Matrix4f().rotationX((float)Math.PI/2);
            var projection=SpaceView.projection(new Matrix4f().perspective((float)Math.toRadians(70),1,.05f,8000),height);
            SpaceView.sky(view,projection,height);
            var rotation=new org.joml.Quaterniond().rotationX(angle*Math.PI/2);
            var region=new dev.realworldmc.EarthRegions.Region(0,44.427454,26.087287,80);
            var camera=GlobeOrbit.cameraInMap(new net.minecraft.world.phys.Vec3(0,height+64,0),GlobeOrbit.centre(region),rotation);
            view.rotate(new org.joml.Quaternionf(rotation));
            var frustum=new net.minecraft.client.renderer.culling.Frustum(view,projection);
            frustum.prepare(camera.x,camera.y,camera.z);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableCull();RenderSystem.disableBlend();
            for(var tile:tiles) {
                if(frustum.isVisible(tile.bounds)) {
                    var mv=new Matrix4f(view).translate((float)(tile.data.anchorX-camera.x),(float)(tile.data.anchorY-camera.y),(float)(tile.data.anchorZ-camera.z));
                    for(int p=0;p<tile.partCount();p++) {
                        RenderSystem.setShaderTexture(0,tile.textureId(p));tile.buffer(p).bind();tile.buffer(p).drawWithShader(mv,projection,RenderSystem.getShader());
                    }
                }
                VertexBuffer.unbind();
            }
            try(var image=new NativeImage(512,512,false)) {
                com.mojang.blaze3d.platform.GlStateManager._bindTexture(target.getColorTextureId());
                image.downloadTexture(0,false);image.flipY();image.writeToFile(Path.of("space-earth-orbit-"+angle+".png"));
                int colored=0;
                for(int y=0;y<512;y++)for(int x=0;x<512;x++){int rgb=image.getPixelRGBA(x,y);int r=rgb&255,g=(rgb>>>8)&255,b=(rgb>>>16)&255;if(Math.max(r,Math.max(g,b))>30 && Math.abs(r-b)>8)colored++;}
                if(colored<10000)throw new AssertionError("Real Earth imagery did not fill orbital view "+angle+": "+colored);
            }
            }
        } finally {tiles.forEach(MapTile::close);RenderSystem.enableCull();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
    }
}
