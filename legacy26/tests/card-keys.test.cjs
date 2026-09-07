/* Generic card and package regressions; no uploaded photos and no live providers. */
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const V=require('../src/main/assets/visual-policy.js');
const source=fs.readFileSync(require.resolve('../src/main/assets/visual-runtime.js'),'utf8');
const clue=(text,role='text',image_index=1)=>({text,role,image_index,certainty:'clear',region:null});
const field=(field,value,number_kind='none',reference_id='ref1')=>({field,value,number_kind,reference_id,quote:value,evidence:'text',scope:'target'});
function fixture(){
 const base={kind:'card',object_unit:'single',category:'trading card',model:'Rivermon H7/H32',family:'Guessed Series',model_confidence:88,brand:'Example',identity_basis:{family:'inferred',variant:'inferred'},variant:'Unconfirmed foil parallel',variant_scope:'commercial',unresolved_identity_fields:['family','variant'],market_ready:false,photo_clues:[clue('Rivermon'),clue('H7/H32','collector_number'),clue('© 2003 Example','copyright')],physical_observations:[{feature:'color',text:'Green foil border with blue center',entity:'target',certainty:'clear',image_index:1}]};
 const ref={id:'ref1',url:'https://catalog.example/rivermon-summit-h7',title:'2003 Summit Rivermon H7',text:'2003 Summit Rivermon H7\nRivermon, Summit, H7/H32. Release year 2003.',text_origin:'retrieved_page'};
 const fields=[field('subject','Rivermon'),field('family','Summit'),field('catalog_number','H7/H32','card_number'),field('year','2003','year')];
 const candidate={decision:'match',unit:'single',same_unit:true,identity_level:'exact',physical_ambiguity:true,ambiguity_scope:'variant',variant_status:'unresolved',fields,matches:[],conflicts:[]};
 return {base,ref,fields,candidate};
}
function evaluate(d){return V.validate(d.base,{candidates:[d.candidate],detail_needed_from:'none'},[d.ref]);}
function runtime(base){
 const ctx={comparisonHistory:[],comparisons:[],recoveries:[],provider:{},photoOcr:[],budget:new V.Budget()},env={V164:V,lastVisionReading:base,scan164:ctx,status:()=>{},guard164:()=>{},addUsage:()=>{},recordClosure164:()=>{},syncIdentity169:x=>x,parseResponseJSON:x=>x,estimate164:()=>.001,schemaFormat:(name,schema)=>({text:{format:{name,type:'json_schema',strict:true,schema}}}),recoverableText166:()=>false};
 vm.createContext(env);
 vm.runInContext(source.slice(source.indexOf('function comparisonBody169('),source.indexOf('async function resolvePrinting168(')),env);
 return {env,ctx};
}
test('a cited name, full typed number and date close the entry core without reference images',()=>{
 const d=fixture(),out=evaluate(d);
 assert.equal(out.catalogue_core_verified,true);assert.equal(out.catalogue_verified,undefined);assert.equal(out.market_ready,false);
 assert.match(out.model,/Summit/);assert.doesNotMatch(out.model,/Guessed Series/);
 assert.equal(out.visual_candidates[0].matches.length,0);assert.equal(out.visual_candidates[0].key_evidence.date_kind,'copyright');
 assert.equal(out.identity_keys.date.kind,'copyright');assert.equal(out.identity_keys.number.value,'H7/H32');
});
test('missing, conflicting, mistyped or non-entry evidence cannot close by keys',()=>{
 const changes=[
  d=>{d.base.photo_clues[2]=clue('2003 Example Summit','season');d.candidate.fields=d.fields.filter(f=>f.field!=='year');},
  d=>{d.fields[3].value=d.fields[3].quote='2004';d.ref.text+=' 2004';},
  d=>{d.fields[2].value=d.fields[2].quote='H8/H32';d.ref.text+=' H8/H32';},
  d=>d.fields[2].number_kind='serial',d=>d.fields[2].scope='listing',
  d=>d.ref.text_origin='google_indexed_title',d=>d.ref.is_collection=true,
  d=>d.ref.title='Summit catalogue list',d=>d.ref.text='Unrelated text',
  d=>d.base.photo_clues[1].certainty='uncertain',
  d=>d.base.photo_clues.push(clue('H8/H32','collector_number')),
  d=>{d.base.photo_clues[2]={...clue('2003 career totals PTS 100','season')};d.candidate.fields=d.fields.filter(f=>f.field!=='year');},
  d=>{d.base.family='Printed Other Series';d.base.identity_basis.family='printed';d.base.photo_clues.push(clue('Printed Other Series','text'));},
 ];
 for(const mutate of changes){const d=fixture();mutate(d);assert.notEqual(evaluate(d).catalogue_core_verified,true,mutate.toString());}
});
test('copyright dates are preserved as such and multiple years cannot stand in for a season',()=>{
 const d=fixture();assert.equal(V.cardKeyFacts(d.base).date.kind,'copyright');
 d.base.photo_clues[2].text='© 1995, 96, 98, 99 Example © 1999 Publisher';assert.equal(V.cardKeyFacts(d.base).date.value,null);assert.deepEqual(V.cardKeyFacts(d.base).date.values,['1995','1999']);assert.equal(V.cardKeyFacts(d.base).date.kind,'copyright');
 d.base.photo_clues.push(clue('2031-32 Example Summit','season'));assert.equal(V.cardKeyFacts(d.base).date.value,'2031-32');
});
test('three-letter photographed names remain usable with a full collector key',()=>{
 const d=fixture();d.base.model='Una H7/H32';d.base.photo_clues[0].text='Una';d.ref.title=d.ref.title.replace('Rivermon','Una');d.ref.text=d.ref.text.replaceAll('Rivermon','Una');d.fields[0].value=d.fields[0].quote='Una';
 assert.equal(evaluate(d).catalogue_core_verified,true);
});
test('card HP/PV does not consume a package-configuration reread',()=>{
 const d=fixture();d.base.photo_clues.push({...clue('110 PV'),region:{image_index:1,x:.6,y:.1,width:.2,height:.1,certain:true}});
 assert.deepEqual(V.detailRequests(d.base,[],1),[]);
});
test('two series sharing a tuple stay ambiguous while their photographed keys survive',()=>{
 const d=fixture(),other={...d.ref,id:'ref2',url:'https://catalog.example/other',title:d.ref.title.replace('Summit','Coast'),text:d.ref.text.replaceAll('Summit','Coast')};
 const candidate={...d.candidate,fields:d.fields.map(f=>({...f,reference_id:'ref2',value:f.value.replace('Summit','Coast'),quote:f.quote.replace('Summit','Coast')}))};
 const out=V.validate(d.base,{candidates:[d.candidate,candidate]},[d.ref,other]);
 assert.notEqual(out.catalogue_core_verified,true);assert.equal(out.identity_keys.subject.value,'Rivermon');assert.equal(out.core_identity.status,'partial');
});
test('a card without a variant question can close from keys but an unseen parallel cannot',()=>{
 const d=fixture();d.base.variant='';d.base.variant_scope='none';d.base.identity_basis.variant='not_applicable';d.base.unresolved_identity_fields=['family'];d.base.physical_observations=[];
 d.candidate.physical_ambiguity=false;d.candidate.variant_status='not_applicable';d.candidate.ambiguity_scope='none';
 assert.equal(evaluate(d).market_ready,true);
 d.base.variant='Green parallel?';d.base.variant_scope='commercial';d.base.identity_basis.variant='inferred';
 assert.equal(evaluate(d).market_ready,false);
});
test('a genuinely conflicting reference number remains blocking despite its page title',()=>{
 const d=fixture();d.ref.image_url='https://catalog.example/card.jpg';d.ref.image_data='synthetic';
 d.candidate.matches=[{reference_id:'ref1',feature:'code',photo_detail:'H7/H32',reference_detail:'8/144',agrees:false,reference_evidence:'image'}];
 assert.notEqual(evaluate(d).catalogue_core_verified,true);
});
test('queries use the border colour without borrowing the jersey or centre colour',()=>{
 const d=fixture();const q=V.plan(d.base).query;
 assert.match(q,/green/i);assert.doesNotMatch(q,/blue/i);assert.doesNotMatch(q,/Guessed Series/i);
});
test('known no-image placeholders cannot count as visual matches',()=>{
 for(const name of ['no-image-new.jpg','no_image.png','noimage.gif','image-not-found.png','logo.png'])assert.equal(V.referenceImageUseful('https://catalog.example/'+name),false,name);
 assert.equal(V.referenceImageUseful('https://catalog.example/card-280-green.jpg'),true);
 const d=fixture();d.candidate.fields=d.fields.filter(f=>f.field!=='family');d.ref.image_url='https://catalog.example/no-image-new.jpg';d.ref.image_data='synthetic';
 d.candidate.matches=['layout','subject'].map(feature=>({reference_id:'ref1',feature,photo_detail:'Rivermon card',reference_detail:'Rivermon card',agrees:true,reference_evidence:'image'}));
 const out=evaluate(d);assert.equal(out.visual_candidates[0].matches.length,0);assert.notEqual(out.catalogue_core_verified,true);
});
test('critical configuration rereads outrank an uncertain secondary name, but never use page text',()=>{
 const region={image_index:1,x:.4,y:.7,width:.3,height:.06,certain:true};
 const base={kind:'object',photo_clues:[{...clue('SECONDARY NAME'),certainty:'uncertain',region},{...clue('1 AUTOGRAPH OR RELIC CARD EVERY 8 BOXES'),region}]};
 let requests=V.detailRequests(base,[{image_index:1,state:'ok',text:'AUTPOXI IN EVERYY'}],1);
 assert.deepEqual(requests.map(c=>c.clue_index),[1]);assert.equal(requests[0].certainty,'clear');
 requests=V.detailRequests(base,[{image_index:1,state:'ok',text:'1 AUTOGRAPH OR RELIC CARD EVERY 8 BOXES'}],1);assert.deepEqual(requests.map(c=>c.clue_index),[0]);
 assert.deepEqual(V.detailRequests(base,[],1,[1]).map(c=>c.clue_index),[1]);
});
test('a physical configuration correction updates stale query text and reopens the variant',()=>{
 const prior={...clue('1 autograph every 8 boxes'),clue_index:0},base={kind:'object',photo_clues:[prior],layout_signature:[{term:prior.text}],catalogue_verified:true,market_ready:true};
 const out=V.applyPhotoDetails(base,[{clue_index:0,text:'1 autograph in every box',role:'text',certainty:'clear'}],[prior],[]).value;
 assert.equal(out.catalogue_verified,false);assert.equal(out.variant_needs_verification,true);assert.equal(out.photo_clues[0].origin,'focused_photo_reread');assert.equal(out.layout_signature[0].term,'1 autograph in every box');
});
test('short format citations expand only to real existing title context and keep scope checks',()=>{
 const ref={id:'ref1',url:'https://catalog.example/box',title:'Example Basketball Hobby Box',text:'Example Basketball Hobby Box. Product details.'};
 const f=field('variant','Hobby');assert.equal(V.validFields({fields:[f]},[ref],{}).length,1);
 assert.equal(V.validFields({fields:[f]},[{...ref,text:'Hobby'}],{}).length,0);
 assert.equal(V.validFields({fields:[{...f,scope:'listing'}]},[ref],{}).length,0);
 assert.equal(V.validFields({fields:[{...f,value:'Retail',quote:'Retail'}]},[ref],{}).length,0);
});
test('focused recovery uses the appearance side after entry confirmation',async()=>{
 const d=fixture(),{env,ctx}=runtime(d.base);d.ref.image_data='synthetic';d.base.photo_clues[1].image_index=2;
 let base=evaluate(d);ctx.lastComparison={reply:{candidates:[d.candidate],detail_needed_from:'target'},references:[d.ref]};ctx.comparisonHistory=[ctx.lastComparison];
 env.targetPhotos169=async()=>[1,2].map(imageIndex=>({data:'synthetic',meta:{imageIndex}}));
 let chosen;env.compareReferences167=async(b,c,photos)=>{chosen=photos[0].meta.imageIndex;return b;};
 await env.finishComparison173(base,ctx);assert.equal(chosen,1);assert.equal(ctx.focusedReview.goal,'variant');
});
test('a reference-side failure cannot spend another comparison on the same image or ask for a new front',async()=>{
 const d=fixture(),{env,ctx}=runtime(d.base);d.ref.image_url='https://catalog.example/no-image-new.jpg';d.ref.image_data='synthetic';
 ctx.lastComparison={reply:{candidates:[d.candidate],detail_needed_from:'reference'},references:[d.ref]};ctx.comparisonHistory=[ctx.lastComparison];ctx.comparisons=[{referenceIds:['ref1']}];
 env.targetPhotos169=async()=>[1,2].map(imageIndex=>({data:'synthetic',meta:{imageIndex}}));env.compareReferences167=async()=>{throw Error('must not compare placeholder');};
 const out=await env.finishComparison173(evaluate(d),ctx);assert.equal(out.assistance_state,'source_detail_needed');assert.equal(out.next_photo_request,null);assert.equal(ctx.focusedReview.state,'no_reference');assert.equal(out.core_identity.status,'confirmed');
});
test('fields are repaired after a focused comparison, retaining previously valid fields',async()=>{
 const d=fixture();d.base.variant='';d.base.variant_scope='none';d.base.identity_basis.variant='physical_evidence';d.base.unresolved_identity_fields=['family'];
 d.ref.image_data='synthetic';d.candidate.fields=d.fields.map(f=>f.field==='family'?{...f,scope:'listing'}:f);
 const matches=['layout','code'].map(feature=>({reference_id:'ref1',feature,photo_detail:feature==='code'?'H7/H32':'Rivermon layout',reference_detail:feature==='code'?'H7/H32':'Rivermon layout',reference_evidence:'image',agrees:true}));
 d.candidate.matches=matches;d.candidate.decision='possible';d.candidate.physical_ambiguity=true;d.candidate.ambiguity_scope='unknown';
 const {env,ctx}=runtime(d.base);ctx.lastComparison={reply:{candidates:[d.candidate]},references:[d.ref]};ctx.comparisonHistory=[ctx.lastComparison];
 env.targetPhotos169=async()=>[{data:'synthetic',meta:{imageIndex:1}}];
 env.compareReferences167=async()=>{const candidate={...d.candidate,decision:'match',physical_ambiguity:false,ambiguity_scope:'none',variant_status:'not_applicable'},reply={candidates:[candidate]};ctx.lastComparison={reply,references:[d.ref]};ctx.comparisonHistory.push({...ctx.lastComparison,purpose:'focused_reference_reread'});return V.validate(d.base,reply,[d.ref]);};
 let repairs=0;env.openai=async body=>{assert.equal(body.text.format.name,'flipcheck_catalogue_fields');repairs++;return {entry_scope:'exact_entry',fields:[field('family','Summit')]};};
 const out=await env.finishComparison173(evaluate(d),ctx);assert.equal(repairs,1);assert.equal(out.catalogue_verified,true);assert.equal(out.catalogue_data.length,4);assert.equal(ctx.fieldRepair.comparisonIndex,2);
});
test('the card key extraction request is text-only and establishes no visual observations',async()=>{
 const d=fixture(),{env,ctx}=runtime(d.base);
 vm.runInContext(source.slice(source.indexOf('async function verifyCardKeys180('),source.indexOf('async function visualResolve164(')),env);
 env.openai=async body=>{assert.equal(body.text.format.name,'flipcheck_card_keys');assert.equal(body.tools,undefined);assert.equal(typeof body.input,'string');return {entries:[{scope:'exact_entry',fields:d.fields}]};};
 const out=await env.verifyCardKeys180(d.base,ctx,[d.ref]);assert.equal(out.catalogue_core_verified,true);assert.equal(out.market_ready,false);assert.equal(out.core_identity.origin,'photo_and_catalogue_keys');assert.equal(ctx.cardKeyVerification.imageComparisons,0);
});
function syncFixture(){
 const d=fixture(),{env,ctx}=runtime(d.base),saved=evaluate(d);saved.core_identity.origin='photo_and_catalogue_keys';
 const keys=V.cardKeyFacts(d.base);ctx.keyCore={signature:JSON.stringify([keys.subject.value,keys.number.value,keys.date.value,keys.date.kind]),identity:saved};ctx.coreState=saved;
 Object.assign(env,{active164:()=>true,canonTerm:s=>String(s||'').toLowerCase()});
 vm.runInContext(source.slice(source.indexOf('function syncIdentity169('),source.indexOf('mergeResolvedFingerprint=function')),env);
 return {d,env,ctx,saved};
}
test('a corrected photo key invalidates both cached identity states',()=>{
 const {d,env,ctx}=syncFixture();d.base.photo_clues[1].text='H8/H32';
 const out=env.syncIdentity169({...d.base,core_identity:null,catalogue_core_verified:false});
 assert.equal(ctx.keyCore,null);assert.equal(ctx.coreState,null);assert.notEqual(out.catalogue_core_verified,true);assert.equal(out.identity_keys.number.value,'H8/H32');
});
test('a later competing key-proven series reopens only series identification',()=>{
 const {d,env,ctx,saved}=syncFixture();
 const alternate={...d.candidate,core_accepted:true,key_evidence:{reference_id:'other'},fields:d.fields.map(f=>f.field==='family'?{...f,value:'Coast'}:f)};
 const out=env.syncIdentity169({...saved,family:'Coast',visual_candidates:[alternate]});
 assert.equal(ctx.keyCore,null);assert.equal(out.assistance_state,'ambiguous');assert.equal(out.core_identity.status,'partial');assert.equal(out.market_ready,false);assert.equal(out.catalogue_core_verified,false);assert.ok(out.unresolved_identity_fields.includes('family'));assert.equal(out.identity_keys.subject.value,'Rivermon');
});
test('a different series suggested only by images cannot replace the key-proven entry',()=>{
 const {env,saved}=syncFixture(),out=env.syncIdentity169({...saved,model:'Wrong visual suggestion',family:'Coast',catalogue_verified:true,catalogue_core_verified:true,visual_candidates:[]});
 assert.equal(out.family,'Summit');assert.equal(out.core_identity.status,'confirmed');assert.equal(out.catalogue_verified,false);assert.equal(out.market_ready,false);
});
