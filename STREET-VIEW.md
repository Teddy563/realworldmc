# Street View

For Minecraft 1.21.1, import the full `.mrpack`, open `/earth setup`, and test/save the optional Google Map Tiles API key. The mod starts the map and photo services automatically. Use `/earth streetview` and `/earth back`, or Sneak + Swap Offhand (normally Shift+F) with empty hands on the ground. `/earth streetkey off` disables the shortcut.

Walking leaves the complete 3×3 area free for building: preparation starts at 1.55 blocks from center and travel starts at 1.8 blocks and waits inside a 5×5 area if needed. You arrive behind the next center without terrain blocking movement. V toggles fixed-center navigation; arrow keys travel in either mode. Both walking and fixed-center travel crossfade. Nearby walking uses limited parallax; the photo ground follows the real camera height in first and third person. V selects fixed-center mode. `/earth hud off` hides the optional panel; `/earth usage off` hides only counts. `/earth controls off` hides key hints, `/earth walk on|off` chooses movement, and `/earth camera first|back|front` chooses perspective. See [1.0.3 behavior](docs/releases/v1.0.3.md).

See [setup](docs/setup.md), [accounts and costs](docs/accounts-and-costs.md), and [controls](docs/controls.md).

The optional browser viewer (also used by 26.2) needs its own Maps JavaScript API browser key in `realworldmc-service/streetview.config.json` as `browserKey`. Restrict it to the localhost viewer origin and required API. The native Map Tiles key is stored as `tilesKey` and is not exposed through the browser settings endpoint. The browser viewer is at http://127.0.0.1:8766/ while Minecraft is running. Manual file edits require restarting Minecraft. Keep provider credits and report-photo controls visible.

Photo navigation does not supply real interiors or precise wall depth. Existing building and explosion effects use approximate Minecraft collision geometry. Coverage, account eligibility and usage charges depend on your provider.

`/earth back` opens Earth at your current Street View coordinates, with an upward camera transition around your existing character. The destination chunk loads before transfer. `/earth back original` restores the saved entry point instead. Failed terrain preparation keeps the current photo and return point. Nearby walking uses frame-interpolated movement and smooth bounded parallax (up to 0.45 m), avoiding the old tick-to-frame ground jitter.

Entering Street View waits for the photograph, lifts the camera around your character, then fades into the photo and restores your previous camera mode. Failed or cancelled entry restores the camera. Navigation arrows are gray; yellow marks the selected direction.
