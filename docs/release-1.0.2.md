# RealWorldMC 1.0.2 validation

Prepared on 15 October 2026 on `release/1.0.2`. This is a release candidate, not a published tag. Read the [release notes](releases/v1.0.2.md) and [26.3 guide](experimental-26.3.md).

## PR #2 integration

Keep the contributor's Node discovery and bundled-preview fix, with these corrections:

- Share Node discovery between all three Minecraft targets.
- Prefer a working PATH installation, validate Node 22+, sort NVM versions numerically, honor `NVM_DIR` and skip unusable installations.
- Retain explicit configuration and the Windows x64 bundled runtime.
- Exclude the unrelated preview redesign, disk-cache changes and voxel material changes. In particular, failed tile requests must remain retryable.

The integration preserves the contributor's commits. Merge the corrected 1.0.2 candidate rather than the original PR on its own. No release tag is created by preparing this branch.

## Local checks

- All 29 service tests passed, including a failed-tile retry regression check.
- The stable 1.21.1 build and its 10 GameTests passed, together with runtime, bundle, 31 setup, 19 navigation and 100-spawn checks.
- The experimental 26.2 and 26.3 builds passed their runtime, bundle and spawn checks.
- The 26.3 disposable client started and rendered a synthetic tile through the actual map renderer. GPU readback confirmed the expected color. This checks shader compilation, texture/vertex upload and draw submission without a world or provider key.
- Node discovery checks passed locally on Windows. The workflow also runs them on macOS and Linux; use the candidate's Actions run to confirm those results before merging.
- Final standalone JARs and clean pack imports passed for all three targets with an empty PATH. Both bundled services started, restarted and stopped, private settings remained intact, and the browser preview loaded from the versioned bundle.

The release workflow repeats the standalone JAR and clean-pack checks. All build and runtime jobs must pass before publication.

## Remaining manual checks

- Full gameplay on macOS and Linux, especially a GUI launcher with a minimal PATH.
- 26.3 world generation, real streamed terrain, mining/masking, save/reload and browser Street View.
- Propwash's Fabric 26.3 build: drone view, scenery beyond the pilot, collision, leaving the goggles, recording and shaders. Propwash is not bundled or certified compatible yet.
- The known Bliss blank-view issue after returning from Earth orbit remains unresolved. This update does not change stable orbital rendering.

Use a separate 26.3 test profile and a new world. Personal keys, worlds, controls and optional recording mods are not changed by this preparation. No provider requests are needed by the automated checks.
