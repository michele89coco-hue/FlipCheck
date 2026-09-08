/* Offline contract replay of the production UI, Responses transport and Android bridge.
 * Phase payloads, usage, OCR and reference text below come from real build-188 diagnostics.
 * HTTP envelopes are reconstructed because diagnostics do not retain raw network envelopes.
 * Images are explicitly synthetic carriers at original dimensions; this does NOT measure
 * recognition accuracy on photographs or predict a new model/search response.
 * Unknown outgoing URLs are aborted. No API keys, remote downloads or paid calls exist here.
 */
'use strict';
const {test,before,after,afterEach}=require('node:test');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('playwright');
const recorded=require('./fixtures/diagnostics-188.json');
const assets=path.join(__dirname,'../src/main/assets'),copy=x=>structuredClone(x);
let server,browser,page,origin,scenario,fixture,api=[],native=[],unexpected=[],errors=[],cursor={},photoCursor=0,referenceImages=new Map(),referenceOcr=new Map(),hostEvents=[],hold;
const stages=()=>api.map(r=>r.text?.format?.name);
const report=()=>page.evaluate(()=>diagnostic26());
const narrow=d=>JSON.stringify({identity:d.identification&&Object.fromEntries(['title','model','family','market_ready','catalogue_core_verified','source_confirmed_catalog_number','source_confirmed_year','core_identity','identity_status','missing_information'].map(k=>[k,d.identification[k]])),route:d.visualAssistance?.route,budget:d.visualAssistance?.budget,api:stages()});
function responseEnvelope(phase,kind){
 const sources=kind==='flipcheck_resolver'?fixture.identification.raw_web_results||[]:[];
 const output=[];
 for(let i=0;i<(phase.webCalls||0);i++)output.push({type:'web_search_call',id:'ws_offline_'+i,status:'completed',action:{type:'search',sources},results:sources});
 output.push({type:'message',role:'assistant',status:'completed',content:[{type:'output_text',text:JSON.stringify(phase.result),annotations:[]}]});
 return {id:'resp_offline_'+api.length,object:'response',status:'completed',output,usage:copy(phase.usage)};
}
function phaseFor(kind){
 const stage=kind==='flipcheck_identification'?'vision':kind;
 const candidates=fixture.phases.filter(p=>p.stage===stage),i=cursor[stage]||0;cursor[stage]=i+1;
 if(candidates[i])return copy(candidates[i]);
 if(scenario.overrides?.[kind])return {stage,result:copy(scenario.overrides[kind]),webCalls:0,usage:{input_tokens:300,output_tokens:80,total_tokens:380}};
 // The empty reply is an explicit transport fault fixture, never an invented successful identity.
 return {stage,result:kind==='flipcheck_printing_detail'?{pokemon_printing:fixture.visionResult.pokemon_printing}:kind==='flipcheck_photo_detail'?{details:[]}:{entries:[]},webCalls:0,usage:{input_tokens:300,output_tokens:80,total_tokens:380}};
}
async function openScenario(name,options={}){
 scenario=options;fixture=copy(recorded[name]);options.mutate?.(fixture);
 api=[];native=[];unexpected=[];errors=[];cursor={};photoCursor=0;hostEvents=[];referenceImages=new Map();referenceOcr=new Map();hold=null;
 await page.goto(origin);await page.waitForFunction(()=>typeof newContext164==='function');
 await page.evaluate(()=>{window.FlipCheckTestMode='offline-contract-replay';trial={free:true,attempts:2,credits:0};saveTrial();$('apiKey').value='offline-openai-secret-DO-NOT-EXPORT';$('googleApiKey').value='offline-google-secret-DO-NOT-EXPORT';$('visualEnabled').checked=true;$('scanBudget').value='.03';$('budgetFx').value='1';});
 const specs=Array.from({length:options.photoCount||fixture.uploadedImageCount},(_,i)=>{const m=fixture.visualAssistance.photoOcr?.find(o=>o.image_index===i+1)?.meta;return {width:m?.originalWidth||900,height:m?.originalHeight||1200,label:'SYNTHETIC PHOTO '+(i+1)};});
 const refList=fixture.visualAssistance.retainedReferences||[];
 const imageSpecs=[...specs,...refList.map((r,i)=>({width:r.ocr?.width||350,height:r.ocr?.height||490,label:'SYNTHETIC REFERENCE '+i}))];
 const payloads=await page.evaluate(specs=>specs.map((s,i)=>{const c=document.createElement('canvas');c.width=s.width;c.height=s.height;const g=c.getContext('2d');g.fillStyle=['#ddd','#cdd','#ddc','#dcc','#ccd'][i%5];g.fillRect(0,0,c.width,c.height);g.fillStyle='#111';g.font='24px sans-serif';g.fillText(s.label,30,60);return c.toDataURL('image/png');}),imageSpecs);
 for(let i=0;i<refList.length;i++){referenceImages.set(refList[i].image_url,payloads[specs.length+i]);referenceOcr.set(payloads[specs.length+i],refList[i].ocr||{state:'ok',text:'',lines:[]});}
 await page.locator('#photoBatch').setInputFiles(specs.map((s,i)=>({name:'offline-photo-'+(i+1)+'.png',mimeType:'image/png',buffer:Buffer.from(payloads[i].split(',')[1],'base64')})));
 await page.waitForFunction(()=>!photoBusy);
}
async function clickIdentify(){await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy,{}, {timeout:20000});return report();}
before(async()=>{
 server=http.createServer((req,res)=>{const name=req.url==='/'?'index.html':req.url.slice(1);if(!/^(?:index\.html|[a-z-]+\.js)$/.test(name)||!fs.existsSync(path.join(assets,name))){res.writeHead(404);return res.end();}res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':'text/html');res.end(fs.readFileSync(path.join(assets,name)));});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));origin='http://127.0.0.1:'+server.address().port;
 browser=await chromium.launch({executablePath:process.env.FLIPCHECK_BROWSER_EXECUTABLE||undefined,headless:true,args:['--no-sandbox']});page=await browser.newPage({viewport:{width:412,height:915},serviceWorkers:'block'});
 page.on('pageerror',error=>errors.push(error.message));page.on('dialog',dialog=>dialog.dismiss());
 await page.exposeFunction('offlineHostEvent',event=>hostEvents.push(event));
 await page.addInitScript(()=>{
  const send=(id,action,payload)=>fetch('https://offline-native.invalid/'+action,{method:'POST',body:JSON.stringify(payload)}).then(r=>r.json()).then(result=>window.FlipCheckDirect.receive(id,result));
  window.FlipCheckGoogle={ocrAvailable(){return true;},request(id,action,payload){send(id,action,JSON.parse(payload));},readText(id,image_data){send(id,'ocr',{image_data});},readTextScript(id,image_data,script){send(id,'ocr',{image_data,script});},cancel(id){window.offlineHostEvent({kind:'native_cancel',id});}};
  window.FlipCheckHost={buildInfo(){return JSON.stringify({versionCode:189,versionName:'offline-contract-replay',sourceCommit:'test-only'});},beginScan(id){window.offlineHostEvent({kind:'begin',id});queueMicrotask(()=>window.FlipCheckBackground.started(id,true,''));},endScan(id,snapshot,outcome){window.offlineHostEvent({kind:'end',id,snapshot,outcome});},backgroundInfo(){return JSON.stringify({state:'offline',retainedRuntime:true});},lastScan(){return '{}';},photoPickerInfo(){return '{}';},saveDiagnostic(snapshot){window.offlineHostEvent({kind:'export',snapshot});}};
 });
 await page.route('**/*',async route=>{
  const url=route.request().url();if(url.startsWith(origin+'/'))return route.continue();
  if(url==='https://api.openai.com/v1/responses'){
   const body=JSON.parse(route.request().postData());api.push(body);const kind=body.text?.format?.name;
   if(scenario.holdInitial&&kind==='flipcheck_identification'){await new Promise(resolve=>{hold=resolve;});}
   if(scenario.httpError&&kind==='flipcheck_identification')return route.fulfill({status:scenario.httpError,contentType:'application/json',body:JSON.stringify({error:{message:'Offline provider error'}})}).catch(()=>{});
   const phase=phaseFor(kind);let result=responseEnvelope(phase,kind);
   if(scenario.incompleteInitial&&kind==='flipcheck_identification'&&cursor.vision===1)result={...result,status:'incomplete',incomplete_details:{reason:'max_output_tokens'},output:[{type:'message',content:[{type:'output_text',text:'{"status":'}]}]};
   if(scenario.malformedInitial&&kind==='flipcheck_identification')result={...result,output:[{type:'message',content:[{type:'output_text',text:'not json'}]}]};
   return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(result)}).catch(()=>{});
  }
  if(url.startsWith('https://offline-native.invalid/')){
   const action=url.split('/').pop(),payload=JSON.parse(route.request().postData());native.push({action,...payload});let result;
   if(action==='ocr'){
    const reference=referenceOcr.get(payload.image_data);
    result=scenario.ocrUnavailable?{state:'ocr_unavailable'}:reference?copy(reference):copy(fixture.visualAssistance.photoOcr?.[photoCursor++]||{state:'ok',origin:'on_device_photo_ocr',script:'latin',text:'',lines:[]});
   }else if(action==='page'){
    const refs=fixture.visualAssistance.retainedReferences||[],same=refs.filter(r=>r.url===payload.url);
    const pages=(fixture.visualAssistance.textReferences||[]).filter(r=>r.url===payload.url),source=pages.find(r=>r.text_origin==='retrieved_page');
    // No invented full-page body: recorded indexed documents stay in the Web envelope.
    result=scenario.pages?.[payload.url]|| (scenario.pageUnavailable?{status:503,state:'provider_unavailable'}:source?{status:200,title:source.title,url:source.url,text:source.text,images:same.map(r=>r.image_url),image_details:same.map(r=>({image_url:r.image_url,caption:r.image_caption||''}))}:{status:404,state:'unrecorded_page'});
   }else if(action==='image')result=referenceImages.has(payload.url)?{status:200,image_data:referenceImages.get(payload.url)}:{status:404,state:'unrecorded_image'};
   else if(action==='detect')result={status:200,body:{responses:[{webDetection:{}}]}}; // explicitly empty discovery fixture
   else {unexpected.push('unknown native action '+action);result={status:400};}
   return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(result)}).catch(()=>{});
  }
  unexpected.push(url.split('?')[0]);return route.abort('blockedbyclient');
 });
});
afterEach(()=>{assert.deepEqual(unexpected,[],'No unmocked external request is permitted');assert.deepEqual(errors,[],'Production UI emitted a JavaScript error');});
after(async()=>{hold?.();await browser?.close();if(server)await new Promise(r=>server.close(r));});
for(const name of ['charizard','cloyster'])test('189 recorded '+name+' slab closes on its label without a paid title search',async()=>{
 await openScenario(name);const d=await clickIdentify();assert.equal(d.identification.market_ready,true,narrow(d));assert.equal(d.identification.slab_verification.certificate_verified,false);
 assert.deepEqual(stages(),['flipcheck_identification']);assert.equal(d.usage.web,0);assert.equal(d.usage.input,fixture.phases[0].usage.input_tokens);assert.equal(d.visualAssistance.route,'slab_label');assert.equal(native.some(r=>r.action==='detect'),false);
 assert.equal(hostEvents.filter(e=>e.kind==='begin').length,1);assert.equal(hostEvents.filter(e=>e.kind==='end').length,1);assert.equal(hostEvents.at(-1).outcome,'completed');
});
test('189 recorded Topps retains proven core and refuses single-card reference images',async()=>{
 await openScenario('topps');const d=await clickIdentify();assert.equal(d.identification.core_identity.status,'confirmed',narrow(d));assert.equal(d.identification.catalogue_core_verified,true,narrow(d));assert.equal(d.identification.market_ready,false);
 assert.equal(stages().includes('flipcheck_visual_comparison'),false,'Unrelated cards must not be compared to the photographed box');assert.match(d.visualAssistance.queries.join(' '),/basketball/i);assert.equal(d.usage.web,1);
});
test('189 recorded Doncic retains both photographs and rejects Ayton/Barkley comparisons',async()=>{
 await openScenario('doncic');const d=await clickIdentify();assert.equal(d.uploadedImageCount,2);assert.equal(d.identification.core_identity.status,'confirmed',narrow(d));assert.equal(d.identification.market_ready,false);
 assert.equal(stages().includes('flipcheck_visual_comparison'),false,'The recorded available images are of other subjects');
 const initial=api.find(r=>r.text.format.name==='flipcheck_identification');assert.equal(initial.input.flatMap(m=>m.content).filter(c=>c.type==='input_image').length,2);assert.ok(d.visualAssistance.budget.spentOrReservedUsd<=.03);
});
test('189 recorded Politoed retains proven keys while genuinely absent release year stays pending',async()=>{
 await openScenario('politoed');const d=await clickIdentify();assert.equal(d.identification.core_identity.status,'partial',narrow(d));
 const fields=d.identification.core_identity.fields;assert.ok(fields.some(f=>f.field==='catalog_number'&&f.value==='H23/H32'),narrow(d));assert.ok(fields.some(f=>f.field==='family'&&f.value==='Skyridge'),narrow(d));assert.ok(d.identification.core_identity.pending_fields.includes('year'),narrow(d));
 assert.equal(stages().includes('flipcheck_visual_comparison'),false,'Recorded H1 and 25/144 images cannot prove H23/H32');assert.equal(d.usage.web,1);assert.ok(stages().filter(s=>s==='flipcheck_card_keys').length<=1,'Identical extraction must not be repeated');
});
test('189 incomplete initial response is recovered once and both real usage records count',async()=>{
 await openScenario('cloyster',{incompleteInitial:true});fixture.phases.splice(1,0,copy(fixture.phases[0]));const d=await clickIdentify();assert.equal(d.identification.market_ready,true,narrow(d));
 assert.equal(stages().filter(s=>s==='flipcheck_identification').length,2);assert.equal(d.usage.requests,api.length);assert.ok(d.usage.input>=2*recorded.cloyster.phases[0].usage.input_tokens);assert.equal(d.usage.web,0);
});
test('189 missing optional Vision fields and unavailable local OCR cannot erase a clear slab',async()=>{
 await openScenario('cloyster',{ocrUnavailable:true,mutate:d=>{for(const p of d.phases.filter(p=>p.stage==='vision'))for(const key of ['candidate_models','distinctive_terms','layout_signature','physical_observations','search_terms'])delete p.result[key];}});
 const d=await clickIdentify();assert.equal(d.identification.market_ready,true,narrow(d));assert.equal(d.usage.web,0);assert.equal(stages().includes('flipcheck_printing_detail'),false);
});
test('189 provider 401 releases UI and native foreground lifecycle without silently retrying',async()=>{
 await openScenario('cloyster',{httpError:401});const d=await clickIdentify();assert.equal(api.length,1);assert.notEqual(d.identification?.market_ready,true);assert.equal(await page.locator('#identifyBtn').isEnabled(),true);assert.equal(hostEvents.filter(e=>e.kind==='end').length,1);assert.equal(hostEvents.find(e=>e.kind==='end').outcome,'failed');assert.equal(native.some(r=>r.action==='detect'),false);
});
test('189 cancellation during an in-flight response ignores its late result and starts no web search',async()=>{
 await openScenario('cloyster',{holdInitial:true});await page.locator('#identifyBtn').click();await page.waitForFunction(()=>apiBusy&&$('cancelScan')&&!$('cancelScan').disabled);
 while(!hold)await new Promise(r=>setTimeout(r,10));await page.locator('#cancelScan').click();hold();hold=null;await page.waitForFunction(()=>!apiBusy);
 const d=await report();assert.equal(api.length,1);assert.notEqual(d.identification?.market_ready,true);assert.equal(hostEvents.find(e=>e.kind==='end')?.outcome,'cancelled');assert.equal(await page.locator('#identifyBtn').isEnabled(),true);
});
test('189 multi-photo removal sends only retained files and snapshots exclude keys and image payloads',async()=>{
 await openScenario('cloyster',{photoCount:3});await page.locator('#s1 .remove-photo').click();await page.waitForFunction(()=>!photoBusy);const d=await clickIdentify();assert.equal(d.uploadedImageCount,2);
 const initial=api.find(r=>r.text.format.name==='flipcheck_identification');assert.equal(initial.input.flatMap(m=>m.content).filter(c=>c.type==='input_image').length,2);
 const saved=hostEvents.find(e=>e.kind==='end').snapshot,diagnostic=JSON.stringify(d),storage=await page.evaluate(()=>JSON.stringify(localStorage));
 for(const value of [saved,diagnostic,storage])assert.doesNotMatch(value,/offline-(?:openai|google)-secret|data:image\//);
 for(const request of native.filter(r=>['page','image','ocr'].includes(r.action)))assert.equal('apiKey' in request,false);
});
test('189 untrusted source HTML and executable link schemes remain inert in rendered evidence',async()=>{
 await openScenario('cloyster');await clickIdentify();
 const out=await page.evaluate(()=>{const bad='javascript:window.__sourceExecuted=true';const value={...ident,identification_sources:[{url:bad,title:'<img src=x onerror="window.__sourceExecuted=true">'},{url:'data:text/html,unsafe',title:'Data URL'},{url:'https://catalog.example/safe',title:'Safe catalogue'}]};renderIdent(value);renderResult({market_status:'insufficient',exact_completed_sales_count:0,currency:'EUR',market_notes:'Offline'},value.identification_sources,null,'Offline card');return [...document.querySelectorAll('#identNote a,#resultPanel a')].map(a=>a.getAttribute('href'));});
 assert.ok(out.includes('https://catalog.example/safe'));assert.equal(out.some(u=>/^(?:javascript|data|file):/i.test(u)),false,'Untrusted source URLs must be filtered before rendering');assert.equal(await page.evaluate(()=>window.__sourceExecuted===true),false);
});

test('189 slab certificate and label routes stay active without an optional Google API key',async()=>{
 await openScenario('cloyster');await page.evaluate(()=>{$('googleApiKey').value='';});const d=await clickIdentify();
 assert.equal(d.identification.market_ready,true,narrow(d));assert.equal(d.visualAssistance.route,'slab_label');assert.deepEqual(stages(),['flipcheck_identification']);assert.equal(native.some(r=>r.action==='detect'),false);
});

test('189 synthetic release-page availability closes Politoed without another paid search',async()=>{
 const url='https://wiki.pokemoncentral.it/Skyridge_%28GCC%29',r=recorded.politoed.visualAssistance.textReferences.find(r=>r.url===url);
 // The following release metadata is intentionally synthetic test input, absent in recorded188.
 await openScenario('politoed',{pages:{[url]:{status:200,url,title:'Skyridge (GCC)',text:'Skyridge\nData di uscita italiana: 15 agosto 2003\n'+r.text,images:[]}}});
 const d=await clickIdentify();assert.equal(d.identification.core_identity.status,'confirmed',narrow(d));assert.equal(d.identification.source_confirmed_year,'2003',narrow(d));assert.equal(d.usage.web,1);assert.equal(stages().includes('flipcheck_visual_comparison'),false);
});

test('189 malformed completed JSON never creates an identity and releases photo controls',async()=>{
 await openScenario('cloyster',{malformedInitial:true});const d=await clickIdentify();
 assert.notEqual(d.identification?.market_ready,true);assert.ok(api.length<=2);assert.equal(stages().includes('flipcheck_resolver'),false);assert.equal(await page.locator('#addPhotos').isEnabled(),true);assert.equal(hostEvents.find(e=>e.kind==='end').outcome,'failed');
});
test('189 double identify taps while a response is pending create one scan and one initial request',async()=>{
 await openScenario('cloyster',{holdInitial:true});await page.locator('#identifyBtn').click();while(!hold)await new Promise(r=>setTimeout(r,10));
 await page.evaluate(()=>{$('identifyBtn').click();$('identifyBtn').click();});assert.equal(api.length,1);hold();hold=null;await page.waitForFunction(()=>!apiBusy);
 assert.equal(hostEvents.filter(e=>e.kind==='begin').length,1);assert.equal(stages().filter(s=>s==='flipcheck_identification').length,1);assert.equal((await report()).identification.market_ready,true);
});

for(const enabled of [true,false])test('191 official slab certificate closes with visual assistance '+enabled+' and no paid follow-up',async()=>{
 const url='https://www.psacard.com/cert/64613920/psa';
 const fields={'Cert Number':'64613920','Subject':'Charizard','Year':'1999','Brand/Title':'CD Promo','Card Number':'6','Item Grade':'MINT 9'};
 await openScenario('charizard',{pages:{[url]:{status:200,url,structured_fields:Object.entries(fields).map(([label,value])=>({label,value})),text:''}}});
 await page.evaluate(enabled=>{$('visualEnabled').checked=enabled;},enabled);const d=await clickIdentify();
 assert.equal(d.identification.market_ready,true,narrow(d));assert.equal(d.identification.grading.certificate_verified,true,narrow(d));
 assert.deepEqual(stages(),['flipcheck_identification']);assert.equal(d.usage.web,0);assert.deepEqual(native.map(r=>r.action),['page']);
 assert.match(await page.locator('#identTitle').textContent(),/1999.*CD Promo.*#6.*Charizard.*PSA MINT 9/);
 assert.match(await page.locator('#gradingIdentity191').textContent(),/PSA.*MINT 9.*Certificato verificato/);
 assert.doesNotMatch(await page.locator('#identPanel').textContent(),/Modello da confermare|MODELLO NON CONFERMATO/);
});
test('191 unavailable certificate with incomplete label asks for its label and never calls another model',async()=>{
 await openScenario('charizard',{mutate:d=>{d.phases[0].result.slab_reading.label_text='MINT 9';d.phases[0].result.slab_reading.certainty='uncertain';}});
 const d=await clickIdentify();assert.equal(d.identification.market_ready,false);assert.match(d.identification.next_photo_request,/etichetta/);assert.deepEqual(stages(),['flipcheck_identification']);assert.equal(d.usage.web,0);
});
// User diagnostics are supplied locally; never commit their full exports or photographs.
if(process.env.FLIPCHECK_DIAGNOSTICS_DIR){
 const names=['boniface','topps','cloyster','machamp','politoed'];
 for(const [i,name] of names.entries()){
  recorded['current_'+name]=JSON.parse(fs.readFileSync(path.join(process.env.FLIPCHECK_DIAGNOSTICS_DIR,'FlipCheck-26Fix-diagnostica'+(i?' ('+i+')':'')+'.json'),'utf8'));
  test('191 replay current build190 '+name+' diagnostic through production UI',async()=>{
   await openScenario('current_'+name);const d=await clickIdentify();
   fs.writeFileSync('/tmp/flipcheck191-replay-'+name+'.json',JSON.stringify(d,null,2));
   assert.equal(d.identification.core_identity.status,'confirmed',narrow(d));
   assert.equal(d.identification.market_ready,name!=='topps',narrow(d));
   assert.ok(stages().length<=fixture.phases.length,'No extra paid phases beyond the recorded run');
   if(name==='cloyster'){assert.deepEqual(stages(),['flipcheck_identification']);assert.equal(d.identification.grading.company,'BGS');assert.match(d.identification.title,/2002.*#8.*Cloyster.*BGS 9/);}
   if(name==='topps')assert.match(d.identification.next_photo_request,/pacch|pack|scatol|confezion/i);
   if(name==='machamp'){assert.match(d.identification.model,/1999.*8\/102.*Machamp/);assert.doesNotMatch(d.identification.variant,/unclear|unconfirmed/);}
   if(name==='politoed'){assert.match(d.identification.model,/2003.*Skyridge.*H23\/H32.*Politoed/);assert.doesNotMatch(d.identification.variant,/unconfirmed/);}
   if(name==='boniface'){assert.equal(d.identification.serial_number,'2/5');assert.equal(d.identification.print_run,5);assert.equal(d.identification.variant,'Green');}
  });
 }
}

test('191 replacing a slab result clears its grading panel from the next identity',async()=>{
 await openScenario('charizard');await clickIdentify();assert.equal(await page.locator('#gradingIdentity191').count(),1);
 await page.evaluate(()=>renderIdent({kind:'card',brand:'Example',model:'Other card',variant:'',core_identity:{status:'confirmed'},exact_identity_status:'confirmed',market_ready:true,normalized_query:'Other card',model_confidence:98}));
 assert.equal(await page.locator('#gradingIdentity191').count(),0);assert.doesNotMatch(await page.locator('#identPanel').textContent(),/PSA|MINT 9/);
});
