package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Last writer of identity state. Critical invariants and UI share this snapshot. */
final class FinalIdentityDecisionEngine {
    private FinalIdentityDecisionEngine() {}
    static FinalIdentityState freeze(Models.Identification id,String stage){if(id==null)return null;
        PhotographicFactNormalizer.normalize(id,"final_"+safe(stage));PhysicalCardNumberPolicy.normalize(id);PhysicalSerialPolicy.normalize(id);PhysicalVariantPolicy.normalize(id);
        IdentityProfileEngine.Assessment a=IdentityProfileEngine.assess(id);HierarchicalConfidencePolicy.apply(id,a);
        for(int i=id.consistencyInvariantErrors.size()-1;i>=0;i--)if(id.consistencyInvariantErrors.get(i).startsWith("FAIL:"))id.consistencyInvariantErrors.remove(i);
        id.consistencyInvariantWarnings.clear();List<String> critical=new ArrayList<>();
        boolean documentedIdentifierConflict=DocumentedConflictPolicy.reconcile(id);
        if(documentedIdentifierConflict)critical.add("IDENTIFIER_CONFLICT:"+id.numberConflicts);
        if("REJECTED_HARD_CONFLICT".equals(id.catalogCompatibilityStatus))critical.add("WEB_CANDIDATE_INCOMPATIBLE:"+id.catalogConflicts);
        if(safe(id.factsRejectedWithReason).contains("relevant_alias_rejected"))critical.add("UNKNOWN_IDENTIFYING_ALIAS");
        if(id.catalogVerified&&safe(id.catalogMatchedFields).isEmpty())critical.add("CATALOG_MATCH_WITHOUT_MATCHED_FIELDS");
        if("PASSED".equals(id.disproofStatus)&&("NOT_RUN".equals(id.webStatus)||"FAILED".equals(id.webStatus)))critical.add("DISPROOF_PASSED_WITHOUT_WEB");
        if("CATALOG_MATCHED".equals(id.exactIdentityStatus)&&(!id.catalogVerified||!"PASSED".equals(id.disproofStatus)))critical.add("EXACT_MATCH_WITHOUT_CATALOG_DISPROOF");
        if(id.closureResult&&safe(id.closureLevel).isEmpty())critical.add("CLOSURE_WITHOUT_LEVEL");
        IdentityProfileEngine.PhotoTuple t=a.tuple;IdentityProfileEngine.Profile p=a.profile;
        updateProvenanceState(id,t);
        id.graphicNumber=t.graphicNumber;id.attackDamage=t.attackDamage;id.cardType=t.cardType;id.copyrightYear=t.copyrightYear;
        id.language=t.language;id.evolutionStage=t.evolutionStage;id.hpOrPv=t.hp;id.attackNames=t.attacks.toString();
        id.finish=t.finish;id.sealedFormat=t.format;id.productConfiguration=t.configuration;id.productType=t.productType;
        TcgPhysicalEditionPolicy.normalize(id,"final_"+safe(stage));
        id.featuredSubjects.clear();id.featuredSubjects.addAll(t.featuredSubjects);
        String title=composeTitle(id,t,p);
        if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT&&(!tokensPreserved(t.family,title)||!tokensPreserved(t.distinctiveTokens,title)))critical.add("DISTINCTIVE_PRODUCT_TOKEN_LOST");
        String votes=safe(id.canonicalProfileVotes);if(!votes.isEmpty()&&!votes.contains("selected="+profileName(p)))critical.add("PROFILE_VOTE_STATE_CONFLICT");
        if(!safe(id.sourceConfirmedCatalogNumber).isEmpty()&&!id.catalogVerified)critical.add("UNMATCHED_CATALOG_NUMBER_PROMOTED");
        for(String x:critical)addFail(id,x);
        addWarnings(id);

