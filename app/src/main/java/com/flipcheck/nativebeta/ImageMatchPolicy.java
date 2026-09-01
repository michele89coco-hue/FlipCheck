package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import java.util.Locale;

final class ImageMatchPolicy {

    enum Action {
        NONE,
        CONFIRM,
        SHOW_OPTIONS,
        SECOND_PHOTO
    }

    private ImageMatchPolicy() {
    }

    static final class Decision {
        Models.CandidateScore candidate;
        int confidence;
        boolean hasImageEvidence;
        int margin;
        Action action = Action.NONE;
        String reason = "";

        Decision() {
        }

        String displayName() {
            return this.candidate == null ? "" : this.candidate.displayName();
        }
    }

    static Decision evaluate(Models.Identification id) {
        Decision d = new Decision();
        if (id == null || id.candidates.isEmpty()) {
            return d;
        }
        Models.CandidateScore best = null;
        Models.CandidateScore second = null;
        int bestScore = -1;
        int secondScore = -1;
        for (Models.CandidateScore c : id.candidates) {
            if (c != null && !c.hardRejected && isImageRetrieval(c)) {
                d.hasImageEvidence = true;
                int s = calibratedConfidence(c);
                if (s > bestScore) {
                    second = best;
                    secondScore = bestScore;
                    best = c;
                    bestScore = s;
                } else if (s > secondScore) {
                    second = c;
                    secondScore = s;
                }
            }
        }
        if (best == null) {
            if (d.hasImageEvidence) {
                d.action = Action.SECOND_PHOTO;
            }
            return d;
        }
        d.candidate = best;
        d.confidence = Math.max(0, bestScore);
        d.margin = second == null ? bestScore : Math.max(0, bestScore - secondScore);
        int cap = evidenceCap(best);
        if (publicCandidateAllowed(best) && d.confidence >= 82 && d.margin >= 8 && cap >= 80) {
            d.action = Action.CONFIRM;
            d.reason = "Il match immagine sostiene un candidato forte; la conferma utente lo rende probabile, non ancora verificato come variante esatta.";
        } else if (publicCandidateAllowed(best) && d.confidence >= 68) {
            d.action = Action.SHOW_OPTIONS;
            d.reason = "Esistono candidati fotografici plausibili, ma il margine o la prova di identità non bastano per una conferma singola.";
        } else {
            d.action = Action.SECOND_PHOTO;
            d.reason = "Google ha trovato immagini simili, ma il collegamento tra immagine e nome/modello è ancora troppo debole: serve un'altra vista.";
        }
        return d;
    }

    static boolean isImageRetrieval(Models.CandidateScore c) {
        if (c == null) {
            return false;
        }
        if (containsIgnoreCase(c.evidence, "Google Reverse Image") || containsIgnoreCase(c.evidence, "Google Web Detection") || containsIgnoreCase(c.evidence, "Visual Retrieval") || containsIgnoreCase(c.evidence, "Image Retrieval Core")) {
            return true;
        }
        return hasFact(c, "gemini_image_search=true") || hasFact(c, "google_reverse_image=true") || hasFact(c, "google_web_detection=true") || hasFact(c, "retrieval_image_match=true");
    }

    static int evidenceCap(Models.CandidateScore c) {
        if (c == null || !isImageRetrieval(c) || hasFact(c, "fusion_rejected=true")) {
            return 0;
        }
        if (hasFact(c, "fusion_validator_error=true") || hasFact(c, "fusion_validator_unavailable=true")) {
            return 64;
        }
        if (hasFact(c, "fusion_independent_vision=true")) {
            int independent = intFact(c, "fusion_vision_confidence", 0);
            if (!boolFact(c, "gemini_same_product") || stringFact(c, "gemini_source_url").isEmpty() || independent < 84) {
                return independent >= 78 ? 82 : 72;
            }
            return 94;
        }
        if (hasFact(c, "gemini_image_search=true")) {
            boolean same = boolFact(c, "gemini_same_product");
            boolean sourced = !stringFact(c, "gemini_source_url").isEmpty();
            if (same && sourced) {
                return 92;
            }
            return same ? 84 : 72;
        }
        if (hasFact(c, "retrieval_image_match=true")) {
            return 92;
        }
        if (boolFact(c, "google_full_match")) {
            return 94;
        }
        if (boolFact(c, "google_pages_2plus")) {
            return 88;
        }
        int pages = intFact(c, "google_pages", 0);
        if (pages >= 1) {
            return 80;
        }
        String kind = stringFact(c, "google_image_kind").toLowerCase(Locale.ROOT);
        if (kind.contains("partial")) {
            return 76;
        }
        if (kind.contains("full")) {
            return 92;
        }
        return kind.contains("similar") ? 58 : 64;
    }

