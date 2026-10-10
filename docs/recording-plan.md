# Setup footage to record

The site currently uses illustrations for steps 1–2, a labelled setup illustration for step 3, and a real gameplay screenshot for step 4. They are placeholders for these recordings, not screenshots of installation screens.

Record at 1920 × 1080, 30 fps, with readable UI. Keep the cursor visible, pause briefly after each click, and avoid fast zooms. Export MP4 (H.264). Save one clear PNG or WebP poster from each clip. Keep provider credits visible in gameplay footage.

| Step | Record these actions | Suggested length | Poster |
| --- | --- | --- | --- |
| 1. Install | Download the stable JAR. Show a Fabric 1.21.1 profile with Fabric API, put the JAR in its mods folder, then launch. Optionally show .mrpack import as the alternative. Cut out download waits. | 25–40 seconds | Imported RealWorldMC profile, ready to launch |
| 2a. Google | Show the project picker, Map Tiles API enabled, API restrictions, and where to review billing and quotas. Never display a real key. | 25–40 seconds | Map Tiles API enabled |
| 2b. Cesium | Show the Google Photorealistic 3D Tiles asset, token creation, selected asset access, assets:read, and optional geocode. Hide the token value. | 25–40 seconds | Asset available in your account |
| 3. Connect | Launch Minecraft, open RealWorldMC setup, choose the provider, use masked fields, Test connection, then Save & finish. Show the services-ready result; no manual launchers are needed. | 20–30 seconds | Successful test with credentials masked |
| 4. Play | Let the mod start its services, then create a Creative world with commands and **Real World** world type, travel to the palace, enter Street View, then return. | 30–45 seconds | Palace loaded in Minecraft |

For the updated 1.21.1 build, demonstrate `/earth setup`, `/earth goto 44.427454, 26.087287`, `/earth streetview`, and `/earth back`. The older release may require `/earth` and title-screen setup. Record the version you are actually distributing.

Before sharing a recording, check every frame for keys, tokens, payment details, email addresses, account IDs and personal filesystem paths. Use a clean recording profile. Do not publish a real key, even briefly before masking it.

## Files to deliver

Put footage and posters in `public/media/setup/`:

- `01-install.mp4` and `01-install.webp`
- `02-google.mp4` and `02-google.webp`
- `02-cesium.mp4` and `02-cesium.webp`
- `03-connect.mp4` and `03-connect.webp`
- `04-play.mp4` and `04-play.webp`
- Matching `.vtt` caption files if there is narration.

Replace the matching illustration in `src/components/site/installation.tsx` with a video using controls, playsInline, preload="none", and its poster. Keep the written steps visible. Do not autoplay setup instructions. Place the Google and Cesium clips inside their respective provider sections so people only load the route they use.

The step links scroll to each row. Desktop artwork stays beside long instructions; mobile artwork returns to normal document flow. GSAP reveals each row once, with reduced-motion support. No scroll locking or automatic slide changes.
