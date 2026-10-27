# RealWorldMC 1.0.4 implementation plan

Audit: 27 October 2026. Planning and source tracking only; no PR merged or release created.

## Baseline

Latest release: [v1.0.3](https://github.com/Teddy563/realworldmc/releases/tag/v1.0.3), published 18 October. Local tracked HEAD and origin/main both point to `6f18d1f` (release rerun fix, PR #8). The current checkout is `port/1.20.1`; the new port was untracked before this audit.

Release targets: Fabric 1.20.1 (new, required), 1.21.1 (recommended), 26.2 and 26.3 (experimental). Native feature changes must not be advertised as experimental-target feature parity.

## Order of work

### 1. Preserve and integrate the 1.20.1 port

Follow [the port handoff](port-1.20.1-handoff.md). First commit the source snapshot and build exclusions separately, preserving its known static generator Codec fix. Add deterministic packaging of the current service, clean base pack, CI and clean-launch/save-reopen checks. Retain Heroes as an optional pack, not a mandatory dependency of RealWorldMC.

### 2. Fix launch crash #12 before feature work

[Issue #12](https://github.com/Teddy563/realworldmc/issues/12) reports a MixinExtras AnnotationNode ClassCastException transforming FireworkRocketItem with only RealWorldMC and Fabric API, Loader 0.17.2, Java 21.0.7.

The published metadata permits Loader >=0.16.0, but development uses 0.19.5. This is a compatibility hypothesis, not a confirmed root cause. Reproduce with the published 1.0.3 JAR and the exact reported minimal stack. Compare the tested loader and the declared minimum; inspect the actual MixinExtras version and remapped rocket mixin annotations. Fix the injection or dependency declaration based on the result. Do not prescribe unrelated optimization mods as a workaround.

Gate: minimal packaged-JAR launch reaches the menu and a world on every supported loader baseline; normal rockets and equipment-free boosts still work. Include Java 17 / 1.20.1 in the launch matrix. If raising the minimum loader is required, tell users plainly in metadata, setup and release notes.

### 3. Resolve region crossing #7; revise PR #9

[Issue #7](https://github.com/Teddy563/realworldmc/issues/7) describes NYC changing to Croatia across a barrier. Current EarthRegions.indexAt rounds positions into cells spaced 50,000 blocks apart, so crossing a midpoint can select a different saved geographic origin. The issue has a linked user video; review that repro during implementation (not watched in this audit).

[PR #9](https://github.com/Teddy563/realworldmc/pull/9), reviewed at `313e9920`, has green CI. Its 22,000-block radius only creates a gap: the next center becomes valid again at 28,000 blocks along the same axis. It does not guarantee geographic continuity and may expose empty terrain in the gap. Do not close #7 on the radius change alone.

Implement explicit ownership of the active geographic region. Before its safe limit, prepare a geographically adjacent origin and translate position, velocity and camera heading together. Retain current coverage until the new frame is ready. If preparation fails, retain the current view and provide a bounded stop/retry path; never infer a destination from an unrelated occupied grid cell. Saved worlds and existing edits must retain their geographic association.

Gate: populated adjacent NYC/Croatia cells, both directions, diagonals, negative boundaries, high-speed flight, orbit return, failed provider request and world reload. Confirm coordinates and scenery agree before/after each transition. Apply to 1.20.1, 1.21.1 and experimental targets, including renderer and collision callers.

### 4. Harden the worker-pool part of PR #9 separately

Useful direction: keep CPU voxelization off the HTTP event loop. Static review found:

- A replacement worker is pushed into freeWorkers by _spawnWorker and may immediately receive a queued task through _processNext without being removed from that free list. A new request can then assign a second task to the same worker and overwrite activeId.
- The pending queue is unbounded; explicit worker counts are not bounded integers. Add a conservative cap and backpressure.
- Guard worker retirement against duplicate error/exit handling, failed respawns and postMessage serialization errors. Every accepted task must resolve or reject; close must reject new work.
- Mesh inputs are structured-cloned; only output buffers are transferred. Measure memory and CPU before calling the entire operation zero-copy.
- Existing tests check output shape, not equivalence to synchronous results under real coordinates or worker failure.

Gate: byte-for-byte sync/worker parity, queue saturation, crash with queued work, failed spawn, duplicate exit, shutdown, invalid counts and a responsiveness/memory benchmark. Preserve the contributor commit and add focused fixes, or split the PR with the contributor before merge. Do not bundle incomplete boundary behavior with an otherwise useful worker optimization.

### 5. Rebase and narrow PR #10 after #9

[PR #10](https://github.com/Teddy563/realworldmc/pull/10), reviewed at `b5045b40`, includes PR #9's exact commit plus updater, Polish translations and rendering settings. CI is green, but it does not test safe update installation. Rebase after #9 so its changes are not integrated twice.

Keep: version notice, changelog UI, translations and a bounded quality-settings screen, after these changes:

- Match the exact Minecraft/loader release asset. Never fall back to the first JAR if the right version is missing.
- Current updater writes a new versioned JAR beside the old one, leaving duplicate mod IDs on restart. For 1.0.4, prefer a Releases/download-folder workflow with clear replacement steps. Defer in-place installation until it has verified staging, backup, restart-time replacement and rollback tests; never overwrite a loaded JAR.
- Validate downloaded filename/path, size, release checksum and mod metadata. Handle interrupted downloads and GitHub rate limits without freezing startup.
- Use Fabric's version parser or another existing semver implementation. Current digit-stripping can misread +mc1.20.1 build metadata and prereleases.
- Marshal progress/UI mutations onto the client thread; handle closed/resized screens. Translate hardcoded English/Polish status text too.
- Bound fineRadius/far/maxTiles/maxSse on both client and service. Apply quality settings before orbital policy so local distance overrides do not overwrite orbit distances. Save atomically through existing config conventions and preserve credentials; report save errors instead of silently ignoring them.

Gate: exact assets for all four targets, no matching asset, offline/403/429, interrupted download, duplicate-mod prevention, prerelease/build metadata comparisons, UI scaling, language fallback and orbital settings.

### 6. Address installation question #11

[Issue #11](https://github.com/Teddy563/realworldmc/issues/11) is a setup/documentation issue: RealWorldMC is a Fabric mod, not a datapack. Explain matching Minecraft + Fabric + Fabric API, placing the JAR in mods, and importing .mrpack through a compatible launcher. Do not claim an untested launcher is supported. Prepare a reply for owner review; no comment was posted in this audit.

## Merge and release gates

1. Source-tracked 1.20.1 port with packaging/tests.
2. Minimal-launch fix #12.
3. Revised region fix and hardened worker pool from #9; keep independent commits.
4. Rebased safe subset of #10, with direct in-place updater deferred unless fully validated.
5. Docs/#11, four-target CI, release notes and clean pack import.

Keep contributor attribution. New maintainer commits use Teddy563 <real@teddy.bar>, no automated co-author trailers. Do not force-push contributor branches or merge solely because CI is green.

Version all mod targets and service to 1.0.4. Generate each JAR/ZIP/mrpack from the same commit and service source; check metadata, archive contents and checksums. Test no-Node Windows startup and documented macOS/Linux Node discovery. Ensure no provider credentials, personal worlds, cached tiles, patched private add-ons or build outputs enter source/releases.

Only tag v1.0.4 after all four build/import/launch gates pass. Keep 1.21.1 recommended; describe 1.20.1 as newly supported with its tested feature list. Publish GitHub first and verify website asset selection for four targets; update Modrinth separately with the matching files and versions.

## Evidence and limits

Read open issues and comments, full PR file diffs, PR commit lists and CI statuses through GitHub API. Both PRs passed six existing checks (three builds plus three Node OS checks). Read the local port source/build metadata and prior handoff; inspected the existing Java 17 JAR. No new gameplay tests or provider requests were performed, and no PRs/comments/releases were changed. The root HANDOFF.md is historical and still contains obsolete 1.0.1/1.0.2 status; update its current-status section during implementation.
