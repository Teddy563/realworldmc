export function nextLink(links, heading, offset = 0) {
  const target = ((heading + offset) % 360 + 360) % 360;
  return (links ?? []).filter(l => l.pano && Number.isFinite(l.heading))
    .map(link => ({ link, angle: Math.abs(((link.heading - target + 540) % 360) - 180) }))
    .filter(x => x.angle <= 60).sort((a, b) => a.angle - b.angle)[0]?.link;
}
