/* Compose presentation from resolved fields, without changing closure decisions. */
(function(root){
'use strict';
const list=v=>Array.isArray(v)?v:[],clean=v=>String(v||'').trim();
const uncertainty=/\b(?:unclear|uncertain|unconfirmed|unknown|unresolved|not (?:yet )?confirmed|non confermat[oa]|non determinabil[ei]|da verificare)\b/i;
function compose(base,photo=base){
 if(!base||base.slab_verification?.state==='confirmed'||base.core_identity?.status!=='confirmed')return base;
 let out={...base},fields=[...list(base.core_identity.fields),...list(base.catalogue_data)],field=k=>fields.find(f=>f.field===k&&f.value&&f.verification!=='pending_physical');
 const family=field('family')?.value||base.family,subject=field('subject')?.value||base.identity_keys?.subject?.value;
 const number=field('catalog_number')?.value||base.identity_keys?.number?.value;
 let date=field('year')|| (base.source_confirmed_year?{value:base.source_confirmed_year,origin:'catalogue',kind:'year'}:null);
 if(!date){
  const seasons=list(photo?.photo_clues).filter(c=>c.role==='season'&&c.certainty==='clear');
  const values=[...new Set(seasons.map(c=>c.text.match(/\b(?:19|20)\d{2}(?:[-/]\d{2,4})?\b/)?.[0]).filter(Boolean))];
  if(values.length===1)date={value:values[0],kind:'season',origin:'photo',quote:seasons[0].text,image_index:seasons[0].image_index};
 }
 const editions=root.FlipCheckEditions||(typeof require==='function'?require('./editions.js'):null);
 if(!date)date=editions?.releaseDate?.(base)||null;
 if(!date){
  const reads=list(photo?.photo_clues).filter(c=>c.role==='copyright'&&c.certainty==='clear');
  const dates=reads.map(c=>{
   const years=[...new Set(c.text.match(/\b(?:19|20)\d{2}\b/g)||[])];
   // A dedicated publisher copyright is not the whole franchise's copyright history.
   const publisher=c.text.match(/(?:©|copyright)\s*((?:19|20)\d{2})\s+Wizards(?: of the Coast)?\b/i);
   return years.length===1?{value:years[0],kind:'copyright',origin:'photo',quote:c.text,image_index:c.image_index}:publisher?{value:publisher[1],kind:'copyright',origin:'photo',quote:c.text,image_index:c.image_index}:null;
  }).filter(Boolean);
  if(new Set(dates.map(d=>d.value)).size===1)date=dates[0];
 }
 if(date&&base.observed_year&&String(base.observed_year.value)!==String(date.value))out.observed_year={...base.observed_year,verification:'superseded',resolved_value:date.value};
 if(date){out.identity_date={...date,kind:date.kind||date.number_kind||'year'};out.identity_keys={...base.identity_keys,date:out.identity_date};}
 const exact=base.exact_identity_status==='confirmed'||base.catalogue_verified&&base.printing_check?.complete!==false&&base.variant_needs_verification!==true;
 if(exact&&base.kind==='card'){
  const old=base.variant||'',parts=old.split(/[;|·]+/).map(clean).filter(t=>t&&!uncertainty.test(t));
  const labels=[...list(base.printing_check?.labels)];
  const observed=list(photo.physical_observations).filter(o=>o.entity==='target'&&o.certainty==='clear'&&o.image_index>=1&&['finish','pattern'].includes(o.feature)).map(o=>o.text);
  if(photo.pokemon_printing?.is_pokemon&&observed.some(t=>/holo|olograf/i.test(t))&&!observed.some(t=>/non[- ]?holo|not holographic|non olograf|reverse|invers|matte|opaca/i.test(t))&&!parts.some(t=>/holo|olograf/i.test(t)))labels.push('Holo');
  out.variant=[...new Set([...parts,...labels])].join(' · ');
  if(old!==out.variant)out.initial_variant_text=base.initial_variant_text||old;
 }
 if(base.kind==='card'&&family&&subject&&number){
  out.family=family;out.model=[date?.value,family,'#'+String(number).replace(/^#+/,''),subject].filter(Boolean).join(' · ');out.title=out.model;
  out.core_identity={...base.core_identity,model:out.model};
  if(base.normalized_query)out.normalized_query=[out.model,out.variant,photo?.pokemon_printing?.language,base.physical_serial?'/'+base.physical_serial.print_run:''].filter(Boolean).join(' ');
 }
 if(!exact&&base.kind==='card'&&base.core_identity?.status==='confirmed'&&!base.next_photo_request&&list(base.missing_information).some(t=>/visiv|pattern|colore|finitura|distinguere/i.test(t)))out.next_photo_request='Fotografa il fronte inclinato alla luce, mantenendo visibili il disegno della finitura e tutti i bordi; includi un eventuale seriale.';
 if(base.kind==='card')out.card_identity={manufacturer:base.brand||null,subject:subject||null,set:family||null,number:number||null,date:out.identity_date||null,language:photo.pokemon_printing?.language||base.language||null,variant:out.variant||null,serial:base.physical_serial||null,printing:base.printing_check?.labels||[]};
 // Keep the initial model estimate as diagnostic history; verification is not a new probability.
 if(base.catalogue_core_verified&&field('family')){out.initial_family_confidence=base.initial_family_confidence??base.family_confidence;out.family_verified=true;out.family_confidence=null;}
 if((base.object_unit==='box'||/\bbox\b/i.test(base.category||''))&&!exact&&base.variant_needs_verification){
  out.next_photo_request=base.next_photo_request||photo?.next_photo_request||'Fotografa retro e lato della confezione: numero di pacchetti, carte per pacchetto e dicitura completa del contenuto garantito.';
 }
 return out;
}
const api={compose};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckIdentityFinal=api;
})(typeof window!=='undefined'?window:globalThis);
