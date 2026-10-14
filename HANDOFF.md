# RealWorldMC handoff

## 1.0.2 preparation, 15 October 2026

Work is on `release/1.0.2`. It integrates PR #2 with corrected shared Node detection and retains the bundled-preview fix. Unrelated preview UI/cache/material changes are excluded. The new `mod-26.3` target shares 26.2 code/resources except the terrain generator and two graphics classes. CI builds all three targets and checks Node discovery on Windows/macOS/Linux. See [1.0.2 validation](docs/release-1.0.2.md) and [26.3 setup](docs/experimental-26.3.md). No release tag should be pushed before reviewing those results.

Updated 2026-10-14 for the approved 1.0.1 release. The sections dated before this update preserve the development and testing record. See [release notes](docs/releases/v1.0.1.md) and [current validation and limits](docs/release-1.0.1.md).

## Project locations

- **Mod:** `D:\RealWorldMC`, branch `main`, origin `https://github.com/Teddy563/realworldmc.git`. The 1.0.2 candidate is on `release/1.0.2`.
- **Website:** `D:\RealWorldMC-SITE`, separate Astro project intended for `Teddy563/realworldmc-site`.
- **Installed personal profile:** `D:\ModrinthApp\profiles\RealWorldMC-1.0.0-mc1.21.1`.
- The working copy at `work/RealWorldMC` is build staging, not the canonical GitHub checkout.
- The published source begins with one 1.0.0 initial snapshot. Earlier local Git history is backed up outside this repository. Source publication does not create a release tag or downloadable release.

## What it does

| Feature | Fabric 1.21.1 | Experimental 26.2 / 26.3 |
|---|---|---|
| Real World preset and 3D terrain exploration | Yes, Java 21 | Yes, Java 25 |
| 100 random new-world starts | Shared catalogue | Shared catalogue |
| Standalone Windows x64 JAR with private Node runtime | Yes | Yes |
| First-run masked key setup, `/earth setup` | Yes | Manual configuration |
| Native Street View, blocks, mobs and excavations | Yes, approximate photo projection | Browser viewer only |
| Native camera toggle | F5 | Native Minecraft camera |

The display name is **RealWorldMC**, author **Teddy563**. Published version: **1.0.1**; prepared candidate: **1.0.2**. The visible world type is **Real World**. `/earth` is the promoted command; stable also accepts `/rw`.

New worlds select one of [100 curated destinations](docs/spawn-locations.md), including the Palace of Parliament. Selection is uniform and the coordinate pair is serialized with the generator. Reloads and deaths never reroll the world. Fixed custom starts remain supported.

## Architecture

```mermaid
flowchart LR
  Site[Astro website] --> GitHub[Public releases and docs]
  Client[Fabric client: setup, render, input] --> Terrain[Local terrain service :8765]
  Client --> Photos[Local photo service :8766]
  Server[Integrated Minecraft server] --> Terrain
  Terrain --> Provider[Google direct or eligible Cesium access]
  Terrain --> OSM[OpenStreetMap data]
  Photos --> Google[Google Street View tiles]
  Server --> Saves[World origin, regions, blocks and entities]
  Catalogue[Shared 100-place catalogue] --> Server
```

The website reads public GitHub releases. It never receives API keys or renders live map tiles. The mod talks to local services, which use the player's own provider credentials.

