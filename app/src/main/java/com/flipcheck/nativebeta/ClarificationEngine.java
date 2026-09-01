package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

final class ClarificationEngine {
    private ClarificationEngine() {
    }

    static void refine(Models.Identification id, ClarificationPlanner.Plan plan, String selected,
                       String details, OpenAiClient client, Models.Usage usage) throws Exception {
        if (id == null || plan == null || client == null) {
            throw new IllegalArgumentException("Chiarimento non valido");
        }
        String key = clean(plan.factKey);
        String value = clean(selected);
        if (key.isEmpty() || value.isEmpty()
                || !ClarificationPlanner.isUserVerifiableKey(key)
                || !isAllowedAnswer(plan, value)) {
            throw new IllegalArgumentException("Valore di chiarimento non valido");
        }

        String confirmedFact = key + "=" + value;
        addOnce(id.userConfirmedFacts, confirmedFact);
        addOnce(id.hardConstraints, confirmedFact);
        id.marketReady = false;
        id.disproofPassed = false;

        List<Models.CandidateScore> survivors = new ArrayList<>();
        Models.CandidateScore exact = null;
        for (Models.CandidateScore candidate : new ArrayList<>(id.candidates)) {
            String declared = ClarificationPlanner.candidateFact(candidate, key);
            if (empty(declared)) {
                candidate.totalScore = Math.min(candidate.totalScore, 70);
                addOnce(candidate.candidateFacts, "user_confirmed_" + key + "=unknown");
                survivors.add(candidate);
                continue;
            }
            if (!ClarificationPlanner.sameValue(key, declared, value)) {
                candidate.hardRejected = true;
                candidate.totalScore = 0;
                addOnce(candidate.hardViolations, confirmedFact + " != " + declared);
                addOnce(candidate.contradictions, "STRONG: dato letto dall'utente incompatibile: "
                        + key + "=" + declared);
                if (!id.rejectedCandidates.contains(candidate)) {
                    id.rejectedCandidates.add(candidate);
                }
                continue;
            }
            candidate.hardRejected = false;
            candidate.hardMatchWeight = Math.max(60, candidate.hardMatchWeight);
            candidate.totalScore = Math.max(88, candidate.totalScore);
            addOnce(candidate.hardMatches, confirmedFact);
            addOnce(candidate.candidateFacts, "user_confirmed_match=true");
            survivors.add(candidate);
            if (exact == null || candidate.totalScore > exact.totalScore) {
                exact = candidate;
            }
        }

        id.candidates.clear();
        id.candidates.addAll(survivors);
        IdentificationEngine.sortCandidates(id);
        if (exact == null) {
            id.model = "";
            id.modelConfidence = 0;
            id.modelProof = "none";
            id.verificationSummary = "La risposta dell'utente esclude i candidati che dichiarano un valore diverso, ma nessun candidato rimasto documenta quel valore.";
            id.decisionReason = "NEED_ANOTHER_PHOTO: il chiarimento elimina ipotesi, non crea un'identita' esatta.";
            id.nextPhotoRequest = "Fotografa la marcatura completa che contiene " + key.replace('_', ' ')
                    + " oppure un altro dettaglio identificativo dell'oggetto";
            id.nextPhotoReason = "Serve collegare il valore confermato a un candidato reale e a una fonte esatta.";
            IdentificationEngine.finalizeOutput(id, null);
            return;
        }

        id.brand = empty(exact.brand) ? id.brand : exact.brand;
        id.family = empty(exact.family) ? id.family : exact.family;
        id.model = exact.model;
        id.modelConfidence = Math.min(84, Math.max(70, exact.totalScore));
        id.verificationSummary = "Dato fisico confermato; verifica source-backed dell'identita' esatta in corso.";

        if (!UniversalConsistencyGate.verificationBudgetAvailable(usage)) {
            id.marketReady = false;
            id.disproofPassed = false;
            id.modelProof = "none";
            id.verificationSummary = "Dato fisico confermato: i candidati incompatibili sono stati esclusi. Il limite di una Web Search per questa scansione impedisce una seconda verifica automatica.";
            id.decisionReason = "SMART CLARIFY v0.77: filtro locale applicato senza superare il budget 1 Vision + 1 Web.";
            id.nextPhotoRequest = "Fotografa l'etichetta completa che riporta "
                    + key.replace('_', ' ') + "=" + value;
            id.nextPhotoReason = "La conferma restringe il torneo, ma non certifica da sola il modello e non autorizza una seconda ricerca web.";
            IdentificationEngine.finalizeOutput(id, null);
            return;
        }

        OpenAiClient.Response verification = client.webStage("verify",
                buildPrompt(id, exact, key, value, details));
        IdentificationEngine.collectStage(id, usage, verification, "clarification-verify-v076");
        IdentificationEngine.applyVerification(id, verification.payload, exact, null);
        if (!id.marketReady) {
            id.modelProof = empty(id.modelProof) ? "none" : id.modelProof;
            if (id.nextPhotoRequest.isEmpty()) {
                id.nextPhotoRequest = "Fotografa l'etichetta o il dettaglio che distingua "
                        + exact.displayName() + " dalle varianti visivamente uguali";
                id.nextPhotoReason = "La conferma utente restringe il torneo, ma non sostituisce una fonte esatta e il disproof.";
            }
        }
        IdentificationEngine.finalizeOutput(id, null);
    }

