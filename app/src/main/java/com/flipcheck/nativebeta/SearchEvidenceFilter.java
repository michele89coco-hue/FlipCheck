package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Category-neutral filter for text that is safe to place in an identification
 * query. UI state, clocks and noisy OCR remain visible in the evidence ledger,
 * but they must not steer retrieval.
 */
final class SearchEvidenceFilter {
    private SearchEvidenceFilter() {
    }

    static boolean isTransientDisplay(String raw) {
        String x = clean(raw).toUpperCase(Locale.ROOT);
        if (x.isEmpty()) {
            return false;
        }
        return x.matches("\\d{1,2}[:.]\\d{2}(?:\\s*(?:AM|PM))?")
                || x.matches("\\d{1,2}[/.-]\\d{1,2}(?:[/.-]\\d{2,4})?")
                || x.matches("[-+]?\\d+(?:[.,]\\d+)?\\s*(?:%|°[CF]|V|A|W|KW|HZ|RPM|MIN|SEC|H|KG|G|LB|OZ|L|ML|CM|MM)")
                || x.matches("(?:MON|TUE|WED|THU|FRI|SAT|SUN|MTWTFSS|SMTWTFS)")
                || x.matches("\\d{1,3}");
    }

    static boolean isControlLabel(String raw) {
        String x = clean(raw).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
        if (x.isEmpty()) {
            return false;
        }
        return x.matches("(?:AUTO|OFF|ON|RESET|START|STOP|POWER|MENU|OK|SET|MODE|PROGRAM|MANUAL|ENTER|CLEAR|HOME|BACK|EXIT|OPTIONS?|PAIR|VOICE|TEXT|SUBTITLE|TOP PICKS|CYCLE START|START CYCLE|RAIN DELAY|NEXT|PREVIOUS|UP|DOWN|LEFT|RIGHT)")
                || x.matches("(?:MON|TUE|WED|THU|FRI|SAT|SUN)(?: (?:MON|TUE|WED|THU|FRI|SAT|SUN))*");
    }

    /**
     * A printed control can be useful as a co-occurrence fingerprint without
     * being an identifier. This deliberately excludes ubiquitous controls and
     * keeps only uncommon service names or multi-word function labels. The
     * caller must still combine them with geometry/category evidence.
     */
    static boolean isDistinctiveControlLabel(String raw) {
        String x = clean(raw).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
        if (x.length() < 5 || isTransientDisplay(x)) {
            return false;
        }
        String[] words = x.split("\\s+");
        boolean hasDistinctiveToken = false;
        for (String word : words) {
            if (!isGenericControlToken(word)) {
                hasDistinctiveToken = true;
                break;
            }
        }
        return hasDistinctiveToken && (words.length >= 2 || x.length() >= 7);
    }

    static List<String> distinctiveControls(List<String> values, int limit) {
        List<String> ranked = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (values == null) {
            return ranked;
        }
        for (String raw : values) {
            String x = clean(raw);
            if (isDistinctiveControlLabel(x) && seen.add(x.toUpperCase(Locale.ROOT))) {
                ranked.add(x);
            }
        }
        Collections.sort(ranked, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                int byInformation = Integer.compare(distinctiveScore(right), distinctiveScore(left));
                return byInformation != 0 ? byInformation : left.compareToIgnoreCase(right);
            }
        });
        List<String> out = new ArrayList<>();
        if (limit <= 0) {
            return out;
        }
        for (String value : ranked) {
            out.add(value);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private static int distinctiveScore(String raw) {
        String x = clean(raw).toUpperCase(Locale.ROOT);
        if (x.isEmpty()) {
            return 0;
        }
        int words = x.split("\\s+").length;
        int score = Math.min(48, x.length()) + Math.min(72, words * 18);
        if (words >= 2) {
            score += 30;
        }
        if (x.matches(".*\\d.*")) {
            score -= 20;
        }
        return score;
    }

    private static boolean isGenericControlToken(String word) {
        return word.matches("(?:AUTO|OFF|ON|RESET|START|STOP|POWER|MENU|OK|SET|SETTINGS|MODE|PROGRAM|MANUAL|ENTER|CLEAR|HOME|BACK|EXIT|OPTION|OPTIONS|PAIR|VOICE|TEXT|SUBTITLE|TV|INFO|SOURCE|SOURCES|INPUT|GUIDE|LIST|MUTE|VOLUME|CHANNEL|PLAY|PAUSE|RECORD|NEXT|PREVIOUS|UP|DOWN|LEFT|RIGHT)");
    }

    static boolean isIdentifierLike(String raw) {
        String x = clean(raw);
        if (x.length() < 3 || x.length() > 56 || x.contains(" ")) {
            return false;
        }
        if (!x.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")) {
            return false;
        }
        String c = x.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return c.matches(".*[0-9].*") && (c.matches(".*[A-Z].*") || c.matches("[0-9]{6,18}"));
    }

    static boolean isSearchableLiteral(String raw) {
        String x = clean(raw);
        if (x.length() < 2 || x.length() > 72 || x.startsWith("{") || x.startsWith("[")) {
            return false;
        }
        if (isTransientDisplay(x) || isControlLabel(x)) {
            return false;
        }
        String compact = x.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (compact.isEmpty() || compact.matches("[0-9]+")) {
            return false;
        }
        return !x.matches("[\\p{Punct}\\s]+") && !probableOcrNoise(x);
    }

    static boolean isSoftOcrLiteral(String raw) {
        String x = clean(raw);
        if (x.length() < 4 || x.length() > 32 || x.contains(" ")
                || !isSearchableLiteral(x) || x.matches(".*[0-9].*")) {
            return false;
        }
        if (!x.matches("[A-Za-z][A-Za-z&.'-]*")) {
            return false;
        }
        return x.matches(".*[A-Z].*") && x.matches(".*[a-z].*");
    }

    static boolean probableOcrNoise(String raw) {
        String x = clean(raw).toUpperCase(Locale.ROOT);
        if (x.length() < 4 || x.contains(" ") || x.matches(".*[0-9].*")) {
            return false;
        }
        if (!x.matches("[A-Z]+")) {
            return false;
        }
        int vowels = 0;
        for (int i = 0; i < x.length(); i++) {
            if ("AEIOUY".indexOf(x.charAt(i)) >= 0) {
                vowels++;
            }
        }
        return x.length() >= 7 && vowels == 0;
    }

    static List<String> uniqueSearchable(List<String> values, int limit) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (values == null) {
            return out;
        }
        for (String raw : values) {
            String x = clean(raw);
            String key = x.toUpperCase(Locale.ROOT);
            if (isSearchableLiteral(x) && seen.add(key)) {
                out.add(x);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}
