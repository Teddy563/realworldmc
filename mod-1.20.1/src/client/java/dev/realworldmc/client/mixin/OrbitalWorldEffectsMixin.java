package dev.realworldmc.client.mixin;

import dev.realworldmc.client.SpaceView;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Earth objects remain saved at the launch point but do not belong in the orbital scene. */
@Mixin({ParticleEngine.class, BlockEntityRenderDispatcher.class})
public abstract class OrbitalWorldEffectsMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void realworldmc$orbitalScene(CallbackInfo ci) {
        if(SpaceView.active())ci.cancel();
    }
}
