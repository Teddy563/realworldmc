package dev.realworldmc.client;

import dev.realworldmc.RegionsPayload;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import java.nio.file.*;

/** Exercises real integrated-server movement in a disposable, offline world. */
final class SpaceFlightIntegrationChecks {
    private int stage, ticks, elapsed;
    private double start, hover;
    private double unzoomed;
    private net.minecraft.client.KeyMapping zoom;
    private net.minecraft.world.phys.Vec3 cruiseAnchor;
    private boolean preparing;
    private volatile Throwable addonFailure;
    void start(Minecraft mc) {
        mc.options.pauseOnLostFocus=false;
        mc.createWorldOpenFlows().openWorld("space-flight-check",()->fail(mc,new AssertionError("Could not open test world")));
    }
    void tick(Minecraft mc) {
        try {
            if(++elapsed>2400)throw new AssertionError("Flight check timed out, stage "+stage);
            if(elapsed%100==0)System.out.println("Flight check stage="+stage+" screen="+(mc.screen==null?"none":mc.screen.getClass().getName()+" "+mc.screen.getTitle().getString()+" "+mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button).map(c->((net.minecraft.client.gui.components.Button)c).getMessage().getString()).toList()));
            if(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen || mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
                // Only this disposable GameTest world: approve Minecraft's experimental-data prompt.
                for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button) {
                    String label=button.getMessage().getString();
                    boolean skipBackup=button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated && translated.getKey().equals("selectWorld.backupJoinSkipButton");
                    if(skipBackup || label.equals("Proceed") || label.equals("Yes")){button.onPress();return;}
                }
            }
            if(mc.player==null || mc.getSingleplayerServer()==null || mc.getOverlay()!=null)return;
            if(stage==0) {
                if(!preparing) {
                    preparing=true;
                    var id=mc.player.getUUID(); var server=mc.getSingleplayerServer();
                    server.execute(()->{dev.realworldmc.EarthRegions.unload();dev.realworldmc.EarthRegions.add(44.4,26.1,120);var p=server.getPlayerList().getPlayer(id);p.setGameMode(GameType.CREATIVE);p.teleportTo(0,2063,0);});
                }
                if(!mc.player.isCreative() || Math.abs(mc.player.getY()-2063)>1)return;
                mc.setScreen(null);
                MapRenderer.INSTANCE.setRegions(new RegionsPayload(java.util.List.of()));
                if(SpaceFlight.enabled())throw new AssertionError("Auto entry triggered below the threshold");
                mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();
                mc.options.keyJump.setDown(true);stage=-1;ticks=0;return;
            }
            if(stage==-1) {
                if(!SpaceFlight.enabled())return;
                if(!SpaceFlight.anchored())throw new AssertionError("Automatic entry did not anchor the subject");
                start=mc.player.getY();cruiseAnchor=mc.player.position();stage=1;ticks=0;return;
            }
            if(++ticks<20)return;
            ticks=0;
            if(stage==1) {
                if(SpaceFlight.altitude()<start-64+100)throw new AssertionError("Ascent did not move the geographic view");
                if(mc.player.position().distanceTo(cruiseAnchor)>.1)throw new AssertionError("Ascent moved Steve instead of the geographic view");
                if(!(SpaceFlight.altitude(0)<SpaceFlight.altitude(.5f) && SpaceFlight.altitude(.5f)<SpaceFlight.altitude(1)))throw new AssertionError("Vertical camera motion is not interpolated");
                FlightVisualChecks.run(mc);
                mc.options.smoothCamera=true;
                var camera=mc.gameRenderer.getMainCamera();
                var perspective=mc.options.getCameraType();
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                camera.setup(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,mc.player,true,false,1);
                if(camera.getPosition().distanceTo(mc.player.getEyePosition(1))<12)throw new AssertionError("Far follow camera remains too close");
                mc.options.setCameraType(perspective);
                unzoomed=fov(mc);
                zoom=java.util.Arrays.stream(mc.options.keyMappings).filter(k->k.getName().equals("zoomify.key.zoom")).findFirst().orElseThrow();
                zoom.setDown(true);
                mc.options.keyJump.setDown(false);hover=mc.player.getY();stage=2;
            } else if(stage==2) {
                if(fov(mc)>=unzoomed*.7) {
                    var type=Class.forName("dev.isxander.zoomify.Zoomify");var instance=type.getField("INSTANCE").get(null);
                    throw new AssertionError("Zoom did not work with smooth camera: "+fov(mc)+" from "+unzoomed+" key="+zoom.isDown()+" active="+type.getMethod("getZooming").invoke(instance)+" divisor="+type.getMethod("getZoomDivisor",float.class).invoke(null,1f));
                }
                zoom.setDown(false);
                if(Math.abs(mc.player.getY()-hover)>3)throw new AssertionError("Hover drifted: "+(mc.player.getY()-hover));
                cruiseAnchor=mc.player.position();mc.player.setYRot(0);hover=SpaceFlight.altitude();mc.options.keyUp.setDown(true);stage=20;
            } else if(stage==20) {
                if(mc.player.position().distanceTo(cruiseAnchor)>.1)throw new AssertionError("Orbit moved Minecraft's player instead of the geographic viewpoint");
                if(SpaceFlight.orbitRotation(1).equals(new org.joml.Quaterniond(),1e-8))throw new AssertionError("W did not advance the globe");
                mc.options.keyUp.setDown(false);mc.options.keyShift.setDown(true);stage=3;
            } else if(stage==3) {
                if(SpaceFlight.enabled() && SpaceFlight.altitude()>hover-100)throw new AssertionError("Descent did not lower the geographic view");
                if(SpaceFlight.enabled())return;
                if(mc.player.getY()>=start-200 || mc.player.position().horizontalDistance()<10)throw new AssertionError("Descent did not hand off to the geographic location below orbit");
                mc.options.keyShift.setDown(false);command("earth space return");stage=4;
            } else if(stage==4) {
                if(SpaceFlight.enabled() || Math.abs(mc.player.getY()-start)>2)throw new AssertionError("Return did not restore launch position");
                if(SpaceFlight.posed(mc.player) || !SpaceFlight.orbitRotation(1).equals(new org.joml.Quaterniond()))throw new AssertionError("Glide pose or orbit survived return");
                stage=5;
                mc.player.setYRot(0);mc.player.setXRot(0);
                var server=mc.getSingleplayerServer();var playerId=mc.player.getUUID();
                server.execute(()->{
                    try {
                        var level=server.overworld();
                        for(String id:java.util.List.of("f22raptor:f16_fighting_falcon","blackholemod:black_hole")) {
                            var key=net.minecraft.resources.ResourceLocation.parse(id);
                            if(!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(key))throw new AssertionError("Missing add-on entity: "+id);
                            var entity=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(key).create(level);
                            entity.setPos(id.startsWith("f22")?0:12,start,12);entity.setNoGravity(true);level.addFreshEntity(entity);
                        }
                        var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("blackholemod:nuke_launcher"));
                        server.getPlayerList().getPlayer(playerId).setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(item));
                    }catch(Throwable failure){addonFailure=failure;}
                });
            } else if(stage++>=11) {
                if(addonFailure!=null)throw new AssertionError("Add-on entity check failed",addonFailure);
                Files.writeString(Path.of("space-flight-result.txt"),"PASS: automatic 2 km entry, anchored/interpolated ascent, hover, W globe cruise, Shift descent and geographic handoff, return without automatic retrigger; far follow camera and Zoomify with smooth camera; both third-person views, glide pose, corner HUD; F-16 and black-hole entities ticked");
                mc.stop();
            }
        } catch(Throwable failure) { fail(mc,failure); }
    }
    private static void command(String command) {
        if(ClientSendMessageEvents.ALLOW_COMMAND.invoker().allowSendCommandMessage(command))throw new AssertionError("Command was not handled locally: "+command);
    }
    private static double fov(Minecraft mc) throws Exception {
        // MixinExtras also leaves an unmodified wrapper body with the same parameter types.
        // Call Minecraft's actual entry point, including all installed zoom hooks.
        String name=net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary",
            "net.minecraft.class_757","method_3196","(Lnet/minecraft/class_4184;FZ)D");
        var method=mc.gameRenderer.getClass().getDeclaredMethod(name,net.minecraft.client.Camera.class,float.class,boolean.class);
        method.setAccessible(true);return (double)method.invoke(mc.gameRenderer,mc.gameRenderer.getMainCamera(),1f,true);
    }
    private static void fail(Minecraft mc,Throwable failure) {
        failure.printStackTrace();
        try{Files.writeString(Path.of("space-flight-result.txt"),"FAIL: "+failure);}catch(Exception ignored){}
        mc.options.keyJump.setDown(false);mc.options.keyShift.setDown(false);mc.options.keyUp.setDown(false);mc.stop();
    }
}
