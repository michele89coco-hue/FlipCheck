/* Catalogue identity engine. Observations are append-only; only reduce() publishes an identity. */
(function(root){
'use strict';
const list=x=>Array.isArray(x)?x:[],str=x=>String(x??'').trim(),clone=x=>JSON.parse(JSON.stringify(x)),norm=x=>str(x).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[’']/g,'').replace(/[^a-z0-9\u3040-\u30ff\u3400-\u9fff]+/g,' ').trim();
const unique=x=>[...new Set(x.filter(Boolean))],same=(a,b)=>norm(a)===norm(b);
const COLOR_WORDS={green:['green','verde'],blue:['blue','blu','azzurro'],red:['red','rosso','rossa'],gold:['gold','golden','oro'],silver:['silver','argento'],black:['black','nero','nera'],white:['white','bianco','bianca'],purple:['purple','viola'],pink:['pink','rosa'],orange:['orange','arancione'],yellow:['yellow','giallo'],teal:['teal'],aqua:['aqua'],bronze:['bronze','bronzo']};
const PATTERNS={wave:['wave','waves','onde','ondulato'],raywave:['raywave'],ice:['ice','cracked ice','ghiaccio'],mojo:['mojo'],checkerboard:['checker','checkerboard','scacchi','checkered'],pulsar:['pulsar'],shimmer:['shimmer'],mosaic:['mosaic','mosaico'],disco:['disco'],honeycomb:['honeycomb','nido d ape'],snakeskin:['snakeskin'],dragon_scale:['dragon scale'],sparkle:['sparkle'],geometric:['geometric','geometrico'],dots:['dots','puntini'],squares:['squares','square','quadrati']};
function tokens(value,dictionary){const t=' '+norm(value)+' ';return Object.keys(dictionary).filter(k=>dictionary[k].some(v=>t.includes(' '+v+' ')));}
function number(value){return str(value).replace(/^(?:no\.?|n[°º.]|number|numero|card\s*#?|carta\s*#?|#)\s*/i,'').replace(/\s+/g,'').toUpperCase();}
function numberParts(value){const n=number(value),m=n.match(/^((?:[A-Z]{1,8}\d{0,4}-)?[A-Z]{0,8}\d+[A-Z]?)(?:\/([A-Z]{0,8}\d+[A-Z]?))?$/);return m?{full:n,local:m[1].replace(/^0+(?=\d)/,''),total:m[2]||''}:null;}
function numbersMatch(a,b){const x=numberParts(a),y=numberParts(b);return !!x&&!!y&&x.local===y.local&&(!x.total||!y.total||x.total===y.total);}
function serial(value){const m=str(value).match(/^\s*(?:serial(?:\s*number)?\s*:?\s*)?(\d{1,6})\s*\/\s*(\d{1,6})\s*$/i);if(!m||+m[1]<1||+m[2]<1||+m[1]>+m[2])return null;return {value:+m[1]+'/'+(+m[2]),specimen:+m[1],print_run:+m[2]};}
function language(v){const t=norm(v);return ({english:'en',inglese:'en',en:'en',italian:'it',italiano:'it',it:'it',japanese:'ja',giapponese:'ja',jp:'ja',ja:'ja',french:'fr',francese:'fr',fr:'fr',german:'de',tedesco:'de',de:'de',spanish:'es',spagnolo:'es',es:'es',korean:'ko',coreano:'ko',ko:'ko'})[t]||'';}
function season(v){return str(v).match(/\b(?:19|20)\d{2}(?:\s*[-/]\s*(?:\d{4}|\d{2}))?\b/)?.[0].replace(/\s/g,'').replace('/','-')||'';}
function finish(v){const t=norm(v);if(/\b(?:non holo|non holographic|non olografica|non olografico|nonholo|normal|matte)\b/.test(t))return 'normal';if(/reverse|invers/.test(t))return 'reverse';if(/holo|olograf/.test(t))return 'holo';if(/refractor|refractive|reflective|prizm|prismatic|foil|metallic/.test(t))return 'reflective';return '';}
function region(r){return r&&r.image_index>0&&[r.x,r.y,r.width,r.height].every(Number.isFinite)&&r.x>=0&&r.y>=0&&r.width>0&&r.height>0&&r.x+r.width<=1.005&&r.y+r.height<=1.005?clone(r):null;}
function mapOcrRegion(line,ocr){const m=ocr.meta||{},crop=m.rect||m.originalRect||{x:0,y:0,width:m.originalWidth||ocr.width||1,height:m.originalHeight||ocr.height||1},w=m.originalWidth||ocr.width||1,h=m.originalHeight||ocr.height||1;return region({image_index:ocr.image_index,x:(crop.x+line.x*crop.width)/w,y:(crop.y+line.y*crop.height)/h,width:line.width*crop.width/w,height:line.height*crop.height/h});}
function near(a,b){return !!a&&!!b&&a.image_index===b.image_index&&Math.abs(a.x+a.width/2-b.x-b.width/2)<Math.max(.04,(a.width+b.width)/2)&&Math.abs(a.y+a.height/2-b.y-b.height/2)<Math.max(.025,(a.height+b.height)/2);}
function subjectMatch(a,b){const x=norm(a),y=norm(b);return x===y||x.split(' ').sort().join(' ')===y.split(' ').sort().join(' ');}
function profile(base){if(base.domain&&base.domain!=='unknown')return base.domain;if(base.object_unit==='box'||base.object_unit==='case'||/\bbox\b|confezione|sealed/i.test(base.category||''))return 'sealed';if(base.pokemon_printing?.is_pokemon||/pokemon|pokémon/i.test(base.category||''))return 'pokemon';if(/one piece/i.test([base.category,base.family].join(' ')))return 'onepiece';if(base.kind==='card'&&/panini|topps|upper deck|leaf|fleer|skybox|basket|soccer|football|baseball|hockey/i.test([base.brand,base.family,base.category].join(' ')))return 'sports';return 'generic';}
class Ledger {
 constructor(base){this.base=clone(base||{});this.domain=profile(base||{});this.atoms=[];this.events=[];this.candidates=[];this.attempts=new Set();this.sequence=0;}
 add(field,value,meta={}){if(value===null||value===undefined||str(value)==='')return;const atom={id:'e'+(++this.sequence),field,value:str(value),level:meta.level||'observed',certainty:meta.certainty||'uncertain',source:meta.source||'vision',region:region(meta.region),image_index:meta.image_index||meta.region?.image_index||0,raw:meta.raw??str(value),...meta};atom.region=region(atom.region);if(atom.level==='observed'&&!atom.region&&!atom.image_index)atom.level='inferred';if(field==='serial'&&!serial(atom.value))atom.certainty='uncertain';Object.freeze(atom);this.atoms.push(atom);return atom;}
 values(field){return this.atoms.filter(a=>a.field===field);}
 evidence(field){const values=this.values(field),replaced=new Set(values.flatMap(a=>list(a.supersedes)));return values.filter(a=>!replaced.has(a.id)&&a.level==='observed'&&a.certainty==='clear');}
 pick(field){const all=this.evidence(field);if(!all.length)return null;if(field==='collector_number'){if(all.every(a=>all.every(b=>numbersMatch(a.value,b.value))))return [...all].sort((a,b)=>b.value.length-a.value.length)[0];return null;}return unique(all.map(a=>norm(a.value))).length===1?all[all.length-1]:null;}
 record(stage,data={}){this.events.push({stage,...clone(data)});}
 attempt(key){if(this.attempts.has(key))return false;this.attempts.add(key);return true;}
 snapshot(){return {version:193,domain:this.domain,observations:this.atoms,events:this.events,attempts:[...this.attempts],candidates:this.candidates};}
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
  if(o.feature==='configuration')ledger.add('configuration',o.text,meta);
 }
}
function ingestVision(ledger,base,source='vision'){
 if(list(base.observations).length){for(const o of base.observations){ledger.add(o.zone==='statistics'?'statistics_text':o.field,o.field==='language'?language(o.text??o.value):o.field==='collector_number'?number(o.text??o.value):o.field==='hp'?str(o.text??o.value).match(/\d+/)?.[0]:o.text??o.value,{source,certainty:o.certainty,region:o.region,image_index:o.image_index,zone:o.zone,raw:o.text??o.value});for(const a of list(o.alternatives))ledger.add(o.field,typeof a==='string'?a:a.text,{source,certainty:'uncertain',region:o.region,image_index:o.image_index,alternative:true,raw:typeof a==='string'?a:a.text});}}
 else {
  for(const c of list(base.photo_clues)){
   const field=({subject:'subject',collector_number:'collector_number',serial:'serial',season:'season',copyright:'copyright',edition:'edition_text',team:'team',model:'product',barcode:'barcode',issue_number:'issue_number'})[c.role]||'text';
   ledger.add(field,field==='collector_number'?number(c.text):c.text,{source,certainty:c.certainty,region:c.region,image_index:c.image_index,raw:c.text});
   if(/^(?:\d+\s*)?(?:HP|PV)$|^\d+\s*(?:HP|PV)$/i.test(c.text))ledger.add('hp',str(c.text).match(/\d+/)?.[0],{source,certainty:c.certainty,region:c.region,image_index:c.image_index,raw:c.text});
  }
  legacyFeatures(ledger,base);
 }
 for(const f of list(base.features)){const vals=f.field==='border_color'?tokens(f.value,COLOR_WORDS):f.field==='pattern'?tokens(f.value,PATTERNS):[f.field==='finish'?finish(f.value)||'unclear':f.value];for(const v of vals)ledger.add(f.field,v,{source,certainty:f.certainty,region:f.region,image_index:f.image_index,zone:f.zone,raw:f.description||f.value});}
 for(const h of list(base.hypotheses))ledger.add(h.field,h.value,{source,level:'inferred',certainty:'uncertain'});
 const p=base.pokemon_printing||{};
 for(const [field,value,image,location] of [['stamp',p.first_edition_stamp,p.stamp_image,p.stamp_location],['shadow',p.artwork_shadow,p.shadow_image,p.shadow_location]])if(['present','absent'].includes(value)&&image&&location)ledger.add(field,value,{source,certainty:'clear',image_index:image,raw:location});
 const lang=language(base.language||p.language);if(lang)ledger.add('language',lang,{source,certainty:'clear',image_index:1});
 if(base.brand)ledger.add('brand',base.brand,{source,level:'inferred'});
 if(base.family)ledger.add('family',base.family,{source,level:'inferred'});
 return ledger;
}
function ingestOcr(ledger,reports){
 for(const o of list(reports).filter(o=>o.state==='ok'))for(const line of list(o.lines)){
  const readings=[line,...list(line.alternatives)],r=mapOcrRegion(line,o),nearby=ledger.atoms.filter(a=>a.source==='vision'&&near(a.region,r));
  const table=/\b(?:FG%|FT%|PPG|RPG|APG|3PM|INTERNATIONAL TOTALS|SEASON\s+TEAM)\b/i.test(o.text||'');
  for(const item of readings){
   const rr=mapOcrRegion(item,o)||r,text=str(item.text),sn=serial(text),num=number(text),np=numberParts(num),context=nearby.find(a=>['serial','collector_number','copyright'].includes(a.field));
   const common={source:'local_ocr',certainty:'uncertain',region:rr,image_index:o.image_index,raw:text,pass:item.pass||line.pass,rotation:item.rotation_degrees||line.rotation_degrees||0,engine_confidence:item.engine_confidence,alternative:item!==line};
   ledger.add('ocr_text',text,common);
   if(context?.field==='collector_number'&&np)ledger.add('collector_number',np.full,common);
   else if(ledger.domain==='pokemon'&&/^[A-Z]{1,6}\d+[Il|][A-Z]{1,6}\d+[A-Z]?$/.test(num)){
    const m=num.match(/^([A-Z]{1,6}\d+)[Il|]([A-Z]{1,6}\d+)[A-Z]?$/);if(m)ledger.add('collector_number',m[1]+'/'+m[2],{...common,normalization:'possible_separator_confusion'});
   }else if(ledger.domain==='pokemon'&&np&&np.total&&rr?.y>.65)ledger.add('collector_number',np.full,common);
   else if(ledger.domain==='sports'&&sn&&!nearby.some(a=>a.field==='collector_number')&&(context?.field==='serial'||rr&&(rr.x>.7||rr.x<.2||rr.y>.65)))ledger.add('serial',sn.value,common);
   else if(context?.field==='serial')ledger.add('serial',text,common);
   else if(table&&/^\d{1,4}$/.test(text))ledger.add('statistics_number',text,common);
  }
 }
 return ledger;
}
function keyValues(l){return {subject:l.pick('subject')?.value||'',numbers:unique(l.values('collector_number').filter(a=>a.level==='observed').map(a=>numberParts(a.value)?.full)),year:season(l.pick('season')?.value)||'',copyright:unique(l.values('copyright').flatMap(a=>str(a.value).match(/\b(?:19|20)\d{2}\b/g)||[])),language:l.pick('language')?.value||'',brands:unique(l.values('brand').map(a=>a.value)),colors:unique(l.evidence('border_color').map(a=>a.value)),patterns:unique(l.evidence('pattern').map(a=>a.value)),serials:unique(l.values('serial').filter(a=>a.level==='observed').map(a=>serial(a.value)?.value)),products:unique(l.evidence('product').concat(l.domain==='generic'?l.evidence('model_code'):[]).map(a=>a.value))};}
function domains(l){const b=norm([l.base.brand,...l.values('brand').map(a=>a.value),...l.evidence('text').map(a=>a.value)].join(' '));if(l.domain==='generic')return [];if(l.domain==='pokemon')return ['tcgdex.net','pokemon.com','pokemontcg.io','bulbapedia.bulbagarden.net','wiki.pokemoncentral.it','psacard.com'];if(l.domain==='onepiece')return ['en.onepiece-cardgame.com','onepiece-cardgame.com'];const makers=[];if(/topps|bowman/.test(b))makers.push('topps.com','ripped.topps.com');if(/panini/.test(b))makers.push('paniniamerica.net');if(/upper deck|fleer|skybox/.test(b))makers.push('upperdeck.com');if(/leaf/.test(b))makers.push('leaftradingcards.com');return unique([...makers,'beckett.com','checklistinsider.com']);}
function query(l,mode='identity'){
 const k=keyValues(l),observed=l.evidence('text').filter(a=>!/\b(?:HP|PV)\b|^[◆★●]$/.test(a.value)),product=unique([...k.products,...observed.filter(a=>/topps|chrome|update|select|prizm|upper deck|leaf|panini|terrace|mezzanine|field level/i.test(a.value)).map(a=>a.value)]).join(' ');
 const card=l.domain!=='sealed',name=card?k.subject:'',nums=card?k.numbers.slice(0,3).map(v=>'"'+(numberParts(v)?.local||v)+'"').join(' OR '):'';
 const date=k.year||k.copyright[k.copyright.length-1]||'';
 const variant=['sports','onepiece'].includes(l.domain)?[...k.colors,...k.patterns,...k.serials.map(v=>'/'+serial(v).print_run),...['autograph','patch'].filter(f=>l.pick(f)?.value==='present')].join(' '):'';
 const config=l.domain==='sealed'?l.evidence('text').filter(a=>/autograph|packs?\b|cards? per|bustine|confezion/i.test(a.value)).map(a=>a.value).join(' '):'';
 return [l.domain==='pokemon'?'Pokémon TCG':l.domain==='onepiece'?'One Piece card':product||l.base.brand,name,nums,date,variant,config,l.domain==='sealed'?'box configuration':l.domain==='generic'?'model specifications':mode==='variant'?'parallel variants checklist':'checklist',domains(l).length?'('+domains(l).map(d=>'site:'+d).join(' OR ')+')':''].filter(Boolean).join(' ').replace(/\s+/g,' ').trim();
}
function sourceTrusted(url){try{return /(^|\.)(?:tcgdex\.net|pokemontcg\.io|pokemon\.com|pokemon-card\.com|bulbagarden\.net|pokemoncentral\.it|serebii\.net|psacard\.com|cgccards\.com|beckett\.com|checklistinsider\.com|topps\.com|paniniamerica\.net|upperdeck\.com|leaftradingcards\.com|onepiece-cardgame\.com)$/.test(new URL(url).hostname);}catch(_){return false;}}
function evaluate(l,entry){
 const k=keyValues(l),reasons=[],matches=[],strongNumbers=l.evidence('collector_number');let score=0;
 if(!entry.subject||!entry.family||!entry.number||!entry.source?.url||entry.grounded!==true)return {...entry,eligible:false,reasons:['ungrounded_entry'],score:0};
 const names=[entry.subject,...list(entry.aliases)];
 if(k.subject&&names.some(n=>subjectMatch(k.subject,n))){score+=35;matches.push('subject');}else if(k.subject)reasons.push('different_subject');
 if(k.numbers.some(n=>numbersMatch(n,entry.number))){score+=25;matches.push('number');if(strongNumbers.some(a=>numbersMatch(a.value,entry.number)))score+=15;else {const reads=l.values('collector_number').filter(a=>numbersMatch(a.value,entry.number));if(unique(reads.map(a=>a.source)).length>1)score+=8;}}
 else if(strongNumbers.length)reasons.push('different_number');
 if(k.language&&entry.language&&k.language!==language(entry.language)&&k.language!==entry.language)reasons.push('different_language');
 if(k.year&&entry.year){if(season(entry.year)===k.year){score+=10;matches.push('season');}else reasons.push('different_product_season');}
 // Copyright is supporting evidence, never a hard product-year veto.
 if(k.copyright.some(y=>str(entry.year).startsWith(y)))score+=3;
 const hp=l.pick('hp');if(hp&&entry.hp){if(+hp.value===+entry.hp){score+=5;matches.push('hp');}else reasons.push('different_hp');}
 const entryText=norm([entry.entry_quote,...list(entry.attacks)].join(' '));
 const attacks=l.evidence('attack').concat(l.domain==='pokemon'?l.evidence('text').filter(a=>a.value.length>7&&!/copyright|pok[eé]mon|nintendo|wizards|HP|PV/i.test(a.value)):[]);
 const attackHits=attacks.filter(a=>entryText.includes(norm(a.value)));score+=Math.min(12,attackHits.length*4);if(attackHits.length)matches.push('attacks');
 const seenProducts=k.products.filter(v=>norm(v)!=='prizm'||l.domain==='sports');
 for(const p of seenProducts)if(!norm([entry.family,entry.subset,entry.brand,entry.variant].join(' ')).includes(norm(p))&&!norm(p).includes(norm(entry.family)))reasons.push('different_printed_product');
 return {...entry,eligible:!reasons.length,score,matches,reasons};
}
function candidateGroups(l,entries){
 const evaluated=entries.map(e=>evaluate(l,e)),eligible=evaluated.filter(e=>e.eligible).sort((a,b)=>b.score-a.score);
 l.candidates=evaluated.map(({image_data,...e})=>e);
 const groups=new Map();for(const e of eligible){const id=[norm(e.subject),norm(e.family),number(e.number),e.language||keyValues(l).language||'',season(e.year),norm(e.subset||'Base').replace(/^.*base(?: set)?(?: checklist)?.*$/,'base')].join('|');if(!groups.has(id))groups.set(id,{id,entry:e,entries:[],score:e.score});const g=groups.get(id);g.entries.push(e);g.score=Math.max(g.score,e.score);}
 return [...groups.values()].sort((a,b)=>b.score-a.score);
}
function printingScope(l,e){
 const lang=language(e?.language)||l.pick('language')?.value,set=norm(e?.family),year=+season(e?.year).slice(0,4),base=/^(?:pokemon game |pokemon |set )?(?:base set|base|basic|set base|set de base|basis|expansion pack|拡張パック)$/.test(set)||e?.set_id==='base1';
 const shadow=lang==='en'&&base&&l.base.pokemon_printing?.card_type!=='energy';
 const historicalFirst=['jungle','giungla','dschungel','jungla','selva','fossil','fossile','fossiles','fosil','team rocket','gym heroes','gym challenge','neo genesis','neo discovery','neo revelation','neo destiny'].includes(set);
 const firstEdition=l.pick('stamp')?.value==='present'||e?.printing_options?.includes('first_edition')===true||(['en','it','fr','de','es','pt','nl'].includes(lang)&&(base||historicalFirst));
 const noRarity=lang==='ja'&&base&&(!year||year===1996);
 return {shadow,firstEdition,noRarity,language:lang,base,scope_source:'set_language_catalogue',source:'https://www.cgccards.com/news/article/10262/pokemon-first-editions/'};
}
function variantOptions(l,group){
 const options=group.entries.flatMap(e=>list(e.variants).map(v=>({...v,source:v.source||e.source}))),uniqueOptions=[];
 for(const o of options)if(!uniqueOptions.some(x=>same(x.name,o.name)&&x.print_run===o.print_run&&JSON.stringify(x.colors||[])===JSON.stringify(o.colors||[])&&JSON.stringify(x.patterns||[])===JSON.stringify(o.patterns||[])))uniqueOptions.push(o);
 return uniqueOptions;
}
function variantState(l,group){
 const e=group.entry,k=keyValues(l),pending=[],labels=[],proof=[],fields={},options=variantOptions(l,group),physicalSerial=l.pick('serial'),sn=physicalSerial&&serial(physicalSerial.value);
 if(l.domain==='pokemon'){
  const scope=printingScope(l,e),observedFinish=l.pick('finish')?.value;
  fields.printingScope=scope;
  for(const [active,field,positive,negative] of [[scope.firstEdition,'stamp','1st Edition','Unlimited'],[scope.shadow,'shadow','Shadowed','Shadowless'],[scope.noRarity,'rarity_symbol','Rarity Symbol','No Rarity Symbol']])if(active){
   let read=l.pick(field),value=read?.value;if(field==='stamp'&&!read&&l.evidence('edition_text').some(a=>/1st|first|edition\s*1|1\s*edition/i.test(a.value)))value='present';
   if(['present','absent'].includes(value)){labels.push(value==='present'?positive:negative);proof.push({field,value,observation:read?.id||l.evidence('edition_text')[0]?.id});}else pending.push(field);
  }
  const finishes=unique(options.map(v=>v.finish).filter(Boolean));
  if(finishes.length===1&&e.number&&/^H\d+/.test(number(e.number))&&/skyridge|aquapolis/i.test(e.family)){labels.push('Holo');proof.push({field:'finish',value:'holo',source:e.source,reason:'documented_holo_catalogue_entry'});}
  else if(observedFinish&&['normal','holo','reverse'].includes(observedFinish)){
   if(!finishes.length||finishes.includes(observedFinish)){labels.push(({normal:'Non holo',holo:'Holo',reverse:'Reverse holo'})[observedFinish]);proof.push({field:'finish',value:observedFinish,observation:l.pick('finish').id});}
   else pending.push('finish');
  }else if(finishes.length===1){labels.push(({normal:'Non holo',holo:'Holo',reverse:'Reverse holo'})[finishes[0]]);proof.push({field:'finish',value:finishes[0],source:e.source,reason:'catalogue_single_printing'});}
  else pending.push('finish');
  if(e.rarity)fields.rarity=e.rarity;
 }else if(l.domain==='sports'||l.domain==='onepiece'){
  const compatible=options.filter(v=>(!sn||v.unnumbered!==true&&(v.print_run===undefined||v.print_run===null||(v.max_print_run?sn.print_run<=+v.print_run:+v.print_run===sn.print_run)))&&(!v.colors?.length||!k.colors.length||v.colors.every(c=>k.colors.includes(c)))&&(!v.patterns?.length||!k.patterns.filter(p=>!['geometric','dots','squares'].includes(p)).length||v.patterns.some(p=>k.patterns.includes(p))));
  fields.variant_candidates=compatible;
  const compared=l.pick('catalogue_variant'),candidate=compared?compatible.find(v=>same(v.name,compared.value)):compatible.length===1?compatible[0]:null;
  const supported=candidate&&!candidate.max_print_run&&(!candidate.print_run||sn&&+candidate.print_run===sn.print_run)&&(!candidate.colors?.length||candidate.colors.every(c=>k.colors.includes(c)))&&(!candidate.patterns?.length||candidate.patterns.every(p=>k.patterns.includes(p)))&&(candidate.visual_required!==true||!!compared);
  if(supported&&(sn||candidate.colors?.length||candidate.patterns?.length||candidate.name==='Base'&&l.pick('finish')?.value==='normal')){labels.push(candidate.name);proof.push({field:'variant',value:candidate.name,source:candidate.source});}
  else pending.push('variant');
  if(l.values('serial').length&&!sn)pending.push('serial');
  for(const feature of ['autograph','patch'])if(l.pick(feature)?.value==='present'){const pattern=feature==='autograph'?/autograph|signature|signed/i:/patch|memorabilia|relic/i;if(!pattern.test([e.subset,candidate?.name].join(' ')))pending.push(feature);else proof.push({field:feature,value:'present',observation:l.pick(feature).id,source:e.source});}
  if(sn)fields.physical_serial={...sn,origin:'original_photo',observation:physicalSerial.id};
 }
 return {status:pending.length?'pending':'confirmed',pending:unique(pending),labels:unique(labels),proof,...fields};
}
function photoRequest(pending,domain){const p=new Set(pending);if(p.has('serial'))return 'Fotografa da vicino la numerazione dell’esemplare: devono essere leggibili entrambi i numeri e la barra, ad esempio 2/5.';if(p.has('collector_number'))return 'Fotografa da vicino il numero della carta, includendo tutte le lettere e gli eventuali numeri dopo la barra.';if(p.has('subject'))return 'Fotografa il nome e il numero della carta mantenendoli nitidi e interamente visibili.';if(p.has('rarity_symbol'))return 'Fotografa da vicino l’angolo con il simbolo di rarità, mantenendo visibile anche il bordo della carta.';if(p.has('shadow'))return 'Fotografa il fronte senza riflessi, mantenendo nitidi il bordo destro e inferiore dell’illustrazione e la riga del copyright.';if(p.has('stamp'))return 'Fotografa da vicino la zona del timbro First Edition sotto l’illustrazione.';if(domain==='sealed')return 'Fotografa lato e retro della confezione: formato, numero di bustine e carte, codice prodotto e contenuto garantito.';if(p.has('variant')||p.has('finish'))return 'Fotografa il fronte leggermente inclinato alla luce, mantenendo visibili il pattern della finitura e tutti i bordi.';return domain==='generic'?'Fotografa da vicino l’etichetta con marca e codice modello.':'Fotografa il fronte completo e nitido, con nome e numero della carta.';}
function configuration(value){
 const s=norm(value),odds=s.match(/(\d+)\s+autographs?\s+(?:in\s+)?every\s+(\d+)\s+boxes/),perBox=s.match(/(\d+)\s+autographs?(?:\s+cards?)?\s+(?:per|in each|in every)\s+box/),packs=s.match(/(\d+)\s+packs?\s+per\s+box/),cards=s.match(/(\d+)\s+cards?\s+per\s+pack/);
 return {autographs:odds?+odds[1]/+odds[2]:perBox?+perBox[1]:null,packs:packs?+packs[1]:null,cards:cards?+cards[1]:null};
}
function reduceObject(l,entries,error){
 const k=keyValues(l),reads=l.evidence('product').concat(l.evidence('text')),physical=reads.map(a=>a.value),brand=l.pick('brand')?.value||l.base.brand||'',product=k.products.join(' ')||physical.filter(t=>/chrome|update series|topps|select|upper deck|leaf|panini/i.test(t)).join(' '),year=k.year;
 const observed=configuration(physical.join(' ')),candidates=[];
 for(const e of entries){
  if(!e.grounded||!e.source?.url||!e.family)continue;
  const text=norm([e.family,e.subject,e.entry_quote].join(' ')),parts=physical.filter(t=>/chrome|update series|topps|select|upper deck|leaf|panini/i.test(t));
  const matched=l.domain==='sealed'?parts.length>0&&parts.every(t=>text.includes(norm(t))):k.products.length>0&&k.products.every(t=>text.includes(norm(t)));
  if(!matched||year&&e.year&&season(e.year)!==year)continue;
  const config=configuration(e.entry_quote||''),conflicts=Object.keys(observed).filter(f=>observed[f]!==null&&config[f]!==null&&observed[f]!==config[f]);
  const exactConfig=Object.keys(observed).filter(f=>observed[f]!==null).length>=2&&Object.keys(observed).every(f=>observed[f]===null||observed[f]===config[f]);
  const code=l.pick('barcode')?.value||l.pick('sku')?.value||l.pick('model_code')?.value,codeMatch=!!code&&str(e.entry_quote).includes(code);
  candidates.push({...e,configuration_conflicts:conflicts,exact:!conflicts.length&&(codeMatch||l.domain==='generic'&&!!k.products.length||exactConfig)});
 }
 l.candidates=candidates;const variants=unique(candidates.filter(e=>e.exact).map(e=>norm([e.family,e.subject,e.number].join(' ')))),exact=variants.length===1,core=!!product&&(l.domain==='sealed'||candidates.length>0),entry=exact?candidates.find(e=>e.exact):candidates[0],family=product||entry?.family||'',model=[year,family].filter(Boolean).join(' · '),pending=exact?[]:[l.domain==='sealed'?'configuration':'model'];
 const result={...l.base,engine_version:193,engine_final:true,kind:'object',title:model||l.base.title,brand,family,model:core?model:'',variant:exact?entry.subject:'',model_verified:core,model_confidence:null,market_ready:exact,catalogue_core_verified:!!entry,catalogue_verified:exact,status:core?'identified':'uncertain',identity_status:core?'confirmed':'partial',exact_identity_status:exact?'confirmed':core?'variant_pending':'unresolved',core_identity:{status:core?'confirmed':'partial',origin:entry?'catalogue_and_photo':'printed_product',model,fields:[{field:'product',value:family,origin:'photo'},{field:'year',value:year,origin:'photo'}].filter(f=>f.value),pending_fields:core?[]:pending},variant_resolution:{status:exact?'confirmed':'pending',pending,labels:exact?[entry.subject]:[],proof:[]},variant_needs_verification:!exact,variant_check:exact?'confirmed':'pending',assistance_state:exact?'confirmed':error||'physical_detail_needed',missing_information:exact?[]:[l.domain==='sealed'?'Formato e configurazione della confezione':'Codice modello'],next_photo_request:exact?null:photoRequest(pending,l.domain),normalized_query:exact?[model,entry.subject].join(' '):'',verification_summary:exact?'Prodotto e configurazione identificati.':'Prodotto riconosciuto; resta il dettaglio indicato.',identification_sources:candidates.map(e=>e.source),candidate_models:candidates.filter(e=>!e.configuration_conflicts.length).map(e=>({model:e.subject,reason:'Configurazione da verificare'}))};
 l.record('reduce',{core:result.core_identity.status,exact:result.exact_identity_status,pending});return result;
}
function reduce(l,entries,{error=null}={}){
 if(['sealed','generic'].includes(l.domain))return reduceObject(l,entries,error);
 const groups=candidateGroups(l,entries),top=groups[0],k=keyValues(l),base=l.base;
 const uniqueMatch=top&&top.score>=75&&(!groups[1]||top.score-groups[1].score>=12),entry=uniqueMatch?top.entry:null;
 const shared=top?.score>=60&&groups.length&&groups.every(g=>same(g.entry.family,top.entry.family)&&subjectMatch(g.entry.subject,top.entry.subject));
 const family=entry?.family||(shared?top.entry.family:''),subject=entry?.subject||k.subject,cardNumber=entry?.number||l.pick('collector_number')?.value||'',date=entry?.year||k.year;
 const variation=entry?variantState(l,top):{status:'pending',pending:[!subject?'subject':!cardNumber?'collector_number':'catalogue'],labels:[],proof:[]};
 const exact=!!entry&&variation.status==='confirmed',fields=[];
 for(const [field,value] of [['subject',subject],['family',family],['catalog_number',cardNumber],['year',date],['brand',entry?.brand||base.brand]])if(value)fields.push({field,value,origin:entry?'catalogue_and_photo':field==='family'?'catalogue':'photo',source:entry?.source||null});
 const model=[date,family,cardNumber?'#'+cardNumber:'',subject].filter(Boolean).join(' · '),pending=variation.pending;
 const result={engine_version:193,engine_final:true,kind:base.kind||'card',category:base.category||'Carta',brand:entry?.brand||base.brand||'',family,model:entry?model:'',title:model||base.title||'Oggetto da identificare',variant:variation.labels.join(' · '),language:k.language,condition:base.condition||'',
  status:entry?'identified':'uncertain',identity_status:entry?'confirmed':'partial',exact_identity_status:exact?'confirmed':entry?'variant_pending':'unresolved',market_ready:exact,model_verified:!!entry,catalogue_core_verified:!!entry,catalogue_verified:exact,model_confidence:null,family_confidence:family?90:0,
  core_identity:{status:entry?'confirmed':'partial',origin:'catalogue_engine',model,fields,pending_fields:entry?[]:pending},identity_keys:{subject:{value:subject},number:{value:cardNumber},date:{value:date}},
  source_confirmed_catalog_number:entry?.number||null,source_confirmed_year:entry?.year||null,physical_serial:variation.physical_serial||null,serial_number:variation.physical_serial?.value||null,
  variant_needs_verification:!exact,variant_check:variation.status,variant_resolution:variation,printing_check:l.domain==='pokemon'&&entry?{complete:variation.status==='confirmed',labels:variation.labels,missing:pending,stamp:variation.printingScope.firstEdition?(l.pick('stamp')?.value||(/1st Edition/.test(variation.labels.join(' '))?'present':'unclear')):'not_applicable',shadow:variation.printingScope.shadow?l.pick('shadow')?.value||'unclear':'not_applicable',contradiction:false}:undefined,
  assistance_state:exact?'confirmed':error|| (entry?'physical_detail_needed':'unidentified'),missing_information:pending.map(p=>({serial:'Numerazione dell’esemplare',variant:'Variante commerciale',finish:'Finitura',stamp:'Timbro First Edition',shadow:'Ombra dell’illustrazione',rarity_symbol:'Simbolo di rarità',catalogue:'Riscontro catalografico',collector_number:'Numero carta',subject:'Nome'})[p]||p),
  next_photo_request:exact?null:photoRequest(pending,l.domain),verification_summary:exact?'Carta e stampa identificate tramite letture e catalogo.':entry?'Carta identificata. Resta il dettaglio di stampa indicato.':'Letture conservate; identità catalografica da completare.',
  normalized_query:exact?[model,variation.labels.join(' '),k.language].filter(Boolean).join(' '):'',candidate_models:(entry?[]:groups.slice(0,4)).map(g=>({model:[g.entry.year,g.entry.family,g.entry.number,g.entry.subject].join(' '),score:g.score})),catalogue_data:fields,identification_sources:unique(groups.flatMap(g=>g.entries.map(e=>e.source.url))).map(url=>({url,title:groups.flatMap(g=>g.entries).find(e=>e.source.url===url)?.source.title||url})),evidence:fields.map(f=>f.field+': '+f.value),object_unit:base.object_unit||'single',pokemon_printing:base.pokemon_printing,
  card_identity:{autograph:l.pick('autograph')?.value||'unclear',patch:l.pick('patch')?.value||'unclear',manufacturer:entry?.brand||base.brand||null,subject,set:family,number:cardNumber,date:date||null,language:k.language,variant:variation.labels.join(' · ')||null,rarity:entry?.rarity||l.pick('rarity_text')?.value||null,rarity_symbol:l.pick('rarity_symbol')?.value||'unclear',serial:variation.physical_serial||null}}
 l.record('reduce',{core:result.core_identity.status,exact:result.exact_identity_status,groups:groups.length,selected:top?.id||null,pending});return result;
}
function recoveryRequests(l,result){
 const pending=new Set(result?.variant_resolution?.pending||[]),requests=[];
 if(['sealed','generic'].includes(l.domain))return [];
 if(result.core_identity?.status!=='confirmed'&&!l.pick('collector_number'))pending.add('collector_number');
 if(l.values('serial').length&&!l.pick('serial'))pending.add('serial');
 for(const field of ['serial','collector_number','stamp','shadow','rarity_symbol','finish','variant'])if(pending.has(field)){
  const values=l.values(field).filter(a=>a.region),best=values.find(a=>a.rotation&&serial(a.value))||values.find(a=>numberParts(a.value))||values[0];
  // No blanket hunt for any small number: only a typed observation or fraction can request a serial read.
  if(field==='serial'&&!values.some(a=>serial(a.value)||a.source==='vision'))continue;
  const key='detail:'+field+':'+(best?JSON.stringify(best.region):'front');if(l.attempts.has(key))continue;
  requests.push({key,field,region:best?.region||null,image_index:best?.image_index||1,rotation:best?.rotation||0,readings:values.map(a=>({id:a.id,text:a.raw,value:a.value,source:a.source,rotation:a.rotation||0})).slice(0,8)});
 }
 return requests.slice(0,3);
}
function applyDetails(l,details,requests){for(const d of list(details)){
 const req=requests.find(r=>r.field===d.field&&(!d.image_index||r.image_index===d.image_index));if(!req||d.certainty!=='clear')continue;
 const value=d.text??d.value;if(d.field==='serial'&&!serial(value)||d.field==='collector_number'&&!numberParts(value))continue;
 if(['stamp','shadow','rarity_symbol'].includes(d.field)&&!['present','absent'].includes(value))continue;
 const previous=l.evidence(d.field);if(previous.some(a=>!(d.field==='collector_number'?numbersMatch(a.value,value):same(a.value,value)))){l.record('conflicting_detail',{field:d.field,value,previous:previous.map(a=>a.id)});continue;}
 l.add(d.field,value,{source:'focused_vision',certainty:'clear',image_index:req.image_index,region:req.region,raw:value,request_key:req.key});
 }return l;}
const api={Ledger,ingestVision,ingestOcr,keyValues,profile,number,numberParts,numbersMatch,serial,language,season,finish,norm,same,subjectMatch,tokens,COLOR_WORDS,PATTERNS,query,domains,sourceTrusted,evaluate,candidateGroups,printingScope,variantState,photoRequest,reduce,recoveryRequests,applyDetails};
if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckCatalogueEngine=api;
})(typeof window==='undefined'?globalThis:window);
