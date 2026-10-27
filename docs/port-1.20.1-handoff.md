# Fabric 1.20.1 backport handoff

Reviewed 27 October 2026. Required release target for 1.0.4; not published yet.

## Integration status

The port is tracked on `release/1.0.4` with its own clean base pack. The previous personal manifest is preserved as `modpack/heroes-1.20.1.index.json`; it is not the default download. Builds use version 1.0.4 and release filenames `RealWorldMC-1.0.4-mc1.20.1.*`.

Shared checks and two Minecraft GameTests pass on Java 17, including generator Codec and saved-region round trips. This tests serialization, not a full graphical save/quit/reopen session. Region travel, safe release notices and render settings are backported. The current service is bundled during packaging. See [validation](release-1.0.4.md) for remaining live checks.

## Original source and evidence

- Source: `mod-1.20.1/`, copied/backported from the 1.0.3 native implementation.
- Pack manifest: `modpack/1.20.1.index.json`, currently named RealWorldMC Heroes.
- Current local branch: `port/1.20.1`; tracked HEAD matches main at `6f18d1f`.
- Prior handoff: local Claude project memory `D--ModrinthApp-profiles-Fabulously-Optimized/memory/realworldmc-vulkanmod.md`, updated 24 October.
- That handoff records successful user testing of the imported Heroes profile. This is historical evidence, not a new clean-install certification.
- Local build artifact is `realworldmc-1.0.3+mc1.20.1.jar`, Java class version 61 (Java 17). It does not contain a bundled service ZIP. The handoff describes a separately repackaged JAR using the 1.0.3 service bundle.

## Backport changes to retain

- Java 17 source/bytecode and mixin compatibility; Minecraft 1.20.1, Fabric API 0.92.12+1.20.1 and build Loader 0.19.5.
- Older networking, registry, saved-data, world-generation, GUI, rendering and mixin APIs adapted from the native target.
- A single static EarthChunkGenerator CODEC returned by codec(). The prior fresh-instance implementation reportedly lost the saved generator in level.dat. Add a save/reopen regression before release.
- 1.20.1 resource paths use loot_tables and tags/blocks instead of the newer singular names; verify drops and mineable tags rather than blindly copying 1.21.1 resources.
- Optional Xaero integration targets Minimap 26.6.0 and World Map 1.47.0 for 1.20.1.
- Native Street View, flight, setup and map source is present. Presence is not proof that every feature works on 1.20.1.

## Personal pack notes, not public defaults

The Heroes profile was created for Viltrumite and Orbital Railgun Renewed. The earlier VulkanMod experiment was reverted. Indium was needed with that profile's Sodium 0.5 / Distant Horizons combination.

The handoff mentions both removing SkinShuffle due to ETF conflicts and later testing a locally patched SkinShuffle. Confirm the final profile state; do not publish that patched third-party JAR without checking its distribution terms. A copied 1.21.1 Visuality config crashed Visuality 0.7.1 and was reset. Mocap was disabled. Keep these personal profile changes separate from the base mod release.

Do not copy provider keys, account files, worlds or personal options into any release. Test the first-run masked setup with empty credentials. The prior successful world test used the user's configured keys, so it does not validate the fresh-user flow.

## Required for 1.0.4

1. Track source, wrapper and manifest; exclude build/.gradle/run directories.
2. Integrate the same 1.0.4 service bundle at build time; do not borrow an old release ZIP.
3. Add 1.20.1 to build-release.ps1, build-pack.ps1, install.ps1, test-pack.ps1 and CI. Normalize project version to 1.0.4 and choose the artifact name explicitly; the current +mc1.20.1 version is rejected by the release script's version check.
4. Add packaged-service, clean import, startup and world save/reopen tests. This module currently has no checks/gametest/clientcheck source sets.
5. Apply the 1.0.4 crash and region fixes to this target too. Build on a JDK supported by Loom/Gradle, compile with release 17, and launch-test on Java 17.
6. Publish a standalone Fabric JAR plus a clean base pack. Keep the Heroes pack a separate optional variant after dependency and licensing review.

Maintain this file alongside port changes. No 1.20.1 release has been created by this audit.
