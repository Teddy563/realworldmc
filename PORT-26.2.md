# Minecraft 26.2 port status

The separate `mod-26.2` source project compiles and produces `realworldmc-mc26.2-1.0.0.jar`. Use Java 25, Fabric Loader 0.19.5 and Fabric API 0.161.0+26.2. The original `mod` project remains Minecraft 1.21.1 / Java 21. Versions between these targets have not been ported or tested.

The port updates identifiers, saved-data codecs, chunk generation signatures, permissions, block registration, teleportation, networking and HUD callbacks. Map meshes now use Minecraft GPU buffers, render passes and pipelines instead of raw OpenGL calls. The custom dimension uses the 26.2 dimension schema.

## Install and build

Extract the complete 26.2 release ZIP. Run `./install.ps1 -MinecraftVersion 26.2`, then import the generated 26.2 pack and select Java 25. Use a new test world first. Set map access as described in README.md. The 1.21.1 visual pack is deliberately unavailable for 26.2.

To build from source, set JAVA_HOME to a Java 25 installation and run `./gradlew.bat build` inside `mod-26.2`. The checked-in wrapper downloads dependencies. Do not replace the JAR inside an existing 1.21.1 profile with this one.

## Remaining release checks

Compilation succeeds. Full gameplay is not yet certified. Before calling 1.0.0 stable, test world creation, streamed mesh rendering, movement/collision, water, block placement and removal, teleportation, disconnect/reconnect, resource reload and world save/reload on 26.2. Test upgrades only on copied saves. Compare GPU memory over repeated travel and ensure attribution remains visible.

Current rendering differences: texture sampling uses linear filtering without the old OpenGL anisotropy/mipmap path; the old custom far-plane override was removed during the API port, so distant scenery can be clipped by Minecraft's projection. These are known follow-up checks, not equivalent visual parity with the 1.21.1 renderer. Shader-mod compatibility is unverified.

Street photos are an independent browser companion; see STREET-VIEW.md. The native playable panorama implementation is currently limited to 1.21.1; its 26.2 port remains pending.

Startup smoke check: the 26.2 development client initialized RealWorldMC, created its graphics device and completed resource/texture loading without a mod or mixin crash. Windows performance-counter warnings and development-account Realms authorization warnings occurred. This does not validate entering an Earth world or rendering streamed tiles.
