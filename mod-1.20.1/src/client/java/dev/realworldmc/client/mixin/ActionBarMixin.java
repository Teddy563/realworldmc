package dev.realworldmc.client.mixin;

import dev.realworldmc.client.ActionBar;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class ActionBarMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void realworldmc$information(Component message, boolean animateColour, CallbackInfo ci) {
        if (ActionBar.capture(message)) ci.cancel();
    }
}
