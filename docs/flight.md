# Fly without an elytra

Use a **Creative, singleplayer Real World world on Minecraft 1.21.1**. These controls are not included in the experimental Minecraft builds.

1. Run `/earth glide` for fast flight with a horizontal, elytra-style body pose. You do not need to wear an elytra.
2. Or run `/earth fly` to move while Steve stays upright, including hovering and moving straight up or down.
3. Run the same command again to turn it off. Add `on` or `off` to set it explicitly. `/rw glide` and `/rw fly` work too. Only one mode can be active.

| Control | Action |
|---|---|
| W / S | Accelerate forward / backward; opposite input brakes before reversing |
| A / D | Strafe left / right |
| Arrow up / down | Increase / decrease the selected speed |
| Space / Shift | Rise / descend |
| Mouse | Steer; glide follows your pitch, fly keeps horizontal travel level |
| Release movement keys | Slow smoothly to a hover |
| Use a firework rocket | Temporary boost; from a hover it launches you forward |
| F5 | Normal camera toggle; your rebound camera key also works |

The starting and maximum local cruise target is **66 metres per second**, roughly twice vanilla rocket-assisted level elytra flight. Vanilla elytra speed varies with pitch. Arrow keys reduce that target as low as 4 m/s, or restore it. A rocket temporarily boosts the selected speed by 50%, up to 99 m/s. Acceleration is gradual and diagonal movement does not exceed the selected limit.

Rockets use their normal item, flight duration, sounds and explosion behaviour. They are optional. No armour is equipped or removed, and turning these modes off restores ordinary Creative controls without making you fall. Block collision still applies. F5, camera-distance mods and optical zoom keep their normal controls.

The speed arrows share Street View's forward/back arrow bindings in Minecraft's Controls menu. Entering Street View, boarding a vehicle, leaving Creative or disconnecting ends this flight mode, so those controls do not take over photo navigation or vehicles.

## Flying around a globe

The existing [automatic space transition](space-flight.md) still works. Above its entry height, the geographic view moves while Steve stays at a stable Minecraft position. The same glide/fly pose, speed percentage and rocket boost apply. Orbital speed grows with altitude rather than staying at the local 66 m/s limit.

Use Shift to descend toward Earth, or `/earth space return` to return to the launch point. A glide can also descend by pointing down and moving forward. On return, your selected glide/fly mode resumes local movement. `/earth glide off` or `/earth fly off` turns off that mode; it does not teleport you out of orbit.

The corner HUD shows the mode, speed percentage and controls. `/earth actionbar` hides or restores it. Maps still depend on connection speed, provider coverage and usage limits.
