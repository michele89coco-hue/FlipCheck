package com.flipcheck.nativebeta;

import java.util.Locale;

/** Repairs safe state drift and exposes any remaining production contradiction. */
final class ConsistencyInvariantChecker {
    private ConsistencyInvariantChecker() {}
    static boolean enforce(Models.Identification id,String stage){if(id==null)return false;
        if(id.discriminativeVisionCount>0&&!safe(id.additionalVisionReason).startsWith("tcg_edition_missing_after_primary")
                &&!safe(id.additionalVisionReason).startsWith("unresolved_discriminative_electronics_field"))fail(id,"UNBOUNDED_NON_TECHNICAL_SECOND_VISION");
        NormalizedPhotoIdentity n=PhotographicFactNormalizer.require(id);IdentityProfileEngine.PhotoTuple t=IdentityProfileEngine.prepare(id);
        if(!n.physicalCardNumber.isEmpty()||!n.physicalCollectorNumber.isEmpty())if(id.physicalCardNumber.isEmpty()){PhysicalCardNumberPolicy.normalize(id);repair(id,"physical_number_resynchronized");}
        if(!n.productLine().isEmpty()&&contains(id.missingDiscriminativeFields,"set_or_product_line"))fail(id,"canonical_productLine_marked_missing");
        if(!n.physicalCollectorNumber.isEmpty()&&contains(id.missingDiscriminativeFields,"collector"))fail(id,"canonical_collectorNumber_marked_missing");
        TcgPhysicalEditionPolicy.normalize(id,"invariant_"+safe(stage));
        if("PRESENT".equals(id.firstEditionMark)&&!"FIRST_EDITION".equals(id.edition)){
            id.edition="FIRST_EDITION";repair(id,"physical_first_edition_restored");}
        if("PRESENT".equals(id.firstEditionMark)&&"UNLIMITED".equalsIgnoreCase(id.sourceConfirmedVariant))fail(id,"PHYSICAL_FIRST_EDITION_WEB_UNLIMITED_CONFLICT");
        if("PRESENT".equals(id.firstEditionMark)&&!"CONFIRMED".equals(id.exactEditionStatus))fail(id,"PHYSICAL_FIRST_EDITION_NOT_CONFIRMED");
        if(!id.candidates.isEmpty()&&id.canonicalCandidateCount==0){id.canonicalCandidateCount=id.candidates.size();repair(id,"canonical_candidate_count_resynchronized");}
        IdentityProfileEngine.Profile p=IdentityProfileEngine.profile(id,t);String req=(id.nextPhotoRequest+" "+id.requestedPhotoReason).toLowerCase(Locale.ROOT);
        if(p==IdentityProfileEngine.Profile.TCG&&(req.contains("model")||req.contains("p/n")||req.contains("seriale apparecchio")||req.contains("barcode generico"))){
            String targeted=PhotographicIdentityClosure.targetedPhotoRequest(id);id.nextPhotoRequest=targeted;id.requestedPhotoReason=targeted.isEmpty()?"":"missing_discriminator="+id.discriminativeField;repair(id,"tcg_device_photo_request_removed");}
        if(PhotographicIdentityClosure.isTerminal(id)){PhotographicIdentityClosure.enforce(id);if("NOT_AVAILABLE".equals(id.marketStatus)&&!id.identityConfirmed)fail(id,"market_status_reopened_identity");}
        if(id.photoIdentityComplete&&id.canonicalCandidateCount==1&&!id.identityConfirmed&&!strongConflict(id)){
            IdentityProfileEngine.Assessment a=IdentityProfileEngine.assess(id);if(a.complete&&id.candidates.get(0).totalScore>=85){
                PhotographicIdentityClosure.apply(id,"invariant_recovery_"+safe(stage));repair(id,"complete_unique_high_confidence_tuple_closed");}}
        if(DocumentedConflictPolicy.hasHardConflict(id)&&!safe(id.physicalCardNumber).isEmpty()&&!safe(id.sourceConfirmedCatalogNumber).isEmpty()
                &&!same(id.physicalCardNumber,id.sourceConfirmedCatalogNumber)){
            id.exactIdentityStatus="NUMBER_CONFLICT";fail(id,"PHYSICAL_NUMBER_CATALOG_CONFLICT");}
        if("CONFIRMED".equals(id.exactIdentityStatus)&&DocumentedConflictPolicy.hasHardConflict(id))fail(id,"CONFIRMED_NUMBER_CONFLICT");
        if(("UNRESOLVED".equals(id.exactIdentityStatus)||"SET_UNRESOLVED".equals(id.exactIdentityStatus)||"NUMBER_UNRESOLVED".equals(id.exactIdentityStatus))&&id.exactIdentityConfidence==100)fail(id,"UNRESOLVED_EXACT_IDENTITY_AT_100");
        if("VARIANT_CONFIRMED".equals(id.variantStatus)&&id.variantConfidence==0)fail(id,"CONFIRMED_VARIANT_WITH_ZERO_CONFIDENCE");
        if("CONFIRMED".equals(id.identityStatus)&&DocumentedConflictPolicy.hasHardConflict(id))fail(id,"GLOBAL_CONFIRMED_WITH_IDENTIFIER_CONFLICT");
        if("CATALOG_MATCHED".equals(id.exactIdentityStatus)&&(!id.catalogVerified||!"PASSED".equals(id.disproofStatus)))fail(id,"EXACT_IDENTITY_WITHOUT_CATALOG_DISPROOF");
        if("PASSED".equals(id.disproofStatus)&&("NOT_RUN".equals(id.webStatus)||"FAILED".equals(id.webStatus)))fail(id,"DISPROOF_PASSED_WITHOUT_WEB");
        if(id.exactIdentityConfidence==100&&("NOT_EVALUATED".equals(id.catalogCompatibilityStatus)||!id.catalogVerified))fail(id,"EXACT_CONFIDENCE_100_WITHOUT_CATALOG");
        if("CONFIRMED".equals(id.exactIdentityStatus)&&!safe(t.cardNumber).isEmpty()&&!t.cardNumberVerified)fail(id,"UNVERIFIED_IDENTIFIER_CONFIRMED_EXACT");
        if(id.localOcrFactCount>0&&safe(id.canonicalPhotoFields).isEmpty())fail(id,"STRONG_LOCAL_OCR_DISCARDED");
        if("INCOMPLETE_MAX_TOKENS".equals(id.visionResponseStatus)&&id.technicalRetryCount==0)fail(id,"VISION_INCOMPLETE_WITHOUT_RETRY");
        if("VISION_TECHNICAL".equals(id.pipelineFailureDomain)&&!safe(id.nextPhotoRequest).isEmpty())fail(id,"TECHNICAL_FAILURE_REPORTED_AS_BAD_PHOTO");
        if(p==IdentityProfileEngine.Profile.TCG&&(safe(id.category).equalsIgnoreCase("Oggetto")||safe(id.categoryKey).equals("other")))fail(id,"TCG_CLASSIFIED_AS_OTHER");
        if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT&&!safe(t.brand).isEmpty()&&!safe(t.family).isEmpty()
                &&!id.identityConfirmed&&contains(id.blockingReason,"format"))fail(id,"SEALED_CORE_DISCARDED_FOR_FORMAT");
        if((safe(id.title).equalsIgnoreCase("Oggetto")||safe(id.title).toLowerCase(Locale.ROOT).contains("sealed trading-card product"))
                &&(!safe(t.brand).isEmpty()||!safe(t.family).isEmpty()||!safe(t.subject).isEmpty()))fail(id,"GENERIC_RESULT_WITH_RICH_CANONICAL_FIELDS");
        String query=safe(id.searchQuery).toLowerCase(Locale.ROOT);for(String featured:id.featuredSubjects)
            if(!safe(featured).isEmpty()&&query.contains(safe(featured).toLowerCase(Locale.ROOT)))fail(id,"FEATURED_SUBJECT_CONTAMINATES_QUERY");
        if(safe(id.factsRejectedWithReason).contains("relevant_alias_rejected"))fail(id,"REJECTED_RELEVANT_ALIAS");
        if(id.closureResult&&!DocumentedConflictPolicy.hasHardConflict(id)&&"CONFLICTED".equals(id.identityStatus)){id.identityStatus="CONFIRMED";id.blockingReason="";repair(id,"false_conflict_removed_after_closure");}
        if("CONFIRMED".equals(id.exactEditionStatus)&&contains(id.missingNonblockingFields,"edition_or_printing")){id.missingNonblockingFields=id.missingNonblockingFields.replace("edition_or_printing","").replaceAll("(^,|,$)","");repair(id,"confirmed_edition_removed_from_missing");}
        if(ProfileQueryBuilder.isSealed(id)&&contains(id.blockingReason,"number")){id.blockingReason="";repair(id,"sealed_card_number_blocker_removed");}
        if("PHOTO_PLUS_CATALOG".equals(id.combinedVerification)&&(!id.numberAgreement||safe(id.sourceConfirmedCatalogNumber).isEmpty()))fail(id,"PHOTO_PLUS_CATALOG_WITHOUT_BOTH_ORIGINS");
        if(!safe(id.nextPhotoRequest).isEmpty()&&safe(id.requestedPhotoProfile).isEmpty())fail(id,"REQUESTED_PHOTO_PROFILE_EMPTY");
        if("FAILED".equals(id.webStatus)&&"CONFIRMED".equals(id.coreIdentityStatus)&&!id.identityConfirmed)fail(id,"WEB_FAILURE_BLOCKS_PHOTO_IDENTITY");
        for(Models.MarketComparable c:id.marketComparables)if(c.included&&!canon(c.itemState).equals(canon(ProfileQueryBuilder.expectedMarketState(id))))fail(id,"MARKET_COMPARABLE_WRONG_BUCKET");
        for(Models.MarketComparable c:id.marketComparables)if(c.included&&!ExactCatalogResolver.marketReady(id))fail(id,"MARKET_COMPARABLE_BEFORE_EXACT_IDENTITY");
        if(!id.candidates.isEmpty()&&id.canonicalCandidateCount==0)fail(id,"CANDIDATE_EXISTS_BUT_COUNT_ZERO");
        if(id.closureResult&&safe(id.closureLevel).isEmpty())fail(id,"CLOSURE_RESULT_WITHOUT_LEVEL");
        for(Models.EvidenceFact f:id.evidenceLedger)if(f!=null&&TextScopePolicy.external(TextScopePolicy.scope(f))&&queryContains(id,f.value))fail(id,"EXTERNAL_TEXT_CONTAMINATES_QUERY:"+TextScopePolicy.scope(f));
        for(String q:id.exactResolutionQueries){String x=safe(q).toLowerCase(Locale.ROOT);if(x.matches(".*\\b(?:null|empty|not visibly specified)\\b.*"))fail(id,"INVALID_EMPTY_VALUE_IN_QUERY");}
        if(id.canonicalCandidateCount==1&&!id.candidates.isEmpty()&&id.candidates.get(0).totalScore>=90
                &&id.familyConfidence==0&&!safe(t.family).isEmpty())fail(id,"STRONG_CANDIDATE_FAMILY_ZERO");
        String family=canon(t.family),stageValue=canon(t.evolutionStage),title=canon(id.title),finish=canon(t.finish);
        if(!stageValue.isEmpty()&&(family.equals(stageValue)||family.contains(stageValue)))fail(id,"EVOLUTION_STAGE_USED_AS_PRODUCT_LINE");
        if(!stageValue.isEmpty()&&title.contains(stageValue)&&IdentityProfileEngine.profile(id,t)==IdentityProfileEngine.Profile.TCG)fail(id,"EVOLUTION_STAGE_IN_MAIN_TITLE");
        if(!finish.isEmpty()&&title.contains(finish))fail(id,"FINISH_IN_MAIN_TITLE");
        if(t.frontComplete&&!id.photoIdentityComplete&&"UNRESOLVED".equals(id.coreIdentityStatus)
                &&!safe(t.brand).isEmpty()&&!safe(t.subject).isEmpty())fail(id,"FRONT_COMPLETE_WITH_UNEXPLAINED_INCOMPLETE_CORE");
        if("CONFIRMED".equals(id.identityStatus)&&"CANDIDATE_UNVERIFIED".equals(PhotographicFactNormalizer.require(id).identifierStatus)
                &&"CONFIRMED".equals(id.exactIdentityStatus))fail(id,"SINGLE_VISION_HYPOTHESIS_CONFIRMED");
        boolean pass=true;for(String e:id.consistencyInvariantErrors)if(e.startsWith("FAIL:")){pass=false;break;}
        id.consistencyInvariants=pass?"PASS":"FAIL";
        if(safe(stage).contains("before_web")||safe(stage).contains("primary"))id.preWebInvariants=id.consistencyInvariants;
        if(safe(stage).contains("post_web")||safe(stage).contains("renderer"))id.postWebInvariants=id.consistencyInvariants;
        return pass;
    }
    private static boolean strongConflict(Models.Identification id){for(String x:id.finalContradictions)if(x.toLowerCase(Locale.ROOT).contains("strong"))return true;return false;}
    private static boolean contains(String source,String token){return safe(source).toLowerCase(Locale.ROOT).contains(token);}
    private static boolean same(String a,String b){return canon(a).equals(canon(b));}
    private static boolean queryContains(Models.Identification id,String value){String needle=safe(value).toLowerCase(Locale.ROOT);if(needle.length()<3)return false;for(String q:id.exactResolutionQueries)if(safe(q).toLowerCase(Locale.ROOT).contains(needle))return true;return safe(id.searchQuery).toLowerCase(Locale.ROOT).contains(needle);}
    private static String canon(String x){return safe(x).toUpperCase(Locale.ROOT).replaceFirst("^#","").replaceAll("[^A-Z0-9]","");}
    private static void repair(Models.Identification id,String x){String v="REPAIRED:"+x;if(!id.consistencyInvariantErrors.contains(v))id.consistencyInvariantErrors.add(v);}
    private static void fail(Models.Identification id,String x){String v="FAIL:"+x;if(!id.consistencyInvariantErrors.contains(v))id.consistencyInvariantErrors.add(v);}
    private static String safe(String x){return x==null?"":x.trim();}
}
