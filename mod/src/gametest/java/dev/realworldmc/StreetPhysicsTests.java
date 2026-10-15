package dev.realworldmc;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

public final class StreetPhysicsTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE)
    public void styledMessagesAndLoadingCleanup(GameTestHelper test) {
        var message=RWMessages.message("Try /earth setup or /rw goto.");
        test.assertTrue(message.getString().equals("RW | Try /earth setup or /rw goto."),"Message text must stay readable");
        java.util.Map<String,Integer> colours=new java.util.HashMap<>();
        message.visit((style,text)->{ if(style.getColor()!=null)colours.put(text,style.getColor().getValue());return java.util.Optional.empty(); },net.minecraft.network.chat.Style.EMPTY);
        test.assertTrue(colours.get("RW")==RWMessages.GREEN && colours.get(" | ")==0x555555,"Brand prefix and separator colours");
        test.assertTrue(colours.get("/earth setup")==0xffff55 && colours.get("/rw goto")==0xffff55 && colours.get("Try ")==0xffffff,"Commands yellow, body white");
        var line=RWMessages.helpLine("/earth goto <place>","Travel");
        test.assertTrue(line.getSiblings().getFirst().getStyle().getClickEvent().getValue().equals("/earth goto "),"Help fills chat without executing a command");
        var player=test.makeMockServerPlayerInLevel();
        var source=player.createCommandSourceStack();
        var task=TravelLoading.start(source);
        test.assertTrue(task!=null && TravelLoading.start(source)==null,"Concurrent travel cannot replace its loading bar");
        task.stage("Preparing arrival",3);
        TravelLoading.disconnect(player.getUUID());
        test.assertTrue(!task.active(),"Disconnect must remove the bar and reject late completion");
        var next=TravelLoading.start(source);test.assertTrue(next!=null,"A finished trip permits another");next.close();
        test.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE)
    public void cameraCommandsAreRemoved(GameTestHelper test) throws Exception {
        var source=test.getLevel().getServer().createCommandSourceStack().withLevel(test.getLevel());
        var commands=source.getServer().getCommands().getDispatcher();
        for(String command:java.util.List.of("rw", "earth", "rw goto 41.4036, 2.1744", "rw streetview", "rw where", "rw back", "rw help", "earth goto 41.4036, 2.1744","earth streetview","earth where","earth back","earth help", "earth setlocation random", "rw setlocation random")) {
            var parsed=commands.parse(command,source);
            test.assertTrue(!parsed.getReader().canRead() && parsed.getContext().getLastChild().getCommand()!=null,"Server must accept command: "+command);
        }
        for(String root:java.util.List.of("earth","rw")) for(String mode:java.util.List.of("fly","hud")) for(String suffix:java.util.List.of(""," on"," off")) {
            String command=root+" "+mode+suffix;
            var parsed=commands.parse(command,source);
            test.assertTrue(!parsed.getReader().canRead() && parsed.getContext().getLastChild().getCommand()!=null,"Flight autocomplete must accept: "+command);
        }
        for(String root:java.util.List.of("earth","rw")) for(String removed:java.util.List.of("glide","actionbar")) {
            var parsed=commands.parse(root+" "+removed,source);
            test.assertTrue(parsed.getReader().canRead(),"Removed command must be rejected: "+removed);
        }
        for(String root:java.util.List.of("earth","rw")) for(int view:new int[]{1,2,3}) {
            try {commands.execute(root+" camera "+view,source);test.assertTrue(false,"Removed camera command must be rejected");}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
        }
        test.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE)
    public void randomDefaultsAndSettingsMigration(GameTestHelper test) throws Exception {
        var directory=java.nio.file.Files.createTempDirectory("realworldmc-config-check-");
        var oldFile=directory.resolve("earthcraft.json");var newFile=directory.resolve("realworldmc.json");
        try {
            var fresh=EarthConfig.load(directory);
            test.assertTrue(fresh.randomSpawn,"New installs must choose from the random catalogue");
            java.nio.file.Files.delete(newFile);
            String existing="{\"latitude\":40.75,\"longitude\":-73.98,\"serviceDir\":\"custom-service\",\"mapRenderDistance\":9000,\"photoEyeHeight\":1.8}";
            java.nio.file.Files.writeString(oldFile,existing);
            var migrated=EarthConfig.load(directory);
            test.assertTrue(!migrated.randomSpawn && migrated.latitude==40.75 && migrated.mapRenderDistance==9000 && migrated.photoEyeHeight==1.8 && migrated.serviceDir.equals("custom-service"),"Migration must preserve customized settings");
            test.assertTrue(java.nio.file.Files.readString(newFile).equals(existing) && java.nio.file.Files.readString(oldFile).equals(existing),"Import must preserve the complete original JSON");
            java.nio.file.Files.writeString(newFile,"{\"latitude\":44.427454,\"longitude\":26.087287}");
            test.assertTrue(EarthConfig.load(directory).latitude==44.427454 && EarthConfig.load(directory).randomSpawn,"Former Palace defaults migrate to random starts; current config takes precedence");
            for(String invalid:java.util.List.of("null","{")) {
                java.nio.file.Files.writeString(newFile,invalid);
                test.assertTrue(EarthConfig.load(directory)!=null && java.nio.file.Files.readString(newFile).equals(invalid),"Invalid settings must not crash or overwrite the original file");
            }
            var biome=test.getLevel().getChunkSource().getGenerator().getBiomeSource();
            EarthChunkGenerator randomized;
            boolean previousRandom=EarthConfig.get().randomSpawn;
            try {
                EarthConfig.get().randomSpawn=true;
                randomized=new EarthChunkGenerator(biome,java.util.Optional.empty(),java.util.Optional.empty(),java.util.Optional.empty());
            } finally { EarthConfig.get().randomSpawn=previousRandom; }
            test.assertTrue(SpawnLocations.ALL.stream().anyMatch(p -> p.latitude()==randomized.latitude && p.longitude()==randomized.longitude),"New generator must use one complete catalogue entry");
            var saved=new EarthChunkGenerator(biome,java.util.Optional.of(52.5),java.util.Optional.of(13.4),java.util.Optional.of(80d));
            var ops=RegistryOps.create(JsonOps.INSTANCE,test.getLevel().registryAccess());
            var randomEncoded=EarthChunkGenerator.CODEC.codec().encodeStart(ops,randomized).getOrThrow();
            for(int i=0;i<20;i++) {
                var reload=EarthChunkGenerator.CODEC.codec().parse(ops,randomEncoded).getOrThrow();
                test.assertTrue(reload.latitude==randomized.latitude && reload.longitude==randomized.longitude,"Saved random origin must never reroll on load");
            }
            var encoded=EarthChunkGenerator.CODEC.codec().encodeStart(ops,saved).getOrThrow();
            var restored=EarthChunkGenerator.CODEC.codec().parse(ops,encoded).getOrThrow();
            test.assertTrue(restored.latitude==52.5 && restored.longitude==13.4,"Changing the new-world default must not relocate saved terrain");
            test.assertTrue(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("realworldmc"),"The current mod must load under the RealWorldMC ID");
        } finally {
            java.nio.file.Files.deleteIfExists(newFile);java.nio.file.Files.deleteIfExists(oldFile);java.nio.file.Files.delete(directory);
        }
        test.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE)
    public void streetViewUsesSavedGeography(GameTestHelper test) throws Exception {
        var region=new EarthRegions.Region(401,44.428582856832655,26.10111463938365,90);
        double lat=region.latitude()+.001,lon=region.longitude()+.002;
        var local=Geo.geodeticToMc(region.latitude(),region.longitude(),region.groundHeight(),lat,lon,1200);
        var coordinates=RealWorldMC.coordinatesInRegion(region,new net.minecraft.world.phys.Vec3(region.offsetX()+local[0],local[1],region.offsetZ()+local[2]));
        test.assertTrue(Math.abs(coordinates[0]-lat)<1e-8 && Math.abs(coordinates[1]-lon)<1e-8,"Nearby photos must use the player's flight position, subtracting both saved region offsets");
        var flat=new EarthRegions.Region(1,lat,lon,Double.NaN);
        var onPhoto=RealWorldMC.coordinatesInRegion(flat,new net.minecraft.world.phys.Vec3(flat.offsetX(),64,flat.offsetZ()));
        test.assertTrue(Math.abs(onPhoto[0]-lat)<1e-8 && Math.abs(onPhoto[1]-lon)<1e-8,"Simulated Street View ground must retain its geographic origin");
        var server=test.getLevel().getServer();
        var source=server.createCommandSourceStack().withLevel(test.getLevel());
        test.assertTrue(RealWorldMC.currentCoordinates(source)==null,"Ordinary worlds must not invent Earth coordinates");
        test.assertTrue(RealWorldMC.currentCoordinates(source.withLevel(server.getLevel(Level.NETHER)))==null,"The Nether must not reuse Overworld coordinates");
        var dispatcher=server.getCommands().getDispatcher();
        var command=dispatcher.getRoot().getChild("rw").getChild("streetview");
        test.assertTrue(command.getCommand()!=null && command.getChild("here")!=null && command.getChild("browser")!=null,"Current-position, here and browser routes must all be registered");
        test.assertTrue(dispatcher.execute("earth streetview",source)==0,"A console must not report a browser launch as successful photo travel");
        test.assertTrue(dispatcher.parse("earth streetview 44.4, 26.1",source).getContext().getLastChild().getCommand()!=null,"Explicit coordinates must remain supported");
        test.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE)
    public void nearbyPickupRespectsVanillaRules(GameTestHelper test) {
        var level=test.getLevel();var player=test.makeMockServerPlayerInLevel();
        var diamond=net.minecraft.world.item.Items.DIAMOND;
        var base=test.absolutePos(new BlockPos(1,20,1));
        int px=base.getX(),py=base.getY(),pz=base.getZ();
        player.teleportTo(px+.5,py,pz+.5);player.setNoGravity(true);
        for(int x=px-3;x<=px+4;x++)for(int y=py-1;y<=py+3;y++)for(int z=pz-3;z<=pz+4;z++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        player.getInventory().clearContent();
        var item=new net.minecraft.world.entity.item.ItemEntity(level,px+2.5,py+.2,pz+.5,new net.minecraft.world.item.ItemStack(diamond,3));
        item.setNoGravity(true);item.setDeltaMovement(0,0,0);item.setPickUpDelay(100);level.addFreshEntity(item);
        // The server must register the moved player's new entity-tracking chunks first.
        test.runAfterDelay(20,()->{
        test.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,player.getBoundingBox().inflate(3)).contains(item),"Pickup fixture must have a tracked nearby item");
        PanoramaRoom.collectNearby(player);
        test.assertTrue(!item.isRemoved() && player.getInventory().countItem(diamond)==0,"Freshly dropped items must retain their pickup delay");
        item.setNoPickUpDelay();item.setTarget(java.util.UUID.randomUUID());
        PanoramaRoom.collectNearby(player);
        test.assertTrue(!item.isRemoved(),"Pickup must respect an item's target owner");
        item.setTarget(player.getUUID());
        for(int y=py;y<py+3;y++)level.setBlockAndUpdate(new BlockPos(px+1,y,pz),Blocks.STONE.defaultBlockState());
        PanoramaRoom.collectNearby(player);
        test.assertTrue(!item.isRemoved(),"Nearby assistance must not collect through walls");
        for(int y=py;y<py+3;y++)level.setBlockAndUpdate(new BlockPos(px+1,y,pz),Blocks.AIR.defaultBlockState());
        test.assertTrue(player.distanceToSqr(item)<=9 && player.hasLineOfSight(item),"Pickup fixture must be visible and within the assistance radius");
        PanoramaRoom.collectNearby(player);
        test.assertTrue(item.isRemoved() && player.getInventory().countItem(diamond)==3,"Visible nearby items must be picked up exactly once");
        PanoramaRoom.collectNearby(player);
        test.assertTrue(player.getInventory().countItem(diamond)==3,"Repeating assistance must not duplicate items");
        for(int i=0;i<36;i++)player.getInventory().setItem(i,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,64));
        var extra=new net.minecraft.world.entity.item.ItemEntity(level,px+2.5,py+.2,pz+.5,new net.minecraft.world.item.ItemStack(diamond));
        extra.setNoPickUpDelay();level.addFreshEntity(extra);PanoramaRoom.collectNearby(player);
        test.assertTrue(!extra.isRemoved() && extra.getItem().getCount()==1,"Full inventories must leave items in the world");
        extra.discard();player.discard();test.succeed();
        });
    }
    @GameTest(template=EMPTY_STRUCTURE)
    public void damageDoesNotMoveThePhotoCamera(GameTestHelper test) {
        var level=test.getLevel();var floor=new BlockPos(40,63,40);
        level.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(floor.above(),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(floor.above(2),Blocks.AIR.defaultBlockState());
        double before=PanoramaRoom.landingY(level,40,40);
        var edits=StreetEdits.storage(level);
        edits.record(floor,Blocks.STONE.defaultBlockState());
        edits.record(floor.above(),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(floor,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(floor.above(),Blocks.TNT.defaultBlockState());
        test.assertTrue(before==64 && PanoramaRoom.landingY(level,40,40)==before,"Mining or placing blocks at a stop must not change its camera ground baseline");
        var cfg=new EarthConfig();cfg.photoEyeHeight=Double.NaN;
        test.assertTrue(cfg.photoEyeHeight()==2.5,"An invalid camera height must not produce invalid teleport coordinates");
        test.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,timeoutTicks=200)
    public void missingCoverageDoesNotCrashWorldLoading(GameTestHelper test) throws Exception {
        var http=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var count=new java.util.concurrent.atomic.AtomicInteger();
        http.createContext("/chunk",exchange->{
            count.incrementAndGet();
            byte[] response="{\"error\":\"No usable 3D terrain in this area. Try another location; no flat replacement was generated.\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404,response.length);
            try(var output=exchange.getResponseBody()){output.write(response);}
        });
        http.start();var config=EarthConfig.get();String previous=config.serviceUrl;
        try {
            config.serviceUrl="http://127.0.0.1:"+http.getAddress().getPort();
            var level=test.getLevel();var biome=level.getChunkSource().getGenerator().getBiomeSource();
            var generator=new EarthChunkGenerator(biome,java.util.Optional.of(0d),java.util.Optional.of(0d),java.util.Optional.of(0d)){
                @Override public EarthRegions.Region regionFor(double x,double z){return new EarthRegions.Region(0,0,0,0);}
            };
            var proto=new ProtoChunk(new net.minecraft.world.level.ChunkPos(6250,-17),UpgradeData.EMPTY,level,level.registryAccess().registryOrThrow(Registries.BIOME),null);
            generator.fillFromNoise(null,null,null,proto).join();
            test.assertTrue(count.get()==1,"Confirmed absent coverage must not retry and fail world generation");
            test.assertTrue(proto.getBlockState(new BlockPos(100000,63,-272)).isAir(),"Missing Earth data must not become fabricated flat terrain");
            var street=new StreetChunkGenerator(biome);
            test.assertTrue(street.noCoverage(proto).length==0 && street.incomplete(EarthRegions.indexAt(100000,-272)),"Street View must retain its explicit simulated-ground fallback");
            test.assertTrue(!new ServiceClient.ServiceException(403,"{\"error\":\"No usable 3D terrain in this area.\"}").noTerrainCoverage(),"Authentication failures must not save empty terrain");
            test.assertTrue(!new ServiceClient.ServiceException(404,"{\"error\":\"not found\"}").noTerrainCoverage(),"Unrelated 404 errors must not be mistaken for map coverage");
            test.assertTrue(new ServiceClient.ServiceException(404,"{\"code\":\"NO_TERRAIN_COVERAGE\"}").noTerrainCoverage(),"Structured coverage errors must be supported");
        } finally {config.serviceUrl=previous;http.stop(0);}
        test.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE)
    public void photoEditsKeepTheirOriginalState(GameTestHelper test){
        var data=new StreetEdits.Storage();BlockPos wall=new BlockPos(50002,65,3),placed=new BlockPos(50003,64,3);
        data.record(wall,Blocks.STONE.defaultBlockState());
        // Filling an excavated cell and breaking it again must not lose its original wall.
        data.record(wall,Blocks.TNT.defaultBlockState());
        data.record(placed,Blocks.AIR.defaultBlockState());data.importedChunks.add(42L);
        var saved=data.save(new CompoundTag(),test.getLevel().registryAccess());
        var restored=StreetEdits.Storage.load(saved,test.getLevel().registryAccess());
        test.assertTrue(restored.original.get(wall.asLong()).is(Blocks.STONE),"Excavations must survive placement, another break, and save/reload");
        test.assertTrue(restored.original.get(placed.asLong()).isAir(),"Removing a block placed in empty space must not tear a hole in the photo");
        test.assertTrue(restored.importedChunks.contains(42L),"Legacy comparison must not run again over changed terrain");
        var buffer=io.netty.buffer.Unpooled.buffer();
        try{
            var payload=new StreetEditsPayload(true,java.util.List.of(new StreetEditsPayload.Edit(wall.asLong(),net.minecraft.world.level.block.Block.getId(Blocks.STONE.defaultBlockState()))));
            StreetEditsPayload.CODEC.encode(buffer,payload);
            test.assertTrue(StreetEditsPayload.CODEC.decode(buffer).equals(payload),"Photo edits must preserve world coordinates across the network");
            buffer.clear();buffer.writeBoolean(false);buffer.writeInt(StreetEditsPayload.MAX_BATCH+1);
            boolean rejected=false;try{StreetEditsPayload.CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}
            test.assertTrue(rejected,"Unbounded edit packets must be rejected");
        }finally{buffer.release();}
        test.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,timeoutTicks=200)
    public void physicalGroundExplosionsAndSavedDamage(GameTestHelper test) throws Exception {
        // The vanilla GameTest preset creates only three dimensions. Decode the actual shipped
        // dimension and generate its blocks in an isolated area of the test world instead.
        var level=test.getLevel();
        var resource=level.getServer().getResourceManager().getResource(RealWorldMC.id("dimension/streetworld.json")).orElseThrow();
        LevelStem stem;
        try(var reader=resource.openAsReader()) { stem=LevelStem.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,level.registryAccess()),JsonParser.parseReader(reader)).getOrThrow(); }
        test.assertTrue(stem.generator() instanceof StreetChunkGenerator,"Shipped dimension must decode to the physical generator");
        var generator=(StreetChunkGenerator)stem.generator();
        var chunk=level.getChunk(0,0);
        var proto=new ProtoChunk(chunk.getPos(),UpgradeData.EMPTY,level,level.registryAccess().registryOrThrow(Registries.BIOME),null);
        generator.fillFromNoise(null,null,null,proto).join();
        for(int y=-64;y<100;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            BlockPos pos=new BlockPos(x,y,z);chunk.setBlockState(pos,proto.getBlockState(pos),false);
        }
        test.assertTrue(level.getBlockState(new BlockPos(3,54,3)).is(Blocks.STONE),"Ground must be deep enough for TNT craters");
        test.assertTrue(level.getBlockState(new BlockPos(3,-64,3)).is(Blocks.BEDROCK),"Bottom bedrock must remain below the digging volume");
        var wall=generator.blockForCode(6);
        BlockPos wallPos=new BlockPos(7,64,3);
        level.setBlockAndUpdate(wallPos,wall);level.setBlockAndUpdate(wallPos.above(),wall);
        test.assertTrue(wall.getRenderShape()==RenderShape.MODEL,"Building surfaces must be visible, not legacy invisible collision blocks");
        var pig=EntityType.PIG.create(level);pig.setNoAi(true);pig.moveTo(6.5,64,3.5,0,0);level.addFreshEntity(pig);
        test.assertTrue(!level.noCollision(pig,pig.getBoundingBox().move(1,0,0)),"Mobs must collide with building blocks");
        BlockPos crater=new BlockPos(3,63,3);
        level.explode(null,3.5,63.5,3.5,4,Level.ExplosionInteraction.TNT);
        test.assertTrue(level.getBlockState(crater).isAir(),"TNT must excavate actual terrain");
        test.assertTrue(pig.getHealth()<pig.getMaxHealth(),"Explosion must affect nearby mobs");
        BlockPos placed=new BlockPos(12,65,12);level.setBlockAndUpdate(placed,Blocks.GOLD_BLOCK.defaultBlockState());
        CompoundTag saved=ChunkSerializer.write(level,chunk);
        var restored=ChunkSerializer.read(level,level.getPoiManager(),new RegionStorageInfo("test",PanoramaRoom.ROOM,"chunk"),chunk.getPos(),saved);
        test.assertTrue(restored.getBlockState(crater).isAir(),"Saved chunk must retain explosion damage");
        test.assertTrue(restored.getBlockState(placed).is(Blocks.GOLD_BLOCK),"Saved chunk must retain placed blocks");
        pig.discard();test.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE)
    public void geographicAnchorsSurviveSaving(GameTestHelper test) {
        var place=new EarthRegions.Region(1,44.428582856832655,26.10111463938365,Double.NaN);
        var storage=new PanoramaPlaces.Storage();storage.saved.add(place);
        CompoundTag data=storage.save(new CompoundTag(),test.getLevel().registryAccess());
        var restored=PanoramaPlaces.Storage.load(data,test.getLevel().registryAccess());
        var back=restored.saved.getFirst();
        test.assertTrue(back.latitude()==place.latitude() && back.longitude()==place.longitude(),"Photo coordinates must survive a world save exactly");
        test.assertTrue(back.offsetX()==place.offsetX() && back.offsetZ()==place.offsetZ(),"Restart must not move placed objects to another geographic cell");
        test.assertTrue(Double.isNaN(back.groundHeight()),"Simulated ground must not silently switch to a regenerated map");
        test.succeed();
    }
}
