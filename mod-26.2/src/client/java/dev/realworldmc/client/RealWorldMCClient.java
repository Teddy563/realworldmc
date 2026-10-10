package dev.realworldmc.client;

import dev.realworldmc.RegionsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import dev.realworldmc.RealWorldMC;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;

public class RealWorldMCClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MapRenderer renderer = MapRenderer.INSTANCE;
		ClientPlayNetworking.registerGlobalReceiver(RegionsPayload.TYPE, (payload, context) -> context.client().execute(() -> renderer.setRegions(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(renderer::clear));
		ClientTickEvents.END_CLIENT_TICK.register(renderer::tick);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> renderer.onChunkLoaded(chunk.getPos()));
		LevelExtractionEvents.END_EXTRACTION.register(renderer::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(renderer::render);

		HudElementRegistry.addLast(RealWorldMC.id("attribution"), (graphics, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (!renderer.enabled()) return; // attribution stays visible even with F1 (Google requirement)
			var font = mc.font;
			int h = graphics.guiHeight();
			// Attribution required by the Google Map Tiles API terms.
			var parts = new java.util.ArrayList<>(renderer.attribution);
			if (!parts.contains("Google")) parts.add(0, "Google");
			if (!parts.contains("RealWorldMC")) parts.add("RealWorldMC");
			String attr = String.join(", ", parts);
			int color = dev.realworldmc.CreditFade.HUD.color(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen);
			graphics.text(font, attr, 4, h - 12, color, true);
			if (!renderer.status.isEmpty()) graphics.text(font, renderer.status, 4, 4, 0xFFFF5555, true);
		});
	}
}
