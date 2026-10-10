package dev.realworldmc.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.realworldmc.StreetEditsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import java.nio.file.Path;
import java.util.*;

/** Renders real resource-pack models over hidden collision depth, at two distant world origins. */
final class PhotoExcavationChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void run(Minecraft mc) throws Exception {
        RenderSystem.backupProjectionMatrix();
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try(NativeImage near=fixture(mc,0,false);NativeImage far=fixture(mc,100000,false);NativeImage unlit=fixture(mc,0,true)) {
            near.writeToFile(Path.of("photo-excavation-check.png"));
            far.writeToFile(Path.of("photo-excavation-far-check.png"));
            int differences=0,red=0,stone=0;
            int background=near.getPixelRGBA(0,0);
            Set<Integer> colours=new HashSet<>();
            Map<Integer,Integer> greys=new HashMap<>();
            for(int y=0;y<near.getHeight();y++)for(int x=0;x<near.getWidth();x++){
                int a=near.getPixelRGBA(x,y),b=far.getPixelRGBA(x,y);
                require(a==unlit.getPixelRGBA(x,y),"Photo blocks must not inherit dark item/entity shader lights");
                // Natural block models choose texture rotations from position. Compare silhouettes,
                // not those expected texel differences, when translating a geographic region.
                if((a==background)!=(b==background))differences++;
                int r=a&255,g=(a>>>8)&255,bl=(a>>>16)&255;
                if(r>70 && r>g*1.7 && r>bl*1.7)red++;
                if(Math.abs(r-g)<5 && Math.abs(g-bl)<5 && r>45){stone++;colours.add(a);greys.merge(a,1,Integer::sum);}
            }
            require(red>100,"Placed TNT must use its red block texture");
            require(stone>300 && colours.size()>12,"Crater surfaces must have varied actual stone texels");
            require(greys.values().stream().mapToInt(Integer::intValue).max().orElse(0)<stone*.55,"A single flat cavity colour must not replace the textured excavation");
            require(differences<30,"A 100 km geographic offset must not change block size or clip the excavation: "+differences);
        } finally {
            PhotoEditsRenderer.INSTANCE.clear();
            PhotoTerrainMask.writingDepthOnly=false;RenderSystem.colorMask(true,true,true,true);
            RenderSystem.restoreProjectionMatrix();modelView.popMatrix();RenderSystem.applyModelViewMatrix();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }
    private static NativeImage fixture(Minecraft mc,int origin,boolean darkLights) {
        TextureTarget target=new TextureTarget(512,512,true,Minecraft.ON_OSX);
        try {
            target.bindWrite(true);RenderSystem.depthMask(true);RenderSystem.colorMask(true,true,true,true);
            RenderSystem.clearColor(.15f,.4f,.75f,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(70),1,.05f,200),VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.setShaderFogStart(100);RenderSystem.setShaderFogEnd(200);
            RenderSystem.setShaderLights(new Vector3f(.2f,1,-.7f).normalize(),new Vector3f(-.2f,1,.7f).normalize());
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.enableCull();
            var camera=new Vec3(origin+4.5,3.5,6.5);
            var view=new Matrix4f().lookAt(new Vector3f(),new Vector3f(-4,-4,-6),new Vector3f(0,1,0));
            var global=RenderSystem.getModelViewStack();global.set(view);RenderSystem.applyModelViewMatrix();
            Map<BlockPos,BlockState> blocks=new HashMap<>();
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-3;y<0;y++)blocks.put(new BlockPos(origin+x,y,z),Blocks.STONE.defaultBlockState());
            List<StreetEditsPayload.Edit> edits=new ArrayList<>();
            for(int x=0;x<2;x++)for(int z=0;z<2;z++){
                var hole=new BlockPos(origin+x,-1,z);blocks.remove(hole);
                edits.add(new StreetEditsPayload.Edit(hole.asLong(),Block.getId(Blocks.STONE.defaultBlockState())));
            }
            var tnt=new BlockPos(origin+2,0,0);blocks.put(tnt,Blocks.TNT.defaultBlockState());
            edits.add(new StreetEditsPayload.Edit(tnt.asLong(),Block.getId(Blocks.AIR.defaultBlockState())));
            var buffers=mc.renderBuffers().bufferSource();var poses=new PoseStack();
            PhotoTerrainMask.writingDepthOnly=true;RenderSystem.colorMask(false,false,false,false);
            blocks.forEach((pos,state)->{
                poses.pushPose();poses.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);
                mc.getBlockRenderer().renderSingleBlock(state,poses,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);poses.popPose();
            });
            buffers.endBatch();PhotoTerrainMask.writingDepthOnly=false;RenderSystem.colorMask(true,true,true,true);
            var renderer=PhotoEditsRenderer.INSTANCE;renderer.receive(new StreetEditsPayload(true,edits));
            // Deliberately dirty the global view: production rendering must establish its own view once.
            global.rotationY(.3f);RenderSystem.applyModelViewMatrix();var previous=new Matrix4f(RenderSystem.getModelViewMatrix());
            if(darkLights)RenderSystem.setShaderLights(new Vector3f(),new Vector3f());
            renderer.renderEdits(camera,view,p->blocks.getOrDefault(p,Blocks.AIR.defaultBlockState()),p->true,null,0);
            require(RenderSystem.getModelViewMatrix().equals(previous),"Photo rendering must restore the caller's view matrix");
            require(!GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL),"Photo rendering must restore polygon offset");
            NativeImage image=new NativeImage(512,512,false);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,target.getColorTextureId());image.downloadTexture(0,false);image.flipY();return image;
        } finally {target.destroyBuffers();}
    }
}
