package com.flipcheck.nativebeta;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.flipcheck.nativebeta.GoogleWebDetectionEngine;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

final class GoogleWebDetectionEngine {
    private static final double CONSERVATIVE_WEB_DETECTION_COST_USD = 0.0035d;
    private static final String ENDPOINT = "https://vision.googleapis.com/v1/images:annotate?key=";
    private static final int MAX_CANDIDATES = 6;
    private static final int MAX_PAGES = 30;
    private static final int MAX_RERANK = 4;
    private static volatile String apiKey = "";
    private static final Pattern MODEL_TOKEN = Pattern.compile("(?i)\\b(?=[A-Z0-9][A-Z0-9._-]{4,29}\\b)(?=[A-Z0-9._-]*[A-Z])(?=[A-Z0-9._-]*\\d)[A-Z0-9]+(?:[-_.][A-Z0-9]+)*\\b");
    private static final Pattern LONG_PART = Pattern.compile("\\b\\d{10,16}\\b");

    private GoogleWebDetectionEngine() {
    }

    static void configure(String key) {
        apiKey = key == null ? "" : key.trim();
    }

    static boolean hasKey() {
        return !apiKey.isEmpty();
    }

    static final class Outcome {
        boolean attempted;
        Models.CandidateScore best;
        int fullMatches;
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
        boolean full;
        String imageUrl;
        String model;
        int pages;
        int score;
        String title;
        String url;

        private Hit() {
            this.title = "";
            this.url = "";
            this.imageUrl = "";
            this.brand = "";
            this.model = "";
        }

        String key() {
            return GoogleWebDetectionEngine.canon(this.brand) + "|" + GoogleWebDetectionEngine.canon(this.model);
        }
    }

    static Outcome run(Models.Identification id, List<String> images, OpenAiClient client, Models.Usage usage) {
        Outcome out = new Outcome();
        if (!hasKey() || images == null || images.isEmpty()) {
            return out;
        }
        out.attempted = true;
        long started = System.currentTimeMillis();
        try {
            String queryImage = cropDataUrl(images.get(0));
            if (queryImage.isEmpty()) {
                try {
                    queryImage = images.get(0);
                } catch (Exception inner) {
                    out.summary = "Google Web Detection v0.52 non disponibile: " + shortError(inner);
                    return out;
                }
            }
            String base64 = base64Payload(queryImage);
            if (base64.isEmpty()) {
                out.summary = "Google Web Detection v0.52: immagine non leggibile.";
                return out;
            }
            JSONObject raw = callGoogle(base64);
            if (usage != null) {
                usage.requests++;
                try {
                    usage.apiMs += Math.max(0L, System.currentTimeMillis() - started);
                    usage.costUsd += CONSERVATIVE_WEB_DETECTION_COST_USD;
                } catch (Exception timingError) {
                    out.summary = "Google Web Detection v0.52 non disponibile: " + shortError(timingError);
                    return out;
                }
            }
            JSONObject response = raw.optJSONArray("responses") == null ? null : raw.optJSONArray("responses").optJSONObject(0);
            if (response == null) {
                out.summary = "Google Web Detection v0.52: nessuna risposta utile.";
                return out;
            }
            JSONObject error = response.optJSONObject("error");
            if (error != null) {
                out.summary = "Google Web Detection v0.52: " + clean(error.optString("message", "servizio non disponibile"));
                return out;
            }
            JSONObject web = response.optJSONObject("webDetection");
            if (web == null) {
                out.summary = "Google Web Detection v0.52: nessun match web restituito.";
                return out;
            }
            out.fullMatches = length(web.optJSONArray("fullMatchingImages"));
            out.partialMatches = length(web.optJSONArray("partialMatchingImages"));
            out.visuallySimilar = length(web.optJSONArray("visuallySimilarImages"));
            List<Hit> hits = collectHits(id, web, out);
            if (hits.isEmpty()) {
                out.summary = "Google Web Detection v0.52: " + out.pages + " pagine correlate, ma nessuna referenza/modello concreto estratto.";
                return out;
            }
            List<Models.CandidateScore> candidates = toCandidateScores(id, hits);
            out.candidates.addAll(candidates);
            if (!out.candidates.isEmpty()) {
                rerankWithVision(id, queryImage, hits, out, client, usage);
                sort(out.candidates);
                boolean z = false;
                out.best = out.candidates.get(0);
                int margin = out.candidates.size() > 1 ? out.best.totalScore - out.candidates.get(1).totalScore : out.best.totalScore;
                boolean directGoogleEvidence = hasFact(out.best, "google_full_match=true") || hasFact(out.best, "google_pages=3") || hasFact(out.best, "google_pages=4") || hasFact(out.best, "google_pages=5");
                if (out.best.totalScore >= 68 && (margin >= 7 || directGoogleEvidence)) {
                    z = true;
                }
                out.usable = z;
                out.summary = "Google Web Detection v0.52: " + out.pages + " pagine · " + out.fullMatches + " full match · " + out.partialMatches + " partial · " + out.visuallySimilar + " similar · " + out.candidates.size() + " modelli estratti" + (out.rerankedImages > 0 ? " · " + out.rerankedImages + " foto rerank" : "") + ". Leader: " + out.best.displayName() + " " + out.best.totalScore + "/100, margine " + margin + ".";
                return out;
            }
            out.summary = "Google Web Detection v0.52: risultati trovati ma nessun candidato valido.";
            return out;
        } catch (Exception error) {
            out.summary = "Google Web Detection v0.52 non disponibile: " + shortError(error);
            return out;
        }
    }

