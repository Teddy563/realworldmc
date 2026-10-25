package dev.realworldmc.client;

import dev.realworldmc.RWTheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A scene-visible command guide. Selecting a command only fills chat. */
public final class HelpScreen extends Screen {
    private record Entry(String group,String command,String description) {}
    private static final Entry[] ENTRIES={
        new Entry("Explore","/earth goto <place or lat, lon>","Travel to a real place."),
        new Entry("Explore","/earth where","Show your coordinates and map link."),
        new Entry("Explore","/earth setup","Add or test API keys privately."),
        new Entry("Explore","/earth fly","Toggle automatic glide and upright hover."),
        new Entry("Street View","/earth streetview","Open the nearest photo within 500 metres."),
        new Entry("Street View","/earth streetview <lat, lon>","Open a photo at chosen coordinates."),
        new Entry("Street View","/earth back","Return to Earth at the current photo location."),
        new Entry("Street View","/earth back original","Return to the original entry point."),
        new Entry("Movement","/earth walk on|off","Walk inside 3×3, or stay at the photo center. V also toggles."),
        new Entry("Movement","/earth camera first|back|front","Choose your perspective. F5 also changes camera."),
        new Entry("Movement","/earth streetkey on|off","Toggle Sneak + Swap Offhand with empty hands (usually Shift + F)."),
        new Entry("Display","/earth hud on|off","Show or hide the optional HUD and navigation arrows."),
        new Entry("Display","/earth usage on|off","Show or hide usage estimates separately."),
        new Entry("Display","/earth controls on|off","Show or hide key hints separately."),
        new Entry("Space","/earth space","Toggle experimental Earth-to-space flight."),
        new Entry("Space","/rw moon","Visit the Moon orbital view."),
        new Entry("Space","/rw mars","Visit the Mars orbital view."),
        new Entry("Space","/rw earth","Return to your previous Earth orbit."),
        new Entry("Advanced","/earth streetview height <1-4>","Set photo eye height in metres. Ground depth is approximate."),
        new Entry("Advanced","/earth streetview browser","Open the companion browser viewer."),
        new Entry("Advanced","/earth streetview setup","Show Street View setup guidance."),
        new Entry("Advanced","/earth streetview legacy","Open the previous flat sandbox."),
        new Entry("Advanced","/earth setlocation <lat> <lon>","Set the start for NEW worlds. Operator permission required."),
        new Entry("Advanced","/earth setlocation random","Randomize the start for NEW worlds. Operator only.")
    };
    private static int requested=-1;
    private int page,left,top,panelWidth,panelHeight,perPage;
    public HelpScreen(boolean advanced){super(Component.literal("Earth guide"));page=advanced?-1:0;}
    public static void register(){
        ClientSendMessageEvents.ALLOW_COMMAND.register(command->{
            String normalized=command.strip().replaceAll("\\s+"," ");
            if(normalized.matches("(?:rw|earth)(?: help(?: more)?)?")){
                requested=normalized.endsWith(" more")?1:0;return false;
            }
            return true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(requested>=0){boolean advanced=requested==1;requested=-1;if(client.player!=null)client.setScreen(new HelpScreen(advanced));}
        });
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float delta){}
    @Override protected void init(){
        panelWidth=Math.min(500,width-16);panelHeight=Math.min(366,height-16);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        perPage=Math.max(1,(panelHeight-100)/43);
        page=page<0?18/perPage:Math.min(page,(ENTRIES.length-1)/perPage);
        int end=Math.min(ENTRIES.length,(page+1)*perPage);
        for(int i=page*perPage;i<end;i++){
            Entry entry=ENTRIES[i];int y=top+48+(i-page*perPage)*43;
            addRenderableWidget(new GuideButton(left+10,y,panelWidth-20,40,entry.command(),()->minecraft.setScreen(new ChatScreen(entry.command())),entry));
        }
        int y=top+panelHeight-29;
        var previous=addRenderableWidget(new GuideButton(left+10,y,70,20,"Previous",()->{page--;rebuildWidgets();},null));previous.active=page>0;
        var next=addRenderableWidget(new GuideButton(left+84,y,70,20,"Next",()->{page++;rebuildWidgets();},null));next.active=end<ENTRIES.length;

        if (UpdateChecker.hasUpdate()) {
            var updateRelease = UpdateChecker.getAvailableUpdate();
            String updateLabel = "Update: v" + updateRelease.version();
            addRenderableWidget(new GuideButton(left+158,y,140,20,updateLabel,()->minecraft.setScreen(new UpdateScreen(this, updateRelease)),null));
        }

        addRenderableWidget(new GuideButton(left+panelWidth-76,y,70,20,"Close",this::onClose,null));
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta){
        g.fill(0,0,width,height,0x55000000);
        g.fill(left,top,left+panelWidth,top+panelHeight,RWTheme.BACKGROUND);
        g.fill(left,top,left+3,top+panelHeight,RWTheme.BRAND);
        g.drawString(font,"Earth guide · "+ENTRIES[page*perPage].group(),left+12,top+10,RWTheme.GREEN,false);
        g.drawString(font,"Select to fill chat · Esc to close",left+12,top+25,RWTheme.MUTED,false);
        String counter=(page+1)+" / "+((ENTRIES.length+perPage-1)/perPage);
        g.drawString(font,counter,left+panelWidth-font.width(counter)-12,top+10,RWTheme.MUTED,false);
        super.render(g,mouseX,mouseY,delta);
    }
    private final class GuideButton extends Button {
        private final Entry entry;
        GuideButton(int x,int y,int w,int h,String label,Runnable action,Entry entry){
            super(x,y,w,h,Component.literal(label+(entry==null?"":". "+entry.description())),b->action.run(),DEFAULT_NARRATION);this.entry=entry;
        }
        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float delta){
            g.fill(getX(),getY(),getX()+width,getY()+height,isHoveredOrFocused()?RWTheme.HOVER:RWTheme.SURFACE);
            int color=active?RWTheme.GREEN:RWTheme.DISABLED;
            if(entry==null){g.drawString(font,getMessage(),getX()+(width-font.width(getMessage()))/2,getY()+6,color,false);return;}
            g.drawString(font,font.plainSubstrByWidth(entry.command(),width-12),getX()+6,getY()+4,color,false);
            var lines=font.split(Component.literal(entry.description()),width-12);
            for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),getX()+6,getY()+17+i*10,RWTheme.TEXT,false);
        }
    }
}
