// Turns photogrammetry triangles into Minecraft block columns.
//
// Voxel (x, y, z) is "touched" by the mesh if a triangle overlaps the box
// [x, x+1] x [y+0.5, y+1.5] x [z, z+1]. The half-block shift makes the top of a
// ground block land within +-0.5 of the visible surface instead of always above it.

import { sampleColor } from './tiles.mjs';

export const M = {
  AIR: 0,
  // Invisible "map" blocks: the Google mesh is drawn where these are.
  GRASS: 1, LEAVES: 2, DIRT: 3, STONE: 4, ASPHALT: 5, PLASTER: 6, BRICK: 7, WOOD: 8, SAND: 9,
  HIDDEN_FILL: 10, // invisible filler (where visible dirt would poke out next to the photo)
  // Ordinary filler blocks below the surface.
  FILL_DIRT: 20, FILL_STONE: 21, BEDROCK: 22,
  // Rivers and lakes (from OpenStreetMap).
  WATER: 23, GRAVEL: 24,
  // Visible shoreline blocks (where the photo cannot cover the side of the bank).
  GRASS_BLOCK: 25, SAND_BLOCK: 26,
};

// ---------------------------------------------------------------- triangle/box overlap
// Tomas Akenine-Möller, "Fast 3D Triangle-Box Overlap Testing".

function axisTest(a, b, fa, fb, v0a, v0b, v1a, v1b, h0, h1) {
  const p0 = a * v0a - b * v0b, p1 = a * v1a - b * v1b;
  const mn = Math.min(p0, p1), mx = Math.max(p0, p1);
  const rad = fa * h0 + fb * h1;
  return !(mn > rad || mx < -rad);
}

export function triBoxOverlap(c, h, t) {
  // c: box center [3], h: half size [3], t: 9 coords
  const v0x = t[0] - c[0], v0y = t[1] - c[1], v0z = t[2] - c[2];
  const v1x = t[3] - c[0], v1y = t[4] - c[1], v1z = t[5] - c[2];
  const v2x = t[6] - c[0], v2y = t[7] - c[1], v2z = t[8] - c[2];
  const e0x = v1x - v0x, e0y = v1y - v0y, e0z = v1z - v0z;
  const e1x = v2x - v1x, e1y = v2y - v1y, e1z = v2z - v1z;
  const e2x = v0x - v2x, e2y = v0y - v2y, e2z = v0z - v2z;
  const hx = h[0], hy = h[1], hz = h[2];

  // 9 cross-product axes
  let fex = Math.abs(e0x), fey = Math.abs(e0y), fez = Math.abs(e0z);
  if (!axisTest(e0z, e0y, fez, fey, v0y, v0z, v2y, v2z, hy, hz)) return false; // X01
  if (!axisTest(-e0z, -e0x, fez, fex, v0x, v0z, v2x, v2z, hx, hz)) return false; // Y02
  if (!axisTest(e0y, e0x, fey, fex, v1x, v1y, v2x, v2y, hx, hy)) return false; // Z12
  fex = Math.abs(e1x); fey = Math.abs(e1y); fez = Math.abs(e1z);
  if (!axisTest(e1z, e1y, fez, fey, v0y, v0z, v2y, v2z, hy, hz)) return false;
  if (!axisTest(-e1z, -e1x, fez, fex, v0x, v0z, v2x, v2z, hx, hz)) return false;
  if (!axisTest(e1y, e1x, fey, fex, v0x, v0y, v1x, v1y, hx, hy)) return false;
  fex = Math.abs(e2x); fey = Math.abs(e2y); fez = Math.abs(e2z);
  if (!axisTest(e2z, e2y, fez, fey, v0y, v0z, v1y, v1z, hy, hz)) return false;
  if (!axisTest(-e2z, -e2x, fez, fex, v0x, v0z, v1x, v1z, hx, hz)) return false;
  if (!axisTest(e2y, e2x, fey, fex, v1x, v1y, v2x, v2y, hx, hy)) return false;

  // box face axes
  if (Math.min(v0x, v1x, v2x) > hx || Math.max(v0x, v1x, v2x) < -hx) return false;
  if (Math.min(v0y, v1y, v2y) > hy || Math.max(v0y, v1y, v2y) < -hy) return false;
  if (Math.min(v0z, v1z, v2z) > hz || Math.max(v0z, v1z, v2z) < -hz) return false;

  // triangle plane
  const nx = e0y * e1z - e0z * e1y, ny = e0z * e1x - e0x * e1z, nz = e0x * e1y - e0y * e1x;
  const d = -(nx * v0x + ny * v0y + nz * v0z);
  const r = hx * Math.abs(nx) + hy * Math.abs(ny) + hz * Math.abs(nz);
  return Math.abs(d) <= r;
}

