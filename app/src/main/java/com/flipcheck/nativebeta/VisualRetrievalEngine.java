package com.flipcheck.nativebeta;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Base64;
import com.flipcheck.nativebeta.ImageHarvester;
import com.flipcheck.nativebeta.Models;
import com.flipcheck.nativebeta.OpenAiClient;
import com.flipcheck.nativebeta.PhotoProtocol;
import com.flipcheck.nativebeta.VisualRetrievalEngine;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

final class VisualRetrievalEngine {
    private static final int MAX_COMPARE_IMAGES = 6;
    private static final int MAX_SOURCE_PAGES_PER_CANDIDATE = 4;
    private static final int MAX_WEB_CANDIDATES = 4;
    private static final double PRE_RETRIEVAL_COST_CAP = 0.024d;

    private VisualRetrievalEngine() {
    }

    static final class Outcome {
        boolean attempted;
        Models.CandidateScore candidate;
        int candidatesFound;
        int candidatesWithImages;
        int comparedImages;
        final List<Models.CandidateScore> preliminaryCandidates = new ArrayList();
        String summary = "";
        boolean usable;

        Outcome() {
        }
    }

    static final class Retrieved {
        String brand;
        String evidence;
        String family;
        String imageDataUrl;
        String imageMethod;
        String imageSourceUrl;
        String model;
        String sourceTitle;
        String sourceUrl;

        private Retrieved() {
            this.brand = "";
            this.family = "";
            this.model = "";
            this.sourceUrl = "";
            this.sourceTitle = "";
            this.imageDataUrl = "";
            this.imageSourceUrl = "";
            this.imageMethod = "";
            this.evidence = "";
        }

        String displayName() {
            StringBuilder b = new StringBuilder();
            VisualRetrievalEngine.append(b, this.brand);
            VisualRetrievalEngine.append(b, this.family);
            VisualRetrievalEngine.append(b, this.model);
            return b.length() == 0 ? this.sourceTitle : b.toString();
        }
    }

    static boolean shouldRun(Models.Identification out, PhotoProtocol.Assessment photo, List<String> images, Models.Usage usage) {
        if (out == null || images == null || images.isEmpty()
                || !UniversalConsistencyGate.webBudgetAvailable(usage)
                || !UniversalConsistencyGate.visionBudgetAvailable(usage)) {
            return false;
        }
        if (usage == null || usage.costUsd < 0.035d) {
            return (out.visibleLabels.isEmpty() && out.spatialSignature.isEmpty() && safe(out.visualFingerprint).length() < 12 && out.visualFacts.isEmpty()) ? false : true;
        }
        return false;
    }

