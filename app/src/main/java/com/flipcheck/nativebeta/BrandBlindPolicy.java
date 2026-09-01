package com.flipcheck.nativebeta;

import androidx.core.os.EnvironmentCompat;
import com.flipcheck.nativebeta.Models;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

final class BrandBlindPolicy {
    private BrandBlindPolicy() {
    }

    static void sanitizeBrandEvidence(Models.Identification id, Models.LocalScan local) {
        if (id == null) {
            return;
        }
        String brand = safe(id.brand);
        if (brand.isEmpty()) {
            id.brandEvidence = EnvironmentCompat.MEDIA_UNKNOWN;
            return;
        }
        String e = safe(id.brandEvidence).toLowerCase(Locale.ROOT);
        if (observedName(e) && !brandLiterallyPresent(id, local, brand)) {
            id.brandEvidence = "visual_guess";
            id.brandRoleConfidence = Math.min(id.brandRoleConfidence, 60);
            removeExact(id.brandLabels, brand);
            removeExact(id.visibleLabels, brand);
            removeExact(id.searchableLabels, brand);
            if (containsWords(id.title, canonWords(brand))) {
                id.title = safe(id.category);
            }
        }
        if (!trustedObservedBrand(id)) {
            scrubGuessFromObservedFields(id, brand);
        }
    }

    static boolean trustedObservedBrand(Models.Identification id) {
        if (id == null || id.brandRoleConfidence < 85 || safe(id.brand).isEmpty()
                || !observedName(safe(id.brandEvidence).toLowerCase(Locale.ROOT))) {
            return false;
        }
        boolean physicalPackageIdentity = SealedProductIdentityPolicy
                .hasBoundManufacturer(id);
        boolean crossPhotoLogo = "visible_logo_cross_photo".equalsIgnoreCase(id.brandEvidence)
                && id.brandRoleConfidence >= 92 && containsIgnoreCase(id.brandLabels, id.brand)
                && id.photoViews.size() >= 2;
        return physicalPackageIdentity || crossPhotoLogo
                || brandLiterallyPresent(id, id.localScan, id.brand);
    }

    static String observedBrandOrEmpty(Models.Identification id) {
        return trustedObservedBrand(id) ? safe(id.brand) : "";
    }

    static boolean brandLiterallyPresent(Models.Identification id, Models.LocalScan local, String brand) {
        String n = canonWords(brand);
        if (n.isEmpty() || local == null) {
            return false;
        }
        // The multimodal response cannot corroborate its own brand claim by
        // echoing the same word in visible_labels. Local OCR is the independent
        // channel required before a visible brand becomes a hard constraint.
        String w = " " + canonWords(local.joinedText()) + " ";
        return w.contains(" " + n + " ");
    }

    static String neutralFingerprint(Models.Identification id) {
        if (id == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        append(b, "category", id.categoryKey);
        append(b, "labels", id.visibleLabels.toString());
        append(b, "layout", id.spatialSignature.toString());
        append(b, "facts", id.visualFacts.toString());
        append(b, "shape", id.visualFingerprint);
        String ob = observedBrandOrEmpty(id);
        if (!ob.isEmpty()) {
            append(b, "observed_brand", ob);
        }
        return clip(b.toString(), 2600);
    }

    static String neutralSearchSeed(Models.Identification id) {
        return UniversalSearchPlan.structureSeed(id);
    }

    static boolean queryContainsUnobservedBrand(Models.Identification id, String query) {
        if (id == null || query == null || safe(id.brand).isEmpty() || trustedObservedBrand(id)) {
            return false;
        }
        return (" " + canonWords(query) + " ").contains(" " + canonWords(id.brand) + " ");
    }

    static void scrubUnobservedOemLanguage(Models.Identification id) {
        if (id == null || trustedObservedBrand(id)) {
            return;
        }
        scrubGuessFromObservedFields(id, safe(id.brand));
    }

    static boolean containsOemLanguage(String raw) {
        return false;
    }

    private static void scrubGuessFromObservedFields(Models.Identification id, String brand) {
        if (brand.isEmpty()) {
            return;
        }
        final String n = canonWords(brand);
        id.visualFacts.removeIf(new Predicate() {
            @Override
            public final boolean test(Object obj) {
                return BrandBlindPolicy.containsWords((String) obj, n);
            }
        });
        id.spatialSignature.removeIf(new Predicate() {
            @Override
            public final boolean test(Object obj) {
                return BrandBlindPolicy.containsWords((String) obj, n);
            }
        });
        if (containsWords(id.visualFingerprint, n)) {
            id.visualFingerprint = "";
        }
    }

    private static void removeExact(List<String> values, String target) {
        if (values == null) {
            return;
        }
        final String wanted = canonWords(target);
        values.removeIf(new Predicate() {
            @Override
            public final boolean test(Object obj) {
                return canonWords((String) obj).equals(wanted);
            }
        });
    }

    public static boolean containsWords(String raw, String words) {
        return !words.isEmpty() && new StringBuilder().append(" ").append(canonWords(raw)).append(" ").toString().contains(new StringBuilder().append(" ").append(words).append(" ").toString());
    }

    private static boolean useful(String x) {
        if (x.length() < 3 || x.length() > 64) {
            return false;
        }
        String u = x.toUpperCase(Locale.ROOT);
        return !u.matches("(OK|ON|OFF|YES|NO|UP|DOWN|LEFT|RIGHT|MENU|BACK|HOME|POWER|START|STOP|RESET|SET|MODE|ENTER|CLEAR|AUTO)");
    }

    private static int informationWeight(String x) {
        String u = safe(x).toUpperCase(Locale.ROOT);
        int w = Math.min(36, u.length());
        if (u.contains(" ")) {
            w += 18;
        }
        if (u.matches(".*[A-Z].*\\d.*|.*\\d.*[A-Z].*")) {
            w += 24;
        }
        int words = u.split("\\s+").length;
        return w + Math.min(20, words * 4);
    }

    private static boolean containsIgnoreCase(List<String> xs, String v) {
        for (String x : xs) {
            if (x.equalsIgnoreCase(v)) {
                return true;
            }
        }
        return false;
    }

    private static boolean observedName(String e) {
        return e.equals("visible_logo") || e.equals("visible_logo_cross_photo")
                || e.equals("visible_brand_text") || e.equals("explicit_label")
                || e.equals("ocr_brand") || e.equals("physical_package_identity");
    }

    private static void append(StringBuilder b, String k, String v) {
        String v2 = safe(v);
        if (v2.isEmpty()) {
            return;
        }
        if (b.length() > 0) {
            b.append(" | ");
        }
        b.append(k).append('=').append(v2);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String clip(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n);
    }

    private static String canonWords(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
