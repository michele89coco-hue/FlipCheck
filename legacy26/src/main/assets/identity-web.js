/* Small category-aware web confirmation policy; no local card-specific catalogue. */
(function(root){
  'use strict';
  const list=x=>Array.isArray(x)?x:[];
  const norm=x=>String(x || '').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
  const contains=(text,value)=>!!norm(value) && (' '+norm(text)+' ').includes(' '+norm(value)+' ');
  const safeUrl=u=>{try{const x=new URL(u);return /^https?:$/.test(x.protocol)?x.href.replace(/#.*$/,''):'';}catch(_){return '';}};
  const anchored=(base,text)=>list(base.layout_signature).some(x=>x.position&&contains(x.term,text));
  // Dimensions, capacities and per-unit quantities are read from text, never assigned to a product name.
  function specifications(text){
    const t=String(text||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase()
      .replace(/[®™*]/g,'').replace(/\b(one|uno|un|una|each|ogni)\b/g,m=>/each|ogni/.test(m)?'every':'1');
    const unit=u=>({cards:'card',carte:'card',packs:'pack',pacchetti:'pack',boxes:'box',scatole:'box',autographs:'autograph',autografi:'autograph',autografo:'autograph'}[u]||u);
    const out=[];
    for(const m of t.matchAll(/\b(\d+(?:[.,]\d+)?)\s+(autographs?|autografi?|cards|carte|packs|pacchetti|pieces|pezzi|batteries|batterie|filters|filtri)(?:\s+cards?)?\s+(?:(?:in\s+)?every|per|a)\s+(?:(\d+)\s+)?(boxes|box|scatole|packs?|pacchetto|confezione|kit|set)\b/g)){
      const before=t.slice(Math.max(0,m.index-28),m.index),mode=/look for|chance|possib|fino a|up to/.test(before)?'possible':'stated';
      out.push({key:unit(m[2])+'_per_'+unit(m[4]),value:Number(m[1].replace(',','.'))/Number(m[3]||1),mode,text:m[0]});
    }
    for(const m of t.matchAll(/\b(\d+(?:[.,]\d+)?)\s*(mah|ah|gb|tb|mb|khz|mhz|ghz|hz|kw|w|mv|v|ma|a|mm|cm|ml|litri|litro|l)\b/g)){
      const u=m[2],scale={mah:['ah',.001],mb:['gb',.001],tb:['gb',1000],khz:['hz',1000],mhz:['hz',1e6],ghz:['hz',1e9],kw:['w',1000],mv:['v',.001],ma:['a',.001],mm:['mm',1],cm:['mm',10],ml:['l',.001],litri:['l',1],litro:['l',1]}[u]||[u,1];
      out.push({key:scale[0],value:Number(m[1].replace(',','.'))*scale[1],mode:'stated',text:m[0]});
    }
    return out.filter((x,i,a)=>a.findIndex(y=>x.key===y.key&&x.value===y.value&&x.mode===y.mode)===i);
  }
  function compareSpecifications(photo,source){
    const observed=specifications(photo),documented=specifications(source),matched=[],different=[],missing=[];
    for(const p of observed){
      const sameUnit=documented.filter(s=>s.key===p.key&&s.mode===p.mode);
      if(!sameUnit.length){missing.push(p);continue;}
      if(sameUnit.some(s=>Math.abs(s.value-p.value)<1e-8))matched.push(p);
      else different.push({observed:p,source:sameUnit});
    }
    return {matched,different,missing};
  }
  function observations(base){return list(base.identifier_observations).filter(x=>x && x.text && ['clear','uncertain'].includes(x.legibility)
    && (base.kind!=='card'||['collector_number','serial_number','slab_cert'].includes(x.role))
    && !(base.kind!=='card'&&x.role==='model'&&/^\s*(?:19|20)\d{2}\s*[/–-]\s*\d{2,4}\s*$/.test(x.text)));}
  function configuration(text){
    const out={},keys={card_per_pack:'cardsPerPack',pack_per_box:'packsPerBox',autograph_per_box:'autographRatio'};
    for(const s of specifications(text))if(keys[s.key]&&s.mode==='stated')out[keys[s.key]]=s.value;
    return out;
  }
  const sealed=x=>x.kind!=='card'&&/\bbox\b|sealed|sigillat|confezione/i.test([x.title,x.category,x.family].join(' '));
  const searchFacts=x=>informative(x).filter(t=>norm(t)!==norm(x.family)||anchored(x,t)).sort((a,b)=>Number(specifications(b).length>0)-Number(specifications(a).length>0));

  function literals(base){return [...new Set([...list(base.distinctive_terms),...list(base.layout_signature).map(x=>x.term),...observations(base).map(x=>x.text)].filter(x=>typeof x==='string' && x.trim()))];}
  function informative(base){return literals(base).filter(x=>norm(x).length>=4 && !/^(?:pokemon|topps|panini|card|carta|holo|italian|italiano|english|base|retail)$/.test(norm(x)) && !/^\d+$/.test(norm(x)));}
  function searchPlan(base,userText,pass,prior){
    const ids=observations(base).filter(x=>!['serial_number','slab_cert'].includes(x.role));
    const hint=observations(base).length?ids.filter(x=>pass===1||x.legibility==='clear').map(x=>x.text):list(base.identifier_hints);
    const context=base.kind==='card'?'trading card checklist':'product model specifications';
    const family=base.identity_route?.reference?.status==='conflict'?base.identity_route.reference.alternatives.map(x=>x.set).join(' '):base.family;
    const excluded=observations(base).filter(x=>['serial_number','slab_cert'].includes(x.role)).map(x=>x.text);
    const terms=searchFacts(base).filter(t=>!excluded.some(x=>contains(t,x))&&!hint.some(h=>contains(t,h)));
    const seasons=literals(base).flatMap(t=>t.match(/\b(?:19|20)\d{2}(?:\s*[/–-]\s*\d{2,4})?\b/g)||[]);
    // Search the best surviving candidate, not whichever the model happened to list first.
    const ranked=list(prior?.candidates).filter(c=>c.model&&(!c.rejection||!['physical_conflict','configuration_conflict'].includes(c.rejection)))
      .sort((a,b)=>(b.specification_check?.matched.length||0)-(a.specification_check?.matched.length||0)||Number(b.relation==='exact_product')-Number(a.relation==='exact_product')||(b.score||0)-(a.score||0));
    const c=pass===2?ranked[0]:null;
    const values=pass===1?[base.brand,family,...seasons,...hint.slice(0,2),...terms.slice(0,4),context,userText]
      :c?[c.brand,c.family,c.model,c.collector_number||c.product_code,...seasons,...terms.slice(0,4),context,userText]
      :[base.brand,family,...seasons,...terms.slice(0,5),context,'configuration specifications',userText];
    const unique=[];for(const v of values)if(v&&!unique.some(t=>contains(t,v)))unique.push(v);
    return {query:unique.join(' ').slice(0,650),focus:c?.model||'physical_facts',photo_terms:terms.slice(0,5),seasons:[...new Set(seasons)]};
  }
  const query=(base,userText,pass,prior)=>searchPlan(base,userText,pass,prior).query;
  function categorySource(url,text,base){
    if(base.kind!=='card')return !/\b(?:pokedex|pokemoncrystal)\b/i.test(url);
    if(/(?:\/pokedex(?:[/-]|$)|\/dex\/|\/r\/pokemoncrystal)/i.test(url))return false;
    return /(?:\b(?:tcg|trading cards?|checklists?|collector|cards?|cart[ae])\b|\/cards?\/|cardmarket|tcgplayer)/i.test(url+' '+text);
  }
  function sourceProof(candidate,base,sources){
    const verified=[];
    for(const evidence of list(candidate.sources)){
      const url=safeUrl(evidence.url),found=sources.find(s=>safeUrl(s.url)===url);
      if(!url||!found||typeof evidence.quote!=='string'||evidence.quote.trim().length<8)continue;
      const actual=found.snippet||found.text||'',text=actual||evidence.quote;
      let decoded=url;try{decoded=decodeURIComponent(url);}catch(_){}
      const context=[text,found.title,decoded].join(' ');
      const family=contains(context,candidate.family),brand=contains(context,candidate.brand);
      const card=base.kind==='card';
      const modelWords=norm(candidate.model).split(' ').filter(w=>w.length>=3&&!/^(?:the|card|carta|pokemon|sealed|box|italian|italiano|english|holo|base)$/.test(w)&&!norm(candidate.family).split(' ').includes(w)&&!norm(candidate.brand).split(' ').includes(w)&&!(base.kind==='card'&&norm(candidate.collector_number).split(' ').includes(w)));
      const number=candidate.collector_number;
      const numberHit=number&&(contains(text,number)||contains(text,collector(number)));
      // A specialist table row may contain only number/name; its title or URL supplies the set.
      const cardRow=card&&family&&numberHit&&modelWords.some(w=>/[a-z]/.test(w)&&contains(text,w));
      if(!categorySource(url,context,base)&&!cardRow)continue;
      if(card&&/\/(?:pokedex|dex)(?:[/-]|$)/i.test(url))continue;
      if(card?!family:(!family&&!brand))continue;
      const modelCodeLinked=!card&&candidate.product_code&&contains(context,candidate.product_code);
      if((!modelWords.length&&!modelCodeLinked)||!modelWords.every(w=>contains(context,w)))continue;
      if(card&&!numberHit)continue;
      if(!card){
        const formats=norm(candidate.model).match(/\b(?:blaster|hobby|mega|jumbo|value|hanger)\b/g)||[];
        if(!formats.every(f=>contains(context,f)))continue;
      }
      if(verified.some(s=>s.url===url))continue;
      verified.push({title:found.title||new URL(url).hostname,url,quote:actual?String(actual).slice(0,1600):text,
        support:actual?'returned_identifiers':'cited_quote',context:family?'set_or_family_link':'brand_link'});
    }
    return verified;
  }
  const collector=text=>{const m=String(text || '').match(/(?:^|[#\s])([A-Z]{0,5}\d{1,4}[A-Z]?)(?:\s*\/\s*([A-Z]?\d{1,4}))?(?=$|\s)/i);return m?norm(m[1]).replace(/^0+(?=\d)/,''):norm(text);};
  function assess(candidate,base,sources){
    const evidence=sourceProof(candidate,base,sources),quote=evidence.map(x=>x.quote).join(' ');
    const matched=informative(base).filter(t=>contains(quote,t));
    // Do not count nested spellings or model-generated matched_terms as separate proofs.
    const independent=matched.filter(t=>!matched.some(other=>norm(other)!==norm(t) && contains(other,t)));
    if(base.kind==='card'&&candidate.collector_number&&contains(quote,collector(candidate.collector_number))&&!independent.some(t=>collector(t)===collector(candidate.collector_number)))independent.push(candidate.collector_number);
    const ids=observations(base).filter(x=>x.legibility==='clear' && !['serial_number','slab_cert'].includes(x.role));
    const idMatches=ids.filter(x=>contains(quote,x.text) || (x.role==='collector_number' && collector(x.text)===collector(candidate.collector_number) && (contains(quote,candidate.collector_number)||contains(quote,collector(candidate.collector_number)))));
    const mismatches=ids.filter(x=>x.role==='collector_number' && candidate.collector_number && collector(x.text)!==collector(candidate.collector_number));
    const codeMismatch=base.kind!=='card' && ids.filter(x=>['model','part_number','barcode'].includes(x.role)).length>0 && !idMatches.some(x=>x.role!=='collector_number');
    const conflicts=list(candidate.conflicts).filter(x=>{
      // Incomplete text is missing evidence, not a contradiction. Clear identifiers stay binding.
      if(x.state==='missing')return false;
      if(/non (?:e |è )?(?:verificat|riportat|indicat|mostrat)|not (?:verified|reported|shown|specified|confirmed)|non verifica|non riporta|non conferm/i.test(x.source||''))return false;
      if(x.field==='family'&&!anchored(base,x.observed))return false;
      if(x.field==='text'&&!literals(base).some(t=>contains(t,x.observed)||contains(x.observed,t)))return false;
      if(x.field==='text' && (contains(x.source,x.observed)||contains(x.observed,x.source)))return false;
      if(['collector_number','product_code'].includes(x.field))return ids.some(i=>contains(i.text,x.observed));
      if(x.state==='different'&&(!x.source_value||!contains(quote,x.source_value)||!evidence.some(s=>safeUrl(s.url)===safeUrl(x.source_url))))return false;
      return !!x.observed && !!x.source && norm(x.observed)!==norm(x.source);
    });
    const exact=candidate.relation==='exact_product';
    const proof=evidence.length>0 && independent.length>=2 && exact && !mismatches.length && !codeMismatch && !conflicts.length;
    // A source must actually identify the candidate's catalogue number/code, not just name the subject.
    const sourceIdentifier=candidate.collector_number || candidate.product_code;
    const linked=sourceIdentifier?(contains(quote,sourceIdentifier)||(base.kind==='card'&&contains(quote,collector(sourceIdentifier)))):independent.length>=3||compareSpecifications(literals(base).join(' '),quote).matched.length>0;
    const specCheck=compareSpecifications(literals(base).join(' '),quote);
    const configKeys=specifications(literals(base).join(' ')),configMismatch=specCheck.different.length>0;
    const configMatch=specCheck.matched.length>0;
    const printedFormat=literals(base).some(t=>/\b(?:blaster|hobby|mega|jumbo|value|hanger)\s*box\b/i.test(t)&&contains(quote,t));
    const formatLinked=!sealed(base)||(configKeys.length?configMatch:printedFormat||idMatches.some(i=>i.role!=='collector_number'));
    const ok=proof && linked && formatLinked && !configMismatch;
    return {...candidate,proof:ok,evidence_sources:evidence,matched_physical_terms:independent,physical_conflicts:conflicts,specification_check:specCheck,
      rejection:ok?'':!evidence.length?'no_relevant_cited_source':mismatches.length||codeMismatch||conflicts.length?'physical_conflict':!exact?'family_or_compatible':configMismatch?'configuration_conflict':!formatLinked?'configuration_not_linked':'insufficient_linkage',
      score:ok?(idMatches.length?95:92):Math.min(79,35+independent.length*8+evidence.length*8)};
  }
  function canonical(c){
    let subject=' '+norm(c.model)+' ';
    for(const t of [c.brand,c.family,c.collector_number,c.product_code].filter(Boolean))subject=subject.replace(' '+norm(t)+' ',' ');
    return [c.brand,c.family,subject.trim().split(/\s+/).sort().join(' '),collector(c.collector_number),c.product_code,c.variant].map(norm).join('|');
  }
  function recoveryRegions(base,result){
    const useful=list(result.web_checks).filter(c=>c.evidence_sources.length);
    const fields=useful.flatMap(c=>list(c.physical_conflicts).map(x=>x.field));
    const quantity=useful.some(c=>c.specification_check?.different.length);
    if(!quantity&&!fields.some(x=>['collector_number','product_code','text'].includes(x)))return [];
    return list(base.detail_regions).filter(r=>r.purpose==='collector_number'?fields.includes('collector_number'):
      ['printed_text','product_label','slab_label'].includes(r.purpose)).slice(0,1);
  }
  function result(base,reply,sources,passes){
    const candidates=[],keys=new Set();
    for(const c of list(reply?.candidates)){
      if(!c?.model || /^(?:water|flying|normal|fire|grass|double)[- ](?:type|edge)$/i.test(c.model))continue;
      const k=canonical(c);if(keys.has(k))continue;keys.add(k);candidates.push(assess(c,base,sources));
    }
    const valid=candidates.filter(c=>c.proof),out={...base,web_checks:candidates,web_passes:passes,
      all_identification_sources:sources.map(s=>({title:s.title,url:s.url})),auto_web_resolved:true,
      candidate_models:candidates.map(c=>({model:c.model,confidence:c.score,verified:c.proof,source_count:c.evidence_sources.length,reason:c.rejection || 'Fonte pertinente collegata ai dati letti'}))};
    if(valid.length===1){
      const c=valid[0],variant=base.kind==='card'?base.variant:(c.variant || base.variant),language=base.pokemon_printing?.language || '';
      Object.assign(out,{status:'identified',title:c.model,model:c.model,brand:c.brand || base.brand,family:c.family || base.family,variant,
        source_confirmed_catalog_number:c.collector_number || '',source_confirmed_product_code:c.product_code || '',
        model_confidence:c.score,family_confidence:Math.max(90,base.family_confidence || 0),market_ready:true,model_verified:true,family_mode:false,
        normalized_query:[c.brand,contains(c.model,c.family)?'':c.family,c.model,variant,contains(c.model+' '+variant,language)?'':language].filter(Boolean).join(' '),identification_sources:c.evidence_sources,
        catalog_context:{family:c.family,source:'validated_web',photo_family_inferred:!anchored(base,base.family)},
        verification_summary:'Identità collegata ai dati fotografici tramite fonte web pertinente.',missing_information:[],next_photo_request:null});
    }else{
      Object.assign(out,{status:'uncertain',model_verified:false,market_ready:false,normalized_query:'',model_confidence:Math.min(79,base.model_confidence || 0),
        identification_sources:candidates.flatMap(c=>c.evidence_sources).slice(0,6),verification_summary:'Identità ancora da verificare: '+(candidates.some(c=>c.rejection==='configuration_not_linked')?'la fonte non collega la configurazione stampata al formato esatto.':candidates.some(c=>c.rejection==='physical_conflict'||c.rejection==='configuration_conflict')?'una lettura fotografica richiede confronto con le specifiche documentate.':'le prove raccolte non distinguono ancora il candidato esatto.'),web_summary:reply?.summary || '',
        missing_information:list(reply?.missing_information),next_photo_request:reply?.next_photo_request || null});
    }
    return out;
  }
  const str={type:'string'};
  const schema={type:'object',additionalProperties:false,properties:{summary:str,missing_information:{type:'array',maxItems:3,items:str},next_photo_request:{type:['string','null']},
    candidates:{type:'array',maxItems:3,items:{type:'object',additionalProperties:false,properties:{brand:str,family:str,model:str,variant:str,collector_number:str,product_code:str,
      relation:{type:'string',enum:['exact_product','compatible','family']},conflicts:{type:'array',maxItems:4,items:{type:'object',additionalProperties:false,properties:{field:{type:'string',enum:['collector_number','product_code','text','variant','family','subject']},observed:str,source:str,state:{type:'string',enum:['missing','different']},source_value:str,source_url:str},required:['field','observed','source','state','source_value','source_url']}},
      sources:{type:'array',maxItems:3,items:{type:'object',additionalProperties:false,properties:{url:str,quote:str},required:['url','quote']}}},
      required:['brand','family','model','variant','collector_number','product_code','relation','conflicts','sources']}}},required:['summary','missing_information','next_photo_request','candidates']};
  const api={observations,literals,query,searchPlan,assess,result,recoveryRegions,schema,safeUrl,configuration,specifications,compareSpecifications,anchored};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckWeb264=api;
})(typeof window!=='undefined'?window:globalThis);
