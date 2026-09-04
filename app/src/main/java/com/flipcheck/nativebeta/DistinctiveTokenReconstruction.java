package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reconstructs product-title candidates from object-bound OCR without a closed brand vocabulary. */
final class DistinctiveTokenReconstruction {
    enum AnchorClass { HARD_IDENTITY_ANCHOR, SOFT_SUPPORTING_ANCHOR, DESCRIPTIVE_TEXT, NON_IDENTITY_TEXT, EXCLUDED_EXTERNAL_TEXT }
    private DistinctiveTokenReconstruction() {}

    static void apply(NormalizedPhotoIdentity n) {
        if (n == null) return;
        n.hardIdentityTokens.clear();n.softSupportingTokens.clear();n.descriptiveTokens.clear();n.excludedExternalTokens.clear();
        Set<String> known = new LinkedHashSet<>();
        collectWords(known, n.brand()); collectWords(known, n.subject()); collectWords(known, n.productLine());
        for(String value:n.values(CanonicalFieldKey.FEATURED_SUBJECT))collectWords(known,value);
        collectWords(known,n.best(CanonicalFieldKey.SPORT));collectWords(known,n.best(CanonicalFieldKey.CONFIGURATION));
        for(NormalizedPhotoIdentity.Fact f:n.facts(CanonicalFieldKey.DISTINCTIVE_PRINTED_TOKEN))classifyAndStore(n,f,f.value,true);
        List<NormalizedPhotoIdentity.Fact> descriptions = new ArrayList<>(n.facts(CanonicalFieldKey.VISUAL_DESCRIPTION));
        for (NormalizedPhotoIdentity.Fact f : descriptions) {
            if (!objectBound(f) || f.confidence < 45){store(n,AnchorClass.EXCLUDED_EXTERNAL_TEXT,f.value);continue;}
            for (String raw : f.value.split("[\\n|;]+")) {
                String token = clean(raw);
                AnchorClass type=classify(f,token,false);store(n,type,token);
                if(type!=AnchorClass.HARD_IDENTITY_ANCHOR||!printedTitleToken(token)||!distinctive(token,known)){if(type==AnchorClass.HARD_IDENTITY_ANCHOR){n.hardIdentityTokens.remove(token);store(n,AnchorClass.DESCRIPTIVE_TEXT,token);}continue;}
                n.add(new NormalizedPhotoIdentity.Fact(CanonicalFieldKey.DISTINCTIVE_PRINTED_TOKEN,
                        titleCase(token), f.originalKey, "distinctive_product_title_token", f.location,
                        f.side, "ocr_token_reconstruction", f.quality, Math.min(82, f.confidence + 12),
                        f.imageIndex, f.origin, f.timestamp, f.stage));
            }
        }
        // Adjacent short fragments from the same OCR block are retained as a second, non-destructive hypothesis.
        for (int i = 0; i + 1 < descriptions.size(); i++) {
            NormalizedPhotoIdentity.Fact a = descriptions.get(i), b = descriptions.get(i + 1);
            if (a.imageIndex != b.imageIndex || !objectBound(a) || !objectBound(b)) continue;
            String av=clean(a.value),bv=clean(b.value),joined=av+bv;
            if (fragment(av)&&fragment(bv)&&joined.length() <= 18 && distinctive(joined, known)) {
                n.add(new NormalizedPhotoIdentity.Fact(CanonicalFieldKey.DISTINCTIVE_PRINTED_TOKEN,
                        titleCase(joined), "adjacent_ocr_fragments", "distinctive_product_title_token",
                        first(a.location,b.location), first(a.side,b.side), "ocr_fragment_reconstruction",
                        NormalizedPhotoIdentity.Quality.LOCAL_OCR_HINT, Math.min(a.confidence,b.confidence),
                        a.imageIndex, EvidenceLedger.LOCAL_OCR, Math.max(a.timestamp,b.timestamp), "token_reconstruction"));
                store(n,AnchorClass.HARD_IDENTITY_ANCHOR,titleCase(joined));
            }
        }
    }

