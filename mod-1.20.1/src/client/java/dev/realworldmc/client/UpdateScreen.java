package dev.realworldmc.client;

import dev.realworldmc.RWTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Read-only changelog with a browser handoff for replacing the mod safely. */
public final class UpdateScreen extends Screen {
    private final Screen parent;
    private UpdateChecker.ReleaseInfo release;
    private int scroll, left, top, panelWidth, panelHeight;
    private static boolean openRequested;
    public UpdateScreen(Screen parent, UpdateChecker.ReleaseInfo release) {
        super(Component.translatable("gui.realworldmc.update.title")); this.parent=parent; this.release=release;
    }
    public static void register() {
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (command.strip().matches("/?(?:rw|earth)\\s+update")) { openRequested=true; return false; } return true;
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!openRequested) return; openRequested=false;
            client.setScreen(new UpdateScreen(null, UpdateChecker.getLatestRelease()));
        });
    }
    public void updateRelease(UpdateChecker.ReleaseInfo fresh) { release=fresh; scroll=0; }
    @Override protected void init() {
        panelWidth=Math.min(480,width-16); panelHeight=Math.min(340,height-16);
        left=(width-panelWidth)/2; top=(height-panelHeight)/2;
        int buttonWidth=(panelWidth-32)/3;
        addRenderableWidget(Button.builder(Component.translatable("gui.realworldmc.update.releases"), b ->
            net.minecraft.Util.getPlatform().openUri(UpdateChecker.RELEASES)).bounds(left+8,top+panelHeight-28,buttonWidth,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realworldmc.update.retry"), b -> refresh()).bounds(left+16+buttonWidth,top+panelHeight-28,buttonWidth,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realworldmc.update.close"), b -> onClose()).bounds(left+24+buttonWidth*2,top+panelHeight-28,buttonWidth,20).build());
        if (release==null) refresh();
    }
    private void refresh() {
        var client=minecraft;
        UpdateChecker.checkAsync().thenAccept(value -> client.execute(() -> updateRelease(value)));
    }
    @Override public boolean mouseScrolled(double x,double y,double vertical) {
        scroll=Math.max(0,scroll-(int)(vertical*20)); return true;
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xC0000000); g.fill(left,top,left+panelWidth,top+panelHeight,0xFF1E2224);
        g.drawString(font,title,left+10,top+10,RWTheme.GREEN,false);
        g.drawString(font,Component.translatable("gui.realworldmc.update.current",UpdateChecker.getCurrentVersion()),left+10,top+24,RWTheme.MUTED,false);
        Component content;
        if (release==null) content=Component.translatable(UpdateChecker.isChecking()?"gui.realworldmc.update.connecting":"gui.realworldmc.update.failed");
        else content=Component.literal("v"+release.version()+"\n\n").append(Component.translatable(release.downloadUrl()==null?"gui.realworldmc.update.no_asset":"gui.realworldmc.update.replace")).append("\n\n"+release.changelog());
        List<net.minecraft.util.FormattedCharSequence> lines=font.split(content,Math.max(40,panelWidth-24));
        int viewHeight=Math.max(10,panelHeight-80); scroll=Math.min(scroll,Math.max(0,lines.size()*10-viewHeight));
        g.enableScissor(left+8,top+42,left+panelWidth-8,top+42+viewHeight);
        int y=top+42-scroll; for(var line:lines){g.drawString(font,line,left+12,y,0xDDDDDD,false);y+=10;}
        g.disableScissor(); super.render(g,mx,my,delta);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
}
