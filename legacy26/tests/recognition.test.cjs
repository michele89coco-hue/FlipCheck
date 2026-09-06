const {test}=require('node:test');
const assert=require('node:assert/strict');
const fixes=require('../src/main/assets/recognition-fixes.js');
const editions=require('../src/main/assets/editions.js');
const fixtures=require('./fixtures/build159-vision.json');
const copy=x=>structuredClone(x);
test('real Machamp and Vileplume readings retain completed identity and physical editions',()=>{
  for(const name of ['machamp','vileplume']) {
    const x=copy(fixtures[name]);const before=JSON.stringify(x);const result=fixes.guard(x);
    assert.equal(result.card_consistency.status,'consistent');assert.equal(result.market_ready,true);
    assert.equal(result.model_confidence,x.model_confidence);assert.equal(result.model,x.model);
    const printed=editions.apply(result,fixes.printingFor(result,x.pokemon_printing),1);
    assert.equal(printed.market_ready,true);assert.match(printed.variant,/1st Edition/);
    if(name==='machamp')assert.match(printed.variant,/Shadowless/);
    assert.equal(JSON.stringify(x),before);
  }
});
test('real Politoed false Neo Genesis identity cannot be confirmed and discovers alternatives',()=>{
  const result=fixes.guard(copy(fixtures.politoed));
  assert.equal(result.market_ready,false);assert.equal(result.model_verified,false);
  assert.equal(result.card_consistency.status,'conflict');assert.match(result.card_consistency.conflicts.join(' '),/Pichu/);
  assert.ok(result.card_consistency.alternatives.some(c=>c.set==='Skyridge'&&c.number==='H23'));
});
test('web cannot repair a conflict by replacing the photographed subject or HP',()=>{
  const x={...copy(fixtures.politoed),model:'Pichu 12/111',title:'Pichu',family:'Neo Genesis',
    distinctive_terms:['30 HP'],visual_fingerprint:'Pichu 30 HP',layout_signature:[],market_ready:true};
  assert.equal(fixes.guard(x,fixtures.politoed).market_ready,false);
});
test('consistent catalogue resolution clears the false set conflict without inventing a stamp',()=>{
  const x={...copy(fixtures.politoed),model:'Politoed H23/H32',family:'Skyridge',market_ready:true};
  const out=fixes.guard(x,fixtures.politoed);assert.equal(out.card_consistency.status,'consistent');
  const printing=fixes.printingFor(out,fixtures.politoed.pokemon_printing);
  assert.equal(printing.first_edition_stamp,'not_applicable');
  assert.equal(editions.apply(out,printing,1).market_ready,true);
});
test('the same catalogue rule rejects different wrong names, HP, numbers and attacks',()=>{
  for(const change of [{model:'Vileplume 1/64'},{distinctive_terms:['120 HP'],visual_fingerprint:'Vileplume 120 HP',layout_signature:[]},
    {model:'Vileplume 15/102'},{layout_signature:[{term:'Seismic Toss',relation:'nome attacco'}]}]) {
    assert.equal(fixes.guard({...copy(fixtures.vileplume),...change}).market_ready,false);
  }
});
test('unknown sets, unsupported translations and non-Pokémon retain their previous behavior',()=>{
  for(const change of [{family:'Future unindexed set'},{pokemon_printing:{...fixtures.vileplume.pokemon_printing,language:'Japanese'}},
    {pokemon_printing:null},{kind:'object'}]) {
    const x={...copy(fixtures.vileplume),...change};assert.strictEqual(fixes.guard(x),x);
  }
  const translated={...copy(fixtures.vileplume),model:'Charizard Oscuro 4/82',title:'Charizard Oscuro',family:'Team Rocket',
    pokemon_printing:{...fixtures.vileplume.pokemon_printing,language:'Italiano',set_name:'Team Rocket'}};
  assert.strictEqual(fixes.guard(translated),translated);
});
test('a claimed visible first edition on a Western e-card set remains a conflict',()=>{
  const x={...copy(fixtures.politoed),model:'Politoed H23/H32',family:'Skyridge',
    pokemon_printing:{...fixtures.politoed.pokemon_printing,first_edition_stamp:'present'}};
  assert.equal(fixes.guard(x).market_ready,false);
});
test('real remote reading suppresses an already supplied back and battery photo request',()=>{
  const x=copy(fixtures.remote);const out=fixes.removeRedundantPhoto(x,x);
  assert.equal(out.next_photo_request,null);assert.deepEqual(out.photo_request_suppressed.identifiers,x.identifier_hints);
  assert.equal(out.market_ready,false);assert.equal(out.model,'');assert.ok(x.next_photo_request);
});
test('genuinely unreadable codes and unphotographed backs retain a targeted request',()=>{
  const x=copy(fixtures.remote);
  assert.strictEqual(fixes.removeRedundantPhoto(x,{...x,evidence:['Il fronte mostra PAIR e Netflix.']}),x);
  const unclear={...x,missing_information:['Codice YKF423-001 illeggibile nella parte finale.']};
  assert.strictEqual(fixes.removeRedundantPhoto(unclear,x),unclear);
  assert.strictEqual(fixes.removeRedundantPhoto(fixtures.box,fixtures.box),fixtures.box);
  for(const request of ['Foto fronte e retro del telecomando.','Foto del barcode sul retro.']) {
    const partial={...x,next_photo_request:request};assert.strictEqual(fixes.removeRedundantPhoto(partial,x),partial);
  }
});
