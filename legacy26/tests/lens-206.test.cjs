const {test}=require('node:test'),assert=require('node:assert/strict');
const E=require('../src/main/assets/catalogue-engine'),L=require('../src/main/assets/lens-policy');
function fixture(){
 const l=new E.Ledger({domain:'pokemon',kind:'card'});for(const [f,v] of Object.entries({subject:'Politoed',collector_number:'H23/H32',copyright:'©2003',language:'it',finish:'holo'}))l.add(f,v,{source:'vision',certainty:'clear',image_index:1});
 const ref={id:'lens-1',title:'Politoed Skyridge 2003 H23/H32 Italian Holo',snippet:'',url:'https://example.com/politoed',image_url:'https://example.com/card.jpg',image_data:'downloaded-test-carrier'};
 const reading={subject:'Politoed',number:'H23/H32',year:'2003',language:'it'};
 const c={reference_id:'lens-1',match:true,ambiguous:false,title_matches_image:true,conflicts:[],original_reading:{...reading},reference_reading:{...reading},features:[{field:'artwork',original:'frog on branch',reference:'frog on branch',agrees:true,certainty:'clear'},{field:'identifier',original:'H23/H32',reference:'H23/H32',agrees:true,certainty:'clear'}],identity:{subject:'Politoed',family:'Skyridge',number:'H23/H32',year:'2003',brand:'Pokémon',subset:'',proof:{subject:'Politoed',family:'Skyridge',year:'2003',subset:''},display_names:{it:'Politoed',en:'Politoed'}}};return {l,ref,c};
}
test('downloaded image plus independent details closes Politoed without a webpage',()=>{
 const {l,ref,c}=fixture(),before=l.atoms.length,out=L.visualEntries206({comparisons:[c]},[ref],l);assert.equal(out.accepted.length,1);assert.equal(l.atoms.length,before+1);assert.equal(l.pick('collector_number').source,'vision');assert.equal(l.atoms.at(-1).source,'lens_image_comparison');
 const r=E.reduce(l,out.accepted);assert.equal(r.market_ready,true);L.present206(r,out.accepted,l,'en');assert.match(r.title,/Politoed.*H23\/H32.*2003.*ITA/);assert.equal(r.language,'it');assert.equal(r.display_language,'en');assert.equal(r.normalized_query,r.title);
});
test('no downloaded bytes, duplicate IDs and unknown IDs cannot prove a comparison',()=>{
 for(const mode of ['no_bytes','unknown','duplicate']){const {l,ref,c}=fixture();if(mode==='no_bytes')delete ref.image_data;if(mode==='unknown')c.reference_id='made-up';const r=L.visualEntries206({comparisons:mode==='duplicate'?[c,c]:[c]},[ref],l);assert.equal(r.accepted.length,mode==='duplicate'?1:0);assert.equal(r.rejected.length,1);}
});
test('wrong number, wrong language, unknown identifier and ambiguous art never close',()=>{
 for(const mutate of [c=>c.reference_reading.number='H24/H32',c=>c.reference_reading.language='ja',c=>c.reference_reading.number='',c=>c.original_reading.number='H24/H32',c=>c.ambiguous=true,c=>c.title_matches_image=false,c=>c.features[1].field='artwork',c=>c.conflicts=['different stamp'],c=>c.identity.family='Aquapolis',c=>c.identity.year='2002']){
  const {l,ref,c}=fixture();mutate(c);const r=L.visualEntries206({comparisons:[c]},[ref],l);assert.equal(r.accepted.length,0);assert.equal(l.pick('catalogue_core'),null);
 }
});
test('catalogue-only number never becomes a physically verified identifier',()=>{
 const {ref,c}=fixture(),l=new E.Ledger({domain:'pokemon',kind:'card'});l.add('subject','Politoed',{source:'vision',certainty:'clear',image_index:1});assert.equal(L.visualEntries206({comparisons:[c]},[ref],l).accepted.length,0);assert.equal(l.pick('collector_number'),null);
});
test('verified translated name preserves physical Chinese language and V suffix',()=>{
 const l=new E.Ledger({domain:'pokemon',kind:'card'});for(const [f,v] of Object.entries({subject:'超梦V',collector_number:'135/127',copyright:'©2024',language:'zh',set_code:'CS5AC',finish:'holo'}))l.add(f,v,{source:'vision',certainty:'clear',image_index:1});
 const {c,ref}=fixture();ref.title='Mewtwo V CS5AC 2024 135/127 Chinese';Object.assign(c,{original_reading:{subject:'超梦V',number:'135/127',year:'2024',language:'zh'},reference_reading:{subject:'超梦V',number:'135/127',year:'2024',language:'zh'}});Object.assign(c.identity,{subject:'Mewtwo V',family:'CS5AC',number:'135/127',year:'2024',proof:{subject:'Mewtwo V',family:'CS5AC',year:'2024',subset:''},display_names:{it:'Mewtwo V',en:'Mewtwo V'}});
 const out=L.visualEntries206({comparisons:[c]},[ref],l);assert.equal(out.accepted.length,1);const r=E.reduce(l,out.accepted);assert.equal(r.core_identity.status,'confirmed');L.present206(r,out.accepted,l,'it');assert.match(r.title,/Mewtwo V.*CS5AC.*135\/127.*2024.*CHN/);assert.equal(r.language,'zh');assert.equal(l.pick('subject').value,'超梦V');
 out.accepted[0].display_names.it='Mewtwo ex';L.present206(r,out.accepted,l,'it');assert.doesNotMatch(r.title,/Mewtwo ex/);
});
