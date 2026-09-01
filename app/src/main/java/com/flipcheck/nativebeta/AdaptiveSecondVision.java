package com.flipcheck.nativebeta;

import androidx.core.os.EnvironmentCompat;
import com.flipcheck.nativebeta.Models;
import com.flipcheck.nativebeta.OpenAiClient;
import com.flipcheck.nativebeta.PhotoProtocol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;
import org.json.JSONArray;
import org.json.JSONObject;

final class AdaptiveSecondVision {
    private static final double PRE_WEB_BUDGET_USD = 0.009d;

    private AdaptiveSecondVision() {
    }

    static boolean shouldRun(Models.Identification id, PhotoProtocol.Assessment photo, Models.Usage usage) {
        if (id == null || photo == null || photo.ready
                || !UniversalConsistencyGate.visionBudgetAvailable(usage)) {
            return false;
        }
        if ((usage != null && usage.costUsd >= 0.0065d) || id.categoryConfidence < 70) {
            return false;
        }
        String k = id.categoryKey == null ? "" : id.categoryKey;
        if (k.equals("card") || k.equals("coin") || k.equals("watch") || k.equals("book_media") || hasStrongObservedIdentifier(id)) {
            return false;
        }
        Models.CandidateScore top = top(id);
        if (top != null && ConfidencePolicy.isSpecific(top, id) && top.totalScore >= 86) {
            return false;
        }
        return true;
    }

    static void enrich(Models.Identification id, Models.LocalScan local, List<String> images, String details, OpenAiClient client, Models.Usage usage) throws Exception {
        if (images == null || images.isEmpty()) {
            return;
        }
        String prompt = buildPrompt(id, local, details);
        OpenAiClient.Response r = client.vision(images, prompt);
        if (usage != null) {
            usage.add(r.usage);
        }
        merge(id, r.payload);
        if (usage != null && usage.costUsd > PRE_WEB_BUDGET_USD) {
            id.decisionReason = append(id.decisionReason, "Seconda Vision eseguita; budget visuale vicino al tetto sub-cent: nessun'altra passata visuale automatica.");
        } else {
            id.decisionReason = append(id.decisionReason, "Seconda Vision indipendente usata per ridurre l'incertezza prima di chiedere un'altra foto; 0 Web Search.");
        }
    }

    private static String buildPrompt(Models.Identification id, Models.LocalScan local, String details) {
        return "SECONDA OPINIONE VISIVA INDIPENDENTE FlipCheck v0.43. Non fare web search e NON fidarti delle ipotesi della prima analisi. Identifica direttamente marca, famiglia e modello probabili dalla/e foto usando forma, layout, pulsanti, loghi e dettagli visivi. Non trasformare il tipo di oggetto in una falsa identita' esatta. Se non sai il modello, lascialo vuoto. Proponi massimo 3 candidati REALI e concreti, anche di marche diverse, con confidenza calibrata. Se riconosci solo la categoria, non inventare brand. Rispondi SOLO JSON: {fast_candidates:[{brand,family,model,confidence,candidate_facts:[...],reason}], summary:string}. Contesto osservato (non ipotesi): categoria=" + safe(id.category) + ", etichette=" + id.visibleLabels + ", fatti=" + id.visualFacts + ", firma=" + id.spatialSignature + ". OCR locale=" + truncate(local == null ? "" : local.joinedText(), 1800) + ". Dettagli utente=" + truncate(details, 400);
    }