| Directory/file | Responsibility |
|---|---|
| `shared/src/main/java/dev/realworldmc/SpawnLocations.java` | Immutable common spawn catalogue and selection |
| `mod/` | Java 21 / Minecraft 1.21.1 Fabric target |
| `mod-26.2/` | Java 25 experimental implementation shared with 26.3 |
| `mod-26.3/` | Exact 26.3 target, terrain/GPU overrides and a disposable rendering check |
| `mod/src/main/java/dev/realworldmc/RealWorldMC.java` | Commands, travel and region synchronization |
| `EarthChunkGenerator`, `EarthRegions`, `Geo` | Collision chunks, saved geographic regions and coordinate transforms |
| `PanoramaRoom`, `PanoramaPlaces`, `StreetEdits` | Photo stops, travel handshake, return state and persistent damage |
| `RWMessages`, `TravelLoading` | Styled chat and loading feedback |
| `SetupCredentials`, `ServiceRuntime`, `BundledService` | Local credentials, automatic service lifecycle and runtime extraction |
| `mod/src/client/java/dev/realworldmc/client/` | Map/photo renderers, setup UI, input, HUD and compatibility |
| `MapRenderer`, `MapTile`, `TileData` | Coarse-to-fine streamed 3D scenery |
| `PanoramaRenderer`, `PhotoEditsRenderer`, `PhotoTerrainMask` | Photo projection, visible edits and hidden collision surfaces |
| Client mixins | Native camera, photo-only Iris pipeline and terrain masking |
| `service/src/server.mjs` | Terrain HTTP endpoints |
| `service/src/provider.mjs`, `tiles.mjs`, `select.mjs` | Provider sessions, glTF/Draco data and LOD selection |
| `service/src/voxel.mjs`, `geo.mjs`, `osm.mjs` | Voxel collision, coordinates and water data |
| `service/src/streetview-server.mjs`, `streetview-native.mjs`, `streetview-navigation.mjs` | Photos, panorama assembly and connected navigation |
| `modpack/`, `build-pack.ps1` | Core, visual and experimental pack generation |
| `mod/src/checks/`, `mod/src/gametest/`, `mod/src/clientcheck/`, `service/test/` | Logic, Minecraft, render and service checks |

## In-game design

- Green **RW**, dark-gray separator, white body text and vanilla light-yellow commands. Clickable help suggests commands without executing them.
- Travel uses a green boss bar with named stages. Photo loading uses the action bar. Avoid repetitive chat status lines.
- F5 is the normal Minecraft perspective binding in both modes. The custom X binding and `/earth camera 1/2/3` are removed. The user's live profile successfully cycled all three perspectives after correcting the keybinding confusion.
- Street View: W toward a selected yellow arrow or arrow keys to travel; V toggles a 3×3 walking square; F7 sources; F8 photo report; `/earth back` returns.
- Untouched collision terrain is hidden under the panorama. Player blocks and excavated surfaces render with Minecraft textures and depth shading. Edits and entities remain at their geographic positions across photo travel.
- Photo worlds use midday light and the vanilla Iris pipeline. Normal Earth shader settings remain available. Distant Horizons is suppressed for Earth/photo scenery to avoid competing LODs.

## Boundaries and saved data

- Photos have no measured wall depth or building interiors. Nearby walking and third person approximate ground projection. Real 3D tile collision is also an approximation.
- Coverage, permissions, network latency and provider quotas affect loading. Do not silently generate fake successful Earth terrain or hide authentication failures.
- New random starts are a curated list, not a verified availability guarantee at every coordinate. Do not probe 100 billable locations at each launch.
- Preserve saved `earthcraft:*` registry names and the loader compatibility alias. Renaming saved namespaces would break worlds; current code packages and display branding use RealWorldMC.
- Credentials belong in local service configuration, never Git, browser code, screenshots, logs or release assets.
- Runtime extraction must preserve credentials on upgrades. Restart/reload only services owned by the mod.
- The personal profile has extra flight/physics mods. They are not part of the public Core pack, and there is no confirmed reason to remove them merely because a camera issue was reported.

## Build and release

Use Java 21 for `mod`, Java 25 for `mod-26.2`, and Node 24 for service tooling. From the corresponding module, run `gradlew.bat build`. Stable also has `gradlew.bat runGameTest`. Both `check` tasks include `spawnLocationChecks`.

The stable regression checks cover all 100 random choices, coordinate pairs, fixed overrides, old configuration migration and serialized-origin reloads. Automated checks do not replace visual gameplay testing.

From the repository root, `build-release.ps1 -MinecraftVersion 1.21.1` (or `26.2`) prepares the standalone JAR, packs, full ZIP and checksums. `prepare-service.ps1`, `bundle-jar.ps1` and `test-pack.ps1` handle the private Node runtime and portable install checks.

Locally prepared bundles are in `release-output/standalone-ready` and `release-output/standalone-experimental`. Check timestamps and the current validation note before using them. Root-level JARs/packs may be older. Build output is not publication.

