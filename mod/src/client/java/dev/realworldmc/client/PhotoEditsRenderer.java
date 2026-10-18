package dev.realworldmc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.realworldmc.StreetEditsPayload;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Draw only changed blocks and the textured faces exposed by excavation. */
public final class PhotoEditsRenderer {
    public static final PhotoEditsRenderer INSTANCE = new PhotoEditsRenderer();
    private final Map<BlockPos, BlockState> originals = new HashMap<>();
    private final ByteBufferBuilder memory = new ByteBufferBuilder(256 * 1024);
    private final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(memory);

    public void clear() { originals.clear(); }
    public void receive(StreetEditsPayload payload) {
        if (payload.reset()) clear();
        for (var edit : payload.edits()) originals.put(BlockPos.of(edit.position()), Block.stateById(edit.originalState()));
    }

    public void render(WorldRenderContext ctx) {
        if (!PanoramaRenderer.INSTANCE.active() || originals.isEmpty()) return;
        renderEdits(ctx.camera().getPosition(), ctx.positionMatrix(), ctx.world()::getBlockState, ctx.world()::hasChunkAt, ctx.world(),PanoramaRenderer.INSTANCE.groundY());
    }

    // Also exercised by the isolated OpenGL fixture at both origin and distant geographic regions.
    void renderEdits(Vec3 camera, Matrix4f view, Function<BlockPos, BlockState> stateAt,
                     Predicate<BlockPos> loaded, BlockAndTintGetter tintWorld,double groundY) {
        renderEdits(camera,view,stateAt,loaded,tintWorld,groundY,false);
    }
    void renderMapCuts(Vec3 camera,Matrix4f view,Function<BlockPos,BlockState> stateAt,Predicate<BlockPos> loaded,BlockAndTintGetter world) {
        renderEdits(camera,view,stateAt,loaded,world,64,true);
    }
    private void renderEdits(Vec3 camera,Matrix4f view,Function<BlockPos,BlockState> stateAt,Predicate<BlockPos> loaded,BlockAndTintGetter tintWorld,double groundY,boolean mapCuts) {
        Minecraft mc = Minecraft.getInstance();
        Map<BlockPos, BlockState> changed = new HashMap<>(), holes = new HashMap<>();
        originals.forEach((pos, original) -> {
            if (pos.distToCenterSqr(camera) > 96 * 96 || !loaded.test(pos)) return;
            BlockState now = stateAt.apply(pos);
            if (now == original) return;
            if (!now.isAir()) { if(!mapCuts)changed.put(pos, now); }
            else if (!original.isAir()) holes.put(pos, original);
        });
        if (changed.isEmpty() && holes.isEmpty()) return;

        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean offset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        float factor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR), units = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        float[] colour = RenderSystem.getShaderColor().clone();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        // Apply the same view once to every model, independent of earlier rendering callbacks.
        modelView.set(view);
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.enableDepthTest(); RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            // Tiny raster bias avoids fighting the hidden terrain's identical depth faces.
            // Block vertices and collision coordinates remain exactly one metre apart.
            RenderSystem.enablePolygonOffset(); RenderSystem.polygonOffset(-1, -2);
            PoseStack matrices = new PoseStack();
            if(mapCuts)matrices.translate(0,.5,0);
            changed.forEach((pos, state) -> {
                // Chunk shaders use the daylight shade below once. Item/entity shaders add
                // a second directional light pass, making photo-world blocks too dark.
                for (Direction face : Direction.values())
                    drawFace(state, pos, face, camera, matrices, tintWorld, stateAt, groundY, false);
            });
            holes.forEach((pos, original) -> {
                for (Direction outward : Direction.values()) {
                    BlockPos neighbour = pos.relative(outward);
                    if (holes.containsKey(neighbour) || changed.containsKey(neighbour) || !loaded.test(neighbour)) continue;
                    BlockState boundary = stateAt.apply(neighbour);
                    if(mapCuts) {
                        if(!dev.realworldmc.EarthBlocks.isMapBlock(boundary))continue;
                        boundary=dev.realworldmc.EarthBlocks.cutMaterial(boundary);
                    }
                    if (boundary.isAir()) {
                        // Thin scanned walls have no interior. Close only their far opening
                        // with a textured backing; never put a lid across the near opening.
                        Vec3 normal = Vec3.atLowerCornerOf(outward.getNormal());
                        if (Vec3.atCenterOf(pos).add(normal.scale(.5)).subtract(camera).dot(normal) <= 0) continue;
                        boundary = original;
                    }
                    drawFace(boundary, neighbour, outward.getOpposite(), camera, matrices, tintWorld,stateAt,groundY,true);
                }
            });
            buffers.endBatch();
        } finally {
            RenderSystem.polygonOffset(factor, units);
            if (offset) RenderSystem.enablePolygonOffset(); else RenderSystem.disablePolygonOffset();
            RenderSystem.depthFunc(depthFunction); RenderSystem.depthMask(mask);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(colour[0], colour[1], colour[2], colour[3]);
            modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
        }
    }

    private void drawFace(BlockState state, BlockPos pos, Direction face, Vec3 camera,
                          PoseStack matrices, BlockAndTintGetter tintWorld,Function<BlockPos,BlockState> stateAt,double groundY,boolean excavation) {
        Minecraft mc = Minecraft.getInstance();
        var model = mc.getBlockRenderer().getBlockModel(state);
        var random = RandomSource.create(state.getSeed(pos));
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, face, random));
        random.setSeed(state.getSeed(pos));
        for (var quad : model.getQuads(state, null, random)) if (quad.getDirection() == face) quads.add(quad);
        if (quads.isEmpty()) return;
        var consumer = buffers.getBuffer(ItemBlockRenderTypes.getChunkRenderType(state));
        matrices.pushPose();
        matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        float shade = switch (face) { case UP -> 1f; case DOWN -> .7f; case NORTH, SOUTH -> .9f; default -> .82f; };
        for (var quad : quads) {
            int tint = quad.isTinted() ? mc.getBlockColors().getColor(state, tintWorld, pos, quad.getTintIndex()) : -1;
            float[] corners=new float[4];int[] packed=quad.getVertices();int stride=packed.length/4;
            for(int i=0;i<4;i++){
                float x=Float.intBitsToFloat(packed[i*stride]),y=Float.intBitsToFloat(packed[i*stride+1]),z=Float.intBitsToFloat(packed[i*stride+2]);
                float depth=(float)Math.max(.72,1-Math.max(0,groundY-(pos.getY()+y))*.055);
                corners[i]=excavation ? cornerShade(pos,face,x,y,z,stateAt)*depth : 1f;
            }
            consumer.putBulkData(matrices.last(), quad, corners, ((tint >> 16) & 255) / 255f * shade,
                ((tint >> 8) & 255) / 255f * shade, (tint & 255) / 255f * shade, 1f,
                new int[]{LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT}, OverlayTexture.NO_OVERLAY,true);
        }
        matrices.popPose();
    }
    static float cornerShade(BlockPos pos,Direction face,float x,float y,float z,Function<BlockPos,BlockState> stateAt){
        BlockPos outside=pos.relative(face);
        int sx=x<.5f?-1:1,sy=y<.5f?-1:1,sz=z<.5f?-1:1;
        BlockPos a,b,diagonal;
        switch(face.getAxis()){
            case X -> {a=outside.offset(0,sy,0);b=outside.offset(0,0,sz);diagonal=outside.offset(0,sy,sz);}
            case Y -> {a=outside.offset(sx,0,0);b=outside.offset(0,0,sz);diagonal=outside.offset(sx,0,sz);}
            default -> {a=outside.offset(sx,0,0);b=outside.offset(0,sy,0);diagonal=outside.offset(sx,sy,0);}
        }
        boolean sideA=stateAt.apply(a).canOcclude(),sideB=stateAt.apply(b).canOcclude();
        int covered=sideA && sideB?3:(sideA?1:0)+(sideB?1:0)+(stateAt.apply(diagonal).canOcclude()?1:0);
        return 1-covered*.11f;
    }
}
