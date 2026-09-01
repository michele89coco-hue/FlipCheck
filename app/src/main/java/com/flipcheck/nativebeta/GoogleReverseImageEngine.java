package com.flipcheck.nativebeta;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.flipcheck.nativebeta.GoogleReverseImageEngine;
import com.flipcheck.nativebeta.Models;
import com.flipcheck.nativebeta.OpenAiClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.helper.HttpConnection;

final class GoogleReverseImageEngine {
    private static final double COST_PER_WEB_DETECTION_USD = 0.0035d;
    private static final String ENDPOINT = "https://vision.googleapis.com/v1/images:annotate?key=";
    private static final int MAX_CANDIDATES = 8;
    private static final int MAX_RERANK_IMAGES = 5;
    private static volatile String apiKey = "";
    private static final Pattern MODEL_TOKEN = Pattern.compile("(?i)\\b(?=[A-Z0-9][A-Z0-9._/-]{4,31}\\b)(?=[A-Z0-9._/-]*[A-Z])(?=[A-Z0-9._/-]*\\d)[A-Z0-9]+(?:[-_./][A-Z0-9]+)*\\b");
    private static final Pattern LONG_PART = Pattern.compile("\\b\\d{9,16}\\b");
    private static final Pattern NUMERIC_MODEL = Pattern.compile("\\b\\d{4,12}\\b");

    private GoogleReverseImageEngine() {
    }

    static void configure(String key) {
        apiKey = key == null ? "" : key.trim();
    }

    static boolean hasKey() {
        return !apiKey.isEmpty();
    }

    static final class Outcome {
        boolean additionalViewUsed;
        boolean attempted;
        Models.CandidateScore best;
        boolean cropUsed;
        int fullMatches;
        int googleCalls;
        boolean imageFirst;
        boolean originalRetry;
        int pages;
        int partialMatches;
        int rerankedImages;
        boolean usable;
        int visuallySimilar;
        final List<Models.CandidateScore> candidates = new ArrayList();
        String summary = "";

        Outcome() {
        }
    }

    static final class Hit {
        String brand;
        final Set<String> evidence;
        boolean full;
        String imageKind;
        String imageUrl;
        String model;
        String pageUrl;
        int pages;
        int score;
        String title;

        private Hit() {
            this.brand = "";
            this.model = "";
            this.title = "";
            this.pageUrl = "";
            this.imageUrl = "";
            this.imageKind = "";
            this.evidence = new LinkedHashSet();
        }
    }

    static Outcome runPure(List<String> images, OpenAiClient client, Models.Usage usage) {
        Models.Identification neutral = new Models.Identification();
        neutral.categoryKey = "";
        Outcome out = run(neutral, images, client, usage);
        out.imageFirst = true;
        for (Models.CandidateScore c : out.candidates) {
            if (c != null) {
                c.candidateFacts.add("image_first_retrieval=true");
            }
        }
        String base = out.summary != null ? out.summary : "";
        out.summary = "IMAGE-FIRST v0.63 · crop=" + out.cropUsed + " · " + base;
        return out;
    }

