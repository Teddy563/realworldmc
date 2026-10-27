package dev.realworldmc.client;

import dev.realworldmc.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import java.util.*;

/** Nearby confirmed edits, bounded by server snapshots as the player travels. */
public final class MapDamage {
    private static final Map<BlockPos,BlockState> originals=new HashMap<>();
    private static final PhotoEditsRenderer surfaces=new PhotoEditsRenderer();
    private static Map<BlockPos,BlockState> pending;
    private MapDamage() { }
    public static void clear(){originals.clear();surfaces.clear();pending=null;}
    public static boolean tracked(BlockPos pos){return originals.containsKey(pos);}
    public static void receive(MapEditsPayload payload) {
        if(payload.reset()){pending=new HashMap<>();return;}
        if(pending!=null) {
            if(!payload.edits().isEmpty()) {
                for(var edit:payload.edits()) {
                    BlockState state=Block.stateById(edit.originalState());
                    if(EarthBlocks.isMapBlock(state) || state.isAir())pending.put(BlockPos.of(edit.position()),state);
                }
                return;
            }
            originals.clear();originals.putAll(pending);pending=null;surfaces.clear();
            var snapshot=originals.entrySet().stream().map(e->new StreetEditsPayload.Edit(e.getKey().asLong(),Block.getId(e.getValue()))).toList();
            surfaces.receive(new StreetEditsPayload(false,snapshot));
            MapRenderer.INSTANCE.invalidateDamage();return;
        }
        List<StreetEditsPayload.Edit> converted=new ArrayList<>();
        for(var edit:payload.edits()) {
            BlockState state=Block.stateById(edit.originalState());
            if(!EarthBlocks.isMapBlock(state) && !state.isAir())continue;
            originals.put(BlockPos.of(edit.position()),state);
            converted.add(new StreetEditsPayload.Edit(edit.position(),edit.originalState()));
        }
        surfaces.receive(new StreetEditsPayload(false,converted));
        MapRenderer.INSTANCE.invalidateDamage();
    }
    public static List<MeshCut.Box> cuts(AABB bounds,ClientLevel level,double ax,double ay,double az) {
        if(level==null || !level.dimension().equals(Level.OVERWORLD))return List.of();
        List<MeshCut.Box> result=new ArrayList<>();
        originals.forEach((pos,state)->{
            double shift=state.isAir()?0:.5;
            if(pos.getX()+1<bounds.minX || pos.getX()>bounds.maxX || pos.getY()+1+shift<bounds.minY || pos.getY()+shift>bounds.maxY || pos.getZ()+1<bounds.minZ || pos.getZ()>bounds.maxZ)return;
            if(!level.hasChunkAt(pos))return;
            BlockState now=level.getBlockState(pos);
            if(EarthBlocks.isMapBlock(now) || (state.isAir() && !now.canOcclude()))return;
            // Match the voxelizer's half-block vertical surface convention.
            result.add(new MeshCut.Box(pos.getX()-ax,pos.getY()+shift-ay,pos.getZ()-az,pos.getX()+1-ax,pos.getY()+1+shift-ay,pos.getZ()+1-az));
        });
        result.sort(Comparator.comparingDouble(MeshCut.Box::x).thenComparingDouble(MeshCut.Box::y).thenComparingDouble(MeshCut.Box::z));
        return result;
    }
    public static void render(WorldRenderContext ctx) {
        if(!ctx.world().dimension().equals(Level.OVERWORLD) || !MapRenderer.INSTANCE.enabled() || SpaceView.active())return;
        surfaces.renderMapCuts(ctx.camera().getPosition(),ctx.matrixStack().last().pose(),ctx.world()::getBlockState,ctx.world()::hasChunkAt,ctx.world());
    }
}