// ---------------------------------------------------------------- color -> material

function rgbToHsv(r, g, b) {
  r /= 255; g /= 255; b /= 255;
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
  let h = 0;
  if (d > 1e-6) {
    if (mx === r) h = ((g - b) / d) % 6;
    else if (mx === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h *= 60; if (h < 0) h += 360;
  }
  return [h, mx === 0 ? 0 : d / mx, mx];
}

export function classify(r, g, b, heightAboveGround) {
  const [h, s, v] = rgbToHsv(r, g, b);
  if (h >= 65 && h <= 170 && s > 0.12 && v > 0.08) return heightAboveGround > 2 ? M.LEAVES : M.GRASS;
  if (s < 0.13) {
    if (v < 0.27) return M.ASPHALT;
    if (v > 0.68) return M.PLASTER;
    return M.STONE;
  }
  if ((h < 22 || h > 340) && s > 0.3 && v > 0.25) return M.BRICK;
  if (h >= 22 && h < 55) {
    if (v > 0.6 && s < 0.45) return M.SAND;
    return heightAboveGround > 1 ? M.WOOD : M.DIRT;
  }
  return M.STONE;
}

/** Interpolate a missing column only when measured ground brackets it. */
export function interpolateGap(src, buildings, w, x, z, radius, none) {
  const estimates = [];
  for (const [dx, dz] of [[1, 0], [0, 1]]) {
    const ends = [];
    for (const sign of [-1, 1]) {
      let found = null;
      for (let d = 1; d <= radius; d++) {
        const xx = x + sign * dx * d, zz = z + sign * dz * d;
        if (xx < 0 || zz < 0 || xx >= w || zz >= w) break;
        const i = zz * w + xx;
        if (src[i] !== none && !buildings[i]) { found = { value: src[i], distance: d }; break; }
      }
      ends.push(found);
    }
    const [a, b] = ends;
    // Do not bridge a cliff or interpret an unbounded edge as measured ground.
    if (a && b && Math.abs(a.value - b.value) <= a.distance + b.distance) {
      estimates.push((a.value * b.distance + b.value * a.distance) / (a.distance + b.distance));
    }
  }
  if (!estimates.length || Math.max(...estimates) - Math.min(...estimates) > 2) return null;
  return estimates.reduce((a, b) => a + b, 0) / estimates.length;
}

// ---------------------------------------------------------------- voxelization

/**
 * Voxelizes all triangles of `meshes` inside the area x in [x0, x0+w), z in [z0, z0+w),
 * y in [minY, minY+height) and fills the ground. Returns a Uint8Array indexed
 * ((y - minY) * w + (z - z0)) * w + (x - x0).
 */
export function voxelizeArea(frame, meshes, x0, z0, w, minY, height, opts = {}) {
  const groundRadius = opts.groundRadius ?? 12;
  const buildingTolerance = opts.buildingTolerance ?? 6;
  const plane = w * w;
  const vox = new Uint8Array(plane * height);
  const walls = new Uint8Array(plane * height); // 1 where a steep (wall-like) photo surface passes
  const colors = new Map(); // voxel index -> [r, g, b, n]
  const c = [0, 0, 0], hs = [0.5, 0.5, 0.5], tri = new Array(9), col = [0, 0, 0];
  const maxY = minY + height;
  const x1 = x0 + w, z1 = z0 + w;

  for (const mesh of meshes) {
    for (const prim of mesh.primitives) {
      const mc = mcPositions(frame, prim);
      const idx = prim.indices;
      for (let t = 0; t < idx.length; t += 3) {
        const a = idx[t] * 3, b = idx[t + 1] * 3, d = idx[t + 2] * 3;
        const ax = mc[a], ay = mc[a + 1], az = mc[a + 2];
        const bx = mc[b], by = mc[b + 1], bz = mc[b + 2];
        const cx = mc[d], cy = mc[d + 1], cz = mc[d + 2];
        const minx = Math.min(ax, bx, cx), maxx = Math.max(ax, bx, cx);
        const minz = Math.min(az, bz, cz), maxz = Math.max(az, bz, cz);
        if (maxx < x0 || minx >= x1 || maxz < z0 || minz >= z1) continue;
        const miny = Math.min(ay, by, cy) - 0.5, maxy = Math.max(ay, by, cy) - 0.5;
        if (maxy < minY || miny >= maxY) continue;

        const ia = idx[t] * 2, ib = idx[t + 1] * 2, ic = idx[t + 2] * 2;
        sampleColor(prim, (prim.uvs[ia] + prim.uvs[ib] + prim.uvs[ic]) / 3, (prim.uvs[ia + 1] + prim.uvs[ib + 1] + prim.uvs[ic + 1]) / 3, col);

        const vx0 = Math.max(x0, Math.floor(minx)), vx1 = Math.min(x1 - 1, Math.floor(maxx));
        const vy0 = Math.max(minY, Math.floor(miny)), vy1 = Math.min(maxY - 1, Math.floor(maxy));
        const vz0 = Math.max(z0, Math.floor(minz)), vz1 = Math.min(z1 - 1, Math.floor(maxz));
        const single = vx0 === vx1 && vy0 === vy1 && vz0 === vz1;
        const ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
        const nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        const normalLength = Math.hypot(nx, ny, nz);
        if (!Number.isFinite(normalLength) || normalLength < 1e-10) continue;
        const uu = ux * ux + uy * uy + uz * uz;
        const uv = ux * vx + uy * vy + uz * vz;
        const vv = vx * vx + vy * vy + vz * vz;
        const denom = normalLength * normalLength;
        const steep = Math.abs(ny) < 0.3 * normalLength; // steeper than ~72°: a wall
        tri[0] = ax; tri[1] = ay; tri[2] = az; tri[3] = bx; tri[4] = by; tri[5] = bz; tri[6] = cx; tri[7] = cy; tri[8] = cz;
        for (let y = vy0; y <= vy1; y++) {
          for (let z = vz0; z <= vz1; z++) {
            for (let x = vx0; x <= vx1; x++) {
              if (!single) {
                c[0] = x + 0.5; c[1] = y + 1.0; c[2] = z + 0.5;
                if (!triBoxOverlap(c, hs, tri)) continue;
              }
              if (prim.texture) {
                // Project this voxel onto the triangle and interpolate its UVs.
                const px = x + 0.5 - ax, py = y + 1 - ay, pz = z + 0.5 - az;
                const pu = px * ux + py * uy + pz * uz, pv = px * vx + py * vy + pz * vz;
                let wb = (vv * pu - uv * pv) / denom, wc = (uu * pv - uv * pu) / denom;
                let wa = Math.max(0, 1 - wb - wc); wb = Math.max(0, wb); wc = Math.max(0, wc);
                const total = wa + wb + wc;
                wa /= total; wb /= total; wc /= total;
                sampleColor(prim, wa * prim.uvs[ia] + wb * prim.uvs[ib] + wc * prim.uvs[ic],
                  wa * prim.uvs[ia + 1] + wb * prim.uvs[ib + 1] + wc * prim.uvs[ic + 1], col);
              }
              const i = ((y - minY) * w + (z - z0)) * w + (x - x0);
              vox[i] = 1;
              if (steep) walls[i] = 1;
              let acc = colors.get(i);
              if (!acc) { acc = [0, 0, 0, 0]; colors.set(i, acc); }
              acc[0] += col[0]; acc[1] += col[1]; acc[2] += col[2]; acc[3]++;
            }
          }
        }
      }
    }
  }

  removeIsolated(vox, colors, w, height);

  // --- ground heightfield
  const NONE = -1e9;
  const g0 = new Float64Array(plane).fill(NONE);   // lowest surface voxel per column
  const gtop = new Float64Array(plane).fill(NONE); // highest surface voxel per column
  for (let y = height - 1; y >= 0; y--) {
    const base = y * plane;
    for (let i = 0; i < plane; i++) if (vox[base + i]) { g0[i] = y + minY; if (gtop[i] === NONE) gtop[i] = y + minY; }
  }
  const gmin = windowMin(g0, w, groundRadius, NONE);
  const isBuilding = new Uint8Array(plane);
  for (let i = 0; i < plane; i++) if (g0[i] !== NONE && g0[i] - gmin[i] > buildingTolerance) isBuilding[i] = 1;
  const ground = new Float64Array(plane);
  const fallbackY = opts.fallbackGround ?? 63;
  for (let z = 0; z < w; z++) {
    for (let x = 0; x < w; x++) {
      const i = z * w + x;
      if (g0[i] !== NONE && !isBuilding[i]) { ground[i] = g0[i]; continue; }
      if (g0[i] === NONE) {
        const interpolated = interpolateGap(g0, isBuilding, w, x, z, groundRadius, NONE);
        if (interpolated !== null) { ground[i] = interpolated; continue; }
      }
      // Building footprint or unbounded gap: median of surrounding open ground.
      // Prefer flat columns; walls reach below street level and would pull the floor down.
      const vals = [];
      for (let pass = 0; pass < 2 && vals.length === 0; pass++) {
        for (let r = groundRadius; r <= groundRadius * 4 && vals.length === 0; r *= 2) {
          for (let dz = -r; dz <= r; dz += 2) for (let dx = -r; dx <= r; dx += 2) {
            const xx = x + dx, zz = z + dz;
            if (xx < 0 || zz < 0 || xx >= w || zz >= w) continue;
            const j = zz * w + xx;
            if (g0[j] === NONE || isBuilding[j]) continue;
            if (pass === 0 && gtop[j] - g0[j] > 2) continue;
            vals.push(g0[j]);
          }
        }
      }
      if (vals.length) { vals.sort((a, b) => a - b); ground[i] = vals[vals.length >> 1]; }
      else ground[i] = g0[i] !== NONE ? gmin[i] : fallbackY;
    }
  }

  // --- classify surface voxels
  for (const [i, acc] of colors) {
    if (!vox[i]) continue;
    const y = Math.floor(i / plane) + minY;
    vox[i] = classify(acc[0] / acc[3], acc[1] / acc[3], acc[2] / acc[3], y - ground[i % plane]);
  }

  // --- water: OSM water areas whose photo surface sits at water level become Minecraft water
  const water = carveWater(opts.water, vox, g0, x0, z0, w, minY, height, NONE);

  // --- fill below ground (never overwrites surface voxels)
  // Visible filler (dirt/stone) only goes as high as every neighbouring column is solid, so no
  // filler face can ever stick out next to air above the photo mesh (steps, curbs, slopes, under
  // tree canopies). Above that, columns are filled with invisible map blocks.
  const solidTop = new Float64Array(plane);
  for (let i = 0; i < plane; i++) solidTop[i] = (g0[i] !== NONE && !isBuilding[i]) ? g0[i] : Math.floor(ground[i]);
  // Banks are visible down to the water level and below, so you never look into nothing underwater.
  if (water) for (let i = 0; i < plane; i++) if (water.isWater[i]) solidTop[i] = water.level;
  for (let z = 0; z < w; z++) {
    for (let x = 0; x < w; x++) {
      const i = z * w + x;
      if (water?.isWater[i]) continue; // filled by carveWater
      const top = (g0[i] !== NONE && !isBuilding[i]) ? ground[i] - 1 : ground[i];
      const topIdx = Math.min(height - 1, Math.floor(top) - minY);
      let visible = top;
      for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) {
        const xx = x + dx, zz = z + dz;
        if ((dx || dz) && xx >= 0 && zz >= 0 && xx < w && zz < w) visible = Math.min(visible, solidTop[zz * w + xx]);
      }
      const visIdx = Math.min(topIdx, Math.floor(visible) - minY);
      for (let y = 0; y <= topIdx; y++) {
        const k = y * plane + i;
        if (vox[k]) continue;
        if (y === 0) vox[k] = M.BEDROCK;
        else if (y > visIdx) vox[k] = M.HIDDEN_FILL;
        else vox[k] = visIdx - y < 3 ? M.FILL_DIRT : M.FILL_STONE;
      }
      if (vox[i] !== M.BEDROCK) vox[i] = M.BEDROCK;
    }
  }
  // Shoreline: an invisible block on land that faces the water (or the open air just above it)
  // would let you look into nothing, because the photo only covers its top. Such blocks become
  // visible Minecraft blocks, unless a steep photo surface (quay wall) covers that side.
  if (water) {
    const lv = water.level - minY;
    const invisible = (v) => v >= M.GRASS && v <= M.HIDDEN_FILL;
    for (let y = 1; y <= Math.min(height - 2, lv + 3); y++) for (let z = 0; z < w; z++) for (let x = 0; x < w; x++) {
      const i = z * w + x, k = y * plane + i, v = vox[k];
      if (!invisible(v) || water.isWater[i]) continue;
      if (walls[k] && y > lv + 1) continue; // right at the waterline always visible
      for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const xx = x + dx, zz = z + dz;
        if (xx < 0 || zz < 0 || xx >= w || zz >= w) continue;
        const j = zz * w + xx;
        const nv = vox[y * plane + j];
        if (!water.isWater[j] || (nv !== M.WATER && nv !== M.AIR)) continue;
        const topOpen = vox[k + plane] === M.AIR;
        if (v === M.GRASS || v === M.LEAVES) vox[k] = topOpen ? M.GRASS_BLOCK : M.FILL_DIRT;
        else if (v === M.SAND) vox[k] = M.SAND_BLOCK;
        else if (v === M.DIRT || v === M.WOOD || v === M.HIDDEN_FILL) vox[k] = M.FILL_DIRT;
        else vox[k] = M.FILL_STONE;
        break;
      }
    }
  }
  return { vox, ground, surfaceColumns: g0.reduce((n, v) => n + (v !== NONE ? 1 : 0), 0), waterLevel: water?.level ?? null };
}

