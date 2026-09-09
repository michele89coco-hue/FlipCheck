const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),zlib=require('zlib'),path=require('path');
const E=require('../src/main/assets/catalogue-engine'),C=require('../src/main/assets/catalogue-sources');
const D=JSON.parse(zlib.gunzipSync(fs.readFileSync(path.join(__dirname,'fixtures/diagnostics-202-pokemon.json.gz'))));
function replay(i){const d=D[i],l=E.ingestVision(new E.Ledger(d.vision),d.vision);E.applyIdentityBands202(l,d.bands);return {l,entries:d.candidates};}
const region={image_index:1,x:.1,y:.8,width:.8,height:.1};
function promo(){const base={domain:'pokemon',kind:'card',language:'ja',observations:[{field:'subject',text:'Charizard',certainty:'clear',image_index:1},{field:'pokedex_number',text:'No.006',certainty:'clear',image_index:1,zone:'footer'},{field:'copyright',text:'©1999 Pokémon',certainty:'clear',image_index:1,zone:'footer'}]};return E.ingestVision(new E.Ledger(base),base);}
function entry(extra={}){return {subject:'Charizard',family:'Japanese CD Promo',number:'6',identifier_type:'pokedex',year:'1999',language:'ja',variants:[{name:'Holo',finish:'holo'}],grounded:true,source:{url:'https://www.psacard.com/cert/example/psa'},...extra};}
test('202 Politoed: Stage 2 is not a product; existing name/number/catalogue closes Skyridge',()=>{
 const {l,entries}=replay(1),r=E.reduce(l,entries);
 assert.deepEqual(E.keyValues(l).products,[]);assert.ok(l.values('card_type').some(a=>a.value==='Pokémon di Fase 2'));
 assert.equal(r.market_ready,true);assert.equal(r.family,'Skyridge');assert.equal(r.physical_card_number,'H23/H32');assert.equal(r.source_confirmed_year,'2003');
 assert.equal(r.card_identity.has_rarity_symbol,true);assert.match(r.pokemon_printing.copyright_text,/2003/);
 assert.match(E.query(l),/Politoed "H23\/H32" 2003/);assert.equal(r.printing_check.stamp,'not_applicable');
});
test('202 raw Charizard keeps 006 in search and display without inventing a collector number',()=>{
 const {l,entries}=replay(0),r=E.reduce(l,entries);assert.match(E.query(l),/リザードン "006"/);assert.match(E.query(l),/Ken Sugimori/);
 assert.equal(r.physical_card_number,null);assert.match(r.title,/No\.006/);assert.equal(r.market_ready,false);assert.equal(l.events.some(e=>e.stage==='identity_band_conflict'&&e.field==='language'),false);
});
test('202 Mewtwo detailed copyright survives while unavailable catalogue remains unresolved',()=>{
 const {l,entries}=replay(2),r=E.reduce(l,entries);assert.match(E.query(l),/135\/127.*2024.*SR/);assert.match(r.pokemon_printing.copyright_text,/2024/);assert.equal(r.source_confirmed_year,null);assert.equal(r.market_ready,false);
});
test('name/006/year selects promo core; image confirmation is required for the exact printing',()=>{
 const l=promo(),e=entry();let r=E.reduce(l,[e]);assert.equal(r.core_identity.status,'confirmed');assert.equal(r.market_ready,false);assert.ok(r.variant_resolution.pending.includes('catalogue_image'));assert.equal(r.next_photo_request,null);
 l.add('catalogue_core',E.coreKey(e),{certainty:'clear',image_index:1,source:'catalogue_image_comparison'});r=E.reduce(l,[e]);assert.equal(r.market_ready,true);assert.equal(r.physical_card_number,null);assert.equal(r.source_confirmed_catalog_number,'6');
});
test('same 006 and year cannot collapse two distinct promo families or a collector fraction',()=>{
 const l=promo();assert.equal(E.reduce(l,[entry(),entry({family:'Other Promo'})]).core_identity.status,'partial');
 assert.equal(E.reduce(l,[entry({family:'Expedition',number:'6/165',identifier_type:'collector'})]).market_ready,false);
});
test('catalogue alternatives sharing a finish are not limited to two or silently collapsed',()=>{
 const l=promo();l.add('collector_number','1/99',{certainty:'clear',image_index:1});
 const e=entry({number:'1/99',identifier_type:'collector',family:'Promo Set',variants:['Stamp A','Stamp B','Stamp C'].map(name=>({name,finish:'holo',visual_required:true,image_url:'https://example.com/'+name}))});
 let r=E.reduce(l,[e]);assert.equal(r.core_identity.status,'confirmed');assert.equal(r.market_ready,false);assert.equal(r.variant_resolution.variant_candidates.length,3);
 l.add('catalogue_variant','Stamp B',{certainty:'clear',image_index:1,source:'variant_comparison',reference_source:e.source.url});assert.equal(E.reduce(l,[e]).market_ready,true);
});
test('band symbols and finish preserve observed absence and original coordinates',()=>{
 const l=promo();E.applyIdentityBands202(l,{features:[{field:'rarity_symbol',value:'absent',certainty:'clear',image_index:1,region,description:'Entire rarity corner legible; symbol absent',zone:'footer'},{field:'finish',value:'holo',certainty:'clear',image_index:1,region,zone:'artwork'}]});
 assert.equal(l.pick('rarity_symbol').value,'absent');assert.deepEqual(l.pick('rarity_symbol').region,region);assert.equal(l.pick('finish').source,'identity_band');
});
test('PSA/PriceCharting reference searches retain the exact candidate, not the species alone',()=>{
 const l=promo(),q=E.query(l,'reference',entry());assert.match(q,/Charizard "6" 1999/);assert.match(q,/Japanese CD Promo/);assert.match(q,/site:psacard.com OR site:pricecharting.com/);
 assert.equal(E.sourceTrusted('https://www.pricecharting.com/game/pokemon-japanese-cd-promo/charizard-6','pokemon'),true);
 assert.equal(E.sourceTrusted('https://www.pricecharting.com/game/example','sports'),false);
});
test('reference images must be attributed to the same page and printing; generic/related pictures are rejected',()=>{
 const l=promo(),e=entry({source:{url:'https://www.pricecharting.com/game/pokemon-japanese-cd-promo/charizard-6'}});
 const page={url:e.source.url,title:'Charizard #6 Pokemon Japanese CD Promo Prices',image_details:[{url:'https://img.example/ok.jpg',caption:'Charizard #6 Pokemon Japanese CD Promo'},{url:'https://img.example/other.jpg',caption:'Charizard #6 Base Set'},{url:'https://img.example/logo.jpg',caption:'PriceCharting logo'}]};
 assert.deepEqual(C.pokemonReferenceImages203(page,e,l),['https://img.example/ok.jpg']);assert.deepEqual(C.pokemonReferenceImages203({...page,url:'https://example.com/other'},e,l),[]);
});
const vm=require('node:vm'),runtime=fs.readFileSync(path.join(__dirname,'../src/main/assets/catalogue-runtime.js'),'utf8');
function sandbox(extra={}){
 const s={E193:E,C193:C,Date,JSON,Math,Object,Map,Set,string193:{type:'string'},strings193:{type:'array'},object193:properties=>({type:'object',properties}),enum193:values=>({type:'string',enum:values}),schemaFormat:()=>({}),entrySchema193:{},estimate164:()=>.001,addUsage(){},guard164(){},status(){},saveEvidence192(){},diagnosticPhases:[],...extra};vm.createContext(s);return s;
}
test('production reference search uses exact candidate and keeps secondary sources grounded',async()=>{
 const l=promo(),e=entry(),calls=[],page={url:'https://www.pricecharting.com/game/pokemon-japanese-cd-promo/charizard-6',title:'Charizard #6 Japanese CD Promo',text:'Japanese CD Promo\n1999\nCharizard #6 Japanese CD Promo'};
 const response={output:[],reply:{entries:[{subject:'Charizard',family:'Japanese CD Promo',number:'6',year:'1999',language:'ja',identifier_type:'pokedex',source_url:page.url,entry_quote:'Charizard #6 Japanese CD Promo',proof:{subject:'Charizard',family:'Japanese CD Promo',number:'6',year:'1999'},variants:[]}]}};
 const s=sandbox({originalOpenai26:async body=>{calls.push(body);return response;},parseResponseJSON:r=>r.reply,countWeb:()=>1,flattenWebResultNodes(){},collectSources:()=>[page],directCall165:async(action,payload)=>{assert.equal(action,'page');assert.equal(payload.url,page.url);assert.ok(payload.terms.includes('006'));return {status:200,...page};}});
 vm.runInContext(runtime.slice(runtime.indexOf('async function searchCatalogue193('),runtime.indexOf('async function rotateDetail193('))+'\nthis.search=searchCatalogue193;',s);
 const ctx={budget:{spent:()=>0,maxUsd:.03},queries:[]},out=await s.search(l,ctx,'reference',[],e);
 assert.equal(calls.length,1);assert.match(calls[0].input,/site:psacard.com OR site:pricecharting.com/);assert.equal(out.entries.some(e=>e.source.url===page.url&&e.grounded),true);
});
test('production core comparison uses an attributed secondary image and ignores slab furniture',async()=>{
 const l=promo(),e=entry(),ref=entry({source:{url:'https://www.pricecharting.com/game/pokemon-japanese-cd-promo/charizard-6'}}),calls=[];
 const pages=[{url:ref.source.url,image_details:[{image_url:'https://img.example/promo.jpg',caption:'Charizard #6 Japanese CD Promo'}]}];
 const reply={comparisons:[{reference_id:'core0',reference_subject:'Charizard',reference_number:'6',match:true,ambiguous:false,conflicts:[],features:[{field:'artwork',original:'specific pose',reference:'specific pose',certainty:'clear',agrees:true},{field:'symbols',original:'specific logo',reference:'specific logo',certainty:'clear',agrees:true}]}]};
 const s=sandbox({lastVisionReading:l.base,directCall165:async(action,payload)=>{assert.equal(action,'image');assert.equal(payload.url,'https://img.example/promo.jpg');return {status:200,image_data:'offline-reference'};},visualPhoto164:async()=>({data:'offline-original',meta:{imageIndex:1}}),originalOpenai26:async body=>{calls.push(body);return reply;},parseResponseJSON:x=>x});
 vm.runInContext(runtime.slice(runtime.indexOf('async function compareCore193('),runtime.indexOf('async function compareVariants193('))+'\nthis.compare=compareCore193;',s);
 await s.compare(l,[e,ref],{budget:{spent:()=>0,maxUsd:.03}},pages);
 assert.equal(calls.length,1);assert.match(calls[0].input[0].content[0].text,/carta dentro la custodia/);assert.equal(l.pick('catalogue_core').reference_source,ref.source.url);assert.equal(E.reduce(l,[e,ref]).market_ready,true);
});