    private static void classifyAndStore(NormalizedPhotoIdentity n,NormalizedPhotoIdentity.Fact f,String token,boolean explicit){store(n,classify(f,token,explicit),token);}
    private static AnchorClass classify(NormalizedPhotoIdentity.Fact f,String token,boolean explicit){String c=(f.semanticRole+" "+f.location+" "+f.evidenceType+" "+f.originalKey).toLowerCase(Locale.ROOT);
        if(!objectBound(f))return AnchorClass.EXCLUDED_EXTERNAL_TEXT;
        if(c.matches(".*(?:biograph|birth|height|weight|college|school|career|statistic|rating|attack_text|rules_text|effect_text).*"))return AnchorClass.NON_IDENTITY_TEXT;
        if(c.matches(".*(?:promotional|slogan).*"))return AnchorClass.DESCRIPTIVE_TEXT;
        if(c.matches(".*(?:visual_description|appearance).*" )&&!explicit&&!printedTitleToken(token))return AnchorClass.DESCRIPTIVE_TEXT;
        if(!explicit&&printedTitleToken(token))return AnchorClass.HARD_IDENTITY_ANCHOR;
        if(explicit||c.matches(".*(?:product_line|subseries|sub_series|set|subset|insert|brand|manufacturer|parallel|format|sku|identifier|card_number|collector).*"))return AnchorClass.HARD_IDENTITY_ANCHOR;
        return AnchorClass.SOFT_SUPPORTING_ANCHOR;}
    private static void store(NormalizedPhotoIdentity n,AnchorClass type,String value){String v=clean(value);if(v.isEmpty())return;List<String> out=type==AnchorClass.HARD_IDENTITY_ANCHOR?n.hardIdentityTokens:type==AnchorClass.SOFT_SUPPORTING_ANCHOR?n.softSupportingTokens:type==AnchorClass.EXCLUDED_EXTERNAL_TEXT?n.excludedExternalTokens:n.descriptiveTokens;if(!out.contains(v)&&out.size()<40)out.add(v);}

    private static boolean objectBound(NormalizedPhotoIdentity.Fact f) {
        Models.EvidenceFact raw=new Models.EvidenceFact(f.originalKey,f.value,f.origin,f.evidenceType,f.confidence,f.imageIndex,f.side,f.location,f.semanticRole,"");
        return TextScopePolicy.distinctiveTokenEligible(raw);
    }
    private static boolean distinctive(String value, Set<String> known) {
        String x=clean(value); if(x.length()<3||x.length()>40||x.matches(".*(?:[$€£]|https?://|www\\.).*"))return false;
        if(x.matches("(?i).*(?:buy it now|venduto|spedizione|offerta|prezzo|item number).*"))return false;
        if(x.matches("(?i).*(?:born|birth|height|weight|college|school|career|average|points per|games played|drafted).*"))return false;
        if(x.matches(".*\\b\\d{1,3}(?:[.,]\\d+)?\\b.*\\b\\d{1,3}(?:[.,]\\d+)?\\b.*"))return false;
        if(x.matches("(?i)(?:19|20)\\d{2}(?:[/.-]\\d{2,4})?")||x.matches("\\d+(?:/\\d+)?"))return false;
        String canon=canon(x); if(canon.matches("(?:FRONT|BACK|TRUE|FALSE|VISIBLE|UNKNOWN|NONE|PHOTO|IMAGE)"))return false;
        int letters=0;for(char ch:x.toCharArray())if(Character.isLetter(ch))letters++;
        if(letters<3||x.split("\\s+").length>5)return false;
        // A token already fully represented by known identity axes need not be duplicated.
        return !known.contains(canon);
    }
    private static boolean printedTitleToken(String x){String v=clean(x);return v.length()>=5&&v.equals(v.toUpperCase(Locale.ROOT))&&v.matches("[\\p{L}][\\p{L} .&'-]*");}
    private static boolean fragment(String x){String v=clean(x);return v.length()>=2&&v.length()<=8&&v.equals(v.toUpperCase(Locale.ROOT))&&v.matches("[\\p{L}]+" );}
    private static void collectWords(Set<String> out,String x){String c=canon(x);if(!c.isEmpty())out.add(c);}
    private static String titleCase(String x){String s=clean(x);if(s.equals(s.toUpperCase(Locale.ROOT))){StringBuilder b=new StringBuilder();for(String p:s.toLowerCase(Locale.ROOT).split(" ")){if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}return b.toString();}return s;}
    private static String first(String a,String b){return clean(a).isEmpty()?clean(b):clean(a);}
    private static String canon(String x){return clean(x).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
