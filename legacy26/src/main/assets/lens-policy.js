/* Lens retrieves hypotheses. Only evidence from the original can eliminate or verify them. */
(function(root){
'use strict';
const E=root.FlipCheckCatalogueEngine||(typeof require==='function'?require('./catalogue-engine.js'):null);
const list=x=>Array.isArray(x)?x:[],norm=E.norm,unique=x=>[...new Set(x.filter(Boolean))];
function url(s){try{const u=new URL(s);return u.protocol==='https:'&&!u.username&&!u.password&&(!u.port||u.port==='443')&&!/[?&](?:.*token|.*signature|.*credential|api_key)=/i.test(u.search)?u.href:'';}catch(_){return '';}}
function normalize(packet){
 const seen=new Set(),out=[];
 const rows=Array.isArray(packet.candidates)?packet.candidates:[...list(packet.visual_matches),...list(packet.exact_matches)];
 for(const x of rows){const link=url(x.url||x.link),title=String(x.title||'').slice(0,500),key=link+' '+title;if(!link||!title||seen.has(key))continue;seen.add(key);
  out.push({id:'lens-'+(out.length+1),position:x.position??null,title,url:link,image_url:url(x.image_url||x.image?.link),thumbnail:url(x.thumbnail),snippet:String(x.snippet||'').slice(0,1000),origin:'searchapi_google_lens',identity_verified:false});if(out.length===20)break;
 }return out;
}
function attributes(c,domain=''){
 const t=c.title+' '+(c.snippet||''),s=norm(t),numbers=unique((t.match(/\b(?:[A-Z]{1,5}-)?\d{1,4}\s*\/\s*(?:[A-Z]{1,5})?\d{1,4}\b/gi)||[]).concat(t.match(/\b[A-Z]{1,5}-\d{2,4}\b/g)||[]).map(E.number));
 if(['pokemon','tcg'].includes(domain))for(const m of t.matchAll(/\b(\d{1,3})-(\d{2,3})\b/g))numbers.push(E.number(m[1]+'/'+m[2]));
 if(!numbers.length){const n=t.match(/(?:#|\bNo\.?\s*)([A-Z]*\d{1,4})\b/i);if(n)numbers.push(E.number(n[1]));}
 const languages=[];for(const [code,re] of Object.entries({de:/\b(?:german|deutsch|tedesc[oa]|ger)\b/,it:/\b(?:italian[oa]?|italien)\b/,en:/\b(?:english|inglese|englisch)\b/,ja:/\b(?:japanese|giapponese|japanisch|jpn)\b/,zh:/\b(?:chinese|cinese|chinois)\b/,fr:/\b(?:french|francais|francese)\b/,es:/\b(?:spanish|espanol|spagnol[oa])\b/}))if(re.test(s))languages.push(code);
 if(/\bITA\b|\(IT\)/.test(t)&&!languages.includes('it'))languages.push('it');
 return {numbers:unique(numbers),languages,years:unique((t.match(/\b(?:19|20)\d{2}(?:[-/]\d{2,4})?\b/g)||[]).map(E.season)),
  finish:/\breverse\b/.test(s)?'reverse':/\b(?:non[ -]?holo|non foil)\b/.test(s)?'normal':/\b(?:holo|holographic|holografica)\b/.test(s)?'holo':'',
  stamp:/\b(?:1st|first|prima) edition\b|1 edizione/.test(s)?'present':/\bunlimited\b/.test(s)?'absent':'',
  shadow:/\bshadowless\b/.test(s)?'absent':/\b(?:shadowed|with shadow)\b/.test(s)?'present':'',
  format:/\b(?:box topper|boxtopper|xxl|jumbo|oversized)\b/.test(s)?'Box Topper':'',
  anniversary:s.match(/\b(\d+)(?:st|nd|rd|th)?\s*(?:anniversary|anniversario)\b/)?.[1]||''};
}
function evaluate(c,l){
 const a=attributes(c,l.domain),keys=E.keyValues(l),p=l.domain==='pokemon'?E.pokemonKeys204(l):{subject:keys.subject,number:l.pick('collector_number')?.value,year:keys.year,language:keys.language};
 const reasons=[],matches=[],missing=[];
 const check=(field,physical,values,equal=(x,y)=>x===y)=>{if(!physical)return;if(!values.length){missing.push(field);return;}if(values.some(x=>equal(physical,x)))matches.push(field);else reasons.push('different_'+field);};
 check('number',p.number,a.numbers,E.numbersMatch);check('language',p.language,a.languages,E.printingLanguageCompatible201);check('year',p.year,a.years);
 for(const field of ['finish','stamp','shadow'])check(field,l.pick(field)?.value,a[field]?[a[field]]:[]);
 const names=unique([p.subject,...(l.domain==='pokemon'?E.pokemonAliases204(l):[])]),text=norm(c.title+' '+c.snippet);
 if(names.length){if(names.some(n=>norm(n)&&text.includes(norm(n))))matches.push('subject');else missing.push('subject');}
 // A translated name must retain V/ex/EX/GX/VMAX/VSTAR. Never inherit it from ranking.
 const suffix=p.subject?.match(/(?:VMAX|VSTAR|GX|EX|ex|V)$/)?.[0];
 if(suffix&&names.some(n=>text.includes(norm(n.replace(/(?:VMAX|VSTAR|GX|EX|ex|V)$/,''))))){
  const found=c.title.match(/\b(?:VMAX|VSTAR|GX|EX|ex|V)\b/g)||[];if(found.length&&!found.includes(suffix))reasons.push('different_subject_suffix');
 }
 const edition=l.evidence('edition_text').map(x=>x.value).join(' ').match(/\b(\d+)(?:st|nd|rd|th)?\s*(?:Anniversary|anniversario)\b/i)?.[1];check('anniversary',edition,a.anniversary?[a.anniversary]:[]);
 const code=l.pick('model_code')?.value||l.pick('sku')?.value||l.pick('barcode')?.value;
 if(code){if(text.includes(norm(code)))matches.push('model_code');else missing.push('model_code');}
 const brand=l.pick('brand')?.value;if(brand&&text.includes(norm(brand)))matches.push('brand');
 if(l.domain==='generic'&&/remote|telecomando/i.test(l.base.category||'')&&/\b(?:smart tv|oled|televisore|fernseher)\b/.test(text)&&!/remote|telecomando|daljinski|control remoto/.test(text))reasons.push('different_product_type');
 return {...c,attributes:a,eligible:!reasons.length,reasons:unique(reasons),matches:unique(matches),missing:unique(missing),identity_verified:false,
  verification_state:reasons.length?'rejected':'needs_catalogue_or_image_verification'};
}
function select(packet,l){return normalize(packet).map(c=>evaluate(c,l));}
function ranked(evaluations){return evaluations.filter(c=>c.eligible).sort((a,b)=>b.matches.length-a.matches.length);}
function fallbackReason(result){return result?.state==='ok'?'identity_not_verified':result?.state||'provider_unavailable';}

// A reference can challenge a reading, but only two original-photo views can replace it.
function reconcileOriginal208(l,readings,crops){
 const accepted=[],rejected=[];
 const fields=['subject','collector_number','set_code','rarity_text','copyright','language','model_code','serial'];
 for(const row of list(readings)){
  const crop=crops.find(c=>c.id===row.crop_id),field=row.field;
  const full=String(row.full_text||'').trim(),detail=String(row.crop_text||'').trim();
  const valid=crop&&crop.image_index===row.image_index&&fields.includes(field)&&row.evidence_found===true&&row.certainty==='clear'&&full&&norm(full)===norm(detail);
  if(!valid){rejected.push({field,reason:'original_views_not_confirmed'});continue;}
  const value=field==='collector_number'?E.number(detail):field==='language'?E.language(detail):detail;
  if(!value||field==='collector_number'&&!E.numberParts(value)||field==='serial'&&!E.serial(value)){rejected.push({field,reason:'invalid_value'});continue;}
  // Conflicting replies for the same field/image never win by array order.
  if(list(readings).some(o=>o!==row&&o.field===field&&o.image_index===row.image_index&&o.evidence_found===true&&o.certainty==='clear'&&norm(o.full_text)===norm(o.crop_text)&&norm(o.crop_text)!==norm(detail))){rejected.push({field,reason:'conflicting_original_views'});continue;}
  const old=l.active(field).filter(a=>a.image_index===row.image_index&&['vision','focused_vision','local_ocr','identity_band','lens_original_reread'].includes(a.source));
  const atom=l.add(field,value,{source:'lens_original_reread',certainty:'clear',image_index:row.image_index,region:crop.region,raw:detail,supersedes:old.map(a=>a.id),original_views:{full,detail,crop_id:crop.id}});
  accepted.push({field,value,observation:atom.id,superseded:old.map(a=>a.id)});
  if(field==='subject'){
   const suffix=v=>String(v).match(/(?:VMAX|VSTAR|GX|EX|ex|V)\s*$/)?.[0]?.trim()||'';
   for(const lang of ['it','en']){const name=String(row.display_names?.[lang]||'').trim();if(name&&/[A-Za-z]/.test(name)&&suffix(name)===suffix(value))l.add('subject_alias',name,{source:'original_translation',level:'inferred',certainty:'uncertain',image_index:row.image_index,translated_from:atom.id,display_language:lang});}
  }
 }
 l.record('original_views_reconciled',{accepted,rejected});return {accepted,rejected};
}
function retrievalPool208(evaluations){
 // Text conflicts change priority, never prevent the image from being checked.
 return [...ranked(evaluations),...evaluations.filter(c=>!c.eligible)].filter(c=>!c.reasons?.includes('different_product_type'));
}
function partialTitle208(result,l,titleLanguage){
 const subject=l.pick('subject');if(!subject)return result;
 const alias=l.values('subject_alias').find(a=>a.source==='original_translation'&&a.translated_from===subject.id&&a.display_language===titleLanguage);
 if(!alias)return result;
 const tag=({it:'ITA',en:'ENG',ja:'JPN',zh:'CHN','zh-hans':'CHN-S','zh-hant':'CHN-T',de:'DEU',fr:'FRA',es:'SPA',ko:'KOR'})[result.language]||result.language?.toUpperCase();
 result.title=unique([alias.value,l.pick('set_code')?.value,result.card_identity?.number,result.card_identity?.date,result.variant,tag]).join(' · ');
 result.identity_display=result.title;result.localized_subject=alias.value;result.display_language=titleLanguage;result.language_suffix=tag;return result;
}
// Build 206: only actually downloaded, compared references can become evidence.
function visualEntries206(reply,refs,l){
 const accepted=[],rejected=[],keys=E.keyValues(l),physical=l.domain==='pokemon'?E.pokemonKeys204(l):{subject:keys.subject,number:l.pick('collector_number')?.value,year:keys.year,language:keys.language};
 const seen=new Set(),literal=(value,text)=>!!String(value||'').trim()&&norm(text).includes(norm(value));
 for(const c of list(reply?.comparisons)){
  const ref=refs.find(r=>r.id===c.reference_id),e=c.identity||{},reasons=[];
  if(!ref||!ref.image_data||seen.has(c.reference_id)){rejected.push({id:c.reference_id,reasons:['unknown_or_duplicate_reference']});continue;}seen.add(c.reference_id);
  if(c.match!==true||c.ambiguous!==false||c.title_matches_image!==true||list(c.conflicts).length)reasons.push('unverified_image');
  const features=list(c.features).filter(f=>f.agrees===true&&f.certainty==='clear'&&f.original&&f.reference);
  const kinds=new Set(features.map(f=>f.field));
  if(kinds.size<2||!['artwork','layout','shape'].some(f=>kinds.has(f))||!['text','identifier','symbols','configuration'].some(f=>kinds.has(f)))reasons.push('insufficient_independent_details');
  const title=ref.title+' '+ref.snippet,proof=e.proof||{};
  for(const field of ['subject','family'])if(!literal(e[field],proof[field])||!literal(proof[field],title))reasons.push('ungrounded_'+field);
  if(e.year&&(!literal(e.year,proof.year)||!literal(proof.year,title)))reasons.push('ungrounded_year');
  const original=c.original_reading||{},reference=c.reference_reading||{};
  if(physical.subject&&(!original.subject||!E.subjectMatch(original.subject,physical.subject)))reasons.push('different_original_subject');
  if(physical.number&&(!original.number||!E.numbersMatch(original.number,physical.number)))reasons.push('different_original_number');
  if(physical.year&&original.year&&E.season(original.year)!==physical.year)reasons.push('different_original_year');
  if(physical.year&&e.year&&physical.year!==E.season(e.year))reasons.push('different_year');
  if(physical.language&&(!reference.language||!E.printingLanguageCompatible201(physical.language,reference.language)))reasons.push('different_reference_language');
  if(physical.language&&original.language&&!E.printingLanguageCompatible201(physical.language,original.language))reasons.push('different_original_language');
  const card=!['generic','sealed'].includes(l.domain);
  if(card){
   if(!physical.language)reasons.push('original_language_unresolved');
   if(reference.year&&e.year&&E.season(reference.year)!==E.season(e.year))reasons.push('different_reference_year');
   if(!e.number||!reference.number||!E.numbersMatch(e.number,reference.number))reasons.push('unreadable_reference_identifier');
   if(physical.number&&!E.numbersMatch(e.number,physical.number))reasons.push('different_number');
   if(!physical.number)reasons.push('original_identifier_unresolved');
   if(!reference.subject)reasons.push('unreadable_reference_subject');
  }
  if(e.subset&&(!literal(e.subset,proof.subset)||!literal(proof.subset,title)))reasons.push('ungrounded_subset');
  if(reasons.length){rejected.push({id:ref.id,reasons:unique(reasons)});continue;}
  // Keep reference readings and original observations separate. No candidate title enters the photo ledger.
  const source={url:ref.url,title:ref.title,provider:'searchapi_visual_comparison'};
  const entry={subject:e.subject,family:e.family,number:e.number,year:E.season(e.year),brand:e.brand||'',subset:e.subset||'',subset_known:!!e.subset,
   language:E.language(reference.language),aliases:unique([reference.subject,physical.subject]),identifier_type:'collector',variants:[],
   grounded:true,entry_quote:ref.title+' '+ref.snippet,source,source_tier:'lens_visual_verified',image_url:ref.image_url||ref.thumbnail,
   requires_image_confirmation:true,visual_reference_id:ref.id,display_names:e.display_names||{},visual_proof:features,reference_reading:reference};
  // This explicitly denotes a comparison, never a new photographed identifier.
  l.add('catalogue_core',E.coreKey(entry),{source:'lens_image_comparison',certainty:'clear',image_index:1,reference_source:ref.url,reference_id:ref.id});
  accepted.push(entry);
 }
 return {accepted,rejected};
}
function present206(result,entries,l,titleLanguage='it'){
 if(!result.card_identity)return result;if(result.core_identity?.status!=='confirmed')return partialTitle208(result,l,titleLanguage);
 const entry=entries.find(e=>e.visual_reference_id&&E.familyKey(e.family)===E.familyKey(result.family)&&E.numbersMatch(e.number,result.card_identity.number));if(!entry)return result;
 const names=entry.display_names||{},name=String(names[titleLanguage]||names.en||entry.subject).trim(),physical=l.pick('subject')?.value||'';
 const suffix=v=>String(v).match(/(?:VMAX|VSTAR|GX|EX|ex|V)\s*$/)?.[0]?.trim()||'';
 const displayName=suffix(physical)&&suffix(name)!==suffix(physical)?physical:name;
 const language=result.language,tag=({it:'ITA',en:'ENG',ja:'JPN',de:'DEU',fr:'FRA',es:'SPA',ko:'KOR',zh:'CHN','zh-hans':'CHN-S','zh-hant':'CHN-T'})[language]||language?.toUpperCase()||'Lingua da verificare';
 const setCode=l.pick('set_code')?.value||'',parts=[displayName,setCode,result.card_identity.number,result.card_identity.date,result.family,result.card_identity.subset,result.variant];
 if(result.card_identity.is_rookie)parts.push('RC');if(result.physical_serial?.value)parts.push(result.physical_serial.value);parts.push(tag);
 result.title=unique(parts).join(' · ');result.identity_display=result.title;if(result.model)result.model=result.title;
 result.display_language=titleLanguage;result.localized_subject=displayName;result.physical_language=language;result.language_suffix=tag;
 if(result.market_ready)result.normalized_query=result.title;
 return result;
}
// Only the free configuration GET is retried. A Lens search is never replayed here.
async function waitForService207(request,{now=()=>Date.now(),wait=ms=>new Promise(r=>setTimeout(r,ms)),guard=()=>{},onAttempt=()=>{}}={}){
 const deadline=now()+80000;let last=null,attempt=0;
 while(now()<deadline&&attempt<16){
  guard();const remaining=deadline-now();if(remaining<1000)break;
  const timeout=Math.min(25000,remaining-500);onAttempt(++attempt,timeout);
  try{last=await request(timeout);}catch(error){guard();if(!/^(?:timeout|scan_timeout|network_error)$/.test(error.message))throw error;last={status:0,state:error.message};}
  guard();
  if(![502,503,504].includes(last.status)&&!(last.status===0&&['timeout','scan_timeout','network_error'].includes(last.state)))return last;
  const delay=Math.min(5000,deadline-now());if(delay>0){await wait(delay);guard();}
 }
 return {status:0,state:'service_startup_timeout',lastState:last?.state||null};
}
const api={reconcileOriginal208,retrievalPool208,partialTitle208,waitForService207,visualEntries206,present206,normalize,attributes,evaluate,select,ranked,fallbackReason,url};
if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckLens=api;
})(typeof globalThis!=='undefined'?globalThis:this);
