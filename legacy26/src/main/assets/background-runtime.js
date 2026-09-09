/* Native lifetime only: the existing identity engine and budget remain the execution path. */
'use strict';
(function(){
 const host=window.FlipCheckHost;
 if(!host?.beginScan||!host?.endScan)return;
 const pending=new Map();let activeToken='',cancelled=false;
 window.FlipCheckBackground={
  started(token,ok,reason){const p=pending.get(token);if(!p)return;pending.delete(token);clearTimeout(p.timer);ok?p.resolve():p.reject(new Error('Impossibile avviare l’analisi in background: '+reason));},
  cancel(token){if(token!==activeToken)return;cancelled=true;$('cancelScan')?.click();},
 };
 function begin(token){return new Promise((resolve,reject)=>{
  const timer=setTimeout(()=>{pending.delete(token);reject(new Error('Avvio della scansione non riuscito. Nessuna richiesta di analisi inviata.'));},5000);
  pending.set(token,{resolve,reject,timer});try{host.beginScan(token);}catch(error){clearTimeout(timer);pending.delete(token);reject(error);}
 });}
 const previousRun=runLocked26;
 runLocked26=function(handler){return previousRun(async()=>{
  const token=crypto.randomUUID();activeToken=token;cancelled=false;let failed=false,started=false;
  try{await begin(token);started=true;if(cancelled)return;return await handler();}
  catch(error){failed=true;status(error.message,'error');return undefined;}
  finally{
   const pendingStart=pending.get(token);if(pendingStart){clearTimeout(pendingStart.timer);pending.delete(token);}
   let snapshot='{}';try{const d=diagnostic26(),saved={};for(const key of ['identification','visionResult','usage','versionCode','versionName','sourceCommit','exportedAt','identificationPipeline'])if(d[key]!==undefined)saved[key]=d[key];snapshot=JSON.stringify(saved);}catch(_){}
   failed=failed||(typeof currentScan!=='undefined'&&currentScan?.status==='technical_error');
   const wasCancelled=cancelled||(typeof scan164!=='undefined'&&scan164?.budget.cancelled);
   // A timed-out start must also release any native service that arrived too late.
   try{host.endScan(token,snapshot,wasCancelled?'cancelled':failed||!started?'failed':'completed');}catch(_){}
   if(activeToken===token)activeToken='';
  }
 });};
 const previousDiagnostic=diagnostic26;
 diagnostic26=function(){let nativeBackground=null;try{nativeBackground=JSON.parse(host.backgroundInfo());}catch(_){}return {...previousDiagnostic(),nativeBackground};};
 // Normal background/Activity recreation retains the full page. After process death, show
 // the last saved outcome without replaying requests or claiming that photos are still loaded.
 try{
  const saved=JSON.parse(host.lastScan()),snapshot=saved.snapshot?JSON.parse(saved.snapshot):null;
  if(saved.state==='interrupted'||snapshot?.identification){
   const notice=document.createElement('div');notice.id='savedScan178';notice.className='status';
   const value=snapshot?.identification;
   notice.textContent=saved.state==='interrupted'?'La scansione precedente è stata interrotta. Nessuna chiamata è stata ripetuta automaticamente.':'Ultimo risultato salvato: '+(value.model||value.title||value.category||'oggetto')+(value.variant?' · '+value.variant:'')+(value.market_ready?' · Identità confermata.':' · Identità ancora da completare.');
   $('scanPage').prepend(notice);
   const clear=invalidatePhotoReading;invalidatePhotoReading=function(){notice.remove();return clear();};
  }
 }catch(_){}
})();
