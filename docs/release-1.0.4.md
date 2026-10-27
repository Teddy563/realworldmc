# 1.0.4 local validation and handoff

Date: 27 October 2026. Branch: `release/1.0.4`. Baseline: `6f18d1f` (v1.0.3 main). Local candidate only: no push, remote merge, tag or publication. Personal Minecraft profiles and credentials were not changed.

## What changed

| Area | Implemented | Still verify before publishing |
|---|---|---|
| Fabric 1.20.1 | Tracked backport, Java 17 bytecode, clean Fabric API base pack, current bundled services, fourth CI target | Fresh graphical setup, live map and Street View, save/quit/reopen on Java 17 |
| Startup crash #12 | Replace flight/rocket Redirect annotations with WrapOperation; retain normal rocket behavior | Exact reporter API 0.116.13 and published packaged-JAR launch; test with their add-ons |
| Region crossing #7 | Start adjacent geographic terrain preparation at 18 km; bound normal horizontal movement at 21 km; async arrival chunks; transform position, heading and velocity; retain native renderer tiles | Fly both ways through a saved NYC/Croatia grid boundary, diagonal/high-speed flight, turn back during loading, disconnect provider, retry, save/reopen and orbit return |
| Worker pool / PR #9 | Bounded queue, two workers by default, explicit limits, synchronous fallback, crash recovery and shutdown | Real long-flight memory/FPS comparison; no performance gain is claimed from synthetic checks |
| Settings / PR #10 | Native `/earth settings`, bounded request overrides, atomic local saves, no provider-config rewrite | Small GUI scales, slider/preset behavior, persistence and live detail changes |
| Update notice / PR #10 | Native `/earth update`, exact target asset matching, bounded network reads and response size, release notes and browser link | Offline/rate-limited UI, long notes, Polish text and small windows |
| Installation issue #11 | JAR vs mrpack vs datapack instructions | Fresh-user follow-through; no issue reply posted |
| Experimental 26.2 / 26.3 | Compile/check/package the shared service and boundary fixes | Real GPU launch, rendering and provider travel on both targets; native settings/photo UI parity is not claimed |

## Automated evidence

All commands ran locally on Windows. Build logs and candidate artifacts are ignored under `release-output/`.

- All four targets: Gradle `build` passed after the implementation changes.
- All four standalone JARs: extraction, checksum, upgrade/reuse, credential preservation and real bundled-service lifecycle checks passed with an empty PATH.
- All four `.mrpack` files: clean temporary import, both services, private-key isolation and automatic shutdown passed with no provider requests or installed Node dependency.
- Service: **45/45 tests passed**, including worker crashes, queue limits, byte parity and render override validation (`release-output/service104.log`).
- 1.21.1: **11 Minecraft GameTests passed**, also tested on Loader 0.17.2. Tested Fabric API is 0.116.17, not the exact reporter's 0.116.13.
- 1.21.1 photo/camera rendering checks passed on Loader 0.17.2 / MixinExtras 0.5.0: camera separation, panorama pixels, textured excavation/TNT, map-wall holes, clipping/render-state restoration and orbital imagery (vanilla pipeline). Receipt: `mod/run-photo-check/photo-check-result.txt`; log: `release-output/photo104-loader0172.log`.
- 1.21.1 isolated graphical flight test passed: forward/back/strafe, throttle, hover/up/down, first/rear/front views, real rockets and expiry, block collisions, anchored orbit/return and survival cleanup. Receipt: `mod/run-flight-check/creative-flight-result.txt`.
- 1.20.1: shared checks passed on **Java 17**, plus **2 Minecraft GameTests** for generator Codec round-trip and saved-region identity/movement ownership. A serialization round-trip does not replace a graphical world reopen test.
- Shared boundary checks cover **245** radial/negative/diagonal/high-speed movements, turning back and nonfinite input. Release metadata checks cover exact assets across all four targets and version ordering.
- `git diff --check` passed. No coverage-percentage instrumentation was run; these are scenario-based checks, not a claimed line-coverage percentage.

Final per-target logs: `release-output/final-VERSION.log`, `standalone-VERSION.log`, `packcheck-VERSION.log`. Downloads: `release-output/v1.0.4-final/VERSION/`.

## Manual test order

1. Back up a world and use a separate Fabric profile. Install exactly one matching RealWorldMC JAR plus Fabric API, or import the clean mrpack. Do not overwrite a working personal profile to test this candidate.
2. On 1.20.1/Java 17, test first-run masked setup, map loading, Street View, placing/mining blocks, F5 and save/quit/reopen. Repeat the short flow on recommended 1.21.1.
3. Run `/earth fly on`. Check W/S and A/D direction, Space/Shift, hover and rocket boost. Check your camera/add-on mods separately.
4. For the region regression, use a copy of a world containing the previously unrelated adjacent saved locations. Fly beyond 18 km from the current region center. Confirm `/earth where`, scenery, heading and motion agree through preparation. Repeat at high speed, diagonally and on the return trip. Interrupt provider access: loading must end or retry without jumping to an unrelated location. World edits must remain at their original geographic places.
5. Open `/earth settings` and `/earth update` at normal and large GUI scales. Confirm settings persist; keys stay intact; update opens the browser but never replaces the installed mod. Test without internet too.
6. Launch experimental 26.2/26.3 in separate test worlds. Check map loading and region transition. These targets still use the browser Street View companion.
7. After a future push, require Windows/macOS/Linux CI to pass. This Windows session does not certify macOS or Linux gameplay. Add 1.20.1 to the separate website selector if needed; its code was not changed here.

## PR and release decision

PR #9 and #10 contributor commits are preserved locally. Follow-up changes fix their queue lifecycle, region ownership and unsafe running-JAR replacement. Merge the reviewed combined branch rather than assuming the original PR heads contain those fixes. Do not close #7 based only on the numerical boundary tests. Keep #12 open until the reporter/minimal packaged launch is confirmed.

Draft notes: [v1.0.4](releases/v1.0.4.md). New maintainer commits use Teddy563 <real@teddy.bar>, without co-author trailers. Imported contributor authors remain credited. No release tag exists for this candidate yet.
