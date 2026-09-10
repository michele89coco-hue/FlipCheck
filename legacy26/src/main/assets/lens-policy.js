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
 const features=list(c.features).filter(f=>f.agrees===true&&f.certainty==='clear'&&f.original&&f.reference&&!(f.field==='identifier'&&/not visible|not readable|unreadable|non visibile|non leggibile/i.test(f.original+' '+f.reference)));
 if(!features.some(f=>['artwork','layout','shape'].includes(f.field)))return null;
 return {reference_id:ref.id,source:ref.url,original_image_index:c.original_image_index||null,scope:'partial_visual_support',identity_proof:false,features,reference_reading:c.reference_reading||{},conflicts:list(c.conflicts)};
}
function family214(value,brand,year,title){
 let v=String(value||'').trim();const original=v,source=norm(title),date=v.match(/^(?:19|20)\d{2}(?:[-/]\d{2,4})?\s+/)?.[0];
 if(date&&E.season(date)===E.season(year)&&source.includes(norm(year)))v=v.slice(date.length).trim();
 if(v&&(' '+source+' ').includes(' '+norm(v)+' '))return {value:v,grounded:true,raw:original};
 const withoutSport=v.replace(/\s+(?:basketball|baseball|football|soccer|hockey)$/i,'');
 if(withoutSport!==v&&(' '+source+' ').includes(' '+norm(withoutSport)+' '))return {value:withoutSport,grounded:true,raw:original};
 const b=norm(brand),n=norm(v);if(b&&n.startsWith(b+' ')&&source.split(' ').includes(b))v=v.split(/\s+/).slice(String(brand).trim().split(/\s+/).length).join(' ');
 if(!v||!(' '+source+' ').includes(' '+norm(v)+' ')){
  // Generic descriptors/manufacturer placement may disappear; distinctive
  // tokens such as XL, Chrome, Update, Select and Prizm must remain.
  const tokens=familyTokens217(original,brand),words=source.split(' ');
  const positions=tokens.map(t=>words.map(w=>w==='adrenalin'?'adrenalyn':w).indexOf(t)),known=tokens.length>0&&positions.every(i=>i>=0);
  if(known&&Math.max(...positions)-Math.min(...positions)<=tokens.length+3){
   const phrase=words.slice(Math.min(...positions),Math.max(...positions)+1).join(' ');
   const allowed=new Set([...tokens,...norm(brand).split(' '),'panini','pokemon','trading','card','cards','game','basketball','baseball','football','soccer','hockey']);
   if(phrase.split(' ').every(t=>allowed.has(t==='adrenalin'?'adrenalyn':t)))return {value:phrase,grounded:true,raw:original,normalization:'equivalent_product_tokens'};
  }
  return {value:original,grounded:false};
 }
 return {value:v,grounded:true,raw:original};
}
function familyTokens217(value,brand=''){
 const generic=new Set([...norm(brand).split(' '),'panini','pokemon','trading','card','cards','game','basketball','baseball','football','soccer','hockey']);
 return unique(E.familyKey(value).replace(/\badrenalin\b/g,'adrenalyn').split(' ').filter(t=>t&&!generic.has(t)&&!/^\d+$/.test(t)));
}
function filterOcr214(refs,l){
 const ranked=rankOcr209(refs,l),eligible=[],reserve=[],excluded=[];
 for(const ref of ranked){const text=[ref.title,ref.snippet,ref.ocr?.text,ref.page_text].filter(Boolean).join(' ').trim(),reasons=[];
  const facts=ref.ocrRank.facts,physical=l.pick('collector_number')?.value;
  // Bare OCR numerals are not identifiers. Only labelled/full codes can eliminate a reference.
  if(physical&&ref.ocr?.state==='ok'&&ref.ocrRank.reasons.includes('identifier_differs')){if(ref.ocrRank.nameMatch&&ref.ocrRank.productMatch&&identifierChallenges220(ranked,l).some(x=>x.id===ref.id))ref.identifier_review=true;else reasons.push('incompatible_identifier');}
  const model=l.pick('model_code')?.value||l.pick('sku')?.value;
  if(model&&!norm(text).includes(norm(model))&&/\b(?:model|modello|sku)\s*[:#]/i.test(text))reasons.push('incompatible_model');
  if(!text)reasons.push('no_associated_text');
  if(reasons.length)excluded.push({id:ref.id,reasons});
  else if(ref.ocrRank.score>0)eligible.push(ref);else reserve.push(ref);
 }
 return {eligible,reserve,excluded};
}
function identifierChallenges220(refs,l){
 if(l.domain!=='sports'||!l.pick('collector_number'))return [];
 const ranked=rankOcr209(refs,l),physical=l.pick('collector_number').value;
 const matches=ranked.filter(r=>r.ocrRank.nameMatch&&r.ocrRank.productMatch&&r.ocrRank.reasons.includes('identifier_differs'));
 return matches.filter(r=>r.ocrRank.facts.labelled.some(n=>!E.numbersMatch(n,physical)&&(l.active('collector_number').some(a=>a.source==='local_ocr'&&E.numbersMatch(a.value,n))||new Set(matches.filter(x=>x.ocrRank.facts.labelled.some(m=>E.numbersMatch(n,m))).map(x=>x.url)).size>=2)));
}
function rankOcr209(refs,l){
 const keys=E.keyValues(l),physical=E.number(l.pick('collector_number')?.value||''),setCodes=keys.setCodes.map(x=>x.replace(/\s/g,'').toUpperCase());
 return refs.map((ref,index)=>{
  const evidenceText=norm([ref.title,ref.snippet,ref.ocr?.text,ref.page_text].filter(Boolean).join(' '));
  const names=unique([keys.subject,...(l.domain==='pokemon'?E.pokemonAliases204(l):[])]),nameMatch=names.some(n=>{const tokens=norm(n).split(' ').filter(Boolean);return tokens.length&&tokens.every(t=>/[a-z]/.test(t)?new RegExp('(?:^|[^a-z0-9])'+t+'(?:$|[^a-z0-9])').test(evidenceText):evidenceText.includes(t));});
  const productTokens=unique(keys.products.flatMap(p=>familyTokens217(p,l.pick('brand')?.value)).filter(t=>!/^(?:road|world|cup)$/.test(t)));
  const productMatch=productTokens.length>0&&productTokens.every(t=>evidenceText.replace(/\badrenalin\b/g,'adrenalyn').split(' ').includes(t));
  const facts=candidateFacts209([ref.title,ref.snippet,ref.ocr?.text,ref.page_text].filter(Boolean).join(' ')),ids=[...(l.domain==='sports'?[]:facts.fractions),...facts.labelled,...facts.codes.filter(x=>!/^CS/.test(x))];
  let score=0;const reasons=[];
  if(physical&&ids.length){if(ids.some(x=>E.numbersMatch(x,physical))){score+=100;reasons.push('identifier_agrees');}else{score-=80;reasons.push('identifier_differs');}}
  if(setCodes.length&&facts.codes.some(x=>setCodes.includes(x))){score+=60;reasons.push('set_agrees');}
  if(keys.year&&facts.years.some(y=>y===keys.year||l.domain==='sports'&&E.sportsSeason215(keys.year,y)))score+=8;
  if(l.domain==='sports'){const t=appearance212(ref.title);for(const color of keys.colors){if(t.split(' ').includes(color))score+=30;else if(/\b(green|purple|silver|red|blue|gold|orange)\b/.test(t))score-=45;}
   if(/\b(?:box|boxes|case|pack|packs|break|digital|nft)\b/.test(t))score-=150;}
  if(/\/(?:b|keywords|discover)\//.test(ref.url||''))score-=60;
  if(nameMatch){score+=35;reasons.push('subject_agrees');}if(productMatch)reasons.push('product_agrees');
  const model=l.pick('model_code')?.value||l.pick('sku')?.value;if(model&&norm([ref.title,ref.ocr?.text,ref.page_text].join(' ')).includes(norm(model))){score+=100;reasons.push('model_agrees');}
  // Unknown/unreadable remains eligible; language never filters discovery.
  return {...ref,ocrRank:{nameMatch,productMatch,score,reasons,facts,index}};
 }).sort((a,b)=>Number(b.ocrRank.nameMatch)-Number(a.ocrRank.nameMatch)||Number(b.ocrRank.productMatch)-Number(a.ocrRank.productMatch)||b.ocrRank.score-a.ocrRank.score||a.ocrRank.index-b.ocrRank.index);
}
// Normalize descriptions, not identifiers. Physical and catalogue values remain separate.
function appearance212(v){return norm(v).replace(/\b(?:verde|verdi|grun|vert)\b/g,'green').replace(/\b(?:argento|argentato|silver)\b/g,'silver').replace(/\b(?:rosso|rossi|rouge)\b/g,'red').replace(/\b(?:blu|azzurro|bleu)\b/g,'blue').replace(/\b(?:oro|dorato)\b/g,'gold').replace(/\b(?:anniversario)\b/g,'anniversary').replace(/\bprizms\b/g,'prizm');}
function yearReading212(value,season){const a=E.season(value),b=E.season(season);if(!a||!b)return true;if(a===b)return true;if(/^\d{4}$/.test(a)&&/^\d{4}-\d{2}$/.test(b)){const end=b.slice(0,2)+b.slice(-2);return a===b.slice(0,4)||a===end;}return false;}
// A reference can challenge a reading, but only two original-photo views can replace it.
function reconcileOriginal208(l,readings,crops){
 const accepted=[],rejected=[];
 const fields=['subject','product','brand','team','pokedex_number','collector_number','set_code','rarity_text','copyright','language','model_code','serial'];
 for(const row of list(readings)){
  const crop=crops.find(c=>c.id===row.crop_id),field=E.observationRole220(l,l.domain!=='pokemon'&&row.field==='pokedex_number'?'collector_number':row.field,row.crop_text);
  const full=String(row.full_text||'').trim(),detail=String(row.crop_text||'').trim();
  const sameView=field==='language'?!!E.language(full)&&!!E.language(detail)&&E.printingLanguageCompatible201(E.language(full),E.language(detail)):norm(full)===norm(detail);
  const valid=crop&&crop.image_index===row.image_index&&fields.includes(field)&&row.evidence_found===true&&row.certainty==='clear'&&full&&sameView;
  if(field==='subject'&&(/^(?:CHARACTER|LEADER|EVENT|STAGE)\b/i.test(full)||full.includes('\n')||l.domain==='onepiece'&&full.includes('/'))){rejected.push({field,reason:'subject_contains_other_fields'});continue;}
  if(!valid){rejected.push({field,reason:'original_views_not_confirmed'});continue;}
  let value=['collector_number','pokedex_number'].includes(field)?E.number(detail.replace(/^(\d+)\s*;.*$/,'$1')):field==='language'?E.language(full):detail;
  if(field==='subject'&&l.domain==='sports'){
   const head=value.split(';')[0].trim();if(value.includes(';')&&list(l.base.observations).some(o=>o.field==='subject'&&o.certainty==='clear'&&E.subjectMatch(o.text,head)))value=head;
   // Remove only independently observed team text. Retain the literal OCR in raw/original_views.
   for(const team of l.evidence('team')){const t=norm(team.value),v=norm(value);if(t&&v.endsWith(' '+t))value=value.slice(0,value.length-team.value.length).trim();}
  }
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
 E.reconcileIdentifiers201(l);
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
 result.title=unique([alias.value,l.pick('set_code')?.value,result.card_identity?.number,result.card_identity?.date,result.family,result.card_identity?.subset,result.variant,tag]).join(' · ');
 result.identity_display=result.title;result.localized_subject=alias.value;result.display_language=titleLanguage;result.language_suffix=tag;return result;
}
// 221: distinguish the depicted people from the identity of a package or a multi-subject panel.
function panel221(l){return l.base.object_unit==='panel';}
function subjectGround221(value,text,l){
 const full=' '+norm(text)+' ',parts=String(value||'').split(/\s*[;&]\s*/).filter(Boolean);
 if(!panel221(l)&&l.domain!=='sealed')return full.includes(' '+norm(value)+' ')||wordsGround222(value,text);
 return parts.length>0&&parts.every(p=>full.includes(' '+norm(p)+' ')||panel221(l)&&[...p.matchAll(/["“]([^"”]+)["”]/g)].some(m=>full.includes(' '+norm(m[1])+' ')));
}
function originalSubject221(value,physical,l){return E.subjectMatch(value,physical)||panel221(l)&&String(value).split(/\s*[;&]\s*/).some(v=>E.subjectMatch(v,physical));}
function flattenedTitle221(parts){const seen=new Set();return parts.flatMap(p=>String(p||'').split(/\s*·\s*/)).filter(p=>{const k=norm(p);if(!k||seen.has(k))return false;seen.add(k);return true;}).join(' · ');}
// Field-aware normalization: source prose remains attached to each candidate.
function wordsGround222(value,text){const words=norm(value).split(' ').filter(Boolean),hay=new Set(norm(text).split(' '));return words.length>0&&words.every(w=>hay.has(w));}
function sourceText222(value,l){return l.domain==='pokemon'?String(value||'').replace(/\bbase pok[eé]mon cards?\b/ig,'Base Set').replace(/\bset base\b/ig,'Base Set'):String(value||'');}
function objectFamily222(value,l){let v=String(value||'').replace(/^(?:19|20)\d{2}(?:[-/]\d{2,4})?\s+/,'');
 if(l.domain==='sealed')v=v.replace(/\b(?:basketball|baseball|football|soccer|hockey|hobby|jumbo|blaster|mega|value|box|boxes)\b/ig,' ').replace(/\s+/g,' ').trim();
 if(E.publication222(l))v=v.replace(/\b(?:fumetto|comic|book|libro|volume)\b/ig,' ').replace(/\s+/g,' ').trim();
 return E.canonicalSet222(v,l.domain);
}
function brandEvidence222(value,features,l,title){
 const key=norm(E.publisher222(value));if(!key)return false;
 const physical=l.evidence('publisher').concat(l.evidence('brand'));
 const photo=physical.some(a=>norm(E.publisher222(a.value))===key);
 const paired=features.some(f=>['text','layout','configuration'].includes(f.field)&&wordsGround222(key,f.original)&&wordsGround222(key,f.reference));
 return (photo||paired)&&(wordsGround222(key,title)||paired);
}
// Build 206: only actually downloaded, compared references can become evidence.
function visualEntries206(reply,refs,l){
 const accepted=[],rejected=[],support=[],keys=E.keyValues(l),physical=l.domain==='pokemon'?E.pokemonKeys204(l):{subject:keys.subject,number:l.pick('collector_number')?.value,year:keys.year,language:keys.language};
 const seen=new Set();
 for(const c of list(reply?.comparisons)){
  const ref=refs.find(r=>r.id===c.reference_id),e={...(c.identity||{})},rawIdentity={...e},reasons=[];
  const sports=l.domain==='sports';
  const partial=referenceSupport213(c,ref);if(partial)support.push(partial);
  if(!ref||!ref.image_data||seen.has(c.reference_id)){rejected.push({id:c.reference_id,reasons:['unknown_or_duplicate_reference']});continue;}seen.add(c.reference_id);
  if(c.match!==true||c.ambiguous!==false||c.title_matches_image!==true||list(c.conflicts).length)reasons.push('unverified_image');
  if(c.original_image_index!==undefined&&(!Number.isInteger(c.original_image_index)||c.original_image_index<1||c.original_image_index>(l.base.uploaded_image_count||3)))reasons.push('invalid_original_image');
  if(c.original_view&&c.reference_view&&c.original_view!=='unknown'&&c.reference_view!=='unknown'&&c.original_view!==c.reference_view&&!(['sealed','generic'].includes(l.domain)&&['front','whole'].includes(c.original_view)&&['front','whole'].includes(c.reference_view)))reasons.push('different_views');
  const features=list(c.features).filter(f=>f.agrees===true&&f.certainty==='clear'&&f.original&&f.reference&&!(f.field==='identifier'&&/not visible|not readable|unreadable|non visibile|non leggibile/i.test(f.original+' '+f.reference)));
  const kinds=new Set(features.map(f=>f.field));
  const original={...(c.original_reading||{})},reference={...(c.reference_reading||{})};
  const savedSubject=l.pick('subject');
  if(!original.subject&&savedSubject&&savedSubject.image_index===(c.original_image_index||1)){
   original.subject=savedSubject.value;
   l.record('comparison_reading_retained',{reference_id:ref.id,field:'subject',observation:savedSubject.id,image_index:savedSubject.image_index});
  }
  l.record('comparison_readings',{reference_id:ref.id,image_index:c.original_image_index||1,original:c.original_reading||{},reference:c.reference_reading||{}});
  const sportsLayout=sports&&kinds.has('artwork')&&kinds.has('layout')&&original.subject&&reference.subject&&E.subjectMatch(original.subject,reference.subject);
  if(kinds.size<2||!['artwork','layout','shape'].some(f=>kinds.has(f))||!['text','identifier','symbols','configuration'].some(f=>kinds.has(f))&&!sportsLayout)reasons.push('insufficient_independent_details');
  const title=(ref.title||'')+' '+(ref.snippet||'')+' '+(ref.page_text||'');
  // Validate each value against actual supplied text, never an ellipsis invented in a quote.
  e.family=objectFamily222(e.family,l);
  if(!e.brand&&(l.domain==='sealed'||E.publication222(l)))e.brand=l.pick('publisher')?.value||l.pick('brand')?.value||'';
  if(l.domain==='sealed')e.subject=e.family; // Depicted players stay in image readings.
  const groundedField=field=>{const value=norm(e[field]);return !!value&&(field==='subject'&&l.domain!=='sealed'?subjectGround221(e[field],title,l):field==='year'?!!E.comparisonYear222(e.year)&&[...title.matchAll(/\b(?:19|20)\d{2}(?:\s*[-/]\s*(?:\d{4}|\d{2}))?\b/g)].some(m=>E.season(m[0])===E.season(e.year)):field==='family'||field==='subject'&&l.domain==='sealed'?wordsGround222(e[field],sourceText222(title,l))||l.domain==='pokemon'&&E.canonicalSet222(rawIdentity.family,l.domain)===e.family&&wordsGround222(rawIdentity.family,title):(' '+norm(title)+' ').includes(' '+value+' '));};
  if(panel221(l))e.family=String(e.family||'').replace(/^(?:19|20)\d{2}\s+/,'').replace(/^(?:Swedish|Italian|English|German|French|Spanish)\s+/i,'');
  if(l.domain==='onepiece'&&!groundedField('family')&&/\bone piece\b/.test(norm(title))&&/^one piece(?: card game| promo)?$/.test(norm(e.family)))e.family='One Piece';
  if(!e.year&&/^\d{4}(?:[-–]\d{2,4})?$/.test(String(e.proof?.year||''))&&(' '+norm(title)+' ').includes(' '+norm(e.proof.year)+' '))e.year=e.proof.year;
  const normalizedFamily=family214(e.family,e.brand,e.year,title);if(normalizedFamily.grounded)e.family=normalizedFamily.value;
  if(l.domain==='sealed'){const product=keys.products.find(p=>E.productEquivalent217(objectFamily222(p,l),objectFamily222(rawIdentity.family,l))||E.productEquivalent217(objectFamily222(p,l),e.brand+' '+objectFamily222(rawIdentity.family,l)));if(product){e.family=objectFamily222(product,l);e.subject=e.family;}}
  const fieldProof={};
  for(const field of ['subject','family']){if(!groundedField(field))reasons.push('ungrounded_'+field);else fieldProof[field]={value:e[field],origin:'reference_text',url:ref.page_url||ref.url};}
  const releaseYear=groundedField('year')?E.season(e.year):'';
  if(releaseYear)fieldProof.year={value:releaseYear,origin:'reference_text',url:ref.page_url||ref.url};
  // A typed jersey observation must never become a conflicting card identifier.
  if(sports){
   const ratings=l.evidence('statistics_number');
   if(ratings.some(a=>E.numbersMatch(a.value,original.number)))original.number='';
   if(ratings.some(a=>E.numbersMatch(a.value,reference.number))){reference.number='';if(!groundedField('number'))e.number='';}
  }
  if(sports&&/^\d+$/.test(reference.number)&&list(c.features).some(f=>{const t=[f.original,f.reference].join(' ');return /jersey|maglia|kit/i.test(t)&&new RegExp('(?:number[- ]*|No[. ]*)?'+reference.number+'(?:\\D|$)').test(t);})){reference.jersey_number=reference.number;reference.number='';if(E.numbersMatch(e.number,reference.jersey_number)&&!groundedField('number'))e.number='';}
  const releaseCompatible=sports&&E.sportsSeason215(physical.year,e.year);
  const suffix208=v=>String(v||'').match(/(?:VMAX|VSTAR|GX|EX|ex|V)\s*$/)?.[0]?.trim()||'';
  if(suffix208(original.subject)&&suffix208(reference.subject)&&suffix208(original.subject)!==suffix208(reference.subject))reasons.push('different_reference_subject_suffix');
  if(l.domain!=='sealed'&&physical.subject&&original.subject&&!originalSubject221(original.subject,physical.subject,l))reasons.push('different_original_subject');
  if(l.domain!=='sealed'&&physical.subject&&!original.subject)reasons.push('unreadable_original_subject');
  if(physical.number&&original.number&&!E.numbersMatch(original.number,physical.number))reasons.push('different_original_number');
  if(physical.year&&E.comparisonYear222(original.year)&&!(sports?yearReading212(original.year,physical.year):E.comparisonYear222(original.year)===physical.year))reasons.push('different_original_year');
  if(physical.year&&e.year&&physical.year!==E.season(e.year)&&!releaseCompatible&&!(l.domain==='pokemon'&&releaseYear))reasons.push('different_year');
  if(!sports&&physical.language&&reference.language&&!E.printingLanguageCompatible201(physical.language,reference.language))reasons.push('different_reference_language');
  if(!sports&&physical.language&&original.language&&!E.printingLanguageCompatible201(physical.language,original.language))reasons.push('different_original_language');
  const panelNumber=panel221(l)&&!physical.number&&!e.number&&features.length>=3&&kinds.has('artwork')&&kinds.has('layout')&&kinds.has('text')&&!!releaseYear;
  const optionalNumber=panelNumber||sports&&!physical.number&&!e.number&&sportsLayout&&features.length>=3&&!!releaseYear&&keys.products.some(p=>E.productEquivalent217(p,e.family))&&(!physical.year||E.sportsSeason215(physical.year,releaseYear));
  const inferredNumber=l.domain==='pokemon'&&!physical.number&&!!e.number&&E.numbersMatch(e.number,reference.number)&&kinds.has('artwork')&&kinds.has('configuration')&&kinds.has('text')&&!!physical.language&&E.language(reference.language)===E.language(physical.language)&&!!l.pick('finish')&&features.some(f=>f.field==='configuration'&&E.finish(f.original)===l.pick('finish').value&&E.finish(f.reference)===l.pick('finish').value);
  const card=!['generic','sealed'].includes(l.domain);
  if(card){
   if(!sports&&!physical.language)reasons.push('original_language_unresolved');
   if(physical.year&&E.comparisonYear222(reference.year)&&!(sports?yearReading212(reference.year,physical.year):E.comparisonYear222(reference.year)===physical.year))reasons.push('different_reference_year');
   if(l.domain!=='pokemon'&&reference.year&&e.year&&!(sports?yearReading212(reference.year,e.year):E.season(reference.year)===E.season(e.year)))reasons.push('different_reference_year');
   const sourceNumber=sports&&groundedField('number')&&(!!releaseYear||sportsLayout&&!!physical.year&&keys.products.some(p=>E.familyKey(p).includes(E.familyKey(e.family))))&&kinds.has('artwork')&&(kinds.has('configuration')||sportsLayout&&physical.number&&E.numbersMatch(e.number,physical.number));
   if(!optionalNumber&&(!e.number||reference.number&&!E.numbersMatch(e.number,reference.number)||!reference.number&&!sourceNumber))reasons.push('unreadable_reference_identifier');
   if(physical.number&&e.number&&!E.numbersMatch(e.number,physical.number))reasons.push('different_number');
   if(!physical.number&&!optionalNumber&&!inferredNumber&&!(sports&&sourceNumber))reasons.push('original_identifier_unresolved');
   if(!reference.subject)reasons.push('unreadable_reference_subject');
  }
  if(l.domain==='pokemon'&&E.printingSubset222(e.subset)){e.claimed_printing=e.subset;e.subset='';}
  if(sports&&/^(?:rc|rookie(?: card)?|rated rookie)$/i.test(e.subset||''))e.subset='';
  if(e.subset&&!groundedField('subset')&&!(sports&&norm(e.proof?.subset)===norm(e.subset)+'s'&&(' '+norm(title)+' ').includes(' '+norm(e.proof.subset)+' '))){e.subset='';}
  if(e.subset&&groundedField('subset'))fieldProof.subset={value:e.subset,origin:'reference_text',url:ref.page_url||ref.url};
  let objectVerified=false;
  if(['generic','sealed'].includes(l.domain)){
   const model=l.pick('model_code')?.value||l.pick('sku')?.value||l.pick('barcode')?.value;
   const literalModel=model&&norm(title).includes(norm(model))&&features.some(f=>['text','identifier'].includes(f.field)&&norm(f.original).includes(norm(model))&&norm(f.reference).includes(norm(model)));
   const visualModel=features.length>=3&&kinds.has('configuration')&&(kinds.has('text')||kinds.has('identifier'))&&(kinds.has('shape')||kinds.has('layout'))&&!!e.number&&groundedField('number');
   if(model&&!norm(title).includes(norm(model)))reasons.push('different_model_code');
   const productAnchored=l.domain==='sealed'&&keys.products.some(p=>E.productEquivalent217(objectFamily222(p,l),e.family))&&!!releaseYear&&!!physical.year&&E.sportsSeason215(physical.year,releaseYear);
   const configFeatures=features.filter(f=>f.field==='configuration');
   const observedConfig=E.configuration(l.evidence('configuration').map(a=>a.value).join(' '));
   const sourceConfig=E.configuration(title+' '+features.map(f=>f.reference).join(' '));
   if(Object.keys(observedConfig).some(k=>observedConfig[k]!==null&&sourceConfig[k]!==null&&observedConfig[k]!==sourceConfig[k]))reasons.push('different_configuration');
   const format=title.match(/\b(?:Hobby|Jumbo|Blaster|Mega|Value)\s+Box\b/i)?.[0]||'';
   const describedObject=(l.domain==='sealed'||E.publication222(l))&&features.length>=(productAnchored?2:3)&&kinds.has('configuration')&&(kinds.has('artwork')||kinds.has('shape')||kinds.has('layout'))&&(productAnchored&&format||kinds.has('text')||kinds.has('layout'))&&groundedField('family')&&groundedField('subject')&&brandEvidence222(e.brand,features,l,title)&&(!model||!!literalModel);
   if(describedObject){e.object_format=format;e.configuration_quote=configFeatures.map(f=>f.reference).join(' ');fieldProof.configuration={origin:'compared_images',reference_id:ref.id,features:configFeatures};}

   objectVerified=!!(literalModel||visualModel||describedObject);
   if(!objectVerified)reasons.push('object_model_not_distinguished');
  }
  if(reasons.length){rejected.push({id:ref.id,reasons:unique(reasons)});continue;}
  // Keep reference readings and original observations separate. No candidate title enters the photo ledger.
  const source={url:ref.url,title:ref.title,provider:'searchapi_visual_comparison'};
  const copyrightYear=E.copyrightYear222(original.year)&&E.copyrightYear222(original.year)===E.copyrightYear222(reference.year)?E.copyrightYear222(reference.year):'';
  fieldProof.number={value:e.number,identifier_type:l.domain==='pokemon'?physical.number_role:'collector_number',origin:reference.number?'reference_image':'reference_text',reference_id:ref.id,url:ref.url};
  if(copyrightYear)fieldProof.copyright_year={value:copyrightYear,origin:'compared_images',reference_id:ref.id};
  const entry={normalization:{original:rawIdentity,normalized:{...e},origin:'lens_image_comparison',reference_id:ref.id},claimed_printing:e.claimed_printing||'',object_format:e.object_format||'',configuration_quote:e.configuration_quote||'',catalogue_number_inferred:inferredNumber,number_optional:optionalNumber,object_identity_verified:objectVerified,local_appearance:c.local_appearance||null,view_evidence:{original_image_index:c.original_image_index||null,original_view:c.original_view||'unknown',reference_view:c.reference_view||'unknown'},subject:e.subject,family:e.family,number:e.number,year:releaseYear,copyright_year:copyrightYear,field_proof:fieldProof,brand:e.brand||'',subset:e.subset||'',subset_known:!!e.subset,
   language:E.language(reference.language),aliases:unique([reference.subject,physical.subject]),identifier_type:l.domain==='pokemon'&&physical.number_role==='pokedex_number'?'pokedex':'collector',variants:[],
   grounded:true,entry_quote:ref.title+' '+ref.snippet,reference_page:ref.page_text?{url:ref.page_url||ref.url,text:ref.page_text}:null,source,source_tier:'lens_visual_verified',image_url:ref.image_url||ref.thumbnail,
   requires_image_confirmation:true,visual_reference_id:ref.id,display_names:e.display_names||{},visual_proof:features,reference_reading:reference};
  if(['sports','onepiece','tcg'].includes(l.domain)&&e.subset){
   const tokens=appearance212(e.subset).split(' ').filter(t=>!['leader','character','event','stage','p'].includes(t));
   const significant=tokens.filter(t=>!['prizm','prizms','parallel','promo'].includes(t));
   const variantFeatures=features.filter(f=>['configuration','layout','text','symbols'].includes(f.field));
   const genericFoil=sports&&/^(?:foil|holo|holographic|holofoil)$/i.test(e.subset)&&features.some(f=>/foil|holo|holographic/i.test(f.original)&&/foil|holo|holographic/i.test(f.reference));
   const matched=genericFoil||significant.length&&significant.every(t=>variantFeatures.some(f=>appearance212(f.original).split(' ').includes(t)&&appearance212(f.reference).split(' ').includes(t))||c.local_appearance?.comparison?.frame_color_agrees&&c.local_appearance.comparison.frame_color===t);
   if(matched){
    const name=tokens.join(' '),variant={id:ref.id+':variant',name,visual_required:true,image_url:entry.image_url,source};
    entry.variants=[variant];entry.subset=genericFoil?'':name;if(genericFoil)entry.subset_known=false;entry.field_proof.subset={value:name,origin:'reference_text_and_compared_images',reference_id:ref.id};
    l.add('catalogue_variant',variant.id,{source:'lens_image_comparison',certainty:'clear',image_index:1,reference_source:ref.url,reference_id:ref.id});
   }
  }
  if(sports){
   const description=norm(title).replace(norm(e.family),'').replace(norm(e.subject),'');
   const named=/\b(?:refractor|pulsar|parallel|gold|green|blue|red|silver|black|ice|wave|auto|autograph|patch|reprint|foil|holo|holographic)\b/.test(description);
   entry.printing_description=e.subset||named?'named':'unspecified';
  }
  // This explicitly denotes a comparison, never a new photographed identifier.
  l.add('catalogue_core',E.coreKey(entry),{source:'lens_image_comparison',certainty:'clear',image_index:1,reference_source:ref.url,reference_id:ref.id});
  accepted.push(entry);
 }
 for(const e of serialCore221(l,reply,refs))if(!accepted.some(a=>a.visual_reference_id===e.visual_reference_id)){accepted.push(e);const i=rejected.findIndex(r=>r.id===e.visual_reference_id);if(i>=0)rejected.splice(i,1);}
 return {accepted,rejected,support};
}
// Reuse actual replies and attributed images after original OCR changes.
function sportsConsensus218(l,batches,refs,checked){
 if(l.domain!=='sports'||!l.pick('subject'))return [];
 // A missing number is not a disagreement. Actual contradictory observations remain binding.
 if(l.active('serial').length||['autograph','patch'].some(f=>l.pick(f)?.value==='present'))return [];
 const permitted=new Set(['unreadable_reference_identifier','original_identifier_unresolved']);
 const candidates=[],subject=l.pick('subject').value,k=E.keyValues(l);
 for(const batch of batches)for(const c of list(batch.reply?.comparisons)){
  const ref=refs.find(r=>r.id===c.reference_id),rejection=checked.rejected.find(r=>r.id===c.reference_id);
  if(!ref?.image_data||rejection&&rejection.reasons.some(r=>!permitted.has(r)))continue;
  if(c.original_view!=='front'||c.reference_view!=='front'||c.match!==true||c.ambiguous!==false||c.title_matches_image!==true||list(c.conflicts).length)continue;
  const e=c.identity||{},features=list(c.features).filter(f=>f.agrees===true&&f.certainty==='clear'&&f.original&&f.reference),kinds=new Set(features.map(f=>f.field));
  if(!E.subjectMatch(e.subject,subject)||!E.subjectMatch(c.reference_reading?.subject,subject)||!kinds.has('artwork')||!kinds.has('layout')||features.length<3)continue;
  const text=[ref.title,ref.snippet,ref.page_text].filter(Boolean).join(' '),family=family214(e.family,e.brand,e.year,text);
  if(!family.grounded)continue;
  const brand=norm(e.brand),photoBrands=k.brands.map(norm);if(brand&&photoBrands.length&&!photoBrands.every(b=>b===brand||b.includes(brand)||brand.includes(b)))continue;
  // URL tokens are catalog evidence, never photographed OCR; accept only explicitly labelled numbers.
  const urlText=decodeURIComponent(ref.url||'').replace(/[-_]/g,' '),sourceNumber=E.number(e.number);
  const numberProof=sourceNumber&&((' '+norm(text)+' ').includes(' '+norm(sourceNumber)+' ')||new RegExp('(?:number|card|no) '+sourceNumber+'(?: |$)','i').test(urlText));
  const number=numberProof?sourceNumber:'';
  if(l.evidence('collector_number').some(a=>number&&!E.numbersMatch(a.value,number)))continue;
  let year=E.season(text.match(/\b(?:19|20)\d{2}(?:[-/]\d{2,4})?\b/)?.[0]||'');
  if(!year&&e.year&&norm(text).includes(norm(e.year)))year=E.season(e.year);
  const short=text.match(/\b(\d{2})-(\d{2})\b/);
  candidates.push({ref,c,e,family:family.value,features,number,numberProof,year,short:short?.[0],text});
 }
 const groups=[];
 for(const a of candidates){let g=groups.find(g=>familyTokens217(g[0].family,g[0].e.brand).sort().join(' ')===familyTokens217(a.family,a.e.brand).sort().join(' '));if(!g){g=[];groups.push(g);}g.push(a);}
 const out=[];
 for(const group of groups){
  const sourceKey=r=>{try{const u=new URL(r.url);return /ebay\./.test(u.hostname)&&u.pathname.match(/\d{10,}/)?'ebay:'+u.pathname.match(/\d{10,}/)[0]:u.origin+u.pathname;}catch(_){return '';}};
  const distinct=group.filter((a,i)=>sourceKey(a.ref)&&group.findIndex(b=>sourceKey(a.ref)===sourceKey(b.ref))===i&&group.findIndex(b=>(a.ref.image_url||a.ref.thumbnail)===(b.ref.image_url||b.ref.thumbnail))===i);
  if(distinct.length<2||!distinct.some(a=>a.features.some(f=>f.field==='configuration')))continue;
  const nums=unique(distinct.map(a=>a.number).filter(Boolean));if(nums.length>1)continue;
  const years=distinct.map(a=>a.year).filter(Boolean);if(!years.length||years.some(y=>!E.sportsSeason215(y,years[0])))continue;
  let year=years.sort((a,b)=>b.length-a.length)[0];const short=distinct.find(a=>a.short&&a.short.slice(0,2)===year.slice(2,4));if(short)year=year.slice(0,2)+short.short;
  if(l.evidence('season').some(a=>!E.sportsSeason215(a.value,year)))continue;
  // Do not silently resolve named parallels or serial variants as an ordinary printing.
  if(distinct.some(a=>/\b(?:refractor|prizm|pulsar|parallel|gold|green|blue|red|silver|black|ice|wave|auto|autograph|patch|reprint)\b/i.test(a.text)))continue;
  for(const a of distinct){
   const entry={subject,aliases:[subject],family:a.family,number:nums[0]||'',year,brand:k.brands.find(b=>!a.e.brand||norm(b).includes(norm(a.e.brand)))||((' '+norm(a.text)+' ').includes(' '+norm(a.e.brand)+' ')?a.e.brand:''),subset:'',subset_known:false,variants:[],grounded:true,
    source:{url:a.ref.url,title:a.ref.title,provider:'searchapi_visual_comparison'},source_tier:'lens_visual_verified',entry_quote:a.text,
    visual_reference_id:a.ref.id,image_url:a.ref.image_url||a.ref.thumbnail,visual_proof:a.features,reference_reading:a.c.reference_reading,
    view_evidence:{original_image_index:a.c.original_image_index,original_view:'front',reference_view:'front'},
    field_proof:{subject:{origin:'compared_images'},family:{origin:'reference_text'},year:{origin:'reference_text'},...(nums.length?{number:{origin:'reference_text',value:nums[0],url:distinct.find(x=>x.number)?.ref.url}}:{})},
    visual_consensus:{references:distinct.map(x=>x.ref.id),sources:distinct.map(x=>x.ref.url),number_status:nums.length?'catalogue_confirmed':'not_recovered',printing:'visually_matched'}};
   l.add('catalogue_core',E.coreKey(entry),{source:'lens_image_comparison',certainty:'clear',image_index:1,reference_source:a.ref.url,reference_id:a.ref.id});
   l.add('visual_consensus',E.coreKey(entry),{source:'lens_consensus',certainty:'clear',image_index:1,references:entry.visual_consensus.references});out.push(entry);
  }
 }
 return out;
}
function serialCore221(l,reply,refs){
 if(l.domain!=='sports'||!l.pick('serial')||!l.pick('collector_number')||!l.pick('subset')||!l.pick('subject'))return [];
 if(['autograph','patch'].some(f=>l.pick(f)?.value==='present'))return [];
 const k=E.keyValues(l),insert=l.pick('subset').value,out=[];
 for(const c of list(reply?.comparisons)){
  const ref=refs.find(r=>r.id===c.reference_id),e=c.identity||{},title=[ref?.title,ref?.snippet,ref?.page_text].join(' ');
  if(!ref?.image_data||c.original_view!=='front'||c.reference_view!=='front'||c.title_matches_image!==true||!E.subjectMatch(e.subject,k.subject)||!E.numbersMatch(e.number,l.pick('collector_number').value)||!E.sportsSeason215(k.year,e.year))continue;
  if(!k.products.some(p=>E.productEquivalent217(p,e.family))||!norm(title).includes(norm(insert))||!norm(title).includes(norm(e.subject))||!norm(title).includes(norm(e.number)))continue;
  if(!list(c.features).some(f=>f.field==='artwork'&&f.agrees===true&&f.certainty==='clear'))continue;
  // Never reinterpret a different player, set, number, autograph or patch as a parallel difference.
  if(list(c.conflicts).some(t=>!/parallel|variant|color|purple|orange|silver|serial|\/\d|configurazione|cromatica/i.test(t)||/different (?:player|subject|set|number)|autograph|patch|firma|giocatore/i.test(t)))continue;
  const source={url:ref.url,title:ref.title,provider:'searchapi_core_comparison'},entry={subject:k.subject,family:e.family,number:e.number,year:E.season(e.year),brand:e.brand,subset:insert,subset_known:true,grounded:true,source,source_tier:'lens_core_verified',entry_quote:title,variants:[],core_only:true,visual_reference_id:ref.id,display_names:e.display_names||{},aliases:[],language:'',image_url:ref.image_url};
  l.add('catalogue_core',E.coreKey(entry),{source:'lens_core_comparison',certainty:'clear',image_index:c.original_image_index||1,reference_source:ref.url,reference_id:ref.id});out.push(entry);
 }
 return out;
}
function resolveComparisons217(l,batches,refs){
 const accepted=[],rejected=[],support=[];
 for(const batch of batches){const checked=visualEntries206(batch.reply,refs.filter(r=>batch.ids.includes(r.id)),l);accepted.push(...checked.accepted);rejected.push(...checked.rejected);support.push(...checked.support);}
 accepted.push(...sportsConsensus218(l,batches,refs,{accepted,rejected}));
 const acceptedIds=new Set(accepted.map(e=>e.visual_reference_id));
 return {accepted:accepted.filter((e,i,a)=>a.findIndex(x=>x.visual_reference_id===e.visual_reference_id)===i),rejected:rejected.filter(e=>!acceptedIds.has(e.id)),support};
}
function present206(result,entries,l,titleLanguage='it'){
 if(!result.card_identity)return result;if(result.core_identity?.status!=='confirmed')return partialTitle208(result,l,titleLanguage);
 const entry=entries.find(e=>e.visual_reference_id&&E.familyKey(e.family)===E.familyKey(result.family)&&E.numbersMatch(e.number,result.card_identity.number));if(!entry)return result;
 const names=entry.display_names||{},name=String(names[titleLanguage]||names.en||entry.subject).trim(),physical=l.pick('subject')?.value||'';
 const suffix=v=>String(v).match(/(?:VMAX|VSTAR|GX|EX|ex|V)\s*$/)?.[0]?.trim()||'';
 const displayName=suffix(physical)&&suffix(name)!==suffix(physical)?physical:name;
 const language=result.language,tag=l.domain==='sports'?'':({it:'ITA',en:'ENG',ja:'JPN',de:'DEU',fr:'FRA',es:'SPA',ko:'KOR',zh:'CHN','zh-hans':'CHN-S','zh-hant':'CHN-T'})[language]||language?.toUpperCase()||'Lingua da verificare';
 const rawSet=l.pick('set_code')?.value||'',setCode=rawSet.length===1&&String(result.card_identity.number).startsWith(rawSet+'-')?'':rawSet,parts=[displayName,setCode,result.card_identity.number,result.card_identity.date,result.family,result.card_identity.subset,result.variant];
 if(result.card_identity.is_rookie)parts.push('RC');if(result.physical_serial?.value)parts.push('/'+result.physical_serial.print_run);parts.push(tag);
 result.title=flattenedTitle221(parts);result.identity_display=result.title;if(result.model)result.model=result.title;
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
function metadata215(refs,l){return rankOcr209(refs,l).map(r=>({...r,metadataStrong:r.ocrRank.nameMatch&&(r.ocrRank.reasons.includes('identifier_agrees')||r.ocrRank.productMatch)&&(!E.keyValues(l).year||!r.ocrRank.facts.years.length||r.ocrRank.facts.years.some(y=>E.sportsSeason215(E.keyValues(l).year,y)))}));}
const api={identifierChallenges220,resolveComparisons217,familyTokens217,metadata215,family214,filterOcr214,frontPlan213,referenceSupport213,appearance212,yearReading212,candidateFacts209,rankOcr209,reconcileOriginal208,retrievalPool208,partialTitle208,waitForService207,visualEntries206,present206,normalize,attributes,evaluate,select,ranked,fallbackReason,url};
if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckLens=api;
})(typeof globalThis!=='undefined'?globalThis:this);
