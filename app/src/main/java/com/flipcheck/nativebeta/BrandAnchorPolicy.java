package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import java.util.Locale;

final class BrandAnchorPolicy {
    private BrandAnchorPolicy() {
    }

    static boolean isLocked(Models.Identification id) {
        if (id == null || empty(id.brand) || id.brandRoleConfidence < 85
                || !BrandBlindPolicy.trustedObservedBrand(id)) {
            return false;
        }
        String e = safe(id.brandEvidence).toLowerCase(Locale.ROOT);
        return e.equals("visible_logo") || e.equals("visible_logo_cross_photo")
                || e.equals("visible_brand_text") || e.equals("explicit_label")
                || e.equals("ocr_brand");
    }

    static String anchor(Models.Identification id) {
        return isLocked(id) ? safe(id.brand) : "";
    }

    static String promptBlock(Models.Identification id) {
        String b = anchor(id);
        if (b.isEmpty()) {
            return "VISIBLE_BRAND_ANCHOR=none | BRAND_LOCK_REQUIRED=false";
        }
        return "VISIBLE_BRAND_ANCHOR=\"" + b.replace("\"", "") + "\" | BRAND_LOCK_REQUIRED=true";
    }

    static boolean candidateCompatible(Models.Identification id, Models.CandidateScore c) {
        if (!isLocked(id)) {
            return true;
        }
        if (c == null || empty(c.brand)) {
            return false;
        }
        return sameBrand(anchor(id), c.brand);
    }

    static boolean sameBrand(String a, String b) {
        String x = canonBrand(a);
        String y = canonBrand(b);
        return !x.isEmpty() && x.equals(y);
    }

    private static String canonBrand(String s) {
        String x = safe(s).toLowerCase(Locale.ROOT).replace('&', ' ').replaceAll("[^a-z0-9]+", " ").trim();
        return x.isEmpty() ? "" : x.replaceAll("\\b(incorporated|inc|llc|ltd|limited|corporation|corp|company|gmbh|srl|spa|plc|sa)\\b", " ").replaceAll("\\s+", " ").trim().replace(" ", "");
    }

    private static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
