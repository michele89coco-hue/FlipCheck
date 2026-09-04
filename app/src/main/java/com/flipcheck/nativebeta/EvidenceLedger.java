package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Append-only provenance ledger. Physical reads never consume Web-origin facts. */
final class EvidenceLedger {
    static final String PHOTO="photo", LOCAL_OCR="local_ocr", WEB_CATALOG="web_catalog",
            WEB_MARKET="web_market", USER_HINT="user_hint", INFERRED="inferred";
    private EvidenceLedger() {}

    static void ingestPhotoObservation(Models.Identification id, JSONObject photoIdentity) {
        if(id==null||photoIdentity==null)return;
        JSONArray structured=photoIdentity.optJSONArray("evidence_facts");
        if(structured!=null)for(int i=0;i<structured.length();i++){
            JSONObject x=structured.optJSONObject(i);if(x==null)continue;
            String key=key(x.optString("key",""));String value=clean(x.optString("value",""));
            if(key.isEmpty()||unresolved(value))continue;
            add(id,new Models.EvidenceFact(key,value,PHOTO,clean(x.optString("evidence_type","vision")),
                    x.optInt("confidence",id.photoIdentityConfidence),x.optInt("image_index",-1),
                    clean(x.optString("side","")),clean(x.optString("location","")),
                    clean(x.optString("semantic_role","")),""));
        }
        // Backward-compatible ingestion: still photo-origin, but with conservative
        // location/semantic metadata resolved from companion fields.
        for(String raw:id.photoIdentityFields){String k=key(raw),v=value(raw);if(k.isEmpty()||unresolved(v))continue;
            String location=companion(id,k+"_location");
            if(k.equals("physical_card_number_marking")||k.equals("physical_card_number"))location=companion(id,"card_number_location");
            String semantic=(k.equals("physical_card_number_marking")||k.equals("physical_card_number"))?companion(id,"card_number_semantic"):"";
            add(id,new Models.EvidenceFact(k,v,PHOTO,"vision_structured_field",id.photoIdentityConfidence,-1,
                    inferSide(id),location,semantic,""));
        }
    }

    static void ingestCompactPhotoObservation(Models.Identification id, JSONArray facts) {
        if(id==null||facts==null)return;
        for(int i=0;i<facts.length();i++){
            JSONObject x=facts.optJSONObject(i);if(x==null)continue;
            String k=key(x.optString("key","")),v=clean(x.optString("value",""));
            if(k.isEmpty()||unresolved(v))continue;
            add(id,new Models.EvidenceFact(k,v,PHOTO,"direct_photo_observation",
                    x.optInt("confidence",0),x.optInt("image",-1),
                    clean(x.optString("side","")),clean(x.optString("location","")),
                    clean(x.optString("role","")),""));
        }
    }

    static void addLocalOcrFact(Models.Identification id,String key,String value,int confidence,
                                int imageIndex,String location,String semanticRole){
        add(id,new Models.EvidenceFact(key(key),clean(value),LOCAL_OCR,"local_ocr_hint",
                confidence,imageIndex,"unknown",location,semanticRole,""));
    }

    static void addPhotoFact(Models.Identification id,String key,String value,String evidenceType,
                             int confidence,int imageIndex,String side,String location,String semanticRole){
        add(id,new Models.EvidenceFact(key(key),clean(value),PHOTO,evidenceType,confidence,imageIndex,
                side,location,semanticRole,""));
    }

    static void addWebCatalogFact(Models.Identification id,String key,String value,int confidence,String url){
        if(id==null||unresolved(value))return;
        add(id,new Models.EvidenceFact(key(key),clean(value),WEB_CATALOG,"source_url",confidence,-1,
                "","",catalogRole(key),clean(url)));
    }

    static void addWebMarketFact(Models.Identification id,String key,String value,int confidence,String url){
        if(id==null||unresolved(value))return;
        add(id,new Models.EvidenceFact(key(key),clean(value),WEB_MARKET,"market_source_url",confidence,-1,
                "","","market_comparable",clean(url)));
    }

