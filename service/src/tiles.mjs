// 3D Tiles client: lazily walks the (Google Photorealistic) tileset tree,
// downloads and decodes glTF content.

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { NodeIO } from '@gltf-transform/core';
import { ALL_EXTENSIONS } from '@gltf-transform/extensions';
import draco3d from 'draco3dgltf';
import jpeg from 'jpeg-js';
import { IDENTITY, Y_UP_TO_Z_UP, mul4, transformBox, geodeticToEcef } from './geo.mjs';

class Limiter {
  constructor(max) { this.max = max; this.active = 0; this.queue = []; }
  async run(fn) {
    if (this.active >= this.max) await new Promise((r) => this.queue.push(r));
    this.active++;
    try { return await fn(); } finally {
      this.active--;
      const next = this.queue.shift();
      if (next) next();
    }
  }
}

class Lru {
  constructor(max) { this.max = max; this.map = new Map(); }
  get(k) {
    const v = this.map.get(k);
    if (v !== undefined) { this.map.delete(k); this.map.set(k, v); }
    return v;
  }
  set(k, v) {
    this.map.delete(k); this.map.set(k, v);
    while (this.map.size > this.max) this.map.delete(this.map.keys().next().value);
  }
}

export class TileNode {
  constructor(id, parent) {
    this.id = id;
    this.parent = parent;
    this.box = null;            // ECEF oriented box [cx,cy,cz, ax,ay,az, bx,by,bz, cx,cy,cz]
    this.geometricError = 0;
    this.transform = IDENTITY;
    this.contentUrl = null;     // absolute URL without API key
    this.contentKind = null;    // 'json' | 'glb' | null
    this.children = [];         // for external tilesets: filled by expand()
    this.expanded = true;
    this.expanding = null;
  }
  get hasContent() { return this.contentKind === 'glb'; }
  get mayHaveChildren() { return !this.expanded || this.children.length > 0; }
}

function boundingVolumeToBox(bv, transform) {
  if (bv.box) return transformBox(transform, bv.box);
  if (bv.sphere) {
    const [x, y, z, r] = bv.sphere;
    return transformBox(transform, [x, y, z, r, 0, 0, 0, r, 0, 0, 0, r]);
  }
  if (bv.region) {
    // Regions are always in EPSG:4979 (no transform applies). Box around the corners.
    const [w, s, e, n, minH, maxH] = bv.region;
    const pts = [];
    for (const lat of [s, n, (s + n) / 2]) for (const lon of [w, e, (w + e) / 2]) for (const h of [minH, maxH]) {
      pts.push(geodeticToEcef(lat * 180 / Math.PI, lon * 180 / Math.PI, h));
    }
    const mn = [Infinity, Infinity, Infinity], mx = [-Infinity, -Infinity, -Infinity];
    for (const p of pts) for (let i = 0; i < 3; i++) { mn[i] = Math.min(mn[i], p[i]); mx[i] = Math.max(mx[i], p[i]); }
    const c = [0, 1, 2].map((i) => (mn[i] + mx[i]) / 2), h = [0, 1, 2].map((i) => (mx[i] - mn[i]) / 2);
    return [...c, h[0], 0, 0, 0, h[1], 0, 0, 0, h[2]];
  }
  throw new Error('unsupported bounding volume');
}

export class TileClient {
  constructor(cfg) {
    this.cfg = cfg;
    this.fetchLimiter = new Limiter(cfg.maxConcurrentDownloads ?? 12);
    this.decodeLimiter = new Limiter(4);
    this.decoded = new Lru(cfg.decodedTileCache ?? 600);
    this.decoding = new Map();
    this.nodes = new Map();
    this.nextId = 1;
    this.root = null;
    this.rootPromise = null;
    this.generation = 0;
    this.stats = { requests: 0, cacheHits: 0, bytes: 0 };
    this.io = null;
  }

  async getIo() {
    if (!this.io) {
      this.io = new NodeIO().registerExtensions(ALL_EXTENSIONS).registerDependencies({
        'draco3d.decoder': await draco3d.createDecoderModule(),
      });
    }
    return this.io;
  }

  /** Resolves a (possibly relative) content URI, carrying over the session parameter. */
  resolve(uri, base) {
    const u = new URL(uri, base);
    const b = new URL(base);
    if (!u.searchParams.has('session') && b.searchParams.has('session')) u.searchParams.set('session', b.searchParams.get('session'));
    u.searchParams.delete('key');
    return u.toString();
  }

