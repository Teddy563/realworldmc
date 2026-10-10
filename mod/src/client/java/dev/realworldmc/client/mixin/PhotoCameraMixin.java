package dev.realworldmc.client.mixin;

import dev.realworldmc.client.PanoramaRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Follow the native camera toggle; limit third-person offset around the photo viewpoint. */
@Mixin(value=Camera.class,priority=900)
public abstract class PhotoCameraMixin {
    @Shadow protected abstract void setPosition(double x,double y,double z);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Inject(method="setup",at=@At("TAIL"))
    private void realworldmc$photoCamera(BlockGetter world,Entity entity,boolean thirdPerson,boolean mirrored,float partialTick,CallbackInfo ci) {
        var photo=PanoramaRenderer.INSTANCE;
        var player=Minecraft.getInstance().player;
        if (!photo.active() || player==null || entity!=player) return;
        // Camera perspective is independent of walking. Never pin a detached camera
        // back inside the player, whether selected with F5 or a command.
        if (photo.walking() || thirdPerson) {
            if(thirdPerson){
                var camera=(Camera)(Object)this;
                var eye=player.getEyePosition(partialTick);
                var offset=camera.getPosition().subtract(eye);
                // Vanilla has already clipped this camera against blocks. Never lengthen that result.
                if(offset.lengthSqr()>2.5*2.5){var position=eye.add(offset.normalize().scale(2.5));setPosition(position.x,position.y,position.z);}
            }
            return;
        }
        var anchor=photo.cameraAnchor();
        setPosition(anchor.x(),anchor.y(),anchor.z());
        setRotation(player.getViewYRot(partialTick),player.getViewXRot(partialTick));
    }
}
