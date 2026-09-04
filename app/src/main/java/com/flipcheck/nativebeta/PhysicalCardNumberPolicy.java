package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves high-risk card identifiers without allowing a single hypothesis to
 * confirm itself. A localized direct read may populate a candidate field; the
 * verified flag requires an independent OCR/photo/catalog signal.
 */
final class PhysicalCardNumberPolicy {
    private PhysicalCardNumberPolicy() {}

    static void normalize(Models.Identification id) {
        if (id == null) return;
        NormalizedPhotoIdentity n = PhotographicFactNormalizer.require(id);
        reset(id, n);
        Map<String, Aggregate> grouped = new LinkedHashMap<>();
        collect(grouped, n, id, CanonicalFieldKey.CARD_NUMBER_CANDIDATE, false);
        collect(grouped, n, id, CanonicalFieldKey.COLLECTOR_NUMBER_CANDIDATE, true);
        for (NormalizedPhotoIdentity.Fact source : n.sourceEvidence) {
            if (source.key != CanonicalFieldKey.SOURCE_CONFIRMED_CATALOG_NUMBER) continue;
            String value = normalizedIdentifier(source.value);
            if (value.isEmpty()) continue;
            Aggregate a = grouped.computeIfAbsent(canon(value), k -> new Aggregate(value));
            a.catalogConfidence = Math.max(a.catalogConfidence, source.confidence);
        }

        List<Aggregate> viable = new ArrayList<>();
        for (Aggregate a : grouped.values()) if (a.bestDirect != null) viable.add(a);
        Aggregate winner = null;
        for (Aggregate a : viable) if (winner == null || a.score() > winner.score()) winner = a;
        if (winner == null) {
            id.numberHypotheses = hypotheses(grouped);
            PhotographicFactNormalizer.syncDebug(id, n);
            return;
        }

        boolean verified = winner.verified();
        boolean competing = false;
        int runnerUp = Integer.MIN_VALUE;
        List<String> alternatives = new ArrayList<>();
        for (Aggregate a : grouped.values()) {
            if (a == winner || (a.bestDirect == null && a.catalogConfidence == 0)) continue;
            if (!canon(a.value).equals(canon(winner.value))) {
                competing = true;
                runnerUp = Math.max(runnerUp, a.score());
                alternatives.add(a.value + "(" + a.supportSummary() + ", relation=" + confusionRelation(winner.value,a.value) + ")");
            }
        }
        boolean catalogOpponent = false;
        for (Aggregate a : grouped.values()) if (a != winner && a.catalogConfidence > 0) catalogOpponent = true;
        boolean conflictResolved = verified && (!competing ||
                ((winner.catalogConfidence > 0 || winner.directGroups.size() >= 2)
                        && winner.score() - runnerUp >= 25
                        && (!catalogOpponent || winner.directGroups.size() >= 2 || winner.catalogConfidence > 0)));

        winner.collector = winner.collector || looksCollector(winner.value);
        if (winner.collector) {
            n.collectorNumberCandidate = winner.value;
            n.physicalCollectorNumber = winner.value;
            n.collectorNumberVerified = verified && (!competing || conflictResolved);
            id.collectorNumberCandidate = winner.value;
            id.physicalCollectorNumber = winner.value;
            id.physicalCardNumber = winner.value;
            id.collectorNumberVerified = n.collectorNumberVerified;
            id.collectorNumberVerificationStatus = status(verified, competing, conflictResolved);
        } else {
            n.cardNumberCandidate = winner.value;
            n.physicalCardNumber = winner.value;
            n.cardNumberVerified = verified && (!competing || conflictResolved);
            id.cardNumberCandidate = winner.value;
            id.physicalCardNumber = winner.value;
            id.cardNumberVerified = n.cardNumberVerified;
            id.cardNumberVerificationStatus = status(verified, competing, conflictResolved);
        }
        id.physicalCardNumberOrigin = "photo:" + (verified ? "verified" : "candidate") + ":"
                + winner.bestDirect.location + ":" + (winner.collector ? "collector_number" : "card_number");
        n.identifierStatus = status(verified, competing, conflictResolved);
        n.identifierAlternatives.clear();
        n.identifierAlternatives.addAll(alternatives);
        if (competing && !conflictResolved) {
            id.numberConflicts = "selectedCandidate=" + winner.value + "; alternatives=" + alternatives;
            addOnce(n.semanticConflicts, "identifierConflict=" + id.numberConflicts);
        } else {
            id.numberConflicts = "";
        }
        id.numberHypotheses = hypotheses(grouped);
        PhotographicFactNormalizer.syncDebug(id, n);
    }

