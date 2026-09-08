/* Slab identity: one initial reading, official certificate first, label fallback.
 * No model, search or image-comparison calls belong to this module.
 */
(function(root){
'use strict';
const clean=v=>String(v??'').normalize('NFKC').trim();
const key=v=>clean(v).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^\p{L}\p{N}]+/gu,'');
const list=v=>Array.isArray(v)?v:[];
const copied=(label,value)=>!!key(value)&&key(label).includes(key(value));
function grader(value){
 const v=key(value);
 if(/^(psa|professionalsportsauthenticator)$/.test(v))return 'PSA';
 if(/^(bgs|beckett|beckettgradingservices)$/.test(v))return 'BGS';
 if(/^(cgc|cgccards|cgcgrading)$/.test(v))return 'CGC';
 if(/^(tag|taggrading)$/.test(v))return 'TAG';
 return clean(value).toUpperCase();
}
function cardNumber(value){
 const s=clean(value).replace(/^(?:(?:CARD|CARTA)\s*)?(?:NO\.?|NUMBER|NUMERO|N[°º.]|#)\s*[:#]?\s*/i,'');
 const m=s.match(/^([A-Z]{0,5}\d+[A-Z]?(?:\s*\/\s*[A-Z]{0,5}\d+[A-Z]?)?|[A-Z0-9]{1,8}-[A-Z0-9]{1,8})(?=$|\s)/i);
 return m?m[1].replace(/\s/g,''):'';
}
function isSlab(base){return base?.slab_reading?.present===true&&!!clean(base.slab_reading.grader);}
function labelFacts(base){
 if(!isSlab(base))return null;
 const p=base.slab_reading,label=clean(p.label_text),out={...p,grader:grader(p.grader),label_text:label};
 for(const field of ['subject','family','model','year','variant'])out[field]=copied(label,p[field])?clean(p[field]):'';
 const rawNumber=cardNumber(p.card_number)||cardNumber(label.match(/(?:^|\s)(#[A-Z0-9][^;\n]*)/i)?.[1]);
 out.card_number=copied(label,rawNumber)?rawNumber:'';
 const variants=clean(p.variant).split(/[,;|·]+/).map(clean).filter(Boolean);
 out.variant=variants.length&&variants.every(v=>copied(label,v))?clean(p.variant):out.variant;
 out.grade=clean(p.grade); // Separate field: never derive a grade from arbitrary label digits.
 out.certificate=clean(p.certificate).replace(/\s/g,''); // Preserve leading zeroes; do not guess O/0.
 out.language=clean(p.language)||clean(base.pokemon_printing?.language);
 out.complete=p.certainty==='clear'&&!!label&&!!(out.subject||out.model)&&!!out.family&&/^(?:19|20)\d{2}(?:[-/]\d{2,4})?$/.test(out.year);
 return out;
}
function certificatePlan(facts){
 if(!facts)return {state:'not_a_slab'};
 const g=grader(facts.grader),cert=clean(facts.certificate),numeric=/^\d{6,14}$/.test(cert);
 let url='';
 if(g==='PSA'&&numeric)url='https://www.psacard.com/cert/'+cert+'/psa';
 if(['BGS','BVG','BCCG'].includes(g)&&numeric)url='https://www.beckett.com/grading/card-lookup?item_id='+cert+'&item_type='+g;
 if(g==='CGC'&&numeric)url='https://www.cgccards.com/certlookup/'+cert+'/';
 if(g==='TAG'&&/^[A-Z0-9]{6,20}$/i.test(cert))url='https://my.taggrading.com/card/'+encodeURIComponent(cert);
 return {state:url?'ready':cert?'unsupported_or_unreadable':'number_unreadable',grader:g,certificate:cert,url};
}
const fieldNames={
 certificate:['certificate','certificatenumber','cert','certnumber','certificationnumber','certification','serialnumber','certno'],
 subject:['subject','player','playername','cardname','name'],family:['brandtitle','setname','set','brand'],
 year:['year','yearissued'],card_number:['cardnumber','cardno','card'],variant:['variety','variation','variant','parallel'],
 grade:['itemgrade','grade','overallgrade','finalgrade'],language:['language'],description:['description','carddescription','cardtitle']
};
function officialRecord(plan,page,facts){
 if(plan?.state!=='ready'||page?.status!==200)return {state:page?.status?'http_'+page.status:page?.state||'unavailable'};
 let received,expected;
 try{received=new URL(page.url||plan.url);expected=new URL(plan.url);}catch(_){return {state:'invalid_source'};}
 const allowed=plan.grader==='CGC'?['www.cgccards.com','cgccards.com','www.cgcgrading.com','cgcgrading.com']:[expected.hostname];
 if(received.protocol!=='https:'||!allowed.includes(received.hostname))return {state:'wrong_provider'};
 const values={};
 for(const f of list(page.structured_fields)){
  const name=Object.keys(fieldNames).find(n=>fieldNames[n].includes(key(f.label)));
  if(name&&clean(f.value)&&!values[name])values[name]=clean(f.value);
 }
 const lines=clean(page.text).split(/\n+/).map(clean).filter(Boolean);
 for(let i=0;i<lines.length;i++){
  const pair=lines[i].match(/^([^:]{2,35}):\s*(.*)$/),name=Object.keys(fieldNames).find(n=>fieldNames[n].includes(key(pair?pair[1]:lines[i])));
  if(name&&!values[name])values[name]=clean(pair?.[2]||lines[i+1]);
 }
 // A generic form, page title, requested URL or echoed input is not a returned record.
 if(values.certificate?.replace(/[\s#]/g,'')!==plan.certificate)return {state:'record_not_returned'};
 const title=values.description||'',subject=values.subject||(copied(title,facts.subject)?facts.subject:''),year=values.year||(title.match(/\b(?:19|20)\d{2}(?:-\d{2,4})?\b/)||[])[0],family=values.family||(copied(title,facts.family)?facts.family:'');
 if(!subject||!year||!family)return {state:'incomplete_record'};
 // Protect against a mistyped digit resolving to another valid certificate. No re-vision.
 if(facts.subject&&!copied(subject,facts.subject)&&!copied(facts.subject,subject))return {state:'different_record'};
 if(facts.year&&key(year)!==key(facts.year))return {state:'different_record'};
 if(facts.family&&!copied(family,facts.family)&&!copied(facts.family,family))return {state:'different_record'};
 const number=cardNumber(values.card_number);
 if(facts.card_number&&number&&key(number)!==key(facts.card_number)&&key(number.split('/')[0])!==key(facts.card_number))return {state:'different_record'};
 const official_fields=['subject','year','family',...(number?['card_number']:[]),...['variant','grade','language'].filter(k=>values[k])];
 return {state:'verified',source:received.href,official_fields,fields:{subject,year,family,card_number:number||facts.card_number,variant:values.variant||facts.variant,grade:values.grade||facts.grade,language:values.language||facts.language},raw_fields:values,title:title||[year,family,number?'#'+number:'',subject,values.variant].filter(Boolean).join(' · ')};
}
function close(base,facts,record={state:'not_attempted'}){
 if(!facts||(!facts.complete&&record.state!=='verified'))return {...base,market_ready:false,model_verified:false,normalized_query:'',identity_status:'unresolved',exact_identity_status:'unresolved',assistance_state:'physical_detail_needed',core_identity:{status:'partial',pending_fields:['slab_label']},slab_verification:{state:'label_incomplete',certificate_verified:false,lookup_state:record.state},missing_information:['Titolo dell’etichetta non leggibile per intero'],next_photo_request:'Fotografa da vicino l’intera etichetta della slab, mantenendo nitidi nome, serie e anno.'};
 const verified=record.state==='verified',data={...facts,...(verified?record.fields:{})},origin=verified?'official_certificate':'photo_slab_label';
 const model=[data.year,data.family,data.card_number?'#'+data.card_number:'',data.subject||data.model].filter(Boolean).join(' · ');
 const grading=[data.grader,data.grade].filter(Boolean).join(' '),title=[model,data.language,data.variant,grading].filter(Boolean).join(' · ');
 const fieldOrigin=k=>verified&&record.official_fields.includes(k)?'official_certificate':'photo_slab_label';
 const fields=['year','family','subject','card_number','variant','language','grader','grade'].filter(k=>data[k]).map(k=>({field:k==='card_number'?'catalog_number':k,value:data[k],origin:fieldOrigin(k),quote:fieldOrigin(k)==='official_certificate'?record.raw_fields[k]||record.title:data.label_text,image_index:data.image_index,...(fieldOrigin(k)==='official_certificate'?{source:record.source}:{})}));
 const result={...base,title,model:title,family:data.family,variant:data.variant||'',condition:grading,language:data.language||'',grader:data.grader,grade:data.grade,slab_reading:{...data,complete:undefined},
  card_identity:{manufacturer:base.brand||null,subject:data.subject||data.model,year:data.year,set:data.family,number:data.card_number||null,language:data.language||null,variant:data.variant||null},
  grading:{company:data.grader,grade:data.grade||null,certificate:data.certificate||null,certificate_verified:verified,source:verified?record.source:null,origin:fieldOrigin('grade')},
  core_identity:{status:'confirmed',model:title,origin,fields,pending_fields:[]},identity_basis:{family:origin,variant:origin},
  identity_status:'confirmed',exact_identity_status:'confirmed',status:'identified',model_verified:true,market_ready:true,model_confidence:base.model_confidence,family_confidence:base.family_confidence,family_verified:true,
  catalogue_core_verified:verified,catalogue_verified:verified,catalogue_needs_verification:false,variant_check:'confirmed',variant_needs_verification:false,unresolved_identity_fields:[],missing_information:[],next_photo_request:null,assistance_state:'confirmed',
  candidate_models:[],candidate_checks:[],visual_candidates:[],normalized_query:title,identity_locked:true,
  slab_verification:{state:'confirmed',origin,scope:'label_description',certificate_verified:verified,lookup_state:record.state,source:verified?record.source:null,label_text:data.label_text},
  verification_summary:verified?'Identità acquisita dalla scheda ufficiale del certificato.':'Identità acquisita dall’etichetta della slab.',
  identification_sources:verified?[{title:'Certificato '+data.grader+' '+data.certificate,url:record.source}]:[],
  identity_keys:{subject:{value:data.subject||data.model,origin:fieldOrigin('subject')},number:{value:data.card_number||null,origin:fieldOrigin('card_number')},date:{value:data.year,kind:'year',origin:fieldOrigin('year')},origin,status:'confirmed'},
  catalogue_data:fields.filter(f=>f.origin==='official_certificate'),observed_identifiers:[],identifier_variants:[],search_mode:'slab',primary_identifier:data.card_number||''};
 for(const k of ['printing_check','variant_proof','printing_resolution','identity_evidence','identity_target_conflicts','catalogue_key_evidence','variant_hypotheses','observed_year'])delete result[k];
 return result;
}
const api={grader,cardNumber,isSlab,labelFacts,certificatePlan,officialRecord,close};
if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckSlab=api;
})(typeof window!=='undefined'?window:globalThis);
