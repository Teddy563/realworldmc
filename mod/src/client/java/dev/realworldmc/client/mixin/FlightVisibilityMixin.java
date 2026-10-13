package dev.realworldmc.client.mixin;

import dev.realworldmc.client.MapRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class FlightVisibilityMixin {
    @Inject(method = "getPackedLightCoords", at = @At("HEAD"), cancellable = true)
    private void realworldmc$orbitalLight(Entity entity, float partial, CallbackInfoReturnable<Integer> cir) {
        // The high anchor may be outside loaded light sections. Keep the filmed subject readable.
        if (dev.realworldmc.client.SpaceFlight.anchored() && dev.realworldmc.client.SpaceFlight.posed(entity))
            cir.setReturnValue(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void realworldmc$hideLocalEntities(Entity entity, double x, double y, double z, float yaw, float partial,
            com.mojang.blaze3d.vertex.PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers,
            int light, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (dev.realworldmc.client.SpaceView.active() && entity != Minecraft.getInstance().player) ci.cancel();
    }
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void realworldmc$cameraSubject(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        var mc = Minecraft.getInstance();
        if (dev.realworldmc.client.SpaceView.active() && entity != mc.player) { cir.setReturnValue(false); return; }
        // Culling uses the current tick position; the following camera and model use interpolated positions.
        // A fast local player can otherwise fail both the distance and frustum tests between every tick.
        if (MapRenderer.INSTANCE.enabled() && entity == mc.player && entity == mc.getCameraEntity()
            && !mc.options.getCameraType().isFirstPerson()) cir.setReturnValue(true);
    }
}
