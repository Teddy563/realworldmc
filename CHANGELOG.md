# Changelog

### Random new-world starts (2026-10-11)

- Choose each new Real World origin uniformly from 100 curated places, including the Palace of Parliament. Save that coordinate pair with the generator; reloading, death and dimension travel never reroll it.
- Preserve fixed custom starts and saved worlds. `/earth setlocation <lat> <lon>` selects a fixed future start; `/earth setlocation random` restores random starts. Both builds share the same catalogue.
- Keep one static README collage and add current repository handoffs.


### Loading feedback and Street View lighting (2026-10-11)

- Show travel stages in a green boss bar and photo loading above the hotbar. Clear progress on failure, disconnect and server shutdown. Prevent overlapping travel requests.
- Use a green RW chat prefix, white messages and yellow commands. The short help list suggests commands when clicked; `/earth help more` lists advanced controls.
- Render photo-world edits with terrain textures and one directional shading pass. Keep depth and corner shading in excavated holes. Use fixed midday lighting in Street View; preserve the Earth world's shader settings.
- Keep the native perspective toggle. The earlier numbered camera command and custom X binding are removed. Camera rendering passes isolated checks with Iris and the camera mods. The full profile also received F5 and cycled through first, rear and front views in the live check.
- Use one static HUD-free gameplay collage: New York, Grand Canyon, Paris and Golden Gate Bridge, with softer credits and no dark boxes.

### Palace start and RealWorldMC cleanup (2026-10-10)

- Fixed the Street View camera being pinned while nearby walking was awaiting acknowledgement. The native camera toggle now controls perspective; the earlier custom command and X binding were removed.

- New worlds and portable packs start at the Palace of Parliament, Bucharest: 44.427454, 26.087287. Existing world origins remain saved with their terrain.
- Use RealWorldMC for the Java packages, entrypoints, Fabric mod ID, mixin names, build metadata, logger and map-service User-Agent. Preserve saved registry identifiers and the loader compatibility alias.
- Import previous settings into `config/realworldmc.json` without dropping custom values. Reject null/malformed configuration safely without overwriting it.
- Prefer `REALWORLDMC_*` service environment names while accepting existing settings.
- Remove the unused low-resolution cube-face API and unused glTF functions dependency. Keep the current high-resolution panorama renderer and saved-world access.
- Include RealWorldMC in flight-view credits. Smoothly reduce corner-credit opacity while chat is open and restore it on close, in both flight and native Street View; leave chat settings unchanged.

### Street View from your current position (1.21.1, 2026-10-10)

- `/earth streetview` and `/earth streetview here` enter the nearest available panorama within 500 metres of your current Earth position, including flight and later geographic regions. Unmapped worlds require explicit coordinates.
- Move browser access to `/earth streetview browser`; preserve coordinate travel, `/earth back` and existing photo controls. Missing nearby coverage leaves the player in place.
- Add coordinate/command regression coverage and bounded-radius provider tests. Update English help and setup documentation.

### Nearby walking, quieter photos and flight preview (1.21.1, 2026-10-10)

- Remove repetitive photo-entry/report chat lines. Show compact Google, OpenStreetMap, RealWorldMC branding with full photo attribution in the F7 Sources panel; retain F8 image reporting.
- V toggles a server-enforced 3×3 walking square. Arrow navigation preserves geographic builds; visible nearby drops retain vanilla pickup rules. Falling too far restores the pinned viewpoint without filling craters. Original flight state is restored on return.
- Add experimental F5 third person with a short camera offset and fixed-origin flat-ground projection. Single photos still cannot supply correct wall parallax or real interiors.
- Shade excavations with native texture quads, corner occlusion and gradual depth darkening.
- Extend new map defaults to 8 km, prioritize coarse previews, and move view selection off the tile download queue. Reduce LOD detail under budget pressure instead of cutting away distant terrain.

### Textured excavation and camera alignment (1.21.1, 2026-10-10)

- Replace plain dark crater faces with native block-model textures. Reveal only the faces bordering excavated cells rather than entire neighbouring cubes. Thin-wall openings use textured backing.
- Use a small polygon depth bias to prevent revealed surfaces from fighting hidden terrain, with one consistent camera transform and complete restoration of the modified matrix/depth state.
- Keep one-metre blocks and use an approximate 2.5-metre photo camera height. Add `/earth streetview height <1-4>` for calibration and `photoEyeHeight` for the default. Camera offsets and crouching no longer shift the photo viewpoint.
- Use the saved original terrain to choose a stop's ground height after mining or placement, avoiding viewpoint changes on return.
- Add a fifth server GameTest and rendered crater/TNT fixtures comparing the origin with a 100 km offset. No saved blocks are regenerated or resized.

