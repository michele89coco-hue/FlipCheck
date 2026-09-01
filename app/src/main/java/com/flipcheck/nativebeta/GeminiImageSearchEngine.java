package com.flipcheck.nativebeta;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.flipcheck.nativebeta.Models;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.helper.HttpConnection;

final class GeminiImageSearchEngine {
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions";
    private static final String MODEL = "gemini-3.1-flash-image";
    private static volatile String apiKey = "";

    private GeminiImageSearchEngine() {
    }

    static void configure(String key) {
        apiKey = key == null ? "" : key.trim();
    }

    static boolean hasKey() {
        return !apiKey.isEmpty();
    }

    static final class Outcome {
        boolean attempted;
        int categoryConfidence;
        boolean completed;
        int httpCode;
        Models.Identification identification;
        int identityConfidence;
        int imageSearchCalls;
        int interactionCalls;
        boolean parsedJson;
        int resultCount;
        boolean sameProduct;
        int searchCalls;
        boolean searchError;
        boolean searchUsed;
        int visualMatch;
        int webSearchCalls;
        boolean needsSecondPhoto = true;
        String title = "";
        String category = "";
        String categoryKey = "other";
        String brand = "";
        String family = "";
        String model = "";
        String candidateLabel = "";
        String reason = "";
        String nextPhoto = "";
        String sourceUrl = "";
        String imageUrl = "";
        String searchSuggestionsHtml = "";
        String rawText = "";
        String status = "";
        String error = "";
        String interactionId = "";
        final List<String> stepTypes = new ArrayList();
        final List<String> queries = new ArrayList();

        Outcome() {
        }

        String diagnostic() {
            return "Gemini v0.61 [attempted=" + this.attempted + " calls=" + this.interactionCalls + " http=" + this.httpCode + " status=" + GeminiImageSearchEngine.clean(this.status) + " completed=" + this.completed + " searchUsed=" + this.searchUsed + " searchCalls=" + this.searchCalls + " imageSearch=" + this.imageSearchCalls + " webSearch=" + this.webSearchCalls + " results=" + this.resultCount + " parsedJson=" + this.parsedJson + " searchError=" + this.searchError + (this.error.isEmpty() ? "" : " error=" + GeminiImageSearchEngine.clean(this.error)) + " steps=" + this.stepTypes + "]";
        }
    }

    static Outcome run(Models.LocalScan local, List<String> images, String details,
                       Models.Usage usage) {
        // The v0.75 product pipeline deliberately uses a single grounded stack.
        // Return a typed, fail-closed result if an older caller reaches this
        // compatibility class; never crash and never manufacture an identity.
        Outcome out = new Outcome();
        out.attempted = false;
        out.completed = false;
        out.searchError = true;
        out.status = "disabled";
        out.error = "Percorso Gemini legacy disattivato nella pipeline v0.75";
        out.nextPhoto = "Aggiungi una foto dell'etichetta o del dettaglio identificativo più discriminante.";
        return out;
    }

    private static JSONObject requestBody(Models.LocalScan local, List<String> images, String details, boolean retry) throws Exception {
        JSONArray input = new JSONArray();
        input.put(new JSONObject().put("type", "text").put("text", retrievalPrompt(local, details, images.size(), retry)));
        int count = Math.min(2, images.size());
        for (int i = 0; i < count; i++) {
            String dataUrl = images.get(i);
            String data = payload(dataUrl);
            if (!data.isEmpty()) {
                input.put(new JSONObject().put("type", "image").put("data", data).put("mime_type", mime(dataUrl)));
            }
        }
        JSONObject tool = new JSONObject().put("type", "google_search").put("search_types", new JSONArray().put("image_search"));
        return new JSONObject().put("model", MODEL).put("store", true).put("input", input).put("tools", new JSONArray().put(tool)).put("generation_config", new JSONObject().put("thinking_level", "high").put("max_output_tokens", 900));
    }

    private static String retrievalPrompt(Models.LocalScan local, String details, int imageCount, boolean retry) {
        String lead;
        String ocr = local == null ? "" : clean(local.joinedText());
        if (ocr.length() > 1200) {
            ocr = ocr.substring(0, 1200);
        }
        String d = clean(details);
        if (d.length() > 400) {
            d = d.substring(0, 400);
        }
        if (retry) {
            lead = "RETRY RETRIEVAL. Use Google IMAGE SEARCH now before answering. ";
        } else {
            lead = "RETRIEVAL STAGE. Use Google IMAGE SEARCH to retrieve images that could show the same physical product. ";
        }
        return "FLIPCHECK v0.61. " + lead + "Do not finalize identity in this turn. This must work for any physical product category. Build image-search queries from distinctive geometry, OCR text, logos, controls, ports, labels, codes, colors and layout. Prefer exact-product or exact-family matches over generic lookalikes. A visually similar object is not proof of identity. Even if no exact match exists, perform the image search and state that evidence is weak. Input photos=" + imageCount + ". Local OCR=" + ocr + ". User details=" + d;
    }

