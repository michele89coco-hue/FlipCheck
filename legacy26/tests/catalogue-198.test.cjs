const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),path=require('path'),zlib=require('zlib');
const E=require('../src/main/assets/catalogue-engine'),C=require('../src/main/assets/catalogue-sources');
const D=JSON.parse(zlib.gunzipSync(fs.readFileSync(path.join(__dirname,'fixtures/diagnostics-197.json.gz'))));
function replay(n){const d=D[n],l=E.ingestVision(new E.Ledger(d.vision),d.vision);E.ingestOcr(l,d.photoOcr);const entries=[...C.records(d.pages,l),...d.phases.filter(p=>p.stage==='flipcheck_catalogue_search').flatMap(p=>C.groundedExtraction(p.result,d.pages,l).accepted)];return {d,l,entries};}
test('197 Boniface duplicate checklist views keep one Terrace identity and resolve only after the recorded serial crop',()=>{
 const {d,l,entries}=replay(2);let r=E.reduce(l,entries);assert.equal(r.core_identity.status,'confirmed');assert.equal(r.market_ready,false);assert.ok(r.variant_resolution.pending.includes('serial'));
 E.applyDetails(l,d.phases.find(p=>p.stage==='flipcheck_evidence_detail').result.details,E.recoveryRequests(l,r));r=E.reduce(l,entries);assert.equal(r.market_ready,true);assert.equal(r.variant,'Green');assert.equal(r.physical_serial.value,'2/5');assert.equal(r.core_identity.pending_fields.length,0);
 for(const es of [entries,[...entries].reverse(),[...entries,...entries]]){const g=E.candidateGroups(l,es);assert.equal(g.length,1);assert.equal(g[0].entry.subset,'Terrace');assert.equal(g[0].score,88);assert.equal(E.reduce(l,es).market_ready,true);}
});
test('197 Doncic year/product aliases resolve the base without guessing a Green parallel',()=>{
 const {l,entries}=replay(3);for(const es of [entries,[...entries].reverse(),[...entries,...entries]]){const r=E.reduce(l,es);assert.equal(r.core_identity.status,'confirmed');assert.equal(E.familyKey(r.family),'prizm');assert.equal(r.market_ready,false);assert.ok(r.variant_resolution.variant_candidates.some(v=>v.name==='Green Pulsar'));assert.ok(r.variant_resolution.variant_candidates.some(v=>v.name==='Green'));assert.equal(r.physical_serial,null);}
});
test('197 identical Bandai labels retain both official artwork references, never a dummy or arbitrary edition',()=>{
 const {l,entries}=replay(1);let r=E.reduce(l,entries);assert.equal(r.core_identity.status,'confirmed');assert.equal(r.market_ready,false);const vs=r.variant_resolution.variant_candidates;assert.equal(vs.length,2);assert.ok(vs.every(v=>v.image_url.includes('/card/P-110')));assert.notEqual(vs[0].id,vs[1].id);
 l.add('catalogue_variant',vs[1].id,{certainty:'clear',image_index:1,reference_source:vs[1].source.url});r=E.reduce(l,entries);assert.equal(r.market_ready,true);assert.equal(r.variant_resolution.proof[0].image_url,vs[1].image_url);assert.equal(r.variant,'4th Anniversary Event');
});
test('197 box search retains basketball and printed autograph configuration, rejecting baseball references',()=>{
 const {l,entries}=replay(0);assert.match(E.query(l),/basketball.*1 AUTOGRAPH/i);const r=E.reduce(l,entries);assert.equal(r.core_identity.status,'confirmed');assert.equal(r.core_identity.origin,'printed_product');assert.equal(r.catalogue_core_verified,false);assert.equal(r.market_ready,false);
});
const src={url:'https://www.beckett.com/test'};
const card=(patch={})=>({subject:'Example',number:'21',family:'Select',year:'2025-26',subset:'Terrace',grounded:true,source:src,variants:[],...patch});
const ledger=()=>{const l=new E.Ledger({domain:'sports',kind:'card'});for(const [f,v] of Object.entries({subject:'Example',collector_number:'21',season:'2025-26',product:'Select'}))l.add(f,v,{certainty:'clear',image_index:1});return l;};
test('unknown subset can join only one complete group and cannot collapse distinct inserts or years',()=>{
 const l=ledger(),a=card(),b=card({subset:'Base',subset_known:false});assert.equal(E.candidateGroups(l,[a,b]).length,1);
 assert.equal(E.reduce(l,[a,b,card({subset:'Fireworks'})]).core_identity.status,'partial');assert.equal(E.candidateGroups(l,[a,card({year:'2024-25'})]).length,1);assert.ok(l.candidates.some(e=>e.reasons.includes('different_product_season')));
 l.atoms=l.atoms.filter(a=>a.field!=='season');assert.equal(E.candidateGroups(l,[a,card({year:'2024-25'})]).length,2);
});
test('comma team suffixes, explicit tiers and 1/1 syntax parse without transferring variants from an unknown section',()=>{
 const l=ledger(),p={url:src.url,title:'2025-26 Select Soccer Checklist',text:'Base Terrace Checklist\nGreen /5\nBlack 1/1\n21 Example, Nigeria\nChecklist Top\nGold /10\n21 Example, Nigeria'};
 const es=C.records([p],l);assert.equal(es.length,2);assert.equal(es[0].subset,'Terrace');assert.ok(es[0].variants.some(v=>v.name==='Black'&&v.print_run===1));assert.deepEqual(es[1].variants,[]);assert.equal(E.candidateGroups(l,es).length,1);
});
test('truncated parallel aliases require one same-source canonical record and cannot hide conflicting inserts',()=>{
 const l=ledger(),a=card({family:'Prizm',subset:'Base',variants:[{name:'Choice Nebula'}]}),b=card({family:'2025-26 Panini Prizm Prizms Choice',subset:'Base-set parallel'});
 assert.equal(E.canonicalEntries([a,b])[1].family,'Prizm');assert.equal(E.canonicalEntries([a,{...b,source:{url:src.url+'/other'}}])[1].family,b.family);
 assert.equal(E.canonicalEntries([a,{...a,subset:'Fireworks'},b])[2].family,b.family);
 assert.notEqual(E.familyKey('Prizm Draft Picks'),E.familyKey('Prizm'));assert.notEqual(E.familyKey('Select 2022'),E.familyKey('Select 2023'));
});
test('One Piece missing attributed artwork is a catalogue gap; different textual editions do not receive pooled images',()=>{
 const {l}=replay(1),url='https://en.onepiece-cardgame.com/cardlist/',text='P-110 | P | CHARACTER\nMonkey.D.Luffy\nCard Set(s)\nPromo A\nP-110 | P | CHARACTER\nMonkey.D.Luffy\nCard Set(s)\nPromo B';
 const es=C.records([{url,text,image_details:[{image_url:'https://en.onepiece-cardgame.com/images/cardlist/card/P-110.png'}]}],l),r=E.reduce(l,es);assert.equal(r.market_ready,false);assert.equal(r.variant_resolution.needs_catalogue,true);assert.equal(r.next_photo_request,null);assert.ok(r.variant_resolution.variant_candidates.every(v=>!v.image_url));
});
test('sealed configuration observations veto mismatching guarantees even when counts match',()=>{
 const l=new E.Ledger({domain:'sealed',kind:'object'});for(const [f,v] of Object.entries({product:'Topps Chrome Box',configuration:'1 autograph in every box, 10 packs per box',text:'NBA'}))l.add(f,v,{certainty:'clear',image_index:1});
 const r=E.reduce(l,[{subject:'Topps Chrome Box Basketball Hobby',family:'Topps Chrome Box',grounded:true,source:src,entry_quote:'Topps Chrome Box 2 autographs in every box, 10 packs per box'}]);assert.equal(r.market_ready,false);assert.deepEqual(l.candidates[0].configuration_conflicts,['autographs']);
});
test('normalizing year-prefixed names never merges a different year hidden in family metadata',()=>{
 const l=ledger();l.atoms=l.atoms.filter(a=>a.field!=='season');const a=card({family:'2025-26 Panini Select Soccer',year:''}),b=card({family:'2024-25 Panini Select Soccer',year:''});assert.equal(E.candidateGroups(l,[a,b]).length,2);assert.equal(E.reduce(l,[a,b]).core_identity.status,'partial');
});
