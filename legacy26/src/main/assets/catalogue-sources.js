/* Provider adapters return attributed catalogue records, never final decisions. */
(function(root){
'use strict';
const E=root.FlipCheckCatalogueEngine||(typeof require==='function'?require('./catalogue-engine.js'):null),list=x=>Array.isArray(x)?x:[],str=x=>String(x??'').trim(),uniq=x=>[...new Set(x.filter(Boolean))];
const providers={pokemon:{directories:['https://api.tcgdex.net/v2/'],domains:['tcgdex.net','pokemon.com','bulbapedia.bulbagarden.net','wiki.pokemoncentral.it','psacard.com']},topps:{directories:['https://www.topps.com/pages/checklists'],domains:['topps.com','ripped.topps.com']},panini:{directories:['https://www.paniniamerica.net/checklist.html'],domains:['paniniamerica.net']},upperdeck:{directories:['https://upperdeck.com/checklists/'],domains:['upperdeck.com']},leaf:{directories:['https://www.leaftradingcards.com/catalog'],domains:['leaftradingcards.com']},onepiece:{directories:['https://en.onepiece-cardgame.com/cardlist/'],domains:['en.onepiece-cardgame.com','www.onepiece-cardgame.com']}};
function source(page){return {url:page.url,title:page.title||page.url,provider:page.provider||new URL(page.url).hostname,retrieved_at:page.retrieved_at||null};}
function pokemonProductEntries204(page,l){
 if(l.domain!=='pokemon')return [];
 let url;try{url=new URL(page.url);}catch(_){return [];}
 if(!/(^|\.)pricecharting\.com$/.test(url.hostname)||!url.pathname.startsWith('/game/pokemon-'))return [];
 const title=str(page.title),m=title.match(/^(.+?)\s+#([A-Z0-9/-]+)\s+Prices\s*\|\s*Pokemon\s+([^|]+)\s*\|/i);if(!m)return [];
 const subject=m[1].replace(/\s*\[[^\]]+\]\s*/g,' ').trim(),printedLanguage=m[3].match(/^(Japanese|Chinese|Italian|German|French|Spanish|Korean)\s+/i),language=printedLanguage?E.language(printedLanguage[1]):'en',family=m[3].replace(/^(Japanese|Chinese|Italian|German|French|Spanish|Korean)\s+/i,'').trim();
 const number=m[2],keys=E.pokemonKeys204(l),names=[keys.subject,...E.pokemonAliases204(l)];
 if(!E.numberParts(number)||!E.pokemonNumbers203(l).some(n=>E.numbersMatch(n,number)))return [];
 if(!names.some(n=>E.subjectMatch(n,subject))&&!/[\u3040-\u9fff]/.test(keys.subject))return [];
 if(keys.language&&!E.printingLanguageCompatible201(keys.language,language))return [];
 const fields=list(page.structured_fields),year=E.season(fields.find(f=>/^release date:?$/i.test(f.label))?.value)||'',variant=title.match(/\[([^\]]+)\]/)?.[1]||'',finish=E.finish(variant);
 const e={subject,number,family,language,year,brand:'Pokémon',identifier_type:/cd promo|unnumbered/i.test(family)?'pokedex':'collector',variants:finish?[{name:variant,finish,visual_required:true}]:[],source:source(page),grounded:true,entry_quote:title,requires_image_confirmation:true,source_tier:'secondary_product'};
 const images=pokemonReferenceImages203(page,e,l);if(images.length)e.image_url=images[images.length-1];
 return [e];
}
function pokemonLeadEntries204(reply,pages,l){
 if(l.domain!=='pokemon')return [];
 const keys=E.pokemonKeys204(l),out=[];
 for(const e of list(reply?.entries)){
  const page=pages.find(p=>p.url===e.source_url&&str(e.entry_quote)&&E.norm(pageText(p)).includes(E.norm(e.entry_quote)));if(!page||E.sourceTrusted(page.url,'pokemon'))continue;
  const text=E.norm(pageText(page));
  if(!e.subject||!e.family||!E.numberParts(e.number)||!text.includes(E.norm(e.subject))||!text.includes(E.norm(e.family))||!E.norm(e.entry_quote).includes(E.norm(e.number)))continue;
  if(!E.pokemonNumbers203(l).some(n=>E.numbersMatch(n,e.number))||keys.language&&E.language(e.language)&&!E.printingLanguageCompatible201(keys.language,e.language))continue;
  const year=e.year&&text.includes(E.norm(e.year))?e.year:'';
  out.push({...e,year,identifier_type:keys.number_role==='pokedex_number'?'pokedex':'collector',variants:[],source:source(page),grounded:true,requires_image_confirmation:true,source_tier:'discovery_lead',image_url:null});
 }
 return out;
}
const pokemonSourcePolicy203={
 structured_catalogue:['tcgdex.net'],
 official_catalogue:['pokemon.com','pokemon-card.com'],
 reference_images:['psacard.com','pricecharting.com'],
 supplementary_catalogue:['bulbapedia.bulbagarden.net','wiki.pokemoncentral.it','tcgcollector.com','serebii.net']
};
function pokemonReferenceImages203(page,entry,l){
 if(l.domain!=='pokemon'||!page||page.url!==entry.source?.url)return [];
 const names=[entry.subject,...list(entry.aliases)].map(E.norm).filter(Boolean),num=E.numberParts(entry.number);
 if(!num)return [];
 const hasNumber=value=>{const parts=str(value).match(/(?:No\.?\s*|#\s*)?[A-Z]{0,8}\d+(?:\/[A-Z]{0,8}\d+)?/gi)||[];return parts.some(n=>E.numbersMatch(n,entry.number));};
 const certificate=list(page.structured_fields).some(f=>f.label==='Subject'&&names.includes(E.norm(f.value)))&&list(page.structured_fields).some(f=>f.label==='Card Number'&&E.numbersMatch(f.value,entry.number));
 return list(page.image_details).filter(i=>{
  const caption=str([i.caption,i.alt,i.title].filter(Boolean).join(' ')),text=E.norm(caption);
  if(/\b(?:logo|icon|avatar|banner|back|retro)\b/i.test(caption))return false;
  if(certificate&&i.caption==='Cert image 1')return true;
  // Exact product context plus a named/card-numbered image. Never use a logo,
  // related product or arbitrary first picture on a price-history page.
  return names.some(n=>text.includes(n))&&hasNumber(caption)&&text.includes(E.norm(entry.family));
 }).map(i=>i.image_url||i.url).filter(Boolean);
}
function requestPlan(l){
 const k=E.keyValues(l);if(l.domain==='pokemon')k.numbers=E.pokemonNumbers203(l);if(l.domain==='pokemon'&&k.subject&&!k.language.startsWith('zh')){const langs=uniq([k.language||'en',...(/[\u3040-\u9fff]/.test(k.subject)?[]:['en'])]),params=new URLSearchParams({name:k.subject,'pagination:page':'1','pagination:itemsPerPage':'100'});return langs.map((lang,index)=>({action:'catalogue',url:'https://api.tcgdex.net/v2/'+lang+'/cards?'+(index&&E.pokemonAliases204(l).length?new URLSearchParams({name:E.pokemonAliases204(l)[0],'pagination:page':'1','pagination:itemsPerPage':'100'}):params),provider:'tcgdex',language:lang,purpose:index?'cross_language_lookup':'name_lookup',fallback:index>0,subject:k.subject,numbers:k.numbers}));}
 if(l.domain==='onepiece')return ['https://en.onepiece-cardgame.com/cardlist/','https://asia-en.onepiece-cardgame.com/cardlist/'].map((base,index)=>({action:'page',url:base+'?'+new URLSearchParams({freewords:k.numbers[0]||k.subject||''}),terms:[...k.numbers,k.subject,...E.distinctiveMarks(l)].filter(Boolean),provider:'bandai',purpose:index?'regional_card_list':'card_code_lookup',fallback:index>0}));
 const brand=E.norm([l.base.brand,...k.brands,...k.products].join(' ')),p=/topps|bowman/.test(brand)?'topps':/panini/.test(brand)?'panini':/upper deck|fleer|skybox/.test(brand)?'upperdeck':/leaf/.test(brand)?'leaf':null;
 return p?[{action:'page',url:providers[p].directories[0],terms:[k.year,...k.products,l.base.family].filter(Boolean),provider:p,purpose:'checklist_directory'}]:[];
}
function tcgdexBriefs(body,keys){return list(body).filter(c=>c.id&&c.name&&E.subjectMatch(keys.subject,c.name)).sort((a,b)=>Number(keys.numbers.some(n=>E.numbersMatch(n,b.localId)))-Number(keys.numbers.some(n=>E.numbersMatch(n,a.localId))));}
function tcgdexCard(card,set,lang,url){
 if(!card?.id||!card.set?.id||!card.name||!card.localId)return null;
 const count=card.set.cardCount?.official,local=str(card.localId),number=/^H\d+$/i.test(local)?local:count?local+'/'+count:local,variants=[];
 for(const [key,name,finish] of [['normal','Non holo','normal'],['holo','Holo','holo'],['reverse','Reverse holo','reverse']])if(card.variants?.[key]===true)variants.push({name,finish});
 const printing_options=[];if(card.variants?.firstEdition===true)printing_options.push('first_edition');
 return {id:card.id,subject:card.name,number,local_number:local,family:card.set.name,set_id:card.set.id,year:(set?.releaseDate||card.set.releaseDate||'').slice(0,4),language:lang,brand:'Pokémon',hp:card.hp,illustrator:card.illustrator||'',attacks:list(card.attacks).map(a=>a.name),attacks_language:lang,rarity:card.rarity,variants,printing_options,image_url:card.image?card.image+'/high.png':null,source:{url,provider:'tcgdex',title:card.name+' · '+card.set.name},grounded:true,entry_quote:JSON.stringify({id:card.id,name:card.name,localId:card.localId,set:card.set,variants:card.variants}),coverage:'documented_variants'};
}
function pageText(p){return [p.catalogue_text,p.source_text,p.text,p.snippet].map(str).sort((a,b)=>b.length-a.length)[0]||'';}
function cleanFamily(value){return str(value).replace(/^\d{4}(?:[-/]\d{2,4})?\s*/,'').replace(/^(?:Pok[eé]mon\s+|Panini\s+)/i,'').replace(/\s+(?:Soccer|Basketball|Baseball|Football|Hockey)?\s*(?:Checklist|Cards?\b|Sports Cards|Collector Guide|Checklists|Set Lists|Price Guide|Online Values).*/i,'').replace(/\s*[,|].*$/,'').replace(/\s+(?:Basketball|Baseball|Soccer|Football|Hockey)$/i,'').trim();}
function titleFamily(p,l){
 const text=pageText(p),k=E.keyValues(l),base=l.base.family||'',candidate=cleanFamily(p.title||text.split('\n')[0]);
 if(base&&E.norm(text).includes(E.norm(base)))return cleanFamily(base);
 const setLabel=text.match(/(?:^|\n)(?:Set|Espansione|Expansion)(?:\s*:\s*|\s*\n)([^\n]+)/i)?.[1];
 return setLabel?cleanFamily(setLabel):candidate;
}
function itemAttributes(name){const colors=E.tokens(name,E.COLOR_WORDS),patterns=E.tokens(name,E.PATTERNS).filter(p=>!['geometric','dots','squares'].includes(p));return {colors,patterns};}
function variantsFromLines(lines,src){
 const out=[];const expanded=lines.flatMap(line=>/^\s*Parallels\s*:/i.test(line)?line.replace(/^\s*Parallels\s*:\s*/i,'').split(';').map(t=>t.trim().replace(/\.$/,'')): [line]);for(const line of expanded){
  const quote=line.trim().replace(/^[•*+\-]\s+/,''),text=quote.replace(/\s+1\/1$/,' /1'),m=text.match(/^([A-Za-z][A-Za-z &'-]{1,65}?)\s*(?:[-–—:]?\s*(?:\/|#'?d\s+to|numbered\s+to))\s*(\d{1,6})(\s+or less)?\s*$/i);
  if(m){out.push({name:m[1].trim(),print_run:+m[2],max_print_run:!!m[3],...itemAttributes(m[1]),source:src,quote});continue;}
  if(text.length<65&&E.tokens(text,E.COLOR_WORDS).length&&/^[a-z &'-]+$/i.test(text)&&!/^base |^look |^the |^each |^all |cards|box|year|set/i.test(text))out.push({name:text,unnumbered:true,...itemAttributes(text),source:src,quote});
 }
 // Some older checklists describe a numbered parallel in prose, after base rows.
 const normalized=lines.map((line,i)=>/^[A-Za-z][A-Za-z &'-]{2,65}$/.test(line)&&/^(?:\d+ cards?\.?|\/\d+)/i.test(lines.slice(i+1).find(t=>t.trim())||'')?'### '+line:line);
 const joined=normalized.join('\n');for(const m of joined.matchAll(/(?:^|\n)#{1,6}[ \t]+([A-Za-z][^\n]{1,80})\n([^#]{0,550}?)(?:all[^.\n]{0,50}(?:are|is) numbered to)\s+(\d{1,6})\b/gi)){if(/checklist|inserts|base set/i.test(m[1]))continue;out.push({name:m[1].trim(),print_run:+m[3],source:src,quote:m[0].trim(),paragraph_parallel:true});}
 return out;
}
function cardRows(p,l){
 const text=pageText(p),rawLines=text.split('\n'),lines=rawLines.map((t,i)=>/^[A-Z][A-Za-z &'’()/-]{2,90}$/.test(t.trim())&&/^\d+ cards?\b/.test(rawLines.slice(i+1).find(v=>v.trim())?.trim()||'')&&/^Parallels\b/.test(rawLines.slice(i+1).filter(v=>v.trim())[1]?.trim()||'')?t.trim()+' Checklist':t),k=E.keyValues(l),out=[],src=source(p),family=titleFamily(p,l);
 let section='Base',sectionKnown=false,sectionLines=[],sectionEntries=[];
 const baseRanges=[...text.matchAll(/\b(Terrace|Mezzanine|Field Level)\s*:\s*#?s?\s*(\d+)\s*[-–]\s*(\d+)/gi)];
 const flush=()=>{const variants=/^(?:Unspecified checklist section|Full Checklist|Team Checklist|Complete Checklist)$/i.test(section)?[]:variantsFromLines(sectionLines,src);for(const e of sectionEntries){e.variants=variants.map(v=>({...v}));if(E.subsetKey(e.subset)==='base'&&variants.length&&variants.every(v=>v.print_run)&&variants.some(v=>v.paragraph_parallel))e.variants.unshift({name:'Base',base_printing:true,source:src,quote:sectionLines.find(t=>/^(?:#{1,6}\s*)?Base(?: Set)?(?: Checklist)?$/i.test(t))||'Base'});}sectionLines=[];sectionEntries=[];};
 for(const line of lines){
  const t=line.trim(),heading=t.replace(/^#{1,6}\s+/,'');
  if(/^(?:Checklist Top|Inserts|Autographs|Related articles|Tags.*)$/i.test(t)){flush();section='Unspecified checklist section';sectionKnown=false;continue;}
  const isHeading=t.length<160&&!/^#?\d{1,4}\s+/.test(t)&&(/^(?:Base(?: Set)?|Base\s*[-–]\s*(?:Terrace|Mezzanine|Field Level))$/i.test(t)||/^(?:#{1,6}\s+).*(?:Checklist|Base & Parallel Details)/i.test(t)||/^(?:Base(?: Set)?|[A-Z0-9][A-Za-z0-9 &’'!()/-]{2,140}) Checklist$/.test(t));
  if(isHeading){
   let next=heading;
   if(/autograph|signature|insert|memorabilia|pairing|terrace|terrance|mezzanine|field level/i.test(heading))next=heading;
   else if(/base|Base & Parallel Details/i.test(heading))next='Base';
   else if(E.norm(heading).includes(E.norm(family)))next=E.familyKey(cleanFamily(heading))===E.familyKey(family)?'Base':'Team Checklist';
   if(next!==null){flush();section=next;sectionKnown=next!=='Team Checklist'&&!/^(?:full|team|complete) checklist$/i.test(heading); }
  }
  sectionLines.push(t);
  const row=t.match(/^#?([A-Z]{0,8}-?\d+[A-Z]?(?:\/[A-Z]*\d+)?)\s+(.*)$/i);
  if(!row||!k.subject)continue;
  const rowName=row[2].split(/\s[-–—|]\s|,\s/)[0].trim(),name=rowName.replace(/\s+(?:RC|Rookie Card)$/i,'');if(!E.subjectMatch(name,k.subject))continue;
  const range=(!sectionKnown||section==='Base')&&baseRanges.find(m=>+row[1]>=+m[2]&&+row[1]<=+m[3]),rowSection=range?range[1]:section;
  const e={subject:name,number:row[1],family,year:E.season(p.title||text),brand:l.base.brand||'',language:k.language,subset:/terrace|terrance/i.test(rowSection)?'Terrace':/mezzanine/i.test(rowSection)?'Mezzanine':/field level/i.test(rowSection)?'Field Level':rowSection,subset_known:!!range||sectionKnown,variants:[],source:src,grounded:true,entry_quote:t,coverage:'documented_variants'};
  out.push(e);sectionEntries.push(e);
 }
 flush();return out;
}
function registryEntries201(p,l){
 if(!/(^|\.)psacard\.com$/.test(new URL(p.url).hostname))return [];
 const text=pageText(p),fields=list(p.structured_fields),read=label=>str(fields.find(f=>f.label.toLowerCase()===label.toLowerCase())?.value)||str(text.match(new RegExp('(?:^|\\n)'+label.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'[ \\t]*\\n[ \\t]*([^\\n]+)','i'))?.[1]);
 const subject=read('Subject'),family=read('Brand/Title'),number=read('Card Number'),year=E.season(read('Year')),variety=read('Variety/Pedigree');
 if(!subject||!family||!E.numberParts(number))return [];
 const lang=E.language((family+' '+variety).match(/(?:JAPANESE|GERMAN|ITALIAN|FRENCH|ENGLISH|CHINESE)/i)?.[0]||''),finish=E.finish(variety),e={subject,number,family:cleanFamily(family),year,brand:'Pokémon',language:lang,printing_language:lang,variants:finish?[{name:variety,finish,source:source(p),quote:variety}]:[],source:source(p),grounded:true,entry_quote:[year,family,'#'+number,subject,variety].join(' ')};
 if(/(?:CD PROMO|TRADE PLEASE|UNNUMBERED)/i.test(family+' '+variety)){e.identifier_type='pokedex';e.visual_required=true;}
 return [e];
}
function singleEntry(p,l){
 const text=pageText(p),k=E.keyValues(l),src=source(p),out=[],registry=registryEntries201(p,l);if(registry.length)return registry;
 if(/pokedex|pok[eé]dex/i.test(p.url+' '+p.title))return out;
 if(!k.subject||!E.norm(text).includes(E.norm(k.subject))&&!text.toLowerCase().includes(k.subject.split(' ').reverse().join(', ').toLowerCase()))return out;
 // Structured label blocks keep the number attached to its expansion; two printings
 // on the same page produce two records, never an invented alias between numbers.
 const blocks=[...text.matchAll(/(?:Espansione|Expansion)\s*\n\s*([^\n]+)[\s\S]{0,280}?\b(?:Numero della carta|Numero|Number)\s*\n\s*([A-Z]{0,8}\d+(?:\/[A-Z]{0,8}\d+)?)/g)];
 for(const m of blocks){const family=m[1].trim(),n=m[2],holo=/^H\d+/.test(n),attacks=l.evidence('text').concat(l.evidence('attack')).filter(a=>a.value.length>6&&E.norm(text).includes(E.norm(a.value))).map(a=>a.value);
  out.push({subject:k.subject,number:n,family,year:E.season(text.match(/(?:Release|Uscita|Published|Pubblicazione)[^\n]*\n?([^\n]+)/i)?.[1]||'')||'',brand:'Pokémon',language:k.language,hp:text.match(/\b(\d{2,3})\s*(?:PV|HP)\b/)?.[1]||text.match(/Punti Salute\s+(\d{2,3})/)?.[1],attacks,variants:holo?[{name:'Holo',finish:'holo'}]:[],source:src,grounded:true,entry_quote:m[0]+'\n'+attacks.join('\n')});}
 const enBlocks=[...text.matchAll(/(?:English expansion|(?:^|\s)Expansion)\s+([A-Za-z0-9 '&é-]+?)\s+(?:Rarity\s+)?English card no\.\s*([A-Z]*\d+\/[A-Z]*\d+)/g)];
 for(const m of enBlocks){const family=m[1].trim();if(family.length>65)continue;out.push({subject:k.subject,number:m[2],family,year:'',brand:'Pokémon',language:'en',printing_language:'en',illustrator:text.match(/Illus\.?\s+([^\n]+)/i)?.[1]||'',variants:[],source:src,grounded:true,entry_quote:m[0]});}
 const jpBlocks=[...text.matchAll(/Japanese expansion\s+([^\n]+?)\s+(?:Japanese rarity\s+)?Japanese card no\.\s*([A-Z]*\d+\/[A-Z]*\d+)/g)];for(const m of jpBlocks)out.push({subject:k.subject,number:m[2],family:m[1].trim(),year:'',brand:'Pokémon',language:'ja',printing_language:'ja',illustrator:text.match(/Illus\.?\s+([^\n]+)/i)?.[1]||'',variants:[],source:src,grounded:true,entry_quote:m[0]});
 if(out.length)return out;
 const number=text.match(/(?:Card Number|Numero carta|Collector Number)\s*:?\s*\n?\s*#?([A-Z]{0,8}-?\d+(?:\/[A-Z]*\d+)?)/i)?.[1];
 const subject=text.match(/(?:Player|Subject|Name)\s*:?\s*\n?\s*([^\n]+)/i)?.[1];
 const family=text.match(/(?:^|\n)Set(?:[ \t]*:\s*|[ \t]*\n[ \t]*)([^\n]+)/i)?.[1];
 if(number&&subject&&family&&!/^(?:registry|set registry)$/i.test(family)&&E.subjectMatch(k.subject,subject)){
  const run=text.match(/Print Run\s*:?\s*\n?\s*(\d+)/i)?.[1],attrs=itemAttributes(family),variant=family.match(/(?:Prizms\s+)(.+)$/i)?.[1]||'';
  out.push({subject:k.subject,number,family:cleanFamily(family.replace(/\s+Prizms\s+.+$/i,'')),year:E.season(family),brand:l.base.brand||'',language:k.language,variants:variant?[{name:variant,print_run:run?+run:undefined,...attrs,source:src}]:[],source:src,grounded:true,entry_quote:[family,subject,number].join('\n')});
 }
 return out;
}
function bandaiEntries(p,l){
 if(l.domain!=='onepiece'||!/(^|\.)onepiece-cardgame\.com$/.test(new URL(p.url).hostname))return [];
 const text=pageText(p),k=E.keyValues(l),out=[],starts=[...text.matchAll(/\b((?:OP|ST|EB|PRB)\d{2}-\d{3}|P-\d{3})\s*\|\s*([A-Z]+)\s*\|\s*(?:LEADER|CHARACTER|EVENT|STAGE)\s+/g)];
 for(let i=0;i<starts.length;i++){
  const m=starts[i],body=text.slice(m.index+m[0].length,starts[i+1]?.index||text.length),printedName=body.trim().split('\n')[0].trim(),name=printedName.replace(/\s*\(Parallel\)\s*$/i,''),family=body.match(/Card Set\(s\)\s+([^\n]+)/)?.[1]?.trim();
  if(!E.subjectMatch(name,k.subject)||!family)continue;
  const siblings=starts.filter(x=>x[1]===m[1]),sameEdition=siblings.every(x=>{const b=text.slice(x.index+x[0].length,starts[starts.indexOf(x)+1]?.index||text.length);return b.trim().split('\n')[0].trim()===printedName&&b.match(/Card Set\(s\)\s+([^\n]+)/)?.[1]?.trim()===family;});
  const images=sameEdition?list(p.image_details).filter(im=>{try{const u=new URL(im.url||im.image_url);return /(^|\.)onepiece-cardgame\.com$/.test(u.hostname)&&new RegExp('/'+m[1]+'(?:_p[0-9]+)?\\.(?:png|jpg|webp)$','i').test(u.pathname);}catch(_){return false;}}):[];
  const variantName=family+(/\(Parallel\)/i.test(printedName)?' · Parallel Artwork':''),image=starts.length===1?list(p.image_details).find(i=>E.norm([i.caption,i.alt,i.title,i.url,i.image_url].join(' ')).includes(E.norm(m[1]))):null;
  out.push({subject:name,number:m[1],family:m[1].startsWith('P-')?'Promotional Cards':m[1].split('-')[0],catalogue_set:family,year:'',language:'en',brand:'Bandai',rarity:m[2],variants:images.length?images.map(im=>({id:im.url||im.image_url,name:variantName,visual_required:true,image_url:im.url||im.image_url,source:source(p)})):[{name:variantName,visual_required:true,image_url:image?.url||image?.image_url||'',source:source(p)}],source:source(p),grounded:true,entry_quote:m[0]+body.slice(0,Math.min(body.length,1600))});
 }return out;
}
function tableEntries(p,l){
 const k=E.keyValues(l),out=[];if(!k.subject)return out;
 for(const row of list(p.catalogue_rows)){
  const headers=list(row.headers).map(E.norm),cells=list(row.cells),ni=headers.findIndex(h=>/^(?:card )?(?:no|number|num|#)$/.test(h)||/card number/.test(h)),si=headers.findIndex(h=>/player|subject|card name|^name$/.test(h));
  if(ni<0||si<0||!E.subjectMatch(cells[si],k.subject)||!E.numberParts(cells[ni]))continue;
  const fi=headers.findIndex(h=>/set|product/.test(h)),yi=headers.findIndex(h=>/year|season/.test(h)),sub=headers.findIndex(h=>/subset|insert/.test(h));
  out.push({subject:cells[si],number:E.number(cells[ni]),family:fi>=0?cleanFamily(cells[fi]):titleFamily(p,l),year:yi>=0?E.season(cells[yi]):E.season(p.title),subset:sub>=0?cells[sub]:'',subset_known:sub>=0,brand:l.base.brand||'',language:k.language,variants:[],source:source(p),grounded:true,entry_quote:row.text||cells.join(' | ')});
 }return out;
}
function objectEntries(p,l){
 if(!['sealed','generic'].includes(l.domain))return [];
 const text=pageText(p),k=E.keyValues(l),family=titleFamily(p,l),parts=k.products.length?k.products:l.evidence('text').filter(a=>/chrome|update series|topps|select|upper deck|leaf|panini/i.test(a.value)).map(a=>a.value);
 if(!parts.length||!parts.every(v=>E.productText201(text).includes(E.productText201(v))))return [];
 const headings=[...text.matchAll(/(?:^|\n)(?:#{1,4}\s*)?((?:19|20)\d{2}[-–]\d{2}[^\n]{4,110}\b(?:Hobby|Jumbo|Delight|Value|Mega|Blaster) Box)\s*\n/g)],sections=[];
 for(let i=0;i<headings.length;i++){const h=headings[i],body=text.slice(h.index+h[0].length,headings[i+1]?.index||text.length).split(/\n(?:#{1,4}\s*)?(?:Who should buy|Choosing|FAQs|Frequently|What is|How many)/i)[0],quote=h[1]+'\n'+body.slice(0,700);sections.push({subject:h[1],family:cleanFamily(h[1].replace(/\s+(?:Hobby|Jumbo|Delight|Value|Mega|Blaster) Box$/i,'')),number:'',year:E.season(h[1].replace('–','-')),brand:l.base.brand,source:source(p),grounded:true,entry_quote:quote,configuration_quote:body.slice(0,700),configuration_section:true});}
 return sections.length?sections:[{subject:p.title||family,family,number:'',year:E.season(p.title),brand:l.base.brand,source:source(p),grounded:true,entry_quote:text}];
}
function usablePage200(p){const t=pageText(p);return !!p.url&&t.length>0&&!/^Your Trusted Marketplace for Collectible Trading Card Games - TCGplayer\s*$/i.test(t);}
function records(pages,l){const out=[];for(const p of pages){if(/pokedex|pok[eé]dex/i.test(p.url+' '+p.title))continue;if(!p.url||!(E.sourceTrusted(p.url,l.domain)||l.domain==='generic'&&/^https:\/\//.test(p.url)))continue;for(const e of [...pokemonProductEntries204(p,l),...bandaiEntries(p,l),...tableEntries(p,l),...cardRows(p,l),...singleEntry(p,l),...objectEntries(p,l)])if(e.family)out.push(e);}return out;}
function groundedExtraction(reply,pages,l){
 const accepted=[],rejected=[];
 for(const original of list(reply?.entries)){
  const e={...original,source_url:E.sourceUrl201(original.source_url)};if(e.source_url!==original.source_url)e.reported_source_url=original.source_url;
  const literal=v=>str(v).replace(/\s+/g,' '),q=literal(e.entry_quote),page=pages.find(p=>p.url===e.source_url&&literal(pageText(p)).includes(q)),text=page&&literal(pageText(page)),proof=e.proof||{},object=l&&['sealed','generic'].includes(l.domain),fields=object?['subject','family']:['subject','family','number'];
  let reason=!page?(pages.some(p=>p.url===e.source_url)?'entry_quote_not_found':'unknown_source'):!(E.sourceTrusted(page.url,l?.domain)||l?.domain==='generic'&&/^https:\/\//.test(page.url))?'untrusted_catalogue':!q||!text.includes(q)?'entry_quote_not_found':null;
  // A composite model family is not a literal catalog heading. Retain the
  // reported value, then use the independently quoted heading when it is a prefix.
  if(!reason&&proof.family&&text.includes(literal(proof.family))&&E.norm(e.family).startsWith(E.norm(proof.family)+' ')){e.reported_family=e.family;e.family=literal(proof.family);}
  if(e.subset&&!text?.includes(literal(e.subset))){e.reported_subset=e.subset;e.subset='';e.subset_known=false;}
  for(const field of fields)if(!reason){const quote=literal(proof[field]);if(!quote||!text.includes(quote)||!E.norm(quote).includes(E.norm(e[field])))reason='ungrounded_'+field;}
  if(!reason&&!object&&(!E.norm(q).includes(E.norm(e.subject))||!new RegExp('(?:^|[^A-Z0-9])'+str(e.number).replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'(?:$|[^A-Z0-9])','i').test(q)))reason='number_subject_not_in_same_entry';
  if(reason){rejected.push({subject:e.subject,number:e.number,reason});continue;}
  if(!reason&&!object&&!E.norm(page.title).includes(E.norm(e.family))&&!E.norm(q).includes(E.norm(e.family))&&!E.norm(text.slice(Math.max(0,text.indexOf(q)-600),text.indexOf(q))).includes(E.norm(e.family))){rejected.push({subject:e.subject,number:e.number,reason:'unlinked_family_heading'});continue;}
  const year=e.year&&proof.year&&text.includes(literal(proof.year))&&proof.year.includes(e.year)?e.year:'';
  const variants=list(e.variants).filter(v=>v.quote&&text.includes(literal(v.quote))&&E.norm(v.quote).includes(E.norm(v.name))&&(!v.print_run||new RegExp('(?:/|to|run\\s*:)\\s*'+v.print_run+'(?:\\D|$)','i').test(v.quote))&&list(v.colors).every(c=>E.tokens(v.quote,E.COLOR_WORDS).includes(c))&&list(v.patterns).every(c=>E.tokens(v.quote,E.PATTERNS).includes(c))).map(v=>({...v,source:source(page)}));
  const aliases=[];const physical=E.keyValues(l).subject;if(physical&&physical!==e.subject&&E.norm(text).includes(E.norm(physical)))aliases.push(physical);
  const attacks=list(e.attacks).filter(a=>text.includes(literal(a)));
  const configuration_quote=object&&e.subset&&text.includes(literal(e.subset))?e.subset:'';
  const identifier_type=['pokedex','unnumbered'].includes(e.identifier_type)?e.identifier_type:/CD PROMO|UNNUMBERED/i.test(e.family)?'pokedex':'collector';
  const illustrator=e.illustrator&&text.includes(literal(e.illustrator))?e.illustrator:'';
  accepted.push({...e,year,variants,aliases,attacks,illustrator,identifier_type,configuration_quote,source:source(page),grounded:true});
 }
 return {accepted,rejected};
}
function relevantSource230(p,l){
 if(l.base.object_unit!=='panel')return true;
 const text=' '+E.norm([p.title,p.url,pageText(p)].join(' '))+' ';
 return l.evidence('subject').some(a=>{const words=uniq(E.norm(a.value).split(' ').filter(w=>w.length>3));return words.filter(w=>text.includes(' '+w+' ')).length>=2||[...String(a.value).matchAll(/[\"“]([^\"”]+)[\"”]/g)].some(m=>text.includes(' '+E.norm(m[1])+' '));});
}
function rankSources(pages,l){
 const k=E.keyValues(l);if(l.domain==='pokemon')k.numbers=E.pokemonNumbers203(l);return pages.filter(p=>relevantSource230(p,l)).filter(p=>!/(?:pokedex|pok[eé]dex)/i.test(p.url+' '+p.title)).map(p=>{const title=E.norm(p.title+' '+p.url),text=E.norm(pageText(p));return {p,score:(k.subject&&title.includes(E.norm(k.subject))?40:0)+(k.subject&&text.includes(E.norm(k.subject))?15:0)+(k.products.some(v=>E.familyKey(cleanFamily(p.title))===E.familyKey(v))?35:0)+(k.numbers.some(n=>title.includes(E.norm(E.numberParts(n)?.local||n)))?30:0)+(/checklist|cardlist|cards|espansione|expansion/i.test(p.url+' '+p.title)?8:0)+(k.numbers.some(n=>text.includes(E.norm(n)))?5:0)};}).sort((a,b)=>b.score-a.score).map(x=>x.p);
}
function directoryLinks(page,l){const k=E.keyValues(l),terms=uniq([k.year,...k.products,...str(l.base.family).split(' ').filter(t=>t.length>3)]).map(E.norm);return list(page.catalogue_links).map(a=>({...a,score:terms.reduce((n,t)=>n+Number(E.norm(a.title+' '+a.url).includes(t)),0)})).filter(a=>a.score>=Math.min(2,terms.length)&&a.score>0).sort((a,b)=>b.score-a.score).slice(0,2);}
const api={relevantSource230,pokemonProductEntries204,pokemonLeadEntries204,pokemonSourcePolicy203,pokemonReferenceImages203,registryEntries201,usablePage200,bandaiEntries,rankSources,providers,requestPlan,tcgdexBriefs,tcgdexCard,pageText,cleanFamily,records,groundedExtraction,directoryLinks,itemAttributes,variantsFromLines};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckCatalogueSources=api;
})(typeof window==='undefined'?globalThis:window);
