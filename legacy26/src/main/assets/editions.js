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
    shadow_edges:{type:'object',additionalProperties:false,properties:{right:state,lower:state},required:['right','lower']},
    copyright_text:string, copyright_image:image,
    rarity_symbol:state, rarity_text:string, rarity_image:image, rarity_location:string,
    slab_text:string, slab_image:image
  };
  const schema = {type:['object','null'],additionalProperties:false,properties,required:Object.keys(properties)};
  const prompt = `CONTROLLO MIRATO EDIZIONE POKEMON, NELLA STESSA LETTURA:
Restituisci pokemon_printing=null per oggetti, carte sportive e altri TCG. Per Pokemon usa i campi dello schema. is_pokemon deve riferirsi alla carta fotografata.
Leggi lingua e set; osserva separatamente il timbro 1st Edition, l'ombra STAMPATA a destra/in basso del riquadro illustrazione e l'intera riga copyright. Present/absent richiedono la zona nitida e scoperta; se tagliata, coperta da slab/riflesso o troppo piccola usa unclear. Non trasformare una zona non leggibile in assenza. Indici immagine 1..3; 0 se non osservato. Indica posizione e testo/simbolo letterale, non quello dell'annuncio o dell'interfaccia telefono.
Timbro: sulle Pokemon vintage occidentali cerca sotto l'illustrazione a sinistra; sulle Energie in alto a destra, sugli Allenatori in basso a sinistra. Per stampe giapponesi usa la posizione e il simbolo appropriati alla serie, non il modello occidentale. Un numero 1 isolato nel testo, nello stadio evolutivo o nei danni NON e' il timbro. Su altri TCG non applicare queste regole.
Shadowless: la distinzione qui riguarda il Base Set inglese originale. Non dedurla dalla sola assenza del timbro, dalla rarita', dal nome del Pokemon o dalla luminosita'. shadow_edges osserva SEPARATAMENTE right e lower: present significa banda scura STAMPATA e sfalsata all'esterno della cornice, absent significa bordo visibile senza questa banda, unclear se la zona non permette di distinguerla. La sottile linea nera della cornice, lo sfondo scuro dell'illustrazione, l'ombra della custodia e un riflesso NON sono l'ombra stampata. Descrivi in shadow_location i due margini osservati. artwork_shadow deve concordare con entrambe le letture. Riporta copyright e timbro indipendentemente. Non generalizzare ad altre lingue/set/riproduzioni moderne. Per Allenatori/Energie il criterio dell'ombra del riquadro non basta: usa unclear/not_applicable e trascrivi il copyright.
Rarità: trascrivi separatamente simbolo o codice letterale (R, RR, AR, SR ecc.) con rarity_image e rarity_location. R, RR e HOLO R non significano automaticamente Reverse Holo. rarity_symbol=absent richiede la zona prevista nitida e interamente visibile, altrimenti unclear. Non dedurre No Rarity dalle promo o da una zona coperta; la logica applicherà questa distinzione soltanto alla serie compatibile. Per le sportive leggi produttore, serie, stagione, numero carta, parallelo, numerazione esemplare/tiratura e autografo come attributi distinti; non scambiare la firma stampata con un autografo autenticato.
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
    const result={...printing,set_name};
    delete result.stamp_policy;delete result.rarity_policy;
    // Edition availability is catalogue/era knowledge, not a photo observation.
    // Western 2003+ releases postdate the original 1st Edition programme. This
    // rule deliberately excludes Japanese, Korean and unspecified languages.
    const western=/^(english|inglese|en|italian|italiano|it|french|francais|francese|fr|german|deutsch|tedesco|de|spanish|espanol|spagnolo|es|portuguese|portugues|portoghese|pt|dutch|nederlands|olandese|nl)$/.test(norm(printing.language));
    const sourceDates=facts.filter(f=>f.field==='year'&&f.origin==='catalogue'&&['year','season'].includes(f.number_kind));
    const sourceYears=[...new Set(sourceDates.map(f=>clean(f.value).match(/\b(?:19|20)\d{2}\b/)?.[0]).filter(Boolean))];
    const photoYears=[...new Set(clean(printing.copyright_text).match(/\b(?:19|20)\d{2}\b/g)||[])];
    const year=sourceYears.length===1?Number(sourceYears[0]):sourceYears.length===0&&photoYears.length===1&&printing.copyright_image>=1?Number(photoYears[0]):0;
    if(identity.catalogue_core_verified&&western&&clean(set_name)&&year>=2003&&year<=2099&&!photoYears.some(y=>Number(y)>year))
      result.stamp_policy={state:'not_applicable',rule:'western_release_after_2002',year,origin:sourceYears.length?'catalogue_release':'verified_series_and_observed_copyright',evidence:sourceYears.length?sourceDates:printing.copyright_text,source:'https://www.psacard.com/articles/articleview/9498/psa-set-registry-collecting-2002-poke-mon-neo-destiny-1st-edition'};
    // Set-specific release knowledge is needed during 2002; Neo Destiny still has 1st Edition.
    const postNeo=/^(?:2002 )?(?:expedition(?: base set)?|legendary collection)$/.test(norm(set_name));
    if(identity.catalogue_core_verified&&western&&postNeo&&year===2002)
      result.stamp_policy={state:'not_applicable',rule:'western_post_neo_2002_release',year,origin:'verified_series',source:'https://www.psacard.com/articles/articleview/9498/psa-set-registry-collecting-2002-poke-mon-neo-destiny-1st-edition'};
    const japanese=/^(japanese|giapponese|ja|jp)$/.test(norm(printing.language));
    if(identity.catalogue_core_verified&&japanese&&year>=1996&&year<2001&&clean(set_name))result.stamp_policy={state:'not_applicable',rule:'japanese_release_before_2001',year,origin:sourceYears.length?'catalogue_release':'verified_series_and_observed_copyright',evidence:sourceYears.length?sourceDates:printing.copyright_text,source:'https://www.cgccards.com/news/article/10262/pokemon-first-editions/'};
    if(identity.catalogue_core_verified&&japanese&&year>=2017)
      result.stamp_policy={state:'not_applicable',rule:'japanese_release_after_2016',year,origin:sourceYears.length?'catalogue_release':'verified_series_and_observed_copyright',source:'https://www.cgccards.com/news/article/10262/pokemon-first-editions/'};
    // Basic energies lack rarity symbols in both printings; they do not identify No Rarity.
    if(identity.catalogue_core_verified&&japanese&&year===1996&&isOriginalBaseSet(set_name)&&['pokemon','trainer'].includes(printing.card_type))
      result.rarity_policy={state:'applicable',rule:'japanese_base_1996',year,source:'https://www.cgccards.com/news/article/11258/'};
    return result;
  }
  function remapCropImages186(printing,originalIndexes){
    if(!printing)return {printing,remapped:[]};
    const out={...printing},valid=new Set(originalIndexes),remapped=[];
    // Multiple crops of ONE photo are unambiguous. Never guess between different originals.
    if(valid.size===1)for(const key of ['stamp_image','shadow_image','copyright_image','slab_image','rarity_image']){
      const index=out[key];
      if(Number.isInteger(index)&&index>=1&&index<=originalIndexes.length&&!valid.has(index)){
        out[key]=originalIndexes[index-1];remapped.push({field:key,from:index,to:out[key],origin:'crop_to_original'});
      }
    }
    return {printing:out,remapped};
  }
  function observedFinish(identity){
    const reads=(identity.physical_observations||[]).filter(o=>o.entity==='target'&&o.certainty==='clear'&&['finish','pattern'].includes(o.feature)&&o.image_index>=1);
    const negative=t=>/non[- ]?holo|not holographic|non olograf|matte|opaca/i.test(t);
    const positive=t=>/holo|olograf/i.test(t)&&!negative(t);
    if(reads.some(o=>positive(o.text))&&!reads.some(o=>negative(o.text)||/reverse|invers/i.test(o.text)))return 'Holo';
    return '';
  }
  function evaluate(p, count) {
    if (!p || p.is_pokemon !== true) return null;
    const located = (i, location) => Number.isInteger(i) && i >= 1 && i <= count && clean(location).length > 0;
    const stampLocated = located(p.stamp_image, p.stamp_location);
    const validStampText = /(?:edition|edizione|édition)/i.test(clean(p.stamp_text));
    const excluded=p.stamp_policy?.state==='not_applicable';
    const stamp = excluded&&p.first_edition_stamp!=='present'?'not_applicable':stampLocated && p.first_edition_stamp === 'present' && validStampText ? 'present'
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
      // New readings must agree on both edges. Legacy records retain their old
      // schema; absent new evidence is never manufactured during replay.
      if(p.shadow_edges&&(!['present','absent'].includes(shadow)||p.shadow_edges.right!==shadow||p.shadow_edges.lower!==shadow))shadow='unclear';
    }
    const labels = [];
    if (stamp === 'present') labels.push('1st Edition');
    if (shadow === 'absent') labels.push('Shadowless');
    if (shadow === 'present' && stamp === 'present') labels.push('Shadowed');
    if (applicable && shadow === 'present' && stamp === 'absent') labels.push('Unlimited');
    const rarityLocated=located(p.rarity_image,p.rarity_location);
    const rarity=rarityLocated&&['present','absent'].includes(p.rarity_symbol)?p.rarity_symbol:'unclear';
    const rarityApplicable=p.rarity_policy?.state==='applicable';
    if(rarityApplicable&&rarity==='absent')labels.push('No Rarity Symbol');
    if(rarityApplicable&&rarity==='present')labels.push('Rarity Symbol');
    if(!rarityApplicable&&rarity==='present'&&/^(?:R|RR|RRR|AR|SAR|SR|UR|CHR|CSR|ACE)$/i.test(clean(p.rarity_text)))labels.push(clean(p.rarity_text).toUpperCase());
    const missing = [];
    if(rarityApplicable&&rarity==='unclear')missing.push('simbolo di rarità in basso a destra');
    if (stamp === 'unclear') missing.push('zona del timbro di edizione sul fronte');
    if (applicable && shadow === 'unclear') missing.push(p.card_type === 'pokemon'
      ? 'bordo destro del riquadro e riga copyright in basso' : 'riga copyright in basso: il solo bordo non distingue questa stampa');
    const slab = located(p.slab_image,'label') ? clean(p.slab_text) : '';
    const slabNorm = norm(slab);
    const contradiction = excluded&&p.first_edition_stamp==='present'||!!slab && ((/shadowless/.test(slabNorm) && shadow === 'present')
      || /(?:1st|first|prima)\s*(?:edition|edizione)/.test(slabNorm) && stamp === 'absent'
      || /unlimited/.test(slabNorm) && (stamp === 'present' || shadow === 'absent'));
    if(excluded&&p.first_edition_stamp==='present')missing.push('timbro di edizione incompatibile con la serie e l’anno verificati');
    return {stamp,shadow,rarity,rarityApplicable,applicable,labels,missing,slab,contradiction,...(p.stamp_policy?{stamp_policy:p.stamp_policy}:{}),
      complete:missing.length === 0 && !contradiction,
      stampLocation:clean(p.stamp_location), shadowLocation:clean(p.shadow_location), copyright};
  }
  function apply(identity, printing, count) {
    if (!identity || identity.kind !== 'card') return identity;
    const result = evaluate(printing,count);
    if (!result) return identity;
    if(identity.slab_verification?.state==='confirmed')return identity;
    const out = Object.assign({},identity);
    // Keep the original v26 core identity and confidence. Only printing assertions are adjusted.
    for (const key of ['title','model','variant','normalized_query']) out[key] = strip(out[key]);
    const finish=observedFinish(identity);
    if(finish&&(!out.variant||/^(?:rare\s+)?holo(?:graphic|foil)?(?:\s*\/\s*e-card)?$/i.test(out.variant))){
      if(out.variant&&out.variant!==finish)out.observed_variant_text=identity.observed_variant_text||out.variant;
      out.variant=finish;
    }
    if(result.complete)out.variant=out.variant.replace(/(?:^|[;/|·])\s*(?:non determinabile dai dati osservati|edition unclear|printing unresolved)\s*(?=$|[;/|·])/gi,'').replace(/[;/|·\s]+$/,'').trim();
    out.variant = [...new Set([out.variant,...result.labels].filter(Boolean))].join(' · ');
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
  const api = {schema,prompt,evaluate,apply,isOriginalBaseSet,contradictsPrinting,cataloguePrinting,observedFinish,remapCropImages186};
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.FlipCheckEditions = api;
})(typeof window !== 'undefined' ? window : globalThis);
