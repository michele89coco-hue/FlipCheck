package com.flipcheck.nativebeta;

import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Adds catalog/variant/price data without ever reopening a physically confirmed identity. */
final class ConfirmedIdentityEnrichment {
    private ConfirmedIdentityEnrichment() {}

    static String prompt(Models.Identification id) {
        String querySeed=ProfileQueryBuilder.seed(id);
        java.util.List<String> exactQueries=ProfileQueryBuilder.exactQueries(id);
        return "QUERY_PROFILE="+safe(id.queryProfile)
                +"\nQUERY_SEED="+querySeed
                +"\nEXACT_QUERY_LADDER="+exactQueries
                +"\nMARKET_ITEM_STATE="+ProfileQueryBuilder.expectedMarketState(id)
                +"\nRARE_VARIANT_PHYSICAL_PROOF="+id.rareVariantPhysicalProof
                +"\nCONFIRMED_BRAND="+safe(id.confirmedBrand)
                +"\nCONFIRMED_FAMILY="+safe(id.confirmedFamily)
                +"\nSOURCE_CONFIRMED_PRODUCT_LINE="+safe(id.sourceConfirmedProductLine)
                +"\nCONFIRMED_MODEL="+safe(id.confirmedModel)
                +"\nVERIFIED_PHYSICAL_CARD_NUMBER="+safe(PhysicalCardNumberPolicy.verifiedValue(id))
                +"\nUNVERIFIED_IDENTIFIER_CANDIDATE_FOR_SEARCH_ONLY="+safe(id.cardNumberVerified?"":first(id.cardNumberCandidate,id.collectorNumberCandidate,id.physicalCardNumber))
                +"\nPHYSICAL_SERIAL="+safe(id.physicalSerial)
                +"\nPHYSICAL_PARALLEL="+safe(id.physicalParallel)
                +"\nPARALLEL_COLOR="+safe(id.parallelColor)
                +"\nFINISH="+safe(id.finish)
                +"\nPHYSICAL_CARD_NUMBER_ORIGIN="+safe(id.physicalCardNumberOrigin)
                +"\nPHYSICAL_SERIAL_ORIGIN="+safe(id.physicalSerialOrigin)
                +"\nSEALED_FORMAT="+safe(id.sealedFormat)
                +"\nPRODUCT_CONFIGURATION="+safe(id.productConfiguration)
                +"\nFEATURED_SUBJECTS_DISPLAY_ONLY_NOT_QUERY="+id.featuredSubjects
                +"\nCANONICAL_CLOSURE_INPUT="+safe(id.closureInputSnapshot)
                +"\nSTAGED_CATALOG_PLAN="+ProfileQueryBuilder.stagedPlan(id)
                +"\nRETURN candidates[] with source_url, source_authority, brand/game, product_line, main_set, insert_subset, design_family, sub_series, distinguishing_tokens, release_year/season, subject/card_name, card_number/collector_number, language, hp, attacks, copyright_year, layout_signature, finish, parallel_family, parallel_color, print_run, serial_number, edition/printing, format, product_code, package_count, cards_per_pack, autograph_guarantee, memorabilia_guarantee, sealed_status and configuration when the source reports them. "
                +"Discovery omits the contested identifier, precise discovery may use it only as a search term. Compare and explicitly disprove competing candidates inside this same Web batch. Never copy a query hypothesis into a candidate unless the cited source reports it. "
                +"Physical values are immutable. Never use FEATURED_SUBJECTS as sealed-product identity or primary query terms.";
    }

