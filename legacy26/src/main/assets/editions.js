/* Targeted Pokémon printing checks. No catalogue lookup and no additional API call. */
(function(root) {
  'use strict';
  const state = {type:'string',enum:['present','absent','unclear','not_applicable']};
  const string = {type:'string'};
  const image = {type:'integer',minimum:0,maximum:3};
  const properties = {
    is_pokemon:{type:'boolean'}, language:string, set_name:string,
    card_type:{type:'string',enum:['pokemon','trainer','energy','other']},
    first_edition_stamp:state, stamp_image:image, stamp_location:string, stamp_text:string,
    artwork_shadow:state, shadow_image:image, shadow_location:string,
    copyright_text:string, copyright_image:image,
    slab_text:string, slab_image:image
  };
  const schema = {type:['object','null'],additionalProperties:false,properties,required:Object.keys(properties)};
  const prompt = `CONTROLLO MIRATO EDIZIONE POKEMON, NELLA STESSA LETTURA:
Restituisci pokemon_printing=null per oggetti, carte sportive e altri TCG. Per Pokemon usa i campi dello schema. is_pokemon deve riferirsi alla carta fotografata.
Leggi lingua e set; osserva separatamente il timbro 1st Edition, l'ombra STAMPATA a destra/in basso del riquadro illustrazione e l'intera riga copyright. Present/absent richiedono la zona nitida e scoperta; se tagliata, coperta da slab/riflesso o troppo piccola usa unclear. Non trasformare una zona non leggibile in assenza. Indici immagine 1..3; 0 se non osservato. Indica posizione e testo/simbolo letterale, non quello dell'annuncio o dell'interfaccia telefono.
Timbro: sulle Pokemon vintage occidentali cerca sotto l'illustrazione a sinistra; sulle Energie in alto a destra, sugli Allenatori in basso a sinistra. Per stampe giapponesi usa la posizione e il simbolo appropriati alla serie, non il modello occidentale. Un numero 1 isolato nel testo, nello stadio evolutivo o nei danni NON e' il timbro. Su altri TCG non applicare queste regole.
Shadowless: la distinzione qui riguarda il Base Set inglese originale. Non dedurla dalla sola assenza del timbro, dalla rarita', dal nome del Pokemon o dalla luminosita'. Riporta l'ombra del riquadro e il copyright come prove indipendenti. Non generalizzare ad altre lingue/set/riproduzioni moderne. Per Allenatori/Energie il criterio dell'ombra del riquadro non basta: usa unclear/not_applicable e trascrivi il copyright.
First Edition e Shadowless sono due attributi separati: il timbro non dimostra da solo l'assenza d'ombra, e l'assenza d'ombra non dimostra il timbro. Non dedurre Unlimited da una zona coperta. Conserva in slab_text SOLO le parole di edizione/stampa LETTE sulla slab con il relativo indice; non usarle come se fossero un timbro visto sulla carta. Se vedi chiaramente le prove, descrivi la variante; altrimenti conserva marca/set/nome/numero e indica solo la zona di edizione incerta.`;
  const clean = value => String(value || '').trim();
  const norm = value => clean(value).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
  const strip = value => clean(value)
    .replace(/\b(?:1st\s*edition|first\s*edition|prima\s*edizione|1[ªa°]\s*edizione|shadowless|shadowed|unlimited)\b/gi,'')
    .replace(/\s*([|·,])\s*(?=[|·,]|$)/g,'').replace(/^[|·,\s]+|[|·,\s]+$/g,'').replace(/\s+/g,' ').trim();
  function isOriginalBaseSet(value) {
    const set=norm(value).replace(/\s*\((?:inferred|inference|dedotto|dedotta|ipotizzato|ipotesi)\)\s*$/i,'')
      .replace(/[™®:·,()]/g,' ').replace(/\s+/g,' ').trim();
    // Catalogue prefixes vary; keep the whole-string match so Base Set 2 and reprints stay excluded.
    return /^(?:1999\s+)?(?:pokemon\s+(?:(?:game|tcg|trading card game)\s+)?)?(?:base\s*set|set\s*(?:di\s*)?base)(?:\s*(?:1999|original|originale))?$/.test(set);
  }
  function contradictsPrinting(value,result) {
    const text=norm(value);
    return (/\bshadowless\b/.test(text)&&result.shadow==='present')
      || (/\bshadowed\b/.test(text)&&result.shadow==='absent')
      || (/\b(?:1st|first|prima)\s*(?:edition|edizione)\b/.test(text)&&result.stamp==='absent')
      || (/\bunlimited\b/.test(text)&&(result.stamp==='present'||result.shadow==='absent'));
  }
  function cataloguePrinting(identity,printing) {
    let set_name=identity.family||printing.set_name;
    // Some catalogues shorten the original set to "Base". Require a verified entry
    // in the 102-card set; preserve the literal catalogue quote separately.
    const facts=identity.core_identity?.fields||identity.catalogue_data||[];
    if(printing.is_pokemon&&identity.catalogue_core_verified&&norm(set_name)==='base'&&
      facts.some(f=>f.field==='catalog_number'&&/^\d+\s*\/\s*102$/.test(clean(f.value))))set_name='Base Set';
    return {...printing,set_name};
  }
  function evaluate(p, count) {
    if (!p || p.is_pokemon !== true) return null;
    const located = (i, location) => Number.isInteger(i) && i >= 1 && i <= count && clean(location).length > 0;
    const stampLocated = located(p.stamp_image, p.stamp_location);
    const validStampText = /(?:edition|edizione|édition)/i.test(clean(p.stamp_text));
    const stamp = stampLocated && p.first_edition_stamp === 'present' && validStampText ? 'present'
      : stampLocated && p.first_edition_stamp === 'absent' ? 'absent'
      : p.first_edition_stamp === 'not_applicable' ? 'not_applicable' : 'unclear';
    const language = norm(p.language);
    const english = /^(english|inglese|en)$/.test(language);
    const base = isOriginalBaseSet(p.set_name);
    const applicable = english && base;
    const borderLocated = located(p.shadow_image,p.shadow_location);
    const copyrightLocated = Number.isInteger(p.copyright_image) && p.copyright_image >= 1 && p.copyright_image <= count;
    const copyright = clean(p.copyright_text);
    // A legible early copyright line provides a second printing cue. Never infer it from web.
    const earlyCopyright = copyrightLocated && /1995/.test(copyright) && /(?:1996|\b96\b)/.test(copyright)
      && /(?:1998|\b98\b)/.test(copyright) && /(?:1999|\b99\b)/.test(copyright) && !/2000|20[1-9]\d/.test(copyright);
    let shadow = 'not_applicable';
    if (applicable) {
      shadow = p.card_type === 'pokemon' && borderLocated && p.artwork_shadow === 'present' ? 'present'
        : p.card_type === 'pokemon' && borderLocated && p.artwork_shadow === 'absent' && earlyCopyright ? 'absent' : 'unclear';
    }
    const labels = [];
    if (stamp === 'present') labels.push('1st Edition');
    if (shadow === 'absent') labels.push('Shadowless');
    if (shadow === 'present' && stamp === 'present') labels.push('Shadowed');
    if (applicable && shadow === 'present' && stamp === 'absent') labels.push('Unlimited');
    const missing = [];
    if (stamp === 'unclear') missing.push('zona del timbro di edizione sul fronte');
    if (applicable && shadow === 'unclear') missing.push(p.card_type === 'pokemon'
      ? 'bordo destro del riquadro e riga copyright in basso' : 'riga copyright in basso: il solo bordo non distingue questa stampa');
    const slab = located(p.slab_image,'label') ? clean(p.slab_text) : '';
    const slabNorm = norm(slab);
    const contradiction = !!slab && ((/shadowless/.test(slabNorm) && shadow === 'present')
      || /(?:1st|first|prima)\s*(?:edition|edizione)/.test(slabNorm) && stamp === 'absent'
      || /unlimited/.test(slabNorm) && (stamp === 'present' || shadow === 'absent'));
    return {stamp,shadow,applicable,labels,missing,slab,contradiction,
      complete:missing.length === 0 && !contradiction,
      stampLocation:clean(p.stamp_location), shadowLocation:clean(p.shadow_location), copyright};
  }
  function apply(identity, printing, count) {
    if (!identity || identity.kind !== 'card') return identity;
    const result = evaluate(printing,count);
    if (!result) return identity;
    const out = Object.assign({},identity);
    // Keep the original v26 core identity and confidence. Only printing assertions are adjusted.
    for (const key of ['title','model','variant','normalized_query']) out[key] = strip(out[key]);
    out.variant = [out.variant,...result.labels].filter(Boolean).join(' · ');
    if (out.normalized_query) out.normalized_query = [out.normalized_query,...result.labels].join(' ');
    out.printing_check = result;
    out.pokemon_printing = printing;
    if(result.complete&&(identity.catalogue_core_verified||identity.catalogue_verified)){
      const updated=(identity.visual_candidates||[]).map(c=>{
        if(c.superseded)return c;
        const contradicted=contradictsPrinting(c.variant,result);
        const oldPrintingRejection=c.decision==='different'&&!contradicted&&(c.conflicts||[]).length>0&&c.conflicts.every(f=>f.scope==='variant'&&/shadow|ombra|edition|edizione/i.test(f.reason||''));
        if(!contradicted&&!oldPrintingRejection)return c;
        return {...c,accepted:false,superseded:true,superseded_reason:'original_photo_printing',previous_decision:c.decision,printing_verification:contradicted?'contradicted_by_original':'prior_rejection_superseded'};
      });
      if(identity.visual_candidates)out.visual_candidates=updated;
      if(updated.some(c=>c.superseded))out.printing_resolution={...identity.printing_resolution,origin:'original_photo',labels:result.labels,superseded_candidate_count:updated.filter(c=>c.superseded).length};
      if(identity.candidate_models)out.candidate_models=identity.candidate_models.filter(c=>!contradictsPrinting(c.model,result));
      out.variant_proof={origin:'original_photo',kind:'pokemon_printing',labels:result.labels,stamp_image:printing.stamp_image,shadow_image:printing.shadow_image,copyright_image:printing.copyright_image};
      out.identity_basis={...identity.identity_basis,variant:'physical_evidence'};
    }
    if (!result.complete) { out.market_ready = false; out.normalized_query = ''; out.variant_check='pending'; }
    if (identity.catalogue_verified && result.complete) {
      // Web labels cannot override the independently located stamp, border and copyright.
      // Retain rejected assertions for diagnostics instead of displaying them as recovered facts.
      const rejected=(identity.catalogue_data||[]).filter(f=>['model','variant'].includes(f.field)&&contradictsPrinting(f.value,result));
      if(rejected.length){
        out.excluded_catalogue_fields=[...(identity.excluded_catalogue_fields||[]),...rejected];
        out.catalogue_data=identity.catalogue_data.filter(f=>!rejected.includes(f));
        out.printing_resolution={origin:'original_photo',labels:result.labels,catalogue_conflict_corrected:true};
      }
      if(identity.core_identity)out.core_identity={...identity.core_identity,model:strip(identity.core_identity.model),fields:(identity.core_identity.fields||[]).filter(f=>!contradictsPrinting(f.value,result))};
      out.variant_check='confirmed';
      out.unresolved_identity_fields=(identity.unresolved_identity_fields||[]).filter(f=>!['variant','family'].includes(f));
    }
    if(Array.isArray(out.catalogue_data))out.catalogue_data=out.catalogue_data.map(f=>
      ['model','variant'].includes(f.field)&&/\b(?:1st\s*edition|first\s*edition|shadowless|shadowed|unlimited)\b/i.test(f.value)
        ?{...f,verification:result.complete?'confirmed_physical':'pending_physical'}:f);
    return out;
  }
  const api = {schema,prompt,evaluate,apply,isOriginalBaseSet,contradictsPrinting,cataloguePrinting};
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.FlipCheckEditions = api;
})(typeof window !== 'undefined' ? window : globalThis);
