package dev.realworldmc.client.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.realworldmc.client.map.OsmTileCache;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRenderer;
import xaero.map.gui.GuiMap;

@Mixin(value = GuiMap.class, remap = false)
public class GuiMapMixin {

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lxaero/map/gui/GuiMap;renderTexturedModalRectWithLighting3(Lorg/joml/Matrix4f;FFFFIZLxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRenderer;)V"), remap = false)
    private void renderTexturedModalRectWithLighting3(Matrix4f matrix, float x, float y, float width, float height, int texture, boolean hasLight, MultiTextureRenderTypeRenderer renderer, Operation<Void> original,
            @Local(name = "flooredCameraX") int flooredCameraX,
            @Local(name = "flooredCameraZ") int flooredCameraZ,
            @Local(name = "noLightRenderer") MultiTextureRenderTypeRenderer noLightRenderer) {
        if (!drawTiles(matrix, x, y, width, height, flooredCameraX, flooredCameraZ, noLightRenderer)) {
            original.call(matrix, x, y, width, height, texture, hasLight, renderer);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lxaero/map/gui/GuiMap;renderTexturedModalSubRectWithLighting(Lorg/joml/Matrix4f;FFFFFFFFIZLxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRenderer;)V"), remap = false)
    private void renderTexturedModalSubRectWithLighting(Matrix4f matrix, float x, float y, float textureX1, float textureY1, float textureX2, float textureY2, float width, float height, int texture, boolean hasLight, MultiTextureRenderTypeRenderer renderer, Operation<Void> original,
            @Local(name = "flooredCameraX") int flooredCameraX,
            @Local(name = "flooredCameraZ") int flooredCameraZ,
            @Local(name = "noLightRenderer") MultiTextureRenderTypeRenderer noLightRenderer) {
        if (!drawTiles(matrix, x, y, width, height, flooredCameraX, flooredCameraZ, noLightRenderer)) {
            original.call(matrix, x, y, textureX1, textureY1, textureX2, textureY2, width, height, texture, hasLight, renderer);
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "render", at = @At("TAIL"), remap = true)
    private void mapCredits(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        OsmTileCache.credits(graphics, true);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void creditClicked(double x, double y, int button, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci) {
        var screen = (GuiMap) (Object) this;
        if (OsmTileCache.clickCredit(x, y, button, screen.width, screen.height)) ci.setReturnValue(true);
    }

    @Unique
    private boolean drawTiles(Matrix4f matrix, float x, float y, float width, float height, int flooredCameraX, int flooredCameraZ, MultiTextureRenderTypeRenderer noLightRenderer) {
        if (width > 512) return false; // just for safety if there is any kind of unlimited zoom option
        // x/y are blocks relative to the floored camera position
        var world = ((GuiMap) (Object) this).getMapProcessor().getMapWorld();
        if (world == null) return false;
        return OsmTileCache.draw(world.getCurrentDimensionId(), matrix, x + flooredCameraX, y + flooredCameraZ, x, y, width, height, noLightRenderer::begin, null);
    }

}
