package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;
import org.json.JSONArray;
import org.json.JSONObject;

final class FinalistKiller {
    private static final double MAX_PRE_PHOTO_COST_USD = 0.07d;

    private FinalistKiller() {
    }

    static boolean shouldRun(Models.Identification id, Models.Usage usage) {
        // Superseded in v0.75 by VisualRetrievalEngine + UniversalConsistencyGate.
        // Keeping this legacy gate disabled prevents an additional, differently
        // calibrated web pass from overriding the evidence ledger.
        return false;
    }

    static void run(Models.Identification id, Models.LocalScan local, String details,
                    OpenAiClient client, Models.Usage usage) {
        // Defensive fail-safe for callers compiled against an older pipeline.
        // No candidate is promoted and no network request is made.
        if (id != null) {
            id.decisionReason = append(id.decisionReason,
                    "Legacy FinalistKiller ignorato: decide il Universal Consistency Gate v0.75.");
        }
    }

    static int lambda$run$0(Models.CandidateScore c) {
        if (c.hardRejected) {
            return -1;
        }
        return c.totalScore;
    }

    static void applyNonFatalWebFailure(Models.Identification id, boolean remoteFrontOnly) {
        if (id == null) {
            return;
        }
        if (remoteFrontOnly) {
            for (Models.CandidateScore c : live(id)) {
                if (specificModel(c.model)) {
                    c.totalScore = Math.min(c.totalScore, 72);
                    c.evidence = append(c.evidence, "finalist_killer_inconclusive=true");
                }
            }
        }
        refreshMargin(id);
        id.decisionReason = append(id.decisionReason, "Controllo indipendente non conclusivo: risultato mantenuto preliminare senza interrompere la scansione.");
    }

    private static String buildPrompt(Models.Identification id, Models.LocalScan local, String details, List<Models.CandidateScore> finalists) {
        StringBuilder f = new StringBuilder();
        for (int i = 0; i < finalists.size(); i++) {
            Models.CandidateScore c = finalists.get(i);
            if (i > 0) {
                f.append(" | ");
            }
            f.append(i + 1).append(") ").append(c.displayName()).append(" score_pre=").append(c.totalScore);
        }
        return "FLIPCHECK v0.48 FINALIST KILLER. Usa il web per FALSIFICARE i finalisti, non per confermare automaticamente il leader. Oggetto categoria=" + safe(id.categoryKey) + ". Etichette=" + id.visibleLabels + ". Firma=" + id.spatialSignature + ". Fatti=" + id.visualFacts + ". Fingerprint=" + clip(safe(id.visualFingerprint), 500) + ". OCR=" + clip(local == null ? "" : local.joinedText(), 900) + ". Finalisti=" + ((Object) f) + ". Dettagli utente=" + clip(safe(details), 400) + ". NON assumere che l'accordo fra piu' analisi Vision sia prova: deriva dalla stessa fotografia e dallo stesso modello AI. Cerca documentazione/manuali/cataloghi/ricambi/foto prodotto che permettano di verificare le caratteristiche rare osservate e la loro disposizione. Per telecomandi pesa soprattutto PAIR, VOICE, TOP PICKS, SOURCES, SETTINGS, SUBTITLE/TEXT, NETFLIX, tastierino e tasti colore con la loro posizione. Per un modello esatto una pagina replacement generica non basta: preferisci manuale OEM, catalogo ricambi con reference precisa, pagina prodotto/reference o almeno due fonti realmente indipendenti. Se nessun finalista spiega tutto, proponi al massimo UN challenger concreto. Non usare informazioni non presenti nella foto come se fossero osservate. OUTPUT CORTO OBBLIGATORIO: niente markdown, niente URL, niente citazioni testuali; le fonti sono gia' raccolte separatamente dal sistema. Massimo 3 assessments. Per ogni assessment: supports massimo 2 stringhe da massimo 90 caratteri; contradictions massimo 2 stringhe da massimo 90 caratteri; evidence massimo 140 caratteri. Per challenger: supports massimo 2, contradictions massimo 2, evidence massimo 140 caratteri. Restituisci SOLO JSON: {\"assessments\":[{\"model\":\"modello esattamente come nel finalista\",\"match_score\":0,\"contradiction_score\":0,\"independent_evidence_count\":0,\"supports\":[],\"contradictions\":[],\"evidence\":\"\"}],\"challenger\":{\"brand\":\"\",\"family\":\"\",\"model\":\"\",\"match_score\":0,\"independent_evidence_count\":0,\"supports\":[],\"contradictions\":[],\"evidence\":\"\"}}. match_score 0-100 misura compatibilita' specifica con TUTTO cio che e' osservato. contradiction_score 0-100 misura quanto una incompatibilita' documentata esclude il candidato. Non dare 90+ se il frontale e' condiviso da piu' revisioni indistinguibili.";
    }

