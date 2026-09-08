'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const S=require('../src/main/assets/slab-identity.js'),F=require('../src/main/assets/identity-final.js');
const specimen=()=>({kind:'card',brand:'Pokemon',model_confidence:99,family_confidence:98,photo_clues:[{text:'#8 CLOYSTER HOLO R',role:'collector_number',certainty:'clear',image_index:1}],pokemon_printing:{is_pokemon:true,language:'Italian',first_edition_stamp:'unclear'},slab_reading:{present:true,certainty:'clear',image_index:1,grader:'Beckett',label_text:'2002 EXPEDITION ITALIAN #8 CLOYSTER HOLO R 9 MINT 0012345678',subject:'Cloyster',family:'Expedition',model:'Cloyster',year:'2002',card_number:'#8',variant:'HOLO R',grade:'9 MINT',certificate:'0012345678',object_match:'matches',match_details:'Label and card agree.'}});
test('191 label fallback closes the mixed collector-number regression with grading retained',()=>{
 const p=specimen(),f=S.labelFacts(p),r=S.close(p,f,{state:'unavailable'});
 assert.equal(r.identity_keys.number.value,'8');assert.equal(r.market_ready,true);assert.equal(r.identity_status,'confirmed');assert.equal(r.exact_identity_status,'confirmed');
 assert.match(r.title,/2002.*Expedition.*#8.*Cloyster.*Italian.*HOLO R.*BGS 9 MINT/);assert.equal(r.grading.company,'BGS');assert.equal(r.grading.grade,'9 MINT');assert.equal(r.slab_verification.certificate_verified,false);assert.equal(r.printing_check,undefined);assert.deepEqual(r.missing_information,[]);
});
test('191 label is sufficient when the internal card number, stamp or object match is unreadable',()=>{
 const p=specimen();p.slab_reading.object_match='unclear';p.photo_clues.push({text:'88',role:'collector_number',certainty:'clear',image_index:1});
 assert.equal(S.close(p,S.labelFacts(p)).market_ready,true);
});
test('191 certificate URL retains leading zeroes and routes to the grading company',()=>{
 const f=S.labelFacts(specimen());assert.equal(S.certificatePlan(f).url,'https://www.beckett.com/grading/card-lookup?item_id=0012345678&item_type=BGS');
 for(const g of ['PSA','CGC','TAG'])assert.equal(S.certificatePlan({...f,grader:g}).state,'ready');
 assert.equal(S.certificatePlan({...f,grader:'TAG',certificate:'T1234567'}).url,'https://my.taggrading.com/card/T1234567');
 assert.notEqual(S.certificatePlan({...f,certificate:'00123O5678'}).state,'ready');
});
test('191 official certificate imports structured name and grade and marks only a returned record verified',()=>{
 const p=specimen(),f=S.labelFacts(p),plan=S.certificatePlan(f),page={status:200,url:plan.url,structured_fields:[['Cert Number','0012345678'],['Year','2002'],['Set Name','Pokemon Expedition'],['Card Name','Cloyster'],['Card Number','8'],['Variation','Holo'],['Grade','9 MINT']].map(([label,value])=>({label,value}))};
 const record=S.officialRecord(plan,page,f);assert.equal(record.state,'verified');const result=S.close(p,f,record);
 assert.equal(result.slab_verification.certificate_verified,true);assert.equal(result.family,'Pokemon Expedition');assert.equal(result.variant,'Holo');assert.equal(result.grading.certificate,'0012345678');
});
test('191 page title, an echoed certificate URL or another valid number cannot verify a certificate',()=>{
 const f=S.labelFacts(specimen()),plan=S.certificatePlan(f);
 for(const page of [{status:200,url:plan.url,title:'2002 Expedition Cloyster 8 Holo',text:'Enter a certificate number'},{status:200,url:'https://unrelated.example/cert/0012345678',text:'Cert Number: 0012345678\nYear: 2002\nSet: Expedition\nSubject: Cloyster'},{status:200,url:plan.url,text:'Cert Number: 0012345679\nYear: 2002\nSet: Expedition\nSubject: Cloyster'},{status:200,url:plan.url,text:'Cert Number: 0012345678\nYear: 2002\nSet: Expedition\nSubject: Pichu'}]){
  const r=S.officialRecord(plan,page,f);assert.notEqual(r.state,'verified');assert.equal(S.close(specimen(),f,r).slab_verification.certificate_verified,false);
 }
});
test('191 incomplete label with no certificate requests only the label and does not invent identity',()=>{
 const p=specimen();p.slab_reading.certainty='uncertain';p.slab_reading.subject='';p.slab_reading.model='';
 const r=S.close(p,S.labelFacts(p));assert.equal(r.market_ready,false);assert.match(r.next_photo_request,/etichetta/);
});
test('191 title preserves full collector prefixes, authentic labels and unknown grading metadata',()=>{
 for(const [raw,expected] of [['#8 CLOYSTER HOLO R','8'],['H23/H32','H23/H32'],['8/165','8/165'],['RA-KB','RA-KB'],['9.5 MINT','']])assert.equal(S.cardNumber(raw),expected);
 const p=specimen();p.slab_reading.grade='';p.slab_reading.certificate='';const r=S.close(p,S.labelFacts(p));assert.equal(r.market_ready,true);assert.equal(r.grading.grade,null);assert.match(r.title,/BGS$/);
});
test('191 composed Machamp retains copyright provenance and removes resolved uncertainty from title/query',()=>{
 const fields=[{field:'family',value:'Base Set'},{field:'subject',value:'Machamp'},{field:'catalog_number',value:'8/102'}];
 const p={kind:'card',family:'Base Set',family_confidence:72,model:'Base Set #8/102 Machamp',variant:'; shadow status unclear · 1st Edition · Shadowless',catalogue_verified:true,catalogue_core_verified:true,core_identity:{status:'confirmed',fields},printing_check:{complete:true,labels:['1st Edition','Shadowless']},normalized_query:'old',photo_clues:[{role:'copyright',certainty:'clear',text:'© 1995, 96, 98, 99 Nintendo, Creatures, GAMEFREAK © 1999 Wizards',image_index:1}]};
 const r=F.compose(p,p);assert.match(r.title,/1999.*Base Set.*8\/102.*Machamp/);assert.equal(r.identity_date.kind,'copyright');assert.equal(r.variant,'1st Edition · Shadowless');assert.doesNotMatch(r.normalized_query,/unclear/);assert.equal(r.initial_family_confidence,72);assert.equal(r.family_verified,true);assert.equal(r.family_confidence,null);
});
test('191 final box preserves a concrete photo request without inventing a configuration',()=>{
 const p={object_unit:'box',category:'basketball box',variant_needs_verification:true,core_identity:{status:'confirmed',fields:[]},next_photo_request:null};const r=F.compose(p,p);
 assert.match(r.next_photo_request,/pacchetti/);assert.equal(r.market_ready,undefined);
});
const E=require('../src/main/assets/editions.js');
test('191 certificate fallback fields keep their label origin even when the record is verified',()=>{
 const base=specimen(),facts=S.labelFacts(base),plan=S.certificatePlan(facts);
 const fields={certificate:facts.certificate,subject:facts.subject,year:facts.year,family:facts.family};
 const record=S.officialRecord(plan,{status:200,url:plan.url,structured_fields:Object.entries(fields).map(([label,value])=>({label:label==='family'?'Set':label,value}))},facts);
 assert.equal(record.state,'verified');const out=S.close(base,facts,record);
 assert.equal(out.grading.origin,'photo_slab_label');assert.equal(out.core_identity.fields.find(f=>f.field==='grade').origin,'photo_slab_label');assert.equal(out.catalogue_data.some(f=>f.field==='grade'),false);
});
test('191 Japanese first-edition applicability preserves 2016 and excludes 2017',()=>{
 const base={catalogue_core_verified:true,family:'Example',core_identity:{fields:[{field:'year',value:'2017',origin:'catalogue',number_kind:'year'}]}};
 const printing={is_pokemon:true,language:'Japanese',card_type:'pokemon',copyright_text:'',copyright_image:0};
 assert.equal(E.cataloguePrinting(base,printing).stamp_policy.rule,'japanese_release_after_2016');
 base.core_identity.fields[0].value='2016';assert.equal(E.cataloguePrinting(base,printing).stamp_policy,undefined);
});
test('191 rarity absence identifies only the applicable Japanese Base printing and never basic energies',()=>{
 const base={catalogue_core_verified:true,family:'Base Set',core_identity:{fields:[{field:'year',value:'1996',origin:'catalogue',number_kind:'year'}]}};
 const p={is_pokemon:true,language:'Japanese',card_type:'pokemon',first_edition_stamp:'not_applicable',rarity_symbol:'absent',rarity_image:1,rarity_location:'clear lower right corner'};
 const configured=E.cataloguePrinting(base,p);assert.equal(E.cataloguePrinting({...base,family:'Jungle'},configured).rarity_policy,undefined);
 const result=E.evaluate(configured,1);assert.ok(result.labels.includes('No Rarity Symbol'));assert.equal(result.complete,true);
 assert.equal(E.evaluate(E.cataloguePrinting(base,{...p,rarity_symbol:'unclear'}),1).complete,false);
 assert.equal(E.evaluate(E.cataloguePrinting(base,{...p,card_type:'energy'}),1).labels.includes('No Rarity Symbol'),false);
 assert.equal(E.evaluate(E.cataloguePrinting({...base,family:'Promo'},p),1).labels.includes('No Rarity Symbol'),false);
 const rr=E.evaluate({...p,rarity_symbol:'present',rarity_text:'RR'},1);assert.ok(rr.labels.includes('RR'));assert.equal(rr.labels.some(v=>/reverse/i.test(v)),false);
});

test('191 Japanese names must actually occur in label text and cannot match as empty ASCII',()=>{
 const p=specimen();p.slab_reading.label_text='1996 Base Set ピカチュウ #25 PSA 9';p.slab_reading.family='Base Set';p.slab_reading.year='1996';p.slab_reading.subject='ピカチュウ';p.slab_reading.model='ピカチュウ';
 assert.equal(S.labelFacts(p).complete,true);p.slab_reading.subject='リザードン';p.slab_reading.model='リザードン';assert.equal(S.labelFacts(p).complete,false);
});

test('192 label title splits year, language, finish and rarity without requiring contiguous words',()=>{
 const p=specimen();p.slab_reading.family='2002 Expedition';p.slab_reading.variant='Italian Holo R';const r=S.close(p,S.labelFacts(p));
 assert.equal(r.variant,'Holo R');assert.equal(r.language,'Italian');assert.equal((r.title.match(/2002/g)||[]).length,1);
});
test('192 finish printed in the subject survives a redundant set-name variant',()=>{
 const p=specimen();Object.assign(p.slab_reading,{label_text:'1999 JAPANESE CHARIZARD-HOLO CD PROMO #6 MINT 9',subject:'Charizard',family:'CD Promo',year:'1999',card_number:'6',variant:'CD Promo'});
 assert.equal(S.labelFacts(p).variant,'HOLO');
});
