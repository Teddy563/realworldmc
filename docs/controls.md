# Commands and controls

[Back to README](../README.md)

These instructions are for **1.21.1**. On 26.2, Street View opens the browser companion; native photo gameplay is not included.

## Travel

1. Use `/earth goto <place name>` or coordinates:

   ```text
   /earth goto 44.427454, 26.087287
   ```

2. Wait for nearby scenery to load before flying far away.
3. Use `/earth where` to see your latitude and longitude. `/earth help` lists commands.

`/earth setlocation 44.427454 26.087287` changes the start for **new worlds** and requires command permission. Existing worlds keep their own origin.

## Glide and fly

`/earth fly` toggles equipment-free flight. Steve automatically glides while moving quickly and stands upright when hovering or moving straight up/down. `/earth glide` enables the same mode. Add `on` or `off` to either command; `/rw` works too. These modes require Creative singleplayer.

W/S move forward/back, A/D strafe, arrow up/down changes speed, and Space/Shift rises/descends. Release movement keys to slow to a hover. Fireworks provide an optional boost. See [the flight guide](flight.md) for speeds, camera behaviour and globe transitions.

## Street View

1. Run `/earth streetview` while standing or flying in an Earth location. It searches within 500 metres of you.
2. In a normal Minecraft world, supply coordinates: `/earth streetview 44.428583, 26.101115`. Coordinate searches use a 50-metre radius.
3. Walk with **WASD**. Beyond the 5×5 area, a connected photo in your movement direction begins loading; crossing the 6×6 boundary travels when ready. Arrow keys also travel between connected photos.
4. Place blocks, mine, use TNT, or spawn mobs as your gamemode allows.
5. Run `/earth back` to return to your previous world and position.

| Control | Action |
|---|---|
| V | Toggle walking (enabled by default); fixed mode recentres the viewpoint |
| WASD | Walk and approach connected stops; in fixed mode, look at a yellow arrow and tap W |
| F5 | Cycle camera views in exploration and Street View; framing follows automatically |
| F7 | Show full source credits |
| F8 | Open the provider's image-report link |
| `/earth streetview height 2.5` | Adjust photo camera height; accepts 1–4 metres |
| `/earth streetview browser` | Open the browser viewer |
| `/earth streetview legacy` | Visit the older flat sandbox |

Edits and entities stay at their geographic positions when you change photos. Normal item expiry, mob despawning, and chunk ticking still apply. Nearby visible drops can be collected within 3 blocks, subject to normal pickup rules.

Your rebound camera toggle also works. Entering or leaving Street View preserves your perspective. V controls nearby walking independently. Photo third person remains approximate because panoramas do not contain full scene depth. 

## Messages and loading

`/earth help` shows the main commands. Click a yellow command to fill chat, then enter its arguments. `/earth help more` shows extra options.

Messages use a green **RW**, a dark gray separator and white text. Commands are pale yellow. `/earth goto` uses a green boss bar with three stages; these are not download percentages. In the prepared 1.0.1 build, photo loading and short control hints appear in stacked lines above the bottom-left map credits. The larger white arrows turn yellow when selected. Loading updates do not fill chat.

`/earth hud` toggles this corner information. Add `on` or `off` to set it explicitly. The setting persists; normal chat, loading boss bars, navigation arrows and other mods' messages are unaffected. Opening chat hides the controls so they do not overlap it.

Street View uses fixed daylight and softly shaded blocks. Lighting in a 360-degree photograph cannot react to placed torches or match every shadow exactly. The photo dimension uses a vanilla Iris pipeline; Earth exploration keeps your chosen shaders.

## Experimental space filming

The local experimental 1.21.1 build also has [Earth-to-space filming controls](space-flight.md). They are not in the published v1.0.0 download.

In Creative singleplayer, fly above 2 km over your region's ground reference to enter automatically. Space/Shift change altitude; W/S cruise and A/D strafe. Descend below 1.6 km to resume local flight. `/earth space` starts ascent earlier; `/earth space return` goes back to the entry point. The threshold uses the region's saved ground height, not a live terrain measurement under the player.

The prepared 1.0.1 build also accepts `/rw moon`, `/rw mars` and `/rw earth` for orbital flyovers and return to Earth orbit. The `/earth` prefix works too. Above 100 km, aim at a destination marker and hold W for 2.5 seconds to travel. Release W or look away to cancel. See [space flight](space-flight.md) for imagery limits and [planetary data](planetary-data.md) for sources.

## New-world starting place

New Real World worlds choose from [100 places](spawn-locations.md). Operator commands `/earth setlocation random` and `/earth setlocation <lat> <lon>` control future worlds only. Saved worlds never move.
