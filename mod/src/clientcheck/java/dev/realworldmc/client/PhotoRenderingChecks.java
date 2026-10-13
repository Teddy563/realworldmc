package dev.realworldmc.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import java.nio.file.*;

/** Real OpenGL, actual Minecraft shaders, and transformed mixins; no network or saved world required. */
public final class PhotoRenderingChecks implements ClientModInitializer {
    private boolean ran;
    private SpaceFlightIntegrationChecks flightCheck;
    private PlanetTravelChecks planetCheck;
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("realworldmc.photoCheck"))return;
        ClientLifecycleEvents.CLIENT_STARTED.register(mc->GLFW.glfwHideWindow(mc.getWindow().getWindow()));
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(planetCheck!=null){planetCheck.tick(mc);return;}
            if(flightCheck!=null){flightCheck.tick(mc);return;}
            if(ran || mc.getOverlay()!=null || GameRenderer.getPositionTexShader()==null)return;
            ran=true;
            try{CameraChecks.run(mc);check(mc);PhotoExcavationChecks.run(mc);SpaceViewChecks.run(mc);Files.writeString(Path.of("photo-check-result.txt"),"PASS: first/rear/front camera switching and separation, panorama pixels, terrain colour mask, textured crater and TNT, geographic scale, clipping and render-state restoration, orbital projection and stars, Moon and Mars textured hemispheres"+(FabricLoader.getInstance().isModLoaded("iris")?", Iris dimension pipeline injection":" (vanilla pipeline)"));}
            catch(Throwable failure){failure.printStackTrace();try{Files.writeString(Path.of("photo-check-result.txt"),"FAIL: "+failure);}catch(Exception ignored){}}
            finally{
                if(Boolean.getBoolean("realworldmc.planetTravelCheck")) {planetCheck=new PlanetTravelChecks();planetCheck.start(mc);}
                else if(Boolean.getBoolean("realworldmc.spaceFlightCheck")) {flightCheck=new SpaceFlightIntegrationChecks();flightCheck.start(mc);}
                else mc.stop();
            }
        });
    }
    private static void check(Minecraft mc) throws Exception {
        var orientation=new org.joml.Matrix3f();
        var ground=PanoramaRenderer.photoPoint(orientation,2.5f,.5f,.75f);
        require(Math.abs(ground.y+2.5)<.0001 && Math.abs(ground.z+2.5)<.0001,"Photo ground must intersect a fixed 2.5 metre capture plane");
        var lower=PanoramaRenderer.photoPoint(orientation,1.5f,.5f,.75f);
        require(Math.abs(lower.y+1.5)<.0001,"Height calibration must rebuild the photo ground plane");
        var sky=PanoramaRenderer.photoPoint(orientation,2.5f,.5f,.25f);
        require(Math.abs(sky.length()-100)<.001,"Sky must retain the distant shell");
        var stone=net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var air=net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        var pos=net.minecraft.core.BlockPos.ZERO;
        var up=net.minecraft.core.Direction.UP;
        require(PhotoEditsRenderer.cornerShade(pos,up,0,1,0,p->air)==1,"Open crater corners stay bright");
        require(PhotoEditsRenderer.cornerShade(pos,up,0,1,0,p->stone)<.7,"Enclosed corners gain ambient shading");
        require(PhotoEditsRenderer.cornerShade(pos,up,0,1,0,p->p.equals(pos.above().west())?stone:air)>.8,"One neighbour must not turn the crater black");
        TextureTarget target=new TextureTarget(128,128,true,Minecraft.ON_OSX);
        NativeImage pixels=new NativeImage(4,2,false);
        for(int y=0;y<2;y++)for(int x=0;x<4;x++)pixels.setPixelRGBA(x,y,0xff3388ee);
        DynamicTexture texture=new DynamicTexture(pixels);
        var method=PanoramaRenderer.class.getDeclaredMethod("buildSphere",JsonObject.class);method.setAccessible(true);
        VertexBuffer sphere=(VertexBuffer)method.invoke(null,new JsonObject());
        try{
            target.bindWrite(true);RenderSystem.clearColor(0,0,0,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
            RenderSystem.disableCull();RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderTexture(0,texture.getId());
            sphere.bind();sphere.drawWithShader(new Matrix4f(),new Matrix4f().perspective((float)Math.toRadians(80),1,.05f,512),RenderSystem.getShader());VertexBuffer.unbind();
            try(MemoryStack stack=MemoryStack.stackPush()){
                var pixel=stack.malloc(4);GL11.glReadPixels(64,64,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
                require((pixel.get(0)&255)>200 && (pixel.get(1)&255)>100,"Photo sphere must draw actual image pixels");
                PhotoTerrainMask.writingDepthOnly=true;RenderSystem.colorMask(true,true,true,true);
                var mask=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);
                require(mask.get(0)==0 && mask.get(1)==0 && mask.get(2)==0 && mask.get(3)==0,"RenderType must not re-enable terrain colour");
                RenderSystem.depthMask(true);RenderSystem.clearColor(0,1,0,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
                GL11.glReadPixels(64,64,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
                require((pixel.get(0)&255)>200,"Depth-only terrain must preserve the photograph");
                PhotoTerrainMask.writingDepthOnly=false;RenderSystem.colorMask(true,true,true,true);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);require(mask.get(0)!=0 && mask.get(3)!=0,"Normal rendering must regain colour writes");
            }
            if(FabricLoader.getInstance().isModLoaded("iris")){
                Class<?> idClass=Class.forName("net.irisshaders.iris.shaderpack.materialmap.NamespacedId");
                Object dimension=idClass.getConstructor(String.class,String.class).newInstance("earthcraft","streetworld");
                var factory=Class.forName("net.irisshaders.iris.Iris").getDeclaredMethod("createPipeline",idClass);factory.setAccessible(true);
                require(java.util.Arrays.stream(factory.getDeclaringClass().getDeclaredMethods()).anyMatch(m->m.getName().contains("photoPipeline")),"Optional Iris compatibility mixin must be applied");
                require(factory.invoke(null,dimension).getClass().getSimpleName().equals("VanillaRenderingPipeline"),"Street View must use a photo-compatible Iris pipeline");
            }
            try(NativeImage result=new NativeImage(128,128,false)){
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,target.getColorTextureId());result.downloadTexture(0,false);result.writeToFile(Path.of("photo-check.png"));
            }
        }finally{
            PhotoTerrainMask.writingDepthOnly=false;RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
            sphere.close();texture.close();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);
        }
    }
}
