/* Browser production wiring. All API payloads are fabricated; no live recognition. */
const {test,before,after}=require('node:test'),assert=require('node:assert/strict'),http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require('playwright');
let server,browser,page,origin,photos,requests=[],googleRequests=[],vision,initialMode='',comparison,comparisonQueue=[],catalogText='',printingReply,providerMode='ok',resolverMode='',waitApi=null,errors=[],usageOverrides={};
const root=path.join(__dirname,'../src/main/assets');
const unknown={status:'uncertain',kind:'object',title:'Oggetto',category:'object',brand:'',family:'',model:'',variant:'',condition:'raw',category_confidence:70,brand_confidence:0,family_confidence:0,model_confidence:20,model_verified:false,market_ready:false,candidate_models:[],visual_fingerprint:'geometrical outline',distinctive_terms:[],search_terms:[],identifier_hints:[],layout_signature:[],evidence:[],missing_information:['catalogue identity'],next_photo_request:null,user_text_consistent:true,normalized_query:'',verification_summary:'Mock only',pokemon_printing:null,photo_clues:[],object_unit:'object',object_region:{image_index:1,x:.1,y:.1,width:.8,height:.8,certain:true}};
const known={...unknown,status:'identified',brand:'Example',family:'Series',model:'Known model',title:'Known model',model_confidence:94,market_ready:true,normalized_query:'Example Known model'};
function candidate(){return {category:'object',brand:'Example',family:'Series',model:'Documented model',year:'',issue_number:'',catalog_number:'',unit:'object',variant:'',identity_level:'exact',specimen_notes:[],decision:'match',same_unit:true,physical_ambiguity:false,conflicts:[],matches:[{reference_id:'ref1',feature:'layout',photo_detail:'two round controls',reference_detail:'two round controls',reference_evidence:'image',agrees:true},{reference_id:'ref1',feature:'shape',photo_detail:'rectangular body',reference_detail:'rectangular body',reference_evidence:'image',agrees:true}],fields:[{field:'model',scope:'target',number_kind:'model_number',value:'Documented model',reference_id:'ref1',quote:'Catalogue entry: Documented model.'}]};}
async function reset(enabled=true){usageOverrides={};requests=[];googleRequests=[];initialMode='';vision=structuredClone(unknown);comparison={physical_detail_needed:null,candidates:[candidate()]};providerMode='ok';resolverMode='';printingReply=null;comparisonQueue=[];catalogText='Catalogue entry: Documented model.';waitApi=null;await page.goto(origin);await page.waitForFunction(()=>typeof newContext164==='function');await page.evaluate(enabled=>{window.FlipCheckTestMode='mock';trial={free:true,attempts:2,credits:0};saveTrial();$('apiKey').value='fake-openai';$('visualEnabled').checked=enabled;$('googleApiKey').value='fake-google-key-1234567890';$('scanBudget').value='.03';$('budgetFx').value='1';},enabled);}
async function upload(){await page.locator('#photoBatch').setInputFiles(photos);await page.waitForFunction(()=>!photoBusy);}
async function identify(){await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy,{},{timeout:12000});}
before(async()=>{
 server=http.createServer((req,res)=>{const name=req.url==='/'?'index.html':req.url.slice(1);if(!['index.html','editions.js','targeted-fixes.js','visual-policy.js','visual-runtime.js','google-direct.js'].includes(name)){res.writeHead(404);return res.end();}res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':'text/html');res.end(fs.readFileSync(path.join(root,name)));});await new Promise(r=>server.listen(0,'127.0.0.1',r));origin='http://127.0.0.1:'+server.address().port;
 browser=await chromium.launch({headless:true,args:['--no-sandbox']});page=await browser.newPage({viewport:{width:412,height:915}});page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.FlipCheckGoogle={request(id,action,payload){fetch('https://native.example/'+action,{method:'POST',body:payload}).then(r=>r.json()).then(result=>window.FlipCheckDirect.receive(id,result));},cancel(){}};});
 await page.route('**/*',async route=>{
  const u=route.request().url();if(u.startsWith(origin))return route.continue();
  if(u==='https://api.openai.com/v1/responses'){
   const body=JSON.parse(route.request().postData());requests.push(body);
   if(body.text.format.name==='flipcheck_identification'&&initialMode&&(initialMode==='always'||requests.filter(r=>r.text.format.name==='flipcheck_identification').length===1))return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({status:'incomplete',incomplete_details:{reason:'max_output_tokens'},output:[{type:'message',content:[{type:'output_text',text:'{'}]}],usage:{input_tokens:4000,output_tokens:3200}})});
   let payload=body.text.format.name==='flipcheck_resolver_recovery'?{candidate_checks:[],verification_summary:'Insufficient evidence',missing_information:['Exact model label']}:body.text.format.name==='flipcheck_printing_detail'?{pokemon_printing:printingReply||vision.pokemon_printing}:body.text.format.name==='flipcheck_visual_comparison'?(comparisonQueue.length?comparisonQueue.shift():comparison):body.text.format.name==='flipcheck_market'?{market_status:'insufficient',exact_completed_sales_count:0,active_listings_count:0,market_low:null,market_high:null,quick_sale_price:null,historical_new_price:null,currency:'EUR',market_notes:'No verified comparable sales',source_summary:''}:vision;
   if(body.text.format.name==='flipcheck_resolver'&&resolverMode){
    if(resolverMode==='unauthorized')return route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({error:{message:'API key rejected'}})});
    const source=resolverMode==='box_completion'?{url:'https://catalog.example/box-entry',title:'2025-26 Topps Chrome Update Series Basketball Hobby, Box',snippet:'1 autograph in every box.'}:{url:'https://acme.example/manual/zx-430',title:'Acme ZX-430 water pump product manual',snippet:'Acme ZX-430 water pump. Two round controls, rectangular body. Model ZX-430.'};
    const withSource=resolverMode==='source'||resolverMode==='complete_source'||resolverMode==='google_incomplete'||resolverMode==='box_completion';
    const catalogue=resolverMode.startsWith('catalogue');
    const sources=catalogue?Array.from({length:resolverMode==='catalogue_cost'?3:1},(_,i)=>({url:'https://catalog.example/entry'+i+(resolverMode==='catalogue_pdf'?'.pdf':''),title:'Catalogue entry: Documented model.',snippet:'Two round controls. Rectangular body. Catalogue entry: Documented model.'})):withSource?[source]:[];
    const output=[{type:'web_search_call',status:'completed',action:{type:'search',sources},results:sources},{type:'message',content:[{type:'output_text',text:resolverMode==='malformed'?'{':JSON.stringify({candidate_checks:[],verification_summary:'Mock response',missing_information:[]})}]}];
    const incomplete=!['malformed','complete_source','box_completion'].includes(resolverMode)&&!catalogue;
    return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({status:incomplete?'incomplete':'completed',...(incomplete?{incomplete_details:{reason:'max_output_tokens'}}:{}),output,usage:resolverMode==='expensive'?{input_tokens:60000,output_tokens:1500}:resolverMode==='catalogue_cost'?{input_tokens:24000,output_tokens:400}:{input_tokens:100,output_tokens:100}})});
   }
   if(waitApi){const pending=waitApi;await pending;}
   return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({status:'completed',output:[{type:'message',content:[{type:'output_text',text:JSON.stringify(payload)}]}],usage:usageOverrides[body.text.format.name]||{input_tokens:100,output_tokens:100}})});
  }
  if(u.startsWith('https://native.example/')){
   const action=u.split('/').pop(),body=JSON.parse(route.request().postData());googleRequests.push({action,...body});
   let reply;
   if(action==='detect'){
    if(providerMode==='network')reply={status:0,state:'dns_error',attempted:true};
    else if(providerMode==='timeout')reply={status:0,state:'timeout',attempted:true};
    else if(providerMode==='invalid')reply={status:400,body:{error:{details:[{reason:'API_KEY_INVALID'}]}}};
    else if(providerMode==='disabled')reply={status:403,body:{error:{details:[{reason:'SERVICE_DISABLED'}]}}};
    else if(providerMode==='quota')reply={status:429,body:{error:{status:'RESOURCE_EXHAUSTED'}}};
    else {const linked={url:'https://catalog.example/item',pageTitle:'Catalogue entry: Documented model.',fullMatchingImages:[{url:'https://catalog.example/item.png'}]};const empty=Array.from({length:7},(_,i)=>({url:'https://catalog.example/generic'+i,pageTitle:'Generic page'}));reply={status:200,body:{responses:[{webDetection:{webEntities:[{description:'Documented model',score:.99}],pagesWithMatchingImages:providerMode==='late_images'?[...empty,linked]:providerMode==='no_images'?empty:[linked]}}]}};}
   }else if(action==='page'&&resolverMode==='catalogue_pdf')reply={status:200,document_type:'pdf',page_count:8,pages_rendered:[1,2,3],image_data:'data:image/png;base64,'+photos[0].buffer.toString('base64')};
   else if(action==='page')reply={status:200,text:catalogText,images:resolverMode.startsWith('catalogue')?['https://catalog.example/'+(resolverMode==='catalogue_cost'?body.url.split('/').pop():'item')+'.png']:[]};
   else if(action==='image')reply={status:200,image_data:'data:image/png;base64,'+photos[0].buffer.toString('base64')};
   else throw new Error('Unexpected native action '+action);
   return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(reply)});
  }
  throw new Error('Forbidden live request '+u);
 });
 await reset();const image=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=400;c.height=600;c.getContext('2d').fillStyle='red';c.getContext('2d').fillRect(0,0,400,600);return c.toDataURL('image/png').split(',')[1];});photos=[{name:'synthetic-object.png',mimeType:'image/png',buffer:Buffer.from(image,'base64')}];
});
after(async()=>{await browser?.close();if(server)await new Promise(r=>server.close(r));});
test('known v26 identity skips all Google calls and keeps original market identity',async()=>{await reset();vision=known;await upload();await identify();assert.equal(requests.length,1);assert.equal(googleRequests.length,0);assert.equal(await page.evaluate(()=>ident.model),'Known model');assert.equal(await page.evaluate(()=>diagnostic26().visualAssistance.provider.state),'skipped_identity_confirmed');});
test('empty OCR triggers one image lookup and real production comparison',async()=>{await reset();await upload();await identify();assert.equal(requests.length,2);assert.equal(requests[1].text.format.name,'flipcheck_visual_comparison');assert.equal(requests[1].tools,undefined);assert.equal(googleRequests.length,3);assert.ok(googleRequests[0].image_base64);assert.equal(googleRequests[0].apiKey,'fake-google-key-1234567890');assert.ok(googleRequests.slice(1).every(r=>!r.apiKey));assert.equal(await page.evaluate(()=>ident.market_ready),true);assert.equal(await page.locator('#visualResult').count(),1);
 const d=await page.evaluate(()=>diagnostic26());assert.equal(d.visualAssistance.imagePreparation.cropped,true);assert.equal(d.visualAssistance.imagePreparation.originalWidth,400);assert.ok(d.visualAssistance.closures.some(x=>x.closure_stage==='production_after_assisted_verification'&&x.closure_result));assert.doesNotMatch(JSON.stringify(d),/fake-google-key|fake-openai|image_base64|image_data/);
});
test('disabled provider finishes without fake photo request or repeated lookup',async()=>{await reset();providerMode='disabled';await upload();await identify();assert.equal(requests.length,1);assert.equal(googleRequests.length,1);assert.equal(await page.evaluate(()=>ident.assistance_state),'service_unavailable');assert.equal(await page.locator('#addConfirmPhoto').isVisible(),false);});
test('ambiguous reference remains uncertain and asks a specific physical detail',async()=>{await reset();comparison={physical_detail_needed:'Retro per distinguere le due stampe.',candidates:[{...candidate(),physical_ambiguity:true}]};await upload();await identify();assert.equal(await page.evaluate(()=>ident.market_ready),false);assert.match(await page.locator('#visualResult').textContent(),/Retro per distinguere/);});
test('budget stops Google before annotation when remaining credit cannot cover it',async()=>{await reset();await page.evaluate(()=>{$('scanBudget').value='.001';});await upload();await identify();assert.equal(googleRequests.length,0);assert.equal(requests.length,0);assert.equal(await page.evaluate(()=>apiBusy),false);});
test('timeout returns without retries and unknown billing remains reserved',async()=>{await reset();providerMode='timeout';await upload();await identify();assert.equal(googleRequests.length,1);const d=await page.evaluate(()=>diagnostic26());assert.equal(d.visualAssistance.budget.entries.find(e=>e.kind==='visual').actualUsd,null);assert.equal(await page.evaluate(()=>apiBusy),false);});
test('cancelled response cannot overwrite next scan and controls recover',async()=>{await reset();let release;waitApi=new Promise(r=>release=r);await upload();await page.locator('#identifyBtn').click();await page.waitForFunction(()=>apiBusy);await page.locator('#cancelScan').click();await page.waitForFunction(()=>!apiBusy);assert.equal(await page.locator('#identifyBtn').isDisabled(),false);waitApi=null;vision=known;await identify();release();await page.waitForTimeout(100);assert.equal(await page.evaluate(()=>ident.model),'Known model');});
test('market missing prices keeps confirmed identity',async()=>{await reset();vision=known;await upload();await identify();await page.locator('#marketBtn').click();await page.waitForFunction(()=>!apiBusy);assert.equal(await page.evaluate(()=>ident.market_ready),true);assert.equal(await page.evaluate(()=>ident.model),'Known model');});
test('feature disabled sends the baseline identification schema',async()=>{await reset(false);vision=known;await upload();await identify();assert.equal(requests[0].text.format.schema.properties.photo_clues,undefined);assert.equal(googleRequests.length,0);});
test('only Google key is required for assistance and no key is saved in preferences',async()=>{await reset();assert.equal(await page.locator('#visualEndpoint,#visualAccess').count(),0);await page.evaluate(()=>updateVisual164());assert.doesNotMatch(await page.evaluate(()=>JSON.stringify(localStorage)),/fake-google-key/);await page.reload();assert.equal(await page.locator('#googleApiKey').inputValue(),'');});
test('missing Google key leaves original schema and known identity working',async()=>{await reset();await page.evaluate(()=>{$('googleApiKey').value='';});vision=known;await upload();await identify();assert.equal(requests[0].text.format.schema.properties.photo_clues,undefined);assert.equal(googleRequests.length,0);assert.equal(await page.evaluate(()=>ident.market_ready),true);});
test('invalid key and exhausted quota have useful final messages and no retry',async()=>{for(const mode of ['invalid','quota']){await reset();providerMode=mode;await upload();await identify();assert.equal(googleRequests.length,1);assert.equal(await page.evaluate(()=>ident.assistance_state),'service_unavailable');assert.match(await page.locator('#visualResult').textContent(),mode==='invalid'?/Chiave Google non valida/:/Quota Google esaurita/);}});
test('direct native timeout cancels the pending request and drops a late response',async()=>{await reset();const result=await page.evaluate(async()=>{let id,cancelled=false;const previous=window.FlipCheckGoogle;window.FlipCheckGoogle={request(value){id=value;},cancel(){cancelled=true;}};let state;try{await FlipCheckDirect.call('detect',{}, {timeoutMs:20});}catch(e){state=e.message;}FlipCheckDirect.receive(id,{status:200});window.FlipCheckGoogle=previous;return {state,cancelled};});assert.deepEqual(result,{state:'scan_timeout',cancelled:true});});
function textObject(){vision={...structuredClone(unknown),brand:'Example',family:'Series',photo_clues:[{text:'Two round controls',role:'text',certainty:'clear',image_index:1,region:null}],distinctive_terms:['Two round controls'],object_region:null};}
test('incomplete or malformed textual JSON reaches Google once without a second text request',async()=>{
 for(const mode of ['incomplete','malformed']){await reset();textObject();resolverMode=mode;await upload();await identify();
  assert.deepEqual(requests.map(r=>r.text.format.name),['flipcheck_identification','flipcheck_resolver','flipcheck_visual_comparison']);
  const resolver=requests[1];assert.equal(resolver.max_output_tokens,2600);assert.deepEqual(Object.keys(resolver.text.format.schema.properties).sort(),['candidate_checks','missing_information','verification_summary']);
  assert.equal(googleRequests.filter(r=>r.action==='detect').length,1);assert.equal(await page.evaluate(()=>ident.market_ready),true);
  const d=await page.evaluate(()=>diagnostic26());assert.equal(d.visualAssistance.recoveries.length,1);assert.equal(d.visualAssistance.recoveries[0].partialJsonDiscarded,true);
  if(mode==='incomplete'){assert.equal(d.visualAssistance.calls[1].state,'incomplete');assert.equal(d.visualAssistance.calls[1].incompleteReason,'max_output_tokens');}
 }
});
test('existing complete source evidence is retained when text JSON is cut off',async()=>{
 for(const mode of ['source','complete_source']){await reset();textObject();resolverMode=mode;
  vision={...vision,brand:'Acme',family:'water pump',category:'water pump',title:'Acme water pump',identifier_hints:['ZX-430'],distinctive_terms:['ZX-430','water pump','two round controls'],photo_clues:[{text:'ZX-430',role:'model',certainty:'clear',image_index:1,region:null}]};
  await upload();await identify();assert.equal(requests.length,2);assert.equal(googleRequests.length,0);assert.equal(await page.evaluate(()=>ident.market_ready),true);assert.match(await page.evaluate(()=>ident.model),/ZX-430/);
  const d=await page.evaluate(()=>diagnostic26());assert.equal(d.visualAssistance.recoveries.length,mode==='source'?1:0);
 }
});
test('incomplete text with insufficient remaining budget skips Google and keeps an explicit budget result',async()=>{await reset();textObject();resolverMode='expensive';await upload();await identify();assert.equal(requests.length,2);assert.equal(googleRequests.length,0);const d=await page.evaluate(()=>diagnostic26());assert.equal(d.visualAssistance.provider.state,'skipped_budget');assert.equal(await page.evaluate(()=>ident.assistance_state),'budget_exhausted');assert.ok(d.visualAssistance.budget.spentOrReservedUsd<=.025);});
test('an OpenAI authentication error is not retried through Google',async()=>{await reset();textObject();resolverMode='unauthorized';await upload();await identify();assert.equal(requests.length,2);assert.equal(googleRequests.length,0);assert.equal(await page.evaluate(()=>ident.assistance_state),'service_unavailable');});
test('linked images beyond the first three Google pages are compared',async()=>{await reset();providerMode='late_images';await upload();await identify();assert.equal(googleRequests.length,3);assert.equal(await page.evaluate(()=>ident.market_ready),true);assert.equal(await page.evaluate(()=>diagnostic26().visualAssistance.provider.referenceAttempts),1);});
test('pages without linked images are not reported as failed image downloads',async()=>{await reset();providerMode='no_images';await upload();await identify();const p=await page.evaluate(()=>diagnostic26().visualAssistance.provider);assert.equal(p.referenceAttempts,0);assert.equal(p.referenceState,'no_linked_images');assert.equal(await page.evaluate(()=>ident.assistance_state),'unidentified');assert.equal(googleRequests.length,1);});
test('existing catalogue images are verified before spending on Google; uncertain title data stay out',async()=>{
 await reset();textObject();resolverMode='catalogue';vision.title='Guessed premium edition';vision.evidence=['MAYBE OCR'];vision.variant='Guessed premium edition';
 vision.photo_clues.push({text:'Rectangular body',role:'text',certainty:'clear',image_index:1,region:null},{text:'MAYBE OCR',role:'text',certainty:'uncertain',image_index:1,region:null});
 await upload();await identify();assert.deepEqual(requests.map(r=>r.text.format.name),['flipcheck_identification','flipcheck_resolver','flipcheck_visual_comparison']);
 assert.equal(googleRequests.filter(r=>r.action==='detect').length,0);assert.equal(googleRequests.length,2);assert.equal(await page.evaluate(()=>ident.market_ready),true);
 assert.doesNotMatch(requests[1].input,/Guessed premium edition|MAYBE OCR/);assert.doesNotMatch(JSON.stringify(requests[2].input),/Guessed premium edition|MAYBE OCR/);
 assert.doesNotMatch(await page.evaluate(()=>ident.normalized_query),/Guessed premium edition/);
 assert.equal(await page.evaluate(()=>diagnostic26().visualAssistance.provider.state),'skipped_catalogue_references');
 assert.deepEqual(await page.evaluate(()=>rawModelCodes('GAME-WORN model ZX-430')),['ZX-430']);
});
test('comparison selects only as many references as remaining budget can fund',async()=>{
 await reset();await page.evaluate(()=>{$('scanBudget').value='.025';});textObject();resolverMode='catalogue_cost';vision.photo_clues.push({text:'Rectangular body',role:'text',certainty:'clear',image_index:1,region:null});
 await upload();await identify();const d=await page.evaluate(()=>diagnostic26());assert.equal(d.visualAssistance.comparison.availableReferences,3);assert.ok(d.visualAssistance.comparison.referenceIds.length<3);assert.ok(d.visualAssistance.comparison.referenceIds.length>=1);assert.ok(d.visualAssistance.budget.spentOrReservedUsd<=.025);assert.equal(await page.evaluate(()=>ident.market_ready),true);assert.equal(googleRequests.filter(r=>r.action==='detect').length,0);
});


