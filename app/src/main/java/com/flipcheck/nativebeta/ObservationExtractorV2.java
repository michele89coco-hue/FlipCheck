package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Parses Vision into located observations plus separate, non-binding hypotheses. */
final class ObservationExtractorV2 {
    static final class Result {String category="";boolean contentSufficient;final List<String> views=new ArrayList<>();final List<IdentityCandidateV2> hypotheses=new ArrayList<>();}
    private ObservationExtractorV2() {}

    static Result ingestPrimary(JSONObject payload,ImmutableEvidenceLedgerV2 ledger){
        Result out=new Result();if(payload==null)return out;
        out.category=safe(payload.optString("category","generic_object"));out.contentSufficient=payload.optBoolean("content_sufficient",false);
        strings(payload.optJSONArray("views"),out.views);
        DomainProfileRouterV2.Profile tentative=DomainProfileRouterV2.route(out.category,ledger);
        ingestFacts(payload.optJSONArray("facts"),ledger,EvidenceAtom.Modality.PRIMARY_VISION,"primary_vision",tentative);
        ingestCandidates(payload.optJSONArray("candidates"),out.hypotheses,tentative,"PRIMARY_VISION_HYPOTHESIS",ledger);
        return out;
    }

    static Result ingestFocused(JSONObject payload,ImmutableEvidenceLedgerV2 ledger,DomainProfileRouterV2.Profile profile,String cropId){
        Result out=new Result();out.category=DomainProfileRouterV2.categoryKey(profile);if(payload==null)return out;
        out.contentSufficient=payload.optBoolean("content_sufficient",payload.optBoolean("applicable",false));
        strings(payload.optJSONArray("views"),out.views);
        JSONArray facts=payload.optJSONArray("facts");if(facts==null)facts=payload.optJSONArray("evidence_facts");
        ingestFacts(facts,ledger,EvidenceAtom.Modality.FOCUSED_VISION,"focused_vision",profile,cropId);
        ingestCandidates(payload.optJSONArray("candidates"),out.hypotheses,profile,"FOCUSED_VISION_HYPOTHESIS",ledger);
        return out;
    }

    static void ingestLocal(Models.LocalScan local,ImmutableEvidenceLedgerV2 ledger){if(local==null)return;
        for(int image=0;image<local.textByImage.size();image++){String block=safe(local.textByImage.get(image));if(!block.isEmpty())ledger.append("printedLabel",block,EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.LOCAL_OCR,"on_device",image,"unknown","local_ocr_text_block","","object_printed_text",78,72,"local_scan","");}
        for(Models.Identifier item:local.identifiers){if(item==null)continue;String modality=safe(item.origin).toLowerCase(Locale.ROOT);
            EvidenceAtom.Modality m=modality.contains("barcode")?EvidenceAtom.Modality.BARCODE_SCAN:EvidenceAtom.Modality.LOCAL_OCR;
            String region=modality.isEmpty()?"":"local_ocr:"+modality;
            ledger.append(item.label,item.value,EvidenceAtom.EpistemicLevel.OBSERVED,m,"on_device",item.imageIndex,"unknown",region,"",item.label,confidence(item,m),75,"local_scan","");}
    }

    private static void ingestFacts(JSONArray facts,ImmutableEvidenceLedgerV2 ledger,EvidenceAtom.Modality modality,String source,DomainProfileRouterV2.Profile profile){ingestFacts(facts,ledger,modality,source,profile,"");}
    private static void ingestFacts(JSONArray facts,ImmutableEvidenceLedgerV2 ledger,EvidenceAtom.Modality modality,String source,DomainProfileRouterV2.Profile profile,String cropId){
        if(facts==null)return;for(int i=0;i<facts.length();i++){JSONObject f=facts.optJSONObject(i);if(f==null)continue;
            String rawField=safe(first(f,"key","field")),raw=safe(first(f,"value","rawTextOrSymbol","raw_value"));
            int image=f.has("image")?f.optInt("image",-1):f.optInt("image_index",-1);String location=safe(first(f,"location","region","boundingBox"));
            String side=safe(f.optString("side","unknown")),role=safe(first(f,"role","semantic_role","semanticScope"));
            String field=TypedFieldNormalizerV2.canonicalField(profileField(rawField,role,profile),role);int confidence=f.optInt("confidence",0);
            EvidenceAtom.EpistemicLevel requested=groundingLevel(field,raw,role,modality,ledger,confidence);
            EvidenceAtom atom=ledger.append(field,raw,requested,modality,source,image,side,location,cropId,role,confidence,quality(location,raw),modality==EvidenceAtom.Modality.PRIMARY_VISION?"primary_observation":"focused_observation","");
            if(atom!=null&&atom.epistemicLevel==EvidenceAtom.EpistemicLevel.INFERRED){/* intentionally demoted */}
        }}

