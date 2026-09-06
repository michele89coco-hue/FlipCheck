/* Small category-aware web confirmation policy; no local card-specific catalogue. */
(function(root){
  'use strict';
  const list=x=>Array.isArray(x)?x:[];
  const norm=x=>String(x || '').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
  const contains=(text,value)=>!!norm(value) && (' '+norm(text)+' ').includes(' '+norm(value)+' ');
  const safeUrl=u=>{try{const x=new URL(u);return /^https?:$/.test(x.protocol)?x.href.replace(/#.*$/,''):'';}catch(_){return '';}};
  function observations(base){return list(base.identifier_observations).filter(x=>x && x.text && ['clear','uncertain'].includes(x.legibility));}
  function literals(base){return [...new Set([...list(base.distinctive_terms),...list(base.layout_signature).map(x=>x.term),...observations(base).map(x=>x.text)].filter(x=>typeof x==='string' && x.trim()))];}
  function informative(base){return literals(base).filter(x=>norm(x).length>=4 && !/^(?:pokemon|topps|panini|card|carta|holo|italian|italiano|english|base|retail)$/.test(norm(x)) && !/^\d+$/.test(norm(x)));}
  function query(base,userText,pass,prior){
    const ids=observations(base).filter(x=>!['serial_number','slab_cert'].includes(x.role));
    const hint=observations(base).length?ids.map(x=>x.text):list(base.identifier_hints);
    const context=base.kind==='card'?'trading card checklist':'product model specifications';
    if(pass===2 && list(prior?.candidates).length){
      const c=prior.candidates[0];
      return [...new Set([c.brand,c.family,c.model,c.collector_number || c.product_code,context,...informative(base).slice(0,2)].filter(Boolean))].join(' ').slice(0,650);
    }
    // A failed exact query gets a different discovery query; guessed numbers/series are not re-imposed.
    const excluded=observations(base).filter(x=>['serial_number','slab_cert'].includes(x.role)).map(x=>x.text);
    const terms=informative(base).filter(t=>!excluded.some(x=>contains(t,x)));
    const values=pass===2?[base.brand,context,...terms.filter(t=>!hint.some(h=>contains(t,h))).slice(0,5),userText]
      :[base.brand,base.family,context,...hint.slice(0,2),...terms.slice(0,3),userText];
    return [...new Set(values.filter(Boolean))].join(' ').slice(0,650);
  }
  function categorySource(url,text,base){
    if(base.kind!=='card')return !/\b(?:pokedex|pokemoncrystal)\b/i.test(url);
    if(/(?:\/pokedex(?:[/-]|$)|\/dex\/|\/r\/pokemoncrystal)/i.test(url))return false;
    return /(?:\b(?:tcg|trading card|checklist|collector|card number|basketball card|baseball card|football card|hockey card)\b|\bcart[ae]\b|\/cards?\/|cardmarket|tcgplayer)/i.test(url+' '+text);
  }
  function sourceProof(candidate,base,sources){
    const verified=[];
    for(const evidence of list(candidate.sources)){
      const url=safeUrl(evidence.url),found=sources.find(s=>safeUrl(s.url)===url);
      if(!url || !found || typeof evidence.quote!=='string' || evidence.quote.trim().length<18)continue;
      const text=evidence.quote,actual=found.snippet || found.text || '';
      if(actual && !contains(actual,text))continue; // When excerpts are available, require an actual verbatim excerpt.
      if(!categorySource(url,text,base))continue;
      if(!contains(text,candidate.family) && !contains(text,candidate.brand))continue;
      const modelWords=norm(candidate.model).split(' ').filter(w=>w.length>=3 && !/^(?:the|card|carta|pokemon|sealed|box|italian|italiano|english|holo|base)$/.test(w));
      if(!modelWords.length || !modelWords.every(w=>contains(text,w)))continue;
      if(base.kind!=='card'){
        const formats=norm(candidate.model+' '+candidate.variant).match(/\b(?:blaster|hobby|mega|jumbo|retail)\b/g) || [];
        if(!formats.every(f=>contains(text,f)))continue;
        const configuration=literals(base).filter(t=>/autograph|autograf|\bpacks?\b|cards per|carte per/i.test(t));
        if(configuration.length && !configuration.some(t=>contains(text,t)))continue;
      }
      if(verified.some(s=>s.url===url))continue;
      verified.push({title:found.title || new URL(url).hostname,url,quote:text,support:actual?'returned_excerpt':'cited_quote'});
    }
    return verified;
  }
  const collector=text=>{const m=String(text || '').match(/(?:^|[#\s])([A-Z]{0,5}\d{1,4}[A-Z]?)(?:\s*\/\s*([A-Z]?\d{1,4}))?(?=$|\s)/i);return m?norm(m[1]).replace(/^0+(?=\d)/,''):norm(text);};
  function assess(candidate,base,sources){
    const evidence=sourceProof(candidate,base,sources),quote=evidence.map(x=>x.quote).join(' ');
    const matched=informative(base).filter(t=>contains(quote,t));
    // Do not count nested spellings or model-generated matched_terms as separate proofs.
    const independent=matched.filter(t=>!matched.some(other=>norm(other)!==norm(t) && contains(other,t)));
    const ids=observations(base).filter(x=>x.legibility==='clear' && !['serial_number','slab_cert'].includes(x.role));
    const idMatches=ids.filter(x=>contains(quote,x.text) || (x.role==='collector_number' && collector(x.text)===collector(candidate.collector_number) && contains(quote,candidate.collector_number)));
    const mismatches=ids.filter(x=>x.role==='collector_number' && candidate.collector_number && collector(x.text)!==collector(candidate.collector_number));
    const codeMismatch=ids.filter(x=>['model','part_number','barcode'].includes(x.role)).length>0 && !idMatches.some(x=>x.role!=='collector_number');
    const conflicts=list(candidate.conflicts).filter(x=>{
      // Incomplete text is missing evidence, not a contradiction. Clear identifiers stay binding.
      if(x.field==='text' && (contains(x.source,x.observed)||contains(x.observed,x.source)))return false;
      if(['collector_number','product_code'].includes(x.field))return ids.some(i=>contains(i.text,x.observed));
      return !!x.observed && !!x.source && norm(x.observed)!==norm(x.source);
    });
    const exact=candidate.relation==='exact_product';
    const proof=evidence.length>0 && independent.length>=2 && exact && !mismatches.length && !codeMismatch && !conflicts.length;
    // A source must actually identify the candidate's catalogue number/code, not just name the subject.
    const sourceIdentifier=candidate.collector_number || candidate.product_code;
    const linked=sourceIdentifier?contains(quote,sourceIdentifier):independent.length>=3;
    const ok=proof && linked;
    return {...candidate,proof:ok,evidence_sources:evidence,matched_physical_terms:independent,
      rejection:ok?'':!evidence.length?'no_relevant_cited_source':mismatches.length||codeMismatch||conflicts.length?'physical_conflict':!exact?'family_or_compatible':'insufficient_linkage',
      score:ok?(idMatches.length?95:92):Math.min(79,35+independent.length*8+evidence.length*8)};
  }
  function canonical(c){
    let subject=' '+norm(c.model)+' ';
    for(const t of [c.brand,c.family,c.collector_number,c.product_code].filter(Boolean))subject=subject.replace(' '+norm(t)+' ',' ');
    return [c.brand,c.family,subject.trim().split(/\s+/).sort().join(' '),collector(c.collector_number),c.product_code,c.variant].map(norm).join('|');
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
      const c=valid[0],variant=base.kind==='card'?base.variant:(c.variant || base.variant);
      Object.assign(out,{status:'identified',title:c.model,model:c.model,brand:c.brand || base.brand,family:c.family || base.family,variant,
        source_confirmed_catalog_number:c.collector_number || '',source_confirmed_product_code:c.product_code || '',
        model_confidence:c.score,family_confidence:Math.max(90,base.family_confidence || 0),market_ready:true,model_verified:true,family_mode:false,
        normalized_query:[c.brand,c.model,variant].filter(Boolean).join(' '),identification_sources:c.evidence_sources,
        verification_summary:'Identità collegata ai dati fotografici tramite fonte web pertinente.',missing_information:[],next_photo_request:null});
    }else{
      Object.assign(out,{status:'uncertain',model_verified:false,market_ready:false,normalized_query:'',model_confidence:Math.min(79,base.model_confidence || 0),
        identification_sources:candidates.flatMap(c=>c.evidence_sources).slice(0,6),verification_summary:reply?.summary || 'Verifica web incompleta; lettura fotografica conservata.',
        missing_information:list(reply?.missing_information),next_photo_request:reply?.next_photo_request || null});
    }
    return out;
  }
  const str={type:'string'};
  const schema={type:'object',additionalProperties:false,properties:{summary:str,missing_information:{type:'array',maxItems:3,items:str},next_photo_request:{type:['string','null']},
    candidates:{type:'array',maxItems:3,items:{type:'object',additionalProperties:false,properties:{brand:str,family:str,model:str,variant:str,collector_number:str,product_code:str,
      relation:{type:'string',enum:['exact_product','compatible','family']},conflicts:{type:'array',maxItems:4,items:{type:'object',additionalProperties:false,properties:{field:{type:'string',enum:['collector_number','product_code','text','variant','family','subject']},observed:str,source:str},required:['field','observed','source']}},
      sources:{type:'array',maxItems:3,items:{type:'object',additionalProperties:false,properties:{url:str,quote:str},required:['url','quote']}}},
      required:['brand','family','model','variant','collector_number','product_code','relation','conflicts','sources']}}},required:['summary','missing_information','next_photo_request','candidates']};
  const api={observations,literals,query,assess,result,schema,safeUrl};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckWeb264=api;
})(typeof window!=='undefined'?window:globalThis);
