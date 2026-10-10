package dev.realworldmc.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.realworldmc.client.PanoramaRenderer;
import dev.realworldmc.client.PhotoTerrainMask;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelRenderer.class)
public abstract class PhotoTerrainMixin {
    @WrapMethod(method="renderSectionLayer")
    private void realworldmc$photoDepth(RenderType layer,double x,double y,double z,Matrix4f view,Matrix4f projection,Operation<Void> original){
        if(!PanoramaRenderer.INSTANCE.active()){original.call(layer,x,y,z,view,projection);return;}
        try(MemoryStack stack=MemoryStack.stackPush()){
            var mask=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);
            boolean previous=PhotoTerrainMask.writingDepthOnly;
            PhotoTerrainMask.writingDepthOnly=true;
            try{RenderSystem.colorMask(false,false,false,false);original.call(layer,x,y,z,view,projection);}
            finally{PhotoTerrainMask.writingDepthOnly=previous;RenderSystem.colorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);}
        }
    }
}
