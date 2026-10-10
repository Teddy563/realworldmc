package dev.realworldmc.mixin;
import dev.realworldmc.StreetEdits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class StreetEditsMixin {
    @Inject(method="sendBlockUpdated",at=@At("HEAD"))
    private void realworldmc$recordEdit(BlockPos pos,BlockState before,BlockState after,int flags,CallbackInfo ci){
        StreetEdits.changed((ServerLevel)(Object)this,pos,before,after);
    }
}
