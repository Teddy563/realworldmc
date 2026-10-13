import test from 'node:test';
import assert from 'node:assert/strict';
import { spaceRenderOptions, spaceAltitude } from '../src/space-view.mjs';
import { Frame, geodeticToEcef } from '../src/geo.mjs';

test('orbital altitude follows the Earth on the far side and at the poles', () => {
  const frame = new Frame(44.4, 26.1, 120);
  for (const [lat, lon] of [[44.4,26.1], [-44.4,-153.9], [89,0], [-89,0]]) {
    const camera = frame.ecefToMc(...geodeticToEcef(lat, lon, 1000120));
    assert.ok(Math.abs(spaceAltitude(frame, camera) - 1000000) < 2);
    assert.ok(spaceRenderOptions({far:8000, maxSse:12, falloff:600, maxTiles:900}, spaceAltitude(frame,camera)).far > 1000000);
  }
});

test('space selection retains ground settings and keeps the Earth inside the far plane', () => {
  const normal = {far: 8000, maxSse: 12, falloff: 600, maxTiles: 900};
  for (const h of [NaN, -100, 0, 1499]) assert.equal(spaceRenderOptions(normal, h), normal);
  let previous = 8000;
  for (const h of [1500, 10000, 100000, 1000000, 20000000]) {
    const view = spaceRenderOptions(normal, h);
    assert.ok(view.far > h, 'The surface must not vanish when the player exceeds 8 km');
    assert.ok(view.far >= previous && view.far <= 50000000);
    assert.equal(view.maxTiles, 256);
    previous = view.far;
  }
  assert.equal(normal.far, 8000);
  assert.equal(spaceRenderOptions({...normal, maxTiles: 100}, 100000).maxTiles, 100);
});
