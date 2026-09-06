// Existing diagnostic replays and synthetic general-product checks; no live recognition.
const {test}=require('node:test'),assert=require('node:assert/strict');
const W=require('../src/main/assets/identity-web.js'),E=require('../src/main/assets/editions.js'),P=require('../src/main/assets/identity-policy.js');
const fixtures=require('./fixtures/build162-regressions.json');
const firstWeb=f=>f.phases.find(p=>p.stage==='Web identificazione 1').result;
test('Vileplume replay resolves inferred Base Set to Jungle and uses the resolved set for printing context',()=>{
 const f=fixtures.vile,b=structuredClone(f.firstVision),out=E.apply(W.result(b,firstWeb(f),f.sources,1),b.pokemon_printing,1);
 assert.equal(out.market_ready,true);assert.equal(out.family,'Jungle');assert.equal(out.printing_check.shadow,'not_applicable');
 assert.equal(out.printing_check.stamp,'present');assert.equal(b.pokemon_printing.set_name,'Base Set');
 assert.deepEqual(out.pokemon_printing,{...b.pokemon_printing,set_name:'Jungle'});
});
test('Politoed replay accepts the first source despite omitted Italian text and retains Italian in market query',()=>{
 const f=fixtures.poli,b=f.firstVision,out=E.apply(W.result(b,firstWeb(f),f.sources,1),b.pokemon_printing,1);
 assert.equal(out.market_ready,true);assert.equal(out.family,'Skyridge');assert.match(out.normalized_query,/Italiano/);
 assert.deepEqual(W.recoveryRegions(b,out),[]);assert.equal(P.retry(b,firstWeb(f),out),false);
});
test('Kobe photographic closure stays one Vision and requires no recovery',()=>{
 const f=fixtures.kobe,x=P.close(f.firstVision,2);assert.equal(x.core_identity_confirmed,true);assert.deepEqual(P.cropPlan(x,2).regions,[]);
});
test('a physically located set conflict is retained, unlike an unlocated inferred family',()=>{
 const f=fixtures.vile,b=structuredClone(f.firstVision);b.layout_signature.push({term:'Base Set',position:'printed label',relation:'label'});
 const out=W.result(b,firstWeb(f),f.sources,1);assert.equal(out.market_ready,false);assert.equal(out.web_checks[0].rejection,'physical_conflict');
});
test('box query includes printed season, line and entire quantity without guessing a format',()=>{
 const f=fixtures.box,q=W.query(P.close(f.firstVision,1),'',1);
 assert.match(q,/2025\/26/);assert.match(q,/UPDATE SERIES/i);assert.match(q,/1 AUTOGRAPH CARD EVERY 3 BOXES/);assert.doesNotMatch(q,/Hobby|Mega|Blaster/i);
});
test('retail qualifier does not invalidate an exact model source; contradictory counts request one precise crop',()=>{
 const f=fixtures.box,out=W.result(f.firstVision,firstWeb(f),f.sources,1);
 assert.equal(out.web_checks.find(c=>c.variant==='Mega Box / retail').evidence_sources.length,1);
 assert.equal(out.market_ready,false);assert.equal(W.recoveryRegions(f.firstVision,out).length,1);
 assert.equal(W.configuration('1 AUTOGRAPH CARD EVERY 3 BOXES').autographRatio,1/3);
 assert.equal(W.configuration('one autograph per box').autographRatio,1);
});
test('after a literal correction, second query selects the candidate matching the quantity, independent of list order',()=>{
 const f=fixtures.box,b=structuredClone(f.firstVision),r=structuredClone(firstWeb(f));
 b.distinctive_terms=b.distinctive_terms.map(t=>t==='1 AUTOGRAPH CARD EVERY 3 BOXES'?'1 AUTOGRAPH CARD EVERY BOX':t);
 b.layout_signature=b.layout_signature.map(t=>t.term==='1 AUTOGRAPH CARD EVERY 3 BOXES'?{...t,term:'1 AUTOGRAPH CARD EVERY BOX'}:t);
 // Old model commentary describes the superseded reading. It cannot remain a new physical conflict.
 r.candidates.reverse();const out=W.result(b,r,f.sources,1),plan=W.searchPlan(b,'',2,{...r,candidates:out.web_checks});
 assert.match(plan.focus,/Hobby Box/);assert.match(plan.query,/1 AUTOGRAPH CARD EVERY BOX/);assert.equal(P.retry(b,r,out),true);
});
test('a clear quantity conflict is not erased to make the user-corrected box label pass',()=>{
 const f=fixtures.box,r=structuredClone(firstWeb(f));r.candidates=r.candidates.filter(c=>/Hobby Box/.test(c.model));
 r.candidates[0].relation='exact_product';r.candidates[0].conflicts=[];
 const out=W.result(f.firstVision,r,f.sources,1);assert.equal(out.market_ready,false);assert.equal(out.web_checks[0].rejection,'configuration_conflict');
});
test('quantity with card word, unit aliases and explicit possibility are normalized without a product-format rule',()=>{
 assert.deepEqual(W.configuration('1 AUTOGRAPH CARD EVERY 3 BOXES'),{autographRatio:1/3});
 assert.equal(W.compareSpecifications('2 batteries per kit','2 batteries per kit').matched.length,1);
 assert.equal(W.compareSpecifications('1 autograph per box','Look for 1 autograph per box').matched.length,0);
 assert.equal(W.compareSpecifications('500 mA','0.5 A').matched.length,1);
 assert.equal(W.compareSpecifications('24 V 30 W','12 V 30 W').different.length,1);
});
test('general electronics query uses label specifications; a documented voltage mismatch remains binding',()=>{
 const b={kind:'object',brand:'Aster',family:'Pump',model:'',distinctive_terms:['FLOW','24 V','30 W','MODEL PX-7'],layout_signature:[],identifier_observations:[{text:'PX-7',role:'model',legibility:'clear',image_index:1}]};
 const url='https://manufacturer.example/products/px-7',quote='Aster Pump PX-7 FLOW 12 V 30 W',c={brand:'Aster',family:'Pump',model:'PX-7',variant:'',product_code:'PX-7',collector_number:'',relation:'exact_product',conflicts:[],sources:[{url,quote}]};
 assert.match(W.query(b,'',1),/24 V/);assert.match(W.query(b,'',1),/PX-7/);
 const out=W.result(b,{candidates:[c]},[{url,snippet:quote}],1);assert.equal(out.market_ready,false);assert.equal(out.web_checks[0].rejection,'configuration_conflict');
});
test('structured missing evidence is not a conflict; a real cited difference still blocks',()=>{
 const f=fixtures.poli,r=structuredClone(firstWeb(f));r.candidates[0].conflicts=[{field:'text',observed:'Ranabalzo',source:'No text',state:'missing',source_value:'',source_url:''}];
 assert.equal(W.result(f.firstVision,r,f.sources,1).market_ready,true);
 const c=r.candidates[0];c.sources[0].quote='Trading card Politoed Skyridge H23/H32: attack Different Attack';
 c.conflicts=[{field:'text',observed:'Ranabalzo',source:'Different Attack',state:'different',source_value:'Different Attack',source_url:c.sources[0].url}];
 assert.equal(W.result(f.firstVision,r,f.sources,1).market_ready,false);
});
