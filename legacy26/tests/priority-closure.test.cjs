/* Actual build-187 observations and responses. Mutated negatives are labelled;
 * no new Vision, Google or Web API requests are made by these tests. */
const test=require('node:test'),assert=require('node:assert/strict');
const V=require('../src/main/assets/visual-policy.js'),E=require('../src/main/assets/editions.js');
const recorded=require('./fixtures/diagnostics-187.json'),copy=x=>structuredClone(x);
const photo=name=>V.reconcilePhotoOcr(copy(recorded[name].photo),recorded[name].assistance.photoOcr||[]);
test('188 a repeated first name earlier in a catalogue cannot hide the complete matching name',()=>{
 assert.equal(V.subjectSpan187('Victor Boniface','19 Victor Froholdt\n20 Mika Biereth\n21 Victor Boniface, Nigeria'),'Victor Boniface');
 assert.equal(V.subjectSpan187('Alex Rivera','Alex Stone\n73 Rivera, Alex'),'Rivera, Alex');
 assert.equal(V.subjectSpan187('Victor Boniface','Victor Froholdt\nBoniface Elsewhere'),'');
});
function literalCore(p,refs){
 const entries=V.checklistEntries186(p,refs),pending=V.variantPending(p);
 return V.validate(V.auditIdentity(p),{candidates:entries.map(e=>({unit:'single',decision:'match',same_unit:true,identity_level:'exact',physical_ambiguity:pending,ambiguity_scope:pending?'variant':'none',variant_status:pending?'unresolved':'not_applicable',matches:[],conflicts:[],fields:e.fields})),detail_needed_from:'none'},refs);
}
test('188 actual Cloyster reordered label fields enter the slab route and close after title search',()=>{
 const p=photo('cloyster');assert.equal(p.slab_reading.variant,'Holo R, Italian');
 assert.equal(V.slabFacts185(p).year,'2002');assert.equal(V.plan(p).kind,'slab');
 const out=V.slabClosure187(V.auditIdentity(p),p,[{url:'https://catalog.example/entry',title:'2002 Expedition Italian Cloyster #8 Holo'}],{attempted:true,completed:true});
 assert.equal(V.ready(E.apply(out,p.pokemon_printing,1)),true);assert.equal(out.slab_verification.certificate_verified,false);
 assert.equal(out.slab_reading.label_text,p.slab_reading.label_text);
});
test('188 synthetic wrong slab words and real physical conflicts cannot use the priority route',()=>{
 for(const mutate of [p=>p.slab_reading.variant='Gold Holo R',p=>p.slab_reading.year='2003',p=>p.photo_clues.find(c=>c.role==='collector_number').text='9',p=>p.slab_reading.object_match='conflict']){
  const p=photo('cloyster');mutate(p);assert.equal(V.slabFacts185(p),null);
 }
});
test('188 actual Vileplume full fraction agrees with OCR and a separate 45 cannot invalidate it',()=>{
 const p=photo('vileplume');assert.equal(V.cardKeyFacts(p).number.value,'15/64');
 assert.ok(p.ocr_auxiliary_readings.some(r=>r.text==='45'));assert.equal(p.reading_disagreements.length,0);
 assert.equal(recorded.vileplume.result.ocr_number_readings.some(r=>r.text==='45'),true);
 const conflicting=copy(p);conflicting.ocr_number_readings.push({...conflicting.ocr_number_readings[0],text:'16/64'});
 assert.equal(V.cardKeyFacts(conflicting),null);
});
test('188 a model flag does not make an untranscribed set a binding photographic fact',()=>{
 const p=photo('vileplume');assert.equal(p.family,'Base Set');assert.equal(p.identity_basis.family,'printed');
 assert.equal(V.familyGrounded188(p),false);assert.doesNotMatch(V.plan(p).query,/Base Set|green|yellow/i);assert.match(V.plan(p).query,/1999.*Vileplume.*15\/64/);
 const query=V.plan({...p,family:'Invented Collection'}).query;assert.doesNotMatch(query,/Invented/);
});
test('188 actual Vileplume closes from literal entry and original printing, with an auditable 90-point core',()=>{
 const p=photo('vileplume'),refs=recorded.vileplume.assistance.textReferences;
 const core=literalCore(p,refs);assert.equal(core.catalogue_core_verified,true);assert.equal(core.family,'Jungle');
 const out=V.priorityClosure188(E.apply(core,E.cataloguePrinting(core,p.pokemon_printing),1),p,refs);
 assert.equal(V.ready(out),true);assert.equal(out.identity_evidence.score,90);assert.equal(out.identity_evidence.score_kind,'verified_key_coverage');assert.equal(out.core_identity.status,'confirmed');
 assert.equal(out.pokemon_printing.first_edition_stamp,'absent');assert.equal(out.source_confirmed_year,'1999');
 assert.equal(out.identity_evidence.evidence.every(e=>e.sources.some(s=>s.url&&s.quote)),true);
});
test('188 complete indexed Doncic document is preserved when direct retrieval yielded only a comment',()=>{
 const p=photo('doncic'),d=recorded.doncic;
 assert.equal(d.assistance.textReferences.find(r=>r.id==='page1').text.length,147);
 const refs=V.webDocuments188(d.result.raw_web_results,p),core=literalCore(p,refs),out=V.priorityClosure188(core,p,refs);
 assert.equal(out.catalogue_core_verified,true);assert.equal(out.identity_evidence.score,90);
 assert.equal(out.core_identity.fields.some(f=>f.field==='catalog_number'&&f.value==='280'),true);
 assert.equal(out.variant_check,'pending');assert.equal(out.market_ready,false); // no invented parallel proof
 assert.ok(refs.find(r=>r.id==='webdoc1').original_text.includes('280 Luka Doncic'));
 assert.equal(refs.some(r=>/mosaic/i.test(r.title)),false);
});
test('188 a 99 self-reported confidence or matching number in a short snippet cannot create evidence',()=>{
 const p={...photo('doncic'),model_confidence:99};
 assert.deepEqual(V.webDocuments188([{url:'https://catalog.example/item',title:'2018-19 Prizm Luka Doncic #280',text:'2018-19 Prizm Luka Doncic #280'}],p),[]);
 const out=V.priorityClosure188({...p,market_ready:false},p,[]);assert.equal(out.identity_evidence,undefined);assert.equal(out.market_ready,false);
});
test('188 mutated wrong number, year and adjacent-row borrow remain rejected by priority keys',()=>{
 const p=photo('doncic'),refs=V.webDocuments188(recorded.doncic.result.raw_web_results,p);
 for(const mutate of [r=>{r.text=r.text.replaceAll('280 Luka Doncic','279 Luka Doncic');},r=>{r.title=r.title.replace('2018-19','2019-20');r.text=r.text.replaceAll('2018-19','2019-20');},r=>{r.text=r.text.replaceAll('280 Luka Doncic','280 Other Player\n281 Luka Doncic');}]){
  const bad=copy(refs[0]);mutate(bad);assert.notEqual(literalCore(p,[bad]).catalogue_core_verified,true);
 }
});
test('188 actual Topps query is for a box and Chrome Black cannot reach image comparison',()=>{
 const p=photo('topps'),plan=V.plan(p);assert.equal(plan.kind,'box_configuration');assert.equal(V.googleFirst(p),false);
 assert.match(plan.query,/2025-26.*Topps Chrome Basketball Update Series.*box.*configuration/);
 assert.doesNotMatch(plan.query,/blue|black|COOPER|VICTOR|parallel|#|checklist parallels/i);
 const refs=recorded.topps.assistance.retainedReferences;
 assert.equal(V.rankReferences(refs,p).some(r=>/Chrome.*Black/i.test(r.title)),false);
 assert.equal(V.catalogueScope186(p,{title:'2025-26 Topps Chrome Updates Basketball Hobby Box',text:'2025-26 Topps Chrome Updates Basketball Hobby Box'},true).eligible,true);
});
test('188 Topps family can close from photo and the matching product text without inventing Hobby',()=>{
 const p=photo('topps'),refs=recorded.topps.assistance.textReferences,out=V.priorityClosure188(V.auditIdentity(p),p,refs);
 assert.equal(out.core_identity.status,'confirmed');assert.equal(out.catalogue_core_verified,true);assert.equal(out.identity_evidence.score,90);
 assert.equal(out.market_ready,false);assert.doesNotMatch(out.model,/Hobby|Mega/);
 const withNoise=V.priorityClosure188({...out,missing_information:['Numero carta: non applicabile','Checklist variante blu e tiratura','Formato commerciale']},p,refs);
 assert.deepEqual(withNoise.missing_information,['Formato commerciale']);
});
test('188 a synthetic box source with another year or another product cannot obtain a 90-point closure',()=>{
 const p=photo('topps');
 for(const title of ['2024-25 Topps Chrome Updates Basketball Hobby Box','2025-26 Topps Chrome Black Basketball Hobby Box']){
  const out=V.priorityClosure188(V.auditIdentity(p),p,[{id:'bad',url:'https://catalog.example/box',title,text:title,text_origin:'retrieved_page'}]);assert.equal(out.identity_evidence,undefined);
 }
});
test('188 literal box configuration accepts the exact guarantee and rejects mixed compatible formats',()=>{
 const p=copy(require('./fixtures/diagnostics-170.json').box.vision),title='2025-26 Topps Chrome Update Series Basketball Hobby Box';
 const ref={id:'page1',url:'https://catalog.example/box',title:'Catalogue',text:title+'\n1 autograph in every box.',text_origin:'retrieved_page'};
 const closed=V.specificationClosure184(V.auditIdentity(p),p,V.literalBoxSpecifications188(p,[ref]),[ref]);assert.equal(closed.market_ready,true);assert.equal(closed.variant,'Hobby Box');
 const mixed={...ref,text:ref.text+'\n2025-26 Topps Chrome Update Series Basketball Mega Box\n1 autograph in every box.'};
 assert.notEqual(V.specificationClosure184(V.auditIdentity(p),p,V.literalBoxSpecifications188(p,[mixed]),[mixed]).market_ready,true);
});
test('188 Ruby Wave cannot be selected as visual proof of the actual green Doncic',()=>{
 const p=photo('doncic'),refs=recorded.doncic.assistance.retainedReferences;
 assert.equal(V.rankReferences(refs,p).some(r=>/ruby-wave/.test(r.url)),false);
});
test('188 Boniface retains the working exact checklist plus physical 2/5 closure',()=>{
 const d=recorded.boniface,p=photo('boniface'),req=V.detailRequests(p,[],2);
 const reply=d.phases.find(x=>x.stage==='flipcheck_photo_detail').result.details;
 const read=V.applyPhotoDetails(p,reply,req,[]).value,refs=d.assistance.textReferences;
 const core=literalCore(read,refs),out=V.specificationClosure184(core,read,V.literalSpecifications187(read,refs),refs);
 assert.equal(out.market_ready,true);assert.equal(out.variant,'Green');assert.equal(out.serial_number,'2/5');
 const wrong=copy(read);wrong.photo_clues.find(c=>c.role==='serial').text='2/7';
 assert.notEqual(V.specificationClosure184(literalCore(wrong,refs),wrong,V.literalSpecifications187(wrong,refs),refs).market_ready,true);
});
