package com.flipcheck.nativebeta;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Typed, deduplicated projection of the immutable Evidence Ledger. */
final class NormalizedPhotoIdentity implements Serializable {
    enum Quality { DIRECT_PHOTO_OBSERVATION, VISION_STRUCTURED_SUMMARY, LOCAL_OCR_HINT,
        USER_HINT, WEB_CATALOG_EVIDENCE, MARKET_EVIDENCE, INFERRED }
    enum FactState { OBSERVED, INFERRED, CATALOG_MATCHED, PHYSICALLY_VERIFIED, CONFLICTED, REJECTED, UNRESOLVED }
    static final class Fact implements Serializable {
        final CanonicalFieldKey key; final String value,rawValue,originalKey,semanticRole,location,side,evidenceType,origin,stage;
        final Quality quality; final FactState state; final int confidence,imageIndex; final long timestamp;
        Fact(CanonicalFieldKey key,String value,String originalKey,String semanticRole,String location,String side,
             String evidenceType,Quality quality,int confidence,int imageIndex){this(key,value,originalKey,semanticRole,location,side,evidenceType,quality,confidence,imageIndex,"",0L,evidenceType);}
        Fact(CanonicalFieldKey key,String value,String originalKey,String semanticRole,String location,String side,
             String evidenceType,Quality quality,int confidence,int imageIndex,String origin,long timestamp,String stage){this.key=key;this.rawValue=value==null?"":value;this.value=clean(value);
            this.originalKey=clean(originalKey);this.semanticRole=clean(semanticRole);this.location=clean(location);
            this.side=clean(side);this.evidenceType=clean(evidenceType);this.quality=quality;
            this.state=quality==Quality.WEB_CATALOG_EVIDENCE?FactState.CATALOG_MATCHED:
                    quality==Quality.MARKET_EVIDENCE?FactState.INFERRED:
                    quality==Quality.INFERRED?FactState.INFERRED:FactState.OBSERVED;
            this.confidence=Math.max(0,Math.min(100,confidence));this.imageIndex=imageIndex;
            this.origin=clean(origin);this.timestamp=timestamp;this.stage=clean(stage);}
        boolean direct(){return quality==Quality.DIRECT_PHOTO_OBSERVATION;}
    }

    private final Map<CanonicalFieldKey,List<Fact>> facts=new EnumMap<>(CanonicalFieldKey.class);
    String categoryHint="",cardNumberCandidate="",collectorNumberCandidate="",physicalCardNumber="",
            physicalCollectorNumber="",physicalSerial="",physicalParallel="",parallelColor="",finish="",
            identifierStatus="NOT_OBSERVED",profile="",normalizationStage="";
    boolean cardNumberVerified,collectorNumberVerified,physicallyGraded,rareVariantPhysicalProof;
    int fingerprintScore;
    final List<String> fingerprintComponents=new ArrayList<>(),aliasesConsumed=new ArrayList<>(),rejectedFacts=new ArrayList<>(),
            semanticConflicts=new ArrayList<>(),identifierAlternatives=new ArrayList<>(),
            hardIdentityTokens=new ArrayList<>(),softSupportingTokens=new ArrayList<>(),
            descriptiveTokens=new ArrayList<>(),excludedExternalTokens=new ArrayList<>(),numericClassifications=new ArrayList<>();
    final List<Fact> sourceEvidence=new ArrayList<>();

