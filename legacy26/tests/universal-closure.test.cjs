/* Regression shapes from build 182; synthetic names and references, no live calls. */
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const V=require('../src/main/assets/visual-policy.js'),E=require('../src/main/assets/editions.js');
const runtime=fs.readFileSync(require.resolve('../src/main/assets/visual-runtime.js'),'utf8');
const clue=(text,role)=>({text,role,certainty:'clear',image_index:1});
const field=(field,value,kind='none',quote=value)=>({field,value,quote,number_kind:kind,scope:'target',evidence:'text',reference_id:'ref1'});
function card(){
 const base={kind:'card',object_unit:'single',category:'sports card',brand:'Example',family:'Prism',title:'Alex Rivera card',model:'',model_confidence:65,market_ready:false,variant:'',variant_scope:'none',identity_basis:{family:'inferred',variant:'not_applicable'},photo_clues:[clue('Alex Rivera','subject'),clue('NO. 73','collector_number')],physical_observations:[]};
 const fields=[field('family','Prism'),field('subject','Alex Rivera'),field('catalog_number','73','card_number'),field('year','2031','year')];
 const ref={id:'ref1',url:'https://catalog.example/prism/alex-73',title:'2031 Prism Alex Rivera #73',text:'2031 Prism Alex Rivera #73. Release year 2031.',text_origin:'retrieved_page'};
 const reply={detail_needed_from:'none',physical_detail_needed:null,candidates:[{unit:'single',decision:'match',same_unit:true,identity_level:'exact',physical_ambiguity:false,ambiguity_scope:'none',variant_status:'not_applicable',matches:[],fields,conflicts:[]}]};
 return {base,fields,ref,reply};
}
const evaluate=d=>V.validate(d.base,d.reply,[d.ref]);
test('photo name and number plus a dated exact web entry close without inventing a photo year',()=>{
 const d=card(),out=evaluate(d);assert.equal(out.market_ready,true);assert.equal(out.source_confirmed_year,'2031');assert.equal(out.identity_keys.date,null);assert.equal(out.visual_candidates[0].key_evidence.date_check,'catalogue_only');assert.equal(out.visual_candidates[0].matches.length,0);
 d.base.photo_clues.push(clue('2009 career totals PTS 100','season'));assert.equal(evaluate(d).market_ready,true);assert.equal(V.cardKeyFacts(d.base).date,null);
});
test('a missing web year, listing date, footer copyright or different card number cannot close',()=>{
 const changes=[d=>d.reply.candidates[0].fields=d.fields.filter(f=>f.field!=='year'),d=>d.fields[3].scope='listing',d=>{d.ref.title='Prism Alex Rivera #73';d.fields[3].quote='Website copyright 2031';d.ref.text=d.ref.title+' Website copyright 2031';},d=>{d.fields[2].value=d.fields[2].quote='74';d.ref.title=d.ref.text='2031 Prism Alex Rivera #74';}];
 for(const mutate of changes){const d=card();mutate(d);assert.notEqual(evaluate(d).market_ready,true,mutate.toString());}
});
test('an observed product year remains binding and two matching series stay ambiguous',()=>{
 const d=card();d.base.photo_clues.push(clue('2032 Prism','season'));assert.notEqual(evaluate(d).market_ready,true);
 d.base.photo_clues.pop();const other={...d.ref,id:'ref2',url:'https://catalog.example/other/alex-73',title:'2031 Spectrum Alex Rivera #73',text:'2031 Spectrum Alex Rivera #73'};
 const second={...d.reply.candidates[0],fields:d.fields.map(f=>({...f,reference_id:'ref2',value:f.value.replace('Prism','Spectrum'),quote:f.quote.replace('Prism','Spectrum')}))};
 assert.notEqual(V.validate(d.base,{candidates:[...d.reply.candidates,second]},[d.ref,other]).market_ready,true);
});
function green(){
 const d=card();Object.assign(d.base,{variant:'Green Prism parallel',variant_scope:'commercial',model_confidence:94,identity_basis:{family:'inferred',variant:'physical_evidence'},physical_observations:[{feature:'color',text:'Bright green holographic border',entity:'target',certainty:'clear',image_index:1},{feature:'pattern',text:'Geometric black, blue and silver background',entity:'target',certainty:'clear',image_index:1}]});
 d.ref.image_data='synthetic';d.ref.title+=' Green Prism';d.ref.text+=' Green Prism';d.fields.push(field('variant','Green Prism','none','Green Prism'));
 Object.assign(d.reply.candidates[0],{variant:'Green Prism',variant_status:'identified',matches:[{reference_id:'ref1',feature:'layout',photo_detail:'Stessa composizione della carta',reference_detail:'Stessa composizione visibile nella fonte',agrees:true,reference_evidence:'image'},{reference_id:'ref1',feature:'appearance',photo_detail:'Bordo verde brillante con finitura olografica',reference_detail:'Green Prism con bordo verde visibile',agrees:true,reference_evidence:'image'}]});
 return d;
}
test('equivalent bilingual border evidence closes a cited colour parallel without requiring verbatim sentences',()=>{
 const d=green(),out=evaluate(d);assert.equal(out.market_ready,true);assert.equal(out.variant,'Green Prism');assert.deepEqual(out.visual_candidates[0].appearance_check.missing_observations,[]);
});
test('text alone, wrong colour and an unverified pattern parallel remain blocked',()=>{
 for(const mode of ['text','wrong','pattern','negative']){
  const d=green(),c=d.reply.candidates[0];
  if(mode==='text')c.matches[1].reference_evidence='description';
  if(mode==='wrong')c.matches[1].reference_detail='Bordo rosso';
  if(mode==='pattern'){c.variant='Green Wave';d.fields[4].value=d.fields[4].quote='Green Wave';d.ref.text+=' Green Wave';}
  if(mode==='negative')c.matches.push({...c.matches[1],feature:'color',agrees:false});
  assert.notEqual(evaluate(d).market_ready,true,mode);
 }
});
test('structured links cover translated visual observations and reject missing or out-of-range links',()=>{
 const d=green(),c=d.reply.candidates[0];c.variant='Green Wave';d.fields[4].value=d.fields[4].quote='Green Wave';d.ref.text+=' Green Wave';
 c.matches[1]={...c.matches[1],feature:'color',observation_indexes:[0]};c.matches.push({reference_id:'ref1',feature:'pattern',observation_indexes:[1],photo_detail:'Motivo descritto nell’osservazione 1',reference_detail:'Stesso motivo visibile nella fonte',agrees:true,reference_evidence:'image'});
 assert.equal(evaluate(d).market_ready,true);c.matches[2].observation_indexes=[5];assert.notEqual(evaluate(d).market_ready,true);
 c.matches[2].observation_indexes=[0];assert.notEqual(evaluate(d).market_ready,true);
});
test('a panel keeps both photographed people while a competing subject or issue remains a conflict',()=>{
 const d=card();Object.assign(d.base,{object_unit:'panel',photo_clues:[clue('Alex Rivera','subject'),clue('Morgan Vale','subject')]});
 const quote='Uncut portrait panel of Alex Rivera and Morgan Vale.';d.ref.text+=' '+quote;
 const a=field('subject','Alex Rivera','none',quote),b=field('subject','Morgan Vale','none',quote);
 let merged=V.mergeCatalogueFields([a],[b],[d.ref],d.base);assert.equal(merged.conflicts.length,0);assert.equal(merged.fields.length,2);
 const fields=[...merged.fields,field('family','Prism'),field('year','2031','year')];assert.match(V.catalogueName(d.base,{},fields),/Alex Rivera \/ Morgan Vale/);
 assert.equal(V.mergeCatalogueFields([a],[b],[d.ref],{...d.base,object_unit:'single',photo_clues:[clue('Alex Rivera','subject')]}).conflicts.length,1);
 d.ref.text+=' Other Person';assert.equal(V.mergeCatalogueFields([a],[field('subject','Other Person')],[d.ref],d.base).conflicts.length,1);
 d.ref.text+=' 2032';assert.equal(V.mergeCatalogueFields([field('year','2031','year')],[field('year','2032','year')],[d.ref],d.base).conflicts.length,1);
});
function printing(){return {is_pokemon:true,language:'Italian',set_name:'Summit',card_type:'pokemon',first_edition_stamp:'unclear',stamp_image:0,stamp_location:'',stamp_text:'',artwork_shadow:'not_applicable',shadow_image:0,shadow_location:'',shadow_edges:{right:'not_applicable',lower:'not_applicable'},copyright_text:'© 2003 Pokémon / Nintendo',copyright_image:1,slab_text:'',slab_image:0};}
test('a verified later western series skips an inapplicable stamp without rewriting the photo observation',()=>{
 const p=printing(),identity={family:'Summit',catalogue_core_verified:true,core_identity:{fields:[]}},resolved=E.cataloguePrinting(identity,p),check=E.evaluate(resolved,1);
 assert.equal(check.complete,true);assert.equal(check.stamp,'not_applicable');assert.equal(resolved.first_edition_stamp,'unclear');assert.equal(p.stamp_policy,undefined);assert.equal(resolved.stamp_policy.origin,'verified_series_and_observed_copyright');
 for(const language of ['Japanese','Korean','unknown'])assert.equal(E.evaluate(E.cataloguePrinting(identity,{...p,language}),1).complete,false);
 assert.equal(E.evaluate(E.cataloguePrinting({...identity,catalogue_core_verified:false},p),1).complete,false);
 assert.equal(E.evaluate(E.cataloguePrinting(identity,{...p,copyright_text:'©1995,96,98,99 Nintendo ©1999 Wizards'}),1).complete,false);
});
test('a visible stamp inconsistent with the verified release is not silently erased',()=>{
 const p={...printing(),first_edition_stamp:'present',stamp_image:1,stamp_location:'below artwork',stamp_text:'1st Edition'};
 const check=E.evaluate(E.cataloguePrinting({family:'Summit',catalogue_core_verified:true},p),1);assert.equal(check.contradiction,true);assert.equal(check.complete,false);
});
test('catalogue printing completion closes the production identity without another Vision and retains observed holo',async()=>{
 const d=card();Object.assign(d.base,{kind:'card',variant:'',pokemon_printing:printing(),physical_observations:[{feature:'finish',text:'Holographic artwork',entity:'target',certainty:'clear',image_index:1}]});
 const base={...d.base,model:'Summit Rivermon H7/H32',family:'Summit',catalogue_core_verified:true,model_confidence:95,core_identity:{status:'confirmed',model:'Summit Rivermon H7/H32',fields:[]},visual_candidates:[{core_accepted:true,accepted:true,ambiguity_scope:'none',blocking_fields:[],identity_conflicts:[]}],identity_basis:{family:'catalogue',variant:'physical_evidence'}};
 const env={V164:V,FlipCheckEditions:E,validImageCount:()=>1,syncIdentity169:x=>x,enforceIdentificationPolicy:x=>E.apply(x,E.cataloguePrinting(x,x.pokemon_printing),1),resolvePrinting168:()=>{throw Error('unnecessary paid printing reread');}};
 vm.createContext(env);vm.runInContext(runtime.slice(runtime.indexOf('async function finishIdentity171('),runtime.indexOf('resolveIdentificationCheap=async function')),env);
 const out=await env.finishIdentity171(base,{});assert.equal(out.market_ready,true);assert.equal(out.variant,'Holo');assert.match(out.normalized_query,/Holo/);assert.equal(out.pokemon_printing.first_edition_stamp,'unclear');
});