`.github/workflows/release.yml` builds both versions and checks services/pack extraction on pushes and pull requests. A matching `vVERSION` tag publishes only after both matrix jobs pass. It assembles a draft release, uploads complete assets, then publishes it. No map keys are needed in CI.

## Reading and remaining captures

- [README](README.md), [documentation index](docs/README.md), [setup](docs/setup.md), [keys and costs](docs/accounts-and-costs.md), [controls](docs/controls.md), [limitations](docs/limitations.md).
- [Release process](docs/releases.md), [release checklist](RELEASE-CHECKLIST.md), [experimental port](PORT-26.2.md).
- [Apache 2.0](LICENSE), [NOTICE](NOTICE), [licensing notes](docs/legal/README.md), [code of conduct](CODE_OF_CONDUCT.md). Retain applicable third-party notices.
- [Media checklist](docs/media.md) and [recording plan](docs/recording-plan.md). The README uses one static grid, with Golden Gate at bottom-right, rather than a GIF.
- Still capture: empty setup UI, masked successful key test, Real World creation, native Street View navigation, matching crater before/after, and F5 in flight/photo modes. Keep keys private and provider attribution readable.

## Latest local validation

2026-10-11: both Minecraft builds and shared spawn-selection checks passed. All 10 stable GameTests passed, including saved-origin round trips and configuration migration. Both standalone JAR checks and clean pack-import checks passed without provider requests. The personal 1.21.1 profile was updated while Minecraft was closed and `randomSpawn` was enabled. No saved worlds or API keys were changed.

Installed JAR SHA256: `3757822d695d8022d79558394bbb7f006a1fd84aefc7eb29d12c01faebec12ca`.

The 100 candidate coordinates have not all been live-validated against paid provider imagery. New-world in-game visual testing remains useful. No GitHub release has been published.

## Local commit identity

Use Teddy563 <real@teddy.bar> for new local project commits. Do not append automated co-author trailers or generated-by authorship labels. Keep the initial snapshot as the root of the published history; local history backups stay outside the repository. The repository-local Git settings use this identity; this does not configure cryptographic signing.

## Prepared 1.0.1 candidate, 11 October 2026

This section supersedes the earlier installed-JAR hash above. The current public-release draft is [release-1.0.1](docs/release-1.0.1.md); no 1.0.1 commit, tag or release has been created. Both mod version files and the service package version are 1.0.1. Only the stable build was rebuilt in this pass.

The 1.21.1 candidate adds experimental globe flight, descent to the geographic location below orbit, bottom-left stacked controls/status above credits, and larger Street View arrows/browser buttons. Creative singleplayer flight enters automatically above 2 km over the region's saved ground reference and exits below 1.6 km while descending. `autoSpaceFlight` and `spaceEntryHeight` configure entry. Steve stays physically anchored while interpolated altitude and rotation move the Earth view; after return, automatic entry waits until the player flies below the exit height.

`SpaceFlight` handles controls and return, `GlobeOrbit` transforms the geographic viewpoint, `SpaceView` renders the globe, `OrbitLanding` prepares the destination on the integrated server, and `ActionBar` owns the compact corner HUD. `/earth actionbar` toggles those hints. `MapRenderer` keeps old scenery until replacement coverage is ready, rejects uploads from previous region epochs and retains tile ownership across descent; `RegionFrame` reprojects that retained scenery into the destination frame. New-location provider loading can still pause descent.

The personal recording profile was restored to its earlier controls on 12 October: F5 perspective, Mouse 5 hold freelook, C hold Zoomify, F6 alternate camera, F10 detach, and 4-block third-person distance. The space update must preserve these preferences. A separate private helper dismisses Multiverse's beta notice. A local F-22 Raptor 1.1.0 patch makes its FOV callback compose with Zoomify instead of bypassing it; retain/recheck this fix when updating that add-on. Neither tweak belongs in the public mod JAR.

