(function(root){'use strict';
const UNIT_USD=.0069;
const str=v=>typeof v==='string'||typeof v==='number'?String(v).trim().slice(0,500):'';
function endpoint(domain){return domain==='sports'?'sport_id':['pokemon','onepiece','tcg'].includes(domain)?'tcg_id':null;}
function state(status){return status===401?'authentication_failed':status===403?'access_denied':status===402||status===429?'quota_or_rate_limit':status===200?'ok':'service_unavailable';}
function token(value){return String(value||'').trim().replace(/^Token[ \t]+/i,'').trim();}
function errorDetails(body,secret){
 const clean=v=>{let s=typeof v==='string'?v:'';for(const key of [secret,token(secret)].filter(Boolean))s=s.split(key).join('[redacted]');return s.replace(/(?:Token|Bearer)\s+[^\s"',}]+/gi,'[redacted]').replace(/[A-Za-z0-9_./+=-]{32,}/g,'[redacted]').slice(0,800);};
 const detail=clean(body?.detail||body?.message||body?.error?.message||body?.error||body?.status?.text);
 const id=body?.status?.request_id||body?.records?.[0]?._status?.request_id;
 return {message:detail,request_id:typeof id==='string'&&/^[a-f0-9-]{36}$/i.test(id)?id:null};
}
function normalize(body,domain){
 const records=Array.isArray(body?.records)?body.records:[],entries=[];let successfulRecords=0;const reportedLanguages=[];
 for(const record of records.slice(0,1)){
  if(record._status?.code!==200)continue;successfulRecords++;
  for(const obj of (Array.isArray(record._objects)?record._objects:[]).filter(o=>o.name==='Card').slice(0,1)){
   if(str(obj._ocr?.lang))reportedLanguages.push(str(obj._ocr.lang));
   const id=obj._identification||{},matches=[id.best_match,...(Array.isArray(id.alternatives)?id.alternatives:[])].filter(Boolean).slice(0,5);
   for(const [rank,m] of matches.entries()){
    const subject=str(m.name),family=str(m.set_name||m.set),number=str(m.card_number),category=str(m.subcategory);
    if(!subject||!family||!number||domain==='pokemon'&&category&&!/pok[eé]mon/i.test(category)||domain==='onepiece'&&category&&!/one.?piece/i.test(category))continue;
    const total=str(m.out_of),source={url:'https://api.ximilar.com/collectibles/v2/'+endpoint(domain),provider:'ximilar',title:'Ximilar · '+str(m.full_name||subject),origin:'recognition_api'};
    // These are provider candidates, never photo observations or exact-edition proof.
    entries.push({subject,family,number:total&&!number.includes('/')?number+'/'+total:number,year:str(m.year),brand:str(m.company||m.brand),language:'',subset:str(m.sub_set),rarity:str(m.rarity),variants:[],source,grounded:true,source_tier:'recognition_api',entry_quote:JSON.stringify({name:subject,set:family,number,year:str(m.year)}),provider_rank:rank,provider_distance:Number.isFinite(id.distances?.[rank])?id.distances[rank]:null});
   }
  }
 }
 return {reportedLanguages,state:successfulRecords?(entries.length?'ok':'no_match'):state(records[0]?._status?.code||body?.status?.code||0),entries};
}
const api={UNIT_USD,endpoint,state,normalize,token,errorDetails};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckXimilar=api;
})(typeof window==='undefined'?globalThis:window);
