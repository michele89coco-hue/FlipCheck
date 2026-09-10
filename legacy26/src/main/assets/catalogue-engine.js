/* Catalogue identity engine. Observations are append-only; only reduce() publishes an identity. */
(function(root){
'use strict';
const list=x=>Array.isArray(x)?x:[],str=x=>String(x??'').trim(),clone=x=>JSON.parse(JSON.stringify(x)),norm=x=>str(x).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[’']/g,'').replace(/[^a-z0-9\u3040-\u30ff\u3400-\u9fff]+/g,' ').trim();
const unique=x=>[...new Set(x.filter(Boolean))],same=(a,b)=>norm(a)===norm(b);
// Canonical comparison values never replace the original observation or its source.
function publication222(l){return l.domain==='generic'&&/fumetto|comic|book|libro|volume/i.test(l.base.category||'');}
function publisher222(v){return str(v).replace(/^(?:edizioni|editions|publisher|editore)\s+/i,'').trim();}
function canonicalSet222(v,domain){const t=norm(v);return domain==='pokemon'&&/^(?:pokemon (?:game )?)?(?:base set|set base|base pokemon cards?|base pokemon card set)$/.test(t)?'Base Set':str(v);}
function comparisonValue222(field,v,domain){
 if(field==='publisher')return norm(publisher222(v));
 if(field==='season')return season(v)||norm(v);
 if(['family','product'].includes(field))return productIdentity217(canonicalSet222(v,domain));
 if(['subject','work_title'].includes(field))return norm(v).split(' ').sort().join(' ');
 return norm(v);
}
function comparisonYear222(v){return /^(?:19|20)\d{2}(?:\s*[-/]\s*(?:\d{4}|\d{2}))?$/.test(str(v))?season(v):'';}
function copyrightYear222(v){const years=str(v).match(/\b(?:19|20)\d{2}\b/g)||[];return years.length?String(Math.max(...years.map(Number))):'';}
function printingSubset222(v){return !!str(v)&&!norm(v).replace(/\b(?:no symbol|no set symbol|no rarity symbol|1st edition|first edition|unlimited|shadowless|shadowed|reverse holo|reverse holographic|holo rare|rare holo|non holo|holo|holographic|rare)\b/g,'').trim();}
const presenceFields=['stamp','shadow','rarity_symbol','autograph','patch'];
// Keep the verbatim observation; only its semantic role may change.
function semanticField(domain,field,value){
 const t=norm(value);
 if(domain!=='pokemon'&&field==='pokedex_number')return 'collector_number';
 if(domain==='pokemon'&&field==='collector_number'&&/^\d+\s*[x×+−-]$/i.test(str(value)))return 'statistics_number';
 if(['onepiece','tcg'].includes(domain)&&field==='rarity_text'&&/^(?:CHARACTER|LEADER|EVENT|STAGE)$/i.test(str(value)))return 'card_type';
 if(domain==='onepiece'&&field==='rarity_text'&&/anniversary|winner|championship|pre.?release/i.test(value))return 'edition_text';
 if(['onepiece','tcg'].includes(domain)&&['set_code','model_code'].includes(field)&&/^(?:(?:OP|ST|EB|PRB)\d{2}-\d{3}|P-\d{3}|[A-Z0-9]{2,8}-[A-Z]{0,4}\d{2,5})$/i.test(str(value)))return 'collector_number';
 if(field==='copyright'&&/^illus\.?\s/i.test(str(value)))return 'illustrator';
 if(domain==='sports'&&field==='serial'&&!/^(?:unclear|unknown|unreadable|illeggibile)$/i.test(str(value))&&!serial(value)&&!/^\d{1,6}\s*\/\s*[?\d]{1,6}$/.test(str(value)))return 'activation_code';
 if(!['product','subset'].includes(field))return field;
 if(domain==='pokemon'&&(/^(?:evolves? from|evolves? into|evolve da|evoluzione di|(?:pokemon (?:di )?)?(?:stage|fase) [012])\b/.test(t)||/^(?:basic pokemon|pokemon base|trainer|allenatore|energy|energia)$/.test(t)))return 'card_type';
 if(domain==='onepiece'&&/^(?:character|leader|event|stage|counter|strike|slash|ranged|special|wisdom)(?:\s|$)/.test(t))return 'card_type';
 if(domain==='onepiece'&&field==='subset')return 'card_traits';
 if(domain==='sports'&&/^(?:rc(?: card)?|rookie(?: card)?|rated rookie|rookie shield)$/.test(t))return 'badge';
 return field;
}
// Reuse independently typed original readings; a product logo is not a second person.
function observationRole220(l,field,value){
 if(field==='season'&&/championship|career|averaged|scored|born|biograf|campionat|stagione precedente|previous season/i.test(str(value)))return 'biography_text';
 if(field==='brand'&&/(?:©|copyright|all rights reserved|printed in)/i.test(str(value)))return 'copyright';
 if(field==='subset'&&list(l.base.observations).some(o=>o.field==='product'&&o.certainty==='clear'&&(' '+norm(o.text)+' ').includes(' '+norm(value)+' ')))return 'product';
 if(field==='brand'&&list(l.base.observations).some(o=>o.field==='product'&&o.certainty==='clear'&&(' '+norm(o.text||o.value)+' ').includes(' '+norm(value)+' ')&&norm(value).split(' ').length>=2))return 'product';
 if(field==='set_code'&&/\b(?:symbol|simbolo|logo)\b/i.test(str(value)))return 'set_symbol';
 if(publication222(l)){
  if(['copyright','brand','publisher'].includes(field)&&/^(?:edizioni|editions|publisher|editore)\s+/i.test(str(value)))return 'publisher';
  if(field==='brand'&&list(l.base.observations).some(o=>o.field==='subject'&&o.certainty==='clear'&&subjectMatch(o.text||o.value,value)))return 'work_title';
 }
 if(field!=='subject')return field;
 const observations=list(l.base.observations).filter(o=>o.certainty==='clear'),names=observations.filter(o=>o.field==='subject').map(o=>o.text||o.value);
 if(!names.some(n=>!subjectMatch(n,value)))return field;
 for(const role of ['product','brand','team'])if(observations.filter(o=>o.field===role).concat(l.evidence(role)).some(o=>role==='product'?productEquivalent217(o.text||o.value,value):same(o.text||o.value,value)))return role;
 return field;
}
// A sports release year abbreviates the START of a printed season, never its end.
function sportsSeason215(a,b){const rawA=str(a),rawB=str(b);a=season(a);b=season(b);if(!a&&/^\d{2}[-/]\d{2}$/.test(rawA)&&b&&rawA.slice(0,2)===b.slice(2,4))a=b.slice(0,2)+rawA.replace('/','-');if(!b&&/^\d{2}[-/]\d{2}$/.test(rawB)&&a&&rawB.slice(0,2)===a.slice(2,4))b=a.slice(0,2)+rawB.replace('/','-');return !!a&&!!b&&(a===b||/^\d{4}-\d{2}$/.test(a)&&b===a.slice(0,4)||/^\d{4}-\d{2}$/.test(b)&&a===b.slice(0,4));}
function familyKey(value){return norm(str(value).replace(/^(?:(?:19|20)\d{2}(?:[-/]\d{2,4})?|\d{2}[-/]\d{2})\s+/,'')).replace(/^(?:panini|pokemon) /,'').replace(/ (?:basketball|baseball|football|soccer|hockey)$/,'').replace(/\b(world cup) 20(\d{2})\b/,'$1 $2');}
function productIdentity217(v){return familyKey(v).replace(/\badrenalin\b/g,'adrenalyn').split(' ').filter(t=>!['panini','pokemon','trading','card','cards','game','basketball','baseball','football','soccer','hockey'].includes(t)).sort().join(' ');}
function productEquivalent217(a,b){return !!productIdentity217(a)&&productIdentity217(a)===productIdentity217(b);}
function subsetKey(value){const t=norm(value||'Base').replace(/ checklist$/,'').replace(/^base (terrace|mezzanine|field level)$/,'$1');return /^(?:base(?: set)?(?: checklist)?|rc(?: card)?|rookie(?: card)?)$/.test(t)?'base':t;}
function distinctiveMarks(l){return unique(l.evidence('text').concat(l.evidence('edition_text'),l.evidence('stamp')).map(a=>str(a.raw||a.value).match(/\b(?:\d{1,2}(?:st|nd|rd|th)?\s+anniversary|pre[ -]?release|winner|championship|finalist)\b/i)?.[0])).slice(0,2);}
function presenceText(field,text){
 const t=norm(text),subject={stamp:/\b(?:stamp|timbro|1st edition|first edition|prima edizione)\b/,shadow:/\b(?:shadow|ombra)\b/,rarity_symbol:/\b(?:rarity|rarita)\b/,autograph:/\b(?:autograph|autografo|firma)\b/,patch:/\b(?:patch|relic|memorabilia)\b/}[field];
 if(str(text).includes('?')||!subject?.test(t)||/\b(?:unclear|uncertain|unreadable|illeggibile|possibly|probably|maybe|may|might|could|would|should|if|se|oppure|dubbio|incerto|incerta|forse|possibile|probabile|sembra|potrebbe|dovrebbe|deve|catalogo|catalogue|reference|riferimento|esempio|fonte|source|listing)\b/.test(t))return '';
 if(/\b(?:assente|absent)\b/.test(t)&&!/\b(?:non|not)\b/.test(t))return 'absent';
 if(/\b(?:presente|present|visibile|visible|leggibile)\b/.test(t)&&!/\b(?:non|not|no|senza|without|cannot|cant)\b/.test(t))return 'present';
 return '';
}
function reconcileVisualEvidence(l){
 const superseded=new Set(l.atoms.flatMap(a=>list(a.supersedes)));
 for(const a of [...l.atoms]){
  if(superseded.has(a.id)||!presenceFields.includes(a.field)||a.level!=='observed'||a.reported_certainty!=='clear'||!['vision','focused_vision'].includes(a.source)||!a.image_index)continue;
  const value=presenceText(a.field,a.raw);if(!value||a.value===value)continue;
  l.add(a.field,value,{source:'visual_consistency',certainty:'clear',image_index:a.image_index,region:a.region,raw:a.raw,supersedes:[a.id],derived_from:a.id});
  // An explicit opposite enumeration remains evidence of a real contradiction.
  if(['present','absent'].includes(a.value))l.add(a.field,a.value,{source:'visual_conflict',certainty:'clear',image_index:a.image_index,raw:a.value,derived_from:a.id});
  l.record('visual_state_reconciled',{field:a.field,observation:a.id,from:a.value,to:value});
 }
}
function assertConsistent(l,result){
 const check=result.printing_check,stamp=result.variant_resolution?.proof?.find(p=>p.field==='stamp');
 if(check&&stamp&&(check.stamp!=='confirmed'||check.stamp_presence!==stamp.value||result.pokemon_printing?.first_edition_stamp!==stamp.value))throw new Error('inconsistent_identification_state:stamp');
 if(check?.stamp==='unclear'&&l.pick('stamp')&&['present','absent'].includes(l.pick('stamp').value))throw new Error('inconsistent_identification_state:unmerged_stamp');
 if(result.exact_identity_status==='confirmed'&&(!result.market_ready||result.variant_resolution?.pending?.length||check?.complete===false||result.next_photo_request||result.job_status!=='variant_resolved'))throw new Error('inconsistent_identification_state:closure');
 return result;
}
const COLOR_WORDS={green:['green','verde'],blue:['blue','blu','azzurro'],red:['red','rosso','rossa','ruby'],gold:['gold','golden','oro'],silver:['silver','argento'],black:['black','nero','nera'],white:['white','bianco','bianca'],purple:['purple','viola'],pink:['pink','rosa'],orange:['orange','arancione'],yellow:['yellow','giallo'],teal:['teal'],aqua:['aqua'],bronze:['bronze','bronzo']};
const PATTERNS={nebula:['nebula'],wave:['wave','waves','onde','ondulato'],raywave:['raywave'],ice:['ice','cracked ice','ghiaccio'],mojo:['mojo'],checkerboard:['checker','checkerboard','scacchi','checkered'],pulsar:['pulsar'],shimmer:['shimmer'],mosaic:['mosaic','mosaico'],disco:['disco'],honeycomb:['honeycomb','nido d ape'],snakeskin:['snakeskin'],dragon_scale:['dragon scale'],sparkle:['sparkle'],geometric:['geometric','geometrico'],dots:['dots','puntini'],squares:['squares','square','quadrati']};
function tokens(value,dictionary){const t=' '+norm(value)+' ';return Object.keys(dictionary).filter(k=>dictionary[k].some(v=>t.includes(' '+v+' ')));}
function number(value){return str(value).normalize('NFKC').replace(/\s+(?:SAR|SSR|SR|UR|RRR|RR|HR|AR|R|C|UC)\s*$/i,'').replace(/^(?:no\.?|n[°º.]|number|numero|card\s*#?|carta\s*#?|#)\s*/i,'').replace(/\s+/g,'').toUpperCase();}
function numberParts(value){const n=number(value);if(/^[A-Z]{2,8}(?:-[A-Z]{2,8}){1,2}$/.test(n))return {full:n,local:n,total:''};const m=n.match(/^((?:[A-Z]{1,8}\d{0,4}-)?[A-Z]{0,8}\d+[A-Z]?)(?:\/([A-Z]{0,8}\d+[A-Z]?))?$/);return m?{full:n,local:m[1].replace(/^0+(?=\d)/,''),total:m[2]||''}:null;}
function numbersMatch(a,b){const x=numberParts(a),y=numberParts(b);return !!x&&!!y&&x.local===y.local&&(!x.total||!y.total||x.total===y.total);}
function serial(value){if(/^one[ -]+of[ -]+one$/i.test(str(value).trim()))return {value:'1/1',specimen:1,print_run:1};const partial=str(value).match(/^\s*\/\s*(\d{1,6})\s*$/);if(partial&&+partial[1]>0)return {value:'/'+(+partial[1]),specimen:null,print_run:+partial[1]};const m=str(value).replace(/\s+of\s+/i,'/').match(/^\s*(?:serial(?:\s*number)?\s*:?\s*)?(\d{1,6})\s*\/\s*(\d{1,6})\s*$/i);if(!m||+m[1]<1||+m[2]<1||+m[1]>+m[2])return null;return {value:+m[1]+'/'+(+m[2]),specimen:+m[1],print_run:+m[2]};}
function language(v){const t=norm(v);if(/简体|简体字|簡體|cinese semplificato|simplified chinese/.test(t))return 'zh-hans';if(/繁體|繁体|cinese tradizionale|traditional chinese/.test(t))return 'zh-hant';if(/^(?:中文|汉语|漢語|testo cinese|chinese text)$/.test(t))return 'zh';if(/^(?:zh hans|zh cn|simplified chinese|cinese semplificato|简体中文)$/.test(t))return 'zh-hans';if(/^(?:zh hant|zh tw|traditional chinese|cinese tradizionale|繁體中文)$/.test(t))return 'zh-hant';if(/^(?:zh|cn|chinese|cinese)(?: |$)/.test(t))return 'zh';return ({english:'en',inglese:'en',en:'en',italian:'it',italiano:'it',it:'it',japanese:'ja','日本語':'ja',giapponese:'ja',jp:'ja',ja:'ja',french:'fr',francais:'fr',francese:'fr',fr:'fr',german:'de',tedesco:'de',deutsch:'de',de:'de',spanish:'es',espanol:'es',spagnolo:'es',es:'es',korean:'ko',coreano:'ko',ko:'ko'})[t]||'';}
function season(v,context=''){const short=str(v).match(/^(\d{2})[-/](\d{2})$/),years=str(context).match(/\b(?:19|20)\d{2}\b/g)||[];if(short&&(+short[1]+1)%100===+short[2]){const candidates=unique(years.flatMap(y=>[+y-1,+y]).filter(y=>y%100===+short[1]));if(candidates.length===1)return candidates[0]+'-'+short[2];}return str(v).match(/\b(?:19|20)\d{2}(?:\s*[-/]\s*(?:\d{4}|\d{2}))?\b/)?.[0].replace(/\s/g,'').replace('/','-').replace(/^(\d{4})-\d{2}(\d{2})$/,'$1-$2')||'';}
function finish(v){const t=norm(v);if(/\b(?:non holo|non holographic|non olografica|non olografico|nonholo|normal|matte)\b/.test(t))return 'normal';if(/reverse|invers/.test(t))return 'reverse';if(/holo|olograf/.test(t))return 'holo';if(/refractor|refractive|reflective|prizm|prismatic|foil|metallic/.test(t))return 'reflective';return '';}
function region(r){return r&&r.image_index>0&&[r.x,r.y,r.width,r.height].every(Number.isFinite)&&r.x>=0&&r.y>=0&&r.width>0&&r.height>0&&r.x+r.width<=1.005&&r.y+r.height<=1.005?clone(r):null;}
function mapOcrRegion(line,ocr){const m=ocr.meta||{},crop=m.rect||m.originalRect||{x:0,y:0,width:m.originalWidth||ocr.width||1,height:m.originalHeight||ocr.height||1},w=m.originalWidth||ocr.width||1,h=m.originalHeight||ocr.height||1;return region({image_index:ocr.image_index,x:(crop.x+line.x*crop.width)/w,y:(crop.y+line.y*crop.height)/h,width:line.width*crop.width/w,height:line.height*crop.height/h});}
function near(a,b){return !!a&&!!b&&a.image_index===b.image_index&&Math.abs(a.x+a.width/2-b.x-b.width/2)<Math.max(.04,(a.width+b.width)/2)&&Math.abs(a.y+a.height/2-b.y-b.height/2)<Math.max(.025,(a.height+b.height)/2);}
function subjectMatch(a,b){const x=norm(a),y=norm(b);return x===y||x.split(' ').sort().join(' ')===y.split(' ').sort().join(' ');}
function profile(base){if(base.kind==='card'&&/magic|yu[ -]?gi|digimon|lorcana|dragon ball|standard tcg/i.test([base.category,base.family].join(' ')))return 'tcg';if(base.domain&&base.domain!=='unknown')return base.domain;if(base.object_unit==='box'||base.object_unit==='case'||/\bbox\b|confezione|sealed/i.test(base.category||''))return 'sealed';if(base.pokemon_printing?.is_pokemon||/pokemon|pokémon/i.test(base.category||''))return 'pokemon';if(/one piece/i.test([base.category,base.family].join(' ')))return 'onepiece';if(base.kind==='card'&&/panini|topps|upper deck|leaf|fleer|skybox|sports|basket|soccer|football|baseball|hockey/i.test([base.brand,base.family,base.category].join(' ')))return 'sports';return 'generic';}
// Keep the explicitly typed subject when a later reading appends adjacent card text.
function subject223(l,atoms){
 const initial=list(l.base.observations).filter(o=>o.field==='subject'&&o.certainty==='clear');
 if(initial.length===1&&atoms.some(a=>!subjectMatch(a.value,initial[0].text||initial[0].value))){const anchor=initial[0].text||initial[0].value,words=norm(anchor).split(' '),context=new Set(list(l.base.observations).filter(o=>o.field==='text'&&o.certainty==='clear').flatMap(o=>norm(o.text||o.value).split(' ')));
  if(atoms.every(a=>{const w=norm(a.value).split(' ');return words.every(v=>w.includes(v))&&w.every(v=>words.includes(v)||context.has(v));}))return {...atoms[0],value:anchor,composed_from:atoms.map(a=>a.id)};
 }
 return composeSubject(atoms);
}
function composeSubject(atoms){
 const groups=[];for(const atom of atoms){const r=atom.region;if(!r){groups.push([atom]);continue;}const g=groups.find(g=>g[0].region&&g[0].image_index===atom.image_index&&Math.abs(g[0].region.y+g[0].region.height/2-r.y-r.height/2)<Math.min(g[0].region.height,r.height)*.6);if(g)g.push(atom);else groups.push([atom]);}
 const combined=groups.map(g=>{g.sort((a,b)=>(a.region?.x||0)-(b.region?.x||0));if(g.length===1)return g[0];if(g.some((a,i)=>i&&a.region.x-(g[i-1].region.x+g[i-1].region.width)>.1))return null;const words=[];for(const a of g)if(!words.some(v=>norm(v).includes(norm(a.value))))words.push(a.value);return {...g[0],value:words.join(' '),composed_from:g.map(a=>a.id)};}).filter(Boolean);
 const uniqueNames=unique(combined.map(a=>norm(a.value)));if(uniqueNames.length===1)return combined[0];const longest=[...combined].sort((a,b)=>b.value.length-a.value.length)[0];if(longest&&atoms.every(a=>norm(longest.value).split(' ').join(' ').includes(norm(a.value))))return longest;return null;
}
function coreKey(e){return [norm(e.subject),norm(e.family),number(e.number),season(e.year),norm(e.subset||'Base')].join('|');}
class Ledger {
 constructor(base){this.base=clone(base||{});this.domain=profile(base||{});this.atoms=[];this.events=[];this.candidates=[];this.attempts=new Set();this.sequence=0;}
 add(field,value,meta={}){if(value===null||value===undefined||str(value)==='')return;const reportedField=field;field=observationRole220(this,semanticField(this.domain,field,value),value);if(reportedField==='subject'&&field!=='subject')meta={...meta,supersedes:list(meta.supersedes).filter(id=>{const a=this.atoms.find(a=>a.id===id);return a&&(a.field===field||a.field==='subject'&&same(a.value,value));})};if(field!==reportedField)meta={...meta,reported_field:reportedField,semantic_correction:true};const atom={id:'e'+(++this.sequence),field,value:str(value),level:meta.level||'observed',certainty:meta.certainty||'uncertain',reported_certainty:meta.certainty||'uncertain',source:meta.source||'vision',region:region(meta.region),image_index:meta.image_index||meta.region?.image_index||0,raw:meta.raw??str(value),...meta};atom.original_text=str(meta.raw??value);atom.normalized_value=comparisonValue222(field,value,this.domain);atom.region=region(atom.region);if(atom.level==='observed'&&!atom.region&&!atom.image_index)atom.level='inferred';if(field==='serial'&&!serial(atom.value)||presenceFields.includes(field)&&!['present','absent'].includes(atom.value))atom.certainty='uncertain';Object.freeze(atom);this.atoms.push(atom);return atom;}
 values(field){return this.atoms.filter(a=>a.field===field);}
 active(field){const replaced=new Set(this.atoms.flatMap(a=>list(a.supersedes)));return this.values(field).filter(a=>!replaced.has(a.id));}
 evidence(field){const values=this.values(field),replaced=new Set(this.atoms.flatMap(a=>list(a.supersedes)));return values.filter(a=>!replaced.has(a.id)&&a.level==='observed'&&a.certainty==='clear');}
 pick(field){const all=this.evidence(field);if(!all.length)return null;if(field==='subject')return subject223(this,all);if(this.domain==='pokemon'&&field==='finish'&&all.every(a=>['reflective','holo','reverse'].includes(a.value))){const specific=all.filter(a=>a.value!=='reflective');if(unique(specific.map(a=>a.value)).length===1)return specific[specific.length-1];}if(['collector_number','pokedex_number'].includes(field)){if(all.every(a=>all.every(b=>numbersMatch(a.value,b.value))))return [...all].sort((a,b)=>b.value.length-a.value.length)[0];return null;}return unique(all.map(a=>a.normalized_value??norm(a.value))).length===1?all[all.length-1]:null;}
 record(stage,data={}){this.events.push({stage,...clone(data)});}
 attempt(key){if(this.attempts.has(key))return false;this.attempts.add(key);return true;}
 snapshot(){return {user_details:this.userDetails||'',version:193,domain:this.domain,observations:this.atoms,events:this.events,attempts:[...this.attempts],candidates:this.candidates};}
}
function legacyFeatures(ledger,base){
 for(const o of list(base.physical_observations).filter(o=>o.entity==='target')){
  const meta={source:'vision',certainty:o.certainty,image_index:o.image_index,raw:o.text,zone:'unknown'};
  if(o.feature==='color'){
   // Locate colour in the border clause; adjective synonyms cannot suppress it.
   const clauses=str(o.text).split(/\bwith\b|\bwhile\b|[;,]/i),border=clauses.find(t=>/border|bordo|frame|cornice/i.test(t));
   if(border)for(const color of tokens(border,COLOR_WORDS))ledger.add('border_color',color,{...meta,zone:'border'});
  }
  if(o.feature==='pattern')for(const pattern of tokens(o.text,PATTERNS))ledger.add('pattern',pattern,{...meta,zone:/border|bordo/i.test(o.text)?'border':'surface'});
  if(o.feature==='finish'&&finish(o.text))ledger.add('finish',finish(o.text),meta);
  if(o.feature==='layout')ledger.add('layout',o.text,meta);
  if(o.feature==='configuration')ledger.add('configuration',o.text,meta);
 }
}
function normalizePrinting226(l){
 if(l.domain!=='sports')return;
 const surfaces=l.evidence('pattern').concat(l.evidence('finish'));
 const labels=l.evidence('text').concat(l.evidence('subset'));
 for(const a of labels){
  const candidates=a.field==='subset'||/^[\p{L}]{4,}$/u.test(a.value)?[a.value]:[...a.value.matchAll(/([\p{L}][\p{L}\p{N}-]{3,})\s*[™®]/gu)].map(m=>m[1]);
  for(const name of candidates){
   if(l.evidence('product').concat(l.evidence('brand')).some(p=>(' '+norm(p.value)+' ').includes(' '+norm(name)+' ')))continue;
   const surface=surfaces.find(b=>b.id!==a.id&&((' '+norm(b.raw)+' ').includes(' '+norm(name)+' ')||a.field==='text'&&/^[\p{L}]{4,}[™®]?$/u.test(a.value.trim())&&b.field==='pattern'&&/concentric|circol|circular|reticol|checker|lattice/i.test((b.source_value||b.value)+' '+b.raw)&&!/absent|unclear|not visible/i.test(b.raw)));
   if(surface&&!l.evidence('printing_name').some(b=>same(b.value,name)))l.add('printing_name',name,{source:'physical_semantic_normalization',certainty:'clear',image_index:a.image_index,region:a.region,raw:a.raw,derived_from:a.id,corroborating_observation:surface.id});
  }
 }
}
function ingestVision(ledger,base,source='vision'){
 if(list(base.observations).length){for(const o of base.observations){ledger.add(o.zone==='statistics'&&o.field!=='hp'?'statistics_text':o.field,o.field==='language'?language(o.text??o.value):o.field==='collector_number'?number(o.text??o.value):o.field==='hp'?str(o.text??o.value).match(/\d+/)?.[0]:o.text??o.value,{source,certainty:o.certainty,region:o.region,image_index:o.image_index,zone:o.zone,raw:o.text??o.value});for(const a of list(o.alternatives))ledger.add(o.field,typeof a==='string'?a:a.text,{source,certainty:'uncertain',region:o.region,image_index:o.image_index,alternative:true,raw:typeof a==='string'?a:a.text});}}
 else {
  for(const c of list(base.photo_clues)){
   const field=({subject:'subject',collector_number:'collector_number',serial:'serial',season:'season',copyright:'copyright',edition:'edition_text',team:'team',model:'product',barcode:'barcode',issue_number:'issue_number'})[c.role]||'text';
   ledger.add(field,field==='collector_number'?number(c.text):c.text,{source,certainty:c.certainty,region:c.region,image_index:c.image_index,raw:c.text});
   if(/^(?:\d+\s*)?(?:HP|PV)$|^\d+\s*(?:HP|PV)$/i.test(c.text))ledger.add('hp',str(c.text).match(/\d+/)?.[0],{source,certainty:c.certainty,region:c.region,image_index:c.image_index,raw:c.text});
  }
  legacyFeatures(ledger,base);
 }
 for(const f of list(base.features)){const vals=f.field==='border_color'?tokens(f.value,COLOR_WORDS):f.field==='pattern'?tokens(f.value,PATTERNS):[f.field==='finish'?finish(f.value)||'unclear':f.value];if(f.field==='pattern'&&!vals.length&&f.value)vals.push(f.value);for(const v of vals)ledger.add(f.field,ledger.domain==='pokemon'&&presenceFields.includes(f.field)?pokemonPresence204(f.field,v,f.description)||v:v,{source,certainty:f.certainty,region:f.region,image_index:f.image_index,zone:f.zone,source_value:f.value,raw:f.description||f.value});}
 for(const h of list(base.hypotheses))ledger.add(h.field,h.value,{source,level:'inferred',certainty:'uncertain'});
 const p=base.pokemon_printing||{};
 for(const [field,value,image,location] of (ledger.domain==='pokemon'?[['stamp',p.first_edition_stamp,p.stamp_image,p.stamp_location],['shadow',p.artwork_shadow,p.shadow_image,p.shadow_location]]:[]))if(['present','absent'].includes(value)&&image&&location)ledger.add(field,value,{source,certainty:'clear',image_index:image,raw:location});
 const lang=language(base.language||p.language),explicitLanguage=list(base.observations).filter(o=>o.field==='language');if(lang&&!explicitLanguage.some(o=>language(o.text??o.value)))ledger.add('language',lang,{source,certainty:'clear',image_index:1});
 if(base.brand)ledger.add('brand',base.brand,{source,level:'inferred'});
 if(base.family)ledger.add('family',base.family,{source,level:'inferred'});
 normalizePrinting226(ledger);reconcileIdentifiers201(ledger);return ledger;
}
function reconcileStatistics219(l){
 if(l.domain!=='sports')return;
 const stats=l.active('statistics_text').concat(l.active('text')).filter(a=>/\b(?:DEF|OFF|ATK|DEFENCE|DEFENSE|ATTACK|OVERALL|RATING|POWER)\s*[: ]\s*\d+/i.test(a.raw||a.value));
 for(const a of l.active('collector_number')){
  const r=a.region,front=list(l.base.image_views).some(v=>v.image_index===a.image_index&&v.view==='front'&&v.certainty==='clear');
  if(!r||!front||!/^\d{1,3}$/.test(a.value))continue;
  const peers=stats.filter(b=>b.image_index===a.image_index&&b.region);
  const nearPanel=peers.some(b=>Math.abs((b.region.x+b.region.width/2)-(r.x+r.width/2))<.13&&Math.abs((b.region.y+b.region.height/2)-(r.y+r.height/2))<.10);
  if(peers.length>=2&&nearPanel)l.add('statistics_number',a.value,{source:'semantic_classification',certainty:a.certainty,image_index:a.image_index,region:r,raw:a.raw,supersedes:[a.id],derived_from:a.id,reason:'front_rating_panel'});
 }
}
function reconcileIdentifiers201(l){
 reconcileStatistics219(l);
 const classic=l.domain==='pokemon'&&language(l.base.language||l.base.pokemon_printing?.language)==='ja'&&l.values('text').concat(l.values('statistics_text')).some(a=>/\bLV\.?\s*\d+/i.test(a.value));
 for(const a of l.active('collector_number'))if(classic&&/^No\.?\s*\d{3}$/i.test(a.raw)&&!str(a.value).includes('/'))l.add('pokedex_number',a.value,{source:'semantic_classification',certainty:a.certainty,image_index:a.image_index,region:a.region,raw:a.raw,supersedes:[a.id],derived_from:a.id});
}
function printingLanguageCompatible201(a,b){a=language(a);b=language(b);return !a||!b||a===b||a==='zh'&&b.startsWith('zh-')||b==='zh'&&a.startsWith('zh-');}
function pokemonKeys204(l){
 const years=unique(l.evidence('copyright').map(a=>{const ys=str(a.value).match(/\b(?:19|20)\d{2}\b/g)||[];return ys.length?String(Math.max(...ys.map(Number))):'';}));
 const date=l.pick('season')|| (years.length===1?{value:years[0],source:'photo_copyright'}:null),collector=l.pick('collector_number'),dex=l.pick('pokedex_number');
 const keys={subject:l.pick('subject')?.value||'',number:number(collector?.value||dex?.value),number_role:collector?'collector_number':dex?'pokedex_number':'',year:season(date?.value),year_origin:date?.source||null,language:l.pick('language')?.value||''};
 keys.complete=!!(keys.subject&&keys.number&&keys.year&&keys.language);return keys;
}
function pokemonAliases204(l){
 const subject=l.pick('subject')?.value||'',suffix=subject.match(/(?:VMAX|VSTAR|GX|EX|ex|V)$/)?.[0]||'';
 return unique(l.values('subject_alias').concat(l.active('subject').filter(a=>a.alternative)).map(a=>{const v=str(a.value),other=v.match(/(?:VMAX|VSTAR|GX|EX|ex|V)$/)?.[0]||'';if(suffix&&other&&other!==suffix)return '';return suffix&&!other?v+' '+suffix:v;}));
}
function pokemonPresence204(field,value,description=''){
 const t=norm(value);if(['present','absent','unclear'].includes(t))return t;
 if(/\b(?:uncertain|unclear|possible|possibly|maybe|forse|possibile|probabile|non leggibile)\b/.test(norm(value+' '+description)))return 'unclear';
 if(field==='rarity_symbol'&&(/^[★☆●◆◇]+$/.test(str(value))||/^(?:star|black star|stella(?: nera)?|diamond|diamante|circle|cerchio|sr|ssr|ar|sar|rr|r|u|c)$/.test(t)))return 'present';
 if(field==='stamp'&&/^(?:first edition|1st edition|edition 1|prima edizione)$/.test(t))return 'present';
 return presenceText(field,value+' '+description)||'';
}
function pokemonPolicy204(l,entry,result){
 if(l.domain!=='pokemon')return result;
 const p=pokemonKeys204(l),names=[entry.subject,...list(entry.aliases)],direct=names.some(n=>subjectMatch(p.subject,n)),alias=pokemonAliases204(l).some(a=>names.some(n=>subjectMatch(a,n))),visual=l.evidence('catalogue_core').some(a=>a.value===coreKey(entry)&&(entry.source_tier!=='discovery_lead'||sourceTrusted(a.reference_source,'pokemon')));
 const reasons=result.reasons.filter(r=>!['different_printing_language','different_product_season'].includes(r));
 const entryYear=season(entry.printing_year||entry.copyright_year||entry.year),entryLanguage=language(entry.printing_language||entry.language),ep=numberParts(entry.number);
 if(p.language&&entryLanguage&&!printingLanguageCompatible201(p.language,entryLanguage))reasons.push('different_printing_language');
 if(p.year&&entryYear&&p.year!==entryYear)reasons.push('different_product_season');
 if(p.number&&(!numbersMatch(p.number,entry.number)||p.number_role==='pokedex_number'&&ep?.total))reasons.push('different_number');
 if(p.subject&&!direct&&!alias&&!/[\u3040-\u9fff]/.test(p.subject))reasons.push('different_subject');
 let score=result.score,matches=[...result.matches];
 if(alias&&!direct){if(!matches.includes('localized_candidate'))score+=35;matches.push('localized_candidate');}
 const secondary=['different_illustrator','different_hp','different_attacks','different_printed_product'];
 const anchored=!!(p.subject&&p.number&&p.language&&(direct||alias||visual)&&numbersMatch(p.number,entry.number));
 const nonblocking=anchored&&p.complete?reasons.filter(r=>secondary.includes(r)):[];
 const blockers=unique(reasons.filter(r=>!nonblocking.includes(r)&&!(r==='different_subject'&&alias)));
 if(anchored&&p.complete&&!blockers.length){score=Math.max(score,100);matches.push('photo_keys');}
 if((!p.subject||!p.number)&&!visual)score=Math.min(score,70);
 if((!direct&&!visual||entry.requires_image_confirmation&&!visual))score=Math.min(score,70);
 return {...result,eligible:!blockers.length,reasons:blockers,score,matches:unique(matches),nonblocking_differences:unique(nonblocking),photo_keys:p};
}
function sourceUrl201(value){const raw=str(value),m=raw.match(/^(?:\[[^\]]*\]\()?\s*(https?:\/\/[^\s<>\[\]]+)/i);if(!m)return '';let url=m[1];if(raw.startsWith('['))url=url.replace(/\)$/,'');try{const u=new URL(url);return ['https:','http:'].includes(u.protocol)?u.href:'';}catch(_){return '';}}
function ingestOcr(ledger,reports){
 for(const o of list(reports).filter(o=>o.state==='ok'))for(const line of list(o.lines)){
  const readings=[line,...list(line.alternatives)],r=mapOcrRegion(line,o),nearby=ledger.atoms.filter(a=>a.source==='vision'&&near(a.region,r));
  const table=/\b(?:FG%|FT%|PPG|RPG|APG|3PM|INTERNATIONAL TOTALS|SEASON\s+TEAM)\b/i.test(o.text||'');
  for(const item of readings){
   const rr=mapOcrRegion(item,o)||r,text=str(item.text),sn=serial(text),num=number(text),np=numberParts(num),context=nearby.find(a=>['serial','collector_number','copyright'].includes(a.field));
   const common={source:'local_ocr',certainty:'uncertain',region:rr,image_index:o.image_index,raw:text,pass:item.pass||line.pass,rotation:item.rotation_degrees||line.rotation_degrees||0,engine_confidence:item.engine_confidence,alternative:item!==line};
   ledger.add('ocr_text',text,common);
   if(ledger.domain==='pokemon'&&(/^\d+\s*[x×+]$/i.test(text)||nearby.some(a=>a.zone==='statistics')&&!context))ledger.add('statistics_number',text,common);
   else if(ledger.domain==='sports'&&nearby.some(a=>a.zone==='statistics'||/^(?:DEF|OFF)\s*\d+/i.test(a.value))&&np)ledger.add('statistics_number',text,common);
   else if(context?.field==='collector_number'&&np)ledger.add('collector_number',np.full,common);
   else if(ledger.domain==='pokemon'&&/^[A-Z]{1,6}\d+[Il|][A-Z]{1,6}\d+[A-Z]?$/.test(num)){
    const m=num.match(/^([A-Z]{1,6}\d+)[Il|]([A-Z]{1,6}\d+)[A-Z]?$/);if(m)ledger.add('collector_number',m[1]+'/'+m[2],{...common,normalization:'possible_separator_confusion'});
   }else if(ledger.domain==='pokemon'&&np&&np.total&&rr?.y>.65)ledger.add('collector_number',np.full,common);
   else if(ledger.domain==='sports'&&sn&&!nearby.some(a=>a.field==='collector_number')&&(context?.field==='serial'||rr&&(rr.x>.7||rr.x<.2||rr.y>.65)))ledger.add('serial',sn.value,common);
   else if(context?.field==='serial'&&/^\d{1,6}\s*\/\s*[?\d]{1,6}$/.test(text))ledger.add('serial',text,common);
   else if(table&&/^\d{1,4}$/.test(text))ledger.add('statistics_number',text,common);
  }
 }
 return ledger;
}
function keyValues(l){return {pokedex:unique(l.evidence('pokedex_number').map(a=>a.value)),illustrator:l.pick('illustrator')?.value||'',attacks:l.evidence('attack').map(a=>a.value),setCodes:unique(l.evidence('set_code').map(a=>a.value)),subject:l.pick('subject')?.value||'',numbers:unique(l.active('collector_number').filter(a=>a.level==='observed').map(a=>numberParts(a.value)?.full)),year:l.domain==='pokemon'?pokemonKeys204(l).year:season(l.pick('season')?.value,l.evidence('copyright').map(a=>a.value).join(' '))||'',copyright:unique(l.values('copyright').flatMap(a=>str(a.value).match(/\b(?:19|20)\d{2}\b/g)||[])),language:l.pick('language')?.value||'',brands:unique(l.values('brand').map(a=>a.value)),colors:unique(l.evidence('border_color').map(a=>a.value)),patterns:unique(l.evidence('pattern').flatMap(a=>tokens(a.value,PATTERNS))),serials:unique(l.active('serial').filter(a=>a.level==='observed').map(a=>serial(a.value)?.value)),products:unique(l.evidence('product').filter(a=>semanticField(l.domain,'product',a.value)==='product').concat(l.domain==='generic'?l.evidence('model_code'):[]).map(a=>a.value))};}
function domains(l){const b=norm([l.base.brand,...l.values('brand').map(a=>a.value),...l.evidence('text').map(a=>a.value)].join(' '));if(l.domain==='generic')return [];if(l.domain==='pokemon')return ['tcgdex.net','pokemon.com','pokemontcg.io','bulbapedia.bulbagarden.net','wiki.pokemoncentral.it','psacard.com','tcgcollector.com','cardmarket.com'];if(l.domain==='onepiece')return ['en.onepiece-cardgame.com','onepiece-cardgame.com'];if(l.domain==='tcg')return ['scryfall.com','gatherer.wizards.com','db.yugioh-card.com','tcgplayer.com','cardmarket.com'];const makers=[];if(/topps|bowman/.test(b))makers.push('topps.com','ripped.topps.com');if(/panini/.test(b))makers.push('paniniamerica.net');if(/upper deck|fleer|skybox/.test(b))makers.push('upperdeck.com');if(/leaf/.test(b))makers.push('leaftradingcards.com');return unique([...makers,'beckett.com','checklistinsider.com']);}
// Search keys preserve their physical roles. A Pokédex identifier is useful for
// retrieval but must never be rewritten as a printed collector fraction.
function pokemonNumbers203(l){const clear=pokemonKeys204(l).number;if(clear)return [clear];return unique(['collector_number','pokedex_number'].flatMap(f=>l.active(f).filter(a=>a.level==='observed'&&a.certainty==='clear').map(a=>numberParts(a.value)?.full)));}
function pokemonQuery203(l,mode,entry){
 const k=keyValues(l),nums=pokemonNumbers203(l),date=k.year||k.copyright[k.copyright.length-1]||'';
 const alias=pokemonAliases204(l).find(a=>/[A-Za-z]/.test(a)&&!/[\u3040-\u9fff]/.test(a));
 const name=alias||k.subject||entry?.subject,languageName=({ja:'Japanese',it:'Italian',en:'English',de:'German',fr:'French',es:'Spanish',zh:'Chinese','zh-hans':'Simplified Chinese','zh-hant':'Traditional Chinese'})[k.language]||'';
 const numberQuery=(nums.length?nums:entry?[number(entry.number)]:[]).map(n=>'"'+n+'"').join(' OR ');
 const hints=mode==='reference'?[entry?.family,languageName,'card image','(site:psacard.com OR site:pricecharting.com)']: [languageName,...k.setCodes,...(k.pokedex.length?[k.illustrator,...(mode==='recovery'?k.attacks:[])]:[]),mode==='variant'?'set card printing variants':mode==='recovery'?'card catalogue alternate printing':'card set checklist'];
 return ['Pokémon TCG',name,numberQuery,date||entry?.year,l.pick('rarity_text')?.value,...hints].filter(Boolean).join(' ').replace(/\s+/g,' ').trim();
}
function photoProfile216(l){const k=keyValues(l);return {origin:'original_photos',subject:k.subject,products:k.products,number:l.pick('collector_number')?.value||'',season:k.year,colors:k.colors,patterns:k.patterns,serial:l.pick('serial')?.value||'',print_run:serial(l.pick('serial')?.value)?.print_run||null,evidence:l.atoms.filter(a=>a.level==='observed'&&['subject','brand','product','subset','collector_number','pokedex_number','season','serial','border_color','pattern','set_code','language'].includes(a.field)).map(a=>({field:a.field,value:a.value,certainty:a.certainty,image_index:a.image_index,source:a.source}))};}
function query(l,mode='identity',entry=null){
 if(l.domain==='pokemon')return pokemonQuery203(l,mode,entry);
 const k=keyValues(l);
 if(l.domain==='onepiece')return ['One Piece card',...k.numbers.slice(0,2).map(n=>'"'+n+'"'),k.subject,...distinctiveMarks(l),mode==='variant'?'artwork edition promo':'card catalogue'].filter(Boolean).join(' ');
 const observed=l.evidence('text').filter(a=>!/\b(?:HP|PV)\b|^[◆★●]$/.test(a.value)),product=unique([...k.products,...observed.filter(a=>/topps|chrome|update|select|prizm|upper deck|leaf|panini|terrace|mezzanine|field level/i.test(a.value)).map(a=>a.value)]).join(' ');
 const alias=l.domain==='pokemon'?l.active('subject').find(a=>a.alternative&&/[A-Za-z]/.test(a.value)&&!/[\u3040-\u9fff]/.test(a.value))?.value:'',card=l.domain!=='sealed',name=card?(l.domain==='pokemon'&&/[\u3040-\u9fff]/.test(k.subject)?alias||k.subject:k.subject):'',nums=card?k.numbers.slice(0,3).map(v=>'"'+v+'"').join(' OR '):'';
 const date=k.year||k.copyright[k.copyright.length-1]||'';
 const variant=['sports','onepiece'].includes(l.domain)?[...k.colors,...k.patterns,...k.serials.map(v=>'/'+serial(v).print_run),...['autograph','patch'].filter(f=>l.pick(f)?.value==='present')].join(' '):'';
 const sport=l.domain==='sealed'?sportCategory(l):'';
 const extra=l.domain==='sports'&&!k.year&&!k.products.length?[...l.evidence('team').map(a=>a.value),...l.evidence('layout').map(a=>a.value)].join(' ').slice(0,220):'';
 const config=l.domain==='sealed'?l.evidence('configuration').concat(l.evidence('text')).filter(a=>/autograph|packs?\b|cards? per|bustine|confezion/i.test(a.value)).map(a=>a.value).join(' '):'';
 const pokemonHints=l.domain==='pokemon'?[...k.setCodes,k.pokedex.length?'Japanese unnumbered promo '+k.pokedex.join(' '):'',k.pokedex.length?k.illustrator:'',k.pokedex.length?k.attacks.join(' '):'',k.language.startsWith('zh')?'Chinese':''].filter(Boolean).join(' '):'';
 return [pokemonHints,l.domain==='pokemon'?'Pokémon TCG':l.domain==='onepiece'?'One Piece card':product||l.base.brand,name,nums,date,l.domain==='pokemon'?l.pick('rarity_text')?.value:'',l.domain==='sports'?l.pick('subset')?.value:'',sport,variant,extra,config,l.domain==='sealed'?'box configuration':l.domain==='generic'?'model specifications':mode==='variant'?'parallel variants checklist':mode==='recovery'?(l.domain==='sports'?'international European edition checklist':'card catalogue alternate language promo'):'checklist',mode==='variant'&&domains(l).length?'('+domains(l).map(d=>'site:'+d).join(' OR ')+')':''].filter(Boolean).join(' ').replace(/\s+/g,' ').trim();
}
function sourceTrusted(url,domain){try{const host=new URL(url).hostname;if(domain==='pokemon'&&/(^|\.)(?:tcgcollector\.com|cardmarket\.com|pricecharting\.com)$/.test(host))return true;if(['onepiece','tcg'].includes(domain)&&/(^|\.)(?:tcgplayer\.com|cardmarket\.com|tcgcollector\.com|scryfall\.com|gatherer\.wizards\.com|db\.yugioh-card\.com)$/.test(host))return true;return /(^|\.)(?:tcgdex\.net|pokemontcg\.io|pokemon\.com|pokemon-card\.com|bulbagarden\.net|pokemoncentral\.it|serebii\.net|psacard\.com|cgccards\.com|beckett\.com|checklistinsider\.com|topps\.com|paniniamerica\.net|upperdeck\.com|leaftradingcards\.com|onepiece-cardgame\.com)$/.test(new URL(url).hostname);}catch(_){return false;}}
function hasConsensus218(l,e){return l.domain==='sports'&&e.source_tier==='lens_visual_verified'&&list(e.visual_consensus?.references).length>=2&&l.evidence('visual_consensus').some(a=>a.value===coreKey(e)&&a.source==='lens_consensus'&&new Set(a.references).size>=2);}
function evaluate(l,entry){
 const k=keyValues(l),reasons=[],matches=[],strongNumbers=l.evidence('collector_number');let score=0;
 if(!entry.subject||!entry.family||!entry.number&&!hasConsensus218(l,entry)&&!(entry.number_optional&&entry.source_tier==='lens_visual_verified'&&l.evidence('catalogue_core').some(a=>a.value===coreKey(entry)))||!entry.source?.url||entry.grounded!==true)return {...entry,eligible:false,reasons:['ungrounded_entry'],score:0};
 if(l.domain==='sports'&&entry.year&&l.evidence('season').some(a=>!sportsSeason215(a.value,entry.year)))reasons.push('different_product_season');
 if(l.domain==='sports'&&entry.brand&&l.evidence('brand').some(a=>!norm(a.value).includes(norm(entry.brand))&&!norm(entry.brand).includes(norm(a.value))))reasons.push('different_manufacturer');
 if(/^(?:registry|set registry|card list|catalogue|search|home)$/i.test(str(entry.family)))reasons.push('navigation_not_product');
 const printingLang=entry.printing_language||entry.language;if(l.domain==='pokemon'&&printingLang&&k.language&&(!printingLanguageCompatible201(printingLang,k.language))&&(/^(?:ja|zh)/.test(language(printingLang))||/^(?:ja|zh)/.test(k.language)))reasons.push('different_printing_language');
 if(['onepiece','tcg'].includes(l.domain)&&printingLang&&k.language&&!printingLanguageCompatible201(printingLang,k.language))reasons.push('different_printing_language');
 if(!k.subject&&l.evidence('subject').length>1)reasons.push('conflicting_original_subject');
 const names=[entry.subject,...list(entry.aliases)],needsSubjectVisual=l.domain==='pokemon'&&/[\u3040-\u9fff]/.test(k.subject)&&!names.some(n=>subjectMatch(k.subject,n));
 if(k.subject&&names.some(n=>subjectMatch(k.subject,n))){score+=35;matches.push('subject');}else if(k.subject&&!needsSubjectVisual)reasons.push('different_subject');else if(needsSubjectVisual&&(k.numbers.concat(k.pokedex).some(n=>numbersMatch(n,entry.number)))){score+=35;matches.push('localized_candidate');}
 if(entry.identifier_type!=='pokedex'&&entry.identifier_type!=='unnumbered'&&k.numbers.some(n=>numbersMatch(n,entry.number))){score+=25;matches.push('number');if(strongNumbers.some(a=>numbersMatch(a.value,entry.number)))score+=15;else {const reads=l.values('collector_number').filter(a=>numbersMatch(a.value,entry.number));if(unique(reads.map(a=>a.source)).length>1)score+=8;}}
 else if(strongNumbers.length&&entry.number)reasons.push('different_number');
 const dexMatch=l.domain==='pokemon'&&!strongNumbers.length&&!numberParts(entry.number)?.total&&k.pokedex.some(n=>numbersMatch(n,entry.number));
 if(dexMatch){score+=40;matches.push('pokedex_number');}
 // Catalogue language describes the reference, not the photographed copy.
 // Subject, collector number and product evidence still have to agree.
 if(k.year&&entry.year){if(season(entry.year)===k.year||l.domain==='sports'&&sportsSeason215(k.year,entry.year)){score+=10;matches.push('season');}else reasons.push('different_product_season');}
 // Copyright also supplies the binding photographed year in the Pokémon policy.
 if(k.copyright.some(y=>str(entry.year).startsWith(y)))score+=3;
 const illustrator=l.pick('illustrator');if(illustrator&&entry.illustrator){const clean=v=>norm(str(v).replace(/^Illus\.?\s*/i,''));if(clean(illustrator.value)!==clean(entry.illustrator))reasons.push('different_illustrator');else{score+=5;matches.push('illustrator');}}
 const hp=l.pick('hp');if(hp&&entry.hp){if(+hp.value===+entry.hp){score+=5;matches.push('hp');}else reasons.push('different_hp');}
 const entryText=norm([entry.entry_quote,...list(entry.attacks)].join(' '));
 const attacks=l.evidence('attack').concat(l.domain==='pokemon'?l.evidence('text').filter(a=>a.value.length>7&&!/copyright|pok[eé]mon|nintendo|wizards|HP|PV/i.test(a.value)):[]);
 const attackKey=v=>norm(v).replace(/^(?:特殊能力|能力|ability|attack) /,'').replace(/ \d+$/,'');
 const attackHits=attacks.filter(a=>entryText.includes(norm(a.value))||list(entry.attacks).some(b=>attackKey(a.value)===attackKey(b))); score+=Math.min(12,attackHits.length*4);if(attackHits.length)matches.push('attacks');
 const observedSubset=l.domain==='sports'?l.pick('subset')?.value:null;if(observedSubset&&l.catalogueSubsets202?.includes(subsetKey(observedSubset))&&entry.subset_known===true&&subsetKey(observedSubset)!==subsetKey(entry.subset))reasons.push('different_printed_subset');
 const seenProducts=k.products.filter(v=>norm(v)!=='prizm'||l.domain==='sports');
 for(const p of seenProducts)if(!norm([entry.family,entry.subset,entry.brand,entry.variant].join(' ')).includes(norm(p))&&!norm(p).includes(norm(entry.family))&&!productEquivalent217(p,entry.family)&&!(entry.number_optional&&entry.source_tier==='lens_visual_verified'&&familyKey(p).split(' ').every(t=>familyKey(entry.family).split(' ').includes(t)))&&!(['lens_visual_verified','lens_core_verified'].includes(entry.source_tier)&&strongNumbers.some(a=>numbersMatch(a.value,entry.number))&&familyKey(p).replace(/\bvariation$/, '').trim().split(' ').every(t=>norm([familyKey(entry.family),entry.subset,entry.brand].join(' ')).split(' ').includes(t))))reasons.push('different_printed_product');
 if(hasConsensus218(l,entry)||entry.number_optional&&entry.source_tier==='lens_visual_verified'&&l.evidence('catalogue_core').some(a=>a.value===coreKey(entry))){score+=25;matches.push('independent_visual_consensus');}
 const visual=l.evidence('catalogue_core').some(a=>a.value===coreKey(entry));if(visual){score+=k.pokedex.length?40:25;if(entry.catalogue_number_inferred&&!strongNumbers.length)score+=25;if(l.domain==='sports'&&!strongNumbers.length&&entry.source_tier==='lens_visual_verified'&&entry.field_proof?.number&&k.year&&sportsSeason215(k.year,entry.year)&&entry.visual_proof?.some(f=>f.field==='artwork'&&f.agrees)&&entry.visual_proof?.some(f=>f.field==='layout'&&f.agrees)){score+=15;matches.push('reference_identifier');}if(l.domain==='sports'&&entry.source_tier==='lens_visual_verified'&&['reference_text','reference_image'].includes(entry.field_proof?.number?.origin)&&entry.field_proof?.year&&entry.variants?.length)score+=20;matches.push('catalogue_image');if(needsSubjectVisual){if(!matches.includes('localized_candidate'))score+=35;matches.push('subject');}}else if(needsSubjectVisual)score=Math.min(score,70);
 if(l.domain==='pokemon'&&(k.pokedex.length&&!k.numbers.length||['pokedex','unnumbered'].includes(entry.identifier_type))&&!visual){
  const yearAgrees=entry.year&&(k.year===season(entry.year)||l.evidence('copyright').some(a=>str(a.value).match(/\b(?:19|20)\d{2}\b/g)?.includes(season(entry.year))));
  if(!dexMatch||!yearAgrees)score=Math.min(score,70);
 }
 if(l.domain==='pokemon'&&entry.attacks_language&&printingLanguageCompatible201(entry.attacks_language,k.language)&&list(entry.attacks).length&&l.evidence('attack').length&&!attackHits.length)reasons.push('different_attacks');
 if(l.domain==='sports'&&!visual&&!k.year&&!k.products.length&&!l.evidence('brand').length)score=Math.min(score,70);
 return pokemonPolicy204(l,entry,{...entry,eligible:!reasons.length,score,matches,reasons});
}
function canonicalEntries(entries){
 entries=entries.map(e=>subsetKey(e.subset)==='prizms'&&entries.some(a=>a!==e&&a.grounded&&a.source?.url===e.source?.url&&familyKey(a.family)===familyKey(e.family)&&subjectMatch(a.subject,e.subject)&&numbersMatch(a.number,e.number)&&season(a.year)===season(e.year)&&subsetKey(a.subset)==='base')?{...e,reported_subset:e.subset,subset:'Base'}:e);
 // A year printed in the grounded product name must survive family normalization.
 entries=entries.map(e=>e.subset&&norm(e.family).endsWith(norm(e.subset))?{...e,reported_subset:e.subset,subset:'Base',subset_known:false}:e);
 entries=entries.map(e=>!e.year&&/^(?:19|20)\d{2}\b/.test(str(e.family))?{...e,year:season(e.family)}:e);
 return entries.map(e=>{
  // A single-card source parsed independently can disambiguate a model's
  // product/parallel split. Never merge solely because a checklist URL agrees.
  const anchors=entries.filter(a=>a!==e&&a.grounded&&a.source?.url===e.source?.url&&subjectMatch(a.subject,e.subject)&&numbersMatch(a.number,e.number)&&(!a.year||!e.year||season(a.year)===season(e.year))&&familyKey(e.family).startsWith(familyKey(a.family)+' prizms ')&&list(a.variants).some(v=>{const full=familyKey(a.family)+' prizms '+norm(v.name);return full===familyKey(e.family)||full.startsWith(familyKey(e.family)+' ');}));
  const anchor=anchors.length&&new Set(anchors.map(a=>[familyKey(a.family),subsetKey(a.subset)].join('|'))).size===1?anchors[0]:null;
  return anchor?{...e,source_family:e.family,source_subset:e.subset,family:anchor.family,subset:anchor.subset||'Base',variants:[...list(e.variants),...list(anchor.variants)]}:e;
 });
}
function candidateGroups(l,entries){
 l.catalogueSubsets202=unique(entries.filter(e=>e.grounded&&e.subset_known===true).map(e=>subsetKey(e.subset)));
 if(l.domain==='onepiece')entries=entries.map(e=>/^P-\d+$/i.test(number(e.number))&&/^(?:one piece promo|promotional cards|promo)$/i.test(str(e.family))?{...e,family:'One Piece Promo'}:e);
 const evaluated=canonicalEntries(entries).map(e=>evaluate(l,e)),eligible=evaluated.filter(e=>e.eligible).sort((a,b)=>b.score-a.score);
 l.candidates=evaluated.map(({image_data,...e})=>e);
 const groups=new Map();
 for(const e of eligible){
  const np=numberParts(e.number),id=[norm(e.subject),productIdentity217(e.family),np?np.local+(np.total?'/'+np.total:''):number(e.number),season(e.year),e.subset_known===false?'':subsetKey(e.subset)].join('|');
  if(!groups.has(id))groups.set(id,{id,entry:e,entries:[],score:e.score});
  const g=groups.get(id);g.entries.push(e);g.score=Math.max(g.score,e.score);
  if(language(e.language)===keyValues(l).language&&language(g.entry.language)!==keyValues(l).language)g.entry=e;
 }
 // A short product name can attach to exactly one richer name actually read
 // on this card. Same subject/number/season/subset are mandatory.
 if(l.domain==='sports')for(const g of [...groups.values()]){
  if(!groups.has(g.id))continue;const e=g.entry;
  const targets=[...groups.values()].filter(h=>h!==g&&familyKey(h.entry.family).startsWith(familyKey(e.family)+' ')&&keyValues(l).products.some(p=>productEquivalent217(p,h.entry.family))&&subjectMatch(e.subject,h.entry.subject)&&numbersMatch(e.number,h.entry.number)&&!!e.year&&sportsSeason215(e.year,h.entry.year)&&h.entry.subset_known!==false&&(e.subset_known===false||subsetKey(e.subset)===subsetKey(h.entry.subset)));
  if(targets.length===1){const target=targets[0];target.entries.push(...g.entries);target.score=Math.max(target.score,g.score);if(e.subset_known===true&&target.entry.subset_known!==true)target.entry={...target.entry,subset:e.subset,subset_known:true};groups.delete(g.id);}
 }
 if(l.domain==='pokemon'){
  // H23 + year from one source and H23/H32 without year from another are
  // complementary records. Merge only into one compatible dated identity;
  // a missing year must never bridge two different documented releases.
  for(const g of [...groups.values()].filter(g=>!g.entry.year)){
   const e=g.entry,targets=[...groups.values()].filter(h=>h!==g&&h.entry.year&&subjectMatch(e.subject,h.entry.subject)&&familyKey(e.family)===familyKey(h.entry.family)&&subsetKey(e.subset)===subsetKey(h.entry.subset)&&numbersMatch(e.number,h.entry.number));
   if(targets.length===1){targets[0].entries.push(...g.entries);targets[0].score=Math.max(targets[0].score,g.score);groups.delete(g.id);}
  }
 }
 // Missing metadata is not a contradictory printing. Attach an incomplete
 // record only to ONE compatible richer group; real years/numbers stay distinct.
 const all=[...groups.values()],specificity=e=>Number(!!season(e.year))+Number(!!numberParts(e.number)?.total)+Number(e.subset_known!==false);
 for(const g of [...all].sort((a,b)=>specificity(b.entry)-specificity(a.entry))){
  const e=g.entry,targets=all.filter(h=>h!==g&&groups.has(h.id)&&specificity(h.entry)>specificity(e)&&subjectMatch(e.subject,h.entry.subject)&&familyKey(e.family)===familyKey(h.entry.family)&&(e.subset_known===false||subsetKey(e.subset)===subsetKey(h.entry.subset))&&numbersMatch(e.number,h.entry.number)&&(!e.year||!h.entry.year||season(e.year)===season(h.entry.year)));
  if(targets.length===1){targets[0].entries.push(...g.entries);targets[0].score=Math.max(targets[0].score,g.score);groups.delete(g.id);}
 }
 return [...groups.values()].sort((a,b)=>b.score-a.score);
}
function printingScope(l,e){
 const lang=l.pick('language')?.value||'',set=norm(canonicalSet222(e?.family,l.domain)),year=+season(e?.year).slice(0,4),base=/^(?:pokemon game |pokemon |set )?(?:base set|base|basic|set base|set de base|basis|expansion pack|拡張パック)$/.test(set)||e?.set_id==='base1';
 const shadow=lang==='en'&&base&&(!year||year<=2000)&&l.base.pokemon_printing?.card_type!=='energy';
 const historicalFirst=['jungle','giungla','dschungel','jungla','selva','fossil','fossile','fossiles','fosil','team rocket','gym heroes','gym challenge','neo genesis','neo discovery','neo revelation','neo destiny'].includes(set);
 const historicalWindow=!year||(lang==='ja'?year<=2016:year<=2002);
 const firstEdition=historicalWindow&&((lang&&language(e?.language)===lang&&e?.printing_options?.includes('first_edition')===true)||(['en','it','fr','de','es','pt','nl'].includes(lang)&&(base||historicalFirst))||(lang==='ja'&&year>=2001&&year<=2016&&l.pick('stamp')?.value==='present')||(!year&&l.pick('stamp')?.value==='present'));
 const noRarity=lang==='ja'&&base&&(!year||year===1996);
 return {shadow,firstEdition,noRarity,language:lang,base,scope_source:'set_language_catalogue',source:'https://www.cgccards.com/news/article/10262/pokemon-first-editions/'};
}
function variantOptions(l,group){
 // A foreign catalogue can identify the core without defining local printings.
 let options=group.entries.filter(e=>!keyValues(l).language||!e.language||printingLanguageCompatible201(e.language,keyValues(l).language)).flatMap(e=>list(e.variants).map(v=>({...v,source:v.source||e.source})));const uniqueOptions=[];
 // A partial extraction of the same resolved card/parallel must retain its explicit
 // unnumbered status. Missing metadata cannot turn Green Ice into a /5 option.
 options=options.map(v=>{if(v.print_run||v.unnumbered===true||!v.source?.url)return v;const peers=options.filter(o=>same(o.name,v.name)&&(o.source?.url===v.source.url||sourceTrusted(o.source?.url,l.domain)));return peers.some(o=>o.unnumbered===true)&&!peers.some(o=>o.print_run)?{...v,unnumbered:true}:v;});
 for(const o of options)if(!uniqueOptions.some(x=>same(x.name,o.name)&&x.print_run===o.print_run&&JSON.stringify(x.colors||[])===JSON.stringify(o.colors||[])&&JSON.stringify(x.patterns||[])===JSON.stringify(o.patterns||[])&&(x.image_url||'')===(o.image_url||'')))uniqueOptions.push(o);
 return uniqueOptions;
}
function variantState(l,group){
 if(hasConsensus218(l,group.entry)&&!l.active('serial').length&&!group.entries.some(e=>e.variants?.length)&&!['autograph','patch'].some(f=>l.pick(f)?.value==='present'))return {status:'confirmed',pending:[],labels:[],proof:[{field:'printing',value:'visually_matched',origin:'independent_visual_consensus',references:group.entry.visual_consensus.references}],variant_origin:'visual_comparison',needs_catalogue:false};
 const e=group.entry,k=keyValues(l),pending=[],labels=[],proof=[],fields={},options=variantOptions(l,group),physicalSerial=l.pick('serial'),sn=physicalSerial&&serial(physicalSerial.value);
 // A legible named printing plus a separate surface observation can establish the
 // photographed variant without pretending to have read its specimen serial.
 if(l.domain==='sports'&&l.evidence('catalogue_core').some(a=>a.value===coreKey(e))){
  const label=l.pick('printing_name')||l.pick('subset'),surface=label&&(l.atoms.find(a=>a.id===label.corroborating_observation)||l.evidence('finish').find(a=>a.image_index&&a.image_index!==label.image_index&&norm(a.raw).includes(norm(label.value))));
  if(l.evidence('printing_name').length>1&&!l.pick('printing_name'))return {status:'pending',pending:['variant'],labels:[],proof:[],printing_conflict:true,needs_catalogue:false};
  const name=label?.value||'',nonDistinctive=/^(?:base|gold|silver|red|blue|green|black|white|foil|holo|reflective|refractor|autograph|signature)$/i.test(name);
  if(label&&surface&&!nonDistinctive&&(e.physical_printing_scope===true||e.source_tier==='lens_visual_verified')&&!options.some(v=>same(v.name,name)&&sn&&v.print_run&&+v.print_run!==sn.print_run))return {status:'confirmed',pending:[],labels:[name],proof:[{field:'variant',value:name,origin:'original_photo',observation:label.id,corroborating_observation:surface.id}],variant_origin:'original_photo',needs_catalogue:false,...(sn?{physical_serial:{...sn,origin:'original_photo',observation:physicalSerial.id}}:{})};
 }

 if(l.domain==='pokemon'){
  const scope=printingScope(l,e),observedFinish=l.pick('finish')?.value;
  const comparedCore=l.evidence('catalogue_core').some(a=>a.value===coreKey(e));
  if(!pokemonKeys204(l).number&&!comparedCore)pending.push('collector_number');
  if(!pokemonKeys204(l).year&&l.active('copyright').length&&!comparedCore)pending.push('copyright');
  if(!e.language&&!comparedCore)pending.push('language');
  fields.printingScope=scope;
  if((e.requires_image_confirmation||e.identifier_type==='pokedex'||e.identifier_type==='unnumbered'||!l.pick('collector_number')&&k.pokedex.length)&&!l.evidence('catalogue_core').some(a=>a.value===coreKey(e))){pending.push('catalogue_image');fields.needs_catalogue=true;}
  const incompatibleStamp=!scope.firstEdition&&l.evidence('stamp').some(a=>a.value==='present'&&/1st|first edition|edition\s*1|prima edizione/i.test(a.raw));if(incompatibleStamp){pending.push('stamp');fields.printing_conflict=true;}
  for(const [active,field,positive,negative] of [[scope.firstEdition,'stamp','1st Edition','Unlimited'],[scope.shadow,'shadow','Shadowed','Shadowless'],[scope.noRarity,'rarity_symbol','Rarity Symbol','No Rarity Symbol']])if(active){
   let read=l.pick(field),value=read?.value;if(field==='stamp'&&!l.evidence('stamp').length){read=l.evidence('edition_text').find(a=>/1st|first|edition\s*1|1\s*edition/i.test(a.value));if(read)value='present';}
   if(['present','absent'].includes(value)){labels.push(value==='present'?positive:negative);proof.push({field,value,observation:read?.id||l.evidence('edition_text')[0]?.id});}else pending.push(field);
  }
  const finishes=unique(options.map(v=>finish(v.finish)||finish(v.name)).filter(v=>['normal','holo','reverse'].includes(v)));
  if(finishes.length===1&&e.number&&/^H\d+/.test(number(e.number))&&/skyridge|aquapolis/i.test(e.family)){labels.push('Holo');proof.push({field:'finish',value:'holo',source:e.source,reason:'documented_holo_catalogue_entry'});}
  else if(observedFinish&&['normal','holo','reverse'].includes(observedFinish)){
   if(!finishes.length||finishes.includes(observedFinish)){labels.push(({normal:'Non holo',holo:'Holo',reverse:'Reverse holo'})[observedFinish]);proof.push({field:'finish',value:observedFinish,observation:l.pick('finish').id});}
   else if(pokemonKeys204(l).complete){labels.push(({normal:'Non holo',holo:'Holo',reverse:'Reverse holo'})[observedFinish]);proof.push({field:'finish',value:observedFinish,observation:l.pick('finish').id,reason:'clear_photo'});}else pending.push('finish');
  }else if(finishes.length===1){labels.push(({normal:'Non holo',holo:'Holo',reverse:'Reverse holo'})[finishes[0]]);proof.push({field:'finish',value:finishes[0],source:e.source,reason:'catalogue_single_printing'});}
  else pending.push('finish');
  // Named stamp/artwork variants sharing a finish remain separate. The number
  // of alternatives is supplied by the catalogue, not a hard-coded pair.
  const compatible=options.filter(v=>!observedFinish||!finish(v.finish)||finish(v.finish)===observedFinish);
  const named=compatible.filter(v=>v.visual_required&& !/^(?:non holo|holo|holofoil|reverse holo|rare holo|holo rare|normal)$/i.test(str(v.name)));
  if(unique(named.map(v=>norm(v.name))).length>1){
   fields.variant_candidates=compatible;fields.needs_catalogue=compatible.some(v=>!v.image_url);
   const compared=l.pick('catalogue_variant'),chosen=compared&&compatible.find(v=>same(v.id||v.name,compared.value)&&(!compared.reference_source||compared.reference_source===v.source?.url));
   if(chosen){labels.push(chosen.name);proof.push({field:'variant',value:chosen.name,source:chosen.source,observation:compared.id});}else pending.push('variant');
  }
  if(e.rarity&&(!e.language||language(e.language)===k.language))fields.rarity=e.rarity;
 }else if(l.domain==='sports'||l.domain==='onepiece'||l.domain==='tcg'){
  const visualOrdinary=e.source_tier==='lens_visual_verified'&&e.printing_description==='unspecified'&&l.evidence('catalogue_core').some(a=>a.value===coreKey(e))&&!options.length&&!l.active('serial').length&&!['autograph','patch'].some(f=>l.pick(f)?.value==='present'&&!e.visual_proof?.some(v=>v.field==='configuration'&&v.agrees&&v.certainty==='clear'&&(f==='autograph'?/autograph|signature/i:/patch|relic/i).test(v.original)&&(f==='autograph'?/autograph|signature/i:/patch|relic/i).test(v.reference)));
  if(visualOrdinary)return {status:'confirmed',pending:[],labels:[],proof:[{field:'printing',value:'matching_reference_without_named_variant',origin:'visual_comparison',source:e.source}],variant_origin:'visual_comparison',needs_catalogue:false};
  if(l.domain==='sports'&&sn&&e.core_only&&e.subset===l.pick('subset')?.value&&l.evidence('catalogue_core').some(a=>a.value===coreKey(e)))return {status:'confirmed',pending:[],labels:[],proof:[{field:'print_run',value:sn.print_run,observation:physicalSerial.id,origin:'original_photo'}],physical_serial:{...sn,origin:'original_photo',observation:physicalSerial.id},variant_origin:'physical_print_run',commercial_variant_name:null,needs_catalogue:false};
  const serialAbsent=l.pick('serial_presence')?.value==='absent';
  const compatible=options.filter(v=>(!serialAbsent||!v.print_run)&&(!sn||v.unnumbered!==true&&(v.print_run===undefined||v.print_run===null||(v.max_print_run?sn.print_run<=+v.print_run:+v.print_run===sn.print_run)))&&(!v.colors?.length||!k.colors.length||v.colors.every(c=>k.colors.includes(c)))&&(!v.patterns?.length||!k.patterns.filter(p=>!['geometric','dots','squares'].includes(p)).length||v.patterns.some(p=>k.patterns.includes(p))));
  fields.variant_candidates=compatible;
  fields.needs_catalogue=options.length===0||compatible.length===0||l.domain==='onepiece'&&compatible.every(v=>v.visual_required&&!v.image_url)||k.colors.length>0&&!compatible.some(v=>v.colors?.some(c=>k.colors.includes(c))||v.patterns?.some(p=>k.patterns.includes(p)&&!['geometric','dots','squares'].includes(p))||v.base_printing||sn&&+v.print_run===sn.print_run);
  const declared=userVariant199(l,compatible),sameVariant=l.domain==='sports'?l.evidence('catalogue_variant').filter(a=>compatible.some(v=>same(v.id||v.name,a.value)&&(!a.reference_source||a.reference_source===v.source?.url))):[],sameNames=unique(sameVariant.map(a=>norm(compatible.find(v=>same(v.id||v.name,a.value)).name))),compared=l.pick('catalogue_variant')||(sameNames.length===1?sameVariant[0]:null),candidate=declared|| (compared?compatible.find(v=>same(v.id||v.name,compared.value)):compatible.length===1?compatible[0]:null);
  const supported=candidate&&!(l.domain==='sports'&&l.evidence('product').some(a=>same(a.value,candidate.name)))&&!candidate.max_print_run&&(!candidate.print_run||sn&&+candidate.print_run===sn.print_run||!sn&&compared&&candidate.visual_required===true)&&(!candidate.colors?.length||candidate.colors.every(c=>k.colors.includes(c)))&&(!candidate.patterns?.length||candidate.patterns.every(p=>k.patterns.includes(p)))&&(candidate.visual_required!==true||!!compared);
  if(supported&&(compared&&(!compared.reference_source||compared.reference_source===candidate.source?.url)||declared||sn||candidate.base_printing&&serialAbsent||candidate.colors?.length||candidate.patterns?.length||candidate.name==='Base'&&l.pick('finish')?.value==='normal')){labels.push(candidate.name);fields.variant_origin=declared?'user_declaration':compared?'visual_comparison':'photo_and_catalogue';proof.push({field:'variant',value:candidate.name,source:candidate.source,image_url:candidate.image_url||undefined,origin:fields.variant_origin});}
  else pending.push('variant');
  if(l.active('serial').length&&!sn&&!serialAbsent&&!(supported&&compared))pending.push('serial');
  for(const feature of ['autograph','patch'])if(l.pick(feature)?.value==='present'){const pattern=feature==='autograph'?/autograph|signature|signed/i:/patch|memorabilia|relic/i;if(!pattern.test([e.family,e.subset,candidate?.name].join(' '))&&!((l.evidence('text').concat(l.evidence('subset')).some(a=>pattern.test(a.value)&&/certified|certificat|autograph|signature|relic|memorabilia/i.test(a.value)))&&(e.visual_proof||[]).some(v=>v.agrees===true&&v.certainty==='clear'&&pattern.test(v.original)&&pattern.test(v.reference))))pending.push(feature);else proof.push({field:feature,value:'present',observation:l.pick(feature).id,source:e.source});}
  if(sn)fields.physical_serial={...sn,origin:'original_photo',observation:physicalSerial.id};
 }
 if(['pokemon','onepiece'].includes(l.domain)&&!k.language)pending.push('language');
 return {status:pending.length?'pending':'confirmed',pending:unique(pending),labels:unique(labels),proof,...fields};
}
function photoRequest(pending,domain,options={}){const p=new Set(pending);if(p.size&&[...p].every(f=>f==='catalogue'||f==='catalogue_image'||f==='variant'&&options.catalogueMissing))return null;if(p.has('serial'))return 'Fotografa da vicino la numerazione dell’esemplare: devono essere leggibili entrambi i numeri e la barra, ad esempio 2/5.';if(p.has('copyright'))return 'Fotografa la riga del copyright in basso: anno e testo devono essere leggibili.';if(p.has('collector_number')&&domain==='sports'&&options.backAvailable)return 'Il codice sul retro caricato non è stato verificato: fotografa più da vicino la zona del numero, mantenendo visibili lettere e trattini.';if(p.has('collector_number')&&domain==='sports')return 'Fotografa il retro completo: devono essere leggibili numero della carta, set e stagione.';if(p.has('collector_number'))return 'Fotografa da vicino il numero della carta, includendo tutte le lettere e gli eventuali numeri dopo la barra.';if(p.has('subject'))return 'Fotografa il nome e il numero della carta mantenendoli nitidi e interamente visibili.';if(p.has('language'))return 'Fotografa una riga di testo della carta, mantenendola nitida e leggibile.';if(p.has('rarity_symbol'))return 'Fotografa da vicino l’angolo con il simbolo di rarità, mantenendo visibile anche il bordo della carta.';if(p.has('shadow'))return 'Fotografa il fronte senza riflessi, mantenendo nitidi il bordo destro e inferiore dell’illustrazione e la riga del copyright.';if(p.has('stamp'))return 'Fotografa da vicino la zona del timbro First Edition sotto l’illustrazione.';if(domain==='sealed')return 'Fotografa lato e retro della confezione: formato, numero di bustine e carte, codice prodotto e contenuto garantito.';if(domain==='onepiece'&&p.has('variant'))return 'Fotografa da vicino il timbro promozionale e la zona dell’illustrazione che distingue la variante.';if(p.has('variant')||p.has('finish'))return 'Fotografa il fronte leggermente inclinato alla luce, mantenendo visibili il pattern della finitura e tutti i bordi.';return domain==='generic'?'Fotografa da vicino l’etichetta con marca e codice modello.':'Fotografa il fronte completo e nitido, con nome e numero della carta.';}
function sportCategory(l){const text=l.evidence('text').map(a=>a.value).join(' ');if(/\bNBA\b|basketball/i.test(text))return 'basketball';if(/\bMLB\b|baseball/i.test(text))return 'baseball';if(/\bNFL\b|american football/i.test(text))return 'football';if(/\bFIFA\b|soccer/i.test(text))return 'soccer';return ''; }
function productText201(v){return norm(v).replace(/\bupdates(?: series)?\b/g,'update').replace(/\bupdate series\b/g,'update');}
function configuration(value){
 const words={one:1,two:2,three:3,four:4,five:5,six:6,seven:7,eight:8,nine:9,ten:10,eleven:11,twelve:12};const s=norm(value).replace(/\b(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\b/g,w=>words[w]),odds=s.match(/(\d+)\s+autographs?\s+(?:in\s+)?every\s+(\d+)\s+boxes/),perBox=s.match(/(\d+)\s+autographs?(?:\s+cards?)?\s+(?:per|in each|in every)\s+box/),packs=s.match(/(\d+)\s+packs?\s+per\s+box/),cards=s.match(/(\d+)\s+cards?\s+per\s+pack/);
 const guarantee=s.match(/(?:guarantees?|delivers?)\s+(\d+)\s+autographs?/)||s.match(/(\d+)\s+(?:guaranteed\s+)?autographs?\s+guaranteed/),packCount=s.match(/(?:contains?|delivers?)\s+(\d+)\s+packs?/);
 return {autographs:odds?+odds[1]/+odds[2]:perBox?+perBox[1]:guarantee?+guarantee[1]:null,packs:packs?+packs[1]:packCount?+packCount[1]:null,cards:cards?+cards[1]:null};
}
function reduceObject(l,entries,error){
 const k=keyValues(l),publication=publication222(l),publisher=publisher222(l.pick('publisher')?.value|| (publication?l.pick('brand')?.value||'':'')),workTitle=l.pick('work_title')?.value|| (publication?l.pick('subject')?.value||'':''),reads=l.evidence('product').concat(l.evidence('text'),l.evidence('configuration')),physical=reads.map(a=>a.value),brand=publisher||l.pick('brand')?.value||l.base.brand||'',product=k.products.join(' ')||workTitle||physical.filter(t=>/chrome|update series|topps|select|upper deck|leaf|panini/i.test(t)).join(' '),year=k.year;
 const observed=configuration(physical.join(' ')),candidates=[];
 for(const e of entries){
  if(!e.grounded||!e.source?.url||!e.family)continue;
  const text=productText201([e.family,e.subject,e.entry_quote].join(' ')),parts=physical.filter(t=>/chrome|update series|topps|select|upper deck|leaf|panini/i.test(t));
  const compared=e.source_tier==='lens_visual_verified'&&e.object_identity_verified===true&&l.evidence('catalogue_core').some(a=>a.value===coreKey(e));
  const matched=compared||(l.domain==='sealed'?parts.length>0&&parts.every(t=>text.includes(productText201(t))):k.products.length>0&&k.products.every(t=>text.includes(norm(t))));
  const sport=sportCategory(l),sourceSport=norm([e.family,e.subject].join(' '));
  if(!matched||year&&e.year&&season(e.year)!==year||sport&&/\b(?:basketball|baseball|football|soccer)\b/.test(sourceSport)&&!sourceSport.includes(sport))continue;
  const config=configuration([e.entry_quote,e.configuration_quote].filter(Boolean).join(' ')),conflicts=Object.keys(observed).filter(f=>observed[f]!==null&&config[f]!==null&&observed[f]!==config[f]);
  const exactConfig=Object.keys(observed).filter(f=>observed[f]!==null).length>=2&&Object.keys(observed).every(f=>observed[f]===null||observed[f]===config[f]);
  const inlineCodes=l.domain==='generic'?unique(l.evidence('product').flatMap(a=>str(a.value).match(/\b[A-Z]{1,6}\d{0,6}[-/]\d{2,6}[A-Z0-9-]*\b/g)||[])):[];
  const code=l.pick('barcode')?.value||l.pick('sku')?.value||l.pick('model_code')?.value||(inlineCodes.length===1?inlineCodes[0]:''),codeMatch=!!code&&str(e.entry_quote).includes(code);
  candidates.push({...e,configuration_conflicts:conflicts,exact:!conflicts.length&&(codeMatch||compared||l.domain==='sealed'&&exactConfig)});
 }
 if(l.domain==='sealed'){const groups=unique(candidates.map(e=>norm(e.family)));for(const family of groups){const group=candidates.filter(e=>norm(e.family)===family),keys=Object.keys(observed).filter(f=>observed[f]!==null),survivors=group.filter(e=>!e.configuration_conflicts.length);if(group.length>=2&&keys.length&&survivors.length===1&&group.every(e=>keys.every(f=>configuration([e.entry_quote,e.configuration_quote].filter(Boolean).join(' '))[f]!==null)))survivors[0].exact=true;}}
 l.candidates=candidates;const variants=unique(candidates.filter(e=>e.exact).map(e=>norm([e.family,e.subject,l.domain==='sealed'?e.object_format||str(e.entry_quote).match(/\b(?:Hobby|Jumbo|Blaster|Mega|Value) Box\b/i)?.[0]||'':e.number].join(' ')))),exact=variants.length===1,core=exact||!!product&&(l.domain==='sealed'||candidates.length>0),entry=exact?candidates.find(e=>e.exact):candidates[0],family=product||entry?.family||'',model=[year||entry?.year,family,l.domain==='generic'&&!norm(family).includes(norm(brand))?brand:'',exact&&entry?.object_identity_verified?(l.domain==='sealed'?str(entry.entry_quote).match(/\b(?:Hobby|Jumbo|Blaster|Mega|Value) Box\b/i)?.[0]||'':str(entry.entry_quote+' '+(entry.configuration_quote||'')).match(/(?:cartonato|hardcover)/i)?.[0]?('cartonato'+(k.colors.includes('green')?' verde':'')):''):''].filter(Boolean).join(' · '),pending=exact?[]:[l.domain==='sealed'?'configuration':'model'];
 const result={...l.base,engine_version:193,engine_final:true,kind:'object',title:model||l.base.title,brand,publisher:publisher||undefined,subject:l.pick('subject')?.value||'',work_title:workTitle||undefined,family,model:core?model:'',variant:exact&&l.domain==='sealed'?str(entry.entry_quote).match(/\b(?:Hobby|Jumbo|Blaster|Mega|Value) Box\b/i)?.[0]||'':'',model_verified:core,model_confidence:null,market_ready:exact,catalogue_core_verified:!!entry,catalogue_verified:exact,visual_reference_id:entry?.visual_reference_id||null,field_proof:entry?.field_proof||null,status:core?'identified':'uncertain',identity_status:core?'confirmed':'partial',exact_identity_status:exact?'confirmed':core?'variant_pending':'unresolved',core_identity:{status:core?'confirmed':'partial',origin:entry?'catalogue_and_photo':'printed_product',model,fields:[{field:'brand',value:l.pick('brand')?.value||'',origin:'photo'},{field:'product',value:family,origin:product?'photo':'catalogue'},{field:'year',value:year,origin:'photo'}].filter(f=>f.value),pending_fields:core?[]:pending},variant_resolution:{status:exact?'confirmed':'pending',pending,labels:exact?[entry.subject]:[],proof:exact&&entry?.visual_reference_id?[{field:'configuration',origin:'image_comparison',reference_id:entry.visual_reference_id,source:entry.source}]:[]},variant_needs_verification:!exact,variant_check:exact?'confirmed':'pending',assistance_state:exact?'confirmed':error||'physical_detail_needed',missing_information:exact?[]:[l.domain==='sealed'?'Formato e configurazione della confezione':publication?'Edizione del volume':'Codice modello'],next_photo_request:exact?null:/fumetto|comic|book|libro/i.test(l.base.category||'')?'Fotografa il colophon o la pagina con editore, anno ed edizione del volume.':photoRequest(pending,l.domain),normalized_query:exact?model:'',edition_verified:publication?!!entry?.year:undefined,identity_scope:publication?'visible_work_publisher_format':undefined,verification_summary:exact?(publication&&!entry?.year?'Opera, editore e formato identificati; anno ed edizione non determinati.':'Prodotto e configurazione identificati.'):'Prodotto riconosciuto; resta il dettaglio indicato.',identification_sources:candidates.map(e=>e.source),candidate_models:candidates.filter(e=>!e.configuration_conflicts.length).map(e=>({model:e.subject,reason:'Configurazione da verificare'}))};
 l.record('reduce',{core:result.core_identity.status,exact:result.exact_identity_status,pending});return result;
}
function reduce(l,entries,{error=null}={}){
 reconcileVisualEvidence(l);
 if(['sealed','generic'].includes(l.domain)){const result=reduceObject(l,entries,error);result.closure_status=result.market_ready?'resolved':'pending';result.job_status=result.market_ready?'variant_resolved':result.core_identity.status==='confirmed'?'variant_pending':'identity_pending';return assertConsistent(l,result);}
 const groups=candidateGroups(l,entries),top=groups[0],k=keyValues(l),base=l.base;
 const uniqueMatch=top&&top.score>=75&&(!groups[1]||top.score-groups[1].score>=12),entry=uniqueMatch?top.entry:null;
 const shared=top?.score>=60&&groups.length&&groups.every(g=>same(g.entry.family,top.entry.family)&&subjectMatch(g.entry.subject,top.entry.subject));
 const family=entry?.family||(shared&&top.score>=75?top.entry.family:'')||title224(k.products),subject=l.domain==='pokemon'?k.subject||entry?.subject:entry?.subject||k.subject,cardNumber=(l.domain==='pokemon'?pokemonKeys204(l).number:'')||(entry&&l.pick('collector_number')&&numbersMatch(entry.number,l.pick('collector_number').value)?l.pick('collector_number').value:entry?.number)||l.pick('collector_number')?.value||(l.domain==='pokemon'?number(l.pick('pokedex_number')?.value):'')||'',date=l.domain==='pokemon'?pokemonKeys204(l).year||entry?.year||'':l.domain==='sports'&&k.year&&(!entry?.year||sportsSeason215(k.year,entry.year))?(k.year.length>=(entry?.year||'').length?k.year:entry.year):entry?.year||k.year;
 const variation=entry?variantState(l,top):{status:'pending',pending:[!subject?'subject':!cardNumber&&!k.pokedex.length?'collector_number':'catalogue'],labels:[],proof:[]};
 if(!entry&&!(l.domain==='pokemon'&&pokemonKeys204(l).number)&&!l.candidates.some(e=>e.eligible&&l.pick('collector_number')&&numbersMatch(e.number,l.pick('collector_number').value))&&l.candidates.some(e=>e.grounded&&subjectMatch(k.subject,e.subject)&&e.reasons?.includes('different_number')&&!e.reasons.includes('different_printed_product')))variation.pending.push('collector_number');
 const physicalSerial=l.pick('serial'),sn=physicalSerial&&serial(physicalSerial.value);if(sn)variation.physical_serial={...sn,origin:'original_photo',observation:physicalSerial.id};
 const exact=!!entry&&variation.status==='confirmed',fields=[];
 for(const [field,value] of [['subject',subject],['family',family],['catalog_number',cardNumber],['year',date],['brand',entry?.brand||base.brand]])if(value){const physical=l.domain==='pokemon'&&({subject:l.pick('subject'),catalog_number:l.pick('collector_number')||l.pick('pokedex_number'),year:pokemonKeys204(l).year})[field];fields.push({field,value,origin:physical?'photo':entry?(l.domain==='pokemon'||l.domain==='sports'&&(field==='catalog_number'&&!l.pick('collector_number')||field==='year'&&!k.year)?'catalogue':'catalogue_and_photo'):'photo',source:physical?null:entry?.source||null});}
 const model=[date,family,cardNumber?(l.domain==='pokemon'&&(!l.pick('collector_number')&&k.pokedex.length||entry&&['pokedex','unnumbered'].includes(entry.identifier_type))?'No.'+cardNumber:'#'+cardNumber):'',subject].filter(Boolean).join(' · '),pending=variation.pending;
 const stampProof=variation.proof.find(p=>p.field==='stamp'),stampObservation=stampProof&&l.atoms.find(a=>a.id===stampProof.observation),stampConflict=unique(l.evidence('stamp').map(a=>a.value)).length>1;
 const printing={...base.pokemon_printing};
 if(l.domain==='pokemon'){
  printing.is_pokemon=true;printing.language=k.language;
  if(entry)printing.set_name=entry.family;
  const copyright=l.pick('copyright')||(pokemonKeys204(l).year?[...l.evidence('copyright')].sort((a,b)=>b.value.length-a.value.length)[0]:null);if(copyright)Object.assign(printing,{copyright_text:copyright.value,copyright_image:copyright.image_index});
  printing.first_edition_stamp=stampProof?.value||'unclear';
  if(stampObservation)Object.assign(printing,{stamp_image:stampObservation.image_index,stamp_location:stampObservation.raw,stamp_text:stampObservation.raw});
  const shadow=l.pick('shadow');if(shadow)Object.assign(printing,{artwork_shadow:shadow.value,shadow_image:shadow.image_index,shadow_location:shadow.raw});
 }
 const result={engine_version:193,engine_final:true,kind:base.kind||'card',category:base.category||'Carta',brand:entry?.brand||base.brand||'',family,model:entry?model:'',title:model||base.title||'Oggetto da identificare',variant:variation.labels.join(' · '),language:k.language,condition:base.condition||'',
  closure_status:exact?'resolved':'pending',job_status:exact?'variant_resolved':entry?'variant_pending':'identity_pending',status:entry?'identified':'uncertain',identity_status:entry?'confirmed':'partial',exact_identity_status:exact?'confirmed':entry?'variant_pending':'unresolved',market_ready:exact,model_verified:!!entry,catalogue_core_verified:!!entry,catalogue_verified:exact,model_confidence:null,family_confidence:family?(l.domain==='pokemon'?Math.min(100,top?.score||0):90):0,
  catalogue_reference_language:language(entry?.language)||null,language_origin:k.language?'original_photo':null,
  core_identity:{status:entry?'confirmed':'partial',origin:'catalogue_engine',model,fields,pending_fields:entry?[]:pending},identity_keys:{subject:{value:subject},number:{value:cardNumber},date:{value:date},language:{value:k.language}},photo_identity_keys:l.domain==='pokemon'?pokemonKeys204(l):undefined,
  physical_card_number:l.pick('collector_number')?.value||null,pokedex_number:l.pick('pokedex_number')?.value||null,source_confirmed_catalog_number:entry?.number||null,source_confirmed_year:(l.domain==='pokemon'?entry?.printing_year||entry?.copyright_year||entry?.year:entry?.year)||null,catalogue_release_year:l.domain==='pokemon'?entry?.year||null:undefined,printed_year:l.domain==='pokemon'?pokemonKeys204(l).year||null:undefined,physical_serial:variation.physical_serial||null,serial_number:variation.physical_serial?.value||null,
  variant_needs_verification:!exact,variant_check:variation.status,variant_resolution:variation,printing_check:l.domain==='pokemon'&&entry?{complete:variation.status==='confirmed',labels:variation.labels,missing:pending,stamp:variation.printingScope.firstEdition?(stampConflict?'conflict':stampProof?'confirmed':'unclear'):'not_applicable',stamp_presence:stampProof?.value||(variation.printingScope.firstEdition?'unclear':'not_applicable'),shadow:variation.printingScope.shadow?l.pick('shadow')?.value||'unclear':'not_applicable',contradiction:stampConflict}:undefined,
  assistance_state:exact?'confirmed':error|| (entry?variation.needs_catalogue?'source_detail_needed':'physical_detail_needed':'unidentified'),missing_information:pending.map(p=>({serial:'Numerazione dell’esemplare',variant:'Variante commerciale',finish:'Finitura',stamp:'Timbro First Edition',shadow:'Ombra dell’illustrazione',rarity_symbol:'Simbolo di rarità',catalogue:'Riscontro catalografico',catalogue_image:'Confronto con immagine catalografica',language:'Lingua della carta',collector_number:'Numero carta',copyright:'Anno stampato sulla carta',subject:'Nome'})[p]||p),
  next_photo_request:exact?null:photoRequest(pending,l.domain,{catalogueMissing:variation.needs_catalogue,backAvailable:list(base.image_views).some(v=>v.view==='back')}),verification_summary:exact?(variation.variant_origin==='user_declaration'?'Carta identificata; variante dichiarata dall’utente e presente nel catalogo.':'Carta e stampa identificate tramite letture e catalogo.'):entry?'Carta identificata. Resta il dettaglio di stampa indicato.':'Letture conservate; identità catalografica da completare.',
  normalized_query:exact?[model,variation.labels.join(' '),k.language].filter(Boolean).join(' '):'',candidate_models:(entry?[]:groups.slice(0,4)).map(g=>({model:[g.entry.year,g.entry.family,g.entry.number,g.entry.subject].join(' '),score:g.score})),catalogue_data:fields,identification_sources:unique(groups.flatMap(g=>g.entries.map(e=>e.source.url))).map(url=>({url,title:groups.flatMap(g=>g.entries).find(e=>e.source.url===url)?.source.title||url})),evidence:fields.map(f=>f.field+': '+f.value),object_unit:base.object_unit||'single',pokemon_printing:l.domain==='pokemon'?printing:undefined,
  card_identity:{autograph:l.pick('autograph')?.value||'unclear',patch:l.pick('patch')?.value||'unclear',manufacturer:entry?.brand||base.brand||null,subject,set:family,number:cardNumber,date:date||null,language:k.language,variant:variation.labels.join(' · ')||null,rarity:l.pick('rarity_text')?.value||(entry&&(!entry.language||language(entry.language)===k.language)?entry.rarity:null)||null,rarity_symbol:l.pick('rarity_symbol')?.value||'unclear',serial:variation.physical_serial||null}}
 identityPresentation200(l,result,entry);
 l.record('reduce',{core:result.core_identity.status,exact:result.exact_identity_status,groups:groups.length,selected:top?.id||null,pending});return assertConsistent(l,result);
}
function title224(parts){
 const key=v=>norm(v).replace(/\bfirst edition\b/g,'1st edition');
 const rows=parts.flatMap(p=>str(p).split(/\s*·\s*/)).filter(Boolean).map(p=>{const w=p.split(/\s+/);for(let n=Math.floor(w.length/2);n>=1;n--)for(let i=0;i+2*n<=w.length;){if(key(w.slice(i,i+n).join(' '))===key(w.slice(i+n,i+2*n).join(' ')))w.splice(i+n,n);else i++;}return w.join(' ');});
 return rows.filter((p,i)=>{const k=key(p);return !rows.some((q,j)=>{const other=key(q);if(i===j)return false;if(k===other)return j<i;if(k==='holo'&&/\b(?:reverse|non)\b/.test(other)||k==='shadowless'&&/\bshadowed\b/.test(other)||k==='shadowed'&&/\bshadowless\b/.test(other))return false;if(other.startsWith(k+' ')&&/^(?:[0-9]+|v|vmax|vstar|ex|gx)$/.test(other.slice(k.length+1)))return false;return (' '+other+' ').includes(' '+k+' ');});}).join(' · ');
}
function identityPresentation200(l,result,entry){
 const c=result.card_identity;if(!c)return result;
 const present=l.pick('rookie'),badge=l.evidence('text').concat(l.evidence('badge')).find(a=>/^(?:rc(?: card)?|rookie(?: card)?|rated rookie|rookie shield)$/i.test(str(a.value)));
 const catalogueRc=entry&&/\b(?:RC|Rookie Card)\b/i.test(entry.entry_quote||'')&&str(entry.entry_quote).length<200;
 if(l.domain==='sports'){c.is_rookie=present?present.value==='present'?true:present.value==='absent'?false:null:badge||catalogueRc?true:null;c.rookie_origin=present||badge?'original_photo':catalogueRc?'catalogue':null;}
 let base=result.core_identity.model;
 if(l.domain==='sports'&&entry){const product=l.pick('product')?.value,subset=entry.subset_known===true&&subsetKey(entry.subset)!=='base'?str(entry.subset).replace(/ Checklist$/i,''):null;const family=product&&(norm(product).includes(norm(entry.family))||productEquivalent217(product,entry.family))?str(product).replace(/^(?:(?:19|20)\d{2}(?:[-/]\d{2,4})?|\d{2}[-/]\d{2})\s+/,''):entry.family;c.product=family;c.subset=subset||null;base=[c.date||result.source_confirmed_year,family,subset&&!norm(family).includes(norm(subset))?subset:'',c.number?'#'+number(c.number):'',c.subject].filter(Boolean).join(' · ');}
 if(['pokemon','onepiece','tcg'].includes(l.domain)&&entry?.subset&&subsetKey(entry.subset)!=='base'){c.subset=entry.subset;c.subset_origin='catalogue';if(!norm(base).includes(norm(entry.subset)))base=[base,entry.subset].filter(Boolean).join(' · ');result.core_identity.model=base;if(result.normalized_query&&!norm(result.normalized_query).includes(norm(entry.subset)))result.normalized_query+=' '+entry.subset;}
 if(l.domain==='sports'&&!entry){c.product=keyValues(l).products.join(' · ')||null;c.subset=l.pick('subset')?.value||null;if(c.subset&&!norm(base).includes(norm(c.subset)))base+=' · '+c.subset;}
 const parts=[base,result.variant];
 if(l.domain==='sports'){if(c.is_rookie===true)parts.push('RC');if(result.physical_serial?.value){const run='/'+result.physical_serial.print_run;parts.push(run);if(result.normalized_query&&!result.normalized_query.split(/\s+/).includes(run))result.normalized_query+=' '+run;}}
 if(l.domain==='pokemon'){
  if(c.rarity)parts.push(c.rarity);
  const symbol=l.pick('rarity_symbol');c.has_rarity_symbol=symbol?.value==='present'?true:symbol?.value==='absent'?false:null;
  const scope=result.variant_resolution?.printingScope||printingScope(l,entry);
  if(scope.noRarity&&c.has_rarity_symbol===false&&!/No Rarity Symbol/i.test(result.variant))parts.push('No Rarity Symbol');
  if(scope.noRarity&&c.has_rarity_symbol===true&&!/Rarity Symbol/i.test(result.variant))parts.push('Rarity Symbol');
 }
 if(['pokemon','onepiece','tcg'].includes(l.domain)&&result.language)parts.push(result.language.toUpperCase());
 result.identity_display=title224(parts);result.title=result.identity_display;
 if(result.model)result.model=result.identity_display;
 return result;
}
function recoveryRequests(l,result=reduce(l,[])){
 if(result.market_ready)return [];
 const pending=new Set(result?.variant_resolution?.pending||[]),requests=[];
 if(['sealed','generic'].includes(l.domain))return [];
 const conflict=l.domain!=='pokemon'&&result.core_identity?.status!=='confirmed'&&l.pick('collector_number')&&!l.candidates.some(e=>e.eligible&&l.pick('collector_number')&&numbersMatch(e.number,l.pick('collector_number').value))&&l.candidates.some(e=>e.grounded&&subjectMatch(keyValues(l).subject,e.subject)&&e.reasons?.includes('different_number')&&!e.reasons.includes('different_printed_product'));
 if(result.core_identity?.status!=='confirmed'&&!l.evidence('pokedex_number').length&&(!l.pick('collector_number')||conflict))pending.add('collector_number');
 if(l.domain==='pokemon'&&result.core_identity?.status!=='confirmed'&&!pokemonKeys204(l).year&&(l.active('copyright').length||l.evidence('pokedex_number').length))pending.add('copyright');
 if(l.active('serial').some(a=>serial(a.value)||/^\d{1,6}\s*\/\s*[?\d]{1,6}$/.test(a.value))&&!l.pick('serial'))pending.add('serial');
 for(const field of ['serial','collector_number','copyright','stamp','shadow','rarity_symbol','finish','language','variant'])if(pending.has(field)){
  const values=l.active(field).filter(a=>a.region&&(field!=='serial'||serial(a.value)||/^\d{1,6}\s*\/\s*[?\d]{1,6}$/.test(a.value))),best=values.find(a=>a.rotation&&serial(a.value))||values.find(a=>numberParts(a.value))||values[0];
  // No blanket hunt for any small number: only a typed observation or fraction can request a serial read.
  if(field==='serial'&&!values.some(a=>serial(a.value)||a.source==='vision'))continue;
  let crop=best?.region|| (l.domain==='pokemon'&&field==='copyright'?pokemonPanels202(l.base).find(p=>p.role==='lower')?.region:null)||null;if(['serial','collector_number'].includes(field)&&crop){const nearby=values.filter(a=>a.image_index===best.image_index&&near(a.region,crop)).map(a=>a.region);crop=unionRegions199(nearby);}
  const back=field==='collector_number'&&l.domain==='sports'?list(l.base.image_views).find(v=>v.view==='back'&&v.certainty==='clear'):null;if(!crop&&back)crop=list(l.base.object_regions).find(r=>r.image_index===back.image_index)||null;
  const key='detail:'+field+':'+(crop?JSON.stringify(crop):'front');if(l.attempts.has(key))continue;
  requests.push({key,field,reason:field==='collector_number'&&conflict?'catalogue_number_conflict':field==='stamp'&&result.variant_resolution?.printing_conflict?'printing_compatibility':undefined,region:crop,image_index:best?.image_index||back?.image_index||1,rotation:best?.rotation||(field==='serial'&&crop&&crop.height>crop.width*1.4?90:0),readings:values.map(a=>({id:a.id,text:a.raw,value:a.value,source:a.source,rotation:a.rotation||0})).slice(0,8)});
 }
 return requests.slice(0,3);
}
function applyDetails(l,details,requests){for(const d of list(details)){
 const req=requests.find(r=>r.field===d.field&&r.image_index===d.image_index);if(!req||d.certainty!=='clear'||d.evidence_found===false)continue;
 if(d.field==='shadow'&&req.reason==='shadow_border_verification'&&(!['present','absent'].includes(d.right_border)||d.right_border!==d.bottom_border||d.right_border!==(d.text??d.value)||!str(d.visual_reason))){l.record('detail_rejected',{field:'shadow',reason:'missing_or_discordant_border_evidence'});continue;}
 if(d.field==='shadow'&&req.spatial_verification){
  const b=d.illustration_bounds,valid=b&&[b.x,b.y,b.width,b.height].every(Number.isFinite)&&b.x>=0&&b.y>=0&&b.width>0&&b.height>0&&b.x+b.width<=1&&b.y+b.height<=1;
  const right=d.right_transition,bottom=d.bottom_transition,allowed=['frame_background','frame_shadow_background'];
  if(!valid||!allowed.includes(right)||right!==bottom||(d.text==='present')!==(right==='frame_shadow_background')||!d.right_description||!d.bottom_description){l.record('detail_rejected',{field:'shadow',reason:'unverified_spatial_transition'});continue;}
 }
 const reported=d.text??d.value;if(['collector_number','serial'].includes(d.field)&&/^(?:assente|absent|not present|not found|nessuno)$/i.test(str(reported))){l.record('detail_rejected',{field:d.field,reason:'identifier_not_found',raw:reported,image_index:req.image_index});continue;}
 const raw=d.text??d.value,value=presenceFields.includes(d.field)?(['present','absent'].includes(raw)?raw:presenceText(d.field,raw)):d.field==='finish'?finish(raw):d.field==='language'?language(raw):d.field==='collector_number'?number(raw):raw;
 if(d.field==='collector_number'&&semanticField(l.domain,d.field,value)!==d.field){l.record('detail_rejected',{field:d.field,value,reason:'non_identifier_text'});continue;}
 if(d.field==='copyright'&&!/\b(?:19|20)\d{2}\b/.test(str(value))){l.record('detail_rejected',{field:d.field,reason:'no_printed_year'});continue;}
 if(!value||d.field==='serial'&&!serial(value)||d.field==='collector_number'&&!numberParts(value))continue;
 if(presenceFields.includes(d.field)&&!['present','absent'].includes(value))continue;
 const correction=d.evidence_found===true&&(['catalogue_number_conflict','code_preflight','identity_band'].includes(req.reason)&&d.field==='collector_number'||req.reason==='printing_compatibility'&&d.field==='stamp'||req.reason==='shadow_border_verification'&&d.field==='shadow');
 const previous=l.evidence(d.field).filter(a=>!(correction&&a.image_index===req.image_index&&a.source==='vision'));if(previous.some(a=>!(d.field==='collector_number'?numbersMatch(a.value,value):same(a.value,value)))){l.record('conflicting_detail',{field:d.field,value,previous:previous.map(a=>a.id)});continue;}
 l.add(d.field,value,{source:'focused_vision',certainty:'clear',image_index:req.image_index,region:req.region,raw,spatial_evidence:d.field==='shadow'?{illustration_bounds:d.illustration_bounds,right_transition:d.right_transition,bottom_transition:d.bottom_transition,right_description:d.right_description,bottom_description:d.bottom_description}:undefined,request_key:req.key,supersedes:l.values(d.field).filter(a=>a.image_index===req.image_index&&(a.certainty!=='clear'||correction&&a.source==='vision')).map(a=>a.id)});
 }return l;}

function unionRegions199(regions){if(!regions.length)return null;const x=Math.min(...regions.map(r=>r.x)),y=Math.min(...regions.map(r=>r.y));return {image_index:regions[0].image_index,x,y,width:Math.max(...regions.map(r=>r.x+r.width))-x,height:Math.max(...regions.map(r=>r.y+r.height))-y};}
function pokemonPanels202(base){
 const cards=list(base.object_regions).filter(r=>region(r));return cards.flatMap(r=>[
 {role:'whole',region:{...r}},
 {role:'upper',region:{...r,height:r.height*.26}},
 {role:'lower',region:{...r,y:r.y+r.height*.70,height:r.height*.30}},
 {role:'artwork',region:{...r,y:r.y+r.height*.17,height:r.height*.60}}
 ]);
}
function mapPokemonBands204(packet,plans){
 const map=row=>{
  if(!row.panel_role)return row;
  const panel=plans.find(p=>p.role===row.panel_role&&p.region.image_index===row.image_index),r=region(row.region);
  if(!panel||!r)return {...row,region:null};
  const p=panel.region;return {...row,panel_region:r,region:region({image_index:p.image_index,x:p.x+r.x*p.width,y:p.y+r.y*p.height,width:r.width*p.width,height:r.height*p.height})};
 };
 return {...packet,observations:list(packet.observations).map(map),features:list(packet.features).map(map)};
}
function applyIdentityBands202(l,packet){
 for(const o of list(packet.observations)){
  if(!['subject','collector_number','pokedex_number','copyright','illustrator','set_code','rarity_text','language'].includes(o.field)||!o.image_index||!region(o.region)||!str(o.text))continue;
  const value=o.field==='collector_number'?number(o.text):o.field==='language'?language(o.text):o.text,role=semanticField(l.domain,o.field,value);if(role!==o.field){l.record('identity_band_rejected',{field:o.field,value,reason:role});continue;}
  if(!value||o.field==='collector_number'&&!numberParts(value))continue;
  if(o.field==='subject'){const seen=l.pick('subject')?.value||value,base=v=>str(v).replace(/(?:VMAX|VSTAR|GX|EX|ex|V)$/,'').trim();if(same(base(seen),base(value)))for(const alias of list(o.alternatives))if(/[A-Za-z]/.test(alias)){const physicalSuffix=str(seen).match(/(?:VMAX|VSTAR|GX|EX|ex|V)$/)?.[0],aliasSuffix=str(alias).match(/(?:VMAX|VSTAR|GX|EX|ex|V)$/)?.[0];if(physicalSuffix&&aliasSuffix&&physicalSuffix!==aliasSuffix){l.record('identity_alias_rejected',{alias,reason:'different_subject_suffix'});continue;}l.add('subject_alias',alias,{source:'identity_band_translation',level:'inferred',certainty:'uncertain',image_index:o.image_index});}}
  const existing=l.active(o.field).filter(a=>a.image_index===o.image_index),conflict=existing.some(a=>a.certainty==='clear'&&!(o.field==='collector_number'?numbersMatch(a.value,value):same(a.value,value)));
  if(conflict&&!(o.field==='copyright'&&pokemonKeys204(l).year&&str(value).includes(pokemonKeys204(l).year))){l.record('identity_band_conflict',{field:o.field,value});continue;}
  const atom=l.add(o.field,value,{source:'identity_band',certainty:o.certainty==='clear'?'clear':'uncertain',image_index:o.image_index,region:o.region,raw:o.text,zone:o.zone,supersedes:o.certainty==='clear'?existing.filter(a=>a.certainty!=='clear').map(a=>a.id):[]});
  if(o.field==='rarity_text'&&o.certainty==='clear'&&/^[★☆●◆◇]+$/.test(str(value)))l.add('rarity_symbol','present',{source:'identity_band',certainty:'clear',image_index:o.image_index,region:o.region,raw:o.text,derived_from:atom.id,supersedes:l.active('rarity_symbol').filter(a=>a.certainty!=='clear').map(a=>a.id)});
 }
 for(const f of list(packet.features)){
  if(!['stamp','rarity_symbol','set_symbol','finish'].includes(f.field)||!region(f.region)||f.image_index!==f.region.image_index||!str(f.value))continue;
  const value=f.field==='finish'?finish(f.value):presenceFields.includes(f.field)?pokemonPresence204(f.field,f.value,f.description):f.value;if(!value||presenceFields.includes(f.field)&&!['present','absent'].includes(value))continue;
  l.add(f.field,value,{source:'identity_band',certainty:f.certainty==='clear'?'clear':'uncertain',image_index:f.image_index,region:f.region,raw:f.description||f.value,zone:f.zone,supersedes:f.certainty==='clear'?l.active(f.field).filter(a=>a.image_index===f.image_index&&a.certainty!=='clear').map(a=>a.id):[]});
 }
 reconcileIdentifiers201(l);return l;
}
function userVariant199(l,options){
 if(l.domain!=='sports')return null;const text=norm(l.userDetails||'').replace(/\b(?:rc|rookie card|rookie|variante|variant|parallela|parallel)\b/g,'').replace(/\s+/g,' ').trim();if(!text)return null;
 const hits=options.filter(v=>norm(v.name)===text),specific=keyValues(l).patterns.filter(p=>!['geometric','dots','squares'].includes(p));
 return hits.length===1&&(!specific.length||specific.every(p=>hits[0].patterns?.includes(p)))?hits[0]:null;
}
function applySurfaceInspection199(l,reply,imageIndexes){
 if(l.domain!=='sports')return;
 const surfaces=list(reply.surfaces).filter(s=>imageIndexes.includes(s.image_index)&&s.fully_visible===true&&s.legible===true);
 const read=reply.serial||{},sn=serial(read.text);if(sn&&read.certainty==='clear'&&read.evidence_found===true&&imageIndexes.includes(read.image_index))applyDetails(l,[{...read,field:'serial'}],[{field:'serial',image_index:read.image_index,key:'surface:serial'}]);
 const absent=reply.serial_presence==='absent'&&surfaces.some(s=>s.side==='front')&&surfaces.some(s=>s.side==='back')&&new Set(surfaces.map(s=>s.image_index)).size>=2&&imageIndexes.every(i=>surfaces.some(s=>s.image_index===i))&&!sn&&!l.evidence('serial').length;
 if(absent)l.add('serial_presence','absent',{source:'surface_inspection',certainty:'clear',image_index:surfaces[0].image_index,checked_images:imageIndexes,raw:'Entire front and back inspected; no specimen serial visible'});
 l.record('surface_inspection',{reply,accepted_absence:absent,images:imageIndexes});
}
const api={normalizePrinting226,title224,configuration,publication222,publisher222,canonicalSet222,comparisonValue222,comparisonYear222,copyrightYear222,printingSubset222,observationRole220,productEquivalent217,photoProfile216,sportsSeason215,mapPokemonBands204,pokemonKeys204,pokemonAliases204,pokemonPresence204,pokemonPolicy204,pokemonNumbers203,pokemonQuery203,pokemonPanels202,applyIdentityBands202,reconcileIdentifiers201,printingLanguageCompatible201,sourceUrl201,productText201,identityPresentation200,unionRegions199,userVariant199,applySurfaceInspection199,semanticField,familyKey,subsetKey,distinctiveMarks,canonicalEntries,coreKey,Ledger,ingestVision,ingestOcr,keyValues,profile,number,numberParts,numbersMatch,serial,language,season,finish,norm,same,subjectMatch,tokens,COLOR_WORDS,PATTERNS,query,domains,sourceTrusted,evaluate,candidateGroups,printingScope,variantState,photoRequest,reduce,recoveryRequests,applyDetails,presenceText,reconcileVisualEvidence,assertConsistent};
if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckCatalogueEngine=api;
})(typeof window==='undefined'?globalThis:window);
