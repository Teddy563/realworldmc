import fs from 'node:fs';
import path from 'node:path';

// Local attempted requests, never an account balance. Separate files avoid cross-service writes.
export class UsageMeter {
  constructor(file, now = () => new Date()) {
    this.file=file;this.now=now;this.data={};this.valid=true;
    try { this.data=JSON.parse(fs.readFileSync(file,'utf8')); }
    catch(e) { if(e.code!=='ENOENT')this.valid=false; }
    if(this.data.month && ![this.data.requests,this.data.tiles,this.data.roots].every(n=>Number.isSafeInteger(n) && n>=0))this.valid=false;
    this.rollover();
  }
  rollover() {
    const parts=new Intl.DateTimeFormat('en-US',{timeZone:'America/Los_Angeles',year:'numeric',month:'2-digit'}).formatToParts(this.now());
    const month=parts.find(p=>p.type==='year').value+'-'+parts.find(p=>p.type==='month').value;
    if(this.data.month!==month)this.data={month,started:this.now().toISOString(),requests:0,tiles:0,roots:0};
  }
  record(kind) {
    this.rollover();this.data.requests++;
    if(kind==='tiles' || kind==='roots')this.data[kind]++;
    if(!this.timer)this.timer=setTimeout(()=>{this.timer=null;this.flush();},250).unref();
  }
  flush() {
    clearTimeout(this.timer);this.timer=null;
    if(!this.valid)return; // Do not overwrite a damaged history with a misleading zero.
    try { fs.mkdirSync(path.dirname(this.file),{recursive:true});fs.writeFileSync(this.file+'.tmp',JSON.stringify(this.data));fs.renameSync(this.file+'.tmp',this.file); }
    catch { this.valid=false; }
  }
  snapshot() { this.rollover();return {...this.data,available:this.valid,scope:'local installation; excludes earlier/unrecorded usage and other apps',freeStreetTiles:100000,free3dRoots:1000}; }
}
