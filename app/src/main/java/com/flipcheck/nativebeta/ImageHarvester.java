package com.flipcheck.nativebeta;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import com.flipcheck.nativebeta.ImageHarvester;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

final class ImageHarvester {
    private static final int MAX_DOWNLOAD_ATTEMPTS = 12;
    private static final int MAX_HTML_BYTES = 1400000;
    private static final int MAX_IMAGE_BYTES = 5000000;

    private ImageHarvester() {
    }

    static final class Result {
        int height;
        int score;
        int tried;
        int width;
        String dataUrl = "";
        String imageUrl = "";
        String method = "";

        Result() {
        }

        boolean usable() {
            return (this.dataUrl == null || this.dataUrl.isEmpty()) ? false : true;
        }
    }

    static final class ImageRef {
        String context;
        String method;
        int score;
        String url;

        ImageRef(String url, String method, String context, int score) {
            this.url = url;
            this.method = method;
            this.context = context == null ? "" : context;
            this.score = score;
        }
    }

    static Result harvestBest(String pageUrl, String modelHint, String familyHint, String titleHint) {
        Result out = new Result();
        if (pageUrl == null || pageUrl.trim().isEmpty()) {
            return out;
        }
        HttpURLConnection page = null;
        try {
            page = open(pageUrl, "text/html,application/xhtml+xml,image/*,*/*;q=0.6", "");
            String type = lower(page.getContentType());
            String resolvedPage = page.getURL() == null ? pageUrl : page.getURL().toString();
            if (type.startsWith("image/")) {
                BitmapData direct = validateAndEncode(readLimited(page.getInputStream(), 5_000_000));
                if (direct != null) {
                    out.dataUrl = direct.dataUrl;
                    out.imageUrl = resolvedPage;
                    out.method = "direct-image";
                    out.width = direct.width;
                    out.height = direct.height;
                    // A ranking score is domain data, not an ML Kit error code. Keeping the
                    // literal here prevents this optional Web helper from loading ML Kit only
                    // to obtain an unrelated integer constant.
                    out.score = 190;
                }
                return out;
            }
            if (type.contains("pdf")) {
                return out;
            }
            byte[] htmlBytes = readLimited(page.getInputStream(), MAX_HTML_BYTES);
            String html = new String(htmlBytes, StandardCharsets.UTF_8);
            List<ImageRef> refs = extractImageRefs(html, resolvedPage, modelHint, familyHint, titleHint);
            refs.sort(Comparator.comparingInt(new ToIntFunction<ImageRef>() {
                @Override
                public int applyAsInt(ImageRef ref) {
                    return ref.score;
                }
            }).reversed());
            Set<String> downloaded = new HashSet<>();
            Result best = null;
            for (ImageRef ref : refs) {
                if (out.tried >= 12) {
                    break;
                }
                if (ref.url == null || ref.url.isEmpty() || !downloaded.add(ref.url)) {
                    continue;
                }
                out.tried++;
                HttpURLConnection image = null;
                try {
                    image = open(ref.url,
                            "image/avif,image/webp,image/png,image/jpeg,image/*,*/*;q=0.5",
                            resolvedPage);
                    byte[] raw = readLimited(image.getInputStream(), 5_000_000);
                    String finalUrl = image.getURL() == null ? ref.url : image.getURL().toString();
                    BitmapData data = validateAndEncode(raw);
                    if (data == null) {
                        continue;
                    }
                    int finalScore = ref.score + dimensionBonus(data.width, data.height);
                    if (best == null || finalScore > best.score) {
                        best = new Result();
                        best.dataUrl = data.dataUrl;
                        best.imageUrl = finalUrl;
                        best.method = ref.method;
                        best.width = data.width;
                        best.height = data.height;
                        best.score = finalScore;
                        best.tried = out.tried;
                    }
                    if (finalScore >= 185) {
                        break;
                    }
                } catch (Exception ignored) {
                    // Try the next grounded image reference.
                } finally {
                    if (image != null) {
                        image.disconnect();
                    }
                }
            }
            return best == null ? out : best;
        } catch (Exception ignored) {
            return out;
        } finally {
            if (page != null) {
                page.disconnect();
            }
        }
    }

