# Build and publish

The `release.yml` GitHub workflow builds both versions, checks the code and
packages standalone Windows x64 JARs, importable .mrpacks and optional ZIPs. Minecraft 26.2 remains experimental even when included in a
normal RealWorldMC release.

## What runs automatically

| GitHub event | Result |
|---|---|
| Push to `main` or a pull request into `main` | Build and test both versions, then upload downloadable Actions artifacts. Nothing is published. |
| Manual Run workflow | The same build and package checks, without publishing. |
| Push a matching `v*` tag | Build and test, then publish JARs, modpacks, ZIPs and SHA-256 checksums together. |

The public mod repository is [Teddy563/realworldmc](https://github.com/Teddy563/realworldmc). The website source belongs in the separate private `Teddy563/realworldmc-site` repository. Its download dialog reads public mod releases; it does not need a private-repository token or access to your API keys.

## Prepare the first publication

Review the [manual checklist](../RELEASE-CHECKLIST.md). Commit source and the small files in `docs/assets/`; do not commit local JARs, runtime folders, provider keys or recording masters. The Gradle wrapper JARs are required build tools and should stay committed.

The workflow is local until committed and pushed. Pushing `main` verifies the build first. A later version tag is the step that publishes the release, so do not tag until the in-game checks are complete.

1. Update `version` in both Gradle projects, the changelog and service package version.
2. Commit the tested source, including `.github/workflows/release.yml`.
3. Push a matching tag, for example `v1.0.1`. Tags with a suffix such as `v1.0.1-rc.1` publish a prerelease.
4. Wait for both builds. The workflow uploads all files to a draft, then publishes it.
5. Open the website's Download Mod dialog and check the stable JAR, pack alternative and collapsed experimental option.

Do not reuse an existing release tag; fix and release a new version.

Standalone downloads are named `RealWorldMC-VERSION-mc1.21.1.jar` and `RealWorldMC-VERSION-mc26.2.jar`. These include a checksum-verified service archive and private runtime; older thin JAR filenames are intentionally not selected by the site.

Importable downloads are named `RealWorldMC-VERSION-mc1.21.1.mrpack` and
`RealWorldMC-VERSION-mc26.2.mrpack`. The site matches these names through GitHub's
public Releases API. No website token is needed. It excludes prereleases from
the recommended 1.21.1 option, permits them for 26.2, and caches results for five
minutes. Missing files or API limits show a releases-page fallback, never a guessed URL.

For a local package, build the appropriate Gradle project, then run
`./build-release.ps1 -MinecraftVersion 1.21.1` (or `26.2`). The package uses an
allowlist and includes LICENSE, NOTICE and LICENSES. It excludes local credentials, logs, caches and worlds. Build-time `npm ci --omit=dev --ignore-scripts` installs locked dependencies into a fresh staging folder. `prepare-service.ps1` downloads a pinned, SHA-256-verified Windows x64 Node runtime, including its licence. Dependencies and runtime enter the pack and standalone JAR; user keys do not. `bundle-jar.ps1` creates the embedded archive without changing the developer JAR. The runtime extracts versioned code away from personal configuration files. Inspect `release-output/` before sharing.

Run `./test-pack.ps1 -Pack <file.mrpack>` before publishing. CI does this automatically: clean extraction, empty PATH, both localhost services, key isolation and parent-exit cleanup. No provider keys are needed for CI. The legacy installer is only an optional shortcut to open the existing pack.

Also run `./mod/gradlew.bat -p mod standaloneJarChecks -PreleaseJar=<absolute-release-jar-path>`. CI runs the matching module task for both versions. It checks safe extraction, upgrades, key preservation and real service startup/restart with an empty PATH.

Build success checks code and packaging, not every graphics driver or live provider.
Run a manual Minecraft smoke test before tagging: setup, provider test, flight,
Street View, F5, save/reopen and `/earth back`.

## Release images and copy

Use [the prepared media](media.md#ready-to-use) in the README and release description. Keep the video as a link or attachment, with the 16:9 grid as its preview. These assets demonstrate flight; add fresh setup and Street View captures before describing those as shown in the video.

Suggested GitHub description: Minecraft Java mod for real-world exploration with Google 3D Tiles, optional Cesium access, and Google Maps Street View. Fabric 1.21.1, with an experimental 26.2 build.

Suggested topics: `minecraft`, `minecraft-mod`, `fabric-mod`, `realworldmc`, `google-maps`, `google-earth`, `street-view`, `3d-tiles`, `cesium`.

Workflow behavior follows [GitHub's event triggers](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow). No Google or Cesium credentials are used by the build checks.
