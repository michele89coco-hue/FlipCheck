/* Browser production wiring. All API payloads are fabricated; no live recognition. */
const {test,before,after}=require('node:test'),assert=require('node:assert/strict'),http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require('playwright');
let server,browser,page,origin,photos,requests=[],googleRequests=[],vision,comparison,providerMode='ok',resolverMode='',waitApi=null,errors=[];
const root=path.join(__dirname,'../src/main/assets');
const unknown={status:'uncertain',kind:'object',title:'Oggetto',category:'object',brand:'',family:'',model:'',variant:'',condition:'raw',category_confidence:70,brand_confidence:0,family_confidence:0,model_confidence:20,model_verified:false,market_ready:false,candidate_models:[],visual_fingerprint:'geometrical outline',distinctive_terms:[],search_terms:[],identifier_hints:[],layout_signature:[],evidence:[],missing_information:['catalogue identity'],next_photo_request:null,user_text_consistent:true,normalized_query:'',verification_summary:'Mock only',pokemon_printing:null,photo_clues:[],object_unit:'object',object_region:{image_index:1,x:.1,y:.1,width:.8,height:.8,certain:true}};
const known={...unknown,status:'identified',brand:'Example',family:'Series',model:'Known model',title:'Known model',model_confidence:94,market_ready:true,normalized_query:'Example Known model'};
function candidate(){return {category:'object',brand:'Example',family:'Series',model:'Documented model',year:'',issue_number:'',catalog_number:'',unit:'object',variant:'',decision:'match',same_unit:true,physical_ambiguity:false,conflicts:[],matches:[{reference_id:'ref1',feature:'layout',photo_detail:'two round controls',reference_detail:'two round controls',agrees:true},{reference_id:'ref1',feature:'shape',photo_detail:'rectangular body',reference_detail:'rectangular body',agrees:true}],fields:[{field:'model',value:'Documented model',reference_id:'ref1',quote:'Catalogue entry: Documented model.'}]};}
async function reset(enabled=true){requests=[];googleRequests=[];vision=structuredClone(unknown);comparison={physical_detail_needed:null,candidates:[candidate()]};providerMode='ok';resolverMode='';waitApi=null;await page.goto(origin);await page.waitForFunction(()=>typeof newContext164==='function');await page.evaluate(enabled=>{window.FlipCheckTestMode='mock';trial={free:true,attempts:2,credits:0};saveTrial();$('apiKey').value='fake-openai';$('visualEnabled').checked=enabled;$('googleApiKey').value='fake-google-key-1234567890';$('scanBudget').value='.025';$('budgetFx').value='1';},enabled);}
async function upload(){await page.locator('#photoBatch').setInputFiles(photos);await page.waitForFunction(()=>!photoBusy);}
async function identify(){await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy,{},{timeout:12000});}
before(async()=>{
 server=http.createServer((req,res)=>{const name=req.url==='/'?'index.html':req.url.slice(1);if(!['index.html','editions.js','targeted-fixes.js','visual-policy.js','visual-runtime.js','google-direct.js'].includes(name)){res.writeHead(404);return res.end();}res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':'text/html');res.end(fs.readFileSync(path.join(root,name)));});await new Promise(r=>server.listen(0,'127.0.0.1',r));origin='http://127.0.0.1:'+server.address().port;
 browser=await chromium.launch({headless:true,args:['--no-sandbox']});page=await browser.newPage({viewport:{width:412,height:915}});page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.FlipCheckGoogle={request(id,action,payload){fetch('https://native.example/'+action,{method:'POST',body:payload}).then(r=>r.json()).then(result=>window.FlipCheckDirect.receive(id,result));},cancel(){}};});
 await page.route('**/*',async route=>{
  const u=route.request().url();if(u.startsWith(origin))return route.continue();
  if(u==='https://api.openai.com/v1/responses'){
   const body=JSON.parse(route.request().postData());requests.push(body);let payload=body.text.format.name==='flipcheck_visual_comparison'?comparison:body.text.format.name==='flipcheck_market'?{market_status:'insufficient',exact_completed_sales_count:0,active_listings_count:0,market_low:null,market_high:null,quick_sale_price:null,historical_new_price:null,currency:'EUR',market_notes:'No verified comparable sales',source_summary:''}:vision;
   if(body.text.format.name==='flipcheck_resolver'&&resolverMode){
    if(resolverMode==='unauthorized')return route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({error:{message:'API key rejected'}})});
    const source={url:'https://acme.example/manual/zx-430',title:'Acme ZX-430 water pump product manual',snippet:'Acme ZX-430 water pump. Two round controls, rectangular body. Model ZX-430.'};
    const withSource=resolverMode==='source'||resolverMode==='complete_source';
    const catalogue=resolverMode.startsWith('catalogue');
    const sources=catalogue?Array.from({length:resolverMode==='catalogue_cost'?3:1},(_,i)=>({url:'https://catalog.example/entry'+i,title:'Catalogue entry: Documented model.',snippet:'Two round controls. Rectangular body. Catalogue entry: Documented model.'})):withSource?[source]:[];
    const output=[{type:'web_search_call',status:'completed',action:{type:'search',sources},results:sources},{type:'message',content:[{type:'output_text',text:resolverMode==='malformed'?'{':JSON.stringify({candidate_checks:[],verification_summary:'Mock response',missing_information:[]})}]}];
    const incomplete=!['malformed','complete_source'].includes(resolverMode)&&!catalogue;
    return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({status:incomplete?'incomplete':'completed',...(incomplete?{incomplete_details:{reason:'max_output_tokens'}}:{}),output,usage:resolverMode==='expensive'?{input_tokens:60000,output_tokens:1500}:resolverMode==='catalogue_cost'?{input_tokens:24000,output_tokens:400}:{input_tokens:100,output_tokens:100}})});
   }
   if(waitApi){const pending=waitApi;await pending;}
   return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({status:'completed',output:[{type:'message',content:[{type:'output_text',text:JSON.stringify(payload)}]}],usage:{input_tokens:100,output_tokens:100}})});
  }
  if(u.startsWith('https://native.example/')){
   const action=u.split('/').pop(),body=JSON.parse(route.request().postData());googleRequests.push({action,...body});
   let reply;
   if(action==='detect'){
    if(providerMode==='timeout')reply={status:0,state:'timeout',attempted:true};
    else if(providerMode==='invalid')reply={status:400,body:{error:{details:[{reason:'API_KEY_INVALID'}]}}};
    else if(providerMode==='disabled')reply={status:403,body:{error:{details:[{reason:'SERVICE_DISABLED'}]}}};
    else if(providerMode==='quota')reply={status:429,body:{error:{status:'RESOURCE_EXHAUSTED'}}};
    else {const linked={url:'https://catalog.example/item',pageTitle:'Catalogue entry: Documented model.',fullMatchingImages:[{url:'https://catalog.example/item.png'}]};const empty=Array.from({length:7},(_,i)=>({url:'https://catalog.example/generic'+i,pageTitle:'Generic page'}));reply={status:200,body:{responses:[{webDetection:{webEntities:[{description:'Documented model',score:.99}],pagesWithMatchingImages:providerMode==='late_images'?[...empty,linked]:providerMode==='no_images'?empty:[linked]}}]}};}
   }else if(action==='page')reply={status:200,text:'Catalogue entry: Documented model.',images:resolverMode.startsWith('catalogue')?['https://catalog.example/item.png']:[]};
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
  const resolver=requests[1];assert.equal(resolver.max_output_tokens,1800);assert.deepEqual(Object.keys(resolver.text.format.schema.properties).sort(),['candidate_checks','missing_information','verification_summary']);
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
 await reset();textObject();resolverMode='catalogue_cost';vision.photo_clues.push({text:'Rectangular body',role:'text',certainty:'clear',image_index:1,region:null});
 await upload();await identify();const d=await page.evaluate(()=>diagnostic26());assert.equal(d.visualAssistance.comparison.availableReferences,3);assert.ok(d.visualAssistance.comparison.referenceIds.length<3);assert.ok(d.visualAssistance.comparison.referenceIds.length>=1);assert.ok(d.visualAssistance.budget.spentOrReservedUsd<=.025);assert.equal(await page.evaluate(()=>ident.market_ready),true);assert.equal(googleRequests.filter(r=>r.action==='detect').length,0);
});
test('no unhandled browser errors',()=>assert.deepEqual(errors,[]));
