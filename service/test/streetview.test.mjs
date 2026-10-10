import { test } from 'node:test';
import assert from 'node:assert/strict';
import { nextLink } from '../src/streetview-navigation.mjs';
test('Street View chooses connected photos across north and relative to camera', () => {
  const links = [{pano:'north',heading:2},{pano:'east',heading:90},{pano:'south',heading:180}];
  assert.equal(nextLink(links,359).pano,'north');
  assert.equal(nextLink(links,0,90).pano,'east');
  assert.equal(nextLink(links,90,90).pano,'south');
  assert.equal(nextLink(links,0,-90),undefined);
  assert.equal(nextLink(null,0),undefined);
  assert.equal(nextLink([{pano:'bad',heading:NaN}],0),undefined);
});
