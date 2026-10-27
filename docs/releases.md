# Build and publish

The `release.yml` GitHub workflow builds all four targets, checks the code and
packages standalone Windows x64 JARs, importable .mrpacks and optional ZIPs. Minecraft 26.2 and 26.3 remain experimental even when included in a
normal RealWorldMC release.

## What runs automatically

| GitHub event | Result |
|---|---|
| Push to `main` or a pull request into `main` | Build and test all four targets, then upload downloadable Actions artifacts. Nothing is published. |
| Manual Run workflow | The same build and package checks, without publishing. |
| Push a matching `v*` tag | Build and test, then publish JARs, modpacks, ZIPs and SHA-256 checksums together. |

The public mod repository is [Teddy563/realworldmc](https://github.com/Teddy563/realworldmc). The website source belongs in the separate private `Teddy563/realworldmc-site` repository. Its download dialog reads public mod releases; it does not need a private-repository token or access to your API keys.

## Prepare the first publication

Review the [manual checklist](../RELEASE-CHECKLIST.md). Commit source and the small files in `docs/assets/`; do not commit local JARs, runtime folders, provider keys or recording masters. The Gradle wrapper JARs are required build tools and should stay committed.

The workflow is local until committed and pushed. Pushing `main` verifies the build first. A later version tag is the step that publishes the release, so do not tag until the in-game checks are complete.

1. Update `version` in all four Gradle projects, the changelog and service package version.
2. Put the approved notes in `docs/releases/<tag>.md`, for example `docs/releases/v1.0.2.md`, then commit the tested source, including `.github/workflows/release.yml`.
3. Push a matching tag, for example `v1.0.2`. Tags with a suffix such as `v1.0.1-rc.1` publish a prerelease.
4. Wait for all builds. The workflow uploads all files to a draft with the approved notes, then publishes it. If no notes file exists, it generates notes from Git history.
5. Open the website's Download Mod dialog and check the stable JAR, pack alternative and collapsed experimental option.

Do not reuse an existing release tag; fix and release a new version.

Downloads use `RealWorldMC-VERSION-mcMINECRAFT.jar`, `.mrpack` and `.zip`, with Minecraft targets `1.20.1`, `1.21.1`, `26.2` and `26.3`. Standalone JARs contain a checksum-verified service archive and a Windows x64 runtime. Thin development JARs are not release downloads.

The website reads public GitHub releases. Its version selector is maintained in the separate site repository; adding a build here does not automatically add a new selector there. Verify 1.20.1 is discoverable before announcing it. GitHub Releases lists all four targets directly.

For a local package, build the appropriate Gradle project, then run
`./build-release.ps1 -MinecraftVersion 1.21.1` (or `1.20.1` / `26.2` / `26.3`). The package uses an
allowlist and includes LICENSE, NOTICE and LICENSES. It excludes local credentials, logs, caches and worlds. Build-time `npm ci --omit=dev --ignore-scripts` installs locked dependencies into a fresh staging folder. `prepare-service.ps1` downloads a pinned, SHA-256-verified Windows x64 Node runtime, including its licence. Dependencies and runtime enter the pack and standalone JAR; user keys do not. `bundle-jar.ps1` creates the embedded archive without changing the developer JAR. The runtime extracts versioned code away from personal configuration files. Inspect `release-output/` before sharing.

Run `./test-pack.ps1 -Pack <file.mrpack>` before publishing. CI does this automatically: clean extraction, empty PATH, both localhost services, key isolation and parent-exit cleanup. No provider keys are needed for CI. The legacy installer is only an optional shortcut to open the existing pack.

Also run `./mod/gradlew.bat -p mod standaloneJarChecks -PreleaseJar=<absolute-release-jar-path>`. CI runs the matching module task for all four targets. It checks safe extraction, upgrades, key preservation and real service startup/restart with an empty PATH.

Build success checks code and packaging, not every graphics driver or live provider.
Run a manual Minecraft smoke test before tagging: setup, provider test, flight,
Street View, F5, save/reopen and `/earth back`.

## Release images and copy

Use [the prepared media](media.md#ready-to-use) in the README and release description. Keep the video as a link or attachment, with the 16:9 grid as its preview. These assets demonstrate flight; add fresh setup and Street View captures before describing those as shown in the video.

Suggested GitHub description: Minecraft Java mod for real-world exploration with Google 3D Tiles, optional Cesium access, and Google Maps Street View. Fabric 1.21.1, with experimental 26.2 and 26.3 builds.

Suggested topics: `minecraft`, `minecraft-mod`, `fabric-mod`, `realworldmc`, `google-maps`, `google-earth`, `street-view`, `3d-tiles`, `cesium`.

Workflow behavior follows [GitHub's event triggers](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow). No Google or Cesium credentials are used by the build checks.

## 1.0.2 checks

The workflow also runs the shared Node detection checks on Windows, macOS and Linux. Publishing requires these checks and all four build/package jobs to pass. The 26.3 artifacts use `RealWorldMC-VERSION-mc26.3` as their filename prefix. The website may still show 26.2 as its experimental shortcut; the GitHub release lists both targets.

`mod-26.3` shares modern source/resources with `mod-26.2`, except the terrain generator and two GPU classes whose APIs changed in 26.3. Build both whenever changing that shared code. See [26.3 details](experimental-26.3.md).

## Current candidate

See [1.0.4 validation and remaining checks](release-1.0.4.md). Build 1.20.1 with JDK 21, targeting Java 17. Its checks can run on Java 17 with `-PchecksJava=<absolute-java17-executable>`; CI builds on 21. Local checks do not substitute for macOS/Linux CI or real provider gameplay.