    /* JADX WARN: Unreachable blocks removed: 2, instructions: 2 */
    static Outcome run(Models.Identification out, List<String> images, String details, OpenAiClient client, Models.Usage usage) {
        Outcome result = new Outcome();
        result.attempted = true;
        try {
            OpenAiClient.Response web = client.webStage("discovery", retrievalPrompt(out, details));
            if (usage != null) {
                usage.add(web.usage);
            }
            collectWebMetadata(out, web, "universal-retrieval-v064");
            List<Retrieved> candidates = parseCandidates(web.payload, web.sources, out);
            result.candidatesFound = candidates.size();
            for (int i = 0; i < candidates.size(); i++) {
                result.preliminaryCandidates.add(preliminaryScore(candidates.get(i), i));
            }
            if (candidates.isEmpty()) {
                result.summary = "Universal Retrieval v0.67: nessun candidato prodotto concreto e grounded trovato.";
                return result;
            }
            int prepared = 0;
            for (Retrieved c : candidates) {
                if (prepared >= 6) {
                    break;
                }
                ImageHarvester.Result harvested = harvestCandidateImage(c, web.sources);
                if (harvested.usable()) {
                    c.imageDataUrl = harvested.dataUrl;
                    c.imageSourceUrl = harvested.imageUrl;
                    c.imageMethod = harvested.method;
                    prepared++;
                }
            }
            result.candidatesWithImages = prepared;
            if (prepared == 0) {
                result.summary = "Universal Retrieval v0.67: " + candidates.size() + " candidati grounded, ma nessuna immagine prodotto confrontabile estratta.";
                return result;
            }
            // Keep the complete evidentiary view. Automatic foreground crops are useful
            // for retrieval, but unsafe as the sole comparison view because they can cut
            // away labels, connectors and variant-specific geometry.
            String query = images.get(0);
            List<String> compareImages = new ArrayList<>();
            compareImages.add(query);
            List<Retrieved> compared = new ArrayList<>();
            for (Retrieved c2 : candidates) {
                if (!c2.imageDataUrl.isEmpty()) {
                    compareImages.add(c2.imageDataUrl);
                    compared.add(c2);
                    if (compared.size() >= 6) {
                        break;
                    }
                }
            }
            result.comparedImages = compared.size();
            OpenAiClient.Response visual = client.visionMatch(compareImages, comparisonPrompt(out, compared), "high");
            if (usage != null) {
                usage.add(visual.usage);
            }
            if (visual.payload == null || visual.payload.length() == 0) {
                result.summary = "Universal Retrieval v0.67: confronto fotografico non disponibile.";
                return result;
            }
            applyVisualLedger(visual.payload, compared, result.preliminaryCandidates);
            int winnerIndex = visual.payload.optInt("winner_candidate_index", -1);
            JSONObject winner = matchAt(visual.payload, winnerIndex);
            Retrieved chosen = winnerIndex >= 0 && winnerIndex < compared.size()
                    ? compared.get(winnerIndex) : null;
            int confidence = winner == null ? 0 : Math.min(
                    clamp(visual.payload.optInt("identity_confidence", 0)),
                    clamp(winner.optInt("visual_similarity", 0)));
            boolean geometryConsistent = winner != null && winner.optBoolean("geometry_consistent", false);
            boolean sameEntity = winner != null && winner.optBoolean("same_entity_role", false);
            boolean exactVariant = winner != null && winner.optBoolean("exact_variant_distinguishable", false);
            int threshold = exactVariant ? (compared.size() >= 2 ? 82 : 90) : 72;
            if (chosen == null || !geometryConsistent || !sameEntity || confidence < threshold) {
                result.summary = "Universal Retrieval v0.75: " + compared.size()
                        + " fotografie grounded confrontate; nessun candidato supera insieme ruolo, geometria e soglia "
                        + threshold + "% (best=" + confidence + "%).";
                return result;
            }
            Models.CandidateScore score = new Models.CandidateScore();
            score.brand = clean(chosen.brand);
            score.family = clean(chosen.family);
            score.model = clean(chosen.model);
            score.identifierScore = 0;
            score.textScore = Math.max(42, Math.min(88, confidence));
            score.layoutScore = confidence;
            score.webScore = compared.size() >= 2 ? 80 : 68;
            score.totalScore = clamp(Math.round((confidence * 0.76f) + (score.webScore * 0.24f)));
            if (!exactVariant) {
                score.totalScore = Math.min(78, score.totalScore);
            }
            score.evidence = "Universal Retrieval v0.75: vista completa confrontata con "
                    + compared.size() + " fotografie grounded. "
                    + clean(winner.optString("reason", visual.payload.optString("reason", "")));
            score.candidateFacts.add(exactVariant
                    ? "retrieval_image_match=true" : "retrieval_family_match=true");
            score.candidateFacts.add("universal_web_candidate=true");
            score.candidateFacts.add("retrieval_source=" + chosen.sourceUrl);
            score.candidateFacts.add("retrieval_image_source=" + chosen.imageSourceUrl);
            score.candidateFacts.add("retrieval_visual_confidence=" + confidence);
            score.candidateFacts.add("retrieval_compared_images=" + compared.size());
            score.candidateFacts.add("geometry_relation=same");
            score.candidateFacts.add("same_entity_role=true");
            score.candidateFacts.add("relationship_only=false");
            score.candidateFacts.add("exact_variant_distinguishable=" + exactVariant);
            addVisualContradictions(score, winner);
            result.candidate = score;
            result.usable = true;
            result.summary = "Universal Retrieval v0.75: " + score.displayName() + " · match "
                    + confidence + "% · variante visivamente distinguibile=" + exactVariant + " · "
                    + compared.size() + " candidati fotografici su " + candidates.size() + " candidati web.";
            return result;
        } catch (Exception e) {
            result.summary = "Universal Retrieval v0.67 non disponibile: risultato lasciato prudente.";
            return result;
        }
    }

