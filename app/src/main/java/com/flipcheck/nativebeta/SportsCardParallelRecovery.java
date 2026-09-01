package com.flipcheck.nativebeta;

import java.util.Locale;
import org.json.JSONObject;

/** Bounded second Web pass for a front/back sports card with one unresolved parallel axis. */
final class SportsCardParallelRecovery {
    private static final double MAX_TOTAL_COST_USD = 0.0250d;
    private static final double RESERVED_WEB_PASS_USD = 0.0125d;

    private SportsCardParallelRecovery() {}

    static boolean eligible(Models.Identification id, Models.Usage usage) {
        if (id == null || usage == null || usage.requests != 1 || usage.webCalls != 1
                || usage.costUsd + RESERVED_WEB_PASS_USD > MAX_TOTAL_COST_USD
                || !CollectibleCardIdentityPolicy.isCard(id)
                || CollectibleCardIdentityPolicy.isTradingCardGame(id)
                || id.photoIdentityOverlayOrWatermark || !frontAndBack(id)
                || id.candidates.isEmpty()) return false;
        if ("physical_card_tuple".equalsIgnoreCase(safe(id.modelProof))) {
            return false;
        }
        Models.CandidateScore top = id.candidates.get(0);
        return top != null && !top.hardRejected
                && factTrue(top, "source_grounded")
                && factTrue(top, "same_entity_role")
                && !factTrue(top, "relationship_only")
                && factTrue(top, "disproof_passed")
                && factTrue(top, "exact_reference_complete")
                && hasIdentityTuple(id)
                && parallelNeedsExactName(id, top);
    }

    static String prompt(Models.Identification id) {
        Models.CandidateScore c = id.candidates.get(0);
        return "CATEGORY=" + safe(id.category)
                + "\nPHOTO_IDENTITY=" + clip(id.photoIdentityName, 500)
                + "\nPHYSICAL_FIELDS=" + clip(id.photoIdentityFields.toString(), 1800)
                + "\nPHOTO_VIEWS=" + clip(id.photoViews.toString(), 300)
                + "\nCURRENT_CANDIDATE=" + clip(c.displayName(), 500)
                + "\nPROBABLE_REFERENCE=" + clip(c.probableReference, 500)
                + "\nCANDIDATE_FACTS=" + clip(c.candidateFacts.toString(), 1600)
                + "\nCURRENT_EVIDENCE=" + clip(c.evidence, 900);
    }

    static boolean apply(Models.Identification id, OpenAiClient.Response response) {
        if (id == null || response == null || !response.complete
                || !safe(response.parseError).isEmpty() || response.payload == null
                || id.candidates.isEmpty()) return false;
        JSONObject p = response.payload;
        String parallel = safe(p.optString("exact_parallel_name", ""));
        String identity = safe(p.optString("normalized_identity", ""));
        String sourceUrl = safe(p.optString("source_url", ""));
        if (!p.optBoolean("supported", false)
                || !p.optBoolean("same_physical_card", false)
                || !p.optBoolean("front_back_match", false)
                || !p.optBoolean("card_number_match", false)
                || !p.optBoolean("parallel_visually_distinguishable", false)
                || p.optInt("identity_confidence", 0) < 92
                || parallel.isEmpty() || identity.isEmpty() || sourceUrl.isEmpty()
                || unresolved(parallel) || unresolved(identity)
                || !safe(p.optString("contradiction", "")).isEmpty()
                || !sourcePresent(response, sourceUrl)) return false;
        Models.CandidateScore c = id.candidates.get(0);
        c.model = identity;
        c.probableReference = identity;
        c.probableReferenceConfidence = Math.max(c.probableReferenceConfidence,
                p.optInt("identity_confidence", 0));
        c.totalScore = Math.max(c.totalScore, 96);
        setFact(c, "exact_identity_supported", "true");
        setFact(c, "source_exact_reference", "true");
        setFact(c, "exact_reference_complete", "true");
        setFact(c, "visual_reference_checked", "true");
        setFact(c, "visual_match_confidence", String.valueOf(p.optInt("identity_confidence", 0)));
        setFact(c, "photo_identity_supported", "true");
        setFact(c, "exact_parallel_recovered", parallel);
        if (!"physical_card_tuple".equalsIgnoreCase(safe(id.modelProof))) {
            id.model = identity;
            id.modelConfidence = Math.min(97, Math.max(92, p.optInt("identity_confidence", 0)));
            id.modelProof = "exact_catalog_front_back_parallel";
        }
        id.marketReady = true;
        id.disproofPassed = true;
        id.nextPhotoRequest = "";
        id.nextPhotoReason = "";
        id.verificationSummary = "Carta sportiva verificata su fronte e retro; checklist, numero carta e pattern fisico confermano il parallel " + parallel + ".";
        id.decisionReason = "CONFIRMED v1.04: seconda ricerca mirata entro budget per il parallel sportivo esatto.";
        return true;
    }

    private static boolean hasIdentityTuple(Models.Identification id) {
        String x = canon(id.photoIdentityFields.toString());
        return (x.contains("PLAYER") || x.contains("SUBJECT"))
                && x.contains("CARD NUMBER") && (x.contains("PARALLEL") || x.contains("FOIL"));
    }

    private static boolean parallelNeedsExactName(Models.Identification id, Models.CandidateScore c) {
        String x = (safe(id.model) + " " + safe(c.model) + " " + safe(c.probableReference)
                + " " + safe(c.evidence) + " " + c.candidateFacts).toLowerCase(Locale.ROOT);
        return x.contains("parallel") && (x.contains("unresolved") || x.contains("unknown")
                || x.contains("green reflective") || x.contains("exact parallel name"));
    }

    private static boolean frontAndBack(Models.Identification id) {
        String x = id.photoViews.toString().toLowerCase(Locale.ROOT);
        return x.contains("front") && (x.contains("back") || x.contains("rear") || x.contains("retro"));
    }

    private static boolean sourcePresent(OpenAiClient.Response response, String url) {
        String wanted = canonUrl(url);
        for (Models.Source s : response.sources) {
            String actual = canonUrl(s == null ? "" : s.url);
            if (!actual.isEmpty() && (actual.equals(wanted) || actual.startsWith(wanted)
                    || wanted.startsWith(actual))) return true;
        }
        return false;
    }

    private static boolean unresolved(String value) {
        String x = safe(value).toLowerCase(Locale.ROOT);
        return x.contains("unresolved") || x.contains("unknown") || x.contains("probable")
                || x.contains("reflective") || x.contains("da verificare");
    }

    private static void setFact(Models.CandidateScore c, String key, String value) {
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        c.candidateFacts.removeIf(x -> safe(x).toLowerCase(Locale.ROOT).startsWith(prefix));
        c.candidateFacts.add(key + "=" + value);
    }

    private static boolean factTrue(Models.CandidateScore c, String key) {
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        for (String raw : c.candidateFacts) {
            String x = safe(raw).toLowerCase(Locale.ROOT);
            if (x.startsWith(prefix)) return "true".equals(x.substring(prefix.length()).trim());
        }
        return false;
    }

    private static String canon(String value) { return safe(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim(); }
    private static String canonUrl(String value) { return safe(value).toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^www\\.", "").replaceAll("[?#].*$", "").replaceAll("/$", ""); }
    private static String clip(String value, int max) { String x = safe(value); return x.length() <= max ? x : x.substring(0, max); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
