/* Build 188 provider responses are replayed literally. Added or altered evidence
 * below is explicitly synthetic; these tests never contact paid providers. */
const test=require('node:test'),assert=require('node:assert/strict');
const V=require('../src/main/assets/visual-policy.js');
const recorded=require('./fixtures/diagnostics-188.json'),copy=x=>structuredClone(x);
const photo=name=>V.reconcilePhotoOcr(copy(recorded[name].visionResult),copy(recorded[name].visualAssistance.photoOcr||[]));
function boxCore(){
 const p=photo('topps'),refs=copy(recorded.topps.visualAssistance.textReferences);
 return {p,refs,core:V.priorityClosure188(V.auditIdentity(p),p,refs)};
}
function politoed(){
 const d=recorded.politoed,p=photo('politoed'),refs=copy(d.visualAssistance.textReferences);
 const entries=d.phases.find(p=>p.stage==='flipcheck_card_keys').result.entries;
 const candidates=entries.map(e=>({unit:'single',decision:'match',same_unit:true,identity_level:'exact',physical_ambiguity:true,ambiguity_scope:'variant',variant_status:'unresolved',matches:[],conflicts:[],fields:copy(e.fields)}));
 return {p,refs,reply:{candidates,detail_needed_from:'none'},partial:V.validate(V.auditIdentity(p),{candidates,detail_needed_from:'none'},refs)};
}
const release=(title='Skyridge (GCC) - Pokémon Central Wiki',date='Data di uscita: 12 maggio 2003')=>({id:'synthetic_release',url:'https://catalog.example/skyridge',title,text:title+'\n'+date,text_origin:'retrieved_page'});
test('189 actual Topps proven core survives the recorded unrelated single-card comparison',()=>{
 const {p,refs,core}=boxCore();assert.equal(core.identity_evidence.score,90);
 const d=recorded.topps,reply=d.phases.find(p=>p.stage==='flipcheck_visual_comparison').result;
 const images=copy(d.visualAssistance.retainedReferences).map(r=>({...r,image_data:'offline image availability marker'}));
 const result=V.validate(core,reply,images);
 assert.equal(result.catalogue_core_verified,true);assert.equal(result.core_identity.status,'confirmed');
 assert.deepEqual(result.core_identity.fields,core.core_identity.fields);assert.deepEqual(result.identity_evidence,core.identity_evidence);
 assert.equal(result.model_verified,true);assert.equal(result.identity_status,'confirmed');assert.equal(result.market_ready,false);
 assert.doesNotMatch(result.model,/Hobby|Mega|Ayton|Barkley/);assert.equal(result.core_retained_from,'verified_identity_checkpoint');
 assert.equal(V.priorityClosure188(result,p,refs).identity_evidence.score,90);
});
test('189 fetch failure, unavailable images and budget exceptions preserve the verified checkpoint',()=>{
 const {p,core}=boxCore();
 for(const assistance_state of ['failed','no_reference','budget_exhausted','source_detail_needed']){
  const result=V.retainIdentity189(core,{...p,assistance_state,core_identity:{status:'partial',fields:[]},catalogue_core_verified:false},{stage:assistance_state});
  assert.equal(result.core_identity.status,'confirmed');assert.equal(result.catalogue_core_verified,true);
  assert.deepEqual(result.identity_evidence,core.identity_evidence);assert.equal(result.market_ready,false);
 }
 assert.equal(V.retainIdentity189(core,null),core);assert.equal(V.retainIdentity189(null,null),null);
});
test('189 a genuinely contradictory clear reading of the original product year remains blocking',()=>{
 const {p,core}=boxCore(),changed=copy(p);
 changed.photo_clues=changed.photo_clues.map(c=>c.role==='season'?{...c,text:'2024/25',origin:'focused_photo_reread'}:c);
 const result=V.retainIdentity189(core,{...core},{stage:'photo_reread',photo:changed});
 assert.equal(result.core_identity.status,'disputed');assert.equal(result.catalogue_core_verified,false);assert.equal(result.photo_core_verified,false);
 assert.equal(result.market_ready,false);assert.equal(result.normalized_query,'');assert.equal(result.identity_evidence.state,'disputed');
 assert.equal(result.identity_target_conflicts[0].observed_value,'2024-25');assert.ok(result.unresolved_identity_fields.includes('year'));
});
test('189 an uncertain read, a web claim or unattested comparison cannot revoke original identity',()=>{
 const {p,core}=boxCore();
 for(const change of [{certainty:'uncertain',origin:'focused_photo_reread'},{certainty:'clear',origin:'web_hypothesis'},{certainty:'clear',origin:'reference_image_ocr'}]){
  const changed=copy(p);changed.photo_clues=changed.photo_clues.map(c=>c.role==='season'?{...c,text:'2024/25',...change}:c);
  const result=V.retainIdentity189(core,{...p,catalogue_core_verified:false},{photo:changed});
  assert.equal(result.core_identity.status,'confirmed');assert.equal(result.identity_target_conflicts,undefined);
 }
});
test('189 another confident catalogue candidate cannot overwrite the already proven core',()=>{
 const {core}=boxCore(),other=copy(core);
 other.core_identity.fields.find(f=>f.field==='year').value='2024-25';other.core_identity.model='2024-25 other product';other.model_confidence=99;
 const result=V.retainIdentity189(core,other,{stage:'later_catalogue_candidate'});
 assert.equal(result.core_identity.fields.find(f=>f.field==='year').value,'2025-26');assert.equal(result.core_identity.model,core.model);
 assert.equal(result.identity_retention.reason,'different_reference_identity');assert.equal(result.market_ready,false);
});
test('189 a 99 confidence without proven fields cannot acquire a retained identity',()=>{
 const p={kind:'card',model:'Guess',model_confidence:99,market_ready:false},next={...p,assistance_state:'failed'};
 assert.deepEqual(V.retainIdentity189(p,next),next);assert.equal(V.priorityClosure188(p,p,[]).identity_evidence,undefined);
});
test('189 actual Politoed keeps three independently verified keys while its year remains missing',()=>{
 const {p,partial}=politoed();assert.equal(V.cardKeyFacts(p).number.value,'H23/H32');
 assert.equal(partial.core_identity.status,'partial');assert.equal(partial.catalogue_core_verified,false);assert.equal(partial.market_ready,false);
 assert.equal(partial.catalogue_key_evidence.state,'partial');assert.equal(partial.catalogue_key_evidence.score,75);
 assert.deepEqual(partial.core_identity.pending_fields,['year']);assert.equal(partial.next_photo_request,null);
 const values=Object.fromEntries(partial.core_identity.fields.map(f=>[f.field,f.value]));
 assert.deepEqual(values,{subject:'Politoed',family:'Skyridge',catalog_number:'H23/H32'});assert.equal(partial.source_confirmed_year,undefined);
});
test('189 a repeated extraction or failed continuation cannot discard exact partial catalogue keys',()=>{
 const {p,refs,reply,partial}=politoed(),again=V.validate(partial,reply,refs);
 assert.deepEqual(again.core_identity.fields,partial.core_identity.fields);
 const failed=V.retainIdentity189(partial,{...p,assistance_state:'failed'},{stage:'missing_year_fetch'});
 assert.deepEqual(failed.catalogue_key_evidence,partial.catalogue_key_evidence);assert.deepEqual(failed.core_identity.pending_fields,['year']);
 assert.equal(failed.market_ready,false);assert.equal(failed.catalogue_core_verified,false);
});
test('189 recorded Politoed pages do not contain a release year and relative publication age cannot invent one',()=>{
 const {p,refs,partial}=politoed(),result=V.targetedDateEvidence189(partial,p,refs);
 assert.equal(result,partial);assert.equal(result.source_confirmed_year,undefined);
 const relative=release(undefined,'Published: 23.3 years ago; Crawled: 6 days ago; Copyright website 2026');
 assert.equal(V.targetedDateEvidence189(partial,p,[...refs,relative]),partial);
});
test('189 synthetic literal release metadata completes only the exact already verified set relationship',()=>{
 const {p,refs,partial}=politoed(),r=release(),result=V.targetedDateEvidence189(partial,p,[...refs,r]);
 assert.equal(result.catalogue_core_verified,true);assert.equal(result.core_identity.status,'confirmed');assert.equal(result.source_confirmed_year,'2003');
 assert.equal(result.identity_evidence.score,90);assert.equal(result.market_ready,false);assert.equal(result.release_resolution.origin,'verified_card_set_release');
 const year=result.core_identity.fields.find(f=>f.field==='year');assert.equal(year.quote,'Data di uscita: 12 maggio 2003');assert.ok(r.text.includes(year.quote));
 assert.equal(result.pokemon_printing?.first_edition_stamp,p.pokemon_printing?.first_edition_stamp);
});
test('189 wrong expansion, wrong individual card, contradictory year or website footer cannot complete the missing key',()=>{
 const {p,refs,partial}=politoed();
 for(const r of [release('Jungle (GCC) - Pokémon Central Wiki'),release('Alakazam Skyridge H1/H32'),release('Politoed Skyridge H24/H32'),release(undefined,'Website published: 2026'),release(undefined,'Release date: 2003\nPublication date: 2004')]){
  assert.equal(V.targetedDateEvidence189(partial,p,[...refs,r]),partial,r.title+' '+r.text);
 }
 const changed=copy(p);changed.photo_clues.push({text:'2004',role:'season',certainty:'clear',image_index:1});
 assert.equal(V.targetedDateEvidence189(partial,changed,[...refs,release()]),partial);
});
test('189 a synthetic exact-card release page retains its literal source quote and original collector number',()=>{
 const {p,refs,partial}=politoed(),r=release('Politoed Skyridge H23/H32','Released May 12, 2003');
 const result=V.targetedDateEvidence189(partial,p,[...refs,r]);
 assert.equal(result.release_resolution.origin,'exact_card_release');assert.equal(result.source_confirmed_year,'2003');
 assert.equal(result.core_identity.fields.find(f=>f.field==='catalog_number').value,'H23/H32');
});
