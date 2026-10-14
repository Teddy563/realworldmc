# RealWorldMC: real-world maps in Minecraft

Explore real cities, fly over mountains and step into Google Maps Street View inside Minecraft Java. RealWorldMC streams Google Photorealistic 3D Tiles and places Minecraft blocks and mobs alongside street-level photos. It offers Google Earth-style exploration without running the Google Earth application.

[Download the latest published release](https://github.com/Teddy563/realworldmc/releases/latest) · [Website](https://realworldmc.com/) · [Setup](docs/setup.md) · [API keys and costs](docs/accounts-and-costs.md)

![RealWorldMC gameplay: New York, the Grand Canyon, Paris and the Golden Gate Bridge in a two-by-two grid](docs/assets/gameplay-grid.jpg)

Actual Minecraft 1.21.1 footage with optional shaders. Provider credits remain visible.

[Watch the 14-second flight preview](docs/assets/flight-preview.mp4) · [Screenshots and recording guide](docs/media.md)

**Recommended: Minecraft Java 1.21.1 with Fabric.** It supports in-game Street View and the masked key setup screen. The separate 26.2 and 26.3 builds are experimental and have browser Street View only. All targets need internet access and your own provider credentials.

## Prepared for 1.0.2

- Automatic Node.js 22+ detection on macOS and Linux, including Homebrew and NVM. Windows x64 keeps its included runtime.
- A separate **experimental Fabric 26.3 build**, intended for testing with Propwash FPV. It requires Java 25; compatibility with the drone camera and shaders still needs in-game testing.
- A fix for the bundled browser map preview, with cross-platform runtime checks.

[1.0.2 release notes](docs/releases/v1.0.2.md) · [26.3 setup and limits](docs/experimental-26.3.md) · [Validation](docs/release-1.0.2.md)

## What is new in 1.0.1?

[Read the 1.0.1 release notes](docs/releases/v1.0.1.md).

- **Earth to orbit:** in a Creative, singleplayer Real World world, fly above the default 2 km entry height or run `/earth space`. Use Space to climb, Shift to descend and WASD to travel. No elytra is required.
- **Moon and Mars flyovers:** visit image-covered globes with `/earth moon` and `/earth mars`. Use `/earth earth` to return to Earth orbit. These destinations do not have walkable surfaces.
- **Steadier filming:** smoother globe motion, retained scenery during tile changes and better third-person player visibility. Earth blocks and mobs are hidden during orbit without being deleted.
- **Clearer controls:** compact bottom-left hints, `/earth actionbar` to hide them and larger Street View arrows.
- **Loading fixes:** optional OpenStreetMap water generation has a setup toggle and bounded requests. Local services try alternative ports when their usual ports are busy.

Space flight is experimental and currently available only in the 1.21.1 build. Read the [space guide](docs/space-flight.md) and [1.0.1 validation notes](docs/release-1.0.1.md) before testing.

## Install and start

1. Use **Windows x64**, **Minecraft Java 1.21.1**, **Fabric**, **Fabric API for 1.21.1** and **Java 21**.
2. Download **`RealWorldMC-VERSION-mc1.21.1.jar`** from [Releases](https://github.com/Teddy563/realworldmc/releases). Put it in your profile's `mods` folder and keep only one RealWorldMC JAR. The release JAR includes the local map service and its private Node runtime.
3. Launch Minecraft. In **RealWorldMC setup**, choose Google direct or Cesium, enter your credentials in the masked fields and test access. Native Street View needs a Google Map Tiles API key. Follow [accounts and costs](docs/accounts-and-costs.md) to set these up.
4. Select **Save & finish**. The mod starts the local services automatically. Create a Creative world with commands enabled and choose the **Real World** world type.
5. Wait for the scenery to load. Each new world starts at one of [100 curated places](docs/spawn-locations.md), including the Palace of Parliament in Bucharest. That world's starting location stays saved.

**Prefer a ready-made profile?** Import the **1.21.1 Core `.mrpack`** from Releases into Modrinth App or Prism Launcher. It sets up Fabric and the mod dependencies. Do not add a second copy of the mod to that profile.

No batch files, PowerShell scripts or separate Node installation are needed for the Windows x64 release. On macOS/Linux, install Node.js 22+; 1.0.2 finds it automatically. Full gameplay testing on those platforms remains outstanding. This is a Fabric mod for Java Edition; it does not support Forge, NeoForge or Bedrock. The experimental 26.2 and 26.3 builds need Java 25 and manual key configuration. See [setup](docs/setup.md).

## Everyday commands

| Command | What it does |
|---|---|
| `/earth help` | Show help |
| `/earth setup` | Open the masked key setup screen |
| `/earth goto 44.427454, 26.087287` | Visit the Palace of Parliament |
| `/earth where` | Show your coordinates |
| `/earth streetview` | Find the nearest photo to your Earth position |
| `/earth back` | Return from Street View |

**F5** cycles camera views in exploration and Street View. In Street View, tap **W** toward a yellow arrow to travel. Press **V** to walk within a 3×3 block area. `/rw` is an equivalent command prefix. See [all controls](docs/controls.md).

### Space commands in 1.0.1

| Command | What it does |
|---|---|
| `/earth space` | Start powered ascent in Creative singleplayer |
| `/earth space return` | Return to the original launch point |
| `/earth moon` | Visit the Moon flyover |
| `/earth mars` | Visit the Mars flyover |
| `/earth earth` | Restore your Earth orbit |
| `/earth actionbar` | Toggle the bottom-left controls and status |

On Earth, descending below the default 1.6 km exit height prepares the geographic location below you and returns to ordinary Creative flight. Loading may pause while that place is prepared. The entry height uses the region's saved ground reference, not the ground directly below every frame.

## Updating

Back up your worlds, close Minecraft and replace the old RealWorldMC JAR with the new release JAR. Keep one copy installed. Your saved worlds, API keys and profile controls stay in their existing files. Use `/earth setup` to change keys; save and reopen the world to refresh map connections.

## Know before playing

- Maps load over the internet. Coverage and detail vary, and provider usage may cost money. [Read the API limits and costs](docs/accounts-and-costs.md) before adding keys.
- Blocks approximate the scenery. Street View photos do not contain building interiors or accurate wall depth. Placed blocks and excavated surfaces use Minecraft textures; photo alignment and third person remain approximate.
- This is an exploration and building mod, not a complete survival world generator.
- Space mode moves the geographic view while keeping the physical player near the launch point. It is for filming, not gameplay across a block-built planet. Moon and Mars use bundled imagery, without terrain blocks, landings or additional imagery API requests.
- The 1.0.1 build passed build, service and isolated gameplay/rendering checks. Those do not certify every shader or add-on. **Bliss showed a blank Earth return view in an offline test with both the previous and current JAR; that issue remains unresolved.**
- Native space flight is not included in the experimental 26.2/26.3 modules. Their gameplay and shader compatibility need separate testing.

Read [limitations](docs/limitations.md), [troubleshooting](docs/troubleshooting.md) and the [release checks](docs/release-1.0.1.md) for details.

## Credits and licence

Earth data comes from Google and OpenStreetMap, with Cesium available for 3D map access. Moon and Mars imagery has separate [dataset credits](docs/planetary-data.md). Keep provider attribution visible when sharing captures.

The code uses [Apache License 2.0](LICENSE). Map and imagery assets have their own terms. RealWorldMC is not affiliated with Mojang, Microsoft, Google or Cesium.

[NOTICE](NOTICE) · [Licensing notes](docs/legal/README.md) · [Code of conduct](CODE_OF_CONDUCT.md)

## Guides and development

- [All documentation](docs/README.md)
- [Capture checklist](docs/media.md) and [installation recording plan](docs/recording-plan.md)
- [Architecture and handoff](HANDOFF.md)
- [Changelog](CHANGELOG.md) and [1.0.1 release notes](docs/releases/v1.0.1.md)
- [Build and release process](docs/releases.md), [release checklist](RELEASE-CHECKLIST.md) and [26.2 port status](PORT-26.2.md)
