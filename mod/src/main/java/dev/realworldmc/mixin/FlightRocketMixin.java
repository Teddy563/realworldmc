package dev.realworldmc.mixin;

import dev.realworldmc.FlightBoost;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(FireworkRocketEntity.class)
public class FlightRocketMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isFallFlying()Z"))
    private boolean realworldmc$attachedFlight(LivingEntity player, Operation<Boolean> original) {
        return FlightBoost.active(player) || original.call(player);
    }
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void realworldmc$boost(LivingEntity player, Vec3 vanillaVelocity, Operation<Void> original) {
        // Vanilla's 1.5-block/tick target would slow our faster cruise and move an orbital anchor.
        if (FlightBoost.active(player)) FlightBoost.rocket(player);
        else original.call(player, vanillaVelocity);
    }
}
