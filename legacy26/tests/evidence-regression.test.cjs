/* Synthetic regressions for evidence transitions; no provider calls or uploaded data. */
const test=require('node:test'),assert=require('node:assert/strict');
const V=require('../src/main/assets/visual-policy.js'),E=require('../src/main/assets/editions.js');
const cases=require('./fixtures/identity-cases.cjs');
const copy=x=>structuredClone(x);
function panel(){
 const d=copy(cases.panel),base={...d.vision,kind:'card',model_confidence:0,identity_basis:{family:'inferred',variant:'inferred'},unresolved_identity_fields:['family','variant']};
 const refs=d.references.map(r=>({...r,image_data:'synthetic-image-marker'}));
 const c={...d.candidates[2],variant:'',fields:d.candidates[2].fields.filter(f=>f.field!=='variant')};
 return {base,refs,c};
}
test('collector crop chooses an alphanumeric fraction, never a stage label',()=>{
 const clue={role:'collector_number',text:'17/120',certainty:'uncertain',image_index:1,region:{image_index:1,x:.7,y:.88,width:.15,height:.03,certain:false}};
 const meta={originalWidth:1000,originalHeight:1500,rect:{x:50,y:30,width:900,height:1400}};
 const ocr=[{image_index:1,state:'ok',meta,lines:[{text:'FASE 2',x:.2,y:.03,width:.1,height:.02},{text:'H17/H40',x:.72,y:.92,width:.18,height:.02}]}];
 const selection=V.detailRegion(clue,{},ocr);
 assert.equal(selection.origin,'local_ocr_region');assert.ok(selection.region.y>.87);assert.equal(selection.region.image_index,1);
 assert.equal(selection.region.x,(50+.72*900)/1000);
 const onlyStage=[{...ocr[0],lines:ocr[0].lines.slice(0,1)}];
 assert.equal(V.detailRegion(clue,{},onlyStage).origin,'whole_object_fallback');
});
test('copyright gets its own local region; ambiguous unlocated numbers use the whole object',()=>{
 const meta={originalWidth:400,originalHeight:600,rect:{x:0,y:0,width:400,height:600}};
 const ocr=[{image_index:1,state:'ok',meta,lines:[{text:'©2004 Nintendo',x:.1,y:.96,width:.6,height:.02},{text:'H17/H40',x:.8,y:.92,width:.15,height:.03},{text:'18/144',x:.7,y:.88,width:.15,height:.03}]}];
 assert.equal(V.detailRegion({role:'copyright',image_index:1},{},ocr).region.y,.96);
 assert.equal(V.detailRegion({role:'collector_number',image_index:1},{},ocr).origin,'whole_object_fallback');
});
test('a corrected number invalidates inferred identity and keeps the actual reread region',()=>{
 const base={kind:'card',brand:'Example',category:'trading card',title:'Wrong title 17/120',model:'Wrong model 17/120',family:'Guessed series',identity_basis:{family:'inferred'},pokemon_printing:{set_name:'Guessed series'},photo_clues:[{text:'17/120',role:'collector_number',certainty:'uncertain',image_index:1}],candidate_models:[{model:'Wrong model'}]};
 const request={...base.photo_clues[0],clue_index:0},region={image_index:1,x:.7,y:.92,width:.15,height:.03,certain:true};
 const changed=V.applyPhotoDetails(base,[{clue_index:0,text:'H17/H40',role:'collector_number',certainty:'clear'}],[request],[{clue_index:0,region}]);
 assert.equal(changed.value.model,'');assert.equal(changed.value.family,'');assert.equal(changed.value.pokemon_printing.set_name,'');
 assert.ok(changed.value.unresolved_identity_fields.includes('family'));assert.equal(changed.value.photo_clues[0].superseded_text,'17/120');assert.deepEqual(changed.value.photo_clues[0].region,region);
 assert.match(V.plan(changed.value).query,/H17\/H40/);assert.doesNotMatch(V.plan(changed.value).query,/17\/120|Guessed/);assert.equal(base.model,'Wrong model 17/120');
 const rejected=V.applyPhotoDetails(base,[{clue_index:0,text:'FASE 2',role:'text',certainty:'clear'}],[request],[{clue_index:0,region}]);
 assert.deepEqual(rejected.updates,[]);assert.equal(rejected.value.photo_clues[0].certainty,'uncertain');
});
test('copyright correction updates printing evidence without relabelling OCR as vision',()=>{
 const base={photo_clues:[{text:'©2001',role:'copyright',certainty:'uncertain',image_index:2}],pokemon_printing:{copyright_text:'©2001',copyright_image:1},identity_basis:{family:'inferred'}};
 const out=V.applyPhotoDetails(base,[{clue_index:0,text:'©2004 Nintendo',role:'copyright',certainty:'clear'}],[{...base.photo_clues[0],clue_index:0}],[]).value;
 assert.equal(out.pokemon_printing.copyright_image,2);assert.equal(out.pokemon_printing.copyright_text,'©2004 Nintendo');assert.equal(out.photo_clues[0].origin,'focused_photo_reread');
});
test('subject names and stage text are not physical product identifiers on cards',()=>{
 const b={kind:'card',photo_clues:[{text:'Examplemon',role:'model',certainty:'clear'},{text:'FASE 2',role:'model',certainty:'clear'},{text:'H17/H40',role:'collector_number',certainty:'clear'}]};
 assert.deepEqual(V.identifiers(b).map(c=>c.text),['H17/H40']);
 assert.equal(V.identifiers({kind:'object',photo_clues:[{text:'ALPHA',role:'model',certainty:'clear'}]}).length,1);
});
test('a focused ambiguity keeps prior verified core without declaring the variant exact',()=>{
 const {base,refs,c}=panel();const first={reply:{candidates:[c]},references:refs};
 const before=V.validate(base,first.reply,refs);assert.equal(before.catalogue_core_verified,true);assert.equal(before.market_ready,false);
 const next={...c,physical_ambiguity:true,matches:c.matches.slice(0,1)};
 const out=V.fuseComparisons(base,[first,{reply:{candidates:[next]},references:[refs[2]],purpose:'focused_reference_reread'}]);
 assert.equal(out.catalogue_core_verified,true);assert.equal(out.core_retained_from,'prior_verified_comparison');assert.equal(out.model,before.model);assert.equal(out.market_ready,false);assert.equal(out.normalized_query,'');assert.equal(out.variant_check,'pending');
 assert.equal(out.visual_candidates[0].accepted,false);
});
test('a focused actual mismatch retracts the old core, including changed typed catalogue facts',()=>{
 const {base,refs,c}=panel(),first={reply:{candidates:[c]},references:refs};
 for(const next of [{...c,decision:'different'},{...c,same_unit:false},{...c,unit:'single'},{...c,conflicts:[{scope:'target',reason:'Different printed number'}]},{...c,fields:c.fields.map(f=>f.field==='year'?{...f,value:'1963',quote:'1963'}:f)}]){
  const rerefs=refs.map(r=>({...r,text:r.text+' 1963'}));
  const out=V.fuseComparisons({...base,catalogue_core_verified:false},[first,{reply:{candidates:[{...next,physical_ambiguity:true}]},references:rerefs,purpose:'focused_reference_reread'}]);
  assert.equal(out.catalogue_core_verified,false,JSON.stringify(next));assert.equal(!!out.market_ready,false);
 }
});
test('no-variant decision requires explicit assessment and cannot dismiss a named parallel',()=>{
 const {base,refs,c}=panel();assert.equal(V.validate(base,{candidates:[c]},refs).market_ready,false);
 const resolved={...c,variant_status:'not_applicable'};
 assert.equal(V.validate(base,{candidates:[resolved]},refs).market_ready,true);
 assert.equal(V.validate({...base,variant:'Green parallel, unconfirmed'},{candidates:[resolved]},refs).market_ready,false);
 const colored={...base,physical_observations:[{feature:'color',text:'green dotted frame',certainty:'clear',entity:'target',image_index:1}]};
 assert.equal(V.validate(colored,{candidates:[resolved]},refs).market_ready,false);
});
test('short Base catalogue label preserves Shadowless checks only with a verified 102-card entry',()=>{
 const p=copy(cases.machamp.vision.pokemon_printing),identity={family:'Base',catalogue_core_verified:true,core_identity:{fields:[{field:'catalog_number',value:'9/102'}]}};
 assert.equal(E.evaluate(E.cataloguePrinting(identity,p),1).shadow,'unclear');
 assert.equal(E.evaluate(E.cataloguePrinting(identity,p),1).applicable,true);
 for(const changed of [{...identity,catalogue_core_verified:false},{...identity,core_identity:{fields:[{field:'catalog_number',value:'9/130'}]}},{...identity,family:'Base Set 2'}])assert.equal(E.evaluate(E.cataloguePrinting(changed,p),1).applicable,false);
 assert.equal(identity.family,'Base');
});
