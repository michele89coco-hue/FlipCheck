/* v0.26.1: multi-selection, independent Pokémon printing evidence, diagnostic export. */
'use strict';
let photoBusy = false, apiBusy = false, lastVisionReading = null;
let diagnosticPhases = [], photoEvents = [];
const originalOpenai26 = openai;
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
  if (identification) {
    const schema = JSON.parse(JSON.stringify(body.text.format.schema));
    schema.properties.pokemon_printing = FlipCheckEditions.schema;
    schema.required.push('pokemon_printing');
    body = Object.assign({},body,{text:{...body.text,format:{...body.text.format,schema}},
      max_output_tokens:2150,input:body.input.map(message => ({...message,content:message.content.map(item =>
        item.type === 'input_text' ? {...item,text:item.text+'\n\n'+FlipCheckEditions.prompt} : item)}))});
  }
  const response = await originalOpenai26(body);
  let parsed = null;
  try { parsed = parseResponseJSON(response); } catch (_) { }
  if (identification) lastVisionReading = parsed ? JSON.parse(JSON.stringify(parsed)) : null;
  diagnosticPhases.push({stage:identification ? 'vision' : (body.text?.format?.name || 'api'),
    result:parsed,webCalls:countWeb(response),usage:response.usage || null});
  diagnosticPhases = diagnosticPhases.slice(-6);
  return response;
};
enforceIdentificationPolicy = function(identity) {
  return FlipCheckEditions.apply(originalPolicy26(identity),lastVisionReading?.pokemon_printing,validImageCount());
};
renderIdent = function(identity) {
  originalRender26(identity);
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
$('identifyBtn').onclick = () => {
  if (photoBusy || apiBusy) return;
  lastVisionReading = null; diagnosticPhases = [];
  return runLocked26(originalIdentify26);
};
$('marketBtn').onclick = () => runLocked26(originalMarket26);

function diagnostic26() {
  let build = {versionCode:161,versionName:'0.26.4-photo-detail-web',sourceCommit:'browser-test'};
  try { if (window.FlipCheckHost) build = JSON.parse(FlipCheckHost.buildInfo()); } catch (_) { }
  return {schema:'flipcheck-v026-targeted-diagnostic-1',...build,baseline:'0.26',
    exportedAt:new Date().toISOString(),uploadedImageCount:validImageCount(),photoEvents,
    identification:ident,visionResult:lastVisionReading,phases:diagnosticPhases,usage:currentScan?.totals || null};
}
$('exportDiagnostic').onclick = () => {
  const payload = JSON.stringify(diagnostic26(),null,2);
  if (window.FlipCheckHost) { FlipCheckHost.saveDiagnostic(payload); return; }
  const blob = new Blob([payload],{type:'application/json'}), url = URL.createObjectURL(blob);
  const a = document.createElement('a'); a.href = url; a.download = 'FlipCheck-26Fix-diagnostica.json'; a.click();
  setTimeout(() => URL.revokeObjectURL(url),1000);
};
lockPhotoControls();
