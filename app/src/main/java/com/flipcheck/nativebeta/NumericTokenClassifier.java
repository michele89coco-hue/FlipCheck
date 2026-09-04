package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Contextual numeric/code classifier. Slash syntax alone never determines meaning. */
final class NumericTokenClassifier {
    enum Kind { CATALOG_CARD_NUMBER, COLLECTOR_NUMBER, PHYSICAL_SERIAL, PRINT_RUN_DENOMINATOR,
        SET_SIZE, SEASON, COPYRIGHT_YEAR, BIRTH_DATE, STATISTICS, JERSEY_NUMBER,
        ATTACK_DAMAGE, HP_OR_PV, PRODUCT_CODE, BARCODE, UNKNOWN_NUMERIC_TOKEN }
    static final class Result { final Kind kind; final String normalized; final List<String> alternatives; final String reason;
        Result(Kind kind,String normalized,List<String> alternatives,String reason){this.kind=kind;this.normalized=normalized;this.alternatives=alternatives;this.reason=reason;} }
    private NumericTokenClassifier() {}

    static Result classify(NormalizedPhotoIdentity.Fact f){
        if(f==null)return result(Kind.UNKNOWN_NUMERIC_TOKEN,"","no_fact");
        String raw=clean(f.value),norm=normalizeCode(raw),ctx=canon(f.originalKey+" "+f.semanticRole+" "+f.location+" "+f.side+" "+f.evidenceType);
        String scope=scope(f);
        if(TextScopePolicy.external(scope))return result(Kind.UNKNOWN_NUMERIC_TOKEN,norm,"external_scope="+scope);
        if(has(ctx,"BARCODE|UPC|EAN|GTIN"))return result(Kind.BARCODE,norm,"barcode_context");
        if(has(ctx,"MODEL|PRODUCT CODE|PART NUMBER|SKU| P N "))return result(Kind.PRODUCT_CODE,norm,"product_code_context");
        if(has(ctx,"HP| PV |HEALTH"))return result(Kind.HP_OR_PV,norm,"hp_context");
        if(has(ctx,"ATTACK DAMAGE|MOVE DAMAGE|DAMAGE"))return result(Kind.ATTACK_DAMAGE,norm,"attack_damage_context");
        if(has(ctx,"BIRTH|BORN|DATE OF BIRTH"))return result(Kind.BIRTH_DATE,norm,"biographical_date_context");
        if(has(ctx,"JERSEY|UNIFORM NUMBER"))return result(Kind.JERSEY_NUMBER,norm,"jersey_context");
        if(has(ctx,"STAT|RATING|OFFENSE|DEFENSE|AVERAGE|CAREER|SEASON TABLE")||TextScopePolicy.OBJECT_STATISTIC.equals(scope))return result(Kind.STATISTICS,norm,"statistics_context");
        if(has(ctx,"COPYRIGHT"))return result(Kind.COPYRIGHT_YEAR,norm,"copyright_context");
        if(has(ctx,"SEASON|RELEASE|SET YEAR|PRINTED YEAR")||looksSeason(raw))return result(Kind.SEASON,SeasonNormalizer.normalize(raw),"season_context");
        if(has(ctx,"SERIAL|NUMBERED TO|COPY NUMBER|STAMPED SERIAL")&&looksFraction(norm))return result(Kind.PHYSICAL_SERIAL,norm,"localized_serial_context");
        if(has(ctx,"PRINT RUN|DENOMINATOR|EDITION SIZE"))return result(Kind.PRINT_RUN_DENOMINATOR,denominator(norm),"print_run_context");
        if(has(ctx,"SET SIZE"))return result(Kind.SET_SIZE,norm,"set_size_context");
        boolean collector=has(ctx,"COLLECTOR|COLLECTIBLE NUMBER");
        boolean card=has(ctx,"CARD NUMBER|CHECKLIST NUMBER|CARD IDENTIFIER|LOWER CARD CODE|NUMBER CANDIDATE");
        if((collector||card)&&!hasLetters(norm)&&looksFraction(norm)&&has(ctx,"OCR LINE|UNKNOWN|UNSPECIFIED"))
            return result(Kind.UNKNOWN_NUMERIC_TOKEN,norm,"numeric_fraction_without_object_zone_binding");
        if((collector||card)&&TextScopePolicy.identifierEligible(toEvidence(f))&&plausibleIdentifier(norm))
            return new Result(collector||hasLetters(norm)?Kind.COLLECTOR_NUMBER:Kind.CATALOG_CARD_NUMBER,norm,alternatives(norm),"localized_identifier_context");
        return result(Kind.UNKNOWN_NUMERIC_TOKEN,norm,"no_reliable_semantic_and_geometric_binding");
    }

    static String normalizeCode(String value){String v=clean(value).replaceFirst("^#","").toUpperCase(Locale.ROOT);
        v=v.replaceAll("\\s*[|]\\s*","/").replaceAll("(?<=\\p{Alnum})\\s+[I1]\\s*(?=[A-Z]\\d)","/");
        if(v.matches("[A-Z]{1,5}\\d{1,6}I[A-Z]{1,5}\\d{1,6}[A-Z]?"))v=v.replaceFirst("I(?=[A-Z]\\d)","/");
        return v.replaceAll("\\s*/\\s*","/").replaceAll("\\s+-\\s*","-").replaceAll("\\s+"," ").trim();}
    static List<String> alternatives(String value){String n=normalizeCode(value);LinkedHashSet<String> out=new LinkedHashSet<>();if(!n.isEmpty())out.add(n);
        if(n.matches("[A-Z]{1,5}\\d{1,6}[I1|][A-Z]{1,5}\\d{1,6}[A-Z]?"))out.add(n.replaceFirst("[I1|]","/"));
        if(n.matches("[A-Z]{1,5}\\d{1,6}\\s+[A-Z]{1,5}\\d{1,6}[A-Z]?"))out.add(n.replaceFirst("\\s+","/"));
        if(n.matches(".*\\d[A-Z]$"))out.add(n.substring(0,n.length()-1));
        return new ArrayList<>(out);}
    private static String scope(NormalizedPhotoIdentity.Fact f){return TextScopePolicy.scope(toEvidence(f));}
    private static Models.EvidenceFact toEvidence(NormalizedPhotoIdentity.Fact f){return new Models.EvidenceFact(f.originalKey,f.value,f.origin,f.evidenceType,f.confidence,f.imageIndex,f.side,f.location,f.semanticRole,"");}
    private static Result result(Kind kind,String norm,String reason){return new Result(kind,norm,alternatives(norm),reason);}
    private static boolean plausibleIdentifier(String v){return !v.isEmpty()&&!looksSeason(v)&&v.matches("(?i)[A-Z0-9]{1,10}(?:[-/][A-Z0-9]{1,10}){0,2}");}
    private static boolean looksFraction(String v){return v.matches("\\d{1,7}/\\d{1,7}");}
    private static boolean looksSeason(String v){return clean(v).matches("(?:19|20)\\d{2}[/.\\-](?:\\d{2}|(?:19|20)\\d{2})");}
    private static boolean hasLetters(String v){return v.matches(".*[A-Z].*");}
    private static String denominator(String v){int p=v.indexOf('/');return p<0?v:v.substring(p);}
    private static boolean has(String context,String regex){return (" "+context+" ").matches(".*(?:"+regex+").*");}
    private static String canon(String x){return clean(x).toUpperCase(Locale.ROOT).replace('_',' ').replaceAll("[^A-Z0-9/]+"," ").trim();}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
