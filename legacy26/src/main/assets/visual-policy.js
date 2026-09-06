/* Optional assistance only. No catalogue answers, product tables or provider-score thresholds. */
(function(root){
'use strict';
const list=x=>Array.isArray(x)?x:[], norm=x=>String(x||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
const empty=x=>!norm(x)||/^(unknown|unresolved|none|not visible|not readable|non leggibile|non visibile|sconosciuto|null|undefined)$/.test(norm(x));
const has=(text,value)=>!empty(value)&&(' '+norm(text)+' ').includes(' '+norm(value)+' ');
const url=x=>{try{const u=new URL(x);return u.protocol==='https:'&&!u.username&&!u.password?u.href.replace(/#.*$/,''):'';}catch(_){return '';}};
function clues(base){
 const rich=list(base.photo_clues).filter(c=>!empty(c.text)&&c.certainty==='clear');
 const raw=Array.isArray(base.photo_clues)?rich:list(base.layout_signature).map(c=>({text:c.term,role:'text',certainty:'clear',location:c.position}));
 return raw.filter(c=>!empty(c.text)&&!['serial','slab_certificate'].includes(c.role)&&norm(c.text).length>=4)
  .filter((c,i,a)=>a.findIndex(v=>norm(v.text)===norm(c.text))===i);
}
function plan(base,previous=[]){
 const terms=clues(base).filter(c=>!/^(pokemon|card|carta|holo|box|sealed|nintendo|topps|panini|on off|settings|home)$/i.test(norm(c.text))).map(c=>c.text);
 const query=terms.slice(0,5).join(' ').slice(0,400);
 return {query,useful:terms.some(t=>norm(t).split(' ').length>=2)||terms.length>=2,duplicate:previous.some(q=>norm(q)===norm(query)),terms};
}
const ready=x=>!!x?.market_ready&&!!x.normalized_query&&Number(x.model_confidence)>=(x.kind==='card'?90:85)&&x.printing_check?.complete!==false;
function canonical(c){let subject=' '+norm(c.model)+' ';for(const value of [c.brand,c.family,c.year,c.issue_number,c.catalog_number].filter(Boolean))subject=subject.replace(' '+norm(value)+' ',' ');subject=subject.trim().split(/\s+/).sort().join(' ');return [c.category,c.brand,c.family,subject,c.year,c.issue_number,c.catalog_number,c.unit,c.variant].map(norm).join('|');}
function mergeCandidates(candidates){
 const map=new Map();for(const c of list(candidates)){const key=canonical(c);if(!map.has(key)){map.set(key,{...c,matches:[...list(c.matches)],fields:[...list(c.fields)],conflicts:[...list(c.conflicts)]});continue;}
 const x=map.get(key);for(const name of ['matches','fields','conflicts'])x[name]=[...new Map([...x[name],...list(c[name])].map(v=>[JSON.stringify(v),v])).values()];
 x.physical_ambiguity=x.physical_ambiguity||c.physical_ambiguity;x.same_unit=x.same_unit&&c.same_unit;
 }return [...map.values()];
}
function validate(base,reply,references){
 if(ready(base))return base;
 const refs=list(references).filter(r=>url(r.url));
 const candidates=mergeCandidates(reply?.candidates).map(c=>{
  const matches=list(c.matches).filter(m=>{
   const ref=refs.find(r=>r.id===m.reference_id);return ref?.image_data&&m.agrees===true&&m.photo_detail&&m.reference_detail&&['layout','text','shape','subject','code','configuration'].includes(m.feature);
  });
  const fields=list(c.fields).filter(f=>{
   const ref=refs.find(r=>r.id===f.reference_id);return ref&&['model','family','brand','year','issue_number','catalog_number'].includes(f.field)&&!empty(f.value)&&f.quote?.length>=8&&has(ref.text,f.quote)&&has(f.quote,f.value);
  });
  const imageSources=[...new Set(matches.map(m=>m.reference_id))];
  const featureKinds=[...new Set(matches.map(m=>m.feature))];
  const named=fields.some(f=>f.field==='model'&&norm(c.model)===norm(f.value));
  const unit=base.object_unit||'unknown';
  const sameUnit=c.same_unit===true&&(unit==='unknown'||unit===c.unit);
  const explicitIds=list(base.photo_clues).filter(x=>x.certainty==='clear'&&['model','collector_number','barcode'].includes(x.role));
  const codesMatch=explicitIds.every(o=>matches.some(m=>m.feature==='code'&&has(m.photo_detail,o.text)&&has(m.reference_detail,o.text)));
  const accepted=c.decision==='match'&&sameUnit&&named&&featureKinds.length>=2&&imageSources.length>0&&codesMatch&&!c.physical_ambiguity&&!list(c.conflicts).length;
  return {...c,matches,fields,accepted,rejection:accepted?'':!sameUnit?'unit_mismatch':!named?'catalogue_not_cited':!codesMatch?'physical_identifier_not_matched':c.physical_ambiguity?'physical_ambiguity':list(c.conflicts).length?'contradiction':'insufficient_visual_comparison'};
 });
 const selected=candidates.filter(c=>c.accepted);
 if(selected.length!==1)return {...base,visual_candidates:candidates,assistance_state:selected.length>1?'ambiguous':reply?.physical_detail_needed?'physical_detail_needed':'unidentified',next_photo_request:reply?.physical_detail_needed||null};
 const c=selected[0],fields=c.fields.map(f=>({...f,origin:'catalogue',source:refs.find(r=>r.id===f.reference_id).url}));
 const value=name=>fields.find(f=>f.field===name)?.value||'';
 const sources=refs.filter(r=>c.matches.some(m=>m.reference_id===r.id)||fields.some(f=>f.reference_id===r.id)).map(r=>({title:r.title||r.url,url:r.url,image_url:r.image_url}));
 return {...base,status:'identified',market_ready:true,model_verified:true,model_confidence:95,model:c.model,title:c.model,brand:value('brand')||base.brand,family:value('family')||base.family,
  // Scores here retain the v26 renderer contract; they are not Google probabilities.
  family_confidence:Math.max(90,base.family_confidence||0),family_mode:false,normalized_query:[c.model,value('year'),c.unit,base.variant,base.pokemon_printing?.language].filter(Boolean).join(' '),
  source_confirmed_catalog_number:value('catalog_number'),source_confirmed_issue_number:value('issue_number'),source_confirmed_year:value('year'),
  observed_data:base.photo_clues||clues(base),catalogue_data:fields,identification_sources:sources,visual_candidates:candidates,assistance_state:'confirmed',
  verification_summary:'Identità verificata confrontando foto e riferimenti catalografici. Autenticità e condizioni non certificate.',missing_information:[],next_photo_request:null};
}
class Budget {
 constructor({maxEur=.025,usdPerEur=1,deadlineMs=90000,now=()=>Date.now()}={}){this.maxUsd=maxEur*usdPerEur;this.now=now;this.deadline=now()+deadlineMs;this.entries=[];this.textCalls=0;this.visualCalls=0;this.cancelled=false;}
 spent(){return this.entries.reduce((n,e)=>n+(e.actualUsd??e.reservedUsd),0);}
 reserve(kind,amount){if(this.cancelled)throw new Error('scan_cancelled');if(this.now()>=this.deadline)throw new Error('scan_timeout');if(!Number.isFinite(amount)||amount<0||this.spent()+amount>this.maxUsd+1e-9)throw new Error('budget_exhausted');
  if(kind==='text'&&this.textCalls>=2||kind==='visual'&&this.visualCalls>=1)throw new Error('call_limit');
  if(kind==='text')this.textCalls++;if(kind==='visual')this.visualCalls++;
  const e={kind,reservedUsd:amount,actualUsd:null,status:'attempted'};this.entries.push(e);return e;
 }
 settle(e,cost){if(Number.isFinite(cost)&&cost>=0){e.actualUsd=cost;e.status='usage_returned';}else e.status='billing_unknown';}
}
const str={type:'string'},strings={type:'array',items:str};
const schema={type:'object',additionalProperties:false,properties:{physical_detail_needed:{type:['string','null']},candidates:{type:'array',maxItems:3,items:{type:'object',additionalProperties:false,properties:{category:str,brand:str,family:str,model:str,year:str,issue_number:str,catalog_number:str,unit:{type:'string',enum:['single','panel','box','object','unknown']},variant:str,decision:{type:'string',enum:['match','possible','different']},same_unit:{type:'boolean'},physical_ambiguity:{type:'boolean'},conflicts:strings,matches:{type:'array',maxItems:8,items:{type:'object',additionalProperties:false,properties:{reference_id:str,feature:{type:'string',enum:['layout','text','shape','subject','code','configuration']},photo_detail:str,reference_detail:str,agrees:{type:'boolean'}},required:['reference_id','feature','photo_detail','reference_detail','agrees']}},fields:{type:'array',maxItems:8,items:{type:'object',additionalProperties:false,properties:{field:{type:'string',enum:['model','family','brand','year','issue_number','catalog_number']},value:str,reference_id:str,quote:str},required:['field','value','reference_id','quote']}}},required:['category','brand','family','model','year','issue_number','catalog_number','unit','variant','decision','same_unit','physical_ambiguity','conflicts','matches','fields']}}},required:['physical_detail_needed','candidates']};
const api={clues,plan,ready,canonical,mergeCandidates,validate,Budget,schema,url,empty};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckVisual=api;
})(typeof window!=='undefined'?window:globalThis);