const printing168={is_pokemon:true,language:'English',set_name:'Base Set',card_type:'pokemon',first_edition_stamp:'present',stamp_image:1,stamp_location:'left below artwork',stamp_text:'1 EDITION',artwork_shadow:'unclear',shadow_image:1,shadow_location:'right artwork border',copyright_text:'',copyright_image:0,slab_text:'',slab_image:0};
function printingCard(){vision={...structuredClone(known),kind:'card',brand:'Pokemon',family:'Base Set',model:'Machamp 8/102',title:'Machamp',variant:'Holo',normalized_query:'Pokemon Base Set Machamp 8/102',pokemon_printing:{...printing168},printing_detail_regions:[{detail:'shadow',region:{image_index:1,x:.8,y:.2,width:.08,height:.4,certain:true}},{detail:'copyright',region:{image_index:1,x:.1,y:.9,width:.7,height:.04,certain:true}}]};}
test('uncertain printing is reread once from original detail crops without web and preserves certain stamp',async()=>{
 await reset();printingCard();printingReply={...printing168,first_edition_stamp:'absent',artwork_shadow:'absent',copyright_text:'©1995,96,98 Nintendo ©1999 Wizards',copyright_image:1};await upload();await identify();
 assert.deepEqual(requests.map(r=>r.text.format.name),['flipcheck_identification','flipcheck_printing_detail']);assert.equal(requests[1].tools,undefined);assert.equal(googleRequests.length,0);
 const d=await page.evaluate(()=>diagnostic26());assert.equal(d.identification.market_ready,true);assert.equal(d.identification.printing_check.stamp,'present');assert.equal(d.identification.printing_check.shadow,'absent');assert.equal(d.visualAssistance.identityState,'confirmed');assert.equal(d.visualAssistance.printingRecovery.images.length,2);assert.ok(d.visualAssistance.printingRecovery.images.every(p=>p.cropped&&p.sentWidth<400));assert.equal(d.visualAssistance.closures[0].closure_result,false);assert.equal(d.visualAssistance.closures.at(-1).closure_result,true);
});
test('unreadable printing finishes with one specific detail request and never a confirmed state',async()=>{
 await reset();printingCard();await upload();await identify();const d=await page.evaluate(()=>diagnostic26());assert.equal(requests.length,2);assert.equal(googleRequests.length,0);assert.equal(d.identification.market_ready,false);assert.equal(d.visualAssistance.identityState,'physical_detail_needed');assert.ok(d.visualAssistance.closures.every(c=>!c.closure_result));assert.match(d.identification.next_photo_request,/bordo destro.*copyright/);assert.doesNotMatch(d.identification.next_photo_request,/Retro/);
});
test('physical observations and PDF figures are passed to comparison before Google',async()=>{
 await reset();textObject();resolverMode='catalogue_pdf';vision.physical_observations=[{text:'Rectangular body',feature:'shape',certainty:'clear',entity:'target',image_index:1}];
 await upload();await identify();assert.deepEqual(requests.map(r=>r.text.format.name),['flipcheck_identification','flipcheck_resolver','flipcheck_visual_comparison']);assert.match(requests[1].input,/physical_observations.*Rectangular body/);assert.equal(googleRequests.length,1);assert.equal(googleRequests[0].action,'page');assert.match(JSON.stringify(requests[2].input),/web_indexed_document/);assert.match(JSON.stringify(requests[2].input),/pages_rendered/);assert.equal(await page.evaluate(()=>ident.market_ready),true);
});
test('season is removed from identifier scoring while explicit user model survives',async()=>{
 await reset();const sig=await page.evaluate(()=>{lastVisionReading={...lastVisionReading,identifier_hints:['2025/26'],photo_clues:[{text:'2025/26',role:'season',certainty:'clear'}]};return buildFingerprintSignature(lastVisionReading,'MODEL ZX-430');});assert.deepEqual(sig.identifiers.map(i=>i.value),['ZX-430']);assert.ok(!sig.identifierVariants.some(i=>i.value==='2025'));
});
test('genuine quoted configuration may close without an identifier; conflicting candidates cannot both close',async()=>{
 await reset();const out=await page.evaluate(()=>{
  lastVisionReading={photo_clues:['Acme Delta','Portable Kit','2 batteries included','2025/26'].map(text=>({text,role:text==='2025/26'?'season':'text',certainty:'clear'}))};
  const sig=buildFingerprintSignature(lastVisionReading,''),source={url:'https://acme.example/kit',text:'Acme Delta Portable Kit 2025/26 has 2 batteries included.'};
  const c={model:'Acme Delta Portable Kit',source_specificity:'exact_model',strong_source_count:1,matched_terms:lastVisionReading.photo_clues.map(c=>c.text),conflicting_terms:[],evidence_sources:[{url:source.url,quality:3}],match_evidence:[{photo_text:'2 batteries included',source_text:'2 batteries included',source_url:source.url}]};
  const a=augmentCandidatesFromRawResults({candidate_checks:[c]},[source],sig).candidate_checks.find(x=>x.model===c.model);
  const both=augmentCandidatesFromRawResults({candidate_checks:[c,{...c,model:'Acme Delta Different Kit'}]},[source],sig).candidate_checks;
  return {ids:sig.identifiers,complete:a.complete_observed_match,score:candidateFingerprintScore(a,sig,true).score,both:both.map(x=>({complete:x.complete_observed_match,score:candidateFingerprintScore(x,sig,true).score}))};
 });assert.deepEqual(out.ids,[]);assert.equal(out.complete,true);assert.ok(out.score>=85);assert.ok(out.both.every(c=>!c.complete&&c.score<=84));assert.equal(requests.length,0);
});
test('market budget refusal keeps identity confirmed and does not consume trial entitlement',async()=>{
 await reset();vision=known;await upload();await identify();const before=await page.evaluate(()=>{scan164.budget.maxUsd=scan164.budget.spent();return JSON.stringify(trial);});await page.locator('#marketBtn').click();await page.waitForFunction(()=>!apiBusy);
 const d=await page.evaluate(()=>diagnostic26());assert.equal(requests.length,1);assert.equal(d.identification.market_ready,true);assert.equal(d.visualAssistance.state,'confirmed');assert.equal(d.visualAssistance.comparablesState,'budget_exhausted');assert.equal(await page.evaluate(()=>JSON.stringify(trial)),before);
});


