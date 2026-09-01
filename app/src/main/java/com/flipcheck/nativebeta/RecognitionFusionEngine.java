package com.flipcheck.nativebeta;

import androidx.core.os.EnvironmentCompat;
import com.flipcheck.nativebeta.GeminiImageSearchEngine;
import com.flipcheck.nativebeta.Models;
import com.flipcheck.nativebeta.OpenAiClient;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

final class RecognitionFusionEngine {
    private RecognitionFusionEngine() {
    }

    static final class Result {
        boolean attempted;
        String diagnostic = "";
        Models.Identification identification;

        Result() {
        }
    }

    static Result run(Models.LocalScan local, List<String> images, String details, OpenAiClient client, Models.Usage usage) {
        Result r = new Result();
        if (!GeminiImageSearchEngine.hasKey() || images == null || images.isEmpty()) {
            return r;
        }
        r.attempted = true;
        GeminiImageSearchEngine.Outcome g = GeminiImageSearchEngine.run(local, images, details, usage);
        r.diagnostic = g.diagnostic();
        if (g.searchError || g.identification == null) {
            return r;
        }
        if (!g.searchUsed || g.imageSearchCalls < 1) {
            Models.Identification safe = g.identification;
            clearIdentity(safe);
            safe.photoProtocolReady = false;
            safe.nextPhotoRequest = bestNextPhoto(g);
            safe.nextPhotoReason = "Gemini non ha eseguito una Google Image Search reale: nessun candidato viene promosso.";
            safe.verificationSummary = safe.nextPhotoReason;
            safe.decisionReason = append(safe.decisionReason, r.diagnostic + " FAIL-CLOSED v0.61: image_search non eseguita.");
            safe.webStages.add("recognition-fusion-v061-no-image-search");
            r.identification = safe;
            return r;
        }
        if (!g.completed) {
            return r;
        }
        Models.Identification id = g.identification;
        id.decisionReason = append(id.decisionReason, r.diagnostic);
        id.webStages.add("recognition-fusion-v061");
        Models.CandidateScore c = id.candidates.isEmpty() ? null : id.candidates.get(0);
        if (c == null || empty(c.model)) {
            clearIdentity(id);
            id.photoProtocolReady = false;
            id.nextPhotoRequest = bestNextPhoto(g);
            id.nextPhotoReason = "La ricerca immagini non ha prodotto un'identità concreta verificabile.";
            id.verificationSummary = id.nextPhotoReason;
            r.identification = id;
            return r;
        }
        if (client == null || !client.hasKey()) {
            c.candidateFacts.add("fusion_validator_unavailable=true");
            forceEvidenceRequest(id, g, "Manca una seconda verifica visiva indipendente: il candidato Google non viene promosso a identità.");
            r.identification = id;
            return r;
        }
        try {
            OpenAiClient.Response judge = client.visionRole(images, adjudicationPrompt(g, local, details), "high", 1300);
            usage.add(judge.usage);
            JSONObject p = judge.payload == null ? new JSONObject() : judge.payload;
            String judgeBrand = clean(p.optString("brand", ""));
            String judgeFamily = clean(p.optString("family", ""));
            String judgeModel = clean(p.optString("model", ""));
            int judgeConfidence = clamp(p.optInt("identity_confidence", 0));
            String judgeReason = clean(p.optString("reason", ""));
            boolean agrees = sameIdentity(g.brand, g.model.isEmpty() ? g.candidateLabel : g.model, judgeBrand, judgeModel) && judgeConfidence >= 78;
            boolean agrees2 = agrees;
            c.candidateFacts.add("fusion_vision_confidence=" + judgeConfidence);
            c.candidateFacts.add("fusion_judge_model=" + safeFact(judgeModel));
            if (agrees2) {
                c.candidateFacts.add("fusion_independent_vision=true");
                c.candidateFacts.add("fusion_status=agreed");
                int fused = Math.min(g.identityConfidence, judgeConfidence);
                c.totalScore = fused;
                c.textScore = 0;
                c.layoutScore = judgeConfidence;
                c.webScore = g.identityConfidence;
                id.modelConfidence = fused;
                id.visionIdentityConfidence = fused;
                id.visionIdentityReason = "Verifica visiva indipendente concorde: " + judgeReason;
                id.verificationSummary = "Google Image Search e verifica visiva indipendente convergono sullo stesso candidato. " + judgeReason;
                id.disproofPassed = judgeConfidence >= 84;
                id.decisionReason = append(id.decisionReason, "Fusion v0.58: retrieval=" + g.identityConfidence + "% independentVision=" + judgeConfidence + "% fused=" + fused + "%.");
                if (g.sameProduct && !g.sourceUrl.isEmpty() && fused >= 84) {
                    id.photoProtocolReady = true;
                    id.nextPhotoRequest = "";
                    id.nextPhotoReason = "";
                } else {
                    id.photoProtocolReady = false;
                    id.nextPhotoRequest = bestNextPhoto(g);
                    id.nextPhotoReason = "Le due analisi concordano, ma manca ancora una prova abbastanza forte per chiudere l'identità.";
                }
            } else {
                c.candidateFacts.add("fusion_rejected=true");
                c.candidateFacts.add("fusion_status=disagreed");
                c.contradictions.add("Verifica visiva indipendente non concorde: " + judgeBrand + " " + judgeFamily + " " + judgeModel + " (" + judgeConfidence + "%). " + judgeReason);
                id.rejectedCandidates.add(c);
                clearIdentity(id);
                id.photoProtocolReady = false;
                id.nextPhotoRequest = bestNextPhoto(g);
                id.nextPhotoReason = "Il candidato recuperato dal web non supera la verifica visiva indipendente.";
                id.verificationSummary = "Retrieval e verifica visiva indipendente NON convergono. Nessun modello viene mostrato come identità.";
                id.decisionReason = append(id.decisionReason, "Fusion v0.58 DISPROOF: candidato web respinto; independentVision=" + judgeConfidence + "% model=" + judgeModel + ".");
                id.disproofPassed = false;
            }
        } catch (Exception e) {
            c.candidateFacts.add("fusion_validator_error=true");
            forceEvidenceRequest(id, g, "Verifica visiva indipendente non disponibile: " + shortError(e));
            id.decisionReason = append(id.decisionReason, "Fusion v0.58 validator error: " + shortError(e));
        }
        r.identification = id;
        return r;
    }