    private static void merge(Models.Identification id, JSONObject payload) {
        if (payload == null) {
            return;
        }
        JSONArray a = payload.optJSONArray("fast_candidates");
        if (a == null) {
            a = payload.optJSONArray("candidates");
        }
        if (a == null) {
            return;
        }
        List<Models.CandidateScore> added = new ArrayList<>();
        for (int i = 0; i < a.length() && i < 3; i++) {
            JSONObject x = a.optJSONObject(i);
            if (x != null) {
                Models.CandidateScore n = new Models.CandidateScore();
                n.brand = clean(x.optString("brand", ""));
                n.family = clean(x.optString("family", ""));
                n.model = clean(x.optString("model", ""));
                int conf = clamp(x.optInt("confidence", 0));
                n.textScore = conf;
                n.layoutScore = conf;
                n.totalScore = Math.max(0, conf - 3);
                n.evidence = "Seconda Vision: " + clean(x.optString("reason", ""));
                JSONArray facts = x.optJSONArray("candidate_facts");
                if (facts != null) {
                    for (int j = 0; j < facts.length(); j++) {
                        String f = clean(facts.optString(j, ""));
                        if (!f.isEmpty()) {
                            n.candidateFacts.add(f);
                        }
                    }
                }
                if (ConfidencePolicy.isSpecific(n, id)) {
                    Models.CandidateScore existing = closest(id.candidates, n);
                    if (existing != null) {
                        existing.totalScore = Math.min(92, Math.max(existing.totalScore, conf) + 8);
                        existing.textScore = Math.max(existing.textScore, conf);
                        existing.layoutScore = Math.max(existing.layoutScore, conf);
                        existing.evidence = append(existing.evidence, n.evidence);
                        for (String f2 : n.candidateFacts) {
                            if (!containsIgnoreCase(existing.candidateFacts, f2)) {
                                existing.candidateFacts.add(f2);
                            }
                        }
                    } else {
                        Models.CandidateScore first = top(id);
                        if (first != null && sameBrand(first, n)) {
                            n.totalScore = Math.min(88, n.totalScore + 5);
                        }
                        added.add(n);
                    }
                }
            }
        }
        id.candidates.addAll(added);
        Collections.sort(id.candidates, Comparator.comparingInt(new ToIntFunction() {
            @Override
            public final int applyAsInt(Object obj) {
                return ((Models.CandidateScore) obj).totalScore;
            }
        }).reversed());
        if (id.candidates.size() > 1) {
            id.tournamentMargin = Math.max(0, id.candidates.get(0).totalScore - id.candidates.get(1).totalScore);
        }
        Models.CandidateScore top = top(id);
        if (top != null && ConfidencePolicy.isSpecific(top, id)) {
            id.visionIdentityConfidence = Math.min(88, Math.max(id.visionIdentityConfidence, top.totalScore));
            if (id.visionIdentityReason == null || id.visionIdentityReason.trim().isEmpty()) {
                id.visionIdentityReason = "Due letture visuali indipendenti hanno ristretto l'identita' preliminare; serve ancora la prova richiesta dal protocollo.";
            }
        }
        String summary = clean(payload.optString("summary", ""));
        if (!summary.isEmpty()) {
            id.visionCandidates.add("Seconda opinione: " + summary);
        }
    }

    private static Models.CandidateScore closest(List<Models.CandidateScore> xs, Models.CandidateScore n) {
        for (Models.CandidateScore c : xs) {
            if (c != null) {
                if (sameIdentity(c, n)) {
                    return c;
                }
                if (sameBrand(c, n) && similar(c.family, n.family)) {
                    return c;
                }
            }
        }
        return null;
    }

    private static boolean sameIdentity(Models.CandidateScore a, Models.CandidateScore b) {
        String strCanon = canon(a.model);
        String strCanon2 = canon(b.model);
        return (strCanon.isEmpty() || strCanon2.isEmpty() || !(strCanon.equals(strCanon2) || strCanon.contains(strCanon2) || strCanon2.contains(strCanon))) ? canon(a.displayName()).equals(canon(b.displayName())) : sameBrand(a, b);
    }

    private static boolean sameBrand(Models.CandidateScore a, Models.CandidateScore b) {
        String x = canon(a.brand);
        String y = canon(b.brand);
        return !x.isEmpty() && x.equals(y);
    }

    private static boolean similar(String a, String b) {
        String strCanon = canon(a);
        String strCanon2 = canon(b);
        return (strCanon.isEmpty() || strCanon2.isEmpty() || (!strCanon.contains(strCanon2) && !strCanon2.contains(strCanon))) ? false : true;
    }

    private static Models.CandidateScore top(Models.Identification id) {
        if (id.candidates.isEmpty()) {
            return null;
        }
        return id.candidates.get(0);
    }

    private static boolean hasStrongObservedIdentifier(Models.Identification id) {
        int p;
        for (String raw : id.visualFacts) {
            if (raw != null && (p = raw.indexOf(61)) > 0) {
                String k = raw.substring(0, p).trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
                String v = raw.substring(p + 1).trim();
                if (k.equals("model_code") || k.equals("part_number") || k.equals("sku") || k.equals("barcode")) {
                    if (meaningful(v)) {
                        return true;
                    }
                }
            }
        }
        if (id.localScan != null) {
            for (Models.Identifier x : id.localScan.identifiers) {
                String k2 = x.label == null ? "" : x.label.toUpperCase(Locale.ROOT);
                if (k2.startsWith("MODEL") || k2.equals("PN") || k2.equals("P/N") || k2.equals("PART") || k2.equals("SKU") || k2.equals("BARCODE")) {
                    if (meaningful(x.value)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean meaningful(String s) {
        if (s == null) {
            return false;
        }
        String x = s.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return (x.isEmpty() || x.equals(EnvironmentCompat.MEDIA_UNKNOWN) || x.equals("null") || x.equals("non_applicable") || x.equals("not_applicable")) ? false : true;
    }

    private static boolean containsIgnoreCase(List<String> xs, String v) {
        for (String x : xs) {
            if (x.equalsIgnoreCase(v)) {
                return true;
            }
        }
        return false;
    }

    private static String append(String a, String b) {
        return (a == null || a.trim().isEmpty()) ? b == null ? "" : b : (b == null || b.trim().isEmpty()) ? a : a + " " + b;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static int clamp(int x) {
        return Math.max(0, Math.min(100, x));
    }

    private static String canon(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n);
    }
}