        id.categoryStatus=p==IdentityProfileEngine.Profile.OTHER_COLLECTIBLE&&safe(id.category).isEmpty()?"UNRESOLVED":"CONFIRMED";
        String finalFamily=displayFamily(id,t);id.familyStatus=safe(finalFamily).isEmpty()?"UNRESOLVED":id.catalogVerified?"CATALOG_MATCHED":"CONFIRMED";
        boolean materialAmbiguity=id.photoIdentityAmbiguous&&Math.max(id.photoAlternativeCount,id.canonicalCandidateCount)>=2;
        boolean core=!materialAmbiguity&&(a.complete||photoCore(p,t));id.coreIdentityStatus=core?"CONFIRMED":photoPartialCore(p,t)?"PROBABLE":"UNRESOLVED";
        NormalizedPhotoIdentity n=PhotographicFactNormalizer.require(id);
        if((n.cardNumberVerified||n.collectorNumberVerified)&&physicalIndependent(n))id.identifierStatus="PHYSICALLY_VERIFIED";
        else if(id.catalogVerified&&!safe(id.sourceConfirmedCatalogNumber).isEmpty())id.identifierStatus="CATALOG_MATCHED";
        else if(n.cardNumberVerified||n.collectorNumberVerified)id.identifierStatus="PHYSICALLY_VERIFIED";
        else if(!safe(n.cardNumberCandidate).isEmpty()||!safe(n.collectorNumberCandidate).isEmpty()||!safe(t.cardNumber).isEmpty())id.identifierStatus=hasOcrNumber(n)?"OCR_CANDIDATE":"PHOTO_CANDIDATE";
        else id.identifierStatus="NOT_OBSERVED";
        if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT){
            if(!safe(t.format).isEmpty())id.formatStatus="FORMAT_PHYSICALLY_VERIFIED";
            else if(id.catalogVerified&&!safe(id.sourceConfirmedFormat).isEmpty())id.formatStatus="FORMAT_CATALOG_MATCHED";
            else id.formatStatus="FORMAT_PENDING";
            id.exactIdentityStatus=id.catalogVerified&&"PASSED".equals(id.disproofStatus)&&!"FORMAT_PENDING".equals(id.formatStatus)?"CATALOG_MATCHED":"FORMAT_PENDING";
            id.commercialFormatStatus="FORMAT_PENDING".equals(id.formatStatus)?"TO_VERIFY":"CONFIRMED";
            id.skuStatus=safe(t.modelCode).isEmpty()?"TO_VERIFY":"CONFIRMED";
        }
        else if(IdentityProfileEngine.electronics(p)){
            id.exactModelStatus=safe(t.modelCode).isEmpty()?"TO_VERIFY":"CONFIRMED";
            id.exactIdentityStatus=safe(t.modelCode).isEmpty()?"MODEL_TO_VERIFY":id.catalogVerified?"CATALOG_MATCHED":"PHYSICALLY_VERIFIED";
        }
        else if((p==IdentityProfileEngine.Profile.SPORTS_CARD||p==IdentityProfileEngine.Profile.TCG)&&id.catalogVerified&&"PASSED".equals(id.disproofStatus)&&(!safe(id.sourceConfirmedCatalogNumber).isEmpty()||!safe(id.sourceConfirmedProductLine).isEmpty()))id.exactIdentityStatus=p==IdentityProfileEngine.Profile.TCG&&safe(finalFamily).isEmpty()?"SET_UNRESOLVED":"CATALOG_MATCHED";
        else if((p==IdentityProfileEngine.Profile.SPORTS_CARD||p==IdentityProfileEngine.Profile.TCG)&&"PHYSICALLY_VERIFIED".equals(id.identifierStatus))id.exactIdentityStatus=p==IdentityProfileEngine.Profile.TCG&&safe(finalFamily).isEmpty()?"SET_UNRESOLVED":"PHYSICALLY_VERIFIED";
        else if(p==IdentityProfileEngine.Profile.TCG&&safe(finalFamily).isEmpty())id.exactIdentityStatus="SET_UNRESOLVED";
        else if((p==IdentityProfileEngine.Profile.SPORTS_CARD||p==IdentityProfileEngine.Profile.TCG)&&!"NOT_OBSERVED".equals(id.identifierStatus))id.exactIdentityStatus="NUMBER_UNRESOLVED";
        else id.exactIdentityStatus=core?("NOT_RUN".equals(id.webStatus)?"EXACT_IDENTITY_PENDING_WEB":"CORE_IDENTIFIED"):"UNRESOLVED";
        if(id.rareVariantPhysicalProof&&id.catalogVerified&&"PASSED".equals(id.disproofStatus)
                &&(!safe(id.sourceConfirmedParallelFamily).isEmpty()||!safe(id.sourceConfirmedVariant).isEmpty()))id.variantStatus="VARIANT_CONFIRMED";
        else if(id.rareVariantPhysicalProof)id.variantStatus="VARIANT_PENDING";else if(id.catalogVerified&&!safe(t.finish).isEmpty()&&variantCompatible(t.finish,id.sourceConfirmedVariant))id.variantStatus="VARIANT_CONFIRMED";else if(!safe(t.finish).isEmpty())id.variantStatus="FINISH_OBSERVED";
        else if(id.catalogVerified&&!safe(id.sourceConfirmedVariant).isEmpty())id.variantStatus="CATALOG_REPORTED";else id.variantStatus="NOT_OBSERVED";
        if(p!=IdentityProfileEngine.Profile.TCG){id.exactEditionStatus="NOT_APPLICABLE";id.finishStatus=safe(t.finish).isEmpty()?"NOT_OBSERVED":"CONFIRMED";}
        id.collectorNumberStatus=id.identifierStatus;
        id.exactCatalogStatus=id.exactIdentityStatus;
        id.copyIdentifierStatus=safe(id.physicalSerial).isEmpty()?"NOT_OBSERVED":"PHYSICALLY_VERIFIED";
        if("VARIANT_CONFIRMED".equals(id.variantStatus))id.closureLevel="VARIANT";
        else if("CATALOG_MATCHED".equals(id.exactIdentityStatus))id.closureLevel="EXACT_CATALOG";
        else if("CONFIRMED".equals(id.coreIdentityStatus))id.closureLevel="CORE_IDENTITY";

        id.coreIdentityConfidence=id.mainIdentityConfidence;id.exactIdentityConfidence=exactConfidence(id);
        if(("UNRESOLVED".equals(id.exactIdentityStatus)||id.exactIdentityStatus.endsWith("_UNRESOLVED")||"CORE_IDENTIFIED".equals(id.exactIdentityStatus)||"EXACT_IDENTITY_PENDING_WEB".equals(id.exactIdentityStatus))&&id.exactIdentityConfidence>=100)critical.add("UNRESOLVED_EXACT_IDENTITY_AT_100");
        if("VARIANT_CONFIRMED".equals(id.variantStatus)&&id.variantConfidence<=0)critical.add("CONFIRMED_VARIANT_WITH_ZERO_CONFIDENCE");
        if("CONFLICTED".equals(id.identityStatus)&&!documentedIdentifierConflict&&safe(id.catalogConflicts).isEmpty())id.identityStatus="UNRESOLVED";

        boolean technical="TECHNICAL_ERROR".equals(id.decision)||"VISION_TECHNICAL".equals(id.pipelineFailureDomain);
        boolean pass=critical.isEmpty();if(technical){id.identityConfirmed=false;id.marketReady=false;id.identityStatus="UNRESOLVED";id.decision="TECHNICAL_ERROR";id.overallStatus="TECHNICAL_ERROR";id.nextPhotoRequest="";id.requestedPhotoReason="";id.finalDecisionReason="technical_failure_preserved; stage="+safe(stage);}
        else if(!pass){if(documentedIdentifierConflict||safe(id.catalogConflicts).contains("IDENTIFIER_CONFLICT")){id.identifierStatus="CONFLICTED";id.exactIdentityStatus="NUMBER_CONFLICT";}
            id.identityConfirmed=false;id.marketReady=false;id.identityStatus="CONFLICTED";id.decision="CONFLICTED";
            id.overallStatus="CONFLICTED";id.hierarchicalStatus="CONFLICTED";if("CONFIRMED".equals(id.coreIdentityStatus))id.coreIdentityStatus="PROBABLE";
            id.finalDecisionReason="critical_invariants="+critical+"; stage="+safe(stage);}
        else {boolean confirmed="CONFIRMED".equals(id.coreIdentityStatus);id.identityConfirmed=confirmed;id.marketReady=ExactCatalogResolver.marketReady(id);
            id.identityStatus=confirmed?"CONFIRMED":"UNRESOLVED";id.decision=id.identityStatus;id.overallStatus=confirmed?"MAIN_IDENTITY_CONFIRMED":"INSUFFICIENT_EVIDENCE";
            id.hierarchicalStatus=id.overallStatus;id.finalDecisionReason="profile="+profileName(p)+"; catalog="+id.catalogCompatibilityStatus+"; disproof="+id.disproofStatus+"; exact="+id.exactIdentityStatus+"; identifier="+id.identifierStatus+"; format="+id.formatStatus+"; marketReady="+id.marketReady+"; stage="+safe(stage);}
        title=composeTitle(id,t,p);id.title=title;id.confirmedBrand=t.brand;id.confirmedFamily=displayFamily(id,t);id.confirmedModel=displayModel(id,t,p);
        id.consistencyInvariants=pass?"PASS":"FAIL";id.postWebInvariants=id.consistencyInvariants;
        id.cardNumberVerificationStatus=id.identifierStatus;id.collectorNumberVerificationStatus=id.identifierStatus;
        id.coreIdentityConfidence=id.mainIdentityConfidence;id.exactIdentityConfidence=exactConfidence(id);
        if(!id.marketReady&&!id.priceAvailable){id.marketStatus="IDENTITY_OR_SKU_PENDING";id.comparablesSummary="Comparabili sospesi: identità/SKU non ancora verificato";}
        id.modelConfidence=id.exactIdentityConfidence;id.marketConfidence=id.priceAvailable?id.priceConfidence:0;
        if("CATALOG_MATCHED".equals(id.identifierStatus))id.identifierConfidence=Math.max(id.identifierConfidence,id.webContributionScore);
        else if("PHYSICALLY_VERIFIED".equals(id.identifierStatus))id.identifierConfidence=Math.max(id.identifierConfidence,Math.min(98,id.mainIdentityConfidence+4));
        else if(id.identifierStatus.endsWith("CANDIDATE"))id.identifierConfidence=Math.min(69,Math.max(35,id.identifierConfidence));else id.identifierConfidence=0;
        if("VARIANT_CONFIRMED".equals(id.variantStatus))id.variantConfidence=Math.max(id.variantConfidence,Math.min(97,(id.webContributionScore+id.mainIdentityConfidence)/2));
        else if("CATALOG_REPORTED".equals(id.variantStatus))id.variantConfidence=Math.max(id.variantConfidence,Math.min(85,(id.webContributionScore*3+id.mainIdentityConfidence)/4));
        else if("NOT_OBSERVED".equals(id.variantStatus))id.variantConfidence=0;
        id.postWebConflicts=id.catalogConflicts;id.finalState=new FinalIdentityState(id,title,pass&&id.identityConfirmed);return id.finalState;}

    private static String composeTitle(Models.Identification id,IdentityProfileEngine.PhotoTuple t,IdentityProfileEngine.Profile p){if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT)return CanonicalIdentityComposer.sealedTitle(id);
        if(IdentityProfileEngine.electronics(p))return join(t.brand,t.productType.isEmpty()?"Telecomando per TV":t.productType,t.modelPhysical?t.modelCode:"");
        String brand=t.brand,family=displayFamily(id,t),year=id.catalogVerified&&!safe(id.sourceConfirmedReleaseYear).isEmpty()?SeasonNormalizer.normalize(id.sourceConfirmedReleaseYear):SeasonNormalizer.normalize(t.year);
        String number="";if("PHYSICALLY_VERIFIED".equals(id.identifierStatus))number=t.verifiedCardNumber;else if(id.catalogVerified)number=id.sourceConfirmedCatalogNumber;
        if(p==IdentityProfileEngine.Profile.TCG)return join(startsWith(family,brand)?"":brand,family,t.subject,number.isEmpty()?"":"#"+number,
                "PRESENT".equals(id.firstEditionMark)?"1st Edition":"");
        String hierarchy=hierarchy(first(id.sourceConfirmedMainSet,t.mainSet,family),first(id.sourceConfirmedSubset,t.insertSubset),first(id.sourceConfirmedSubSeries,t.subSeries));
        return join(year,startsWith(hierarchy,brand)?"":brand,hierarchy,t.subject,number.isEmpty()?"":"#"+number);}
    private static String displayFamily(Models.Identification id,IdentityProfileEngine.PhotoTuple t){return id.catalogVerified&&!safe(id.sourceConfirmedProductLine).isEmpty()?id.sourceConfirmedProductLine:t.family;}
    private static String displayModel(Models.Identification id,IdentityProfileEngine.PhotoTuple t,IdentityProfileEngine.Profile p){String n=id.catalogVerified?id.sourceConfirmedCatalogNumber:t.verifiedCardNumber;return join(t.subject,n.isEmpty()?"":"#"+n);}
    private static int exactConfidence(Models.Identification id){int c=id.mainIdentityConfidence;if("CATALOG_MATCHED".equals(id.exactIdentityStatus))c=(c*65+id.webContributionScore*35)/100;
        else c=Math.min(c,69);if("NOT_EVALUATED".equals(id.catalogCompatibilityStatus)||"NOT_EXECUTED".equals(id.disproofStatus))c=Math.min(c,69);return clamp(c);}
    private static void addWarnings(Models.Identification id){String rejected=safe(id.factsRejectedWithReason).toLowerCase(Locale.ROOT);if(rejected.contains("position")||rejected.contains("height")||rejected.contains("weight")||rejected.contains("birth"))id.consistencyInvariantWarnings.add("OPTIONAL_BIOGRAPHIC_FIELD_NOT_USED");
        IdentityProfileEngine.PhotoTuple t=IdentityProfileEngine.tuple(id);if(safe(t.barcode).isEmpty())id.consistencyInvariantWarnings.add("BARCODE_NOT_OBSERVED");if(ProfileQueryBuilder.isSealed(id)&&safe(t.format).isEmpty())id.consistencyInvariantWarnings.add("COMMERCIAL_FORMAT_UNRESOLVED");}
    private static boolean hasOcrNumber(NormalizedPhotoIdentity n){for(NormalizedPhotoIdentity.Fact f:n.facts(CanonicalFieldKey.CARD_NUMBER_CANDIDATE))if(f.quality==NormalizedPhotoIdentity.Quality.LOCAL_OCR_HINT)return true;for(NormalizedPhotoIdentity.Fact f:n.facts(CanonicalFieldKey.COLLECTOR_NUMBER_CANDIDATE))if(f.quality==NormalizedPhotoIdentity.Quality.LOCAL_OCR_HINT)return true;return false;}
    private static boolean physicalIndependent(NormalizedPhotoIdentity n){return independent(n,CanonicalFieldKey.CARD_NUMBER_CANDIDATE)||independent(n,CanonicalFieldKey.COLLECTOR_NUMBER_CANDIDATE);}
    private static boolean independent(NormalizedPhotoIdentity n,CanonicalFieldKey key){java.util.Set<String> groups=new java.util.LinkedHashSet<>();boolean ocr=false;for(NormalizedPhotoIdentity.Fact f:n.facts(key)){if(f.quality==NormalizedPhotoIdentity.Quality.LOCAL_OCR_HINT)ocr=true;if(f.quality==NormalizedPhotoIdentity.Quality.DIRECT_PHOTO_OBSERVATION)groups.add(f.evidenceType+"@"+f.imageIndex);}return groups.size()>=2||(groups.size()>=1&&ocr);}
    private static boolean photoCore(IdentityProfileEngine.Profile p,IdentityProfileEngine.PhotoTuple t){if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT)return !safe(t.brand).isEmpty()&&!safe(t.family).isEmpty()&&(!safe(t.year).isEmpty()||!safe(t.productType).isEmpty());if(p==IdentityProfileEngine.Profile.TCG)return !safe(t.brand).isEmpty()&&!safe(t.subject).isEmpty()&&t.frontComplete;if(p==IdentityProfileEngine.Profile.SPORTS_CARD)return !safe(t.brand).isEmpty()&&!safe(t.family).isEmpty()&&!safe(t.subject).isEmpty();if(IdentityProfileEngine.electronics(p))return !safe(t.brand).isEmpty()&&(t.modelPhysical||t.layoutDistinctive&&(!safe(t.controlLayout).isEmpty()||!safe(t.shortcutButtons).isEmpty()||!safe(t.brandMark).isEmpty()));return t.modelPhysical;}
    private static void updateProvenanceState(Models.Identification id,IdentityProfileEngine.PhotoTuple t){
        id.physicalReleaseYear=t.year;id.statisticsSeason=t.statisticalSeason;
        Models.EvidenceFact brand=EvidenceLedger.bestPhotoFact(id,"brand","manufacturer","publisher","brand_mark");
        if(brand!=null&&!safe(brand.location).isEmpty()){id.observedBrand=brand.value;id.brandStatus="CONFIRMED";}
        else if(!safe(t.brand).isEmpty()){id.inferredBrand=t.brand;id.brandStatus="HYPOTHESIS";}
        String physical=first(t.collectorNumber,t.cardNumber,id.physicalCollectorNumber,id.physicalCardNumber);
        String catalog=safe(id.sourceConfirmedCatalogNumber);id.numberAgreement=!physical.isEmpty()&&!catalog.isEmpty()&&canon(physical).equals(canon(catalog));
        boolean photoNumber=false;for(Models.EvidenceFact f:id.evidenceLedger)if(f!=null&&EvidenceLedger.isPixelOrigin(f.origin)
                &&(f.key.contains("card_number")||f.key.contains("collector_number"))&&canon(f.value).equals(canon(physical))&&!safe(f.location).isEmpty()){photoNumber=true;break;}
        id.combinedVerification=photoNumber&&id.numberAgreement?"PHOTO_PLUS_CATALOG":photoNumber?"PHOTO_ONLY":!catalog.isEmpty()?"CATALOG_ONLY":"NONE";
    }
    private static boolean photoPartialCore(IdentityProfileEngine.Profile p,IdentityProfileEngine.PhotoTuple t){return !safe(t.brand).isEmpty()&&(!safe(t.family).isEmpty()||!safe(t.subject).isEmpty());}
    private static boolean tokensPreserved(String source,String title){String hay=" "+canon(title)+" ";for(String token:canon(source).split(" "))if(token.length()>2&&!hay.contains(" "+token+" "))return false;return true;}
    private static boolean tokensPreserved(java.util.List<String> source,String title){for(String value:source)if(!tokensPreserved(value,title))return false;return true;}
    private static boolean profileName(IdentityProfileEngine.Profile p,String v){return profileName(p).equals(v);}private static String profileName(IdentityProfileEngine.Profile p){return p.name().toLowerCase(Locale.ROOT);}
    private static void addFail(Models.Identification id,String x){String v="FAIL:"+x;if(!id.consistencyInvariantErrors.contains(v))id.consistencyInvariantErrors.add(v);}
    private static int clamp(int x){return Math.max(0,Math.min(100,x));}private static String canon(String x){return safe(x).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}
    private static String join(String...x){StringBuilder b=new StringBuilder();for(String v:x)if(!safe(v).isEmpty()){if(b.length()>0)b.append(' ');b.append(safe(v));}return b.toString();}
    private static String first(String...x){for(String v:x)if(!safe(v).isEmpty())return safe(v);return "";}
    private static String hierarchy(String...x){String out="";for(String v:x)if(!safe(v).isEmpty()&&!canon(out).contains(canon(v)))out=join(out,v);return out;}
    private static boolean startsWith(String text,String prefix){String t=canon(text),p=canon(prefix);return !p.isEmpty()&&(t.equals(p)||t.startsWith(p+" "));}
    private static boolean variantCompatible(String a,String b){String x=canon(a).replace("HOLOGRAPHIC","HOLO"),y=canon(b).replace("HOLOGRAPHIC","HOLO");return !x.isEmpty()&&!y.isEmpty()&&(x.contains(y)||y.contains(x)||x.contains("HOLO")&&y.contains("HOLO"));}
    private static String safe(String x){return x==null?"":x.trim();}
}
