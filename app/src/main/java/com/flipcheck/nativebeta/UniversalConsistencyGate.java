package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

final class UniversalConsistencyGate {
    private UniversalConsistencyGate() {
    }

    static void calibrateCandidate(Models.CandidateScore c) {
        if (c == null) {
            return;
        }
        String geometry = fact(c, "geometry_relation");
        if (factTrue(c, "major_geometry_mismatch") || "conflict".equalsIgnoreCase(geometry)) {
            c.totalScore = Math.min(c.totalScore, 20);
            addOnce(c.candidateFacts, "consistency_geometry_conflict=true");
            return;
        }
        boolean completePrintedIdentity = PhotoIdentityPolicy.skipGeometryFloor(c);
        if (!completePrintedIdentity && c.identifierScore <= 0 && c.layoutScore < 55) {
            c.totalScore = Math.min(c.totalScore, 49);
            addOnce(c.candidateFacts, "consistency_geometry_weak=true");
        } else if (!completePrintedIdentity && c.identifierScore <= 0
                && c.layoutScore < 64 && c.webScore > c.layoutScore) {
            c.totalScore = Math.min(c.totalScore, 59);
            addOnce(c.candidateFacts, "consistency_web_cannot_rescue_geometry=true");
        }
        if (factFalse(c, "same_entity_role") || factTrue(c, "relationship_only")) {
            int conf = c.totalScore;
            c.totalScore = Math.min(conf, 20);
            addOnce(c.candidateFacts, "consistency_entity_role_conflict=true");
            return;
        }
        if (factTrue(c, "domain_locked") && factFalse(c, "brand_entity_validated")) {
            c.totalScore = Math.min(c.totalScore, 55);
            addOnce(c.candidateFacts, "consistency_unvalidated_domain_lock=true");
        }
        int conf2 = factInt(c, "contradiction_evidence_confidence");
        boolean hard = factTrue(c, "contradiction_hard_evidence") || conf2 >= 85;
        int strong = 0;
        int weak = 0;
        for (String raw : c.contradictions) {
            String x = safe(raw).toUpperCase(Locale.ROOT);
            if (x.startsWith("STRONG:") || x.startsWith("FORTE:")) {
                if (hard) {
                    strong++;
                } else {
                    weak++;
                }
            } else if (!x.isEmpty()) {
                weak++;
            }
        }
        if (strong <= 0) {
            if (weak > 0) {
                c.totalScore = Math.max(0, c.totalScore - Math.min(18, weak * 6));
                addOnce(c.candidateFacts, "consistency_weak_conflicts=" + weak);
                return;
            }
            return;
        }
        c.totalScore = Math.min(c.totalScore, 25);
        addOnce(c.candidateFacts, "consistency_strong_conflict=true");
    }

    static boolean strongCandidateConflict(Models.CandidateScore c) {
        if (c == null) {
            return false;
        }
        if (factTrue(c, "consistency_entity_role_conflict")
                || factTrue(c, "consistency_geometry_conflict")
                || factTrue(c, "major_geometry_mismatch")
                || "conflict".equalsIgnoreCase(fact(c, "geometry_relation"))
                || factTrue(c, "consistency_strong_conflict")
                || factFalse(c, "same_entity_role")
                || factTrue(c, "relationship_only")) {
            return true;
        }
        int conf = factInt(c, "contradiction_evidence_confidence");
        if (conf < 85 && !factTrue(c, "contradiction_hard_evidence")) {
            return false;
        }
        for (String raw : c.contradictions) {
            String x = safe(raw).toUpperCase(Locale.ROOT);
            if (x.startsWith("STRONG:") || x.startsWith("FORTE:")) {
                return true;
            }
        }
        return false;
    }

    static boolean strongVerificationConflict(JSONObject r) {
        if (r == null) {
            return false;
        }
        if (r.optBoolean("relationship_only", false) || (r.has("same_entity_role") && !r.optBoolean("same_entity_role", true))) {
            return true;
        }
        String level = safe(r.optString("conflict_level", "")).toLowerCase(Locale.ROOT);
        return (level.equals("strong") || level.equals("forte")) && EvidenceReliabilityGate.verificationConflictConfidence(r) >= 85;
    }

    static int verificationWeakConflictCount(JSONObject r) {
        if (r == null) {
            return 0;
        }
        int n = 0;
        String level = safe(r.optString("conflict_level", "")).toLowerCase(Locale.ROOT);
        if ((level.equals("strong") || level.equals("forte")) && EvidenceReliabilityGate.verificationConflictConfidence(r) < 85) {
            n = 0 + 1;
        }
        if (level.equals("weak") || level.equals("debole")) {
            n++;
        }
        JSONArray xs = r.optJSONArray("contradictions");
        if (xs != null) {
            for (int i = 0; i < xs.length(); i++) {
                if (!safe(xs.optString(i, "")).isEmpty()) {
                    n++;
                }
            }
        }
        return n;
    }

