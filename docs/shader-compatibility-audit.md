# Explore Earth shader audit

Status: investigation, not a verified cloud fix.

The current Earth mesh uses a position/texture shader after entities. It writes depth with blending disabled, but does not supply a shader pack with normal terrain material attributes. The installed IterationRP pack uses a copied solid-depth texture for its sky and translucent effects. Drawing the Earth mesh outside that capture can make clouds and water disagree with the visible surface.

Next checks:
1. Compare Iris off and IterationRP on at ground level, cloud height and above clouds.
2. Inspect the depth-copy timing before choosing an opaque-terrain render hook.
3. Confirm terrain depth reaches cloud and translucent passes without hiding Steve or the hand.
4. Check water, glass, shader reload and the return from orbit; repeat with BSL and Bliss.

Do not disable all depth testing or make the planet translucent to hide the problem. Orbit currently uses a separate pipeline; local Explore Earth needs its own validation.
