package dev.realworldmc.client.mixin;
import dev.realworldmc.client.PanoramaRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method="tick",at=@At("TAIL"))
    private void realworldmc$photoNavigation(boolean slowDown,float factor,CallbackInfo ci) {
        PanoramaRenderer.INSTANCE.forward(Minecraft.getInstance().options.keyUp.isDown(),shiftKeyDown);
        if (!dev.realworldmc.client.SpaceFlight.enabled() && (!PanoramaRenderer.INSTANCE.active() || PanoramaRenderer.INSTANCE.walking()))return;
        up=false;down=false;left=false;right=false;jumping=false;shiftKeyDown=false;
        forwardImpulse=0;leftImpulse=0;
    }
}
