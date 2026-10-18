# Building surfaces and destruction — 1.0.3

## Changes

The old renderer assigned every triangle to its centre block and hid the whole triangle when that block was absent. One explosion could therefore remove a much larger piece of facade. It also treated mismatches between the collision terrain and the displayed detail level as damage. Remaining collision blocks had no visible cut faces.

The renderer now uses saved, confirmed block changes. It subtracts the edited cell volume from the mesh, interpolates the original texture coordinates along the new edges, and preserves the rest of each triangle. Both detailed and coarse fallback tiles receive the cuts. Solid blocks placed into empty space get a cut matching their actual cube bounds so the scanned facade cannot run through them.

Exposed edges use the remaining material's block texture: brick, plaster, stone, wood, asphalt, sand or earth. Only faces bordering actual excavations are drawn. Hollow scanned buildings stay hollow; this does not invent realistic rooms or reconstruct missing interior measurements. Collision, mining, TNT and vanilla save data remain authoritative.

The filler-height calculation also now accounts for the top of a full cube. It keeps newly generated dirt below the lowest adjacent scanned surface instead of allowing it to poke half a block above the photo.

## Performance and persistence

Damage is stored in `realworldmc_map_edits` in the world. Nearby batches are transferred atomically as the player changes horizontal or vertical sections. Client damage is cleared on disconnect. A spatial index limits each triangle's cut work to intersecting edited cells. Undamaged geometry uses the original upload path; tile buffers rebuild only when their damage volumes change.

## Existing saves

Saved blocks are not regenerated or replaced. The filler-height fix affects newly generated chunks. The earlier renderer did not save confirmed map damage, so it is not possible to distinguish all old excavations from missing/generated terrain reliably. Accurate damage tracking starts with this update; old holes cannot be promised automatic recovery. No automatic comparison against freshly downloaded terrain is used to guess historical edits.

The separate Street View hotfix preserves ambiguous old edit records in `unverified_legacy_edits` and stops drawing them as player construction. New confirmed Street View changes remain tracked. Existing saved builds and excavated blocks are not deleted.

## Validation

- Offline service tests, including a hollow-building footprint where filler previously rose above the photographed street.
- Geometry tests for exact hole area, unaffected neighbouring surface, UV interpolation, repeated cuts, geographic offsets and indexed damage lookup.
- Server TNT test verifies destroyed collision cells, original wall material and save/reload persistence.
- Integrated client test verifies damage packets reach actual client cut lists and restoring a map block removes the cut; Street View walking still passes.
- Actual OpenGL mesh test verifies a bounded opening, visible brick edge texels and the same geometry at a 100 km offset. Before/after synthetic-wall images are saved by the test.
- Production-mapped rendering fixture with Iris 1.8.14-beta.1 and Sodium 0.8.13 verifies the same render checks without development remapping.

These are renderer and gameplay regression checks, not a claim that historical edits or inaccurate source photogrammetry can be reconstructed.
