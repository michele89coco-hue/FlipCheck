package com.flipcheck.nativebeta;

import java.util.Locale;

/** Mandatory boundary between raw Vision aliases and every identity decision. */
final class PhotographicFactNormalizer {
    private PhotographicFactNormalizer() {}

    static NormalizedPhotoIdentity normalize(Models.Identification id,String stage){
        NormalizedPhotoIdentity out=new NormalizedPhotoIdentity();if(id==null)return out;
        out.categoryHint=clean(id.categoryKey)+" "+clean(id.category);out.normalizationStage=clean(stage);
        for(Models.EvidenceFact raw:id.evidenceLedger){if(raw==null||unresolved(raw.value))continue;
            boolean photographic=EvidenceLedger.PHOTO.equals(raw.origin)||EvidenceLedger.LOCAL_OCR.equals(raw.origin)
                    ||EvidenceLedger.USER_HINT.equals(raw.origin);
            boolean sourced=EvidenceLedger.WEB_CATALOG.equals(raw.origin)||EvidenceLedger.WEB_MARKET.equals(raw.origin);
            if(!photographic&&!sourced)continue;
            if((EvidenceLedger.PHOTO.equals(raw.origin)||EvidenceLedger.LOCAL_OCR.equals(raw.origin))&&!TextScopePolicy.primaryObjectEvidence(raw)){
                addOnce(out.rejectedFacts,"external_text_excluded:"+raw.key+"(scope="+TextScopePolicy.scope(raw)+")");continue;
            }
            CanonicalFieldKey aliasKey=CanonicalFieldKey.fromAlias(raw.key);
            String normalizedValue=raw.value;
            if(aliasKey==CanonicalFieldKey.CARD_NUMBER_CANDIDATE||aliasKey==CanonicalFieldKey.COLLECTOR_NUMBER_CANDIDATE||aliasKey==CanonicalFieldKey.PHYSICAL_SERIAL_CANDIDATE){
                NormalizedPhotoIdentity.Fact probe=new NormalizedPhotoIdentity.Fact(aliasKey,raw.value,raw.key,raw.semanticRole,raw.location,raw.side,raw.evidenceType,quality(raw),raw.confidence,raw.imageIndex,raw.origin,raw.createdAtMillis,raw.timestampStage);
                NumericTokenClassifier.Result classified=NumericTokenClassifier.classify(probe);
                if(!clean(classified.normalized).isEmpty())normalizedValue=classified.normalized;
                addOnce(out.numericClassifications,raw.value+"→"+classified.kind.name()+"("+classified.reason+") alternatives="+classified.alternatives);
            }
            CanonicalFieldKey key=contextualKey(aliasKey,raw);
            if(key==CanonicalFieldKey.UNKNOWN)key=contextualKey(CanonicalFieldKey.fromAlias(raw.semanticRole),raw);
            if(key==CanonicalFieldKey.UNKNOWN){
                String prefix=aliasKey!=CanonicalFieldKey.UNKNOWN?"recognized_alias_value_rejected:":relevant(raw)?"relevant_alias_rejected:":"alias_not_mapped:";
                addOnce(out.rejectedFacts,prefix+raw.key+"(role="+raw.semanticRole+")");continue;}
            NormalizedPhotoIdentity.Quality quality=quality(raw);
            if(key==CanonicalFieldKey.ATTACK_NAME){for(String attack:raw.value.split("[;|\\n]+"))add(out,sourced,new NormalizedPhotoIdentity.Fact(key,attack,raw.key,raw.semanticRole,raw.location,raw.side,
                    raw.evidenceType,quality,raw.confidence,raw.imageIndex,raw.origin,raw.createdAtMillis,raw.timestampStage));}
            else if(key==CanonicalFieldKey.PRODUCT_LINE&&ambiguousAlternative(raw.value)){
                String[] alternatives=raw.value.split("\\s*/\\s*",3);for(String alternative:alternatives)add(out,sourced,new NormalizedPhotoIdentity.Fact(key,alternative,raw.key,raw.semanticRole,raw.location,raw.side,
                        raw.evidenceType,quality,Math.max(0,raw.confidence-5),raw.imageIndex,raw.origin,raw.createdAtMillis,raw.timestampStage));
                addOnce(out.semanticConflicts,"productLineAlternatives="+java.util.Arrays.toString(alternatives));
            }else add(out,sourced,new NormalizedPhotoIdentity.Fact(key,normalizedValue,raw.key,raw.semanticRole,raw.location,raw.side,
                    raw.evidenceType,quality,raw.confidence,raw.imageIndex,raw.origin,raw.createdAtMillis,raw.timestampStage));
            addOnce(out.aliasesConsumed,raw.key+"→"+key.debugName);
        }
        DistinctiveTokenReconstruction.apply(out);
        // The parser-validated visible brand is a summary fallback, never direct physical code evidence.
        if(out.brand().isEmpty()&&!clean(id.brand).isEmpty()&&id.brandRoleConfidence>=85){
            out.add(new NormalizedPhotoIdentity.Fact(CanonicalFieldKey.BRAND,id.brand,"parsed_visible_brand","brand",
                    "","","vision_parser_binding",NormalizedPhotoIdentity.Quality.VISION_STRUCTURED_SUMMARY,
                    id.brandRoleConfidence,-1));addOnce(out.aliasesConsumed,"parsed_visible_brand→brand");
        }
        detectSemanticConflicts(out);
        composeDistinctiveProductLine(out);
        id.normalizedPhotoIdentity=out;id.normalizationStage="completed";
        String marker="normalization_source_stage="+clean(stage);
        if(!id.observedEvidence.contains(marker))id.observedEvidence.add(marker);
        syncDebug(id,out);CanonicalProfileVoting.apply(id,out);return out;
    }

