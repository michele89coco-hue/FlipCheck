package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Builds identity-only queries from typed facts and parses grounded candidates. */
final class CandidateRetrieverV2 {
    private CandidateRetrieverV2() {}

    static String prompt(DomainProfileRouterV2.Profile profile,ImmutableEvidenceLedgerV2 ledger,List<IdentityCandidateV2> hypotheses){
        StringBuilder observed=new StringBuilder(),inferred=new StringBuilder();
        for(EvidenceAtom a:ledger.all()){
            if(a.parentEvidenceId.isEmpty()&&hasNormalizedChild(ledger,a.id))continue;
            if(!queryField(a.field)||a.semanticScope.equals("OBJECT_STATISTIC")||a.semanticScope.equals("UI_OVERLAY")||a.semanticScope.equals("MARKET_TEXT"))continue;
            String item=a.field+"="+a.normalizedValue;
            if(a.epistemicLevel==EvidenceAtom.EpistemicLevel.OBSERVED&&a.localized())append(observed,item);
            else if(a.epistemicLevel==EvidenceAtom.EpistemicLevel.INFERRED)append(inferred,item);
        }
        StringBuilder alternatives=new StringBuilder();if(hypotheses!=null)for(IdentityCandidateV2 h:hypotheses)if(!h.fields.isEmpty())append(alternatives,h.display());
        return "PROFILE="+DomainProfileRouterV2.categoryKey(profile)
                +"\nLOCALIZED_OBSERVED_FACTS="+clip(observed.toString(),1500)
                +"\nINFERRED_LEADS_NON_BINDING="+clip(inferred.toString(),500)
                +"\nALTERNATIVE_HYPOTHESES_TO_PROVE_OR_DISPROVE="+clip(alternatives.toString(),500)
                +"\nCreate a compact query ladder: core tuple, then one discriminator. Do not include statistics, UI text, prices or marketplace wording. "
                +"For remote controls use the combined stable button labels and topology; for cards require checklist number agreement; for sealed products keep manufacturer, line, season and printed configuration separate from format/SKU.";
    }

    static List<IdentityCandidateV2> parse(JSONObject payload,DomainProfileRouterV2.Profile profile,ImmutableEvidenceLedgerV2 ledger){List<IdentityCandidateV2>out=new ArrayList<>();if(payload==null)return out;JSONArray rows=payload.optJSONArray("candidates");if(rows==null)return out;
        for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);if(x==null)continue;IdentityCandidateV2 c=new IdentityCandidateV2("WEB-"+(i+1),profile,"WEB_IDENTITY");c.retrieved=true;c.sourceUrl=safe(x.optString("source_url",""));c.webSourceQuality=x.optInt("source_quality",0);c.exactReference=x.optBoolean("exact_reference",false);c.disproofPassed=x.optBoolean("disproof_passed",false);c.layoutMatch=x.optInt("layout_match",0);
            put(c,"manufacturer",x.optString("brand",""));put(c,"productLine",x.optString("product_line",""));put(c,"setName",x.optString("set_name",""));put(c,"model",x.optString("model",""));put(c,"productReleaseYear",x.optString("year",""));
            if(profile==DomainProfileRouterV2.Profile.TCG_CARD)put(c,"cardName",x.optString("subject",""));else if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD)put(c,"athlete",x.optString("subject",""));put(c,profile==DomainProfileRouterV2.Profile.TCG_CARD?"catalogCardNumber":"catalogCardNumber",x.optString("card_number",""));put(c,"language",x.optString("language",""));put(c,"edition",x.optString("edition",""));put(c,"finish",x.optString("finish",""));put(c,"commercialFormat",x.optString("format",""));put(c,"configuration",x.optString("configuration",""));put(c,"productCode",x.optString("product_code",""));put(c,"barcode",x.optString("barcode",""));put(c,"productType",x.optString("category",""));
            strings(x.optJSONArray("matched_observed_fields"),c.matchedEvidence);strings(x.optJSONArray("contradicted_observed_fields"),c.trueConflicts);strings(x.optJSONArray("unknown_fields"),c.unknownFields);
            for(String field:c.fields.keySet()){String value=c.value(field);if(value.isEmpty()||c.sourceUrl.isEmpty())continue;ledger.append(field,value,EvidenceAtom.EpistemicLevel.RETRIEVED,EvidenceAtom.Modality.WEB_CATALOG,x.optString("source_authority","web"),-1,"","","",field,c.webSourceQuality,c.webSourceQuality,"identity_retrieval",c.sourceUrl);}
            out.add(c);}
        return out;}

    static List<String> queries(JSONObject payload){List<String>out=new ArrayList<>();if(payload!=null)strings(payload.optJSONArray("queries"),out);return out;}
    private static boolean hasNormalizedChild(ImmutableEvidenceLedgerV2 ledger,String id){for(EvidenceAtom x:ledger.all())if(x.parentEvidenceId.equals(id))return true;return false;}
    private static boolean queryField(String f){String x=safe(f);return x.matches("manufacturer|brand|game|productLine|setName|cardName|athlete|physicalCardNumber|collectorNumber|productReleaseYear|language|edition|finish|configuration|commercialFormat|model|productCode|sku|barcode|controlLayout|shortcutButtons|printedLabel|navigationLayout|numericKeypad|voiceControl|layoutSignature|sport");}
    private static void put(IdentityCandidateV2 c,String field,String value){String v=safe(value);if(!v.isEmpty()&&!TypedFieldNormalizerV2.ambiguous(v))c.fields.put(field,TypedFieldNormalizerV2.normalizeValue(field,v,""));}
    private static void strings(JSONArray a,List<String>out){if(a==null)return;for(int i=0;i<a.length();i++){String v=safe(a.optString(i,""));if(!v.isEmpty())out.add(v);}}
    private static void append(StringBuilder b,String v){if(b.length()>0)b.append(" | ");b.append(v);}
    private static String clip(String v,int n){return v.length()<=n?v:v.substring(0,n);}
    private static String safe(String v){return v==null?"":v.trim();}
}
