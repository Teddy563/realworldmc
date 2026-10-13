# Experimental Earth-to-space flight

Available in v1.0.1 on Minecraft 1.21.1. Space flight is experimental and is not included in v1.0.0 or the 26.2 build.

1. Open a singleplayer **Real World** world in Creative mode. Wait for the map to load.
2. Leave any vehicle and fly upward. Space mode starts automatically above **2 km over the region's ground reference**. `/earth space` starts powered ascent earlier.
3. Hold **Space** to rise or **Shift** to descend. **W/S** move forward/back, **A/D** strafe, and the mouse steers. Acceleration and braking are gradual. Release the controls to slow to a hover. Speed increases with altitude.
4. Look down to keep Earth in the shot. **F5** remains the normal camera toggle. **F1** hides the HUD for recording.
5. Run `/earth space return` to return to where space mode began. `/earth space off` also returns there once the view is anchored; below the entry height, it resumes ordinary Creative flight in place. After either command, automatic entry stays suppressed until you fly below the lower exit height. `/earth space` can still start it manually.
6. Use `/earth actionbar` to toggle the compact controls and status above the bottom-left credits. `/earth actionbar on` and `/earth actionbar off` set it explicitly. Your choice is saved. Chat, boss bars, source credits and other mods' action bars stay available.

Earth flight stays in the same world and uses the same Google 3D tiles, including their Earth curvature. Above the entry height, horizontal and vertical movement both move the geographic view while Steve stays near one Minecraft position. This avoids huge player movement packets, camera jitter and unnecessary chunk travel. Both altitude and globe rotation interpolate between ticks.

While orbit is active, local Minecraft terrain, other entities, block entities, particles and world-border effects are hidden. The launch world stays saved and appears again when you return. The crosshair cannot mine or target invisible Earth objects from the orbital view.

Steve's pose follows the movement keys, independently of where you look. Space raises both arms for an upright climb. Shift tips the body into a downward dive. Forward cruise uses a one-arm-leading pose, and releasing the controls relaxes into a hover. No elytra or equipment changes are needed.

Entering space keeps the current map tiles. During a detail swap, loaded scenery stays visible until new tiles or coarse parents are available. A geographic handoff retains the last visible tiles in the correct position until the destination loads. In `config/realworldmc.json`, `autoSpaceFlight` enables automatic entry and `spaceEntryHeight` sets the height in metres (2,000 to 100,000; default 2,000). Restart after editing. The exit height is 80% of the entry height to prevent repeated switching near the boundary.

The distant map has its own depth range so it does not reduce depth precision for your player. The sky starts darkening at 12 km and reaches a black star field by 80 km. This is a filming transition, not an atmospheric simulation. The world clock is unchanged. Orbital rendering uses an isolated vanilla Iris pipeline so shader clouds and atmospheric effects cannot cover space; your selected shader pack returns for local Earth flight. The orbital player uses consistent bright lighting instead of depending on unloaded high-altitude light sections; normal model shading remains. Third-person visibility follows the camera subject instead of incorrectly culling Steve at his newer tick position during fast flight.

## Moon and Mars

In the same Creative, singleplayer Real World world:

- `/rw moon` opens a Moon orbital flyover.
- `/rw mars` opens a Mars orbital flyover.
- `/rw earth` restores the Earth orbit you left.
- `/earth space return` returns to your original Minecraft launch position.

The `/earth` prefix also accepts `moon`, `mars` and `earth`. W/S and A/D move around the globe; Space and Shift change altitude. Speed grows with altitude, up to 250 km per tick for ascent. Earth-to-globe ascent from the default entry height takes less than 70 seconds at 20 ticks per second.

Above 100 km, look around for the Moon, Mars or Earth travel markers. Aim at one and hold your forward key for 2.5 seconds to transfer. Look away or release the key to cancel. After arrival, release forward before choosing another marker. These are navigation signs, not the planets' real positions or distances. F1 and `/earth actionbar off` hide them and disable marker activation; commands still work.

These two destinations use bundled, real global image mosaics. The Moon map is 4096×2048; Mars is 1440×720. They are mapped onto spheres with the bodies' mean radii. There are no generated planet blocks, collision terrain, landings or Moon/Mars Street View. Minimum flyover heights are 20 km for the Moon and 40 km for Mars. Close-up detail is limited by the original images. No Google or Cesium requests are made for planetary imagery. Earth map requests pause while visiting these destinations; a request already in flight can still finish.

See [planetary data and credits](planetary-data.md) for the exact datasets and asset sources.

