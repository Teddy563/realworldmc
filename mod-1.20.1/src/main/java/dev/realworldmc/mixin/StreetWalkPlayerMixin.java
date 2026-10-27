package dev.realworldmc.mixin;

import dev.realworldmc.StreetWalkMotion;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class StreetWalkPlayerMixin {
    @Inject(method="tick",at=@At("RETURN"))
    private void realworldmc$photoCollision(CallbackInfo ci) {
        Player player=(Player)(Object)this;
        if(StreetWalkMotion.active(player)) {
            player.noPhysics=true;player.setOnGround(true);
            var velocity=player.getDeltaMovement();player.setDeltaMovement(velocity.x,0,velocity.z);
        }
    }
}
