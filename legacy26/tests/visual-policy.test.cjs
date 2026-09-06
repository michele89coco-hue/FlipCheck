const {test}=require('node:test');const assert=require('node:assert/strict');
const V=require('../src/main/assets/visual-policy.js');
const base={kind:'card',market_ready:false,model_confidence:20,model:'',object_unit:'panel',variant:'raw',photo_clues:[{text:'MANOEL FRANCISCO SANTOS',role:'text',certainty:'clear'},{text:'EDVALDO ALVES SANTAROSA PELE',role:'text',certainty:'clear'}]};
const reference={id:'ref1',url:'https://catalog.example/panel',text:'Double portrait panel. Catalogue title: Historical portrait panel. Published in 1958, issue 37.',image_data:'data:image/png;base64,AAAA'};
const candidate={category:'panel',brand:'',family:'',model:'Historical portrait panel',year:'1958',issue_number:'37',catalog_number:'',unit:'panel',variant:'',decision:'match',same_unit:true,physical_ambiguity:false,conflicts:[],matches:[{reference_id:'ref1',feature:'layout',photo_detail:'two portraits side by side',reference_detail:'two portraits side by side',agrees:true},{reference_id:'ref1',feature:'text',photo_detail:'both name captions',reference_detail:'both name captions',agrees:true}],fields:[{field:'model',value:'Historical portrait panel',reference_id:'ref1',quote:'Catalogue title: Historical portrait panel.'},{field:'year',value:'1958',reference_id:'ref1',quote:'Published in 1958, issue 37.'},{field:'issue_number',value:'37',reference_id:'ref1',quote:'Published in 1958, issue 37.'}]};
test('historical spelling is preserved and query never receives catalogue answer',()=>{const q=V.plan({...base,brand:'Guessed',family:'Rekord',model:'1958 issue 37'});assert.equal(q.query,'MANOEL FRANCISCO SANTOS EDVALDO ALVES SANTAROSA PELE');assert.equal(q.useful,true);assert.equal(V.plan(base,[q.query]).duplicate,true);});
test('no text still permits visual route and missing placeholders are omitted',()=>{assert.equal(V.plan({photo_clues:[{text:'non leggibile',certainty:'clear'}]}).useful,false);assert.equal(V.plan({photo_clues:[{text:'2/5',role:'serial',certainty:'clear'}]}).query,'');});
test('provider score and suggested labels alone cannot confirm',()=>{const out=V.validate(base,{candidates:[{...candidate,matches:[],score:1,providerScore:99}]},[reference]);assert.equal(out.market_ready,false);});
test('visual comparison and cited facts close panel, issue stays separate from card number',()=>{const out=V.validate(base,{candidates:[candidate]},[reference]);assert.equal(out.market_ready,true);assert.equal(out.source_confirmed_issue_number,'37');assert.equal(out.source_confirmed_catalog_number,'');assert.equal(out.variant,'raw');assert.deepEqual(out.observed_data,base.photo_clues);assert.equal(out.catalogue_data[1].origin,'catalogue');});
test('single card cannot replace double panel and ambiguous reprint stays unresolved',()=>{for(const change of [{unit:'single'},{same_unit:false},{physical_ambiguity:true},{conflicts:['different text']}])assert.equal(V.validate(base,{candidates:[{...candidate,...change}]},[reference]).market_ready,false);});
test('missing image or fabricated quotation is insufficient',()=>{assert.equal(V.validate(base,{candidates:[candidate]},[{...reference,image_data:''}]).market_ready,false);assert.equal(V.validate(base,{candidates:[candidate]},[{...reference,text:'unrelated catalogue'}]).market_ready,false);});
test('duplicates are merged while distinct units survive',()=>{const out=V.mergeCandidates([candidate,structuredClone(candidate),{...candidate,unit:'single'}]);assert.equal(out.length,2);assert.equal(out[0].matches.length,2);});
test('clear identifiers must be compared; serial and edition are never transferred',()=>{const x={...base,photo_clues:[...base.photo_clues,{text:'2/5',role:'serial',certainty:'clear'},{text:'PX-7',role:'model',certainty:'clear'}],variant:'1st edition 2/5'};assert.equal(V.validate(x,{candidates:[candidate]},[reference]).market_ready,false);const c={...candidate,matches:[...candidate.matches,{reference_id:'ref1',feature:'code',photo_detail:'PX-7',reference_detail:'PX-7',agrees:true}]};assert.equal(V.validate(x,{candidates:[c]},[reference]).variant,'1st edition 2/5');});
test('already confirmed baseline is immutable',()=>{const x={...base,market_ready:true,model_confidence:94,normalized_query:'known'};assert.equal(V.validate(x,{candidates:[]},[]),x);});
test('global budget includes failed attempts, stops duplicate count and expires',()=>{let now=0;const b=new V.Budget({now:()=>now,deadlineMs:90000});const e=b.reserve('text',.012);b.settle(e,.0105);b.reserve('visual',.0035);assert.throws(()=>b.reserve('visual',.001),/call_limit/);assert.throws(()=>b.reserve('market',.02),/budget_exhausted/);const second=b.reserve('text',.011);b.settle(second,null);assert.equal(b.spent(),.025);now=90001;assert.throws(()=>b.reserve('vision',0),/timeout/);});

