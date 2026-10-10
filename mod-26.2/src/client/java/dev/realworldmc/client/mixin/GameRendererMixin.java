package dev.realworldmc.client.mixin;
import dev.realworldmc.client.MapRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method="close", at=@At("HEAD"))
    private void realworldmc$close(CallbackInfo ci) { MapRenderer.INSTANCE.clear(); }
}
