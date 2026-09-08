/* Regression contracts for the build-195 failures. All sources and observations
   below are controlled fixtures: these tests do not measure fresh OCR accuracy. */
const {test}=require('node:test'),assert=require('node:assert/strict');
const E=require('../src/main/assets/catalogue-engine'),C=require('../src/main/assets/catalogue-sources');
const src={url:'https://www.beckett.com/checklist-test'};
function read(domain,fields){const l=new E.Ledger({domain,kind:'card'});for(const [field,value] of Object.entries(fields))l.add(field,value,{certainty:'clear',image_index:1});return l;}
const card=(patch={})=>({subject:'Example',number:'8/102',family:'Base Set',year:'1999',language:'en',grounded:true,source:src,variants:[{name:'Holo',finish:'holo'}],...patch});
const sport=()=>read('sports',{subject:'Example',collector_number:'280',season:'2018-19',product:'Prizm',border_color:'green'});
const sportsCard=(patch={})=>card({family:'Prizm',number:'280',year:'2018-19',subset:'Base',variants:[],...patch});
test('evolution and type text retain evidence but cannot veto the actual set',()=>{
 const l=read('pokemon',{subject:'Example',collector_number:'8/102',language:'en',product:'Evolves from Another; Stage 2',finish:'holo',stamp:'present'});
 const r=E.reduce(l,[card(),card({year:''})]);assert.equal(r.core_identity.status,'confirmed');assert.deepEqual(r.variant_resolution.pending,['shadow']);assert.equal(r.market_ready,false);
 assert.deepEqual(E.keyValues(l).products,[]);assert.equal(l.values('card_type')[0].raw,'Evolves from Another; Stage 2');assert.equal(l.values('card_type')[0].reported_field,'product');
 l.add('product','Jungle',{certainty:'clear',image_index:1});assert.equal(E.reduce(l,[card()]).core_identity.status,'partial');
});
test('HP keeps its semantic role but sports statistics cannot become collector numbers or serials',()=>{
 const l=E.ingestVision(new E.Ledger({domain:'pokemon'}),{observations:[{field:'hp',text:'100 HP',zone:'statistics',certainty:'clear',image_index:1},{field:'serial',text:'2/5',zone:'statistics',certainty:'clear',image_index:1},{field:'collector_number',text:'42',zone:'statistics',certainty:'clear',image_index:1}]});
 assert.equal(l.pick('hp').value,'100');assert.deepEqual(E.keyValues(l).serials,[]);assert.deepEqual(E.keyValues(l).numbers,[]);
});
test('incomplete metadata attaches only to one compatible complete entry, independent of order',()=>{
 const l=read('pokemon',{subject:'Example',collector_number:'8',language:'en'}),full=card(),partial=card({year:'',number:'8'});
 for(const entries of [[full,partial],[partial,full],[partial,full,partial,full]]){const g=E.candidateGroups(l,entries);assert.equal(g.length,1);assert.equal(g[0].entry.year,'1999');assert.equal(g[0].entry.number,'8/102');assert.equal(g[0].score,75);}
 const groups=E.candidateGroups(l,[full,partial,card({year:'2000'})]);assert.equal(groups.length,3);assert.equal(E.reduce(l,[full,partial,card({year:'2000'})]).core_identity.status,'partial');
 assert.equal(E.candidateGroups(l,[card(),card({number:'8/110'})]).length,2);
});
test('parallel product aliases require a matching attributed single-card record',()=>{
 const l=sport(),a=sportsCard({variants:[{name:'Ruby Wave',colors:['red'],patterns:['wave']}]}),b=sportsCard({family:'Panini Prizm Prizms Ruby Wave',subset:'Prizms Ruby Wave'});
 for(const entries of [[a,b],[b,a],[b,a,b,a]]){assert.equal(E.candidateGroups(l,entries).length,1);assert.equal(E.reduce(l,entries).core_identity.status,'confirmed');assert.equal(E.reduce(l,entries).market_ready,false);}
 assert.equal(E.candidateGroups(l,[a,{...b,source:{url:'https://www.beckett.com/unrelated'}}]).length,2);
 assert.equal(E.candidateGroups(l,[a,{...b,family:'Prizm Draft Picks'}]).length,2);
 assert.equal(E.candidateGroups(l,[a,{...a,subset:'Fireworks'}]).length,2);
});
test('checklist sections preserve their own variants, including lists after rows and unknown inserts',()=>{
 const l=sport(),page={url:src.url,title:'2018-19 Panini Prizm Basketball Checklist',text:'Base Checklist\n280 Example\nParallels:\n• Green\nFireworks Checklist\n280 Example\nParallels:\n* Gold /5\n2018-19 Panini Prizm Basketball Team Checklist\n280 Example'};
 const records=C.records([page],l).filter(e=>e.number==='280');assert.equal(records.length,3);
 assert.deepEqual(records[0].variants.map(v=>v.name),['Green']);assert.equal(records[1].subset,'Fireworks Checklist');assert.deepEqual(records[1].variants.map(v=>v.name),['Gold']);assert.deepEqual(records[2].variants,[]);
});
test('unavailable or incompatible catalogues do not request a replacement photo',()=>{
 let r=E.reduce(sport(),[]);assert.equal(r.next_photo_request,null);assert.equal(r.market_ready,false);
 r=E.reduce(sport(),[sportsCard({variants:[{name:'Blue Ice',colors:['blue'],patterns:['ice']}]})]);assert.equal(r.core_identity.status,'confirmed');assert.equal(r.next_photo_request,null);assert.equal(r.assistance_state,'source_detail_needed');
 const l=sport();l.add('serial','unclear',{certainty:'uncertain',image_index:1});r=E.reduce(l,[sportsCard()]);assert.match(r.next_photo_request,/numerazione/);
});
test('documented Green can resolve from physical evidence; an undocumented green cannot',()=>{
 const l=sport();assert.equal(E.reduce(l,[sportsCard()]).market_ready,false);
 const r=E.reduce(l,[sportsCard({variants:[{name:'Green',colors:['green'],unnumbered:true}]})]);assert.equal(r.market_ready,true);assert.equal(r.variant,'Green');assert.equal(r.next_photo_request,null);
});
function onepiece(){return read('onepiece',{subject:'Example',collector_number:'P-110',language:'en',product:'CHARACTER',subset:'Straw Hat Crew',text:'4th Anniversary'});}
test('One Piece prioritizes exact code and promo text; category and traits are not product constraints',()=>{
 const l=onepiece();assert.deepEqual(E.keyValues(l).products,[]);assert.equal(l.values('card_traits')[0].value,'Straw Hat Crew');assert.match(E.query(l),/^One Piece card "P-110"/);assert.match(E.query(l),/4th Anniversary/);assert.doesNotMatch(E.query(l),/CHARACTER|site:/);
 assert.equal(C.requestPlan(l).length,2);assert.ok(C.requestPlan(l).every(p=>p.url.endsWith('freewords=P-110')));assert.equal(C.requestPlan(l)[1].fallback,true);
 assert.equal(E.sourceTrusted('https://www.tcgplayer.com/product/1','onepiece'),true);assert.equal(E.sourceTrusted('https://tcgplayer.com.attacker.example/product/1','onepiece'),false);assert.equal(E.sourceTrusted('https://www.tcgplayer.com/product/1','pokemon'),false);
});
test('secondary sources still require literal entry-level evidence',()=>{
 const l=onepiece(),url='https://www.tcgplayer.com/product/1',reply={entries:[{subject:'Example',number:'P-110',family:'Promo',source_url:url,entry_quote:'Example P-110 Promo',proof:{subject:'Example',number:'P-110',family:'Promo'},variants:[]}]};
 assert.equal(C.groundedExtraction(reply,[{url,text:'Example P-110 Promo'}],l).accepted.length,1);assert.equal(C.groundedExtraction(reply,[{url,text:'Example P-111 Promo'}],l).accepted.length,0);assert.equal(C.groundedExtraction(reply,[],l).accepted.length,0);
});
test('same-code Bandai editions share their base but retain variants and require visual proof',()=>{
 const l=onepiece(),url='https://en.onepiece-cardgame.com/cardlist/',text='P-110 | P | CHARACTER\nExample\nCard Set(s)\nPromo A\nP-110 | P | CHARACTER\nExample (Parallel)\nCard Set(s)\nPromo B';
 const records=C.records([{url,text,image_details:[{url:'https://en.onepiece-cardgame.com/images/P-110.png'}]}],l),r=E.reduce(l,records);
 assert.equal(E.candidateGroups(l,records).length,1);assert.equal(r.core_identity.status,'confirmed');assert.equal(r.market_ready,false);assert.equal(r.variant_resolution.variant_candidates.length,2);assert.ok(records.every(e=>e.variants.every(v=>!v.image_url)));
 l.add('catalogue_variant','Promo B · Parallel Artwork',{certainty:'clear',image_index:1,reference_source:url});assert.equal(E.reduce(l,records).market_ready,true);
});
test('foreign comparison evidence cannot close another source variant',()=>{
 const l=onepiece();l.add('catalogue_variant','Promo',{certainty:'clear',image_index:1,reference_source:'https://www.beckett.com/other'});
 const r=E.reduce(l,[card({number:'P-110',family:'Promo',variants:[{name:'Promo',visual_required:true}]})]);assert.equal(r.market_ready,false);
});
test('missing language remains unresolved at attribute level without a system-language default',()=>{
 const l=read('pokemon',{subject:'Example',collector_number:'8/102',finish:'holo'}),r=E.reduce(l,[card()]);assert.equal(r.core_identity.status,'confirmed');assert.equal(r.market_ready,false);assert.ok(r.variant_resolution.pending.includes('language'));assert.equal(r.language,'');assert.ok(E.recoveryRequests(l,r).some(r=>r.field==='language'));
});
test('Pokemon schema and evidence cannot leak into sports or One Piece decisions',()=>{
 for(const domain of ['sports','onepiece']){const base={domain,kind:'card',pokemon_printing:{is_pokemon:false,first_edition_stamp:'present',stamp_image:1,stamp_location:'wrong domain'}};const l=E.ingestVision(new E.Ledger(base),base),r=E.reduce(l,[]);assert.equal(l.values('stamp').length,0);assert.equal(JSON.parse(JSON.stringify(r)).pokemon_printing,undefined);}
});

test('a truncated checklist cannot donate the following unnamed section variants to base cards',()=>{
 const l=sport(),page={url:src.url,title:'2018-19 Panini Prizm Basketball Checklist',text:'280 Example\nChecklist Top\nPrizms Parallels:\nChoice Green /8\n3 Example\n### Fireworks Checklist\n280 Example\nGreen'};
 const records=C.records([page],l);assert.deepEqual(records.find(e=>e.number==='280'&&e.subset==='Base').variants,[]);assert.equal(records.find(e=>e.number==='3').subset,'Unspecified checklist section');
});
