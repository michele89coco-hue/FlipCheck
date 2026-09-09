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
  out.push({id:'lens-'+(out.length+1),position:x.position??null,title,url:link,image_url:url(x.image_url||x.image?.link),thumbnail:url(x.thumbnail),snippet:String(x.snippet||'').slice(0,1000),origin:'searchapi_google_lens',identity_verified:false});if(out.length===60)break;
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
 check('number',p.number,a.numbers,E.numbersMatch);if(l.domain!=='sports')check('language',p.language,a.languages,E.printingLanguageCompatible201);check('year',p.year,a.years);
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

// OCR and page text rank hypotheses only. No reference text enters the photo ledger.
function candidateFacts209(text){
 const t=String(text||''),codes=unique((t.match(/\b(?:SWSH\s*\d{1,4}|SM\s*\d{1,4}|XY\s*\d{1,4}|SVP\s*\d{1,4}|(?:OP|ST|EB|PRB)\d{2}-\d{3}|P-\d{3}|CS\d+[a-z]?C?)\b/gi)||[]).map(x=>x.replace(/\s/g,'').toUpperCase()));
 const fractions=unique((t.match(/\b[A-Z]*\d{1,4}\s*\/\s*[A-Z]*\d{1,4}\b/gi)||[]).map(E.number));
 const labelled=unique([...t.matchAll(/(?:#|\bNo\.?\s*)([A-Z]*\d{1,4})\b/gi)].map(m=>E.number(m[1])));
 return {codes,fractions,labelled,years:unique(t.match(/\b(?:19|20)\d{2}\b/g)||[])};
}
// Choose a front from physical observations only; never from marketplace metadata.
function frontPlan213(reading,count){
 const observations=reading.observations||[],features=reading.features||[],regions=reading.object_regions||[];
 const views=Array.from({length:count},(_,i)=>{const image_index=i+1,obs=observations.filter(o=>o.image_index===image_index&&o.certainty==='clear');
  const front=obs.filter(o=>o.zone==='front').length*3+features.filter(f=>f.image_index===image_index&&f.certainty==='clear'&&['artwork','border'].includes(f.zone)).length*2;
  const back=obs.filter(o=>o.zone==='back').length*3;
  return {image_index,score:front-back,front,back};}).sort((a,b)=>b.score-a.score||a.image_index-b.image_index);
 const declared=(reading.image_views||[]).filter(v=>v.view==='front'&&v.certainty==='clear'&&v.image_index>=1&&v.image_index<=count),best=declared.length===1?views.find(v=>v.image_index===declared[0].image_index):views[0],r=regions.find(r=>r?.image_index===best?.image_index&&r.certain&&r.width>0&&r.height>0);
 return {image_index:best?.image_index||1,region:r||null,reason:declared.length===1?'classified_front':best?.front&&best.score>0&&(!views[1]||best.score>views[1].score)?'physical_front_evidence':'front_uncertain',views};
}
function referenceSupport213(c,ref){
 if(!ref?.image_data)return null;
 const features=list(c.features).filter(f=>f.agrees===true&&f.certainty==='clear'&&f.original&&f.reference);
 if(!features.some(f=>['artwork','layout','shape'].includes(f.field)))return null;
 return {reference_id:ref.id,source:ref.url,original_image_index:c.original_image_index||null,scope:'partial_visual_support',identity_proof:false,features,reference_reading:c.reference_reading||{},conflicts:list(c.conflicts)};
}
function family214(value,brand,year,title){
 let v=String(value||'').trim();const original=v,source=norm(title),date=v.match(/^(?:19|20)\d{2}(?:[-/]\d{2,4})?\s+/)?.[0];
 if(date&&E.season(date)===E.season(year)&&source.includes(norm(year)))v=v.slice(date.length).trim();
 if(v&&(' '+source+' ').includes(' '+norm(v)+' '))return {value:v,grounded:true,raw:original};
 const b=norm(brand),n=norm(v);if(b&&n.startsWith(b+' ')&&source.split(' ').includes(b))v=v.split(/\s+/).slice(String(brand).trim().split(/\s+/).length).join(' ');
 if(!v||!(' '+source+' ').includes(' '+norm(v)+' '))return {value:original,grounded:false};
 return {value:v,grounded:true,raw:original};
}
function filterOcr214(refs,l){
 const ranked=rankOcr209(refs,l),eligible=[],reserve=[],excluded=[];
 for(const ref of ranked){const text=[ref.title,ref.snippet,ref.ocr?.text,ref.page_text].filter(Boolean).join(' ').trim(),reasons=[];
  const facts=ref.ocrRank.facts,physical=l.pick('collector_number')?.value;
  // Bare OCR numerals are not identifiers. Only labelled/full codes can eliminate a reference.
  if(physical&&ref.ocrRank.reasons.includes('identifier_differs'))reasons.push('incompatible_identifier');
  const model=l.pick('model_code')?.value||l.pick('sku')?.value;
  if(model&&!norm(text).includes(norm(model))&&/\b(?:model|modello|sku)\s*[:#]/i.test(text))reasons.push('incompatible_model');
  if(!text)reasons.push('no_associated_text');
  if(reasons.length)excluded.push({id:ref.id,reasons});
  else if(ref.ocrRank.score>0)eligible.push(ref);else reserve.push(ref);
 }
 return {eligible,reserve,excluded};
}
function rankOcr209(refs,l){
 const keys=E.keyValues(l),physical=E.number(l.pick('collector_number')?.value||''),setCodes=keys.setCodes.map(x=>x.replace(/\s/g,'').toUpperCase());
 return refs.map((ref,index)=>{
  const facts=candidateFacts209([ref.title,ref.snippet,ref.ocr?.text,ref.page_text].filter(Boolean).join(' ')),ids=[...(l.domain==='sports'?[]:facts.fractions),...facts.labelled,...facts.codes.filter(x=>!/^CS/.test(x))];
  let score=0;const reasons=[];
  if(physical&&ids.length){if(ids.some(x=>E.numbersMatch(x,physical))){score+=100;reasons.push('identifier_agrees');}else{score-=80;reasons.push('identifier_differs');}}
  if(setCodes.length&&facts.codes.some(x=>setCodes.includes(x))){score+=60;reasons.push('set_agrees');}
  if(keys.year&&facts.years.includes(keys.year))score+=8;
  if(keys.subject&&norm([ref.title,ref.ocr?.text].join(' ')).includes(norm(keys.subject)))score+=35;
  const model=l.pick('model_code')?.value||l.pick('sku')?.value;if(model&&norm([ref.title,ref.ocr?.text,ref.page_text].join(' ')).includes(norm(model))){score+=100;reasons.push('model_agrees');}
  // Unknown/unreadable remains eligible; language never filters discovery.
  return {...ref,ocrRank:{score,reasons,facts,index}};
 }).sort((a,b)=>b.ocrRank.score-a.ocrRank.score||a.ocrRank.index-b.ocrRank.index);
}
// Normalize descriptions, not identifiers. Physical and catalogue values remain separate.
function appearance212(v){return norm(v).replace(/\b(?:verde|verdi|grun|vert)\b/g,'green').replace(/\b(?:argento|argentato|silver)\b/g,'silver').replace(/\b(?:rosso|rossi|rouge)\b/g,'red').replace(/\b(?:blu|azzurro|bleu)\b/g,'blue').replace(/\b(?:oro|dorato)\b/g,'gold').replace(/\b(?:anniversario)\b/g,'anniversary').replace(/\bprizms\b/g,'prizm');}
function yearReading212(value,season){const a=E.season(value),b=E.season(season);if(!a||!b)return true;if(a===b)return true;if(/^\d{4}$/.test(a)&&/^\d{4}-\d{2}$/.test(b)){const end=b.slice(0,2)+b.slice(-2);return a===b.slice(0,4)||a===end;}return false;}
// A reference can challenge a reading, but only two original-photo views can replace it.
function reconcileOriginal208(l,readings,crops){
 const accepted=[],rejected=[];
 const fields=['subject','collector_number','set_code','rarity_text','copyright','language','model_code','serial'];
 for(const row of list(readings)){
  const crop=crops.find(c=>c.id===row.crop_id),field=row.field;
  const full=String(row.full_text||'').trim(),detail=String(row.crop_text||'').trim();
  const sameView=field==='language'?!!E.language(full)&&!!E.language(detail)&&E.printingLanguageCompatible201(E.language(full),E.language(detail)):norm(full)===norm(detail);
  const valid=crop&&crop.image_index===row.image_index&&fields.includes(field)&&row.evidence_found===true&&row.certainty==='clear'&&full&&sameView;
  if(field==='subject'&&(/^(?:CHARACTER|LEADER|EVENT|STAGE)\b/i.test(full)||full.includes('\n')||l.domain==='onepiece'&&full.includes('/'))){rejected.push({field,reason:'subject_contains_other_fields'});continue;}
  if(!valid){rejected.push({field,reason:'original_views_not_confirmed'});continue;}
  let value=field==='collector_number'?E.number(detail):field==='language'?E.language(full):detail;
  if(!value||field==='collector_number'&&!E.numberParts(value)||field==='serial'&&!E.serial(value)){rejected.push({field,reason:'invalid_value'});continue;}
  // Conflicting replies for the same field/image never win by array order.
  if(list(readings).some(o=>o!==row&&o.field===field&&o.image_index===row.image_index&&o.evidence_found===true&&o.certainty==='clear'&&norm(o.full_text)===norm(o.crop_text)&&norm(o.crop_text)!==norm(detail))){rejected.push({field,reason:'conflicting_original_views'});continue;}
  const old=l.active(field).filter(a=>a.image_index===row.image_index&&['vision','focused_vision','local_ocr','identity_band','lens_original_reread'].includes(a.source));
  if(field==='language'&&value==='zh'){const specific=old.find(a=>/^zh-(hans|hant)$/.test(a.value));if(specific)value=specific.value;}
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
 const tag=l.domain==='sports'?'':({it:'ITA',en:'ENG',ja:'JPN',zh:'CHN','zh-hans':'CHN-S','zh-hant':'CHN-T',de:'DEU',fr:'FRA',es:'SPA',ko:'KOR'})[result.language]||result.language?.toUpperCase();
 result.title=unique([alias.value,l.pick('set_code')?.value,result.card_identity?.number,result.card_identity?.date,result.variant,tag]).join(' · ');
 result.identity_display=result.title;result.localized_subject=alias.value;result.display_language=titleLanguage;result.language_suffix=tag;return result;
}
// Build 206: only actually downloaded, compared references can become evidence.
function visualEntries206(reply,refs,l){
 const accepted=[],rejected=[],support=[],keys=E.keyValues(l),physical=l.domain==='pokemon'?E.pokemonKeys204(l):{subject:keys.subject,number:l.pick('collector_number')?.value,year:keys.year,language:keys.language};
 const seen=new Set();
 for(const c of list(reply?.comparisons)){
  const ref=refs.find(r=>r.id===c.reference_id),e={...(c.identity||{})},reasons=[];
  const sports=l.domain==='sports';
  const partial=referenceSupport213(c,ref);if(partial)support.push(partial);
  if(!ref||!ref.image_data||seen.has(c.reference_id)){rejected.push({id:c.reference_id,reasons:['unknown_or_duplicate_reference']});continue;}seen.add(c.reference_id);
  if(c.match!==true||c.ambiguous!==false||c.title_matches_image!==true||list(c.conflicts).length)reasons.push('unverified_image');
  if(c.original_image_index!==undefined&&(!Number.isInteger(c.original_image_index)||c.original_image_index<1||c.original_image_index>(l.base.uploaded_image_count||3)))reasons.push('invalid_original_image');
  if(c.original_view&&c.reference_view&&c.original_view!=='unknown'&&c.reference_view!=='unknown'&&c.original_view!==c.reference_view)reasons.push('different_views');
  const features=list(c.features).filter(f=>f.agrees===true&&f.certainty==='clear'&&f.original&&f.reference);
  const kinds=new Set(features.map(f=>f.field));
  if(kinds.size<2||!['artwork','layout','shape'].some(f=>kinds.has(f))||!['text','identifier','symbols','configuration'].some(f=>kinds.has(f)))reasons.push('insufficient_independent_details');
  const title=(ref.title||'')+' '+(ref.snippet||'')+' '+(ref.page_text||'');
  // Validate each value against actual supplied text, never an ellipsis invented in a quote.
  const groundedField=field=>{const value=norm(e[field]);return !!value&&(' '+norm(title)+' ').includes(' '+value+' ');};
  if(l.domain==='onepiece'&&!groundedField('family')&&/\bone piece\b/.test(norm(title))&&/^one piece(?: card game| promo)?$/.test(norm(e.family)))e.family='One Piece';
  const normalizedFamily=family214(e.family,e.brand,e.year,title);if(normalizedFamily.grounded)e.family=normalizedFamily.value;
  const fieldProof={};
  for(const field of ['subject','family']){if(!groundedField(field))reasons.push('ungrounded_'+field);else fieldProof[field]={value:e[field],origin:'reference_text',url:ref.page_url||ref.url};}
  const releaseYear=groundedField('year')?E.season(e.year):'';
  if(releaseYear)fieldProof.year={value:releaseYear,origin:'reference_text',url:ref.page_url||ref.url};
  const original=c.original_reading||{},reference=c.reference_reading||{};
  const suffix208=v=>String(v||'').match(/(?:VMAX|VSTAR|GX|EX|ex|V)\s*$/)?.[0]?.trim()||'';
  if(suffix208(original.subject)&&suffix208(reference.subject)&&suffix208(original.subject)!==suffix208(reference.subject))reasons.push('different_reference_subject_suffix');
  if(physical.subject&&(!original.subject||!E.subjectMatch(original.subject,physical.subject)))reasons.push('different_original_subject');
  if(physical.number&&((!original.number&&!(sports&&c.original_view==='front'))||original.number&&!E.numbersMatch(original.number,physical.number)))reasons.push('different_original_number');
  if(physical.year&&original.year&&!(sports?yearReading212(original.year,physical.year):E.season(original.year)===physical.year))reasons.push('different_original_year');
  if(physical.year&&e.year&&physical.year!==E.season(e.year)&&!(l.domain==='pokemon'&&releaseYear))reasons.push('different_year');
  if(!sports&&physical.language&&reference.language&&!E.printingLanguageCompatible201(physical.language,reference.language))reasons.push('different_reference_language');
  if(!sports&&physical.language&&original.language&&!E.printingLanguageCompatible201(physical.language,original.language))reasons.push('different_original_language');
  const card=!['generic','sealed'].includes(l.domain);
  if(card){
   if(!sports&&!physical.language)reasons.push('original_language_unresolved');
   if(physical.year&&reference.year&&!(sports?yearReading212(reference.year,physical.year):E.season(reference.year)===physical.year))reasons.push('different_reference_year');
   if(l.domain!=='pokemon'&&reference.year&&e.year&&!(sports?yearReading212(reference.year,e.year):E.season(reference.year)===E.season(e.year)))reasons.push('different_reference_year');
   const sourceNumber=sports&&groundedField('number')&&!!releaseYear&&kinds.has('artwork')&&kinds.has('configuration');
   if(!e.number||reference.number&&!E.numbersMatch(e.number,reference.number)||!reference.number&&!sourceNumber)reasons.push('unreadable_reference_identifier');
   if(physical.number&&!E.numbersMatch(e.number,physical.number))reasons.push('different_number');
   if(!physical.number&&!(sports&&sourceNumber))reasons.push('original_identifier_unresolved');
   if(!reference.subject)reasons.push('unreadable_reference_subject');
  }
  if(e.subset&&!groundedField('subset')&&!(sports&&norm(e.proof?.subset)===norm(e.subset)+'s'&&(' '+norm(title)+' ').includes(' '+norm(e.proof.subset)+' '))){e.subset='';}
  if(e.subset&&groundedField('subset'))fieldProof.subset={value:e.subset,origin:'reference_text',url:ref.page_url||ref.url};
  let objectVerified=false;
  if(['generic','sealed'].includes(l.domain)){
   const model=l.pick('model_code')?.value||l.pick('sku')?.value||l.pick('barcode')?.value;
   const literalModel=model&&norm(title).includes(norm(model))&&features.some(f=>['text','identifier'].includes(f.field)&&norm(f.original).includes(norm(model))&&norm(f.reference).includes(norm(model)));
   const visualModel=features.length>=3&&kinds.has('configuration')&&(kinds.has('text')||kinds.has('identifier'))&&(kinds.has('shape')||kinds.has('layout'))&&!!e.number&&groundedField('number');
   if(model&&!norm(title).includes(norm(model)))reasons.push('different_model_code');
   objectVerified=!!(literalModel||visualModel);
   if(!objectVerified)reasons.push('object_model_not_distinguished');
  }
  if(reasons.length){rejected.push({id:ref.id,reasons:unique(reasons)});continue;}
  // Keep reference readings and original observations separate. No candidate title enters the photo ledger.
  const source={url:ref.url,title:ref.title,provider:'searchapi_visual_comparison'};
  const copyrightYear=E.season(original.year)&&E.season(original.year)===E.season(reference.year)?E.season(reference.year):'';
  fieldProof.number={value:e.number,origin:reference.number?'reference_image':'reference_text',reference_id:ref.id,url:ref.url};
  if(copyrightYear)fieldProof.copyright_year={value:copyrightYear,origin:'compared_images',reference_id:ref.id};
  const entry={object_identity_verified:objectVerified,local_appearance:c.local_appearance||null,view_evidence:{original_image_index:c.original_image_index||null,original_view:c.original_view||'unknown',reference_view:c.reference_view||'unknown'},subject:e.subject,family:e.family,number:e.number,year:releaseYear,copyright_year:copyrightYear,field_proof:fieldProof,brand:e.brand||'',subset:e.subset||'',subset_known:!!e.subset,
   language:E.language(reference.language),aliases:unique([reference.subject,physical.subject]),identifier_type:'collector',variants:[],
   grounded:true,entry_quote:ref.title+' '+ref.snippet,reference_page:ref.page_text?{url:ref.page_url||ref.url,text:ref.page_text}:null,source,source_tier:'lens_visual_verified',image_url:ref.image_url||ref.thumbnail,
   requires_image_confirmation:true,visual_reference_id:ref.id,display_names:e.display_names||{},visual_proof:features,reference_reading:reference};
  if(['sports','onepiece','tcg'].includes(l.domain)&&e.subset){
   const tokens=appearance212(e.subset).split(' ').filter(t=>!['leader','character','event','stage','p'].includes(t));
   const significant=tokens.filter(t=>!['prizm','prizms','parallel','promo'].includes(t));
   const variantFeatures=features.filter(f=>['configuration','layout','text','symbols'].includes(f.field));
   const matched=significant.length&&significant.every(t=>variantFeatures.some(f=>appearance212(f.original).split(' ').includes(t)&&appearance212(f.reference).split(' ').includes(t))||c.local_appearance?.comparison?.frame_color_agrees&&c.local_appearance.comparison.frame_color===t);
   if(matched){
    const name=tokens.join(' '),variant={id:ref.id+':variant',name,visual_required:true,image_url:entry.image_url,source};
    entry.variants=[variant];entry.subset=name;entry.field_proof.subset={value:name,origin:'reference_text_and_compared_images',reference_id:ref.id};
    l.add('catalogue_variant',variant.id,{source:'lens_image_comparison',certainty:'clear',image_index:1,reference_source:ref.url,reference_id:ref.id});
   }
  }
  // This explicitly denotes a comparison, never a new photographed identifier.
  l.add('catalogue_core',E.coreKey(entry),{source:'lens_image_comparison',certainty:'clear',image_index:1,reference_source:ref.url,reference_id:ref.id});
  accepted.push(entry);
 }
 return {accepted,rejected,support};
}
function present206(result,entries,l,titleLanguage='it'){
 if(!result.card_identity)return result;if(result.core_identity?.status!=='confirmed')return partialTitle208(result,l,titleLanguage);
 const entry=entries.find(e=>e.visual_reference_id&&E.familyKey(e.family)===E.familyKey(result.family)&&E.numbersMatch(e.number,result.card_identity.number));if(!entry)return result;
 const names=entry.display_names||{},name=String(names[titleLanguage]||names.en||entry.subject).trim(),physical=l.pick('subject')?.value||'';
 const suffix=v=>String(v).match(/(?:VMAX|VSTAR|GX|EX|ex|V)\s*$/)?.[0]?.trim()||'';
 const displayName=suffix(physical)&&suffix(name)!==suffix(physical)?physical:name;
 const language=result.language,tag=l.domain==='sports'?'':({it:'ITA',en:'ENG',ja:'JPN',de:'DEU',fr:'FRA',es:'SPA',ko:'KOR',zh:'CHN','zh-hans':'CHN-S','zh-hant':'CHN-T'})[language]||language?.toUpperCase()||'Lingua da verificare';
 const rawSet=l.pick('set_code')?.value||'',setCode=rawSet.length===1&&String(result.card_identity.number).startsWith(rawSet+'-')?'':rawSet,parts=[displayName,setCode,result.card_identity.number,result.card_identity.date,result.family,result.card_identity.subset,result.variant];
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
const api={family214,filterOcr214,frontPlan213,referenceSupport213,appearance212,yearReading212,candidateFacts209,rankOcr209,reconcileOriginal208,retrievalPool208,partialTitle208,waitForService207,visualEntries206,present206,normalize,attributes,evaluate,select,ranked,fallbackReason,url};
if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckLens=api;
})(typeof globalThis!=='undefined'?globalThis:this);
