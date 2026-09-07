/* Google REST and public references through Android networking; no application server. */
(function(root){
'use strict';
const pending=new Map(),list=x=>Array.isArray(x)?x:[];
function url(value){try{const u=new URL(value);return u.protocol==='https:'&&!u.username&&!u.password&&(!u.port||u.port==='443')?u.href.replace(/#.*$/,''):'';}catch(_){return '';}}
function receive(id,result){const p=pending.get(id);if(p)p.resolve(result);}
function call(action,payload,{signal,timeoutMs=22000}={}){
 if(!root.FlipCheckGoogle?.request)return Promise.reject(new Error('native_service_missing'));
 return new Promise((resolve,reject)=>{
  const id=crypto.randomUUID();let timer;
  const finish=(value,error)=>{if(!pending.has(id))return;pending.delete(id);clearTimeout(timer);signal?.removeEventListener('abort',abort);error?reject(error):resolve(value);};
  const abort=()=>{root.FlipCheckGoogle.cancel(id);finish(null,new Error(signal?.aborted?'scan_cancelled':'scan_timeout'));};
  pending.set(id,{resolve:r=>finish(r)});signal?.addEventListener('abort',abort,{once:true});
  if(signal?.aborted){abort();return;}
  timer=setTimeout(abort,timeoutMs);
  try{if(action==='ocr')root.FlipCheckGoogle.readText(id,payload.image_data);else root.FlipCheckGoogle.request(id,action,JSON.stringify(payload));}catch(_){finish(null,new Error('native_service_missing'));}
 });
}
function errorState(response){
 if(response.state==='invalid_api_key')return 'invalid_api_key';
 const e=response.body?.error||{},reasons=list(e.details).map(d=>d.reason).join(' ');
 if(/API_KEY_INVALID|API_KEY_EXPIRED/.test(reasons)||response.status===401)return 'invalid_api_key';
 if(/SERVICE_DISABLED/.test(reasons))return 'api_not_enabled';
 if(/BILLING_DISABLED/.test(reasons))return 'billing_not_enabled';
 if(response.status===429||e.code===8)return 'quota_exhausted';
 if(response.status===403)return 'google_access_denied';
 return response.state==='timeout'?'timeout':'provider_unavailable';
}
function normalize(response){
 if(response.status!==200){
  const allowed=['network_error','dns_error','tls_error','connection_error','timeout','response_unavailable','invalid_request','invalid_api_key'];
  return {state:errorState(response),failureReason:allowed.includes(response.state)?response.state:'http_error',references:[],providerCalls:response.attempted===false?0:1,billingUnknown:response.attempted!==false};
 }
 const first=response.body?.responses?.[0];
 if(!first||first.error)return {state:first?.error?'provider_image_error':'empty_response',providerErrorCode:Number(first?.error?.code)||null,failureReason:first?.error?.code===3?'invalid_image':first?.error?.code===8?'quota_exhausted':first?.error?'google_processing_error':'empty_response',references:[],providerCalls:1,billingUnknown:true};
 const raw=first.webDetection||{},images=xs=>list(xs).slice(0,10).map(i=>({url:url(i.url)})).filter(i=>i.url);
 return {state:'ok',providerCalls:1,billingUnknown:false,
  webEntities:list(raw.webEntities).slice(0,10).map(e=>({description:String(e.description||'').slice(0,300),providerScore:e.score})),
  bestGuessLabels:list(raw.bestGuessLabels).slice(0,5).map(e=>({label:String(e.label||'').slice(0,300)})),
  fullMatchingImages:images(raw.fullMatchingImages),partialMatchingImages:images(raw.partialMatchingImages),visuallySimilarImages:images(raw.visuallySimilarImages),
  pagesWithMatchingImages:list(raw.pagesWithMatchingImages).slice(0,8).map(p=>({url:url(p.url),pageTitle:String(p.pageTitle||'').replace(/<[^>]*>/g,'').slice(0,500),fullMatchingImages:images(p.fullMatchingImages),partialMatchingImages:images(p.partialMatchingImages)})).filter(p=>p.url),
  scoreMeaning:'provider ranking only; not identity probability',references:[]};
}
async function references(found,options){
 if(found.state!=='ok')return found;
 // Filter first: early generic pages must not hide later pages with actual linked images.
 const selectedImages=new Set();
 const pages=list(found.pagesWithMatchingImages).map(p=>({...p,selectedImage:[...list(p.fullMatchingImages),...list(p.partialMatchingImages)].find(i=>url(i.url)&&FlipCheckVisual.referenceImageUseful(i.url))}))
  .filter(p=>{const image=p.selectedImage?.url;if(!image||selectedImages.has(url(image)))return false;selectedImages.add(url(image));return true;}).slice(0,3);
 const results=await Promise.allSettled(pages.map(async(p,i)=>{
  const image=p.selectedImage;
  // Both downloads are public GETs. The Google key is never passed to reference requests.
  const [page,picture]=await Promise.allSettled([call('page',{url:p.url},options),call('image',{url:image.url},options)]);
  const data=picture.status==='fulfilled'?picture.value:null;if(!data?.image_data)return null;
  let document=page.status==='fulfilled'?page.value:null,referenceUrl=p.url,title=p.pageTitle;
  const link=list(document?.image_links).find(l=>imageKey(l.image_url)===imageKey(image.url)&&url(l.url)&&url(l.url)!==url(p.url));
  if(link){
   try{const detail=await call('page',{url:link.url},options);if(detail.text&&list(detail.images).some(u=>imageKey(u)===imageKey(image.url))){document=detail;referenceUrl=detail.url||link.url;title=detail.title||link.title;}}
   catch(_){} // Discovery can continue through the text query if a detail page is unavailable.
  }
  // Collection text must not be paired with an arbitrary product image on that page.
  if(document?.text&&!document.is_collection&&!titleSupported(p.pageTitle,document.text))return null;
  const text=document?.is_collection?(link?.title||''):document?.text||p.pageTitle;
  if(!text)return null;
  return {id:'ref'+(i+1),url:referenceUrl,title,text:text.slice(0,5000),text_origin:document?.is_collection?'linked_image_title':document?.text?'retrieved_page':'google_indexed_title',image_url:image.url,image_data:data.image_data};
 }));
 const refs=results.filter(r=>r.status==='fulfilled'&&r.value).map(r=>r.value);
 // Global image matches have no attributed page. Retain them for visual discovery, without inventing one.
 const direct=[...list(found.fullMatchingImages),...list(found.partialMatchingImages),...(!refs.length&&!list(found.fullMatchingImages).length&&!list(found.partialMatchingImages).length?list(found.visuallySimilarImages):[])].filter((item,i,all)=>url(item.url)&&FlipCheckVisual.referenceImageUseful(item.url)&&all.findIndex(x=>imageKey(x.url)===imageKey(item.url))===i&&!refs.some(r=>imageKey(r.image_url)===imageKey(item.url))).slice(0,3-refs.length);
 const downloads=await Promise.allSettled(direct.map(async(item,i)=>{const picture=await call('image',{url:item.url},options);return picture.image_data?{id:'ref'+(pages.length+i+1),url:item.url,title:'Immagine trovata da Google · pagina non attribuita',text:'',text_origin:'unattributed_image',discovery_only:true,image_url:item.url,image_data:picture.image_data}:null;}));
 for(const download of downloads)if(download.status==='fulfilled'&&download.value)refs.push(download.value);
 const attempts=pages.length+direct.length;
 return {...found,references:refs,referenceAttempts:attempts,unattributedImageAttempts:direct.length,referenceState:!attempts?'no_linked_images':refs.length?'retrieved':'downloads_unavailable',state:attempts&&!refs.length?'references_unavailable':'ok'};
}
function titleSupported(title,text){
 const words=String(title||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().match(/[a-z0-9]{4,}/g)||[];
 const useful=[...new Set(words)].filter(w=>!['https','www','html','shop','ebay','sale','buy','item','card','holo','rare','with','and','the','product','catalogue','entry'].includes(w));
 const body=String(text||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
 return !useful.length||useful.some(w=>body.includes(w));
}
async function catalogueReferences(sources,options,base){
 const pages=list(sources).filter(s=>url(s.url)).slice(0,6),refs=[],attempts=[];
 const terms=[base?.category,...FlipCheckVisual.evidence(base||{}).map(c=>c.text)].filter(Boolean).slice(0,12);
 async function retrieve(s,i){
  const attempt={url:s.url,state:'requested'};attempts.push(attempt);
  try{
   const page=await call('page',{url:s.url,terms},options);attempt.status=page.status||0;
   if(page.status!==200){attempt.state='page_unavailable';return null;}
   if(page.document_type==='pdf'&&page.image_data){
    const text=(page.text||[s.title,s.text||s.snippet].filter(Boolean).join(' ')).slice(0,5000);
    attempt.state='retrieved_pdf';attempt.pages_rendered=page.pages_rendered;attempt.page_selection=page.page_selection;
    if(!text)return null;
    if(page.page_images?.length)return page.page_images.slice(0,3).map(p=>({id:'ref'+(i+1)+'p'+p.page_number,url:s.url,title:s.title,text:p.text||text,text_origin:p.text?'retrieved_pdf_pages':'web_indexed_document',document_context:page.document_context||'',pages_rendered:[p.page_number],page_count:page.page_count,page_selection:page.page_selection,image_url:s.url+'#page='+p.page_number,image_data:p.image_data}));
    return {id:'ref'+(i+1),url:s.url,title:s.title,text,text_origin:page.text?'retrieved_pdf_pages':'web_indexed_document',pages_rendered:page.pages_rendered,page_count:page.page_count,page_selection:page.page_selection,image_url:s.url,image_data:page.image_data};
   }
   if(!page.text||page.is_collection){attempt.state=page.is_collection?'collection_page':'no_page_text';return null;}
   if(!titleSupported(s.title,page.text)){attempt.state='page_content_mismatch';return null;}
   const images=list(page.images).map(url).filter(u=>u&&FlipCheckVisual.referenceImageUseful(u)).slice(0,2);
   const pictures=[];
   for(const image of images){
    const picture=await call('image',{url:image},options);if(!picture.image_data)continue;
    const detail=list(page.image_details).find(d=>imageKey(d.image_url)===imageKey(image));
    pictures.push({id:'ref'+(i+1)+'i'+pictures.length,url:page.url||s.url,title:page.title||s.title,text:page.text.slice(0,5000),text_origin:'retrieved_page',image_caption:detail?.caption||'',image_url:image,image_data:picture.image_data});
   }
   if(pictures.length){attempt.state='retrieved_image';return pictures;}
   attempt.state=images.length?'image_unavailable':'no_linked_images';return null;
  }catch(error){attempt.state=options?.signal?.aborted?'retrieval_timeout':'download_unavailable';return null;}
 }
 // A bounded second group recovers other domains when the first pages contain no useful image.
 for(let offset=0;offset<pages.length&&refs.length<3&&!options?.signal?.aborted;offset+=3){
  const results=await Promise.allSettled(pages.slice(offset,offset+3).map((s,i)=>retrieve(s,offset+i)));
  for(const result of results)if(result.status==='fulfilled'&&result.value)for(const ref of Array.isArray(result.value)?result.value:[result.value])if(!refs.some(r=>imageKey(r.image_url)===imageKey(ref.image_url)))refs.push(ref);
 }
 return {state:'ok',references:refs.slice(0,6),referenceAttempts:attempts.length,attempts,referenceState:refs.length?'retrieved':'no_accessible_page_images'};
}

function imageKey(value){try{const u=new URL(value);for(const key of ['width','height','w','h','quality'])u.searchParams.delete(key);return u.href;}catch(_){return '';}}
root.FlipCheckDirect={call,receive,normalize,references,catalogueReferences,url};
})(typeof window==='undefined'?globalThis:window);
