import {test} from 'node:test';
import assert from 'node:assert/strict';
import {StreetTileCache,freshUntil} from '../src/streetview-cache.mjs';
import {NativeStreetView} from '../src/streetview-native.mjs';
test('private cache respects freshness, age, prohibitions and bounded eviction',()=>{
 let now=100000;const cache=new StreetTileCache(6,()=>now);
 const headers=new Headers({'cache-control':'private, max-age=10, must-revalidate','age':'2'});
 assert.equal(freshUntil(headers,now),108000);
 for(const value of ['no-store, max-age=60','no-cache, max-age=60','max-age=0','public']){cache.put('x',Buffer.from('abc'),new Headers({'cache-control':value}));assert.equal(cache.get('x'),null);}
 cache.put('a',Buffer.from('abc'),headers);cache.put('b',Buffer.from('def'),headers);assert.ok(cache.get('a'));
 cache.put('c',Buffer.from('ghi'),headers);assert.equal(cache.get('b'),null);assert.equal(cache.snapshot().cachedTiles,2);
 now=108000;assert.equal(cache.get('a'),null);assert.equal(cache.snapshot().cachedTiles,0);
});
test('backtracking reuses fresh tiles without adding outbound usage; expiry fetches again',async()=>{
 let requests=0;const events=[];
 const provider=new NativeStreetView('fixture',async u=>{
  if(u.pathname.endsWith('createSession'))return Response.json({session:'fixture',expiry:Date.now()/1000+3600});
  requests++;return new Response('tile',{headers:{'cache-control':'private, max-age=60'}});
 },{usage:{record:k=>events.push(k)}});
 let now=Date.now();provider.cache.now=()=>now;
 for(let i=0;i<2;i++)assert.equal(await (await provider.request('tiles/2/0/0',{panoId:'A'})).text(),'tile');
 assert.equal(requests,1);assert.equal(events.filter(x=>x==='tiles').length,1);assert.equal(provider.cache.snapshot().cacheHits,1);
 now+=61000;await provider.request('tiles/2/0/0',{panoId:'A'});assert.equal(requests,2);
 await provider.request('tiles/2/0/0',{panoId:'B'});assert.equal(requests,3);
});
