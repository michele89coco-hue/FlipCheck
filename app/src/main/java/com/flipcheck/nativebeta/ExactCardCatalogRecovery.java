package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Mandatory second-pass adjudication for every richly observed unresolved object. */
final class ExactCardCatalogRecovery {
    private static final double MAX_TOTAL_COST_USD = 0.0250d;
    private static final double RESERVED_TEXT_PASS_USD = 0.0018d;
    private static final double RESERVED_WEB_PASS_USD = 0.0112d;
    private ExactCardCatalogRecovery() {}

    static boolean eligible(Models.Identification id, Models.Usage usage) {
        if (id == null || usage == null || id.marketReady || usage.requests != 1
                || usage.webCalls != 1 || usage.costUsd + RESERVED_TEXT_PASS_USD > MAX_TOTAL_COST_USD
                || OverlayScopePolicy.blocksIdentity(id) || !richPhysicalEvidence(id)) return false;
        if (physicalTupleLocked(id)) {
            return false;
        }
        if (id.candidates.isEmpty()) return true;
        Models.CandidateScore top = id.candidates.get(0);
        return top == null || top.hardRejected || safe(id.model).isEmpty()
                || !factTrue(top, "source_grounded") || !factTrue(top, "exact_reference_complete")
                || CollectibleCardIdentityPolicy.isCard(id)
                    && CollectibleCardIdentityPolicy.variantUnresolved(id, top);
    }

    static boolean useSecondWeb(Models.Usage usage) {
        return usage != null && usage.costUsd + RESERVED_WEB_PASS_USD <= MAX_TOTAL_COST_USD;
    }

    static boolean attachImages(Models.Identification id) {
        return id != null && !CollectibleCardIdentityPolicy.isCard(id)
                && !SealedProductIdentityPolicy.isSealedRetailProduct(id);
    }

    static String prompt(Models.Identification id) {
        StringBuilder candidates = new StringBuilder();
        for (Models.CandidateScore c : id.candidates) {
            if (c == null || candidates.length() > 1400) continue;
            candidates.append(" || ").append(c.displayName()).append(" / ")
                    .append(c.probableReference).append(" / ").append(c.candidateFacts);
        }
        StringBuilder sources = new StringBuilder();
        for (Models.Source s : id.sources) {
            if (s == null || sources.length() > 3000) continue;
            sources.append(" || ").append(s.title).append(" | ").append(s.snippet)
                    .append(" | ").append(s.url);
        }
        return "OBSERVED_CATEGORY=" + safe(id.category) + "\nOBSERVED_BRAND=" + safe(id.brand)
                + "\nVISIBLE_LABELS=" + clip(id.visibleLabels.toString(), 2200)
                + "\nPHYSICAL_FACTS=" + clip(id.visualFacts.toString(), 2200)
                + "\nPHOTO_IDENTITY=" + clip(id.photoIdentityName, 900)
                + "\nPHYSICAL_IDENTITY_FIELDS=" + clip(id.photoIdentityFields.toString(), 2400)
                + "\nPHOTO_VIEWS=" + clip(id.photoViews.toString(), 600)
                + "\nSPATIAL_SIGNATURE=" + clip(id.spatialSignature.toString(), 1200)
                + "\nFIRST_PASS_CANDIDATES=" + clip(candidates.toString(), 1600)
                + "\nFIRST_PASS_SOURCES=" + clip(sources.toString(), 3200)
                + "\nFINAL_CONTRADICTIONS=" + clip(id.finalContradictions.toString(), 1000);
    }

