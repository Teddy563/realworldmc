// End-to-end test against the mock tile server: node --test
import test from 'node:test';
import assert from 'node:assert/strict';
import { startMockServer, MOCK_ORIGIN } from '../src/mock.mjs';
import { EarthService, createServer, loadConfig } from '../src/server.mjs';
import { Frame, geodeticToEcef, ecefToGeodetic } from '../src/geo.mjs';
import { triBoxOverlap, M } from '../src/voxel.mjs';

test('geo round trip', () => {
  const p = geodeticToEcef(52.5, 13.4, 123.4);
  const g = ecefToGeodetic(...p);
  assert.ok(Math.abs(g[0] - 52.5) < 1e-9 && Math.abs(g[1] - 13.4) < 1e-9 && Math.abs(g[2] - 123.4) < 1e-4);
  const f = new Frame(52.5, 13.4, 50);
  const e = f.mcToEcef(10, 70, -20); // 10 m east, 6 m up, 20 m north
  const back = f.ecefToMc(...e);
  assert.ok(Math.abs(back[0] - 10) < 1e-6 && Math.abs(back[1] - 70) < 1e-6 && Math.abs(back[2] + 20) < 1e-6);
  const g2 = ecefToGeodetic(...e);
  assert.ok(g2[0] > 52.5 && g2[1] > 13.4, 'north-east of origin');
});

test('triangle/box overlap', () => {
  const c = [0.5, 0.5, 0.5], h = [0.5, 0.5, 0.5];
  assert.equal(triBoxOverlap(c, h, [-5, 0.5, -5, 5, 0.5, -5, 0, 0.5, 5]), true);   // plane through box
  assert.equal(triBoxOverlap(c, h, [-5, 1.6, -5, 5, 1.6, -5, 0, 1.6, 5]), false);  // above
  assert.equal(triBoxOverlap(c, h, [2, 0, 0, 3, 0, 0, 2, 1, 1]), false);           // beside
  assert.equal(triBoxOverlap(c, h, [-1, -1, 0.5, 2, 2, 0.5, 2, -1, 0.5]), true);   // vertical, through
});

