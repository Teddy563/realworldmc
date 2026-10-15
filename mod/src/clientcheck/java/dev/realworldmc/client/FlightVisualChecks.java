package dev.realworldmc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.realworldmc.EarthConfig;
import dev.realworldmc.RWMessages;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

final class FlightVisualChecks {
    static void run(Minecraft mc) throws Exception {
        var player = mc.player;
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("rwmc_filming_tweaks") &&
            !(boolean)Class.forName("com.conquest.client.hud.BetaNoticeHud").getMethod("isDismissed").invoke(null))
            throw new AssertionError("Conquest beta notice was not dismissed");
        var dispatcher = mc.getEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(player);
        if(SpaceView.active() && !Boolean.getBoolean("realworldmc.baselineComparison")) {
            var creeper=new net.minecraft.world.entity.monster.Creeper(net.minecraft.world.entity.EntityType.CREEPER,mc.level);
            creeper.setPos(player.getX(),player.getY(),player.getZ()+3);
            var broadFrustum=new Frustum(new Matrix4f(),new Matrix4f());
            broadFrustum.prepare(creeper.getX(),creeper.getY(),creeper.getZ());
            if(!dispatcher.getRenderer(creeper).shouldRender(creeper,broadFrustum,creeper.getX(),creeper.getY(),creeper.getZ()))
                throw new AssertionError("Fixture must contain a normally visible Earth mob");
            if(dispatcher.shouldRender(creeper,broadFrustum,creeper.getX(),creeper.getY(),creeper.getZ()))
                throw new AssertionError("Earth mobs leaked into orbit");
            net.minecraft.client.renderer.MultiBufferSource forbidden=layer->{throw new AssertionError("Earth renderer submitted geometry while in orbit");};
            dispatcher.render(creeper,0,0,3,0,1,new PoseStack(),forbidden,15728880);
            var chest=new net.minecraft.world.level.block.entity.ChestBlockEntity(player.blockPosition(),net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            chest.setLevel(mc.level);
            mc.getBlockEntityRenderDispatcher().render(chest,1,new PoseStack(),forbidden);
            if(creeper.isRemoved())throw new AssertionError("Orbital isolation must not delete Earth entities");
            mc.crosshairPickEntity=creeper;
            mc.hitResult=new net.minecraft.world.phys.EntityHitResult(creeper);
            mc.gameRenderer.pick(1);
            if(mc.crosshairPickEntity!=null || mc.hitResult.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)
                throw new AssertionError("Orbit can still target the hidden Earth world");
        }
        if(SpaceFlight.anchored() && dispatcher.getPackedLightCoords(player,1)!=net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
            throw new AssertionError("Orbital anchor must not black out the player");
        var original = mc.options.getCameraType();
        var frustum = new Frustum(new Matrix4f(), new Matrix4f().perspective(1.2f, 1, .05f, 8000));
        try {
            for (var camera : new CameraType[]{CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT}) {
                mc.options.setCameraType(camera);
                for (double offset : new double[]{100, 1000, 6000}) {
                    double x=player.getX(), y=player.getY()-offset, z=player.getZ();
                    frustum.prepare(x,y,z);
                    if (offset>=1000 && renderer.shouldRender(player,frustum,x,y,z)) throw new AssertionError("Fixture did not reproduce vanilla fast-player culling");
                    if (!dispatcher.shouldRender(player,frustum,x,y,z)) throw new AssertionError("Fast camera subject disappeared in "+camera);
                }
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (dispatcher.shouldRender(player,frustum,player.getX(),player.getY()-6000,player.getZ())) throw new AssertionError("Visibility fix escaped third person");
            if (!SpaceFlight.posed(player) || player.isFallFlying()) throw new AssertionError("Space pose must not change real fall-flying physics");
            if (!player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()) throw new AssertionError("Fixture must not wear an elytra");
            var rotation = java.util.Arrays.stream(renderer.getClass().getDeclaredMethods()).filter(m -> {
                var types=m.getParameterTypes();
                return types.length==6 && types[0]==AbstractClientPlayer.class && types[1]==PoseStack.class;
            }).findFirst().orElseThrow();
            rotation.setAccessible(true);
            float pitch=player.getXRot();player.setXRot(0);
            try {
                var poses=new PoseStack();
                rotation.invoke(renderer,player,poses,0f,0f,1f,1f);
                float expected=(float)Math.cos(Math.toRadians(SpaceFlight.pose().pitch(1)));
                if(Math.abs(poses.last().pose().m11()-expected)>.05f)throw new AssertionError("Body does not follow the movement-driven pose");
                player.setXRot(75);var looking=new PoseStack();rotation.invoke(renderer,player,looking,0f,0f,1f,1f);
                if(!poses.last().pose().equals(looking.last().pose(),.0001f))throw new AssertionError("Looking up changes the body's flight direction");
            } finally {player.setXRot(pitch);}
            var model=((net.minecraft.client.renderer.entity.player.PlayerRenderer)renderer).getModel();
            model.setupAnim(player,0,0,0,0,0);
            if(model.rightArm.xRot>-2.5f || model.leftArm.xRot>-2.2f)throw new AssertionError("Superman arms must lead the ascent");
            command("earth hud off");
            if(EarthConfig.get().actionBar || !ActionBar.hidden(RWMessages.message("flight")) || ActionBar.hidden(Component.literal("Vanilla message")))throw new AssertionError("Actionbar toggle affected the wrong messages");
            Component vanilla=Component.literal("Vanilla overlay check");mc.gui.setOverlayMessage(vanilla,false);
            var overlay=java.util.Arrays.stream(mc.gui.getClass().getDeclaredFields()).filter(f -> {
                if(!Component.class.isAssignableFrom(f.getType()))return false;
                try{f.setAccessible(true);return f.get(mc.gui)==vanilla;}catch(Exception e){return false;}
            }).findFirst().orElseThrow();
            mc.gui.setOverlayMessage(RWMessages.message("hidden"),false);
            if(overlay.get(mc.gui)!=vanilla)throw new AssertionError("Hidden RW overlay replaced vanilla information");
            command("rw hud");
            Component visible=RWMessages.message("visible");mc.gui.setOverlayMessage(visible,false);
            if(!EarthConfig.get().actionBar || overlay.get(mc.gui)!=vanilla)throw new AssertionError("RW corner status must preserve vanilla overlays");
            var status=ActionBar.class.getDeclaredField("status");status.setAccessible(true);
            if(status.get(null)!=visible)throw new AssertionError("RW status was not routed to the corner HUD");
            var file=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("realworldmc.json");
            if(!com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject().get("actionBar").getAsBoolean())throw new AssertionError("Actionbar preference was not saved");
        } finally {mc.options.setCameraType(original);}
    }
    private static void command(String command) {
        if(net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.invoker().allowSendCommandMessage(command))throw new AssertionError("Command was not handled: "+command);
    }
}
