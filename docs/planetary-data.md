# Planetary imagery

The experimental 1.21.1 orbital views use real global maps, not generated illustrations. The code remains Apache 2.0. These image assets have their own source credits and usage terms.

## Moon

- Source: [NASA Scientific Visualization Studio CGI Moon Kit](https://svs.gsfc.nasa.gov/4720/), 2019 color map, `lroc_color_poles_4k.tif`.
- Credit: NASA's Scientific Visualization Studio; LROC WAC data from NASA/GSFC/Arizona State University. Visualization by Ernie Wright.
- Original resolution: 4096×2048, centered on 0° longitude. Converted losslessly from RGB TIFF to PNG for Minecraft.
- Original URL: `https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/lroc_color_poles_4k.tif`.
- Acquisition note: NASA's SVS host timed out during preparation. The TIFF was retrieved from the [Moonquake Map copy](https://github.com/JugalKothari/Moonquake_Map/blob/main/lroc_color_poles_4k.tif), with the original filename and expected dimensions. No code from that project is included. The original NASA endpoint could not be reached for byte-for-byte comparison.
- Retrieved TIFF SHA-256: `918649a7f8ed2f1329b2cd95bb0d25483befdcb60ae1a66db681a637cc21344f`.

The source combines lunar imagery, with polar fill as described by NASA. This release does not use the separate LOLA elevation files. Its globe is spherical.

## Mars

- Source: [NASA/JPL Solar System Simulator texture maps](https://maps.jpl.nasa.gov/tmaps/mars.html), Viking global map, `mar0kuu2.tif`.
- Credit: NASA/JPL-Caltech/USGS. Viking imagery processed by USGS.
- Original resolution: 1440×720. Converted losslessly from RGB TIFF to PNG for Minecraft.
- Original URL: `https://maps.jpl.nasa.gov/tmaps/pix/mar0kuu2.tif`.
- Retrieved TIFF SHA-256: `1dcad8bb108f4b6ea25ec56ff03e76fa7b3808636efa583b237e081b1a6d8e64`.
- Usage: [JPL image-use policy](https://www.jpl.nasa.gov/jpl-image-use-policy/). Keep the credit visible and do not imply endorsement.

The Viking map is a global visual reference. It is not live imagery, a high-resolution landing map, or a measured 3D terrain model. The artificial lighting is for viewing the globe and does not reproduce the Sun's current position.

## Included assets

`mod/src/client/resources/assets/realworldmc/textures/space/moon.png` and `mars.png` are bundled in the 1.21.1 JAR. Runtime downloads and additional API keys are not required. Credits appear in the lower-left corner while visiting each body.

RealWorldMC is not affiliated with or endorsed by NASA, JPL, Caltech, USGS or Arizona State University.

## Surface data options, not integrated yet

Cesium offers [Moon](https://cesium.com/platform/cesium-ion/content/cesium-moon/) and [Mars](https://cesium.com/platform/cesium-ion/content/cesium-mars/) as streamed 3D Tiles, combining measured terrain and imagery. Their documented ion asset IDs are 2684829 and 3644333. Moon terrain is roughly 59 metres per sample at mid-latitudes and reaches 5 metres near the poles. Mars is roughly 200 metres globally, with 20-metre terrain at Jezero and 1-metre terrain at Gale Crater. Imagery resolution differs from terrain resolution.

These would be the closest match to Earth-style exploration. They require Cesium account/token access and are subject to that account's streaming allowance and applicable content terms. The existing service's Cesium adapter only resolves Google Earth access. Supporting planetary assets also requires their endpoint authentication, coordinate systems, tile selection, credits and a collision/landing path. Changing the Earth asset ID is not sufficient.

[NASA Trek](https://trek.nasa.gov/tiles/apidoc/) also exposes tiled map imagery. Its WMTS image layers are not interchangeable with a 3D collision mesh. NASA PDS elevation files are another possible source, but have not been bundled or used for playable terrain in this candidate.
