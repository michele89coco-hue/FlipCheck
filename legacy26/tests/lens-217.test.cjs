const {test}=require('node:test'),assert=require('node:assert/strict');
const E=require('../src/main/assets/catalogue-engine'),L=require('../src/main/assets/lens-policy'),V=require('../src/main/assets/visual-policy');
function replay(name){const f=structuredClone(require('./fixtures/lens-217-'+name+'.json')),l=new E.Ledger(f.vision);E.ingestVision(l,f.vision);f.references.forEach(r=>r.image_data='downloaded');return {...f,l};}
test('217 product resolver accepts reordered manufacturer and generic descriptions, preserves distinctive set tokens',()=>{
 for(const title of ['Panini Adrenalyn XL','Adrenalyn Panini XL','XL Adrenalyn Panini','Panini Adrenalin XL']){const r=L.family214('Adrenalyn XL Trading Card Game','Panini','',title);assert.equal(r.grounded,true,title);assert.ok(E.productEquivalent217(r.value,'Adrenalyn XL Trading Card Game'));}
 for(const title of ['Panini Select XL','Panini Adrenalyn','Prizm Draft Picks','Topps Chrome'])assert.equal(L.family214('Adrenalyn XL Trading Card Game','Panini','',title).grounded,false,title);
 assert.equal(E.productEquivalent217('Prizm','Prizm Draft Picks'),false);assert.equal(E.productEquivalent217('Topps Chrome','Topps Chrome Update'),false);
});
test('217 actual Curry no longer loses photographed subject or rejects equivalent family',()=>{
 const f=replay('curry'),before=f.l.pick('subject'),r=L.resolveComparisons217(f.l,f.batches,f.references);
 assert.equal(r.accepted.length,1,JSON.stringify(r.rejected));assert.equal(f.l.pick('subject').id,before.id);
 assert.equal(E.reduce(f.l,r.accepted).core_identity.status,'confirmed');
 assert.ok(f.l.events.some(e=>e.stage==='comparison_reading_retained'&&e.observation===before.id));
 assert.equal(f.batches[0].reply.comparisons[0].original_reading.subject,'');
 assert.ok(L.metadata215(f.references,f.l).some(r=>r.metadataStrong));
});
test('217 missing name is reusable only from the same original photo; explicit mismatch still rejects',()=>{
 const f=replay('curry'),c=f.batches[0].reply.comparisons[0];c.original_reading.subject='Seth Curry';assert.ok(L.resolveComparisons217(f.l,f.batches,f.references).rejected[0].reasons.includes('different_original_subject'));
 c.original_reading.subject='';c.original_image_index=2;assert.ok(L.resolveComparisons217(f.l,f.batches,f.references).rejected[0].reasons.includes('unreadable_original_subject'));
});
test('217 actual Charizard first match is reconsidered after the later correct original crop, without another API',()=>{
 const f=replay('charizard');assert.ok(L.resolveComparisons217(f.l,[f.batches[0]],f.references).rejected.find(r=>r.id==='lens-8').reasons.includes('original_identifier_unresolved'));
 const later=f.batches[1].reply;assert.ok(L.reconcileOriginal208(f.l,later.original_readings,f.crops).accepted.some(r=>r.value==='9/12'));
 const r=L.resolveComparisons217(f.l,f.batches,f.references),result=E.reduce(f.l,r.accepted);
 assert.deepEqual(r.accepted.map(e=>e.visual_reference_id),['lens-8']);assert.equal(result.market_ready,true);assert.equal(result.physical_card_number,'9/12');assert.equal(result.family,'Skyridge');
 assert.ok(r.rejected.some(r=>r.id==='lens-13'));assert.ok(f.l.values('collector_number').some(a=>a.value==='92/??'));
});
test('217 actual Kobe conflict requires original reread, then saved front comparison can resolve core',()=>{
 const f=replay('kobe');const initial=L.resolveComparisons217(f.l,f.batches,f.references);assert.equal(initial.accepted.length,0);assert.ok(initial.rejected.find(r=>r.id==='lens-44').reasons.includes('different_number'));
 const atom=f.l.pick('collector_number'),req={field:'collector_number',image_index:atom.image_index,region:atom.region,reason:'catalogue_number_conflict',key:'test-original'};
 E.applyDetails(f.l,[{field:'collector_number',text:'81',certainty:'clear',evidence_found:true,image_index:atom.image_index}],[req]);
 const r=L.resolveComparisons217(f.l,f.batches,f.references);assert.deepEqual(r.accepted.map(e=>e.visual_reference_id),['lens-44']);assert.equal(E.reduce(f.l,r.accepted).core_identity.status,'confirmed');assert.equal(f.l.pick('collector_number').value,'81');assert.ok(f.l.values('collector_number').some(a=>a.value==='18'));
});
for(const name of ['doncic','mewtwo'])test('217 recorded '+name+' keeps its existing exact closure',()=>{const f=replay(name);for(const b of f.batches)L.reconcileOriginal208(f.l,b.reply.original_readings,f.crops);const r=L.resolveComparisons217(f.l,f.batches,f.references);assert.equal(E.reduce(f.l,r.accepted).market_ready,true);});
test('217 Boniface recorded consumption fits the explicit completion tolerance; target stays 0.030',()=>{
 const f=replay('boniface'),b=new V.Budget();b.enableCompletionTolerance();b.enableCompletionTolerance();assert.equal(b.targetUsd,.03);assert.equal(b.maxUsd,.033);
 for(const e of f.budget.entries){const r=b.reserve(e.kind,e.reservedUsd);b.settle(r,e.actualUsd);}
 assert.ok(Math.abs(b.spent()-.03163085)<1e-9);assert.throws(()=>b.reserve('vision',.0015),/budget_exhausted/);assert.throws(()=>b.reserve('market',0),/budget_exhausted/);
});
test('217 tolerance does not discard unknown charges, exceed 10 percent or bypass cancellation',()=>{
 const b=new V.Budget({maxEur:.01});b.enableCompletionTolerance();assert.equal(b.maxUsd,.011);const r=b.reserve('vision',.01);b.settle(r,null);assert.throws(()=>b.reserve('vision',.0011),/budget_exhausted/);b.cancelled=true;assert.throws(()=>b.reserve('vision',0),/scan_cancelled/);
});
test('217 merged catalogues retain documented unnumbered Green Ice despite a partial extraction',()=>{
 const fs=require('fs'),z=require('zlib'),C=require('../src/main/assets/catalogue-sources'),read=n=>JSON.parse(z.gunzipSync(fs.readFileSync(__dirname+'/fixtures/diagnostics-'+n+'.json.gz')));
 const d=read(200)[0],pages=read(199)[0].pages,l=E.ingestVision(new E.Ledger(d.vision),d.vision);E.ingestOcr(l,d.photoOcr);
 E.applyDetails(l,[{field:'serial',text:'2/5',certainty:'clear',evidence_found:true,image_index:2}],E.recoveryRequests(l,E.reduce(l,[])));
 const es=C.records(pages,l).concat(d.phases.filter(p=>p.stage==='flipcheck_catalogue_search').flatMap(p=>C.groundedExtraction(p.result,pages,l).accepted));const r=E.reduce(l,es);
 assert.equal(r.market_ready,true,JSON.stringify(r.variant_resolution));assert.equal(r.variant,'Green');assert.equal(r.physical_serial.value,'2/5');
});