Stable build, service tests, standalone extraction/startup checks and disposable in-game camera/flight checks passed. The latest client run also passed automatic entry, stable player position, altitude interpolation, descent/return without re-entry, tile retention and stale-upload checks. See the release draft for limits and remaining manual checks. Installed stable JAR SHA256: `e46b89b66bcb83b38ea6f67a0370292f787b2c0fd64651bda958bb6105947ce3`. Personal profile backups and compatibility-patch source are outside this repository in the task workspace. The latest replacement changed only the RealWorldMC JAR; worlds, keys and filming add-on settings were preserved.

## Latest orbital update, 13 October 2026

This supersedes the older candidate hash above. The stable candidate is installed locally, remains version 1.0.1, and is not published. SHA-256: `2db9c0005fb93ed7f7a3bcf3dda529706d9dc5d7bfb7f002699ae96a9ab820a7`.

- `SpaceFlight` owns entry, altitude, body selection, Earth return and the stable physical anchor. `FlightPose` interpolates movement-driven body tilt, heading, arm strength and bank.
- `SpacePoseMixin` and `SpaceModelMixin` apply that pose without changing equipment or real fall-flying physics. `FlightVisibilityMixin` keeps the local camera subject visible and lit in orbit.
- `SpaceView` handles the separate distant depth range, atmosphere fade and stars. `PhotoIrisMixin` selects a cached vanilla orbital pipeline without overwriting shader preferences; `SpaceWeatherMixin` removes local clouds/precipitation there.
- `CelestialBody` and `PlanetRenderer` draw Moon/Mars global image spheres. `SpaceDestinations` implements aim-and-hold transfers with cancellation. `/rw moon`, `/rw mars`, `/rw earth` also accept `/earth` as a prefix.
- Planet maps live in `mod/src/client/resources/assets/realworldmc/textures/space/`. Provenance, limitations and credits are in `docs/planetary-data.md`, `NOTICE` and `LICENSES/planetary-imagery.md`. No extra planetary API key is needed.
- The stable build, 28 service tests, standalone service checks and live OpenGL/integrated-world tests passed. The client checks passed with Bliss and the enabled personal-profile mods. Full details and release gates are in `docs/release-1.0.1.md`.

The profile's restored 4-block camera distance, Mouse 5 freelook and C zoom are unchanged. Do not reapply the earlier 16-block filming preset. No personal world or credential files were changed. Moon/Mars are orbital flyovers, not terrain or landings. Native space remains 1.21.1 only.

## Orbital scene isolation, 14 October 2026

Installed a replacement 1.21.1 candidate with SHA-256 `a5c2f838144644c1f1cd2acbfd4cca3d23f008458d1eb8c5b57758edffb7b323`. It remains unpublished version 1.0.1. `PhotoTerrainMixin`, `FlightVisibilityMixin`, `OrbitalWorldEffectsMixin` and `SpaceWeatherMixin` suppress launch-world chunk layers, non-player entities, block entities, particles, border and selection effects only while `SpaceView.active()`. `GameRendererMixin` replaces orbital picking with a miss so hidden Earth blocks/entities cannot be targeted. No entities or saved terrain are deleted.

Build/GameTests, packaged service startup checks, live orbital checks with Bliss, and vanilla before/orbit/return rendering checks passed. The vanilla fixture's magenta wall was visible before and after orbit, absent in orbit; entity rendering resumed after return. A blank local-world return view was also observed with Bliss in this offline fixture. The previous installed JAR reproduced it, so it is an existing shader/fixture limitation, not certified fixed here. Investigate separately before claiming complete shader compatibility.

Only the profile's JAR changed; hashes of 484 preference files were unchanged. Evidence and the old JAR are under the task workspace's `outputs/orbital-isolation-validation` and `backups/orbital-isolation-20261014`. The optional `realworldmc.baselineComparison` test property skips the new isolation assertions when testing an older JAR; do not use that flag to validate the new fix.

The user asked whether Moon/Mars have Earth-like tile services. Verified Cesium Moon and Cesium Mars provide actual 3D Tiles. The existing Google-only Cesium adapter cannot use them by changing its asset ID. No planetary surface implementation or provider-access change was made. See `docs/planetary-data.md` for links, resolutions and integration requirements. Do not replace real terrain with invented elevation from imagery.