  withKey(url) {
    const u = new URL(url);
    if (u.origin !== new URL(this.cfg.rootUrl).origin) throw new Error('Cross-origin tile requests are not supported');
    if (this.cfg.apiKey) u.searchParams.set('key', this.cfg.apiKey);
    return u.toString();
  }

  cachePath(url, ext) {
    const u = new URL(url);
    u.searchParams.delete('session');
    u.searchParams.delete('key');
    const h = crypto.createHash('sha1').update(u.toString()).digest('hex');
    return path.join(this.cfg.cacheDir, h.slice(0, 2), h + ext);
  }

  async fetchBytes(url, { cacheExt = null } = {}) {
    const cp = cacheExt && this.cfg.diskCache ? this.cachePath(url, cacheExt) : null;
    if (cp) {
      try {
        const buf = await fs.promises.readFile(cp);
        this.stats.cacheHits++;
        return new Uint8Array(buf.buffer, buf.byteOffset, buf.byteLength);
      } catch { /* miss */ }
    }
    const gen = this.generation;
    const bytes = await this.fetchLimiter.run(async () => {
      for (let attempt = 0; ; attempt++) {
        this.stats.requests++;
        let res;
        try {
          res = await fetch(this.withKey(url), { signal: AbortSignal.timeout(30_000), redirect: 'error' });
        } catch (e) {
          if (attempt < 3) { await new Promise((r) => setTimeout(r, 500 * (attempt + 1))); continue; }
          throw e;
        }
        if (res.ok) return new Uint8Array(await res.arrayBuffer());
        await res.body?.cancel();
        if ((res.status === 429 || res.status >= 500) && attempt < 3) {
          await new Promise((r) => setTimeout(r, 1000 * (attempt + 1)));
          continue;
        }
        const err = new Error(`HTTP ${res.status} for ${new URL(url).pathname}`);
        err.status = res.status;
        throw err;
      }
    }).catch(async (e) => {
      // Google can reject expired 3D tile sessions with 400. Discard the tree
      // so the next operation obtains new content URLs instead of retrying stale ones.
      const sessionRequest = new URL(url).searchParams.has('session');
      if (e.status === 400 && sessionRequest) {
        if (gen === this.generation) this.reset('tile session rejected (400)');
        e.message = 'Map tile session rejected (HTTP 400). The tileset was reset; retry the command. If it repeats, restart the map service and check provider access.';
      }
      // An expired session shows up as a 4xx on tile requests: start over from root.json.
      if ((e.status === 401 || e.status === 403) && gen === this.generation) {
        try { await this.cfg.refreshEndpoint?.(); }
        finally { if (gen === this.generation) this.reset(`session error (${e.status})`); }
      }
      throw e;
    });
    this.stats.bytes += bytes.byteLength;
    if (cp) {
      fs.promises.mkdir(path.dirname(cp), { recursive: true }).then(() => fs.promises.writeFile(cp, bytes)).catch(() => {});
    }
    return bytes;
  }

  reset(reason) {
    console.warn(`[tiles] resetting tileset (${reason})`);
    this.generation++;
    this.root = null;
    this.rootPromise = null;
    this.nodes.clear();
  }

  async getRoot() {
    if (this.root) return this.root;
    if (!this.rootPromise) {
      this.rootPromise = (async () => {
        if (!this.cfg.apiKey && this.cfg.rootUrl.includes('googleapis.com')) {
          throw new Error('No Google API key configured (service/config.json -> apiKey)');
        }
        const bytes = await this.fetchBytes(this.cfg.rootUrl);
        const json = JSON.parse(new TextDecoder().decode(bytes));
        const root = this.buildNode(json.root, IDENTITY, this.cfg.rootUrl, null);
        this.root = root;
        console.log(`[tiles] root tileset loaded`);
        return root;
      })().catch((e) => { this.rootPromise = null; throw e; });
    }
    return this.rootPromise;
  }

  buildNode(json, parentTransform, baseUrl, parent) {
    const node = new TileNode(this.nextId++, parent);
    this.nodes.set(node.id, node);
    node.transform = json.transform ? mul4(parentTransform, json.transform) : parentTransform;
    node.box = boundingVolumeToBox(json.boundingVolume, node.transform);
    node.geometricError = json.geometricError ?? 0;
    const uri = json.content?.uri ?? json.content?.url;
    if (uri) {
      node.contentUrl = this.resolve(uri, baseUrl);
      node.contentKind = new URL(node.contentUrl).pathname.endsWith('.json') ? 'json' : 'glb';
    }
    if (node.contentKind === 'json') node.expanded = false;
    node.children = (json.children ?? []).map((c) => this.buildNode(c, node.transform, baseUrl, node));
    return node;
  }