    private static String adjudicationPrompt(GeminiImageSearchEngine.Outcome g, Models.LocalScan local, String details) {
        String candidate = clean(g.brand + " " + g.family + " " + (g.model.isEmpty() ? g.candidateLabel : g.model));
        String ocr = local == null ? "" : clean(local.joinedText());
        if (ocr.length() > 1400) {
            ocr = ocr.substring(0, 1400);
        }
        String d = clean(details);
        if (d.length() > 400) {
            d = d.substring(0, 400);
        }
        return "FLIPCHECK v0.58 INDEPENDENT VISUAL DISPROOF. A separate retrieval engine proposed this hypothesis: [" + candidate + "]. Treat it as an UNTRUSTED hypothesis. Do not use web search, filenames, article context, compatibility claims or prior AI authority. Inspect ONLY the user's original photos and literal OCR. Try to DISPROVE the hypothesis first. Compare identity-bearing geometry, proportions, control/port/button layout, logos actually visible, printed labels/codes and variant-specific details. If the exact hypothesis is visually supported with no material contradiction, return its exact brand/family/model. If evidence is insufficient, leave model empty and keep identity_confidence below 70. If the hypothesis conflicts with the photos, return the more plausible identity only if directly supported; otherwise leave model empty. identity_confidence means confidence that the exact proposed model is the photographed object, not confidence in the category. OCR=" + ocr + ". User details=" + d;
    }

    private static void forceEvidenceRequest(Models.Identification id, GeminiImageSearchEngine.Outcome g, String why) {
        Models.CandidateScore c = id.candidates.isEmpty() ? null : id.candidates.get(0);
        if (c != null) {
            c.totalScore = Math.min(c.totalScore, 64);
        }
        clearIdentity(id);
        id.photoProtocolReady = false;
        id.nextPhotoRequest = bestNextPhoto(g);
        id.nextPhotoReason = why;
        id.verificationSummary = why;
        id.disproofPassed = false;
    }

    private static void clearIdentity(Models.Identification id) {
        id.brand = "";
        id.brandEvidence = EnvironmentCompat.MEDIA_UNKNOWN;
        id.family = "";
        id.model = "";
        id.familyConfidence = 0;
        id.modelConfidence = 0;
        id.visionIdentityConfidence = 0;
        id.marketReady = false;
    }

    private static String bestNextPhoto(GeminiImageSearchEngine.Outcome g) {
        return (g == null || empty(g.nextPhoto)) ? "Aggiungi una foto dell'etichetta/codice identificativo oppure della vista opposta con dettagli distintivi leggibili." : g.nextPhoto;
    }

    private static boolean sameIdentity(String brandA, String modelA, String brandB, String modelB) {
        String strNorm = norm(modelA);
        String strNorm2 = norm(modelB);
        if (strNorm.length() < 3 || strNorm2.length() < 3) {
            return false;
        }
        boolean model = strNorm.equals(strNorm2) || (strNorm.length() >= 5 && strNorm2.contains(strNorm)) || (strNorm2.length() >= 5 && strNorm.contains(strNorm2));
        if (!model) {
            return false;
        }
        String strNorm3 = norm(brandA);
        String strNorm4 = norm(brandB);
        return strNorm3.isEmpty() || strNorm4.isEmpty() || strNorm3.equals(strNorm4) || strNorm3.contains(strNorm4) || strNorm4.contains(strNorm3);
    }

    private static String norm(String s) {
        return clean(s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String safeFact(String s) {
        return clean(s).replace('=', '-').replace('|', '-');
    }

    private static String append(String a, String b) {
        return empty(a) ? clean(b) : empty(b) ? clean(a) : clean(a) + " " + clean(b);
    }

    private static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static int clamp(int n) {
        return Math.max(0, Math.min(100, n));
    }

    private static String shortError(Exception e) {
        String s = e == null ? "errore sconosciuto" : clean(e.getMessage());
        if (s.isEmpty()) {
            s = e.getClass().getSimpleName();
        }
        return s.length() > 180 ? s.substring(0, 180) : s;
    }
}