    static Outcome run(Models.Identification id, List<String> images, OpenAiClient client, Models.Usage usage) {
        boolean z;
        int margin;
        Outcome out = new Outcome();
        if (!hasKey() || images == null || images.isEmpty()) {
            return out;
        }
        out.attempted = true;
        try {
            String original = images.get(0);
            String crop = cropDataUrl(original);
            if (crop.isEmpty()) {
                crop = original;
            }
            String crop2 = crop;
            if (!crop2.equals(original)) {
                z = true;
            } else {
                z = false;
            }
            out.cropUsed = z;
            String rerankQuery = crop2;
            Map<String, Hit> merged = new LinkedHashMap<>();
            analyzeOne(id, crop2, out, merged, usage);
            if (needsAdditionalView(out)) {
                if (images.size() > 1) {
                    out.additionalViewUsed = true;
                    String second = images.get(1);
                    String secondCrop = cropDataUrl(second);
                    if (secondCrop.isEmpty()) {
                        secondCrop = second;
                    }
                    analyzeOne(id, secondCrop, out, merged, usage);
                    rerankQuery = secondCrop;
                } else if (!crop2.equals(original)) {
                    out.originalRetry = true;
                    analyzeOne(id, original, out, merged, usage);
                    rerankQuery = original;
                }
            }
            List<Hit> hits = new ArrayList<>(merged.values());
            hits.sort(Comparator.comparingInt(new ToIntFunction() {
                @Override
                public final int applyAsInt(Object obj) {
                    return ((GoogleReverseImageEngine.Hit) obj).score;
                }
            }).reversed());
            if (hits.size() > 8) {
                hits = new ArrayList<>(hits.subList(0, 8));
            }
            if (hits.isEmpty()) {
                out.summary = "Image Retrieval Core v0.56: " + out.pages + " pagine · " + out.fullMatches + " full · " + out.partialMatches + " partial · " + out.visuallySimilar + " immagini simili, ma nessun model/part number concreto estratto" + (out.originalRetry ? " dopo crop + foto originale." : ".");
                return out;
            }
            for (Hit h : hits) {
                out.candidates.add(toScore(h, hits.get(0).score));
                rerankQuery = rerankQuery;
            }
            rerank(id, rerankQuery, hits, out, client, usage);
            sort(out.candidates);
            out.best = out.candidates.get(0);
            if (out.candidates.size() > 1) {
                margin = Math.max(0, out.best.totalScore - out.candidates.get(1).totalScore);
            } else {
                margin = 0;
            }
            int calibrated = ImageMatchPolicy.calibratedConfidence(out.best);
            out.usable = ImageMatchPolicy.publicCandidateAllowed(out.best) && calibrated >= 68 && (margin >= 7 || ImageMatchPolicy.evidenceCap(out.best) >= 80);
            out.summary = "Image Retrieval Core v0.56: " + out.pages + " pagine · " + out.fullMatches + " full · " + out.partialMatches + " partial · " + out.visuallySimilar + " similar · " + out.candidates.size() + " modelli" + (out.additionalViewUsed ? " · seconda foto usata" : out.originalRetry ? " · retry foto intera" : "") + (out.rerankedImages > 0 ? " · " + out.rerankedImages + " foto rerank" : "") + ". Leader " + out.best.displayName() + " " + out.best.totalScore + "/100, margine " + (out.candidates.size() > 1 ? String.valueOf(margin) : "n/d") + ".";
            return out;
        } catch (Exception e) {
            out.summary = "Image Retrieval Core v0.56 non disponibile: " + shortError(e);
            return out;
        }
    }

    private static boolean needsAdditionalView(Outcome stats) {
        if (stats == null) {
            return true;
        }
        return stats.pages == 0 && stats.fullMatches == 0 && stats.partialMatches == 0;
    }