  /** Loads the external tileset referenced by a node (if any). */
  expand(node) {
    if (node.expanded) return Promise.resolve(node.children);
    if (!node.expanding) {
      node.expanding = (async () => {
        const bytes = await this.fetchBytes(node.contentUrl);
        const json = JSON.parse(new TextDecoder().decode(bytes));
        const child = this.buildNode(json.root, node.transform, node.contentUrl, node);
        node.children.push(child);
        node.expanded = true;
        return node.children;
      })().finally(() => { node.expanding = null; });
    }
    return node.expanding;
  }

  /** Decoded triangles of a tile, positions in ECEF. Cached. */
  async getMesh(node) {
    const key = node.contentUrl;
    const hit = this.decoded.get(key);
    if (hit) return hit;
    let p = this.decoding.get(key);
    if (!p) {
      p = (async () => {
        const bytes = await this.fetchBytes(node.contentUrl, { cacheExt: '.glb' });
        const mesh = await this.decodeLimiter.run(() => this.decodeGlb(bytes, node.transform));
        this.decoded.set(key, mesh);
        return mesh;
      })().finally(() => this.decoding.delete(key));
      this.decoding.set(key, p);
    }
    return p;
  }

  async decodeGlb(bytes, tileTransform) {
    const io = await this.getIo();
    const doc = await io.readBinary(bytes);
    const root = doc.getRoot();
    const copyright = root.getAsset().copyright ?? '';
    const base = mul4(tileTransform, Y_UP_TO_Z_UP);
    const primitives = [];
    const textures = new Map();
    const el = [0, 0, 0];

    const decodeTexture = (tex) => {
      if (!tex) return null;
      if (textures.has(tex)) return textures.get(tex);
      let out = null;
      const img = tex.getImage();
      const mime = tex.getMimeType();
      if (img && (mime === 'image/jpeg' || (img[0] === 0xff && img[1] === 0xd8))) {
        try {
          const d = jpeg.decode(img, { useTArray: true, formatAsRGBA: true, maxMemoryUsageInMB: 256 });
          out = { width: d.width, height: d.height, data: d.data };
        } catch { out = null; }
      }
      textures.set(tex, out);
      return out;
    };

    for (const scene of root.listScenes()) {
      scene.traverse((gnode) => {
        const mesh = gnode.getMesh();
        if (!mesh) return;
        const m = mul4(base, gnode.getWorldMatrix());
        for (const prim of mesh.listPrimitives()) {
          if (prim.getMode() !== 4) continue; // TRIANGLES only
          const pos = prim.getAttribute('POSITION');
          if (!pos) continue;
          const uv = prim.getAttribute('TEXCOORD_0');
          const n = pos.getCount();
          const positions = new Float64Array(n * 3);
          const uvs = new Float32Array(n * 2);
          for (let i = 0; i < n; i++) {
            pos.getElement(i, el);
            const x = el[0], y = el[1], z = el[2];
            positions[i * 3] = m[0] * x + m[4] * y + m[8] * z + m[12];
            positions[i * 3 + 1] = m[1] * x + m[5] * y + m[9] * z + m[13];
            positions[i * 3 + 2] = m[2] * x + m[6] * y + m[10] * z + m[14];
            if (uv) { uv.getElement(i, el); uvs[i * 2] = el[0]; uvs[i * 2 + 1] = el[1]; }
          }
          const idx = prim.getIndices();
          let indices;
          if (idx) indices = Uint32Array.from(idx.getArray());
          else { indices = new Uint32Array(n); for (let i = 0; i < n; i++) indices[i] = i; }
          const mat = prim.getMaterial();
          const texture = mat ? decodeTexture(mat.getBaseColorTexture()) : null;
          const f = mat ? mat.getBaseColorFactor() : [0.6, 0.6, 0.6, 1];
          primitives.push({ positions, uvs, indices, texture, color: [f[0] * 255, f[1] * 255, f[2] * 255] });
        }
      });
    }
    return { primitives, copyright };
  }
}

/** Samples a primitive's color at a uv coordinate (nearest texel). */
export function sampleColor(prim, u, v, out) {
  const t = prim.texture;
  if (!t) { out[0] = prim.color[0]; out[1] = prim.color[1]; out[2] = prim.color[2]; return out; }
  u -= Math.floor(u); v -= Math.floor(v);
  const x = Math.min(t.width - 1, (u * t.width) | 0);
  const y = Math.min(t.height - 1, (v * t.height) | 0);
  const i = (y * t.width + x) * 4;
  out[0] = t.data[i]; out[1] = t.data[i + 1]; out[2] = t.data[i + 2];
  return out;
}
