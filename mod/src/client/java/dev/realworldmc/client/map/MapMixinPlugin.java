package dev.realworldmc.client.map;

import dev.realworldmc.map.MapCompatibility;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** Does not load any Xaero classes when deciding which optional hooks to apply. */
public final class MapMixinPlugin implements IMixinConfigPlugin {
    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse(null);
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return MapCompatibility.apply(mixin, version("xaerominimap"), version("xaeroworldmap"));
    }
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
}
