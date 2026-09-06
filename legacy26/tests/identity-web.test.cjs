const {test}=require('node:test');
const assert=require('node:assert/strict');
const W=require('../src/main/assets/identity-web.js');
const base={kind:'card',brand:'Pokémon',family:'Skyridge',model:'Politoed 12/64',title:'Politoed 12/64',variant:'Holo, Italiano',
  model_confidence:94,market_ready:true,distinctive_terms:['Politoed','110 PV','Crescita Improvvisa','Ranabalzo','Spruzza'],
  identifier_hints:['12/64'],identifier_observations:[{text:'12/64',role:'collector_number',legibility:'uncertain',image_index:1}]};
const url='https://catalog.example/cards/skyridge-h23';
const quote='Carta Pokémon Politoed Skyridge H23, 110 PV, Crescita Improvvisa, Ranabalzo, Spruzza Energia.';
const candidate={brand:'Pokémon',family:'Skyridge',model:'Politoed H23 Skyridge',variant:'Holo',collector_number:'H23',product_code:'',relation:'exact_product',conflicts:[],sources:[{url,quote}]};
const sources=[{url,title:'Politoed Skyridge H23',snippet:quote}];
const reply=c=>({candidates:[c],summary:'Catalogued',missing_information:[],next_photo_request:null});
test('standalone H23 replaces a disputed number without recycling stale title, preserving physical variant',()=>{
 const out=W.result(base,reply(candidate),sources,2);
 assert.equal(out.market_ready,true);assert.equal(out.title,'Politoed H23 Skyridge');
 assert.equal(out.source_confirmed_catalog_number,'H23');assert.equal(out.variant,base.variant);
 assert.equal(out.identifier_observations[0].text,'12/64');assert.doesNotMatch(out.normalized_query,/12\/64/);
});
test('a clearly reread conflicting collector number cannot be silently overwritten by web',()=>{
 const reading={...base,identifier_observations:[{...base.identifier_observations[0],legibility:'clear'}]};
 assert.equal(W.result(reading,reply(candidate),sources,1).market_ready,false);
});
test('partial attack wording is not a fabricated contradiction',()=>{
 const c={...candidate,conflicts:[{field:'text',observed:'Spruzza',source:'Spruzza Energia'}]};
 assert.equal(W.result(base,reply(c),sources,1).market_ready,true);
});
test('number-only coincidences and unrelated Pokédex pages cannot prove a card',()=>{
 for(const u of ['https://www.pokemon.com/it/pokedex/politoed','https://pokemondb.net/pokedex/politoed','https://rotomlabs.net/dex/politoed']){
  assert.equal(W.result(base,reply({...candidate,sources:[{url:u,quote}]}),[{url:u,snippet:quote}],1).market_ready,false);
 }
 const wrongQuote='Pokémon Politoed 25 HP games stats and moves Water-Type';
 assert.equal(W.result(base,reply({...candidate,sources:[{url,quote:wrongQuote}]}),[{url,snippet:wrongQuote}],1).market_ready,false);
});
test('missing tool URLs, fabricated excerpts and unsafe links are rejected',()=>{
 assert.equal(W.result(base,reply(candidate),[],1).market_ready,false);
 assert.equal(W.result(base,reply(candidate),[{url,snippet:'A completely different product'}],1).market_ready,false);
 assert.equal(W.safeUrl('javascript:alert(1)'),'');
});
test('citation-only tool envelopes accept quoted evidence, explicitly distinguished from returned excerpts',()=>{
 const out=W.result(base,reply(candidate),[{url,title:'Catalogue'}],1);
 assert.equal(out.market_ready,true);assert.equal(out.identification_sources[0].support,'cited_quote');
});
test('two different valid physical variants remain unresolved',()=>{
 const second={...candidate,model:'Politoed 25 Skyridge',collector_number:'25',sources:[{url,quote:quote.replace('H23','25')}]};
 const out=W.result(base,{candidates:[candidate,second]},[{url,snippet:quote+' '+quote.replace('H23','25')}],1);
 assert.equal(out.market_ready,false);
});
test('equivalent candidate descriptions do not create a false ambiguity',()=>{
 const other={...candidate,model:'Skyridge Politoed H23'};
 assert.equal(W.result(base,{candidates:[candidate,other]},sources,1).market_ready,true);
});
test('source must explicitly connect the actual model code; compatible parts stay unresolved',()=>{
 const b={kind:'object',brand:'Philips',family:'Remote control',variant:'Black',distinctive_terms:['Philips','YKF423-001','398GM10BEPHN0004HT','Remote control'],
  identifier_observations:[{text:'YKF423-001',role:'model',legibility:'clear',image_index:2}]};
 const q='Philips Remote control YKF423-001. Part 398GM10BEPHN0004HT.';
 const c={...candidate,brand:'Philips',family:'Remote control',model:'Philips YKF423-001',collector_number:'',product_code:'YKF423-001',sources:[{url,quote:q}]};
 assert.equal(W.result(b,reply(c),[{url,snippet:q}],1).market_ready,true);
 assert.equal(W.result(b,reply({...c,relation:'compatible'}),[{url,snippet:q}],1).market_ready,false);
 assert.equal(W.result({...b,identifier_observations:[{...b.identifier_observations[0],text:'YKF423-009'}]},reply(c),[{url,snippet:q}],1).market_ready,false);
});
test('an unsupported Blaster guess cannot become a confirmed sealed product',()=>{
 const b={kind:'object',brand:'Topps',family:'Chrome Update Series',model:'Blaster Box',model_confidence:91,market_ready:true,
  distinctive_terms:['Topps Chrome','2025/26','UPDATE SERIES','1 AUTOGRAPH IN EVERY BOX'],identifier_observations:[]};
 const c={...candidate,brand:'Topps',family:'Chrome Update Series',model:'Topps Blaster Box',collector_number:'',product_code:'',sources:[]};
 assert.equal(W.result(b,reply(c),[],1).market_ready,false);
});
test('serial of the copy and slab certificate are not used as a collector number in exact search',()=>{
 const b={...base,identifier_hints:['2/5','0014436473'],identifier_observations:[{text:'2/5',role:'serial_number',legibility:'clear'},{text:'0014436473',role:'slab_cert',legibility:'clear'}]};
 const q=W.query(b,'',1,null);assert.doesNotMatch(q,/2\/5|0014436473/);
});
test('the second query changes focus, verifies the candidate, and drops the disputed number',()=>{
 const one=W.query(base,'',1),two=W.query(base,'',2,reply(candidate));
 assert.notEqual(one,two);assert.match(two,/H23/);assert.doesNotMatch(two,/12\/64/);
 assert.notEqual(W.query(base,'',2,null),one);
});