    private static Models.CandidateScore preliminaryScore(Retrieved c, int rank) {
        Models.CandidateScore score = new Models.CandidateScore();
        score.brand = clean(c.brand);
        score.family = clean(c.family);
        score.model = clean(c.model);
        score.identifierScore = 0;
        score.textScore = Math.max(42, 62 - (rank * 3));
        score.layoutScore = Math.max(35, 52 - (rank * 3));
        score.webScore = Math.max(48, 68 - (rank * 3));
        score.totalScore = Math.max(45, 59 - (rank * 3));
        score.evidence = "Universal Retrieval v0.67: candidato concreto trovato da ricerca iniziata senza marca inferita. " + clean(c.evidence);
        score.candidateFacts.add("universal_web_candidate=true");
        score.candidateFacts.add("retrieval_source=" + c.sourceUrl);
        return score;
    }

    private static ImageHarvester.Result harvestCandidateImage(Retrieved c, List<Models.Source> sources) {
        List<Models.Source> ordered = candidateSources(c, sources);
        int tried = 0;
        for (Models.Source s : ordered) {
            int tried2 = tried + 1;
            if (tried >= 4) {
                break;
            }
            if (s != null && isHarvestablePage(s.url)) {
                ImageHarvester.Result r = ImageHarvester.harvestBest(s.url, c.model, c.family, join(c.brand, c.family, c.model, s.title));
                if (r.usable()) {
                    if (c.sourceUrl.isEmpty()) {
                        c.sourceUrl = s.url;
                    }
                    if (c.sourceTitle.isEmpty()) {
                        c.sourceTitle = safe(s.title);
                    }
                    return r;
                }
            }
            tried = tried2;
        }
        return new ImageHarvester.Result();
    }

    private static List<Models.Source> candidateSources(final Retrieved c, List<Models.Source> sources) {
        List<Models.Source> out = new ArrayList<>();
        if (sources == null) {
            return out;
        }
        String requested = normalizeUrl(c.sourceUrl);
        Iterator<Models.Source> it = sources.iterator();
        while (true) {
            if (!it.hasNext()) {
                break;
            }
            Models.Source s = it.next();
            if (s != null && !safe(s.url).isEmpty() && !requested.isEmpty() && normalizeUrl(s.url).equals(requested)) {
                out.add(s);
                break;
            }
        }
        List<Models.Source> rest = new ArrayList<>();
        for (Models.Source s2 : sources) {
            if (s2 != null && !out.contains(s2)) {
                rest.add(s2);
            }
        }
        rest.sort(new Comparator() {
            @Override
            public final int compare(Object obj, Object obj2) {
                VisualRetrievalEngine.Retrieved retrieved = c;
                return Integer.compare(VisualRetrievalEngine.sourceFit(retrieved, (Models.Source) obj2), VisualRetrievalEngine.sourceFit(retrieved, (Models.Source) obj));
            }
        });
        for (Models.Source s3 : rest) {
            if (sourceFit(c, s3) >= 100) {
                out.add(s3);
            }
        }
        return out;
    }