## Limits

- This is a filming mode. Globe travel moves the rendered geographic viewpoint, not your Minecraft blocks, mobs or saved location. `/earth where` still reports the underlying Minecraft region. Use `/earth goto` for gameplay at another place.
- Holding Shift below about **1.6 km** at the default setting prepares the geographic location beneath you, then resumes ordinary Creative flight there. Continue holding Shift to descend. Scenery is retained, but a new location can still pause while its ground data loads. Provider outages or missing coverage cannot be made seamless by the renderer. The entry point remains available through `/earth space return`.
- Descent needs provider coverage and a working local service. If preparation fails, you remain in orbit and can retry Shift or return. A new region may be saved when descending into a new location.
- Leave space mode before changing locations. Entering another map region, a vehicle, or another dimension ends the mode.
- The geographic camera caps ascent at roughly 20,000 km; Steve does not travel to that Minecraft Y coordinate. `/earth space return` remembers the entry point only for the current session.
- The threshold uses the saved ground height at the region origin, not the terrain directly underneath. This avoids extra provider requests every tick. Mountains elsewhere in the region can have a different elevation. Automatic flight requires Creative, singleplayer, on foot, in a Real World world; it does not take over aircraft controls.
- Globe detail depends on the provider's coarse imagery and your connection. Newly requested scenery can still appear progressively. The isolated Iris pipeline was checked with Bliss. Other rendering mods may still affect the final appearance.
- The orbital tile selection targets at most 256 tiles, using coarser parents where available. This limits detail, not API billing. Map requests still use your configured provider and its quota.
- Aircraft, superhero abilities and black-hole effects are separate optional mods. They are not bundled into the public RealWorldMC release.

## Filming add-ons

The personal recording profile uses **Zoomify** and **Camera Utils**. These are optional, not required by RealWorldMC. The earlier profile controls were restored on 12 October; this update does not rebind them:

| Key | Action |
|---|---|
| F5 | Normal first/rear/front camera toggle; rear camera starts 4 blocks back |
| F6 | Camera Utils alternate follow preset |
| Hold C; scroll while held | Original hold-to-zoom settings; scroll adjusts magnification |
| Hold Mouse 5 | Freelook |
| F10 | Detached Camera Utils camera |

Optical zoom changes the lens; camera distance moves the camera away from Steve. They work together. Street View retains its shorter camera limit because a single photograph cannot supply distant camera parallax.

The personal profile has a local compatibility fix for F-22 Raptor 1.1.0: its camera hook now composes the jet's FOV boost with Zoomify instead of exiting before zoom runs, including outside a plane. The original aircraft JAR is backed up. Updating that add-on may require checking zoom again; this patch is not part of the public RealWorldMC download.

The **Physics Beta / Ragdoll is experimental** notice belongs to Multiverse: Heroes & Villains. The personal profile's tiny RWMC Filming Tweaks companion calls that mod's existing dismiss action at startup. The public RealWorldMC JAR does not contain the notice or that companion.

- [F-22 Raptor, Fabric 1.21.1](https://www.curseforge.com/minecraft/mc-mods/fighter-jets-overhaul/files/9000605): includes the F-16C, F-22, F-14 and F/A-18. Use the F-16 item from Creative inventory, place it, and right-click to board. Its own engine, throttle and camera controls appear in Minecraft's Controls menu. This aircraft is for terrain flyovers; leave it before using space mode.
- [Black Hole & Nuclear Weapons](https://modrinth.com/mod/black-hole-weapon): a black-hole wand, orbiting debris and a targeting laser on the strike designator. These effects destroy blocks. Film them in a copy of your world.
- [Multiverse: Heroes & Villains](https://github.com/ali975/the-diabolical-conquest): the separate superhero mod already used by the recording profile. `/conquest give` enables its powers and `/conquest remove` removes them. It is not a port of the original 1.20.1 Viltrumite mod.

## Checks

The service tests cover altitude-dependent selection on both sides of Earth. Optional client checks exercise real OpenGL globe rendering at three orientations, automatic entry, interpolated ascent, W cruise, hover, descent and server return without immediate re-entry. They verify that the physical player stays anchored, reproduce vanilla fast-player culling, and check both third-person views, the equipment-free flight pose and the saved action-bar setting. Terrain checks cover delayed replacement tiles, non-overlapping detail levels, retained scenery across geographic frames and stale uploads. The recording-profile check also spawns the F-16 and black-hole entities while holding the laser designator. It does not establish compatibility with every shader pack or every aircraft weapon.
