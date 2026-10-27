package dev.realworldmc.client.mixin;

import dev.realworldmc.client.StreetShortcut;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class StreetShortcutMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void realworldmc$streetShortcut(long window,int key,int scan,int action,int modifiers,CallbackInfo ci) {
        if(window==net.minecraft.client.Minecraft.getInstance().getWindow().getWindow() && StreetShortcut.press(key,scan,action))ci.cancel();
    }
}