test('Google image lookup precedes text for an uncertain card variant and comparison includes both sides',async()=>{
 await reset();vision={...structuredClone(known),kind:'card',object_unit:'single',market_ready:false,variant:'Green Pulsar, likely',photo_clues:[{text:'Visible player name',role:'text',certainty:'clear',image_index:1,region:null}],physical_observations:[{feature:'color',text:'Green patterned frame',certainty:'clear',entity:'target',image_index:1}]};
 catalogText+=' Parallel: Green Pulsar.';comparison.candidates[0]={...candidate(),unit:'single',variant:'Green Pulsar',fields:[...candidate().fields,{field:'variant',scope:'target',number_kind:'none',value:'Green Pulsar',quote:'Parallel: Green Pulsar.',reference_id:'ref1'}],matches:[...candidate().matches,{reference_id:'ref1',feature:'color',photo_detail:'green frame',reference_detail:'green frame',reference_evidence:'image',agrees:true}]};
 const back=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=400;c.height=600;const x=c.getContext('2d');x.fillStyle='blue';x.fillRect(0,0,400,600);return c.toDataURL('image/png').split(',')[1];});
 await page.locator('#photoBatch').setInputFiles([photos[0],{name:'synthetic-back.png',mimeType:'image/png',buffer:Buffer.from(back,'base64')}]);await page.waitForFunction(()=>!photoBusy);assert.equal(await page.evaluate(()=>validImageCount()),2);await identify();
 assert.deepEqual(requests.map(r=>r.text.format.name),['flipcheck_identification','flipcheck_visual_comparison']);assert.equal(googleRequests.filter(r=>r.action==='detect').length,1);assert.equal(requests[1].input[0].content.filter(c=>c.type==='input_image').length,3);
 const d=await page.evaluate(()=>diagnostic26());assert.equal(d.identification.market_ready,true);assert.equal(d.identification.variant,'Green Pulsar');assert.deepEqual(d.visualAssistance.comparison.photoIndexes,[1,2]);assert.equal(d.visualAssistance.route,'google_first');assert.ok(d.visualAssistance.budget.spentOrReservedUsd<=.025);
});
test('unconfirmed catalogue images allow a single Google recovery and a new verified reference',async()=>{
 await reset();textObject();vision.photo_clues.push({text:'Rectangular body',role:'text',certainty:'clear',image_index:1,region:null});resolverMode='catalogue';comparisonQueue=[{physical_detail_needed:null,candidates:[]},comparison];
 await upload();await identify();assert.deepEqual(requests.map(r=>r.text.format.name),['flipcheck_identification','flipcheck_resolver','flipcheck_visual_comparison','flipcheck_visual_comparison']);assert.equal(googleRequests.filter(r=>r.action==='detect').length,1);assert.equal(await page.evaluate(()=>ident.market_ready),true);assert.equal(await page.evaluate(()=>diagnostic26().visualAssistance.comparisons.length),2);
});
test('Google first with no match can use one text fallback and never repeat the Google call',async()=>{
 await reset();textObject();vision.kind='card';vision.object_unit='single';vision.photo_clues.push({text:'Rectangular body',role:'text',certainty:'clear',image_index:1,region:null});resolverMode='catalogue';comparisonQueue=[{physical_detail_needed:null,candidates:[]},{physical_detail_needed:null,candidates:[]}];
 await upload();await identify();assert.equal(requests.filter(r=>r.text.format.name==='flipcheck_resolver').length,1);assert.equal(googleRequests.filter(r=>r.action==='detect').length,1);assert.equal(await page.evaluate(()=>ident.market_ready),false);assert.equal(await page.evaluate(()=>apiBusy),false);
});
test('Google preflight reserves room for verification before spending on detection',async()=>{
 await reset();await upload();const result=await page.evaluate(async()=>{scan164=newContext164();scan164.budget.maxUsd=.004;const photos=await targetPhotos169({},scan164);return googleResolve169({market_ready:false},scan164,photos);});assert.equal(result.assistance_state,'budget_exhausted');assert.equal(googleRequests.length,0);assert.equal(requests.length,0);
});
test('recorded uncertain shadow region also sends the full card instead of copyright alone',async()=>{
 await reset();printingCard();const recorded=require('./fixtures/diagnostics-168.json').mach;vision.printing_detail_regions=recorded.vision.printing_detail_regions;vision.object_region=recorded.vision.object_region;
 await upload();await identify();const p=await page.evaluate(()=>diagnostic26().visualAssistance.printingRecovery);assert.equal(p.images.length,2);assert.ok(p.coverage.some(r=>r.detail==='shadow'&&r.fallback));assert.ok(p.images.some(r=>r.rect.height>=590));assert.equal(googleRequests.length,0);
});
test('confirmed Hobby identity clears the old Blaster title and obsolete SKU request',async()=>{
 await reset();const recorded=require('./fixtures/diagnostics-168.json').box.identity;const out=await page.evaluate(value=>syncIdentity169(value),recorded);assert.match(out.title,/Hobby/);assert.doesNotMatch(out.title,/Blaster/);assert.deepEqual(out.missing_information,[]);assert.equal(out.market_ready,true);
});


