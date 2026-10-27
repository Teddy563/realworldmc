package dev.realworldmc.client.mixin;

import dev.realworldmc.client.SpaceFlight;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class SpaceModelMixin {
    @Shadow @Final public ModelPart head,hat,rightArm,leftArm,rightLeg,leftLeg;
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void realworldmc$heroPose(LivingEntity entity,float walk,float amount,float age,float headYaw,float headPitch,CallbackInfo ci) {
        if(!SpaceFlight.posed(entity))return;
        var pose=SpaceFlight.pose();
        float partial=Mth.clamp(age-entity.tickCount,0,1), strength=pose.thrust(partial);
        float flutter=Mth.sin(age*.12f)*.035f, tilt=pose.pitch(partial);
        if (dev.realworldmc.client.CreativeFlight.enabled()) {
            // Elytra-like arms trail the body; hover keeps the upright, relaxed silhouette.
            rightArm.xRot=leftArm.xRot=-.10f;
            rightArm.yRot=leftArm.yRot=0;
            rightArm.zRot=.08f+strength*.25f; leftArm.zRot=-rightArm.zRot;
            rightLeg.xRot=.02f+flutter; leftLeg.xRot=-.02f-flutter;
            rightLeg.yRot=leftLeg.yRot=rightLeg.zRot=leftLeg.zRot=0;
            head.xRot=(float)Math.toRadians(Mth.clamp(headPitch-tilt,-80,65));
            head.yRot=(float)Math.toRadians(Mth.clamp(Mth.wrapDegrees(entity.getYRot()-pose.yaw(partial)),-65,65));
            hat.copyFrom(head);
            return;
        }
        // Both hands lead a vertical ascent/descent; one leads during horizontal cruise.
        float horizontal=(float)Math.sin(Math.toRadians(tilt));
        rightArm.xRot=Mth.lerp(strength,-.12f,-(float)Math.PI+.08f)+flutter;
        leftArm.xRot=Mth.lerp(strength,-.12f,-(float)Math.PI+.08f+horizontal*.65f)-flutter;
        rightArm.zRot=.10f; leftArm.zRot=-.10f; rightArm.yRot=leftArm.yRot=0;
        rightLeg.xRot=.04f+flutter;leftLeg.xRot=-.04f-flutter;
        rightLeg.yRot=leftLeg.yRot=0;rightLeg.zRot=.025f;leftLeg.zRot=-.025f;
        head.xRot=(float)Math.toRadians(Mth.clamp(headPitch-tilt,-80,65));
        head.yRot=(float)Math.toRadians(Mth.clamp(Mth.wrapDegrees(entity.getYRot()-pose.yaw(partial)),-65,65));
        hat.copyFrom(head);
    }
}
