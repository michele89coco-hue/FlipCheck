/* Recorded build-185 observations; new catalogue fixtures are explicitly synthetic. No live AI. */
const test=require('node:test'),assert=require('node:assert/strict');
const V=require('../src/main/assets/visual-policy.js'),E=require('../src/main/assets/editions.js');
const reports=require('./fixtures/diagnostics-185.json'),photo=name=>structuredClone(reports[name].photo);
const reference=(title,text=title)=>({id:'page1',url:'https://catalog.example/checklist',title,text,text_origin:'retrieved_page'});
const sportsRef=()=>reference('2025-26 Panini Select Road to FIFA World Cup 2026 Soccer Checklist','2025-26 Panini Select Road to FIFA World Cup 2026 Soccer Checklist\nBase Terrace\n21 Victor Boniface – Nigeria\nBase Terrace Parallels\nGreen /5\nGold /10');
const validate=(p,refs)=>V.validate(p,{candidates:V.checklistEntries186(p,refs).map(e=>({unit:'single',decision:'match',same_unit:true,identity_level:'exact',physical_ambiguity:true,ambiguity_scope:'variant',variant_status:'unresolved',matches:[],conflicts:[],fields:e.fields})),detail_needed_from:'none'},refs);
test('real Machamp selects Pokemon catalogues from typed game even with WOTC and TCG metadata',()=>{
 const p=photo('machamp'),route=V.cataloguePlan185(p);
 assert.equal(route.kind,'card_checklist');assert.match(route.query,/Pokemon.*Base Set.*Machamp.*#8\/102/);assert.match(route.query,/serebii/);
 const c=p.photo_clues.find(c=>c.role==='copyright');c.certainty='clear';
 assert.match(V.cataloguePlan185(p).query,/1999/);assert.equal(V.cardKeyFacts(p).date.value,null);
});
test('real Cloyster label searches card identity and specialist catalogues in the first query',()=>{
 const p=photo('cloyster'),q=V.cataloguePlan185(p).query;
 assert.match(q,/2002.*EXPEDITION.*Cloyster.*#8/i);assert.doesNotMatch(q,/0014436473|cert verification|##/);assert.match(q,/serebii/);assert.match(q,/beckett/);
});
test('real slab closes from a scoped catalogue title and matching row without a certificate page',()=>{
 const p=photo('cloyster'),r=reference('2002 Expedition Checklist','2002 Expedition Checklist\n8 Cloyster Holo Rare\n9 Another Creature');
 const out=V.slabClosure185(V.auditIdentity(p),p,[r]);assert.equal(out.market_ready,true);assert.equal(E.apply(out,p.pokemon_printing,1).printing_check.complete,true);assert.equal(out.slab_verification.certificate_verified,false);
 for(const r of [reference('2003 Expedition Checklist','2003 Expedition Checklist\n8 Cloyster'),reference('2002 Expedition Checklist','2002 Expedition Checklist\n9 Cloyster\n8 Another Creature'),reference('2002 Expedition Cloyster #9'),reference('2002 Other Series Cloyster #8')])assert.notEqual(V.slabClosure185(V.auditIdentity(p),p,[r]).market_ready,true,r.text);
});
test('real unreadable vertical serial triggers rotated original side and remains unknown until read',()=>{
 const p=photo('boniface'),req=V.detailRequests(p,[],2),r=req.find(c=>c.role==='serial');
 assert.equal(r.serial_search,true);assert.equal(r.image_index,2);assert.equal(V.detailRegion(r,p).region.certain,false);
 assert.equal(V.serialEvidence184(p),null);
 const out=V.applyPhotoDetails(p,[{clue_index:r.clue_index,text:'2/5',role:'serial',certainty:'clear'}],req,[]).value;
 assert.equal(V.serialEvidence184(out).value,'2/5');assert.equal(V.cardKeyFacts(out).number.value,'21');
 assert.equal(V.serialEvidence184(V.applyPhotoDetails(p,[{clue_index:r.clue_index,text:'2/5',role:'serial',certainty:'uncertain'}],req,[]).value),null);
});
test('same manufacturer or tournament cannot admit different products and empty storefronts',()=>{
 const p=photo('boniface'),r=sportsRef();
 for(const s of [reference('2025-26 Panini National Treasures Road to FIFA World Cup Soccer'),reference('FIFA World Cup 2026 Official Sticker Collection Update Set'),reference('2024-25 Panini Select Road to FIFA World Cup Soccer'),reference('2025-26 Panini Select Road to FIFA World Cup 2026 Victor Boniface #22')])assert.equal(V.catalogueScope186(p,s).eligible,false,s.title);
 assert.equal(V.catalogueScope186(p,r,true).eligible,true);
 assert.equal(V.catalogueScope186(p,{...r,text:'Shop all sports trading cards. Sitemap.'},true).eligible,false);
 const ranked=V.rankSources([...reports.boniface.sources,{...r,url:'https://checklistinsider.com/select',snippet:r.text}],p);
 assert.ok(ranked.some(x=>x.url.includes('checklistinsider')));assert.equal(ranked.some(x=>/national-treasures|sticker-collection/.test(x.url)),false);
});
test('literal checklist row confirms core and photographed fraction plus scoped table closes parallel',()=>{
 const p=photo('boniface'),serial=p.photo_clues.find(c=>c.role==='serial');serial.text='2/5';serial.certainty='clear';
 const r=sportsRef(),core=validate(p,[r]);assert.equal(core.catalogue_core_verified,true);assert.equal(core.market_ready,false);
 const out=V.specificationClosure184(core,p,{entries:[{reference_id:r.id,unit:'single',scope:'base',variant:'Green',section_quote:'Base Terrace Parallels\nGreen /5\nGold /10',variant_quote:'Green /5'}]},[r]);
 assert.equal(out.market_ready,true);assert.equal(out.serial_number,'2/5');assert.equal(out.variant,'Green');
});
test('checklist entry cannot borrow another number, year, row or fabricated quote',()=>{
 const p=photo('boniface'),r=sportsRef();
 for(const bad of [{...r,text:r.text.replace('21 Victor','22 Victor')},{...r,title:r.title.replace('2025-26','2024-25'),text:r.text.replaceAll('2025-26','2024-25')},{...r,text:r.text.replace('21 Victor Boniface – Nigeria','21 Another Player\n22 Victor Boniface – Nigeria')},{...r,text:'21 Victor Boniface – Nigeria'}])assert.notEqual(validate(p,[bad]).catalogue_core_verified,true,bad.text);
});
test('a matching checklist cannot transfer an autograph or another level row into the base identity',()=>{
 const p=photo('boniface'),r=sportsRef();
 for(const section of ['Mezzanine','Terrace Autographs','Signatures']){
  const bad={...r,text:r.text.replace('Base Terrace\n21',section+'\n21')};
  assert.notEqual(validate(p,[bad]).catalogue_core_verified,true,section);
 }
 assert.equal(V.catalogueScope186(p,reference('2025-26 Panini Select Serie A Soccer Checklist')).eligible,false);
});
test('real Machamp crop ordinal is mapped only when all crops belong to the same original',()=>{
 const p=reports.machamp.printingReply,m=E.remapCropImages186(p,[1,1]);
 assert.equal(m.printing.shadow_image,1);assert.equal(m.printing.copyright_image,1);assert.equal(E.evaluate(m.printing,1).complete,true);assert.ok(E.evaluate(m.printing,1).labels.includes('Shadowless'));
 assert.equal(E.remapCropImages186({...p,shadow_image:3},[1,2]).printing.shadow_image,3);
 assert.equal(E.evaluate(E.remapCropImages186({...p,shadow_image:3},[1,2]).printing,2).complete,false);
});
test('2002 post-Neo western releases exclude stamps without excluding Neo Destiny or Japanese prints',()=>{
 const p=photo('cloyster'),identity={...p,catalogue_core_verified:true,core_identity:{fields:[{field:'year',value:'2002',origin:'catalogue',number_kind:'year'}]}};
 assert.equal(E.evaluate(E.cataloguePrinting(identity,p.pokemon_printing),1).complete,true);
 assert.equal(E.cataloguePrinting({...identity,family:'Neo Destiny'}, {...p.pokemon_printing,set_name:'Neo Destiny'}).stamp_policy,undefined);
 assert.equal(E.cataloguePrinting(identity,{...p.pokemon_printing,language:'Japanese'}).stamp_policy,undefined);
 assert.equal(E.evaluate(E.cataloguePrinting(identity,{...p.pokemon_printing,first_edition_stamp:'present',stamp_text:'1st Edition'}),1).contradiction,true);
});
