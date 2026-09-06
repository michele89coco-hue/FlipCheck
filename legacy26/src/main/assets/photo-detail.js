/* Photo preparation and literal detail reading, layered on the v0.26.2 host. */
'use strict';
const photoPrepared264 = new WeakMap();
let detailDiagnostics264 = {photos:[],crops:[],skipped:[],readingFailure:null};
const photoSchema264 = {
  type:'array',maxItems:3,items:{type:'object',additionalProperties:false,
    properties:{image_index:{type:'integer',minimum:1,maximum:3},purpose:{type:'string',enum:['collector_number','serial_number','product_label','slab_label','edition','printed_text']},
      x:{type:'number',minimum:0,maximum:1},y:{type:'number',minimum:0,maximum:1},width:{type:'number',minimum:0,maximum:1},height:{type:'number',minimum:0,maximum:1}},
    required:['image_index','purpose','x','y','width','height']}
};
const observationSchema264 = {type:'array',maxItems:8,items:{type:'object',additionalProperties:false,
  properties:{text:{type:'string'},role:{type:'string',enum:['collector_number','serial_number','model','part_number','barcode','slab_cert','season','printed_code']},
    image_index:{type:'integer',minimum:1,maximum:3},legibility:{type:'string',enum:['clear','uncertain']}},required:['text','role','image_index','legibility']}};
const detailPrompt264 = `LETTURA AD ALTA RISOLUZIONE:
Se hai già nome/numero/codice e prove nitide non proporre crop ridondanti. Anno/stagione è season, un codice accessorio e-reader è printed_code: nessuno dei due è un modello commerciale. Trascrivi i testi fisici, senza completare da memoria numeri, copyright o modello. Distingui numero catalografico della carta, seriale della singola copia (/5, /99...), certificato della slab, codice modello, ricambio e barcode. Riporta gli identificatori in identifier_observations con ruolo, foto originale e leggibilita'; un numero ipotizzato NON e' clear.
detail_regions: individua fino a 3 riquadri PRECISI di testo piccolo utile da rileggere sull'originale. Priorita': numero carta/seriale, etichetta slab o modello/SKU, timbro edizione o riga stampata decisiva. Coordinate normalizzate 0..1 rispetto all'INTERA foto orientata mostrata, origine in alto a sinistra. Inquadra l'intera riga/etichetta con margine, non la carta intera, non un ritaglio fisso. Usa [] se i testi decisivi sono gia' grandi e inequivocabili o non individui la zona. Non individuare testi dell'interfaccia telefono, annunci o watermark. Non inventare una posizione per un testo non visibile.
Per box sigillati leggi anno, linea, diciture, configurazione pacchetti/carte/autografi. Hobby, Blaster, Mega e Retail non sono equivalenti: non confermare un formato dedotto dalla forma. Per tutti gli oggetti usa tutte le foto fornite.`;
async function decodeOriginal264(file) {
  try { if (window.createImageBitmap) return await createImageBitmap(file,{imageOrientation:'from-image'}); } catch (_) {}
  return new Promise((resolve,reject)=>{
    const url=URL.createObjectURL(file),im=new Image();
    im.onload=()=>{URL.revokeObjectURL(url);resolve(im);};
    im.onerror=()=>{URL.revokeObjectURL(url);reject(new Error('Immagine non leggibile'));};im.src=url;
  });
}
function imageDimensions264(image){return {width:image.naturalWidth || image.width,height:image.naturalHeight || image.height};}
function drawPhoto264(image,rect,maxSide,mime,quality) {
  const scale=Math.min(1,maxSide/Math.max(rect.width,rect.height));
  const canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(rect.width*scale));canvas.height=Math.max(1,Math.round(rect.height*scale));
  const ctx=canvas.getContext('2d');ctx.fillStyle='#ffffff';ctx.fillRect(0,0,canvas.width,canvas.height);
  ctx.imageSmoothingEnabled=true;ctx.imageSmoothingQuality='high';
  ctx.drawImage(image,rect.x,rect.y,rect.width,rect.height,0,0,canvas.width,canvas.height);
  const data=canvas.toDataURL(mime,quality),result={data,width:canvas.width,height:canvas.height,mime,encodedBytes:Math.floor((data.length-data.indexOf(',')-1)*3/4)};
  canvas.width=canvas.height=1;return result;
}
resize = async function(file) {
  const source=await decodeOriginal264(file);
  try {
    const size=imageDimensions264(source),out=drawPhoto264(source,{x:0,y:0,...size},2560,'image/jpeg',.94);
    photoPrepared264.set(file,{originalWidth:size.width,originalHeight:size.height,sentWidth:out.width,sentHeight:out.height,
      originalBytes:file.size,encodedBytes:out.encodedBytes,mime:out.mime,jpegQuality:.94});return out.data;
  } finally { if(source.close)source.close(); }
};
async function detailCrops264(regions) {
  const active=files.filter(Boolean),out=[],seen=new Set();
  for(const r of (Array.isArray(regions)?regions:[]).slice(0,3)) {
    if(!r || !Number.isInteger(r.image_index) || !active[r.image_index-1] ||
      ![r.x,r.y,r.width,r.height].every(Number.isFinite) || r.x<0 || r.y<0 || r.width<=0 || r.height<=0 ||
      r.x+r.width>1.001 || r.y+r.height>1.001 || r.width*r.height>.65) {
      detailDiagnostics264.skipped.push({reason:'invalid_or_oversized_region'});continue;
    }
    const key=[r.image_index,r.x,r.y,r.width,r.height].join(':');if(seen.has(key))continue;seen.add(key);
    const source=await decodeOriginal264(active[r.image_index-1]);
    try {
      const size=imageDimensions264(source),pad=.015;
      const x=Math.max(0,Math.floor((r.x-pad)*size.width)),y=Math.max(0,Math.floor((r.y-pad)*size.height));
      const right=Math.min(size.width,Math.ceil((r.x+r.width+pad)*size.width)),bottom=Math.min(size.height,Math.ceil((r.y+r.height+pad)*size.height));
      const rect={x,y,width:right-x,height:bottom-y};
      if(rect.width<24 || rect.height<12){detailDiagnostics264.skipped.push({image_index:r.image_index,reason:'too_small'});continue;}
      const crop=drawPhoto264(source,rect,1600,'image/png');
      const meta={image_index:r.image_index,purpose:r.purpose,originalRect:rect,sentWidth:crop.width,sentHeight:crop.height,mime:crop.mime,encodedBytes:crop.encodedBytes};
      detailDiagnostics264.crops.push(meta);out.push({...meta,data:crop.data,overview:drawPhoto264(source,{x:0,y:0,...size},1024,'image/jpeg',.9).data});
    } finally {if(source.close)source.close();}
  }
  return out;
}
