package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Selective no-Web visual veto for control devices whose web candidate was
 * inferred from shared control vocabulary without an actual reference-image
 * comparison.  It may retain a probable brand/family, but it never confirms an
 * exact model and never turns a visual hypothesis into a hard brand lock.
 */
final class VisualBrandFamilyRecovery {
    private static final double MAX_TOTAL_COST_USD = 0.0200d;
    private static final double RESERVED_VISUAL_PASS_USD = 0.0035d;

    private VisualBrandFamilyRecovery() {
    }

    static boolean eligible(Models.Identification id, Models.Usage usage) {
        if (id == null || usage == null || usage.requests != 1 || usage.webCalls != 1
                || usage.costUsd + RESERVED_VISUAL_PASS_USD > MAX_TOTAL_COST_USD
                || id.marketReady || id.candidates.isEmpty()
                || BrandBlindPolicy.trustedObservedBrand(id)
                || !safe(id.primaryIdentifier).isEmpty()
                || CollectibleCardIdentityPolicy.isCard(id)
                || SealedProductIdentityPolicy.isSealedRetailProduct(id)
                || isRemoteOrAccessory(id)) {
            return false;
        }
        Models.CandidateScore top = id.candidates.get(0);
        return top != null && !top.hardRejected
                && factTrue(top, "source_grounded")
                && !factTrue(top, "visual_reference_checked")
                && id.controlLabels.size() >= 3
                && id.visualFacts.size() >= 2
                && (!id.spatialSignature.isEmpty() || !safe(id.visualFingerprint).isEmpty());
    }

    static String prompt(Models.Identification id) {
        return "CATEGORY=" + safe(id.category)
                + "\nVISIBLE_CONTROLS=" + clip(id.controlLabels.toString(), 900)
                + "\nPHYSICAL_FACTS=" + clip(id.visualFacts.toString(), 1200)
                + "\nSPATIAL_LAYOUT=" + clip(id.spatialSignature.toString(), 900)
                + "\nSHAPE=" + clip(id.visualFingerprint, 700)
                + "\nPHOTO_VIEWS=" + clip(id.photoViews.toString(), 300);
    }

    static boolean apply(Models.Identification id, OpenAiClient.Response response) {
        if (id == null || response == null || !response.complete
                || !safe(response.parseError).isEmpty() || response.payload == null) {
            return false;
        }
        JSONObject p = response.payload;
        String brand = safe(p.optString("brand", ""));
        String family = safe(p.optString("family", ""));
        int confidence = clamp(p.optInt("confidence", 0));
        if (!p.optBoolean("applicable", false)
                || !p.optBoolean("same_foreground_object", false)
                || !p.optBoolean("visually_distinguishable", false)
                || p.optBoolean("model_code_visible", false)
                || !safe(p.optString("contradiction", "")).isEmpty()
                || brand.isEmpty() || family.isEmpty() || confidence < 90) {
            return false;
        }

        List<Models.CandidateScore> keep = new ArrayList<>();
        for (Models.CandidateScore c : id.candidates) {
            if (c == null) continue;
            boolean differentBrand = !safe(c.brand).isEmpty()
                    && !BrandAnchorPolicy.sameBrand(brand, c.brand);
            int referenceVisualConfidence = factInt(c, "visual_match_confidence");
            boolean directReferenceVisual = factTrue(c, "visual_reference_checked")
                    && referenceVisualConfidence >= 85;
            if (differentBrand && !directReferenceVisual) {
                c.hardRejected = true;
                c.totalScore = 0;
                addOnce(c.hardViolations,
                        "marca incompatibile con il controllo visivo indipendente della scocca e del pannello");
                addOnce(c.contradictions,
                        "STRONG: la marca web deriva da testi/layout condivisi senza confronto immagine-riferimento");
                addOnce(c.candidateFacts, "visual_brand_family_veto=true");
                id.rejectedCandidates.add(c);
            } else {
                keep.add(c);
            }
        }
        id.candidates.clear();
        id.candidates.addAll(keep);

        id.brand = brand;
        id.brandEvidence = "visual_family_recovery";
        id.brandRoleConfidence = Math.max(id.brandRoleConfidence, Math.min(84, confidence));
        id.family = family;
        id.familyConfidence = Math.max(id.familyConfidence, Math.min(90, confidence));
        id.model = "";
        id.modelConfidence = 0;
        id.marketReady = false;
        id.disproofPassed = false;
        id.modelProof = "none";
        id.title = join(brand, family);
        id.nextPhotoRequest = "Fotografa da vicino il logo e la targhetta con MODEL/P/N, mantenendo visibile l'intero pannello comandi";
        id.nextPhotoReason = "Marca e famiglia sono riconoscibili dal design, ma il modello esatto richiede una marcatura fisica o un confronto diretto con un riferimento visivo.";
        id.verificationSummary = "Il candidato di marca concorrente è stato escluso: condivideva i testi dei comandi, ma non aveva un confronto immagine-riferimento.";
        addOnce(id.inferredEvidence, "Controllo visivo selettivo marca/famiglia="
                + join(brand, family) + " confidence=" + confidence);
        JSONArray cues = p.optJSONArray("distinctive_cues");
        if (cues != null) {
            for (int i = 0; i < cues.length() && i < 6; i++) {
                String cue = safe(cues.optString(i, ""));
                if (!cue.isEmpty()) addOnce(id.matchedVisualFacts, cue);
            }
        }
        return true;
    }

