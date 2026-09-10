const {test}=require('node:test'),assert=require('node:assert/strict');
const E=require('../src/main/assets/catalogue-engine'),L=require('../src/main/assets/lens-policy'),S=require('../src/main/assets/slab-identity'),N=require('../src/main/assets/identity-names');
function fixture(name){const f=structuredClone(require('./fixtures/lens-218-'+name+'.json'));f.l=E.ingestVision(new E.Ledger(f.vision),f.vision);f.references.forEach(r=>r.image_data='downloaded');return f;}
function resolve(f){for(const b of f.batches)L.reconcileOriginal208(f.l,b.reply.original_readings,f.crops);const checked=L.resolveComparisons217(f.l,f.batches,f.references);return {checked,result:E.reduce(f.l,checked.accepted)};}
for(const name of ['curry','kobe'])test('218 recorded '+name+' closes from two matching fronts without inventing a photo number',()=>{
 const f=fixture(name),{checked,result:r}=resolve(f);assert.equal(r.market_ready,true,JSON.stringify(checked.rejected));assert.equal(r.closure_status,'resolved');assert.deepEqual(r.missing_information,[]);assert.equal(r.next_photo_request,null);assert.equal(r.physical_card_number,null);assert.equal(f.l.values('collector_number').length,0);
 assert.equal(r.source_confirmed_catalog_number,name==='kobe'?'81':null);assert.equal(r.source_confirmed_year,name==='kobe'?'1997-98':'2009-10');assert.equal(checked.accepted.length,2);assert.equal(r.variant_resolution.variant_origin,'visual_comparison');
 if(name==='kobe'){assert.equal(f.l.pick('subject').value,'KOBE BRYANT');assert.ok(f.l.values('subject').some(a=>a.original_views?.full==='KOBE BRYANT LOS ANGELES LAKERS'));}
});
test('218 missing-number consensus requires distinct sources, images and two comparisons',()=>{
 for(const change of [f=>f.batches.pop(),f=>f.references[1].url=f.references[0].url,f=>f.references[1].image_url=f.references[0].image_url,f=>f.batches[1].reply.comparisons[0].ambiguous=true]){const f=fixture('curry');change(f);assert.equal(resolve(f).result.market_ready,false);}
});
test('218 strong matches cannot erase an actual number, subject, season or manufacturer conflict',()=>{
 for(const [field,text] of [['collector_number','18'],['subject','Seth Curry'],['season','2018'],['brand','Topps']]){
  const f=fixture(field==='collector_number'?'kobe':'curry');f.l.add(field,text,{source:'focused_vision',certainty:'clear',image_index:3});assert.equal(resolve(f).result.market_ready,false,field);
 }
 const f=fixture('curry');for(const b of f.batches)b.reply.comparisons[0].original_reading.subject='Seth Curry';assert.equal(resolve(f).result.market_ready,false);
});
test('218 ordinary-printing recovery cannot swallow a serial or named parallel',()=>{
 for(const edit of [f=>f.l.add('serial','2/5',{certainty:'clear',source:'vision',image_index:2}),f=>f.references.forEach(r=>r.title+=' Green Pulsar'),f=>f.l.add('autograph','present',{certainty:'clear',source:'vision',image_index:1})]){const f=fixture('curry');edit(f);assert.equal(resolve(f).result.market_ready,false);}
});
test('218 translations preserve official names and card suffixes across scripts',()=>{
 assert.ok(N.count>=1025);
 for(const [input,expected] of [['Glurak','Charizard'],['Dracaufeu','Charizard'],['超梦V','Mewtwo V'],['超夢V','Mewtwo V'],['ミュウツーV','Mewtwo V'],['뮤츠 V','Mewtwo V']]){const n=N.normalize(input);assert.equal(n.it,expected,input);assert.equal(n.en,expected);assert.equal(n.original,input);}
 assert.equal(N.normalize('Unknown creature').en,'Unknown creature');assert.equal(N.normalize('Mewtwo V',{translations:{original:'Mewtwo V',it:'Charizard',en:'Charizard',certainty:'clear'}}).en,'Mewtwo V');
 assert.equal(N.normalize('Glurak',{domain:'sports'}).en,'Glurak');
 const t=N.normalize('陌生角色',{domain:'tcg',translations:{original:'陌生角色',it:'Nome italiano',en:'English name',certainty:'clear'}});assert.equal(t.it,'Nome italiano');assert.equal(t.en,'English name');
});
test('218 recorded unknown grader slab closes with localized name and original card language',()=>{
 const f=fixture('mewtwo'),facts=S.labelFacts(f.vision),r=S.close({...f.vision,title_language:'en'},facts);assert.equal(r.market_ready,true);assert.match(r.title,/Mewtwo V/);assert.match(r.title,/CHN/);assert.doesNotMatch(r.title,/UNKNOWN/);assert.equal(r.language,'zh');assert.equal(r.card_identity.original_subject,'超梦V');assert.equal(r.grading.company,'UNKNOWN');assert.equal(r.grading.certificate_verified,false);assert.equal(r.name_identity.origin,'local_name_dictionary');assert.equal(r.card_identity.number,'135/127');assert.ok(r.localized_titles.it&&r.localized_titles.en);
});