test('explicit uncertain OCR cannot re-enter the query through legacy layout',()=>{const x={photo_clues:[{text:'PX-7',role:'model',certainty:'uncertain'}],layout_signature:[{term:'PX-7',position:'label'}]};assert.equal(V.plan(x).query,'');});
test('sixth quantity clue gets query priority for any product; guesses and uncertain OCR are absent',()=>{
 const x={title:'Guessed premium edition',evidence:['MAYBE OCR'],photo_clues:['Acme Tools','Series Delta','Portable Kit','Outdoor Use','Workshop','2 BATTERIES INCLUDED'].map(text=>({text,role:'text',certainty:'clear'}))};
 x.photo_clues.push({text:'MAYBE OCR',role:'text',certainty:'uncertain'});
 assert.match(V.plan(x).query,/^2 BATTERIES INCLUDED/);assert.equal(V.plan(x).terms.length,6);
 assert.doesNotMatch(V.resolverPrompt(x,''),/Guessed premium edition|MAYBE OCR/);
});
test('observed and sourced contradictions survive; guesses cannot become physical conflicts',()=>{
 const x={photo_clues:[{text:'2 BATTERIES INCLUDED',role:'text',certainty:'clear'}]},source={url:'https://catalog.example/kit',text:'This kit contains 3 batteries included in the package.'};
 const c={matched_terms:[],conflicting_terms:['2 BATTERIES INCLUDED'],conflict_evidence:[{photo_text:'2 BATTERIES INCLUDED',source_text:source.text,source_url:source.url,kind:'contradiction'}]};
 const grounded=V.groundChecks([c],x,[source])[0];assert.equal(grounded.conflicting_terms.length,1);
 const guessed=V.groundChecks([{...c,conflict_evidence:[],conflicting_terms:['premium edition']}],x,[source])[0];assert.equal(guessed.conflicting_terms.length,0);assert.equal(guessed.requires_visual_check,true);
 const fake=V.groundChecks([{...c,conflict_evidence:[{...c.conflict_evidence[0],source_text:'Invented contradictory quotation'}]}],x,[source])[0];assert.equal(fake.conflicting_terms.length,0);assert.equal(fake.requires_visual_check,true);
});
test('a matching documented label error is distinct from a physical contradiction',()=>{
 const x={photo_clues:[{text:'MISPRINTED NAME',role:'text',certainty:'clear'}]},s={url:'https://catalog.example/label',text:'The original item has the documented error MISPRINTED NAME.'};
 const c={matched_terms:['MISPRINTED NAME'],conflicting_terms:['MISPRINTED NAME'],conflict_evidence:[{photo_text:'MISPRINTED NAME',source_text:s.text,source_url:s.url,kind:'documented_label_error'}]};
 assert.equal(V.groundChecks([c],x,[s])[0].conflicting_terms.length,0);
});
test('catalogue discovery requires observed context and prefers discriminating quantities',()=>{
 const x={photo_clues:['Acme Tools','Series Delta','2 BATTERIES INCLUDED'].map(text=>({text,role:'text',certainty:'clear'}))};
 const sources=[{url:'https://catalog.example/generic',text:'Acme Tools Series Delta'},{url:'https://catalog.example/exact',text:'Acme Tools Series Delta 2 BATTERIES INCLUDED'},{url:'https://catalog.example/unrelated',text:'Acme Tools only'}];
 assert.deepEqual(V.rankSources(sources,x).map(s=>s.url),[sources[1].url,sources[0].url]);
});
test('visual confirmation must compare clear quantities and reject a different count',()=>{
 const x={...base,photo_clues:[{text:'2 BATTERIES INCLUDED',role:'text',certainty:'clear'}]};
 assert.equal(V.validate(x,{candidates:[candidate]},[reference]).market_ready,false);
 const match={reference_id:'ref1',feature:'configuration',photo_detail:'2 BATTERIES INCLUDED',reference_detail:'2 batteries in the package',agrees:true};
 assert.equal(V.validate(x,{candidates:[{...candidate,matches:[...candidate.matches,match]}]},[reference]).market_ready,true);
 assert.equal(V.validate(x,{candidates:[{...candidate,matches:[...candidate.matches,{...match,reference_detail:'3 batteries in the package'}]}]},[reference]).market_ready,false);
});