/** Even-odd rasterization of polygon features (MC-space segment lists) onto the column grid. */
export function rasterizePolygons(features, x0, z0, w) {
  const mask = new Uint8Array(w * w);
  if (!features?.length) return mask;
  const xs = [];
  for (const f of features) {
    const seg = f.segs;
    for (let j = 0; j < w; j++) {
      const zc = z0 + j + 0.5;
      xs.length = 0;
      for (let k = 0; k < seg.length; k += 4) {
        const ax = seg[k], az = seg[k + 1], bx = seg[k + 2], bz = seg[k + 3];
        if ((az > zc) !== (bz > zc)) xs.push(ax + ((zc - az) * (bx - ax)) / (bz - az));
      }
      if (xs.length < 2) continue;
      xs.sort((a, b) => a - b);
      for (let k = 0; k + 1 < xs.length; k += 2) {
        const i0 = Math.max(0, Math.ceil(xs[k] - x0 - 0.5)), i1 = Math.min(w - 1, Math.floor(xs[k + 1] - x0 - 0.5));
        for (let i = i0; i <= i1; i++) mask[j * w + i] = 1;
      }
    }
  }
  return mask;
}

/**
 * Turns OSM water areas into Minecraft water with a visible river bed.
 * - The water level is the median height of the photo surface inside the water areas.
 * - Columns whose surface is well above that level are banks (kept as land), except deep
 *   inside the water area where they are bridges: the deck stays, water flows below it.
 * - Depth grows from 1 at the bank to 5 in the middle; the bed is gravel, dirt, then stone.
 */
