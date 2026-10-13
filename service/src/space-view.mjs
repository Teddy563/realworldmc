// Orbital views use the same provider tiles and WGS84 frame as the street below.
// Keep this opt-in so existing clients retain their normal request budget.
import { ecefToGeodetic } from './geo.mjs';

export function spaceAltitude(frame, camera) {
  // The original tangent-frame Y becomes negative on the other side of Earth.
  return ecefToGeodetic(...frame.mcToEcef(...camera))[2] - frame.h0;
}

export function spaceRenderOptions(render, altitude) {
  const h = Math.min(20_000_000, Math.max(0, Number.isFinite(altitude) ? altitude : 0));
  if (h < 1500) return render;
  const horizon = Math.sqrt(h * (2 * 6_378_137 + h));
  return {
    ...render,
    far: Math.max(render.far, Math.min(50_000_000, horizon * 1.15 + 10_000)),
    maxSse: Math.max(render.maxSse, 24),
    falloff: Math.max(render.falloff, h * 0.15),
    maxTiles: Math.min(render.maxTiles, 256),
  };
}