test('service against mock tiles', async (t) => {
  const mock = await startMockServer(0);
  const cfg = { ...loadConfig(), apiKey: 'TEST', rootUrl: mock.url, diskCache: false, port: 0, water: false };
  const service = new EarthService(cfg);
  const server = createServer(service);
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  t.after(() => { server.close(); mock.server.close(); });
  const { lat, lon } = MOCK_ORIGIN;

  const health = await (await fetch(`${base}/health`)).json();
  assert.equal(health.name, 'RealWorldMC');
  assert.equal(health.version, '1.0.0');
  for (const query of ['lat=91&lon=0', 'lat=&lon=0']) {
    assert.equal((await fetch(`${base}/ground?${query}`)).status, 400);
  }
  for (const extra of ['height=1000000000&cx=0', 'height=704&cx=0.5']) {
    assert.equal((await fetch(`${base}/chunk?lat=${lat}&lon=${lon}&h=0&cz=0&minY=-64&${extra}`)).status, 400);
  }
  const missing = await fetch(`${base}/chunk?lat=${lat}&lon=${lon}&h=0&cx=10000&cz=10000&minY=-64&height=128`);
  assert.equal(missing.status, 404, 'no coverage must not return a generated flat chunk');
  assert.match((await missing.json()).error, /No usable 3D terrain/);

  const ground = await (await fetch(`${base}/ground?lat=${lat}&lon=${lon}`)).json();
  // The estimate is the low part of a 180 m window (the mock ground slopes by +-2.7 m across it).
  assert.ok(Math.abs(ground.height - MOCK_ORIGIN.ground) < 3.0, `ground ${JSON.stringify(ground)}`);
  const h = MOCK_ORIGIN.ground; // block expectations below assume the exact origin height

  // Every request after root.json must carry the key and the session.
  for (const l of mock.log) {
    assert.match(l, /key=TEST/);
    if (!l.includes('root.json')) assert.match(l, /session=MOCKSESSION/);
  }

  const minY = -64, height = 384;
  const getChunk = async (cx, cz) => {
    const r = await fetch(`${base}/chunk?lat=${lat}&lon=${lon}&h=${h}&cx=${cx}&cz=${cz}&minY=${minY}&height=${height}`);
    assert.equal(r.status, 200);
    return new Uint8Array(await r.arrayBuffer());
  };
  const at = (data, lx, y, lz) => data[((y - minY) * 16 + lz) * 16 + lx];
  const column = (data, lx, lz) => { const c = []; for (let y = minY; y < minY + height; y++) c.push(at(data, lx, y, lz)); return c; };
  const topSolid = (data, lx, lz) => { for (let y = minY + height - 1; y >= minY; y--) if (at(data, lx, y, lz)) return y; return null; };

  // Chunk (2,0): x 32..47, z 0..15 -> grass south of the road (north = -z), no building.
  const c20 = await getChunk(2, 0);
  {
    // MC x=40 -> E=40, ground U = 1.2 -> MC 65.2 -> standing surface block should be 64 or 65
    const top = topSolid(c20, 8, 12);
    assert.ok(top === 64 || top === 65, `ground top ${top}`);
    assert.equal(at(c20, 8, top, 12), M.GRASS, 'grass material');
    assert.equal(at(c20, 8, top - 1, 12), M.FILL_DIRT);
    assert.equal(at(c20, 8, top - 10, 12), M.FILL_STONE);
    assert.equal(at(c20, 8, minY, 12), M.BEDROCK);
    // road at z=0 (N=0)
    const rt = topSolid(c20, 8, 0);
    assert.equal(at(c20, 8, rt, 0), M.ASPHALT, 'asphalt on road');
  }

  // No visible filler face may touch air sideways or from above (it would poke out of the photo).
  {
    const isFill = (v) => v === M.FILL_DIRT || v === M.FILL_STONE;
    let exposed = 0;
    for (let y = minY + 1; y < minY + height - 1; y++) for (let z = 1; z < 15; z++) for (let x = 1; x < 15; x++) {
      if (!isFill(at(c20, x, y, z))) continue;
      if (!at(c20, x + 1, y, z) || !at(c20, x - 1, y, z) || !at(c20, x, y, z + 1) || !at(c20, x, y, z - 1) || !at(c20, x, y + 1, z)) exposed++;
    }
    assert.equal(exposed, 0, 'filler blocks exposed to air');
  }

  // Building: E 10..30, N -20..10 -> MC x 10..30, z -10..20. Chunk (1,0) covers x 16..31, z 0..15.
  const c10 = await getChunk(1, 0);
  {
    const col = column(c10, 4, 8); // x=20, z=8: inside the building
    const roofY = topSolid(c10, 4, 8);
    assert.ok(roofY >= 64 + 38 && roofY <= 64 + 42, `roof at ${roofY}`);
    assert.equal(at(c10, 4, roofY, 8), M.BRICK, 'red roof -> brick');
    // hollow inside: air a few meters above ground level
    assert.equal(at(c10, 4, 64 + 10, 8), M.AIR, 'building is hollow');
    // floor exists at roughly street level
    const floorY = col.findIndex((v, i) => v && !col[i + 1]) + minY;
    const outside = topSolid(c20, 0, 8);   // x=32, z=8: just east of the building
    assert.ok(Math.abs(floorY - outside) <= 1, `floor at ${floorY}, street at ${outside}`);
    // wall at x=30 (lx 14) z=8 is plaster
    let wall = 0;
    for (let y = 70; y < 100; y++) if (at(c10, 14, y, 8) === M.PLASTER) wall++;
    assert.ok(wall > 20, `wall voxels ${wall}`);
  }

  // Tree: E=-30, N=20 -> MC x=-30, z=-20, canopy U 3..9 -> leaves well above ground
  const ct = await getChunk(-2, -2); // x -32..-17, z -32..-17
  {
    let leaves = 0;
    for (let y = 64 + 4; y < 64 + 12; y++) if (at(ct, 2, y, 12) === M.LEAVES) leaves++;
    assert.ok(leaves >= 2, `leaves ${leaves}`);
  }

  // Rendering: near the origin we should get the fine tiles, far away the coarse one.
  const sel = async (x, y, z) => {
    for (let i = 0; i < 10; i++) {
      const s = await (await fetch(`${base}/render/select?lat=${lat}&lon=${lon}&h=${h}&x=${x}&y=${y}&z=${z}`)).json();
      if (!s.pending) return s;
      await new Promise((r) => setTimeout(r, 50));
    }
    throw new Error('selection never settled');
  };
  const near = await sel(0, 70, 0);
  assert.equal(near.tiles.length, 4);
  assert.ok(near.tiles.every((t) => t.fine));
  const far = await sel(0, 1500, 0);
  assert.equal(far.tiles.length, 1);
  assert.equal(far.tiles[0].fine, false);

  const r = await fetch(`${base}/render/tile?lat=${lat}&lon=${lon}&h=${h}&id=${near.tiles[0].id}`);
  const buf = Buffer.from(await r.arrayBuffer());
  assert.equal(buf.toString('ascii', 0, 4), 'ECT1');
  const prims = buf.readInt32LE(4);
  assert.equal(prims, 1);
  const verts = buf.readInt32LE(32), tw = buf.readInt32LE(36);
  assert.ok(verts > 0 && verts % 3 === 0 && tw === 64);
  assert.equal(buf.length, 32 + 16 + verts * 20 + 64 * 64 * 4);

  const again = await sel(0, 70, 0);
  assert.deepEqual(again.attribution.sort(), ['Mock Data', 'Mock Imagery']);
});
