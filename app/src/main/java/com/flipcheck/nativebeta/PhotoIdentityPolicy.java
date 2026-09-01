package com.flipcheck.nativebeta;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a complete, physically bound identity visible in the supplied photos
 * into exact proof without pretending that a generic visual resemblance is an
 * identifier.  The model may propose the fields, but these deterministic gates
 * decide whether they are strong enough to close the identity.
 */
final class PhotoIdentityPolicy {
    private static final Pattern SERIAL_PATTERN = Pattern.compile("(?<![0-9])([0-9]{1,5})\\s*/\\s*([0-9]{1,5})(?!\\s*[/.-]\\s*[0-9])(?![0-9])");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<![0-9])(?:19|20)[0-9]{2}(?:\\s*[-/]\\s*[0-9]{2,4})?(?![0-9])");
    private static final Pattern ITEM_NUMBER_PATTERN = Pattern.compile("(?i)(?:\\bno\\.?|#|card[_ ]?number\\s*[=:]?)\\s*[a-z0-9-]+");

    private PhotoIdentityPolicy() {
    }

    /**
     * The observer is intentionally conservative, but its boolean must not be a
     * single-model veto when the same structured observation already contains a
     * complete, physically bound tuple.  This consolidates only literal fields
     * that are present in complementary photos; it never invents a web value.
     */
    static void consolidateObservation(Models.Identification id, Models.LocalScan local) {
        if (id == null || !id.photoIdentityPhysicalBinding
                || id.photoIdentityOverlayOrWatermark || !isComposite(id)
                || !hasComplementaryViews(id.photoViews)) {
            return;
        }
        // Card serial fractions are particularly easy to steal from a phone
        // screenshot, gallery counter or narrative date.  The card policy
        // validates them separately and fail-closed; the universal
        // consolidator must not manufacture a serial from raw OCR.
        String serial = CollectibleCardIdentityPolicy.isCard(id) ? ""
                : firstSerial(id.photoIdentityFields, id.visibleLabels,
                local == null ? null : local.textByImage);
        if (!empty(serial)) {
            addOnce(id.photoIdentityFields, "serial=" + serial);
            if (empty(id.photoIdentityCode)) {
                id.photoIdentityCode = serial;
            }
        }
        int fieldCount = substantialFieldCount(id.photoIdentityFields);
        boolean collectionBound = containsYearOrCollection(id.photoIdentityFields);
        boolean itemNumberBound = containsItemNumber(id.photoIdentityFields, id.visibleLabels);
        boolean enoughDiscriminators = fieldCount >= 6
                || (fieldCount >= 5 && itemNumberBound && !empty(serial));
        if (!collectionBound || !itemNumberBound || !enoughDiscriminators) {
            return;
        }
        id.photoIdentityComplete = true;
        id.photoIdentityConfidence = Math.max(id.photoIdentityConfidence,
                empty(serial) ? 91 : 94);
        if (empty(id.photoIdentityName) || truncated(id.photoIdentityName)) {
            id.photoIdentityName = composedName(id.photoIdentityFields);
        }
    }

    static boolean observationStrong(Models.Identification id) {
        return id != null && id.photoIdentityComplete
                && id.photoIdentityPhysicalBinding
                && !id.photoIdentityOverlayOrWatermark
                && id.photoIdentityConfidence >= 90
                && !empty(id.photoIdentityName)
                && !truncated(id.photoIdentityName)
                && id.photoIdentityFields.size() >= 2
                && !"none".equalsIgnoreCase(id.photoIdentityKind);
    }

    static boolean isComposite(Models.Identification id) {
        return id != null && "composite_markings".equalsIgnoreCase(id.photoIdentityKind);
    }

    /**
     * Returns a confidence only when a complete source code is independently
     * visible on the object.  Repeated OCR may repair one common glyph confusion
     * (for example B/8), but only when Vision saw the same code prefix and the
     * source-backed family name is also physically visible.
     */
    static int resolvedCodeBindingConfidence(Models.Identification id,
                                             Models.Identifier primary,
                                             String candidateCode,
                                             String family) {
        if (id == null || !completeCode(candidateCode)) {
            return 0;
        }
        String wanted = canon(candidateCode);
        if (wanted.length() < 7 || visibleCodePrefix(id, wanted) < 6) {
            return 0;
        }
        boolean familyVisible = empty(family) || containsInObservation(id, family);
        if (!familyVisible) {
            return 0;
        }
        if (primary != null && canon(primary.value).equals(wanted)) {
            return 98;
        }
        int exact = 0;
        int confusable = 0;
        if (id.localScan != null) {
            for (String page : id.localScan.textByImage) {
                if (page == null) {
                    continue;
                }
                for (String line : page.split("[\\r\\n]+")) {
                    String actual = canon(line);
                    if (actual.contains(wanted)) {
                        exact++;
                    } else if (containsOneConfusableVariant(actual, wanted)) {
                        confusable++;
                    }
                }
            }
        }
        if (exact >= 1) {
            return 97;
        }
        return confusable >= 2 ? 94 : 0;
    }

    static void promoteResolvedCodeIdentity(Models.Identification id,
                                            Models.CandidateScore c,
                                            String code, int confidence) {
        if (id == null || c == null || confidence < 90 || !completeCode(code)) {
            return;
        }
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityOverlayOrWatermark = false;
        id.photoIdentityComplete = true;
        id.photoIdentityKind = "identity_label";
        id.photoIdentityCode = clean(code);
        id.photoIdentityName = join(c.brand, c.family, code);
        id.photoIdentityConfidence = Math.max(id.photoIdentityConfidence,
                Math.min(97, confidence));
        if (!empty(c.brand)) {
            addOnce(id.photoIdentityFields, "brand=" + c.brand);
        }
        if (!empty(c.family)) {
            addOnce(id.photoIdentityFields, "family=" + c.family);
        }
        addOnce(id.photoIdentityFields, "model_code=" + clean(code));
    }

    static int sourceSupportedCompositeFieldCount(Models.Identification id,
                                                   Models.CandidateScore c,
                                                   Models.Source source) {
        if (!observationStrong(id) || !isComposite(id) || c == null || source == null) {
            return 0;
        }
        String hay = clean(c.brand + " " + c.family + " " + c.model + " "
                + c.probableReference + " " + c.evidence + " " + source.title + " "
                + source.snippet + " " + source.url).toLowerCase(Locale.ROOT);
        String canonicalHay = canon(hay);
        int count = 0;
        for (String raw : id.photoIdentityFields) {
            String value = fieldValue(raw);
            if (empty(value) || SERIAL_PATTERN.matcher(value).find()) {
                continue;
            }
            String canonicalValue = canon(value);
            boolean matched = canonicalValue.length() >= 3
                    && canonicalHay.contains(canonicalValue);
            if (!matched) {
                Matcher number = ITEM_NUMBER_PATTERN.matcher(value);
                if (number.find()) {
                    String digits = number.group().replaceAll("[^0-9]", "");
                    matched = !digits.isEmpty() && (hay.contains("#" + digits)
                            || hay.matches("(?s).*\\bno\\.?\\s*" + digits + "\\b.*")
                            || hay.matches("(?s).*card\\s*(?:number|no\\.?)?\\s*" + digits + "\\b.*"));
                }
            }
            if (matched) {
                count++;
            }
        }
        return count;
    }

    static boolean skipGeometryFloor(Models.CandidateScore c) {
        return factTrue(c, "photo_identity_supported")
                && "composite_markings".equalsIgnoreCase(fact(c, "photo_identity_kind"))
                && factInt(c, "photo_identity_matched_count") >= 4;
    }

    static int candidateScore(Models.Identification id, Models.CandidateScore c) {
        if (!observationStrong(id) || c == null || !factTrue(c, "photo_identity_supported")) {
            return 0;
        }
        int matched = factInt(c, "photo_identity_matched_count");
        if (matched < Math.min(3, id.photoIdentityFields.size())) {
            return 0;
        }
        int score = Math.round((id.photoIdentityConfidence * 40
                + c.textScore * 35 + c.webScore * 25) / 100.0f);
        if (isComposite(id) && matched >= 5) {
            score += 3;
        }
        return clamp(score);
    }

    static boolean canConfirm(Models.Identification id, Models.CandidateScore c,
                              Models.Identifier primary, int margin) {
        if (!observationStrong(id) || c == null || c.hardRejected
                || UniversalConsistencyGate.strongCandidateConflict(c)
                || !factTrue(c, "source_grounded")
                || !factTrue(c, "same_entity_role")
                || factTrue(c, "relationship_only")
                || !factTrue(c, "disproof_passed")
                || !factTrue(c, "photo_identity_supported")) {
            return false;
        }
        int matched = factInt(c, "photo_identity_matched_count");
        int sourceConfidence = factInt(c, "source_identity_confidence");
        if (isComposite(id)) {
            return id.photoIdentityFields.size() >= 5
                    && matched >= 5
                    && hasComplementaryViews(id.photoViews)
                    && sourceConfidence >= 65
                    && c.textScore >= 82
                    && c.webScore >= 55
                    && c.totalScore >= 82
                    && (margin >= 8 || matched >= 7);
        }
        if (!("identity_label".equalsIgnoreCase(id.photoIdentityKind)
                || "barcode".equalsIgnoreCase(id.photoIdentityKind)
                || "device_identity_screen".equalsIgnoreCase(id.photoIdentityKind))) {
            return false;
        }
        if (!completeCode(id.photoIdentityCode)
                || (!localCorroboratesCode(id, primary, id.photoIdentityCode)
                && resolvedCodeBindingConfidence(id, primary, id.photoIdentityCode,
                c.family) < 90)) {
            return false;
        }
        boolean candidateBindsCode = containsCanonical(c.model, id.photoIdentityCode)
                || containsCanonical(c.probableReference, id.photoIdentityCode)
                || factTrue(c, "exact_identity_supported");
        return candidateBindsCode && matched >= 2 && sourceConfidence >= 78
                && c.totalScore >= 80 && margin >= 8;
    }

    static void confirm(Models.Identification id, Models.CandidateScore c) {
        if (id == null || c == null) {
            return;
        }
        if (!empty(c.brand)) {
            id.brand = c.brand;
        }
        if (!empty(c.family)) {
            id.family = c.family;
        }
        id.model = clean(id.photoIdentityName);
        id.modelConfidence = Math.min(97, Math.max(90,
                Math.min(id.photoIdentityConfidence, c.totalScore + 8)));
        id.marketReady = true;
        id.disproofPassed = true;
        id.modelProof = "photo_complete_identity";
        id.nextPhotoRequest = "";
        id.nextPhotoReason = "";
        id.categoryConfidence = Math.max(id.categoryConfidence, 94);
        id.familyConfidence = Math.max(id.familyConfidence, 90);
        id.matchedVisualFacts.clear();
        id.matchedVisualFacts.addAll(id.photoIdentityFields);
        id.verificationSummary = "L'identità completa è leggibile e fisicamente legata all'oggetto; "
                + "i suoi campi compositi sono coerenti con la fonte e non restano contraddizioni forti.";
        id.decisionReason = "CONFIRMED v0.84: identità fotografica completa + fonte coerente + disproof superato.";
        addOnce(c.candidateFacts, "confirmed_by_photo_identity=true");
    }

    static boolean probableReferenceAllowed(Models.CandidateScore c, Models.Identification id) {
        return c != null && !c.hardRejected && c.probableReferenceConfidence >= 45
                && factTrue(c, "source_grounded")
                && !UniversalConsistencyGate.strongCandidateConflict(c)
                && !empty(c.probableReference)
                && !truncated(c.probableReference)
                && !IdentificationPipelineV082.isEvidenceGap(c.probableReference)
                && ConfidencePolicy.looksSpecific(c.probableReference, id);
    }

    private static int substantialFieldCount(List<String> fields) {
        List<String> seen = new ArrayList<>();
        if (fields == null) {
            return 0;
        }
        for (String raw : fields) {
            String value = fieldValue(raw);
            String key = canon(value);
            if (key.length() >= 2 && !seen.contains(key)) {
                seen.add(key);
            }
        }
        return seen.size();
    }

    private static boolean containsYearOrCollection(List<String> fields) {
        if (fields == null) {
            return false;
        }
        for (String raw : fields) {
            String x = clean(raw).toLowerCase(Locale.ROOT);
            if (YEAR_PATTERN.matcher(x).find() || x.startsWith("collection=")
                    || x.startsWith("set=") || x.startsWith("series=")
                    || x.startsWith("edition=")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsItemNumber(List<String> fields, List<String> labels) {
        return containsItemNumber(fields) || containsItemNumber(labels);
    }

    private static boolean containsItemNumber(List<String> values) {
        if (values == null) {
            return false;
        }
        for (String raw : values) {
            String x = clean(raw);
            if (ITEM_NUMBER_PATTERN.matcher(x).find()
                    || x.toLowerCase(Locale.ROOT).startsWith("item_number=")
                    || x.toLowerCase(Locale.ROOT).startsWith("card_number=")) {
                return true;
            }
        }
        return false;
    }

    @SafeVarargs
    private static String firstSerial(List<String>... groups) {
        if (groups == null) {
            return "";
        }
        for (List<String> group : groups) {
            if (group == null) {
                continue;
            }
            for (String raw : group) {
                Matcher matcher = SERIAL_PATTERN.matcher(clean(raw));
                if (matcher.find()) {
                    return matcher.group(1) + "/" + matcher.group(2);
                }
            }
        }
        return "";
    }

    private static String composedName(List<String> fields) {
        List<String> values = new ArrayList<>();
        if (fields != null) {
            for (String raw : fields) {
                String value = fieldValue(raw);
                if (empty(value)) {
                    continue;
                }
                String key = canon(value);
                boolean keep = true;
                for (int i = values.size() - 1; i >= 0; i--) {
                    String oldKey = canon(values.get(i));
                    if (oldKey.equals(key) || oldKey.contains(key)) {
                        keep = false;
                        break;
                    }
                    if (key.contains(oldKey)) {
                        values.remove(i);
                    }
                }
                if (keep) {
                    values.add(value);
                }
            }
        }
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(value);
        }
        return clean(out.toString());
    }

    private static String fieldValue(String raw) {
        String x = clean(raw);
        int split = x.indexOf('=');
        return split >= 0 ? clean(x.substring(split + 1)) : x;
    }

    private static int visibleCodePrefix(Models.Identification id, String wanted) {
        int best = commonPrefixIn(id.identifierLabels, wanted);
        best = Math.max(best, commonPrefixIn(id.visibleLabels, wanted));
        best = Math.max(best, commonPrefixIn(id.photoIdentityFields, wanted));
        return best;
    }

    private static int commonPrefixIn(List<String> values, String wanted) {
        int best = 0;
        if (values == null) {
            return 0;
        }
        for (String raw : values) {
            String actual = canon(fieldValue(raw));
            int common = 0;
            while (common < actual.length() && common < wanted.length()
                    && actual.charAt(common) == wanted.charAt(common)) {
                common++;
            }
            best = Math.max(best, common);
        }
        return best;
    }

    private static boolean containsInObservation(Models.Identification id, String value) {
        String wanted = canon(value);
        if (wanted.length() < 4) {
            return false;
        }
        if (containsCanonical(id.visibleLabels, value)
                || containsCanonical(id.photoIdentityFields, value)) {
            return true;
        }
        return id.localScan != null && canon(id.localScan.joinedText()).contains(wanted);
    }

    private static boolean containsCanonical(List<String> values, String value) {
        String wanted = canon(value);
        if (values == null || wanted.isEmpty()) {
            return false;
        }
        for (String raw : values) {
            String actual = canon(fieldValue(raw));
            if (!actual.isEmpty() && (actual.contains(wanted) || wanted.contains(actual))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsOneConfusableVariant(String actual, String wanted) {
        if (actual.length() < wanted.length()) {
            return false;
        }
        for (int start = 0; start <= actual.length() - wanted.length(); start++) {
            int mismatches = 0;
            for (int i = 0; i < wanted.length(); i++) {
                char a = actual.charAt(start + i);
                char b = wanted.charAt(i);
                if (a == b) {
                    continue;
                }
                if (!confusable(a, b) || ++mismatches > 1) {
                    mismatches = 99;
                    break;
                }
            }
            if (mismatches == 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean confusable(char a, char b) {
        return (a == '8' && b == 'B') || (a == 'B' && b == '8')
                || (a == '0' && b == 'O') || (a == 'O' && b == '0')
                || (a == '1' && (b == 'I' || b == 'L'))
                || (b == '1' && (a == 'I' || a == 'L'));
    }

    private static String join(String... values) {
        StringBuilder b = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                String x = clean(value);
                if (x.isEmpty()) {
                    continue;
                }
                if (b.length() > 0) {
                    b.append(' ');
                }
                b.append(x);
            }
        }
        return b.toString();
    }

    private static boolean localCorroboratesCode(Models.Identification id,
                                                  Models.Identifier primary, String code) {
        String wanted = canon(code);
        if (wanted.length() < 5) {
            return false;
        }
        if (primary != null && canon(primary.value).equals(wanted)) {
            return true;
        }
        boolean visionBound = false;
        for (String x : id.identifierLabels) {
            if (canon(x).equals(wanted)) {
                visionBound = true;
                break;
            }
        }
        String local = id.localScan == null ? "" : canon(id.localScan.joinedText());
        return visionBound && local.contains(wanted);
    }

    private static boolean hasComplementaryViews(List<String> views) {
        if (views == null || views.isEmpty()) {
            return false;
        }
        StringBuilder b = new StringBuilder();
        for (String x : views) {
            b.append(' ').append(clean(x).toLowerCase(Locale.ROOT));
        }
        String all = b.toString();
        return views.size() >= 2 || (all.contains("front") && all.contains("back"))
                || (all.contains("fronte") && all.contains("retro"));
    }

    private static boolean completeCode(String value) {
        String x = clean(value);
        return x.length() >= 5 && !truncated(x)
                && x.matches(".*[A-Za-z].*") && x.matches(".*[0-9].*");
    }

    private static boolean containsCanonical(String container, String value) {
        String a = canon(container);
        String b = canon(value);
        return !a.isEmpty() && !b.isEmpty() && (a.contains(b) || b.contains(a));
    }

    private static boolean truncated(String value) {
        String x = clean(value);
        return x.contains("...") || x.contains("…") || x.endsWith("-") || x.endsWith("/");
    }

    private static String fact(Models.CandidateScore c, String key) {
        if (c == null) {
            return "";
        }
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        for (String raw : c.candidateFacts) {
            String x = clean(raw);
            if (x.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return x.substring(x.indexOf('=') + 1).trim();
            }
        }
        return "";
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

    private static void addOnce(List<String> values, String value) {
        if (values == null || empty(value)) {
            return;
        }
        for (String old : values) {
            if (value.equalsIgnoreCase(old)) {
                return;
            }
        }
        values.add(value);
    }

    private static String canon(String s) {
        return clean(s).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static boolean empty(String s) {
        return clean(s).isEmpty();
    }

    private static int clamp(int x) {
        return Math.max(0, Math.min(100, x));
    }
}
