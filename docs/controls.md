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

## Street View

1. Run `/earth streetview` while standing or flying in an Earth location. It searches within 500 metres of you.
2. In a normal Minecraft world, supply coordinates: `/earth streetview 44.428583, 26.101115`. Coordinate searches use a 50-metre radius.
3. Look at an arrow until it turns yellow, then tap **W** to travel. Arrow keys also travel between connected photos.
4. Place blocks, mine, use TNT, or spawn mobs as your gamemode allows.
5. Run `/earth back` to return to your previous world and position.

| Control | Action |
|---|---|
| V | Toggle a 3×3 block walking area; press again to recenter |
| WASD | Walk when nearby walking is enabled; use arrow keys to change photos |
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

Messages use a green **RW**, a dark gray separator and white text. Commands are pale yellow. `/earth goto` uses a green boss bar with three stages; these are not download percentages. Street View photo loading appears above the hotbar, including the first photo before you enter the photo world. Loading updates do not fill chat.

Street View uses fixed daylight and softly shaded blocks. Lighting in a 360-degree photograph cannot react to placed torches or match every shadow exactly. The photo dimension uses a vanilla Iris pipeline; Earth exploration keeps your chosen shaders.

## New-world starting place

New Real World worlds choose from [100 places](spawn-locations.md). Operator commands `/earth setlocation random` and `/earth setlocation <lat> <lon>` control future worlds only. Saved worlds never move.
