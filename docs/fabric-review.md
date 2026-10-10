# Fabric review: October 10, 2026

Scope: the 1.21.1 client setup, command routing, HUD credits and Distant Horizons integration. This is a focused source audit, not an FPS benchmark or a complete shader compatibility test.

- HUD attribution was copying a list and joining text every rendered frame. It now builds the credit string when map metadata changes and publishes one immutable string to the render thread. Provider credits and their order remain intact.
- Credential tests already run asynchronously. The UI returns results on the client thread; provider requests are not performed in the frame callback.
- Setup opens on the next client tick. Only the exact local setup commands are intercepted, so ordinary server commands still reach the server.
- Distant Horizons reflection is cached once its API is ready. The frame hook guards the first frame after entering a photo world; removing it just to reduce callbacks could reintroduce visible LODs. It remains in place.
- RWMC-prefixed environment settings are now accepted, with existing names as fallbacks. Saved block, dimension and region identifiers remain stable to keep old worlds readable.

No FPS improvement is claimed without measurement. Next measure frame time and allocation rate during the same recorded flight before changing tile budgets or renderer scheduling. Network/API latency and provider coverage are separate from Fabric frame performance.

These changes are in the local working build, not a published or installed Modrinth update.
