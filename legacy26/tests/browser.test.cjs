// Browser integration with fabricated API responses; all external requests are intercepted.
const {test,before,after} = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require('playwright');
let server,browser,page,origin,photos,requests=[],response,errors=[];
const root=path.join(__dirname,'../src/main/assets');
const kobe={status:'identified',kind:'card',title:'SkyBox 1997-98 Metal Universe Kobe Bryant #81 Base',category:'sports card',brand:'SkyBox',family:'Metal Universe',model:'1997-98 Metal Universe Kobe Bryant #81',variant:'Base',condition:'raw',category_confidence:99,brand_confidence:98,family_confidence:98,model_confidence:94,model_verified:false,market_ready:true,candidate_models:[],visual_fingerprint:'Kobe Bryant with metal background',distinctive_terms:['Kobe Bryant','Metal Universe','81'],search_terms:['Metal Universe'],identifier_hints:['81'],layout_signature:[],evidence:['Kobe Bryant nameplate','81 on back'],missing_information:[],next_photo_request:null,user_text_consistent:true,normalized_query:'1997-98 SkyBox Metal Universe Kobe Bryant #81 Base',verification_summary:'Synthetic browser behavior fixture',pokemon_printing:null};
const printing={is_pokemon:true,language:'English',set_name:'Base Set',card_type:'pokemon',first_edition_stamp:'present',stamp_image:1,stamp_location:'left below artwork',stamp_text:'1 EDITION',artwork_shadow:'absent',shadow_image:1,shadow_location:'right frame',copyright_text:'©1995,96,98 Nintendo ©1999 Wizards',copyright_image:1,slab_text:'',slab_image:0};
async function reset() {
  requests=[];response=structuredClone(kobe);
  await page.goto(origin);await page.waitForFunction(()=>typeof diagnostic26==='function');
  await page.evaluate(()=>{trial={attempts:2,free:true,credits:0};saveTrial();$('apiKey').value='test-key-never-transmitted';});
}
async function upload(items) { await page.locator('#photoBatch').setInputFiles(items);await page.waitForFunction(()=>!photoBusy); }
before(async()=>{
  server=http.createServer((req,res)=>{
    const name=req.url==='/'?'index.html':req.url.slice(1);
    if(!['index.html','editions.js','targeted-fixes.js','visual-policy.js','visual-runtime.js','google-direct.js','background-runtime.js','slab-identity.js','identity-final.js','image-evidence.js','catalogue-engine.js','catalogue-sources.js','catalogue-runtime.js'].includes(name)){res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':'text/html');res.end(fs.readFileSync(path.join(root,name)));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));origin='http://127.0.0.1:'+server.address().port;
  browser=await chromium.launch({executablePath:process.env.FLIPCHECK_BROWSER_EXECUTABLE||undefined,headless:true,args:['--no-sandbox']});page=await browser.newPage({viewport:{width:412,height:915}});
  page.on('pageerror',error=>errors.push(error.message));
  await page.route('**/*',async route=>{
    if(route.request().url().startsWith(origin))return route.continue();
    if(route.request().url()==='https://api.openai.com/v1/responses'){
      requests.push(JSON.parse(route.request().postData()));
      return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({status:'completed',output:[{type:'message',content:[{type:'output_text',text:JSON.stringify(response)}]}],usage:{input_tokens:100,output_tokens:100,input_tokens_details:{cached_tokens:0}}})});
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
// Identity decisions and final rendering are exercised through the production
// catalogue engine in catalogue-browser.test.cjs (supersedes Vision-only closure).
