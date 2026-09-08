/* Release-189 reference selection: actual 188 observations plus adversarial
 * synthetic provider replies. No network, no paid API or generated OCR truth. */
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const V=require('../src/main/assets/visual-policy.js'),actual=require('./fixtures/diagnostics-188.json');
const photo=n=>structuredClone(actual[n].visionResult),refs=n=>structuredClone(actual[n].visualAssistance.retainedReferences);
const card=()=>({kind:'card',object_unit:'single',category:'basketball card',brand:'Example',family:'Prizm Basketball',title:'Alex Rivera #73',photo_clues:[{text:'Alex Rivera',role:'subject',certainty:'clear'},{text:'NO. 73',role:'collector_number',certainty:'clear'},{text:'2031-32 Example Prizm Basketball',role:'season',certainty:'clear'}],physical_observations:[]});
const ref=(image,caption='',ocr)=>({id:image,url:'https://catalog.example/checklist',title:'2031-32 Example Prizm Basketball Checklist',text:'2031-32 Example Prizm Basketball Checklist\n73 Alex Rivera\n74 Other Player',text_origin:'retrieved_page',image_url:'https://images.example/'+image,image_caption:caption,ocr});
test('189 actual Doncic rejects Ayton and Barkley before image comparison',()=>{
 assert.equal(V.rankReferences(refs('doncic'),photo('doncic')).length,0);
 for(const r of refs('doncic')){delete r.ocr;assert.equal(V.imageTargetAffinity189(r,photo('doncic')).eligible,false);}
});
test('189 actual Topps rejects the single-card montage without borrowing page box text',()=>{
 const r=refs('topps'),p=photo('topps');assert.equal(V.rankReferences(r,p).length,0);
 for(const item of r){delete item.ocr;assert.equal(V.imageTargetAffinity189(item,p).reason,'card_image_for_box_target');}
 assert.equal(V.referenceImageKey(r[0]),V.referenceImageKey(r[1]));
});
test('189 actual Politoed rejects non-holo number and both Alakazam resolutions',()=>{
 const r=refs('politoed'),p=photo('politoed');assert.deepEqual(V.rankReferences(r,p),[]);
 assert.equal(V.imageTargetAffinity189(r[0],p).reason,'different_image_card_number');
 assert.equal(V.imageTargetAffinity189(r[3],p).reason,'different_reference_header_subject');
});
test('189 repeated correct names in page body never rescue another image subject',()=>{
 const p=card(),r=ref('2031-32-Prizm-Other-Player.jpg','2031-32 Prizm Other Player');r.text+='\n73 Alex Rivera'.repeat(300);
 assert.equal(V.referenceRelevant(r,p),false);
 r.ocr={text:'NO. 73\nOTHER PLAYER'};assert.equal(V.referenceRelevant(r,p),false);
});
test('189 correctly named image remains a candidate with accented/missing-letter aliases',()=>{
 const p=card();for(const name of ['Alex Rivera','Álex Rivéra','Alex Rvera']){
  const r=ref('2031-32-Prizm-'+name.replaceAll(' ','-')+'.jpg',name+' Prizm card — front');
  assert.equal(V.referenceRelevant(r,p),true,name);assert.ok(V.imageTargetAffinity189(r,p).score>0);
 }
});
test('189 reference image number beats correct page number and caption',()=>{
 const p=card(),r=ref('Alex-Rivera.jpg','Alex Rivera', {state:'ok',text:'NO. 74\nALEX RIVERA'});
 assert.equal(V.referenceRelevant(r,p),false);
 r.ocr.text='NO. 73\nALEX RIVERA';assert.equal(V.referenceRelevant(r,p),true);
});
test('189 sports stamped serial is not treated as a conflicting collector number',()=>{
 const p=card(),r=ref('Alex-Rivera.jpg','Alex Rivera',{state:'ok',text:'ALEX RIVERA\n2/5'});
 assert.equal(V.referenceRelevant(r,p),true);assert.equal(V.rankReferences([r],p).length,1);
 r.ocr.text+='\nNO. 74';assert.equal(V.referenceRelevant(r,p),false);
});
test('189 wrong parallel colour belongs to its own image despite matching page heading',()=>{
 const p=card();p.physical_observations=[{feature:'color',entity:'target',text:'Green border',certainty:'clear',image_index:1}];
 const r=ref('Alex-Rivera-Red-Prizm.jpg','Alex Rivera Red Prizm');r.title='2031-32 Prizm Alex Rivera #73 Green';
 assert.equal(V.imageTargetAffinity189(r,p).reason,'different_image_parallel_colour');
});
test('189 opaque image stays discovery-only and readable unrelated OCR then excludes it',()=>{
 const p=card(),r=ref('b038fc8932c0301.jpg');assert.equal(V.imageTargetAffinity189(r,p).score,0);
 r.ocr={state:'ok',text:'OTHER PLAYER\nCITY TEAM'};assert.equal(V.imageTargetAffinity189(r,p).eligible,false);
});
test('189 target number allows a genuine reverse image without a repeated subject name',()=>{
 const p=card(),r=ref('reverse.jpg','Back',{state:'ok',text:'NO. 73\nPLAYER BIOGRAPHY\nPRIZM BASKETBALL'});
 assert.equal(V.referenceRelevant(r,p),true);assert.equal(V.imageTargetAffinity189(r,p).image_number_supported,true);
});
test('189 PDF pages remain catalogue documents rather than single card photographs',()=>{
 const r=ref('checklist.pdf');r.pages_rendered=[12];r.ocr={text:'NO. 74\nOTHER PLAYER'};
 assert.equal(V.imageTargetAffinity189(r,card()).reason,'catalogue_document_image');
});
test('189 resized and reordered-query images deduplicate but different views remain distinct',()=>{
 const key=image_url=>V.referenceImageKey({image_url});
 assert.equal(key('https://images.example/card-551x315.jpg?w=551&q=80'),key('https://images.example/card.jpg'));
 assert.equal(key('https://images.example/card.jpg?token=abc&a=1&height=200'),key('https://images.example/card.jpg?a=1&token=abc'));
 assert.notEqual(key('https://images.example/front.jpg'),key('https://images.example/back.jpg'));
});
test('189 all page images are ranked before the download limit; resized repeats cannot hide target',()=>{
 const p=card(),page={title:'2031-32 Prizm checklist',text:'73 Alex Rivera',images:['https://images.example/logo.jpg','https://images.example/Prizm-Other-Player.jpg','https://images.example/Prizm-Other-Player-551x315.jpg','https://images.example/Prizm-Alex-Rivera.jpg','https://images.example/Prizm-Alex-Rivera-551x315.jpg'],image_details:[]};
 const out=V.referenceImageCandidates189(page,p);assert.equal(out.length,1);assert.match(out[0].image_url,/Prizm-Alex-Rivera\.jpg$/);
});
test('189 box candidate selection chooses actual packaging beyond initial card montages',()=>{
 const p=photo('topps'),page={images:['https://images.example/2025-26-topps-chrome-variations-feature.jpg','https://images.example/2025-26-topps-chrome-card-parallels.jpg','https://images.example/2025-26-topps-chrome-update-hobby-box.jpg'],image_details:[]};
 const out=V.referenceImageCandidates189(page,p);assert.equal(out.length,1);assert.match(out[0].image_url,/hobby-box/);
});
function directHarness(page){
 const requests=[],context={FlipCheckVisual:V,URL,crypto:require('node:crypto').webcrypto,setTimeout,clearTimeout,queueMicrotask,AbortController};
 context.FlipCheckGoogle={request(id,action,payload){requests.push({action,...JSON.parse(payload)});queueMicrotask(()=>context.FlipCheckDirect.receive(id,action==='page'?page:{status:200,image_data:'synthetic-image'}));},cancel(){}};
 vm.createContext(context);vm.runInContext(fs.readFileSync(require.resolve('../src/main/assets/google-direct.js'),'utf8'),context);return {context,requests};
}
test('189 actual direct bridge downloads later matching image and no incompatible early images',async()=>{
 const p=card(),page={status:200,title:'2031-32 Example Prizm Basketball Checklist',text:'2031-32 Example Prizm Basketball Checklist\n73 Alex Rivera',images:['https://images.example/2031-Prizm-Other-Player.jpg','https://images.example/2031-Prizm-Another-Player.jpg','https://images.example/2031-Prizm-Alex-Rivera.jpg','https://images.example/2031-Prizm-Alex-Rivera-800x1000.jpg'],image_details:[]};
 const {context,requests}=directHarness(page),out=await context.FlipCheckDirect.catalogueReferences([{url:'https://catalog.example/checklist',title:page.title}],{},p);
 assert.equal(out.references.length,1);assert.match(out.references[0].image_url,/Alex-Rivera\.jpg$/);assert.equal(out.textReferences.length,1);
 assert.deepEqual(requests.map(r=>r.action),['page','image']);assert.equal(out.attempts[0].image_candidates,4);assert.equal(out.attempts[0].image_eligible,1);
});
test('189 image exclusion preserves retrieved catalogue text without a paid search or comparison',async()=>{
 const p=photo('topps'),page={status:200,title:'2025-26 Topps Chrome Update Series Basketball Variations Guide',text:'2025-26 Topps Chrome Update Series Basketball Variations Guide\nHobby boxes contain trading cards.',images:['https://images.example/2025-26-topps-chrome-variations-feature.jpg']};
 const {context,requests}=directHarness(page),out=await context.FlipCheckDirect.catalogueReferences([{url:'https://catalog.example/guide',title:page.title}],{},p);
 assert.equal(out.references.length,0);assert.equal(out.textReferences.length,1);assert.deepEqual(requests.map(r=>r.action),['page']);
});

test('189 Hobby-exclusive card image is not mistaken for a Hobby box',()=>{
 const r=ref('Alex-Rivera-Hobby-Exclusive.jpg','Alex Rivera Hobby Exclusive parallel card');
 assert.equal(V.imageTargetAffinity189(r,card()).eligible,true);
});
test('189 image-specific collector caption cannot borrow the matching page number',()=>{
 const r=ref('Alex-Rivera.jpg','Alex Rivera #74');assert.equal(V.imageTargetAffinity189(r,card()).reason,'different_image_metadata_number');
 r.image_caption='Alex Rivera #73';assert.equal(V.imageTargetAffinity189(r,card()).eligible,true);
});
test('189 unknown autograph image is not classified as packaging from autograph text alone',()=>{
 const p=photo('topps'),r={image_url:'https://images.example/027f3ae39d2.jpg',ocr:{state:'ok',text:'CHROME\nCERTIFIED AUTOGRAPH\nOTHER ATHLETE'}};
 assert.equal(V.imageTargetAffinity189(r,p).eligible,false);
});
