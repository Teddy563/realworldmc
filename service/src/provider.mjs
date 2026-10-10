/** Resolve temporary Google tile access using the user's Cesium ion account. */
export async function configureProvider(cfg, fetchImpl = fetch) {
  if (!cfg.cesiumToken) return;
  if (cfg.apiKey && !cfg.geocodeKey) cfg.geocodeKey = cfg.apiKey;
  let pending;
  let lastAttempt = -Infinity;
  cfg.refreshEndpoint = () => {
    if (pending) return pending;
    if (Date.now() - lastAttempt < 10_000) return Promise.reject(new Error('Map access refresh is cooling down. Retry shortly.'));
    lastAttempt = Date.now();
    pending = (async () => {
      const asset = cfg.cesiumAsset ?? 2275207;
      if (!Number.isSafeInteger(Number(asset)) || Number(asset) <= 0) throw new Error('Invalid Cesium asset ID');
      const url = new URL(`https://api.cesium.com/v1/assets/${asset}/endpoint`);
      url.searchParams.set('access_token', cfg.cesiumToken);
      const res = await fetchImpl(url, { signal: AbortSignal.timeout(15_000), redirect: 'error' });
      if (!res.ok) throw new Error(`Cesium access failed (HTTP ${res.status}). Check your token, asset access, and account quota.`);
      const endpoint = await res.json();
      const root = new URL(endpoint.options?.url ?? endpoint.url);
      if (root.protocol !== 'https:' || root.hostname !== 'tile.googleapis.com' || root.port || root.username || root.password) {
        throw new Error('This Cesium asset is not a supported Google 3D tiles endpoint.');
      }
      const key = root.searchParams.get('key');
      if (!key) throw new Error('Cesium did not provide Google tile access. Check the selected asset.');
      root.searchParams.delete('key');
      // Runtime values only: never write a temporary key into config.json.
      cfg.apiKey = key;
      cfg.rootUrl = root.toString();
    })().finally(() => { pending = undefined; });
    return pending;
  };
  await cfg.refreshEndpoint();
}