    static String photoValue(Models.Identification id,String...keys){return valueFrom(id,true,keys);}
    static List<String> photoValues(Models.Identification id,String...keys){List<String>out=new ArrayList<>();if(id==null)return out;for(Models.EvidenceFact f:id.evidenceLedger){if(f==null||!PHOTO.equals(f.origin)||!matches(f.key,keys)||unresolved(f.value))continue;for(String raw:f.value.split("[,;|]")){String v=clean(raw);if(v.isEmpty())continue;boolean seen=false;for(String old:out)if(old.equalsIgnoreCase(v)){seen=true;break;}if(!seen)out.add(v);}}return out;}
    static String physicalOrUserValue(Models.Identification id,String...keys){return valueFrom(id,false,keys);}
    static Models.EvidenceFact bestPhotoFact(Models.Identification id,String...keys){
        if(id==null)return null;Models.EvidenceFact best=null;
        for(Models.EvidenceFact f:id.evidenceLedger)if(f!=null&&PHOTO.equals(f.origin)&&matches(f.key,keys)
                &&!unresolved(f.value)&&(best==null||f.confidence>best.confidence))best=f;
        return best;
    }

    static String debug(Models.EvidenceFact f){if(f==null)return "";return f.key+"="+f.value
            +" [origin="+f.origin+", type="+f.evidenceType+", confidence="+f.confidence
            +", image="+f.imageIndex+", side="+f.side+", location="+f.location
            +", semantic_role="+f.semanticRole+", text_scope="+TextScopePolicy.scope(f)+(f.sourceUrl.isEmpty()?"":", source="+f.sourceUrl)+"]";}

    private static String valueFrom(Models.Identification id,boolean photoOnly,String...keys){
        if(id==null)return "";Models.EvidenceFact best=null;
        for(Models.EvidenceFact f:id.evidenceLedger){if(f==null||!matches(f.key,keys)||unresolved(f.value))continue;
            if(photoOnly&&!PHOTO.equals(f.origin))continue;
            if(!photoOnly&&!PHOTO.equals(f.origin)&&!USER_HINT.equals(f.origin))continue;
            if(best==null||f.confidence>best.confidence)best=f;
        }return best==null?"":best.value;
    }
    private static void add(Models.Identification id,Models.EvidenceFact f){if(id==null||f==null||f.key.isEmpty()||unresolved(f.value))return;
        for(Models.EvidenceFact old:id.evidenceLedger)if(old!=null&&old.key.equals(f.key)&&old.value.equalsIgnoreCase(f.value)
                &&old.origin.equals(f.origin)&&old.location.equalsIgnoreCase(f.location)&&old.semanticRole.equalsIgnoreCase(f.semanticRole))return;
        id.evidenceLedger.add(f);
    }
    private static String companion(Models.Identification id,String wanted){for(String raw:id.photoIdentityFields)if(key(raw).equals(wanted))return value(raw);return "";}
    private static String inferSide(Models.Identification id){String views=id.photoViews.toString().toLowerCase(Locale.ROOT);boolean f=views.contains("front")||views.contains("fronte"),b=views.contains("back")||views.contains("retro")||views.contains("rear")||views.contains("reverse");return f&&b?"multiple":f?"front":b?"back":"unspecified";}
    private static String catalogRole(String key){String k=key(key);return k.contains("number")?"catalog_number":k.contains("variant")?"catalog_variant":"catalog_metadata";}
    private static boolean matches(String key,String...keys){for(String k:keys)if(key.equals(key(k)))return true;return false;}
    private static String key(String x){String v=clean(x);int p=v.indexOf('=');if(p<1)p=v.indexOf(':');if(p>0)v=v.substring(0,p);return v.toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');}
    private static String value(String x){String v=clean(x);int p=v.indexOf('=');if(p<1)p=v.indexOf(':');return p<0?"":clean(v.substring(p+1));}
    private static boolean unresolved(String x){String v=clean(x).toLowerCase(Locale.ROOT).replace('_',' ');return v.isEmpty()||v.equals("unknown")||v.equals("unresolved")||v.equals("unclear")||v.equals("not visible")||v.equals("none visible")||v.equals("non leggibile")||v.equals("none")||v.equals("n/a");}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