test('incomplete initial Vision gets one bounded retry with a larger response and correct cost accounting',async()=>{
 await reset();vision=known;initialMode='once';await upload();await identify();const d=await page.evaluate(()=>diagnostic26());
 assert.equal(requests.length,2);assert.equal(requests[0].max_output_tokens,4500);assert.equal(requests[1].max_output_tokens,6000);assert.equal(requests[0].reasoning.effort,'low');assert.equal(d.usage.requests,2);assert.equal(d.identification.market_ready,true);assert.equal(d.visualAssistance.recoveries.filter(r=>r.stage==='initial_vision').length,1);assert.equal(googleRequests.length,0);
});
test('two incomplete Vision responses stop without loops or invented identification',async()=>{
 await reset();initialMode='always';await upload();await identify();const d=await page.evaluate(()=>diagnostic26());assert.equal(requests.length,2);assert.equal(d.identification,null);assert.equal(d.visualAssistance.state,'response_incomplete');assert.equal(d.usage.requests,2);assert.equal(await page.evaluate(()=>apiBusy),false);
});
test('recorded high confidence subtype uncertainty reaches Google instead of closing early',async()=>{
 await reset();vision=structuredClone(require('./fixtures/diagnostics-169.json').doncic.vision);vision.object_regions=[];vision.object_region=null;providerMode='no_images';resolverMode='catalogue';comparison={physical_detail_needed:null,candidates:[]};await page.evaluate(()=>{$('scanBudget').value='.03';});
 await upload();await identify();const d=await page.evaluate(()=>diagnostic26());assert.equal(d.visualAssistance.closures[0].closure_result,false);assert.equal(googleRequests.filter(r=>r.action==='detect').length,1);assert.equal(d.identification.market_ready,false);assert.equal(d.identification.variant_needs_verification,true);assert.ok(d.visualAssistance.budget.spentOrReservedUsd<=.03);
});
test('recorded box closes from Google reference comparison without a subsequent text search',async()=>{
 await reset();const fixture=require('./fixtures/diagnostics-169.json').box;vision=structuredClone(fixture.vision);comparison=structuredClone(fixture.comparison);catalogText=fixture.references[0].text;await page.evaluate(()=>{$('scanBudget').value='.03';});
 await upload();await identify();const d=await page.evaluate(()=>diagnostic26());assert.equal(d.identification.market_ready,true);assert.equal(d.identification.variant,'Hobby Box');assert.equal(requests.filter(r=>r.tools?.length).length,0);assert.equal(googleRequests.filter(r=>r.action==='detect').length,1);
});
test('Google-first truncated web output can be completed from collected sources without another web search',async()=>{
 await reset();textObject();vision.kind='card';vision.object_unit='single';resolverMode='google_incomplete';providerMode='no_images';comparison={physical_detail_needed:null,candidates:[]};await page.evaluate(()=>{$('scanBudget').value='.03';});
 await upload();await identify();const d=await page.evaluate(()=>diagnostic26());assert.equal(requests.filter(r=>r.tools?.length).length,1);assert.equal(requests.filter(r=>r.text.format.name==='flipcheck_resolver_recovery').length,1);assert.equal(d.visualAssistance.recoveries.filter(r=>r.stage==='text_response_completion').length,1);assert.equal(d.identification.market_ready,false);
});
test('default budget migration raises the previous ceiling and preserves a deliberately smaller cap',async()=>{
 await reset();await page.evaluate(()=>localStorage.setItem('flipcheck_visual_config',JSON.stringify({scanBudget:.025,budgetFx:1,enabled:true})));await page.reload();assert.equal(await page.locator('#scanBudget').inputValue(),'.03');assert.equal(await page.evaluate(()=>visualConfig164().maxEur),.03);
 await page.evaluate(()=>localStorage.setItem('flipcheck_visual_config',JSON.stringify({scanBudget:.01,budgetFx:1,enabled:true})));await page.reload();assert.equal(await page.evaluate(()=>visualConfig164().maxEur),.01);
});

