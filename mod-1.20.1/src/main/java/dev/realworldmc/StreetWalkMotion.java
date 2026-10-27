package dev.realworldmc;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.Predicate;

/** Photo navigation uses horizontal motion, independent of the scanned collision terrain. */
public final class StreetWalkMotion {
    public static Predicate<Player> clientWalking = player -> false;
    public static boolean active(Player player) {
        return player instanceof ServerPlayer server ? PanoramaRoom.walking(server) : clientWalking.test(player);
    }
    private StreetWalkMotion() { }
}