    private static void analyzeOne(Models.Identification id, String image, Outcome stats, Map<String, Hit> merged, Models.Usage usage) throws Exception {
        String str;
        String str2;
        String entityBrand;
        JSONArray pages;
        int i;
        String str3;
        String str4;
        Models.Identification identification = id;
        Outcome outcome = stats;
        String base64 = payload(image);
        if (base64.isEmpty()) {
            return;
        }
        long started = System.currentTimeMillis();
        JSONObject raw = call(base64);
        outcome.googleCalls++;
        if (usage != null) {
            usage.requests++;
            usage.apiMs += Math.max(0L, System.currentTimeMillis() - started);
            usage.costUsd += COST_PER_WEB_DETECTION_USD;
        }
        JSONObject response = raw.optJSONArray("responses") == null ? null : raw.optJSONArray("responses").optJSONObject(0);
        if (response == null) {
            return;
        }
        JSONObject err = response.optJSONObject("error");
        if (err != null) {
            throw new IllegalStateException(err.optString("message", "Google Vision error"));
        }
        JSONObject web = response.optJSONObject("webDetection");
        if (web == null) {
            return;
        }
        outcome.fullMatches += length(web.optJSONArray("fullMatchingImages"));
        String str5 = "partialMatchingImages";
        outcome.partialMatches += length(web.optJSONArray("partialMatchingImages"));
        outcome.visuallySimilar += length(web.optJSONArray("visuallySimilarImages"));
        String entityText = entityText(web.optJSONArray("webEntities"));
        String entityBrand2 = guessBrand(entityText);
        String observedBrand = BrandBlindPolicy.observedBrandOrEmpty(identification);
        if (!observedBrand.isEmpty()) {
            entityBrand2 = observedBrand;
        }
        String directFallback = firstImage(web.optJSONArray("fullMatchingImages"));
        if (directFallback.isEmpty()) {
            directFallback = firstImage(web.optJSONArray("partialMatchingImages"));
        }
        if (directFallback.isEmpty()) {
            directFallback = firstImage(web.optJSONArray("visuallySimilarImages"));
        }
        String directFallback2 = directFallback;
        JSONArray pages2 = web.optJSONArray("pagesWithMatchingImages");
        String str6 = "";
        if (pages2 != null) {
            entityBrand = entityBrand2;
            int i2 = 0;
            while (i2 < pages2.length() && i2 < 40) {
                JSONObject p = pages2.optJSONObject(i2);
                if (p == null) {
                    pages = pages2;
                    str4 = str5;
                    str3 = str6;
                    i = i2;
                } else {
                    pages = pages2;
                    outcome.pages++;
                    String title = html(p.optString("pageTitle", str6));
                    i = i2;
                    String pageUrl = clean(p.optString("url", str6));
                    String brand = guessBrand(title);
                    if (brand.isEmpty()) {
                        brand = entityBrand;
                    }
                    str3 = str6;
                    String brand2 = brand;
                    String imageUrl = firstImage(p.optJSONArray("fullMatchingImages"));
                    boolean full = !imageUrl.isEmpty();
                    String kind = full ? "page_full" : "page_partial";
                    if (imageUrl.isEmpty()) {
                        imageUrl = firstImage(p.optJSONArray(str5));
                    }
                    str4 = str5;
                    String imageUrl2 = imageUrl;
                    String str7 = identification == null ? str3 : identification.categoryKey;
                    String hay = title;
                    Iterator<String> it = modelTokens(hay, str7).iterator();
                    while (it.hasNext()) {
                        String hay2 = hay;
                        String model = it.next();
                        Iterator<String> it2 = it;
                        Hit h = get(merged, brand2, model);
                        String brand3 = brand2;
                        h.pages++;
                        h.full |= full;
                        h.score += full ? 52 : 34;
                        if (containsIgnoreCase(title, model)) {
                            h.score += 22;
                        }
                        h.title = first(h.title, title);
                        h.pageUrl = first(h.pageUrl, pageUrl);
                        h.imageUrl = first(h.imageUrl, imageUrl2);
                        h.imageKind = first(h.imageKind, kind);
                        h.evidence.add("matching_page");
                        it = it2;
                        hay = hay2;
                        brand2 = brand3;
                    }
                }
                i2 = i + 1;
                identification = id;
                outcome = stats;
                pages2 = pages;
                str6 = str3;
                str5 = str4;
            }
            str = str5;
            str2 = str6;
        } else {
            str = "partialMatchingImages";
            str2 = "";
            entityBrand = entityBrand2;
        }
        Models.Identification identification2 = id;
        String entityBrand3 = entityBrand;
        String str8 = str2;
        collectImageArray(identification2, web.optJSONArray("fullMatchingImages"), "full", 58, true, entityBrand3, merged);
        collectImageArray(identification2, web.optJSONArray(str), "partial", 42, false, entityBrand3, merged);
        collectImageArray(identification2, web.optJSONArray("visuallySimilarImages"), "similar", 28, false, entityBrand3, merged);
        for (String model2 : modelTokens(entityText, identification2 == null ? str8 : identification2.categoryKey)) {
            Hit h2 = get(merged, entityBrand3, model2);
            h2.score += 38;
            h2.imageUrl = first(h2.imageUrl, directFallback2);
            h2.imageKind = first(h2.imageKind, "entity+direct_image");
            h2.evidence.add("web_entity_model");
        }
        JSONArray guesses = web.optJSONArray("bestGuessLabels");
        if (guesses != null) {
            int i3 = 0;
            while (i3 < guesses.length()) {
                JSONObject g = guesses.optJSONObject(i3);
                String label = g == null ? str8 : html(g.optString("label", str8));
                String brand4 = guessBrand(label);
                if (brand4.isEmpty()) {
                    brand4 = entityBrand3;
                }
                for (String model3 : modelTokens(label, identification2 == null ? str8 : identification2.categoryKey)) {
                    JSONArray guesses2 = guesses;
                    Hit h3 = get(merged, brand4, model3);
                    h3.score += 34;
                    h3.title = first(h3.title, label);
                    h3.imageUrl = first(h3.imageUrl, directFallback2);
                    h3.imageKind = first(h3.imageKind, "best_guess+direct_image");
                    h3.evidence.add("best_guess_model");
                    guesses = guesses2;
                    i3 = i3;
                }
                i3++;
                identification2 = id;
            }
        }
    }