    static void apply(Models.Identification id, OpenAiClient.Response response) {
        if(id==null)return;
        try {
            if(response==null || !response.complete || response.payload==null
                    || response.usage==null || response.usage.webCalls!=1
                    || !safe(response.parseError).isEmpty()) { unavailable(id); return; }
            id.webStatus="COMPLETED";
            JSONObject p=response.payload;
            importFoundSources(id,response);
            recordReported(id,p);
            CatalogCandidateMatcher.Result match=CatalogCandidateMatcher.evaluate(id,response);
            id.rejectedCandidates.addAll(match.rejected);id.catalogConflicts=match.conflicts.toString();
            id.disproofStatus=match.disproofPassed?"PASSED":match.ambiguous?"AMBIGUOUS":"FAILED";
            if(match.accepted!=null&&sourcePresent(response,match.accepted.sourceUrl))accept(id,response,match.accepted,match);
            else {id.catalogVerified=false;id.catalogCompatibilityStatus=match.ambiguous?"AMBIGUOUS":match.rejected.isEmpty()?"NO_STRUCTURED_CANDIDATE":"REJECTED_HARD_CONFLICT";
                id.webContributionScore=0;id.webFieldsRejected=match.conflicts.isEmpty()?"no_compatible_structured_catalog_candidate":match.conflicts.toString();}
            PhotographicFactNormalizer.normalize(id,"post_web_enrichment");
            PhysicalCardNumberPolicy.normalize(id);PhysicalSerialPolicy.normalize(id);PhysicalVariantPolicy.normalize(id);
            PostEnrichmentConsistencyChecker.apply(id);
            CanonicalIdentityComposer.refreshCatalogReleaseDisplay(id);
            if(id.catalogVerified)ComparablePricePolicy.apply(id,p,response);else {id.priceAvailable=false;id.marketStatus="IDENTITY_UNCONFIRMED";id.marketConfidence=0;}
            PostEnrichmentConsistencyChecker.apply(id);
            ConsistencyInvariantChecker.enforce(id,"post_web_before_ui");
            id.postWebInvariants=id.consistencyInvariants;
        } finally { FinalIdentityDecisionEngine.freeze(id,"post_catalog_enrichment"); }
    }

    static void unavailable(Models.Identification id) {
        if(id==null)return;
        id.priceAvailable=false;id.priceConfidence=0;
        id.webStatus="FAILED";id.marketStatus="NOT_AVAILABLE";
        id.marketConfidence=0;id.marketDecisionStatus=HierarchicalIdentityStatus.MARKET_UNAVAILABLE.name();
        id.disproofStatus="NOT_EXECUTED";
        id.priceSummary="mercato non disponibile/non affidabile";
        id.comparablesSummary="comparabili non disponibili";
        FinalIdentityDecisionEngine.freeze(id,"web_unavailable");
    }