    static NormalizedPhotoIdentity require(Models.Identification id){if(id==null)return new NormalizedPhotoIdentity();
        return id.normalizedPhotoIdentity==null?normalize(id,"lazy_guard"):id.normalizedPhotoIdentity;}
    static void syncDebug(Models.Identification id,NormalizedPhotoIdentity n){if(id==null||n==null)return;
        id.canonicalPhotoFields=n.debugPhotoFields();id.canonicalPhysicalFields=n.debugPhysicalFields();
        id.aliasesConsumed=n.aliasesConsumed.toString();id.factsRejectedWithReason=n.rejectedFacts.toString();
        id.fingerprintComponents=n.fingerprintComponents.toString();id.fingerprintScore=n.fingerprintScore;
        id.semanticConflicts=n.semanticConflicts.toString();
        id.canonicalProfile=n.profile;
        int ocr=0;for(Models.EvidenceFact f:id.evidenceLedger)if(f!=null&&EvidenceLedger.LOCAL_OCR.equals(f.origin))ocr++;
        id.localOcrFactCount=ocr;}
    private static CanonicalFieldKey contextualKey(CanonicalFieldKey key,Models.EvidenceFact raw){String context=(clean(raw.key)+" "+clean(raw.semanticRole)+" "+clean(raw.location)+" "+clean(raw.evidenceType)).toLowerCase(Locale.ROOT);
        if(key==CanonicalFieldKey.CARD_NUMBER_CANDIDATE||key==CanonicalFieldKey.COLLECTOR_NUMBER_CANDIDATE||key==CanonicalFieldKey.PHYSICAL_SERIAL_CANDIDATE){
            NormalizedPhotoIdentity.Fact probe=new NormalizedPhotoIdentity.Fact(key,raw.value,raw.key,raw.semanticRole,raw.location,raw.side,raw.evidenceType,quality(raw),raw.confidence,raw.imageIndex,raw.origin,raw.createdAtMillis,raw.timestampStage);
            NumericTokenClassifier.Result numeric=NumericTokenClassifier.classify(probe);
            switch(numeric.kind){
                case SEASON:return CanonicalFieldKey.PHYSICAL_SET_OR_RELEASE_YEAR;
                case COPYRIGHT_YEAR:return CanonicalFieldKey.COPYRIGHT_YEAR;
                case STATISTICS:case BIRTH_DATE:case JERSEY_NUMBER:return CanonicalFieldKey.STATISTICS;
                case ATTACK_DAMAGE:return CanonicalFieldKey.ATTACK_DAMAGE;
                case HP_OR_PV:return CanonicalFieldKey.HP_OR_PV;
                case PRODUCT_CODE:return CanonicalFieldKey.MODEL_CODE;
                case BARCODE:return CanonicalFieldKey.BARCODE;
                case PHYSICAL_SERIAL:return CanonicalFieldKey.PHYSICAL_SERIAL_CANDIDATE;
                case COLLECTOR_NUMBER:return CanonicalFieldKey.COLLECTOR_NUMBER_CANDIDATE;
                case CATALOG_CARD_NUMBER:return CanonicalFieldKey.CARD_NUMBER_CANDIDATE;
                default:return CanonicalFieldKey.UNKNOWN;
            }
        }
        if(key==CanonicalFieldKey.PRODUCT_LINE||key==CanonicalFieldKey.SET||key==CanonicalFieldKey.SERIES){
            if(context.contains("insert")||context.contains("subset"))return CanonicalFieldKey.INSERT_SUBSET;
            if(context.contains("design_family")||context.contains("design series"))return CanonicalFieldKey.DESIGN_FAMILY;
            if(context.contains("subseries")||context.contains("sub_series")||context.contains("qualifier"))return CanonicalFieldKey.SUB_SERIES;
            if(context.contains("main_set")||context.contains("parent_set")||context.contains("release_main"))return CanonicalFieldKey.MAIN_SET;
        }
        if(key==CanonicalFieldKey.PHYSICAL_SET_OR_RELEASE_YEAR&&(context.contains("stat")||context.contains("season table")||context.contains("career")))return CanonicalFieldKey.STATISTICAL_SEASON;
        if((key==CanonicalFieldKey.INSERT_LEVEL||key==CanonicalFieldKey.UNKNOWN)
                &&(context.contains("evolution")||context.contains("evoluzione")||context.contains("creature_stage")||context.contains("pokemon_stage")))return CanonicalFieldKey.EVOLUTION_STAGE;
        if(key==CanonicalFieldKey.BRAND&&(context.contains("publisher")&&!context.contains("manufacturer")))return CanonicalFieldKey.PUBLISHER;
        if(key==CanonicalFieldKey.BRAND&&context.contains("manufacturer")&&!context.contains("publisher"))return CanonicalFieldKey.MANUFACTURER;
        if(key==CanonicalFieldKey.CARD_TYPE&&(context.contains("category")||context.contains("object_type")))return CanonicalFieldKey.PRODUCT_TYPE;
        if((key==CanonicalFieldKey.CARD_NUMBER_CANDIDATE||key==CanonicalFieldKey.COLLECTOR_NUMBER_CANDIDATE)
                &&looksSeason(raw.value)&&(context.contains("season")||context.contains("year")||context.contains("release")))return CanonicalFieldKey.PHYSICAL_SET_OR_RELEASE_YEAR;
        return key;}
    private static NormalizedPhotoIdentity.Quality quality(Models.EvidenceFact f){
        if(EvidenceLedger.USER_HINT.equals(f.origin))return NormalizedPhotoIdentity.Quality.USER_HINT;
        if(EvidenceLedger.LOCAL_OCR.equals(f.origin))return NormalizedPhotoIdentity.Quality.LOCAL_OCR_HINT;
        if(EvidenceLedger.WEB_CATALOG.equals(f.origin))return NormalizedPhotoIdentity.Quality.WEB_CATALOG_EVIDENCE;
        if(EvidenceLedger.WEB_MARKET.equals(f.origin))return NormalizedPhotoIdentity.Quality.MARKET_EVIDENCE;
        String type=clean(f.evidenceType).toLowerCase(Locale.ROOT);
        boolean located=f.imageIndex>=0&&!clean(f.location).isEmpty();
        if(located&&!type.equals("vision_structured_field"))return NormalizedPhotoIdentity.Quality.DIRECT_PHOTO_OBSERVATION;
        if(type.contains("local_ocr"))return NormalizedPhotoIdentity.Quality.LOCAL_OCR_HINT;
        return NormalizedPhotoIdentity.Quality.VISION_STRUCTURED_SUMMARY;
    }
    private static void add(NormalizedPhotoIdentity out,boolean sourced,NormalizedPhotoIdentity.Fact fact){if(sourced)out.addSource(fact);else out.add(fact);}
    private static boolean relevant(Models.EvidenceFact f){String x=(clean(f.key)+" "+clean(f.semanticRole)).toLowerCase(Locale.ROOT);return x.matches(".*(?:brand|maker|publisher|set|series|product|subject|player|athlete|card|collector|serial|parallel|finish|edition|printing|format|sport|game|model|barcode|attack|move|hp|pv|year|season).*" );}
    private static void addOnce(java.util.List<String> out,String value){if(out.size()<60&&!out.contains(value))out.add(value);}
    private static boolean ambiguousAlternative(String value){String v=clean(value);if(!v.contains("/"))return false;
        if(v.matches(".*\\d\\s*/\\s*\\d.*")||v.matches(".*(?:19|20)\\d{2}\\s*/\\s*\\d{2,4}.*"))return false;
        String[] p=v.split("\\s*/\\s*");return p.length==2&&p[0].matches(".*[\\p{L}].*")&&p[1].matches(".*[\\p{L}].*");}
    private static void detectSemanticConflicts(NormalizedPhotoIdentity out){CanonicalFieldKey[] keys={CanonicalFieldKey.PRODUCT_LINE,CanonicalFieldKey.SET,
            CanonicalFieldKey.SERIES,CanonicalFieldKey.PRODUCT_NAME,CanonicalFieldKey.SUBJECT,CanonicalFieldKey.PHYSICAL_SET_OR_RELEASE_YEAR};
        for(CanonicalFieldKey key:keys){java.util.List<NormalizedPhotoIdentity.Fact> facts=out.facts(key);for(int i=0;i<facts.size();i++)for(int j=i+1;j<facts.size();j++){
            NormalizedPhotoIdentity.Fact a=facts.get(i),b=facts.get(j);if(a.confidence<70||b.confidence<70||compatible(a.value,b.value))continue;
            addOnce(out.semanticConflicts,key.debugName+"Conflict="+a.value+"<>"+b.value);}}
    }
    private static boolean compatible(String a,String b){String x=clean(a).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();
        String y=clean(b).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();return x.equals(y)||x.contains(y)||y.contains(x);}
    private static boolean unresolved(String x){String v=clean(x).toLowerCase(Locale.ROOT).replace('_',' ');return v.isEmpty()||v.equals("unknown")||v.equals("unresolved")||v.equals("unclear")||v.equals("not visible")||v.equals("none visible")||v.equals("non leggibile")||v.equals("none")||v.equals("n/a");}
    private static boolean looksSeason(String x){return clean(x).matches("(?:19|20)\\d{2}[/.\\-](?:\\d{2}|(?:19|20)\\d{2})");}
    private static void composeDistinctiveProductLine(NormalizedPhotoIdentity out){String line=out.productLine();if(line.isEmpty()||!sealedContext(out))return;
        String merged=line;for(NormalizedPhotoIdentity.Fact token:out.facts(CanonicalFieldKey.DISTINCTIVE_PRINTED_TOKEN)){
            String role=clean(token.semanticRole).toLowerCase(Locale.ROOT);if(role.contains("promo")||role.contains("slogan"))continue;
            if(token.confidence<65||compatible(merged,token.value))continue;merged=clean(merged+" "+token.value);}
        if(!line.equalsIgnoreCase(merged)){
            out.add(new NormalizedPhotoIdentity.Fact(CanonicalFieldKey.SUB_SERIES,clean(merged.substring(Math.min(line.length(),merged.length()))),
                    "distinctive_printed_token_composition","product_line_qualifier","printed title area","front",
                    "normalized_composition",NormalizedPhotoIdentity.Quality.INFERRED,80,0));
            out.add(new NormalizedPhotoIdentity.Fact(CanonicalFieldKey.PRODUCT_LINE,merged,
                    "distinctive_printed_token_composition","product_line","printed title area","front",
                    "normalized_composition",NormalizedPhotoIdentity.Quality.INFERRED,80,0));
        }}
    private static boolean sealedContext(NormalizedPhotoIdentity out){String x=(clean(out.categoryHint)+" "+out.best(CanonicalFieldKey.PRODUCT_TYPE)).toLowerCase(Locale.ROOT);return x.contains("sealed")||x.contains("box")||x.contains("pack")||!out.best(CanonicalFieldKey.CONFIGURATION).isEmpty();}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
