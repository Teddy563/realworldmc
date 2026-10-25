package dev.realworldmc.client;

import dev.realworldmc.RegionsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;

public class RealWorldMCClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        SetupScreen.register();
        HelpScreen.register();
        UpdateScreen.register();
        EarthSettingsScreen.register();
        UpdateChecker.checkAsync();
        ClientTickEvents.END_CLIENT_TICK.register(dev.realworldmc.client.map.OsmTileCache::tick);

        CreativeFlight.register();
        SpaceFlight.register();
        ClientTickEvents.END_CLIENT_TICK.register(SpaceDestinations::tick);
        ActionBar.register();
        ClientTickEvents.END_CLIENT_TICK.register(ApiUsageHud::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->onClientThread(client,ApiUsageHud::clear));
		MapRenderer renderer = MapRenderer.INSTANCE;
        PanoramaRenderer panorama=PanoramaRenderer.INSTANCE;
        panorama.registerKeys();
        dev.realworldmc.StreetWalkMotion.clientWalking = p -> p == Minecraft.getInstance().player && panorama.active() && panorama.walking();
        ClientPlayNetworking.registerGlobalReceiver(dev.realworldmc.PanoramaPayload.TYPE, (payload, context) -> context.client().execute(() -> panorama.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(dev.realworldmc.MapEditsPayload.TYPE,(payload,context)->context.client().execute(()->MapDamage.receive(payload)));
        WorldRenderEvents.AFTER_ENTITIES.register(MapDamage::render);
        ClientPlayNetworking.registerGlobalReceiver(dev.realworldmc.StreetEditsPayload.TYPE,(payload,context)->context.client().execute(()->PhotoEditsRenderer.INSTANCE.receive(payload)));
        // Draw the photo after chunk depth, before entities. A shader sky can no longer replace it.
        net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry.registerSkyRenderer(dev.realworldmc.PanoramaRoom.ROOM,context -> {});
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {panorama.renderSky(context);PhotoEditsRenderer.INSTANCE.render(context);});
        net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry.registerCloudRenderer(dev.realworldmc.PanoramaRoom.ROOM,context -> {});
        net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry.registerWeatherRenderer(dev.realworldmc.PanoramaRoom.ROOM,context -> {});
        ClientTickEvents.END_CLIENT_TICK.register(panorama::tick);
        ClientTickEvents.END_CLIENT_TICK.register(StreetExitTransition::tick);
        ClientTickEvents.END_CLIENT_TICK.register(DistantHorizonsCompat::update);
        WorldRenderEvents.START.register(context -> DistantHorizonsCompat.update(Minecraft.getInstance()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> onClientThread(client, () -> {panorama.clear();StreetExitTransition.clear();DistantHorizonsCompat.release();}));
        HudRenderCallback.EVENT.register((graphics,delta) -> panorama.hud(graphics));
        HudRenderCallback.EVENT.register((graphics,delta) -> StreetExitTransition.hud(graphics));
		ClientPlayNetworking.registerGlobalReceiver(RegionsPayload.TYPE, (payload, context) -> context.client().execute(() -> renderer.setRegions(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onClientThread(client, () -> { renderer.clear(); MapDamage.clear(); dev.realworldmc.client.map.OsmTileCache.clear(); }));
		ClientTickEvents.END_CLIENT_TICK.register(renderer::tick);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> renderer.onChunkLoaded(chunk.getPos()));
		WorldRenderEvents.AFTER_ENTITIES.register(renderer::render);
        WorldRenderEvents.BEFORE_ENTITIES.register(renderer::renderSpace);

		HudRenderCallback.EVENT.register((graphics, delta) -> {
			Minecraft mc = Minecraft.getInstance();
            if (!renderer.enabled() || mc.level == null || !mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return; // attribution stays visible even with F1
			var font = mc.font;
			int h = graphics.guiHeight();
			// Attribution required by the Google Map Tiles API terms.
			String attr = SpaceFlight.body()==CelestialBody.EARTH ? renderer.attributionText : SpaceFlight.body().credits;
			int color = dev.realworldmc.CreditFade.HUD.color(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
			graphics.drawString(font, attr, 4, h - 12, color, true);
            ActionBar.render(graphics,h-25,ActionBar.flightControls());
            SpaceDestinations.hud(graphics);
		});
	}

	// Minecraft.disconnect drops all queued tasks, so cleanup queued with execute() while leaving a world could be lost: the next (even vanilla) world then kept the old places and drew their photo mesh.
	private static void onClientThread(Minecraft client, Runnable task) {
		if (client.isSameThread()) task.run();
		else client.execute(task);
	}
}
