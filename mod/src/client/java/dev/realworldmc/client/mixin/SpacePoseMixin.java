package dev.realworldmc.client.mixin;

import dev.realworldmc.client.SpaceFlight;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The body follows flight input while the camera remains free to look around. */
@Mixin(PlayerRenderer.class)
public class SpacePoseMixin {
    @Redirect(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isFallFlying()Z"))
    private boolean realworldmc$glide(AbstractClientPlayer player) { return !SpaceFlight.posed(player) && player.isFallFlying(); }
    @Inject(method="setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",at=@At("TAIL"))
    private void realworldmc$flightRotation(AbstractClientPlayer player,PoseStack stack,float age,float bodyYaw,float partial,float scale,CallbackInfo ci) {
        if(!SpaceFlight.posed(player))return;
        var pose=SpaceFlight.pose();
        stack.mulPose(Axis.YP.rotationDegrees(bodyYaw-pose.yaw(partial)));
        // Rotate around the torso instead of swinging Steve's feet around the camera anchor.
        stack.translate(0,.9*scale,0);
        stack.mulPose(Axis.XP.rotationDegrees(-pose.pitch(partial)));
        stack.mulPose(Axis.ZP.rotationDegrees(pose.bank(partial)));
        stack.translate(0,-.9*scale,0);
    }
}
