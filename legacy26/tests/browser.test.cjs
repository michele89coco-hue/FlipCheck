// Browser integration with fabricated API responses; all external requests are intercepted.
const {test,before,after} = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require('playwright');
let server,browser,page,origin,photos,requests=[],response,responder=null,errors=[];
const realReadings=require('./fixtures/build159-vision.json');
const envelope=(value,extra={})=>({status:'completed',output:[{type:'message',content:[{type:'output_text',text:JSON.stringify(value)}]}],usage:{input_tokens:100,output_tokens:100,input_tokens_details:{cached_tokens:0}},...extra});
const root=path.join(__dirname,'../src/main/assets');
const kobe={status:'identified',kind:'card',title:'SkyBox 1997-98 Metal Universe Kobe Bryant #81 Base',category:'sports card',brand:'SkyBox',family:'Metal Universe',model:'1997-98 Metal Universe Kobe Bryant #81',variant:'Base',condition:'raw',category_confidence:99,brand_confidence:98,family_confidence:98,model_confidence:94,model_verified:false,market_ready:true,candidate_models:[],visual_fingerprint:'Kobe Bryant with metal background',distinctive_terms:['Kobe Bryant','Metal Universe','81'],search_terms:['Metal Universe'],identifier_hints:['81'],layout_signature:[],evidence:['Kobe Bryant nameplate','81 on back'],missing_information:[],next_photo_request:null,user_text_consistent:true,normalized_query:'1997-98 SkyBox Metal Universe Kobe Bryant #81 Base',verification_summary:'Synthetic browser behavior fixture',pokemon_printing:null};
const printing={is_pokemon:true,language:'English',set_name:'Base Set',card_type:'pokemon',first_edition_stamp:'present',stamp_image:1,stamp_location:'left below artwork',stamp_text:'1 EDITION',artwork_shadow:'absent',shadow_image:1,shadow_location:'right frame',copyright_text:'©1995,96,98 Nintendo ©1999 Wizards',copyright_image:1,slab_text:'',slab_image:0};
async function reset() {
  requests=[];response=structuredClone(kobe);responder=null;
  await page.goto(origin);await page.waitForFunction(()=>typeof diagnostic26==='function');
  await page.evaluate(()=>{trial={attempts:2,free:true,credits:0};saveTrial();$('apiKey').value='test-key-never-transmitted';});
}
async function upload(items) { await page.locator('#photoBatch').setInputFiles(items);await page.waitForFunction(()=>!photoBusy); }
before(async()=>{
  server=http.createServer((req,res)=>{
    const name=req.url==='/'?'index.html':req.url.slice(1);
    if(!['index.html','editions.js','tcg-catalog.js','recognition-fixes.js','targeted-fixes.js'].includes(name)){res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':'text/html');res.end(fs.readFileSync(path.join(root,name)));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));origin='http://127.0.0.1:'+server.address().port;
  browser=await chromium.launch({headless:true,args:['--no-sandbox']});page=await browser.newPage({viewport:{width:412,height:915}});
  page.on('pageerror',error=>errors.push(error.message));
  await page.route('**/*',async route=>{
    if(route.request().url().startsWith(origin))return route.continue();
    if(route.request().url()==='https://api.openai.com/v1/responses'){
      requests.push(JSON.parse(route.request().postData()));
      const payload=responder?responder(requests.at(-1)):envelope(response);
      return route.fulfill({status:payload.httpStatus || 200,contentType:'application/json',body:JSON.stringify(payload)});
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
test('original sports result uses two images in one Vision request and no web',async()=>{
  await reset();await upload(photos.slice(0,2));await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  assert.equal(requests.length,1);assert.equal(requests[0].model,'gpt-5.6-luna');assert.equal(requests[0].tools,undefined);
  assert.equal(requests[0].input[0].content.filter(x=>x.type==='input_image').length,2);
  assert.match(await page.locator('#identTitle').textContent(),/Metal Universe Kobe Bryant #81 Base/);
  assert.equal(await page.evaluate(()=>ident.model_confidence),94);assert.equal(await page.locator('#marketBtn').isDisabled(),false);
  assert.equal(await page.locator('#printingPanel').count(),0);
});
test('same Vision response displays stamp and Shadowless with matching market query',async()=>{
  await reset();await upload([photos[0]]);
  response={...kobe,brand:'Pokemon',family:'Base Set',model:'Alakazam #1/102',title:'Alakazam',variant:'Holo',normalized_query:'Pokemon Base Set Alakazam #1/102 Holo',pokemon_printing:printing};
  await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  assert.equal(requests.length,1);assert.ok(requests[0].text.format.schema.required.includes('pokemon_printing'));
  assert.match(await page.locator('#printingPanel').textContent(),/1st Edition visibile/);
  assert.match(await page.locator('#confirmed').inputValue(),/1st Edition Shadowless/);
  assert.equal(await page.locator('#marketBtn').isDisabled(),false);
  await page.screenshot({path:process.env.FC26_SCREENSHOT||'/tmp/flipcheck26-printing.png',fullPage:true});
});
test('uncertain edition keeps identity visible, disables automatic comps and exports without key/images',async()=>{
  await reset();await upload([photos[0]]);
  response={...kobe,brand:'Pokemon',family:'Base Set',model:'Alakazam #1/102',variant:'Holo',pokemon_printing:{...printing,first_edition_stamp:'unclear',stamp_text:''}};
  await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  assert.equal(requests.length,1);assert.match(await page.locator('#identTitle').textContent(),/Alakazam #1\/102/);
  assert.match(await page.locator('#tags').textContent(),/EDIZIONE DA VERIFICARE/);assert.equal(await page.locator('#marketBtn').isDisabled(),true);
  const diagnostic=await page.evaluate(()=>JSON.stringify(diagnostic26()));
  assert.doesNotMatch(diagnostic,/test-key-never-transmitted|data:image|Authorization|Bearer/);
  assert.equal(JSON.parse(diagnostic).uploadedImageCount,1);assert.equal(JSON.parse(diagnostic).phases.length,1);
  assert.deepEqual(errors,[]);
});
test('real successful card readings still use one Vision and zero searches',async()=>{
  for(const name of ['machamp','vileplume']) {
    await reset();await upload([photos[0]]);response=structuredClone(realReadings[name]);
    await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
    assert.equal(requests.length,1);assert.equal(await page.evaluate(()=>ident.market_ready),true);
    assert.match(await page.locator('#confirmed').inputValue(),/1st Edition/);
  }
});
test('truncated resolver responses keep observations and show the technical cause for box and remote',async()=>{
  for(const name of ['box','remote']) {
    await reset();await upload(photos.slice(0,name==='remote'?2:1));
    responder=body=>body.text.format.name==='flipcheck_identification'?envelope(realReadings[name]):
      envelope(null,{status:'incomplete',incomplete_details:{reason:'max_output_tokens'},
        output:[{type:'web_search_call',status:'completed'},{type:'message',content:[{type:'output_text',text:'{"candidate_checks":['}]}],
        usage:{input_tokens:1000,output_tokens:body.max_output_tokens}});
    await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
    assert.equal(requests.length,2);assert.equal(requests[1].max_output_tokens,2600);assert.equal(requests[1].max_tool_calls,1);
    const d=await page.evaluate(()=>diagnostic26());
    assert.equal(d.phases[1].incompleteReason,'max_output_tokens');assert.ok(d.phases[1].parseError);
    assert.equal(d.identification.market_ready,false);assert.equal(d.resolverFailure.reason,'max_output_tokens');
    assert.match(await page.locator('#runStatus').textContent(),/risposta incompleta/);
    assert.equal(await page.locator('#addConfirmPhoto').isVisible(),false);
    if(name==='remote')assert.equal(d.identification.next_photo_request,null);
  }
});
test('Politoed catalogue contradiction forces one search without making the disputed number an exact query',async()=>{
  await reset();await upload([photos[0]]);
  responder=body=>body.text.format.name==='flipcheck_identification'?envelope(realReadings.politoed):
    envelope(null,{status:'incomplete',incomplete_details:{reason:'max_output_tokens'}});
  await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  assert.equal(requests.length,2);assert.match(requests[1].input,/CONFLITTO CATALOGO LOCALE/);
  const d=await page.evaluate(()=>diagnostic26());
  assert.equal(d.initialCardCheck.status,'conflict');assert.equal(d.identification.market_ready,false);
  assert.equal(d.identification.model_verified,false);assert.match(await page.locator('#tags').textContent(),/DATI INCOERENTI/);
  const signature=await page.evaluate(()=>buildFingerprintSignature(ident,''));
  assert.equal(signature.identifiers.length,0);assert.doesNotMatch(signature.query,/12\s*\/\s*111|Neo Genesis/i);
});
test('complete resolver response consumes grounded remote codes and removes duplicate back requests',async()=>{
  await reset();await upload(photos.slice(0,2));
  responder=body=>{
    if(body.text.format.name==='flipcheck_identification')return envelope(realReadings.remote);
    const refined={...realReadings.remote,candidate_checks:[{model:'YKF423-001',brand:'Philips',family:'TV remote',variant:'Voice',
      part_number:'398GM10BEPHN0004HT',alias:'',matched_terms:realReadings.remote.distinctive_terms,
      missing_terms:[],conflicting_terms:[],source_specificity:'identifier_grounded',source_count:1,reason:'Synthetic source linkage'}]};
    const result=envelope(refined);
    result.output.unshift({type:'web_search_call',status:'completed',action:{sources:[{url:'https://www.philips.com/support/test-remote',
      title:'Philips YKF423-001 remote manual',snippet:'Philips remote model YKF423-001 part 398GM10BEPHN0004HT. RC6H_IR_V10_20170707. PAIR TOP PICKS VOICE TV GUIDE SOURCES OPTIONS SUBTITLE TEXT NETFLIX.'}]}});
    return result;
  };
  await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  assert.equal(requests.length,2);
  const d=await page.evaluate(()=>diagnostic26());assert.equal(d.phases[1].parseError,null);
  assert.equal(d.identification.model,'YKF423-001');assert.equal(d.identification.market_ready,true);
  assert.equal(d.identification.next_photo_request,null);assert.equal(d.resolverFailure,null);
});
test('failed request is recorded without a key and never exports the previous identity',async()=>{
  await reset();await upload([photos[0]]);await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  responder=()=>({httpStatus:401,error:{message:'Invalid test-key-never-transmitted'}});
  await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  const d=await page.evaluate(()=>diagnostic26());assert.equal(d.identification,null);
  assert.equal(d.phases[0].responseStatus,'request_error');assert.doesNotMatch(JSON.stringify(d),/test-key-never-transmitted/);
  assert.equal(await page.locator('#identifyBtn').isDisabled(),false);assert.deepEqual(errors,[]);
});
test('a coherent sourced card can recover from the wrong set without replacing observed HP or requesting a stamp',async()=>{
  await reset();await upload([photos[0]]);
  responder=body=>{
    if(body.text.format.name==='flipcheck_identification')return envelope(realReadings.politoed);
    const refined={...realReadings.politoed,title:'Politoed Skyridge',family:'Skyridge',
      candidate_checks:[{model:'Politoed H23/H32',brand:'Pokémon',family:'Skyridge',variant:'Holo Italian',part_number:'',alias:'',
        matched_terms:['Politoed','Crescita Improvvisa','Ranabalzo','Spruzza','110 PV','FASE 2','30+','70'],missing_terms:[],conflicting_terms:[],
        source_specificity:'exact_model',source_count:2,reason:'Synthetic sourced catalogue resolution'}]};
    const result=envelope(refined);
    result.output.unshift({type:'web_search_call',status:'completed',action:{sources:[
      {url:'https://www.pokemon.com/test-catalog/politoed',title:'Pokemon Politoed H23/H32 Skyridge catalog',snippet:'Pokemon Politoed H23/H32 Holo Italiano. 110 PV FASE 2 Crescita Improvvisa Ranabalzo 30+ Spruzza 70.'},
      {url:'https://www.pokemon.com/test-checklist/skyridge',title:'Pokemon Skyridge checklist Politoed H23/H32',snippet:'Politoed H23/H32 Pokemon. 110 PV Crescita Improvvisa Ranabalzo Spruzza.'}
    ]}});return result;
  };
  await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  const d=await page.evaluate(()=>diagnostic26());assert.equal(requests.length,2);
  assert.equal(d.identification.card_consistency.status,'consistent');assert.equal(d.identification.family,'Skyridge');
  assert.equal(d.identification.market_ready,true);assert.equal(d.identification.printing_check.stamp,'not_applicable');
  assert.equal(d.visionResult.model,'Politoed 12/111');assert.doesNotMatch(await page.locator('#confirmed').inputValue(),/1st|Neo Genesis|12\/111/);
});
test('a complete box resolver response uses source-backed configuration without another photo',async()=>{
  await reset();await upload([photos[0]]);
  responder=body=>{
    if(body.text.format.name==='flipcheck_identification')return envelope(realReadings.box);
    const model='2025-26 Topps Chrome Basketball Update Series Hobby Box';
    const refined={...realReadings.box,next_photo_request:null,missing_information:[],candidate_checks:[
      {model,brand:'Topps',family:realReadings.box.family,variant:'Hobby Box',part_number:'',alias:'',
        matched_terms:realReadings.box.distinctive_terms,missing_terms:[],conflicting_terms:[],
        source_specificity:'exact_model',source_count:2,reason:'Synthetic catalogue configuration match'}]};
    const result=envelope(refined);
    result.output.unshift({type:'web_search_call',status:'completed',action:{sources:[1,2].map(n=>({
      url:'https://www.topps.com/test-catalog/'+n,title:model+' product catalog',
      snippet:realReadings.box.family+' Scatola sigillata. '+realReadings.box.distinctive_terms.join(' ')+' Hobby Box.'
    }))}});return result;
  };
  await page.locator('#identifyBtn').click();await page.waitForFunction(()=>!apiBusy);
  const d=await page.evaluate(()=>diagnostic26());assert.equal(requests.length,2);
  assert.equal(d.identification.market_ready,true);assert.match(d.identification.model,/Hobby Box/);
  assert.equal(d.identification.next_photo_request,null);
});
test('source metadata without excerpts cannot become grounded web evidence',async()=>{
  await reset();
  const results=await page.evaluate(()=>collectRawWebResults({output:[{type:'web_search_call',action:{sources:[
    {url:'https://www.philips.com/example',title:'Philips YKF423-001 remote manual'}]}}]}));
  assert.equal(results.length,0);
});