    private static void mergeChallenger(Models.Identification id, JSONObject x, boolean remoteFrontOnly) {
        String brand = clean(x.optString("brand", ""));
        String family = clean(x.optString("family", ""));
        String model = clean(x.optString("model", ""));
        int match = clamp(x.optInt("match_score", 0));
        int independent = Math.max(0, Math.min(4, x.optInt("independent_evidence_count", 0)));
        if (model.isEmpty() || match < 60) {
            return;
        }
        Models.CandidateScore existing = find(id.candidates, model);
        if (existing == null) {
            existing = new Models.CandidateScore();
            existing.brand = brand;
            existing.family = family;
            existing.model = model;
            id.candidates.add(existing);
        }
        if (existing.brand.isEmpty()) {
            existing.brand = brand;
        }
        if (existing.family.isEmpty()) {
            existing.family = family;
        }
        int score = clamp(Math.round(match * 0.82f) + Math.min(12, independent * 3));
        if (remoteFrontOnly && independent < 2) {
            score = Math.min(score, 74);
        }
        existing.totalScore = Math.max(existing.totalScore, score);
        existing.webScore = Math.max(existing.webScore, match);
        existing.candidateFacts.addAll(strings(x.optJSONArray("supports")));
        existing.contradictions.addAll(strings(x.optJSONArray("contradictions")));
        existing.evidence = append(existing.evidence, "finalist_killer_challenger=true; independent=" + independent + "; " + clip(clean(x.optString("evidence", "")), 180));
    }

    private static Models.CandidateScore find(List<Models.CandidateScore> list, String model) {
        String strCanon = canon(model);
        if (strCanon.isEmpty()) {
            return null;
        }
        for (Models.CandidateScore c : list) {
            if (c != null) {
                String strCanon2 = canon(c.model);
                if (strCanon2.equals(strCanon)) {
                    return c;
                }
                if (strCanon2.length() >= 5 && strCanon.length() >= 5 && (strCanon2.contains(strCanon) || strCanon.contains(strCanon2))) {
                    return c;
                }
            }
        }
        return null;
    }

    private static List<Models.CandidateScore> live(Models.Identification id) {
        List<Models.CandidateScore> out = new ArrayList<>();
        if (id == null || id.candidates == null) {
            return out;
        }
        for (Models.CandidateScore c : id.candidates) {
            if (c != null && !c.hardRejected) {
                out.add(c);
            }
        }
        Collections.sort(out, Comparator.comparingInt(new ToIntFunction() {
            @Override
            public final int applyAsInt(Object obj) {
                return ((Models.CandidateScore) obj).totalScore;
            }
        }).reversed());
        return out;
    }

    private static void refreshMargin(Models.Identification id) {
        List<Models.CandidateScore> after = live(id);
        if (after.size() < 2) {
            if (after.size() != 1) {
                id.tournamentMargin = 0;
                return;
            } else {
                id.tournamentMargin = after.get(0).totalScore;
                return;
            }
        }
        id.tournamentMargin = Math.max(0, after.get(0).totalScore - after.get(1).totalScore);
    }

    private static boolean specificModel(String value) {
        if (value == null) {
            return false;
        }
        String x = value.trim().toLowerCase(Locale.ROOT);
        return (x.isEmpty() || x.equals("remote") || x.equals("remote control") || x.equals("smart tv remote") || x.equals("tv remote") || x.equals("telecomando") || x.equals("telecomando tv")) ? false : true;
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) {
            return out;
        }
        for (int i = 0; i < a.length(); i++) {
            String s = clip(clean(a.optString(i, "")), 120);
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static int clamp(int x) {
        return Math.max(0, Math.min(100, x));
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String canon(String s) {
        return safe(s).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String clip(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n);
    }

    private static String append(String a, String b) {
        return safe(a).isEmpty() ? safe(b) : safe(b).isEmpty() ? safe(a) : a + " " + b;
    }
}
