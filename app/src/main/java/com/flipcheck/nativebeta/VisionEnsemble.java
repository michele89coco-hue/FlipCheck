package com.flipcheck.nativebeta;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import androidx.core.os.EnvironmentCompat;
import com.flipcheck.nativebeta.GoogleReverseImageEngine;
import com.flipcheck.nativebeta.ImageMatchPolicy;
import com.flipcheck.nativebeta.Models;
import com.flipcheck.nativebeta.OpenAiClient;
import com.flipcheck.nativebeta.PhotoProtocol;
import com.flipcheck.nativebeta.VisualRetrievalEngine;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.json.JSONArray;
import org.json.JSONObject;

final class VisionEnsemble {
    private static final double HARD_CASE_PRE_WEB_BUDGET_USD = 0.04d;
    private static final double SINGLE_VIEW_VISUAL_BUDGET_USD = 0.028d;
    private static final double VISUAL_BUDGET_USD = 0.035d;

    private VisionEnsemble() {
    }

    static boolean shouldRun(Models.Identification id, PhotoProtocol.Assessment photo, Models.Usage usage) {
        if (id == null || photo == null || photo.ready
                || !UniversalConsistencyGate.visionBudgetAvailable(usage)) {
            return false;
        }
        if ((usage != null && usage.costUsd >= VISUAL_BUDGET_USD) || id.categoryConfidence < 55) {
            return false;
        }
        String k = safe(id.categoryKey).toLowerCase(Locale.ROOT);
        if (k.equals("card") || k.equals("coin") || k.equals("watch") || k.equals("book_media")) {
            return false;
        }
        return true;
    }