    private static JSONObject synthesisBody(String previousInteractionId) throws Exception {
        return new JSONObject().put("model", MODEL).put("store", false).put("previous_interaction_id", previousInteractionId).put("input", "Using ONLY the original user photo(s) and the Google Image Search evidence from the previous interaction, identify the physical product. Distinguish visual similarity from exact identity. If exact brand/model is not supported, keep brand/family/model empty and request the single most informative next photo. Return only the required JSON schema.").put("generation_config", new JSONObject().put("thinking_level", "high").put("max_output_tokens", 1600)).put("response_format", responseFormat());
    }

    private static JSONObject responseFormat() throws Exception {
        JSONObject props = new JSONObject().put("title", new JSONObject().put("type", "string")).put("category", new JSONObject().put("type", "string")).put("category_key", new JSONObject().put("type", "string")).put("category_confidence", new JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 100)).put("candidate_label", new JSONObject().put("type", "string")).put("brand", new JSONObject().put("type", "string")).put("family", new JSONObject().put("type", "string")).put("model", new JSONObject().put("type", "string")).put("visual_match", new JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 100)).put("identity_confidence", new JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 100)).put("same_product", new JSONObject().put("type", "boolean")).put("needs_second_photo", new JSONObject().put("type", "boolean")).put("next_photo", new JSONObject().put("type", "string")).put("best_source_url", new JSONObject().put("type", "string")).put("best_image_url", new JSONObject().put("type", "string")).put("reason", new JSONObject().put("type", "string"));
        JSONArray required = new JSONArray();
        String[] names = {"title", "category", "category_key", "category_confidence", "candidate_label", "brand", "family", "model", "visual_match", "identity_confidence", "same_product", "needs_second_photo", "next_photo", "best_source_url", "best_image_url", "reason"};
        for (String name : names) {
            required.put(name);
        }
        JSONObject schema = new JSONObject().put("type", "object").put("additionalProperties", false).put("properties", props).put("required", required);
        return new JSONObject().put("type", "text").put("mime_type", "application/json").put("schema", schema);
    }

    private static String prompt(Models.LocalScan local, String details, int imageCount) {
        String ocr = local == null ? "" : clean(local.joinedText());
        if (ocr.length() > 1200) {
            ocr = ocr.substring(0, 1200);
        }
        String d = clean(details);
        if (d.length() > 400) {
            d = d.substring(0, 400);
        }
        return "FLIPCHECK v0.57 IMAGE SEARCH IDENTIFICATION. Identify the exact physical product in the user's photo using GOOGLE IMAGE SEARCH as the primary retrieval source. This is category-agnostic: do not assume a remote, controller, card, tool or any specific product class. Search from the PHOTO itself and compare retrieved web images against it. Prioritize exact visual agreement: silhouette, proportions, materials, color blocks, controls, ports, printed text, logo position, labels, distinctive geometry and wear-independent design. A visually similar object is NOT enough. Do not copy a host product, article title, filename, CDN token, TV model, compatible device or page context as the photographed model unless the retrieved image/source clearly names that same photographed object. If multiple variants look nearly identical, keep confidence below 82 and request the single additional view/detail that best separates them. If the same product/model is strongly supported by retrieved images and their sources, set same_product=true. Use image_search and web_search. Do not rely on any brand/model guessed by another AI. OCR below is literal local OCR and may contain mistakes. Return ONLY one JSON object, no markdown: {\"title\":\"\",\"category\":\"\",\"category_key\":\"other\",\"category_confidence\":0,\"candidate_label\":\"\",\"brand\":\"\",\"family\":\"\",\"model\":\"\",\"visual_match\":0,\"identity_confidence\":0,\"same_product\":false,\"needs_second_photo\":true,\"next_photo\":\"\",\"best_source_url\":\"\",\"best_image_url\":\"\",\"reason\":\"\"}. visual_match measures appearance only. identity_confidence measures confidence that the NAME/MODEL belongs to the photographed object. Never set identity_confidence above visual_match. Input photos=" + imageCount + ". Local OCR=" + ocr + ". User details=" + d;
    }

    private static void accumulateUsage(JSONObject raw, Models.Usage usage) {
        JSONObject u;
        if (usage == null || raw == null || (u = raw.optJSONObject("usage")) == null) {
            return;
        }
        long in = u.optLong("total_input_tokens", u.optLong("input_tokens", 0L));
        long outTokens = u.optLong("total_output_tokens", u.optLong("output_tokens", 0L));
        usage.inputTokens += in;
        usage.outputTokens += outTokens;
        usage.costUsd += ((in * 0.5d) + (outTokens * 3.0d)) / 1000000.0d;
    }

    private static JSONObject call(JSONObject body, Outcome out) throws Exception {
        StringBuilder sb;
        String strOptString;
        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        c.setConnectTimeout(AccessibilityNodeInfoCompat.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_MAX_LENGTH);
        c.setReadTimeout(70000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("x-goog-api-key", apiKey);
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
            out.httpCode = code;
            InputStream in = (code < 200 || code >= 300) ? c.getErrorStream() : c.getInputStream();
            String text = readAll(in);
            c.disconnect();
            JSONObject raw = new JSONObject(text);
            if (code < 200 || code >= 300) {
                JSONObject err = raw.optJSONObject("error");
                if (err == null) {
                    sb = new StringBuilder();
                    strOptString = sb.append("Gemini HTTP ").append(code).toString();
                } else {
                    sb = new StringBuilder();
                    strOptString = err.optString("message", sb.append("Gemini HTTP ").append(code).toString());
                }
                out.error = strOptString;
                throw new IllegalStateException(out.error);
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

    private static void parseSteps(JSONArray steps, Outcome out) {
        JSONArray content;
        if (steps == null) {
            return;
        }
        StringBuilder modelText = new StringBuilder();
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step != null) {
                String type = clean(step.optString("type", ""));
                if (!type.isEmpty()) {
                    out.stepTypes.add(type);
                }
                if ("google_search_call".equals(type)) {
                    out.searchUsed = true;
                    out.searchCalls++;
                    JSONObject args = step.optJSONObject("arguments");
                    String searchType = clean(step.optString("search_type", args == null ? "" : args.optString("search_type", ""))).toLowerCase(Locale.ROOT);
                    if (searchType.contains("image")) {
                        out.imageSearchCalls++;
                    } else if (searchType.contains("web")) {
                        out.webSearchCalls++;
                    }
                    JSONArray qs = args == null ? null : args.optJSONArray("queries");
                    if (qs != null) {
                        for (int q = 0; q < qs.length(); q++) {
                            String x = clean(qs.optString(q, ""));
                            if (!x.isEmpty()) {
                                out.queries.add(x);
                            }
                        }
                    }
                    String q2 = args != null ? clean(args.optString("query", "")) : "";
                    if (!q2.isEmpty()) {
                        out.queries.add(q2);
                    }
                } else if ("google_search_result".equals(type)) {
                    out.searchUsed = true;
                    if (step.optBoolean("is_error", false)) {
                        out.searchError = true;
                        out.error = "Google Search grounding ha restituito is_error=true";
                    }
                    String suggestions = step.optString("search_suggestions", "");
                    JSONArray r = step.optJSONArray("result");
                    if (r != null) {
                        out.resultCount += r.length();
                        for (int j = 0; j < r.length(); j++) {
                            JSONObject item = r.optJSONObject(j);
                            if (item != null) {
                                if (suggestions.isEmpty()) {
                                    suggestions = item.optString("search_suggestions", "");
                                }
                                if (out.imageUrl.isEmpty()) {
                                    out.imageUrl = imageUri(item);
                                }
                                if (out.sourceUrl.isEmpty()) {
                                    out.sourceUrl = urlValue(item);
                                }
                            }
                        }
                    }
                    if (!suggestions.isEmpty()) {
                        out.searchSuggestionsHtml = suggestions;
                    }
                } else if ("model_output".equals(type) && (content = step.optJSONArray("content")) != null) {
                    for (int j2 = 0; j2 < content.length(); j2++) {
                        JSONObject item2 = content.optJSONObject(j2);
                        if (item2 != null && "text".equals(item2.optString("type"))) {
                            String t = item2.optString("text", "");
                            if (!t.isEmpty()) {
                                if (modelText.length() > 0) {
                                    modelText.append('\n');
                                }
                                modelText.append(t);
                            }
                            if (out.sourceUrl.isEmpty()) {
                                out.sourceUrl = citationUrl(item2.optJSONArray("annotations"));
                            }
                        }
                    }
                }
            }
        }
        out.rawText = modelText.toString().trim();
    }

    private static void applyJson(JSONObject p, Outcome out) {
        out.title = clean(p.optString("title", ""));
        out.category = clean(p.optString("category", ""));
        out.categoryKey = clean(p.optString("category_key", "other")).toLowerCase(Locale.ROOT);
        out.categoryConfidence = clamp(p.optInt("category_confidence", 0));
        out.candidateLabel = clean(p.optString("candidate_label", ""));
        out.brand = clean(p.optString("brand", ""));
        out.family = clean(p.optString("family", ""));
        out.model = clean(p.optString("model", ""));
        out.visualMatch = clamp(p.optInt("visual_match", 0));
        out.identityConfidence = clamp(p.optInt("identity_confidence", 0));
        out.sameProduct = p.optBoolean("same_product", false);
        out.needsSecondPhoto = p.optBoolean("needs_second_photo", true);
        out.nextPhoto = clean(p.optString("next_photo", ""));
        out.reason = clean(p.optString("reason", ""));
    }

    private static void calibrate(Outcome out) {
        out.identityConfidence = Math.min(out.identityConfidence, out.visualMatch);
        if (!out.searchUsed) {
            out.identityConfidence = Math.min(out.identityConfidence, 55);
        }
        if (!out.sameProduct) {
            out.identityConfidence = Math.min(out.identityConfidence, 72);
        }
        if (out.sourceUrl.isEmpty()) {
            out.identityConfidence = Math.min(out.identityConfidence, 76);
        }
        if (out.model.isEmpty() && out.candidateLabel.isEmpty()) {
            out.identityConfidence = 0;
        }
        if (out.identityConfidence >= 84 && out.sameProduct && !out.sourceUrl.isEmpty()) {
            out.needsSecondPhoto = false;
        } else if (out.identityConfidence < 72) {
            out.needsSecondPhoto = true;
        }
        if (!out.needsSecondPhoto || !out.nextPhoto.isEmpty()) {
            return;
        }
        out.nextPhoto = "Aggiungi una seconda foto da un'angolazione diversa o di un dettaglio distintivo/etichetta.";
    }

    private static Models.Identification toIdentification(Outcome o, Models.LocalScan local) {
        Models.Identification id = new Models.Identification();
        id.localScan = local;
        id.title = o.title.isEmpty() ? o.category.isEmpty() ? "Oggetto" : o.category : o.title;
        id.category = o.category.isEmpty() ? "Oggetto" : o.category;
        id.categoryKey = o.categoryKey.isEmpty() ? "other" : o.categoryKey;
        id.categoryConfidence = o.categoryConfidence;
        id.brand = o.brand;
        id.brandEvidence = "retrieval_match";
        id.family = o.family;
        id.model = o.model;
        id.visionIdentityConfidence = o.identityConfidence;
        id.modelConfidence = o.identityConfidence;
        id.photoProtocolReady = !o.needsSecondPhoto;
        id.marketReady = false;
        id.disproofPassed = false;
        id.nextPhotoRequest = o.needsSecondPhoto ? o.nextPhoto : "";
        id.nextPhotoReason = o.needsSecondPhoto ? o.reason : "Il miglior risultato da Google Image Search e' abbastanza forte da chiedere conferma.";
        id.visionIdentityReason = o.reason;
        id.verificationSummary = o.reason;
        id.decisionReason = "Gemini Image Search v0.61: foto -> Google Image Search -> confronto multimodale. visual=" + o.visualMatch + "% identity=" + o.identityConfidence + "% same_product=" + o.sameProduct + ".";
        id.webStages.add("gemini-image-search-v061");
        id.webQueries.addAll(o.queries);
        id.searchSuggestionsHtml = o.searchSuggestionsHtml;
        if (local != null) {
            String text = clean(local.joinedText());
            if (!text.isEmpty()) {
                id.observedEvidence.add("OCR locale disponibile");
            }
        }
        String text2 = o.candidateLabel;
        if (!text2.isEmpty() || !o.model.isEmpty()) {
            Models.CandidateScore c = new Models.CandidateScore();
            c.brand = o.brand;
            c.family = o.family;
            c.model = !o.model.isEmpty() ? o.model : o.candidateLabel;
            c.textScore = 0;
            c.layoutScore = o.visualMatch;
            c.webScore = o.searchUsed ? o.identityConfidence : 0;
            c.totalScore = o.identityConfidence;
            c.evidence = "Gemini Image Search v0.61: candidato scelto confrontando la foto utente con immagini recuperate da Google Image Search. " + o.reason;
            c.candidateFacts.add("gemini_image_search=true");
            c.candidateFacts.add("gemini_same_product=" + o.sameProduct);
            c.candidateFacts.add("gemini_visual_match=" + o.visualMatch);
            c.candidateFacts.add("gemini_identity_confidence=" + o.identityConfidence);
            if (!o.sourceUrl.isEmpty()) {
                c.candidateFacts.add("gemini_source_url=" + o.sourceUrl);
                c.candidateFacts.add("google_page=" + o.sourceUrl);
                Models.Source s = new Models.Source();
                s.title = o.candidateLabel.isEmpty() ? c.displayName() : o.candidateLabel;
                s.url = o.sourceUrl;
                s.snippet = o.reason;
                s.relevance = o.identityConfidence;
                s.strong = o.sameProduct && o.identityConfidence >= 80;
                id.sources.add(s);
            }
            if (!o.imageUrl.isEmpty()) {
                c.candidateFacts.add("gemini_match_image=" + o.imageUrl);
                c.candidateFacts.add("google_match_image=" + o.imageUrl);
            }
            id.candidates.add(c);
            id.tournamentMargin = o.identityConfidence;
        }
        return id;
    }

    private static JSONObject parseObject(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf(10);
            if (nl >= 0) {
                s = s.substring(nl + 1);
            }
            int end = s.lastIndexOf("```");
            if (end >= 0) {
                s = s.substring(0, end);
            }
        }
        int a = s.indexOf(123);
        int b = s.lastIndexOf(125);
        if (a < 0 || b <= a) {
            return null;
        }
        try {
            return new JSONObject(s.substring(a, b + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static String citationUrl(JSONArray annotations) {
        if (annotations == null) {
            return "";
        }
        for (int i = 0; i < annotations.length(); i++) {
            JSONObject a = annotations.optJSONObject(i);
            if (a != null) {
                String u = clean(a.optString("url", a.optString("source", "")));
                if (http(u)) {
                    return u;
                }
            }
        }
        return "";
    }

    private static String firstImageUri(Object node) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            String direct = imageUri(o);
            if (!direct.isEmpty()) {
                return direct;
            }
            JSONArray names = o.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String r = firstImageUri(o.opt(names.optString(i)));
                    if (!r.isEmpty()) {
                        return r;
                    }
                }
                return "";
            }
            return "";
        }
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i2 = 0; i2 < a.length(); i2++) {
                String r2 = firstImageUri(a.opt(i2));
                if (!r2.isEmpty()) {
                    return r2;
                }
            }
            return "";
        }
        return "";
    }

    private static String firstHttpUrl(Object node) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            String u = urlValue(o);
            if (!u.isEmpty()) {
                return u;
            }
            JSONArray names = o.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String r = firstHttpUrl(o.opt(names.optString(i)));
                    if (!r.isEmpty()) {
                        return r;
                    }
                }
                return "";
            }
            return "";
        }
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i2 = 0; i2 < a.length(); i2++) {
                String r2 = firstHttpUrl(a.opt(i2));
                if (!r2.isEmpty()) {
                    return r2;
                }
            }
            return "";
        }
        return "";
    }

    private static String imageUri(JSONObject o) {
        if (o == null) {
            return "";
        }
        String type = clean(o.optString("type", ""));
        String u = clean(o.optString("uri", ""));
        if ("image".equals(type) && http(u)) {
            return u;
        }
        String image = clean(o.optString("image_url", o.optString("imageUri", "")));
        return http(image) ? image : "";
    }

    private static String urlValue(JSONObject o) {
        if (o == null) {
            return "";
        }
        String[] keys = {"url", "source_url", "page_url"};
        for (String k : keys) {
            String u = clean(o.optString(k, ""));
            if (http(u)) {
                return u;
            }
        }
        return "";
    }

    private static String payload(String dataUrl) {
        if (dataUrl == null) {
            return "";
        }
        int comma = dataUrl.indexOf(44);
        return comma >= 0 ? dataUrl.substring(comma + 1).trim() : dataUrl.trim();
    }

    private static String mime(String dataUrl) {
        int semi;
        if (dataUrl != null && dataUrl.startsWith("data:") && (semi = dataUrl.indexOf(59)) > 5) {
            return dataUrl.substring(5, semi);
        }
        return "image/jpeg";
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

    private static boolean http(String s) {
        return s != null && (s.startsWith("https://") || s.startsWith("http://"));
    }

    private static int clamp(int n) {
        return Math.max(0, Math.min(100, n));
    }

    public static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String shortError(Exception e) {
        String s = e == null ? "errore sconosciuto" : clean(e.getMessage());
        if (s.isEmpty()) {
            s = e.getClass().getSimpleName();
        }
        return s.length() > 180 ? s.substring(0, 180) : s;
    }
}
