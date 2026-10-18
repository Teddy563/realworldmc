# Street View

For Minecraft 1.21.1, import the full `.mrpack`, open `/earth setup`, and test/save the optional Google Map Tiles API key. The mod starts the map and photo services automatically. Use `/earth streetview` and `/earth back`, or Sneak + Swap Offhand (normally Shift+F) with empty hands on the ground. `/earth streetkey off` disables the shortcut.

Walking prepares a connected photo one block from center, changes stops at the 3×3 boundary and waits inside a 5×5 area if needed. You arrive behind the next center without terrain blocking movement. V toggles fixed-center navigation; arrow keys travel in either mode. Nearby walking uses limited parallax and a short fade; V selects fixed-center mode. `/earth hud off` hides the optional panel; `/earth usage off` hides only counts. See [1.0.3 behavior](docs/releases/v1.0.3.md).

See [setup](docs/setup.md), [accounts and costs](docs/accounts-and-costs.md), and [controls](docs/controls.md).

The optional browser viewer (also used by 26.2) needs its own Maps JavaScript API browser key in `realworldmc-service/streetview.config.json` as `browserKey`. Restrict it to the localhost viewer origin and required API. The native Map Tiles key is stored as `tilesKey` and is not exposed through the browser settings endpoint. The browser viewer is at http://127.0.0.1:8766/ while Minecraft is running. Manual file edits require restarting Minecraft. Keep provider credits and report-photo controls visible.

Photo navigation does not supply real interiors or precise wall depth. Existing building and explosion effects use approximate Minecraft collision geometry. Coverage, account eligibility and usage charges depend on your provider.