test('physical counts reach discovery while holder and uncertain observations stay out',()=>{
 const x={photo_clues:[{text:'PROGRAM',role:'text',certainty:'clear'},{text:'PROGRAM RESET',role:'text',certainty:'clear'}],physical_observations:[{text:'6 stations',feature:'count',certainty:'clear',entity:'target',image_index:1},{text:'30 cm holder',feature:'measurement',certainty:'clear',entity:'holder',image_index:1},{text:'Maybe brand',feature:'shape',certainty:'uncertain',entity:'target',image_index:1}]};
 assert.equal(V.plan(x).query,'6 stations PROGRAM RESET');assert.match(V.resolverPrompt(x,''),/6 stations/);assert.doesNotMatch(V.resolverPrompt(x,''),/30 cm holder|Maybe brand/);
});
test('typed seasons and listing IDs are not product identifiers; short card numbers survive',()=>{
 const x={photo_clues:[{text:'2025/26',role:'season',certainty:'clear'},{text:'81',role:'collector_number',certainty:'clear'},{text:'ZX-430',role:'model',certainty:'clear'}]};
 assert.deepEqual(V.identifiers(x).map(c=>c.text),['81','ZX-430']);assert.equal(V.seasonLike('2025/26'),true);assert.equal(V.seasonLike('ZX-430'),false);
 const r={...reference,url:'https://auction.example/2030184-card',text:'2030184. Auction catalogue number. Historical portrait panel.'};
 const field={field:'catalog_number',value:'2030184',quote:'2030184. Auction catalogue number.',reference_id:'ref1'};
 assert.deepEqual(V.validFields({fields:[field]},[r]),[]);assert.deepEqual(V.validFields({fields:[{...field,number_kind:'listing_id',scope:'listing'}]},[{...r,url:'https://catalog.example/item'}]),[]);
});
function composedPanel(){
 const ref={...reference,text:'Publisher Rekord. Year 1958, issue 37. Subjects Pelé and Manoel Francisco Santos. Auction 2030184.'};
 const f=(field,value,quote,scope='target',number_kind='none')=>({field,value,quote,scope,number_kind,reference_id:'ref1'});
 const c={...candidate,model:'Italian translated title',identity_level:'exact',decision:'possible',physical_ambiguity:true,conflicts:[{scope:'holder',reason:'Plastic holder versus loose panel'},{scope:'parent',reason:'30 by 21 cm describes newspaper'},{scope:'authenticity',reason:'Originality cannot be certified'}],specimen_notes:['Originality not assessed'],fields:[f('brand','Rekord','Publisher Rekord.','parent'),f('year','1958','Year 1958, issue 37.','parent','year'),f('issue_number','37','Year 1958, issue 37.','parent','issue_number'),f('subject','Pelé and Manoel Francisco Santos','Subjects Pelé and Manoel Francisco Santos.'),f('catalog_number','2030184','Auction 2030184.','listing','listing_id')]};
 return {ref,c};
}
test('separately cited catalogue facts name a panel without importing listing or holder data',()=>{
 const {ref,c}=composedPanel(),out=V.validate(base,{candidates:[c]},[ref]);assert.equal(out.market_ready,true);assert.match(out.model,/Rekord.*1958.*n\. 37.*Pelé/);assert.doesNotMatch(out.model,/2030184|Italian/);assert.equal(out.source_confirmed_catalog_number,'');assert.equal(out.authenticity_status,'not_assessed');assert.equal(out.specimen_notes.length,1);
});
test('real target conflict, unknown physical ambiguity, family and different unit remain blocking',()=>{
 const {ref,c}=composedPanel();for(const change of [{conflicts:[{scope:'target',reason:'different printed frame'}]},{conflicts:[]},{identity_level:'family'},{decision:'different'},{unit:'single'}])assert.equal(V.validate(base,{candidates:[{...c,...change}]},[ref]).market_ready,false);
});
test('description quotations cannot stand in for an actual image comparison',()=>{
 const c={...candidate,matches:candidate.matches.map(m=>({...m,reference_evidence:'description'}))};assert.equal(V.validate(base,{candidates:[c]},[reference]).market_ready,false);
});
test('complete configuration evidence can close only with source-grounded quantities',()=>{
 const texts=['Acme Delta','Portable Kit','2 batteries included'],x={photo_clues:texts.map(text=>({text,role:'text',certainty:'clear'}))};
 const source={url:'https://acme.example/kit',text:'Acme Delta Portable Kit has 2 batteries included.'};
 const c={source_specificity:'exact_model',strong_source_count:1,matched_terms:texts,conflicting_terms:[],evidence_sources:[{url:source.url,quality:3}],match_evidence:[{photo_text:texts[2],source_text:'2 batteries included',source_url:source.url}]};
 assert.equal(V.groundChecks([c],x,[source])[0].complete_observed_match,true);
 for(const change of [{match_evidence:[]},{strong_source_count:0},{source_specificity:'family'},{matched_terms:texts.slice(0,2)}])assert.equal(V.groundChecks([{...c,...change}],x,[source])[0].complete_observed_match,false);
});
test('PDF manuals remain eligible references and observed season mismatches block closure',()=>{
 const x={photo_clues:[{text:'ZX-430',role:'model',certainty:'clear'}]};assert.equal(V.rankSources([{url:'https://acme.example/manual.pdf',text:'ZX-430 manual'}],x).length,1);
 const season={...base,photo_clues:[{text:'2025/26',role:'season',certainty:'clear'}]};assert.equal(V.validate(season,{candidates:[candidate]},[reference]).market_ready,false);
});