    static int calibratedConfidence(Models.CandidateScore c) {
        if (c == null || !isImageRetrieval(c)) {
            return 0;
        }
        int cap = evidenceCap(c);
        int raw = clamp(c.totalScore);
        if (!hasFact(c, "gemini_image_search=true")) {
            int visual = intFact(c, "google_visual_rerank", 0);
            int score = raw;
            if (visual > 0) {
                score = Math.max(raw, Math.round((raw * 0.4f) + (clamp(visual) * 0.6f)));
            }
            return Math.min(cap, clamp(score));
        }
        if (hasFact(c, "fusion_rejected=true")) {
            return 0;
        }
        int identity = intFact(c, "gemini_identity_confidence", raw);
        int score2 = Math.min(clamp(identity), clamp(intFact(c, "gemini_visual_match", identity)));
        if (hasFact(c, "fusion_independent_vision=true")) {
            score2 = Math.min(score2, clamp(intFact(c, "fusion_vision_confidence", score2)));
        }
        return Math.min(cap, score2);
    }

    static int capScore(Models.CandidateScore c, int proposed) {
        return Math.min(evidenceCap(c), clamp(proposed));
    }

    static boolean publicCandidateAllowed(Models.CandidateScore c) {
        if (c == null || !isImageRetrieval(c)) {
            return false;
        }
        return hasFact(c, "gemini_image_search=true") ? (hasFact(c, "fusion_rejected=true") || hasFact(c, "fusion_validator_error=true") || hasFact(c, "fusion_validator_unavailable=true") || !hasFact(c, "fusion_independent_vision=true") || !boolFact(c, "gemini_same_product") || evidenceCap(c) < 80 || calibratedConfidence(c) < 72 || c.model == null || c.model.trim().isEmpty()) ? false : true : evidenceCap(c) >= 72 && calibratedConfidence(c) >= 68 && c.model != null && !c.model.trim().isEmpty();
    }

    static int baseScoreForGoogleEvidence(int pages, boolean full, String imageKind, int rawEvidence) {
        int base;
        String kind = imageKind == null ? "" : imageKind.toLowerCase(Locale.ROOT);
        if (full || kind.contains("full")) {
            base = 88;
        } else if (pages >= 2) {
            base = 82;
        } else if (pages == 1) {
            base = 74;
        } else if (kind.contains("partial")) {
            base = 68;
        } else {
            base = kind.contains("similar") ? 48 : 56;
        }
        return clamp(base + Math.min(6, Math.max(0, rawEvidence) / 45));
    }

    private static boolean boolFact(Models.CandidateScore c, String key) {
        String v = stringFact(c, key);
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }

    private static int intFact(Models.CandidateScore c, String key, int fallback) {
        String v = stringFact(c, key);
        try {
            return Integer.parseInt(v.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String stringFact(Models.CandidateScore c, String key) {
        int p;
        if (c == null || key == null) {
            return "";
        }
        String wanted = key.trim().toLowerCase(Locale.ROOT);
        for (String raw : c.candidateFacts) {
            if (raw != null && (p = raw.indexOf(61)) > 0) {
                String k = raw.substring(0, p).trim().toLowerCase(Locale.ROOT);
                if (wanted.equals(k)) {
                    return raw.substring(p + 1).trim();
                }
            }
        }
        return "";
    }

    private static boolean hasFact(Models.CandidateScore c, String exact) {
        if (c == null || exact == null) {
            return false;
        }
        for (String f : c.candidateFacts) {
            if (f != null && f.equalsIgnoreCase(exact)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String a, String b) {
        return (a == null || b == null || !a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT))) ? false : true;
    }

    private static int clamp(int n) {
        return Math.max(0, Math.min(100, n));
    }
}
