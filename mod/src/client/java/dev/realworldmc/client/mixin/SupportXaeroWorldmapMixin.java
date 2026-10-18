package dev.realworldmc.client.mixin;

import dev.realworldmc.client.map.OsmTileCache;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRenderer;
import xaero.common.minimap.render.MinimapRendererHelper;
import xaero.common.mods.SupportXaeroWorldmap;
import xaero.map.region.MapTileChunk;

// basically called once per 64x64 chunk, so visited areas keep their tiles
@Mixin(value = SupportXaeroWorldmap.class, remap = false)
public abstract class SupportXaeroWorldmapMixin {
    @org.spongepowered.asm.mixin.Shadow public abstract net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> getMapDimension();

    @Inject(method = "prepareMapTexturedRect", at = @At("HEAD"), cancellable = true, remap = false)
    private void prepareMapTexturedRect(Matrix4f matrix, float x, float y, int textureX, int textureY, float width, float height, MapTileChunk chunk, MultiTextureRenderTypeRenderer noLightRenderer, MultiTextureRenderTypeRenderer withLightRenderer, MinimapRendererHelper helper, CallbackInfo ci) {
        if (OsmTileCache.draw(getMapDimension(), matrix, chunk.getX() * 64.0, chunk.getZ() * 64.0, x, y, width, height, noLightRenderer::begin, null)) {
            ci.cancel();
        }
    }
}