    private static void collectImageArray(Models.Identification id, JSONArray a, String kind, int weight, boolean full, String entityBrand, Map<String, Hit> merged) {
        if (a == null || merged == null || merged.isEmpty()) {
            return;
        }
        for (int i = 0; i < a.length() && i < 40; i++) {
            JSONObject x = a.optJSONObject(i);
            String url = x != null ? clean(x.optString("url", "")) : "";
            if (!url.isEmpty()) {
                String ucanon = canon(decode(url));
                for (Hit h : merged.values()) {
                    if (h != null && !clean(h.model).isEmpty()) {
                        String mcanon = canon(h.model);
                        if (mcanon.length() >= 4 && ucanon.contains(mcanon)) {
                            h.score += Math.max(4, weight / 5);
                            h.full |= full;
                            h.imageUrl = first(h.imageUrl, url);
                            h.imageKind = first(h.imageKind, kind);
                            h.evidence.add("direct_" + kind + "_image_attached");
                        }
                    }
                }
            }
        }
    }

    private static Hit get(Map<String, Hit> map, String brand, String model) {
        String key = canon(brand) + "|" + canon(model);
        Hit h = map.get(key);
        if (h == null) {
            Hit h2 = new Hit();
            h2.brand = clean(brand);
            h2.model = clean(model);
            map.put(key, h2);
            return h2;
        }
        return h;
    }

    private static Models.CandidateScore toScore(Hit h, int leaderRaw) {
        Models.CandidateScore c = new Models.CandidateScore();
        c.brand = h.brand;
        c.family = "";
        c.model = h.model;
        int calibrated = ImageMatchPolicy.baseScoreForGoogleEvidence(h.pages, h.full, h.imageKind, h.score);
        c.textScore = calibrated;
        c.layoutScore = calibrated;
        c.webScore = calibrated;
        c.totalScore = calibrated;
        c.evidence = "Image Retrieval Core v0.56: metadato estratto da un'immagine/pagina collegata alla foto; la somiglianza visuale e l'identita' sono valutate separatamente. " + clean(h.title);
        c.candidateFacts.add("google_web_detection=true");
        c.candidateFacts.add("google_reverse_image=true");
        c.candidateFacts.add("google_pages=" + h.pages);
        c.candidateFacts.add("google_pages_2plus=" + (h.pages >= 2));
        c.candidateFacts.add("google_full_match=" + h.full);
        c.candidateFacts.add("google_image_kind=" + clean(h.imageKind));
        if (!h.pageUrl.isEmpty()) {
            c.candidateFacts.add("google_page=" + h.pageUrl);
        }
        if (!h.imageUrl.isEmpty()) {
            c.candidateFacts.add("google_match_image=" + h.imageUrl);
        }
        return c;
    }

