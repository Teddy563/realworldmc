package dev.realworldmc;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RealWorldMC implements ModInitializer {
	public static final String MOD_ID = "realworldmc";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Region list version each player has received. */
	private static final Map<UUID, Integer> syncedVersion = new HashMap<>();

	/** Stable saved-world namespace; changing it would orphan existing blocks and dimensions. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("earthcraft", path);
	}

	@Override
	public void onInitialize() {
		EarthConfig.get();
		EarthBlocks.init();
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("earth"), EarthChunkGenerator.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RegionsPayload.TYPE, RegionsPayload.CODEC);

		// Start the tile service early (if configured) so it is up when a world loads.
		Thread starter = new Thread(ServiceClient::ensureRunning, "RealWorldMC service starter");
		starter.setDaemon(true);
		starter.start();

		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD && level.getChunkSource().getGenerator() instanceof EarthChunkGenerator gen) {
				EarthRegions.load(server, gen);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> { EarthRegions.unload(); TravelLoading.clear(); });
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { syncedVersion.remove(handler.player.getUUID()); TravelLoading.disconnect(handler.player.getUUID()); });
		ServerTickEvents.END_SERVER_TICK.register(RealWorldMC::syncRegions);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("earth")
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
                .then(Commands.literal("streetview")
                    .executes(ctx -> streetView(ctx.getSource(), ""))
                    .then(Commands.argument("coordinates", StringArgumentType.greedyString())
                        .executes(ctx -> streetView(ctx.getSource(), StringArgumentType.getString(ctx, "coordinates"))))
                    .then(Commands.literal("setup").executes(ctx -> streetViewSetup(ctx.getSource()))))
				.then(Commands.literal("goto")
					.then(Commands.argument("place", StringArgumentType.greedyString())
						.executes(ctx -> goTo(ctx.getSource(), StringArgumentType.getString(ctx, "place")))))
				.then(Commands.literal("where").executes(ctx -> where(ctx.getSource())))
				.then(Commands.literal("setlocation").requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.literal("random").executes(ctx -> {
                        EarthConfig cfg = EarthConfig.get();
                        cfg.randomSpawn = true;
                        cfg.save();
                        ctx.getSource().sendSuccess(() -> RWMessages.message("New Real World worlds now choose randomly from 100 destinations. Existing worlds stay in place."), false);
                        return 1;
                    }))
					.then(Commands.argument("latitude", DoubleArgumentType.doubleArg(-90, 90))
						.then(Commands.argument("longitude", DoubleArgumentType.doubleArg(-180, 180)).executes(ctx -> {
							EarthConfig cfg = EarthConfig.get();
							cfg.latitude = DoubleArgumentType.getDouble(ctx, "latitude");
							cfg.longitude = DoubleArgumentType.getDouble(ctx, "longitude");
                            cfg.randomSpawn = false;
							cfg.save();
							ctx.getSource().sendSuccess(() -> RWMessages.message(String.format(Locale.ROOT,
								"New RealWorldMC worlds now start at %.6f, %.6f. Inside a world, /earth goto is faster.", cfg.latitude, cfg.longitude)), false);
							return 1;
						}))))));
		LOGGER.info("RealWorldMC initialized");
	}

	public static EarthChunkGenerator generator(MinecraftServer server) {
		return server.overworld().getChunkSource().getGenerator() instanceof EarthChunkGenerator g ? g : null;
	}


    private static int help(CommandSourceStack src) {
        var help = RWMessages.message("RealWorldMC · experimental 26.2")
            .append(RWMessages.helpLine("/earth goto <place or lat, lon>", "Travel to a real place"))
            .append(RWMessages.helpLine("/earth where", "Your coordinates and map link"))
            .append(RWMessages.helpLine("/earth streetview", "Open the browser photo viewer"))
            .append(RWMessages.helpLine("/earth streetview setup", "Browser setup help"))
            .append(RWMessages.helpLine("/earth setlocation <lat> <lon>", "Fixed start for NEW worlds; operator only"))
            .append(RWMessages.helpLine("/earth setlocation random", "Random start for NEW worlds; operator only"));
        src.sendSuccess(() -> help, false);
        return 1;
    }

    private static int streetViewSetup(CommandSourceStack src) {
        src.sendSuccess(() -> RWMessages.message("Street View setup (on YOUR computer):\n1. In your launcher instance, copy realworldmc-service/streetview.config.example.json to realworldmc-service/streetview.config.json.\n2. Put your restricted Google Maps JavaScript API browser key in browserKey. Never paste keys into Minecraft chat.\n3. The imported pack starts the photo service automatically.\n4. Use /earth streetview and click the link. Enter coordinates in the browser.\nSee STREET-VIEW.md for API/referrer restrictions. Photos open in your browser, not inside Minecraft."), false);
        return 1;
    }

    private static int streetView(CommandSourceStack src, String coordinates) {
        String fragment = "";
        if (!coordinates.isBlank()) {
            try {
                String[] parts = coordinates.trim().split("(?:\\s*,\\s*|\\s+)", -1);
                if (parts.length != 2) throw new IllegalArgumentException();
                double lat = Double.parseDouble(parts[0]), lon = Double.parseDouble(parts[1]);
                if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180)
                    throw new IllegalArgumentException();
                fragment = "#lat=" + lat + "&lng=" + lon;
            } catch (IllegalArgumentException e) {
                src.sendFailure(RWMessages.message("Use /earth streetview <latitude>, <longitude> (latitude -90 to 90, longitude -180 to 180)."));
                return 0;
            }
        }
        final String link = "http://127.0.0.1:8766/" + fragment;
        src.sendSuccess(() -> RWMessages.message("Street photos: ").append(
            Component.literal("[Open 360-degree viewer]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withUnderlined(true).withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(link)))))
            .append(Component.literal("\nThe imported pack starts the photo service automatically. Need a key? /earth streetview setup. This opens a browser; your Minecraft player stays in the world.")), false);
        return 1;
    }

	private static int where(CommandSourceStack src) {
		EarthChunkGenerator gen = generator(src.getServer());
		var p = src.getPosition();
		EarthRegions.Region r = gen == null ? null : gen.regionFor(p.x, p.z);
		if (r == null || Double.isNaN(r.groundHeight())) {
			src.sendFailure(RWMessages.message("This is not a RealWorldMC place."));
			return 0;
		}
		double[] g = Geo.mcToGeodetic(r.latitude(), r.longitude(), r.groundHeight(), p.x - r.offsetX(), p.y, p.z - r.offsetZ());
		String link = String.format(Locale.ROOT, "https://www.google.com/maps/@%.6f,%.6f,19z", g[0], g[1]);
		src.sendSuccess(() -> RWMessages.message(String.format(Locale.ROOT, "You are at %.6f, %.6f  ", g[0], g[1]))
			.append(Component.literal(link).withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
				.withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(link))))), false);
		return 1;
	}

	/** /earth goto <address | lat, lon>: finds the place, creates a region for it if needed and teleports there. */
	private static int goTo(CommandSourceStack src, String query) {
		ServerPlayer player = src.getPlayer(); // null from the console: only creates the place and reports it
		MinecraftServer server = src.getServer();
		if (generator(server) == null) {
			src.sendFailure(RWMessages.message("This is not a RealWorldMC world. Create a new world with World Type \"Real World\"."));
			return 0;
		}
        TravelLoading loading = TravelLoading.start(src);
        if (loading == null) return 0;
		CompletableFuture.runAsync(() -> {
			try {
				JsonObject geo = ServiceClient.getJson("/geocode?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8), Duration.ofSeconds(20));
				double lat = geo.get("lat").getAsDouble(), lon = geo.get("lon").getAsDouble();
				String address = geo.get("address").getAsString();

				// Reuse a nearby region, otherwise make a new one centered on the target.
				EarthRegions.Region region = null;
				double[] local = null;
				for (EarthRegions.Region r : EarthRegions.all()) {
					if (Double.isNaN(r.groundHeight())) continue;
					double[] l = Geo.geodeticToMc(r.latitude(), r.longitude(), r.groundHeight(), lat, lon, r.groundHeight());
					if (Math.hypot(l[0], l[2]) < EarthRegions.REUSE_RADIUS && (local == null || Math.hypot(l[0], l[2]) < Math.hypot(local[0], local[2]))) {
						region = r;
						local = l;
					}
				}
				EarthRegions.Region existing = region;
				double ground = Double.NaN;
				if (existing == null) {
					server.execute(() -> loading.stage("Loading 3D terrain", 2));
					ground = ServiceClient.getJson(String.format(Locale.ROOT, "/ground?lat=%.8f&lon=%.8f", lat, lon), Duration.ofSeconds(90)).get("height").getAsDouble();
					local = new double[] {0, EarthChunkGenerator.GROUND_Y, 0};
				}
				double newGround = ground;
				double[] target = local;
				server.execute(() -> {
                    if (!loading.active()) return;
                    try {
                    loading.stage("Preparing arrival", 3);
					EarthRegions.Region r = existing != null ? existing : EarthRegions.add(lat, lon, newGround);
					ServerLevel level = server.overworld();
					int x = (int) Math.floor(r.offsetX() + target[0]), z = (int) Math.floor(r.offsetZ() + target[2]);
					level.getChunk(x >> 4, z >> 4); // generate before reading the height
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
					LOGGER.info("goto {} ({}, {}) -> region {} at {} {} {}", address, lat, lon, r.index(), x, y, z);
					if (player == null) {
						src.sendSuccess(() -> RWMessages.message(String.format(Locale.ROOT, "%s: place %d at %d %d %d", address, r.index(), x, y, z)), false);
						return;
					}
					if (player.isRemoved()) return;
					player.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
					player.sendOverlayMessage(RWMessages.message("Arrived · " + address));
                    } catch (Exception e) {
                        src.sendFailure(RWMessages.message("Destination could not load. Try another place with /earth goto."));
                    } finally { loading.close(); }
				});
			} catch (Exception e) {
				server.execute(() -> {
                    if (!loading.active()) return;
                    loading.close();
                    src.sendFailure(RWMessages.message("Could not load that place. Check map setup, then retry /earth goto."));
                });
			}
		});
		return 1;
	}

	private static void syncRegions(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0 || generator(server) == null) return;
		var regions = EarthRegions.all();
		if (regions.isEmpty() || Double.isNaN(regions.get(0).groundHeight())) return;
		int version = EarthRegions.version;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Integer seen = syncedVersion.get(player.getUUID());
			if (seen == null || seen != version) {
				syncedVersion.put(player.getUUID(), version);
				ServerPlayNetworking.send(player, new RegionsPayload(regions));
			}
		}
	}
}
