import jpeg from 'jpeg-js';
import {StreetTileCache} from './streetview-cache.mjs';
// Official Street View Tiles API; never use Cesium's temporary terrain credential here.

export function panoramaLayout(meta, maxWidth = 4096) {
  if (![1024,2048,4096].includes(maxWidth)) throw new Error("Photo width must be 1024, 2048 or 4096.");
  const {imageWidth:w,imageHeight:h,tileWidth:tw,tileHeight:th}=meta;
  if (![w,h,tw,th].every(Number.isSafeInteger) || w<1 || h<1 || w>32768 || h>16384 || tw<1 || th<1 || tw>1024 || th>1024) throw new Error('Invalid panorama dimensions.');
  // Smaller contributor panoramas reach native resolution before zoom 5.
  // Using 5 for every image crops the upper-left quarter of those photos.
  const nativeZoom=Math.max(1,Math.min(5,Math.ceil(Math.log2(w/tw))));
  let zoom=nativeZoom;
  while(zoom>1 && (Math.ceil(w/2**(nativeZoom-zoom))>maxWidth || Math.ceil(h/2**(nativeZoom-zoom))>maxWidth/2))zoom--;
  const width=Math.ceil(w/2**(nativeZoom-zoom)),height=Math.ceil(h/2**(nativeZoom-zoom));
  return {zoom,width,height,columns:Math.ceil(width/tw),rows:Math.ceil(height/th),tileWidth:tw,tileHeight:th};
}

export function copyTile(output,layout,tile,x,y) {
  const x0=x*layout.tileWidth,y0=y*layout.tileHeight;
  const width=Math.min(tile.width,layout.width-x0),height=Math.min(tile.height,layout.height-y0);
  for(let row=0;row<height;row++)output.set(tile.data.subarray(row*tile.width*4,(row*tile.width+width)*4),((y0+row)*layout.width+x0)*4);
}
export class NativeStreetView {
  constructor(key, fetchImpl = fetch, {maxWidth = 2048, usage = null} = {}) {
    if (![1024,2048,4096].includes(maxWidth)) throw new Error("Photo width must be 1024, 2048 or 4096.");
    this.cache=new StreetTileCache();this.usage=usage; this.key = key; this.fetch = fetchImpl; this.expiry = 0; this.maxWidth=maxWidth; this.retryAfter=0;
  }
  async session() {
    if (this.token && Date.now() < this.expiry) return this.token;
    if (!this.pending) this.pending = (async () => {
      const u = new URL('https://tile.googleapis.com/v1/createSession'); u.searchParams.set('key', this.key);
      this.usage?.record('session');
      const r = await this.fetch(u, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({mapType:'streetview',language:'en-US',region:'RO'}), signal:AbortSignal.timeout(15000), redirect:'error' });
      if (!r.ok) throw new Error(`Street View Tiles access failed (HTTP ${r.status}). Check Map Tiles API and key restrictions.`);
      const data = await r.json();
      if (!data.session) throw new Error('Street View returned no session.');
      this.token = data.session; this.expiry = Number(data.expiry)*1000-60000;
      return this.token;
    })().finally(() => { this.pending = null; });
    return this.pending;
  }
  async request(path, params) {
    const cacheKey=path.startsWith('tiles/')?path+':'+params.panoId:null;
    if(cacheKey){const cached=this.cache.get(cacheKey);if(cached)return cached;}
    if(Date.now()<this.retryAfter)throw new Error("Street View provider is cooling down after a quota or service error.");
    const u = new URL('https://tile.googleapis.com/v1/streetview/'+path);
    u.searchParams.set('key',this.key); u.searchParams.set('session',await this.session());
    for (const [k,v] of Object.entries(params)) u.searchParams.set(k,String(v));
    this.usage?.record(path.startsWith('tiles/')?'tiles':'metadata');
    const r = await this.fetch(u,{signal:AbortSignal.timeout(20000),redirect:'error'});
    if (!r.ok) {
      if(r.status===429 || r.status>=500){
        const retry=r.headers.get("Retry-After");
        const seconds=/^\d+$/.test(retry??"")?Number(retry):Math.max(0,(Date.parse(retry)-Date.now())/1000);
        this.retryAfter=Date.now()+Math.max(30,Math.min(86400,Number.isFinite(seconds)?seconds:30))*1000;
      }
      if(r.status===401 || r.status===403) this.expiry=0; throw Object.assign(new Error(`Street View request failed (HTTP ${r.status}). Check coverage, quota and Map Tiles API access.`),{status:r.status}); }
    if(cacheKey){
      const bytes=Buffer.from(await r.arrayBuffer());if(bytes.length>4_000_000)throw new Error('Tile exceeds the image limit.');
      this.cache.put(cacheKey,bytes,r.headers);return new Response(bytes,{headers:r.headers});
    }
    return r;
  }
  async metadata(q) {
    let params;
    if(q.has('pano')) { const p=q.get('pano'); if(!/^[\w-]{1,256}$/.test(p)) throw new Error('Invalid panorama ID.'); params={panoId:p}; }
    else {
      const lat=Number(q.get('lat')),lng=Number(q.get('lng'));
      if(!q.get('lat')?.trim() || !q.get('lng')?.trim() || !Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat)>90 || Math.abs(lng)>180) throw new Error('Invalid latitude/longitude.');
      const radius=q.has('radius')?Number(q.get('radius')):50;
      if(!Number.isInteger(radius) || radius<1 || radius>1000)throw new Error('Search radius must be between 1 and 1000 metres.');
      params={lat,lng,radius};
    }
    const meta=await (await this.request('metadata',params)).json();
    if(meta.imageWidth && meta.imageHeight && meta.tileWidth && meta.tileHeight){const layout=panoramaLayout(meta,this.maxWidth);meta.rwmcTileCount=layout.columns*layout.rows;}return meta;
  }
  async panorama(q) {
    const pano=q.get('pano');
    if(!/^[\w-]{1,256}$/.test(pano??''))throw new Error('Invalid panorama ID.');
    // Coalesce assemblies. Only fresh provider-permitted tiles remain in the private memory cache.
    if(this.assembly) {
      if(this.assembly.pano===pano)return this.assembly.promise;
      throw new Error('Another panorama is loading. Retry shortly.');
    }
    const promise=(async()=>{
      const meta=await this.metadata(new URLSearchParams({pano}));
      const layout=panoramaLayout(meta,this.maxWidth),output=Buffer.alloc(layout.width*layout.height*4);
      const count=layout.columns*layout.rows;let next=0;
      async function worker(provider) {
        while(next<count){
          const index=next++,x=index%layout.columns,y=Math.floor(index/layout.columns);
          const r=await provider.request(`tiles/${layout.zoom}/${x}/${y}`,{panoId:pano});
          const bytes=Buffer.from(await r.arrayBuffer());
          if(bytes.length>4_000_000)throw new Error('Tile exceeds the image limit.');
          const tile=jpeg.decode(bytes,{useTArray:true,maxResolutionInMP:2,maxMemoryUsageInMB:32});
          if(tile.width!==layout.tileWidth || tile.height!==layout.tileHeight)throw new Error('Unexpected tile dimensions.');
          copyTile(output,layout,tile,x,y);
        }
      }
      // Wait for every worker, including on error, before releasing the concurrency limit.
      const results=await Promise.allSettled(Array.from({length:3},()=>worker(this)));
      const failed=results.find(r=>r.status==='rejected');if(failed)throw failed.reason;
      return jpeg.encode({data:output,width:layout.width,height:layout.height},90).data;
    })();
    this.assembly={pano,promise};
    try{return await promise;}finally{this.assembly=null;}
  }
}
