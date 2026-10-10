// One child process owns both localhost services. Minecraft owns its stdin pipe.
import { startTerrainServer } from './server.mjs';
if (process.env.RWMC_MANAGED === '1') {
  process.stdin.resume();
  process.stdin.on('end', () => process.exit(0));
}
// Start photos independently so a terrain provider outage cannot block them.
await import('./streetview-server.mjs');
try { await startTerrainServer(); }
catch { console.error('Terrain service could not start. Check map credentials in /earth setup and internet access.'); }