    /**
     * Fail-safe when the selective visual pass is inconclusive or malformed.
     * Text/control similarity may keep an internal lead, but it cannot expose a
     * competing manufacturer or family as the public result.
     */
    static void applyInconclusiveGuard(Models.Identification id) {
        if (id == null || BrandBlindPolicy.trustedObservedBrand(id)) return;
        for (Models.CandidateScore c : id.candidates) {
            if (c == null) continue;
            boolean directReferenceVisual = factTrue(c, "visual_reference_checked")
                    && factInt(c, "visual_match_confidence") >= 85;
            if (!directReferenceVisual && !safe(c.brand).isEmpty()) {
                c.totalScore = Math.min(c.totalScore, 49);
                setFact(c, "brand_identity_supported", "false");
                setFact(c, "family_identity_supported", "false");
                addOnce(c.candidateFacts, "public_brand_family_withheld=true");
            }
        }
        id.brand = "";
        id.family = "";
        id.model = "";
        id.modelConfidence = 0;
        id.familyConfidence = 0;
        id.marketReady = false;
        id.disproofPassed = false;
        id.modelProof = "none";
        id.title = safe(id.category).isEmpty() ? "Oggetto" : safe(id.category);
        id.nextPhotoRequest = "Fotografa da vicino il logo e la targhetta con MODEL/P/N, mantenendo visibile l'intero pannello comandi";
        id.nextPhotoReason = "I testi dei comandi sono condivisi da più produttori e il controllo visivo non ha separato con sicurezza la marca.";
        id.verificationSummary = "La marca proposta dalla ricerca è stata trattenuta internamente perché priva di confronto immagine-riferimento.";
    }

    private static boolean isRemoteOrAccessory(Models.Identification id) {
        String x = canon(join(id.categoryKey, id.category));
        return x.contains("REMOTE") || x.contains("TELECOM")
                || x.contains("ACCESSOR") || x.contains("RICAMBIO")
                || x.contains("REPLACEMENT");
    }

    private static boolean factTrue(Models.CandidateScore c, String key) {
        return "true".equalsIgnoreCase(fact(c, key));
    }

    private static int factInt(Models.CandidateScore c, String key) {
        try { return Integer.parseInt(fact(c, key)); } catch (Exception ignored) { return 0; }
    }

    private static String fact(Models.CandidateScore c, String key) {
        if (c == null) return "";
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        for (String raw : c.candidateFacts) {
            String x = safe(raw);
            if (x.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return safe(x.substring(x.indexOf('=') + 1));
            }
        }
        return "";
    }

    private static void addOnce(List<String> out, String value) {
        if (out == null || safe(value).isEmpty()) return;
        for (String old : out) if (safe(old).equalsIgnoreCase(safe(value))) return;
        out.add(value);
    }

    private static void setFact(Models.CandidateScore c, String key, String value) {
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        c.candidateFacts.removeIf(raw -> safe(raw).toLowerCase(Locale.ROOT).startsWith(prefix));
        c.candidateFacts.add(key + "=" + value);
    }

    private static String join(String... values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            String x = safe(value);
            if (x.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(x);
        }
        return out.toString();
    }

    private static String canon(String value) {
        return safe(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private static String clip(String value, int max) {
        String x = safe(value);
        return x.length() <= max ? x : x.substring(0, max);
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
