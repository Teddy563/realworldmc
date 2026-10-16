package dev.realworldmc.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.realworldmc.client.map.OsmTileCache;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRenderer;
import xaero.common.minimap.render.MinimapFBORenderer;
import xaero.common.minimap.render.MinimapRendererHelper;

@Mixin(value = MinimapFBORenderer.class, remap = false)
public abstract class MinimapFBORendererMixin {

    @WrapOperation(method = "renderChunksToFBO", at = @At(value = "INVOKE", target = "Lxaero/common/minimap/render/MinimapRendererHelper;prepareMyTexturedColoredModalRect(Lorg/joml/Matrix4f;FFIIFFFFIFFFFLxaero/common/graphics/renderer/multitexture/MultiTextureRenderTypeRenderer;)V"), remap = false
    )
    private void prepareMyTexturedColoredModalRect(MinimapRendererHelper helper, Matrix4f matrix, float x, float z, int textureX, int textureY, float width, float height, float textureWidth, float textureHeight, int texture, float red, float green, float blue, float alpha, MultiTextureRenderTypeRenderer renderer, Operation<Void> original,
            @Local(name = "xFloored") int xFloored,
            @Local(name = "zFloored") int zFloored) {
        if (!OsmTileCache.draw(matrix, x + xFloored, z + zFloored, x, z, width, height, renderer::begin, new float[] {red, green, blue, alpha})) {
            original.call(helper, matrix, x, z, textureX, textureY, width, height, textureWidth, textureHeight, texture, red, green, blue, alpha, renderer);
        }
    }
}
