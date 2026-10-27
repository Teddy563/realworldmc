package dev.realworldmc.mixin;

import dev.realworldmc.FlightBoost;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FireworkRocketItem.class)
public class FlightRocketItemMixin {
    // Reuse vanilla rocket spawning, duration, item consumption and statistics.
    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isFallFlying()Z"))
    private boolean realworldmc$poweredFlight(Player player) {
        return FlightBoost.active(player) || player.isFallFlying();
    }
}
