package com.flipcheck.nativebeta;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LocalVisionEngine {
    private final Context context;
    private static final Pattern LABELED_CODE = Pattern.compile("(?i)\\b(P\\s*[/.-]?\\s*N|PART\\s*(?:NO|NUMBER|#)?|MODEL(?:\\s*NO)?|MOD(?:ELLO)?|SKU|REF(?:ERENCE)?|TYPE|ITEM)\\s*[:#=.-]?\\s*([A-Z0-9][A-Z0-9._/\\-]{2,30})");
    private static final Pattern REVISION = Pattern.compile("(?i)\\bREV(?:ISION)?\\s*[:#=.-]?\\s*([A-Z0-9._/-]{1,12})");
    private static final Pattern YEAR = Pattern.compile("\\b(19[7-9]\\d|20[0-3]\\d)\\b");
    private static final Pattern CODEISH = Pattern.compile("\\b(?=[A-Z0-9._/-]{4,26}\\b)(?=[A-Z0-9._/-]*\\d)(?=[A-Z0-9._/-]*[A-Z])[A-Z0-9]+(?:[-._/][A-Z0-9]+)+\\b", 2);
    private static final Pattern NUMERIC_PART = Pattern.compile("\\b\\d{5,14}(?:[-/][A-Z0-9]{1,8})?\\b", 2);

    LocalVisionEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    Models.LocalScan scan(List<Uri> uris) throws Exception {
        long started = System.currentTimeMillis();
        Models.LocalScan result = new Models.LocalScan();
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        BarcodeScanner barcodeScanner = BarcodeScanning.getClient();
        Map<String, Models.Identifier> unique = new LinkedHashMap<>();
        Map<String, String> uniqueBarcodes = new LinkedHashMap<>();

        try {
            if (uris == null) {
                return result;
            }
            for (int imageIndex = 0; imageIndex < uris.size(); imageIndex++) {
                Uri uri = uris.get(imageIndex);
                Map<String, String> uniqueLines = new LinkedHashMap<>();
                Bitmap base = null;
                try {
                    base = decodeBitmap(uri);
                    if (base != null) {
                        Bitmap scaled = scaleDown(base, 1900);
                        if (scaled != base) {
                            base.recycle();
                            base = scaled;
                        }
                        InputImage original = InputImage.fromBitmap(base, 0);
                        // Una sola bitmap campionata alimenta OCR, barcode e ritagli. Questo
                        // evita due decodifiche full-resolution contemporanee sui telefoni.
                        tryTextPass(recognizer, original, imageIndex, "original", unique, uniqueLines);
                        try {
                            List<Barcode> barcodes = Tasks.await(barcodeScanner.process(original),
                                    20, TimeUnit.SECONDS);
                            if (barcodes != null) {
                                for (Barcode barcode : barcodes) {
                                    String raw = clean(barcode.getRawValue());
                                    if (!raw.isEmpty()) {
                                        uniqueBarcodes.put(canon(raw), raw);
                                        add(unique, new Models.Identifier("BARCODE", raw,
                                                imageIndex, "mlkit_barcode"));
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                            // Barcode opzionale: l'OCR resta utilizzabile.
                        }
                        tryBitmapPass(recognizer, rotate(base, 90), imageIndex, "rot90", unique, uniqueLines, true);
                        tryBitmapPass(recognizer, rotate(base, 180), imageIndex, "rot180", unique, uniqueLines, true);
                        tryBitmapPass(recognizer, rotate(base, 270), imageIndex, "rot270", unique, uniqueLines, true);

                        int stripWidth = Math.max(180, Math.round(base.getWidth() * 0.38f));
                        if (stripWidth < base.getWidth()) {
                            Bitmap left = Bitmap.createBitmap(base, 0, 0, stripWidth, base.getHeight());
                            Bitmap right = Bitmap.createBitmap(base, base.getWidth() - stripWidth, 0, stripWidth, base.getHeight());
                            tryCropFamily(recognizer, left, imageIndex, "left", unique, uniqueLines);
                            tryCropFamily(recognizer, right, imageIndex, "right", unique, uniqueLines);
                        }
                    }
                } catch (Exception ignored) {
                    // Le passate di recupero non devono annullare un OCR originale valido.
                } finally {
                    if (base != null && !base.isRecycled()) {
                        base.recycle();
                    }
                }

                StringBuilder merged = new StringBuilder();
                for (String line : uniqueLines.values()) {
                    if (line == null || line.trim().isEmpty()) {
                        continue;
                    }
                    if (merged.length() > 0) {
                        merged.append('\n');
                    }
                    merged.append(line.trim());
                }
                result.textByImage.add(merged.toString());
            }
        } finally {
            recognizer.close();
            barcodeScanner.close();
            result.identifiers.addAll(unique.values());
            result.barcodes.addAll(uniqueBarcodes.values());
            result.durationMs = System.currentTimeMillis() - started;
        }
        return result;
    }

    private void tryTextPass(TextRecognizer recognizer, InputImage image, int imageIndex, String pass,
                             Map<String, Models.Identifier> ids, Map<String, String> lines) {
        try {
            processTextPass(recognizer, image, imageIndex, pass, ids, lines);
        } catch (Exception ignored) {
        }
    }

    private void tryBitmapPass(TextRecognizer recognizer, Bitmap bitmap, int imageIndex, String pass,
                               Map<String, Models.Identifier> ids, Map<String, String> lines, boolean recycle) {
        try {
            runBitmapPass(recognizer, bitmap, imageIndex, pass, ids, lines, recycle);
        } catch (Exception ignored) {
            if (recycle && bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private void tryCropFamily(TextRecognizer recognizer, Bitmap crop, int imageIndex, String prefix,
                               Map<String, Models.Identifier> ids, Map<String, String> lines) {
        try {
            runCropFamily(recognizer, crop, imageIndex, prefix, ids, lines);
        } catch (Exception ignored) {
            if (crop != null && !crop.isRecycled()) {
                crop.recycle();
            }
        }
    }

    private void runCropFamily(TextRecognizer recognizer, Bitmap crop, int imageIndex, String prefix, Map<String, Models.Identifier> ids, Map<String, String> lines) throws Exception {
        if (crop == null) {
            return;
        }
        Bitmap prepared = crop;
        try {
            prepared = enlargeForOcr(crop, 1800);
            if (prepared != crop) {
                crop.recycle();
            }
            runBitmapPass(recognizer, prepared, imageIndex, prefix + "-0", ids, lines, false);
            runBitmapPass(recognizer, rotate(prepared, 90), imageIndex, prefix + "-90", ids, lines, true);
            runBitmapPass(recognizer, rotate(prepared, 270), imageIndex, prefix + "-270", ids, lines, true);
        } finally {
            if (prepared != null && !prepared.isRecycled()) {
                prepared.recycle();
            }
        }
    }

    private void runBitmapPass(TextRecognizer recognizer, Bitmap bitmap, int imageIndex, String pass, Map<String, Models.Identifier> ids, Map<String, String> lines, boolean recycle) throws Exception {
        if (bitmap == null) {
            return;
        }
        try {
            processTextPass(recognizer, InputImage.fromBitmap(bitmap, 0), imageIndex, pass, ids, lines);
        } finally {
            if (recycle && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private void processTextPass(TextRecognizer recognizer, InputImage image, int imageIndex, String pass, Map<String, Models.Identifier> ids, Map<String, String> lines) throws Exception {
        Text text = (Text) Tasks.await(recognizer.process(image), 25L, TimeUnit.SECONDS);
        String full = text == null ? "" : text.getText();
        if (full == null) {
            full = "";
        }
        collectIdentifiers(full, imageIndex, "mlkit_ocr_" + pass, ids);
        mergeUniqueLines(full, lines);
    }

    private static void mergeUniqueLines(String text, Map<String, String> out) {
        if (text == null) {
            return;
        }
        for (String raw : text.split("[\\r\\n]+")) {
            String line = clean(raw);
            if (!line.isEmpty()) {
                String key = canon(line);
                if (key.length() >= 2) {
                    out.putIfAbsent(key, line);
                }
            }
        }
    }

    private Bitmap decodeBitmap(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream first = this.context.getContentResolver().openInputStream(uri);
        try {
            BitmapFactory.decodeStream(first, null, bounds);
        } finally {
            if (first != null) {
                first.close();
            }
        }
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > 2600 && sample < 32) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        InputStream second = this.context.getContentResolver().openInputStream(uri);
        try {
            Bitmap decoded=BitmapFactory.decodeStream(second, null, options);
            return decoded==null?null:ImageDataEncoder.orientFromExif(this.context,uri,decoded);
        } finally {
            if (second != null) {
                second.close();
            }
        }
    }

    private static Bitmap scaleDown(Bitmap src, int maxSide) {
        int max = Math.max(src.getWidth(), src.getHeight());
        if (max <= maxSide) {
            return src;
        }
        float s = (float) maxSide / (float) max;
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * s)), Math.max(1, Math.round(src.getHeight() * s)), true);
    }

    private static Bitmap enlargeForOcr(Bitmap src, int targetLongSide) {
        int max = Math.max(src.getWidth(), src.getHeight());
        if (max >= targetLongSide || max <= 0) {
            return src;
        }
        float s = (float) targetLongSide / (float) max;
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * s)), Math.max(1, Math.round(src.getHeight() * s)), true);
    }

    private static Bitmap rotate(Bitmap src, int degrees) {
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private static void collectIdentifiers(String text, int imageIndex, String origin, Map<String, Models.Identifier> out) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String normalized = text.replace('\u00A0', ' ');
        // Never flatten line breaks before pairing labels with values. ML Kit may
        // return a stray "P" and "N" on adjacent rows of a control chart; joining
        // the page would fabricate "P/N <next token>" and turn document text into
        // a product part number.
        for (String rawLine : normalized.split("[\\r\\n]+")) {
            Matcher labeled = LABELED_CODE.matcher(rawLine.trim());
            while (labeled.find()) {
                String label = normalizeLabel(labeled.group(1));
                String value = clean(labeled.group(2));
                if (isPlausibleProductCode(value)) {
                    add(out, new Models.Identifier(label, value, imageIndex, origin + "_labeled"));
                }
            }
        }
        Matcher rev = REVISION.matcher(normalized);
        while (rev.find()) {
            String value2 = clean(rev.group(1));
            if (!value2.isEmpty()) {
                add(out, new Models.Identifier("REV", value2, imageIndex, origin + "_context"));
            }
        }
        Matcher year = YEAR.matcher(normalized);
        while (year.find()) {
            add(out, new Models.Identifier("YEAR", year.group(1), imageIndex, origin + "_context"));
        }
        Matcher codeish = CODEISH.matcher(normalized.toUpperCase(Locale.ROOT));
        while (codeish.find()) {
            String value3 = clean(codeish.group());
            if (looksUsefulUnlabelled(value3)) {
                add(out, new Models.Identifier("CODE", value3, imageIndex, origin + "_unlabelled"));
            }
        }
        Matcher numeric = NUMERIC_PART.matcher(normalized.toUpperCase(Locale.ROOT));
        while (numeric.find()) {
            String value4 = clean(numeric.group());
            if (value4.length() >= 5 && !isLikelyYear(value4)) {
                add(out, new Models.Identifier("CODE", value4, imageIndex, origin + "_unlabelled"));
            }
        }
    }

    static List<Models.Identifier> collectIdentifiersForTest(String text) {
        Map<String, Models.Identifier> out = new LinkedHashMap<>();
        collectIdentifiers(text, 0, "test", out);
        return new ArrayList<>(out.values());
    }

    private static boolean isPlausibleProductCode(String s) {
        String c = canon(s);
        if (c.length() < 3 || c.length() > 30 || c.matches("(?i)(AUTO|OFF|ON|RESET|START|STOP|PROGRAM|INTERVAL|MANUAL|WATERING|STATION|WEEK)")) {
            return false;
        }
        return c.matches(".*\\d.*");
    }

    private static void add(Map<String, Models.Identifier> out, Models.Identifier id) {
        if (id.value.length() < 3) {
            return;
        }
        String key = canon(id.value);
        if (key.isEmpty()) {
            return;
        }
        Models.Identifier existing = out.get(key);
        if (existing == null || priority(id.label) > priority(existing.label)) {
            out.put(key, id);
        }
    }

    private static int priority(String label) {
        String s = label == null ? "" : label.toUpperCase(Locale.ROOT);
        if (s.startsWith("MODEL") || s.equals("MOD")) {
            return 100;
        }
        if (s.equals("PN") || s.equals("PART")) {
            return 95;
        }
        if (s.equals("SKU") || s.equals("REF") || s.equals("TYPE") || s.equals("ITEM")) {
            return 85;
        }
        if (s.equals("BARCODE")) {
            return 80;
        }
        return s.equals("CODE") ? 30 : 10;
    }

    private static String normalizeLabel(String s) {
        String x = s != null ? s.toUpperCase(Locale.ROOT).replaceAll("\\s+", "") : "";
        if (x.startsWith("P") && x.contains("N")) {
            return "PN";
        }
        if (x.startsWith("PART")) {
            return "PART";
        }
        if (x.startsWith("MODEL") || x.startsWith("MOD")) {
            return "MODEL";
        }
        if (x.startsWith("REF")) {
            return "REF";
        }
        return x;
    }

    private static boolean looksUsefulUnlabelled(String s) {
        String c = canon(s);
        if (c.length() >= 4 && !c.matches("(?i)(AUTO|OFF|ON|RESET|START|STOP|PROGRAM|INTERVAL|WEEK|FIRST|SECOND|MANUAL|WATERING|STATION)") && c.matches(".*\\d.*")) {
            return c.matches(".*[A-Z].*") || s.contains("-") || s.contains("/") || s.contains(".");
        }
        return false;
    }

    private static boolean isLikelyYear(String s) throws NumberFormatException {
        try {
            int n = Integer.parseInt(s.replaceAll("\\D", ""));
            return n >= 1970 && n <= 2035;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isStrongIdentifierLabel(String label) {
        String l = label == null ? "" : label.toUpperCase(Locale.ROOT);
        return l.startsWith("MODEL") || l.equals("PN") || l.equals("PART") || l.equals("SKU") || l.equals("REF") || l.equals("TYPE") || l.equals("ITEM") || l.equals("BARCODE");
    }

    static List<String> normalizeIdentifierVariants(String value) {
        List<String> out = new ArrayList<>();
        String original = clean(value).toUpperCase(Locale.ROOT);
        if (original.isEmpty()) {
            return out;
        }
        addUnique(out, original);
        String noRev = original.replaceAll("(?i)[\\s_-]+REV(?:ISION)?[\\s._-]*[A-Z0-9]+$", "").trim();
        addUnique(out, noRev);
        Matcher root = Pattern.compile("^([A-Z]*\\d{4,}[A-Z]*)(?:[-_/][A-Z0-9]{1,8})+$", 2).matcher(noRev);
        if (root.matches()) {
            addUnique(out, root.group(1).toUpperCase(Locale.ROOT));
        }
        String compact = noRev.replaceAll("[^A-Z0-9]", "");
        if (compact.length() >= 6 && compact.length() <= 24) {
            addUnique(out, compact);
        }
        return out;
    }

    private static void addUnique(List<String> out, String s) {
        if (s == null) {
            return;
        }
        String s2 = clean(s);
        if (s2.isEmpty()) {
            return;
        }
        String c = canon(s2);
        for (String x : out) {
            if (canon(x).equals(c)) {
                return;
            }
        }
        out.add(s2);
    }

    static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("^[\\s:;,#=]+|[\\s:;,#=]+$", "").replaceAll("\\s+", " ");
    }

    static String canon(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
