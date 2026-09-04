package com.flipcheck.nativebeta;

/** Deliberately independent from identity resolution. v1.32 does not buy market calls early. */
final class MarketResolverV2 {
    private MarketResolverV2() {}
    static void defer(Models.Identification id,String reason){if(id==null)return;id.marketCalls=0;id.marketStatus="NOT_RUN";id.marketDecisionStatus="MARKET_DEFERRED";id.priceAvailable=false;id.priceSummary="Prezzo non calcolato durante l’identificazione";id.comparablesSummary=reason;}
}
