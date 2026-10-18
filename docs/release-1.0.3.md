# 1.0.3 validation and release checklist

Prepared on 18 October 2026 on local branch `release/1.0.3`, based on PR #5 head `4e90a161da496c054680e1949861e791977de6c0`. The release preserves the contributor commits and is published from the release branch; main is not directly pushed.

## Automated checks

- Java 21 build and server GameTests for Minecraft 1.21.1; Java 25 builds for 26.2 and 26.3.
- Service tests use synthetic responses, including rate limits, panorama assembly, bounded workers and duplicate-request coalescing.
- Map checks cover dateline/polar bounds, invalid dimensions, tile budgets, optional version gates, native resource ownership, disk revalidation, corrupt PNG recovery and shared request backoff.
- Four actual client launches: Xaero absent, minimap only, world map only, both. Target render classes are loaded to exercise Mixin application. This does not certify every map-screen interaction.
- Integrated offline walking test uses a synthetic photograph and local server: default walking, free 3×3 movement, directional preparation, 5×5 waiting boundary, photo reuse, missing-link fallback, retreat cancellation and return cleanup.

Reproduce from the checkout (Java 21 for `mod`, Java 25 for modern modules):

```powershell
node --test service/test/*.test.mjs
./mod/gradlew.bat -p mod build runGameTest runStreetWalkingCheck runPhotoCheck
./mod/gradlew.bat -p mod runMapClientCheck -PxaeroTestSet=none
./mod/gradlew.bat -p mod runMapClientCheck -PxaeroTestSet=minimap
./mod/gradlew.bat -p mod runMapClientCheck -PxaeroTestSet=worldmap
./mod/gradlew.bat -p mod runMapClientCheck -PxaeroTestSet=both
./mod-26.2/gradlew.bat -p mod-26.2 build
./mod-26.3/gradlew.bat -p mod-26.3 build
```

## Validation scope and limitations

Use a separate game profile for live Google credentials and real coverage. Confirm movement at intersections, slow connections and unavailable imagery; inspect attribution, camera comfort and map-screen dimension switching. Verify unsupported Xaero versions leave the overlay disabled in that profile. Offline fixtures intentionally make no billable provider requests.

Linux/macOS execution and optional shader/mod combinations are not certified by the Windows checks. The previously documented Bliss blank-view issue after returning from Earth orbit remains outside this patch. Review the repository's CI result on the final commit before merging; a local passing build does not stand in for remote CI approval.

Package using `build-release.ps1`, validate each resulting `.mrpack` with `test-pack.ps1`, and run `standaloneJarChecks -PreleaseJar=<absolute bundled jar>` before publishing. Never package personal API keys, caches or worlds. Native Street View and this Xaero integration are 1.21.1 features; modern branches keep their existing browser workflow.

Final local checks: 39 service tests, 11 GameTests, Java 21 build, rendering and walking/entry/return checks, isolated Xaero/Iris/Sodium compatibility checks, and visual verification of help/HUD styling. The affected smaller panorama was checked using live provider imagery; automated fixtures use synthetic responses.
