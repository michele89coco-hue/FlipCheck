const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const editions = require('../src/main/assets/editions.js');
const base = {is_pokemon:true,language:'English',set_name:'Base Set',card_type:'pokemon',
  first_edition_stamp:'present',stamp_image:1,stamp_location:'left below artwork',stamp_text:'1 EDITION',
  artwork_shadow:'absent',shadow_image:1,shadow_location:'right edge of artwork frame',
  copyright_text:'©1995,96,98 Nintendo Creatures GAMEFREAK ©1999 Wizards',copyright_image:1,slab_text:'',slab_image:0};
const identity = {kind:'card',brand:'Pokemon',family:'Base Set',model:'Alakazam #1/102',title:'Alakazam',variant:'Holo',model_confidence:95,market_ready:true,normalized_query:'Pokemon Base Set Alakazam #1/102 Holo'};
test('1st Edition and Shadowless remain separate confirmed observations',()=>{
  const out = editions.apply(identity,base,1);
  assert.equal(out.variant,'Holo · 1st Edition · Shadowless');
  assert.equal(out.model,identity.model); assert.equal(out.model_confidence,95); assert.equal(out.market_ready,true);
});
test('Shadowless without a stamp is not called 1st Edition or Unlimited',()=>{
  const out=editions.evaluate({...base,first_edition_stamp:'absent',stamp_text:''},1);
  assert.deepEqual(out.labels,['Shadowless']); assert.equal(out.complete,true);
});
test('a visible shadow and absent visible stamp produce Unlimited',()=>{
  assert.deepEqual(editions.evaluate({...base,first_edition_stamp:'absent',stamp_text:'',artwork_shadow:'present'},1).labels,['Unlimited']);
});
test('a stamped shadowed card is not forced to Shadowless',()=>{
  const out=editions.evaluate({...base,artwork_shadow:'present'},1);
  assert.deepEqual(out.labels,['1st Edition']); assert.equal(out.shadow,'present');
});
test('an unreadable stamp is not absence and preserves core identity',()=>{
  const out=editions.apply(identity,{...base,first_edition_stamp:'unclear',stamp_text:''},1);
  assert.equal(out.printing_check.stamp,'unclear'); assert.equal(out.model,identity.model);
  assert.equal(out.model_confidence,95); assert.equal(out.market_ready,false);
});
test('missing frame or copyright cannot prove Shadowless',()=>{
  for (const change of [{shadow_location:''},{copyright_text:'©1999'},{copyright_image:0},{copyright_text:'©1995,96,98 Nintendo ©1999-2000 Wizards'}]) {
    assert.equal(editions.evaluate({...base,...change},1).shadow,'unclear');
  }
});
test('Italian, Japanese, Jungle and modern reprint cards do not inherit English Base rules',()=>{
  for(const change of [{language:'Italiano'},{language:'Japanese'},{set_name:'Jungle'},{set_name:'Base Set 2'},{set_name:'Evolutions'}]) {
    const out=editions.evaluate({...base,...change},1); assert.equal(out.shadow,'not_applicable'); assert.deepEqual(out.labels,['1st Edition']);
  }
});
test('Trainer and Energy first edition stamps use their actual observed location',()=>{
  for(const [card_type,stamp_location] of [['trainer','bottom left'],['energy','top right']]) {
    const out=editions.evaluate({...base,card_type,stamp_location},1);
    assert.equal(out.stamp,'present'); assert.equal(out.shadow,'unclear');
  }
});
test('a stage number 1 does not pass the stamp check',()=>{
  assert.equal(editions.evaluate({...base,stamp_text:'Stage 1',stamp_location:'top left stage'},1).stamp,'unclear');
});
test('indices outside uploaded photos do not count as physical evidence',()=>{
  const out=editions.evaluate({...base,stamp_image:3,shadow_image:3,slab_image:3,slab_text:'1st Edition'},2);
  assert.equal(out.stamp,'unclear');assert.equal(out.shadow,'unclear');assert.equal(out.slab,'');
});
test('slab claims are displayed without becoming observed stamps',()=>{
  const out=editions.evaluate({...base,first_edition_stamp:'unclear',slab_text:'1st Edition Shadowless',slab_image:1},1);
  assert.equal(out.slab,'1st Edition Shadowless');assert.equal(out.stamp,'unclear'); assert.equal(out.complete,false);
});
test('contradictory slab and card keep printing unconfirmed',()=>{
  const out=editions.apply(identity,{...base,artwork_shadow:'present',slab_text:'SHADOWLESS',slab_image:1},1);
  assert.equal(out.printing_check.contradiction,true);assert.equal(out.market_ready,false); assert.equal(out.model,identity.model);
});
test('web wording cannot override the saved physical edition observations',()=>{
  const web={...identity,model:'Alakazam #1/102 Shadowless 1st Edition',variant:'Holo 1st Edition Shadowless',normalized_query:'Alakazam Shadowless 1st Edition'};
  const out=editions.apply(web,{...base,first_edition_stamp:'absent',stamp_text:'',artwork_shadow:'present'},1);
  assert.equal(out.variant,'Holo · Unlimited');assert.doesNotMatch(out.model,/Shadowless|1st/);assert.equal(out.normalized_query,'Alakazam Unlimited');
});
test('sports, objects and other TCG keep v26 results byte-for-byte',()=>{
  for(const kind of ['card','object']) {
    const original={...identity,kind,brand:'SkyBox',family:'Metal Universe',model:'Kobe Bryant #81',variant:'Base'};
    assert.strictEqual(editions.apply(original,null,2),original);
    assert.strictEqual(editions.apply(original,{...base,is_pokemon:false},2),original);
  }
});
test('applying printing policy twice neither duplicates labels nor modifies input',()=>{
  const saved=JSON.stringify(identity);const a=editions.apply(identity,base,1);const b=editions.apply(a,base,1);
  assert.deepEqual(a,b);assert.equal(JSON.stringify(identity),saved);
});
test('the entire original v26 engine script remains identical',()=>{
  const extract=s=>s.match(/<script>([\s\S]*?)<\/script>/)[1];
  const before=fs.readFileSync(path.join(__dirname,'../baseline/index.html'),'utf8');
  const after=fs.readFileSync(path.join(__dirname,'../src/main/assets/index.html'),'utf8');
  assert.equal(extract(after),extract(before));
});
