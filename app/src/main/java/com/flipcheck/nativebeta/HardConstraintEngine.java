package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class HardConstraintEngine {
    private HardConstraintEngine() {
    }

    static void apply(Models.Identification o, Models.Identifier primary) {
        if (o == null) {
            return;
        }
        o.hardConstraints.clear();
        if (BrandAnchorPolicy.isLocked(o)) {
            o.hardConstraints.add("brand=" + BrandAnchorPolicy.anchor(o));
        }
        if (primary != null && notEmpty(primary.value)) {
            int reliability = EvidenceReliabilityGate.identifierConfidence(o.localScan, primary);
            addOnce(o.observedEvidence, "identifier_candidate=" + primary.value + " reliability=" + reliability);
            if (EvidenceReliabilityGate.isHardIdentifier(o.localScan, primary)) {
                String key = identifierKey(primary.label);
                if (!key.isEmpty()) {
                    o.hardConstraints.add(key + '=' + primary.value.trim());
                }
            }
        }
        if (o.candidates.isEmpty() || o.hardConstraints.isEmpty()) {
            return;
        }
        List<Models.CandidateScore> keep = new ArrayList<>();
        for (Models.CandidateScore c : o.candidates) {
            if (BrandAnchorPolicy.isLocked(o) && !BrandAnchorPolicy.candidateCompatible(o, c)) {
                c.hardMatches.clear();
                c.hardViolations.clear();
                c.hardViolations.add("brand=" + BrandAnchorPolicy.anchor(o) + " richiesto dal marchio visibile; candidato di marca diversa o non dichiarata");
                c.hardRejected = true;
                c.totalScore = 0;
                o.rejectedCandidates.add(c);
            } else if (BrandEntityPolicy.isResolved(o) && !BrandEntityPolicy.candidateCompatible(o, c)) {
                c.hardMatches.clear();
                c.hardViolations.clear();
                c.hardViolations.add("brand_entity=" + o.brandEntity + " / " + o.brandOfficialDomain + " richiesto; candidato non dimostrato come prodotto di questa entita' marca");
                c.hardRejected = true;
                c.totalScore = 0;
                o.rejectedCandidates.add(c);
            } else {
                evaluate(c, o.hardConstraints);
                if (c.hardRejected) {
                    o.rejectedCandidates.add(c);
                } else {
                    keep.add(c);
                }
            }
        }
        o.candidates.clear();
        o.candidates.addAll(keep);
    }

    private static void evaluate(Models.CandidateScore c, List<String> constraints) {
        c.hardMatches.clear();
        c.hardViolations.clear();
        c.hardMatchWeight = 0;
        c.hardRejected = false;
        Map<String, String> facts = new LinkedHashMap<>();
        Iterator<String> it = c.candidateFacts.iterator();
        while (it.hasNext()) {
            String raw = it.next();
            int p = raw == null ? -1 : raw.indexOf(61);
            if (p > 0 && p < raw.length() - 1) {
                facts.put(norm(raw.substring(0, p)), raw.substring(p + 1).trim());
            }
        }
        if (notEmpty(c.model)) {
            facts.putIfAbsent("model_code", c.model);
            facts.putIfAbsent("part_number", c.model);
            facts.putIfAbsent("sku", c.model);
        }
        for (String raw2 : constraints) {
            int p2 = raw2.indexOf(61);
            if (p2 > 0) {
                String key = norm(raw2.substring(0, p2));
                String observed = raw2.substring(p2 + 1).trim();
                String declared = facts.get(key);
                if (notEmpty(declared)) {
                    String strCanon = canon(observed);
                    String strCanon2 = canon(declared);
                    boolean match = strCanon.equals(strCanon2) || (strCanon.length() >= 6 && strCanon2.contains(strCanon)) || (strCanon2.length() >= 6 && strCanon.contains(strCanon2));
                    if (match) {
                        c.hardMatches.add(key + '=' + observed);
                        c.hardMatchWeight += 60;
                    } else {
                        c.hardViolations.add(key + '=' + observed + " != " + declared);
                        c.hardRejected = true;
                        c.totalScore = 0;
                        return;
                    }
                } else {
                    continue;
                }
            }
        }
        if (c.hardMatchWeight >= 60) {
            c.totalScore = Math.max(c.totalScore, 88);
        }
    }

    private static String identifierKey(String label) {
        String l = norm(label);
        if (l.startsWith("model") || l.equals("type") || l.equals("item") || l.equals("ref") || l.equals("reference")) {
            return "model_code";
        }
        if (l.equals("sku")) {
            return "sku";
        }
        if (l.equals("barcode") || l.equals("ean") || l.equals("upc")) {
            return "barcode";
        }
        return (l.equals("pn") || l.equals("p_n") || l.equals("part") || l.equals("part_number") || l.equals("part_no")) ? "part_number" : "";
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace('/', '_').replace('-', '_').replace(' ', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static String canon(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static boolean notEmpty(String s) {
        return (s == null || s.trim().isEmpty()) ? false : true;
    }

    private static void addOnce(List<String> xs, String x) {
        if (xs == null || xs.contains(x)) {
            return;
        }
        xs.add(x);
    }
}
