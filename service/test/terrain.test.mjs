import test from 'node:test';
import assert from 'node:assert/strict';
import { interpolateGap, voxelizeArea, M } from '../src/voxel.mjs';

const NONE = -1e9;
test('small missing ground columns follow a measured slope', () => {
  const w = 9, src = new Float64Array(w * w), buildings = new Uint8Array(w * w);
  for (let z = 0; z < w; z++) for (let x = 0; x < w; x++) src[z * w + x] = 60 + x;
  src[4 * w + 4] = NONE;
  src[4 * w + 3] = NONE;
  assert.equal(interpolateGap(src, buildings, w, 4, 4, 4, NONE), 64);
  assert.equal(interpolateGap(src, buildings, w, 3, 4, 4, NONE), 63);
});

test('gap interpolation refuses cliffs, unbounded edges, and roofs', () => {
  const w = 9, src = new Float64Array(w * w).fill(NONE), buildings = new Uint8Array(w * w);
  src[4 * w + 3] = 60; src[4 * w + 5] = 90;
  assert.equal(interpolateGap(src, buildings, w, 4, 4, 4, NONE), null);
  src[4 * w + 5] = NONE;
  assert.equal(interpolateGap(src, buildings, w, 4, 4, 4, NONE), null);
  src[4 * w + 5] = 60; buildings[4 * w + 5] = 1;
  assert.equal(interpolateGap(src, buildings, w, 4, 4, 4, NONE), null);
});

function scene(positions, indices, texture = null, uvs = null) {
  const frame = {};
  const prim = {
    positions: Float64Array.from(positions), indices: Uint32Array.from(indices),
    uvs: uvs ? Float32Array.from(uvs) : new Float32Array(positions.length / 3 * 2),
    texture, color: [70, 140, 50], _mcFrame: frame, _mc: Float64Array.from(positions),
  };
  return voxelizeArea(frame, [{ primitives: [prim] }], 0, 0, 16, 0, 80);
}

test('one large textured ground triangle produces local grass and asphalt materials', () => {
  const data = new Uint8Array([70, 140, 50, 255, 30, 30, 30, 255]);
  const { vox } = scene([0, 64.6, 0, 16, 64.6, 0, 0, 64.6, 16], [0, 1, 2],
    { width: 2, height: 1, data }, [0, 0, 0.99, 0, 0, 0.99]);
  const at = (x, y, z) => vox[(y * 16 + z) * 16 + x];
  assert.equal(at(2, 64, 2), M.GRASS);
  assert.equal(at(11, 64, 2), M.ASPHALT, 'material follows the texel, not the triangle centroid');
  assert.equal(at(2, 0, 2), M.BEDROCK);
});

test('degenerate triangles do not create floating collision lines', () => {
  const { vox, surfaceColumns } = scene([1, 70.6, 1, 8, 70.6, 1, 15, 70.6, 1], [0, 1, 2]);
  assert.equal(surfaceColumns, 0);
  assert.equal(vox[(70 * 16 + 1) * 16 + 8], M.AIR);
});

test('empty coverage is distinguishable from real terrain', () => {
  const { surfaceColumns } = scene([], []);
  assert.equal(surfaceColumns, 0);
});

test('building footprint filler stays below the adjacent photographed street', () => {
  // A hollow scanned building has a roof but no photographed floor inside it.
  const positions=[],indices=[];
  const quad=(x0,z0,x1,z1,y)=>{
    const n=positions.length/3;positions.push(x0,y,z0,x1,y,z0,x1,y,z1,x0,y,z1);
    indices.push(n,n+1,n+2,n,n+2,n+3);
  };
  quad(0,0,5,16,64.6);quad(11,0,16,16,64.6);
  quad(5,0,11,5,64.6);quad(5,11,11,16,64.6);
  quad(5,5,11,11,76.6);
  const {vox}=scene(positions,indices);
  const at=(x,y,z)=>vox[(y*16+z)*16+x];
  assert.equal(at(8,64,8),M.HIDDEN_FILL,'a dirt cube here would reach 65, above the 64.6m street photo');
  assert.equal(at(8,63,8),M.FILL_DIRT,'deep earth remains physical and textured');
  assert.equal(at(8,66,8),M.AIR,'building interior remains hollow');
});
