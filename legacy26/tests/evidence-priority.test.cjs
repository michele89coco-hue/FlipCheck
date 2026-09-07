/* Software regressions, with fabricated observations and frozen comparisons. No live AI. */
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const V=require('../src/main/assets/visual-policy.js');
const clone=x=>structuredClone(x);
const clue=(text,role='text',image_index=1)=>({text,role,image_index,certainty:'clear',region:null});
function card(){return {kind:'card',category:'sports trading card',object_unit:'single',model:'Alex Rivera #73',family:'Aurora Prizm',brand:'Aurora',model_confidence:96,market_ready:false,variant:'Green parallel, unconfirmed',variant_scope:'commercial',identity_basis:{family:'printed',variant:'inferred'},unresolved_identity_fields:['variant'],photo_clues:[clue('NO. 73','collector_number',2),clue('2031-32 AURORA PRIZM','season',2),clue('ALEX RIVERA'),clue('EAST CITY'),clue('ROOKIE CARD')],physical_observations:[{feature:'color',text:'Green foil border',entity:'target',certainty:'clear',image_index:1}]};}
const field=(field,value,quote=value,scope='target',number_kind='none')=>({field,value,quote,scope,number_kind,evidence:'text',reference_id:'ref1'});
const matches=()=>[{reference_id:'ref1',feature:'layout',photo_detail:'Subject on diagonal layout',reference_detail:'Subject on diagonal layout',agrees:true,reference_evidence:'image'},{reference_id:'ref1',feature:'subject',photo_detail:'Alex Rivera',reference_detail:'Alex Rivera',agrees:true,reference_evidence:'image'}];
function printingCase(){
 const base={...card(),category:'Pokemon card',brand:'Pokemon',model:'Examplemon 9/102',family:'Base Set',identity_basis:{family:'inferred',variant:'physical_evidence'},photo_clues:[clue('Examplemon'),clue('9/102','collector_number'),clue('1st Edition','edition')],pokemon_printing:{is_pokemon:true},physical_observations:[]};
 const ref={id:'ref1',url:'https://catalog.example/entry',title:'Examplemon Base Set 9/102',text:'Examplemon Base Set 9/102 1999',image_data:'synthetic comparison marker'};
 const candidate={unit:'single',decision:'possible',same_unit:true,physical_ambiguity:true,identity_level:'family',variant_status:'unresolved',conflicts:[],matches:matches(),fields:[field('family','Base Set'),field('subject','Examplemon'),field('year','1999','1999','target','year'),field('catalog_number','9/102','9/102','target','card_number')]};
 return {base,ref,candidate,reply:{candidates:[candidate],physical_detail_needed:'Verificare il bordo destro e inferiore per la presenza di ombra nella foto originale.',detail_needed_from:'target'}};
}
test('printed series, subject, season and typed number preserve confirmed photo core while parallel remains pending',()=>{
 const base=card(),out=V.auditIdentity(base);
 assert.equal(out.core_identity.status,'confirmed');assert.equal(out.core_identity.origin,'photo');assert.equal(out.model_verified,true);assert.equal(out.status,'identified');assert.equal(out.model_confidence,96);assert.equal(out.market_ready,false);
 assert.equal(out.core_identity.fields.find(f=>f.field==='catalog_number').quote,'NO. 73');assert.ok(out.core_identity.fields.every(f=>f.origin==='photo'));
 assert.equal(V.googleFirst(base),false);assert.match(V.plan(base).query,/green/i);assert.match(V.plan(base).query,/73/);assert.match(V.plan(base).query,/2031-32/);assert.doesNotMatch(V.plan(base).query,/unconfirmed/);
});
test('number and year alone, inferred series, copyright, serials, stats and unread numbers cannot establish photo core',()=>{
 for(const change of [
  {identity_basis:{family:'inferred',variant:'inferred'}},{family:'Unprinted Series'},
  {photo_clues:card().photo_clues.map(c=>c.role==='season'?{...c,text:'©2031 Aurora Prizm',role:'copyright'}:c)},
  {photo_clues:card().photo_clues.map(c=>c.role==='collector_number'?{...c,role:'serial'}:c)},
  {photo_clues:card().photo_clues.map(c=>c.role==='collector_number'?{...c,role:'slab_certificate'}:c)},
  {photo_clues:card().photo_clues.map(c=>c.role==='collector_number'?{...c,certainty:'uncertain'}:c)},
  {photo_clues:[...card().photo_clues,clue('74','collector_number')]},
  {photo_clues:card().photo_clues.map(c=>c.role==='season'?{...c,text:'2031-32 AURORA PRIZM career totals PTS 203'}:c)},
  {model:'Unprinted player #73'}
 ])assert.equal(V.photoIdentity({...card(),...change}),null,JSON.stringify(change));
});
test('an unrelated reference cannot erase the printed tuple or verify an unproved parallel',()=>{
 const b=card(),r={id:'ref1',url:'https://catalog.example/wrong',text:'Other player 2031-32 Aurora Prizm 73',image_data:'synthetic marker'};
 const out=V.validate(b,{candidates:[{decision:'different',same_unit:true,unit:'single',identity_level:'exact',physical_ambiguity:false,conflicts:[{scope:'target',reason:'Reference shows another player'}],matches:[],fields:[]}],detail_needed_from:'reference'},[r]);
 assert.equal(out.core_identity.origin,'photo');assert.equal(out.core_identity.status,'confirmed');assert.match(out.model,/ALEX RIVERA/);assert.notEqual(out.catalogue_verified,true);assert.equal(out.market_ready,false);
});
test('printing-only ambiguity keeps a cited card core without falsely resolving its printing',()=>{
 const d=printingCase(),out=V.validate(d.base,d.reply,[d.ref]);
 assert.equal(out.catalogue_core_verified,true);assert.equal(out.core_identity.status,'confirmed');assert.equal(out.visual_candidates[0].ambiguity_scope,'variant');assert.notEqual(out.catalogue_verified,true);assert.equal(out.market_ready,false);
 for(const change of [{physical_detail_needed:'Verificare numero carta e bordo ombra'},{physical_detail_needed:'Dettaglio non specificato',detail_needed_from:'none'}])assert.notEqual(V.validate(d.base,{...d.reply,...change},[d.ref]).catalogue_core_verified,true);
});
test('a real number conflict is not converted to a printing-only uncertainty',()=>{
 const d=printingCase();d.candidate.conflicts=[{scope:'target',reason:'Different physical number'}];
 assert.notEqual(V.validate(d.base,d.reply,[d.ref]).catalogue_core_verified,true);
 d.candidate.conflicts=[];d.candidate.matches.push({reference_id:'ref1',feature:'code',photo_detail:'9/102',reference_detail:'10/102',reference_evidence:'image',agrees:false});
 assert.notEqual(V.validate(d.base,d.reply,[d.ref]).catalogue_core_verified,true);
});
test('source compaction retains original evidence and remains bounded across repeated compaction',()=>{
 const b={photo_clues:[clue('1 AUTOGRAPH EVERY BOX!')]},raw={id:'ref1',title:'Aurora Hobby Box',text:'Intro\n'+('Long navigation '.repeat(80))+'\n1 Autograph\nWith one guaranteed autograph per box.'};
 const c=V.compactReference(raw,b,180),again=V.compactReference(c,b,90);
 assert.equal(V.referenceText(c),raw.text);assert.equal(V.referenceText(again),raw.text);assert.ok(c.text.length<=180);assert.ok(again.text.length<=90);
 assert.equal(V.quantityMatches('1 AUTOGRAPH EVERY BOX!','one guaranteed autograph per box'),true);
 assert.equal(V.quantityMatches('1 AUTOGRAPH EVERY BOX!','two guaranteed autographs per box'),false);
 assert.equal(V.quantityMatches('1 AUTOGRAPH EVERY BOX!','one autograph per pack'),false);
 assert.equal(V.quantityMatches('2 batteries','two batteries'),true);
 assert.equal(V.configuration({text:'Two illustrated portraits side by side',role:'configuration'}),false);
});
test('literal fields validate against retained source evidence, never an invented quote',()=>{
 const b=card(),r=V.compactReference({id:'ref1',url:'https://catalog.example/item',title:'Aurora',text:'2031-32\nAurora Prizm\n'+('Navigation '.repeat(50))+'\nSpecific published entry'},b,30);
 assert.equal(V.validFields({fields:[field('model','Specific published entry')]},[r],b).length,1);
 assert.equal(V.validFields({fields:[field('model','Invented published entry')]},[r],b).length,0);
});
test('zero valid fields remains recoverable with actual image matches; listing IDs stay invalid',()=>{
 const b={kind:'object',object_unit:'panel',category:'collectible portrait panel',market_ready:false,variant_scope:'physical_description',photo_clues:[clue('Visible name')]},r={id:'ref1',url:'https://auction.example/9876543',title:'Publication 1955',text:'1955, issue 17',image_data:'synthetic marker'};
 const c={decision:'match',identity_level:'exact',unit:'panel',same_unit:true,physical_ambiguity:false,conflicts:[],matches:matches(),fields:[field('year','1955','1955','listing','year')]};
 const out=V.validate(b,{candidates:[c]},[r]);assert.equal(out.visual_candidates[0].fields.length,0);assert.equal(V.recoverableComparison(out.visual_candidates[0]),true);assert.equal(out.visual_candidates[0].field_issues[0].scope,'listing');
 for(const bad of [{matches:[]},{decision:'different'},{conflicts:[{scope:'target',reason:'Wrong object'}]},{physical_ambiguity:true}])assert.equal(V.recoverableComparison({...out.visual_candidates[0],...bad}),false);
});
test('card reference ranking excludes unrelated product thumbnails even on a relevant player page',()=>{
 const b=card(),common={title:'Alex Rivera cards',text:'2031-32 Aurora Prizm Alex Rivera 73'};
 const refs=[{...common,id:'binder',image_caption:'440 pockets trading card binder'},{...common,id:'box',image_caption:'Factory sealed Mega Box'},{...common,id:'card',image_caption:'Alex Rivera Green Prizm card — front'}];
 assert.deepEqual(V.rankReferences(refs,b).map(r=>r.id),['card']);
});
function runtimePlan(base){
 const text=fs.readFileSync(path.join(__dirname,'../src/main/assets/visual-runtime.js'),'utf8'),ctx={comparisonHistory:[],photoOcr:[],provider:{},budget:new V.Budget()};
 const env={V164:V,lastVisionReading:base,scan164:ctx,schemaFormat:(name,schema)=>({text:{format:{name,type:'json_schema',strict:true,schema},verbosity:'low'}}),estimate164:body=>{const s=JSON.stringify(body,(k,v)=>k==='image_url'?'[image]':v),images=(JSON.stringify(body).match(/"type":"input_image"/g)||[]).length;return ((Math.ceil(Buffer.byteLength(s)/2)+images*8192)*.25+body.max_output_tokens*1.2)/1e6;}};
 vm.createContext(env);vm.runInContext(text.slice(text.indexOf('function comparisonBody169('),text.indexOf('function expandFocused174(')),env);return {env,ctx};
}
test('comparison planner can use the front for the parallel while keeping back facts in context',()=>{
 const b=card(),{env,ctx}=runtimePlan(b),photos=[1,2].map(imageIndex=>({meta:{imageIndex},data:'synthetic'})),refs=[{id:'ref1',title:'Alex Rivera Aurora Prizm 73 Green',url:'https://catalog.example/item',text:'Aurora Prizm 73 Green',image_data:'synthetic'}];
 const one=env.estimate164(env.focusedBody174(b,photos.slice(0,1),refs,ctx,1600,'Verifica identità e variante utilizzando soltanto le prove disponibili.'));
 ctx.budget.maxUsd=one+.0001;const plan=env.planComparison179(b,photos,refs,ctx);
 assert.ok(plan.body);assert.equal(plan.photos.length,1);assert.equal(plan.photos[0].meta.imageIndex,1);assert.ok(plan.estimatedUsd<=ctx.budget.maxUsd);assert.match(JSON.stringify(plan.body.input),/2031-32 AURORA PRIZM/);assert.match(JSON.stringify(plan.body.input),/NO. 73/);
 const pending={...b,identity_basis:{family:'inferred',variant:'inferred'}},{env:unsafe}=runtimePlan(pending);
 assert.equal(unsafe.comparisonPhotos179(pending,photos).length,1);assert.equal(unsafe.planComparison179(pending,photos,refs,ctx).body,undefined);
});
test('catalogue field recovery reuses frozen matches and validates the new text response',async()=>{
 const source=fs.readFileSync(path.join(__dirname,'../src/main/assets/visual-runtime.js'),'utf8');
 const base={kind:'object',category:'collectible panel',object_unit:'panel',variant_scope:'physical_description',variant:'two portraits',market_ready:false,photo_clues:[clue('Alex and Morgan')]};
 const ref={id:'ref1',url:'https://catalog.example/panel',title:'Northern Record, issue 17, 1955',text:'Northern Record, issue 17, 1955. Alex and Morgan.',image_data:'frozen comparison marker'};
 const bad={unit:'panel',decision:'match',same_unit:true,physical_ambiguity:false,identity_level:'exact',variant_status:'not_applicable',conflicts:[],matches:matches(),fields:[field('year','1955','1955','listing','year')]};
 const corrected=[field('family','Northern Record','Northern Record','parent'),field('subject','Alex and Morgan'),field('year','1955','1955','parent','year'),field('issue_number','17','issue 17','parent','issue_number')];
 for(const valid of [true,false]){
  const {env,ctx}=runtimePlan(base),reply={candidates:[bad],detail_needed_from:'none',physical_detail_needed:null};ctx.lastComparison={reply,references:[ref]};ctx.comparisonHistory=[ctx.lastComparison];ctx.recoveries=[];
  let calls=0;Object.assign(env,{status:()=>{},openai:async body=>{calls++;assert.equal(body.tools,undefined);assert.equal(body.text.format.name,'flipcheck_catalogue_fields');assert.equal(typeof body.input,'string');return {entry_scope:'exact_entry',fields:valid?corrected:[field('family','Invented publication')]};},addUsage:()=>{},guard164:()=>{},parseResponseJSON:x=>x,recordClosure164:()=>{},syncIdentity169:x=>x});
  vm.runInContext(source.slice(source.indexOf('async function repairCatalogueFields179('),source.indexOf('async function finishComparison173(')),env);
  const out=await env.repairCatalogueFields179(V.validate(base,reply,[ref]),ctx);assert.equal(calls,1);assert.equal(out.market_ready,valid);assert.equal(ctx.fieldRepair.state,valid?'completed':'no_valid_fields');
  if(valid){assert.match(out.model,/Northern Record.*17/);assert.equal(JSON.stringify(ctx.lastComparison.reply.candidates[0].matches),JSON.stringify(bad.matches));}
 }
});
test('a mocked original printing reread closes the verified core only when its required details are clear',async()=>{
 const E=require('../src/main/assets/editions.js'),source=fs.readFileSync(path.join(__dirname,'../src/main/assets/visual-runtime.js'),'utf8'),fixture=require('./fixtures/identity-cases.cjs').machamp;
 for(const shadow of ['absent','unclear']){
  const base=clone(fixture.vision),{env,ctx}=runtimePlan(base),refs=fixture.references.map(r=>({...r,image_data:'frozen comparison marker'}));ctx.calls=[];ctx.recoveries=[];
  const reply={candidates:fixture.candidates.map(c=>({...c,decision:'possible',identity_level:'exact',physical_ambiguity:true,ambiguity_scope:'variant'})),detail_needed_from:'target',physical_detail_needed:'Read the right/lower artwork border for shadow.'};
  const core=V.validate(base,reply,refs);assert.equal(core.catalogue_core_verified,true);
  Object.assign(env,{FlipCheckEditions:E,status:()=>{},guard164:()=>{},guard164AfterError:()=>{},addUsage:()=>{},recordClosure164:()=>{},syncIdentity169:x=>x,validImageCount:()=>1,visualPhoto164:async()=>({data:'synthetic crop',meta:{imageIndex:1,cropped:true}}),parseResponseJSON:x=>x,
   enforceIdentificationPolicy:x=>E.apply(x,E.cataloguePrinting(x,env.lastVisionReading.pokemon_printing),1),
   openai:async body=>{ctx.calls.push({purpose:body.text.format.name});assert.equal(body.tools,undefined);return {pokemon_printing:{...base.pokemon_printing,artwork_shadow:shadow,shadow_image:1,shadow_location:'Right and lower artwork border'}};}});
  vm.runInContext(source.slice(source.indexOf('async function resolvePrinting168('),source.indexOf('resolveIdentificationCheap=async function')),env);
  const out=await env.finishIdentity171(core,ctx);assert.equal(ctx.printingRecovery.attempted,true);assert.equal(ctx.calls.length,1);assert.equal(out.market_ready,shadow==='absent');
  if(shadow==='absent'){assert.match(out.variant,/Shadowless/);assert.equal(out.identity_basis.variant,'physical_evidence');}else assert.equal(out.assistance_state,'physical_detail_needed');
 }
});
