// Replay of supplied build-161 diagnostic JSON. No new image recognition or paid API calls.
const {test}=require('node:test'),assert=require('node:assert/strict');
const P=require('../src/main/assets/identity-policy.js'),W=require('../src/main/assets/identity-web.js');
const fixtures=require('./fixtures/build161-regressions.json');
const web=(name,n=0)=>fixtures[name].phases.filter(p=>p.stage.startsWith('Web'))[n].result;
test('Vileplume: first photo result closes without crops or web; slab grading is optional',()=>{
 const f=fixtures.wilo,x=P.close(f.firstVision,f.uploadedImageCount);
 assert.equal(x.core_identity_confirmed,true);assert.equal(x.variant,'1st Edition Holo');
 assert.equal(P.reference(x).status,'consistent');assert.deepEqual(P.cropPlan(x,1).regions,[]);
 assert.deepEqual(x.missing_information,[]);assert.equal(x.next_photo_request,null);
 assert.ok(x.additional_information.some(s=>/slab/i.test(s)));
});
test('Politoed: clear H23/H32 skips rereading, routes the Aquapolis contradiction to web',()=>{
 const f=fixtures.poli,x=P.close(f.firstVision,1);
 assert.equal(x.core_identity_confirmed,undefined);assert.equal(x.identity_route.reason,'catalogue_disagreement');
 assert.equal(x.family,'Aquapolis');assert.deepEqual(P.cropPlan(x,1).regions,[]);
 const q=W.query(x,'',1);assert.match(q,/Skyridge/);assert.doesNotMatch(q,/Aquapolis|E - 62/);
 const result=W.result(x,web('poli'),f.sources,1);
 assert.equal(result.market_ready,true);assert.equal(result.family,'Skyridge');
 assert.match(result.model,/Politoed H23\/H32/);assert.equal(result.variant,x.variant);
 assert.equal(P.retry(x,web('poli'),result),false);
});
test('Vileplume checklist row is pertinent; absent slab grading does not discredit it',()=>{
 const f=fixtures.wilo,out=W.result(f.firstVision,web('wilo'),f.sources,1);
 assert.equal(out.market_ready,true);assert.ok(out.identification_sources.length);
});
test('Boniface: core number/name/series closes; the inferred Green Prizm name stays unconfirmed',()=>{
 const f=fixtures.boniface,b=structuredClone(f.firstVision);
 b.identifier_observations=b.identifier_observations.filter(o=>o.image_index<=f.uploadedImageCount);
 const out=P.close(b,2);assert.equal(out.core_identity_confirmed,true);
 assert.equal(out.variant_status,'to_verify');assert.match(out.proposed_variant,/Green Prizm/);
 assert.doesNotMatch(out.normalized_query,/Green/);assert.match(out.normalized_query,/2\/5/);
 assert.match(out.model,/21/);assert.deepEqual(P.cropPlan(b,2).regions,[]);
});
test('Box: season is not a model code, and missing physical configuration cannot confirm Mega',()=>{
 const f=fixtures.box,b=f.firstVision;assert.equal(P.decision(b,1).close,false);
 assert.equal(W.observations(b).filter(o=>o.role==='model').length,0);
 const q=W.query(b,'',1);assert.ok(q.indexOf('AUTOGRAPH')<q.indexOf('COOPER FLAGG'));
 const out=W.result(b,web('box',1),f.sources,2);
 assert.equal(out.market_ready,false);assert.equal(out.web_checks[0].rejection,'configuration_not_linked');
 assert.doesNotMatch(out.verification_summary,/Identità verificata/);
});
test('Second web needs a relevant returned candidate; empty and invented-source lookups stop',()=>{
 const f=fixtures.box,r=web('box');assert.equal(P.retry(f.firstVision,r,W.result(f.firstVision,r,f.sources,1)),true);
 assert.equal(P.retry(f.firstVision,{candidates:[]},W.result(f.firstVision,{candidates:[]},[],1)),false);
 assert.equal(P.retry(f.firstVision,r,W.result(f.firstVision,r,[],1)),false);
});
test('Clear photo collector conflicts remain binding; omitted language in a source is not a conflict',()=>{
 const f=fixtures.poli,r=structuredClone(web('poli'));
 r.candidates[0].conflicts.push({field:'variant',observed:'Italian holo',source:'La fonte non verifica la lingua italiana.'});
 assert.equal(W.result(f.firstVision,r,f.sources,1).market_ready,true);
 const b=structuredClone(f.firstVision);b.identifier_observations.find(o=>o.role==='collector_number').text='H22/H32';
 assert.equal(W.result(b,r,f.sources,1).market_ready,false);
});
test('Manufacturer model-code photo can close without web; model year alone cannot',()=>{
 const x={kind:'object',status:'identified',brand:'Philips',model:'YKF423-001',model_confidence:95,market_ready:true,normalized_query:'Philips YKF423-001',identifier_observations:[{text:'YKF423-001',role:'model',legibility:'clear',image_index:1}]};
 assert.equal(P.close(x,1).core_identity_confirmed,true);
 assert.equal(P.decision({...x,identifier_observations:[{text:'2025/26',role:'model',legibility:'clear',image_index:1}]},1).close,false);
});
test('Autograph ratios distinguish every-three-boxes from every-box configuration',()=>{
 assert.deepEqual(W.configuration('1 AUTOGRAPH* IN EVERY 3 BOXES!'),{autographRatio:1/3});
 assert.deepEqual(W.configuration('6 Cards Per Pack. 7 Packs Per Box. 1 autograph per box'),{cardsPerPack:6,packsPerBox:7,autographRatio:1});
});
