package com.flipcheck.nativebeta;

import java.util.Locale;

/** Selects one profile before candidate resolution; the selected profile is stable. */
final class DomainProfileRouterV2 {
    enum Profile { TCG_CARD, SPORTS_CARD, SEALED_TRADING_CARD_PRODUCT, CONSUMER_ELECTRONICS,
        TELEVISION_REMOTE_CONTROL, SMARTPHONE, APPLIANCE, BARCODE_PRODUCT, GENERIC_COLLECTIBLE, GENERIC_OBJECT }
    private DomainProfileRouterV2() {}

    static Profile route(String category,ImmutableEvidenceLedgerV2 ledger){
        String c=safe(category).toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');
        if(c.contains("tcg"))return Profile.TCG_CARD;if(c.contains("sport")&&c.contains("card"))return Profile.SPORTS_CARD;
        if(c.contains("sealed"))return Profile.SEALED_TRADING_CARD_PRODUCT;if(c.contains("remote")&&c.contains("television"))return Profile.TELEVISION_REMOTE_CONTROL;
        if(c.contains("remote"))return Profile.TELEVISION_REMOTE_CONTROL;if(c.contains("smartphone")||c.contains("mobile_phone"))return Profile.SMARTPHONE;
        if(c.contains("appliance"))return Profile.APPLIANCE;if(c.contains("electronic"))return Profile.CONSUMER_ELECTRONICS;
        if(ledger.strongest("barcode",EvidenceAtom.EpistemicLevel.OBSERVED)!=null)return Profile.BARCODE_PRODUCT;
        if(ledger.strongest("collectorNumber",EvidenceAtom.EpistemicLevel.OBSERVED)!=null||ledger.strongest("hp",EvidenceAtom.EpistemicLevel.OBSERVED)!=null)return Profile.TCG_CARD;
        if(ledger.strongest("athlete",EvidenceAtom.EpistemicLevel.OBSERVED)!=null||ledger.strongest("team",EvidenceAtom.EpistemicLevel.OBSERVED)!=null)return Profile.SPORTS_CARD;
        if(ledger.strongest("configuration",EvidenceAtom.EpistemicLevel.OBSERVED)!=null)return Profile.SEALED_TRADING_CARD_PRODUCT;
        if(ledger.strongest("controlLayout",EvidenceAtom.EpistemicLevel.OBSERVED)!=null||ledger.strongest("shortcutButtons",EvidenceAtom.EpistemicLevel.OBSERVED)!=null)return Profile.TELEVISION_REMOTE_CONTROL;
        return c.contains("collect")?Profile.GENERIC_COLLECTIBLE:Profile.GENERIC_OBJECT;
    }
    static boolean cards(Profile p){return p==Profile.TCG_CARD||p==Profile.SPORTS_CARD;}
    static boolean electronics(Profile p){return p==Profile.CONSUMER_ELECTRONICS||p==Profile.TELEVISION_REMOTE_CONTROL||p==Profile.SMARTPHONE||p==Profile.APPLIANCE;}
    static String categoryKey(Profile p){return p.name().toLowerCase(Locale.ROOT);}
    private static String safe(String value){return value==null?"":value.trim();}
}
