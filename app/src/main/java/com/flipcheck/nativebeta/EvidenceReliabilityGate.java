package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.json.JSONObject;

final class EvidenceReliabilityGate {
    private EvidenceReliabilityGate() {
    }

    static int identifierConfidence(Models.LocalScan scan, Models.Identifier id) {
        if (id == null || empty(id.value)) {
            return 0;
        }
        String value = canon(id.value);
        if (value.length() < 3) {
            return 0;
        }
        String origin = safe(id.origin).toLowerCase(Locale.ROOT);
        String label = norm(id.label);
        if (origin.contains("barcode") || origin.contains("qr") || origin.contains("ean") || origin.contains("upc") || containsBarcode(scan, value)) {
            return 100;
        }
        int score = explicitIdentifierLabel(label) ? 42 + 18 : 42;
        if (value.length() >= 6) {
            score += 8;
        }
        if (value.matches(".*[A-Z].*") && value.matches(".*[0-9].*")) {
            score += 6;
        }
        if (value.length() <= 5) {
            score -= 12;
        }
        int exact = 0;
        Set<String> alternatives = new HashSet<>();
        if (scan != null) {
            for (Models.Identifier x : scan.identifiers) {
                if (x != null && !empty(x.value)) {
                    String cv = canon(x.value);
                    if (cv.equals(value)) {
                        exact++;
                    }
                    if (sameIdentifierFamily(label, norm(x.label))) {
                        alternatives.add(cv);
                    }
                }
            }
        }
        if (exact >= 2) {
            score += 28;
        } else if (countCompact(scan == null ? "" : scan.joinedText(), value) >= 2) {
            score += 18;
        }
        if (alternatives.size() >= 2 && exact < 2) {
            score -= 22;
        }
        if ((origin.contains("vision") || origin.contains("inferred")) && exact < 2) {
            score = Math.min(score, 45);
        }
        return clamp(score);
    }

    static boolean isHardIdentifier(Models.LocalScan scan, Models.Identifier id) {
        return identifierConfidence(scan, id) >= 88;
    }

    static int verificationConflictConfidence(JSONObject r) {
        if (r == null) {
            return 0;
        }
        return clamp(r.optInt("conflict_evidence_confidence", 0));
    }

    private static boolean containsBarcode(Models.LocalScan scan, String value) {
        if (scan == null) {
            return false;
        }
        for (String b : scan.barcodes) {
            if (canon(b).equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean explicitIdentifierLabel(String x) {
        return x.equals("model") || x.equals("model_number") || x.equals("model_code") || x.equals("pn") || x.equals("p_n") || x.equals("part") || x.equals("part_no") || x.equals("part_number") || x.equals("sku") || x.equals("ref") || x.equals("reference") || x.equals("type") || x.equals("item") || x.equals("ean") || x.equals("upc") || x.equals("barcode");
    }

    private static boolean sameIdentifierFamily(String a, String b) {
        return a.equals(b) || (explicitIdentifierLabel(a) && explicitIdentifierLabel(b));
    }

    private static int countCompact(String text, String value) {
        String t = canon(text);
        if (t.isEmpty() || value.isEmpty()) {
            return 0;
        }
        int n = 0;
        int p = 0;
        while (true) {
            int p2 = t.indexOf(value, p);
            if (p2 < 0) {
                return n;
            }
            n++;
            p = p2 + value.length();
        }
    }

    private static String norm(String s) {
        return safe(s).toLowerCase(Locale.ROOT).replace('/', '_').replace('-', '_').replace(' ', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static String canon(String s) {
        return safe(s).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static int clamp(int x) {
        return Math.max(0, Math.min(100, x));
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
