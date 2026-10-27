package dev.realworldmc;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** A local Creative flight session. No remote server can enable this through a packet. */
public final class FlightBoost {
    private record Session(MinecraftServer server, UUID player) {}
    private static volatile Session session;
    private static final AtomicInteger ticks = new AtomicInteger();
    private FlightBoost() {}

    public static void begin(MinecraftServer server, UUID player) {
        clear();
        if (server != null && server.isSingleplayer()) session = new Session(server, player);
    }
    public static void clear() { session = null; ticks.set(0); }
    public static boolean active(LivingEntity entity) {
        Session current = session;
        return current != null && entity instanceof Player player && current.player.equals(player.getUUID())
            && player.isCreative() && player.isAlive() && !player.isPassenger()
            && player.level().dimension() == Level.OVERWORLD
            && (player.level().isClientSide || player.getServer() == current.server);
    }
    public static void rocket(LivingEntity entity) {
        if (active(entity)) ticks.set(4);
    }
    public static void tick() { ticks.updateAndGet(value -> Math.max(0, value - 1)); }
    public static boolean boosted() { return session != null && ticks.get() > 0; }
}
