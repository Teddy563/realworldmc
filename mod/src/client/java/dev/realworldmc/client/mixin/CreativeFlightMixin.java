package dev.realworldmc.client.mixin;

import dev.realworldmc.client.CreativeFlight;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Own travel only while RW flight is enabled, before Axiom replaces vanilla travel.
@Mixin(value = Player.class, priority = 1100)
public class CreativeFlightMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void realworldmc$flight(Vec3 input, CallbackInfo ci) {
        if (CreativeFlight.travel((Player)(Object)this)) ci.cancel();
    }
}
