// Browser integration with fabricated API responses; all external requests are intercepted.
const {test,before,after} = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require('playwright');
const regressions=require('./fixtures/build161-regressions.json');
let server,browser,page,origin,photos,requests=[],response,errors=[],apiHandler=null;
const root=path.join(__dirname,'../src/main/assets');
const kobe={status:'identified',kind:'card',title:'SkyBox 1997-98 Metal Universe Kobe Bryant #81 Base',category:'sports card',brand:'SkyBox',family:'Metal Universe',model:'1997-98 Metal Universe Kobe Bryant #81',variant:'Base',condition:'raw',category_confidence:99,brand_confidence:98,family_confidence:98,model_confidence:94,model_verified:false,market_ready:true,candidate_models:[],visual_fingerprint:'Kobe Bryant with metal background',distinctive_terms:['Kobe Bryant','Metal Universe','81'],search_terms:['Metal Universe'],identifier_hints:['81'],layout_signature:[],evidence:['Kobe Bryant nameplate','81 on back'],missing_information:[],next_photo_request:null,user_text_consistent:true,normalized_query:'1997-98 SkyBox Metal Universe Kobe Bryant #81 Base',verification_summary:'Synthetic browser behavior fixture',pokemon_printing:null,identifier_observations:[{text:'81',role:'collector_number',legibility:'clear',image_index:1}]};
const printing={is_pokemon:true,language:'English',set_name:'Base Set',card_type:'pokemon',first_edition_stamp:'present',stamp_image:1,stamp_location:'left below artwork',stamp_text:'1 EDITION',artwork_shadow:'absent',shadow_image:1,shadow_location:'right frame',copyright_text:'©1995,96,98 Nintendo ©1999 Wizards',copyright_image:1,slab_text:'',slab_image:0};
function envelope(parsed,web=false,sources=[]) {
 return {status:'completed',output:[...(web?[{type:'web_search_call',action:{sources}}]:[]),{type:'message',content:[{type:'output_text',text:JSON.stringify(parsed)}]}],usage:{input_tokens:100,output_tokens:100,input_tokens_details:{cached_tokens:0}}};
}
function defaultEnvelope(request) {
 if(!request.tools)return envelope({...response,detail_regions:response.detail_regions || [],identifier_observations:response.identifier_observations || []});
 const quote=['Trading card',response.brand,response.family,response.model,...response.distinctive_terms].join(' ');
 const url='https://catalog.example/cards/test';
 return envelope({summary:'Verified fixture',missing_information:[],next_photo_request:null,candidates:[{brand:response.brand,family:response.family,model:response.model,variant:response.variant,collector_number:'81',product_code:'',relation:'exact_product',conflicts:[],sources:[{url,quote}]}]},true,[{url,title:'Test catalogue',snippet:quote}]);
}
async function reset() {
  requests=[];response=structuredClone(kobe);apiHandler=null;
  await page.goto(origin);await page.waitForFunction(()=>typeof diagnostic26==='function');
  await page.evaluate(()=>{trial={attempts:2,free:true,credits:0};saveTrial();$('apiKey').value='test-key-never-transmitted';});
}
async function upload(items) { await page.locator('#photoBatch').setInputFiles(items);await page.waitForFunction(()=>!photoBusy); }
before(async()=>{
  server=http.createServer((req,res)=>{
    const name=req.url==='/'?'index.html':req.url.slice(1);
    if(!['index.html','editions.js','targeted-fixes.js','photo-detail.js','identity-web.js','tcg-reference.js','identity-policy.js','detail-runtime.js'].includes(name)){res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':'text/html');res.end(fs.readFileSync(path.join(root,name)));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));origin='http://127.0.0.1:'+server.address().port;
  browser=await chromium.launch({headless:true,args:['--no-sandbox']});page=await browser.newPage({viewport:{width:412,height:915}});
  page.on('pageerror',error=>errors.push(error.message));
  await page.route('**/*',async route=>{
    if(route.request().url().startsWith(origin))return route.continue();
    if(route.request().url()==='https://api.openai.com/v1/responses'){
      const request=JSON.parse(route.request().postData());requests.push(request);
      const envelope=apiHandler?apiHandler(request,requests.length):defaultEnvelope(request);
      return route.fulfill({status:envelope.httpStatus || 200,contentType:'application/json',body:JSON.stringify(envelope)});
    }
    throw new Error('Unexpected external request: '+route.request().url());
  });
  await reset();
  photos=await page.evaluate(()=>['red','blue','green','yellow'].map(color=>{
    const c=document.createElement('canvas');c.width=120;c.height=180;c.getContext('2d').fillStyle=color;c.getContext('2d').fillRect(0,0,120,180);
    return{name:color+'.png',base64:c.toDataURL('image/png').split(',')[1]};
  }));
  photos=photos.map(p=>({name:p.name,mimeType:'image/png',buffer:Buffer.from(p.base64,'base64')}));
});
after(async()=>{if(browser)await browser.close();if(server)await new Promise(resolve=>server.close(resolve));});
test('empty plus and add button open multi-select; filled slot opens replacement',async()=>{
  await reset();
  assert.equal(await page.locator('.beta').textContent(),'v0.26.5 · FIX');
  assert.match(await page.locator('#scanPage').textContent(),/2560 px/);
  let chooserPromise=page.waitForEvent('filechooser');await page.locator('#s0').click();let chooser=await chooserPromise;
  assert.equal(chooser.isMultiple(),true);await chooser.setFiles(photos.slice(0,2));await page.waitForFunction(()=>!photoBusy);
  chooserPromise=page.waitForEvent('filechooser');await page.locator('#s0').click();chooser=await chooserPromise;
  assert.equal(chooser.isMultiple(),false);await chooser.setFiles([]);
  chooserPromise=page.waitForEvent('filechooser');await page.locator('#addPhotos').click();chooser=await chooserPromise;
  assert.equal(chooser.isMultiple(),true);await chooser.setFiles([photos[2]]);await page.waitForFunction(()=>!photoBusy);
  assert.equal(await page.evaluate(()=>validImageCount()),3);
});
test('multi-select accepts three photos in selection order and reports overflow',async()=>{
  await reset();await upload(photos);
  assert.deepEqual(await page.evaluate(()=>files.map(f=>f&&f.name)),['red.png','blue.png','green.png']);
  assert.match(await page.locator('#photoStatus').textContent(),/1 non aggiunte/);
  assert.equal(await page.locator('.slot img').count(),3);assert.equal(requests.length,0);
});
test('cancellation, corrupt replacement and deletion preserve unrelated photos',async()=>{
  await reset();await upload(photos.slice(0,2));
  await page.evaluate(()=>loadSelectedPhotos([]));
  assert.equal(await page.evaluate(()=>validImageCount()),2);
  await page.locator('#photo0').setInputFiles({name:'broken.png',mimeType:'image/png',buffer:Buffer.from('bad image')});
  await page.waitForFunction(()=>!photoBusy);
  assert.deepEqual(await page.evaluate(()=>files.map(f=>f&&f.name)),['red.png','blue.png',null]);
  await page.locator('#s0 .remove-photo').click();await upload([photos[2]]);
  assert.deepEqual(await page.evaluate(()=>files.map(f=>f&&f.name)),['green.png','blue.png',null]);
});
test('duplicate photos are rejected without replacing an existing image',async()=>{
  await reset();await upload([photos[0]]);await upload([photos[0],photos[1]]);
  assert.equal(await page.evaluate(()=>validImageCount()),2);
  assert.match(await page.locator('#photoStatus').textContent(),/duplicate/);
});
test('decoding order cannot reorder photos and analysis is locked until ready',async()=>{
  await reset();
  await page.evaluate(()=>{window.savedResize26=resize;window.releasePhoto26=[];resize=file=>new Promise(resolve=>releasePhoto26.push(async()=>resolve(await savedResize26(file))));});
  await page.locator('#photoBatch').setInputFiles(photos.slice(0,2));
  assert.equal(await page.locator('#identifyBtn').isDisabled(),true);assert.equal(await page.locator('#addPhotos').isDisabled(),true);
  await page.evaluate(async()=>{await releasePhoto26[1]();await releasePhoto26[0]();});await page.waitForFunction(()=>!photoBusy);
  assert.deepEqual(await page.evaluate(()=>files.map(f=>f&&f.name)),['red.png','blue.png',null]);
  assert.equal(await page.locator('#identifyBtn').isDisabled(),false);
  await page.evaluate(()=>{resize=savedResize26;});
});
test('complete sports result uses both originals and closes without web',async()=>{
  await reset();await upload(photos.slice(0,2));await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  assert.equal(requests.length,1);assert.equal(requests[0].model,'gpt-5.6-luna');assert.equal(requests[0].tools,undefined);
  assert.equal(requests[0].input[0].content.filter(x=>x.type==='input_image').length,2);
  assert.match(await page.locator('#identTitle').textContent(),/Metal Universe Kobe Bryant #81/);
  assert.equal(await page.evaluate(()=>ident.model_confidence),94);assert.equal(await page.locator('#marketBtn').isDisabled(),false);
  assert.equal(await page.locator('#printingPanel').count(),0);
});
test('same Vision response displays stamp and Shadowless with matching market query',async()=>{
  await reset();await upload([photos[0]]);
  response={...kobe,brand:'Pokemon',family:'Base Set',model:'Alakazam #1/102',title:'Alakazam',variant:'Holo',normalized_query:'Pokemon Base Set Alakazam #1/102 Holo',pokemon_printing:printing,distinctive_terms:['Alakazam','80 HP','1/102'],identifier_observations:[{text:'1/102',role:'collector_number',legibility:'clear',image_index:1}]};
  await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  assert.equal(requests.length,1);assert.ok(requests[0].text.format.schema.required.includes('pokemon_printing'));
  assert.match(await page.locator('#printingPanel').textContent(),/1st Edition visibile/);
  assert.match(await page.locator('#confirmed').inputValue(),/1st Edition Shadowless/);
  assert.equal(await page.locator('#marketBtn').isDisabled(),false);
  await page.screenshot({path:process.env.FC26_SCREENSHOT||'/tmp/flipcheck26-printing.png',fullPage:true});
});
test('uncertain edition keeps identity visible, disables automatic comps and exports without key/images',async()=>{
  await reset();await upload([photos[0]]);
  response={...kobe,brand:'Pokemon',family:'Base Set',model:'Alakazam #1/102',variant:'Holo',pokemon_printing:{...printing,first_edition_stamp:'unclear',stamp_text:''},distinctive_terms:['Alakazam','80 HP','1/102'],identifier_observations:[{text:'1/102',role:'collector_number',legibility:'clear',image_index:1}]};
  await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  assert.equal(requests.length,1);assert.match(await page.locator('#identTitle').textContent(),/Alakazam #1\/102/);
  assert.match(await page.locator('#tags').textContent(),/EDIZIONE DA VERIFICARE/);assert.equal(await page.locator('#marketBtn').isDisabled(),true);
  const diagnostic=await page.evaluate(()=>JSON.stringify(diagnostic26()));
  assert.doesNotMatch(diagnostic,/test-key-never-transmitted|data:image|Authorization|Bearer/);
  assert.equal(JSON.parse(diagnostic).uploadedImageCount,1);assert.equal(JSON.parse(diagnostic).phases.length,1);
  assert.deepEqual(errors,[]);
});
test('2560px originals produce precise native-pixel text crops before web; requests and costs counted once',async()=>{
 await reset();
 const large=await page.evaluate(()=>{
  const c=document.createElement('canvas');c.width=4096;c.height=3072;const g=c.getContext('2d');
  g.fillStyle='white';g.fillRect(0,0,c.width,c.height);g.fillStyle='blue';g.fillRect(2400,2000,1600,1000);
  g.fillStyle='white';g.font='32px sans-serif';g.fillText('MODEL YKF423-001',2800,2400);
  return c.toDataURL('image/png').split(',')[1];
 });
 await upload([{name:'full-detail.png',mimeType:'image/png',buffer:Buffer.from(large,'base64')}]);
 response={...kobe,market_ready:false,missing_information:['Etichetta parzialmente leggibile'],detail_regions:[{image_index:1,purpose:'product_label',x:.65,y:.72,width:.30,height:.20}]};
 apiHandler=(request,n)=>{
  if(!request.tools)return n===1?envelope(response):envelope({identifier_observations:[],text_corrections:[],pokemon_printing:null,detail_note:'No correction'});
  return defaultEnvelope(request);
 };
 await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
 assert.equal(requests.length,3);assert.equal(requests.filter(r=>r.tools).length,1);
 const diag=await page.evaluate(()=>diagnostic26());
 assert.equal(diag.imagePreparation.photos[0].originalWidth,4096);assert.equal(diag.imagePreparation.photos[0].sentWidth,2560);
 const crop=diag.imagePreparation.crops[0];assert.equal(crop.mime,'image/png');
 assert.ok(crop.originalRect.width>1300);assert.equal(crop.sentWidth,crop.originalRect.width);
 assert.equal(requests[1].input[0].content.filter(x=>x.type==='input_image').length,2);
 assert.equal(diag.usage.requests,3);assert.equal(diag.usage.vision,2);assert.equal(diag.usage.web,1);
 assert.equal(diag.phases.length,3);assert.doesNotMatch(JSON.stringify(diag),/data:image|test-key-never-transmitted/);
});
test('second web verifies a concrete compatible candidate and stops when resolved',async()=>{
 await reset();await upload([photos[0]]);response.market_ready=false;let webs=0;
 apiHandler=request=>{
  if(!request.tools)return defaultEnvelope(request);
  if(++webs===1){const out=defaultEnvelope(request),parsed=JSON.parse(out.output[1].content[0].text);parsed.candidates[0].relation='compatible';out.output[1].content[0].text=JSON.stringify(parsed);return out;}
  return defaultEnvelope(request);
 };
 await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
 assert.equal(webs,2);assert.equal(requests.length,3);
 const d=await page.evaluate(()=>diagnostic26());assert.equal(d.identification.market_ready,true);
 assert.notEqual(d.webAttempts[0].query,d.webAttempts[1].query);
 for(const request of requests.filter(x=>x.tools)){assert.equal(request.max_tool_calls,1);assert.equal(request.tool_choice,'required');}
});
test('empty web lookup stops after one search and a new failed scan clears previous identity',async()=>{
 await reset();await upload([photos[0]]);response.market_ready=false;apiHandler=request=>!request.tools?defaultEnvelope(request):envelope({summary:'Not found',candidates:[],missing_information:['Code'],next_photo_request:'Read label'},true,[]);
 await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
 assert.equal(requests.length,2);assert.equal(await page.evaluate(()=>ident.market_ready),false);
 assert.equal(await page.locator('#marketBtn').isDisabled(),true);
 apiHandler=()=>({httpStatus:401,error:{message:'Invalid API key'}});
 await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
 const diag=await page.evaluate(()=>diagnostic26());assert.equal(diag.identification,null);assert.equal(diag.visionResult,null);assert.equal(diag.firstVision,null);
 assert.equal(diag.phases.length,1);assert.equal(diag.phases[0].httpStatus,401);
});
test('invalid or oversized regions are skipped and small images are never enlarged',async()=>{
 await reset();await upload([photos[0]]);
 response={...kobe,market_ready:false,missing_information:['Etichetta slab parzialmente leggibile'],detail_regions:[{image_index:1,purpose:'printed_text',x:0,y:0,width:1,height:1},{image_index:3,purpose:'slab_label',x:0,y:0,width:.5,height:.2}]};
 await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
 const d=await page.evaluate(()=>diagnostic26());assert.equal(d.imagePreparation.crops.length,0);assert.equal(d.imagePreparation.skipped.length,2);
 assert.equal(d.imagePreparation.photos[0].sentHeight,180);assert.equal(requests.length,2);
});
test('actual Vileplume and Boniface first readings close after one Vision with no web or crops',async()=>{
 for(const name of ['wilo','boniface']){
  await reset();const f=regressions[name];await upload(photos.slice(0,f.uploadedImageCount));response=structuredClone(f.phases[0].result);
  await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  const d=await page.evaluate(()=>diagnostic26());
  assert.equal(requests.length,1,name);assert.equal(d.identification.market_ready,true,name);
  assert.equal(d.identification.core_identity_confirmed,true,name);assert.equal(d.imagePreparation.crops.length,0,name);
  assert.equal(d.webAttempts.length,0,name);assert.equal(d.usage.requests,1,name);
  assert.equal(requests[0].reasoning.effort,'low');
  assert.equal(requests[0].text.format.schema.properties.identifier_observations.items.properties.image_index.maximum,f.uploadedImageCount);
  assert.match(await page.locator('#photoIdentity265').textContent(),/Identità riconosciuta dalla foto/);
  if(name==='boniface'){
   assert.equal(d.firstVision.invalid_photo_references.length,2);
   assert.match(await page.locator('#photoIdentity265').textContent(),/variante da verificare/);
   assert.doesNotMatch(await page.locator('#confirmed').inputValue(),/Green/);
  }
 }
});
test('actual Politoed closes with first web response, without a redundant detail Vision',async()=>{
 await reset();await upload([photos[0]]);const f=regressions.poli;response=structuredClone(f.firstVision);
 apiHandler=request=>request.tools?envelope(f.phases.find(p=>p.stage.startsWith('Web')).result,true,f.sources):defaultEnvelope(request);
 await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
 const d=await page.evaluate(()=>diagnostic26());assert.equal(requests.length,2);assert.equal(d.usage.vision,1);assert.equal(d.usage.web,1);
 assert.equal(d.identification.family,'Skyridge');assert.equal(d.identification.market_ready,true);
 assert.equal(d.imagePreparation.crops.length,0);assert.match(await page.locator('#confirmed').inputValue(),/Politoed H23\/H32/);
 assert.doesNotMatch(await page.locator('#confirmed').inputValue(),/Aquapolis/);
});
test('network failure stops after one web attempt and records waiting time without invented usage',async()=>{
 await reset();await upload([photos[0]]);response.market_ready=false;
 apiHandler=request=>request.tools?{httpStatus:503,error:{message:'Temporary failure'}}:defaultEnvelope(request);
 await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
 const d=await page.evaluate(()=>diagnostic26());assert.equal(requests.length,2);assert.equal(d.webAttempts.length,1);
 assert.equal(d.webAttempts[0].stopReason,'request_failed_no_automatic_retry');assert.equal(d.usage.requests,1);assert.equal(d.usage.web,0);
 assert.equal(d.identificationTiming.failedRequests,1);assert.equal(d.phases[1].usageKnown,false);
 assert.ok(d.identificationTiming.elapsedMs>=d.identificationTiming.failedRequestMs);
 assert.equal(await page.locator('#marketBtn').isDisabled(),true);assert.deepEqual(errors,[]);
});
test('targeted detail merge changes literal numbers only and preserves prior image attribution',async()=>{
 await reset();await upload(photos.slice(0,2));
 const merged=await page.evaluate(()=>mergeDetail265({model:'Card H22/H32',title:'H22/H32',distinctive_terms:['H22/H32'],identifier_observations:[{text:'H22/H32',role:'collector_number',legibility:'uncertain',image_index:2}],layout_signature:[]},
  {text_corrections:[{previous:'H22/H32',read_text:'H23/H32'},{previous:'Card',read_text:'Invented'}],identifier_observations:[{text:'H23/H32',role:'collector_number',legibility:'clear',image_index:2}],pokemon_printing:null}));
 assert.equal(merged.model,'Card H23/H32');assert.equal(merged.identifier_observations[0].image_index,2);
 assert.deepEqual(errors,[]);
});
