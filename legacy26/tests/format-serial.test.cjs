/* Build 184 regression shapes; no live API calls or product-specific answers. */
const test=require('node:test'),assert=require('node:assert/strict');
const V=require('../src/main/assets/visual-policy.js'),E=require('../src/main/assets/editions.js');
const clue=(text,role,image_index=1,region=null)=>({text,role,image_index,region,certainty:'clear'});
function box(){return {kind:'object',object_unit:'box',category:'basketball sealed box',brand:'Example',family:'Chrome Update Series',brand_confidence:98,family_confidence:96,model_confidence:96,market_ready:false,variant:'',variant_scope:'commercial',identity_basis:{family:'printed',variant:'inferred'},photo_clues:[clue('Example','text'),clue('Chrome','text'),clue('UPDATE SERIES','text'),clue('2031/32','season'),clue('1 AUTOGRAPH CARD EVERY BOX!','text')],physical_observations:[]};}
const boxRef={id:'page1',url:'https://catalog.example/guide',title:'2031-32 Example Chrome Updates Basketball Box Guide',text:'2031-32 Example Chrome Updates Basketball Box Guide\nA Hobby Box contains 20 packs with four cards per pack and guarantees one autograph.\nA Jumbo Box guarantees three autographs.\nA Mega Box contains six packs.' ,text_origin:'retrieved_page'};
const entry=(quote='A Hobby Box contains 20 packs with four cards per pack and guarantees one autograph.')=>({reference_id:'page1',unit:'box',scope:'configuration',variant:'Hobby',section_quote:quote,variant_quote:''});
test('box closes from the exact guarantee while pack counts have their own denominator',()=>{
 const p=box(),out=V.specificationClosure184(p,p,{entries:[entry()]},[boxRef]);
 assert.equal(out.market_ready,true);assert.equal(out.variant,'Hobby');assert.match(out.model,/2031-32.*Hobby/);assert.equal(out.next_photo_request,null);assert.equal(out.specification_check.origin,'photo_configuration_and_catalogue');
});
test('confidence and source count do not substitute a wrong format, series, year or guarantee',()=>{
 for(const mode of ['brand','series','year','count','chance','per_case','uncited','unit','mixed']){
  const p=box(),r={...boxRef},e=entry();
  if(mode==='series')r.title=r.title.replace('Updates','Black');
  if(mode==='brand')r.title=r.title.replace('Example','Different Manufacturer');
  if(mode==='year')r.title=r.title.replace('2031-32','2030-31');
  if(mode==='count')e.section_quote=e.section_quote.replace('one autograph','three autographs');
  if(mode==='chance')e.section_quote='A Hobby Box may contain one autograph.';
  if(mode==='per_case')e.section_quote='A Hobby Box contains one autograph per case.';
  if(mode==='uncited')e.section_quote='A Hobby Box guarantees one autograph and one relic.';
  if(mode==='unit')e.unit='single';
  if(mode==='mixed')e.section_quote+=' A Jumbo Box guarantees three autographs.';
  if(!['series','year','uncited'].includes(mode))r.text+='\n'+e.section_quote;
  const out=V.specificationClosure184({...p,model_confidence:99,strong_source_count:4},p,{entries:[e]},[r]);assert.notEqual(out.market_ready,true,mode);
 }
});
test('two genuinely compatible box configurations stay ambiguous',()=>{
 const p=box(),second={...entry(),variant:'Value',section_quote:'A Value Box guarantees one autograph.'},r={...boxRef,text:boxRef.text+'\n'+second.section_quote};
 assert.equal(V.specificationClosure184(p,p,{entries:[entry(),second]},[r]).specification_check.state,'ambiguous');
});
function card(){return {kind:'card',object_unit:'single',category:'soccer trading card',brand:'Example',family:'Select Road to World Cup 2032',model_confidence:94,market_ready:false,variant:'Green parallel unconfirmed',variant_scope:'commercial',identity_basis:{family:'printed',variant:'physical_evidence'},photo_clues:[clue('Alex Rivera','subject'),clue('No. 21','collector_number',2,{image_index:2,x:.4,y:.1,width:.15,height:.03,certain:true}),clue('TERRACE','edition',2),clue('ROAD TO WORLD CUP 2032','season'),clue('2031-32 Example Select Road to World Cup 2032 Soccer','copyright',2),clue('2/5','serial',2)],physical_observations:[{feature:'color',text:'Green holographic border',entity:'target',certainty:'clear',image_index:1}]};}
function catalogue(){const photo=card(),title="2031-32 Example Select Road to World Cup '32 Soccer Checklist",text=title+'\nBase Terrace Checklist\n20 Morgan Vale, Somewhere\n21 Alex Rivera, Elsewhere\n22 Other Athlete\nBase Terrace Parallels\nGreen /5\nGreen Ice\nGold /10';const ref={id:'page1',title,text,url:'https://catalog.example/checklist',text_origin:'retrieved_page'};
 const field=(field,value,kind='none',quote=value)=>({field,value,quote,number_kind:kind,reference_id:'page1',scope:'target',evidence:'text'}),fields=[field('family',"Select Road to World Cup '32 Soccer"),field('subject','Alex Rivera','none','21 Alex Rivera, Elsewhere'),field('catalog_number','21','card_number','21 Alex Rivera, Elsewhere'),field('year','2031-32','season')];return {photo,ref,fields};}
