package dev.realworldmc;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("earthcraft", path);
	}

	@Override
	public void onInitialize() {
		EarthConfig.get();
		EarthBlocks.init();
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("earth"), EarthChunkGenerator.CODEC);
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("streetworld"), StreetChunkGenerator.CODEC);
		PayloadTypeRegistry.playS2C().register(RegionsPayload.TYPE, RegionsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PanoramaPayload.TYPE, PanoramaPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StreetEditsPayload.TYPE, StreetEditsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PanoramaActionPayload.TYPE, PanoramaActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PanoramaActionPayload.TYPE, (payload,context) -> context.server().execute(() -> PanoramaRoom.action(context.player(),payload)));
        ServerTickEvents.END_SERVER_TICK.register(PanoramaRoom::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register(StreetEdits::importChunk);
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive) -> PanoramaRoom.restore(newPlayer));
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> PanoramaRoom.restore(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> PanoramaRoom.disconnected(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> server.getPlayerList().getPlayers().forEach(PanoramaRoom::restore));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PanoramaRoom.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> StreetEdits.clear());

		// Start the tile service early (if configured) so it is up when a world loads.
		Thread starter = new Thread(ServiceClient::ensureRunning, "RealWorldMC service starter");
		starter.setDaemon(true);
		starter.start();

		ServerWorldEvents.LOAD.register((server, level) -> {
			if (level.dimension().equals(Level.OVERWORLD)) PanoramaPlaces.load(server);
			if (level.dimension() == Level.OVERWORLD && level.getChunkSource().getGenerator() instanceof EarthChunkGenerator gen) {
				EarthRegions.load(server, gen);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> { EarthRegions.unload(); TravelLoading.clear(); });
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { syncedVersion.remove(handler.player.getUUID()); TravelLoading.disconnect(handler.player.getUUID()); });
		ServerTickEvents.END_SERVER_TICK.register(RealWorldMC::syncRegions);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> { var root = dispatcher.register(
			Commands.literal("rw")
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> help(ctx.getSource()))
                    .then(Commands.literal("more").executes(ctx -> moreHelp(ctx.getSource()))))
                .then(Commands.literal("setup").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> RWMessages.message("Open RealWorldMC setup on your Minecraft title screen. The updated client opens it with /earth setup. Do not enter keys in chat."), false);
                    return 1;
                }))
                .then(Commands.literal("back").executes(ctx -> PanoramaRoom.exit(ctx.getSource())))
                .then(Commands.literal("moon").executes(ctx -> spaceHelp(ctx.getSource())))
                .then(Commands.literal("mars").executes(ctx -> spaceHelp(ctx.getSource())))
                .then(Commands.literal("earth").executes(ctx -> spaceHelp(ctx.getSource())))
                .then(Commands.literal("actionbar").executes(ctx -> actionBarHelp(ctx.getSource()))
                    .then(Commands.literal("on").executes(ctx -> actionBarHelp(ctx.getSource())))
                    .then(Commands.literal("off").executes(ctx -> actionBarHelp(ctx.getSource()))))
                .then(Commands.literal("space").executes(ctx -> spaceHelp(ctx.getSource()))
                    .then(Commands.literal("on").executes(ctx -> spaceHelp(ctx.getSource())))
                    .then(Commands.literal("off").executes(ctx -> spaceHelp(ctx.getSource())))
                    .then(Commands.literal("return").executes(ctx -> spaceHelp(ctx.getSource()))))
                .then(Commands.literal("streetview")
                    .executes(ctx -> streetView(ctx.getSource(), ""))
                    .then(Commands.literal("here").executes(ctx -> streetView(ctx.getSource(), "")))
                    .then(Commands.literal("browser").executes(ctx -> streetViewBrowser(ctx.getSource())))
                    .then(Commands.argument("coordinates", StringArgumentType.greedyString())
                        .executes(ctx -> streetView(ctx.getSource(), StringArgumentType.getString(ctx, "coordinates"))))
                    .then(Commands.literal("exit").executes(ctx -> PanoramaRoom.exit(ctx.getSource())))
                    .then(Commands.literal("back").executes(ctx -> PanoramaRoom.exit(ctx.getSource())))
                    .then(Commands.literal("legacy").executes(ctx -> PanoramaRoom.legacy(ctx.getSource())))
                    .then(Commands.literal("height").then(Commands.argument("metres", DoubleArgumentType.doubleArg(1,4))
                        .executes(ctx -> PanoramaRoom.cameraHeight(ctx.getSource(),DoubleArgumentType.getDouble(ctx,"metres")))))
                    .then(Commands.literal("setup").executes(ctx -> streetViewSetup(ctx.getSource()))))
				.then(Commands.literal("goto")
					.then(Commands.argument("place", StringArgumentType.greedyString())
						.executes(ctx -> goTo(ctx.getSource(), StringArgumentType.getString(ctx, "place")))))
				.then(Commands.literal("where").executes(ctx -> where(ctx.getSource())))
				.then(Commands.literal("setlocation").requires(src -> src.hasPermission(2))
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
						})))));
            dispatcher.register(Commands.literal("earth").executes(ctx -> help(ctx.getSource())).redirect(root));
        });
		LOGGER.info("RealWorldMC initialized");
	}

	public static EarthChunkGenerator generator(MinecraftServer server) {
		return server.overworld().getChunkSource().getGenerator() instanceof EarthChunkGenerator g ? g : null;
	}


    private static int spaceHelp(CommandSourceStack src) {
        src.sendSuccess(() -> RWMessages.message("Creative flight enters space automatically above 2 km by default. /earth space starts early. W/S cruise, A/D strafe, mouse steers, Space/Shift rise and descend. /earth space return goes back; /earth actionbar toggles information."), false);
        return 1;
    }

    private static int actionBarHelp(CommandSourceStack src) {
        src.sendSuccess(() -> RWMessages.message("The updated client toggles its information with /earth actionbar, /earth actionbar on or /earth actionbar off."), false);
        return 1;
    }

    private static int help(CommandSourceStack src) {
        var help = RWMessages.message("RealWorldMC · click a command to fill chat")
            .append(RWMessages.helpLine("/earth setup", "Add or test your API keys"))
            .append(RWMessages.helpLine("/earth space", "Experimental Earth-to-space filming flight"))
            .append(RWMessages.helpLine("/rw moon", "Moon orbital flyover using NASA imagery"))
            .append(RWMessages.helpLine("/rw mars", "Mars orbital flyover using Viking imagery"))
            .append(RWMessages.helpLine("/rw earth", "Return to your previous Earth orbit"))
            .append(RWMessages.helpLine("/earth actionbar", "Show or hide the bottom-left controls and status"))
            .append(RWMessages.helpLine("/earth goto <place or lat, lon>", "Travel to a real place"))
            .append(RWMessages.helpLine("/earth where", "Your coordinates and map link"))
            .append(RWMessages.helpLine("/earth streetview", "Nearest photo within 500 metres"))
            .append(RWMessages.helpLine("/earth streetview <lat, lon>", "Photo at chosen coordinates"))
            .append(RWMessages.helpLine("/earth back", "Return from Street View"))
            .append(RWMessages.helpLine("/earth help more", "Camera height, browser and world settings"))
            .append(RWMessages.text("\n  F5: camera · Arrows / W: photo travel · V: nearby walk\n  F7: credits · F8: report photo · /rw works as an alias."));
        src.sendSuccess(() -> help, false);
        return 1;
    }

    private static int moreHelp(CommandSourceStack src) {
        var help = RWMessages.message("More commands")
            .append(RWMessages.helpLine("/earth streetview height <1-4>", "Photo eye height in metres"))
            .append(RWMessages.helpLine("/earth streetview browser", "Open the browser viewer"))
            .append(RWMessages.helpLine("/earth streetview setup", "Photo setup help"))
            .append(RWMessages.helpLine("/earth streetview legacy", "Previous flat sandbox"))
            .append(RWMessages.helpLine("/earth setlocation <lat> <lon>", "Fixed start for NEW worlds; operator only"))
            .append(RWMessages.helpLine("/earth setlocation random", "Random start for NEW worlds; operator only"))
            .append(RWMessages.text("\n  Mine and place normally. Photo walls have no measured depth.\n  F5 and nearby walking use an approximate ground projection."));
        src.sendSuccess(() -> help, false);
        return 1;
    }

    private static int streetViewSetup(CommandSourceStack src) {
        src.sendSuccess(() -> RWMessages.message("Street View setup (on YOUR computer):\n1. Use /earth setup to add and test your optional Google Map Tiles Street View key.\n2. Save to restart local services automatically. Never paste keys into chat.\n3. The optional browser companion uses its own browserKey in realworldmc-service/streetview.config.json (Maps JavaScript API).\n4. Use /earth streetview at your Earth position, or supply <latitude>, <longitude>. Use /earth streetview browser for the browser companion.\nSee STREET-VIEW.md for Maps JavaScript API versus Map Tiles API setup. Native photos require Map Tiles access."), false);
        return 1;
    }

    private static int streetView(CommandSourceStack src, String coordinates) {
        if (coordinates.isBlank()) {
            if (src.getPlayer()==null) {
                src.sendFailure(RWMessages.message("Run /earth streetview as a player in an Earth location."));return 0;
            }
            double[] location=currentCoordinates(src);
            if(location==null){
                src.sendFailure(RWMessages.message("This world position has no Earth coordinates. Use /earth streetview <latitude>, <longitude>, or visit a RealWorldMC Earth location first."));return 0;
            }
            return PanoramaRoom.enter(src,location[0],location[1],500);
        }
        if (!coordinates.isBlank()) {
            try {
                String[] parts = coordinates.trim().split("(?:\\s*,\\s*|\\s+)", -1);
                if (parts.length != 2) throw new IllegalArgumentException();
                double lat = Double.parseDouble(parts[0]), lon = Double.parseDouble(parts[1]);
                if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180)
                    throw new IllegalArgumentException();
                return PanoramaRoom.enter(src, lat, lon);
            } catch (IllegalArgumentException e) {
                src.sendFailure(RWMessages.message("Use /earth streetview <latitude>, <longitude> (latitude -90 to 90, longitude -180 to 180)."));
                return 0;
            }
        }
        return 0;
    }

    private static int streetViewBrowser(CommandSourceStack src) {
        final String link = ServiceClient.photoBase() + "/";
        src.sendSuccess(() -> RWMessages.message("Street photos: ").append(
            Component.literal("[Open 360-degree viewer]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withUnderlined(true).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, link))))
            .append(Component.literal("\nThe imported pack starts the photo service automatically. Need a key? /earth streetview setup. This opens a browser; your Minecraft player stays in the world.")), false);
        return 1;
    }

	private static int where(CommandSourceStack src) {
		double[] g=currentCoordinates(src);
		if(g==null){src.sendFailure(RWMessages.message("This is not a RealWorldMC place."));return 0;}
		String link = String.format(Locale.ROOT, "https://www.google.com/maps/@%.6f,%.6f,19z", g[0], g[1]);
		src.sendSuccess(() -> RWMessages.message(String.format(Locale.ROOT, "You are at %.6f, %.6f  ", g[0], g[1]))
			.append(Component.literal(link).withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
				.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, link)))), false);
		return 1;
	}

    /** Both /earth where and the no-coordinate photo command use the same saved geographic origin. */
    static double[] currentCoordinates(CommandSourceStack src) {
		EarthChunkGenerator gen = generator(src.getServer());
		var p = src.getPosition();
		boolean street = src.getLevel().dimension().equals(PanoramaRoom.ROOM);
		EarthRegions.Region r = street ? PanoramaPlaces.at(p.x,p.z) : gen != null && src.getLevel().dimension().equals(Level.OVERWORLD) ? gen.regionFor(p.x,p.z) : null;
		if (r == null || (!street && !Double.isFinite(r.groundHeight())))return null;
        return coordinatesInRegion(r,p);
    }

    static double[] coordinatesInRegion(EarthRegions.Region region,net.minecraft.world.phys.Vec3 position) {
        double[] g=Geo.mcToGeodetic(region.latitude(),region.longitude(),Double.isFinite(region.groundHeight())?region.groundHeight():0,
            position.x-region.offsetX(),position.y,position.z-region.offsetZ());
        return StreetNavigation.coordinates(g[0],g[1])?g:null;
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
					if (PanoramaRoom.isRoom(player.level())) PanoramaRoom.restore(player);
					if (PanoramaRoom.isRoom(player.level())) return;
					if (y <= level.getMinBuildHeight()) {
						player.sendSystemMessage(RWMessages.message("No collision ground at that destination. Choose a nearby covered place with /earth goto."));
						return;
					}
					player.teleportTo(level, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
					player.displayClientMessage(RWMessages.message("Arrived · " + address), true);
                    } catch (Exception e) {
                        src.sendFailure(RWMessages.message("Destination could not load. Try another place with /earth goto."));
                        LOGGER.warn("RealWorldMC destination preparation failed: {}", e.getClass().getSimpleName());
                    } finally { loading.close(); }
				});
			} catch (Exception e) {
				server.execute(() -> {
                    if (!loading.active()) return;
                    loading.close();
                    src.sendFailure(RWMessages.message("Could not load that place. Check /earth setup, then retry /earth goto."));
                });
                LOGGER.warn("RealWorldMC destination lookup failed: {}", e.getClass().getSimpleName());
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
