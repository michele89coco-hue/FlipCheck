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
  try{root.FlipCheckGoogle.request(id,action,JSON.stringify(payload));}catch(_){finish(null,new Error('native_service_missing'));}
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
 if(!first||first.error)return {state:first?.error?'provider_image_error':'empty_response',references:[],providerCalls:1,billingUnknown:true};
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
  const text=document?.is_collection?(link?.title||''):document?.text||p.pageTitle;
  if(!text)return null;
  return {id:'ref'+(i+1),url:referenceUrl,title,text:text.slice(0,5000),text_origin:document?.is_collection?'linked_image_title':document?.text?'retrieved_page':'google_indexed_title',image_url:image.url,image_data:data.image_data};
 }));
 const refs=results.filter(r=>r.status==='fulfilled'&&r.value).map(r=>r.value);
 return {...found,references:refs,referenceAttempts:pages.length,referenceState:!pages.length?'no_linked_images':refs.length?'retrieved':'downloads_unavailable',state:pages.length&&!refs.length?'references_unavailable':'ok'};
}
async function catalogueReferences(sources,options,base){
 const pages=list(sources).filter(s=>url(s.url)).slice(0,3);
 const results=await Promise.allSettled(pages.map(async(s,i)=>{
  const page=await call('page',{url:s.url},options);
  if(page.document_type==='pdf'&&page.image_data){const text=[s.title,s.text||s.snippet].filter(Boolean).join(' ').slice(0,5000);if(!text)return null;return {id:'ref'+(i+1),url:s.url,title:s.title,text,text_origin:'web_indexed_document',pages_rendered:page.pages_rendered,page_count:page.page_count,image_url:s.url,image_data:page.image_data};}
  if(!page.text||page.is_collection)return null;
  const image=list(page.images).map(url).find(u=>u&&FlipCheckVisual.referenceImageUseful(u));if(!image)return null;
  const picture=await call('image',{url:image},options);if(!picture.image_data)return null;
  return {id:'ref'+(i+1),url:s.url,title:page.title||s.title,text:page.text.slice(0,5000),text_origin:'retrieved_page',image_url:image,image_data:picture.image_data};
 }));
 const refs=results.filter(r=>r.status==='fulfilled'&&r.value).map(r=>r.value).filter((r,i,a)=>a.findIndex(x=>imageKey(x.image_url)===imageKey(r.image_url))===i);
 return {state:'ok',references:refs,referenceAttempts:pages.length,referenceState:refs.length?'retrieved':'no_accessible_page_images'};
}
function imageKey(value){try{const u=new URL(value);for(const key of ['width','height','w','h','quality'])u.searchParams.delete(key);return u.href;}catch(_){return '';}}
root.FlipCheckDirect={call,receive,normalize,references,catalogueReferences,url};
})(typeof window==='undefined'?globalThis:window);