    static boolean apply(Models.Identification id, OpenAiClient.Response response) {
        if (id == null || response == null || !response.complete
                || !safe(response.parseError).isEmpty() || response.payload == null) return false;
        JSONObject p = response.payload;
        boolean keepPhysicalTuple = physicalTupleLocked(id);
        String manufacturer = safe(p.optString("manufacturer", ""));
        String family = safe(p.optString("set_or_series", ""));
        String subject = safe(p.optString("subject", ""));
        String identity = safe(p.optString("normalized_identity", ""));
        String sourceUrl = safe(p.optString("source_url", ""));
        JSONArray matched = p.optJSONArray("matched_physical_fields");
        JSONArray contradictions = p.optJSONArray("contradictions");
        int matchedCount = matched == null ? 0 : matched.length();
        boolean card = CollectibleCardIdentityPolicy.isCard(id);
        boolean sealed = SealedProductIdentityPolicy.isSealedRetailProduct(id);
        boolean directVisual = p.optBoolean("visual_reference_checked", false)
                && p.optInt("visual_match_confidence", 0) >= 90;
        boolean composite = p.optBoolean("exact_composite_tuple_match", false)
                && matchedCount >= (card ? 6 : 5);
        if (!p.optBoolean("supported", false) || !p.optBoolean("exact_reference_complete", false)
                || !p.optBoolean("disproof_passed", false) || p.optInt("identity_confidence", 0) < 92
                || manufacturer.isEmpty() || family.isEmpty() || identity.length() < 8
                || unresolved(identity) || sourceUrl.isEmpty() || !sourcePresent(response, id, sourceUrl)
                || contradictions != null && contradictions.length() > 0) return false;
        if (card && (!p.optBoolean("same_physical_card", false) || subject.isEmpty()
                || !(directVisual || composite) || matchedCount < 4
                || !observed(id, manufacturer) || !observed(id, subject) || !observed(id, family))) return false;
        if (sealed && (!(directVisual || composite) || matchedCount < 4
                || !observed(id, manufacturer) || !observed(id, family))) return false;
        if (!card && !sealed && (!directVisual || !p.optBoolean("strongest_alternative_disproved", false)
                || matchedCount < 5 || !observed(id, manufacturer))) return false;

        Models.CandidateScore c = new Models.CandidateScore();
        c.brand = manufacturer; c.family = family; c.model = identity;
        c.probableReference = identity; c.probableReferenceConfidence = p.optInt("identity_confidence", 0);
        c.identifierScore = safe(p.optString("card_number", "")).isEmpty() ? 92 : 100;
        c.textScore = 98; c.layoutScore = Math.max(92, p.optInt("visual_match_confidence", 0));
        c.webScore = 96; c.totalScore = 98; c.evidence = safe(p.optString("evidence", ""));
        addFact(c, "source_url=" + sourceUrl); addFact(c, "source_grounded=true");
        addFact(c, "source_exact_reference=true"); addFact(c, "exact_reference_complete=true");
        addFact(c, "exact_identity_supported=true"); addFact(c, "same_entity_role=true");
        addFact(c, "relationship_only=false"); addFact(c, "disproof_passed=true");
        addFact(c, "visual_reference_checked=" + directVisual);
        addFact(c, "exact_composite_tuple_match=" + composite);
        addFact(c, "visual_match_confidence=" + p.optInt("visual_match_confidence", 0));
        addFact(c, "photo_identity_supported=true");
        addFact(c, "photo_identity_matched_count=" + matchedCount);
        if (matched != null) for (int i = 0; i < matched.length(); i++)
            addFact(c, "photo_feature=" + safe(matched.optString(i, "")));
        if (keepPhysicalTuple) {
            id.candidates.add(c);
            id.modelProof = empty(id.modelProof) ? "physical_card_tuple" : id.modelProof;
            id.photoIdentityComplete = !empty(id.photoIdentityName);
            id.photoIdentityPhysicalBinding = true;
            id.photoIdentityConfidence = Math.max(id.photoIdentityConfidence, c.layoutScore);
            id.disproofPassed = true;
            id.marketReady = true;
            id.nextPhotoRequest = "";
            id.nextPhotoReason = "";
            addFact(c, "physical_tuple_preserved= true");
            return true;
        }
        id.candidates.clear(); id.candidates.add(c); id.brand = manufacturer;
        id.brandEvidence = "verified_web"; id.brandRoleConfidence = Math.max(id.brandRoleConfidence, 95);
        id.family = family; id.familyConfidence = Math.max(id.familyConfidence, 95); id.model = identity;
        id.modelConfidence = Math.min(97, Math.max(92, p.optInt("identity_confidence", 0)));
        id.photoIdentityComplete = true; id.photoIdentityPhysicalBinding = true;
        id.photoIdentityKind = card ? "front_back_exact_catalog_tuple" : "exact_catalog_tuple";
        id.photoIdentityConfidence = Math.max(id.photoIdentityConfidence, c.layoutScore);
        id.marketReady = true; id.disproofPassed = true;
        id.modelProof = "universal_exact_identity_second_pass";
        id.nextPhotoRequest = ""; id.nextPhotoReason = "";
        id.verificationSummary = "Identità esatta recuperata dalla seconda verifica indipendente su tutti i dettagli fisici osservati.";
        id.decisionReason = "CONFIRMED v1.11: secondo passaggio universale obbligatorio entro 0,025 USD.";
        return true;
    }

    private static boolean physicalTupleLocked(Models.Identification id) {
        return id != null && !safe(id.model).isEmpty() && id.marketReady
                && "physical_card_tuple".equalsIgnoreCase(id.modelProof);
    }

