package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reconstructs product-title candidates from object-bound OCR without a closed brand vocabulary. */
final class DistinctiveTokenReconstruction {
    private DistinctiveTokenReconstruction() {}

    static void apply(NormalizedPhotoIdentity n) {
        if (n == null) return;
        Set<String> known = new LinkedHashSet<>();
        collectWords(known, n.brand()); collectWords(known, n.subject()); collectWords(known, n.productLine());
        for(String value:n.values(CanonicalFieldKey.FEATURED_SUBJECT))collectWords(known,value);
        collectWords(known,n.best(CanonicalFieldKey.SPORT));collectWords(known,n.best(CanonicalFieldKey.CONFIGURATION));
        List<NormalizedPhotoIdentity.Fact> descriptions = new ArrayList<>(n.facts(CanonicalFieldKey.VISUAL_DESCRIPTION));
        for (NormalizedPhotoIdentity.Fact f : descriptions) {
            if (!objectBound(f) || f.confidence < 45) continue;
            for (String raw : f.value.split("[\\n|;]+")) {
                String token = clean(raw);
                if (!printedTitleToken(token) || !distinctive(token, known)) continue;
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
            }
        }
    }

    private static boolean objectBound(NormalizedPhotoIdentity.Fact f) {
        String c = (f.semanticRole + " " + f.location + " " + f.evidenceType).toLowerCase(Locale.ROOT);
        return !c.matches(".*(?:marketplace|listing|screen_ui|price|seller|background|watermark|external_sticker).*" );
    }
    private static boolean distinctive(String value, Set<String> known) {
        String x=clean(value); if(x.length()<3||x.length()>40||x.matches(".*(?:[$€£]|https?://|www\\.).*"))return false;
        if(x.matches("(?i).*(?:buy it now|venduto|spedizione|offerta|prezzo|item number).*"))return false;
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
