package dev.realworldmc;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.resources.RegistryOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import java.util.Optional;

public final class PortChecks implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE)
    public void generatorSurvivesSaveRoundTrip(GameTestHelper test) {
        var generator=new EarthChunkGenerator(test.getLevel().getChunkSource().getGenerator().getBiomeSource(),Optional.of(44.427454),Optional.of(26.087287),Optional.of(80.0));
        var ops=RegistryOps.create(JsonOps.INSTANCE,test.getLevel().registryAccess());
        var encoded=ChunkGenerator.CODEC.encodeStart(ops,generator).getOrThrow(false,message->{throw new AssertionError(message);});
        var decoded=ChunkGenerator.CODEC.parse(ops,encoded).getOrThrow(false,message->{throw new AssertionError(message);});
        test.assertTrue(decoded instanceof EarthChunkGenerator,"Saved world must retain its Earth generator");
        var earth=(EarthChunkGenerator)decoded;
        test.assertTrue(earth.latitude==44.427454 && earth.longitude==26.087287 && earth.groundHeightOrNaN()==80,"Saved geographic origin must remain exact");
        test.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE)
    public void savedRegionsAndMovementStaySeparate(GameTestHelper test) {
        var storage=new EarthRegions.Storage();
        storage.saved.add(new EarthRegions.Region(0,40.7,-74,10));
        storage.saved.add(new EarthRegions.Region(1,45.8,15.9,120));
        var restored=EarthRegions.Storage.load(storage.save(new CompoundTag()));
        test.assertTrue(restored.saved.equals(storage.saved),"Region identities survive saving");
        var selected=EarthRegions.at(restored.saved,20_999,0);
        double step=50_000*RegionBoundary.fraction(20_999,0,50_000,0);
        test.assertTrue(EarthRegions.at(restored.saved,20_999+step,0).equals(selected),"Flight must not select unrelated neighboring region");
        test.assertTrue(EarthRegions.at(restored.saved,25_000,0)==null,"Region gap is not geographic terrain");
        test.succeed();
    }
}
