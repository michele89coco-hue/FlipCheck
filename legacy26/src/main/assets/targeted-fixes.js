/* v0.26.1: multi-selection, independent Pokémon printing evidence, diagnostic export. */
'use strict';
let photoBusy = false, apiBusy = false, lastVisionReading = null;
let diagnosticPhases = [], photoEvents = [];
let resolverFailure = null, initialCardCheck = null;
const originalOpenai26 = openai;
const originalShouldResolve26 = shouldResolveOnline;
const originalSignature26 = buildFingerprintSignature;
const originalResolver26 = resolveIdentificationCheap;
const originalCandidateScore26 = candidateFingerprintScore;
const originalPolicy26 = enforceIdentificationPolicy;
const originalRender26 = renderIdent;
const originalIdentify26 = $('identifyBtn').onclick;
const originalMarket26 = $('marketBtn').onclick;

function lockPhotoControls() {
  const busy = photoBusy || apiBusy;
  ['identifyBtn','addPhotos','addConfirmPhoto','photoBatch','photo0','photo1','photo2','details','confirmed'].forEach(id => $(id).disabled = busy);
  document.querySelectorAll('.remove-photo').forEach(button => button.disabled = busy);
  $('marketBtn').disabled = busy || $('confirmed').value.trim().length < 4;
}
function invalidatePhotoReading() {
  resetLocalOcr(); ident = null; lastVisionReading = null; currentScan = null; diagnosticPhases = [];
  resolverFailure = null; initialCardCheck = null;
  $('identPanel').classList.add('hide'); $('resultPanel').classList.add('hide');
  $('runStatus').classList.add('hide'); $('liveCost').classList.add('hide');
  $('confirmed').value = ''; $('marketBtn').disabled = true;
}
removePhoto = function(i) {
  if (photoBusy || apiBusy) return;
  files[i] = null; images[i] = null; $('photo'+i).value = ''; renderSlot(i);
  invalidatePhotoReading(); updatePhotoStatus(validImageCount() ? null : 'Nessuna foto caricata. Puoi selezionarne fino a 3 insieme.');
  lockPhotoControls();
};
async function loadSelectedPhotos(selected, replaceIndex = null) {
  if (photoBusy || apiBusy) return;
  const chosen = Array.from(selected || []);
  if (!chosen.length) return; // Picker cancellation must preserve the previous reading and photos.
  const slots = replaceIndex === null ? [0,1,2].filter(i => !images[i]) : [replaceIndex];
  if (!slots.length) { updatePhotoStatus('Hai già 3 foto. Rimuovine una o toccala per sostituirla.'); return; }
  const accepted = chosen.slice(0,slots.length);
  photoBusy = true; lockPhotoControls(); updatePhotoStatus('Caricamento di '+accepted.length+' foto…');
  let loaded = 0, failed = 0, duplicates = 0;
  try {
    // Promise result order follows selection order even when image decoding finishes out of order.
    const results = await Promise.allSettled(accepted.map(file => resize(file)));
    results.forEach((result, index) => {
      if (result.status !== 'fulfilled') { failed++; return; }
      const slot = slots[index];
      if (images.some((value,i) => i !== slot && value === result.value)) { duplicates++; return; }
      files[slot] = accepted[index]; images[slot] = result.value; renderSlot(slot); loaded++;
    });
    if (loaded) invalidatePhotoReading();
    const notes = [];
    if (chosen.length > slots.length) notes.push('Limite 3 foto: '+(chosen.length-slots.length)+' non aggiunte');
    if (duplicates) notes.push(duplicates+' duplicate non aggiunte');
    if (failed) notes.push(failed+' non leggibili; le foto precedenti sono conservate');
    updatePhotoStatus(validImageCount()+' foto caricate'+(notes.length ? ' · '+notes.join(' · ') : ' ✓'));
    photoEvents.push({selected:chosen.length,loaded,failed,duplicates,total:validImageCount()});
    photoEvents = photoEvents.slice(-10);
  } finally { photoBusy = false; lockPhotoControls(); }
}
for (let i = 0; i < 3; i++) {
  $('s'+i).onclick = event => {
    if (photoBusy || apiBusy || event.target.closest('.remove-photo')) return;
    if (!images[i]) { openBatchPicker(); return; }
    $('photo'+i).value = ''; $('photo'+i).click();
  };
  $('photo'+i).onchange = event => loadSelectedPhotos(event.target.files,i);
}
function openBatchPicker() {
  if (photoBusy || apiBusy) return;
  if (validImageCount() >= 3) { updatePhotoStatus('Hai già 3 foto. Tocca × per liberare un posto.'); return; }
  $('photoBatch').value = ''; $('photoBatch').click();
}
$('photoBatch').onchange = event => loadSelectedPhotos(event.target.files);
$('addPhotos').onclick = openBatchPicker;
$('addConfirmPhoto').onclick = () => { openBatchPicker(); window.scrollTo({top:0,behavior:'smooth'}); };