    private static int sourceFit(Retrieved c, Models.Source s) {
        if (s == null) {
            return 0;
        }
        String hay = canon(safe(s.title) + " " + safe(s.snippet) + " " + safe(s.url));
        int score = 0;
        String model = canon(c.model);
        String brand = canon(c.brand);
        String family = canon(c.family);
        if (!model.isEmpty() && hay.contains(model)) {
            score = 0 + 100;
        }
        if (!brand.isEmpty() && hay.contains(brand)) {
            score += 22;
        }
        if (!family.isEmpty() && hay.contains(family)) {
            score += 28;
        }
        String u = lower(s.url);
        if (u.contains("product") || u.contains("item") || u.contains("model") || u.contains("part") || u.contains("shop") || u.contains("catalog")) {
            score += 12;
        }
        return (u.endsWith(".pdf") || u.contains("/manual")) ? score - 80 : score;
    }

    private static boolean isHarvestablePage(String url) {
        String u = lower(url);
        if (u.startsWith("http://") || u.startsWith("https://")) {
            return !u.matches(".*\\.(pdf|doc|docx|zip)(?:[?#].*)?$");
        }
        return false;
    }

    private static String retrievalPrompt(Models.Identification o, String details) {
        String hint = safe(details);
        return "FLIPCHECK v0.75 UNIVERSAL MULTI-HYPOTHESIS VISUAL RETRIEVAL. Find concrete real-world candidates for the SAME physical entity shown in the photo. Use at most THREE search queries total. NEVER blend incompatible object-class hypotheses into one query. If BRAND_LOCK_REQUIRED=true, every query and candidate must stay inside the exact visible-brand namespace. QUERY A is STRUCTURE-DRIVEN; optional lanes each test one distinct class hypothesis. Literal text remains ambiguous until independently linked to the foreground product. Related, compatible, host and accessory products are not identity candidates. Return only candidates with a real source_url present in the search evidence. For each candidate return brand,family,model,source_url, identifier_score,text_score,layout_score,web_score, candidate_facts,contradictions,evidence. candidate_facts must include search_lane, same_entity_role, relationship_only, geometry_relation, major_geometry_mismatch and brand_entity_validated. Scores are 0-100 and missing evidence is UNKNOWN. Return max 6 plus next_photo_request and next_photo_reason. " + UniversalSearchPlan.policyBlock(o) + " | OBSERVED=" + BrandBlindPolicy.neutralFingerprint(o) + " | USER_HINT_UNTRUSTED=" + (hint.isEmpty() ? "none" : hint);
    }