### Fixed photo stops and terrain crash recovery (1.21.1, 2026-10-10)

- Show the panorama over hidden, depth-writing collision terrain. Draw placed blocks, mobs, and excavated edges over the photograph; removed terrain reveals a simulated dark cavity.
- Lock the player at street-level stops. Look toward the yellow pixel arrow and tap W, or use arrow keys; free walking, jumping and flight are disabled during photo mode. `/earth back` restores the saved return point.
- Save the original state of edited terrain with the world and sync nearby damage after travel. Compare older chunks with generated reference data without overwriting them.
- Use Iris's vanilla rendering pipeline only in the photo dimension. Preserve shader selection and enablement for the Earth world.
- Handle the exact missing-coverage 404 from the October 10 server crash as empty Earth collision chunks. Do not invent flat Earth terrain or suppress authentication/network errors. Missing Street View coverage retains simulated deep ground.
- Verify four Minecraft GameTests, 11 navigation checks, and an isolated production-runtime OpenGL check using Iris 1.8.14-beta.1 and Sodium 0.8.13. Full gameplay testing of photograph alignment, damage visuals and all profile mods remains a manual release gate.

## 1.0.0 — prepared for release testing, 2026-10-09

- Target Minecraft Java 1.21.1 with explicit version metadata.
- Improve small-gap ground interpolation and per-voxel textured material sampling.
- Ignore degenerate collision triangles and reject entirely missing terrain coverage.
- Stop saving synthetic flat mapped chunks/zero ground heights after download failure; preserve interruption during loading.
- Refresh temporary Cesium endpoint access after 401/403, coalesce refresh calls, and avoid tileset resets for quota responses.
- Add provider request deadlines, same-origin tile credential checks, coordinate/allocation bounds, and safer provider errors.
- Remove German project localization and translate the browser preview. Use RealWorldMC in display text and logs.
- Preserve saved registry/config identifiers and required license notices.
- Rewrite the English setup, limitations, troubleshooting, and upgrade documentation.
- Default to a Core pack, with the previous visual mod selection available as an experimental Visual pack.
- Use masked credential prompts, `npm ci`, exact JAR selection, English pack defaults, and temporary-then-final pack creation.

Minecraft 26.2, seamless recovery from failed generation, automatic repair of old terrain, and certified shader/DH compatibility are not part of this release.

## 1.0.0 experimental 26.2 companion update
- Separate Java 25 / Minecraft 26.2 source and core pack; startup smoke check passed.
- Independent 360-degree Street View browser viewer with linked-photo arrows and heading tests.
- Version-selectable installer and English setup / validation notes.
- Full 26.2 gameplay, native in-game panoramas and paid live-photo access remain unverified.

### Native Street View preview (1.21.1)
- Coordinate command enters an isolated flat room with six photo faces and linked-photo navigation.
- Return position is recorded before entry and restored on exit/reconnect.
- Official metadata and face fetch succeeded at the requested coordinates; 17 service tests pass.
- Experimental 250px image faces; in-game alignment, shaders and recovery still require gameplay validation.
- 26.2 retains browser-based panoramas.

### Playable panorama update (1.21.1)
- Photo sky replaces the blocking preview screen; normal Minecraft interaction stays available.
- Explicit /earth back and /earth streetview back commands; Escape is the normal menu.
- Official tile stitching up to 4096x2048, bounded downloads, and in-memory imagery.
- Temporary Distant Horizons render override in Earth/photo dimensions; shader settings preserved.

### Physical Street View and geographic navigation (1.21.1, 2026-10-10)
- New persistent Street View dimension with deep ground, visible voxelized building surfaces, normal block/mob collision and explosions.
- Server-owned linked-photo travel moves the player between geographic coordinates after image/terrain readiness; old blocks and craters are never repainted.
- Translucent white pixel arrows, yellow selection, one-step W navigation, Shift+W walking and V toggle.
- Preserve earlier flat-room builds through /earth streetview legacy; keep /earth back recovery, and add respawn recovery.
- Add navigation regressions and isolated Minecraft physics/save tests. Native 26.2 Street View and client shader validation remain outstanding.

## Camera command routing fix — 2026-10-10

Removed client command registration and the custom X binding. The native perspective toggle controls both exploration and Street View; no separate camera command is needed. Regression checks cover command pass-through and camera view mapping.
