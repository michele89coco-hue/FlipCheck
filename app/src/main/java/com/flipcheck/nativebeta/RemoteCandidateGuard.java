package com.flipcheck.nativebeta;

import java.util.Locale;

/** Prevents generic key vocabularies from publishing a guessed remote manufacturer. */
final class RemoteCandidateGuard {
    private RemoteCandidateGuard() {}

    static void apply(Models.Identification id) {
        if (UniversalIdentityClosure.enforceTerminalState(id)) return;
        if (!isRemote(id) || id == null || id.marketReady || trustedBrand(id)) return;
        boolean directVisual = false;
        for (Models.CandidateScore c : id.candidates) {
            if (c == null) continue;
            boolean checked = factTrue(c, "visual_reference_checked")
                    && factInt(c, "visual_match_confidence") >= 90;
            if (checked) directVisual = true;
            else {
                c.totalScore = Math.min(c.totalScore, 49);
                c.probableReferenceConfidence = Math.min(c.probableReferenceConfidence, 49);
                c.probableReference = "";
                setFact(c, "brand_identity_supported", "false");
                setFact(c, "family_identity_supported", "false");
                setFact(c, "remote_generic_controls_withheld", "true");
            }
        }
        if (directVisual) return;
        id.brand = "";
        id.family = "";
        id.model = "";
        id.modelConfidence = 0;
        id.familyConfidence = 0;
        id.title = "Telecomando TV";
        id.nextPhotoRequest = "Fotografa il retro e il vano batterie, includendo logo, MODEL/P/N e tutte le etichette";
        id.nextPhotoReason = "NETFLIX, HOME, SOURCES e la forma dei tasti sono condivisi da più produttori e non provano da soli un marchio specifico.";
        id.verificationSummary = "Il marchio ipotizzato è stato escluso dal risultato pubblico perché mancavano logo, codice fisico o confronto diretto con un riferimento visivo esatto.";
    }

    private static boolean trustedBrand(Models.Identification id) {
        return BrandBlindPolicy.trustedObservedBrand(id)
                || "visual_family_recovery".equalsIgnoreCase(id.brandEvidence)
                && id.brandRoleConfidence >= 80;
    }

    private static boolean isRemote(Models.Identification id) {
        if (id == null) return false;
        String x = (safe(id.categoryKey) + " " + safe(id.category)).toLowerCase(Locale.ROOT);
        return x.contains("remote") || x.contains("telecomando");
    }

    private static boolean factTrue(Models.CandidateScore c, String key) { return "true".equalsIgnoreCase(fact(c, key)); }
    private static int factInt(Models.CandidateScore c, String key) { try { return Integer.parseInt(fact(c, key)); } catch (Exception e) { return 0; } }
    private static String fact(Models.CandidateScore c, String key) {
        String p = key.toLowerCase(Locale.ROOT) + "=";
        for (String raw : c.candidateFacts) { String x = safe(raw); if (x.toLowerCase(Locale.ROOT).startsWith(p)) return x.substring(x.indexOf('=') + 1).trim(); }
        return "";
    }
    private static void setFact(Models.CandidateScore c, String key, String value) {
        String p = key.toLowerCase(Locale.ROOT) + "=";
        c.candidateFacts.removeIf(x -> safe(x).toLowerCase(Locale.ROOT).startsWith(p));
        c.candidateFacts.add(key + "=" + value);
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
