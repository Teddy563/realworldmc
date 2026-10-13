# RealWorldMC 1.0.1 validation

Release tag: `v1.0.1`. Publication approved on 14 October 2026 with the limitations below. Read the [release notes](releases/v1.0.1.md) or [download the release](https://github.com/Teddy563/realworldmc/releases/tag/v1.0.1).

## Local checks

These results are from the local candidate checks on 11–14 October 2026, not a claim that every shader or provider combination has been tested.

- The 1.21.1 build passed 10 GameTests, 31 setup checks, 19 navigation checks, 100-location checks and existing runtime/bundle checks.
- All 28 local-service tests passed. Standalone packaging checks passed service extraction, startup, restart, shutdown and credential preservation without provider calls.
- Disposable client checks covered automatic entry, anchored motion, ascent, descent, hover, both third-person views, zoom/follow-camera compatibility, destination commands and markers, and geographic return.
- OpenGL checks covered Earth projection, atmosphere/stars, both planetary mosaics, photo rendering, excavation and terrain continuity. Iris with Bliss passed the orbital checks; its Earth return limitation is recorded above.
- The orbital-isolation fixture kept a wall, chest and creeper in the launch world. With shaders off, the wall was visible before and after orbit, and hidden during orbit. Picking and entity/block-entity suppression checks passed.
- The installed candidate preserved all 484 checked preference files. Personal profile helpers, camera bindings and optional add-ons are not release contents.


On 14 October, both the 1.21.1 (Java 21) and experimental 26.2 (Java 25) builds passed again, together with all 28 service tests. Final standalone JARs and clean imports of both modpacks also passed local startup, restart, shutdown and credential-isolation checks. The tag workflow repeats those checks before publication. See [GitHub Actions](https://github.com/Teddy563/realworldmc/actions/workflows/release.yml) for the final release results and the download checksums for packaged artifact hashes.

## Known limitations and remaining gameplay checks

- Bliss produced a blank Earth return view in an offline fixture with both the previous and updated JAR. The orbital checks passed, but ordinary Earth return with this shader remains unresolved.
- Remote-region descent still needs live map coverage. Offline tests do not prove provider availability or all graphics-driver combinations.
- Moon and Mars are orbital image globes only, without surface terrain or landings.
- The 26.2 build has no native space flight or in-game Street View. Full 26.2 gameplay testing remains outstanding.
- Personal profile controls, camera/cape/aircraft mods and local compatibility helpers are separate from the public RealWorldMC release.

The 1.21.1 candidate installed before publication had SHA-256 `a5c2f838144644c1f1cd2acbfd4cca3d23f008458d1eb8c5b57758edffb7b323`. Repackaged release artifacts can have a different hash; use the `.sha256` file supplied with each public download.