    private static void ingestCandidates(JSONArray candidates,List<IdentityCandidateV2> out,DomainProfileRouterV2.Profile profile,String source,ImmutableEvidenceLedgerV2 ledger){if(candidates==null)return;
        for(int i=0;i<candidates.length();i++){JSONObject c=candidates.optJSONObject(i);if(c==null)continue;IdentityCandidateV2 x=new IdentityCandidateV2(source+"-"+(i+1),profile,source);
            add(x,"manufacturer",first(c,"brand","manufacturer"));add(x,profile==DomainProfileRouterV2.Profile.TCG_CARD?"setName":"productLine",first(c,"product_line","family","set_name"));
            if(profile==DomainProfileRouterV2.Profile.TCG_CARD)add(x,"cardName",first(c,"subject","card_name"));else if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD)add(x,"athlete",first(c,"subject","athlete"));add(x,"productReleaseYear",c.optString("year",""));
            add(x,profile==DomainProfileRouterV2.Profile.TCG_CARD?"collectorNumber":"physicalCardNumber",c.optString("card_number",""));add(x,"language",c.optString("language",""));
            add(x,"edition",c.optString("edition",""));add(x,"finish",c.optString("finish",""));add(x,"commercialFormat",c.optString("format",""));add(x,"model",c.optString("model",""));
            x.inferenceOnlyPenalty=25;x.totalScore=Math.min(74,c.optInt("confidence",0));x.status="LOW_SUPPORT";out.add(x);}}

    private static EvidenceAtom.EpistemicLevel groundingLevel(String field,String raw,String role,EvidenceAtom.Modality modality,ImmutableEvidenceLedgerV2 ledger,int confidence){
        String f=safe(field),r=safe(role).toLowerCase(Locale.ROOT);
        boolean identityLabel=f.equals("manufacturer")||f.equals("brand")||f.equals("game")||f.equals("setName")||f.equals("productLine")||f.equals("subSeries");
        if(!identityLabel)return EvidenceAtom.EpistemicLevel.OBSERVED;
        boolean literalRole=r.contains("printed")||r.contains("ocr")||r.contains("brand_mark")||r.contains("visible_text")||r.contains("logo")||r.endsWith("_text")||r.equals("product_line")||r.equals("set_name")||r.equals("game_brand");
        // A set-name inferred from a symbol classification is a hypothesis; the observed item is the symbol's appearance, not the catalog set label.
        if((f.equals("setName")||f.equals("productLine"))&&r.contains("symbol"))return EvidenceAtom.EpistemicLevel.INFERRED;
        return literalRole&&confidence>=72&&localTextSupports(ledger,raw)?EvidenceAtom.EpistemicLevel.OBSERVED:EvidenceAtom.EpistemicLevel.INFERRED;
    }

    private static boolean localTextSupports(ImmutableEvidenceLedgerV2 ledger,String value){String target=canon(value);if(target.isEmpty())return false;for(EvidenceAtom a:ledger.current("printedLabel")){String hay=canon(a.rawValue);if(hay.contains(target)||tokenCoverage(hay,target)>=.75d)return true;}return false;}
    private static double tokenCoverage(String hay,String target){String[]parts=target.split(" ");int total=0,found=0;for(String p:parts){if(p.length()<2)continue;total++;if((" "+hay+" ").contains(" "+p+" "))found++;}return total==0?0d:(double)found/total;}
    private static String canon(String v){return safe(v).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static void strings(JSONArray a,List<String>out){if(a==null)return;for(int i=0;i<a.length();i++){String v=safe(a.optString(i,""));if(!v.isEmpty()&&!out.contains(v))out.add(v);}}

    private static String profileField(String field,String role,DomainProfileRouterV2.Profile profile){String k=safe(field).toLowerCase(Locale.ROOT).replace('-','_');String r=safe(role).toLowerCase(Locale.ROOT);
        if(k.equals("text")||k.equals("raw_text")||k.equals("printed_text")){
            if(r.equals("product_line"))return profile==DomainProfileRouterV2.Profile.TCG_CARD?"setName":"productLine";
            if(r.equals("series_text")||r.equals("sub_series"))return "subSeries";
            if(r.equals("release_season")||r.equals("printed_season"))return "productReleaseYear";
            if(r.equals("sealed_configuration"))return "configuration";
            // Composite branding text stays a transcription: do not invent a brand split.
            if(r.equals("product_branding"))return "printedLabel";
        }
        if((k.equals("subject")||k.equals("subject_name"))&&profile==DomainProfileRouterV2.Profile.TCG_CARD)return "cardName";
        if((k.equals("subject")||k.equals("subject_name"))&&profile==DomainProfileRouterV2.Profile.SPORTS_CARD)return "athlete";
        if((k.equals("card_number")||TypedFieldNormalizerV2.canonicalField(field,role).equals("collectorNumber"))&&profile==DomainProfileRouterV2.Profile.TCG_CARD)return "collectorNumber";
        if(k.equals("card_number")&&profile==DomainProfileRouterV2.Profile.SPORTS_CARD)return "physicalCardNumber";
        if((k.equals("year")||k.equals("season"))&&r.contains("stat"))return "statisticsSeason";
        if(k.equals("year")||k.equals("season")||k.equals("physical_set_or_release_year"))return "productReleaseYear";
        if((k.equals("family")||k.equals("product_line"))&&profile==DomainProfileRouterV2.Profile.TCG_CARD)return "setName";
        return field;}
    private static String first(JSONObject x,String...keys){for(String k:keys){String v=x.optString(k,"");if(!safe(v).isEmpty())return v;}return "";}
    private static void add(IdentityCandidateV2 c,String field,String value){String v=safe(value);if(!v.isEmpty())c.fields.put(field,TypedFieldNormalizerV2.normalizeValue(field,v,""));}
    private static int quality(String location,String value){return safe(location).isEmpty()?35:Math.min(100,65+Math.min(25,safe(value).length()));}
    private static int confidence(Models.Identifier id,EvidenceAtom.Modality modality){return modality==EvidenceAtom.Modality.BARCODE_SCAN?98:safe(id.origin).toLowerCase(Locale.ROOT).contains("labeled")?82:68;}
    private static String safe(String value){return value==null?"":value.trim();}
}