    private static void rerank(Models.Identification id, String query, List<Hit> hits, Outcome out, OpenAiClient client, Models.Usage usage) {
        List<String> imgs = new ArrayList<>();
        List<Hit> compared = new ArrayList<>();
        Object obj;
        Object obj2;
        try {
            imgs.add(query);
            for (Hit h : hits) {
                if (!h.imageUrl.isEmpty()) {
                    imgs.add(h.imageUrl);
                    compared.add(h);
                    if (compared.size() >= 5) {
                        break;
                    }
                }
            }
            out.rerankedImages = compared.size();
            if (compared.isEmpty()) {
                return;
            }
            StringBuilder list = new StringBuilder();
            for (int i = 0; i < compared.size(); i++) {
                if (i > 0) {
                    list.append(" || ");
                }
                list.append("IMG ").append(i + 2).append(" = [").append(compared.get(i).brand).append("] [").append(compared.get(i).model).append(']');
            }
            StringBuilder sbAppend = new StringBuilder().append("FLIPCHECK v0.56 IMAGE MATCH RERANK. IMG 1 è la foto utente; le altre sono immagini restituite direttamente da Google Web Detection. Confronta silhouette, proporzioni, ordine/posizione dei tasti, testi e icone. NON inventare brand o modelli. Scegli solo dall'elenco e copia esattamente il model. Se nessuna coincide, model vuoto e confidence <50. JSON: {\"brand\":\"\",\"model\":\"\",\"identity_confidence\":0,\"reason\":\"\"}. Candidati=").append((Object) list).append(" | testi osservati=");
            if (id != null) {
                obj = id.visibleLabels;
            } else {
                obj = "";
            }
            StringBuilder sbAppend2 = sbAppend.append(obj).append(" | layout=");
            if (id != null) {
                obj2 = id.spatialSignature;
            } else {
                obj2 = "";
            }
            String prompt = sbAppend2.append(obj2).toString();
            try {
                OpenAiClient.Response r = client.visionRole(imgs, prompt, "low", 620);
                if (usage != null) {
                    usage.add(r.usage);
                }
                if (r == null || r.payload == null) {
                    return;
                }
                if (r.payload.length() == 0) {
                    return;
                }
                String model = clean(r.payload.optString("model", ""));
                String brand = clean(r.payload.optString("brand", ""));
                int conf = clamp(r.payload.optInt("identity_confidence", 0));
                if (!model.isEmpty() && conf >= 50) {
                    Models.CandidateScore chosen = null;
                    for (Models.CandidateScore c : out.candidates) {
                        List<String> imgs2 = imgs;
                        if (!canon(c.model).equals(canon(model))) {
                            imgs = imgs2;
                        } else {
                            if (brand.isEmpty() || c.brand.isEmpty() || canon(c.brand).equals(canon(brand))) {
                                chosen = c;
                                break;
                            }
                            imgs = imgs2;
                        }
                    }
                    if (chosen == null) {
                        return;
                    }
                    chosen.layoutScore = Math.max(chosen.layoutScore, Math.min(conf, ImageMatchPolicy.evidenceCap(chosen)));
                    int proposed = clamp(Math.round((chosen.totalScore * 0.42f) + (conf * 0.58f)));
                    chosen.totalScore = ImageMatchPolicy.capScore(chosen, proposed);
                    chosen.evidence += " Rerank foto=" + conf + "%: " + clean(r.payload.optString("reason", ""));
                    chosen.candidateFacts.add("google_visual_rerank=" + conf);
                    for (Models.CandidateScore c2 : out.candidates) {
                        if (c2 != chosen && c2.totalScore >= chosen.totalScore) {
                            c2.totalScore = Math.max(0, chosen.totalScore - 5);
                        }
                    }
                }
            } catch (Exception e2) {
            }
        } catch (Exception e3) {
        }
    }

