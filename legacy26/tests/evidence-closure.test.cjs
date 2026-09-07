/* Generic regressions derived from real build-180 failure shapes. No private photos or live providers. */
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const V=require('../src/main/assets/visual-policy.js'),E=require('../src/main/assets/editions.js');
const runtime=fs.readFileSync(require.resolve('../src/main/assets/visual-runtime.js'),'utf8');
const clue=(text,role='text',certainty='clear')=>({text,role,certainty,image_index:1,region:{image_index:1,x:.7,y:.9,width:.18,height:.035,certain:certainty==='clear'}});
function fixture(){
 const base={kind:'card',object_unit:'single',category:'TCG card',brand:'Example',model:'',title:'Rivermon trading card',family:'Guessed series',identity_basis:{family:'inferred',variant:'physical_evidence'},variant:'Holographic',variant_scope:'commercial',model_confidence:60,market_ready:false,unresolved_identity_fields:['family','variant'],photo_clues:[{...clue('Rivermon'),region:{image_index:1,x:.25,y:.08,width:.3,height:.07,certain:true}},clue('H7/132','collector_number','uncertain'),clue('© 2003 Example','copyright')],physical_observations:[{feature:'finish',text:'Holographic foil artwork area',entity:'target',certainty:'clear',image_index:1}]};
 const ocr=[{state:'ok',image_index:1,text:'Rivermon\nH7/H32',meta:{originalWidth:1000,originalHeight:1300,rect:{x:0,y:0,width:1000,height:1300}},lines:[{text:'Rivermon',x:.25,y:.08,width:.3,height:.07},{text:'H7/H32',x:.72,y:.91,width:.12,height:.02}]}];
 const ref={id:'ref1',url:'https://catalog.example/series-h7',title:'Summit Rivermon H7',text:'Summit Rivermon H7. Card number H7/H32.',text_origin:'retrieved_page'};
 const fields=[['subject','Rivermon','none'],['family','Summit','none'],['catalog_number','H7/H32','card_number']].map(([field,value,number_kind])=>({field,value,quote:value,reference_id:'ref1',scope:'target',evidence:'text',number_kind}));
 return {base,ocr,ref,fields};
}
function evaluate(base,ref,fields){return V.validate(base,{candidates:[{unit:'single',decision:'match',same_unit:true,physical_ambiguity:false,identity_level:'exact',variant_status:'not_applicable',ambiguity_scope:'none',matches:[],fields,conflicts:[]}]},[ref]);}
test('observed subject survives an empty or invalidated model without selecting attack text',()=>{
 const {base}=fixture();assert.equal(V.observedSubject(base).text,'Rivermon');
 base.photo_clues.push(clue('Powerful Wave'));assert.equal(V.observedSubject(base).text,'Rivermon');
 base.model='Rivermon H7/132';const req={...base.photo_clues[1],clue_index:1};
 const out=V.applyPhotoDetails(base,[{clue_index:1,text:'H7/H32',role:'collector_number',certainty:'clear'}],[req],[]).value;
 assert.equal(out.model,'');assert.equal(out.family,'');assert.equal(V.cardKeyFacts(out).subject.value,'Rivermon');assert.equal(V.cardKeyFacts(out).number.value,'H7/H32');
});
test('a typed photographed name is independent from inferred title and model',()=>{
 const {base}=fixture();base.model='';base.title='Unresolved card';base.photo_clues[0].role='subject';
 assert.equal(V.observedSubject(base).text,'Rivermon');base.photo_clues.push(clue('Othermon','subject'));assert.equal(V.observedSubject(base),null);
});
test('a title cannot invent a subject that was never transcribed',()=>{
 const {base}=fixture();base.photo_clues=base.photo_clues.slice(1);assert.equal(V.observedSubject(base),null);
});
test('local OCR participates as a provisional physical reading without overwriting uncertain Vision',()=>{
 const {base,ocr,ref,fields}=fixture(),out=V.reconcilePhotoOcr(base,ocr),keys=V.cardKeyFacts(out);
 assert.equal(out.photo_clues[1].text,'H7/132');assert.equal(out.photo_clues[1].certainty,'uncertain');
 assert.equal(keys.number.value,'H7/H32');assert.equal(keys.number.origin,'on_device_photo_ocr');assert.equal(keys.number.certainty,'provisional');
 assert.equal(out.reading_disagreements[0].vision,'H7/132');assert.match(V.plan(out).query,/H7\/H32/);assert.equal(V.googleFirst(out),false);
 assert.equal(V.rankSources([ref],out).length,1,'the catalogue page must reach extraction even without a clear Vision number or a source year');
 const result=evaluate(out,ref,fields);assert.equal(result.catalogue_core_verified,true);assert.equal(result.market_ready,true);
 assert.equal(result.visual_candidates[0].key_evidence.date_check,'not_stated_in_entry');assert.equal(result.source_confirmed_year,'');assert.equal(result.identity_keys.date.kind,'copyright');
});
test('a conflicting clear number cannot be silently replaced by OCR or web',()=>{
 const {base,ocr,ref,fields}=fixture();base.photo_clues[1].certainty='clear';
 const out=V.reconcilePhotoOcr(base,ocr);assert.equal(V.cardKeyFacts(out),null);assert.notEqual(evaluate(out,ref,fields).catalogue_core_verified,true);
});
test('two local OCR alternatives remain unresolved and never become two certain card numbers',()=>{
 const {base,ocr}=fixture();ocr[0].lines.push({...ocr[0].lines[1],text:'H8/H32',y:.92});
 const out=V.reconcilePhotoOcr(base,ocr);assert.equal(out.ocr_number_readings.length,2);assert.equal(V.cardKeyFacts(out),null);assert.equal(out.photo_clues[1].certainty,'uncertain');
});
test('statistics and unsupported labels cannot enter the photographic key',()=>{
 const {base,ocr}=fixture();ocr[0].lines=[{text:'110 PV',x:.7,y:.9,width:.2,height:.02},{text:'2017/18',x:.7,y:.91,width:.2,height:.02}];
 assert.equal(V.reconcilePhotoOcr(base,ocr).ocr_number_readings.length,0);
});
test('an entry year conflicting with the physical copyright remains blocking',()=>{
 const {base,ocr,ref,fields}=fixture();ref.text+=' Year 2004.';fields.push({field:'year',value:'2004',quote:'Year 2004',scope:'target',evidence:'text',number_kind:'year',reference_id:'ref1'});
 assert.notEqual(evaluate(V.reconcilePhotoOcr(base,ocr),ref,fields).catalogue_core_verified,true);
});
test('a season or a bare sports number still requires a corroborated entry year',()=>{
 for(const mode of ['season','number']){
  const {base,ocr,ref,fields}=fixture();if(mode==='season')base.photo_clues[2]=clue('2003-04 Example Summit','season');
  else {base.photo_clues[1]=clue('NO. 280','collector_number');ocr[0].lines[1].text='NO. 280';ref.title='Summit Rivermon 280';ref.text='Summit Rivermon 280.';fields[2].value=fields[2].quote='280';}
  assert.notEqual(evaluate(V.reconcilePhotoOcr(base,ocr),ref,fields).catalogue_core_verified,true,mode);
 }
});
test('multiple copyright dates remain a set of constraints, never an invented release season',()=>{
 const {base,ocr,ref,fields}=fixture();base.photo_clues[2].text='© 1995, 96, 98, 99 Example © 1999 Publisher';ref.text+=' Released 1999.';
 fields.push({field:'year',value:'1999',quote:'Released 1999',scope:'target',evidence:'text',number_kind:'year',reference_id:'ref1'});
 const out=evaluate(V.reconcilePhotoOcr(base,ocr),ref,fields);assert.equal(out.catalogue_core_verified,true);assert.equal(out.identity_keys.date.value,null);assert.equal(out.identity_keys.date.kind,'copyright');assert.equal(out.source_confirmed_year,'1999');
});
test('equivalent subject refinement merges while preserving independently cited publication fields',()=>{
 const {ref}=fixture();ref.text='Northern Record 1955 issue 17. Uncut picture of Alex and Morgan on the front.';
 const f=(field,value,quote=value)=>({field,value,quote,reference_id:'ref1',scope:field==='subject'?'target':'parent',evidence:'text',number_kind:field==='year'?'year':field==='issue_number'?'issue_number':'none'});
 const old=[{...f('subject','Uncut picture of Alex and Morgan on the front.'),recovered_from:'cited_subject_description'}];
 const incoming=[f('subject','Alex and Morgan',old[0].quote),f('family','Northern Record'),f('year','1955'),f('issue_number','17')];
 const merged=V.mergeCatalogueFields(old,incoming,[ref],{});assert.equal(merged.conflicts.length,0);assert.equal(merged.fields.length,4);assert.equal(merged.fields[0].value,'Alex and Morgan');
 ref.text+=' Alternative year 1956.';const conflict=V.mergeCatalogueFields(incoming,[f('year','1956','Alternative year 1956')],[ref],{});assert.equal(conflict.conflicts[0].field,'year');
 const unrelated=V.mergeCatalogueFields([f('subject','Alex')],[f('subject','Morgan')],[ref],{});assert.equal(unrelated.conflicts[0].field,'subject');
});
test('a commercial parallel cannot become verified solely by changing market_ready',()=>{
 const {base}=fixture();base.pokemon_printing=null;base.variant='Green Prism parallel';base.identity_basis.variant='physical_evidence';base.unresolved_identity_fields=[];base.model_confidence=96;
 base.physical_observations=[{feature:'color',text:'green reflective border',entity:'target',certainty:'clear',image_index:1}];
 assert.equal(V.variantPending({...base,market_ready:true}),true);assert.equal(V.variantPending({...base,market_ready:false}),true);
 assert.equal(V.physicalVariantProof(base),null);assert.notEqual(V.auditIdentity({...base,market_ready:true,normalized_query:'Example'}).market_ready,true);
});
test('literal physical finish may close without inventing a commercial parallel',()=>{
 const {base}=fixture();assert.equal(V.variantPending(base),false);assert.equal(V.physicalVariantProof(base).kind,'observed_finish');
 base.variant='Reverse Holo';assert.equal(V.physicalVariantProof(base),null);assert.equal(V.variantPending(base),true);
});
test('an omitted box guarantee still receives a contextual original-image request',()=>{
 const base={kind:'object',object_unit:'box',category:'sealed box',photo_clues:[clue('Example Chrome')],missing_information:['Complete quantity/autograph text'],object_region:{image_index:1,x:.2,y:.01,width:.7,height:.8,certain:true}};
 const ocr=[{image_index:1,state:'ok',meta:{originalWidth:1000,originalHeight:1400,rect:{x:200,y:0,width:700,height:1140}},lines:[{text:'IN EVERYY',x:.4,y:.89,width:.2,height:.02}]}];
 const requests=V.detailRequests(base,ocr,1);assert.equal(requests.length,1);assert.equal(requests[0].missing_configuration,true);assert.equal(requests[0].text,'');assert.ok(requests[0].region.width>=.7);
 const out=V.applyPhotoDetails(base,[{clue_index:1,text:'1 autograph in every box',role:'text',certainty:'clear'}],requests,[{clue_index:1,region:requests[0].region}]).value;
 assert.equal(out.photo_clues[1].image_index,1);assert.equal(out.photo_clues[1].origin,'focused_photo_reread');assert.equal(V.evidence(out).filter(V.configuration).length,1);
});
test('reference ranking uses actual image numbers and deduplicates thumbnail resolutions',()=>{
 const {base,ocr,ref}=fixture(),photo=V.reconcilePhotoOcr(base,ocr);
 const references=[{...ref,id:'wrong',image_url:'https://media.example/wiki/a/card.jpg',ocr:{text:'25/144'},text:ref.text.repeat(3)},
  {...ref,id:'duplicate',image_url:'https://media.example/wiki/thumb/a/card.jpg/800px-card.jpg',ocr:{text:'25/144'}},
  {...ref,id:'right',image_url:'https://media.example/h7.jpg',ocr:{text:'H7/H32'},text:''}];
 const ranked=V.rankReferences(references,photo);assert.equal(ranked[0].id,'right');assert.equal(ranked.length,2);
 assert.equal(V.referenceImageUseful('https://images.example/uploads/mini_auction_company_x.jpg'),false);
});
test('original printing recovery supersedes both a wrong match and its stale rejection',()=>{
 const printing={is_pokemon:true,language:'English',set_name:'Base Set',card_type:'pokemon',first_edition_stamp:'present',stamp_image:1,stamp_location:'left of artwork',stamp_text:'Edition 1',artwork_shadow:'absent',shadow_image:1,shadow_location:'right and lower border',copyright_image:1,copyright_text:'©1995,96,98,99 Example ©1999 Publisher',slab_image:0,slab_text:''};
 const base={kind:'card',catalogue_verified:true,catalogue_core_verified:true,market_ready:true,model:'Example card',variant:'Holographic Shadowed',visual_candidates:[{variant:'1st Edition Shadowed',decision:'match',accepted:true,core_accepted:true,conflicts:[]},{variant:'1st Edition Shadowless',decision:'different',accepted:false,conflicts:[{scope:'variant',reason:'Shadow visible on original'}]}]};
 const out=E.apply(base,printing,1);assert.match(out.variant,/Shadowless/);assert.ok(out.visual_candidates.every(c=>c.superseded&&!c.accepted));assert.equal(out.variant_proof.origin,'original_photo');assert.deepEqual(E.apply(out,printing,1),out);
});
test('focused recovery tries a smaller complete request when the first plan exceeds remaining budget',()=>{
 const {base,ref}=fixture(),ctx={comparisonHistory:[],budget:{maxUsd:.03,spent:()=>.023}},env={V164:V,lastVisionReading:base,scan164:ctx,schemaFormat:(name,schema)=>({text:{format:{name,schema}}}),estimate164:b=>b.max_output_tokens===1600?.0072:.0068};
 vm.createContext(env);vm.runInContext(runtime.slice(runtime.indexOf('function comparisonBody169('),runtime.indexOf('function expandFocused174(')),env);
 const planned=env.planComparison179(base,[{data:'synthetic',meta:{imageIndex:1}}],[{...ref,image_data:'synthetic'}],ctx,'Verify the missing field.');assert.equal(planned.body.max_output_tokens,1400);assert.ok(planned.estimatedUsd<=.007);
});
