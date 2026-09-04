package com.flipcheck.nativebeta;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Iterator;
import org.json.JSONObject;

/** Fuses descriptive aliases before margin, ambiguity or photo-request decisions. */
final class CandidateCanonicalizer {
    private CandidateCanonicalizer() {}

    static Models.CandidateScore fromJson(JSONObject x) {
        Models.CandidateScore c=new Models.CandidateScore();if(x==null)return c;
        c.brand=first(axis(x,CanonicalFieldKey.MANUFACTURER),axis(x,CanonicalFieldKey.PUBLISHER),axis(x,CanonicalFieldKey.BRAND));
        c.family=first(axis(x,CanonicalFieldKey.PRODUCT_LINE),axis(x,CanonicalFieldKey.SET),axis(x,CanonicalFieldKey.SERIES));
        c.mainSet=first(axis(x,CanonicalFieldKey.MAIN_SET),axis(x,CanonicalFieldKey.SET));
        c.subset=axis(x,CanonicalFieldKey.INSERT_SUBSET);c.designFamily=axis(x,CanonicalFieldKey.DESIGN_FAMILY);
        c.subSeries=axis(x,CanonicalFieldKey.SUB_SERIES);
        c.model=axis(x,CanonicalFieldKey.PRODUCT_NAME);c.categoryKey=clean(x.optString("category_key",""));
        c.year=axis(x,CanonicalFieldKey.PHYSICAL_SET_OR_RELEASE_YEAR);c.subject=axis(x,CanonicalFieldKey.SUBJECT);
        c.team=axis(x,CanonicalFieldKey.TEAM);c.sport=axis(x,CanonicalFieldKey.SPORT);
        c.cardNumber=first(axis(x,CanonicalFieldKey.CARD_NUMBER_CANDIDATE),axis(x,CanonicalFieldKey.COLLECTOR_NUMBER_CANDIDATE),axis(x,CanonicalFieldKey.SOURCE_CONFIRMED_CATALOG_NUMBER));
        c.language=axis(x,CanonicalFieldKey.LANGUAGE);c.edition=axis(x,CanonicalFieldKey.EDITION);
        c.printing=axis(x,CanonicalFieldKey.PRINTING);c.parallel=axis(x,CanonicalFieldKey.PHYSICAL_PARALLEL_CANDIDATE);
        c.parallelFamily=first(axis(x,CanonicalFieldKey.PARALLEL_FAMILY),c.parallel);
        c.parallelColor=axis(x,CanonicalFieldKey.PARALLEL_COLOR);c.finish=axis(x,CanonicalFieldKey.FINISH);
        c.printRun=axis(x,CanonicalFieldKey.PRINT_RUN);c.serialNumber=axis(x,CanonicalFieldKey.PHYSICAL_SERIAL_CANDIDATE);
        c.format=axis(x,CanonicalFieldKey.FORMAT);c.configuration=axis(x,CanonicalFieldKey.CONFIGURATION);
        c.hpOrPv=axis(x,CanonicalFieldKey.HP_OR_PV);c.evolutionStage=axis(x,CanonicalFieldKey.EVOLUTION_STAGE);
        c.copyrightYear=axis(x,CanonicalFieldKey.COPYRIGHT_YEAR);c.layoutSignature=axis(x,CanonicalFieldKey.LAYOUT_SIGNATURE);
        c.productType=axis(x,CanonicalFieldKey.PRODUCT_TYPE);c.productCode=axis(x,CanonicalFieldKey.MODEL_CODE);
        c.packageCount=axis(x,CanonicalFieldKey.PACKAGE_COUNT);c.cardsPerPack=axis(x,CanonicalFieldKey.CARDS_PER_PACK);
        c.autographGuarantee=axis(x,CanonicalFieldKey.AUTOGRAPH_GUARANTEE);c.memorabiliaGuarantee=axis(x,CanonicalFieldKey.MEMORABILIA_GUARANTEE);
        c.sealedStatus=axis(x,CanonicalFieldKey.SEALED_STATUS);c.attackNames.addAll(arrayAxis(x,CanonicalFieldKey.ATTACK_NAME));
        c.distinguishingTokens.addAll(arrayAxis(x,CanonicalFieldKey.DISTINCTIVE_PRINTED_TOKEN));
        c.sourceAuthority=clean(x.optString("source_authority",""));
        c.materialVariantKey=clean(x.optString("material_variant_key",""));
        c.materiallyDistinctVariant=x.optBoolean("materially_distinct_variant",x.optBoolean("materially_distinct",false));
        c.sourceUrl=clean(x.optString("source_url",""));
        c.totalScore=clamp(x.optInt("source_identity_confidence",x.optInt("confidence",0)));
        c.webScore=clamp(x.optInt("web_score",0));c.textScore=clamp(x.optInt("text_score",0));
        c.layoutScore=clamp(x.optInt("layout_score",0));c.identifierScore=clamp(x.optInt("identifier_score",0));
        c.probableReference=clean(x.optString("probable_reference",c.model));
        c.probableReferenceConfidence=clamp(x.optInt("probable_reference_confidence",c.totalScore));
        c.evidence=clean(x.optString("evidence",""));return c;
    }

