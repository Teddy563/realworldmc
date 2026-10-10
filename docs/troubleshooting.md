# Troubleshooting and updates

[Back to README](../README.md)

## Map does not load

1. Check the world type is **Real World**.
2. Open `/earth setup`, save your tested keys, and check [service health](http://127.0.0.1:8765/health).
3. If unreachable, check the setup error and `realworldmc-service/service.log`. Reimport the complete Windows x64 pack if its bundled runtime is missing.
4. For 401/403 errors, check your token/key, enabled APIs, asset access, restrictions, and quota.
5. Try coordinates if place-name search fails. Try another location if coverage is missing.

If ports 8765 or 8766 are in use, close the other RealWorldMC game or the old manually started service. The mod never terminates another installation. Retry local services from setup.

## Street View does not load

1. Save your optional photo key through `/earth setup`; both services start automatically.
2. Check the native `tilesKey` and Map Tiles API access. `browserKey` is separate and only needed by the browser companion.
3. Try a street with known coverage. `/earth streetview` searches near your current Earth position.
4. Use `/earth back` if you need to leave photo mode.

## Camera or graphics problems

- Use **F5** to change perspective, or Fn+F5 on keyboards with media-key mode. Check Controls for duplicate bindings. The same camera toggle works in Street View.
- If photo scale feels wrong, try `/earth streetview height 1.62` or `2.5`. This changes viewpoint height, not block size.
- If Steve disappears, check camera mods and Entity Culling's player exclusions. Check in both first and third person.
- If shaders cause problems, disable them and test the Core pack. Street View deliberately uses its own shader-compatible pipeline.
- Let coarse map tiles load before flying quickly. Lower Minecraft render distance or disable expensive visual mods if performance drops.

## Update safely

1. Close Minecraft; its managed services stop with it.
2. Back up your profile, saves and private configs under `realworldmc-service`.
3. Import the new `.mrpack` into a new profile. Complete setup there, then test a new world.
4. Copy a backed-up save only after checking the Minecraft version. Never open your only 1.21.1 save in 26.2.
5. Keep one RealWorldMC JAR per profile. Do not distribute a private profile export containing credentials.

Existing settings import into `config/realworldmc.json`. Existing terrain and world origins are preserved. To roll back, restore both the previous profile and its pre-update save backup.

## Report a problem

Include your Minecraft version, mod/shader versions, what you did, and the crash report or `logs/latest.log`. Service output is in `realworldmc-service/service.log` when launched by the mod. Remove keys and private details before sharing; never upload private service configs.
