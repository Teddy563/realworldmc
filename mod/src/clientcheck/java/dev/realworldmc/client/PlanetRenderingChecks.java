package dev.realworldmc.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import java.nio.file.Path;

final class PlanetRenderingChecks {
    static void run(Minecraft mc) throws Exception {
        var target=new TextureTarget(512,512,true,Minecraft.ON_OSX);
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),1,.05f,8000);
        try {
            for(var body:new CelestialBody[]{CelestialBody.MOON,CelestialBody.MARS}) {
                int[] samples=new int[2];
                for(int side=0;side<2;side++) {
                    target.bindWrite(true);RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
                    RenderSystem.clearColor(0,0,0,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
                    PlanetRenderer.render(body,body.radius*.8,new Matrix4f().rotationX((float)Math.PI/2),projection,new Quaternionf().rotationX(side*(float)Math.PI));
                    try(var image=new NativeImage(512,512,false)) {
                        com.mojang.blaze3d.platform.GlStateManager._bindTexture(target.getColorTextureId());
                        image.downloadTexture(0,false);image.flipY();image.writeToFile(Path.of("planet-"+body.id()+"-"+side+".png"));
                        int colored=0,magenta=0,detail=0;var values=new java.util.HashSet<Integer>();
                        for(int y=0;y<512;y++)for(int x=0;x<512;x++) {
                            int rgb=image.getPixelRGBA(x,y),r=rgb&255,g=rgb>>>8&255,b=rgb>>>16&255;
                            if(r+g+b>18){colored++;values.add(rgb);}
                            if(r>150 && b>150 && g<20)magenta++;
                            if(x>24 && x<486 && y>24 && y<486 && Math.abs(r-(image.getPixelRGBA(x+1,y)&255))>1)detail++;
                        }
                        if(colored<35000 || values.size()<80 || detail<1000 || magenta>10)throw new AssertionError(body+" hemisphere "+side+" map texture did not render: pixels="+colored+", colors="+values.size()+", detail="+detail+", missing="+magenta);
                        samples[side]=image.getPixelRGBA(256,256);
                    }
                }
                if(samples[0]==samples[1])throw new AssertionError(body+" globe did not turn to a different hemisphere");
            }
        } finally {target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
    }
}
