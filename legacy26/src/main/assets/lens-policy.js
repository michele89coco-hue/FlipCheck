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
const api={normalize,attributes,evaluate,select,ranked,fallbackReason,url};
if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckLens=api;
})(typeof globalThis!=='undefined'?globalThis:this);
