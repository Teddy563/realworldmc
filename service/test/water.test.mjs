// River carving on a synthetic scene: channel with quay walls, photo water surface and a bridge.
import test from 'node:test';
import assert from 'node:assert/strict';
import { voxelizeArea, M } from '../src/voxel.mjs';

function quad(pos, idx, a, b, c, d) {
  const base = pos.length / 3;
  pos.push(...a, ...b, ...c, ...d);
  idx.push(base, base + 1, base + 2, base, base + 2, base + 3);
}

test('river becomes Minecraft water with a closed bed and intact bridge', () => {
  const W = 64, x0 = 0, z0 = 0, minY = -64, height = 256;
  const pos = [], idx = [];
  const G = 66.6, L = 63.6, B = 70.6; // land, water surface, bridge deck (mesh heights)
  quad(pos, idx, [0, G, 0], [20, G, 0], [20, G, W], [0, G, W]);          // west land
  quad(pos, idx, [40, G, 0], [W, G, 0], [W, G, W], [40, G, W]);          // east land
  quad(pos, idx, [20, L, 0], [40, L, 0], [40, L, W], [20, L, W]);        // photo water surface
  quad(pos, idx, [20, L, 0], [20, G, 0], [20, G, W], [20, L, W]);        // west quay wall
  quad(pos, idx, [40, L, 0], [40, G, 0], [40, G, W], [40, L, W]);        // east quay wall
  quad(pos, idx, [20, B, 28], [40, B, 28], [40, B, 34], [20, B, 34]);    // bridge deck
  const prim = { positions: new Float64Array(pos.length), uvs: new Float32Array((pos.length / 3) * 2), indices: Uint32Array.from(idx), texture: null, color: [120, 120, 120] };
  const frame = {};
  prim._mc = Float64Array.from(pos); prim._mcFrame = frame;
  // OSM polygon slightly wider than the channel (typical mapping inaccuracy).
  const water = [{ segs: Float64Array.from([19, -1, 41, -1, 41, -1, 41, 65, 41, 65, 19, 65, 19, 65, 19, -1]) }];

  const { vox, waterLevel } = voxelizeArea(frame, [{ primitives: [prim] }], x0, z0, W, minY, height, { water });
  const at = (x, y, z) => vox[((y - minY) * W + z) * W + x];
  assert.equal(waterLevel, 63);

  // Channel center: water down to a gravel bed, solid below.
  const col = (x, z) => { const o = []; for (let y = 55; y <= 66; y++) o.push(at(x, y, z)); return o; };
  assert.equal(at(30, 63, 10), M.WATER);
  assert.equal(at(30, 64, 10), M.AIR, 'nothing floating on the water');
  let y = 63; while (at(30, y, 10) === M.WATER) y--;
  assert.equal(at(30, y, 10), M.GRAVEL, `bed below water: ${col(30, 10)}`);
  assert.ok(63 - y >= 4, 'deep in the middle');
  assert.ok(at(21, 62, 10) === M.WATER || at(21, 62, 10) === M.GRAVEL, 'shallow at the bank');

  // Bridge: deck stays solid, water flows below it.
  assert.equal(at(30, 63, 30), M.WATER);
  assert.ok(at(30, 70, 30) !== M.AIR, 'bridge deck kept');
  assert.equal(at(30, 66, 30), M.AIR, 'free space under the bridge');

  // Nothing visible may face empty space (no looking through the ground), and water may not leak.
  const visible = (v) => v === M.FILL_DIRT || v === M.FILL_STONE || v === M.GRAVEL;
  // Air directly above water is the open space over the river: a visible quay wall facing it is wanted.
  const overWater = (x, y, z) => { while (y > minY && at(x, y, z) === M.AIR) y--; return at(x, y, z) === M.WATER; };
  let exposed = 0, leaks = 0;
  for (let yy = minY + 1; yy < 100; yy++) for (let z = 1; z < W - 1; z++) for (let x = 1; x < W - 1; x++) {
    const v = at(x, yy, z);
    const n = [at(x + 1, yy, z), at(x - 1, yy, z), at(x, yy, z + 1), at(x, yy, z - 1)];
    const sideAir = [[x + 1, z], [x - 1, z], [x, z + 1], [x, z - 1]].some(([xx, zz]) => at(xx, yy, zz) === M.AIR && !overWater(xx, yy, zz));
    if (visible(v) && (sideAir || at(x, yy + 1, z) === M.AIR)) exposed++;
    if (v === M.WATER && (n.includes(M.AIR) || at(x, yy - 1, z) === M.AIR)) leaks++;
  }
  assert.equal(exposed, 0, 'filler/bed exposed to air');
  assert.equal(leaks, 0, 'water next to air would flow away');

  // Seen from the water, no invisible filler may face open air (no looking into nothing).
  let seeThrough = 0;
  for (let yy = 60; yy < 70; yy++) for (let z = 1; z < W - 1; z++) for (let x = 1; x < W - 1; x++) {
    if (at(x, yy, z) !== M.HIDDEN_FILL) continue;
    if ([at(x + 1, yy, z), at(x - 1, yy, z)].includes(M.AIR)) seeThrough++;
  }
  assert.equal(seeThrough, 0, 'invisible filler next to air over water');

  // Underwater, the banks are real visible blocks (not invisible map blocks).
  for (let yy = 59; yy <= 63; yy++) {
    const bank = at(19, yy, 10);
    assert.ok(bank === M.FILL_DIRT || bank === M.FILL_STONE || bank >= 1 && bank <= 9 && yy === 63, `bank at y=${yy} is ${bank}`);
  }
});

