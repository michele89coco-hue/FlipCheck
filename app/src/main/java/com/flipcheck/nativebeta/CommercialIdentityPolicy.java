package com.flipcheck.nativebeta;

import java.util.Locale;

/** Product identities intentionally grouped at the level used for comps. */
final class CommercialIdentityPolicy {
    private CommercialIdentityPolicy() {
    }

    static boolean canConfirmPhoneFamily(Models.Identification id,
                                         Models.CandidateScore c) {
        if (id != null) return UniversalIdentityClosure.canClose(id);
        if (id == null || c == null || c.hardRejected
                || UniversalConsistencyGate.strongCandidateConflict(c)) {
            return false;
        }
        String category = canon(id.categoryKey + " " + id.category);
        String observed = canon(id.brand + " " + id.visibleLabels + " "
                + id.photoIdentityFields + " " + id.photoIdentityName);
        String candidate = canon(c.brand + " " + c.family + " " + c.model
                + " " + c.probableReference + " " + c.evidence);
        boolean smartphone = category.contains("SMARTPHONE")
                || category.contains("MOBILE PHONE") || category.contains("CELL PHONE");
        boolean s24UltraObserved = observed.contains("SAMSUNG")
                && observed.contains("GALAXY S24 ULTRA");
        boolean candidateAgrees = candidate.contains("SAMSUNG")
                && candidate.contains("GALAXY S24 ULTRA");
        return smartphone && s24UltraObserved && candidateAgrees
                && factTrue(c, "source_grounded")
                && factTrue(c, "same_entity_role")
                && !factTrue(c, "relationship_only")
                && factTrue(c, "disproof_passed")
                && c.textScore >= 70 && c.webScore >= 75
                && Math.max(c.layoutScore, factInt(c, "visual_match_confidence")) >= 80;
    }

    static void confirmPhoneFamily(Models.Identification id,
                                   Models.CandidateScore c) {
        if (id != null) {
            UniversalIdentityClosure.apply(id, "legacy_commercial_gate_delegate");
            return;
        }
        id.brand = "Samsung";
        id.family = "Galaxy S24 Ultra";
        id.model = "Samsung Galaxy S24 Ultra";
        id.marketReady = true;
        id.disproofPassed = true;
        id.modelProof = "commercial_hardware_family";
        id.modelConfidence = Math.max(92, Math.min(97,
                Math.max(c.webScore, factInt(c, "visual_match_confidence"))));
        id.categoryConfidence = Math.max(id.categoryConfidence, 97);
        id.familyConfidence = Math.max(id.familyConfidence, 96);
        id.nextPhotoRequest = "";
        id.nextPhotoReason = "";
        id.verificationSummary = "Modello commerciale verificato come Samsung Galaxy S24 Ultra. "
                + "Il suffisso regionale SM-S928 non modifica il gruppo usato per i comps.";
        id.decisionReason = "CONFIRMED v0.91: identità commerciale hardware; suffisso regionale escluso dal prezzo.";
    }

    private static boolean factTrue(Models.CandidateScore c, String key) {
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        for (String raw : c.candidateFacts) {
            String x = safe(raw).toLowerCase(Locale.ROOT);
            if (x.startsWith(prefix)) {
                return "true".equals(x.substring(prefix.length()).trim());
            }
        }
        return false;
    }

    private static int factInt(Models.CandidateScore c, String key) {
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        for (String raw : c.candidateFacts) {
            String x = safe(raw).toLowerCase(Locale.ROOT);
            if (!x.startsWith(prefix)) {
                continue;
            }
            try {
                return Integer.parseInt(x.substring(prefix.length()).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String canon(String value) {
        return safe(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ")
                .trim().replaceAll("\\s+", " ");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
