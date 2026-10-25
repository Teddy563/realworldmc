package dev.realworldmc.client;

import dev.realworldmc.RWTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen displaying update information, changelog, and confirmation to download and install.
 */
public final class UpdateScreen extends Screen {
    private final Screen parent;
    private final UpdateChecker.ReleaseInfo release;
    private int left, top, panelWidth, panelHeight;
    private int scrollOffset = 0;
    private final List<FormattedCharSequence> formattedChangelog = new ArrayList<>();
    private boolean downloading = false;
    private boolean completed = false;
    private String statusText = "";
    private int progressPercent = 0;
    private Button downloadButton;

    public UpdateScreen(Screen parent, UpdateChecker.ReleaseInfo release) {
        super(Component.translatable("gui.realworldmc.update.title"));
        this.parent = parent;
        this.release = release;
    }

    public static void register() {
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String c = command.strip().toLowerCase(java.util.Locale.ROOT);
            if (c.equals("earth update") || c.equals("rw update")) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                mc.execute(() -> {
                    var rel = UpdateChecker.getAvailableUpdate();
                    mc.setScreen(new UpdateScreen(mc.screen, rel));
                });
                return false;
            }
            return true;
        });
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(480, width - 24);
        panelHeight = Math.min(340, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;

        prepareChangelog();

        int buttonY = top + panelHeight - 32;

        downloadButton = addRenderableWidget(Button.builder(
                completed ? Component.translatable("gui.realworldmc.update.ready") : Component.translatable("gui.realworldmc.update.download"),
                b -> startDownload()
        ).bounds(left + 12, buttonY, 150, 20).build());
        downloadButton.active = !downloading && !completed;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.realworldmc.update.close"),
                b -> onClose()
        ).bounds(left + panelWidth - 92, buttonY, 80, 20).build());
    }

    private void prepareChangelog() {
        formattedChangelog.clear();
        if (release == null || release.changelog() == null || release.changelog().isBlank()) {
            formattedChangelog.addAll(font.split(Component.literal("No changelog provided."), panelWidth - 36));
            return;
        }

        String[] rawLines = release.changelog().split("\r?\n");
        int maxTextWidth = panelWidth - 36;

        for (String raw : rawLines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                formattedChangelog.addAll(font.split(Component.literal(" "), maxTextWidth));
                continue;
            }
            // Strip markdown bold / headers / bullets nicely
            if (line.startsWith("#")) line = "§a" + line.replaceAll("^#+\\s*", "");
            else if (line.startsWith("- ") || line.startsWith("* ")) line = "§7• " + line.substring(2);

            formattedChangelog.addAll(font.split(Component.literal(line), maxTextWidth));
        }
    }

    private void startDownload() {
        if (downloading || completed || release == null) return;
        downloading = true;
        downloadButton.active = false;
        statusText = "Downloading update...";

        UpdateChecker.downloadUpdateAsync(release, (read, total) -> {
            progressPercent = (int) ((read * 100) / Math.max(1, total));
            statusText = "Downloading: " + progressPercent + "% (" + (read / 1024 / 1024) + "MB / " + (total / 1024 / 1024) + "MB)";
        }).thenAccept(path -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    downloading = false;
                    completed = true;
                    statusText = "Update downloaded! Restart Minecraft to apply.";
                    downloadButton.setMessage(Component.translatable("gui.realworldmc.update.installed"));
                });
            }
        }).exceptionally(ex -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    downloading = false;
                    downloadButton.active = true;
                    statusText = "Download failed: " + ex.getMessage();
                });
            }
            return null;
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int contentHeight = formattedChangelog.size() * 10;
        int viewHeight = panelHeight - 110;
        if (contentHeight > viewHeight) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) (verticalAmount * 16), contentHeight - viewHeight));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x66000000);
        g.fill(left, top, left + panelWidth, top + panelHeight, RWTheme.BACKGROUND);
        g.fill(left, top, left + 3, top + panelHeight, RWTheme.BRAND);

        // Header
        g.drawString(font, Component.translatable("gui.realworldmc.update.heading"), left + 14, top + 12, RWTheme.GREEN, false);
        String versionStr = "Current: v" + UpdateChecker.getCurrentVersion() + "  ->  §aNew: v" + (release != null ? release.version() : "?");
        g.drawString(font, versionStr, left + 14, top + 26, RWTheme.MUTED, false);

        // Changelog Box
        int boxX = left + 12, boxY = top + 42, boxW = panelWidth - 24, boxH = panelHeight - 88;
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0x33000000);

        g.enableScissor(boxX, boxY, boxX + boxW, boxY + boxH);
        int textY = boxY + 4 - scrollOffset;
        for (var line : formattedChangelog) {
            if (textY + 10 >= boxY && textY <= boxY + boxH) {
                g.drawString(font, line, boxX + 6, textY, 0xDDDDDD, false);
            }
            textY += 10;
        }
        g.disableScissor();

        // Status / progress
        if (!statusText.isEmpty()) {
            int color = completed ? 0x55FF55 : (downloading ? 0xFFFF55 : 0xFF5555);
            g.drawString(font, statusText, left + 14, top + panelHeight - 46, color, false);
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
