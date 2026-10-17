package dev.realworldmc.client.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.realworldmc.EarthRegions;
import dev.realworldmc.client.map.MapProjection;
import dev.realworldmc.client.map.OsmTileCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

    @Unique
    private static boolean drawTiles(Matrix4f matrix, float x, float y, float width, float height, int flooredCameraX, int flooredCameraZ, MultiTextureRenderTypeRenderer noLightRenderer) {
        if (width > 512) return false; // just for safety if there is any kind of unlimited zoom option
        // x/y are blocks relative to the floored camera position
        return OsmTileCache.draw(matrix, x + flooredCameraX, y + flooredCameraZ, x, y, width, height, noLightRenderer::begin, null);
    }

    @ModifyVariable(method = "render", at = @At("STORE"), name = "biomeText", remap = false)
    private String replaceBiomeText(String original,
                                    @Local(name = "mousePosX") double mouseX,
                                    @Local(name = "mousePosZ") double mouseZ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != Level.OVERWORLD) return original;
        EarthRegions.Region region = MapProjection.regionAt(mouseX, mouseZ);
        if (region == null) return original;
        double[] geo = MapProjection.latLon(region, mouseX, mouseZ);
        String name = MapProjection.cityAt(geo[0], geo[1]);
        return name == null ? original : name;
    }





}
