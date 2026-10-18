package dev.realworldmc.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.realworldmc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import java.nio.*;
import java.nio.file.Path;
import java.util.*;

/** Actual textured mesh upload and brick cut surfaces; no provider imagery or key. */
final class MapExcavationChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void run(Minecraft mc) throws Exception {
        RenderSystem.backupProjectionMatrix();
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try(NativeImage intact=fixture(mc,false,0);NativeImage cut=fixture(mc,true,0);NativeImage distant=fixture(mc,true,100000)) {
            intact.writeToFile(Path.of("map-wall-intact.png"));cut.writeToFile(Path.of("map-wall-cut.png"));
            int changed=0,brick=0,background=0,offsetDifferences=0;
            for(int y=0;y<512;y++)for(int x=0;x<512;x++) {
                int a=intact.getPixelRGBA(x,y),b=cut.getPixelRGBA(x,y),c=distant.getPixelRGBA(x,y);
                if(a!=b)changed++;
                int r=b&255,g=b>>>8&255,blue=b>>>16&255;
                if(r>50 && r>g*1.2 && r>blue*1.1)brick++;
                if(a!=b && r<15 && g<15 && blue<15)background++;
                if((b&0xffffff)!=(c&0xffffff))offsetDifferences++;
            }
            require(changed>200 && changed<15000,"Damage must change the hole, not remove the large wall triangles: "+changed);
            require(background>100,"A real opening must reveal the hollow building interior: "+background);
            require(brick>40,"Exposed wall edges must use brick texels, not invisible collision blocks: "+brick);
            require(offsetDifferences<50,"Distant regions preserve cut geometry and materials: "+offsetDifferences);
        } finally {
            VertexBuffer.unbind();RenderSystem.restoreProjectionMatrix();modelView.popMatrix();RenderSystem.applyModelViewMatrix();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }
    private static NativeImage fixture(Minecraft mc,boolean damaged,int origin) {
        TextureTarget target=new TextureTarget(512,512,true,Minecraft.ON_OSX);
        MapTile tile=null;
        try {
            target.bindWrite(true);RenderSystem.depthMask(true);RenderSystem.colorMask(true,true,true,true);
            RenderSystem.clearColor(.02f,.02f,.02f,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
            Matrix4f projection=new Matrix4f().perspective((float)Math.toRadians(50),1,.1f,100);
            RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.setShaderFogStart(100);RenderSystem.setShaderFogEnd(200);RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.disableBlend();RenderSystem.disableCull();
            Vec3 camera=new Vec3(origin+8,7,12);
            Matrix4f view=new Matrix4f().lookAt(new Vector3f(),new Vector3f(-4,-2.5f,-11.5f),new Vector3f(0,1,0));
            RenderSystem.getModelViewStack().identity();RenderSystem.applyModelViewMatrix();
            float[] vertices={0,.5f,.5f,0,0, 8,.5f,.5f,1,0, 8,8.5f,.5f,1,1, 0,.5f,.5f,0,0, 8,8.5f,.5f,1,1, 0,8.5f,.5f,0,1};
            ByteBuffer bytes=ByteBuffer.allocate(32+16+vertices.length*4+4).order(ByteOrder.LITTLE_ENDIAN);
            bytes.put(new byte[]{'E','C','T','1'}).putInt(1).putDouble(origin).putDouble(0).putDouble(0);
            bytes.putInt(6).putInt(1).putInt(1).putInt(0xffbc9168);
            for(float value:vertices)bytes.putFloat(value);bytes.putInt(0xffbc9168);
            TileData data=TileData.parse(1,false,bytes.array(),0,0); // Coarse fallback must respect damage too.
            List<MeshCut.Box> boxes=new ArrayList<>();List<StreetEditsPayload.Edit> edits=new ArrayList<>();
            Map<BlockPos,BlockState> blocks=new HashMap<>();
            for(int x=0;x<8;x++)for(int y=0;y<8;y++)blocks.put(new BlockPos(origin+x,y,0),EarthBlocks.MAP_BRICK.defaultBlockState());
            if(damaged)for(int x=3;x<5;x++)for(int y=3;y<5;y++) {
                BlockPos pos=new BlockPos(origin+x,y,0);blocks.remove(pos);
                boxes.add(new MeshCut.Box(x,y+.5,0,x+1,y+1.5,1));
                edits.add(new StreetEditsPayload.Edit(pos.asLong(),Block.getId(EarthBlocks.MAP_BRICK.defaultBlockState())));
            }
            tile=MapTile.withCuts(data,boxes);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderTexture(0,tile.textureId(0));
            tile.buffer(0).bind();tile.buffer(0).drawWithShader(new Matrix4f(view).translate((float)(origin-camera.x),(float)-camera.y,(float)-camera.z),projection,RenderSystem.getShader());VertexBuffer.unbind();
            RenderSystem.enableCull();
            var surfaces=new PhotoEditsRenderer();surfaces.receive(new StreetEditsPayload(true,edits));
            surfaces.renderMapCuts(camera,view,p->blocks.getOrDefault(p,Blocks.AIR.defaultBlockState()),p->true,null);
            NativeImage image=new NativeImage(512,512,false);GL11.glBindTexture(GL11.GL_TEXTURE_2D,target.getColorTextureId());image.downloadTexture(0,false);image.flipY();return image;
        } finally {if(tile!=null)tile.close();target.destroyBuffers();}
    }
}
