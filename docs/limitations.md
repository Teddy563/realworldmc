# Limitations

[Back to README](../README.md)

## Maps and blocks

- Scenery streams from a provider. Coverage, detail, age, and loading speed vary by location.
- Collision blocks approximate the 3D mesh. Trees, walls, entrances, water, and slopes can disagree with the image.
- There are no measured building interiors or underground geology. Generated dirt, stone, and inferred floors are simulated.
- Destinations occupy separate areas of one save. This is not a continuous, fully generated Earth.
- Vanilla ores, structures, and complete survival terrain are not generated.
- In 1.21.1 Earth mode, confirmed missing 3D coverage creates empty collision chunks. Other service failures can interrupt world loading. Saved chunks are not automatically regenerated.

## Street View

- The photo covers untouched terrain. You see Minecraft textures where blocks are placed or broken.
- Only actual collision geometry is breakable. A photographed wall does not supply its depth or interior.
- If 3D terrain is unavailable, Street View uses simulated deep ground. Photo objects can lack collision.
- Walking and third person use an approximate flat-ground projection. Close walls and slopes can distort.
- Photos are stitched up to 4096×2048, depending on source quality. Higher resolution cannot recover missing source detail.
- Returning to a stop keeps its edits. Replacing saved terrain to improve alignment could erase builds, so the mod does not do that automatically.

## Versions and performance

- Use the exact 1.21.1 or 26.2 build. Intermediate Minecraft versions are not supported by these artifacts.
- 26.2 is experimental: native photo gameplay is absent, distant terrain can clip, and shader compatibility is unverified.
- On 1.21.1, coarse map tiles provide the distant preview, with an 8,000-block default. This does not increase collision or simulation distance.
- Distant Horizons rendering is suppressed in Earth and photo dimensions. Street View uses the vanilla Iris pipeline; other dimensions keep their usual shaders.
- Automated builds and selected rendering checks pass. They do not prove every shader/mod combination or long session works.
- Offline scenery and public multiplayer hosting are not supported as a ready-made setup. Each client needs map access; native photos also need the server's photo service.

## Provider usage

New map areas and photos use provider quota and may incur charges. A photo can require dozens of tile requests. Check your own account's current limits; credentials and unlimited access are not included.

Keep Google, OpenStreetMap, and other required credits. RealWorldMC uses Apache 2.0; imagery and generated map-derived content have separate conditions. Check provider permissions before sharing map-derived saves or using them commercially: [Google content through Cesium](https://cesium.com/legal/terms-for-google/) and [attribution](https://cesium.com/learn/ion/content-usage-and-attribution-guide/).
