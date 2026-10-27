package dev.realworldmc.mixin;
import dev.realworldmc.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public class RegionMoveMixin {
    @ModifyVariable(method="move",at=@At("HEAD"),argsOnly=true)
    private Vec3 realworldmc$keepGeographicFrame(Vec3 motion) {
        Entity entity=(Entity)(Object)this;
        if(!(entity instanceof Player) || entity.level().dimension()!=Level.OVERWORLD)return motion;
        EarthRegions.Region region;
        if(entity instanceof ServerPlayer player) {
            if(!(player.level().getChunkSource().getGenerator() instanceof EarthChunkGenerator))return motion;
            region=EarthRegions.at(entity.getX(),entity.getZ());
        } else region=EarthRegions.at(RegionTravel.clientRegions,entity.getX(),entity.getZ());
        if(region==null)return motion;
        double fraction=RegionBoundary.fraction(entity.getX()-region.offsetX(),entity.getZ()-region.offsetZ(),motion.x,motion.z);
        return fraction==1?motion:new Vec3(motion.x*fraction,motion.y,motion.z*fraction);
    }
}
