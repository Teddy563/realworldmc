# Try Minecraft 26.3

This is an experimental target alongside stable 1.21.1 and experimental 26.2. It is not a full port of the native Street View or space-flight features.

## Install

1. Create a separate **Minecraft 26.3 Fabric** profile using **Java 25** and Fabric Loader **0.19.5** or newer.
2. Install **Fabric API 0.163.0+26.3** and the `RealWorldMC-VERSION-mc26.3.jar` from the matching release. Alternatively, import the 26.3 `.mrpack`.
3. Launch once to unpack the local map service, then close the game. Copy the example configuration files in `realworldmc-service` to `config.json` and `streetview.config.json` and add your own credentials. Follow [accounts and costs](accounts-and-costs.md).
4. On macOS/Linux, install Node.js 22+. The mod looks for it automatically. Windows x64 uses the included runtime.
5. Create a new Creative world with commands enabled and choose **Real World**. Wait for the map to load and use `/earth goto <place>`.

Street View opens in the browser and needs its browser key. `/earth setup` and native photo play remain exclusive to 1.21.1.

## Test with Propwash

Install the **Fabric 26.3** download from [Propwash FPV](https://propwashfpv.com/). Do not use its NeoForge JAR. The mod is not included in our pack. On multiplayer, Propwash needs matching client/server versions.

First test without shaders. Start near the pilot, enter the drone camera, fly over loaded terrain, then fly beyond the pilot's nearby area. Confirm that scenery follows the drone, ground collision loads, controls work and leaving the goggles restores the normal camera. Test recording and shaders after that.

RealWorldMC streams photographic meshes and approximates collision with blocks. Photos cannot guarantee correct tiny gaps, windows or interiors for a drone. Compatibility is not certified by a successful compile.

## Development

`mod-26.3` reuses the 26.2 implementation and resources. It overrides three files: `EarthChunkGenerator` for the new terrain-generation hooks, and `MapRenderer`/`MapTile` for the RenderPearl graphics API. Its Gradle build excludes those three older implementations, pins 26.3 dependencies and expands the exact Minecraft version into `fabric.mod.json`.

Build with Java 25: `./mod-26.3/gradlew.bat -p mod-26.3 build` on Windows, or `./mod-26.3/gradlew -p mod-26.3 build` on macOS/Linux. Package with `./build-release.ps1 -MinecraftVersion 26.3` on Windows. CI creates the JAR, pack, ZIP and checksums alongside the other targets.

`runRenderCheck` opens a disposable client, renders a synthetic map triangle through the real renderer, reads its pixels back and closes itself. It uses no world or provider key. The check is separate from headless CI and is not included in the release JAR.
