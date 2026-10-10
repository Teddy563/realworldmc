# Map accounts, keys and costs

[Setup](setup.md) · [Back to README](../README.md)

The mod is free to download. The companies supplying the maps may charge you. A key is a password that lets the mod use **your** map account. Never share it in chat, screenshots, bug reports or release files.

## 1. Choose how to load 3D terrain

**Cesium ion:** create an account at [Cesium ion](https://ion.cesium.com/), find Google Photorealistic 3D Tiles, and confirm that your account can use that asset. Create a token with `assets:read`; add `geocode` if you want place-name searches. The default asset is `2275207`. If your account cannot access it, a token alone will not fix that.

**Google direct:** create a Google Cloud project, connect a billing account, enable Map Tiles API and create an API key. Restrict it to the required APIs. Enable Geocoding API only if you use place-name searches. Native requests come from your computer's local service; a website-referrer restriction is not appropriate for those requests.

**In Romania / the EEA:** Google bases eligibility on account and project conditions, including the billing address. Check [the current EEA rules](https://developers.google.com/maps/comms/eea/map-tiles). Cesium may provide an eligible account route to 3D tiles; it is not a way to bypass provider restrictions. There is no special “EU key.”

## 2. Add Street View, if you want it

1. Create a Google Cloud project and review its billing settings.
2. Enable **Map Tiles API** for the 1.21.1 in-game photos.
3. Create a restricted API key for those requests. You can use a separate key from terrain.
4. Enter it in the optional **Street View key** field in Minecraft setup.

Cesium's terrain token does not unlock Google Street View. The separate browser viewer uses **Maps JavaScript API**, with a browser key restricted to its localhost origin. A native photo access test does not test the browser viewer. See [Street View tiles](https://developers.google.com/maps/documentation/tile/streetview).

## 3. Add and test keys in Minecraft 1.21.1

1. Download the 1.21.1 Core `.mrpack` and import it into Modrinth or Prism on Windows x64.
2. Launch Minecraft. **RealWorldMC setup** opens before you create a world. You can reopen it from the title screen.
3. The bundled service folder is detected automatically; no folder selection is needed.
4. Read the cost message, choose the provider, paste its key/token, and optionally add the Street View key. The fields are masked.
5. Press **Test connection**. This contacts your selected map provider and, if supplied, asks Google for a Street View session. These requests may count toward usage. No panorama downloads or terrain generation run in this test.
6. A successful test means those requests worked now. It does not guarantee every location, sufficient remaining allowance, browser access, or zero charges.
7. Press **Save & finish**. Use `/earth setup` to reopen this screen. The mod starts or reloads its local services automatically. If you changed keys inside a world, reopen that world; Minecraft itself does not need restarting.

The files are `realworldmc-service/config.json` and `realworldmc-service/streetview.config.json`. They contain readable secrets on your own computer, not encrypted storage. Do not share them. Cancelling does not save the entered keys. Existing settings are loaded locally and preserved unless you change them. Environment-variable credentials override service files; remove old overrides if the service uses a different account from the test.

For 26.2, use the manual setup instructions. That experimental build does not include this native first-run screen or native photo gameplay yet.

## 4. Understand the bill before playing

Imagine each API has a bucket of free requests every month. Loading a view takes requests from that bucket. When it is empty, extra requests may be billed to the payment method linked to your account. This is not necessarily a prepaid balance that stops at zero.

- A Minecraft frame is not one billable request. A view may need many map tiles.
- Different APIs have different free allowances and counting rules. Street View tiles, 3D tiles and the browser viewer are separate products.
- Higher photo resolution, visiting more places, or repeated loading can increase requests.
- Cesium has its own plan, limits and billing. Do not apply Google's direct API allowance to a Cesium account.
- **Alerts-only budgets warn you; they do not automatically stop charges.** Check supported quotas and any available spend-cap feature for your account. Rate quotas are not a guaranteed monthly money cap, and usage reporting can be delayed.
- This mod does not know your remaining free allowance and cannot promise a maximum bill.

Example only: if a service includes 100,000 events and then costs $2 per 1,000, 105,000 events means 5,000 paid events, or $10 before tax and other charges. This is an explanation of arithmetic, not a quote for a play session.

Before testing: read [Google's current pricing](https://developers.google.com/maps/billing-and-pricing/pricing), [Cesium's plans](https://cesium.com/platform/cesium-ion/pricing/), and [Google budget guidance](https://docs.cloud.google.com/billing/docs/how-to/budgets). Check each API in your actual account. Reviewed October 10, 2026; provider terms can change.

## If the test fails

| Message | Try this |
|---|---|
| HTTP 401 / 403 | Check the key, API enablement, key restrictions, billing, asset access and regional eligibility. |
| HTTP 429 | Check quota and usage in the provider dashboard. Do not repeatedly retry. |
| Cannot connect | Check internet access and try later. |
| Cannot read folder/config | Reimport the complete pack if files are missing; repair malformed JSON using the example files. Back up your existing config first. |
| Passed but no photo | That location may have no Street View coverage; check the photo service is running. |

The **Setup & costs guide** button opens this page. Do not send keys when asking for help.
