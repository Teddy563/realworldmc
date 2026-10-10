# RealWorldMC: real-world maps in Minecraft

<img src="docs/assets/explorer-green.webp" align="right" width="104" alt="RealWorldMC explorer mascot">

RealWorldMC is a Minecraft Java mod for exploring real cities and terrain with Google Photorealistic 3D Tiles and Google Maps Street View. Fly over mountains with an elytra or visit street-level photos with Minecraft blocks and mobs around you. It offers Google Earth-style exploration inside Minecraft; it does not run the Google Earth application.

[Download the latest release](https://github.com/Teddy563/realworldmc/releases/latest) · [Setup](docs/setup.md) · [API keys and costs](docs/accounts-and-costs.md)

![RealWorldMC gameplay: New York, the Grand Canyon, Paris and the Golden Gate Bridge in a two-by-two grid](docs/assets/gameplay-grid.jpg)

Actual Minecraft 1.21.1 footage with optional shaders. Provider credits remain visible. Mascots are brand artwork, not in-game characters.

[Watch the 14-second flight preview](docs/assets/flight-preview.mp4) · [Media details and capture checklist](docs/media.md)

**Use Minecraft Java 1.21.1 for in-game Street View.** The separate 26.2 build is experimental and has browser Street View only. Both need a local map service, internet access, and your own provider credentials.

## Start here

<img src="docs/assets/explorer-blue.webp" align="right" width="144" alt="Blue RealWorldMC explorer holding a laptop">

1. Use **Minecraft Java 1.21.1**, **Fabric**, **Fabric API for 1.21.1**, and **Java 21** on Windows x64.
2. Download **`RealWorldMC-VERSION-mc1.21.1.jar`** from Releases and put it in your profile's `mods` folder. Keep only one RealWorldMC JAR installed. The release JAR includes the local map service and a private Node runtime.
3. Launch Minecraft. In **RealWorldMC setup**, choose Google direct or Cesium, enter your own credentials, and test access. Street View uses an optional separate Google Map Tiles key.
4. **Save & finish** starts or reloads local services automatically. Create a Creative world with commands and the **Real World** world type. If you changed keys inside a world, reopen that world to refresh map connections.
5. New worlds pick one of **100 curated starting places** at random, including the Palace of Parliament in Bucharest. The chosen location stays saved with that world. [See all 100 starts](docs/spawn-locations.md). Use `/earth setup` to reopen setup and [controls](docs/controls.md) to travel.

**Prefer a ready-made profile?** Import the **1.21.1 Core `.mrpack`** into Modrinth App or Prism Launcher. It sets up Fabric and the mod dependencies. Do not install both copies into one profile.

No PowerShell, batch files, or separate Node installation is needed on **Windows x64**. This is a **Fabric** mod, not Forge/NeoForge or Bedrock. The experimental 26.2 download needs Java 25 and manual key configuration. See [setup](docs/setup.md).

## Useful commands for 1.21.1

| Command | What it does |
|---|---|
| `/earth help` | Show help |
| `/earth goto 44.427454, 26.087287` | Visit the Palace of Parliament |
| `/earth where` | Show your coordinates |
| `/earth streetview` | Find the nearest photo to your current Earth position |
| `/earth back` | Return from Street View |

**F5** cycles camera views in exploration and Street View. In Street View, tap **W** toward a yellow arrow to travel. Press **V** to walk within a 3×3 block area.

Minecraft 1.21.1 supports `/earth setup` to reopen the masked key setup screen, plus `/rw` as an equivalent shortcut for commands. See [command updates](docs/rw-commands.md).

## Know before playing

- Maps load over the internet. Some places have missing or outdated coverage; provider usage may cost money.
- Blocks approximate the scenery. Photos do not contain real building interiors or accurate wall depth.
- Street View shows the photo over untouched terrain. Placed blocks and excavated surfaces use Minecraft textures. Photo alignment and third person are approximate.
- This is an exploration and building mod, not a complete survival world generator.
- Existing saves keep their starting coordinates and terrain. Back up saves before updating.
- Builds and automated checks pass. Full gameplay, shader combinations, and the 26.2 port still need testing.

Read [Limitations](docs/limitations.md) or [Troubleshooting and updates](docs/troubleshooting.md) for details.

## Credits

RealWorldMC uses map data from Google and OpenStreetMap, with Cesium available for 3D map access. Keep provider credits visible. Credits become fainter while chat is open; chat opacity is unchanged.

RealWorldMC uses [Apache License 2.0](LICENSE). Map data has separate provider terms. RealWorldMC is not affiliated with Mojang, Microsoft, Google, or Cesium.

Read the [licensing notes](docs/legal/README.md), [NOTICE](NOTICE) and [code of conduct](CODE_OF_CONDUCT.md) before contributing or redistributing.

## Screenshots and videos

The [media checklist](docs/media.md) lists the screenshots and short clips needed for the README, download page and release. Only use actual gameplay to demonstrate features; keep provider attribution visible. Logo art is not a gameplay screenshot.

For installation footage, follow the [step-by-step recording plan](docs/recording-plan.md). Browse all guides in the [documentation index](docs/README.md).

Developer notes: [changelog](CHANGELOG.md), [automatic builds and releases](docs/releases.md), [release checklist](RELEASE-CHECKLIST.md), [26.2 port status](PORT-26.2.md).
