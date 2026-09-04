package com.flipcheck.nativebeta;

import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Imports Web catalog suggestions without mutating any photographed identity field. */
final class NonDestructiveWebEnrichment {
    private NonDestructiveWebEnrichment() {}
    static void apply(Models.Identification id,OpenAiClient.Response response){if(id==null)return;
        ConfirmedIdentityEnrichment.apply(id,response);
        if(!id.catalogVerified){id.priceAvailable=false;id.priceConfidence=0;id.priceSummary="mercato non disponibile/non affidabile";
            id.comparablesSummary="nessun candidato catalografico compatibile: comparabili non promossi";id.marketStatus="IDENTITY_UNCONFIRMED";
            id.marketConfidence=0;id.marketDecisionStatus=HierarchicalIdentityStatus.MARKET_UNAVAILABLE.name();}
    }
    private static boolean sourcePresent(OpenAiClient.Response r,String url){String target=norm(url);for(Models.Source s:r.sources)if(s!=null&&norm(s.url).equals(target))return true;return false;}
    private static String cleanCatalog(String x){String v=safe(x);return v.startsWith("#")?v.substring(1).trim():v;}
    private static String norm(String x){String v=safe(x).toLowerCase(Locale.ROOT);while(v.endsWith("/"))v=v.substring(0,v.length()-1);return v;}
    private static String safe(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
