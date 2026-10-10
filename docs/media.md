# Screenshots and video checklist

Use actual Minecraft 1.21.1 footage. Keep provider credits in the game capture. Hide private keys, account details, chat and player names you do not have permission to show. Generated brand art must not be presented as game footage.

## Ready to use

- [Static gameplay grid](assets/gameplay-grid.jpg): one 1920×1080 JPG showing New York, Grand Canyon, Paris and Golden Gate Bridge. Golden Gate replaces the former bottom-right Barcelona panel. No GIF or alternate grids are published.
- The collage uses real footage in a 2×2 grid. Inventory, FPS text and black borders are cropped away. Provider credits remain inside each panel as softer text, without dark boxes or a repeated RealWorldMC label.
- [Flight preview](assets/flight-preview.mp4): about 14 seconds, 1280×720, 30 fps, silent H.264 with fast-start playback. Four scenes with short crossfades. The captures use optional shaders; this is development footage, not a promise of identical graphics on every installation.
- [Green explorer](assets/explorer-green.webp) and [blue download helper](assets/explorer-blue.webp): existing RealWorldMC mascot artwork, sized for the README.

The README links to the MP4 rather than depending on an autoplaying video embed. Use the static JPG as the repository's main preview and the MP4 as a release attachment or demo link. Keep full-resolution recordings outside Git. The committed media is small enough to review alongside source changes.

Sources from Teddy563's recordings:

| Scene | Original file | Still | Video extract |
|---|---|---|---|
| New York | `episode_3_fhd.mp4` | 00:04 | 00:04–00:07 |
| Grand Canyon | `2026-10-10 15-13-20.mp4` | 01:01 | 01:00–01:03 |
| Paris | `2026-10-10 14-30-52.mp4` | 01:32 | 01:31–01:34 |
| Barcelona | `2026-10-10 16-02-34.mp4` | 01:12 | 01:11–01:14 |
| Giza | `2026-10-10 14-34-57.mp4` | 00:40 | Still only |
| Golden Gate Bridge | `2026-10-10 15-05-12.mp4` | 01:16 | Still only |
| New York alternate | `episode_3_fhd.mp4` | 00:15 | Still only |
| Paris alternate | `2026-10-10 14-33-08.mp4` | 00:38 | Still only |

Each preview scene plays at 75% of the original speed; transitions last 0.6 seconds. Provider attribution is retained. The source masters are unchanged.

Still needed: the new empty setup form, a successful masked connection test, and matching Street View before/after shots. Those must be captured in the updated mod; the flight footage cannot demonstrate setup or block damage.

## Capture these first

| File | What to show | Where it goes |
|---|---|---|
| `hero-locations.mp4` | A 12-20 second silent loop: Bucharest, a city flight, then a mountain approach. Avoid rapid cuts. | Website background |
| `hero-poster.webp` | A sharp frame from that loop, with clear space for the page title. | Video fallback |
| `streetview.webp` | One working photo stop with Minecraft hand visible. | README / release gallery |
| `build-before.webp`, `build-after.webp` | The same Street View camera before and after placing blocks or making a crater. | Explain simulated ground |
| `setup.webp` | The first-run setup screen with empty key fields. | Installation guide |
| `flight.webp` | A wide shot of loaded terrain, not a loading failure. | Release gallery |
| `demo.mp4`, `demo.vtt` | A 30-60 second walkthrough of install, flying and Street View, with English captions. | Optional full video |

Capture landscape at 1920×1080 or higher. Export the hero as MP4/H.264 without audio; aim for a short download and test on mobile. Keep the original master. Export stills as WebP with readable credits. A 1200×630 social image should use the title card and clearly labelled branding, not unsupported gameplay claims.

## Put files on the website

1. Put approved media in `D:\RealWorldMC-SITE\public\media\`.
2. Replace the matching landscape/portrait video and first-frame poster files used by `src/components/site/hero-video.tsx`. Keep provider credits.
3. Use `site.video` for the optional captioned walkthrough.
4. Check the page on desktop and a phone, with reduced motion enabled.

## Brand references

Use the original server explorers: square faces, tiny oval eyes, tan hats with coloured bands and white location-pin badges. The source collection is `site/public/brand/crew` in the server project. `cast-master.png` is the character reference sheet. `prompts.json`, `marker-prompts.json` and `website-prompts-2026-09-12.json` preserve the source briefs.

Keep artwork separate from the code license until ownership and asset permissions are confirmed. Do not remove map-provider credits to make a shot cleaner.