    private static String comparisonPrompt(Models.Identification o, List<Retrieved> xs) {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < xs.size(); i++) {
            Retrieved c = xs.get(i);
            if (i > 0) {
                list.append(" || ");
            }
            list.append("CANDIDATO ").append(i).append(" / IMMAGINE ").append(i + 2)
                    .append(" = ").append(c.displayName());
        }
        return "UNIVERSAL VISUAL MATCH v0.75. IMMAGINE 1 e' la vista completa dell'oggetto utente; le successive sono fotografie grounded dei candidati nell'ordine dichiarato. Valuta OGNI candidato separatamente. Un riferimento deve raffigurare la stessa entita' fisica: accessori, ricambi, host, confezioni e prodotti compatibili hanno same_entity_role=false. Confronta silhouette, proporzioni, assi, componenti attaccati, controlli, aperture, bordi, testi e spaziature. Una sola differenza strutturale importante rende geometry_consistent=false. exact_variant_distinguishable=true solo se i dettagli visibili separano davvero quella variante dalle versioni con aspetto condiviso. Per ogni candidato restituisci candidate_index 0-based, visual_similarity, geometry_consistent, same_entity_role, exact_variant_distinguishable, contradictions e reason. winner_candidate_index=-1 se nessuno supera il confronto. Non usare i nomi come prova visiva. CANDIDATI: " + list + ". Testi osservati=" + o.visibleLabels + ". Firma osservata=" + o.spatialSignature + ".";
    }

    private static List<Retrieved> parseCandidates(JSONObject payload, List<Models.Source> sources, Models.Identification identification) {
        JSONArray a;
        List<Retrieved> out = new ArrayList<>();
        if (payload == null || (a = payload.optJSONArray("candidates")) == null) {
            return out;
        }
        Set<String> used = new HashSet<>();
        for (int i = 0; i < a.length() && out.size() < 4; i++) {
            JSONObject x = a.optJSONObject(i);
            if (x != null) {
                Retrieved c = new Retrieved();
                c.brand = clean(x.optString("brand", ""));
                c.family = clean(x.optString("family", ""));
                c.model = clean(x.optString("model", ""));
                c.evidence = clean(x.optString("evidence", ""));
                String requested = clean(x.optString("source_url", ""));
                Models.Source matched = matchRealSource(requested, c, sources);
                if (matched != null && !safe(matched.url).isEmpty()) {
                    c.sourceUrl = matched.url;
                    c.sourceTitle = safe(matched.title);
                    String key = canon(c.displayName());
                    if (key.isEmpty()) {
                        key = canon(c.sourceUrl);
                    }
                    if (!key.isEmpty() && used.add(key)) {
                        out.add(c);
                    }
                }
            }
        }
        return out;
    }

    private static Models.Source matchRealSource(String requested, Retrieved c, List<Models.Source> sources) {
        if (sources == null) {
            return null;
        }
        if (!safe(requested).isEmpty()) {
            String norm = normalizeUrl(requested);
            for (Models.Source s : sources) {
                if (s != null && normalizeUrl(s.url).equals(norm)) {
                    return s;
                }
            }
        }
        Models.Source best = null;
        int bestScore = 0;
        for (Models.Source s2 : sources) {
            int score = sourceFit(c, s2);
            if (score > bestScore) {
                bestScore = score;
                best = s2;
            }
        }
        if (!canon(c.model).isEmpty() && bestScore >= 100) {
            return best;
        }
        return null;
    }

    private static void applyVisualLedger(JSONObject payload, List<Retrieved> compared,
                                          List<Models.CandidateScore> preliminary) {
        JSONArray matches = payload == null ? null : payload.optJSONArray("matches");
        if (matches == null) {
            return;
        }
        for (int i = 0; i < matches.length(); i++) {
            JSONObject match = matches.optJSONObject(i);
            int index = match == null ? -1 : match.optInt("candidate_index", -1);
            if (index < 0 || index >= compared.size()) {
                continue;
            }
            Retrieved retrieved = compared.get(index);
            Models.CandidateScore score = findScore(preliminary, retrieved);
            if (score == null) {
                continue;
            }
            int similarity = clamp(match.optInt("visual_similarity", 0));
            boolean geometry = match.optBoolean("geometry_consistent", false);
            boolean sameEntity = match.optBoolean("same_entity_role", false);
            score.layoutScore = similarity;
            score.candidateFacts.add("retrieval_visual_confidence=" + similarity);
            score.candidateFacts.add("geometry_relation=" + (geometry ? "same" : "conflict"));
            score.candidateFacts.add("major_geometry_mismatch=" + (!geometry));
            score.candidateFacts.add("same_entity_role=" + sameEntity);
            score.candidateFacts.add("relationship_only=" + (!sameEntity));
            score.candidateFacts.add("exact_variant_distinguishable="
                    + match.optBoolean("exact_variant_distinguishable", false));
            addVisualContradictions(score, match);
            if (!geometry || !sameEntity) {
                score.candidateFacts.add("contradiction_evidence_confidence=95");
                score.candidateFacts.add("contradiction_hard_evidence=true");
            }
            UniversalConsistencyGate.calibrateCandidate(score);
        }
    }

    private static Models.CandidateScore findScore(List<Models.CandidateScore> scores, Retrieved retrieved) {
        String wanted = canon(retrieved.displayName());
        for (Models.CandidateScore score : scores) {
            if (canon(score.displayName()).equals(wanted)) {
                return score;
            }
        }
        return null;
    }

    private static JSONObject matchAt(JSONObject payload, int wantedIndex) {
        JSONArray matches = payload == null ? null : payload.optJSONArray("matches");
        if (matches == null) {
            return null;
        }
        for (int i = 0; i < matches.length(); i++) {
            JSONObject match = matches.optJSONObject(i);
            if (match != null && match.optInt("candidate_index", -1) == wantedIndex) {
                return match;
            }
        }
        return null;
    }

    private static void addVisualContradictions(Models.CandidateScore score, JSONObject match) {
        if (score == null || match == null) {
            return;
        }
        if (!match.optBoolean("geometry_consistent", false)) {
            score.contradictions.add("STRONG: geometria fotografica incompatibile");
        }
        if (!match.optBoolean("same_entity_role", false)) {
            score.contradictions.add("STRONG: la foto di riferimento mostra un'entita' correlata, non lo stesso oggetto");
        }
        JSONArray returned = match.optJSONArray("contradictions");
        if (returned == null) {
            return;
        }
        for (int i = 0; i < returned.length(); i++) {
            String value = clean(returned.optString(i, ""));
            if (!value.isEmpty()) {
                String normalized = value.toUpperCase(Locale.ROOT).startsWith("STRONG:")
                        || value.toUpperCase(Locale.ROOT).startsWith("WEAK:")
                        ? value : "WEAK: " + value;
                if (!score.contradictions.contains(normalized)) {
                    score.contradictions.add(normalized);
                }
            }
        }
    }

    private static Retrieved chooseReturnedCandidate(JSONObject payload, List<Retrieved> candidates) {
        String b = clean(payload.optString("brand", ""));
        String f = clean(payload.optString("family", ""));
        String m = clean(payload.optString("model", ""));
        String target = canon(b + "|" + f + "|" + m);
        Retrieved best = null;
        int bestScore = 0;
        for (Retrieved c : candidates) {
            String name = canon(c.brand + "|" + c.family + "|" + c.model);
            int score = similarityKey(target, name);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (bestScore >= 55) {
            return best;
        }
        return null;
    }

    private static int similarityKey(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return 100;
        }
        if (a.contains(b) || b.contains(a)) {
            return 90;
        }
        Set<String> aa = trigrams(a);
        Set<String> bb = trigrams(b);
        if (aa.isEmpty() || bb.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String x : aa) {
            if (bb.contains(x)) {
                inter++;
            }
        }
        int union = (aa.size() + bb.size()) - inter;
        if (union == 0) {
            return 0;
        }
        return Math.round((inter * 100.0f) / union);
    }

    private static Set<String> trigrams(String s) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i + 3 <= s.length(); i++) {
            out.add(s.substring(i, i + 3));
        }
        return out;
    }

    private static void collectWebMetadata(Models.Identification o, OpenAiClient.Response r, String stage) {
        if (o == null || r == null) {
            return;
        }
        o.webStages.add(stage);
        for (String q : r.queries) {
            if (!containsIgnoreCase(o.webQueries, q)) {
                o.webQueries.add(q);
            }
        }
        for (Models.Source s : r.sources) {
            boolean dup = false;
            Iterator<Models.Source> it = o.sources.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                Models.Source old = it.next();
                if (!safe(s.url).isEmpty() && s.url.equals(old.url)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                o.sources.add(s);
            }
        }
    }

    static String objectCropDataUrl(String dataUrl) {
        if (safe(dataUrl).isEmpty()) {
            return "";
        }
        Bitmap bitmap = null;
        Bitmap crop = null;
        Bitmap scaled = null;
        ObjectDetector detector = null;
        try {
            int comma = dataUrl.indexOf(44);
            if (comma < 0) {
                return "";
            }
            byte[] raw = Base64.decode(dataUrl.substring(comma + 1), 0);
            bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            if (bitmap == null) {
                return "";
            }
            ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableMultipleObjects()
                    .build();
            detector = ObjectDetection.getClient(options);
            List<DetectedObject> objects = Tasks.await(
                    detector.process(InputImage.fromBitmap(bitmap, 0)), 8L, TimeUnit.SECONDS);
            Rect best = chooseBestObject(objects, bitmap.getWidth(), bitmap.getHeight());
            if (best != null) {
                int padX = Math.round(best.width() * 0.08f);
                int padY = Math.round(best.height() * 0.06f);
                int l = Math.max(0, best.left - padX);
                int t = Math.max(0, best.top - padY);
                int r = Math.min(bitmap.getWidth(), best.right + padX);
                int b = Math.min(bitmap.getHeight(), best.bottom + padY);
                if (r > l + 40 && b > t + 40) {
                    crop = Bitmap.createBitmap(bitmap, l, t, r - l, b - t);
                }
            }
            if (crop == null) {
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();
                int l2 = Math.round(w * 0.08f);
                int r2 = Math.round(w * 0.92f);
                int t2 = Math.round(h * 0.03f);
                crop = Bitmap.createBitmap(bitmap, l2, t2, Math.max(1, r2 - l2), Math.max(1, Math.round(h * 0.97f) - t2));
            }
            scaled = scaleDown(crop, 1000);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)) {
                return "";
            }
            return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), 2);
        } catch (Exception e) {
            return "";
        } finally {
            if (detector != null) {
                detector.close();
            }
            if (scaled != null && scaled != crop && scaled != bitmap && !scaled.isRecycled()) {
                scaled.recycle();
            }
            if (crop != null && crop != bitmap && !crop.isRecycled()) {
                crop.recycle();
            }
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private static Rect chooseBestObject(List<DetectedObject> objects, int width, int height) {
        if (objects == null || objects.isEmpty()) {
            return null;
        }
        Rect best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double cx = width / 2.0d;
        double cy = height / 2.0d;
        for (DetectedObject o : objects) {
            Rect r = o.getBoundingBox();
            if (r.width() >= width * 0.12d && r.height() >= height * 0.12d) {
                double area = (r.width() * r.height()) / Math.max(1.0d, width * height);
                double dist = Math.hypot((r.exactCenterX() - cx) / Math.max(1.0d, width), (r.exactCenterY() - cy) / Math.max(1.0d, height));
                double aspectBonus = ((double) r.height()) > ((double) r.width()) * 1.3d ? 0.15d : 0.0d;
                double score = (area + aspectBonus) - (0.35d * dist);
                if (score > bestScore) {
                    bestScore = score;
                    best = r;
                }
            }
        }
        return best;
    }

    private static Bitmap scaleDown(Bitmap src, int maxSide) {
        int max = Math.max(src.getWidth(), src.getHeight());
        if (max <= maxSide) {
            return src;
        }
        float s = (float) maxSide / (float) max;
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * s)), Math.max(1, Math.round(src.getHeight() * s)), true);
    }

    private static String normalizeUrl(String s) {
        try {
            URI u = new URI(safe(s).trim());
            String scheme = u.getScheme() == null ? "https" : lower(u.getScheme());
            String host = lower(u.getHost()).replaceFirst("^www\\.", "");
            String path = safe(u.getPath()).replaceAll("/+$", "");
            return scheme + "://" + host + path;
        } catch (Exception e) {
            return safe(s).trim();
        }
    }

    private static boolean containsIgnoreCase(List<String> xs, String q) {
        for (String x : xs) {
            if (safe(x).equalsIgnoreCase(safe(q))) {
                return true;
            }
        }
        return false;
    }

    public static void append(StringBuilder b, String s) {
        String s2 = clean(s);
        if (s2.isEmpty()) {
            return;
        }
        if (b.length() > 0) {
            b.append(' ');
        }
        b.append(s2);
    }

    private static String join(String... xs) {
        StringBuilder b = new StringBuilder();
        if (xs != null) {
            for (String x : xs) {
                append(b, x);
            }
        }
        return b.toString();
    }

    private static String clean(String s) {
        return safe(s).trim().replaceAll("\\s+", " ");
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String lower(String s) {
        return safe(s).toLowerCase(Locale.ROOT);
    }

    private static String canon(String s) {
        return safe(s).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static int clamp(int n) {
        return Math.max(0, Math.min(100, n));
    }
}
