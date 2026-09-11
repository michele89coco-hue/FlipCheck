'use strict';
function cardMarketEligible236(value){return !!value?.card_identity&&(value.pokemon_printing?.is_pokemon||/sport|football|basketball|baseball|soccer|hockey|calcio|basket|pokemon|pokémon/i.test(value.category||''));}
function paintCardMarket236(value){
 if(!cardMarketEligible236(value))return;
 const old=$('priceSummary235');if(old)old.remove();
 let panel=$('cardMarket236');if(!panel){panel=document.createElement('div');panel.id='cardMarket236';($('identityHeader229')||$('identTitle')).after(panel);}
 const state=value.card_market||{state:'not_requested',target:FlipCheckCardMarket.target(value),sales:[],grade_rows:[]};panel.innerHTML=FlipCheckCardMarket.html(state);
 $('marketBtn').textContent=value.card_market?'AGGIORNA VENDITE':'CERCA VENDITE COMPARABILI';
}
(function(){const previous=renderIdent;renderIdent=function(value){previous(value);$('cardMarket236')?.remove();paintCardMarket236(value);};})();
async function runCardMarket236(){
 if(!ident?.market_ready||ident.exact_identity_status!=='confirmed'){status('Completa l’identità prima di cercare vendite della variante esatta.','warn');return;}
 if(!needKey())return;if(!canUse()){showPaywall();return;}
 const saved=ident,ctx=scan164,p=FlipCheckCardMarket.plan(saved),previousTrial=JSON.parse(JSON.stringify(trial));
 if(trial.free){trial.free=false;trial.attempts=0;}else trial.credits=Math.max(0,(trial.credits||0)-1);saveTrial();
 saved.card_market={state:'searching',target:p.target,sales:[],grade_rows:[]};paintCardMarket236(saved);
 const body={model:'gpt-5.6-luna',reasoning:{effort:'low'},store:false,max_output_tokens:3000,max_tool_calls:2,tools:[{type:'web_search',filters:{allowed_domains:p.domains},search_context_size:'medium'}],include:['web_search_call.action.sources'],input:FlipCheckCardMarket.prompt(p),...schemaFormat('flipcheck_card_market',FlipCheckCardMarket.schema)};
 // A fresh explicit button press may refresh the same identity; no automatic retry.
 ctx.requestKeys.delete('market:'+JSON.stringify(body.input));
 const started=Date.now();
 try{
  const response=await originalOpenai26(body);addUsage(response,body.model,countWeb(response),'Vendite carta',false,started);guard164(ctx);
  let data=parseResponseJSON(response);const sources=collectSources(response),retrieval=[];
  if(data.access==='unavailable'){
   const urls=[...new Set(sources.map(s=>s.url))].filter(u=>{try{const x=new URL(u);return x.protocol==='https:'&&p.domains.includes(x.hostname.replace(/^www\./,''))&&x.pathname.startsWith('/game/');}catch(_){return false;}}).slice(0,2);
   for(const url of urls){try{const page=await directCall165('page',{url,terms:[p.target.subject,p.target.number,'Sold Listings','Sale Date']},ctx,8500);retrieval.push({url,status:page.status,rows:page.market_rows?.length||0});if(page.status!==200||!page.market_rows?.length)continue;
    const retry={model:body.model,reasoning:{effort:'low'},store:false,max_output_tokens:2400,input:FlipCheckCardMarket.prompt(p)+' Nessuna ricerca aggiuntiva. Estrai esclusivamente dalle righe recuperate sotto; se nessuna documenta una vendita non inventarla. Fonte: '+JSON.stringify({url,title:page.title,rows:page.market_rows.slice(0,40)}),...schemaFormat('flipcheck_card_market_extract',FlipCheckCardMarket.schema)};
    if(ctx.budget.spent()+estimate164(retry)>ctx.budget.maxUsd){retrieval.push({url,state:'extraction_budget_exhausted'});break;}
    const t=Date.now(),r=await originalOpenai26(retry);addUsage(r,retry.model,0,'Lettura vendite dalla scheda',false,t);guard164(ctx);data=parseResponseJSON(r);break;
   }catch(error){guard164(ctx);retrieval.push({url,state:'unavailable',reason:responseReason166(error)});}}
  }
  const m=FlipCheckCardMarket.evaluate(data,saved,sources);m.retrieval=retrieval;m.extraction=data;m.web_actions=(response.output||[]).filter(o=>o.type==='web_search_call').map(o=>({status:o.status,action:o.action}));
  saved.card_market=m;ctx.cardMarket=m;diagnosticPhases.push({stage:'card_market',result:m,webCalls:countWeb(response),usage:response.usage||null});
  paintCardMarket236(saved);$('resultPanel').classList.add('hide');persistCompletedScan(p.query,'completed');
  status(m.stat?'Vendite selezionate. Valore e fonti sono nella scheda della carta.':m.state==='one_of_one_no_sales'?'Identità confermata · nessuna vendita comparabile trovata per questa 1/1.':'Identità conservata · '+({source_unavailable:'sito non accessibile',card_not_found:'scheda non trovata',few_comparables:'campione vendite insufficiente'}[m.state]||'dati di mercato insufficienti')+'.',m.stat?'ok':'warn');
 }catch(error){
  if(ctx!==scan164||ctx.budget.cancelled)return;
  trial=previousTrial;saveTrial();const state=/budget/.test(error.message)?'budget_exhausted':'service_error';saved.card_market={state,target:p.target,sales:[],grade_rows:[]};ctx.cardMarket=saved.card_market;
  diagnosticPhases.push({stage:'card_market',result:{...saved.card_market,error:error.message},webCalls:0});paintCardMarket236(saved);persistCompletedScan(p.query,state);
  status(state==='budget_exhausted'?'Identità conservata. Il budget residuo non copre la ricerca vendite.':'Ricerca non completata: '+error.message,'warn');
 }
}