test('sports query retains the athlete and separates tournament year from the printed product season',()=>{
 const p=card(),keys=V.cardKeyFacts(p);assert.equal(keys.date.value,'2031-32');assert.equal(V.clueRole(p.photo_clues[3]),'event_year');assert.match(V.plan(p).query,/Alex Rivera/);assert.match(V.plan(p).query,/21/);
});
test('one cited checklist row closes the exact number even when the page title names the whole set',()=>{
 const {photo,ref,fields}=catalogue(),candidate={unit:'single',decision:'match',same_unit:true,identity_level:'exact',physical_ambiguity:true,ambiguity_scope:'variant',variant_status:'unresolved',fields,matches:[],conflicts:[]};
 const out=V.validate(photo,{candidates:[candidate]},[ref]);assert.equal(out.catalogue_core_verified,true,JSON.stringify(out));assert.match(out.model,/#21.*Alex Rivera/);assert.equal(out.market_ready,false);
 fields[1].quote='20 Morgan Vale, Somewhere';assert.equal(V.keyEvidence(photo,fields,[ref]),null);
 fields[1].quote='21 Alex Rivera, Elsewhere';fields[2].quote='22 Other Athlete';assert.equal(V.keyEvidence(photo,fields,[ref]),null);
});
test('a numbered colour parallel closes with base-level scope and preserves the specimen serial',()=>{
 const {photo,ref,fields}=catalogue(),base={...photo,catalogue_core_verified:true,core_identity:{status:'confirmed',model:'2031-32 Select #21 Alex Rivera',fields}},e={reference_id:'page1',unit:'single',scope:'base',variant:'Green',section_quote:'Base Terrace Parallels\nGreen /5\nGreen Ice\nGold /10',variant_quote:'Green /5'};
 const out=V.specificationClosure184(base,photo,{entries:[e]},[ref]);assert.equal(out.market_ready,true);assert.equal(out.variant,'Green');assert.equal(out.serial_number,'2/5');assert.equal(out.print_run,5);assert.equal(out.physical_serial.image_index,2);assert.match(out.normalized_query,/\/5/);
 for(const mode of ['run','subset','scope','pattern','colour','serial']){
  const p=structuredClone(photo),c={...e},r={...ref};
  if(mode==='run')p.photo_clues[5].text='2/10';
  if(mode==='subset')c.section_quote=c.section_quote.replace('Base Terrace','Autographs');
  if(mode==='scope')c.scope='other';
  if(mode==='pattern'){c.variant='Green Wave';c.variant_quote='Green Wave /5';c.section_quote=c.section_quote.replace('Green /5','Green Wave /5');}
  if(mode==='colour')p.physical_observations[0].text='Red holographic border';
  if(mode==='serial')p.photo_clues[5].certainty='uncertain';
  r.text+='\n'+c.section_quote;assert.notEqual(V.specificationClosure184(base,p,{entries:[c]},[r]).market_ready,true,mode);
 }
});
test('a blurred footer serial triggers an original-photo crop and never becomes another card number',()=>{
 const p=card();p.photo_clues.pop();const ocr=[{image_index:2,state:'ok',meta:{originalWidth:1000,originalHeight:1400,rect:{x:0,y:0,width:1000,height:1400}},lines:[{text:'No. 21',x:.4,y:.1,width:.15,height:.03},{text:'215',x:.45,y:.9,width:.1,height:.02}]}];
 const requests=V.detailRequests(p,ocr,2);assert.equal(requests[0].role,'serial');assert.equal(requests[0].image_index,2);assert.ok(requests[0].region.y>.7);
 const bad=V.applyPhotoDetails(p,[{clue_index:requests[0].clue_index,text:'215',role:'serial',certainty:'clear'}],requests,[]);assert.equal(bad.updates.length,0);
 const good=V.applyPhotoDetails(p,[{clue_index:requests[0].clue_index,text:'2/5',role:'serial',certainty:'clear'}],requests,[]);assert.equal(V.serialEvidence184(good.value).value,'2/5');assert.equal(V.cardKeyFacts(good.value).number.value,'21');
 ocr[0].lines[1].text='2/5';assert.equal(V.reconcilePhotoOcr(p,ocr).ocr_number_readings.some(r=>r.text==='2/5'),false);
});
test('unreadable, impossible, conflicting and non-serial numbers are never fabricated as specimen numbering',()=>{
 for(const t of ['215','6/5','0/5','2025/26'])assert.equal(V.parseSerial184(t),null,t);
 const p=card();p.photo_clues.push(clue('3/5','serial',1));assert.equal(V.serialEvidence184(p),null);
 p.photo_clues=p.photo_clues.filter(c=>c.role!=='serial');p.photo_clues.push(clue('2/5','collector_number'));assert.equal(V.serialEvidence184(p),null);
});
test('catalogue release date resolves the stamp era without rewriting a misread copyright',()=>{
 const p={kind:'card',family:'Summit',catalogue_core_verified:true,pokemon_printing:{is_pokemon:true,language:'Italian',set_name:'Summit',first_edition_stamp:'unclear',copyright_text:'©2001 Example',copyright_image:1,artwork_shadow:'not_applicable'},core_identity:{status:'confirmed',fields:[{field:'family',value:'Summit'},{field:'subject',value:'Rivermon'},{field:'catalog_number',value:'H7/H32'}]}};
 const r={id:'page2',url:'https://catalog.example/rivermon',title:'Rivermon Summit H7',text:'Date de sortie de la carte\n12 mai 2003\nH7/H32',text_origin:'retrieved_page'};
 const out=V.releaseEvidence184(p,[r]),printing=E.cataloguePrinting(out,out.pokemon_printing);assert.equal(out.source_confirmed_year,'2003');assert.equal(printing.stamp_policy.state,'not_applicable');assert.equal(printing.copyright_text,'©2001 Example');assert.equal(printing.first_edition_stamp,'unclear');
 assert.equal(V.releaseEvidence184(p,[{...r,title:'Different set H7'}]).source_confirmed_year,undefined);
 assert.equal(V.releaseEvidence184(p,[{...r,text:'Website copyright 2003'}]).source_confirmed_year,undefined);
 assert.equal(V.releaseEvidence184(p,[r,{...r,id:'page3',text:'Release date 2004'}]).source_confirmed_year,undefined);
});
test('resolver citation URLs are retrieved but their asserted quotes do not become evidence',()=>{
 const p=box(),checks=[{model:boxRef.title,match_evidence:[{source_url:boxRef.url,source_text:'Invented quote'}]}],sources=V.citedSources184([{url:'https://catalog.example/irrelevant',title:'Other'}],checks,p);
 assert.equal(sources[0].url,boxRef.url);assert.equal(sources[0].discovery_only,true);assert.equal(sources[0].text,'');assert.equal(V.trustedReferenceText(sources[0]),false);
});
