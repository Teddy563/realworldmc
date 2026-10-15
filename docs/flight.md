# Fly without an elytra

Use a **Creative, singleplayer Real World world on Minecraft 1.21.1**. These controls are not included in the experimental Minecraft builds.

1. Run `/earth fly` to enable automatic flight. `/earth glide` does the same thing. You do not need an elytra.
2. Hold W to accelerate. Steve gradually adopts the glide pose as he gains speed. S brakes, then reverses if held. Release movement keys to slow into an upright hover. Straight up/down movement stays upright.
3. Run either command again to turn flight off. Add `on` or `off` to set it explicitly. `/rw fly` and `/rw glide` work too.

| Control | Action |
|---|---|
| W / S | Accelerate forward / backward; opposite input brakes before reversing |
| A / D | Strafe left / right |
| Arrow up / down | Increase / decrease the selected speed |
| Space / Shift | Rise / descend |
| Mouse | Steer forward/back travel with your view; Space/Shift add vertical movement |
| Release movement keys | Slow smoothly to a hover |
| Use a firework rocket | Temporary boost; from a hover it launches you forward |
| F5 | Normal camera toggle; your rebound camera key also works |

The starting and maximum local cruise target is **66 metres per second**, roughly twice vanilla rocket-assisted level elytra flight. Vanilla elytra speed varies with pitch. Arrow keys reduce that target as low as 4 m/s, or restore it. A rocket temporarily boosts the selected speed by 50%, up to 99 m/s. Acceleration is gradual and diagonal movement does not exceed the selected limit. Axiom's vanilla flight-speed setting and movement-speed attributes (including Speed and Slowness effects) also scale flight, up to an 8x combined multiplier before rockets. While enabled, RealWorldMC controls acceleration and steering; turn it off to return to Axiom's normal flight behavior.

Rockets use their normal item, flight duration, sounds and explosion behaviour. They are optional. No armour is equipped or removed, and turning flight off restores ordinary Creative controls without making you fall. Block collision still applies. F5, camera-distance mods and optical zoom keep their normal controls.

The speed arrows share Street View's forward/back arrow bindings in Minecraft's Controls menu. Entering Street View, boarding a vehicle, leaving Creative or disconnecting ends this flight mode, so those controls do not take over photo navigation or vehicles.

## Flying around a globe

The existing [automatic space transition](space-flight.md) still works. Above its entry height, the geographic view moves while Steve stays at a stable Minecraft position. The same automatic glide/hover pose, speed percentage and rocket boost apply. Orbital speed grows with altitude rather than staying at the local 66 m/s limit.

Use Shift to descend toward Earth, or `/earth space return` to return to the launch point. A glide can also descend by pointing down and moving forward. On return, automatic flight resumes local movement. `/earth glide off` or `/earth fly off` turns off automatic flight; it does not teleport you out of orbit.

The corner HUD shows the mode, speed percentage and controls. `/earth actionbar` hides or restores it. Maps still depend on connection speed, provider coverage and usage limits.