    private static boolean richPhysicalEvidence(Models.Identification id) {
        int n = id.visibleLabels.size() + id.visualFacts.size() + id.photoIdentityFields.size()
                + id.spatialSignature.size();
        boolean brand = BrandBlindPolicy.trustedObservedBrand(id)
                || containsKey(id, "manufacturer", "publisher", "brand") || !safe(id.brand).isEmpty();
        boolean axis = containsKey(id, "model", "reference", "part_number", "product_line", "set",
                "series", "season", "year", "subject", "player", "format")
                || !safe(id.photoIdentityName).isEmpty();
        if (CollectibleCardIdentityPolicy.isCard(id)) {
            boolean subject = containsKey(id, "subject", "player", "athlete", "character")
                    || labelLikePerson(id.visibleLabels);
            return brand && subject && axis && n >= 7 && (frontAndBack(id) || id.photoIdentityComplete);
        }
        if (SealedProductIdentityPolicy.isSealedRetailProduct(id)) return brand && axis && n >= 6;
        return brand && axis && n >= 7 && (id.photoIdentityPhysicalBinding || id.spatialSignature.size() >= 4);
    }

    private static boolean containsKey(Models.Identification id, String... keys) {
        List<String> all = new ArrayList<>(); all.addAll(id.visualFacts); all.addAll(id.photoIdentityFields);
        for (String raw : all) for (String key : keys) if (fieldKey(raw).equals(key)) return true;
        return false;
    }
    private static boolean labelLikePerson(List<String> labels) { for (String raw : labels) if (safe(raw).matches("(?i)[A-ZÀ-ÖØ-Ý][A-ZÀ-ÖØ-öø-ÿ.'-]+\\s+[A-ZÀ-ÖØ-Ý][A-ZÀ-ÖØ-öø-ÿ.'-]+")) return true; return false; }
    private static boolean observed(Models.Identification id, String value) { String wanted = canon(value), hay = canon(safe(id.brand) + " " + id.visibleLabels + " " + id.visualFacts + " " + id.photoIdentityFields + " " + safe(id.photoIdentityName) + " " + (id.localScan == null ? "" : id.localScan.joinedText())); if (wanted.isEmpty()) return false; if (hay.contains(wanted)) return true; int n = 0; String[] ts = wanted.split(" "); for (String t : ts) if (t.length() >= 3 && hay.contains(t)) n++; return n >= Math.min(2, Math.max(1, ts.length)); }
    private static boolean sourcePresent(OpenAiClient.Response r, Models.Identification id, String url) { String w = canonUrl(url); for (Models.Source s : r.sources) if (urlMatches(w, s == null ? "" : s.url)) return true; for (Models.Source s : id.sources) if (urlMatches(w, s == null ? "" : s.url)) return true; return false; }
    private static boolean urlMatches(String w, String u) { String a = canonUrl(u); return !a.isEmpty() && (a.equals(w) || a.startsWith(w) || w.startsWith(a)); }
    private static boolean frontAndBack(Models.Identification id) { String x = id.photoViews.toString().toLowerCase(Locale.ROOT); return (x.contains("front") || x.contains("fronte")) && (x.contains("back") || x.contains("rear") || x.contains("reverse") || x.contains("retro")); }
    private static boolean unresolved(String x) { String v = safe(x).toLowerCase(Locale.ROOT); return v.contains("unresolved") || v.contains("unknown") || v.contains("da determinare") || v.contains("probable"); }
    private static String fieldKey(String raw) { String x = safe(raw); int p = x.indexOf('='); if (p < 1) p = x.indexOf(':'); return p < 1 ? "" : x.substring(0, p).trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
    private static void addFact(Models.CandidateScore c, String v) { if (!v.isEmpty() && !c.candidateFacts.contains(v)) c.candidateFacts.add(v); }
    private static boolean factTrue(Models.CandidateScore c, String key) { String p = key.toLowerCase(Locale.ROOT) + "="; for (String raw : c.candidateFacts) { String x = safe(raw).toLowerCase(Locale.ROOT); if (x.startsWith(p)) return "true".equals(x.substring(p.length()).trim()); } return false; }
    private static String canon(String v) { return safe(v).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " "); }
    private static String canonUrl(String v) { return safe(v).toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^www\\.", "").replaceAll("[?#].*$", "").replaceAll("/$", ""); }
    private static String clip(String v, int max) { String x = safe(v); return x.length() <= max ? x : x.substring(0, max); }
    private static String safe(String v) { return v == null ? "" : v.trim(); }
}