test('same catalogue name with different year or physical variant remains ambiguous',()=>{
 for(const change of [{year:'1959'},{variant:'Different printing'}]) {
  const out=V.validate(base,{candidates:[candidate,{...candidate,...change}]},[reference]);
  assert.equal(out.market_ready,false);assert.equal(out.assistance_state,'ambiguous');
 }
 const duplicate=V.validate(base,{candidates:[candidate,structuredClone(candidate)]},[reference]);assert.equal(duplicate.market_ready,true);
});

const recorded168=require('./fixtures/diagnostics-168.json');
test('recorded Machamp recovery covers the unknown border as well as copyright',()=>{
 const m=recorded168.mach,plan=V.printingPlan(m.vision,m.check,1);assert.equal(plan.length,2);assert.equal(plan.find(p=>p.detail==='shadow').fallback,true);assert.equal(plan.find(p=>p.detail==='shadow').object_region.width,.98);assert.equal(plan.find(p=>p.detail==='copyright').detail_crop,true);
});
test('recorded Pele comparison closes using the cited description without invented name or lot number',()=>{
 const p=recorded168.pele,out=V.validate(p.vision,p.comparison,p.references);assert.equal(out.market_ready,true);assert.match(out.model,/1958.*37.*Pelé y Manoel Francisco Santos/);assert.equal(out.source_confirmed_catalog_number,'');assert.equal(out.catalogue_data.find(f=>f.field==='subject').recovered_from,'cited_subject_description');assert.equal(out.authenticity_status,'not_assessed');
});
test('subject recovery still rejects uncited descriptions and quotations unrelated to the photographed names',()=>{
 const p=recorded168.pele;for(const quote of ['Invented unknown description','Tidningen Rekord, nº 37 1958']){const c=structuredClone(p.comparison);c.candidates[0].fields.find(f=>f.field==='subject').quote=quote;assert.equal(V.validate(p.vision,c,p.references).market_ready,false);}
});
test('typed collector labels normalize without changing physical numbers or year meaning',()=>{
 assert.equal(V.identifierValue({role:'collector_number',text:'NO. 280'}),'280');assert.equal(V.identifierValue({role:'model',text:'NO-280'}),'NO-280');assert.equal(V.seasonValue('2018-19 PANINI - PRIZM BASKETBALL'),'2018-19');assert.equal(V.seasonValue('2018/2019'),'2018-19');
});
test('appearance travels into queries and needs both image comparison and a cited variant name',()=>{
 const x={...base,variant:'Green Pulsar, likely',physical_observations:[{feature:'color',text:'Green patterned frame',certainty:'clear',entity:'target',image_index:1}]};assert.match(V.plan(x).query,/Green patterned frame/);assert.match(V.resolverPrompt(x,''),/Green patterned frame/);
 const r={...reference,text:reference.text+' Parallel: Green Pulsar.'},c={...candidate,variant:'Green Pulsar',matches:[...candidate.matches,{reference_id:'ref1',feature:'color',photo_detail:'green frame',reference_detail:'green frame',reference_evidence:'image',agrees:true}]};
 assert.equal(V.validate(x,{candidates:[c]},[r]).market_ready,false);c.fields=[...c.fields,{field:'variant',scope:'target',number_kind:'none',value:'Green Pulsar',quote:'Parallel: Green Pulsar.',reference_id:'ref1'}];assert.equal(V.validate(x,{candidates:[c]},[r]).market_ready,true);assert.equal(V.validate(x,{candidates:[c]},[r]).variant,'Green Pulsar');
 c.matches=c.matches.filter(m=>m.feature!=='color');assert.equal(V.validate(x,{candidates:[c]},[r]).market_ready,false);
});
test('Google first targets unknown cards or uncertain appearance while known identities stay terminal',()=>{
 assert.equal(V.googleFirst(recorded168.pele.vision),true);assert.equal(V.googleFirst(recorded168.doncic.vision),true);assert.equal(V.googleFirst({...base,market_ready:true,normalized_query:'Known',model_confidence:95}),false);assert.equal(V.googleFirst({kind:'object',photo_clues:[{text:'ZX-430',role:'model',certainty:'clear'}]}),false);
});
test('reference compaction preserves useful evidence and excludes obvious template images',()=>{
 const r={text:'Header. '+('Generic navigation. '.repeat(150))+'2 batteries included. Cookie settings Unrelated footer'};const out=V.compactReference(r,{photo_clues:[{text:'2 batteries included',role:'text',certainty:'clear'}]},900);assert.ok(out.text.length<=900);assert.match(out.text,/2 batteries included/);assert.doesNotMatch(out.text,/Cookie settings/);assert.equal(V.referenceImageUseful('https://site.example/static-images/hero-inner.png'),false);assert.equal(V.referenceImageUseful('https://site.example/actual-product.jpg'),true);
});