    static void enrich(Models.Identification id, Models.LocalScan local, List<String> images, String details, OpenAiClient client, Models.Usage usage) throws Exception {
        Models.LocalScan localScan;
        String str;
        OpenAiClient openAiClient;
        if (id == null || images == null || images.isEmpty()) {
            return;
        }
        CategoryFactPolicy.apply(id);
        PhotoProtocol.Assessment photo = PhotoProtocol.assess(id.categoryKey, id.category, id.photoViews, local, images.size());
        if (GoogleReverseImageEngine.hasKey() && !imageFirstAlreadyAttempted(id)) {
            GoogleReverseImageEngine.Outcome google = GoogleReverseImageEngine.run(id, images, client, usage);
            id.decisionReason = append(id.decisionReason, google.summary);
            boolean visualNeighbourhood = google.fullMatches > 0 || google.partialMatches > 0 || google.visuallySimilar >= 3;
            if (!google.candidates.isEmpty()) {
                for (Models.CandidateScore gc : google.candidates) {
                    if (gc != null) {
                        gc.candidateFacts.add("cloud_vision_retrieval=true");
                        id.candidates.add(gc);
                    }
                }
                ImageMatchPolicy.Decision imageDecision = ImageMatchPolicy.evaluate(id);
                id.tournamentMargin = Math.max(id.tournamentMargin, imageDecision.margin);
                id.visionIdentityReason = google.summary;
                id.visionIdentityConfidence = Math.max(id.visionIdentityConfidence, imageDecision.confidence);
                if (imageDecision.candidate == null || imageDecision.action != ImageMatchPolicy.Action.CONFIRM || !ImageMatchPolicy.publicCandidateAllowed(imageDecision.candidate)) {
                    id.decisionReason = append(id.decisionReason, "v0.62: Cloud Vision ha prodotto solo evidenza preliminare; continuo con ricerca web indipendente basata su OCR, layout e fatti visivi.");
                } else {
                    Models.CandidateScore top = imageDecision.candidate;
                    if (!empty(top.brand)) {
                        id.brand = top.brand;
                    }
                    if (!empty(top.family)) {
                        id.family = top.family;
                    }
                    id.model = top.model;
                    id.modelConfidence = imageDecision.confidence;
                    id.brandEvidence = "retrieval_match";
                    id.decisionReason = append(id.decisionReason, "v0.62: Cloud Vision ha prodotto un match fotografico forte; candidato mantenuto per conferma utente.");
                    CategoryFactPolicy.apply(id);
                    return;
                }
            } else if (visualNeighbourhood) {
                id.decisionReason = append(id.decisionReason, "v0.62: Cloud Vision ha trovato un vicinato visivo ma nessun nome/modello affidabile; continuo con OCR/layout web retrieval.");
            }
        }
        if (VisualRetrievalEngine.shouldRun(id, photo, images, usage)) {
            VisualRetrievalEngine.Outcome retrieval = VisualRetrievalEngine.run(id, images, details, client, usage);
            id.decisionReason = append(id.decisionReason, retrieval.summary);
            for (Models.CandidateScore preliminary : retrieval.preliminaryCandidates) {
                if (preliminary != null && !empty(preliminary.model)) {
                    boolean duplicate = false;
                    Iterator<Models.CandidateScore> it = id.candidates.iterator();
                    while (true) {
                        if (!it.hasNext()) {
                            break;
                        }
                        Models.CandidateScore existing = it.next();
                        if (existing != null && canon(existing.brand + existing.model).equals(canon(preliminary.brand + preliminary.model))) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        id.candidates.add(preliminary);
                    }
                }
            }
            if (retrieval.usable && retrieval.candidate != null) {
                id.candidates.clear();
                id.candidates.add(retrieval.candidate);
                id.tournamentMargin = retrieval.candidate.totalScore;
                id.brand = safe(retrieval.candidate.brand);
                id.family = safe(retrieval.candidate.family);
                id.model = safe(retrieval.candidate.model);
                id.brandEvidence = "retrieval_match";
                id.visionIdentityConfidence = Math.min(84, Math.max(55, retrieval.candidate.totalScore));
                id.visionIdentityReason = retrieval.summary;
                id.modelConfidence = Math.min(79, Math.max(55, retrieval.candidate.totalScore));
                CategoryFactPolicy.apply(id);
                id.decisionReason = append(id.decisionReason, "v0.54: candidato ottenuto da remote-part retrieval/reverse-image + confronto fotografico; ensemble generativo saltato.");
                return;
            }
            if (usage != null && usage.webCalls > 0) {
                id.model = "";
                id.modelConfidence = 0;
                id.decisionReason = append(id.decisionReason, "v0.54: remote-part retrieval senza match fotografico sufficiente; restano solo candidati di prodotto realmente recuperati.");
                return;
            }
        }
        double visualCeiling = 0 != 0 ? SINGLE_VIEW_VISUAL_BUDGET_USD : VISUAL_BUDGET_USD;
        List<Vote> votes = new ArrayList<>();
        captureExisting(id, votes);
        aggregate(id, votes);
        if (budget(usage, visualCeiling) && !strongExact(id)) {
            runRole("layout", id, local, roleImages(images, "layout"), details, client, usage, votes);
            aggregate(id, votes);
        }
        if (budget(usage, visualCeiling) && !strongExact(id)) {
            runRole("brand", id, local, roleImages(images, "brand"), details, client, usage, votes);
            aggregate(id, votes);
        }
        if (budget(usage, visualCeiling) && !strongExact(id)) {
            localScan = local;
            str = details;
            openAiClient = client;
            runRole("adjudicator", id, localScan, roleImages(images, "adjudicator"), str, openAiClient, usage, votes);
            aggregate(id, votes);
        } else {
            localScan = local;
            str = details;
            openAiClient = client;
        }
        if (0 == 0 && !strongExact(id) && canLayoutRescue(id) && usage != null && usage.webCalls == 0) {
            if (usage.costUsd < HARD_CASE_PRE_WEB_BUDGET_USD) {
                layoutWebRescue(id, localScan, str, openAiClient, usage);
            }
        }
        if (0 == 0 && FinalistKiller.shouldRun(id, usage)) {
            FinalistKiller.run(id, localScan, str, openAiClient, usage);
        }
        CategoryFactPolicy.apply(id);
        Models.CandidateScore best = bestPublicCandidate(id);
        if (!trustedBrand(id)) {
            if (best != null && brandVotes(best) >= 2 && !empty(best.brand)) {
                id.brand = best.brand;
                id.brandEvidence = "ensemble_consensus";
                if (!empty(best.family)) {
                    id.family = best.family;
                }
            } else {
                id.brand = "";
                id.family = "";
                id.model = "";
                id.brandEvidence = EnvironmentCompat.MEDIA_UNKNOWN;
            }
        }
        if (0 == 0) {
            id.decisionReason = append(id.decisionReason, "Fallback Vision Ensemble: " + (usage != null ? usage.visionCalls + " Vision, " + usage.webCalls + " Web" : "") + "; modello esatto solo con consenso e margine reale dopo contrasto.");
            return;
        }
        id.model = "";
        id.modelConfidence = 0;
        id.decisionReason = append(id.decisionReason, "Fallback ensemble v0.50: singola vista senza retrieval conclusivo; modello esatto lasciato vuoto.");
    }

