import http from 'node:http';
import {fileURLToPath} from 'node:url';
import {UsageMeter} from './usage.mjs';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { NativeStreetView } from './streetview-native.mjs';

const port = Number(process.env.RWMC_STREETVIEW_PORT ?? 8766);
// Intentionally separate from the terrain service and its private provider credentials.
let config = {};
try { config = JSON.parse(await readFile(process.env.RWMC_DATA_DIR ? path.join(process.env.RWMC_DATA_DIR, 'streetview.config.json') : new URL('../streetview.config.json', import.meta.url), 'utf8')); }
catch (error) { if (error.code !== 'ENOENT') throw new Error('Could not read streetview.config.json. Repair its JSON before retrying setup.'); }
const clean = value => typeof value === 'string' && !value.startsWith('YOUR_') ? value.trim() : '';
const key = clean(process.env.REALWORLDMC_STREETVIEW_BROWSER_KEY ?? config.browserKey);
// The native Map Tiles key never goes to the browser /settings endpoint.
const tilesKey = clean(process.env.RWMC_STREETVIEW_TILES_KEY ?? ('tilesKey' in config ? config.tilesKey : key));
const assets = new Map([
  ['/', ['../preview/streetview.html', 'text/html']],
  ['/streetview.mjs', ['../preview/streetview.mjs', 'text/javascript']],
  ['/navigation.mjs', ['./streetview-navigation.mjs', 'text/javascript']],
]);
const usage=new UsageMeter(path.join(process.env.RWMC_DATA_DIR ?? fileURLToPath(new URL('../',import.meta.url)), 'streetview-usage.json'));
process.once('exit',()=>usage.flush());
const native = tilesKey ? new NativeStreetView(tilesKey, fetch, {maxWidth:config.maxPhotoWidth ?? 2048,usage}) : null;
http.createServer(async (req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  if (req.headers.host !== `127.0.0.1:${port}` && req.headers.host !== `localhost:${port}`) {
    res.writeHead(403).end(); return;
  }
  if (req.method !== 'GET') { res.writeHead(405).end(); return; }
  const url = new URL(req.url, 'http://127.0.0.1:8766');
  if(url.pathname==='/usage'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify({...usage.snapshot(),...(native?.cache.snapshot()??{}),configured:!!tilesKey}));return;}
  if (url.pathname === '/health') {
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ok:true, name:'RealWorldMC Street View', instanceId:process.env.RWMC_INSTANCE_ID ?? '', hasKey:!!tilesKey})); return;
  }
  if (['/native/metadata','/native/panorama'].includes(url.pathname)) {
    if (!native) { res.writeHead(503, {'Content-Type':'application/json'}).end(JSON.stringify({error:'Add an optional Street View key in /earth setup.'})); return; }
    try {
      if(url.pathname.endsWith('/metadata')) { res.setHeader('Content-Type','application/json'); res.end(JSON.stringify(await native.metadata(url.searchParams))); }
      else { res.setHeader('Content-Type','image/jpeg'); res.end(await native.panorama(url.searchParams)); }
    } catch(error) {
      const missing=url.pathname.endsWith('/metadata') && error.status===404;
      res.writeHead(missing?404:503,{'Content-Type':'application/json'}).end(JSON.stringify({error:missing?'No Street View photo found in the search area.':'Street View could not load. Check coverage, Map Tiles API, key restrictions and quota.'}));
    }
    return;
  }
  if (req.url === '/settings') {
    res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ browserKey: key })); return;
  }
  const asset = assets.get(req.url);
  if (!asset) { res.writeHead(404).end(); return; }
  try { res.setHeader('Content-Type', asset[1] + '; charset=utf-8'); res.end(await readFile(new URL(asset[0], import.meta.url))); }
  catch { res.writeHead(500).end('Could not load viewer.'); }
}).listen(port, '127.0.0.1', () => console.log(`RealWorldMC photo viewer: http://127.0.0.1:${port}`));
