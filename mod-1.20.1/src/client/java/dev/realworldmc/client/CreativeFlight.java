package dev.realworldmc.client;

import dev.realworldmc.FlightBoost;
import dev.realworldmc.RWMessages;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Equipment-free local flight; the existing orbital controller owns movement above its threshold. */
public final class CreativeFlight {
    // Vanilla rocket-assisted level flight is about 33 m/s; this cruise target is about twice that.
    static final double MAX_SPEED = 3.3, MIN_SPEED = .2;
    private static boolean active, glide;
    private static double speed = MAX_SPEED;
    private static Vec3 velocity = Vec3.ZERO;
    private static final FlightPose pose = new FlightPose();
    private CreativeFlight() {}

    public static boolean enabled() { return active; }
    public static boolean gliding() { return glide; }
    public static boolean posed(Entity entity) { return enabled() && entity == Minecraft.getInstance().player; }
    public static FlightPose pose() { return pose; }
    public static double speedFactor() { return enabled() ? speed / MAX_SPEED * movementMultiplier() * (FlightBoost.boosted() ? 1.5 : 1) : 1; }
    private static double movementMultiplier() {
        var player = Minecraft.getInstance().player;
        if (player == null) return 1;
        // Axiom adjusts vanilla flying speed; effects and movement mods use this attribute.
        double flying = player.getAbilities().getFlyingSpeed() / .05f;
        double base = player.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        double effects = base > 0 ? player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) / base : 1;
        double multiplier = flying * effects;
        return Double.isFinite(multiplier) ? net.minecraft.util.Mth.clamp(multiplier, 0, 8) : 1;
    }
    public static int rocketForward(int forward, int right, int vertical) {
        return enabled() && FlightBoost.boosted() && forward == 0 && right == 0 && vertical == 0 ? 1 : forward;
    }
    public static String status() {
        return (gliding() ? "Glide" : "Hover") + String.format(java.util.Locale.ROOT, " · %.0f%% speed%s", speed / MAX_SPEED * 100, FlightBoost.boosted() ? " · rocket" : "");
    }
    private static boolean allowed(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.hasSingleplayerServer()
            && mc.player.isCreative() && mc.player.isAlive() && !mc.player.isPassenger()
            && mc.level.dimension() == Level.OVERWORLD && MapRenderer.INSTANCE.enabled();
    }
    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String[] words = command.strip().split("\\s+");
            if (words.length < 2 || words.length > 3 || !(words[0].equals("earth") || words[0].equals("rw"))) return true;
            if (!words[1].equals("fly") || words.length == 3 && !java.util.Set.of("on", "off").contains(words[2])) return true;
            set(words.length == 3 ? words[2].equals("on") : !enabled());
            return false;
        });
        ClientTickEvents.START_CLIENT_TICK.register(CreativeFlight::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> stop(mc));
    }
    private static void set(boolean on) {
        var mc = Minecraft.getInstance();
        if (!on) {
            stop(mc);
            ActionBar.show(RWMessages.message("Flight off"));
            return;
        }
        if (!allowed(mc)) {
            if (mc.player != null) mc.player.displayClientMessage(RWMessages.message("Glide and fly need a Creative, singleplayer Real World world. Leave Street View or your vehicle first."), false);
            return;
        }
        if (enabled()) return;
        active = true; glide = false;
        pose.reset(mc.player.getYRot());
        FlightBoost.begin(mc.getSingleplayerServer(), mc.player.getUUID());
        mc.player.stopFallFlying();
        mc.player.getAbilities().flying = true;
        mc.player.onUpdateAbilities();
        var id = mc.player.getUUID(); var server = mc.getSingleplayerServer();
        server.execute(() -> { var player = server.getPlayerList().getPlayer(id); if (player != null && FlightBoost.active(player)) player.stopFallFlying(); });
        ActionBar.show(RWMessages.message(status() + " · /earth fly: off"));
    }
    private static void stop(Minecraft mc) {
        if (enabled() && mc.player != null) mc.player.setDeltaMovement(Vec3.ZERO);
        active = false; glide = false; velocity = Vec3.ZERO; speed = MAX_SPEED;
        FlightBoost.clear();
        // Leave ordinary Creative flight available so toggling off cannot drop the player.
    }
    private static void tick(Minecraft mc) {
        if (!enabled()) return;
        if (!allowed(mc)) { stop(mc); return; }
        if (mc.isPaused()) return;
        FlightBoost.tick();
        if (!mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        mc.player.fallDistance = 0;
        if (mc.screen != null) { velocity = Vec3.ZERO; glide = false; pose.film(false, Vec3.ZERO, mc.player.getYRot()); return; }
        var arrows = PanoramaRenderer.INSTANCE;
        adjustSpeed((arrows.flightSpeedKey(true).isDown() ? 1 : 0) - (arrows.flightSpeedKey(false).isDown() ? 1 : 0));
        int forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        int right = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
        int vertical = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
        forward = rocketForward(forward, right, vertical);
        Vec3 direction = direction(forward, right, vertical, mc.player.getYRot(), mc.player.getXRot());
        Vec3 target = direction.scale(MAX_SPEED * speedFactor());
        velocity = velocity.lerp(target, .18);
        if (velocity.lengthSqr() < .00001) velocity = Vec3.ZERO;
        updateStance();
        pose.film(gliding(), velocity, mc.player.getYRot());
    }
    private static void updateStance() {
        // Separate thresholds prevent pose chatter near hovering speed.
        glide = velocity.horizontalDistance() > (glide ? .25 : .65);
    }
    static void adjustSpeed(int direction) { speed = net.minecraft.util.Mth.clamp(speed + Integer.signum(direction) * .045, MIN_SPEED, MAX_SPEED); }
    static Vec3 direction(int forward, int right, int vertical, float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
        Vec3 direction = new Vec3(-Math.sin(yaw) * Math.cos(pitch) * forward - Math.cos(yaw) * right,
            vertical - Math.sin(pitch) * forward, Math.cos(yaw) * Math.cos(pitch) * forward - Math.sin(yaw) * right);
        return direction.lengthSqr() > 1 ? direction.normalize() : direction;
    }
    public static boolean travel(Player player) {
        var mc = Minecraft.getInstance();
        if (!posed(player) || !allowed(mc) || SpaceFlight.enabled()) return false;
        player.setDeltaMovement(velocity);
        player.move(MoverType.SELF, velocity);
        if (player.horizontalCollision) velocity = new Vec3(0, velocity.y, 0);
        if (player.verticalCollision) velocity = new Vec3(velocity.x, 0, velocity.z);
        player.setDeltaMovement(velocity);
        updateStance();
        player.fallDistance = 0;
        return true;
    }
}