test('build170 two-photo Google failure permits the actual text request within 0.03',async()=>{
 await reset();const f=require('./fixtures/diagnostics-170.json').doncic;vision=structuredClone(f.vision);usageOverrides.flipcheck_identification=f.phases[0].usage;providerMode='network';resolverMode='catalogue';comparison={physical_detail_needed:null,candidates:[]};
 const back=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=420;c.height=600;c.getContext('2d').fillStyle='blue';c.getContext('2d').fillRect(0,0,420,600);return c.toDataURL('image/png').split(',')[1];});
 await page.locator('#photoBatch').setInputFiles([photos[0],{name:'back.png',mimeType:'image/png',buffer:Buffer.from(back,'base64')}]);await page.waitForFunction(()=>!photoBusy);await identify();
 const d=await page.evaluate(()=>diagnostic26());assert.equal(d.uploadedImageCount,2);assert.equal(requests.filter(r=>r.tools?.length).length,1);assert.equal(googleRequests.filter(r=>r.action==='detect').length,1);assert.equal(d.visualAssistance.calls.find(c=>c.provider==='google').failureReason,'dns_error');assert.equal(d.visualAssistance.continuationBudget.strategy,'next_request');assert.ok(d.visualAssistance.budget.spentOrReservedUsd<=.03);assert.equal(d.identification.market_ready,false);
});
test('build170 box reuses its visual comparison and closes after a specification query',async()=>{
 await reset();const f=require('./fixtures/diagnostics-170.json').box;vision=structuredClone(f.vision);comparison=structuredClone(f.phases.find(p=>p.stage==='flipcheck_visual_comparison').result);
 for(const c of comparison.candidates)for(const entry of [...c.matches,...c.fields])entry.reference_id='ref1';
 catalogText='2025-26 Topps Chrome Update Series Basketball Hobby, Box';resolverMode='box_completion';
 usageOverrides.flipcheck_identification=f.phases[0].usage;usageOverrides.flipcheck_visual_comparison=f.phases[1].usage;
 await upload();await identify();const d=await page.evaluate(()=>diagnostic26());assert.equal(requests.filter(r=>r.tools?.length).length,1);assert.equal(requests.filter(r=>r.text.format.name==='flipcheck_visual_comparison').length,1);assert.match(requests.find(r=>r.tools?.length).input,/1 AUTOGRAPH IN EVERY BOX/);assert.equal(d.identification.market_ready,true);assert.match(d.identification.title,/Hobby/);assert.equal(d.visualAssistance.referenceCompletion.reusedComparison,true);assert.ok(d.visualAssistance.budget.spentOrReservedUsd<=.03);
});
test('build170 inferred set is researched before an uncertain edition stamp',async()=>{
 await reset();vision=structuredClone(require('./fixtures/diagnostics-170.json').politoed.vision);providerMode='no_images';resolverMode='catalogue';comparison={physical_detail_needed:null,candidates:[]};await upload();await identify();
 assert.equal(requests.filter(r=>r.text.format.name==='flipcheck_printing_detail').length,0);assert.equal(requests.filter(r=>r.tools?.length).length,1);assert.equal(googleRequests.filter(r=>r.action==='detect').length,1);
 const query=requests.find(r=>r.tools?.length).input;assert.match(query,/Crescita Improvvisa/);assert.doesNotMatch(query,/12\/111|Neo Genesis/);assert.equal(await page.evaluate(()=>ident.market_ready),false);
});
test('printing applicability is reread against verified catalogue without overriding a certain stamp',async()=>{
 await reset();await upload();const raw=structuredClone(require('./fixtures/diagnostics-170.json').politoed.vision);
 printingReply={...raw.pokemon_printing,set_name:'Verified catalogue series',first_edition_stamp:'not_applicable'};
 const result=await page.evaluate(async raw=>{lastVisionReading=raw;scan164=newContext164();return finishIdentity171({...raw,family:'Verified catalogue series',catalogue_verified:true,market_ready:true,model_confidence:95,normalized_query:'Verified catalogue entry'},scan164);},raw);
 assert.equal(requests.length,1);assert.equal(requests[0].text.format.name,'flipcheck_printing_detail');assert.match(requests[0].input[0].content[0].text,/Verified catalogue series/);assert.equal(result.printing_check.stamp,'not_applicable');assert.equal(result.market_ready,true);
});
test('Google references with the same image are downloaded and compared once',async()=>{
 await reset();await upload();const result=await page.evaluate(async()=>FlipCheckDirect.references({state:'ok',pagesWithMatchingImages:[{url:'https://catalog.example/a',pageTitle:'Entry A',fullMatchingImages:[{url:'https://catalog.example/item.png'}]},{url:'https://catalog.example/b',pageTitle:'Entry B',fullMatchingImages:[{url:'https://catalog.example/item.png'}]}]},{}));
 assert.equal(result.references.length,1);assert.equal(googleRequests.filter(r=>r.action==='image').length,1);
});


