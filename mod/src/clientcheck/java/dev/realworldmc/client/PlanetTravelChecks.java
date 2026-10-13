package dev.realworldmc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import java.nio.file.*;
import net.minecraft.world.phys.Vec3;

/** Offline integrated-world checks. This fixture never reads the personal profile or provider keys. */
final class PlanetTravelChecks {
    private int stage,ticks,total;
    private boolean preparing;
    private Vec3 anchor;
    private double height,earthHeight;
    private boolean markerCancelled;
    private static void capture(Minecraft mc,String name) throws Exception {
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(Path.of("flight-"+name+".png"));
        }
    }
    void start(Minecraft mc) {
        mc.options.pauseOnLostFocus=false;
        mc.createWorldOpenFlows().openWorld("space-flight-check",()->fail(mc,new AssertionError("Missing disposable world")));
    }
    void tick(Minecraft mc) {
        try {
            if(++total>1600)throw new AssertionError("Planet travel timed out at stage "+stage);
            if(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen || mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
                for(var c:mc.screen.children())if(c instanceof net.minecraft.client.gui.components.Button b) {
                    String text=b.getMessage().getString();
                    if(text.equals("Proceed") || text.equals("Yes") || text.equals("I know what I'm doing!")){b.onPress();return;}
                }
            }
            if(mc.player==null || mc.getSingleplayerServer()==null || mc.getOverlay()!=null)return;
            if(stage==0) {
                if(!preparing) {
                    preparing=true;var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                    server.execute(()->{dev.realworldmc.EarthRegions.unload();dev.realworldmc.EarthRegions.add(44.4,26.1,120);var p=server.getPlayerList().getPlayer(id);p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(6000,0,false,false);p.setGameMode(GameType.CREATIVE);p.teleportTo(0,2063,0);});
                }
                if(!mc.player.isCreative() || Math.abs(mc.player.getY()-2063)>1)return;
                mc.setScreen(null);MapRenderer.INSTANCE.setRegions(new dev.realworldmc.RegionsPayload(java.util.List.of(new dev.realworldmc.EarthRegions.Region(0,44.4,26.1,120))));
                mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);mc.player.setXRot(-10);mc.options.keyJump.setDown(true);stage=1;ticks=0;return;
            }
            if(stage==1) {
                if(!SpaceFlight.anchored())return;
                if(++ticks<30)return;
                anchor=mc.player.position();earthHeight=SpaceFlight.altitude();
                capture(mc,"earth-ascent");FlightVisualChecks.run(mc);mc.options.keyJump.setDown(false);
                command("rw moon");stage=2;ticks=0;return;
            }
            if(++ticks<(stage==101 || stage==12?120:25))return;ticks=0;
            if(stage<10 && mc.player.position().distanceTo(anchor)>.1)throw new AssertionError("Planet flight moved the physical player");
            if(stage==2) {
                require(SpaceFlight.body()==CelestialBody.MOON,"Moon command");
                if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
                    var iris=Class.forName("net.irisshaders.iris.Iris");
                    require(iris.getMethod("getCurrentDimension").invoke(null).toString().equals("realworldmc:orbit"),"Iris orbital pipeline selection");
                    var manager=iris.getMethod("getPipelineManager").invoke(null);
                    var pipeline=(java.util.Optional<?>)manager.getClass().getMethod("getPipeline").invoke(manager);
                    require(pipeline.isPresent() && pipeline.get().getClass().getSimpleName().equals("VanillaRenderingPipeline"),"Iris must actually use the isolated pipeline");
                }
                mc.player.setXRot(65);mc.options.hideGui=true;height=SpaceFlight.altitude();mc.options.keyJump.setDown(true);stage=3;
            } else if(stage==3) {
                require(SpaceFlight.altitude()>height+1000,"Lunar ascent");capture(mc,"moon-ascent");
                height=SpaceFlight.altitude();mc.options.keyJump.setDown(false);mc.options.keyShift.setDown(true);stage=4;
            } else if(stage==4) {
                require(SpaceFlight.altitude()<height,"Lunar descent");
                require(SpaceFlight.pose().pitch(1)>175,"Descent pose");capture(mc,"moon-descent");
                mc.options.keyShift.setDown(false);mc.options.keyUp.setDown(true);stage=5;
            } else if(stage==5) {
                require(!SpaceFlight.orbitRotation(1).equals(new org.joml.Quaterniond(),1e-8),"Lunar orbit travel");
                capture(mc,"moon-cruise");mc.options.keyUp.setDown(false);command("earth mars");stage=6;
            } else if(stage==6) {
                require(SpaceFlight.body()==CelestialBody.MARS,"Mars alias");capture(mc,"mars");
                mc.options.hideGui=false;
                // Aim at the actual Moon marker, release partway, then hold to confirm.
                var d=SpaceDestinations.direction(CelestialBody.MOON);
                mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
                mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                mc.options.keyUp.setDown(true);stage=60;
            } else if(stage==60) {
                require(SpaceFlight.body()==CelestialBody.MARS,"Marker must wait for confirmation");
                mc.options.keyUp.setDown(false);stage=61;
            } else if(stage==61) {
                var field=SpaceDestinations.class.getDeclaredField("ticks");field.setAccessible(true);
                require(field.getInt(null)==0,"Releasing forward cancels marker travel");markerCancelled=true;
                mc.options.keyUp.setDown(true);stage=62;
            } else if(stage==62) {
                if(SpaceFlight.body()!=CelestialBody.MOON)return;
                require(markerCancelled,"Marker cancellation was tested");mc.options.keyUp.setDown(false);
                command("rw mars");
                var f=SpaceFlight.class.getDeclaredField("height");f.setAccessible(true);f.setDouble(null,CelestialBody.MARS.minimumAltitude+100);
                mc.options.keyShift.setDown(true);stage=7;
            } else if(stage==7) {
                require(SpaceFlight.body()==CelestialBody.MARS && SpaceFlight.altitude()>=CelestialBody.MARS.minimumAltitude,"Planet surface guard");
                mc.options.keyShift.setDown(false);command("rw earth");stage=8;
            } else if(stage==8) {
                require(SpaceFlight.body()==CelestialBody.EARTH && Math.abs(SpaceFlight.altitude()-earthHeight)<1,"Previous Earth orbit restored");
                command("earth space return");stage=9;
            } else if(stage==9) {
                require(!SpaceFlight.enabled() && !SpaceFlight.posed(mc.player),"Return leaves no pose or automatic retrigger");
                if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
                    var iris=Class.forName("net.irisshaders.iris.Iris");
                    require(!iris.getMethod("getCurrentDimension").invoke(null).toString().equals("realworldmc:orbit"),"Earth shader selection restored");
                }
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                server.execute(()->{
                    var p=server.getPlayerList().getPlayer(id);var level=p.serverLevel();
                    for(int x=5;x<=11;x++)for(int y=70;y<=74;y++)
                        level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,14),net.minecraft.world.level.block.Blocks.MAGENTA_CONCRETE.defaultBlockState());
                    level.setBlockAndUpdate(new net.minecraft.core.BlockPos(7,70,11),net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
                    var mob=new net.minecraft.world.entity.monster.Creeper(net.minecraft.world.entity.EntityType.CREEPER,level);
                    mob.setNoAi(true);mob.setNoGravity(true);mob.setPos(9,70,11);level.addFreshEntity(mob);
                    p.teleportTo(8,70,8);
                });
                stage=10;
            } else if(stage==10) {
                if(Math.abs(mc.player.getY()-70)>.1)return;
                require(!mc.level.getBlockState(new net.minecraft.core.BlockPos(8,72,14)).isAir(),"Ground fixture received");
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.player.setYRot(0);mc.player.setXRot(0);
                stage=101;
            } else if(stage==101) {
                var camera=mc.gameRenderer.getMainCamera();
                System.out.println("Orbital fixture: player="+mc.player.position()+", camera="+camera.getPosition()+", pitch="+camera.getXRot()+", yaw="+camera.getYRot()+", photo="+PanoramaRenderer.INSTANCE.active());
                capture(mc,"ground-before-orbit");command("rw mars");stage=11;
            } else if(stage==11) {
                require(SpaceView.active() && SpaceFlight.body()==CelestialBody.MARS,"Ground-level Mars entry");
                require(Math.abs(mc.player.getY()-70)<.1,"Ground anchor unchanged");
                require(!mc.level.getBlockState(new net.minecraft.core.BlockPos(8,72,14)).isAir(),"Orbit must not remove launch terrain");
                capture(mc,"ground-mars-isolated");command("earth space return");stage=12;
            } else if(stage==12) {
                require(!SpaceView.active(),"Ground-level return");
                require(!mc.level.getBlockState(new net.minecraft.core.BlockPos(8,72,14)).isAir(),"Launch terrain preserved after return");
                var dispatcher=mc.getEntityRenderDispatcher();
                var mob=new net.minecraft.world.entity.monster.Creeper(net.minecraft.world.entity.EntityType.CREEPER,mc.level);
                mob.setPos(9,70,11);int[] submissions={0};
                var buffers=mc.renderBuffers().bufferSource();
                net.minecraft.client.renderer.MultiBufferSource tracking=layer->{submissions[0]++;return buffers.getBuffer(layer);};
                dispatcher.render(mob,1,0,3,0,1,new com.mojang.blaze3d.vertex.PoseStack(),tracking,15728880);
                require(submissions[0]>0,"Earth entity rendering must resume after orbit");
                var camera=mc.gameRenderer.getMainCamera();
                System.out.println("Return render: camera="+camera.getPosition()+", submissions="+submissions[0]);
                capture(mc,"ground-return");
                Files.writeString(Path.of("planet-travel-result.txt"),"PASS: automatic entry, ascent pose independent of look direction, local-player visibility, hidden Earth mobs and block entities, orbital picking, corner HUD, Moon/Mars commands and aliases, stable player anchor, lunar ascent/descent/cruise, downward pose, waypoint cancellation and confirmed transfer, surface guard, Earth orbit restoration, return, Iris orbital pipeline and restoration, ground-level Mars entry and original terrain preservation");
                mc.stop();
            }
        } catch(Throwable e){fail(mc,e);}
    }
    private static void command(String input) {
        require(!net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.invoker().allowSendCommandMessage(input),"Client handles "+input);
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void fail(Minecraft mc,Throwable e) {
        e.printStackTrace();try{Files.writeString(Path.of("planet-travel-result.txt"),"FAIL: "+e);}catch(Exception ignored){}
        mc.options.keyJump.setDown(false);mc.options.keyShift.setDown(false);mc.options.keyUp.setDown(false);mc.stop();
    }
}
