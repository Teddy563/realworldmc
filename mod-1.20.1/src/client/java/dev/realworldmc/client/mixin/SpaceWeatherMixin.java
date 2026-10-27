package dev.realworldmc.client.mixin;

import dev.realworldmc.client.SpaceView;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Local-world clouds and precipitation do not follow the orbital camera. */
@Mixin(LevelRenderer.class)
public class SpaceWeatherMixin {
    @Inject(method={"renderClouds","renderSnowAndRain","renderWorldBorder","renderHitOutline"},at=@At("HEAD"),cancellable=true)
    private void realworldmc$orbitalWeather(CallbackInfo ci) {
        if(SpaceView.active())ci.cancel();
    }
}
