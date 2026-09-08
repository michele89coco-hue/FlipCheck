/* Provider adapters return attributed catalogue records, never final decisions. */
(function(root){
'use strict';
const E=root.FlipCheckCatalogueEngine||(typeof require==='function'?require('./catalogue-engine.js'):null),list=x=>Array.isArray(x)?x:[],str=x=>String(x??'').trim(),uniq=x=>[...new Set(x.filter(Boolean))];
const providers={pokemon:{directories:['https://api.tcgdex.net/v2/'],domains:['tcgdex.net','pokemon.com','bulbapedia.bulbagarden.net','wiki.pokemoncentral.it','psacard.com']},topps:{directories:['https://www.topps.com/pages/checklists'],domains:['topps.com','ripped.topps.com']},panini:{directories:['https://www.paniniamerica.net/checklist.html'],domains:['paniniamerica.net']},upperdeck:{directories:['https://upperdeck.com/checklists/'],domains:['upperdeck.com']},leaf:{directories:['https://www.leaftradingcards.com/catalog'],domains:['leaftradingcards.com']},onepiece:{directories:['https://en.onepiece-cardgame.com/cardlist/'],domains:['en.onepiece-cardgame.com','www.onepiece-cardgame.com']}};
function source(page){return {url:page.url,title:page.title||page.url,provider:page.provider||new URL(page.url).hostname,retrieved_at:page.retrieved_at||null};}
function requestPlan(l){
 const k=E.keyValues(l);if(l.domain==='pokemon'&&k.subject){const langs=uniq([k.language||'en','en']),params=new URLSearchParams({name:k.subject,'pagination:page':'1','pagination:itemsPerPage':'100'});return langs.map((lang,index)=>({action:'catalogue',url:'https://api.tcgdex.net/v2/'+lang+'/cards?'+params,provider:'tcgdex',language:lang,purpose:index?'cross_language_lookup':'name_lookup',fallback:index>0,subject:k.subject,numbers:k.numbers}));}
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
 return {id:card.id,subject:card.name,number,local_number:local,family:card.set.name,set_id:card.set.id,year:(set?.releaseDate||card.set.releaseDate||'').slice(0,4),language:lang,brand:'Pokémon',hp:card.hp,attacks:list(card.attacks).map(a=>a.name),rarity:card.rarity,variants,printing_options,image_url:card.image?card.image+'/high.png':null,source:{url,provider:'tcgdex',title:card.name+' · '+card.set.name},grounded:true,entry_quote:JSON.stringify({id:card.id,name:card.name,localId:card.localId,set:card.set,variants:card.variants}),coverage:'documented_variants'};
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
 const out=[];for(const line of lines){
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
 const text=pageText(p),lines=text.split('\n'),k=E.keyValues(l),out=[],src=source(p),family=titleFamily(p,l);
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
function singleEntry(p,l){
 const text=pageText(p),k=E.keyValues(l),src=source(p),out=[];
 if(/pokedex|pok[eé]dex/i.test(p.url+' '+p.title))return out;
 if(!k.subject||!E.norm(text).includes(E.norm(k.subject))&&!text.toLowerCase().includes(k.subject.split(' ').reverse().join(', ').toLowerCase()))return out;
 // Structured label blocks keep the number attached to its expansion; two printings
 // on the same page produce two records, never an invented alias between numbers.
 const blocks=[...text.matchAll(/(?:Espansione|Expansion)\s*\n\s*([^\n]+)[\s\S]{0,280}?\b(?:Numero della carta|Numero|Number)\s*\n\s*([A-Z]{0,8}\d+(?:\/[A-Z]{0,8}\d+)?)/g)];
 for(const m of blocks){const family=m[1].trim(),n=m[2],holo=/^H\d+/.test(n),attacks=l.evidence('text').concat(l.evidence('attack')).filter(a=>a.value.length>6&&E.norm(text).includes(E.norm(a.value))).map(a=>a.value);
  out.push({subject:k.subject,number:n,family,year:E.season(text.match(/(?:Release|Uscita|Published|Pubblicazione)[^\n]*\n?([^\n]+)/i)?.[1]||'')||'',brand:'Pokémon',language:k.language,hp:text.match(/\b(\d{2,3})\s*(?:PV|HP)\b/)?.[1]||text.match(/Punti Salute\s+(\d{2,3})/)?.[1],attacks,variants:holo?[{name:'Holo',finish:'holo'}]:[],source:src,grounded:true,entry_quote:m[0]+'\n'+attacks.join('\n')});}
 const enBlocks=[...text.matchAll(/(?:English expansion|(?:^|\s)Expansion)\s+([A-Za-z0-9 '&é-]+?)\s+(?:Rarity\s+)?English card no\.\s*([A-Z]*\d+\/[A-Z]*\d+)/g)];
 for(const m of enBlocks){const family=m[1].trim();if(family.length>65)continue;out.push({subject:k.subject,number:m[2],family,year:'',brand:'Pokémon',language:'en',variants:[],source:src,grounded:true,entry_quote:m[0]});}
 if(out.length)return out;
 const number=text.match(/(?:Card Number|Numero carta|Collector Number)\s*:?\s*\n?\s*#?([A-Z]{0,8}-?\d+(?:\/[A-Z]*\d+)?)/i)?.[1];
 const subject=text.match(/(?:Player|Subject|Name)\s*:?\s*\n?\s*([^\n]+)/i)?.[1];
 const family=text.match(/(?:^|\n)Set\s*:?\s*\n?\s*([^\n]+)/i)?.[1];
 if(number&&subject&&family&&E.subjectMatch(k.subject,subject)){
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
  out.push({subject:cells[si],number:E.number(cells[ni]),family:fi>=0?cleanFamily(cells[fi]):titleFamily(p,l),year:yi>=0?E.season(cells[yi]):E.season(p.title),subset:sub>=0?cells[sub]:'Base',brand:l.base.brand||'',language:k.language,variants:[],source:source(p),grounded:true,entry_quote:row.text||cells.join(' | ')});
 }return out;
}
function objectEntries(p,l){
 if(!['sealed','generic'].includes(l.domain))return [];
 const text=pageText(p),k=E.keyValues(l),family=titleFamily(p,l),parts=k.products.length?k.products:l.evidence('text').filter(a=>/chrome|update series|topps|select|upper deck|leaf|panini/i.test(a.value)).map(a=>a.value);
 if(!parts.length||!parts.every(v=>E.norm(text).includes(E.norm(v))))return [];
 return [{subject:p.title||family,family,number:'',year:E.season(p.title),brand:l.base.brand,source:source(p),grounded:true,entry_quote:text}];
}
function usablePage200(p){const t=pageText(p);return !!p.url&&t.length>0&&!/^Your Trusted Marketplace for Collectible Trading Card Games - TCGplayer\s*$/i.test(t);}
function records(pages,l){const out=[];for(const p of pages){if(/pokedex|pok[eé]dex/i.test(p.url+' '+p.title))continue;if(!p.url||!(E.sourceTrusted(p.url,l.domain)||l.domain==='generic'&&/^https:\/\//.test(p.url)))continue;for(const e of [...bandaiEntries(p,l),...tableEntries(p,l),...cardRows(p,l),...singleEntry(p,l),...objectEntries(p,l)])if(e.family)out.push(e);}return out;}
function groundedExtraction(reply,pages,l){
 const accepted=[],rejected=[];
 for(const e of list(reply?.entries)){
  const literal=v=>str(v).replace(/\s+/g,' '),q=literal(e.entry_quote),page=pages.find(p=>p.url===e.source_url&&literal(pageText(p)).includes(q)),text=page&&literal(pageText(page)),proof=e.proof||{},object=l&&['sealed','generic'].includes(l.domain),fields=object?['subject','family']:['subject','family','number'];
  let reason=!page?(pages.some(p=>p.url===e.source_url)?'entry_quote_not_found':'unknown_source'):!(E.sourceTrusted(page.url,l?.domain)||l?.domain==='generic'&&/^https:\/\//.test(page.url))?'untrusted_catalogue':!q||!text.includes(q)?'entry_quote_not_found':null;
  for(const field of fields)if(!reason){const quote=literal(proof[field]);if(!quote||!text.includes(quote)||!E.norm(quote).includes(E.norm(e[field])))reason='ungrounded_'+field;}
  if(!reason&&!object&&(!E.norm(q).includes(E.norm(e.subject))||!new RegExp('(?:^|[^A-Z0-9])'+str(e.number).replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'(?:$|[^A-Z0-9])','i').test(q)))reason='number_subject_not_in_same_entry';
  if(reason){rejected.push({subject:e.subject,number:e.number,reason});continue;}
  if(!reason&&!object&&!E.norm(page.title).includes(E.norm(e.family))&&!E.norm(q).includes(E.norm(e.family))&&!E.norm(text.slice(Math.max(0,text.indexOf(q)-600),text.indexOf(q))).includes(E.norm(e.family))){rejected.push({subject:e.subject,number:e.number,reason:'unlinked_family_heading'});continue;}
  const year=e.year&&proof.year&&text.includes(literal(proof.year))&&proof.year.includes(e.year)?e.year:'';
  const variants=list(e.variants).filter(v=>v.quote&&text.includes(literal(v.quote))&&E.norm(v.quote).includes(E.norm(v.name))&&(!v.print_run||new RegExp('(?:/|to|run\\s*:)\\s*'+v.print_run+'(?:\\D|$)','i').test(v.quote))&&list(v.colors).every(c=>E.tokens(v.quote,E.COLOR_WORDS).includes(c))&&list(v.patterns).every(c=>E.tokens(v.quote,E.PATTERNS).includes(c))).map(v=>({...v,source:source(page)}));
  accepted.push({...e,year,variants,source:source(page),grounded:true});
 }
 return {accepted,rejected};
}
function rankSources(pages,l){
 const k=E.keyValues(l);return pages.filter(p=>!/(?:pokedex|pok[eé]dex)/i.test(p.url+' '+p.title)).map(p=>{const title=E.norm(p.title+' '+p.url),text=E.norm(pageText(p));return {p,score:(k.subject&&title.includes(E.norm(k.subject))?40:0)+(k.subject&&text.includes(E.norm(k.subject))?15:0)+(k.products.some(v=>E.familyKey(cleanFamily(p.title))===E.familyKey(v))?35:0)+(k.numbers.some(n=>title.includes(E.norm(E.numberParts(n)?.local||n)))?30:0)+(/checklist|cardlist|cards|espansione|expansion/i.test(p.url+' '+p.title)?8:0)+(k.numbers.some(n=>text.includes(E.norm(n)))?5:0)};}).sort((a,b)=>b.score-a.score).map(x=>x.p);
}
function directoryLinks(page,l){const k=E.keyValues(l),terms=uniq([k.year,...k.products,...str(l.base.family).split(' ').filter(t=>t.length>3)]).map(E.norm);return list(page.catalogue_links).map(a=>({...a,score:terms.reduce((n,t)=>n+Number(E.norm(a.title+' '+a.url).includes(t)),0)})).filter(a=>a.score>=Math.min(2,terms.length)&&a.score>0).sort((a,b)=>b.score-a.score).slice(0,2);}
const api={usablePage200,bandaiEntries,rankSources,providers,requestPlan,tcgdexBriefs,tcgdexCard,pageText,cleanFamily,records,groundedExtraction,directoryLinks,itemAttributes,variantsFromLines};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckCatalogueSources=api;
})(typeof window==='undefined'?globalThis:window);