function carveWater(features, vox, g0, x0, z0, w, minY, height, NONE) {
  const plane = w * w;
  const mask = rasterizePolygons(features, x0, z0, w);
  const heights = [];
  for (let i = 0; i < plane; i++) if (mask[i] && g0[i] !== NONE) heights.push(g0[i]);
  if (heights.length < 8) return null;
  heights.sort((a, b) => a - b);
  const level = heights[heights.length >> 1];

  // Distance (in columns) to the nearest column outside the water area.
  const dist = new Int32Array(plane).fill(1 << 20);
  const queue = [];
  for (let i = 0; i < plane; i++) if (!mask[i]) { dist[i] = 0; queue.push(i); }
  for (let q = 0; q < queue.length; q++) {
    const i = queue[q], x = i % w, z = (i / w) | 0;
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const xx = x + dx, zz = z + dz;
      if (xx < 0 || zz < 0 || xx >= w || zz >= w) continue;
      const j = zz * w + xx;
      if (dist[j] > dist[i] + 1) { dist[j] = dist[i] + 1; queue.push(j); }
    }
  }

  const isWater = new Uint8Array(plane);
  for (let i = 0; i < plane; i++) {
    if (!mask[i]) continue;
    const high = g0[i] !== NONE && g0[i] > level + 1.5;
    if (high && dist[i] < 3) continue; // bank or quay at the edge of the polygon
    isWater[i] = 1;
  }
  const levelIdx = level - minY;
  if (levelIdx < 6 || levelIdx >= height - 2) return null;
  for (let i = 0; i < plane; i++) {
    if (!isWater[i]) continue;
    const depth = Math.max(1, Math.min(5, 1 + (dist[i] >> 1)));
    const bed = levelIdx - depth;
    for (let y = 0; y <= levelIdx + 1; y++) {
      const k = y * plane + i;
      if (y === 0) vox[k] = M.BEDROCK;
      else if (y < bed - 2) vox[k] = M.FILL_STONE;
      else if (y < bed) vox[k] = M.FILL_DIRT;
      else if (y === bed) vox[k] = M.GRAVEL;
      else if (y <= levelIdx) vox[k] = M.WATER;
      else vox[k] = M.AIR; // photo water surface / wave noise just above the level
    }
  }
  return { isWater, level };
}