const recorded169=require('./fixtures/diagnostics-169.json');
test('recorded Hobby comparison closes from images plus cited quantity without requiring every surface adjective',()=>{
 const p=recorded169.box,refs=p.references.map(r=>V.compactReference(r,p.vision)),out=V.validate(p.vision,p.comparison,refs);
 assert.equal(out.market_ready,true);assert.match(out.model,/Hobby Box/);assert.equal(out.variant,'Hobby Box');assert.equal(out.visual_candidates[1].rejection,'unit_mismatch');assert.equal(out.catalogue_verified,true);
 assert.match(refs[0].text,/Each Box contains 1 Autograph/);assert.doesNotMatch(refs[0].text,/Blaster 40-Box/);
});
test('description is supplementary evidence, never a substitute for images or an invented quantity quote',()=>{
 const p=recorded169.box;for(const change of ['quantity','invented','no_images']){
  const r=structuredClone(p.comparison),refs=p.references.map(x=>({...x}));
  if(change==='quantity')r.candidates[0].matches.find(m=>m.feature==='configuration').reference_detail='Each Box contains 2 Autographs';
  if(change==='invented')r.candidates[0].matches.find(m=>m.feature==='configuration').reference_detail='Guaranteed imaginary source: 1 autograph every box';
  if(change==='no_images')r.candidates[0].matches=r.candidates[0].matches.filter(m=>m.reference_evidence==='description');
  assert.equal(V.validate(p.vision,r,refs).market_ready,false,change);
 }
 assert.equal(V.quantityMatches('2 batteries in every kit','Each kit contains 2 batteries'),true);
 assert.equal(V.quantityMatches('2 batteries in every kit','2 batteries per pack'),false);
 assert.equal(V.quantityMatches('2 batteries 4 chargers','4 batteries 2 chargers'),false);
});
test('recorded Doncic confidence cannot bypass its own explicit subtype uncertainty',()=>{
 const x=V.auditIdentity(recorded169.doncic.vision);assert.equal(V.ready(x),false);assert.equal(x.market_ready,false);assert.equal(x.variant_needs_verification,true);assert.equal(V.googleFirst(x),true);assert.equal(x.model,recorded169.doncic.vision.model);
});
test('a remembered expansion requires a cited catalogue family and can be corrected without a product rule',()=>{
 const x={...base,model:'Visible name A12',family:'Guessed Series',market_ready:true,model_confidence:98,normalized_query:'Guessed Series Visible name A12',photo_clues:[{text:'Visible name',role:'text',certainty:'clear'},{text:'A12',role:'collector_number',certainty:'clear'}]};
 assert.equal(V.ready(x),false);assert.equal(V.auditIdentity(x).catalogue_needs_verification,true);
 const r={...reference,text:'Real Series Visible name A12 catalogue entry.'};const c={...candidate,model:'Real Series Visible name A12',family:'Real Series',matches:[...candidate.matches,{reference_id:'ref1',feature:'code',photo_detail:'A12',reference_detail:'A12',agrees:true,reference_evidence:'image'}],fields:[{field:'model',value:'Real Series Visible name A12',reference_id:'ref1',quote:r.text}]};
 const out=V.validate(V.auditIdentity(x),{candidates:[c]},[r]);assert.equal(out.market_ready,true);assert.equal(out.family,'Real Series');assert.equal(V.ready(out),true);
 const ungrounded={...c,family:'Unsupported Series'};assert.equal(V.validate(V.auditIdentity(x),{candidates:[ungrounded]},[r]).market_ready,false);
 const printed={...x,photo_clues:[...x.photo_clues,{text:'Guessed Series',role:'text',certainty:'clear'}]};assert.equal(V.ready(printed),true);
});
test('recorded remote query keeps category and printed controls before incidental texture',()=>{
 const q=V.plan(recorded169.remote.vision).query;assert.match(q,/^TV remote control/);assert.match(q,/TV GUIDE/);assert.match(q,/TOP PICKS/);assert.doesNotMatch(q,/Brushed|Philips|scuff/);
});
test('0.03 EUR ceiling preserves configured exchange factor and enforces the aggregate budget',()=>{
 const b=new V.Budget(),r=b.reserve('vision',.01);b.settle(r,.01);b.reserve('text',.015);b.reserve('visual',.0035);assert.throws(()=>b.reserve('vision',.002),/budget_exhausted/);assert.equal(b.maxUsd,.03);assert.equal(new V.Budget({maxEur:1,usdPerEur:1.1}).maxUsd,.033);
});


