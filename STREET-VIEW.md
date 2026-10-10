# Street View

For Minecraft 1.21.1, import the full `.mrpack`, open `/earth setup`, and test/save the optional Google Map Tiles API key. The mod starts the map and photo services automatically. Use `/earth streetview` and `/earth back`.

See [setup](docs/setup.md), [accounts and costs](docs/accounts-and-costs.md), and [controls](docs/controls.md).

The optional browser viewer (also used by 26.2) needs its own Maps JavaScript API browser key in `realworldmc-service/streetview.config.json` as `browserKey`. Restrict it to the localhost viewer origin and required API. The native Map Tiles key is stored as `tilesKey` and is not exposed through the browser settings endpoint. The browser viewer is at http://127.0.0.1:8766/ while Minecraft is running. Manual file edits require restarting Minecraft. Keep provider credits and report-photo controls visible.

Photo navigation does not supply real interiors or precise wall depth. Existing building and explosion effects use approximate Minecraft collision geometry. Coverage, account eligibility and usage charges depend on your provider.
