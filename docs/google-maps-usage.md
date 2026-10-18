# Google Maps usage for RealWorldMC 1.0.3

Verified against Google's official documentation on 18 October 2026. A daily quota limits requests; it is not a daily free allowance. Billing must be enabled. The old $200 monthly credit ended in February 2025.

| API/SKU | Global monthly free usage | First paid tier (USD/1,000 events) | Used by |
|---|---:|---:|---|
| Street View Tiles | 100,000 tile events | $2 | Native 1.21.1 panorama imagery |
| Photorealistic 3D Tiles | 1,000 root requests | $6 | Google 3D terrain provider |
| Dynamic Street View | 5,000 panorama events | $14 | Browser companion |
| 2D Map Tiles | 100,000 tiles | $0.60 | Not the OpenStreetMap overlay |

These are monthly global pay-as-you-go figures, not per-player grants. India pricing and negotiated/subscription arrangements differ. Check the billing account's actual pricing and other projects' usage. [Official pricing](https://developers.google.com/maps/billing-and-pricing/pricing).

Default project quotas: 15,000/day and 6,000/min combined 2D tiles, Street View tiles, thumbnails and pano-ID queries. Metadata/session/viewport requests have a separate combined 6,000/min limit and do not consume that daily quota. Google 3D allows 10,000 root requests/day; each root session permits up to three hours of renderer requests. Renderer tiles have a 12,000/min limit and no daily limit. Check your project's actual quotas; they may differ. [Usage and billing](https://developers.google.com/maps/documentation/tile/usage-and-billing).

## What a walking step costs

The service defaults to `maxPhotoWidth: 2048` in `streetview.config.json`; supported values are 1024, 2048 and 4096. For a 13,312 by 6,656 source with 512px tiles, the default assembles a 1,664 by 832 photograph using 8 tile requests. The previous 4096 setting uses 28. Actual dimensions determine the count. Metadata, retries, cancelled travel, other applications and browser use must be considered separately. A photograph is not one tile request.

Only one chosen destination is prepared after the player walks toward its link. It can be ahead, behind or beside the current stop. There is no speculative download of every surrounding panorama. A private 32 MiB memory cache reuses previously requested tiles only with an explicit unexpired max-age. It respects Age/Date, no-store and no-cache; stale tiles are fetched again and nothing is saved to disk. Google restricts prefetching and storage; permitted caching must follow response cache directives. [Map Tiles policies](https://developers.google.com/maps/documentation/tile/policies).

Set conservative project quotas in Google Cloud and monitor billing. Budget alerts do not stop spending. Daily quotas alone cannot guarantee staying within a monthly free allowance. Cesium ion has separate account terms and allowances; its terrain credential is never used for native Street View.

## In-game local counter (1.21.1)

The left corner HUD polls only the local services every five seconds. /earth hud off hides it; /earth usage off hides only counts. Cache shows fresh tile entries; Reused counts tile hits since the service started, not photos or monthly savings. A cache hit does not increment outbound request usage. Metadata and expired tiles may still require requests when revisiting. Counts are attempted outbound Google requests, including retries and prepared photos that are later cancelled. Metadata/session calls are shown in total requests but are not deducted from the tile allowance. 3D content requests are counted separately from root requests; only roots are compared with the 1,000 monthly allowance. Non-direct providers are labelled separately.

The service persists numeric totals in streetview-usage.json and terrain-usage.json in the instance service data folder. No API keys, panorama IDs or locations are stored in these counters. Monthly counters reset using the Pacific calendar month. They start when enabled: previous use, other installations, browser companion loads and other applications or projects sharing the billing account are excluded. Corrupt or unreadable counters display unavailable rather than a verified zero. Failed attempts can overcount billing, and an abrupt termination can lose the last 250 ms of counts.

The displayed remaining allowance and photo count are estimates, not a spending limit or Google account balance. The next-photo estimate uses the current panorama dimensions and configured resolution; another stop may use a different number of tiles. Check Google Cloud Billing reports and Maps usage for authoritative totals. The published global free monthly caps checked on 2026-10-18 are 100,000 Street View tiles and 1,000 Photorealistic 3D root requests; regional/contract pricing can differ.
