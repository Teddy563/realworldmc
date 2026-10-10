import { nextLink } from '/navigation.mjs';
const status = document.querySelector('#status');
const buttons = [...document.querySelectorAll('[data-offset]')];
let panorama, service, moving = false, generation = 0;
function update() {
  for (const b of buttons) b.disabled = moving || !panorama?.getVisible() || !nextLink(panorama.getLinks(), panorama.getPov().heading, Number(b.dataset.offset));
}
async function move(offset) {
  if (moving || !panorama?.getVisible()) return;
  const link = nextLink(panorama.getLinks(), panorama.getPov().heading, offset);
  if (!link) { status.textContent = 'No connected photo in that direction. Turn or choose another arrow.'; return; }
  await load({ pano: link.pano });
}
async function load(request) {
  const id = ++generation; moving = true; update(); status.textContent = 'Loading panorama…';
  try {
    const { data } = await service.getPanorama(request);
    if (id !== generation) return;
    panorama.setPano(data.location.pano); panorama.setVisible(true);
    status.textContent = data.location.description || 'Drag to look around; use arrows to move.';
  } catch { if (id === generation) status.textContent = 'No panorama available, or access failed. Check coverage, billing and browser key restrictions.'; }
  finally { if (id === generation) { moving = false; update(); } }
}
window.gm_authFailure = () => { status.textContent = 'Google rejected the browser key. Check Maps JavaScript API, billing and referrer restrictions.'; };
window.initStreetView = () => {
  panorama = new google.maps.StreetViewPanorama(document.querySelector('#pano'), { visible: false, linksControl: true, clickToGo: false, enableCloseButton: false, motionTracking: false, motionTrackingControl: false });
  service = new google.maps.StreetViewService();
  for (const event of ['links_changed', 'pov_changed', 'visible_changed']) panorama.addListener(event, update);
  document.querySelector('#open').disabled = false;
  status.textContent = 'Enter coordinates to find street photos within 50 metres.';
  const params = new URLSearchParams(window.location.hash.slice(1));
  if (params.has('lat') || params.has('lng')) {
    const latText = params.get('lat'), lngText = params.get('lng');
    const lat = Number(latText), lng = Number(lngText);
    if (!latText?.trim() || !lngText?.trim() || !Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
      status.textContent = 'Invalid coordinates in the viewer link. Enter latitude and longitude below.';
      return;
    }
    document.querySelector('#lat').value = latText;
    document.querySelector('#lng').value = lngText;
    load({ location: { lat, lng }, radius: 50 });
  }
};
document.querySelector('#location').addEventListener('submit', e => {
  e.preventDefault();
  const lat = Number(document.querySelector('#lat').value), lng = Number(document.querySelector('#lng').value);
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat)>90 || Math.abs(lng)>180) return;
  load({ location: { lat, lng }, radius: 50 });
});
for (const b of buttons) b.addEventListener('click', () => move(Number(b.dataset.offset)));
window.addEventListener('keydown', e => {
  if (['INPUT','TEXTAREA','SELECT','BUTTON'].includes(e.target.tagName) || e.altKey || e.ctrlKey || e.metaKey) return;
  const offset = { ArrowUp:0, ArrowDown:180, ArrowLeft:-90, ArrowRight:90 }[e.key];
  if (offset === undefined) return;
  e.preventDefault(); e.stopImmediatePropagation(); if (!e.repeat) move(offset);
}, true);
try {
  const { browserKey } = await (await fetch('/settings')).json();
  const script = document.createElement('script');
  script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(browserKey)}&callback=initStreetView&loading=async&v=quarterly&language=en`;
  script.onerror = () => { status.textContent = 'Could not load Google Street View. Check your connection.'; };
  document.head.append(script);
} catch { status.textContent = 'Could not load viewer settings. Restart the local Street View server.'; }
