package com.flipcheck.nativebeta;

import java.util.Locale;

/** Classifies text scope before semantic normalization. */
final class TextScopePolicy {
    static final String OBJECT_PRINTED_TEXT="OBJECT_PRINTED_TEXT";
    static final String OBJECT_IDENTIFIER="OBJECT_IDENTIFIER";
    static final String OBJECT_STATISTIC="OBJECT_STATISTIC";
    static final String OBJECT_BIOGRAPHICAL_TEXT="OBJECT_BIOGRAPHICAL_TEXT";
    static final String OBJECT_RULES_TEXT="OBJECT_RULES_TEXT";
    static final String SCREEN_UI_TEXT="SCREEN_UI_TEXT";
    static final String MARKETPLACE_LISTING_TEXT="MARKETPLACE_LISTING_TEXT";
    static final String BACKGROUND_OBJECT="BACKGROUND_OBJECT";
    static final String EXTERNAL_OVERLAY="EXTERNAL_OVERLAY";
    static final String UNKNOWN_SCOPE="UNKNOWN_SCOPE";
    private TextScopePolicy() {}
    static String scope(Models.EvidenceFact f){if(f==null)return UNKNOWN_SCOPE;
        String meta=clean(f.semanticRole+" "+f.location+" "+f.evidenceType+" "+f.key).toLowerCase(Locale.ROOT);
        String value=clean(f.value).toLowerCase(Locale.ROOT),all=meta+" "+value;
        if(matches(all,"marketplace|listing|seller|buy it now|venduto|sold price|spedizione|shipping|il mio ebay|watchlist|item price"))return MARKETPLACE_LISTING_TEXT;
        if(matches(meta,"screen_ui|status[_ ]bar|navigation[_ ]bar|app[_ ]interface|gallery[_ ]indicator|phone[_ ]ui")
                ||value.matches("(?:[01]?\\d|2[0-3]):[0-5]\\d")||value.matches("\\d+\\s+(?:di|of)\\s+\\d+"))return SCREEN_UI_TEXT;
        if(matches(all,"external[_ ]sticker|shipping[_ ]label|watermark|external[_ ]overlay|price[_ ]tag"))return EXTERNAL_OVERLAY;
        if(matches(meta,"background|thumbnail|nearby[_ ]object|other[_ ]card"))return BACKGROUND_OBJECT;
        if(matches(meta,"biograph|birth|born|birthplace|height|weight|college|school|career|hometown|position"))return OBJECT_BIOGRAPHICAL_TEXT;
        if(matches(meta,"statistic|stats|rating|jersey|offense|defense|average|season[_ ]table|graphic[_ ]number"))return OBJECT_STATISTIC;
        if(matches(meta,"rules[_ ]text|attack[_ ]text|move[_ ]text|description|effect[_ ]text|instruction"))return OBJECT_RULES_TEXT;
        if(matches(meta,"serial|collector|card[_ ]number|barcode|product[_ ]code|physical[_ ]marking|model[_ ]code|sku|part[_ ]number"))return OBJECT_IDENTIFIER;
        if(EvidenceLedger.PHOTO.equals(f.origin)||EvidenceLedger.LOCAL_OCR.equals(f.origin)||EvidenceLedger.USER_HINT.equals(f.origin))return OBJECT_PRINTED_TEXT;
        return UNKNOWN_SCOPE;}
    static boolean primaryObjectEvidence(Models.EvidenceFact f){String s=scope(f);return s.equals(OBJECT_PRINTED_TEXT)||s.equals(OBJECT_IDENTIFIER)||s.equals(OBJECT_STATISTIC)||s.equals(OBJECT_BIOGRAPHICAL_TEXT)||s.equals(OBJECT_RULES_TEXT);}
    static boolean fingerprintEligible(Models.EvidenceFact f){String s=scope(f);return s.equals(OBJECT_PRINTED_TEXT)||s.equals(OBJECT_IDENTIFIER)||s.equals(OBJECT_STATISTIC)||s.equals(OBJECT_RULES_TEXT);}
    static boolean identifierEligible(Models.EvidenceFact f){return OBJECT_IDENTIFIER.equals(scope(f));}
    static boolean distinctiveTokenEligible(Models.EvidenceFact f){String s=scope(f);return s.equals(OBJECT_PRINTED_TEXT)||s.equals(OBJECT_IDENTIFIER);}
    static boolean external(String scope){return SCREEN_UI_TEXT.equals(scope)||MARKETPLACE_LISTING_TEXT.equals(scope)||BACKGROUND_OBJECT.equals(scope)||EXTERNAL_OVERLAY.equals(scope)||UNKNOWN_SCOPE.equals(scope);}
    private static boolean matches(String x,String regex){return x.matches(".*(?:"+regex+").*");}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
