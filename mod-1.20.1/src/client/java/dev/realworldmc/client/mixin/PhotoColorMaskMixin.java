package dev.realworldmc.client.mixin;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.realworldmc.client.PhotoTerrainMask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** RenderType setup/reset must not turn colour writes back on inside the depth-only scope. */
@Mixin(GlStateManager.class)
public abstract class PhotoColorMaskMixin {
    @ModifyVariable(method="_colorMask",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private static boolean realworldmc$red(boolean value){return value && !PhotoTerrainMask.writingDepthOnly;}
    @ModifyVariable(method="_colorMask",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private static boolean realworldmc$green(boolean value){return value && !PhotoTerrainMask.writingDepthOnly;}
    @ModifyVariable(method="_colorMask",at=@At("HEAD"),argsOnly=true,ordinal=2)
    private static boolean realworldmc$blue(boolean value){return value && !PhotoTerrainMask.writingDepthOnly;}
    @ModifyVariable(method="_colorMask",at=@At("HEAD"),argsOnly=true,ordinal=3)
    private static boolean realworldmc$alpha(boolean value){return value && !PhotoTerrainMask.writingDepthOnly;}
}
