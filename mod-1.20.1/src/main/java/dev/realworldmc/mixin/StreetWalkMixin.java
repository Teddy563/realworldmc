package dev.realworldmc.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.realworldmc.StreetWalkMotion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public abstract class StreetWalkMixin {
    @WrapMethod(method="move")
    private void realworldmc$photoMotion(MoverType type, Vec3 motion, Operation<Void> original) {
        Entity entity=(Entity)(Object)this;
        if (!(entity instanceof Player player) || !StreetWalkMotion.active(player)) { original.call(type,motion); return; }
        boolean old=entity.noPhysics;
        try { entity.noPhysics=true; original.call(type,new Vec3(motion.x,0,motion.z)); }
        finally { entity.noPhysics=old; }
    }
}
