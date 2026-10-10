package dev.realworldmc.client;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.EmptyBlockGetter;

/** Exercise the real, mod-transformed camera without a save or provider connection. */
final class CameraChecks {
    static void run(Minecraft mc) {
        var commands=new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.invoker().register(commands,null);
        for(String input:java.util.List.of("earth goto 41.4036, 2.1744","earth streetview","earth where","earth back","earth help","earth setlocation 44.427454 26.087287")) {
            try {
                commands.execute(input,null);
                throw new AssertionError("Client intercepted server command: "+input);
            } catch(com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                if(error.getType()!=com.mojang.brigadier.exceptions.CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand())
                    throw new AssertionError("Fabric would swallow this server command instead of forwarding it: "+input,error);
            }
        }
        if(commands.getRoot().getChild("rwmc")!=null)throw new AssertionError("No separate camera root should be registered");
        var original=mc.options.getCameraType();
        var subject=new ArmorStand(null,0,70,0);
        subject.xo=0;subject.yo=70;subject.zo=0;
        try {
            for(var type:CameraType.values()){
                mc.options.setCameraType(type);
                PanoramaRenderer.INSTANCE.receive(new dev.realworldmc.PanoramaPayload(0,"camera","2",0,0,0));
                if(mc.options.getCameraType()!=type)throw new AssertionError("Camera selection was overridden: "+type);
                var camera=new Camera();
                camera.setup(EmptyBlockGetter.INSTANCE,subject,!type.isFirstPerson(),type.isMirrored(),1);
                if(camera.isDetached()==type.isFirstPerson())throw new AssertionError("Wrong camera attachment: "+type);
                double horizontal=Math.hypot(camera.getPosition().x,camera.getPosition().z);
                if(type.isFirstPerson() ? horizontal>.01 : horizontal<3.5)
                    throw new AssertionError("Camera stayed inside the subject: "+type+" offset="+horizontal);
            }
        } finally { mc.options.setCameraType(original); }
    }
}
