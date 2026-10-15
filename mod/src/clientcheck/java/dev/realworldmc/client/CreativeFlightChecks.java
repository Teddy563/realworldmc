package dev.realworldmc.client;

import dev.realworldmc.EarthConfig;
import dev.realworldmc.FlightBoost;
import dev.realworldmc.RegionsPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;

/** Actual client input, integrated-server movement, vanilla rockets and transformed pose mixins. */
final class CreativeFlightChecks {
    private int stage, ticks, elapsed;
    private boolean opened, prepared, sawBoost;
    private double reference, boostedSpeed;
    private Vec3 anchor;
    private volatile Throwable serverFailure;
    void register() {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(4);
            mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
            EarthConfig.get().setupComplete = true;
            EarthConfig.get().autoSpaceFlight = false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void command(String text) {
        require(!ClientSendMessageEvents.ALLOW_COMMAND.invoker().allowSendCommandMessage(text), "Unrecognized local command: " + text);
    }
    private static void release(Minecraft mc) {
        for (var key : new net.minecraft.client.KeyMapping[]{mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft,
                mc.options.keyRight, mc.options.keyJump, mc.options.keyShift, PanoramaRenderer.INSTANCE.flightSpeedKey(true), PanoramaRenderer.INSTANCE.flightSpeedKey(false)}) key.setDown(false);
    }
    private void next() { stage++; ticks=0; }
    private void server(Minecraft mc, java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> task) {
        var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
        server.execute(() -> { try { task.accept(server.getPlayerList().getPlayer(id)); } catch(Throwable failure) {serverFailure=failure;} });
    }
    private void tick(Minecraft mc) {
        try {
            require(++elapsed<2400, "Timed out in stage " + stage);
            if(serverFailure!=null)throw new AssertionError("Server check",serverFailure);
            if(!opened && mc.getOverlay()==null) {
                opened=true;
                mc.createWorldOpenFlows().openWorld("creative-flight-check", () -> fail(mc,new AssertionError("Test world failed to open")));
            }
            if(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen || mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
                for(var child:mc.screen.children()) if(child instanceof net.minecraft.client.gui.components.Button button) {
                    String label=button.getMessage().getString();
                    boolean skip=button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text && text.getKey().equals("selectWorld.backupJoinSkipButton");
                    if(skip || label.equals("Proceed") || label.equals("Yes")) {button.onPress();return;}
                }
            }
            if(mc.player==null || mc.getSingleplayerServer()==null || mc.getOverlay()!=null)return;
            if(stage==0) {
                if(!prepared) { prepared=true; server(mc,p->{p.setGameMode(GameType.CREATIVE);p.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);p.teleportTo(0,160,0);}); }
                if(!mc.player.isCreative() || Math.abs(mc.player.getY()-160)>1)return;
                mc.setScreen(null); mc.player.setYRot(0);mc.player.setXRot(0);
                MapRenderer.INSTANCE.setRegions(new RegionsPayload(java.util.List.of()));
                for (int yaw : new int[]{0,90,180,270}) {
                    var forward=CreativeFlight.direction(1,0,0,yaw,0);
                    var right=CreativeFlight.direction(0,1,0,yaw,0);
                    require(right.dot(forward.cross(new Vec3(0,1,0)))>.999,"Right must match Minecraft camera direction at yaw "+yaw);
                    require(CreativeFlight.direction(0,-1,0,yaw,0).dot(right)<-.999,"Left must oppose right");
                }
                command("earth fly"); require(CreativeFlight.enabled() && !CreativeFlight.gliding(),"Start upright");
                mc.options.keyUp.setDown(true); next(); return;
            }
            if(stage==10) { sawBoost |= FlightBoost.boosted(); boostedSpeed=Math.max(boostedSpeed,mc.player.getDeltaMovement().length()); }
            if(++ticks<40)return;
            System.out.println("Creative flight check stage="+stage+" position="+mc.player.position()+" velocity="+mc.player.getDeltaMovement());
            switch(stage) {
                case 1 -> {
                    require(mc.player.getZ()>70,"Forward flight did not move");
                    require(Math.abs(mc.player.getDeltaMovement().length()-3.3)<.1,"Default cruise must be about 66 m/s");
                    require(!mc.player.isFallFlying() && mc.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),"No elytra or fall-flying physics needed");
                    require(CreativeFlight.pose().pitch(1)>85,"Glide body must be horizontal");
                    var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);
                    renderer.getModel().setupAnim(mc.player,0,0,mc.player.tickCount,0,0);
                    require(Math.abs(renderer.getModel().rightArm.xRot)<.2,"Glide arms should trail, not use the Superman pose");
                    for(var camera: new net.minecraft.client.CameraType[]{net.minecraft.client.CameraType.THIRD_PERSON_BACK,net.minecraft.client.CameraType.THIRD_PERSON_FRONT}) {
                        mc.options.setCameraType(camera);
                        mc.gameRenderer.getMainCamera().setup(mc.level,mc.player,true,camera==net.minecraft.client.CameraType.THIRD_PERSON_FRONT,1);
                        require(mc.gameRenderer.getMainCamera().getPosition().distanceTo(mc.player.getEyePosition(1))>2,"Third-person camera collapsed");
                    }
                    release(mc); PanoramaRenderer.INSTANCE.flightSpeedKey(false).setDown(true); next();
                }
                case 2 -> {
                    require(mc.player.getDeltaMovement().length()<.01,"Release must settle to hover");
                    require(!CreativeFlight.gliding() && Math.abs(CreativeFlight.pose().pitch(1))<.2,"Automatic upright hover");
                    require(CreativeFlight.speedFactor()<.5,"Down arrow must reduce throttle");
                    release(mc);mc.player.getAbilities().setFlyingSpeed(.1f);mc.options.keyUp.setDown(true);next();
                }
                case 3 -> {
                    require(mc.player.getDeltaMovement().length()<3.2 && mc.player.getDeltaMovement().length()>2.6,"Axiom-style flying speed must scale actual cruise");
                    mc.player.getAbilities().setFlyingSpeed(.05f);PanoramaRenderer.INSTANCE.flightSpeedKey(true).setDown(true);next();
                }
                case 4 -> {
                    require(CreativeFlight.speedFactor()==1,"Up arrow must restore bounded maximum throttle");
                    release(mc);mc.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("realworldmc", "flight_check"), .4, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));mc.options.keyRight.setDown(true);next();
                }
                case 5 -> {
                    require(mc.player.getDeltaMovement().x< -4.5 && mc.player.getDeltaMovement().x> -4.8,"Movement-speed modifier must scale strafe");
                    mc.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).removeModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("realworldmc", "flight_check"));
                    release(mc);mc.options.keyDown.setDown(true);next();
                }
                case 6 -> {
                    require(mc.player.getDeltaMovement().z<-3,"Backward flight failed");
                    release(mc);command("rw fly on"); require(CreativeFlight.enabled(),"Idempotent alias");
                    reference=mc.player.getY();mc.options.keyJump.setDown(true);next();
                }
                case 7 -> {
                    require(mc.player.getY()>reference+70,"Upright ascent failed");
                    require(Math.abs(CreativeFlight.pose().pitch(1))<.1,"Fly must remain upright");
                    release(mc);reference=mc.player.getY();mc.options.keyShift.setDown(true);next();
                }
                case 8 -> {
                    require(mc.player.getY()<reference-50,"Shift descent failed");
                    release(mc);server(mc,p->{var stack=new ItemStack(Items.FIREWORK_ROCKET,3);stack.set(net.minecraft.core.component.DataComponents.FIREWORKS,new net.minecraft.world.item.component.Fireworks(3,java.util.List.of()));p.setItemInHand(InteractionHand.MAIN_HAND,stack);});next();
                }
                case 9 -> {
                    require(mc.player.getDeltaMovement().length()<.01,"Stationary hover drift");
                    require(mc.player.getMainHandItem().is(Items.FIREWORK_ROCKET),"Rocket inventory synchronization");
                    require(mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND).consumesAction(),"Vanilla rocket item must accept equipment-free flight");next();
                }
                case 10 -> {
                    require(sawBoost && boostedSpeed>4,"Real firework must boost beyond normal cruise");
                    release(mc);next();
                }
                case 11 -> {
                    require(!FlightBoost.boosted(),"Rocket duration must expire");
                    command("earth fly off");require(!CreativeFlight.enabled() && !FlightBoost.active(mc.player),"Explicit off must clear state");
                    require(!Items.FIREWORK_ROCKET.use(mc.level,mc.player,InteractionHand.MAIN_HAND).getResult().consumesAction(),"Ordinary non-gliding rocket use must be unchanged");
                    mc.player.startFallFlying();
                    require(Items.FIREWORK_ROCKET.use(mc.level,mc.player,InteractionHand.MAIN_HAND).getResult().consumesAction(),"Vanilla elytra rocket eligibility must remain intact");
                    mc.player.stopFallFlying();
                    server(mc,p->{p.teleportTo(0,160,0);for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)p.serverLevel().setBlockAndUpdate(new net.minecraft.core.BlockPos(x,159,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());for(int x=-3;x<=3;x++)for(int y=157;y<=164;y++)p.serverLevel().setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,10),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());});next();
                }
                case 12 -> { mc.player.setYRot(0);mc.player.setXRot(0);command("rw fly on");mc.options.keyUp.setDown(true);next(); }
                case 13 -> {
                    if (mc.player.noPhysics) require(mc.player.getZ()>70,"Axiom noclip must still allow flight through blocks");
                    else require(mc.player.getZ()<9.71 && mc.player.getZ()>8,"Powered flight clipped through blocks");
                    server(mc,p->require(p.getAbilities().flying,"Flight ability must remain synchronized after starting on the ground"));
                    release(mc);command("rw fly off");
                    server(mc,p->{dev.realworldmc.EarthRegions.unload();dev.realworldmc.EarthRegions.add(44.4,26.1,120);p.teleportTo(0,2200,0);});next();
                }
                case 14 -> {
                    command("earth fly on");EarthConfig.get().autoSpaceFlight=true;next();
                }
                case 15 -> {
                    require(SpaceFlight.anchored(),"Automatic orbit transition lost");
                    anchor=mc.player.position();reference=SpaceFlight.altitude();mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(true);next();
                }
                case 16 -> {
                    require(mc.player.position().distanceTo(anchor)<.1,"Orbit moved physical player");
                    require(SpaceFlight.altitude()>reference+100 && !SpaceFlight.orbitRotation(1).equals(new org.joml.Quaterniond(),1e-8),"Orbit flight did not move geographic view");
                    require(CreativeFlight.gliding() && SpaceFlight.pose().pitch(1)>40,"Automatic glide in orbit");
                    release(mc);command("earth fly on");mc.options.keyUp.setDown(true);mc.player.setXRot(0);
                    require(mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND).consumesAction(),"Orbital rocket launch");next();
                }
                case 17 -> {
                    require(mc.player.position().distanceTo(anchor)<.1,"Rocket moved orbital anchor");
                    require(SpaceFlight.pose().pitch(1)>85,"Orbital glide pose missing");
                    release(mc);command("earth space return");command("earth fly");next();
                }
                case 18 -> {
                    require(!SpaceFlight.enabled() && !CreativeFlight.enabled(),"Toggle/return cleanup");
                    command("rw fly on");server(mc,p->p.setGameMode(GameType.SURVIVAL));next();
                }
                case 19 -> {
                    require(!CreativeFlight.enabled() && !FlightBoost.active(mc.player),"Game-mode change leaked flight");
                    command("earth fly on");require(!CreativeFlight.enabled(),"Survival should reject Creative flight");
                    require(ClientSendMessageEvents.ALLOW_COMMAND.invoker().allowSendCommandMessage("earth fly invalid"),"Invalid arguments must reach normal command errors");
                    Files.writeString(Path.of("creative-flight-result.txt"),"PASS: aliases/toggles, no elytra, 66 m/s forward/back/strafe, throttle, upright rise/descent/hover, both third-person cameras, real rockets/expiry, block collision, automatic anchored orbit with rockets, return and survival cleanup");mc.stop();
                }
            }
        } catch(Throwable failure) {fail(mc,failure);}
    }
    private void fail(Minecraft mc,Throwable failure) {
        failure.printStackTrace();
        try {Files.writeString(Path.of("creative-flight-result.txt"),"FAIL stage "+stage+": "+failure);}catch(Exception ignored){}
        mc.stop();
    }
}
