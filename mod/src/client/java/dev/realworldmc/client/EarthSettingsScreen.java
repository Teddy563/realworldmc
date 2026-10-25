package dev.realworldmc.client;

import dev.realworldmc.RWTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class EarthSettingsScreen extends Screen {
    private final Screen parent;
    private int panelWidth, panelHeight, left, top;

    private int fineRadiusIdx;
    private int farIdx;
    private int maxTilesIdx;
    private int maxSseIdx;

    private Button fineRadiusBtn;
    private Button farBtn;
    private Button maxTilesBtn;
    private Button maxSseBtn;
    private String statusMsg = "";

    public EarthSettingsScreen(Screen parent) {
        super(Component.translatable("gui.realworldmc.settings.title"));
        this.parent = parent;
        EarthSettings.ensureLoaded();
        initIndices();
    }

    public static void register() {
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String c = command.strip().toLowerCase(java.util.Locale.ROOT);
            if (c.equals("earth settings") || c.equals("rw settings")) {
                var mc = Minecraft.getInstance();
                mc.execute(() -> mc.setScreen(new EarthSettingsScreen(mc.screen)));
                return false;
            }
            return true;
        });
    }

    private void initIndices() {
        fineRadiusIdx = findClosestIndex(EarthSettings.FINE_RADIUS_PRESETS, EarthSettings.getFineRadius());
        farIdx = findClosestIndex(EarthSettings.FAR_PRESETS, EarthSettings.getFar());
        maxTilesIdx = findClosestIndex(EarthSettings.MAX_TILES_PRESETS, EarthSettings.getMaxTiles());
        maxSseIdx = findClosestIndex(EarthSettings.MAX_SSE_PRESETS, EarthSettings.getMaxSse());
    }

    private static int findClosestIndex(int[] array, int val) {
        int best = 0;
        int diff = Math.abs(array[0] - val);
        for (int i = 1; i < array.length; i++) {
            int d = Math.abs(array[i] - val);
            if (d < diff) {
                diff = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(440, width - 24);
        panelHeight = Math.min(290, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;

        int rowY = top + 42;
        int btnW = 200;
        int btnX = left + panelWidth - btnW - 16;

        // 1. Fine Radius
        fineRadiusBtn = addRenderableWidget(Button.builder(
                getFineRadiusText(),
                b -> {
                    fineRadiusIdx = (fineRadiusIdx + 1) % EarthSettings.FINE_RADIUS_PRESETS.length;
                    b.setMessage(getFineRadiusText());
                    statusMsg = "";
                }
        ).bounds(btnX, rowY, btnW, 20).build());

        // 2. Far distance
        rowY += 32;
        farBtn = addRenderableWidget(Button.builder(
                getFarText(),
                b -> {
                    farIdx = (farIdx + 1) % EarthSettings.FAR_PRESETS.length;
                    b.setMessage(getFarText());
                    statusMsg = "";
                }
        ).bounds(btnX, rowY, btnW, 20).build());

        // 3. Max tiles
        rowY += 32;
        maxTilesBtn = addRenderableWidget(Button.builder(
                getMaxTilesText(),
                b -> {
                    maxTilesIdx = (maxTilesIdx + 1) % EarthSettings.MAX_TILES_PRESETS.length;
                    b.setMessage(getMaxTilesText());
                    statusMsg = "";
                }
        ).bounds(btnX, rowY, btnW, 20).build());

        // 4. Max SSE / Quality
        rowY += 32;
        maxSseBtn = addRenderableWidget(Button.builder(
                getMaxSseText(),
                b -> {
                    maxSseIdx = (maxSseIdx + 1) % EarthSettings.MAX_SSE_PRESETS.length;
                    b.setMessage(getMaxSseText());
                    statusMsg = "";
                }
        ).bounds(btnX, rowY, btnW, 20).build());

        // Actions
        int bottomY = top + panelHeight - 32;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.realworldmc.settings.save"),
                b -> applyAndSave()
        ).bounds(left + 16, bottomY, 130, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.realworldmc.settings.defaults"),
                b -> resetDefaults()
        ).bounds(left + 154, bottomY, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.realworldmc.update.close"),
                b -> onClose()
        ).bounds(left + panelWidth - 96, bottomY, 80, 20).build());
    }

    private Component getFineRadiusText() {
        int val = EarthSettings.FINE_RADIUS_PRESETS[fineRadiusIdx];
        int chunks = val / 16;
        String note = (val == EarthSettings.DEFAULT_FINE_RADIUS) ? " (Domyślne)" : "";
        return Component.literal(val + " m (" + chunks + " ch.)" + note);
    }

    private Component getFarText() {
        int val = EarthSettings.FAR_PRESETS[farIdx];
        int km = val / 1000;
        int chunks = val / 16;
        String note = (val == EarthSettings.DEFAULT_FAR) ? " (Domyślne)" : "";
        return Component.literal(km + " km (" + chunks + " ch.)" + note);
    }

    private Component getMaxTilesText() {
        int val = EarthSettings.MAX_TILES_PRESETS[maxTilesIdx];
        String note = (val == EarthSettings.DEFAULT_MAX_TILES) ? " (Domyślny)" : (val >= 1600 ? " (Dla M4)" : "");
        return Component.literal(val + " kafli" + note);
    }

    private Component getMaxSseText() {
        int val = EarthSettings.MAX_SSE_PRESETS[maxSseIdx];
        String label = switch (val) {
            case 18 -> "Wydajność (18)";
            case 12 -> "Standard (12)";
            case 8 -> "Wysoka jakość (8)";
            case 4 -> "Ultra / Ostra (4)";
            default -> String.valueOf(val);
        };
        return Component.literal(label);
    }

    private void applyAndSave() {
        EarthSettings.setFineRadius(EarthSettings.FINE_RADIUS_PRESETS[fineRadiusIdx]);
        EarthSettings.setFar(EarthSettings.FAR_PRESETS[farIdx]);
        EarthSettings.setMaxTiles(EarthSettings.MAX_TILES_PRESETS[maxTilesIdx]);
        EarthSettings.setMaxSse(EarthSettings.MAX_SSE_PRESETS[maxSseIdx]);
        EarthSettings.save();
        statusMsg = "§aZapisano i zastosowano na żywo!";
    }

    private void resetDefaults() {
        EarthSettings.resetDefaults();
        initIndices();
        if (fineRadiusBtn != null) fineRadiusBtn.setMessage(getFineRadiusText());
        if (farBtn != null) farBtn.setMessage(getFarText());
        if (maxTilesBtn != null) maxTilesBtn.setMessage(getMaxTilesText());
        if (maxSseBtn != null) maxSseBtn.setMessage(getMaxSseText());
        statusMsg = "§ePrzywrócono ustawienia domyślne.";
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xC0000000);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xFF1E2224);
        g.fill(left, top, left + 3, top + panelHeight, RWTheme.BRAND);

        // Heading
        g.drawString(font, Component.translatable("gui.realworldmc.settings.heading"), left + 14, top + 12, RWTheme.GREEN, false);
        g.drawString(font, Component.translatable("gui.realworldmc.settings.subheading"), left + 14, top + 24, RWTheme.MUTED, false);

        // Labels
        int labelX = left + 16;
        int rowY = top + 47;
        g.drawString(font, Component.translatable("gui.realworldmc.settings.fine_radius"), labelX, rowY, 0xEEEEEE, false);
        rowY += 32;
        g.drawString(font, Component.translatable("gui.realworldmc.settings.far"), labelX, rowY, 0xEEEEEE, false);
        rowY += 32;
        g.drawString(font, Component.translatable("gui.realworldmc.settings.max_tiles"), labelX, rowY, 0xEEEEEE, false);
        rowY += 32;
        g.drawString(font, Component.translatable("gui.realworldmc.settings.max_sse"), labelX, rowY, 0xEEEEEE, false);

        // Status message
        if (!statusMsg.isEmpty()) {
            g.drawString(font, statusMsg, left + 16, top + panelHeight - 48, 0xFFFFFF, false);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
