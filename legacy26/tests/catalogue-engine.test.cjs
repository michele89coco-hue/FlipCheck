/* Pure decision tests. Real 192 observations are replayed; no new image recognition is simulated. */
const {test}=require('node:test'),assert=require('node:assert/strict');
const E=require('../src/main/assets/catalogue-engine'),C=require('../src/main/assets/catalogue-sources'),D=JSON.parse(require('node:zlib').gunzipSync(require('node:fs').readFileSync(require('node:path').join(__dirname,'fixtures/diagnostics-192.json.gz'))));
const copy=structuredClone;
function replay(name){const d=copy(D[name]),l=E.ingestVision(new E.Ledger(d.vision),d.vision);E.ingestOcr(l,d.photoOcr);return {d,l,entries:C.records(d.pages,l)};}
function observed(fields,domain='pokemon'){const l=new E.Ledger({domain,kind:'card'});for(const [field,value] of Object.entries(fields))l.add(field,value,{certainty:'clear',image_index:1});return l;}
const entry=(overrides={})=>({subject:'Example',number:'H23/H32',family:'Skyridge',language:'en',year:'2003',grounded:true,source:{url:'https://api.tcgdex.net/v2/en/cards/ecard3-H23'},variants:[{name:'Holo',finish:'holo'}],...overrides});
function stampCase(family='Jungle'){
 const l=observed({subject:'Example',collector_number:'1/64',season:'1999',language:'en',finish:'holo'});
 l.add('stamp','unclear',{certainty:'clear',image_index:1,raw:'Edition area unreadable'});
 return {l,entries:[entry({number:'1/64',family,year:'1999'})]};
}
test('confirmed stamp detail replaces unknown state and publishes coherent printing fields',()=>{
 const {l,entries}=stampCase(),before=E.reduce(l,entries),requests=E.recoveryRequests(l,before);
 assert.equal(before.job_status,'variant_pending');assert.equal(before.printing_check.stamp,'unclear');
 E.applyDetails(l,[{field:'stamp',text:'present',certainty:'clear',evidence_found:true,image_index:1}],requests);
 const r=E.reduce(l,entries);assert.equal(r.printing_check.stamp,'confirmed');assert.equal(r.printing_check.stamp_presence,'present');assert.equal(r.pokemon_printing.first_edition_stamp,'present');assert.equal(r.job_status,'variant_resolved');assert.equal(r.closure_status,'resolved');assert.equal(r.next_photo_request,null);assert.equal(l.values('stamp')[0].value,'unclear');
 assert.deepEqual(E.recoveryRequests(l,r),[]);assert.deepEqual(E.reduce(l,entries),r);
});
test('stamp recovery preserves an unresolved shadow and does not falsely complete the variant',()=>{
 const {l,entries}=stampCase('Base Set'),before=E.reduce(l,entries);
 E.applyDetails(l,[{field:'stamp',text:'Stamp 1st Edition visibile sotto l’illustrazione',certainty:'clear',evidence_found:true,image_index:1}],E.recoveryRequests(l,before));
 const r=E.reduce(l,entries);assert.equal(r.printing_check.stamp,'confirmed');assert.equal(r.job_status,'variant_pending');assert.equal(r.market_ready,false);assert.deepEqual(r.variant_resolution.pending,['shadow']);assert.match(r.next_photo_request,/bordo/);
});
test('affirmative original-photo description repairs an inconsistent unclear enumeration',()=>{
 const {l,entries}=stampCase();l.add('stamp','unclear',{certainty:'clear',image_index:1,raw:'Stamp 1st Edition visibile sotto l’illustrazione'});
 const r=E.reduce(l,entries);assert.equal(r.printing_check.stamp,'confirmed');assert.equal(r.market_ready,true);assert.equal(r.pokemon_printing.stamp_image,1);assert.ok(l.events.some(e=>e.stage==='visual_state_reconciled'));
 const count=l.atoms.length;E.reduce(l,entries);assert.equal(l.atoms.length,count);
 assert.throws(()=>E.assertConsistent(l,{...r,printing_check:{...r.printing_check,stamp:'unclear'}}),/inconsistent_identification_state/);
});
test('negated, uncertain, reference-only or unlocated statements cannot confirm a stamp',()=>{
 for(const patch of [{raw:'Stamp 1st Edition non visibile'},{raw:'1st Edition stamp not visible'},{raw:'Stamp may be visible'},{raw:'Stamp 1st Edition visibile?'},{raw:'Timbro forse visibile'},{raw:'Timbro presente nel catalogo'},{raw:'Se il timbro è visibile'},{raw:'Stamp 1st Edition visibile',certainty:'uncertain'},{raw:'Stamp 1st Edition visibile',image_index:0}]){
  const {l,entries}=stampCase();l.add('stamp','unclear',{certainty:'clear',image_index:1,...patch});const r=E.reduce(l,entries);assert.equal(r.market_ready,false,JSON.stringify(patch));assert.equal(r.printing_check.stamp,'unclear');
 }
});
test('a false evidence flag, another image or a contradictory clear observation cannot resolve the detail',()=>{
 for(const patch of [{evidence_found:false},{image_index:2},{certainty:'uncertain'}]){
  const {l,entries}=stampCase(),before=E.reduce(l,entries);E.applyDetails(l,[{field:'stamp',text:'present',certainty:'clear',evidence_found:true,image_index:1,...patch}],E.recoveryRequests(l,before));assert.equal(E.reduce(l,entries).market_ready,false);
 }
 const {l,entries}=stampCase();l.add('stamp','absent',{certainty:'clear',image_index:1,raw:'Stamp 1st Edition visibile'});const r=E.reduce(l,entries);assert.equal(r.printing_check.stamp,'conflict');assert.equal(r.printing_check.contradiction,true);assert.equal(r.market_ready,false);
});
test('verified stamp absence resolves Unlimited without claiming a visible stamp',()=>{
 const {l,entries}=stampCase();E.applyDetails(l,[{field:'stamp',text:'absent',certainty:'clear',evidence_found:true,image_index:1}],E.recoveryRequests(l,E.reduce(l,entries)));
 const r=E.reduce(l,entries);assert.equal(r.printing_check.stamp,'confirmed');assert.equal(r.printing_check.stamp_presence,'absent');assert.equal(r.pokemon_printing.first_edition_stamp,'absent');assert.match(r.variant,/Unlimited/);assert.doesNotMatch(r.variant,/1st Edition/);
});
test('the actual detail handler commits identity and evidence before its promise resolves',async()=>{
 const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path'),source=fs.readFileSync(path.join(__dirname,'../src/main/assets/catalogue-runtime.js'),'utf8');
 const production=source.slice(source.indexOf('function commitCatalogue193('),source.indexOf('async function localShadow193('));
 for(const cancelled of [false,true]){
  const {l,entries}=stampCase(),ctx={budget:{spent:()=>0,maxUsd:1}},before=E.reduce(l,entries),requests=E.recoveryRequests(l,before);let release;
  const response=new Promise(r=>release=r),sandbox={E193:E,Date,JSON,Math,Object,string193:{type:'string'},enum193:values=>({type:'string',enum:values}),object193:properties=>({type:'object',properties}),schemaFormat:()=>({}),estimate164:()=>0,addUsage(){},guard164(c){if(c.cancelled)throw new Error('scan_cancelled');},visualPhoto164:async()=>({data:'offline-carrier',meta:{imageIndex:1}}),originalOpenai26:async()=>response,parseResponseJSON:x=>x,diagnosticPhases:[]};
  vm.createContext(sandbox);vm.runInContext(production+'\nthis.read=detailRead193;this.commit=commitCatalogue193;',sandbox);
  sandbox.commit(l,entries,ctx);const revision=ctx.catalogueState.revision,previousSnapshot=ctx.engineSnapshot,previousCount=previousSnapshot.observations.length,pending=sandbox.read(l,ctx,requests,entries);
  await new Promise(r=>setImmediate(r));assert.equal(ctx.catalogueIdentity.job_status,'variant_pending');ctx.cancelled=cancelled;
  release({details:[{field:'stamp',text:'present',certainty:'clear',evidence_found:true,image_index:1}]});
  if(cancelled){await assert.rejects(pending,/scan_cancelled/);assert.equal(ctx.catalogueState.revision,revision);assert.equal(l.pick('stamp'),null);}
  else {await pending.then(result=>{assert.equal(ctx.catalogueIdentity,result);assert.equal(ctx.catalogueState.result,result);assert.equal(ctx.jobStatus,'variant_resolved');assert.equal(ctx.detailReread.committed_revision,ctx.catalogueState.revision);assert.equal(ctx.detailReread.evidence_found,true);assert.ok(ctx.engineSnapshot.observations.some(a=>a.field==='stamp'&&a.value==='present'));assert.equal(previousSnapshot.observations.length,previousCount);});}
 }
});
test('204: a foreign edition cannot close the photographed Italian printing',()=>{
 const l=observed({subject:'Example',collector_number:'H23/H32',copyright:'© 2003',language:'it',finish:'holo'});
 const r=E.reduce(l,[entry({rarity:'English catalogue rarity'})]);
 assert.equal(r.core_identity.status,'partial');assert.equal(r.market_ready,false);assert.equal(r.card_identity.number,'H23/H32');assert.equal(r.source_confirmed_year,null);
 assert.equal(r.language,'it');assert.equal(r.card_identity.language,'it');assert.equal(r.language_origin,'original_photo');assert.equal(r.catalogue_reference_language,null);
 assert.equal(r.card_identity.rarity,null);assert.equal(r.printed_year,'2003');assert.equal(r.next_photo_request,null);
 const local=E.reduce(l,[entry({language:'it'})]);assert.equal(local.market_ready,true);assert.equal(local.catalogue_reference_language,'it');
});
test('language differences never merge conflicting numbers, subjects or printed years',()=>{
 const l=observed({subject:'Example',collector_number:'H23/H32',season:'2003',language:'it'});
 for(const changes of [{number:'25/144'},{number:'H23/144'},{subject:'Another'},{year:'2002'}])assert.equal(E.reduce(l,[entry(changes)]).core_identity.status,'partial');
});
test('language versions of the same core form one candidate and retain local printing evidence',()=>{
 const l=observed({subject:'Example',collector_number:'H23/H32',season:'2003',language:'it',finish:'holo'});
 const entries=[entry({variants:[{name:'Reverse',finish:'reverse'}]}),entry({language:'it',rarity:'Local rarity',variants:[{name:'Holo',finish:'holo'}]})];
 for(const list of [entries,[...entries].reverse()]){
  assert.equal(E.candidateGroups(l,list).length,1);const r=E.reduce(l,list);assert.equal(r.market_ready,true);assert.equal(r.catalogue_reference_language,'it');assert.equal(r.card_identity.rarity,'Local rarity');assert.equal(r.variant,'Holo');
 }
 const ambiguous=observed({subject:'Example',collector_number:'H23/H32',language:'it'});
 assert.equal(E.reduce(ambiguous,[entry({language:'it'}),entry({year:'2004',language:'it'})]).core_identity.status,'partial');
});
test('foreign language printing options cannot assign first edition or finish to the photographed copy',()=>{
 const l=observed({subject:'Example',collector_number:'7/100',season:'2003',language:'it'}),e=entry({number:'7/100',family:'Example Set',printing_options:['first_edition'],variants:[{name:'Reverse',finish:'reverse'}]});
 const r=E.reduce(l,[e]);assert.equal(r.core_identity.status,'partial');assert.equal(r.market_ready,false);assert.deepEqual(r.variant_resolution.pending,['catalogue']);assert.ok(l.candidates[0].reasons.includes('different_printing_language'));
 const base=entry({family:'Base Set',year:'1999'});assert.equal(E.printingScope(l,base).shadow,false);
 const unknown=observed({subject:'Example',collector_number:'H23/H32'}),out=E.reduce(unknown,[entry()]);assert.equal(out.core_identity.status,'confirmed');assert.equal(out.language,'');assert.equal(out.language_origin,null);
});
test('catalogue lookup has one English fallback without changing the observed language',()=>{
 const l=observed({subject:'Example',collector_number:'7',language:'it'}),plans=C.requestPlan(l);
 assert.deepEqual(plans.map(p=>p.language),['it','en']);assert.equal(plans[1].fallback,true);assert.match(plans[1].url,/\/en\/cards\?/);assert.equal(E.keyValues(l).language,'it');
 assert.equal(C.requestPlan(observed({subject:'Example',language:'en'})).length,1);
});
test('204: repeating an uncertain number across OCR and vision cannot certify it',()=>{
 const l=observed({subject:'Politoed',hp:'110',copyright:'© 2003 Pokémon / Nintendo',language:'it',finish:'holo'});
 for(const [value,source] of [['H23/H32','vision'],['H23/32','vision'],['H23/H32','local_ocr']])l.add('collector_number',value,{source,certainty:'uncertain',image_index:1});
 const record=C.tcgdexCard({id:'ecard3-H23',name:'Politoed',localId:'H23',hp:110,set:{id:'ecard3',name:'Skyridge',cardCount:{official:144,total:182}},variants:{firstEdition:false,holo:true,normal:false,reverse:false},rarity:'Holo Rare'},{releaseDate:'2003-05-12'},'en','https://api.tcgdex.net/v2/en/cards/ecard3-H23');
 const r=E.reduce(l,[record]);assert.equal(r.core_identity.status,'partial');assert.equal(r.market_ready,false);assert.equal(r.language,'it');assert.equal(r.physical_card_number,null);assert.equal(l.pick('collector_number'),null);assert.equal(r.source_confirmed_year,null);assert.ok(E.recoveryRequests(l,r).some(q=>q.field==='collector_number'));
});
test('production catalogue retrieval falls back across languages, skips unrelated cards and preserves observations',async()=>{
 const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path'),source=fs.readFileSync(path.join(__dirname,'../src/main/assets/catalogue-runtime.js'),'utf8');
 const production=source.slice(source.indexOf('async function catalogueLookup193('),source.indexOf('const variantSchema193='));
 for(const localStatus of [200,503,'matched']){
  const requests=[],l=observed({subject:'Politoed',collector_number:'H23/H32',copyright:'© 2003',language:'it',finish:'holo'}),ctx={};
  const sandbox={E193:E,C193:C,URLSearchParams,guard164(){},async directCall165(action,payload){
   requests.push(payload.url);
   if(payload.url.includes('/it/')&&localStatus!=='matched')return {status:localStatus,body:[{id:'xy3-18',localId:'18',name:'Politoed'},{id:'sm2-25',localId:'25',name:'Politoed'},{id:'swsh11-032',localId:'032',name:'Politoed'}]};
   if(payload.url.includes('/cards?'))return {status:200,body:[{id:'ecard3-H23',name:'Politoed',localId:'H23'},{id:'xy3-18',name:'Politoed',localId:'18'}]};
   if(payload.url.includes('/cards/'))return {status:200,body:{id:'ecard3-H23',name:'Politoed',localId:'H23',set:{id:'ecard3',name:'Skyridge'},variants:{holo:true}}};
   return {status:200,body:{releaseDate:'2003-05-12'}};
  }};
  vm.createContext(sandbox);vm.runInContext(production+'\nthis.lookup=catalogueLookup193;',sandbox);const found=await sandbox.lookup(l,ctx),r=E.reduce(l,found.entries);
  assert.equal(r.core_identity.status,localStatus==='matched'?'confirmed':'partial');assert.equal(r.market_ready,localStatus==='matched');assert.equal(r.language,'it');assert.equal(r.catalogue_reference_language,localStatus==='matched'?'it':null);
  assert.equal(requests.some(url=>url.includes('/en/')),localStatus!=='matched');assert.ok(!requests.some(url=>url.includes('/cards/xy3-18')));assert.equal(ctx.catalogueCoverage.truncated,false);assert.equal(E.keyValues(l).language,'it');
 }
});
test('204: recorded uncertain Politoed requires a physical reread before exact closure',()=>{const {l,entries}=replay('politoed'),r=E.reduce(l,entries);assert.equal(r.core_identity.status,'partial');assert.equal(r.market_ready,false);assert.equal(r.physical_card_number,null);assert.deepEqual(E.recoveryRequests(l,r).map(q=>q.field),['collector_number','copyright']);assert.equal(l.base.family,'Aquapolis');assert.equal(r.language,'it');assert.ok(!r.candidate_models.some(c=>/Pokédex/.test(c.model)));});
test('different printings on one wiki page never become aliases',()=>{const {l,entries}=replay('politoed');assert.ok(entries.some(e=>e.number==='25/144'));assert.ok(entries.some(e=>e.number==='H23/H32'));const groups=E.candidateGroups(l,entries);assert.ok(groups.find(g=>g.entry.number==='H23/H32').score>groups.find(g=>g.entry.number==='25/144').score);assert.equal(groups.length,2);});
test('recorded Boniface serial alternatives survive with original-image coordinates and rotation',()=>{const {l,entries}=replay('boniface'),r=E.reduce(l,entries),req=E.recoveryRequests(l,r).find(r=>r.field==='serial');assert.equal(l.pick('serial'),null);assert.equal(req.rotation,90);assert.equal(req.image_index,2);assert.ok(req.region.x>.7&&req.region.x<.85);assert.ok(req.readings.some(r=>r.value==='2/5'));assert.equal(r.market_ready,false);assert.equal(r.core_identity.status,'confirmed');});
test('a verified serial closes documented Green /5 and retains the specimen numerator',()=>{const {l,entries}=replay('boniface'),r=E.reduce(l,entries);E.applyDetails(l,[{field:'serial',text:'2/5',certainty:'clear',image_index:2}],E.recoveryRequests(l,r));const out=E.reduce(l,entries);assert.equal(out.variant,'Green');assert.equal(out.market_ready,true);assert.equal(out.physical_serial.specimen,2);assert.equal(out.physical_serial.print_run,5);});
test('a dubious serial is never autocorrected to fit the checklist',()=>{const {l,entries}=replay('boniface'),req=E.recoveryRequests(l,E.reduce(l,entries));E.applyDetails(l,[{field:'serial',text:'215',certainty:'clear',image_index:2}],req);assert.equal(E.reduce(l,entries).market_ready,false);E.applyDetails(l,[{field:'serial',text:'2/5',certainty:'uncertain',image_index:2}],req);assert.equal(l.pick('serial'),null);});
test('recorded Doncic retains core and green; statistics 42 and wrong Blue Ice are excluded',()=>{const {l,entries}=replay('doncic'),r=E.reduce(l,entries);assert.equal(r.core_identity.status,'confirmed');assert.equal(r.family,'Prizm');assert.equal(r.card_identity.number,'280');assert.deepEqual(E.keyValues(l).serials,[]);assert.match(E.query(l),/green/);assert.doesNotMatch(E.query(l),/\b42\b/);assert.ok(!r.variant_resolution.variant_candidates.some(v=>/Blue Ice/.test(v.name)));assert.equal(r.market_ready,false);});
test('recorded box keeps the product and never forces Hobby or requests a card number',()=>{const {l,entries}=replay('topps'),r=E.reduce(l,entries);assert.equal(r.core_identity.status,'confirmed');assert.equal(r.market_ready,false);assert.match(r.title,/Chrome.*UPDATE SERIES/i);assert.doesNotMatch(r.variant,/Hobby|Jumbo/);assert.match(r.next_photo_request,/confezione/);assert.deepEqual(E.recoveryRequests(l,r),[]);assert.doesNotMatch(E.query(l),/FLAGG|WEMBANYAMA/);});
test('recorded Magikarp closes only after scoped local shadow evidence; observations stay immutable',()=>{const {d,l,entries}=replay('magikarp'),r=E.reduce(l,entries);assert.equal(r.core_identity.status,'confirmed');assert.deepEqual(r.variant_resolution.pending,['shadow']);const count=l.atoms.length;l.add('shadow','absent',{source:'local_frame_measurement',certainty:'clear',image_index:1,raw:JSON.stringify(d.localPrinting)});const out=E.reduce(l,entries);assert.equal(out.market_ready,true);assert.match(out.variant,/1st Edition.*Shadowless.*Non holo/);assert.equal(l.atoms.length,count+1);assert.equal(l.base.pokemon_printing.artwork_shadow,'unclear');});
test('same prefix/year with a different subject is rejected; number alone never identifies',()=>{let l=observed({subject:'Example',collector_number:'H23',language:'en'});const r=E.reduce(l,[entry(),entry({subject:'Another',family:'Aquapolis'})]);assert.equal(r.family,'Skyridge');l=observed({collector_number:'H23',language:'en'});assert.equal(E.reduce(l,[entry()]).core_identity.status,'partial');});
test('204: clear photographed year binds the printing; an explicitly documented printing year can differ from set release',()=>{const l=observed({subject:'Example',collector_number:'H23',copyright:'© 2002',language:'en'});assert.equal(E.reduce(l,[entry()]).core_identity.status,'partial');const r=E.reduce(l,[entry({printing_year:'2002'})]);assert.equal(r.core_identity.status,'confirmed');assert.equal(r.card_identity.date,'2002');assert.equal(r.printed_year,'2002');});
test('full numbers conflict but local numbers and leading zeros can corroborate',()=>{const l=observed({collector_number:'006'});l.add('collector_number','6',{certainty:'clear',image_index:2});assert.ok(l.pick('collector_number'));assert.equal(E.numbersMatch('H23','H23/H32'),true);assert.equal(E.numbersMatch('25/144','25/165'),false);assert.equal(E.numbersMatch('OP01-001','OP02-001'),false);l.add('collector_number','7',{certainty:'clear',image_index:2});assert.equal(l.pick('collector_number'),null);});
test('candidate order and duplicate sources do not alter the decision',()=>{const {l,entries}=replay('politoed'),a=E.reduce(l,entries),b=E.reduce(l,[...entries,...entries].reverse());assert.equal(a.model,b.model);assert.equal(a.market_ready,b.market_ready);});
test('set and language scope shadow, first edition and no rarity separately',()=>{let l=observed({language:'en'});assert.equal(E.printingScope(l,entry({family:'Neo Destiny',year:'2002'})).firstEdition,true);assert.equal(E.printingScope(l,entry({family:'Skyridge'})).shadow,false);l=observed({language:'ja'});const scope=E.printingScope(l,entry({family:'Expansion Pack',language:'ja',year:'1996'}));assert.equal(scope.noRarity,true);assert.equal(scope.shadow,false);});
test('unreadable rarity symbol is not absence; English set-symbol absence is unrelated',()=>{const l=observed({subject:'Example',collector_number:'1',language:'ja',finish:'normal',set_symbol:'absent'}),e=entry({number:'1',family:'Expansion Pack',language:'ja',year:'1996',variants:[{name:'Non holo',finish:'normal'}]});let r=E.reduce(l,[e]);assert.ok(r.variant_resolution.pending.includes('rarity_symbol'));l.add('rarity_symbol','absent',{certainty:'clear',image_index:1});r=E.reduce(l,[e]);assert.match(r.variant,/No Rarity Symbol/);});
test('geometry is independent of color and cannot be invented from a serial',()=>{const l=observed({subject:'Example',collector_number:'21',season:'2003',serial:'2/5',border_color:'green'},'sports'),e=entry({number:'21',variants:[{name:'Green',colors:['green'],print_run:5},{name:'Green Ice',colors:['green'],patterns:['ice'],print_run:5}]});let r=E.reduce(l,[e]);assert.equal(r.market_ready,false);assert.equal(r.variant_resolution.variant_candidates.length,2);l.add('pattern','geometric',{certainty:'clear',image_index:1});r=E.reduce(l,[e]);assert.equal(r.market_ready,false);});
test('structured catalogue lookup uses name before the uncertain set hypothesis',()=>{const {l}=replay('politoed'),p=C.requestPlan(l)[0];assert.match(p.url,/\/it\/cards\?/);assert.match(p.url,/name=Politoed/);assert.doesNotMatch(p.url,/Aquapolis/);assert.doesNotMatch(E.query(l),/Aquapolis/);});
test('TCGdex keeps finish and rarity separate and does not invent a release date',()=>{const c=C.tcgdexCard({id:'set-7',name:'Example',localId:'7',set:{id:'set',name:'Example Set',cardCount:{official:100}},rarity:'Rare',variants:{normal:true,reverse:true}},null,'en','https://api.tcgdex.net/test');assert.equal(c.year,'');assert.equal(c.number,'7/100');assert.equal(c.rarity,'Rare');assert.deepEqual(c.variants.map(v=>v.finish),['normal','reverse']);});
test('structured table column mapping cannot use another player or serial as card number',()=>{const l=observed({subject:'Example',collector_number:'21'},'sports');const p={url:'https://www.paniniamerica.net/checklist',title:'2025-26 Select',catalogue_rows:[{headers:['Card Number','Player','Set','Year','Subset'],cells:['21','Example','Select','2025-26','Base'],text:'21 Example Select 2025-26 Base'},{headers:['Serial','Player'],cells:['2/5','Example']} ]};assert.equal(C.records([p],l).length,1);assert.equal(C.records([p],l)[0].number,'21');});
test('model extraction must be grounded in a retrieved page, not its own response',()=>{const l=observed({subject:'Example',collector_number:'7'}),reply={entries:[{subject:'Example',family:'Example Set',number:'7',source_url:'https://www.beckett.com/test',entry_quote:'7 Example',proof:{subject:'Example',family:'Example Set',number:'7'},variants:[]}]};assert.equal(C.groundedExtraction(reply,[],l).accepted.length,0);assert.equal(C.groundedExtraction(reply,[{url:reply.entries[0].source_url,text:'Different entry'}],l).accepted.length,0);assert.equal(C.groundedExtraction(reply,[{url:reply.entries[0].source_url,text:'Short snippet'},{url:reply.entries[0].source_url,text:'Example Set\n7 Example'}],l).accepted.length,1);});
test('an explicitly superseded measurement is retained but no longer votes twice',()=>{const l=observed({shadow:'present'}),old=l.atoms[0];l.add('shadow','absent',{source:'local_frame_measurement',certainty:'clear',image_index:1,supersedes:[old.id]});assert.equal(l.pick('shadow').value,'absent');assert.equal(l.atoms[0].value,'present');assert.ok(Object.isFrozen(old));});
test('service failure preserves confirmed card fields and asks only the variant detail',()=>{const {l,entries}=replay('doncic'),r=E.reduce(l,entries,{error:'service_unavailable'});assert.equal(r.core_identity.status,'confirmed');assert.match(r.model,/280/);assert.deepEqual(r.variant_resolution.pending,['variant']);assert.equal(r.assistance_state,'service_unavailable');});
test('generic product model uses public specifications, not sports-only sites',()=>{const l=observed({brand:'Acme',model_code:'ZX-430'},'generic'),p={url:'https://acme.example/manual',title:'Acme ZX-430',text:'Acme ZX-430 water pump technical specifications'};assert.doesNotMatch(E.query(l),/beckett|panini/);const r=E.reduce(l,C.records([p],l));assert.equal(r.market_ready,true);assert.match(r.model,/ZX-430/);});