    private static String axis(JSONObject x,CanonicalFieldKey wanted){if(x==null)return "";Iterator<String> keys=x.keys();while(keys.hasNext()){
        String raw=keys.next();if(CanonicalFieldKey.fromAlias(raw)==wanted){String value=clean(x.optString(raw,""));if(!value.isEmpty())return value;}}return "";}

    static int canonicalize(Models.Identification id,String stage) {
        if(id==null)return 0;
        IdentityProfileEngine.PhotoTuple t=IdentityProfileEngine.tuple(id);
        IdentityProfileEngine.Profile profile=IdentityProfileEngine.profile(id,t);
        Map<String,Models.CandidateScore> unique=new LinkedHashMap<>();int input=0,rejected=0;
        for(Models.CandidateScore c:new ArrayList<>(id.candidates)){
            if(c==null||c.hardRejected)continue;input++;
            if(physicalConflict(c,t,profile)){c.hardRejected=true;c.hardViolations.add("conflicts_with_photographic_tuple");id.rejectedCandidates.add(c);rejected++;continue;}
            String key=key(c,t,profile);c.canonicalKey=key;
            Models.CandidateScore old=null;
            for(Models.CandidateScore candidate:unique.values())if(equivalent(candidate,c,t,profile)){old=candidate;break;}
            if(old==null)unique.put(key,c);else merge(old,c);
        }
        List<Models.CandidateScore> out=new ArrayList<>(unique.values());
        Collections.sort(out,Comparator.comparingInt((Models.CandidateScore c)->c.totalScore).reversed());
        id.candidates.clear();id.candidates.addAll(out);id.canonicalCandidateCount=out.size();
        id.tournamentMargin=out.isEmpty()?0:out.size()==1?out.get(0).totalScore:
                Math.max(0,out.get(0).totalScore-out.get(1).totalScore);
        id.candidateCanonicalizationSummary="stage="+clean(stage)+"; input="+input+"; canonical="+out.size()+"; rejected_physical_conflicts="+rejected;
        if(input>0){
            id.photoAlternativeCount=out.size();
            if(out.size()<=1){id.photoIdentityAmbiguous=false;id.discriminativeField="";id.discriminativeFieldVisible=false;}
            else{id.photoIdentityAmbiguous=true;if(clean(id.discriminativeField).isEmpty())id.discriminativeField=difference(out);}
        }
        return out.size();
    }