    private static JSONObject callGoogle(String base64) throws Exception {
        StringBuilder sb;
        String strOptString;
        JSONObject request = new JSONObject().put("image", new JSONObject().put("content", base64)).put("features", new JSONArray().put(new JSONObject().put("type", "WEB_DETECTION").put("maxResults", 50))).put("imageContext", new JSONObject().put("webDetectionParams", new JSONObject().put("includeGeoResults", false)));
        JSONObject body = new JSONObject().put("requests", new JSONArray().put(request));
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

    private static List<Hit> collectHits(Models.Identification id, JSONObject web, Outcome stats) {
        int i;
        int i2;
        int i3;
        String entityText;
        JSONArray pages;
        int i4;
        int i5;
        Outcome outcome = stats;
        Map<String, Hit> grouped = new HashMap<>();
        String entityText2 = entityText(web.optJSONArray("webEntities"));
        String inferredBrand = guessBrand(entityText2 + " " + safe(id.brand));
        JSONArray pages2 = web.optJSONArray("pagesWithMatchingImages");
        int i6 = 1;
        if (pages2 != null) {
            int i7 = 0;
            for (int i8 = 30; i7 < pages2.length() && i7 < i8; i8 = 30) {
                JSONObject p = pages2.optJSONObject(i7);
                if (p == null) {
                    entityText = entityText2;
                    pages = pages2;
                    i3 = i6;
                    i4 = i7;
                } else {
                    outcome.pages += i6;
                    String title = htmlClean(p.optString("pageTitle", ""));
                    String url = clean(p.optString("url", ""));
                    i3 = i6;
                    String hay = title + " " + decodeUrl(url);
                    String brand = guessBrand(hay + " " + inferredBrand);
                    List<String> models = modelTokens(hay, id.categoryKey);
                    if (models.isEmpty()) {
                        entityText = entityText2;
                        pages = pages2;
                        i4 = i7;
                    } else {
                        JSONArray full = p.optJSONArray("fullMatchingImages");
                        JSONArray partial = p.optJSONArray("partialMatchingImages");
                        String image = firstImage(full);
                        boolean fullMatch = !image.isEmpty();
                        if (image.isEmpty()) {
                            image = firstImage(partial);
                        }
                        String image2 = image;
                        for (String model : models) {
                            String entityText3 = entityText2;
                            JSONArray pages3 = pages2;
                            String hay2 = hay;
                            String key = canon(brand) + "|" + canon(model);
                            Hit h = grouped.get(key);
                            if (h == null) {
                                i5 = i7;
                                h = new Hit();
                                h.brand = brand;
                                h.model = model;
                                h.title = title;
                                h.url = url;
                                h.imageUrl = image2;
                                grouped.put(key, h);
                            } else {
                                i5 = i7;
                            }
                            int i9 = h.pages;
                            h.pages = i9 + 1;
                            h.full |= fullMatch;
                            h.score += fullMatch ? 36 : 23;
                            if (title.toUpperCase(Locale.ROOT).contains(model.toUpperCase(Locale.ROOT))) {
                                h.score += 18;
                            }
                            if (!brand.isEmpty() && !safe(id.brand).isEmpty() && canon(brand).equals(canon(id.brand))) {
                                h.score += 6;
                            }
                            if (h.imageUrl.isEmpty() && !image2.isEmpty()) {
                                h.imageUrl = image2;
                            }
                            if (h.title.isEmpty()) {
                                h.title = title;
                            }
                            if (h.url.isEmpty()) {
                                h.url = url;
                            }
                            pages2 = pages3;
                            entityText2 = entityText3;
                            hay = hay2;
                            i7 = i5;
                        }
                        entityText = entityText2;
                        pages = pages2;
                        i4 = i7;
                    }
                }
                i7 = i4 + 1;
                outcome = stats;
                i6 = i3;
                pages2 = pages;
                entityText2 = entityText;
            }
            i = i6;
            i2 = 6;
        } else {
            i = 1;
            i2 = 6;
        }
        JSONArray guesses = web.optJSONArray("bestGuessLabels");
        if (guesses != null) {
            for (int i10 = 0; i10 < guesses.length(); i10++) {
                String label = htmlClean(guesses.optJSONObject(i10) == null ? "" : guesses.optJSONObject(i10).optString("label", ""));
                String brand2 = guessBrand(label + " " + inferredBrand);
                for (String model2 : modelTokens(label, id.categoryKey)) {
                    String key2 = canon(brand2) + "|" + canon(model2);
                    Hit h2 = grouped.get(key2);
                    if (h2 == null) {
                        h2 = new Hit();
                        h2.brand = brand2;
                        h2.model = model2;
                        h2.title = label;
                        grouped.put(key2, h2);
                    }
                    h2.score += 28;
                }
            }
        }
        List<Hit> out = new ArrayList<>(grouped.values());
        for (Hit h3 : out) {
            int i11 = i;
            if (h3.pages > i11) {
                h3.score += Math.min(30, (h3.pages - i11) * 10);
            }
            if (h3.full) {
                h3.score += 12;
            }
            if (preferredModelShape(h3.model, id.categoryKey)) {
                h3.score += 8;
            }
            i = i11;
        }
        Collections.sort(out, Comparator.comparingInt(new ToIntFunction() {
            @Override
            public final int applyAsInt(Object obj) {
                return ((GoogleWebDetectionEngine.Hit) obj).score;
            }
        }).reversed());
        int i12 = i2;
        return out.size() > i12 ? new ArrayList(out.subList(0, i12)) : out;
    }

    private static List<Models.CandidateScore> toCandidateScores(Models.Identification id, List<Hit> hits) {
        List<Models.CandidateScore> out = new ArrayList<>();
        int max = hits.isEmpty() ? 1 : Math.max(1, hits.get(0).score);
        for (Hit h : hits) {
            Models.CandidateScore c = new Models.CandidateScore();
            c.brand = h.brand;
            c.family = safe(id.family);
            c.model = h.model;
            int googleScore = clamp(Math.round(((h.score * 43.0f) / max) + 52.0f));
            c.textScore = Math.min(98, googleScore);
            c.layoutScore = h.full ? Math.min(98, googleScore + 3) : googleScore;
            c.webScore = Math.min(99, (h.pages >= 2 ? 3 : 0) + googleScore);
            c.totalScore = googleScore;
            c.evidence = "Google Web Detection: pagina/immagine trovata dalla foto originale. " + clean(h.title);
            c.candidateFacts.add("google_web_detection=true");
            c.candidateFacts.add("google_pages=" + h.pages);
            c.candidateFacts.add("google_full_match=" + h.full);
            if (!h.url.isEmpty()) {
                c.candidateFacts.add("google_page=" + h.url);
            }
            if (!h.imageUrl.isEmpty()) {
                c.candidateFacts.add("google_match_image=" + h.imageUrl);
            }
            out.add(c);
        }
        return out;
    }

    private static void rerankWithVision(Models.Identification id, String queryImage, List<Hit> hits, Outcome out, OpenAiClient client, Models.Usage usage) {
        List<String> images = new ArrayList<>();
        try {
            images.add(queryImage);
            List<Hit> compared = new ArrayList<>();
            for (Hit h : hits) {
                if (!h.imageUrl.isEmpty()) {
                    images.add(h.imageUrl);
                    compared.add(h);
                    if (compared.size() >= 4) {
                        break;
                    }
                }
            }
            out.rerankedImages = compared.size();
            if (compared.isEmpty()) {
                return;
            }
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < compared.size(); i++) {
                if (i > 0) {
                    names.append(" || ");
                }
                Hit h2 = compared.get(i);
                names.append("IMMAGINE ").append(i + 2).append(" = brand[").append(h2.brand).append("] model[").append(h2.model).append("]");
            }
            String prompt = "FLIPCHECK v0.52 GOOGLE WEB DETECTION RERANK. IMMAGINE 1 e' la foto dell'utente; le altre sono immagini che Google Web Detection ha collegato direttamente alla foto. Confronta SOLO geometria reale: silhouette, proporzioni, posizione e ordine dei controlli, testi, icone, bordi e spaziature. Non inventare nuovi modelli. Scegli uno dei candidati solo se la foto coincide; altrimenti brand/family/model vuoti e confidence <50. Copia ESATTAMENTE brand e model dall'elenco. CANDIDATI: " + ((Object) names) + ". Etichette osservate=" + id.visibleLabels + ". Firma=" + id.spatialSignature + ".";
            try {
                OpenAiClient.Response r = client.visionRole(images, prompt, "low", 700);
                if (usage != null) {
                    usage.add(r.usage);
                }
                if (r.payload != null) {
                    if (r.parseError == null || r.parseError.isEmpty()) {
                        String model = clean(r.payload.optString("model", ""));
                        String brand = clean(r.payload.optString("brand", ""));
                        int conf = clamp(r.payload.optInt("identity_confidence", 0));
                        if (!model.isEmpty() && conf >= 50) {
                            Models.CandidateScore chosen = null;
                            for (Models.CandidateScore c : out.candidates) {
                                String strCanon = canon(c.model);
                                List<String> images2 = images;
                                if (strCanon.equals(canon(model)) && (brand.isEmpty() || c.brand.isEmpty() || canon(c.brand).equals(canon(brand)))) {
                                    chosen = c;
                                    break;
                                }
                                images = images2;
                            }
                            if (chosen == null) {
                                return;
                            }
                            chosen.layoutScore = Math.max(chosen.layoutScore, conf);
                            chosen.textScore = Math.max(chosen.textScore, Math.min(95, conf));
                            chosen.totalScore = clamp(Math.round((chosen.totalScore * 0.45f) + (conf * 0.55f)));
                            chosen.evidence += " Rerank fotografico Luna=" + conf + "%: " + clean(r.payload.optString("reason", ""));
                            chosen.candidateFacts.add("google_visual_rerank=" + conf);
                            for (Models.CandidateScore c2 : out.candidates) {
                                if (c2 != chosen && c2.totalScore >= chosen.totalScore) {
                                    c2.totalScore = Math.max(0, chosen.totalScore - 6);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e2) {
            }
        } catch (Exception e3) {
        }
    }

    private static List<String> modelTokens(String text, String categoryKey) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String s = text == null ? "" : text.replace("%2F", "/");
        Matcher m = MODEL_TOKEN.matcher(s.toUpperCase(Locale.ROOT));
        while (m.find()) {
            String x = trimToken(m.group());
            if (validModelToken(x)) {
                String c = canon(x);
                if (seen.add(c)) {
                    out.add(x);
                }
                if (out.size() >= 3) {
                    break;
                }
            }
        }
        if ("remote_control".equalsIgnoreCase(safe(categoryKey)) && out.size() < 3) {
            Matcher n = LONG_PART.matcher(s);
            while (n.find()) {
                String x2 = n.group();
                if (seen.add(x2)) {
                    out.add(x2);
                }
                if (out.size() >= 3) {
                    break;
                }
            }
        }
        return out;
    }

    private static boolean validModelToken(String x) {
        if (x == null || x.length() < 5 || x.length() > 30) {
            return false;
        }
        String u = x.toUpperCase(Locale.ROOT);
        if (u.matches("20\\d{2}[-_/]20\\d{2}") || u.matches("(ANDROID|NETFLIX|AMAZON|GOOGLE|REMOTE|CONTROL|SMARTTV|BLUETOOTH|YOUTUBE|TOSHIBA|PANASONIC|SAMSUNG|HISENSE|SONY)") || u.matches("\\d{3,4}P") || u.matches("\\d{3,4}HZ")) {
            return false;
        }
        return true;
    }

    private static boolean preferredModelShape(String model, String categoryKey) {
        String m = safe(model).toUpperCase(Locale.ROOT);
        if ("remote_control".equalsIgnoreCase(safe(categoryKey))) {
            return m.matches(".*[A-Z].*\\d.*") && (m.contains("-") || m.startsWith("YKF") || m.startsWith("RM") || m.startsWith("RC") || m.startsWith("CT"));
        }
        return m.matches(".*[A-Z].*\\d.*");
    }

    private static String guessBrand(String text) {
        String u = safe(text).toUpperCase(Locale.ROOT);
        String[][] brands = {new String[]{"SONY", "Sony"}, new String[]{"TOSHIBA", "Toshiba"}, new String[]{"PANASONIC", "Panasonic"}, new String[]{"SAMSUNG", "Samsung"}, new String[]{"HISENSE", "Hisense"}, new String[]{"SHARP", "Sharp"}, new String[]{"JVC", "JVC"}, new String[]{"TCL", "TCL"}, new String[]{"LG ", "LG"}, new String[]{"LG-", "LG"}, new String[]{"SKY ", "Sky"}, new String[]{"BOSCH", "Bosch"}, new String[]{"MAKITA", "Makita"}, new String[]{"DEWALT", "DeWalt"}, new String[]{"MILWAUKEE", "Milwaukee"}, new String[]{"APPLE", "Apple"}, new String[]{"LENOVO", "Lenovo"}, new String[]{"HP ", "HP"}, new String[]{"DELL", "Dell"}, new String[]{"ASUS", "Asus"}, new String[]{"ACER", "Acer"}, new String[]{"CANON", "Canon"}, new String[]{"NIKON", "Nikon"}};
        for (String[] b : brands) {
            if (u.contains(b[0])) {
                return b[1];
            }
        }
        return "";
    }

    private static String entityText(JSONArray entities) {
        if (entities == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < entities.length() && i < 8; i++) {
            JSONObject e = entities.optJSONObject(i);
            if (e != null) {
                String d = clean(e.optString("description", ""));
                if (!d.isEmpty()) {
                    if (b.length() > 0) {
                        b.append(' ');
                    }
                    b.append(d);
                }
            }
        }
        return b.toString();
    }

    private static String firstImage(JSONArray arr) {
        if (arr == null) {
            return "";
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i);
            String u = x == null ? "" : clean(x.optString("url", ""));
            if (u.startsWith("http://") || u.startsWith("https://")) {
                return u;
            }
        }
        return "";
    }

    private static String cropDataUrl(String dataUrl) {
        return VisualRetrievalEngine.objectCropDataUrl(dataUrl);
    }

    private static String base64Payload(String dataUrl) {
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

    private static void sort(List<Models.CandidateScore> xs) {
        Collections.sort(xs, Comparator.comparingInt(new ToIntFunction() {
            @Override
            public final int applyAsInt(Object obj) {
                return ((Models.CandidateScore) obj).totalScore;
            }
        }).reversed());
    }

    private static boolean hasFact(Models.CandidateScore c, String prefix) {
        if (c == null) {
            return false;
        }
        for (String f : c.candidateFacts) {
            if (f != null && f.equalsIgnoreCase(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static int length(JSONArray a) {
        if (a == null) {
            return 0;
        }
        return a.length();
    }

    private static int clamp(int n) {
        return Math.max(0, Math.min(100, n));
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    public static String canon(String s) {
        return safe(s).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String trimToken(String s) {
        return safe(s).replaceAll("^[._-]+|[._-]+$", "");
    }

    private static String htmlClean(String s) {
        return clean(s).replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"");
    }

    private static String decodeUrl(String s) {
        try {
            return URLDecoder.decode(safe(s), "UTF-8").replace('/', ' ').replace('_', ' ');
        } catch (Exception e) {
            return safe(s);
        }
    }

    private static String shortError(Exception e) {
        String s = e == null ? "errore sconosciuto" : safe(e.getMessage());
        if (s.isEmpty()) {
            s = e.getClass().getSimpleName();
        }
        return s.length() > 180 ? s.substring(0, 180) : s;
    }
}
