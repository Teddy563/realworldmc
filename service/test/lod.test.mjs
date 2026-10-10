import test from 'node:test';
import assert from 'node:assert/strict';
import { coarsenToBudget } from '../src/select.mjs';

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
