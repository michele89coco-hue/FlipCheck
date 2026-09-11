/* Ximilar recognition price statistics. Graded aggregates are never a slab quote. */
(function(root){'use strict';
const E=typeof module!=='undefined'&&module.exports?require('./catalogue-engine'):root.FlipCheckCatalogueEngine;
const num=v=>typeof v==='number'&&Number.isFinite(v)&&v>=0?v:null;
const text=v=>typeof v==='string'?v.trim().slice(0,180):'';
const date=v=>/^\d{4}-\d{2}-\d{2}$/.test(text(v))?v:null;
function extract(match){
 return (Array.isArray(match?.price_stats)?match.price_stats:[]).slice(0,30).filter(s=>['ungraded','graded'].includes(s.stats_type)).map(s=>{
  const v=s.value||{},currency=text(s.currency||v.currency||match.currency).toUpperCase();
  return {type:s.stats_type,interval:text(s.interval),currency:['USD','EUR','GBP','CAD','AUD','JPY','CHF'].includes(currency)?currency:null,median:num(v.median),q1:num(v.q1),q3:num(v.q3),volume:Number.isInteger(v.volume)&&v.volume>0?v.volume:null,latest_date:date(v.latest_date),oldest_date:date(v.oldest_date)};
 });
}
function model(identity,base,entries){
 const c=identity?.card_identity||{},fields=identity?.core_identity?.fields||[],read=k=>fields.find(f=>f.field===k)?.value||'',slab=!!(identity?.grading||identity?.slab_verification||base?.slab_reading?.present||base?.slab_detected);
 const company=text(identity?.grading?.company||identity?.grader),grade=text(String(identity?.grading?.grade||identity?.grade||''));
 const result={slab,company,grade,label:slab?[company||'Ente da verificare',grade?'Voto '+grade:'Voto da verificare'].join(' · '):'RAW · Carta non gradata',state:'no_data',stat:null,rows:[5,6,7,8,9,10].map(g=>({grader:'PSA',grade:g,value:null})),source:null};
 if(identity?.core_identity?.status!=='confirmed'){result.state='identity_pending';return result;}
 const subject=c.original_subject||c.subject||read('subject'),number=c.number||read('catalog_number'),family=c.set||read('family')||identity.family,year=c.date||c.year||read('year');
 const entry=(entries||[]).find(e=>e.provider_rank===0&&e.source?.provider==='ximilar'&&E.subjectMatch(e.subject,subject)&&E.numbersMatch(e.number,number)&&E.familyKey(e.family)===E.familyKey(family)&&(!year||!e.year||E.sportsSeason215(year,e.year))&&(!c.subset||E.subsetKey(c.subset)===E.subsetKey(e.subset)));
 if(!entry){result.state='no_matching_prices';return result;}
 result.source=entry.source;result.state=slab?'grade_data_missing':'no_data';
 // This endpoint documents only generic graded/ungraded groups. Never extrapolate PSA 5–10.
 if(slab)return result;
 const stats=(entry.price_statistics||[]).filter(s=>s.type==='ungraded'&&s.median!==null&&s.median>0&&s.interval==='overall');
 if(stats.length!==1)return result;
 result.stat=stats[0];result.state=identity.market_ready?'indicative':'printing_pending';
 return result;
}
function html(m){
 const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const n=v=>new Intl.NumberFormat('it-IT',{maximumFractionDigits:2,minimumFractionDigits:2}).format(v),s=m.stat;
 const fmt=v=>s?.currency?new Intl.NumberFormat('it-IT',{style:'currency',currency:s.currency}).format(v):n(v);
 const amount=s?fmt(s.median):'Dati non disponibili';
 const span=s&&s.q1!==null&&s.q3!==null&&s.q1<=s.median&&s.median<=s.q3?`${fmt(s.q1)} – ${fmt(s.q3)}`:null;
 const note=m.slab?'Servono prezzi dello stesso ente e dello stesso voto. I dati generici delle carte gradate non valutano questa slab.':m.state==='identity_pending'?'Conferma l’identità per visualizzare prezzi pertinenti.':s?'Mediana delle rilevazioni raw. Condizioni, lingua ed edizione non sono distinte in questi dati: è un riferimento, non una stima della singola copia.':'La fonte non ha restituito prezzi raw per questa identità.';
 return `<section class="price235" aria-label="Valore della carta"><div class="price235-top"><span class="price235-kicker">${m.slab?'VALORE DELLA TUA SLAB':'VALORE INDICATIVO RAW'}</span><span class="price235-condition">${esc(m.label)}</span></div><div class="price235-amount ${s?'':'empty'}">${esc(amount)}</div>${s&&!s.currency?'<p class="price235-currency">Valuta non indicata dalla fonte · nessuna conversione applicata</p>':''}${span?`<div class="price235-range">Fascia centrale <strong>${esc(span)}</strong></div>`:''}${s?`<div class="price235-meta">${s.volume?esc(s.volume)+' rilevazioni':'Numero rilevazioni non disponibile'}${s.latest_date?' · Ultimo dato '+esc(s.latest_date):''}</div>`:''}${m.state==='printing_pending'?'<p class="price235-alert">Variante ancora da verificare: il prezzo non è confermato per questa stampa.</p>':''}<p class="price235-note">${esc(note)}</p><div class="price235-grading"><h3>${m.slab?'Confronto con altri voti':'Se la facessi gradare'}</h3><p>Confronto PSA · valori separati dal prezzo della tua carta</p><table><thead><tr><th scope="col">Grado</th><th scope="col">Valore indicativo</th></tr></thead><tbody>${m.rows.map(r=>`<tr><th scope="row">PSA ${r.grade}</th><td><span class="price235-missing">Non disponibile</span></td></tr>`).join('')}</tbody></table><p class="price235-foot">Ximilar non ha restituito prezzi distinti per questi voti. Nessuna previsione del voto ottenibile.</p></div>${m.source?'<div class="price235-source">Fonte: Ximilar · statistiche della carta riconosciuta</div>':''}</section>`;
}
const api={extract,model,html};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckPriceSummary=api;
})(typeof window==='undefined'?globalThis:window);
