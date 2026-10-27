# Setup

[Back to README](../README.md)

## 1. Install the JAR or pack

You need Windows x64, Minecraft Java Edition, internet access and your own map account.

### JAR: use your existing profile

1. Create or choose a **Minecraft 1.21.1 Fabric** profile using Java 21.
2. Install [Fabric API for 1.21.1](https://modrinth.com/mod/fabric-api).
3. Download **`RealWorldMC-VERSION-mc1.21.1.jar`** from [Releases](https://github.com/Teddy563/realworldmc/releases). Drop it into that profile's `mods` folder. Remove the older RealWorldMC JAR when updating; do not leave duplicate copies.
4. Launch Minecraft. The release JAR unpacks its map service and private Windows x64 Node runtime automatically. No scripts or separate Node installation are needed.

The service code lives in `realworldmc-service/.bundles/<checksum>/`. Keys, logs and settings stay in `realworldmc-service/`, outside the versioned code. Updating the JAR leaves these settings intact. Older bundles remain available locally; they are not loaded by a newer JAR.

### Pack: let the launcher prepare the profile

Import the **1.21.1 Core `.mrpack`** into Modrinth App or Prism Launcher, then launch with Java 21. It includes the mod and declares Fabric/API dependencies for the launcher. Do not place a `.mrpack` in the `mods` folder or add a second RealWorldMC JAR to that pack.

Both options are Fabric, not Forge/NeoForge. Mobile and Bedrock Edition are not supported. Developer JARs built directly by Gradle lack the service payload; use the named release JAR above.

## 2. Connect your account

1. The first-launch **RealWorldMC setup** screen finds the bundled service automatically.
2. Read the usage notice. Choose Google direct or Cesium ion. Follow [accounts and costs](accounts-and-costs.md) to create a suitable key/token.
3. Enter your map credential in the masked field. Optionally add a **Google Map Tiles API** key for in-game Street View.
4. Select **Test connection**, then **Save & finish**. Testing makes a small number of provider requests that may count toward usage.
5. The mod starts/reloads both localhost services. Wait for the ready message. Retry from setup if it reports a problem.

Use `/earth setup` to return to this screen. Your keys stay in readable files under the instance's `realworldmc-service` folder. Never share those files or an exported instance containing them. Provider eligibility and usage charges still apply; Cesium does not bypass Google's regional conditions.

## 3. Play

Create a Creative world with commands enabled and choose **Real World** as the world type. Let tiles load, then use `/earth goto <place or coordinates>`.

For photos, use `/earth streetview`. Return with `/earth back`. No service terminal needs to stay open. Services stop when Minecraft exits, including when its parent pipe closes unexpectedly. Run one RealWorldMC instance at a time because the local services use ports 8765 and 8766.

If you change keys while playing, save through setup and reopen the world. You do not need a full Minecraft restart.

## Experimental 26.2 and 26.3

Use the matching release JAR in a Fabric 26.2 or 26.3 profile with Fabric API and Java 25, or import its `.mrpack`. For a JAR install, launch once to unpack the service, then close Minecraft before editing keys. This version uses the same automatic service startup, but has no native setup screen or in-game Street View yet. Close Minecraft, copy the example configs inside the instance's `realworldmc-service` folder to `config.json` and `streetview.config.json`, enter your keys, then launch. Its browser viewer requires a separate Maps JavaScript API `browserKey`. Use a new test world.

## Advanced or existing installations

The release ZIP is optional troubleshooting material. Its manual launchers remain available for developers. Use the release JAR or `.mrpack` for normal installation.

Linux, macOS and Windows ARM do not have a bundled runtime. Install Node.js 22+. From 1.0.2, Linux/macOS detect a compatible Node from PATH, Homebrew, standard local locations or NVM (including `NVM_DIR`). The release JAR already includes the service files. If detection fails, set `nodeExecutable` in `config/realworldmc.json` to the full Node path. Full gameplay on those platforms still needs testing. An existing custom `serviceDir` still resolves as an absolute path or relative to the instance directory. For the portable pack, leave it as `realworldmc-service`.

For the optional browser companion on 1.21.1, add a restricted Maps JavaScript API `browserKey` in `realworldmc-service/streetview.config.json`, keeping it separate from the native `tilesKey`. Restart Minecraft after manual file edits. The first-run screen configures the native key, not browser access.

For Propwash FPV and the 26.3 target, see [experimental 26.3](experimental-26.3.md).


## Installing a JAR instead of a pack

RealWorldMC is a Fabric mod, not a datapack. Do not put it in a world's `datapacks` folder.

1. Create a Fabric profile for the exact Minecraft version on the download.
2. Install Fabric API for that same version.
3. Put the RealWorldMC JAR in that profile's `mods` folder. Remove older RealWorldMC JARs first.
4. Start that profile and complete the setup screen.

For a `.mrpack`, use the import option in Modrinth App or Prism Launcher. Do not put the pack in `mods` or `datapacks`.

The next release includes a Fabric 1.20.1 build targeting Java 17, alongside the recommended 1.21.1 build (Java 21). Use the matching download, never a JAR for another Minecraft version. The base 1.20.1 pack contains RealWorldMC and Fabric API; the optional personal Heroes pack is not required.