/** Converts a primitive's ECEF positions to MC coordinates once per frame. */
export function mcPositions(frame, prim) {
  if (prim._mcFrame === frame) return prim._mc;
  const p = prim.positions, n = p.length;
  const out = new Float64Array(n);
  for (let i = 0; i < n; i += 3) frame.ecefToMc(p[i], p[i + 1], p[i + 2], out, i);
  prim._mc = out; prim._mcFrame = frame;
  return out;
}

function removeIsolated(vox, colors, w, height) {
  const plane = w * w;
  const dead = [];
  for (const i of colors.keys()) {
    const y = Math.floor(i / plane), r = i % plane, z = Math.floor(r / w), x = r % w;
    let found = false;
    for (let dy = -1; dy <= 1 && !found; dy++) for (let dz = -1; dz <= 1 && !found; dz++) for (let dx = -1; dx <= 1 && !found; dx++) {
      if (!dx && !dy && !dz) continue;
      const xx = x + dx, yy = y + dy, zz = z + dz;
      if (xx < 0 || yy < 0 || zz < 0 || xx >= w || zz >= w || yy >= height) continue;
      if (vox[(yy * w + zz) * w + xx]) found = true;
    }
    if (!found) dead.push(i);
  }
  for (const i of dead) { vox[i] = 0; colors.delete(i); }
}

function windowMin(src, w, r, none) {
  // Separable min filter ignoring `none` entries.
  const tmp = new Float64Array(src.length), out = new Float64Array(src.length);
  const pass = (from, to, horizontal) => {
    for (let a = 0; a < w; a++) {
      for (let b = 0; b < w; b++) {
        let m = Infinity;
        for (let k = Math.max(0, b - r); k <= Math.min(w - 1, b + r); k++) {
          const v = horizontal ? from[a * w + k] : from[k * w + a];
          if (v !== none && v < m) m = v;
        }
        to[horizontal ? a * w + b : b * w + a] = m === Infinity ? none : m;
      }
    }
  };
  pass(src, tmp, true);
  pass(tmp, out, false);
  return out;
}
