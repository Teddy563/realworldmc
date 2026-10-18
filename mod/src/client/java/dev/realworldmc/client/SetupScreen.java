package dev.realworldmc.client;

import dev.realworldmc.RWTheme;

import dev.realworldmc.EarthConfig;
import dev.realworldmc.SetupCredentials;
import dev.realworldmc.ServiceClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/** First launch wizard; also available from the title screen's Map setup button. */
public final class SetupScreen extends Screen {
    private final Screen parent;
    private int page;
    private boolean cesium = true, consent, busy, passed, saved, water = true;
    private final String directory = ServiceClient.directory().toString();
    private String key = "", streetKey = "", message = "";
    private EditBox mapField, streetField;
    private static boolean prompted;
    private static boolean openRequested;
    private static final String GUIDE = "https://github.com/Teddy563/realworldmc/blob/main/docs/accounts-and-costs.md";
    public SetupScreen(Screen parent) { super(Component.literal("RealWorldMC setup")); this.parent = parent; }
    public static void register() {
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (command.strip().matches("(?:rw|earth)\\s+setup")) {
                openRequested = true;
                return false;
            }
            return true;
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen) Screens.getButtons(screen).add(Button.builder(Component.literal("RealWorldMC setup"), b -> client.setScreen(new SetupScreen(screen))).bounds(width - 144, 6, 138, 20).build());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openRequested) {
                openRequested = false;
                client.setScreen(new SetupScreen(null));
            }
            if (!prompted && client.screen instanceof TitleScreen && client.getOverlay() == null) {
                prompted = true;
                if (!EarthConfig.get().setupComplete) client.setScreen(new SetupScreen(client.screen));
            }
        });
    }
    private Button button(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> action.run()).bounds(x, y, w, 20).build());
    }
    @Override protected void init() {
        int x = width / 2 - 150;
        if (page == 0) {
            button("Water settings", x, 86, 300, () -> {
                try {
                    var config = SetupCredentials.read(Path.of(directory).resolve("config.json"));
                    water = !config.has("water") || config.get("water").getAsBoolean();
                    page = 3; message = ""; rebuildWidgets();
                } catch (Exception ignored) { message = "Could not read water settings."; }
            });
            button(consent ? "Costs understood: yes" : "I understand usage may cost money", x, height - 79, 300, () -> { consent = !consent; rebuildWidgets(); });
            button("Next: accounts", x, height - 55, 146, () -> {
                try {
                    Path dir = Path.of(directory);
                    var config = SetupCredentials.read(dir.resolve("config.json"));
                    key = SetupCredentials.value(config, "cesiumToken"); cesium = !key.isBlank();
                    if (!cesium) key = SetupCredentials.value(config, "apiKey");
                    if (key.isBlank()) cesium = true;
                    var photos = SetupCredentials.read(dir.resolve("streetview.config.json"));
                    streetKey = SetupCredentials.value(photos, "tilesKey");
                    if (!photos.has("tilesKey")) streetKey = SetupCredentials.value(photos, "browserKey");
                    if (streetKey.startsWith("YOUR_")) streetKey = "";
                    page = 1; message = ""; rebuildWidgets();
                } catch (Exception ignored) { message = "Cannot read this folder or config. See the guide; files were not changed."; }
            }).active = consent;
        } else if (page == 3 && !saved) {
            button("OSM water generation: " + (water ? "ON" : "OFF"), x, 42, 300, () -> { water = !water; rebuildWidgets(); }).active = !busy;
            button("Save water setting", x, height - 79, 300, () -> {
                try {
                    SetupCredentials.saveWater(Path.of(directory), water);
                    saved = true; startServices(true);
                } catch (Exception ignored) { message = "Could not save water settings."; }
            }).active = !busy && minecraft.level == null;
            button("Back", x, height - 55, 146, () -> { page = 0; message = ""; rebuildWidgets(); }).active = !busy;
        } else if (page == 1 && !saved) {
            button(cesium ? "Map provider: Cesium ion" : "Map provider: Google direct", x, 42, 300, () -> {
                capture(); cesium = !cesium; key = ""; passed = false; message = ""; rebuildWidgets();
            }).active = !busy;
            mapField = secret(x, 88, cesium ? "Cesium token" : "Google Map Tiles key", key);
            streetField = secret(x, 131, "Optional Google Street View Map Tiles key", streetKey);
            button(busy ? "Testing..." : "Test connection", x, height - 79, 146, this::test).active = consent && !busy;
            button("Back", x, height - 55, 146, () -> { capture(); page = 0; message = ""; rebuildWidgets(); }).active = !busy;
        } else if (!saved) {
            button("Save & finish", x, height - 79, 300, this::save).active = passed && !busy;
            button("Edit / retry", x, height - 55, 146, () -> { page = 1; passed = false; message = ""; rebuildWidgets(); }).active = !busy;
        }
        if (saved && !busy && !ServiceClient.startupError().isEmpty()) button("Retry local services", x, height - 79, 300, () -> startServices(true));
        button("Setup & costs guide", x + 154, height - 55, 146, () -> net.minecraft.Util.getPlatform().openUri(GUIDE));
        button(saved ? "Done" : "Set up later", x, height - 29, 300, this::onClose).active = !busy;
    }
    private EditBox secret(int x, int y, String label, String value) {
        var field = new EditBox(font, x, y, 300, 20, Component.literal(label)) {
            @Override protected net.minecraft.network.chat.MutableComponent createNarrationMessage() { return Component.literal(label + ": hidden"); }
        };
        field.setMaxLength(4096); field.setValue(value);
        field.setFormatter((text, start) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        field.setResponder(text -> { passed = false; });
        field.setEditable(!busy); addRenderableWidget(field); return field;
    }
    private void capture() { if (mapField != null) { key = mapField.getValue().trim(); streetKey = streetField.getValue().trim(); } }
    private void test() {
        capture(); passed = false; busy = true; page = 2; message = "Testing only on provider servers. This may count toward usage."; rebuildWidgets();
        final boolean provider = cesium; final String mapKey = key, photoKey = streetKey;
        CompletableFuture.supplyAsync(() -> {
            try {
                var config = SetupCredentials.read(Path.of(directory).resolve("config.json"));
                long asset = config.has("cesiumAsset") ? config.get("cesiumAsset").getAsLong() : 2275207L;
                return SetupCredentials.test(provider, mapKey, photoKey, asset, SetupCredentials.NETWORK);
            } catch (Exception ignored) { return new SetupCredentials.Result(false, "Could not read map configuration. Check the guide."); }
        }).thenAccept(result -> minecraft.execute(() -> { busy = false; passed = result.ok(); message = result.message(); if (minecraft.screen == this) rebuildWidgets(); }));
    }
    private void save() {
        // Fields can change after the last render; never save credentials that were not tested.
        if (!passed || busy || !consent) { message = "Test these credentials before saving."; return; }
        try {
            var result = SetupCredentials.save(Path.of(directory), cesium, key, streetKey);
            var config = EarthConfig.get(); config.setupComplete = true; config.save();
            saved = true;
            key = ""; streetKey = ""; mapField = null; streetField = null;
            startServices(result.mapChanged() || result.photoChanged());
        } catch (Exception ignored) { message = "Could not save. Check folder permissions; setup is not complete."; }
    }
    private void startServices(boolean restart) {
        busy = true; message = "Starting your local map and photo services..."; rebuildWidgets();
        CompletableFuture.supplyAsync(() -> {
            if (restart) ServiceClient.restartManaged(); else ServiceClient.ensureRunning();
            return ServiceClient.startupError();
        }).thenAccept(error -> minecraft.execute(() -> {
            busy = false;
            message = error.isEmpty() ? "Saved. Map and photo services are ready. " + (minecraft.level == null ? "Create or open a Real World world." : "Reopen your world to refresh its map connections. No Minecraft restart needed.") : "Keys saved. " + error;
            if (minecraft.screen == this) rebuildWidgets();
        }));
    }
    @Override public void onClose() { if (!busy) minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0,0,width,height,RWTheme.BACKGROUND);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        int x = width / 2 - 150;
        g.drawString(font, title, (width-font.width(title))/2, 15, RWTheme.GREEN, false);
        if (page == 0) {
            if (message.isBlank()) g.drawWordWrap(font, Component.literal("RealWorldMC starts its bundled map service. Add and test your keys here; no scripts or separate Node installation needed on Windows x64."), x, 39, 300, 0xFFFFFFFF);

            g.drawWordWrap(font, Component.literal("Free allowance = monthly requests. Extra use can cost money. Alerts-only budgets do not stop charges. Set quotas; read the costs guide."), x, 114, 300, 0xFFFFFFAA);
        } else if (page == 1 && !saved) {
            g.drawString(font, cesium ? "Cesium token (3D Tiles asset access)" : "Google key (Map Tiles API)", x, 75, 0xFFFFFFFF);
            g.drawString(font, "Street View key (optional; Google Map Tiles API)", x, 118, 0xFFFFFFFF);
        }
        if (page == 3 && !saved && message.isBlank()) g.drawWordWrap(font, Component.literal(
            "Adds Minecraft water blocks using OpenStreetMap. OFF skips OSM requests. Existing chunks stay unchanged. "
            + (minecraft.level == null ? "Failed requests time out quickly and pause before retrying." : "Return to the title screen before saving.")), x, 76, 300, 0xFFFFFFFF);
        if (!message.isBlank()) g.drawWordWrap(font, Component.literal(message), x, page == 0 ? 39 : 65, 300, passed || saved ? RWTheme.GREEN : 0xFFFFFFAA);
    }
}
