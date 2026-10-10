import test from 'node:test';
import assert from 'node:assert/strict';
import { configureProvider } from '../src/provider.mjs';
import { TileClient } from '../src/tiles.mjs';

test('Cesium resolution keeps geocoding credentials separate and coalesces refresh', async () => {
  const cfg = { cesiumToken: 'SYNTHETIC_TOKEN', apiKey: 'OWN_GOOGLE_KEY' };
  let calls = 0, firstRequest;
  const fetcher = async (url, options) => {
    calls++; firstRequest = url;
    assert.ok(options.signal);
    return { ok: true, json: async () => ({ options: { url: 'https://tile.googleapis.com/v1/3dtiles/root.json?key=TEMPORARY' } }) };
  };
  const initializing = configureProvider(cfg, fetcher);
  const same = cfg.refreshEndpoint();
  await Promise.all([initializing, same]);
  assert.equal(calls, 1);
  assert.equal(cfg.geocodeKey, 'OWN_GOOGLE_KEY');
  assert.equal(cfg.apiKey, 'TEMPORARY');
  assert.equal(new URL(cfg.rootUrl).searchParams.has('key'), false);
  assert.equal(firstRequest.searchParams.get('access_token'), 'SYNTHETIC_TOKEN');
  await assert.rejects(cfg.refreshEndpoint(), /cooling down/);
});

test('provider errors do not expose upstream credentials or error bodies', async () => {
  await assert.rejects(configureProvider({ cesiumToken: 'SECRET' }, async () => ({ ok: false, status: 403,
    text: async () => 'SECRET' })), error => error.message.includes('403') && !error.message.includes('SECRET'));
});

test('unsupported provider destinations are rejected before credentials can be reused', async () => {
  await assert.rejects(configureProvider({ cesiumToken: 'SYNTHETIC' }, async () => ({ ok: true,
    json: async () => ({ url: 'https://example.invalid/root.json?key=BAD' }) })), /not a supported/);
});

test('tile authentication failure refreshes access and resets selection before retry', async t => {
  let refreshes = 0;
  const cfg = { rootUrl: 'https://tile.googleapis.com/root.json', apiKey: 'OLD' };
  cfg.refreshEndpoint = async () => { refreshes++; cfg.apiKey = 'NEW'; };
  const client = new TileClient(cfg);
  t.mock.method(globalThis, 'fetch', async url => new URL(url).searchParams.get('key') === 'OLD'
    ? new Response('private provider error', { status: 403 }) : new Response(new Uint8Array([1, 2, 3])));
  await assert.rejects(client.fetchBytes(cfg.rootUrl), /HTTP 403/);
  assert.equal(refreshes, 1);
  assert.equal(client.generation, 1);
  assert.deepEqual(await client.fetchBytes(cfg.rootUrl), new Uint8Array([1, 2, 3]));
});


test('400 on a session tile discards stale URLs but a root 400 does not reset', async t => {
  const cfg = { rootUrl: 'https://tile.googleapis.com/v1/3dtiles/root.json', apiKey: 'SYNTHETIC' };
  const client = new TileClient(cfg);
  client.root = { stale: true };
  client.nodes.set(1, client.root);
  t.mock.method(globalThis, 'fetch', async () => new Response('private upstream detail', {status:400}));
  await assert.rejects(client.fetchBytes('https://tile.googleapis.com/v1/3dtiles/tile.json?session=OLD'), /tileset was reset; retry/);
  assert.equal(client.root, null);
  assert.equal(client.nodes.size, 0);
  assert.equal(client.generation, 1);
  await assert.rejects(client.fetchBytes(cfg.rootUrl), /HTTP 400/);
  assert.equal(client.generation, 1);
});
