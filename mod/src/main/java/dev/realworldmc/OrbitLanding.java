package dev.realworldmc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;

/** Re-enter the real region below an orbital view before resuming normal Creative flight. */
public final class OrbitLanding {
    public record Arrival(EarthRegions.Region region, Vec3 position, TravelLoading loading) {}
    private OrbitLanding() {}
    public static void begin(ServerPlayer player, double[] location, float yaw, float pitch,
                             BooleanSupplier valid, BiConsumer<Arrival,Throwable> finished) {
        var server = player.getServer();
        var loading = TravelLoading.start(player.createCommandSourceStack());
        if (loading == null) { finished.accept(null,new IllegalStateException("Another destination is loading")); return; }
        loading.stage("Preparing descent", 1);
        CompletableFuture.runAsync(() -> {
            try {
                EarthRegions.Region nearest = null;
                double distance = EarthRegions.REUSE_RADIUS;
                for (var region : EarthRegions.all()) {
                    if (!Double.isFinite(region.groundHeight())) continue;
                    var local = Geo.geodeticToMc(region.latitude(), region.longitude(), region.groundHeight(), location[0], location[1], region.groundHeight());
                    // Include vertical curvature: the antipode must never reuse the starting cell.
                    double d = Math.sqrt(local[0]*local[0] + local[2]*local[2] + Math.pow(local[1]-64, 2));
                    if (d < distance) { nearest = region; distance = d; }
                }
                var existing = nearest;
                double ground = existing == null ? ServiceClient.getJson(String.format(Locale.ROOT,
                    "/ground?lat=%.8f&lon=%.8f", location[0], location[1]), Duration.ofSeconds(90)).get("height").getAsDouble() : existing.groundHeight();
                if (!Double.isFinite(ground)) throw new IllegalStateException("Destination height unavailable");
                server.execute(() -> {
                    try {
                        if (!loading.active() || !valid.getAsBoolean() || player.isRemoved() || player.level() != server.overworld()) { loading.close(); finished.accept(null,new IllegalStateException("Descent cancelled")); return; }
                        loading.stage("Loading arrival terrain", 2);
                        var region = existing != null ? existing : EarthRegions.add(location[0], location[1], ground);
                        var local = Geo.geodeticToMc(region.latitude(), region.longitude(), region.groundHeight(), location[0], location[1], Math.max(location[2], ground + 400));
                        // Arrival stays above collision terrain; regular descent loads the chunks below.
                        loading.stage("Resuming flight", 3);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new RegionsPayload(EarthRegions.all()));
                        player.teleportTo(server.overworld(), region.offsetX()+local[0], local[1], region.offsetZ()+local[2], yaw, pitch);
                        player.setDeltaMovement(Vec3.ZERO);
                        finished.accept(new Arrival(region,player.position(),loading),null);
                    } catch (Throwable failure) { loading.close(); finished.accept(null,failure); }
                });
            } catch (Throwable failure) {
                server.execute(() -> { loading.close(); if (valid.getAsBoolean()) finished.accept(null,failure); });
            }
        });
    }
}