    static Models.CandidateScore bestPublicCandidate(Models.Identification id) {
        if (id == null) {
            return null;
        }
        for (Models.CandidateScore c : id.candidates) {
            if (c != null && !c.hardRejected && retrievalCandidate(c) && c.totalScore >= 62) {
                return c;
            }
        }
        for (Models.CandidateScore c2 : id.candidates) {
            if (c2 != null && !c2.hardRejected && isStrongExactCandidate(c2, id)) {
                return c2;
            }
        }
        for (Models.CandidateScore c3 : id.candidates) {
            if (c3 != null && !c3.hardRejected && !empty(c3.brand) && brandVotes(c3) >= 2 && c3.totalScore >= 58) {
                return c3;
            }
        }
        return null;
    }

    static boolean isStrongExactCandidate(Models.CandidateScore c, Models.Identification id) {
        if (c == null || empty(c.model) || genericModel(c.model, id)) {
            return false;
        }
        if (retrievalCandidate(c)) {
            return c.totalScore >= 66;
        }
        int margin = marginFor(id, c);
        if (c.evidence != null && c.evidence.contains("finalist_killer_challenger=true")) {
            return c.totalScore >= 80 && margin >= 10;
        }
        if (c.evidence != null && c.evidence.contains("finalist_killer=true")) {
            return c.totalScore >= 80 && margin >= 10;
        }
        if (c.evidence != null && c.evidence.contains("web_rescue=true")) {
            return c.totalScore >= 82 && margin >= 12;
        }
        int votes = ensembleVotes(c);
        if (trustedBrand(id)) {
            return votes >= 2 && c.totalScore >= 76 && margin >= 8;
        }
        if (votes < 3 || c.totalScore < 78 || margin < 8) {
            return votes >= 2 && c.totalScore >= 82 && margin >= 10;
        }
        return true;
    }

    static boolean isExactCandidate(Models.CandidateScore c, Models.Identification id) {
        if (c == null || empty(c.model) || genericModel(c.model, id)) {
            return false;
        }
        return true;
    }

    private static boolean strongExact(Models.Identification id) {
        Models.CandidateScore c = bestPublicCandidate(id);
        return c != null && isStrongExactCandidate(c, id);
    }

