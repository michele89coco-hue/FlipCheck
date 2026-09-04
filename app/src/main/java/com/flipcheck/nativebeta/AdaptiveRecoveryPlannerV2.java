package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;

/** Chooses the cheapest next discriminator with positive information gain. */
final class AdaptiveRecoveryPlannerV2 {
    enum Action { FOCUSED_VISION, IDENTITY_WEB, CLOSE, REQUEST_PHOTO, TECHNICAL_FAILURE }
    static final class Plan {final Action action;final String discriminator,reason;final double expectedInformationGain,estimatedCost;
        Plan(Action action,String field,String reason,double gain,double cost){this.action=action;this.discriminator=field;this.reason=reason;this.expectedInformationGain=gain;this.estimatedCost=cost;}}
    private AdaptiveRecoveryPlannerV2() {}

    static Plan afterPrimary(DomainProfileRouterV2.Profile profile,ImmutableEvidenceLedgerV2 ledger,Models.Usage usage){
        List<String>missing=criticalMissing(profile,ledger);if(!missing.isEmpty()&&cost(usage)+.0035d<=.025d)return new Plan(Action.FOCUSED_VISION,groupedDiscriminator(profile,missing.get(0)),"critical_visible_field_missing_after_primary",.82d,.0035d);
        if(profile==DomainProfileRouterV2.Profile.TCG_CARD
                &&ledger.strongest("edition",EvidenceAtom.EpistemicLevel.OBSERVED)==null
                &&ledger.strongest("firstEditionMark",EvidenceAtom.EpistemicLevel.OBSERVED)==null
                &&cost(usage)+.0035d<=.025d)return new Plan(Action.FOCUSED_VISION,"edition_and_finish","tcg_physical_edition_not_localized",.68d,.0035d);
        if(needsWeb(profile,ledger)&&cost(usage)+.008d<=.025d)return new Plan(Action.IDENTITY_WEB,nextWebDiscriminator(profile,ledger),"identity_verification_or_disproof_required",.76d,.008d);
        return new Plan(Action.CLOSE,"","physical_level_ready",.20d,0d);
    }
    static Plan afterFocused(DomainProfileRouterV2.Profile profile,ImmutableEvidenceLedgerV2 ledger,Models.Usage usage){
        if(needsWeb(profile,ledger)&&cost(usage)+.008d<=.025d)return new Plan(Action.IDENTITY_WEB,nextWebDiscriminator(profile,ledger),"focused_evidence_requires_catalog_disproof",.79d,.008d);
        List<String>missing=criticalMissing(profile,ledger);if(!missing.isEmpty())return new Plan(Action.REQUEST_PHOTO,missing.get(0),"remaining_field_not_recoverable_from_current_views",.90d,0d);
        return new Plan(Action.CLOSE,"","focused_physical_level_ready",.20d,0d);
    }
    static List<String> criticalMissing(DomainProfileRouterV2.Profile p,ImmutableEvidenceLedgerV2 l){List<String>m=new ArrayList<>();
        if(p==DomainProfileRouterV2.Profile.TCG_CARD){need(m,l,"cardName");need(m,l,"collectorNumber");needEither(m,l,"setName","productLine");}
        else if(p==DomainProfileRouterV2.Profile.SPORTS_CARD){need(m,l,"athlete");needEither(m,l,"physicalCardNumber","collectorNumber");need(m,l,"productLine");need(m,l,"productReleaseYear");}
        else if(p==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT){need(m,l,"manufacturer");need(m,l,"productLine");need(m,l,"productReleaseYear");}
        else if(p==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL){need(m,l,"brand");}
        else if(DomainProfileRouterV2.electronics(p)){needEither(m,l,"model","barcode");}
        return m;}
    static boolean needsWeb(DomainProfileRouterV2.Profile p,ImmutableEvidenceLedgerV2 l){if(p==DomainProfileRouterV2.Profile.GENERIC_OBJECT)return l.all().size()>=2;return !l.all().isEmpty();}
    private static String nextWebDiscriminator(DomainProfileRouterV2.Profile p,ImmutableEvidenceLedgerV2 l){if(p==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL)return "brand_and_control_layout";if(DomainProfileRouterV2.cards(p))return "catalog_tuple";if(p==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT)return "manufacturer_line_configuration";return "brand_model_or_code";}
    private static String groupedDiscriminator(DomainProfileRouterV2.Profile p,String missing){if(p==DomainProfileRouterV2.Profile.TCG_CARD)return "tcg_number_set_edition_finish";if(p==DomainProfileRouterV2.Profile.SPORTS_CARD)return "sports_number_and_season";if(p==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT)return "sealed_brand_line_configuration";if(p==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL)return "remote_brand_and_layout";return missing;}
    private static void need(List<String>m,ImmutableEvidenceLedgerV2 l,String f){if(l.strongest(f,EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.EpistemicLevel.RETRIEVED)==null)m.add(f);}
    private static void needEither(List<String>m,ImmutableEvidenceLedgerV2 l,String a,String b){if(l.strongest(a,EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.EpistemicLevel.RETRIEVED)==null&&l.strongest(b,EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.EpistemicLevel.RETRIEVED)==null)m.add(a+"_or_"+b);}
    private static double cost(Models.Usage usage){return usage==null?0d:usage.costUsd;}
}