test('Bandai card list preserves code and rarity without inferring an artwork variant',()=>{const l=observed({subject:'Roronoa Zoro',collector_number:'OP01-001',language:'en'},'onepiece'),page={url:'https://en.onepiece-cardgame.com/cardlist/?series=569101',text:'OP01-001 | L | LEADER\nRoronoa Zoro\nLife\n5\nCard Set(s)\n-ROMANCE DAWN- [OP01]\nCARD VIEW'};const r=E.reduce(l,C.records([page],l));assert.equal(r.core_identity.status,'confirmed');assert.equal(r.card_identity.rarity,'L');assert.equal(r.market_ready,false);assert.match(C.requestPlan(l)[0].url,/freewords=OP01-001/);});

test('an observed autograph cannot silently close an ordinary parallel',()=>{const l=observed({subject:'Example',collector_number:'21',season:'2003',border_color:'green',autograph:'present'},'sports'),r=E.reduce(l,[entry({number:'21',variants:[{name:'Green',colors:['green']} ]})]);assert.equal(r.market_ready,false);assert.ok(r.variant_resolution.pending.includes('autograph'));assert.match(E.query(l),/autograph/);assert.equal(r.card_identity.autograph,'present');});

test('Italian Base Set does not lose First Edition just because its language differs',()=>{const l=observed({language:'it'});assert.equal(E.printingScope(l,entry({family:'Set Base',language:'it',year:'1999'})).firstEdition,true);assert.equal(E.printingScope(l,entry({family:'Set Base',language:'it',year:'1999'})).shadow,false);});
test('a malformed initial serial can be resolved by a clear crop instead of blocking it',()=>{const l=observed({serial:'215'},'sports');assert.equal(l.pick('serial'),null);E.applyDetails(l,[{field:'serial',text:'2/5',certainty:'clear',image_index:1}],[{field:'serial',image_index:1,key:'detail:serial',region:null}]);assert.equal(l.pick('serial').value,'2/5');});

test('fragmented names are composed from aligned original-photo regions',()=>{const l=new E.Ledger({kind:'card',category:'sports trading card'});for(const [value,x,w] of [['MICHAEL',.39,.17],['JORDAN',.585,.16]])l.add('subject',value,{certainty:'clear',image_index:1,region:{image_index:1,x,y:.595,width:w,height:.035}});assert.equal(l.domain,'sports');assert.equal(E.keyValues(l).subject,'MICHAEL JORDAN');assert.equal(l.atoms.length,2);});
test('name plus a repeated sports number launches lookup but cannot choose an unobserved product',()=>{const l=observed({subject:'Example',collector_number:'141'},'sports'),e=entry({number:'141',family:'Product A'});assert.equal(E.reduce(l,[e]).core_identity.status,'partial');assert.equal(E.reduce(l,[e]).family,'');l.add('catalogue_core',E.coreKey(e),{source:'catalogue_image_comparison',certainty:'clear',image_index:1});assert.equal(E.reduce(l,[e]).core_identity.status,'confirmed');});
