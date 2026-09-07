/* Synthetic policy regressions. An optional local fixture supports private diagnostic replay. No provider calls. */
const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path');
const assets=process.env.FLIPCHECK_TEST_ASSETS||path.join(__dirname,'../src/main/assets');
const V=require(path.join(assets,'visual-policy.js')),E=require(path.join(assets,'editions.js'));
const data=require(process.env.FLIPCHECK_RECORDED_FIXTURE||'./fixtures/identity-cases.cjs');
const refs=d=>d.references.map(r=>({...r,image_data:'recorded-comparison-placeholder-not-a-photo'}));
const replay=d=>V.validate(structuredClone(d.vision),{candidates:structuredClone(d.candidates),detail_needed_from:'none',physical_detail_needed:null},refs(d));

test('A Base Set printing catalogue family still requires an independently read artwork border',()=>{
 const d=data.machamp,out=E.apply({...d.vision,...d.identification},{...d.vision.pokemon_printing,set_name:d.identification.family},1);
 assert.equal(out.printing_check.applicable,true);assert.equal(out.printing_check.shadow,'unclear');
 assert.equal(out.printing_check.complete,false);assert.equal(out.market_ready,false);assert.equal(out.normalized_query,'');
});
test('Base Set catalogue aliases keep printing checks without extending them to other sets',()=>{
 for(const set_name of ['Pokemon Game Base Set','1999 Pokémon Game Base Set','Pokémon TCG Base Set','Base Set (1999)'])
  assert.equal(E.evaluate({...data.machamp.vision.pokemon_printing,set_name},1).applicable,true,set_name);
 for(const set_name of ['Pokemon Game Base Set 2','Base Set 1999-2000 reprint','Base Set Collection','Jungle','Skyridge'])
  assert.equal(E.evaluate({...data.machamp.vision.pokemon_printing,set_name},1).applicable,false,set_name);
});
test('a simulated physical Shadowless reread removes contradictory catalogue claims and stale state',()=>{
 const d=data.machamp;
 // This simulated reread tests propagation only.
 const p={...d.vision.pokemon_printing,set_name:d.identification.family,artwork_shadow:'absent',shadow_location:'Visible right and lower artwork border'};
 const out=E.apply({...d.vision,...d.identification},p,1);
 assert.equal(out.printing_check.complete,true);assert.match(out.variant,/Shadowless/);
 assert.ok(out.excluded_catalogue_fields.some(f=>/Shadowed/.test(f.value)));
 assert.ok(out.catalogue_data.every(f=>!/Shadowed/.test(f.value)));
 assert.doesNotMatch(out.normalized_query,/Shadowed/);assert.equal(out.variant_check,'confirmed');
 assert.deepEqual(E.apply(out,p,1),out);
});
test('A collectible panel keeps its real object kind and closes from cited publication, issue and image matches',()=>{
 const out=replay(data.panel);
 assert.equal(data.panel.vision.kind,'object');assert.equal(out.market_ready,true);
 assert.match(out.model,new RegExp(data.panel.expected.year));assert.match(out.model,new RegExp(data.panel.expected.issue));assert.match(out.family,new RegExp(data.panel.expected.family,'i'));
 assert.equal(out.core_identity.status,'confirmed');assert.equal(out.variant_check,'confirmed');
 assert.deepEqual(out.unresolved_identity_fields,[]);assert.equal(out.next_photo_request,null);
});
test('a panel cannot close using image discovery alone or a single-card unit',()=>{
 const d=data.panel;
 const discovery=refs(d).map(r=>({...r,discovery_only:true,text_origin:'unattributed_image',ocr:null}));
 const candidates=d.candidates.filter(c=>c.fields.some(f=>f.reference_id==='ref3'));
 assert.equal(!!V.validate(d.vision,{candidates},discovery).market_ready,false);
 const wrong=candidates.map(c=>({...c,unit:'single'}));
 assert.equal(!!V.validate(d.vision,{candidates:wrong},refs(d)).market_ready,false);
});
test('A box presentation rejection is repairable but cannot be confirmed without a new comparison',()=>{
 const out=replay(data.box),c=out.visual_candidates[0];
 assert.equal(V.recoverableComparison(c),true);assert.equal(!!out.market_ready,false);
 assert.ok(out.catalogue_data.some(f=>f.field==='brand'&&f.value===data.box.expected.brand));
 assert.ok(out.catalogue_data.some(f=>f.field==='year'&&f.value===data.box.expected.year));
 assert.equal(out.core_identity.status,'partial');
 for(const change of [{unit:'case'},{blocking_fields:[...c.blocking_fields,'configuration_not_matched']},{blocking_fields:[...c.blocking_fields,'physical_identifier_not_matched']}])
  assert.equal(V.recoverableComparison({...c,...change}),false);
});
test('small printed symbols survive evidence selection without becoming model identifiers',()=>{
 const b={...data.box.vision,photo_clues:[...data.box.vision.photo_clues,{role:'symbol',text:'H',certainty:'clear',image_index:1,region:null}]};
 assert.ok(V.observed(b).photo_clues.some(c=>c.role==='symbol'&&c.text==='H'));
 assert.ok(V.identifiers(b).every(c=>c.text!=='H'));
});
test('A remote searches with a separate brand hypothesis and rejects both irrelevant images',()=>{
 const d=data.remote,plan=V.plan(d.vision);
 assert.match(plan.query,new RegExp(d.vision.brand));assert.equal(plan.hypotheses[0].origin,'vision_hypothesis');
 assert.ok(V.observed(d.vision).photo_clues.every(c=>!new RegExp(d.vision.brand).test(c.text)));
 assert.equal(V.googleFirst(d.vision),false);assert.deepEqual(V.rankReferences(refs(d),d.vision),[]);
 assert.ok(V.rankReferences([{id:'pdf8',title:'TV manual',text:'Remote control: TV GUIDE, TOP PICKS and VOICE'}],d.vision).length);
});
test('source failures retain the already requested physical label without asking the user for source images',()=>{
 const d=data.remote,out=V.validate(d.vision,{candidates:[],detail_needed_from:'reference',physical_detail_needed:'Reference image of the remote in the manual'},[]);
 assert.equal(out.assistance_state,'source_detail_needed');assert.equal(out.next_photo_request,d.vision.next_photo_request);
 assert.doesNotMatch(out.next_photo_request,/manual|reference/);
});
