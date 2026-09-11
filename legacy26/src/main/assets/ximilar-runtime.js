/* Optional direct Ximilar adapter. Credentials stay in memory; no raw response is persisted. */
'use strict';
const ximilarPanel233=document.createElement('div');ximilarPanel233.className='panel';
ximilarPanel233.innerHTML='<div class="label">Riconoscimento carte</div><label class="label" for="cardProvider233">Servizio immagini</label><select id="cardProvider233"><option value="lens">Google Lens / SearchAPI</option><option value="ximilar">Ximilar</option></select><label class="label" for="ximilarToken233">Token API Ximilar</label><input id="ximilarToken233" type="password" autocomplete="off" spellcheck="false" placeholder="Incolla il token Ximilar"><p class="note">Il token resta in memoria finché l’app rimane aperta. Per Ximilar serve accesso Collectibles attivo. Viene inviata una foto frontale: riconoscimento e statistiche prezzi, senza Magic AI o lettura slab aggiuntiva. Riserva stimata: $0,0138 per foto sul piano Business 100K; il costo effettivo dipende dal piano. OpenAI resta necessario per letture e verifiche.</p><p id="ximilarStatus233" class="note"></p>';
$('settingsPage').firstElementChild.after(ximilarPanel233);
try{$('cardProvider233').value=localStorage.getItem('flipcheck_card_provider')==='ximilar'?'ximilar':'lens';}catch(_){}
function ximilarSelected233(){return $('cardProvider233').value==='ximilar';}
function ximilarSettings233(){localStorage.setItem('flipcheck_card_provider',ximilarSelected233()?'ximilar':'lens');$('ximilarStatus233').textContent=ximilarSelected233()?$('ximilarToken233').value.trim()?'Ximilar selezionato. Accesso da verificare alla prima scansione.':'Incolla il token Ximilar per iniziare.':'Google Lens selezionato.';}
for(const id of ['cardProvider233','ximilarToken233'])$(id).addEventListener('change',ximilarSettings233);ximilarSettings233();
function ximilarMessage233(state){return ({ok:'Ximilar: candidati ricevuti e confrontati con le letture della foto.',no_match:'Ximilar: nessuna carta riconosciuta. Continuo con OCR e cataloghi.',authentication_failed:'Ximilar: autenticazione rifiutata (HTTP 401). Consulta il dettaglio nel report.',invalid_token:'Ximilar: formato del token non valido.',access_denied:'Ximilar: accesso rifiutato (HTTP 403). Consulta il dettaglio nel report.',quota_or_rate_limit:'Ximilar: quota esaurita o limite temporaneo di richieste.',not_configured:'Ximilar: inserisci il token nelle Impostazioni.',budget_exhausted:'Ximilar: budget della scansione insufficiente.',front_required:'Ximilar: serve una foto frontale riconoscibile.',timeout:'Ximilar: tempo di risposta scaduto.'})[state]||'Ximilar non disponibile. Continuo con OCR e cataloghi.';}
async function recognizeXimilar233(l,ctx){
 if(!ximilarSelected233()||!FlipCheckXimilar.endpoint(l.domain))return [];
 if(ctx.ximilar)return ctx.ximilar.entries||[];
 const endpoint=FlipCheckXimilar.endpoint(l.domain),state=ctx.ximilar={provider:'ximilar',endpoint,state:'not_requested',requests:0,entries:[],estimatedUsd:0};
 const token=FlipCheckXimilar.token($('ximilarToken233').value);let reservation=null;const started=Date.now();
 try{
  if(!token){state.state='not_configured';return [];}
  const view=(l.base.image_views||[]).find(v=>v.view==='front'&&v.certainty==='clear'),index=view?.image_index||(validImageCount()===1?1:null),region=(l.base.object_regions||[]).find(r=>r.image_index===index);
  const picture=index?await visualPhoto164({object_region:region?{...region,certain:true}:{image_index:index,certain:false},object_unit:'single'}):null;guard164(ctx);if(!picture){state.state='front_required';return [];}
  reservation=ctx.budget.reserve('ximilar',FlipCheckXimilar.UNIT_USD);state.estimatedUsd=FlipCheckXimilar.UNIT_USD;state.requests=1;state.image={image_index:picture.meta.imageIndex||1};
  const reply=await directCall165('ximilar',{token,endpoint,image_base64:picture.data.split(',')[1]},ctx,40000);
  state.httpStatus=reply.status||0;if(reply.status!==200)state.provider_error=FlipCheckXimilar.errorDetails(reply.body,token);
  if(reply.status!==200){state.state=reply.state==='invalid_api_key'?'invalid_token':reply.state==='timeout'?'timeout':FlipCheckXimilar.state(reply.status);ctx.budget.settle(reservation,[401,402,403,429].includes(reply.status)||reply.attempted===false?0:null);state.billingUnknown=![401,402,403,429].includes(reply.status)&&reply.attempted!==false;return [];}
  const normalized=FlipCheckXimilar.normalize(reply.body,l.domain);Object.assign(state,normalized);
  // Subscription unit cost is an estimate, not provider-confirmed monetary usage.
  reservation.status='estimated';state.billingUnknown=false;return state.entries;
 }catch(error){if(reservation){ctx.budget.settle(reservation,null);state.billingUnknown=true;}state.state=/timeout/.test(error.message)?'timeout':error.message;if(error.message==='scan_cancelled')throw error;return [];}
 finally{state.elapsedMs=Date.now()-started;ctx.calls.push({provider:'ximilar',kind:'ximilar',state:state.state,requests:state.requests,estimatedUsd:state.estimatedUsd,elapsedMs:state.elapsedMs});diagnosticPhases.push({stage:'ximilar_recognition',result:state,webCalls:0});$('ximilarStatus233').textContent=ximilarMessage233(state.state)+(state.provider_error?.message?' '+state.provider_error.message:'');renderLiveCost();}
}

// Prices are rendered from the same recognition response, never fetched by rendering.
(function(){const previous=renderIdent;renderIdent=function(value){previous(value);document.getElementById('priceSummary235')?.remove();if(!value||value.kind!=='card')return;const ctx=typeof scan164==='undefined'?null:scan164;if(!ctx?.ximilar)return;const model=FlipCheckPriceSummary.model(value,lastVisionReading,ctx.ximilar.entries);value.price_summary=model;const panel=document.createElement('div');panel.id='priceSummary235';panel.innerHTML=FlipCheckPriceSummary.html(model);$('identityHeader229').after(panel);$('marketBtn').textContent='APPROFONDISCI IL VALORE';};})();
