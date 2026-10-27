package dev.realworldmc.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.realworldmc.client.MapRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public class FlightChunkVisibilityMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;isChunkCompiled(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean realworldmc$keepCameraSubject(LevelRenderer renderer, BlockPos pos,
            Operation<Boolean> original, @Local(ordinal = 0) Entity entity) {
        var mc = Minecraft.getInstance();
        // The local model must not disappear while its destination chunk mesh is rebuilding.
        // Keep the usual chunk gate for all other entities and preserve terrain depth testing.
        if (MapRenderer.INSTANCE.enabled() && entity == mc.player && entity == mc.getCameraEntity()
                && !mc.options.getCameraType().isFirstPerson()) return true;
        return original.call(renderer, pos);
    }
}
