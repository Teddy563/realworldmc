package dev.realworldmc.mixin;

import dev.realworldmc.FlightBoost;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(FireworkRocketItem.class)
public class FlightRocketItemMixin {
    // Reuse vanilla rocket spawning, duration, item consumption and statistics.
    @WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isFallFlying()Z"))
    private boolean realworldmc$poweredFlight(Player player, Operation<Boolean> original) {
        return FlightBoost.active(player) || original.call(player);
    }
}