    private static JSONObject call(String base64) throws Exception {
        StringBuilder sb;
        String strOptString;
        JSONObject req = new JSONObject().put("image", new JSONObject().put("content", base64)).put("features", new JSONArray().put(new JSONObject().put("type", "WEB_DETECTION").put("maxResults", 50))).put("imageContext", new JSONObject().put("webDetectionParams", new JSONObject().put("includeGeoResults", false)));
        JSONObject body = new JSONObject().put("requests", new JSONArray().put(req));
        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT + apiKey).openConnection();
        c.setConnectTimeout(AccessibilityNodeInfoCompat.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_MAX_LENGTH);
        c.setReadTimeout(50000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty(HttpConnection.CONTENT_TYPE, "application/json; charset=utf-8");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        OutputStream os = c.getOutputStream();
        try {
            os.write(bytes);
            if (os != null) {
                os.close();
            }
            int code = c.getResponseCode();
            InputStream in = (code < 200 || code >= 300) ? c.getErrorStream() : c.getInputStream();
            String text = readAll(in);
            c.disconnect();
            JSONObject raw = new JSONObject(text);
            if (code < 200 || code >= 300) {
                JSONObject err = raw.optJSONObject("error");
                if (err == null) {
                    sb = new StringBuilder();
                    strOptString = sb.append("Google HTTP ").append(code).toString();
                } else {
                    sb = new StringBuilder();
                    strOptString = err.optString("message", sb.append("Google HTTP ").append(code).toString());
                }
                throw new IllegalStateException(strOptString);
            }
            return raw;
        } catch (Throwable th) {
            if (os != null) {
                try {
                    os.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private static List<String> modelTokens(String text, String categoryKey) throws NumberFormatException {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String s = clean(text).replace("%2F", "/").toUpperCase(Locale.ROOT);
        Matcher m = MODEL_TOKEN.matcher(s);
        while (m.find()) {
            String x = trim(m.group());
            if (validModel(x, categoryKey)) {
                if (seen.add(canon(x))) {
                    out.add(x);
                }
                if (out.size() >= 5) {
                    break;
                }
            }
        }
        if (out.size() < 5) {
            Matcher n = LONG_PART.matcher(s);
            while (n.find()) {
                String x2 = n.group();
                if (seen.add(x2)) {
                    out.add(x2);
                }
                if (out.size() >= 5) {
                    break;
                }
            }
        }
        if (out.size() < 5) {
            Matcher n2 = NUMERIC_MODEL.matcher(s);
            while (n2.find()) {
                String x3 = n2.group();
                int v = 0;
                try {
                    v = Integer.parseInt(x3);
                } catch (Exception e) {
                }
                if (x3.length() != 4 || v < 1900 || v > 2099) {
                    if (seen.add(x3)) {
                        out.add(x3);
                    }
                    if (out.size() >= 5) {
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static boolean validModel(String x, String categoryKey) {
        String u = clean(x).toUpperCase(Locale.ROOT);
        if (u.length() < 3 || u.length() > 40 || CandidateSanitizer.imageArtifact(u) || CandidateSanitizer.infrastructureToken(u) || u.matches("(UNKNOWN|GENERIC|PRODUCT|MODEL|ITEM|IMAGE|PHOTO|THUMBNAIL)")) {
            return false;
        }
        return CandidateSanitizer.plausibleProductCode(u);
    }

    private static String guessBrand(String text) {
        return "";
    }

    private static String entityText(JSONArray a) {
        if (a == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length() && i < 16; i++) {
            JSONObject x = a.optJSONObject(i);
            String d = x == null ? "" : clean(x.optString("description", ""));
            if (!d.isEmpty()) {
                if (b.length() > 0) {
                    b.append(' ');
                }
                b.append(d);
            }
        }
        return b.toString();
    }

    private static String firstImage(JSONArray a) {
        if (a == null) {
            return "";
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            String u = x == null ? "" : clean(x.optString("url", ""));
            if (u.startsWith("http://") || u.startsWith("https://")) {
                return u;
            }
        }
        return "";
    }

    static String cropDataUrl(String dataUrl) {
        return VisualRetrievalEngine.objectCropDataUrl(dataUrl);
    }

    private static String payload(String dataUrl) {
        if (dataUrl == null) {
            return "";
        }
        int comma = dataUrl.indexOf(44);
        return comma >= 0 ? dataUrl.substring(comma + 1).trim() : dataUrl.trim();
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (true) {
            int n = in.read(buf);
            if (n < 0) {
                in.close();
                return out.toString("UTF-8");
            }
            out.write(buf, 0, n);
        }
    }

    private static boolean fact(Models.CandidateScore c, String fact) {
        if (c == null) {
            return false;
        }
        for (String f : c.candidateFacts) {
            if (fact.equalsIgnoreCase(clean(f))) {
                return true;
            }
        }
        return false;
    }

    private static boolean numericFactAtLeast(Models.CandidateScore c, String prefix, int min) {
        if (c == null) {
            return false;
        }
        Iterator<String> it = c.candidateFacts.iterator();
        while (it.hasNext()) {
            String f = it.next();
            if (f != null && f.startsWith(prefix)) {
                try {
                    return Integer.parseInt(f.substring(prefix.length())) >= min;
                } catch (Exception e) {
                }
            }
        }
        return false;
    }

    private static void sort(List<Models.CandidateScore> xs) {
        xs.sort(Comparator.comparingInt(new ToIntFunction() {
            @Override
            public final int applyAsInt(Object obj) {
                return ((Models.CandidateScore) obj).totalScore;
            }
        }).reversed());
    }

    private static int length(JSONArray a) {
        if (a == null) {
            return 0;
        }
        return a.length();
    }

    private static int clamp(int x) {
        return Math.max(0, Math.min(100, x));
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String canon(String s) {
        return clean(s).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String trim(String s) {
        return clean(s).replaceAll("^[._/-]+|[._/-]+$", "");
    }

    private static String first(String a, String b) {
        return clean(a).isEmpty() ? clean(b) : clean(a);
    }

    private static String html(String s) {
        return clean(s).replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'");
    }

    private static boolean containsIgnoreCase(String a, String b) {
        return clean(a).toLowerCase(Locale.ROOT).contains(clean(b).toLowerCase(Locale.ROOT));
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(clean(s), "UTF-8").replace('/', ' ').replace('_', ' ');
        } catch (Exception e) {
            return clean(s);
        }
    }

    private static String shortError(Exception e) {
        String s = e == null ? "errore sconosciuto" : clean(e.getMessage());
        if (s.isEmpty()) {
            s = e.getClass().getSimpleName();
        }
        return s.length() > 180 ? s.substring(0, 180) : s;
    }
}