test('build170 box completes its missing specification from an exact entry without another image comparison',()=>{
 const f=require('./fixtures/diagnostics-170.json').box,reply=f.phases.find(p=>p.stage==='flipcheck_visual_comparison').result;
 const refs=f.references.map(r=>({...r,image_data:'already-compared-image-marker'}));
 const source={url:'https://catalog.example/entry',title:'2025-26 Topps Chrome Update Series Basketball Hobby, Box',snippet:'1 autograph in every box.'};
 const before=V.validate(f.vision,reply,refs);assert.equal(before.visual_candidates[0].rejection,'configuration_not_matched');
 const after=V.completeComparison(f.vision,reply,refs,[source]);assert.equal(after.market_ready,true);assert.match(after.title,/Hobby/);assert.ok(after.identification_sources.some(s=>s.url===source.url));
 for(const change of [{snippet:'2 autographs in every box.'},{title:'A different product, hobby box'},{title:'2024-25 Topps Chrome Update Series Basketball Hobby, Box'}])assert.notEqual(V.completeComparison(f.vision,reply,refs,[{...source,...change}]).market_ready,true);
 assert.notEqual(V.completeComparison(f.vision,reply,f.references,[source]).market_ready,true);
});

const recorded171=require('./fixtures/diagnostics-171.json');
test('build171 explicit parallel doubt stays open despite physical_evidence and 96 percent',()=>{
 const x=recorded171.doncic.vision;assert.equal(x.market_ready,true);assert.equal(V.variantPending(x),true);assert.equal(V.ready(V.auditIdentity(x)),false);
 assert.equal(V.variantPending({...x,variant:'Base',missing_information:[],verification_summary:'Read completely',unresolved_identity_fields:['variant']}),true);
 assert.equal(V.variantPending({...x,variant:'Base',missing_information:['Condition should be confirmed'],verification_summary:'Read completely'}),false);
});
test('build171 subject in family does not bypass catalogue and generic card unit is not a mismatch',()=>{
 const x=recorded171.politoed.vision;assert.equal(V.cataloguePending(x),true);
 const reply=recorded171.politoed.phases.find(p=>p.stage==='flipcheck_visual_comparison').result;
 const out=V.validate(x,reply,recorded171.politoed.references.map(r=>({...r,image_data:'provided reference marker'})));
 assert.ok(out.visual_candidates.every(c=>c.rejection!=='unit_mismatch'));assert.notEqual(out.market_ready,true);
 assert.equal(V.validate({...base,object_unit:'panel'},{candidates:[{...candidate,unit:'single'}]},[reference]).market_ready,false);
});
test('build171 catalogue retrieval diversifies domains and uses candidate names only to discover sources',()=>{
 const f=recorded171.pele,ranked=V.rankSources(f.identity.raw_web_results,f.vision,f.identity.candidate_models);
 assert.notEqual(new URL(ranked[0].url).hostname,new URL(ranked[1].url).hostname);
 const p=recorded171.politoed,q=V.plan(p.vision).query,r=V.rankSources(p.identity.raw_web_results,p.vision,p.identity.candidate_models);
 assert.ok(r.some(s=>/tcgcollector|serebii/.test(s.url)));assert.equal(V.plan(p.vision).query,q);assert.equal(V.ready(p.vision),false);
});
test('reference image label facts require a readable same-image text match and never borrow listing IDs',()=>{
 const c={...candidate,fields:[{field:'model',scope:'target',evidence:'image',value:'Historical portrait panel',reference_id:'ref1',quote:'Historical portrait panel'}],matches:[candidate.matches[0],{...candidate.matches[1],reference_detail:'Historical portrait panel'}]};
 const r={...reference,text:'Indexed page title only'};assert.equal(V.validate(base,{candidates:[c]},[r]).market_ready,true);
 for(const changes of [{matches:[candidate.matches[0]]},{fields:[{...c.fields[0],scope:'listing'}]},{fields:[{...c.fields[0],quote:'Another entry',value:'Another entry'}]}])assert.equal(V.validate(base,{candidates:[{...c,...changes}]},[r]).market_ready,false);
 assert.equal(V.validate(base,{candidates:[c]},[{...r,image_data:''}]).market_ready,false);
});
test('missing manual figure is a source deficit, not a request for a new target photo',()=>{
 const f=recorded171.remote,reply=f.phases.find(p=>p.stage==='flipcheck_visual_comparison').result;
 const out=V.validate(f.vision,reply,f.catalogueRetrieval.references||[]);assert.equal(out.assistance_state,'source_detail_needed');assert.equal(out.next_photo_request,null);
});
