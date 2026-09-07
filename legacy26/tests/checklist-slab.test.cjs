/* Build 185: synthetic policy regressions, no network or paid recognition. */
const test=require('node:test'),assert=require('node:assert/strict');
const V=require('../src/main/assets/visual-policy.js'),E=require('../src/main/assets/editions.js');
const clue=(text,role,image_index=1)=>({text,role,image_index,certainty:'clear'});
function sports(){return {kind:'card',object_unit:'single',category:'soccer trading card',brand:'Panini',family:'Select Road to World Cup 2032',variant:'Green parallel unconfirmed',variant_scope:'commercial',identity_basis:{family:'printed',variant:'physical_evidence'},market_ready:false,model_confidence:95,photo_clues:[clue('Alex Rivera','subject'),clue('No. 21','collector_number',2),clue('TERRACE','text',2),clue('2031-32 PANINI SELECT ROAD TO WORLD CUP 2032 SOCCER','season',2),clue('PRIZM','edition',2)],object_regions:[{image_index:1,x:.1,y:.2,width:.8,height:.6,certain:true},{image_index:2,x:.1,y:.2,width:.8,height:.6,certain:true}],physical_observations:[{feature:'color',text:'Green reflective border',certainty:'clear',entity:'target',image_index:1}]};}
function slab(){return {kind:'card',object_unit:'single',category:'Pokemon card',brand:'Pokemon',family:'Fossil',variant:'Holo',variant_scope:'commercial',model_confidence:95,market_ready:false,identity_basis:{family:'printed',variant:'physical_evidence'},photo_clues:[clue('Ghostmon','subject'),clue('#94','collector_number'),clue('No. 094','collector_number')],slab_reading:{present:true,certainty:'clear',image_index:1,grader:'PSA',label_text:'1997 JAPANESE FOSSIL #94 GHOSTMON HOLO MINT 9 12345678',subject:'Ghostmon',family:'Fossil',model:'',year:'1997',card_number:'94',variant:'HOLO',grade:'MINT 9',certificate:'12345678',object_match:'matches',match_details:'The visible subject and card layout agree with the label.'},pokemon_printing:{is_pokemon:true,language:'Japanese',set_name:'Fossil',first_edition_stamp:'unclear',artwork_shadow:'not_applicable'},physical_observations:[]};}
const ref={id:'page1',url:'https://catalog.example/card',title:'1997 Japanese Fossil Ghostmon Holo #94',text:'1997 Japanese Fossil Ghostmon Holo #94',text_origin:'retrieved_page'};
test('slab confirms from full label and one matching entry without inventing stamp or authentication',()=>{
 const p=slab(),out=V.slabClosure185(p,p,[ref]);assert.equal(out.market_ready,true);assert.equal(out.slab_verification.state,'confirmed');assert.equal(out.authenticity_status,'not_assessed');assert.equal(out.slab_verification.certificate_verified,false);
 const printed=E.apply(out,p.pokemon_printing,1);assert.equal(printed.printing_check.complete,true);assert.equal(printed.printing_check.physical_check.complete,false);assert.equal(printed.variant_proof.origin,'photo_slab_label');assert.equal(printed.next_photo_request,null);
 assert.equal(V.cardKeyFacts(p).number.value,'94');assert.match(V.plan(p).query,/1997.*Fossil.*Ghostmon.*#94/);assert.doesNotMatch(V.plan(p).query,/12345678|cert verification/);assert.match(V.plan(p).query,/site:psacard.com/);
});
test('slab rejects mismatched card, uncertain label, wrong year/number/source and uncited values',()=>{
 for(const mode of ['conflict','unclear','number','year','uncited','unknown_source','wrong_reference','wrong_card','missing_variant']){
  const p=slab(),r={...ref};
  if(mode==='conflict')p.slab_reading.object_match='conflict';if(mode==='unclear')p.slab_reading.certainty='uncertain';
  if(mode==='number')p.slab_reading.card_number='95';if(mode==='year')p.slab_reading.year='1998';if(mode==='uncited')p.slab_reading.variant='Gold';
  if(mode==='unknown_source')r.text_origin='google_indexed_title';if(mode==='wrong_reference'){r.text=r.title='1998 Japanese Fossil Ghostmon #95';}
  if(mode==='wrong_card')p.photo_clues[1].text='#95';if(mode==='missing_variant'){p.slab_reading.variant='';p.variant='Parallel unconfirmed';}
  assert.notEqual(V.slabClosure185(p,p,[r]).market_ready,true,mode);
 }
});
test('label path handles non-card slabbed objects by model and year',()=>{
 const p=slab();p.kind='object';p.category='graded comic';p.photo_clues=[];Object.assign(p.slab_reading,{model:'Example Comics 12',family:'',subject:'',card_number:'',label_text:'2005 Example Comics 12 First printing 9.0',year:'2005',variant:'First printing',grade:'9.0',certificate:''});
 const r={...ref,title:'2005 Example Comics 12 First printing',text:'2005 Example Comics 12 First printing'};
 assert.equal(V.slabClosure185(p,p,[r]).market_ready,true);
});
test('checklist routing retains keys and has a bounded fallback without generic discovery',()=>{
 const p=sports(),a=V.plan(p),b=V.fallbackPlan(p,[a.query]);assert.match(a.query,/2031-32 PANINI SELECT/);assert.match(a.query,/Alex Rivera/);assert.match(a.query,/#21/);assert.match(a.query,/terrace/);assert.match(a.query,/site:paniniamerica.net/);assert.doesNotMatch(b.query,/site:/);assert.match(b.query,/#21/);assert.equal(b.duplicate,false);assert.equal(V.googleFirst(p),false);
 for(const [brand,domain] of [['Topps','topps.com'],['Upper Deck','upperdeck.com'],['One Piece','onepiece-cardgame.com'],['Pokemon','pokemon.com']]){p.brand=brand;assert.ok(V.plan(p).domains.includes(domain));}
});
test('serial omitted by OCR searches both whole sides and rotates edges without guessing a fraction',()=>{
 const p=sports(),req=V.detailRequests(p,[],2);assert.equal(req.length,2);assert.ok(req.every(r=>r.role==='serial'&&r.serial_search));assert.deepEqual(req.map(r=>r.image_index),[1,2]);assert.deepEqual(V.detailRegion(req[1],p).rotations,[90,270]);
 const bad=V.applyPhotoDetails(p,[{clue_index:req[1].clue_index,role:'serial',certainty:'clear',text:'215'}],req,[]);assert.equal(V.serialEvidence184(bad.value),null);
 const good=V.applyPhotoDetails(p,[{clue_index:req[1].clue_index,role:'serial',certainty:'clear',text:'2/5'}],req,[]);assert.equal(V.serialEvidence184(good.value).value,'2/5');assert.equal(V.cardKeyFacts(good.value).number.value,'21');
});
test('subset family extraction does not reject matching sports number; PRIZM is not the subset',()=>{
 const p=sports();p.photo_clues.push(clue('2/5','serial',2));
 const title='2031-32 Panini Select Road to World Cup 2032 Soccer Checklist',text=title+'\nBase Terrace Set\n21 Alex Rivera, Nigeria\nBase Terrace Parallels\nGreen /5\nGold /10';
 const r={...ref,title,text},fields=[['family','Base Terrace Set','none','Base Terrace Set'],['subject','Alex Rivera','none','21 Alex Rivera, Nigeria'],['catalog_number','21','catalog_number','21 Alex Rivera, Nigeria'],['year','2031-32','season','2031-32']].map(([field,value,number_kind,quote])=>({field,value,number_kind,quote,reference_id:'page1',scope:'target',evidence:'text'}));
 const out=V.validate(p,{candidates:[{unit:'single',same_unit:true,decision:'match',identity_level:'exact',physical_ambiguity:true,ambiguity_scope:'variant',variant_status:'unresolved',fields,matches:[],conflicts:[]}]},[r]);
 assert.equal(out.catalogue_core_verified,true,JSON.stringify(out));assert.equal(out.visual_candidates[0].identifier_agreements[0].agrees,true);assert.equal(out.market_ready,false);
 const entry={reference_id:'page1',unit:'single',scope:'base',variant:'Green',section_quote:'Base Terrace Parallels\nGreen /5\nGold /10',variant_quote:'Green /5'};
 const closed=V.specificationClosure184(out,p,{entries:[entry]},[r]);assert.equal(closed.market_ready,true);assert.equal(closed.serial_number,'2/5');
 const wrong={...entry,section_quote:entry.section_quote.replace('Terrace','Mezzanine')};assert.notEqual(V.specificationClosure184(out,p,{entries:[wrong]},[{...r,text:text+'\n'+wrong.section_quote}]).market_ready,true);
});
test('Japanese pre-2001 printing policy excludes stamps only after catalogue year verification',()=>{
 const p=slab();p.catalogue_core_verified=true;p.core_identity={fields:[{field:'year',value:'1997',origin:'catalogue',number_kind:'year'}]};
 const result=E.cataloguePrinting(p,p.pokemon_printing);assert.equal(result.stamp_policy.rule,'japanese_release_before_2001');assert.equal(E.evaluate(result,1).complete,true);
 p.core_identity.fields[0].value='2001';assert.equal(E.cataloguePrinting(p,p.pokemon_printing).stamp_policy,undefined);
 p.core_identity.fields[0].value='1997';p.pokemon_printing.first_edition_stamp='present';assert.equal(E.evaluate(E.cataloguePrinting(p,p.pokemon_printing),1).contradiction,true);
});
test('observed copyright survives final identity without fabricated catalogue year',()=>{
 const p=sports();p.photo_clues=p.photo_clues.filter(c=>c.role!=='season');p.photo_clues.push(clue('© 2003 Example','copyright'));p.catalogue_core_verified=true;p.core_identity={fields:[]};
 const out=V.preserveObservedYear185(p,p);assert.equal(out.observed_year.value,'2003');assert.equal(out.observed_year.kind,'copyright');assert.equal(out.source_confirmed_year,undefined);
});
test('slab wording cannot override a clearly conflicting physical edition or product season',()=>{
 const p=slab();p.slab_reading.label_text+=' 1st Edition';p.slab_reading.variant='1st Edition';p.pokemon_printing.first_edition_stamp='absent';assert.equal(V.slabFacts185(p),null);
 const q=slab();q.photo_clues.push(clue('1999','season'));assert.equal(V.slabFacts185(q),null);
});
test('a serial recovered only on the second side survives JSON round-trip without empty clue slots',()=>{
 const p=sports(),req=V.detailRequests(p,[],2),out=V.applyPhotoDetails(p,[{clue_index:req[1].clue_index,text:'2/5',role:'serial',certainty:'clear'}],req,[]).value;
 const restored=JSON.parse(JSON.stringify(out));assert.equal(V.serialEvidence184(restored).value,'2/5');assert.equal(V.cardKeyFacts(restored).number.value,'21');assert.equal(restored.photo_clues.includes(null),false);
});
