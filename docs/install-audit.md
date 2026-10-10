# Portable install audit

## End-user flow

Windows x64 + Minecraft 1.21.1: download `.mrpack` → import into Modrinth or Prism → launch → test/save personal keys in Minecraft → create a **Real World** world.

No pack generation, folder picker, Node installation, `install.ps1`, `start-service.bat`, or `start-streetview.bat` is needed in this flow.

## Fixes

- Build-time dependency installation replaces player-side npm setup.
- The pack contains a pinned Node 24.21.0 Windows x64 runtime and its licence. The archive hash is verified before extraction.
- The service directory resolves relative to the launcher instance, so moving the instance does not break a machine-specific path.
- One managed Node process owns terrain and photo listeners. Minecraft starts it on a worker thread. Closing Minecraft's pipe ends the services; the shutdown hook also cleans up the process.
- Saving tested credentials restarts only the child owned by this game. The setup UI waits for readiness and offers a retry after errors.
- A second profile or an old manual service on the same ports is reported as a conflict. It is not killed or silently adopted.
- Native Street View `tilesKey` is separate from browser `browserKey`; newly entered native keys are not returned by `/settings`. Older configs retain a compatibility fallback until saved through setup.
- GitHub Actions builds both versions, verifies each clean pack and publishes release assets on version tags. The site selects actual `.mrpack` assets from the Releases API.

## Checks performed

- Java 21 mod build, setup checks, service ownership/start/stop/restart checks and Minecraft gameplay regression tests.
- Java 25 experimental build.
- Node service regression suite, using synthetic terrain/provider responses.
- Actual `.mrpack` extraction into a new folder with spaces; both services started using the bundled runtime with PATH empty, no personal keys and no provider requests. Native/browser key isolation and shutdown passed.
- Website build and release-dialog tests, including missing releases, retries, experimental labels and mobile layout.

## Limits and remaining manual checks

- The bundled runtime targets Windows x64. Linux/macOS/Windows ARM need their own Node runtime and further testing.
- Modrinth and Prism are launchers. The mod itself is Fabric, not Forge/NeoForge.
- Minecraft and Java are supplied by the user's account/launcher. Map access still needs their credentials, account eligibility and any usage charges.
- 26.2 gets automatic services, but still uses manual key files and browser-only Street View. Recommend 1.21.1.
- One local instance at a time (ports 8765 and 8766).
- Fresh launcher UI import, live provider billing/account tests and a complete new-world playthrough remain manual release checks. Automated extraction is not a substitute for those.
- These artifacts are built locally. No GitHub push, tag or public release was performed during this audit.

## Standalone JAR update

The release JAR is now a normal installation option for Windows x64. Fabric and Fabric API are still required. `bundle-jar.ps1` embeds the same allowlisted service and runtime used by the pack. The new release filename includes `-mc1.21.1` or `-mc26.2`; older thin JARs are not advertised as standalone downloads.

On first launch, SHA-256 is checked and files are extracted to a temporary directory before the completed bundle is moved into `realworldmc-service/.bundles/<hash>`. Path traversal and private config entries are rejected. Keys and logs remain in the parent data directory. Explicit custom service directories are left externally managed. No executable is downloaded at game startup.

Checks include safe extraction, checksum rejection, repeat startup, upgrading beside an older bundle, preserving credentials, both real localhost services with PATH empty, and shutdown. Full launcher UI and real-provider gameplay remain manual release checks.
