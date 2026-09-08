/* Offline semantic-role and query regressions. Boniface/Topps variants below
 * mutate the stored 187 observations to reproduce the reported 188 defects;
 * they are not represented as new live provider responses. */
const test=require('node:test'),assert=require('node:assert/strict');
const V=require('../src/main/assets/visual-policy.js'),recorded=require('./fixtures/diagnostics-187.json');
const clone=x=>structuredClone(x);
function boniface(){
 const p=clone(recorded.boniface.photo),n=p.photo_clues.find(c=>c.role==='collector_number');
 n.role='text';n.region={image_index:2,x:.43,y:.35,width:.15,height:.03,certain:true};
 return p;
}
const numberClue=p=>p.photo_clues.find(c=>/^No\./i.test(c.text));
function local(p,text='No.21',extra={}){
 const c=numberClue(p),r=c.region;
 return [{image_index:c.image_index,state:'ok',origin:'on_device_photo_ocr',meta:{originalWidth:1000,originalHeight:1000,rect:{x:0,y:0,width:1000,height:1000}},lines:[{text,x:r.x,y:r.y,width:r.width,height:r.height,...extra}]}];
}
test('189 semantic No. recovery uses the literal image and local OCR, then targets the exact checklist',()=>{
 const p=boniface(),read=V.reconcilePhotoOcr(p,local(p));
 assert.equal(numberClue(p).role,'text'); // original provider response is immutable
 assert.equal(numberClue(read).role,'collector_number');
 assert.equal(numberClue(read).text,'No. 21');assert.equal(numberClue(read).original_role,'text');
 assert.equal(read.number_role_recoveries[0].rule,'explicit_label_photo_ocr_agreement');
 assert.equal(read.number_role_recoveries[0].ocr_quote,'No.21');
 assert.equal(V.cardKeyFacts(read).number.value,'21');assert.equal(V.googleFirst(read),false);
 assert.match(V.cataloguePlan185(read).query,/2025-26.*VICTOR BONIFACE.*#21.*terrace.*green.*\/5/);
 assert.doesNotMatch(V.cataloguePlan185(read).query,/#No|#2\/5/);
});
test('189 a clear bounded No. label with printed sports product and season can route without OCR',()=>{
 const p=boniface(),read=V.reconcilePhotoOcr(p,[]);
 assert.equal(V.cardKeyFacts(read).number.value,'21');
 assert.equal(read.number_role_recoveries[0].rule,'explicit_label_printed_card_context');
 assert.equal(read.number_role_recoveries[0].ocr_quote,undefined);
 assert.equal(V.serialEvidence184(read).value,'2/5');
});
test('189 a recovered semantic role never erases a genuine independent number disagreement',()=>{
 const p=boniface(),read=V.reconcilePhotoOcr(p,local(p,'No.22'));
 assert.equal(V.cardKeyFacts(read),null);assert.equal(read.reading_disagreements[0].ocr,'22');
 assert.equal(read.reading_disagreements[0].vision,'No. 21');
});
test('189 native rotated OCR alternatives remain conflicting readings, not repeated confidence votes',()=>{
 const p=boniface(),n=numberClue(p),ocr=local(p,'No.21',{ambiguous:true,observation_count:5,alternatives:[{text:'No.22',x:n.region.x,y:n.region.y,width:n.region.width,height:n.region.height,observation_count:1,rotation_degrees:90}]});
 const out=V.reconcilePhotoOcr(p,ocr);assert.equal(V.cardKeyFacts(out),null);
 assert.deepEqual(out.ocr_number_readings.map(r=>r.text),['21','22']);
});
test('189 agreeing alternate punctuation does not become a different collector number',()=>{
 const p=boniface(),n=numberClue(p),ocr=local(p,'No.21',{ambiguous:true,alternatives:[{text:'No. 21',x:n.region.x,y:n.region.y,width:n.region.width,height:n.region.height}]});
 const out=V.reconcilePhotoOcr(p,ocr);assert.equal(V.cardKeyFacts(out).number.value,'21');assert.equal(out.ocr_number_readings.length,1);
});
test('189 another image cannot be claimed as independent OCR support for this No. label',()=>{
 const p=boniface(),ocr=local(p);ocr[0].image_index=1;
 const out=V.reconcilePhotoOcr(p,ocr);assert.equal(out.number_role_recoveries[0].rule,'explicit_label_printed_card_context');
});
test('189 another clearly transcribed collector number remains a conflict after role recovery',()=>{
 const p=boniface();p.photo_clues.push({...numberClue(p),role:'collector_number',text:'No. 22'});
 assert.equal(V.cardKeyFacts(V.reconcilePhotoOcr(p,local(p))),null);
});
test('189 jersey, statistic, serial, year and ambiguous labels do not become a collector key',()=>{
 for(const mutate of [
  p=>numberClue(p).text='21',p=>numberClue(p).text='2025-26',p=>numberClue(p).text='2/5',
  p=>numberClue(p).text='21 PTS',p=>numberClue(p).text='No. 2025',
  p=>numberClue(p).location='jersey number',p=>numberClue(p).context='career statistics',
  p=>numberClue(p).role='serial',p=>numberClue(p).role='card_stat',p=>numberClue(p).role='subject',
  p=>numberClue(p).certainty='uncertain',p=>numberClue(p).region.certain=false,
  p=>numberClue(p).region={image_index:2,x:.01,y:.01,width:.05,height:.02,certain:true},
  p=>numberClue(p).region.image_index=1,p=>p.object_unit='lot',
 ]){
  const p=boniface();mutate(p);const out=V.reconcilePhotoOcr(p,[]);
  assert.equal(V.cardKeyFacts(out),null,mutate.toString());
 }
});
test('189 a Pokemon species No. stays distinct from a set collector number even if OCR agrees',()=>{
 const p=boniface();p.brand='Pokémon';p.category='Pokémon TCG';p.pokemon_printing={is_pokemon:true};
 const out=V.reconcilePhotoOcr(p,local(p));assert.equal(numberClue(out).role,'text');assert.equal(V.cardKeyFacts(out),null);
});
test('189 the same unknown role without photographed family and date requires independent OCR',()=>{
 const p=boniface();p.family='Imagined Collection';p.photo_clues=p.photo_clues.filter(c=>!c.text.startsWith('2025-26'));
 assert.equal(V.cardKeyFacts(V.reconcilePhotoOcr(p,[])),null);
 assert.equal(V.cardKeyFacts(V.reconcilePhotoOcr(p,local(p))).number.value,'21');
});
test('189 normalizing a labelled number never rewrites its literal photographic quote',()=>{
 const p=boniface();numberClue(p).text='No. 0021';
 const out=V.reconcilePhotoOcr(p,local(p,'No.0021'));
 assert.equal(V.numberKey185(V.cardKeyFacts(out).number.value),'21');
 assert.equal(V.cardKeyFacts(out).number.quote,'No. 0021');
});
test('189 four-digit seasons are parsed completely, including century boundaries',()=>{
 for(const [input,expected] of [['2025-2026','2025-26'],['2025/2026','2025-26'],['1999-2000','1999-00'],['1997-98','1997-98'],['2026','2026']])assert.equal(V.seasonValue(input),expected,input);
 const p=boniface();p.photo_clues.find(c=>c.text.startsWith('2025-26')).text='2025-2026 PANINI SELECT ROAD TO FIFA WORLD CUP 2026 SOCCER';
 const out=V.reconcilePhotoOcr(p,local(p));assert.equal(V.cardKeyFacts(out).date.value,'2025-26');
 assert.equal(V.catalogueScope186(out,{title:'2025-2026 Panini Select Road to FIFA World Cup Soccer Victor Boniface #21'}).eligible,true);
 assert.equal(V.catalogueScope186(out,{title:'2024-2025 Panini Select Road to FIFA World Cup Soccer Victor Boniface #21'}).eligible,false);
});
test('189 statistics and event dates are not a substitute for a photographed release season',()=>{
 const p=boniface();p.photo_clues=p.photo_clues.filter(c=>!c.text.startsWith('2025-26'));
 p.photo_clues.push({text:'2025-2026 career totals PTS 320',role:'season',certainty:'clear',image_index:2});
 const read=V.reconcilePhotoOcr(p,local(p));assert.equal(V.cardKeyFacts(read).date,null);
 p.photo_clues.push({text:'FIFA WORLD CUP 2026',role:'season',certainty:'clear',image_index:2});
 assert.equal(V.cardKeyFacts(V.reconcilePhotoOcr(p,local(p))).date,null);
});
test('189 aliases tolerate specified spelling differences but not a distinct name or numeric key',()=>{
 for(const [a,b] of [['Dončić','Doncic'],['Luka Donci','Luka Doncic'],['Doncic','Luka Doncic'],['Kob Bryant','Kobe Bryant']])assert.equal(V.nameAlias187(a,b),true);
 for(const [a,b] of [['Mew','Mewtwo'],['Devin Booker','Devon Booker'],['Luka Doncic','Luka Samanic'],['21','22'],['280','28']])assert.equal(V.nameAlias187(a,b),false);
 assert.equal(V.subjectSpan187('Victor Boniface','21 Victor Froholdt\n22 Boniface Other'),'');
});
test('189 a manufacturer without a preconfigured domain still gets a precise key-based checklist query',()=>{
 const p=clone(recorded.vileplume.photo);p.brand='Independent Publisher';p.category='trading card';p.pokemon_printing=null;
 const plan=V.cataloguePlan185(p);assert.equal(plan.kind,'card_checklist');assert.match(plan.query,/Independent Publisher.*Vileplume.*15\/64.*checklist/);assert.deepEqual(plan.domains,[]);
});
test('189 Topps query retains literally printed Basketball even if family extraction omitted sport',()=>{
 const p=clone(require('./fixtures/diagnostics-188.json').topps.visionResult);p.family='Topps Chrome Update Series';
 const plan=V.cataloguePlan185(p);assert.equal(plan.kind,'box_configuration');assert.match(plan.query,/Topps Chrome Update Series basketball box/i);
 assert.doesNotMatch(plan.query,/Cooper|Flagg|Wembanyama|Hobby|Mega|parallel|#[0-9]/i);
 assert.equal(V.catalogueScope186(p,{title:'2025-26 Topps Chrome Update Baseball Hobby Box'}).eligible,false);
 assert.equal(V.catalogueScope186(p,{title:'2025-26 Topps Chrome Update Basketball Hobby Box'}).eligible,true);
});
test('189 the sport is supported by printed text, never guessed from athlete names or inferred category',()=>{
 const p=clone(recorded.topps.photo);p.family='Topps Chrome Update Series';p.photo_clues=p.photo_clues.filter(c=>!/(?:basketball|baseball|soccer)/i.test(c.text));
 assert.doesNotMatch(V.cataloguePlan185(p).query,/basketball/i);
});
test('189 a label without denominator may match its exact card fraction, but not another number',()=>{
 const p=clone(recorded.cloyster.photo);assert.equal(V.numberKey185(V.slabFacts185(p).card_number),'8');
 assert.equal(V.catalogueScope186(p,{title:'2002 Expedition Italian Cloyster #8/165 Holo'}).eligible,true);
 assert.equal(V.catalogueScope186(p,{title:'2002 Expedition Italian Cloyster #9/165 Holo'}).eligible,false);
 p.slab_reading.card_number='8/165';p.slab_reading.label_text+=' 8/165';p.photo_clues=p.photo_clues.filter(c=>c.role!=='collector_number');
 assert.equal(V.catalogueScope186(p,{title:'2002 Expedition Italian Cloyster #8/102 Holo'}).eligible,false);
});
test('189 a green border does not borrow blue from the card centre or red from a jersey',()=>{
 const p=boniface();p.physical_observations=[{feature:'color',text:'Green foil border with blue center and red jersey',entity:'target',certainty:'clear',image_index:1}];
 assert.deepEqual(V.borderColors187(p),['green']);assert.doesNotMatch(V.cataloguePlan185(V.reconcilePhotoOcr(p,[])).query,/blue|red/i);
 p.physical_observations[0].text='Green and gold prismatic border';assert.deepEqual(V.borderColors187(p),['green','gold']);
});