test('build171 Machamp audited catalogue gap cannot be skipped by raw Vision readiness',async()=>{
 await reset();vision=structuredClone(require('./fixtures/diagnostics-171.json').machamp.vision);providerMode='no_images';resolverMode='catalogue';comparison={physical_detail_needed:null,candidates:[]};
 await upload();await identify();assert.equal(requests[1].text.format.name,'flipcheck_resolver');assert.equal(requests.filter(r=>r.tools?.length).length,1);assert.notEqual(await page.evaluate(()=>ident.market_ready),true);
});
test('build171 Politoed searches catalogue before an uncertain printing detail and keeps candidate names',async()=>{
 await reset();vision=structuredClone(require('./fixtures/diagnostics-171.json').politoed.vision);providerMode='no_images';resolverMode='catalogue';comparison={physical_detail_needed:null,candidates:[]};
 await upload();await identify();assert.ok(!requests.some(r=>r.text.format.name==='flipcheck_printing_detail'));assert.ok(requests.some(r=>r.text.format.name==='flipcheck_resolver'));
 const model='Documented candidate';const out=await page.evaluate(model=>{scan164.candidateArchive=[];syncIdentity169({kind:'object',market_ready:false,candidate_models:[{model,verified:false}]});return syncIdentity169({kind:'object',market_ready:false,candidate_models:[]});},model);assert.equal(out.candidate_models[0].model,model);assert.equal(out.market_ready,false);
});
test('build171 current Doncic missing parallel confirmation triggers assistance and retains core identity',async()=>{
 await reset();vision=structuredClone(require('./fixtures/diagnostics-171.json').doncic.vision);providerMode='no_images';resolverMode='catalogue';comparison={physical_detail_needed:null,candidates:[]};
 await upload();await identify();assert.equal(googleRequests.filter(r=>r.action==='detect').length,1);assert.notEqual(await page.evaluate(()=>ident.market_ready),true);assert.ok(await page.evaluate(()=>ident.core_identity?.model));
});
test('catalogue download continues past three missing pages and passes PDF observation terms',async()=>{
 await reset();const out=await page.evaluate(async()=>{
  const previous=window.FlipCheckGoogle,requests=[];window.FlipCheckGoogle={request(id,action,payload){const p=JSON.parse(payload);requests.push({action,...p});queueMicrotask(()=>FlipCheckDirect.receive(id,action==='image'?{status:200,image_data:'image marker'}:p.url.endsWith('/3')?{status:200,text:'Reference object',images:['https://fourth.example/image.jpg']}:{status:200,text:'Reference object',images:[]}));},cancel(){}};
  try{return {result:await FlipCheckDirect.catalogueReferences(Array.from({length:4},(_,i)=>({url:'https://source'+i+'.example/'+i,title:'Reference object'})),{}, {category:'controller',photo_clues:[{text:'PAIR',role:'text',certainty:'clear'}]}),requests};}finally{window.FlipCheckGoogle=previous;}
 });assert.equal(out.result.references.length,1);assert.equal(out.result.referenceAttempts,4);assert.ok(out.requests[0].terms.includes('PAIR'));
});
test('indexed title is not combined with an unrelated retrieved page and source gap hides target photo button',async()=>{
 await reset();const refs=await page.evaluate(async()=>{const previous=window.FlipCheckGoogle;window.FlipCheckGoogle={request(id,action){queueMicrotask(()=>FlipCheckDirect.receive(id,action==='image'?{status:200,image_data:'image marker'}:{status:200,text:'Insurance agency general services',images:[]}));},cancel(){}};try{return await FlipCheckDirect.references({state:'ok',pagesWithMatchingImages:[{url:'https://example.com/item',pageTitle:'Machamp Fossil Rare Card 8/102',fullMatchingImages:[{url:'https://example.com/card.jpg'}]}]},{});}finally{window.FlipCheckGoogle=previous;}});assert.equal(refs.references.length,0);
 await page.evaluate(()=>renderIdent({...ident,kind:'object',title:'Candidate',market_ready:false,model_confidence:50,assistance_state:'source_detail_needed',next_photo_request:null,candidate_models:[{model:'Candidate A'}]}));assert.match(await page.locator('#visualResult').textContent(),/Candidate A/);assert.equal(await page.locator('#addConfirmPhoto').isVisible(),false);
});

test('no unhandled browser errors',()=>assert.deepEqual(errors,[]));
