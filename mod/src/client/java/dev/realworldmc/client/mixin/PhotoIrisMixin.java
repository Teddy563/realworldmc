package dev.realworldmc.client.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A photograph already contains lighting. Use Iris's vanilla pipeline for this dimension only. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.Iris",remap=false)
public abstract class PhotoIrisMixin {
    @Inject(method="createPipeline",at=@At("HEAD"),cancellable=true,require=0)
    private static void realworldmc$photoPipeline(@Coerce Object dimension,CallbackInfoReturnable<Object> cir){
        if(!"earthcraft:streetworld".equals(dimension.toString()))return;
        try{cir.setReturnValue(Class.forName("net.irisshaders.iris.pipeline.VanillaRenderingPipeline").getConstructor().newInstance());}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Could not select the Street View photo rendering pipeline",e);}
    }
}
