// Water areas (rivers, canals, lakes) from OpenStreetMap via the Overpass API.
// Data © OpenStreetMap contributors (ODbL). Cached on disk in ~2 km cells.

import fs from 'node:fs';
import path from 'node:path';

const CELL = 0.02; // degrees
const memory = new Map();

function cellKey(i, j) {
  return `${i}_${j}`;
}

function query(s, w, n, e) {
  const b = `${s},${w},${n},${e}`;
  return `[out:json][timeout:90];
(
  way["natural"="water"](${b});
  relation["natural"="water"](${b});
  way["waterway"="riverbank"](${b});
  relation["waterway"="riverbank"](${b});
);
out geom;`;
}

const SKIP_WATER = new Set(['fountain', 'reflecting_pool', 'wastewater', 'basin', 'swimming_pool']);

/** Overpass JSON -> features, each a list of line strings ([[lat, lon], ...]) whose segments form closed rings. */
function toFeatures(json) {
  const out = [];
  for (const el of json.elements ?? []) {
    const t = el.tags ?? {};
    if (SKIP_WATER.has(t.water) || t.covered === 'yes' || t.tunnel) continue;
    const lines = [];
    if (el.type === 'way' && el.geometry) lines.push(el.geometry.map((p) => [p.lat, p.lon]));
    if (el.type === 'relation') {
      for (const m of el.members ?? []) {
        if (m.type === 'way' && m.geometry && (m.role === 'outer' || m.role === 'inner' || m.role === '')) {
          lines.push(m.geometry.map((p) => [p.lat, p.lon]));
        }
      }
    }
    if (lines.length) out.push({ id: `${el.type}/${el.id}`, lines });
  }
  return out;
}

async function loadCell(cfg, i, j) {
  const key = cellKey(i, j);
  if (memory.has(key)) return memory.get(key);
  const p = (async () => {
    const file = path.join(cfg.cacheDir, 'osm', `water_${key}.json`);
    try {
      return JSON.parse(await fs.promises.readFile(file, 'utf8'));
    } catch { /* not cached */ }
    const s = i * CELL, w = j * CELL;
    let lastErr;
    for (let attempt = 0; attempt < 4; attempt++) {
      try {
        const res = await fetch(cfg.overpassUrl ?? 'https://overpass-api.de/api/interpreter', {
          method: 'POST',
          headers: { 'content-type': 'application/x-www-form-urlencoded', 'user-agent': 'RealWorldMC/1.0.0 (Minecraft terrain service)' },
          body: 'data=' + encodeURIComponent(query(s, w, s + CELL, w + CELL)),
        });
        if (!res.ok) throw new Error(`Overpass HTTP ${res.status}`);
        const features = toFeatures(await res.json());
        await fs.promises.mkdir(path.dirname(file), { recursive: true });
        await fs.promises.writeFile(file, JSON.stringify(features));
        return features;
      } catch (e) {
        lastErr = e;
        await new Promise((r) => setTimeout(r, 2000 * (attempt + 1)));
      }
    }
    throw lastErr;
  })();
  memory.set(key, p);
  p.catch(() => memory.delete(key));
  return p;
}

/** Water features overlapping a lat/lon box (deduplicated across cells). */
export async function waterFeatures(cfg, south, west, north, east) {
  const cells = [];
  for (let i = Math.floor(south / CELL); i <= Math.floor(north / CELL); i++) {
    for (let j = Math.floor(west / CELL); j <= Math.floor(east / CELL); j++) cells.push(loadCell(cfg, i, j));
  }
  const seen = new Map();
  for (const list of await Promise.all(cells)) for (const f of list) seen.set(f.id, f);
  return [...seen.values()];
}