    void add(Fact incoming){if(incoming==null||incoming.key==CanonicalFieldKey.UNKNOWN||clean(incoming.value).isEmpty())return;
        List<Fact> values=facts.computeIfAbsent(incoming.key,k->new ArrayList<>());
        for(int i=0;i<values.size();i++){Fact old=values.get(i);if(canon(old.value).equals(canon(incoming.value))&&sameEvidenceFamily(old,incoming)){
            if(rank(incoming)>rank(old))values.set(i,incoming);return;}}
        values.add(incoming);
    }
    void addSource(Fact incoming){if(incoming==null||incoming.key==CanonicalFieldKey.UNKNOWN||clean(incoming.value).isEmpty())return;
        for(int i=0;i<sourceEvidence.size();i++){Fact old=sourceEvidence.get(i);if(old.key==incoming.key&&canon(old.value).equals(canon(incoming.value))){if(rank(incoming)>rank(old))sourceEvidence.set(i,incoming);return;}}
        sourceEvidence.add(incoming);
    }
    String best(CanonicalFieldKey key){Fact f=bestFact(key);return f==null?"":f.value;}
    Fact bestFact(CanonicalFieldKey key){List<Fact> values=facts.get(key);Fact best=null;if(values!=null)for(Fact f:values)if(best==null||rank(f)>rank(best))best=f;return best;}
    List<String> values(CanonicalFieldKey key){List<String> out=new ArrayList<>();List<Fact> fs=facts.get(key);if(fs!=null)for(Fact f:fs)out.add(f.value);return out;}
    List<Fact> facts(CanonicalFieldKey key){List<Fact> out=facts.get(key);return out==null?new ArrayList<>():new ArrayList<>(out);}
    String brand(){return first(best(CanonicalFieldKey.MANUFACTURER),best(CanonicalFieldKey.PUBLISHER),best(CanonicalFieldKey.BRAND));}
    String productLine(){List<Fact> candidates=new ArrayList<>();candidates.addAll(facts(CanonicalFieldKey.PRODUCT_LINE));
        candidates.addAll(facts(CanonicalFieldKey.MAIN_SET));candidates.addAll(facts(CanonicalFieldKey.SET));candidates.addAll(facts(CanonicalFieldKey.SERIES));candidates.addAll(facts(CanonicalFieldKey.PRODUCT_NAME));
        String catalog=canon(sourceProductLine());Fact best=null;if(!catalog.isEmpty()){for(Fact f:candidates)if(compatible(catalog,canon(f.value))&&(best==null||canon(f.value).length()>canon(best.value).length()))best=f;if(best!=null)return best.value;}
        String brandKey=canon(brand());for(Fact f:candidates){
            if(!catalog.isEmpty()&&compatible(catalog,canon(f.value))){if(best==null||rank(f)>rank(best))best=f;continue;}
            if(!catalog.isEmpty()&&hasCatalogCompatibleCandidate(candidates,catalog))continue;
            if(best==null){best=f;continue;}
            String a=canon(best.value),b=canon(f.value);boolean aBrand=!brandKey.isEmpty()&&a.contains(brandKey),bBrand=!brandKey.isEmpty()&&b.contains(brandKey);
            if(bBrand&&!aBrand){best=f;continue;}if(aBrand&&!bBrand)continue;
            if((b.contains(a)||a.contains(b))&&b.length()>a.length()){best=f;continue;}
            if(rank(f)>rank(best))best=f;}
        return best==null?"":best.value;}
    String sourceProductLine(){for(Fact f:sourceEvidence)if(f.key==CanonicalFieldKey.SOURCE_CONFIRMED_PRODUCT_LINE)return f.value;return "";}
    boolean productLineConflictResolvedBySource(){String catalog=canon(sourceProductLine());if(catalog.isEmpty())return false;
        List<Fact> candidates=facts(CanonicalFieldKey.PRODUCT_LINE);int matches=0;String last="";for(Fact f:candidates){String value=canon(f.value);
            if(compatible(catalog,value)&&!value.equals(last)){matches++;last=value;}}return matches==1&&candidates.size()>1;}
    String set(){return first(best(CanonicalFieldKey.SET),best(CanonicalFieldKey.PRODUCT_LINE),best(CanonicalFieldKey.SERIES));}
    String mainSet(){return first(best(CanonicalFieldKey.MAIN_SET),best(CanonicalFieldKey.SET),best(CanonicalFieldKey.PRODUCT_LINE));}
    String insertSubset(){return best(CanonicalFieldKey.INSERT_SUBSET);}
    String designFamily(){return best(CanonicalFieldKey.DESIGN_FAMILY);}
    String subSeries(){return best(CanonicalFieldKey.SUB_SERIES);}
    String parallelFamily(){return first(best(CanonicalFieldKey.PARALLEL_FAMILY),best(CanonicalFieldKey.PHYSICAL_PARALLEL_CANDIDATE));}
    String printRun(){return best(CanonicalFieldKey.PRINT_RUN);}
    List<String> distinctiveTokens(){List<String> out=new ArrayList<>();out.addAll(hardIdentityTokens);for(String x:values(CanonicalFieldKey.DISTINCTIVE_PRINTED_TOKEN))if(!out.contains(x))out.add(x);return out;}
    String subject(){return best(CanonicalFieldKey.SUBJECT);}
    String language(){return best(CanonicalFieldKey.LANGUAGE);}
    String physicalYear(){return best(CanonicalFieldKey.PHYSICAL_SET_OR_RELEASE_YEAR);}
    String edition(){return first(best(CanonicalFieldKey.EDITION),best(CanonicalFieldKey.PRINTING));}
    String debugPhotoFields(){List<String> out=new ArrayList<>();for(Map.Entry<CanonicalFieldKey,List<Fact>> e:facts.entrySet())for(Fact f:e.getValue())out.add(e.getKey().debugName+"="+f.value+"@"+f.quality.name().toLowerCase(Locale.ROOT)+":"+f.state.name().toLowerCase(Locale.ROOT));for(Fact f:sourceEvidence)out.add("source."+f.key.debugName+"="+f.value+"@"+f.quality.name().toLowerCase(Locale.ROOT)+":"+f.state.name().toLowerCase(Locale.ROOT));return out.toString();}
    String debugPhysicalFields(){return "cardNumberCandidate="+cardNumberCandidate+", collectorNumberCandidate="+collectorNumberCandidate
            +", physicalCardNumber="+physicalCardNumber+", cardNumberVerified="+cardNumberVerified
            +", physicalCollectorNumber="+physicalCollectorNumber+", collectorNumberVerified="+collectorNumberVerified
            +", identifierStatus="+identifierStatus+", alternatives="+identifierAlternatives
            +", numericClassifications="+numericClassifications+", physicalSerial="+physicalSerial+", physicalParallel="+physicalParallel+", parallelColor="+parallelColor+", finish="+finish
            +", tokenClasses={hard="+hardIdentityTokens+", soft="+softSupportingTokens+", descriptive="+descriptiveTokens+", excluded="+excludedExternalTokens+"}";}
    private static int rank(Fact f){int q=f.quality==Quality.DIRECT_PHOTO_OBSERVATION?700:f.quality==Quality.USER_HINT?600:
            f.quality==Quality.VISION_STRUCTURED_SUMMARY?500:f.quality==Quality.LOCAL_OCR_HINT?400:
            f.quality==Quality.WEB_CATALOG_EVIDENCE?300:f.quality==Quality.MARKET_EVIDENCE?200:100;return q+f.confidence;}
    private static boolean sameEvidenceFamily(Fact a,Fact b){return family(a.quality).equals(family(b.quality));}
    private static String family(Quality q){if(q==Quality.DIRECT_PHOTO_OBSERVATION||q==Quality.VISION_STRUCTURED_SUMMARY)return "primary_vision";
        if(q==Quality.LOCAL_OCR_HINT)return "local_ocr";if(q==Quality.USER_HINT)return "user";if(q==Quality.WEB_CATALOG_EVIDENCE)return "catalog";
        if(q==Quality.MARKET_EVIDENCE)return "market";return "inferred";}
    private static boolean hasCatalogCompatibleCandidate(List<Fact> facts,String catalog){for(Fact f:facts)if(compatible(catalog,canon(f.value)))return true;return false;}
    private static boolean compatible(String a,String b){return a.equals(b)||a.contains(b)||b.contains(a);}
    private static String first(String...xs){for(String x:xs)if(!clean(x).isEmpty())return clean(x);return "";}
    private static String canon(String x){return clean(x).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