test('pond with sloped grass banks: no see-through at the shoreline', () => {
  const W = 64, minY = -64, height = 256;
  // Heightfield: lawn at 66.6, pond surface at 64.6 inside [24,40)^2, 2 m wide slope around it.
  const h = (x, z) => {
    const d = Math.max(24 - x, x - 40, 24 - z, z - 40); // >0 outside the pond square
    if (d <= 0) return 64.6;
    return 64.6 + Math.min(2, d);
  };
  const pos = [], idx = [];
  for (let z = 0; z < W; z++) for (let x = 0; x < W; x++) {
    const base = pos.length / 3;
    pos.push(x, h(x, z), z, x + 1, h(x + 1, z), z, x + 1, h(x + 1, z + 1), z + 1, x, h(x, z + 1), z + 1);
    idx.push(base, base + 1, base + 2, base, base + 2, base + 3);
  }
  const prim = { positions: new Float64Array(pos.length), uvs: new Float32Array((pos.length / 3) * 2), indices: Uint32Array.from(idx), texture: null, color: [70, 140, 50] };
  const frame = {};
  prim._mc = Float64Array.from(pos); prim._mcFrame = frame;
  // OSM outline a bit larger than the pond (typical), so the rim photo gets carved away.
  const water = [{ segs: Float64Array.from([23, 23, 41, 23, 41, 23, 41, 41, 41, 41, 23, 41, 23, 41, 23, 23]) }];
  const { vox, waterLevel } = voxelizeArea(frame, [{ primitives: [prim] }], 0, 0, W, minY, height, { water });
  const at = (x, y, z) => vox[((y - minY) * W + z) * W + x];
  assert.ok(waterLevel >= 63 && waterLevel <= 65, `level ${waterLevel}`);
  assert.equal(at(32, waterLevel, 32), M.WATER);

  const invisible = (v) => v >= M.GRASS && v <= M.HIDDEN_FILL;
  let seeThrough = 0;
  for (let y = waterLevel - 6; y <= waterLevel + 3; y++) for (let z = 1; z < W - 1; z++) for (let x = 1; x < W - 1; x++) {
    if (!invisible(at(x, y, z))) continue;
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const n = at(x + dx, y, z + dz);
      if (n === M.WATER) { seeThrough++; if (process.env.DBG) console.log('W', x, y, z, at(x, y, z), dx, dz); }
      if (n === M.AIR) { let yy = y; while (at(x + dx, yy, z + dz) === M.AIR) yy--; if (at(x + dx, yy, z + dz) === M.WATER) { seeThrough++; if (process.env.DBG) console.log('A', x, y, z, at(x, y, z), dx, dz, 'lv', waterLevel); } }
    }
  }
  assert.equal(seeThrough, 0, 'invisible block faces water or the air over it');
});
