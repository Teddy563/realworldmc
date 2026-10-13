package dev.realworldmc.client.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Photo lighting and orbit use isolated vanilla pipelines, preserving the selected shader pack. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.Iris",remap=false)
public abstract class PhotoIrisMixin {
    @Unique private static Object realworldmc$orbitDimension;
    @Inject(method="getCurrentDimension",at=@At("HEAD"),cancellable=true,require=0)
    private static void realworldmc$orbitPipelineId(CallbackInfoReturnable<Object> cir) {
        if(!dev.realworldmc.client.SpaceView.active())return;
        try {
            if(realworldmc$orbitDimension==null)realworldmc$orbitDimension=Class.forName("net.irisshaders.iris.shaderpack.materialmap.NamespacedId")
                .getConstructor(String.class,String.class).newInstance("realworldmc","orbit");
            cir.setReturnValue(realworldmc$orbitDimension);
        } catch(ReflectiveOperationException e) { throw new IllegalStateException("Could not select the RealWorldMC orbital pipeline",e); }
    }
    @Inject(method="createPipeline",at=@At("HEAD"),cancellable=true,require=0)
    private static void realworldmc$photoPipeline(@Coerce Object dimension,CallbackInfoReturnable<Object> cir){
        if(!"earthcraft:streetworld".equals(dimension.toString()) && !"realworldmc:orbit".equals(dimension.toString()))return;
        try{cir.setReturnValue(Class.forName("net.irisshaders.iris.pipeline.VanillaRenderingPipeline").getConstructor().newInstance());}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Could not select the Street View photo rendering pipeline",e);}
    }
}
