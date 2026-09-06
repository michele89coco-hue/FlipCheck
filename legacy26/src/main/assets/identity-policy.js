/* Fast photographic closure and optional recovery. Never promote a catalogue suggestion. */
(function(root){
  'use strict';
  const catalog=typeof module!=='undefined'&&module.exports?require('./tcg-reference.js'):root.FlipCheckCatalog;
  const editions=typeof module!=='undefined'&&module.exports?require('./editions.js'):root.FlipCheckEditions;
  const list=x=>Array.isArray(x)?x:[],norm=s=>String(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
  const has=(text,part)=>!!norm(part)&&(' '+norm(text)+' ').includes(' '+norm(part)+' ');
  const setKey=s=>norm(s).replace(/^pokemon /,'').replace(/^(base set|set base|set di base)$/,'base').replace(/^(expedition|spedizione)$/,'expedition base set');
  const terms=x=>[...list(x.distinctive_terms),...list(x.layout_signature).map(t=>t.term)];
  function observations(x){return list(x.identifier_observations).filter(o=>{
    if(!o?.text)return false;
    if(x.kind==='card')return ['collector_number','serial_number','slab_cert'].includes(o.role);
    return !(o.role==='model' && /^\s*(?:19|20)\d{2}\s*[/–-]\s*\d{2,4}\s*$/.test(o.text));
  });}
  function reference(x){
    if(x.pokemon_printing?.is_pokemon!==true)return {status:'unsupported'};
    const set=catalog.sets.find(s=>setKey(s.name)===setKey(x.family));if(!set)return {status:'unsupported'};
    const number=observations(x).find(o=>o.role==='collector_number'&&o.legibility==='clear')?.text ||
      terms(x).find(t=>/\bH?\d+\s*\/\s*H?\d+\b/i.test(t)) || x.model;
    const m=String(number||'').match(/\b(H?\d{1,3})\s*\/\s*(H?\d{1,3})\b/i);if(!m)return {status:'unavailable'};
    const row=set.cards.find(r=>r[0].toUpperCase()===m[1].toUpperCase());
    const names=[...new Set(catalog.sets.flatMap(s=>s.cards.filter(c=>c[2]).map(c=>c[1])))].sort((a,b)=>b.length-a.length);
    const subject=names.find(n=>has(x.model,n)||has(x.title,n));if(!subject)return {status:'unsupported'};
    // Do not match an untranslated owner/qualifier through a bare species suffix.
    if(!set.cards.some(r=>norm(r[1])===norm(subject))&&set.cards.some(r=>has(r[1],subject)))return {status:'unsupported'};
    const hp=[...terms(x).join(' ').matchAll(/\b(\d{2,3})\s*(?:HP|PV|PS)\b/gi)].map(m=>+m[1]);
    const same=!!row&&norm(row[1])===norm(subject)&&(!hp.length||hp.every(v=>!row[2]||v===+row[2]));
    const total=m[1].toUpperCase().startsWith('H')?'H32':String(set.total);
    const consistent=same&&(m[2].toUpperCase()===total || total==='H32'&&m[2]==='32');
    return {status:consistent?'consistent':'conflict',set:set.name,subject,number:m[1].toUpperCase(),
      alternatives:consistent?[]:catalog.sets.flatMap(s=>s.cards.filter(r=>norm(r[1])===norm(subject)&&r[0].toUpperCase()===m[1].toUpperCase()).map(r=>({set:s.name,number:r[0],name:r[1]}))),source:catalog.source};
  }
  const conditionOnly=t=>/condizion|condition|authent|autenticit|angol|corner|centering|conservazion|certificat|grade|grading|voto|graffi|superfic|surface|edges/i.test(t) && !/numero carta|collector number|codice modello|model code/i.test(t);
  const variantUnknown=x=>[x.variant,x.verification_summary,...list(x.missing_information)].some(t=>/parallel.*(?:not explicitly|probable|non .*stampat|non .*confermat)|(?:probabil|probable|inferred|dedott).*(?:parallel|prizm|refractor)|(?:parallel|prizm|refractor).*(?:probabil|probable|non .*confermat)/i.test(t||''));
  function decision(x,count=3){
    if(!x||x.status==='failed')return {close:false,reason:'no_identity',reference:{status:'unavailable'}};
    const ref=reference(x),printing=editions.evaluate(x.pokemon_printing,count);
    const nums=observations(x).filter(o=>o.role==='collector_number'&&o.legibility==='clear');
    const printed=terms(x),label=printed.some(t=>/^(?:no\.?|#)\s*\d|\bH?\d+\s*\/\s*H?\d+/i.test(t));
    const tuple=x.kind==='card'?(!!x.brand&&!!x.family&&!!x.model&&(nums.length>0||label)&&printed.length>=3)
      :(!!x.brand&&!!x.model&&observations(x).some(o=>['model','part_number','barcode'].includes(o.role)&&o.legibility==='clear'));
    const coreReady=!!(tuple&&x.market_ready&&x.normalized_query&&Number(x.model_confidence)>=(x.kind==='card'?90:85)&&ref.status!=='conflict');
    const close=coreReady&&(!printing||printing.complete);
    return {close,coreReady,reason:close?'complete_photo_identity':ref.status==='conflict'?'catalogue_disagreement':printing&&!printing.complete?'printing_uncertain':'identity_incomplete',reference:ref,variantUncertain:variantUnknown(x)};
  }
  function close(x,count){
    const d=decision(x,count);if(!d.close)return {...x,identity_route:d};
    const out={...x,status:'identified',core_identity_confirmed:true,identity_route:d,market_ready:true,
      variant_status:d.variantUncertain?'to_verify':'observed',additional_information:list(x.missing_information),
      missing_information:[],next_photo_request:null,verification_summary:'Identità principale riconosciuta dai dati fotografici.'};
    if(d.variantUncertain){
      out.proposed_variant=x.variant;
      out.variant=terms(x).filter(t=>/^\d+\s*\/\s*\d+$/.test(t)||/^(?:prizm|refractor)$/i.test(t)).join(' · ');
      out.normalized_query=[x.brand,x.family,x.model,out.variant].filter(Boolean).join(' ');
    }
    return out;
  }
  function cropPlan(x,count){
    const d=decision(x,count),uncertain=observations(x).filter(o=>o.legibility==='uncertain');
    if(d.close)return {regions:[],reason:'photo_identity_complete'};
    if(d.reference.status==='conflict'&&!uncertain.length)return {regions:[],reason:'resolve_catalogue_not_reread_clear_text'};
    const needs=list(x.missing_information).filter(t=>!conditionOnly(t)).join(' ');
    const regions=list(x.detail_regions).filter(r=>{
      if(r.purpose==='collector_number')return uncertain.some(o=>o.role==='collector_number')||!observations(x).some(o=>o.role==='collector_number'&&o.legibility==='clear');
      if(r.purpose==='edition')return d.reason==='printing_uncertain';
      if(r.purpose==='slab_label')return /slab|etichetta|label/i.test(needs)&&!/certificat|grade|voto/i.test(needs);
      if(r.purpose==='serial_number')return uncertain.some(o=>o.role==='serial_number');
      return /illeggibil|non (?:completamente )?leggibil|unreadable|unclear|sfocat|caratter|character|parzial/i.test(needs);
    }).slice(0,2);
    return {regions,reason:regions.length?'unreadable_identity_detail':'no_useful_reread'};
  }
  function retry(base,reply,result){
    if(result.core_identity_confirmed||result.market_ready)return false;
    return list(result.web_checks).some(c=>c.model&&list(c.evidence_sources).length>0&&(c.relation!=='family'||c.collector_number||c.product_code));
  }
  const api={observations,reference,decision,close,cropPlan,retry,conditionOnly,variantUnknown};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckIdentity265=api;
})(typeof window!=='undefined'?window:globalThis);
