package dev.realworldmc.mixin;

import dev.realworldmc.FlightBoost;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FireworkRocketEntity.class)
public class FlightRocketMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isFallFlying()Z"))
    private boolean realworldmc$attachedFlight(LivingEntity player) {
        return FlightBoost.active(player) || player.isFallFlying();
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void realworldmc$boost(LivingEntity player, Vec3 vanillaVelocity) {
        // Vanilla's 1.5-block/tick target would slow our faster cruise and move an orbital anchor.
        if (FlightBoost.active(player)) FlightBoost.rocket(player);
        else player.setDeltaMovement(vanillaVelocity);
    }
}