    static boolean verifiedNumber(Models.Identification id) {
        return id != null && (id.cardNumberVerified || id.collectorNumberVerified);
    }

    static String verifiedValue(Models.Identification id) {
        if (id == null) return "";
        if (id.collectorNumberVerified) return clean(id.physicalCollectorNumber);
        return id.cardNumberVerified ? clean(id.physicalCardNumber) : "";
    }

    private static void collect(Map<String, Aggregate> grouped, NormalizedPhotoIdentity n,
                                Models.Identification id, CanonicalFieldKey key, boolean collectorAlias) {
        for (NormalizedPhotoIdentity.Fact f : n.facts(key)) {
            Validation validation = validate(f, collectorAlias);
            if (!validation.accepted) {
                reject(n, id, f.originalKey + "=" + f.value + ":" + validation.reason);
                continue;
            }
            String value = normalizedIdentifier(f.value);
            Aggregate a = grouped.computeIfAbsent(canon(value), k -> new Aggregate(value));
            a.collector |= validation.collector;
            if (f.quality == NormalizedPhotoIdentity.Quality.DIRECT_PHOTO_OBSERVATION) {
                if (a.bestDirect == null || f.confidence > a.bestDirect.confidence) a.bestDirect = f;
                a.directGroups.add(independenceGroup(f));
                a.directConfidence = Math.max(a.directConfidence, f.confidence);
            } else if (f.quality == NormalizedPhotoIdentity.Quality.LOCAL_OCR_HINT) {
                a.localOcrConfidence = Math.max(a.localOcrConfidence, f.confidence);
            }
        }
    }

    private static Validation validate(NormalizedPhotoIdentity.Fact f, boolean collectorAlias) {
        String role = canonWords(f.semanticRole);
        boolean collector = collectorAlias || role.contains("COLLECTOR");
        boolean semantic = collector || role.equals("CARD NUMBER") || role.equals("CHECKLIST NUMBER")
                || role.equals("CARD IDENTIFIER") || role.contains("NUMBER CANDIDATE");
        boolean located = !clean(f.location).isEmpty();
        boolean acceptedQuality = f.quality == NormalizedPhotoIdentity.Quality.DIRECT_PHOTO_OBSERVATION
                ? f.confidence >= 75 : f.quality == NormalizedPhotoIdentity.Quality.LOCAL_OCR_HINT && f.confidence >= 60;
        String reason = !acceptedQuality ? "quality_or_confidence_insufficient"
                : !located ? "missing_physical_location"
                : !semantic ? "semantic_role_not_card_or_collector_number"
                : !plausible(f.value, collector) ? "number_format_not_plausible"
                : nonCardContext(f) ? "stat_hp_rating_year_ui_or_external_context" : "";
        return new Validation(reason.isEmpty(), collector, reason);
    }

    private static boolean plausible(String value, boolean collector) {
        String v = normalizedIdentifier(value);
        if (v.isEmpty() || v.matches("(?:19|20)[0-9]{2}")||v.matches("(?:19|20)[0-9]{2}[/.-](?:[0-9]{2}|(?:19|20)[0-9]{2})")) return false;
        if (v.matches("[0-9]{1,6}/[0-9]{1,6}")) return collector;
        return v.matches("(?i)[A-Z0-9]{1,8}(?:[-/][A-Z0-9]{1,8}){0,2}");
    }

    private static boolean nonCardContext(NormalizedPhotoIdentity.Fact f) {
        String x = (f.originalKey + " " + f.semanticRole + " " + f.location).toLowerCase(Locale.ROOT).replace('-', '_');
        return x.matches(".*(?:hp|pv|rating|stat|power|attack|defense|offense|activation|jersey|ui|watermark|overlay|status_bar|listing|screen|copyright|year).*" );
    }

    private static String status(boolean verified, boolean competing, boolean resolved) {
        if (competing && !resolved) return "CONFLICT";
        if (verified && competing) return "VERIFIED_AFTER_CONFLICT";
        return verified ? "VERIFIED" : "CANDIDATE_UNVERIFIED";
    }

