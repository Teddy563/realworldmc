import test from 'node:test';
import assert from 'node:assert/strict';
import { coarsenToBudget, selectRenderTiles } from '../src/select.mjs';

test('LOD budget keeps distant coverage by substituting a parent, never slicing tiles away',()=>{
  const root={id:1,hasContent:false};
  const near={id:2,parent:root,hasContent:true,geometricError:20,distance:10};
  const far={id:3,parent:root,hasContent:true,geometricError:20,distance:6000};
  const leaves=[near,near,far,far].map((parent,i)=>({node:{id:10+i,parent,hasContent:true,geometricError:0,distance:parent.distance},dist:parent.distance}));
  const selected=coarsenToBudget(leaves,3,n=>n.distance);
  assert.deepEqual(selected.map(t=>t.node.id),[10,11,3]);
  for(const leaf of leaves){
    let covered=false;
    for(let node=leaf.node;node;node=node.parent)if(selected.some(t=>t.node===node))covered=true;
    assert.ok(covered,'Every previously selected surface keeps a drawable representative');
  }
  assert.equal(coarsenToBudget(leaves,4,n=>n.distance).length,4);
});

test('LOD budget preserves coverage when the provider has no drawable parents',()=>{
  const tiles=[1,2,3].map(id=>({node:{id,hasContent:true,parent:null},dist:id}));
  assert.equal(coarsenToBudget(tiles,1,()=>1).length,3);
});


test('flight look-ahead retains current coverage and is capped at 256 metres', async () => {
  const frame = {};
  const node = (id, x) => ({id, hasContent:true, geometricError:0, mayHaveChildren:false, expanded:true,
    _aabbFrame:frame, _aabb:{center:[x,0,0],radius:0}});
  const root = {...node(0,0),hasContent:false,expanded:true,mayHaveChildren:true};
  root.children=[node(1,0),node(2,300),node(3,-300),node(4,1000)];
  const client={getRoot:async()=>root};
  const opts={maxSse:16,sseFactor:1,far:150,fineRadius:0,targetError:1,maxTiles:10,falloff:100};
  const select=async ahead=>(await selectRenderTiles(client,frame,[0,0,0],{...opts,ahead})).tiles.map(t=>t.node.id);
  assert.deepEqual(await select([0,0]),[1]);
  assert.deepEqual(await select([256,0]),[1,2]);
  assert.deepEqual(await select([-256,0]),[1,3]);
  assert.deepEqual(await select([100000,0]),[1,2]);
});
