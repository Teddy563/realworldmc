import test from 'node:test';
import assert from 'node:assert/strict';
import { boundedRenderOverrides } from '../src/server.mjs';
test('render quality accepts only bounded finite settings', () => {
  assert.deepEqual(boundedRenderOverrides({fineRadius:-1,far:1e99,maxTiles:1e9,maxSse:0,apiKey:'never'}), {fineRadius:96,far:24000,maxTiles:2400,maxSse:4});
  assert.deepEqual(boundedRenderOverrides({far:NaN,maxTiles:Infinity,maxSse:'8'}),{});
  assert.deepEqual(boundedRenderOverrides({fineRadius:160,far:8000,maxTiles:900,maxSse:12}),{fineRadius:160,far:8000,maxTiles:900,maxSse:12});
});
