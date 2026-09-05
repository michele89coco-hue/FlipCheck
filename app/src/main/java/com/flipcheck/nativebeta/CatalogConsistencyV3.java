package com.flipcheck.nativebeta;

import java.util.Locale;

/** Rejects internally incomplete or mixed catalog records before they can lead. */
final class CatalogConsistencyV3 {
    static final class Result {final boolean coherent;final String reason;Result(boolean ok,String why){coherent=ok;reason=why;}}
    private CatalogConsistencyV3() {}

    static Result check(IdentityCandidateV2 c,DomainProfileRouterV2.Profile profile){
        if(c==null)return new Result(false,"missing_candidate");
        if(c.retrieved&&empty(c.sourceUrl))return new Result(false,"retrieved_candidate_without_source_url");
        if(mixed(c))return new Result(false,"multiple_catalog_records_fused_in_one_candidate");
        if(c.exactReference&&(empty(c.sourceRecordId)||!("SINGLE_RECORD".equals(c.sourcePageScope)||"CHECKLIST_ROW".equals(c.sourcePageScope)||isolatedSealedFormatSection(c,profile))))return new Result(false,"exact_reference_without_isolated_record");
        if(profile==DomainProfileRouterV2.Profile.TCG_CARD&&c.exactReference){
            if(empty(c.value("cardName"))||empty(c.value("catalogCardNumber"))||empty(c.value("setName")))return new Result(false,"incomplete_tcg_catalog_tuple");
        }else if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD&&c.exactReference){
            if(empty(c.value("athlete"))||empty(c.value("catalogCardNumber"))||empty(c.value("productLine")))return new Result(false,"incomplete_sports_catalog_tuple");
        }else if(profile==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT){
            if(empty(c.value("manufacturer"))||empty(c.value("productLine")))return new Result(false,"incomplete_sealed_product_family");
        }else if(profile==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL){
            if(empty(c.value("manufacturer")))return new Result(false,"remote_candidate_without_brand");
            if(c.layoutMatch<60)return new Result(false,"remote_layout_not_distinctive_enough");
        }
        return new Result(true,"isolated_candidate_record_coherent");
    }

    private static boolean isolatedSealedFormatSection(IdentityCandidateV2 c,DomainProfileRouterV2.Profile profile){
        if(profile!=DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT
                ||!"MULTI_RECORD_PAGE".equals(c.sourcePageScope)
                ||!"VARIANT_OR_FORMAT".equals(c.identityLevel)
                ||empty(c.sourceRecordId)||empty(c.value("commercialFormat"))
                ||!SemanticRelationV3.completeBoxConfiguration(c.value("configuration")))return false;
        SemanticRelationV3.Relation configuration=c.fieldRelations.get("configuration");
        if(configuration==null||!SemanticRelationV3.compatible(configuration))return false;
        for(String unknown:c.unknownFields){
            String value=unknown.toLowerCase(Locale.ROOT).replace('_',' ');
            if(value.matches(".*(?:format not isolated|quantities not assigned to (?:a|one) single record|cards per pack (?:unknown|unconfirmed|not confirmed)|packs per box (?:unknown|unconfirmed|not confirmed)).*"))return false;
        }
        return c.reportedContradictedFields.isEmpty();
    }

    private static boolean mixed(IdentityCandidateV2 c){for(String field:c.fields.keySet()){if(!identityField(field))continue;String v=c.value(field).toUpperCase(Locale.ROOT);if(v.contains(" OR ")||v.contains(" | ")||v.contains("; ")||v.matches(".*#[0-9A-Z/-]+\\s*(?:,|/)\\s*#[0-9A-Z/-]+.*"))return true;}return false;}
    private static boolean identityField(String f){return f.matches("manufacturer|brand|game|productLine|setName|subSeries|cardName|athlete|catalogCardNumber|model|productCode|barcode|edition|commercialFormat");}
    private static boolean empty(String v){return v==null||v.trim().isEmpty();}
}
