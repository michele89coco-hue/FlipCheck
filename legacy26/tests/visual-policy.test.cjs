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
test('global budget includes failed attempts, stops duplicate count and expires',()=>{let now=0;const b=new V.Budget({now:()=>now});const e=b.reserve('text',.012);b.settle(e,.0105);b.reserve('visual',.0035);assert.throws(()=>b.reserve('visual',.001),/call_limit/);assert.throws(()=>b.reserve('market',.02),/budget_exhausted/);const second=b.reserve('text',.011);b.settle(second,null);assert.equal(b.spent(),.025);now=90001;assert.throws(()=>b.reserve('vision',0),/timeout/);});

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