    private static boolean sourcePresent(OpenAiClient.Response response,String url) {
        if(url.isEmpty())return false;
        String target=normalizeUrl(url);
        for(Models.Source s:response.sources)if(s!=null&&normalizeUrl(s.url).equals(target))return true;
        return false;
    }
    private static String cleanCatalog(String x){String v=clean(x);if(v.startsWith("#"))v=v.substring(1).trim();return v;}
    private static void recordReported(Models.Identification id,JSONObject p){id.sourceReportedCatalogNumber=cleanCatalog(p.optString("source_reported_catalog_number",p.optString("source_confirmed_catalog_number","")));
        id.sourceReportedReleaseYear=SeasonNormalizer.normalize(p.optString("source_reported_release_year",p.optString("source_confirmed_release_year","")));
        id.sourceReportedProductLine=clean(p.optString("source_reported_product_line",p.optString("source_confirmed_product_line","")));
        id.sourceReportedVariant=clean(p.optString("source_reported_variant",p.optString("source_confirmed_variant","")));}
    private static void accept(Models.Identification id,OpenAiClient.Response response,Models.CandidateScore c,CatalogCandidateMatcher.Result result){
        id.catalogVerified=true;id.catalogCompatibilityStatus="MATCHED";id.catalogMatchedFields=result.matched.toString();id.webContributionScore=c.webScore;
        String source=clean(c.sourceUrl),catalog=cleanCatalog(c.cardNumber),year=SeasonNormalizer.normalize(c.year),line=clean(first(c.family,c.mainSet)),variant=clean(first(c.edition,c.printing,c.parallelFamily,c.parallel));
        if(!catalog.isEmpty()){id.sourceConfirmedCatalogNumber=catalog;id.sourceConfirmedCatalogNumberOrigin="web:catalog_matched";EvidenceLedger.addWebCatalogFact(id,"source_confirmed_catalog_number",catalog,c.webScore,source);}
        if(!year.isEmpty()){id.sourceConfirmedReleaseYear=year;EvidenceLedger.addWebCatalogFact(id,"source_confirmed_release_year",year,c.webScore,source);}
        if(!line.isEmpty()){id.sourceConfirmedProductLine=line;EvidenceLedger.addWebCatalogFact(id,"source_confirmed_product_line",line,c.webScore,source);}
        if(!variant.isEmpty()){id.sourceConfirmedVariant=variant;EvidenceLedger.addWebCatalogFact(id,"source_confirmed_variant",variant,c.webScore,source);}
        id.sourceConfirmedMainSet=clean(c.mainSet);id.sourceConfirmedSubset=clean(c.subset);id.sourceConfirmedSubSeries=clean(c.subSeries);
        id.sourceConfirmedParallelFamily=clean(first(c.parallelFamily,c.parallel));
        if(id.sourceConfirmedParallelFamily.isEmpty()&&id.rareVariantPhysicalProof&&containsWords(variant,id.physicalParallel))id.sourceConfirmedParallelFamily=variant;
        id.sourceConfirmedParallelColor=clean(c.parallelColor);
        id.sourceConfirmedPrintRun=clean(c.printRun);id.sourceConfirmedFormat=clean(c.format);id.sourceConfirmedProductCode=clean(c.productCode);
        if(!id.sourceConfirmedMainSet.isEmpty())EvidenceLedger.addWebCatalogFact(id,"main_set",id.sourceConfirmedMainSet,c.webScore,source);
        if(!id.sourceConfirmedSubset.isEmpty())EvidenceLedger.addWebCatalogFact(id,"insert_subset",id.sourceConfirmedSubset,c.webScore,source);
        if(!id.sourceConfirmedSubSeries.isEmpty())EvidenceLedger.addWebCatalogFact(id,"sub_series",id.sourceConfirmedSubSeries,c.webScore,source);
        if(!id.sourceConfirmedParallelFamily.isEmpty())EvidenceLedger.addWebCatalogFact(id,"parallel_family",id.sourceConfirmedParallelFamily,c.webScore,source);
        if(!id.sourceConfirmedParallelColor.isEmpty())EvidenceLedger.addWebCatalogFact(id,"parallel_color",id.sourceConfirmedParallelColor,c.webScore,source);
        if(!id.sourceConfirmedPrintRun.isEmpty())EvidenceLedger.addWebCatalogFact(id,"print_run",id.sourceConfirmedPrintRun,c.webScore,source);
        if(!id.sourceConfirmedFormat.isEmpty())EvidenceLedger.addWebCatalogFact(id,"format",id.sourceConfirmedFormat,c.webScore,source);
        if(!id.sourceConfirmedProductCode.isEmpty())EvidenceLedger.addWebCatalogFact(id,"product_code",id.sourceConfirmedProductCode,c.webScore,source);
        id.catalogHierarchy=CatalogHierarchy.describe(c);
        id.sourceCatalogTitle=clean(first(c.model,id.sourceCatalogTitle));id.webFieldsAccepted="matched="+result.matched+"; web_score="+c.webScore;
        SourceClassificationPolicy.importAndMark(id,response,source,"catalog",c.webScore);id.candidates.add(c);}
    private static String first(String...x){for(String v:x)if(!safe(v).isEmpty())return clean(v);return "";}
    private static void importFoundSources(Models.Identification id,OpenAiClient.Response response){for(Models.Source source:response.sources){if(source==null||safe(source.url).isEmpty())continue;boolean found=false;for(Models.Source old:id.sources)if(normalizeUrl(old.url).equals(normalizeUrl(source.url))){found=true;break;}if(!found)id.sources.add(source);}}
    private static boolean containsWords(String text,String part){return canon(text).contains(canon(part));}
    private static String canon(String x){return safe(x).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}
    private static String normalizeUrl(String x){String v=safe(x).toLowerCase(Locale.ROOT);while(v.endsWith("/"))v=v.substring(0,v.length()-1);return v;}
    private static String clean(String x){return safe(x).replaceAll("\\s+"," ");}
    private static String compact(String x){return clean(x).replaceAll("(?:^| )(?:null|unknown|unresolved)(?: |$)"," ").replaceAll("\\s+"," ").trim();}
    private static String safe(String x){return x==null?"":x.trim();}
}