    static List<String> extractImageUrlsForTest(String html, String baseUrl, String modelHint) {
        List<ImageRef> refs = extractImageRefs(html, baseUrl, modelHint, "", "");
        refs.sort(Comparator.comparingInt(new ToIntFunction() {
            @Override
            public final int applyAsInt(Object obj) {
                return ((ImageHarvester.ImageRef) obj).score;
            }
        }).reversed());
        List<String> out = new ArrayList<>();
        for (ImageRef r : refs) {
            if (!out.contains(r.url)) {
                out.add(r.url);
            }
        }
        return out;
    }

    private static List<ImageRef> extractImageRefs(String html, String baseUrl, String modelHint, String familyHint, String titleHint) {
        String titleHint2;
        String modelHint2;
        String familyHint2;
        String titleHint3;
        List<ImageRef> out = new ArrayList<>();
        if (html != null && !html.isEmpty()) {
            try {
                Document doc = Jsoup.parse(html, baseUrl);
                String modelHint3 = baseUrl;
                String familyHint3 = modelHint;
                String titleHint4 = familyHint;
                String titleHint5 = titleHint;
                List<ImageRef> out2 = out;
                try {
                    addMeta(doc, out2, "meta[property=og:image]", "content", "og:image", 125, modelHint3, familyHint3, titleHint4, titleHint5);
                    addMeta(doc, out2, "meta[property=og:image:secure_url]", "content", "og:image:secure", 128, modelHint3, familyHint3, titleHint4, titleHint5);
                    addMeta(doc, out2, "meta[name=twitter:image]", "content", "twitter:image", 116, modelHint3, familyHint3, titleHint4, titleHint5);
                    addMeta(doc, out2, "meta[name=twitter:image:src]", "content", "twitter:image:src", 116, modelHint3, familyHint3, titleHint4, titleHint5);
                    addMeta(doc, out2, "meta[itemprop=image]", "content", "itemprop:image", 112, modelHint3, familyHint3, titleHint4, titleHint5);
                    titleHint5 = titleHint5;
                    titleHint4 = titleHint4;
                    familyHint3 = familyHint3;
                    modelHint3 = modelHint3;
                    out2 = out2;
                    addMeta(doc, out2, "link[rel=image_src]", "href", "image_src", 110, modelHint3, familyHint3, titleHint4, titleHint5);
                    out = out2;
                    titleHint2 = modelHint3;
                    modelHint2 = familyHint3;
                    familyHint2 = titleHint4;
                    titleHint3 = titleHint5;
                    try {
                        Iterator<Element> it = doc.select("script[type=application/ld+json]").iterator();
                        while (it.hasNext()) {
                            Element script = it.next();
                            String json = script.data();
                            if (json == null || json.trim().isEmpty()) {
                                json = script.html();
                            }
                            List<ImageRef> out3 = out;
                            String baseUrl2 = titleHint2;
                            try {
                                collectJsonLdImages(json, baseUrl2, out3, modelHint2, familyHint2, titleHint3);
                                out = out3;
                                titleHint2 = baseUrl2;
                            } catch (Exception e) {
                                out = out3;
                                modelHint2 = modelHint2;
                                familyHint2 = familyHint2;
                                titleHint3 = titleHint3;
                                titleHint2 = baseUrl2;
                            }
                        }
                        Iterator<Element> it2 = doc.select("img").iterator();
                        while (it2.hasNext()) {
                            Element img = it2.next();
                            String context = joinText(img.attr("alt"), img.attr("title"), img.attr("class"), img.attr("id"));
                            add(out, attrUrl(img, "src"), "img:src", context, 58, titleHint2, modelHint2, familyHint2, titleHint3);
                            add(out, attrUrl(img, "data-src"), "img:data-src", context, 70, titleHint2, modelHint2, familyHint2, titleHint3);
                            add(out, attrUrl(img, "data-lazy-src"), "img:data-lazy-src", context, 70, titleHint2, modelHint2, familyHint2, titleHint3);
                            add(out, attrUrl(img, "data-original"), "img:data-original", context, 70, titleHint2, modelHint2, familyHint2, titleHint3);
                            addSrcset(out, img.attr("srcset"), "img:srcset", context, 76, titleHint2, modelHint2, familyHint2, titleHint3);
                            addSrcset(out, img.attr("data-srcset"), "img:data-srcset", context, 76, titleHint2, modelHint2, familyHint2, titleHint3);
                        }
                        Iterator<Element> it3 = doc.select("picture source[srcset], source[data-srcset]").iterator();
                        while (it3.hasNext()) {
                            Element source = it3.next();
                            String context2 = joinText(source.attr("media"), source.attr("type"));
                            addSrcset(out, source.attr("srcset"), "picture:srcset", context2, 82, titleHint2, modelHint2, familyHint2, titleHint3);
                            addSrcset(out, source.attr("data-srcset"), "picture:data-srcset", context2, 82, titleHint2, modelHint2, familyHint2, titleHint3);
                        }
                    } catch (Exception e2) {
                    }
                } catch (Exception e3) {
                    out = out2;
                    titleHint2 = modelHint3;
                    modelHint2 = familyHint3;
                    familyHint2 = titleHint4;
                    titleHint3 = titleHint5;
                }
            } catch (Exception e4) {
                titleHint2 = baseUrl;
                modelHint2 = modelHint;
                familyHint2 = familyHint;
                titleHint3 = titleHint;
            }
            List<ImageRef> out4 = out;
            collectEscapedImageUrls(html, titleHint2, out4, modelHint2, familyHint2, titleHint3);
            List<ImageRef> dedup = new ArrayList<>();
            for (ImageRef candidate : out4) {
                ImageRef old = null;
                Iterator<ImageRef> it4 = dedup.iterator();
                while (true) {
                    if (!it4.hasNext()) {
                        break;
                    }
                    ImageRef x = it4.next();
                    if (sameUrl(x.url, candidate.url)) {
                        old = x;
                        break;
                    }
                }
                if (old == null) {
                    dedup.add(candidate);
                } else if (candidate.score > old.score) {
                    old.score = candidate.score;
                    old.method = candidate.method;
                    old.context = candidate.context;
                }
            }
            return dedup;
        }
        return out;
    }