    private static String buildPrompt(Models.Identification id, Models.CandidateScore top, String key, String value, String details) {
        return "FLIPCHECK v0.76 - VERIFICA FAIL-CLOSED DOPO CHIARIMENTO UTENTE. "
                + "L'utente ha letto fisicamente " + key + "=" + value + ". Questo fatto elimina "
                + "i candidati incompatibili, ma NON prova da solo marca, modello o variante. Verifica il candidato "
                + top.displayName() + " con una fonte reale che nomini esattamente la stessa identita'. "
                + "same_entity_role deve essere false per accessori, ricambi compatibili, host, confezioni o prodotti correlati. "
                + "exact_identity_supported=true solo se la fonte collega esplicitamente il candidato e il valore confermato. "
                + "visual_reference_checked=true solo se hai realmente confrontato una foto associata a quella fonte. "
                + "Esegui DISPROVE e indica la migliore alternativa ancora compatibile. Informazione mancante=UNKNOWN, non match. "
                + "Rispondi SOLO JSON: {\"confirmed\":false,\"same_entity_role\":true,\"relationship_only\":false,"
                + "\"exact_identity_supported\":false,\"source_identity_confidence\":0,\"visual_reference_checked\":false,"
                + "\"visual_match_confidence\":0,\"conflict_level\":\"none\",\"conflict_evidence_confidence\":0,"
                + "\"attribute_conflicts\":[],\"brand\":\"\",\"family\":\"\",\"model\":\"\","
                + "\"model_proof\":\"none\",\"matched_visual_facts\":[],\"matched_layout_tokens\":[],"
                + "\"contradictions\":[],\"disproof_passed\":false,\"strongest_alternative\":\"\","
                + "\"evidence\":\"\",\"next_photo_request\":\"\",\"next_photo_reason\":\"\"}. "
                + "model_proof: direct_product_page|exact_manual|exact_catalog|exact_retailer|exact_identifier|weak|none."
                + "\nCategoria=" + id.categoryKey + "\nFATTO_UTENTE=" + key + "=" + value
                + "\nHARD_CONSTRAINTS=" + id.hardConstraints + "\nCandidati superstiti="
                + candidates(id.candidates) + "\nEtichette foto=" + id.visibleLabels
                + "\nFatti foto=" + id.visualFacts + "\nDettagli utente=" + (details == null ? "" : details);
    }

    private static boolean isAllowedAnswer(ClarificationPlanner.Plan plan, String selected) {
        for (String option : plan.options) {
            if (ClarificationPlanner.sameValue(plan.factKey, option, selected)) {
                return true;
            }
        }
        return false;
    }

    private static void addOnce(List<String> values, String value) {
        if (values == null || empty(value)) {
            return;
        }
        for (String old : values) {
            if (value.equalsIgnoreCase(old)) {
                return;
            }
        }
        values.add(value);
    }

    private static String candidates(List<Models.CandidateScore> xs) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < xs.size() && i < 4; i++) {
            if (b.length() > 0) {
                b.append(" || ");
            }
            Models.CandidateScore c = xs.get(i);
            b.append(c.displayName()).append(" facts=").append(c.candidateFacts);
        }
        return b.toString();
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) {
            return out;
        }
        for (int i = 0; i < a.length(); i++) {
            String s = clean(a.optString(i, ""));
            if (!empty(s)) {
                out.add(s);
            }
        }
        return out;
    }

    private static boolean hasUrl(List<Models.Source> xs, String url) {
        if (empty(url)) {
            return true;
        }
        for (Models.Source s : xs) {
            if (url.equalsIgnoreCase(s.url)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(List<String> xs, String value) {
        if (empty(value)) {
            return false;
        }
        for (String x : xs) {
            if (value.equalsIgnoreCase(x)) {
                return true;
            }
        }
        return false;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
