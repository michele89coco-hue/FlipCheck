package com.flipcheck.nativebeta;

import java.util.Locale;

/** Separates text printed on the foreground object from screenshot/listing/UI text. */
final class TextScopePolicy {
    private TextScopePolicy() {}
    static String scope(Models.EvidenceFact f){if(f==null)return "UNKNOWN";String x=clean(f.semanticRole+" "+f.location+" "+f.evidenceType+" "+f.value).toLowerCase(Locale.ROOT);
        if(x.matches(".*(?:marketplace|listing|seller|buy it now|venduto|spedizione|listing price|ebay ui).*"))return "MARKETPLACE_LISTING_TEXT";
        if(x.matches(".*(?:screen_ui|status bar|navigation bar|app interface).*"))return "SCREEN_UI";
        if(x.matches(".*(?:external sticker|external_sticker|shipping label).*"))return "EXTERNAL_STICKER";
        if(x.matches(".*(?:background|thumbnail|nearby object).*"))return "BACKGROUND_OBJECT";
        if(x.matches(".*(?:serial|collector|card_number|barcode|product_code|physical_marking).*"))return "OBJECT_PHYSICAL_MARKING";
        if(EvidenceLedger.PHOTO.equals(f.origin)||EvidenceLedger.LOCAL_OCR.equals(f.origin))return "OBJECT_PRINTED_TEXT";
        return "UNKNOWN";}
    static boolean primaryObjectEvidence(Models.EvidenceFact f){String s=scope(f);return s.equals("OBJECT_PRINTED_TEXT")||s.equals("OBJECT_PHYSICAL_MARKING");}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