    private static String key(Models.CandidateScore c,IdentityProfileEngine.PhotoTuple t,IdentityProfileEngine.Profile p){
        List<String> parts=new ArrayList<>();add(parts,p.name());add(parts,first(t.brand,c.brand));
        add(parts,first(t.family,c.family));
        if(p==IdentityProfileEngine.Profile.SPORTS_CARD||p==IdentityProfileEngine.Profile.TCG){
            add(parts,first(t.subject,c.subject));add(parts,t.cardNumberVerified?t.verifiedCardNumber:c.cardNumber);add(parts,first(t.year,c.year));
            add(parts,first(t.language,c.language));
            material(parts,t.edition,first(c.edition,c.printing),c);
            material(parts,t.level,c.parallel,c);material(parts,t.color,c.parallelColor,c);
            if(c.materiallyDistinctVariant)material(parts,"",c.finish,c);
        }else if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT){
            add(parts,first(t.year,c.year));add(parts,first(t.format,c.format));add(parts,first(t.configuration,c.configuration));
        }else{add(parts,first(t.modelCode,c.model));add(parts,first(t.design,c.subject));}
        if(c.materiallyDistinctVariant&&!clean(c.materialVariantKey).isEmpty())add(parts,c.materialVariantKey);
        StringBuilder b=new StringBuilder();for(String x:parts){if(b.length()>0)b.append('|');b.append(canon(x));}return b.toString();
    }
    private static void material(List<String>parts,String physical,String candidate,Models.CandidateScore c){
        if(!clean(physical).isEmpty())add(parts,physical);else if(c.materiallyDistinctVariant)add(parts,candidate);
    }
    private static boolean physicalConflict(Models.CandidateScore c,IdentityProfileEngine.PhotoTuple t,IdentityProfileEngine.Profile p){
        return identityTextConflict(t.brand,c.brand)||CatalogHierarchy.sameLevelConflict(t,c)||
                ((p==IdentityProfileEngine.Profile.SPORTS_CARD||p==IdentityProfileEngine.Profile.TCG)&&
                        (identityTextConflict(t.subject,c.subject)||(t.cardNumberVerified&&conflict(t.verifiedCardNumber,c.cardNumber))||conflict(t.language,c.language)||
                                conflict(t.edition,first(c.edition,c.printing))||conflict(t.parallelFamily,first(c.parallelFamily,c.parallel))))||
                false;
    }
    private static boolean equivalent(Models.CandidateScore a,Models.CandidateScore b,
                                      IdentityProfileEngine.PhotoTuple t,IdentityProfileEngine.Profile p){
        if(identityTextConflict(first(t.brand,a.brand),first(t.brand,b.brand))||identityTextConflict(first(t.family,a.family),first(t.family,b.family)))return false;
        if(p==IdentityProfileEngine.Profile.SPORTS_CARD||p==IdentityProfileEngine.Profile.TCG){
            if(identityTextConflict(first(t.subject,a.subject),first(t.subject,b.subject)))return false;
            if(t.cardNumberVerified){if(conflict(t.verifiedCardNumber,first(a.cardNumber,b.cardNumber)))return false;}
            else if(conflict(a.cardNumber,b.cardNumber))return false;
            if(conflict(first(t.language,a.language),first(t.language,b.language)))return false;
            if(materialConflict(t.edition,first(a.edition,a.printing),first(b.edition,b.printing),a,b))return false;
            if(materialConflict(t.level,a.parallel,b.parallel,a,b))return false;
            if((a.materiallyDistinctVariant||b.materiallyDistinctVariant)
                    &&conflict(a.materialVariantKey,b.materialVariantKey))return false;
            return true;
        }
        if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT)
            return true;
        return !conflict(first(t.modelCode,a.model),first(t.modelCode,b.model));
    }
    private static boolean materialConflict(String physical,String a,String b,Models.CandidateScore ca,Models.CandidateScore cb){
        if(!clean(physical).isEmpty())return false;
        return (ca.materiallyDistinctVariant||cb.materiallyDistinctVariant)&&conflict(a,b);
    }
    private static boolean conflict(String physical,String candidate){return !clean(physical).isEmpty()&&!clean(candidate).isEmpty()&&!canon(physical).equals(canon(candidate));}
    private static boolean identityTextConflict(String a,String b){if(clean(a).isEmpty()||clean(b).isEmpty())return false;String x=canon(a),y=canon(b);return !x.equals(y)&&!x.contains(y)&&!y.contains(x);}
    private static String difference(List<Models.CandidateScore>x){String[] fields={"card_number","edition","printing","parallel","language","format"};for(String f:fields){String first=value(x.get(0),f);for(int i=1;i<x.size();i++)if(!canon(first).equals(canon(value(x.get(i),f))))return f;}return "physical_variant_marker";}
    private static String value(Models.CandidateScore c,String f){if(f.equals("card_number"))return c.cardNumber;if(f.equals("edition"))return c.edition;if(f.equals("printing"))return c.printing;if(f.equals("parallel"))return c.parallel;if(f.equals("language"))return c.language;if(f.equals("format"))return c.format;return "";}
    private static void merge(Models.CandidateScore a,Models.CandidateScore b){a.totalScore=Math.max(a.totalScore,b.totalScore);a.webScore=Math.max(a.webScore,b.webScore);a.textScore=Math.max(a.textScore,b.textScore);a.layoutScore=Math.max(a.layoutScore,b.layoutScore);a.identifierScore=Math.max(a.identifierScore,b.identifierScore);for(String f:b.candidateFacts)if(!a.candidateFacts.contains(f))a.candidateFacts.add(f);for(String f:b.contradictions)if(!a.contradictions.contains(f))a.contradictions.add(f);if(!clean(b.sourceUrl).isEmpty())a.candidateFacts.add("equivalent_source="+b.sourceUrl);}
    private static void add(List<String>x,String v){if(!clean(v).isEmpty())x.add(v);}
    private static List<String> arrayAxis(JSONObject x,CanonicalFieldKey wanted){List<String> out=new ArrayList<>();if(x==null)return out;Iterator<String> keys=x.keys();while(keys.hasNext()){
        String raw=keys.next();if(CanonicalFieldKey.fromAlias(raw)!=wanted)continue;Object value=x.opt(raw);if(value instanceof org.json.JSONArray){org.json.JSONArray a=(org.json.JSONArray)value;for(int i=0;i<a.length();i++){String v=clean(a.optString(i,""));if(!v.isEmpty())out.add(v);}}else{String v=clean(String.valueOf(value));for(String p:v.split("[;|\\n]+"))if(!clean(p).isEmpty())out.add(clean(p));}}return out;}
    private static String first(String...values){for(String value:values)if(!clean(value).isEmpty())return clean(value);return "";}
    private static int clamp(int x){return Math.max(0,Math.min(100,x));}
    private static String canon(String x){return Normalizer.normalize(clean(x),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