openai = async function(body) {
  const identification = body.text?.format?.name === 'flipcheck_identification';
  const resolver = body.text?.format?.name === 'flipcheck_resolver';
  if (identification) {
    const schema = JSON.parse(JSON.stringify(body.text.format.schema));
    schema.properties.pokemon_printing = FlipCheckEditions.schema;
    schema.required.push('pokemon_printing');
    body = Object.assign({},body,{text:{...body.text,format:{...body.text.format,schema}},
      max_output_tokens:2150,input:body.input.map(message => ({...message,content:message.content.map(item =>
        item.type === 'input_text' ? {...item,text:item.text+'\n\n'+FlipCheckEditions.prompt+'\n\n'+FlipCheckRecognition.visionPrompt} : item)}))});
  }
  if (resolver) {
    const schema=JSON.parse(JSON.stringify(body.text.format.schema));
    schema.properties.candidate_checks.maxItems=2;
    // Existing v26 grounding supports these values, but its original schema excluded them.
    schema.properties.candidate_checks.items.properties.source_specificity.enum=
      ['exact_model','identifier_grounded','identifier_normalized','family','generic'];
    body={...body,max_output_tokens:2600,text:{...body.text,format:{...body.text.format,schema}},
      input:body.input+'\n\n'+FlipCheckRecognition.resolverPrompt+
        (initialCardCheck?.status==='conflict'?'\nCONFLITTO CATALOGO LOCALE (lettura originale da verificare):\n'+JSON.stringify(initialCardCheck):'')};
  }
  const stage=identification?'vision':(body.text?.format?.name || 'api');
  const started=Date.now();
  const safeError=e=>String(e?.message || e || '').split(key() || '\u0000').join('[redacted]').slice(0,400);
  let response;
  try { response=await originalOpenai26(body); }
  catch(error) {
    diagnosticPhases.push({stage,attempted:true,responseStatus:'request_error',error:safeError(error),elapsedMs:Date.now()-started});
    diagnosticPhases=diagnosticPhases.slice(-6);
    throw error;
  }
  let parsed = null, parseError = null;
  try { parsed = parseResponseJSON(response); } catch (error) { parseError=safeError(error); }
  if (identification) lastVisionReading = parsed ? JSON.parse(JSON.stringify(parsed)) : null;
  diagnosticPhases.push({stage,attempted:true,result:parsed,responseStatus:response.status || 'unknown',
    incompleteReason:response.incomplete_details?.reason || null,parseError,
    maxOutputTokens:body.max_output_tokens,outputCharacters:outText(response).length,elapsedMs:Date.now()-started,
    webCalls:countWeb(response),usage:response.usage || null});
  diagnosticPhases = diagnosticPhases.slice(-6);
  return response;
};
shouldResolveOnline = function(identity) {
  const guarded=FlipCheckRecognition.guard(identity);
  initialCardCheck=guarded?.card_consistency || null;
  if (guarded!==identity) Object.assign(identity,guarded);
  return originalShouldResolve26(identity);
};
buildFingerprintSignature = function(base,userDetails) {
  const signature=originalSignature26(base,userDetails);
  const check=base.card_consistency;
  if (check?.status!=='conflict') return signature;
  // A contradictory collector number cannot force the search toward the wrong card.
  // It stays in the diagnostic/prompt as a disputed observation, not an exact search key.
  const excluded=new Set([...(base.identifier_hints || []),...(base.search_terms || []).filter(t=>/\d+\s*\//.test(t))].map(canonTerm));
  signature.terms=signature.terms.filter(t=>!excluded.has(canonTerm(t)) && !/\d+\s*\//.test(t));
  signature.discovery=signature.discovery.filter(t=>!excluded.has(canonTerm(t)) && !/\d+\s*\//.test(t));
  signature.identifiers=[]; signature.identifierVariants=[]; signature.hints=[]; signature.primaryIdentifier='';
  signature.mode='fingerprint'; signature.family=''; signature.contextNoun='pokemon'; signature.category='Pokemon card';
  signature.query=['Pokemon',check.subject,...signature.discovery.filter(t=>canonTerm(t)!==canonTerm(check.subject)).slice(0,3)].join(' ');
  return signature;
};
resolveIdentificationCheap = async function(base,userDetails) {
  try { return await originalResolver26(base,userDetails); }
  catch(error) {
    const phase=diagnosticPhases.findLast(p=>p.stage==='flipcheck_resolver');
    resolverFailure={reason:phase?.incompleteReason || (phase?.responseStatus==='request_error'?'request_error':'invalid_response'),
      responseStatus:phase?.responseStatus || 'unknown',webCalls:phase?.webCalls || 0};
    return {...base,resolver_error:resolverFailure};
  }
};
candidateFingerprintScore = function(candidate,signature,hasSources) {
  const score=originalCandidateScore26(candidate,signature,hasSources);
  if (initialCardCheck?.status!=='conflict' || signature.kind!=='card' || !lastVisionReading) return score;
  const check=FlipCheckRecognition.inspect({...lastVisionReading,model:candidate.model,family:candidate.family},lastVisionReading);
  // A real, relevant source plus a matching offline tuple adds corroboration only
  // on the conflict-recovery path. Catalogue suggestions alone cannot raise a score.
  if (check?.status==='consistent' && check.number && check.hp.length===1 && score.conflicts===0
    && score.strongSourceCount>=1 && score.exactModel && score.rareMatched>=2 && score.score>=80)
    return {...score,score:Math.min(96,score.score+8),catalogCorroborated:true};
  return score;
};
enforceIdentificationPolicy = function(identity) {
  let out=FlipCheckRecognition.guard(identity,lastVisionReading || identity);
  const printing=FlipCheckRecognition.printingFor(out,lastVisionReading?.pokemon_printing);
  out=FlipCheckEditions.apply(originalPolicy26(out),printing,validImageCount());
  return FlipCheckRecognition.removeRedundantPhoto(out,lastVisionReading);
};
renderIdent = function(identity) {
  originalRender26(identity);
  const conflict=identity.card_consistency?.status==='conflict';
  if (conflict) {
    $('identTitle').textContent=identity.card_consistency.subject+' · dati da verificare';
    $('tags').innerHTML='<span class="tag">CARTA</span><span class="tag">DATI INCOERENTI</span>';
  }
  if (resolverFailure || identity.photo_request_suppressed || conflict) {
    const need=$('identNote').querySelector('.need');
    if (need) {
      const text=resolverFailure?'La verifica web non si è completata. Le informazioni lette nelle foto sono conservate.'
        :conflict?'Nome, numero o set non corrispondono. Serve risolvere questa discordanza prima della conferma.'
        :'I codici sono già stati letti nelle foto. Resta da verificare a quale modello corrispondono.';
      need.innerHTML='<b>Verifica da completare</b><br>'+esc(text);
    }
    if (!identity.next_photo_request || resolverFailure) $('addConfirmPhoto').classList.add('hide');
  }
  const previous = $('printingPanel'); if (previous) previous.remove();
  const check = identity.printing_check; if (!check) return;
  const panel = document.createElement('div'); panel.id = 'printingPanel'; panel.className = 'status';
  const stamp = {present:'Timbro 1st Edition visibile',absent:'Timbro 1st Edition assente nella zona leggibile',unclear:'Timbro di edizione da verificare',not_applicable:'Timbro 1st Edition non applicabile'};
  const shadow = {absent:'Shadowless: bordo e copyright coerenti',present:'Ombra del riquadro presente',unclear:'Stampa Shadowless da verificare',not_applicable:'Criterio Shadowless non applicabile a questa lingua/serie'};
  panel.innerHTML = '<b>Edizione e stampa</b><br>'+esc(stamp[check.stamp])+'<br>'+esc(shadow[check.shadow])
    +(check.slab ? '<br><b>Dichiarazione sulla slab:</b> '+esc(check.slab) : '')
    +(check.contradiction ? '<br><span class="warn">Etichetta e dettagli della carta discordanti: verifica la stampa.</span>' : '')
    +(check.missing.length ? '<br><b>Dettaglio da verificare:</b> '+esc(check.missing.join('; ')) : '');
  $('identNote').prepend(panel);
  if (!check.complete) {
    const tag = document.createElement('span'); tag.className = 'tag'; tag.textContent = 'EDIZIONE DA VERIFICARE'; $('tags').append(tag);
  }
};
async function runLocked26(handler) {
  if (photoBusy || apiBusy) return;
  apiBusy = true; lockPhotoControls();
  try { return await handler(); } finally { apiBusy = false; lockPhotoControls(); }
}
$('identifyBtn').onclick = async () => {
  if (photoBusy || apiBusy) return;
  lastVisionReading = null; diagnosticPhases = []; resolverFailure=null; initialCardCheck=null;
  // A failed new request must not leave the previous object's result in diagnostics.
  ident=null; currentScan=null;
  await runLocked26(originalIdentify26);
  if (resolverFailure) status('Verifica web interrotta'+(resolverFailure.reason==='max_output_tokens'?': risposta incompleta.':'.')+' Le informazioni lette sono conservate; nessuna ricerca mercato avviata.','warn');
  else if (ident?.card_consistency?.status==='conflict') status('Dati della carta discordanti: identità da verificare.','warn');
};
$('marketBtn').onclick = () => runLocked26(originalMarket26);

function diagnostic26() {
  let build = {versionCode:160,versionName:'0.26.3-targeted-recognition',sourceCommit:'browser-test'};
  try { if (window.FlipCheckHost) build = JSON.parse(FlipCheckHost.buildInfo()); } catch (_) { }
  return {schema:'flipcheck-v026-targeted-diagnostic-1',...build,baseline:'0.26',
    exportedAt:new Date().toISOString(),uploadedImageCount:validImageCount(),photoEvents,
    identification:ident,visionResult:lastVisionReading,initialCardCheck,resolverFailure,
    phases:diagnosticPhases,usage:currentScan?.totals || null};
}
$('exportDiagnostic').onclick = () => {
  const payload = JSON.stringify(diagnostic26(),null,2);
  if (window.FlipCheckHost) { FlipCheckHost.saveDiagnostic(payload); return; }
  const blob = new Blob([payload],{type:'application/json'}), url = URL.createObjectURL(blob);
  const a = document.createElement('a'); a.href = url; a.download = 'FlipCheck-26Fix-diagnostica.json'; a.click();
  setTimeout(() => URL.revokeObjectURL(url),1000);
};
lockPhotoControls();
