package com.flipcheck.nativebeta;

import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/** Selective, no-web second pass for evidence-rich near misses only. */
final class BorderlineIdentityAdjudicator {
    private static final double MAX_TOTAL_COST_USD = 0.0200d;
    private static final double RESERVED_TEXT_PASS_USD = 0.0035d;

    private BorderlineIdentityAdjudicator() {
    }

    static boolean eligible(Models.Identification id, Models.Usage usage) {
        if (id == null || id.marketReady || id.candidates.isEmpty()
                || usage == null || usage.requests != 1 || usage.webCalls != 1
                || usage.costUsd + RESERVED_TEXT_PASS_USD > MAX_TOTAL_COST_USD
                || !id.photoIdentityComplete || !id.photoIdentityPhysicalBinding
                || id.photoIdentityOverlayOrWatermark || id.photoIdentityConfidence < 92
                || id.photoIdentityFields.size() < 4) {
            return false;
        }
        Models.CandidateScore c = id.candidates.get(0);
        return c != null && !c.hardRejected
                && !UniversalConsistencyGate.strongCandidateConflict(c)
                && factTrue(c, "source_grounded")
                && factTrue(c, "same_entity_role")
                && !factTrue(c, "relationship_only")
                && factTrue(c, "disproof_passed")
                && factInt(c, "source_identity_confidence") >= 85
                && factTrue(c, "exact_reference_complete")
                && (!c.model.isEmpty() || !c.probableReference.isEmpty());
    }

    static String prompt(Models.Identification id) {
        Models.CandidateScore c = id.candidates.get(0);
        StringBuilder sources = new StringBuilder();
        for (Models.Source source : id.sources) {
            if (source == null) continue;
            append(sources, source.title + " | " + clip(source.snippet, 260));
            if (sources.length() > 1000) break;
        }
        return "CATEGORY=" + safe(id.category)
                + "\nPHOTO_IDENTITY=" + clip(id.photoIdentityName, 320)
                + "\nPHYSICAL_FIELDS=" + clip(id.photoIdentityFields.toString(), 1500)
                + "\nPHOTO_VIEWS=" + clip(id.photoViews.toString(), 300)
                + "\nCANDIDATE=" + c.displayName()
                + "\nPROBABLE_REFERENCE=" + safe(c.probableReference)
                + "\nCANDIDATE_FACTS=" + clip(c.candidateFacts.toString(), 1800)
                + "\nCANDIDATE_EVIDENCE=" + clip(c.evidence, 900)
                + "\nRETRIEVED_SOURCES=" + sources;
    }

    static boolean apply(Models.Identification id, OpenAiClient.Response response) {
        if (id == null || response == null || !response.complete
                || !safe(response.parseError).isEmpty() || response.payload == null
                || id.candidates.isEmpty()) {
            return false;
        }
        JSONObject p = response.payload;
        if (!p.optBoolean("supported", false)
                || !p.optBoolean("same_entity", false)
                || p.optBoolean("contradiction", true)
                || p.optInt("identity_confidence", 0) < 92) {
            return false;
        }
        Models.CandidateScore c = id.candidates.get(0);
        if (!identityCompatible(id, c, p.optString("normalized_identity", ""))) {
            return false;
        }
        setFact(c, "second_adjudication_supported", "true");
        setFact(c, "photo_identity_supported", "true");
        setFact(c, "photo_identity_matched_count",
                String.valueOf(id.photoIdentityFields.size()));
        if (CollectibleCardIdentityPolicy.canConfirm(id, c)) {
            CollectibleCardIdentityPolicy.confirm(id, c);
        } else if (SealedProductIdentityPolicy.canConfirmCommercialSku(id, c)) {
            SealedProductIdentityPolicy.confirmCommercialSku(id, c);
        } else if (PhotoIdentityPolicy.canConfirm(id, c, null, Math.max(8, id.tournamentMargin))) {
            PhotoIdentityPolicy.confirm(id, c);
        } else {
            return false;
        }
        id.decisionReason = "CONFIRMED v1.03: seconda verifica visiva selettiva, senza nuova ricerca web.";
        return true;
    }

    private static boolean identityCompatible(Models.Identification id,
                                              Models.CandidateScore c,
                                              String normalized) {
        String hay = canon(safe(normalized) + " " + c.displayName() + " "
                + c.probableReference + " " + c.evidence);
        int matched = 0;
        for (String raw : id.photoIdentityFields) {
            String value = fieldValue(raw);
            String key = canon(value);
            if (key.length() >= 3 && hay.contains(key)) {
                matched++;
            }
        }
        return matched >= Math.min(4, id.photoIdentityFields.size());
    }

    private static void setFact(Models.CandidateScore c, String key, String value) {
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        c.candidateFacts.removeIf(raw -> safe(raw).toLowerCase(Locale.ROOT).startsWith(prefix));
        c.candidateFacts.add(key + "=" + value);
    }

    private static boolean factTrue(Models.CandidateScore c, String key) {
        return "true".equalsIgnoreCase(fact(c, key));
    }

    private static int factInt(Models.CandidateScore c, String key) {
        try { return Integer.parseInt(fact(c, key)); } catch (Exception ignored) { return 0; }
    }

    private static String fact(Models.CandidateScore c, String key) {
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        for (String raw : c.candidateFacts) {
            String x = safe(raw);
            if (x.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return x.substring(x.indexOf('=') + 1).trim();
            }
        }
        return "";
    }

    private static String fieldValue(String raw) {
        String x = safe(raw);
        int split = x.indexOf('=');
        if (split < 0) split = x.indexOf(':');
        return split >= 0 ? safe(x.substring(split + 1)) : x;
    }

    private static void append(StringBuilder out, String value) {
        if (safe(value).isEmpty()) return;
        if (out.length() > 0) out.append(" || ");
        out.append(value);
    }

    private static String clip(String value, int max) {
        String x = safe(value);
        return x.length() <= max ? x : x.substring(0, max);
    }

    private static String canon(String value) {
        return safe(value).toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
