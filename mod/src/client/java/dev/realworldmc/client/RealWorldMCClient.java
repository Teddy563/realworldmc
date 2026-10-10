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
		MapRenderer renderer = MapRenderer.INSTANCE;
        PanoramaRenderer panorama=PanoramaRenderer.INSTANCE;
        panorama.registerKeys();
        ClientPlayNetworking.registerGlobalReceiver(dev.realworldmc.PanoramaPayload.TYPE, (payload, context) -> context.client().execute(() -> panorama.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(dev.realworldmc.StreetEditsPayload.TYPE,(payload,context)->context.client().execute(()->PhotoEditsRenderer.INSTANCE.receive(payload)));
        // Draw the photo after chunk depth, before entities. A shader sky can no longer replace it.
        net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry.registerSkyRenderer(dev.realworldmc.PanoramaRoom.ROOM,context -> {});
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {panorama.renderSky(context);PhotoEditsRenderer.INSTANCE.render(context);});
        net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry.registerCloudRenderer(dev.realworldmc.PanoramaRoom.ROOM,context -> {});
        net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry.registerWeatherRenderer(dev.realworldmc.PanoramaRoom.ROOM,context -> {});
        ClientTickEvents.END_CLIENT_TICK.register(panorama::tick);
        ClientTickEvents.END_CLIENT_TICK.register(DistantHorizonsCompat::update);
        WorldRenderEvents.START.register(context -> DistantHorizonsCompat.update(Minecraft.getInstance()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> client.execute(() -> {panorama.clear();DistantHorizonsCompat.release();}));
        HudRenderCallback.EVENT.register((graphics,delta) -> panorama.hud(graphics));
		ClientPlayNetworking.registerGlobalReceiver(RegionsPayload.TYPE, (payload, context) -> context.client().execute(() -> renderer.setRegions(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(renderer::clear));
		ClientTickEvents.END_CLIENT_TICK.register(renderer::tick);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> renderer.onChunkLoaded(chunk.getPos()));
		WorldRenderEvents.AFTER_ENTITIES.register(renderer::render);

		HudRenderCallback.EVENT.register((graphics, delta) -> {
			Minecraft mc = Minecraft.getInstance();
            if (!renderer.enabled() || mc.level == null || !mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return; // attribution stays visible even with F1
			var font = mc.font;
			int h = graphics.guiHeight();
			// Attribution required by the Google Map Tiles API terms.
			String attr = renderer.attributionText;
			int color = dev.realworldmc.CreditFade.HUD.color(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
			graphics.drawString(font, attr, 4, h - 12, color, true);
		});
	}
}