    private static String independenceGroup(NormalizedPhotoIdentity.Fact f) {
        String method = clean(f.evidenceType).toLowerCase(Locale.ROOT);
        if (method.startsWith("focused_")) method = "focused_verification";
        else if (method.contains("ocr")) method = "photo_ocr";
        else method = "primary_vision";
        return method + "|image=" + f.imageIndex;
    }

    private static String hypotheses(Map<String, Aggregate> grouped) {
        StringBuilder out = new StringBuilder();
        for (Aggregate a : grouped.values()) {
            if (out.length() > 0) out.append(" | ");
            out.append("value=").append(a.value).append(", ").append(a.supportSummary())
                    .append(", verified=").append(a.verified());
        }
        return out.toString();
    }

    private static void reset(Models.Identification id, NormalizedPhotoIdentity n) {
        n.cardNumberCandidate = ""; n.collectorNumberCandidate = "";
        n.physicalCardNumber = ""; n.physicalCollectorNumber = "";
        n.cardNumberVerified = false; n.collectorNumberVerified = false;
        n.identifierStatus = "NOT_OBSERVED"; n.identifierAlternatives.clear();
        id.physicalCardNumber = ""; id.physicalCollectorNumber = "";
        id.cardNumberCandidate = ""; id.collectorNumberCandidate = "";
        id.cardNumberVerified = false; id.collectorNumberVerified = false;
        id.cardNumberVerificationStatus = "NOT_OBSERVED";
        id.collectorNumberVerificationStatus = "NOT_OBSERVED";
        id.physicalCardNumberOrigin = "";
    }

    private static void reject(NormalizedPhotoIdentity n, Models.Identification id, String reason) {
        addOnce(n.rejectedFacts, reason);
        addOnce(id.observedEvidence, "rejected_physical_number=" + reason);
    }

    private static boolean looksCollector(String value) {
        String v = normalizedIdentifier(value);
        return v.contains("/") && v.matches("(?i).*[A-Z].*");
    }

    private static String confusionRelation(String a,String b){String x=normalizedIdentifier(a),y=normalizedIdentifier(b);
        if(new StringBuilder(x).reverse().toString().equals(y))return "orientation_reversal";
        if(x.length()==y.length()){int diff=0;boolean glyph=true;for(int i=0;i<x.length();i++)if(x.charAt(i)!=y.charAt(i)){
            diff++;String pair=""+x.charAt(i)+y.charAt(i);if(!(pair.equals("69")||pair.equals("96")||pair.equals("17")||pair.equals("71")
                    ||pair.equals("0O")||pair.equals("O0")||pair.equals("5S")||pair.equals("S5")))glyph=false;}
            if(diff>0&&glyph)return "glyph_confusion";}
        return "different_identifier";}

    private static String normalizedIdentifier(String value) {
        return clean(value).replaceFirst("^#", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static String canon(String x) { return normalizedIdentifier(x).replaceAll("[^A-Z0-9]", ""); }
    private static String canonWords(String x) { return clean(x).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim(); }
    private static void addOnce(List<String> out, String value) { if (!out.contains(value)) out.add(value); }
    private static String clean(String x) { return x == null ? "" : x.trim().replaceAll("\\s+", " "); }

    private static final class Validation {
        final boolean accepted, collector; final String reason;
        Validation(boolean accepted, boolean collector, String reason) { this.accepted=accepted; this.collector=collector; this.reason=reason; }
    }

    private static final class Aggregate {
        final String value; boolean collector; NormalizedPhotoIdentity.Fact bestDirect;
        int directConfidence, localOcrConfidence, catalogConfidence;
        final Set<String> directGroups = new LinkedHashSet<>();
        Aggregate(String value) { this.value=value; }
        boolean verified() { return bestDirect != null && (directGroups.size() >= 2 || localOcrConfidence >= 60 || catalogConfidence >= 70); }
        int score() { return directConfidence + (directGroups.size() >= 2 ? 35 : 0)
                + (localOcrConfidence > 0 ? 20 : 0) + (catalogConfidence > 0 ? 30 : 0); }
        String supportSummary() { return "direct=" + directGroups.size() + ", localOcr=" + localOcrConfidence + ", catalog=" + catalogConfidence + ", score=" + score(); }
    }
}