    static void copyVerificationConflicts(JSONObject r, List<String> dst) {
        JSONArray a;
        if (r == null || dst == null || (a = r.optJSONArray("attribute_conflicts")) == null) {
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            Object v = a.opt(i);
            if (v != null) {
                String x = String.valueOf(v);
                if (!x.trim().isEmpty() && !dst.contains(x)) {
                    dst.add(x);
                }
            }
        }
    }

    static int retrievalVisualConfidence(Models.CandidateScore c) throws NumberFormatException {
        if (c == null) {
            return 0;
        }
        boolean match = false;
        boolean directReference = false;
        int conf = 0;
        int directConfidence = 0;
        for (String raw : c.candidateFacts) {
            String s = safe(raw);
            if (s.equalsIgnoreCase("retrieval_image_match=true")) {
                match = true;
            }
            if (s.equalsIgnoreCase("visual_reference_checked=true")) {
                directReference = true;
            }
            if (s.toLowerCase(Locale.ROOT).startsWith("retrieval_visual_confidence=")) {
                try {
                    conf = Math.max(conf, Integer.parseInt(s.substring(s.indexOf(61) + 1).trim()));
                } catch (Exception e) {
                }
            }
            if (s.toLowerCase(Locale.ROOT).startsWith("visual_match_confidence=")) {
                try {
                    directConfidence = Math.max(directConfidence,
                            Integer.parseInt(s.substring(s.indexOf(61) + 1).trim()));
                } catch (Exception e) {
                }
            }
        }
        if (directReference) {
            conf = Math.max(conf, directConfidence);
        }
        if (match || directReference) {
            return clamp(conf);
        }
        return 0;
    }

    static boolean independentVisualIdentityEvidence(Models.CandidateScore c, JSONObject r) {
        int direct = r == null ? 0 : clamp(r.optInt("visual_match_confidence", 0));
        return (r != null && r.optBoolean("visual_reference_checked", false) && direct >= 85) || retrievalVisualConfidence(c) >= 90;
    }

    static boolean exactSourceSupport(JSONObject r) {
        return (r == null || r.optBoolean("relationship_only", false) || (r.has("same_entity_role") && !r.optBoolean("same_entity_role", true)) || !r.optBoolean("exact_identity_supported", false) || clamp(r.optInt("source_identity_confidence", 0)) < 80) ? false : true;
    }

    static int capVerifiedConfidence(int proposed, Models.CandidateScore c, JSONObject r, boolean exactLocalIdentifier, boolean disproofPassed) {
        int i;
        int x = clamp(proposed);
        if (strongVerificationConflict(r)) {
            i = 35;
        } else {
            if (!disproofPassed) {
                x = Math.min(x, 78);
            }
            if (!exactLocalIdentifier) {
                boolean exact = exactSourceSupport(r);
                boolean visual = independentVisualIdentityEvidence(c, r);
                return Math.min(x, (exact && visual) ? 95 : exact ? 88 : visual ? 86 : 82);
            }
            i = 97;
        }
        return Math.min(x, i);
    }

    static boolean exactIdentityReady(Models.CandidateScore c, JSONObject r, boolean exactLocalIdentifier) {
        if (strongVerificationConflict(r)) {
            return false;
        }
        if (exactLocalIdentifier) {
            return true;
        }
        return exactSourceSupport(r) && independentVisualIdentityEvidence(c, r);
    }

    static boolean discoveryBudgetAvailable(Models.Usage u) {
        return u == null || u.webCalls < 1;
    }

    static boolean verificationBudgetAvailable(Models.Usage u) {
        return u == null || u.webCalls < 1;
    }

    static boolean webBudgetAvailable(Models.Usage u) {
        return discoveryBudgetAvailable(u);
    }

    static boolean visionBudgetAvailable(Models.Usage u) {
        return u == null || u.visionCalls < 1;
    }

    private static boolean factTrue(Models.CandidateScore c, String k) {
        return "true".equalsIgnoreCase(fact(c, k));
    }

    private static boolean factFalse(Models.CandidateScore c, String k) {
        return "false".equalsIgnoreCase(fact(c, k));
    }

    private static int factInt(Models.CandidateScore c, String k) {
        try {
            return Integer.parseInt(fact(c, k));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String fact(Models.CandidateScore c, String k) {
        if (c == null) {
            return "";
        }
        String p = k.toLowerCase(Locale.ROOT) + "=";
        for (String r : c.candidateFacts) {
            String s = safe(r);
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) {
                return s.substring(s.indexOf(61) + 1).trim();
            }
        }
        return "";
    }

    private static void addOnce(List<String> xs, String v) {
        if (xs == null || xs.contains(v)) {
            return;
        }
        xs.add(v);
    }

    private static int clamp(int x) {
        return Math.max(0, Math.min(100, x));
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
