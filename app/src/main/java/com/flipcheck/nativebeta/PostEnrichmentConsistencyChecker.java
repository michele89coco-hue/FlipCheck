package com.flipcheck.nativebeta;

import java.util.Locale;

/** Attribute-scoped conflict resolution after catalog enrichment and before UI. */
final class PostEnrichmentConsistencyChecker {
    private PostEnrichmentConsistencyChecker() {}
    static void apply(Models.Identification id){if(id==null)return;
        String physical=clean(id.physicalCardNumber),catalog=clean(id.sourceConfirmedCatalogNumber);
        boolean resolverConflict=!clean(id.numberConflicts).isEmpty();
        if((!physical.isEmpty()&&!catalog.isEmpty()&&!same(physical,catalog))||resolverConflict){
            add(id,"PHYSICAL_NUMBER_CATALOG_CONFLICT:"+physical+"!="+catalog);
            if(id.numberConflicts.isEmpty())id.numberConflicts="physicalCardNumber="+physical+"; sourceConfirmedCatalogNumber="+catalog;
            id.exactIdentityStatus="NUMBER_CONFLICT";
            id.coreIdentityStatus="CONFIRMED";
            id.blockingReason="exact_attribute_number_conflict";
            id.identityStatus="CONFLICTED";id.decision="CONFLICTED";id.hierarchicalStatus=HierarchicalIdentityStatus.CONFLICTED.name();
            id.marketReady=false;id.disproofPassed=false;
        }else if(!physical.isEmpty()&&!catalog.isEmpty()&&PhysicalCardNumberPolicy.verifiedNumber(id)){
            id.numberConflicts="";
            IdentityProfileEngine.PhotoTuple tuple=IdentityProfileEngine.tuple(id);
            IdentityProfileEngine.Profile profile=IdentityProfileEngine.profile(id,tuple);
            id.exactIdentityStatus=profile==IdentityProfileEngine.Profile.TCG&&tuple.family.isEmpty()?"SET_UNRESOLVED":"CONFIRMED";
            id.identityStatus="CONFIRMED";id.decision="CONFIRMED";id.marketReady=true;id.disproofPassed=true;
            id.hierarchicalStatus=HierarchicalIdentityStatus.MAIN_IDENTITY_CONFIRMED.name();
            removePrefix(id,"PHYSICAL_NUMBER_CATALOG_CONFLICT:");
        }else if(!catalog.isEmpty()&&id.catalogVerified){
            id.exactIdentityStatus="CATALOG_MATCHED";id.identifierStatus="CATALOG_MATCHED";
            id.cardNumberVerificationStatus="CATALOG_MATCHED";id.collectorNumberVerificationStatus="CATALOG_MATCHED";
            id.identifierConfidence=Math.max(id.identifierConfidence,id.webContributionScore);
        }
        NormalizedPhotoIdentity n=PhotographicFactNormalizer.require(id);
        String physicalYear=n.physicalYear(),sourceYear=clean(id.sourceConfirmedReleaseYear);
        if(!physicalYear.isEmpty()&&!sourceYear.isEmpty()&&!compatibleSeason(physicalYear,sourceYear))
            add(id,"RELEASE_YEAR_CONFLICT:"+physicalYear+"!="+sourceYear);
        if(id.catalogVerified&&!clean(id.sourceConfirmedVariant).isEmpty()&&!id.rareVariantPhysicalProof){id.variantStatus="CATALOG_REPORTED";
            id.variantConfidence=Math.min(Math.max(45,id.webContributionScore),82);}
        id.postWebConflicts=id.consistencyInvariantErrors.toString();
        HierarchicalConfidencePolicy.apply(id,IdentityProfileEngine.assess(id));
    }
    private static boolean compatibleSeason(String a,String b){return SeasonNormalizer.compatible(a,b);}
    private static String digits(String x){return clean(x).replaceAll("[^0-9]","");}
    private static boolean same(String a,String b){return canon(a).equals(canon(b));}
    private static String canon(String x){return clean(x).toUpperCase(Locale.ROOT).replaceFirst("^#","").replaceAll("[^A-Z0-9]","");}
    private static void add(Models.Identification id,String x){if(!id.consistencyInvariantErrors.contains(x))id.consistencyInvariantErrors.add(x);}
    private static void removePrefix(Models.Identification id,String prefix){for(int i=id.consistencyInvariantErrors.size()-1;i>=0;i--)if(id.consistencyInvariantErrors.get(i).startsWith(prefix)||id.consistencyInvariantErrors.get(i).equals("FAIL:PHYSICAL_NUMBER_CATALOG_CONFLICT"))id.consistencyInvariantErrors.remove(i);}
    private static String clean(String x){return x==null?"":x.trim();}
}
