# RealWorldMC release checklist

Stable target: Minecraft Java 1.21.1, Fabric, Java 21, Windows x64. Minecraft 26.2 and 26.3 use Java 25 and remain experimental. Publishing source is separate from publishing a downloadable release.

## Source and packaging

- [x] RealWorldMC name, Teddy563 author, Apache 2.0 licence and packaged icon.
- [x] Separate public mod and private Astro website repositories, each starting from one initial source snapshot.
- [x] Credentials, caches, private runtimes, build outputs and personal profiles excluded from source commits.
- [x] Standalone JAR includes the local service and private Node runtime; Core packs support direct launcher import.
- [x] All three targets have automated build, extraction and clean-pack checks.
- [x] Main pushes run CI; matching version tags publish releases after all build and Node-runtime jobs pass.
- [ ] Confirm the candidate GitHub Actions checks succeed on Windows, macOS and Linux.
- [ ] Complete the live checks below before publishing a stable release tag.

## Local automated validation

Run `npm test` in `service`, `gradlew.bat build` in all three mod modules, and `gradlew.bat runGameTest` in `mod`. Release packaging also runs standalone JAR and clean-pack checks as described in [the release guide](docs/releases.md).

The checks cover 100 random new-world starts, fixed starts, saved origins, configuration migration, terrain and navigation logic, persistent edits, local service startup and credential preservation. Synthetic checks do not establish live provider coverage or shader compatibility.

## Live Minecraft checks

- [ ] Fresh Windows x64 launcher import, masked first-run setup and a successful provider test without separately installed Node.
- [ ] Google direct and eligible Cesium account access; understandable quota and authentication failures.
- [ ] New Real World starts use the 100-place catalogue; Palace of Parliament is included. Reloading preserves the selected place.
- [ ] Flight loading, distant terrain, collision, building, mining and water with real provider data.
- [ ] Street View entry, linked arrows, V walking, normal F5 camera views, nearby drops and `/earth back`.
- [ ] Placed blocks, mobs and explosions remain aligned and persist through photo travel and save/reload.
- [ ] Crater textures, photo lighting and the optional Sodium/Iris/shader/Distant Horizons combinations in the full profile.
- [ ] Existing world copy opens correctly; provider outages preserve saved terrain; credential changes recover cleanly.
- [ ] Two-hour session, visible provider attribution and review of provider terms for intended distribution.

## Experimental 26.2 and 26.3

- [ ] Live generation, rendering, collision, block masking, save/reload and far-plane appearance.
- [ ] Browser Street View, navigation and account failures.

- [ ] 26.3: run the isolated `runRenderCheck` GPU check.
- [ ] 26.3 with Propwash: drone camera, collision, camera exit, recording and optional shaders.

Do not describe 26.2 or 26.3 as having native Street View or the stable 1.21.1 setup UI. See [port status](PORT-26.2.md), [install audit](docs/install-audit.md) and [limitations](docs/limitations.md).