    private static boolean imageFirstAlreadyAttempted(Models.Identification id) {
        if (id == null) {
            return false;
        }
        if (id.decisionReason != null && id.decisionReason.contains("IMAGE-FIRST v0.63")) {
            return true;
        }
        for (Models.CandidateScore c : id.candidates) {
            if (c != null) {
                for (String f : c.candidateFacts) {
                    if ("image_first_retrieval=true".equalsIgnoreCase(f)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean retrievalCandidate(Models.CandidateScore c) {
        if (c == null) {
            return false;
        }
        if (c.evidence != null && c.evidence.contains("Visual Retrieval:")) {
            return true;
        }
        for (String f : c.candidateFacts) {
            if (f != null && (f.equalsIgnoreCase("retrieval_image_match=true") || f.equalsIgnoreCase("google_web_detection=true"))) {
                return true;
            }
        }
        return false;
    }

    private static void captureExisting(Models.Identification id, List<Vote> votes) {
        for (Models.CandidateScore c : id.candidates) {
            if (c != null) {
                Vote v = new Vote();
                v.pass = "general";
                v.brand = safe(c.brand);
                v.family = safe(c.family);
                v.model = safe(c.model);
                v.confidence = Math.max(0, Math.min(100, c.totalScore));
                v.reason = safe(c.evidence);
                votes.add(v);
            }
        }
    }

    private static void runRole(String role, Models.Identification id, Models.LocalScan local, List<String> roleImages, String details, OpenAiClient client, Models.Usage usage, List<Vote> votes) throws Exception {
        String prompt = rolePrompt(role, id, local, details);
        String detail = role.equals("brand") ? "high" : "low";
        int maxOutput = role.equals("adjudicator") ? 650 : 720;
        OpenAiClient.Response r = client.visionRole(roleImages, prompt, detail, maxOutput);
        if (usage != null) {
            usage.add(r.usage);
        }
        if (r.parseError != null && !r.parseError.isEmpty()) {
            id.visionCandidates.add("Seconda opinione " + role + ": risposta strutturata incompleta, ignorata senza interrompere la scansione.");
        } else {
            collectPayload(role, r.payload, votes, id);
        }
    }

    private static String rolePrompt(String role, Models.Identification id, Models.LocalScan local, String details) {
        String common = "FLIPCHECK v0.50 FALLBACK VISION ENSEMBLE. Niente web. Categoria osservata=" + safe(id.categoryKey) + ". Etichette letterali=" + id.visibleLabels + ". Firma spaziale=" + id.spatialSignature + ". Fatti filtrati=" + id.visualFacts + ". OCR locale=" + clip(local == null ? "" : local.joinedText(), 1800) + ". Dettagli utente=" + safe(details) + ". ";
        if (role.equals("layout")) {
            return common + "RUOLO: SPECIALISTA DI LAYOUT. Ignora le marche ipotizzate dalla prima analisi. Usa geometria, ordine dei controlli, terminologia rara e proporzioni. Rispondi SOLO JSON: {\"brand\":\"\",\"family\":\"\",\"model\":\"\",\"identity_confidence\":0,\"reason\":\"\",\"candidates\":[{\"brand\":\"\",\"family\":\"\",\"model\":\"\",\"confidence\":0,\"reason\":\"\"}]}. Massimo 4 candidati. identity_confidence e confidence sono INTERI 0-100. reason molto breve. model deve essere una referenza commerciale concreta; se non sai il modello esatto, lascialo vuoto. ";
        }
        if (role.equals("brand")) {
            return common + "RUOLO: SPECIALISTA MARCA/OEM. Valuta design industriale, forma del guscio, vocabolario, tipografia e convenzioni OEM. Non scegliere una marca solo perche' comune. Rispondi SOLO JSON: {\"brand\":\"\",\"family\":\"\",\"model\":\"\",\"identity_confidence\":0,\"reason\":\"\",\"candidates\":[{\"brand\":\"\",\"family\":\"\",\"model\":\"\",\"confidence\":0,\"reason\":\"\"}]}. Massimo 4 candidati. identity_confidence e confidence sono INTERI 0-100. reason molto breve. model deve essere una referenza commerciale concreta; se non sai il modello esatto, lascialo vuoto. ";
        }
        String candidates = compactCandidates(id.candidates);
        return common + "RUOLO: GIUDICE FINALE VISIVO. Analisi precedenti: " + candidates + ". Non votare per maggioranza cieca: elimina cio' che non spiega i dettagli osservati. Rispondi SOLO JSON: {\"brand\":\"\",\"family\":\"\",\"model\":\"\",\"identity_confidence\":0,\"reason\":\"\",\"candidates\":[{\"brand\":\"\",\"family\":\"\",\"model\":\"\",\"confidence\":0,\"reason\":\"\"}]}. Massimo 4 candidati. identity_confidence e confidence sono INTERI 0-100. reason molto breve. model deve essere una referenza commerciale concreta; se non sai il modello esatto, lascialo vuoto. ";
    }

    private static void collectPayload(String pass, JSONObject payload, List<Vote> votes, Models.Identification id) {
        String str;
        if (payload == null) {
            return;
        }
        addVote(pass, payload.optString("brand", ""), payload.optString("family", ""), payload.optString("model", ""), payload.optInt("identity_confidence", 0), payload.optString("reason", ""), votes);
        JSONArray a = payload.optJSONArray("candidates");
        if (a == null) {
            str = pass;
        } else {
            for (int i = 0; i < a.length() && i < 4; i++) {
                JSONObject x = a.optJSONObject(i);
                if (x != null) {
                    addVote(pass, x.optString("brand", ""), x.optString("family", ""), x.optString("model", ""), x.optInt("confidence", 0), x.optString("reason", ""), votes);
                }
            }
            str = pass;
        }
        String summary = str + ": " + safe(payload.optString("brand", "")) + " " + safe(payload.optString("family", "")) + " " + safe(payload.optString("model", ""));
        if (!summary.trim().equals(str + ":")) {
            id.visionCandidates.add("Seconda opinione " + summary.trim());
        }
    }

    private static void addVote(String pass, String brand, String family, String model, int confidence, String reason, List<Vote> votes) {
        String brand2 = clean(brand);
        String family2 = clean(family);
        String model2 = clean(model);
        if (brand2.isEmpty() && family2.isEmpty() && model2.isEmpty()) {
            return;
        }
        Vote v = new Vote();
        v.pass = pass;
        v.brand = brand2;
        v.family = family2;
        v.model = model2;
        v.confidence = Math.max(0, Math.min(100, confidence));
        v.reason = clean(reason);
        votes.add(v);
    }

    private static void aggregate(Models.Identification id, List<Vote> votes) {
        Vote v;
        List<Aggregate> exact = new ArrayList<>();
        Map<String, Set<String>> brandPasses = new HashMap<>();
        Map<String, Vote> bestBrandVote = new HashMap<>();
        for (Vote v2 : votes) {
            if (!empty(v2.brand)) {
                String bk = canon(v2.brand);
                if (!bk.isEmpty()) {
                    brandPasses.computeIfAbsent(bk, new Function() {
                        @Override
                        public final Object apply(Object obj) {
                            return VisionEnsemble.lambda$aggregate$0((String) obj);
                        }
                    }).add(v2.pass);
                    Vote old = bestBrandVote.get(bk);
                    if (old == null || v2.confidence > old.confidence) {
                        bestBrandVote.put(bk, v2);
                    }
                }
            }
            if (!empty(v2.model) && !genericModel(v2.model, id)) {
                Aggregate a = findAggregate(exact, v2);
                if (a == null) {
                    a = new Aggregate(v2);
                    exact.add(a);
                }
                a.add(v2);
            }
        }
        List<Models.CandidateScore> merged = new ArrayList<>();
        for (Aggregate a2 : exact) {
            int votesCount = a2.passes.size();
            int brandCount = a2.brandKey.isEmpty() ? 0 : size(brandPasses.get(a2.brandKey));
            int avg = a2.count == 0 ? 0 : Math.round(a2.confidenceSum / a2.count);
            int score = clamp((Math.max(0, votesCount - 1) * 6) + avg + Math.min(6, brandCount * 2));
            Models.CandidateScore c = new Models.CandidateScore();
            c.brand = a2.brand;
            c.family = a2.family;
            c.model = a2.model;
            c.textScore = score;
            c.layoutScore = score;
            c.totalScore = score;
            c.evidence = "vision_ensemble=true; ensemble_votes=" + votesCount + "; brand_votes=" + brandCount + "; passes=" + a2.passes + "; " + a2.reason;
            merged.add(c);
        }
        for (Map.Entry<String, Set<String>> e : brandPasses.entrySet()) {
            int n = e.getValue().size();
            if (n >= 2 && (v = bestBrandVote.get(e.getKey())) != null) {
                boolean exactForBrand = false;
                Iterator<Models.CandidateScore> it = merged.iterator();
                while (true) {
                    if (it.hasNext()) {
                        if (canon(it.next().brand).equals(e.getKey())) {
                            exactForBrand = true;
                            break;
                        }
                    } else {
                        break;
                    }
                }
                if (!exactForBrand) {
                    Models.CandidateScore c2 = new Models.CandidateScore();
                    c2.brand = v.brand;
                    c2.family = v.family;
                    c2.model = "";
                    c2.totalScore = Math.min(76, (n * 9) + 46);
                    c2.textScore = c2.totalScore;
                    c2.layoutScore = c2.totalScore;
                    c2.evidence = "vision_ensemble=true; ensemble_votes=0; brand_votes=" + n + "; family_level=true; passes=" + e.getValue();
                    merged.add(c2);
                }
            }
        }
        Collections.sort(merged, Comparator.comparingInt(new ToIntFunction() {
            @Override
            public final int applyAsInt(Object obj) {
                return ((Models.CandidateScore) obj).totalScore;
            }
        }).reversed());
        id.candidates.clear();
        id.candidates.addAll(merged);
        if (merged.size() < 2) {
            if (merged.size() != 1) {
                id.tournamentMargin = 0;
                return;
            } else {
                id.tournamentMargin = merged.get(0).totalScore;
                return;
            }
        }
        id.tournamentMargin = Math.max(0, merged.get(0).totalScore - merged.get(1).totalScore);
    }

    static Set lambda$aggregate$0(String k) {
        return new HashSet();
    }

    private static Aggregate findAggregate(List<Aggregate> list, Vote v) {
        for (Aggregate a : list) {
            if (compatibleBrand(a.brand, v.brand)) {
                String strCanon = canon(a.model);
                String strCanon2 = canon(v.model);
                if (strCanon.equals(strCanon2)) {
                    return a;
                }
                if (strCanon.length() >= 5 && strCanon2.length() >= 5 && (strCanon.contains(strCanon2) || strCanon2.contains(strCanon))) {
                    return a;
                }
            }
        }
        return null;
    }

    private static void layoutWebRescue(Models.Identification id, Models.LocalScan local, String details, OpenAiClient client, Models.Usage usage) throws Exception {
        String prompt = "FLIPCHECK v0.50 FALLBACK LAYOUT RESCUE. Fai UNA ricerca web mirata usando combinazioni rare di testi e layout. Categoria=" + id.categoryKey + ". Etichette=" + id.visibleLabels + ". Firma=" + id.spatialSignature + ". Fingerprint=" + safe(id.visualFingerprint) + ". OCR=" + clip(local == null ? "" : local.joinedText(), 1600) + ". Candidati=" + compactCandidates(id.candidates) + ". Dettagli=" + safe(details) + ". Rispondi SOLO JSON {\"candidates\":[{\"brand\":\"\",\"family\":\"\",\"model\":\"\",\"identifier_score\":0,\"text_score\":0,\"layout_score\":0,\"web_score\":0,\"candidate_facts\":[],\"contradictions\":[],\"evidence\":\"\"}],\"next_photo_request\":\"\",\"next_photo_reason\":\"\"}.";
        OpenAiClient.Response r = client.webStage("discovery", prompt);
        usage.add(r.usage);
        id.webStages.add("layout-rescue");
        id.webQueries.addAll(r.queries);
        id.sources.addAll(r.sources);
        JSONArray a = r.payload == null ? null : r.payload.optJSONArray("candidates");
        if (a == null) {
            return;
        }
        for (int i = 0; i < a.length() && i < 4; i++) {
            JSONObject x = a.optJSONObject(i);
            if (x != null) {
                Models.CandidateScore c = new Models.CandidateScore();
                c.brand = clean(x.optString("brand", ""));
                c.family = clean(x.optString("family", ""));
                c.model = clean(x.optString("model", ""));
                if (!c.brand.isEmpty() || !c.family.isEmpty() || !c.model.isEmpty()) {
                    c.identifierScore = clamp(x.optInt("identifier_score", 0));
                    c.textScore = clamp(x.optInt("text_score", 0));
                    c.layoutScore = clamp(x.optInt("layout_score", 0));
                    c.webScore = clamp(x.optInt("web_score", 0));
                    c.candidateFacts.addAll(strings(x.optJSONArray("candidate_facts")));
                    c.contradictions.addAll(strings(x.optJSONArray("contradictions")));
                    int visual = Math.max(c.textScore, c.layoutScore);
                    int contradictionPenalty = Math.min(28, c.contradictions.size() * 9);
                    c.totalScore = clamp(Math.round((visual * 0.45f) + (c.webScore * 0.55f)) - contradictionPenalty);
                    c.evidence = "web_rescue=true; " + clean(x.optString("evidence", ""));
                    mergeWebCandidate(id.candidates, c);
                }
            }
        }
        Collections.sort(id.candidates, Comparator.comparingInt(new ToIntFunction() {
            @Override
            public final int applyAsInt(Object obj) {
                return ((Models.CandidateScore) obj).totalScore;
            }
        }).reversed());
        if (id.candidates.size() < 2) {
            if (id.candidates.size() == 1) {
                id.tournamentMargin = id.candidates.get(0).totalScore;
                return;
            }
            return;
        }
        id.tournamentMargin = Math.max(0, id.candidates.get(0).totalScore - id.candidates.get(1).totalScore);
    }

    private static void mergeWebCandidate(List<Models.CandidateScore> list, Models.CandidateScore incoming) {
        for (Models.CandidateScore old : list) {
            if (compatibleBrand(old.brand, incoming.brand)) {
                String strCanon = canon(old.model);
                String strCanon2 = canon(incoming.model);
                if (!strCanon.isEmpty() && !strCanon2.isEmpty() && (strCanon.equals(strCanon2) || strCanon.contains(strCanon2) || strCanon2.contains(strCanon))) {
                    old.totalScore = Math.max(old.totalScore, incoming.totalScore);
                    old.webScore = Math.max(old.webScore, incoming.webScore);
                    old.textScore = Math.max(old.textScore, incoming.textScore);
                    old.layoutScore = Math.max(old.layoutScore, incoming.layoutScore);
                    old.candidateFacts.addAll(incoming.candidateFacts);
                    old.contradictions.addAll(incoming.contradictions);
                    old.evidence = append(old.evidence, incoming.evidence);
                    return;
                }
            }
        }
        list.add(incoming);
    }

    private static boolean canLayoutRescue(Models.Identification id) {
        if (id == null) {
            return false;
        }
        String k = safe(id.categoryKey);
        boolean category = k.equals("remote_control") || k.equals("electronics") || k.equals("appliance") || k.equals("tool") || k.equals("automotive_part") || k.equals("footwear") || k.equals("apparel") || k.equals("other");
        if (category) {
            return id.visibleLabels.size() >= 3 || id.spatialSignature.size() >= 2 || !empty(id.visualFingerprint);
        }
        return false;
    }

    private static List<String> roleImages(List<String> images, String role) {
        List<String> out = new ArrayList<>();
        if (images == null || images.isEmpty()) {
            return out;
        }
        String full = images.get(0);
        if (role.equals("layout")) {
            out.add(full);
            String center = crop(full, 0.04f, 0.15f, 0.92f, 0.72f);
            if (!center.isEmpty()) {
                out.add(center);
            }
        } else if (role.equals("brand")) {
            String top = crop(full, 0.04f, 0.0f, 0.92f, 0.48f);
            if (!top.isEmpty()) {
                out.add(top);
            }
            out.add(full);
        } else {
            out.add(full);
            String top2 = crop(full, 0.04f, 0.0f, 0.92f, 0.48f);
            String center2 = crop(full, 0.04f, 0.18f, 0.92f, 0.68f);
            if (!top2.isEmpty()) {
                out.add(top2);
            }
            if (!center2.isEmpty()) {
                out.add(center2);
            }
        }
        return out;
    }

    private static String crop(String dataUrl, float xFrac, float yFrac, float wFrac, float hFrac) {
        int comma;
        if (dataUrl == null) {
            comma = -1;
        } else {
            try {
                comma = dataUrl.indexOf(44);
            } catch (Exception e) {
                return "";
            }
        }
        if (comma < 0) {
            return "";
        }
        byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), 0);
        Bitmap src = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (src == null) {
            return "";
        }
        int x = Math.max(0, Math.round(src.getWidth() * xFrac));
        int y = Math.max(0, Math.round(src.getHeight() * yFrac));
        int w = Math.min(src.getWidth() - x, Math.max(1, Math.round(src.getWidth() * wFrac)));
        int h = Math.min(src.getHeight() - y, Math.max(1, Math.round(src.getHeight() * hFrac)));
        Bitmap part = Bitmap.createBitmap(src, x, y, w, h);
        int max = Math.max(part.getWidth(), part.getHeight());
        Bitmap scaled = part;
        if (max > 900) {
            float scale = 900.0f / max;
            scaled = Bitmap.createScaledBitmap(part, Math.max(1, Math.round(part.getWidth() * scale)), Math.max(1, Math.round(part.getHeight() * scale)), true);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, bos);
        if (scaled != part) {
            scaled.recycle();
        }
        part.recycle();
        src.recycle();
        return "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), 2);
    }

    private static int ensembleVotes(Models.CandidateScore c) {
        return markerInt(c == null ? "" : c.evidence, "ensemble_votes=");
    }

    private static int brandVotes(Models.CandidateScore c) {
        return markerInt(c == null ? "" : c.evidence, "brand_votes=");
    }

    private static int markerInt(String text, String marker) {
        int p;
        if (text == null || (p = text.indexOf(marker)) < 0) {
            return 0;
        }
        int p2 = p + marker.length();
        int e = p2;
        while (e < text.length() && Character.isDigit(text.charAt(e))) {
            e++;
        }
        try {
            return Integer.parseInt(text.substring(p2, e));
        } catch (Exception e2) {
            return 0;
        }
    }

    private static int marginFor(Models.Identification id, Models.CandidateScore target) {
        if (id == null || target == null) {
            return 0;
        }
        int second = -1;
        for (Models.CandidateScore c : id.candidates) {
            if (c != null && c != target && !c.hardRejected) {
                second = Math.max(second, c.totalScore);
            }
        }
        return second < 0 ? target.totalScore : Math.max(0, target.totalScore - second);
    }

    private static boolean genericModel(String model, Models.Identification id) {
        if (empty(model)) {
            return true;
        }
        String low = model.toLowerCase(Locale.ROOT).trim();
        if (low.equals("remote") || low.equals("remote control") || low.equals("tv remote") || low.equals("smart tv remote") || low.equals("smart tv remote control") || low.equals("telecomando") || low.equals("telecomando tv") || low.equals("irrigation controller") || low.equals("sprinkler timer") || low.equals("controller") || low.equals("sports trading card") || low.equals("trading card") || low.equals("card")) {
            return true;
        }
        String cat = id == null ? "" : safe(id.category).toLowerCase(Locale.ROOT);
        return !cat.isEmpty() && low.equals(cat);
    }

    private static boolean frontOnlyRemote(Models.Identification id) {
        return (id == null || !"remote_control".equalsIgnoreCase(safe(id.categoryKey)) || id.photoProtocolReady) ? false : true;
    }

    private static boolean trustedBrand(Models.Identification id) {
        String e = id == null ? "" : safe(id.brandEvidence).toLowerCase(Locale.ROOT);
        return e.equals("visible_logo") || e.equals("visible_brand_text") || e.equals("explicit_label") || e.equals("ocr_brand") || e.equals("verified_web") || e.equals("retrieval_match");
    }

    private static boolean compatibleBrand(String a, String b) {
        if (empty(a) || empty(b)) {
            return true;
        }
        return canon(a).equals(canon(b));
    }

    private static String compactCandidates(List<Models.CandidateScore> list) {
        StringBuilder b = new StringBuilder();
        if (list != null) {
            for (int i = 0; i < list.size() && i < 6; i++) {
                if (i > 0) {
                    b.append(" | ");
                }
                Models.CandidateScore c = list.get(i);
                b.append(c.displayName()).append(" [").append(c.totalScore).append("]");
            }
        }
        int i2 = b.length();
        return i2 == 0 ? "nessuno" : b.toString();
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) {
            return out;
        }
        for (int i = 0; i < a.length(); i++) {
            String s = clean(a.optString(i, ""));
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static boolean budget(Models.Usage usage, double ceiling) {
        return usage == null || usage.costUsd < ceiling;
    }

    private static int size(Set<String> set) {
        if (set == null) {
            return 0;
        }
        return set.size();
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

    public static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static String canon(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String clip(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n);
    }

    private static String append(String a, String b) {
        return empty(a) ? safe(b) : empty(b) ? safe(a) : a + " " + b;
    }

    private static final class Vote {
        String brand;
        int confidence;
        String family;
        String model;
        String pass;
        String reason;

        private Vote() {
            this.pass = "";
            this.brand = "";
            this.family = "";
            this.model = "";
            this.reason = "";
        }
    }

    private static final class Aggregate {
        String brand;
        String brandKey;
        int confidenceSum;
        int count;
        String family;
        String model;
        final Set<String> passes = new HashSet();
        String reason = "";

        Aggregate(Vote v) {
            this.brand = "";
            this.family = "";
            this.model = "";
            this.brandKey = "";
            this.brand = v.brand;
            this.family = v.family;
            this.model = v.model;
            this.brandKey = VisionEnsemble.canon(v.brand);
        }

        void add(Vote v) {
            this.passes.add(v.pass);
            this.confidenceSum += v.confidence;
            this.count++;
            if (v.confidence >= 70 && !VisionEnsemble.empty(v.reason)) {
                this.reason = v.reason;
            }
            if (VisionEnsemble.empty(this.brand) && !VisionEnsemble.empty(v.brand)) {
                this.brand = v.brand;
                this.brandKey = VisionEnsemble.canon(v.brand);
            }
            if (VisionEnsemble.empty(this.family) && !VisionEnsemble.empty(v.family)) {
                this.family = v.family;
            }
            if (v.model.length() > this.model.length()) {
                this.model = v.model;
            }
        }
    }
}
