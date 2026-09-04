package com.flipcheck.nativebeta;

import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Merges an unbiased, orientation-aware number transcription into photo evidence. */
final class NumberConflictResolver {
    private NumberConflictResolver() {}
    static boolean apply(Models.Identification id,OpenAiClient.Response response){
        if(id==null||response==null||!response.complete||response.payload==null
                ||!response.payload.optBoolean("same_card",false))return false;
        JSONArray readings=response.payload.optJSONArray("readings");if(readings==null)return false;
        Map<String,Integer> score=new LinkedHashMap<>();Map<String,JSONObject> best=new LinkedHashMap<>();
        for(int i=0;i<readings.length();i++){JSONObject r=readings.optJSONObject(i);if(r==null)continue;
            String value=clean(r.optString("value","")),location=clean(r.optString("location",""));int q=r.optInt("confidence",0);
            if(value.isEmpty()||location.isEmpty()||q<75)continue;
            score.put(value,score.containsKey(value)?score.get(value)+q:q);
            if(!best.containsKey(value)||q>best.get(value).optInt("confidence",0))best.put(value,r);
        }
        String winner="";int top=0,second=0;for(Map.Entry<String,Integer>e:score.entrySet()){
            if(e.getValue()>top){second=top;top=e.getValue();winner=e.getKey();}else second=Math.max(second,e.getValue());}
        if(winner.isEmpty()||top-second<15)return false;JSONObject r=best.get(winner);
        EvidenceLedger.addPhotoFact(id,"card_number",winner,"focused_number_ocr_rot"+r.optInt("orientation",0),
                Math.min(99,r.optInt("confidence",0)+5),r.optInt("image",0),r.optString("side",""),
                r.optString("location",""),"card_number");
        PhotographicFactNormalizer.normalize(id,"after_number_conflict_vision");PhysicalCardNumberPolicy.normalize(id);
        id.numberHypotheses+=(id.numberHypotheses.isEmpty()?"":" | ")+"focused_consensus="+winner+", score="+top+", margin="+(top-second);
        return true;
    }
    private static String clean(String x){return x==null?"":x.trim();}
}
