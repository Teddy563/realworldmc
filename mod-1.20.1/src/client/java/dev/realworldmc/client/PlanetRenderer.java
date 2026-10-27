package dev.realworldmc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Real global color mosaics, at their native resolution. No invented terrain or provider requests. */
public final class PlanetRenderer {
    private static VertexBuffer sphere;
    private PlanetRenderer() {}

    public static void render(WorldRenderContext context) {
        var mc=Minecraft.getInstance();
        float partial=context.tickDelta();
        var offset=context.camera().getPosition().subtract(mc.player.getPosition(partial));
        render(SpaceFlight.body(),SpaceFlight.altitude(partial),
            new Matrix4f(context.matrixStack().last().pose()).setTranslation(0,0,0)
                .translate((float)-offset.x,(float)-offset.y,(float)-offset.z),
            context.projectionMatrix(),new Quaternionf(SpaceFlight.orbitRotation(partial)));
    }
    static void render(CelestialBody body,double altitude,Matrix4f view,Matrix4f projection,Quaternionf rotation) {
        if(body==CelestialBody.EARTH)return;
        if(sphere==null) {
            var builder=Meshes.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            final int width=512,height=256;
            for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                vertex(builder,(float)x/width,(float)y/height);
                vertex(builder,(float)x/width,(float)(y+1)/height);
                vertex(builder,(float)(x+1)/width,(float)(y+1)/height);
                vertex(builder,(float)(x+1)/width,(float)y/height);
            }
            sphere=new VertexBuffer(VertexBuffer.Usage.STATIC);
            sphere.bind();sphere.upload(builder.end());VertexBuffer.unbind();
        }
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
        RenderSystem.disableCull();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0,new ResourceLocation("realworldmc","textures/space/"+body.id()+".png"));
        // Keep the physical player and camera near the origin. Only the distant mesh uses planet-scale values.
        var model=new Matrix4f(view).translate(0,(float)(-body.radius-altitude),0).rotate(rotation).scale((float)body.radius);
        sphere.bind();sphere.drawWithShader(model,SpaceView.projection(projection,altitude,body.radius),RenderSystem.getShader());
        VertexBuffer.unbind();RenderSystem.enableCull();RenderSystem.setShaderColor(1,1,1,1);
    }
    private static void vertex(BufferBuilder builder,float u,float v) {
        double lat=(.5-v)*Math.PI,lon=(u-.5)*Math.PI*2;
        float x=(float)(Math.cos(lat)*Math.sin(lon)),y=(float)(Math.cos(lat)*Math.cos(lon)),z=(float)-Math.sin(lat);
        float light=.16f+.84f*Math.max(0,x*.35f+y*.85f-z*.39f);
        builder.vertex(x,y,z).uv(u,v).color(light,light,light,1).endVertex();
    }
}
