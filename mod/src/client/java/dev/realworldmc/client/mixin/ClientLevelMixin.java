package dev.realworldmc.client.mixin;

import dev.realworldmc.client.MapRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the map renderer about mined/placed blocks so the mesh can be cut open. */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void realworldmc$blockChanged(BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo ci) {
		MapRenderer.INSTANCE.onBlockChanged(pos, before, after);
	}
}
