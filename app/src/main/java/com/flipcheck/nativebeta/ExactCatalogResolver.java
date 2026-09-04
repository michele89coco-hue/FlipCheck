package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;

/** Mandatory post-core resolver for profile-specific exact catalog axes. */
final class ExactCatalogResolver {
    private ExactCatalogResolver() {}

    static boolean required(Models.Identification id) {
        if(id==null)return false;IdentityProfileEngine.PhotoTuple t=IdentityProfileEngine.tuple(id);
        IdentityProfileEngine.Profile p=IdentityProfileEngine.profile(id,t);
        if(!id.catalogVerified)return true;
        if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT)return empty(id.sourceConfirmedSubSeries)&&!t.distinctiveTokens.isEmpty()||empty(id.sourceConfirmedFormat);
        if(p==IdentityProfileEngine.Profile.TCG)return empty(id.sourceConfirmedProductLine)&&empty(id.sourceConfirmedMainSet)||empty(id.sourceConfirmedCatalogNumber);
        if(p==IdentityProfileEngine.Profile.SPORTS_CARD)return empty(id.sourceConfirmedCatalogNumber)||
                (id.rareVariantPhysicalProof&&(empty(id.sourceConfirmedParallelFamily)||empty(id.sourceConfirmedPrintRun)&&!empty(t.serial)));
        return empty(t.modelCode)&&empty(id.sourceConfirmedProductCode);
    }

    static String reason(Models.Identification id){List<String>x=missing(id);return x.isEmpty()?"exact_axes_catalog_matched":"exact_catalog_resolution_required="+x;}
    static List<String> missing(Models.Identification id){List<String>x=new ArrayList<>();if(id==null)return x;IdentityProfileEngine.PhotoTuple t=IdentityProfileEngine.tuple(id);IdentityProfileEngine.Profile p=IdentityProfileEngine.profile(id,t);
        if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT){if(empty(t.subSeries)&&!t.distinctiveTokens.isEmpty())x.add("subSeries placement");if(empty(t.format)&&empty(id.sourceConfirmedFormat))x.add("commercialFormat");if(empty(t.modelCode)&&empty(id.sourceConfirmedProductCode))x.add("SKU");}
        else if(p==IdentityProfileEngine.Profile.TCG){if(empty(t.family)&&empty(id.sourceConfirmedProductLine)&&empty(id.sourceConfirmedMainSet))x.add("set");if(empty(t.cardNumber)&&empty(id.sourceConfirmedCatalogNumber))x.add("collectorNumber");if(empty(t.edition))x.add("edition/printing");}
        else if(p==IdentityProfileEngine.Profile.SPORTS_CARD){if(empty(t.mainSet)&&empty(t.family))x.add("mainSet");if(empty(t.cardNumber)&&empty(id.sourceConfirmedCatalogNumber))x.add("cardNumber");if(id.rareVariantPhysicalProof){if(empty(t.parallelFamily)&&empty(id.sourceConfirmedParallelFamily))x.add("parallelFamily");if(!empty(t.serial)&&empty(t.printRun)&&empty(id.sourceConfirmedPrintRun))x.add("printRun");}}
        else if(empty(t.modelCode)&&empty(id.sourceConfirmedProductCode))x.add("modelCode");return x;}

    static boolean marketReady(Models.Identification id){if(id==null||!id.catalogVerified||!"PASSED".equals(id.disproofStatus))return false;IdentityProfileEngine.PhotoTuple t=IdentityProfileEngine.tuple(id);IdentityProfileEngine.Profile p=IdentityProfileEngine.profile(id,t);
        if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT)return !empty(first(t.format,id.sourceConfirmedFormat));
        if(p==IdentityProfileEngine.Profile.TCG)return !empty(id.sourceConfirmedCatalogNumber)&&!empty(first(id.sourceConfirmedProductLine,id.sourceConfirmedMainSet,t.family));
        if(p==IdentityProfileEngine.Profile.SPORTS_CARD)return !empty(id.sourceConfirmedCatalogNumber)&&(!id.rareVariantPhysicalProof||(!empty(id.sourceConfirmedParallelFamily)&&(!empty(id.sourceConfirmedPrintRun)||empty(t.serial))));
        return !empty(first(t.modelCode,id.sourceConfirmedProductCode));}
    private static String first(String...x){for(String v:x)if(!empty(v))return v.trim();return "";}
    private static boolean empty(String x){return x==null||x.trim().isEmpty();}
}