    private static void addMeta(Document doc, List<ImageRef> out, String selector, String attr, String method, int baseScore, String baseUrl, String modelHint, String familyHint, String titleHint) {
        Iterator<Element> it = doc.select(selector).iterator();
        while (it.hasNext()) {
            Element e = it.next();
            add(out, e.attr(attr), method, e.outerHtml(), baseScore, baseUrl, modelHint, familyHint, titleHint);
        }
    }

    private static void collectJsonLdImages(String raw, String baseUrl, List<ImageRef> out, String modelHint, String familyHint, String titleHint) {
        Object jSONArray;
        if (raw == null) {
            return;
        }
        String cleaned = raw.trim();
        if (cleaned.isEmpty()) {
            return;
        }
        try {
            if (!cleaned.startsWith("[")) {
                jSONArray = new JSONObject(cleaned);
            } else {
                try {
                    jSONArray = new JSONArray(cleaned);
                } catch (Exception e) {
                    return;
                }
            }
            Object root = jSONArray;
            try {
                walkJson(root, "", baseUrl, out, modelHint, familyHint, titleHint);
            } catch (Exception e2) {
            }
        } catch (Exception e3) {
        }
    }

    private static void walkJson(Object node, String context, String baseUrl, List<ImageRef> out, String modelHint, String familyHint, String titleHint) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            String localContext = joinText(context, o.optString("name", ""), o.optString("sku", ""), o.optString("mpn", ""), o.optString("model", ""), o.optString("@type", ""));
            JSONArray names = o.names();
            if (names == null) {
                return;
            }
            for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i, "");
                Object value = o.opt(key);
                if ("image".equalsIgnoreCase(key) || "thumbnailUrl".equalsIgnoreCase(key) || "contentUrl".equalsIgnoreCase(key)) {
                    collectImageValue(value, localContext, baseUrl, out, modelHint, familyHint, titleHint);
                } else {
                    walkJson(value, localContext, baseUrl, out, modelHint, familyHint, titleHint);
                }
            }
            return;
        }
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i2 = 0; i2 < a.length(); i2++) {
                walkJson(a.opt(i2), context, baseUrl, out, modelHint, familyHint, titleHint);
            }
        }
    }

    private static void collectImageValue(Object value, String context, String baseUrl, List<ImageRef> out, String modelHint, String familyHint, String titleHint) {
        if (value instanceof String) {
            add(out, (String) value, "jsonld:image", context, 122, baseUrl, modelHint, familyHint, titleHint);
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) {
                collectImageValue(a.opt(i), context, baseUrl, out, modelHint, familyHint, titleHint);
            }
            return;
        }
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            add(out, o.optString("url", ""), "jsonld:image:url", context, 122, baseUrl, modelHint, familyHint, titleHint);
            add(out, o.optString("contentUrl", ""), "jsonld:image:contentUrl", context, 122, baseUrl, modelHint, familyHint, titleHint);
        }
    }

    private static void addSrcset(List<ImageRef> out, String srcset, String method, String context, int baseScore, String baseUrl, String modelHint, String familyHint, String titleHint) {
        if (srcset == null || srcset.trim().isEmpty()) {
            return;
        }
        String[] entries = srcset.split(",");
        for (String entry : entries) {
            String[] parts = entry.trim().split("\\s+");
            if (parts.length != 0) {
                int bonus = 0;
                if (parts.length > 1) {
                    String descriptor = parts[parts.length - 1].toLowerCase(Locale.ROOT);
                    try {
                        if (descriptor.endsWith("w")) {
                            bonus = Math.min(22, Integer.parseInt(descriptor.substring(0, descriptor.length() - 1)) / 100);
                        } else if (descriptor.endsWith("x")) {
                            bonus = Math.min(18, Math.round(Float.parseFloat(descriptor.substring(0, descriptor.length() - 1)) * 6.0f));
                        }
                    } catch (Exception e) {
                    }
                }
                add(out, parts[0], method, context, baseScore + bonus, baseUrl, modelHint, familyHint, titleHint);
            }
        }
    }

    private static void collectEscapedImageUrls(String html, String baseUrl, List<ImageRef> out, String modelHint, String familyHint, String titleHint) {
        if (html == null) {
            return;
        }
        String normalized = html.replace("\\/", "/").replace("\\u0026", "&");
        Matcher m = Pattern.compile("https?://[^\\\"'<>\\s]+?\\.(?:jpg|jpeg|png|webp)(?:\\?[^\\\"'<>\\s]*)?", 2).matcher(normalized);
        int n = 0;
        while (m.find()) {
            int n2 = n + 1;
            if (n < 40) {
                add(out, m.group(), "inline-js", "", 48, baseUrl, modelHint, familyHint, titleHint);
                n = n2;
            } else {
                return;
            }
        }
    }

    private static void add(List<ImageRef> out, String rawUrl, String method, String context, int baseScore, String baseUrl, String modelHint, String familyHint, String titleHint) {
        String url = absolutize(baseUrl, decodeHtml(rawUrl));
        if (usableUrl(url)) {
            String hay = lower(url + " " + context + " " + titleHint);
            int score = baseScore;
            String modelCanon = canon(modelHint);
            String hayCanon = canon(hay);
            if (!modelCanon.isEmpty() && hayCanon.contains(modelCanon)) {
                score += 72;
            }
            for (String token : meaningfulTokens(modelHint + " " + familyHint)) {
                if (hay.contains(token.toLowerCase(Locale.ROOT))) {
                    score += 8;
                }
            }
            if (hay.contains("product") || hay.contains("remote") || hay.contains("media") || hay.contains("catalog")) {
                score += 8;
            }
            if (hay.matches(".*(logo|icon|sprite|avatar|flag|badge|spinner|placeholder|loading|pixel|tracking|banner|header|footer|cookie).*")) {
                score -= 90;
            }
            if (url.toLowerCase(Locale.ROOT).endsWith(".svg") || url.toLowerCase(Locale.ROOT).endsWith(".gif")) {
                score -= 80;
            }
            out.add(new ImageRef(url, method, context, score));
        }
    }

    private static String attrUrl(Element e, String attr) {
        String v = e.attr(attr);
        return v == null ? "" : v.trim();
    }

    private static boolean usableUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String s = lower(url);
        return ((!s.startsWith("http://") && !s.startsWith("https://")) || s.startsWith("data:") || s.contains("javascript:")) ? false : true;
    }

    private static String absolutize(String baseUrl, String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return "";
        }
        try {
            if (v.startsWith("//")) {
                URI base = new URI(baseUrl);
                return (base.getScheme() == null ? "https" : base.getScheme()) + ":" + v;
            }
            return new URL(new URL(baseUrl), v).toString();
        } catch (Exception e) {
            return (v.startsWith("http://") || v.startsWith("https://")) ? v : "";
        }
    }

    private static boolean sameUrl(String a, String b) {
        try {
            URI aa = new URI(a);
            URI bb = new URI(b);
            if (lower(aa.getHost()).replaceFirst("^www\\.", "").equals(lower(bb.getHost()).replaceFirst("^www\\.", ""))) {
                if (safe(aa.getPath()).replaceAll("/+$", "").equalsIgnoreCase(safe(bb.getPath()).replaceAll("/+$", ""))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return safe(a).equalsIgnoreCase(safe(b));
        }
    }

    private static HttpURLConnection open(String url, String accept, String referer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8500);
        c.setReadTimeout(11000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 Chrome/128 Safari/537.36 FlipCheck/0.51");
        c.setRequestProperty("Accept", accept);
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9,it;q=0.7");
        c.setRequestProperty("Cache-Control", "no-cache");
        if (referer != null && !referer.isEmpty()) {
            c.setRequestProperty("Referer", referer);
        }
        int code = c.getResponseCode();
        if (code < 200 || code >= 400) {
            throw new IllegalStateException("HTTP " + code);
        }
        return c;
    }

    private static final class BitmapData {
        String dataUrl;
        int height;
        int width;

        private BitmapData() {
        }
    }

    private static BitmapData validateAndEncode(byte[] raw) {
        BitmapData bitmapData;
        if (raw != null && raw.length >= 800) {
            Bitmap bitmap = null;
            Bitmap scaled = null;
            try {
                try {
                    bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                    if (bitmap == null) {
                        return null;
                    }
                    int w = bitmap.getWidth();
                    int h = bitmap.getHeight();
                    int shortSide = Math.min(w, h);
                    int longSide = Math.max(w, h);
                    long area = (long) w * (long) h;
                    double ratio = (double) longSide / (double) Math.max(1, shortSide);
                    if (shortSide < 85 || longSide < 220 || area < 45000 || ratio > 8.2d) {
                        BitmapData bitmapData2 = null;
                        if (0 != 0 && null != bitmap && !scaled.isRecycled()) {
                            scaled.recycle();
                        }
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                        return bitmapData2;
                    }
                    scaled = scaleDown(bitmap, 900);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    scaled.compress(Bitmap.CompressFormat.JPEG, 84, out);
                    BitmapData data = new BitmapData();
                    bitmapData = null;
                    try {
                        data.dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), 2);
                        data.width = w;
                        data.height = h;
                        if (scaled != null && scaled != bitmap && !scaled.isRecycled()) {
                            scaled.recycle();
                        }
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                        return data;
                    } catch (Exception e) {
                        if (scaled != null && scaled != bitmap && !scaled.isRecycled()) {
                            scaled.recycle();
                        }
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                        return bitmapData;
                    }
                } catch (Exception e2) {
                    bitmapData = null;
                }
            } finally {
                if (scaled != null && scaled != bitmap && !scaled.isRecycled()) {
                    scaled.recycle();
                }
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        }
        return null;
    }

    private static Bitmap scaleDown(Bitmap src, int maxSide) {
        int max = Math.max(src.getWidth(), src.getHeight());
        if (max <= maxSide) {
            return src;
        }
        float s = (float) maxSide / (float) max;
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * s)), Math.max(1, Math.round(src.getHeight() * s)), true);
    }

    private static int dimensionBonus(int w, int h) {
        int shortSide = Math.min(w, h);
        int longSide = Math.max(w, h);
        int bonus = 0;
        if (longSide >= 1200) {
            bonus = 0 + 22;
        } else if (longSide >= 800) {
            bonus = 0 + 16;
        } else if (longSide >= 500) {
            bonus = 0 + 10;
        }
        return shortSide >= 300 ? bonus + 8 : bonus;
    }

    private static byte[] readLimited(InputStream in, int max) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                byte[] buf = new byte[8192];
                int total = 0;
                while (true) {
                    int n = in.read(buf);
                    if (n >= 0) {
                        total += n;
                        if (total > max) {
                            throw new IllegalStateException("response too large");
                        }
                        out.write(buf, 0, n);
                    } else {
                        byte[] byteArray = out.toByteArray();
                        out.close();
                        if (in != null) {
                            in.close();
                        }
                        return byteArray;
                    }
                }
            } finally {
            }
        } catch (Throwable th) {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private static List<String> meaningfulTokens(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        for (String x : s.split("[^A-Za-z0-9_-]+")) {
            String t = x.trim();
            if (t.length() >= 4 && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private static String joinText(String... xs) {
        StringBuilder b = new StringBuilder();
        if (xs != null) {
            for (String x : xs) {
                if (x != null && !x.trim().isEmpty()) {
                    if (b.length() > 0) {
                        b.append(' ');
                    }
                    b.append(x.trim());
                }
            }
        }
        return b.toString();
    }

    private static String decodeHtml(String s) {
        return s == null ? "" : s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("\\/", "/");
    }

    private static String canon(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
