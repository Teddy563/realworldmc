package dev.realworldmc.client.mixin;

import dev.realworldmc.EarthConfig;
import dev.realworldmc.client.MapRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pushes the far plane out so the 3D map is visible far beyond Minecraft's render distance. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "pick(F)V", at = @At("HEAD"), cancellable = true)
	private void realworldmc$orbitalPicking(float partial, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (!dev.realworldmc.client.SpaceView.active() || mc.player == null) return;
		var eye = mc.player.getEyePosition(partial);
		mc.crosshairPickEntity = null;
		mc.hitResult = net.minecraft.world.phys.BlockHitResult.miss(eye, net.minecraft.core.Direction.DOWN, net.minecraft.core.BlockPos.containing(eye));
		ci.cancel();
	}

	@Inject(method = "getDepthFar", at = @At("RETURN"), cancellable = true)
	private void realworldmc$farPlane(CallbackInfoReturnable<Float> cir) {
		if (MapRenderer.INSTANCE.enabled()) {
			cir.setReturnValue(Math.max(cir.getReturnValueF(), EarthConfig.get().mapRenderDistance));
		}
	}
}
