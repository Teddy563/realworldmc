package dev.realworldmc.client.mixin;

import dev.realworldmc.client.UpdateChecker;
import dev.realworldmc.client.UpdateScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void realworldmc$addButtons(CallbackInfo ci) {
        int btnWidth = 140;
        int btnHeight = 20;
        int x = width - btnWidth - 10;
        int y = 10;

        addRenderableWidget(Button.builder(
            Component.literal("§bRealWorldMC ⚙"),
            b -> {
                if (minecraft != null) {
                    minecraft.setScreen(new dev.realworldmc.client.EarthSettingsScreen(this));
                }
            }
        ).bounds(x, y, btnWidth, btnHeight).build());

        if (UpdateChecker.hasUpdate()) {
            var release = UpdateChecker.getAvailableUpdate();
            String label = "RealWorldMC v" + release.version() + " ★";
            addRenderableWidget(Button.builder(
                Component.literal("§a" + label),
                b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new UpdateScreen(this, release));
                    }
                }
            ).bounds(x, y + 24, btnWidth, btnHeight).build());
        }
    }
}
