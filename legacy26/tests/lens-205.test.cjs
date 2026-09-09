/* Real recorded SearchApi responses; photo facts below are labelled expectations/recorded readings.
   These test selection and evidence rules, not fresh OCR/model accuracy. No paid API calls. */
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),zlib=require('zlib'),path=require('path');
const E=require('../src/main/assets/catalogue-engine'),L=require('../src/main/assets/lens-policy');
const D=JSON.parse(zlib.gunzipSync(fs.readFileSync(path.join(__dirname,'fixtures/lens-205-recorded.json.gz'))));
function photo(domain,values,uncertain={}){const l=new E.Ledger({domain,kind:['generic','sealed'].includes(domain)?'object':'card',category:domain==='generic'?'Telecomando':'',object_unit:'single'});for(const [field,value] of Object.entries(values))l.add(field,value,{source:'vision',certainty:'clear',image_index:1,zone:field==='copyright'?'footer':'front'});for(const [field,value] of Object.entries(uncertain))l.add(field,value,{source:'vision',certainty:'uncertain',image_index:1});return l;}
function evaluate(name,l){const out=L.select(D[name],l);assert.equal(out.length,20);assert.ok(out.every(c=>c.identity_verified===false));return out;}
test('Glurak: Box Topper 9/12 survives, 146/144 and #146 are rejected; German remains required',()=>{
 const l=photo('pokemon',{subject:'Glurak',collector_number:'9/12',copyright:'©2003',language:'de',finish:'holo'});l.add('subject_alias','Charizard',{level:'inferred',certainty:'uncertain'});
 const out=evaluate('glurak',l);assert.equal(out[0].eligible,false);assert.ok(out[0].reasons.includes('different_number'));assert.equal(out[1].eligible,true);assert.equal(out[1].attributes.format,'Box Topper');assert.ok(out[1].missing.includes('language'));assert.ok(out.filter(c=>c.attributes.numbers.includes('146/144')).every(c=>!c.eligible));
 const entry={subject:'Glurak',family:'Skyridge',number:'9/12',year:'2003',language:'de',subset:'Box Topper',grounded:true,source:{url:'https://www.pricecharting.com/game/pokemon-skyridge/charizard-box-topper-9'},variants:[{name:'Holo',finish:'holo'}]};const r=E.reduce(l,[entry]);assert.match(r.title,/Box Topper/);assert.match(r.normalized_query,/Box Topper/);assert.equal(r.card_identity.subset_origin,'catalogue');
});
test('Dragonite: 9/165 Italian Holo; 43/165, Reverse and Japanese are incompatible',()=>{
 const l=photo('pokemon',{subject:'Dragonite',collector_number:'9/165',copyright:'©2002',language:'it',finish:'holo'}),out=evaluate('dragonite',l);
 assert.equal(out[0].eligible,false);assert.ok(out[0].reasons.includes('different_number'));assert.ok(out[0].reasons.includes('different_finish'));assert.equal(out[3].eligible,true);assert.ok(out[3].matches.includes('language'));assert.equal(out[12].eligible,false);assert.equal(out[17].eligible,false);
});
test('Luffy: English P-110 4th Anniversary keeps exact prefix; Japanese candidate rejected',()=>{
 const l=photo('onepiece',{subject:'Monkey.D.Luffy',collector_number:'P-110',language:'en',edition_text:'4th Anniversary'}),out=evaluate('luffy',l);
 assert.equal(out[0].eligible,false);assert.ok(out[0].reasons.includes('different_language'));assert.equal(out[13].eligible,true);assert.ok(out[13].matches.includes('number'));assert.ok(out[13].matches.includes('anniversary'));
 assert.equal(L.evaluate({title:'Monkey.D.Luffy OP-110 English 3rd Anniversary',snippet:''},l).eligible,false);
});
test('Machamp: 8/102 and stamp read on photo; shadow cannot come from a Lens title',()=>{
 const l=photo('pokemon',{subject:'Machamp',collector_number:'8/102',copyright:'©1999',language:'en',stamp:'present',finish:'holo'},{shadow:'unclear'}),out=evaluate('machamp',l);assert.equal(out[1].eligible,true);assert.equal(out[2].eligible,false);assert.equal(l.pick('shadow'),null);
 const entry={subject:'Machamp',family:'Base Set',number:'8/102',year:'1999',language:'en',grounded:true,source:{url:'https://api.tcgdex.net/v2/en/cards/base1-8'},variants:[{name:'Holo',finish:'holo'}],printing_options:['first_edition']};const r=E.reduce(l,[entry]);assert.equal(r.market_ready,false);assert.ok(r.variant_resolution.pending.includes('shadow'));
 l.add('shadow','present',{source:'vision_detail',certainty:'clear',image_index:1});assert.equal(L.select(D.machamp,l)[11].eligible,false);
});
test('Vileplume: 15/64 Holo First Edition survives; 31/64 and Unlimited rejected',()=>{
 const l=photo('pokemon',{subject:'Vileplume',collector_number:'15/64',copyright:'©1999',language:'en',finish:'holo',stamp:'present'}),out=evaluate('vileplume',l);
 assert.equal(out[0].eligible,false);assert.equal(out[1].eligible,true);assert.equal(out[6].eligible,false);assert.equal(out[14].eligible,false);
});
test('Kobe: name and set season 1997-98 and #81 stay photo facts; sports table is not a serial',()=>{
 const l=photo('sports',{subject:'Kobe Bryant',collector_number:'81',season:'1997-98',product:'Metal Universe',language:'en'}),out=evaluate('kobe',l);
 assert.equal(out[0].eligible,true);assert.ok(out[0].matches.includes('number'));assert.ok(out[0].matches.includes('year'));assert.equal(l.pick('serial'),null);assert.equal(l.pick('season').source,'vision');assert.equal(l.pick('season').value,'1997-98');
 E.ingestOcr(l,[{state:'ok',image_index:2,text:'SEASON TEAM FG% PPG',lines:[{text:'539',x:.5,y:.8,width:.1,height:.02}]}]);assert.equal(l.pick('serial'),null);
});
test('Pelé/Garrincha: Rekord Journal 1958 is a source candidate, never authenticity proof',()=>{
 const l=photo('sports',{subject:'Pele'}),out=evaluate('pele',l),candidate=out.find(c=>/1958 Swedish Rekord Journal Soccer Garrincha Pele/.test(c.title));assert.ok(candidate.eligible);assert.equal(l.pick('season'),null);assert.equal(l.pick('product'),null);assert.equal(candidate.identity_verified,false);assert.equal(candidate.authenticity,undefined);
});
test('Topps box: product/season separate from exact box configuration',()=>{
 const l=photo('sealed',{brand:'Topps',product:'Chrome Update Series',season:'2025-26',configuration:'1 autograph in every box'}),out=evaluate('topps',l);assert.equal(out[1].eligible,false);assert.ok(out[6].eligible);
 const r=E.reduce(l,[]);assert.equal(r.core_identity.status,'confirmed');assert.equal(r.market_ready,false);assert.ok(r.variant_resolution.pending.includes('configuration'));
});
test('Philips remote: TV result discarded, brand retained, marketplace models stay hypotheses',()=>{
 const l=photo('generic',{brand:'Philips',product:'Remote control'}),out=evaluate('philips',l);assert.equal(out[0].eligible,false);assert.equal(out[1].eligible,true);
 const entry={subject:'Remote control YKF423-007',family:'Philips Remote control',number:'YKF423-007',grounded:true,entry_quote:'Philips Remote control YKF423-007',source:{url:'https://catalogue.example/remote'}};const r=E.reduce(l,[entry]);assert.equal(r.brand,'Philips');assert.equal(r.market_ready,false);assert.ok(r.missing_information.includes('Codice modello'));assert.equal(l.pick('model_code'),null);
});
test('Orbit: multiple candidates considered and 94026 remains uncertain without a physical label',()=>{
 const l=photo('generic',{}, {brand:'Orbit',model_code:'94026'}),out=evaluate('orbit',l);assert.ok(out.some(c=>c.eligible&&/Orbit/i.test(c.title)));assert.equal(l.pick('model_code'),null);
 const entry={subject:'Orbit 94026',family:'Orbit Control Star',number:'94026',grounded:true,entry_quote:'Orbit Control Star 94026',source:{url:'https://catalogue.example/orbit'}};assert.equal(E.reduce(l,[entry]).market_ready,false);assert.equal(E.reduce(l,[entry]).normalized_query,'');
});
test('an actual model code inside the photographed product name still closes a generic object',()=>{
 const l=photo('generic',{brand:'Example',product:'Example ZX-500'}),entry={subject:'Example ZX-500',family:'Example ZX-500',number:'ZX-500',grounded:true,entry_quote:'Example ZX-500 water pump. Model Example ZX-500.',source:{url:'https://example.com/catalogue/zx-500'}};
 assert.equal(E.reduce(l,[entry]).market_ready,true);assert.equal(E.reduce(l,[entry]).core_identity.fields.find(f=>f.field==='product').origin,'photo');
 const uncertain=photo('generic',{brand:'Example'},{product:'Example ZX-500'});assert.equal(E.reduce(uncertain,[entry]).market_ready,false);
});
test('Mewtwo V cannot be translated to Mewtwo ex, including a conflicting identity-band packet',()=>{
 const l=photo('pokemon',{subject:'超梦V',collector_number:'135/127',copyright:'©2024',language:'zh'});E.applyIdentityBands202(l,{observations:[{field:'subject',text:'超梦',alternatives:['Mewtwo ex','Mewtwo'],certainty:'clear',image_index:1,region:{image_index:1,x:.1,y:.1,width:.3,height:.1}}]});assert.deepEqual(E.pokemonAliases204(l),['Mewtwo V']);assert.match(E.query(l),/Mewtwo V/);assert.doesNotMatch(E.query(l),/Mewtwo ex/);
});
test('uncertain number cannot exclude another candidate or become a confirmed identity',()=>{
 const l=photo('pokemon',{subject:'Dragonite',language:'it'},{collector_number:'9/165'});assert.equal(L.select(D.dragonite,l)[0].eligible,true);assert.equal(l.pick('collector_number'),null);
});
test('normalization caps to 20, deduplicates, rejects unsafe links and never treats score as identity',()=>{
 const rows=Array.from({length:30},(_,i)=>({title:'Card '+i,link:'https://catalogue.example/'+i,score:100}));const out=L.normalize({visual_matches:[{title:'invalid',link:'javascript:alert(1)'},...rows,rows[0]]});assert.equal(out.length,20);assert.ok(out.every(c=>c.identity_verified===false));assert.equal(out[0].score,undefined);
});
test('timeouts, empty results and exhausted credits provide explicit fallback reasons',()=>{
 for(const state of ['timeout','empty_results','quota_exhausted','authentication_failed','not_configured'])assert.equal(L.fallbackReason({state}),state);assert.equal(L.fallbackReason({state:'ok'}),'identity_not_verified');
});
test('early Latin OCR is retained and Japanese OCR runs once after script recognition',async()=>{
 const vm=require('vm'),source=fs.readFileSync(path.join(__dirname,'../src/main/assets/visual-runtime.js'),'utf8'),calls=[],ctx={};
 const sandbox={window:{FlipCheckGoogle:{ocrAvailable:()=>true}},targetPhotos169:async()=>[{data:'recorded-carrier',meta:{imageIndex:1}}],directCall165:async(action,p)=>{calls.push(p.script);return {state:'ok',text:p.script,lines:[]};},guard164(){}};
 vm.createContext(sandbox);vm.runInContext(source.slice(source.indexOf('async function readPhotoOcr174('),source.indexOf('function detailRegion174('))+'\nthis.read=readPhotoOcr174;',sandbox);
 await sandbox.read({},ctx);await sandbox.read({language:'ja'},ctx);await sandbox.read({language:'ja'},ctx);assert.deepEqual(calls,['latin','japanese']);assert.equal(ctx.photoOcr.length,2);assert.ok(ctx.photoOcr.every(r=>r.origin==='on_device_photo_ocr'));
});

test('an uncertain explicit language cannot be promoted by the Vision summary or Lens',()=>{
 const packet={domain:'pokemon',kind:'card',language:'en',observations:[{field:'language',text:'en',certainty:'uncertain',image_index:1},{field:'subject',text:'Dragonite',certainty:'clear',image_index:1},{field:'collector_number',text:'9/165',certainty:'clear',image_index:1}]};const l=E.ingestVision(new E.Ledger(packet),packet);assert.equal(l.pick('language'),null);L.select(D.dragonite,l);assert.equal(l.pick('language'),null);assert.equal(E.pokemonKeys204(l).complete,false);
});
