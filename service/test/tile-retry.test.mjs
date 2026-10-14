import test from 'node:test';
import assert from 'node:assert/strict';
import { TileClient } from '../src/tiles.mjs';

test('failed tile requests must not become cached empty collision terrain', async () => {
  const client = new TileClient({});
  let requests = 0;
  client.fetchBytes = async () => {
    if (++requests === 1) throw Object.assign(new Error('temporary missing tile'), { status: 404 });
    return new Uint8Array();
  };
  const mesh = { primitives: [{ vertices: 'fixture' }], copyright: 'fixture' };
  client.decodeGlb = async () => mesh;
  const node = { contentUrl: 'https://example.test/tile.glb' };
  await assert.rejects(client.getMesh(node), { status: 404 });
  assert.equal(client.decoded.get(node.contentUrl), undefined);
  assert.equal(await client.getMesh(node), mesh);
  assert.equal(await client.getMesh(node), mesh);
  assert.equal(requests, 2, 'retry failure, then reuse the successful mesh');
});
