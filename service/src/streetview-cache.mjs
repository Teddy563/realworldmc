// Private in-memory response reuse. Missing freshness or revalidation directives mean a miss.
export function freshUntil(headers, now = Date.now()) {
  const control=headers.get('cache-control') ?? '';
  if(/(?:^|,)\s*(?:no-store|no-cache)(?:\s|,|=|$)/i.test(control) || headers.get('vary')==='*')return 0;
  const max=control.match(/(?:^|,)\s*max-age\s*=\s*"?(\d+)"?/i);
  if(!max)return 0;
  const date=Date.parse(headers.get('date')),age=Number(headers.get('age')??0);
  if(!Number.isFinite(age)||age<0)return 0;
  const elapsed=Math.max(age*1000,Number.isFinite(date)?Math.max(0,now-date):0);
  return now+Math.max(0,Number(max[1])*1000-elapsed);
}
export class StreetTileCache {
  constructor(limit=32*1024*1024,now=()=>Date.now()){this.limit=limit;this.now=now;this.entries=new Map();this.bytes=0;this.hits=0;}
  remove(key){const item=this.entries.get(key);if(item){this.bytes-=item.bytes.length;this.entries.delete(key);}}
  prune(){for(const [key,item] of this.entries)if(item.until<=this.now())this.remove(key);}
  get(key){this.prune();const item=this.entries.get(key);if(!item)return null;this.entries.delete(key);this.entries.set(key,item);this.hits++;return new Response(item.bytes,{headers:item.headers});}
  put(key,bytes,headers){this.remove(key);this.prune();const until=freshUntil(headers,this.now());if(until<=this.now() || bytes.length>this.limit)return;
    while(this.bytes+bytes.length>this.limit && this.entries.size)this.remove(this.entries.keys().next().value);
    this.entries.set(key,{bytes,headers:new Headers(headers),until});this.bytes+=bytes.length;
  }
  snapshot(){this.prune();return {cachedTiles:this.entries.size,cacheHits:this.hits};}
}
