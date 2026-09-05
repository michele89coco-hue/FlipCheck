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
            if(!queryField(a.field)||a.semanticScope.equals("OBJECT_STATISTIC")||a.semanticScope.equals("UI_OVERLAY")||a.semanticScope.equals("MARKET_TEXT")||(a.modality==EvidenceAtom.Modality.LOCAL_OCR&&a.field.equals("printedLabel")&&a.rawValue.length()>80))continue;
            String item=a.field+"="+a.normalizedValue;
            if(a.epistemicLevel==EvidenceAtom.EpistemicLevel.OBSERVED&&a.localized())append(observed,item);
            else if(a.epistemicLevel==EvidenceAtom.EpistemicLevel.INFERRED)append(inferred,item);
        }
        StringBuilder alternatives=new StringBuilder();if(hypotheses!=null)for(IdentityCandidateV2 h:hypotheses)if(!h.fields.isEmpty())append(alternatives,h.display());
        boolean neutralBrand=profile==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL||DomainProfileRouterV2.electronics(profile);
        return "PROFILE="+DomainProfileRouterV2.categoryKey(profile)
                +"\nLOCALIZED_OBSERVED_FACTS="+clip(observed.toString(),1500)
                +"\nINFERRED_LEADS_NON_BINDING="+(neutralBrand?"WITHHELD_FROM_FIRST_QUERY":clip(inferred.toString(),500))
                +"\nALTERNATIVE_HYPOTHESES_TO_PROVE_OR_DISPROVE="+(neutralBrand?"GENERATE_ONLY_AFTER_NEUTRAL_RETRIEVAL":clip(alternatives.toString(),500))
                +"\nMANDATORY: query[0] must contain only localized observed facts and must exclude every inferred brand/model. A later query may test brands independently discovered by that neutral search. "
                +"Create separate candidate records for every checklist row, edition, card number, product format or device model found on the same page. Never merge fields across records or pages. "
                +"Do not include statistics, UI text, prices or marketplace wording. For remote controls return the candidate's own control_layout, shortcut_buttons, navigation_layout, numeric_keypad, voice_control and layout_signature so the app can recompute the match locally; for cards require checklist number agreement; for sealed products keep manufacturer, line, season and printed configuration separate from format/SKU.";
    }

    static List<IdentityCandidateV2> parse(JSONObject payload,DomainProfileRouterV2.Profile profile,ImmutableEvidenceLedgerV2 ledger){return parse(payload,profile,ledger,false);}
    static List<IdentityCandidateV2> parseReplay(JSONObject payload,DomainProfileRouterV2.Profile profile,ImmutableEvidenceLedgerV2 ledger){return parse(payload,profile,ledger,true);}
    private static List<IdentityCandidateV2> parse(JSONObject payload,DomainProfileRouterV2.Profile profile,ImmutableEvidenceLedgerV2 ledger,boolean legacyReplay){List<IdentityCandidateV2>out=new ArrayList<>();if(payload==null)return out;JSONArray rows=payload.optJSONArray("candidates");if(rows==null)return out;
        for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);if(x==null)continue;String candidateId=safe(x.optString("candidate_id","WEB-"+(i+1)));IdentityCandidateV2 c=new IdentityCandidateV2(candidateId,profile,"WEB_IDENTITY");c.retrieved=true;c.sourceUrl=safe(x.optString("source_url",""));c.sourceId=safe(x.optString("source_id",c.sourceUrl));c.sourceTitle=safe(x.optString("source_title",""));c.sourceAuthority=safe(x.optString("source_authority","web"));c.sourceRecordId=safe(x.optString("source_record_id",""));c.sourcePageScope=safe(x.optString("source_page_scope",""));c.identityLevel=safe(x.optString("identity_level","CORE_IDENTITY"));c.webSourceQuality=x.optInt("source_quality",0);c.exactReference=x.optBoolean("exact_reference",false);c.reportedDisproofPassed=x.optBoolean("disproof_passed",false);c.layoutMatch=x.optInt("layout_match",0);
            if(legacyReplay&&c.exactReference&&c.sourceRecordId.isEmpty())c.sourceRecordId="record-"+(i+1);if(legacyReplay&&c.exactReference&&c.sourcePageScope.isEmpty())c.sourcePageScope="CHECKLIST_ROW";
            put(c,"manufacturer",x.optString("brand",""));put(c,"productLine",x.optString("product_line",""));put(c,"setName",x.optString("set_name",""));put(c,"model",x.optString("model",""));put(c,"productReleaseYear",x.optString("year",""));
            if(profile==DomainProfileRouterV2.Profile.TCG_CARD)put(c,"cardName",x.optString("subject",""));else if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD)put(c,"athlete",x.optString("subject",""));put(c,"catalogCardNumber",x.optString("card_number",""));put(c,"language",x.optString("language",""));
            String edition=x.optString("edition","");if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD&&isBaseRole(edition))put(c,"cardRole","BASE");else put(c,"edition",edition);
            put(c,"cardRole",x.optString("card_role",""));put(c,"subSeries",x.optString("sub_series",""));put(c,"printedTotal",x.optString("printed_total",""));put(c,"setSymbol",x.optString("set_symbol",""));put(c,"copyrightYear",x.optString("copyright_year",""));put(c,"sport",x.optString("sport",""));put(c,"team",x.optString("team",""));put(c,"finish",x.optString("finish",""));put(c,"commercialFormat",x.optString("format",""));put(c,"configuration",x.optString("configuration",""));put(c,"productCode",x.optString("product_code",""));put(c,"barcode",x.optString("barcode",""));put(c,"productType",x.optString("category",""));
            put(c,"controlLayout",x.optString("control_layout",""));put(c,"shortcutButtons",x.optString("shortcut_buttons",""));put(c,"navigationLayout",x.optString("navigation_layout",""));put(c,"numericKeypad",x.optString("numeric_keypad",""));put(c,"voiceControl",x.optString("voice_control",""));put(c,"layoutSignature",x.optString("layout_signature",""));
            if(c.value("printedTotal").isEmpty()){String number=c.value("catalogCardNumber");int slash=number.indexOf('/');if(slash>0&&number.substring(slash+1).matches("\\d{1,5}"))c.fields.put("printedTotal",number.substring(slash+1));}
            strings(x.optJSONArray("matched_observed_fields"),c.reportedMatchedFields);strings(x.optJSONArray("contradicted_observed_fields"),c.reportedContradictedFields);strings(x.optJSONArray("unknown_fields"),c.unknownFields);
            out.add(c);}
        return out;}

    /** Only the selected candidate may enter the shared retrieved-evidence ledger. */
    static void commitWinner(IdentityCandidateV2 winner,ImmutableEvidenceLedgerV2 ledger){if(winner==null||winner.rejected||!winner.retrieved||!winner.disproofPassed)return;for(String field:winner.fields.keySet()){String value=winner.value(field);if(value.isEmpty()||winner.sourceUrl.isEmpty())continue;ledger.append(field,value,EvidenceAtom.EpistemicLevel.RETRIEVED,EvidenceAtom.Modality.WEB_CATALOG,winner.sourceAuthority,-1,"","","",field,winner.webSourceQuality,winner.webSourceQuality,"winning_candidate_commit",winner.sourceUrl);}}

    static List<String> queries(JSONObject payload){List<String>out=new ArrayList<>();if(payload!=null)strings(payload.optJSONArray("queries"),out);return out;}
    static String neutralQueryViolation(JSONObject payload,DomainProfileRouterV2.Profile profile,List<IdentityCandidateV2> hypotheses,ImmutableEvidenceLedgerV2 ledger){
        if(!(profile==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL||DomainProfileRouterV2.electronics(profile)))return "";
        if(ledger.hasObserved("manufacturer")||ledger.hasObserved("brand"))return "";
        List<String>qs=queries(payload);if(qs.isEmpty())return "missing_neutral_query";String first=" "+canon(qs.get(0))+" ";
        List<String>forbidden=new ArrayList<>();if(hypotheses!=null)for(IdentityCandidateV2 h:hypotheses){addUnique(forbidden,h.value("manufacturer"));addUnique(forbidden,h.value("brand"));addUnique(forbidden,h.value("model"));}
        for(EvidenceAtom a:ledger.byLevel(EvidenceAtom.EpistemicLevel.INFERRED))if(a.field.equals("manufacturer")||a.field.equals("brand")||a.field.equals("model"))addUnique(forbidden,a.normalizedValue);
        for(String value:forbidden){String token=canon(value);if(token.length()>1&&first.contains(" "+token+" "))return "query0_contains_inferred_identity="+value;}
        return "";
    }
    static void bindToolSources(List<IdentityCandidateV2> candidates,List<Models.Source> toolSources){if(candidates==null)return;for(IdentityCandidateV2 candidate:candidates){if(candidate==null||!candidate.retrieved)continue;Models.Source matched=null;if(toolSources!=null)for(Models.Source source:toolSources)if(source!=null&&sameUrl(candidate.sourceUrl,source.url)){matched=source;break;}if(matched==null){candidate.rejected=true;candidate.rejectionReason="candidate_url_not_in_web_tool_sources";candidate.disproofResult="FAILED";candidate.disproofReason=candidate.rejectionReason;}else{candidate.sourceUrl=matched.url;if(candidate.sourceTitle.isEmpty())candidate.sourceTitle=matched.title;}}}
    private static boolean hasNormalizedChild(ImmutableEvidenceLedgerV2 ledger,String id){for(EvidenceAtom x:ledger.all())if(x.parentEvidenceId.equals(id))return true;return false;}
    private static boolean queryField(String f){String x=safe(f);return x.matches("manufacturer|brand|game|productLine|subSeries|setName|cardName|athlete|physicalCardNumber|collectorNumber|productReleaseYear|language|edition|finish|configuration|commercialFormat|model|productCode|sku|barcode|controlLayout|shortcutButtons|printedLabel|navigationLayout|numericKeypad|voiceControl|layoutSignature|sport");}
    private static void put(IdentityCandidateV2 c,String field,String value){String v=safe(value);if(!v.isEmpty()&&!TypedFieldNormalizerV2.ambiguous(v))c.fields.put(field,TypedFieldNormalizerV2.normalizeValue(field,v,""));}
    private static boolean isBaseRole(String value){String x=safe(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();return x.equals("BASE")||x.equals("BASE SET")||x.equals("BASE CARD");}
    private static void strings(JSONArray a,List<String>out){if(a==null)return;for(int i=0;i<a.length();i++){String v=safe(a.optString(i,""));if(!v.isEmpty())out.add(v);}}
    private static void addUnique(List<String>out,String value){String v=safe(value);if(!v.isEmpty()&&!out.contains(v))out.add(v);}
    private static String canon(String value){return safe(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static boolean sameUrl(String a,String b){String x=urlKey(a),y=urlKey(b);return !x.isEmpty()&&x.equals(y);}
    private static String urlKey(String value){String x=safe(value).toLowerCase(Locale.ROOT).replaceFirst("^https?://(?:www\\.)?","");int query=x.indexOf('?');if(query>=0)x=x.substring(0,query);int fragment=x.indexOf('#');if(fragment>=0)x=x.substring(0,fragment);while(x.endsWith("/"))x=x.substring(0,x.length()-1);return x;}
    private static void append(StringBuilder b,String v){if(b.length()>0)b.append(" | ");b.append(v);}
    private static String clip(String v,int n){return v.length()<=n?v:v.substring(0,n);}
    private static String safe(String v){return v==null?"":v.trim();}
}
