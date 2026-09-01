package com.flipcheck.nativebeta;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prevents a shared/manual family page from proving the wrong physical variant. */
final class ReferenceScopePolicy {
    private ReferenceScopePolicy() {
    }

    static String hardViolation(Models.Identification id, Models.CandidateScore candidate) {
        if (!isIrrigationController(id) || candidate == null) {
            return "";
        }
        int observed = observedStationCount(id);
        int proposed = candidateStationCount(candidate);
        if (observed > 0 && proposed > 0 && observed != proposed) {
            return "photographed controller has " + observed + " stations/sliders, candidate has "
                    + proposed;
        }
        return "";
    }

    static boolean allowsExactConfirmation(Models.Identification id,
                                           Models.CandidateScore candidate) {
        if (!isIrrigationController(id)) {
            return true;
        }
        if (!hardViolation(id, candidate).isEmpty()) {
            return false;
        }
        int observed = observedStationCount(id);
        int proposed = candidateStationCount(candidate);
        return observed > 0 && proposed == observed
                && factTrue(candidate, "exact_identity_supported")
                && factTrue(candidate, "source_exact_reference")
                && factTrue(candidate, "visual_reference_checked")
                && factInt(candidate, "visual_match_confidence") >= 88
                && factTrue(candidate, "disproof_passed");
    }

    /** Keeps a plausible controller reference visible without presenting it as exact. */
    static void enforceCandidateScope(Models.Identification id,
                                      Models.CandidateScore candidate) {
        if (!isIrrigationController(id) || candidate == null || candidate.model.isEmpty()
                || allowsExactConfirmation(id, candidate)) {
            return;
        }
        if (candidate.probableReference.isEmpty()) {
            candidate.probableReference = candidate.model;
            candidate.probableReferenceConfidence = Math.max(
                    candidate.probableReferenceConfidence, 72);
        }
        candidate.model = "";
        setFact(candidate, "exact_identity_supported", "false");
        setFact(candidate, "identity_level", "family");
    }

    static int observedStationCount(Models.Identification id) {
        if (id == null) {
            return 0;
        }
        String text = (id.visualFacts + " " + id.spatialSignature + " "
                + id.visibleLabels + " " + id.photoIdentityFields + " "
                + (id.localScan == null ? "" : id.localScan.joinedText()))
                .toLowerCase(Locale.ROOT);
        Matcher words = Pattern.compile(
                "\\b(two|three|four|five|six|seven|eight|nine|ten|[2-9]|10)\\b.{0,24}\\b(station|zone|slider)")
                .matcher(text);
        if (words.find()) {
            return number(words.group(1));
        }
        Matcher reverse = Pattern.compile(
                "\\b(station|zone|slider)[a-z ]{0,18}(two|three|four|five|six|seven|eight|nine|ten|[2-9]|10)\\b")
                .matcher(text);
        return reverse.find() ? number(reverse.group(2)) : 0;
    }

    static int candidateStationCount(Models.CandidateScore candidate) {
        if (candidate == null) {
            return 0;
        }
        StringBuilder text = new StringBuilder(join(candidate.brand, candidate.family,
                candidate.model, candidate.probableReference, candidate.evidence));
        for (String fact : candidate.candidateFacts) {
            text.append(' ').append(fact);
        }
        String value = text.toString().toLowerCase(Locale.ROOT);
        // A model number is opaque.  In particular, Orbit 57004 is a
        // six-station controller: the trailing digits must never be decoded as
        // a station count.  Counts are accepted only when a source/candidate
        // states the physical count explicitly.
        Matcher keyed = Pattern.compile(
                "\\b(?:station_count|zone_count|slider_count|stations|zones)\\s*[=:]\\s*"
                        + "(two|three|four|five|six|seven|eight|nine|ten|[2-9]|10)\\b")
                .matcher(value);
        if (keyed.find()) {
            return number(keyed.group(1));
        }
        Matcher matcher = Pattern.compile(
                "\\b(two|three|four|five|six|seven|eight|nine|ten|[2-9]|10)[ -]"
                        + "(?:station|zone|slider)")
                .matcher(value);
        return matcher.find() ? number(matcher.group(1)) : 0;
    }

    private static boolean isIrrigationController(Models.Identification id) {
        String value = id == null ? "" : (safe(id.categoryKey) + " "
                + safe(id.category)).toLowerCase(Locale.ROOT);
        return value.contains("irrigation") || value.contains("sprinkler")
                || value.contains("watering controller");
    }

    private static int number(String value) {
        String x = safe(value).toLowerCase(Locale.ROOT);
        if (x.matches("[0-9]+")) {
            return Integer.parseInt(x);
        }
        String[] words = {"", "", "two", "three", "four", "five", "six",
                "seven", "eight", "nine", "ten"};
        for (int i = 2; i < words.length; i++) {
            if (words[i].equals(x)) {
                return i;
            }
        }
        return 0;
    }

    private static boolean factTrue(Models.CandidateScore c, String key) {
        return "true".equalsIgnoreCase(fact(c, key));
    }

    private static int factInt(Models.CandidateScore c, String key) {
        try {
            return Integer.parseInt(fact(c, key));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String fact(Models.CandidateScore c, String key) {
        String prefix = key + "=";
        for (String raw : c.candidateFacts) {
            if (safe(raw).startsWith(prefix)) {
                return raw.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static void setFact(Models.CandidateScore c, String key, String value) {
        String prefix = key + "=";
        c.candidateFacts.removeIf(raw -> safe(raw).startsWith(prefix));
        c.candidateFacts.add(prefix + value);
    }

    private static String join(String... values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(value.trim());
            }
        }
        return out.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
