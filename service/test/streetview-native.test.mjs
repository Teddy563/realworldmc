import {test} from 'node:test';
import assert from 'node:assert/strict';
import {NativeStreetView,panoramaLayout,copyTile} from '../src/streetview-native.mjs';
test('native viewer coalesces sessions and bounds metadata and panorama IDs',async()=>{
 const calls=[];
 const provider=new NativeStreetView('test-key',async(url)=>{
   calls.push(new URL(url));
   if(url.pathname.endsWith('createSession'))return Response.json({session:'test-session',expiry:Date.now()/1000+3600});
   return Response.json({panoId:'example'});
 });
 await Promise.all([provider.session(),provider.session()]);assert.equal(calls.length,1);
 await provider.metadata(new URLSearchParams({lat:'44.428582856832655',lng:'26.10111463938365'}));
 assert.equal(calls[1].searchParams.get('lat'),'44.428582856832655');
 await assert.rejects(provider.metadata(new URLSearchParams({lat:'NaN',lng:'0'})));
 await assert.rejects(provider.panorama(new URLSearchParams({pano:'../invalid'})));
 await assert.rejects(provider.panorama(new URLSearchParams()));
});
test('native provider errors never expose provider body or credentials',async()=>{
 const provider=new NativeStreetView('private-key',async()=>new Response('private-key',{status:403}));
 await assert.rejects(provider.session(),e=>e.message.includes('403')&&!e.message.includes('private-key'));
});
test('nearby metadata sends the bounded search radius and preserves provider snapping',async()=>{
 const calls=[];
 const provider=new NativeStreetView('test-key',async(url)=>{
   calls.push(new URL(url));
   if(url.pathname.endsWith('createSession'))return Response.json({session:'test-session',expiry:Date.now()/1000+3600});
   return Response.json({panoId:'nearest-road',lat:44.4286,lng:26.1012});
 });
 const meta=await provider.metadata(new URLSearchParams({lat:'44.428582856832655',lng:'26.10111463938365',radius:'500'}));
 assert.equal(calls.at(-1).searchParams.get('radius'),'500');
 assert.equal(meta.panoId,'nearest-road');assert.equal(meta.lat,44.4286);
 await provider.metadata(new URLSearchParams({lat:'44.4',lng:'26.1'}));
 assert.equal(calls.at(-1).searchParams.get('radius'),'50');
 const count=calls.length;
 for(const radius of ['','0','1001','NaN','Infinity','1.5'])await assert.rejects(provider.metadata(new URLSearchParams({lat:'44.4',lng:'26.1',radius})));
 assert.equal(calls.length,count,'Invalid radii must not reach the provider');
 await provider.metadata(new URLSearchParams({pano:'nearest-road'}));
 assert.equal(calls.at(-1).searchParams.get('panoId'),'nearest-road');
 assert.equal(calls.at(-1).searchParams.has('radius'),false,'Linked photo navigation must keep its exact panorama ID');
});
test('absent nearby panorama is distinguishable without exposing the provider error body',async()=>{
 const provider=new NativeStreetView('private-key',async(url)=>url.pathname.endsWith('createSession')
   ?Response.json({session:'test-session',expiry:Date.now()/1000+3600}):new Response('private-key details',{status:404}));
 await assert.rejects(provider.metadata(new URLSearchParams({lat:'44',lng:'26',radius:'500'})),e=>e.status===404 && !e.message.includes('private-key'));
});
test('high-resolution panorama uses source tiles within bounded memory',()=>{
 const layout=panoramaLayout({imageWidth:13312,imageHeight:6656,tileWidth:512,tileHeight:512});
 assert.deepEqual(layout,{zoom:3,width:3328,height:1664,columns:7,rows:4,tileWidth:512,tileHeight:512});
 assert.equal(panoramaLayout({imageWidth:16384,imageHeight:8192,tileWidth:512,tileHeight:512}).width,4096);
 assert.equal(panoramaLayout({imageWidth:2048,imageHeight:1024,tileWidth:512,tileHeight:512}).zoom,5);
 assert.throws(()=>panoramaLayout({imageWidth:1e9,imageHeight:1e9,tileWidth:512,tileHeight:512}));
});
test('stitching crops padded right and bottom tiles instead of stretching the panorama',()=>{
 const output=Buffer.alloc(3*3*4);
 const layout={width:3,height:3,tileWidth:2,tileHeight:2};
 const tile={width:2,height:2,data:Uint8Array.from([10,11,12,255,20,21,22,255,30,31,32,255,40,41,42,255])};
 copyTile(output,layout,tile,0,0);copyTile(output,layout,tile,1,1);
 assert.deepEqual([...output.subarray(0,8)],[10,11,12,255,20,21,22,255]);
 assert.deepEqual([...output.subarray(32,36)],[10,11,12,255]);
 assert.equal(output[8],0); // no wrap into the previous row's padding
});
