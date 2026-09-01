package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

final class ClarificationPlanner {
    /**
     * Closed allow-list of facts that a person can actually read on the physical
     * object or its label. Candidate facts also contain retrieval metadata
     * (search_lane, geometry_relation, scores, URLs, etc.); those values must
     * never become questions or user-confirmed identity constraints.
     */
    private static final String[] USER_VERIFIABLE_KEYS = {
            "model_code", "part_number", "product_code", "article_number",
            "catalog_number", "reference", "sku", "barcode", "serial_number",
            "version", "variant", "revision"
    };

    private ClarificationPlanner() {
    }

    static final class Plan {
        final String factKey;
        final List<String> options;
        final String question;

        Plan(String question, String factKey, List<String> options) {
            this.question = question;
            this.factKey = factKey;
            this.options = options;
        }
    }

    static Plan plan(Models.Identification id) {
        if (id == null || id.marketReady || UniversalRecognitionLadder.hasNoMorePhotos(id) || id.candidates.size() < 2) {
            return null;
        }
        int limit = Math.min(4, id.candidates.size());
        for (String key : USER_VERIFIABLE_KEYS) {
            Plan p = forKey(id, key, limit);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    private static Plan forKey(Models.Identification id, String key, int limit) {
        if (!isUserVerifiableKey(key)) {
            return null;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        int declared = 0;
        for (int i = 0; i < limit; i++) {
            String v = candidateFact(id.candidates.get(i), key);
            if (isUserVerifiableValue(key, v)) {
                declared++;
                values.add(v.trim());
            }
        }
        if (declared < 2 || values.size() < 2 || values.size() > 4) {
            return null;
        }
        return new Plan(questionFor(key), key, new ArrayList(values));
    }

    static String candidateFact(Models.CandidateScore c, String wantedKey) {
        int p;
        if (c == null) {
            return "";
        }
        String wk = normKey(wantedKey);
        if (identityBearingKey(wk)
                && (!factTrue(c, "exact_reference_complete")
                || !factTrue(c, "source_exact_reference")
                || !factTrue(c, "exact_identity_supported")
                || !factTrue(c, "same_entity_role")
                || factTrue(c, "relationship_only"))) {
            return "";
        }
        for (String raw : c.candidateFacts) {
            if (raw != null && (p = raw.indexOf(61)) > 0 && p < raw.length() - 1) {
                String k = normKey(raw.substring(0, p));
                String v = raw.substring(p + 1).trim();
                if (wk.equals(k) && !empty(v)) {
                    if (!wk.equals("model_code") || strictIdentifierText(v)) {
                        return v;
                    }
                }
            }
        }
        if (wk.equals("model_code")) {
            return modelFieldIdentifier(c.model);
        }
        return "";
    }

    private static boolean identityBearingKey(String key) {
        String k = normKey(key);
        return k.equals("model_code") || k.equals("part_number")
                || k.equals("product_code") || k.equals("article_number")
                || k.equals("catalog_number") || k.equals("reference")
                || k.equals("sku") || k.equals("barcode")
                || k.equals("serial_number");
    }

    static boolean isUserVerifiableKey(String key) {
        String k = normKey(key);
        for (String allowed : USER_VERIFIABLE_KEYS) {
            if (allowed.equals(k)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUserVerifiableValue(String key, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < 2 || value.length() > 56
                || CandidateSanitizer.imageArtifact(value)
                || CandidateSanitizer.infrastructureToken(value)) {
            return false;
        }
        String canonical = canon(value);
        if (canonical.isEmpty()
                || canonical.equals("TRUE") || canonical.equals("FALSE")
                || canonical.equals("UNKNOWN") || canonical.equals("NONE")
                || canonical.equals("SAME") || canonical.equals("COMPATIBLE")
                || canonical.equals("CONFLICT") || canonical.equals("STRUCTURE")
                || canonical.equals("LITERAL") || canonical.matches("LANE[0-9]+")) {
            return false;
        }
        String k = normKey(key);
        if (k.equals("barcode")) {
            return canonical.matches("[0-9]{6,18}");
        }
        if (k.equals("model_code") || k.equals("part_number")
                || k.equals("product_code") || k.equals("article_number")
                || k.equals("catalog_number") || k.equals("reference")
                || k.equals("sku") || k.equals("serial_number")) {
            return strictIdentifierText(value);
        }
        return true;
    }

    /**
     * A model field is a fallback only when the whole field is code-shaped. It
     * deliberately rejects descriptive names such as "65PUS8601-series remote"
     * so a related host/compatibility label cannot be promoted to a product code.
     */
    private static String modelFieldIdentifier(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < 3 || value.length() > 56
                || CandidateSanitizer.imageArtifact(value)
                || CandidateSanitizer.infrastructureToken(value)) {
            return "";
        }
        String[] parts = splitIdentifierAliases(value);
        if (parts.length == 0 || parts.length > 3) {
            return "";
        }
        for (String part : parts) {
            if (!strictCodeToken(part)) {
                return "";
            }
        }
        return value;
    }

    private static boolean strictIdentifierText(String value) {
        String[] parts = splitIdentifierAliases(value == null ? "" : value.trim());
        if (parts.length == 0 || parts.length > 3) {
            return false;
        }
        for (String part : parts) {
            if (!strictCodeToken(part)) {
                return false;
            }
        }
        return true;
    }

    private static boolean strictCodeToken(String raw) {
        String token = raw == null ? "" : raw.trim();
        if (token.matches("[0-9]{6,18}")) {
            return true;
        }
        return token.length() >= 3 && token.length() <= 28
                && token.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")
                && token.matches(".*[A-Za-z].*")
                && token.matches(".*[0-9].*");
    }

    private static String[] splitIdentifierAliases(String value) {
        String x = value == null ? "" : value.trim();
        if (x.matches(".*\\s+/\\s+.*") || x.contains("|") || x.contains(",") || x.contains(";")) {
            return x.split("\\s+/\\s+|\\s*[|,;]\\s*");
        }
        return new String[]{x};
    }

    private static boolean factTrue(Models.CandidateScore c, String wantedKey) {
        if (c == null) {
            return false;
        }
        String prefix = normKey(wantedKey) + "=";
        for (String raw : c.candidateFacts) {
            if (raw == null) {
                continue;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (normalized.startsWith(prefix)) {
                return "true".equalsIgnoreCase(raw.substring(raw.indexOf('=') + 1).trim());
            }
        }
        return false;
    }

    static boolean sameValue(String key, String a, String b) {
        String strCanon = canon(a);
        String strCanon2 = canon(b);
        if (strCanon.isEmpty() || strCanon2.isEmpty()) {
            return false;
        }
        return strCanon.equals(strCanon2) || (strCanon.length() >= 6 && strCanon2.contains(strCanon)) || (strCanon2.length() >= 6 && strCanon.contains(strCanon2));
    }

    private static String questionFor(String key) {
        String k = normKey(key);
        if (k.equals("model_code")) {
            return "Quale MODEL/codice prodotto leggi sull'oggetto, sull'etichetta o nel vano interno?";
        }
        if (k.equals("part_number")) {
            return "Quale P/N o PART NUMBER è visibile?";
        }
        if (k.equals("barcode")) {
            return "Quale codice a barre / EAN / UPC leggi?";
        }
        if (k.equals("serial_number")) {
            return "Quale seriale o numero identificativo è visibile?";
        }
        if (k.equals("sku") || k.equals("reference")) {
            return "Quale SKU/riferimento è riportato sull'oggetto?";
        }
        if (k.equals("product_code") || k.equals("article_number") || k.equals("catalog_number")) {
            return "Quale codice prodotto/articolo è riportato sull'oggetto?";
        }
        return "Quale versione o variante è scritta sull'oggetto o sull'etichetta?";
    }

    private static String normKey(String key) {
        String x = key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace('/', '_').replace(' ', '_');
        if (x.equals("model") || x.equals("model_number") || x.equals("model_no")) {
            return "model_code";
        }
        if (x.equals("pn") || x.equals("p_n") || x.equals("part")
                || x.equals("part_no") || x.equals("mpn")) {
            return "part_number";
        }
        if (x.equals("serial") || x.equals("serial_no")) {
            return "serial_number";
        }
        if (x.equals("ref")) {
            return "reference";
        }
        if (x.equals("ean") || x.equals("upc") || x.equals("gtin") || x.equals("isbn")) {
            return "barcode";
        }
        if (x.equals("product_number") || x.equals("item_number") || x.equals("item_code")) {
            return "product_code";
        }
        if (x.equals("article_no") || x.equals("article_code")) {
            return "article_number";
        }
        if (x.equals("catalog_no") || x.equals("catalog_code")) {
            return "catalog_number";
        }
        return x.replaceAll("[^a-z0-9_]", "");
    }

    private static String canon(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
