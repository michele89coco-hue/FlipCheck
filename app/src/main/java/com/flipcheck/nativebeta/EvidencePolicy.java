package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import com.flipcheck.nativebeta.UniversalRecognitionLadder;
import java.util.List;
import java.util.Locale;

final class EvidencePolicy {
    private EvidencePolicy() {
    }

    static void apply(Models.Identification id) {
        if (id == null) {
            return;
        }
        ConfirmationIntegrityPolicy.enforce(id);
        id.observedEvidence.clear();
        id.inferredEvidence.clear();
        id.verifiedEvidence.clear();
        add(id.observedEvidence, nonEmpty(id.category) ? "Tipo osservato: " + id.category : "");
        if (isObservedBrand(id)) {
            add(id.observedEvidence, "Marca letta: " + id.brand);
        }
        if (nonEmpty(id.primaryIdentifier)) {
            add(id.observedEvidence, "Codice candidato letto: " + id.primaryIdentifier);
        }
        for (String label : id.visibleLabels) {
            if (meaningful(label)) {
                add(id.observedEvidence, "Testo visibile: " + label);
            }
            if (id.observedEvidence.size() >= 10) {
                break;
            }
        }
        for (String fact : id.visualFacts) {
            if (meaningfulFact(fact)) {
                add(id.observedEvidence, "Fatto visivo: " + fact);
            }
            if (id.observedEvidence.size() >= 16) {
                break;
            }
        }
        for (String fact2 : id.userConfirmedFacts) {
            if (!workflowFact(fact2) && meaningfulFact(fact2)) {
                add(id.observedEvidence, "Confermato dall'utente: " + fact2);
            }
        }
        if (PhotoIdentityPolicy.observationStrong(id)) {
            add(id.observedEvidence, "Identità completa leggibile: " + id.photoIdentityName);
            for (String field : id.photoIdentityFields) {
                if (meaningfulFact(field)) {
                    add(id.observedEvidence, "Campo identitario: " + field);
                }
            }
        }
        UniversalRecognitionLadder.State s = UniversalRecognitionLadder.assess(id);
        if (s.level >= 2 && !s.brand.isEmpty() && !isObservedBrand(id)) {
            add(id.inferredEvidence, "Marca probabile: " + s.brand);
        }
        if (s.level >= 3 && !s.family.isEmpty()) {
            add(id.inferredEvidence, "Famiglia/serie probabile: " + s.family);
        }
        if (s.level >= 4 && !s.model.isEmpty()) {
            add(id.inferredEvidence, "Modello candidato: " + s.model);
        }
        if (s.level >= 5 && !s.variant.isEmpty()) {
            add(id.inferredEvidence, "Versione/variante candidata: " + s.variant);
        }
        for (String x : id.visionCandidates) {
            add(id.inferredEvidence, "Ipotesi Vision non vincolante: " + x);
        }
        for (int i = 0; i < id.candidates.size() && i < 4; i++) {
            Models.CandidateScore candidate = id.candidates.get(i);
            add(id.inferredEvidence, "Candidato " + (i + 1) + ": " + candidate.displayName());
            if (PhotoIdentityPolicy.probableReferenceAllowed(candidate, id)) {
                add(id.inferredEvidence, "Riferimento probabile da verificare: "
                        + candidate.probableReference + " ("
                        + candidate.probableReferenceConfidence + "%)");
            }
        }
        if (id.marketReady) {
            add(id.verifiedEvidence, "Marca verificata: " + id.brand);
            add(id.verifiedEvidence, "Famiglia verificata: " + id.family);
            add(id.verifiedEvidence, "Modello verificato: " + id.model);
            if (nonEmpty(id.modelProof) && !"none".equalsIgnoreCase(id.modelProof)) {
                add(id.verifiedEvidence, "Tipo prova: " + id.modelProof);
            }
            if ("photo_complete_identity".equalsIgnoreCase(id.modelProof)) {
                add(id.verifiedEvidence, "Identità fotografica completa e coerente con la fonte");
            }
            for (String x2 : id.matchedVisualFacts) {
                add(id.verifiedEvidence, "Fatto confermato: " + x2);
            }
            for (String x3 : id.matchedLayoutTokens) {
                add(id.verifiedEvidence, "Layout confermato: " + x3);
            }
            id.brandEvidence = "verified_web";
        }
    }

    static String publicTitle(Models.Identification id) {
        if (id == null) {
            return "Oggetto";
        }
        if (id.marketReady) {
            String x = verifiedTitle(id);
            return nonEmpty(x) ? x : nonEmpty(id.title) ? id.title : fallbackCategory(id);
        }
        UniversalRecognitionLadder.State s = UniversalRecognitionLadder.assess(id);
        if (s.level >= 4) {
            String x2 = join(s.brand, s.family, s.model);
            if (nonEmpty(x2)) {
                return x2;
            }
        }
        if (s.level >= 3) {
            String x3 = join(s.brand, s.family);
            if (nonEmpty(x3)) {
                return x3;
            }
        }
        if (s.level >= 2) {
            String x4 = join(s.brand, fallbackCategory(id));
            if (nonEmpty(x4)) {
                return x4;
            }
        }
        return fallbackCategory(id);
    }

    private static String verifiedTitle(Models.Identification id) {
        String model = id == null ? "" : safe(id.model);
        if (model.isEmpty()) {
            return join(id.brand, id.family);
        }
        if (tokenCoverage(model, id.family) >= 0.75d) {
            return tokenCoverage(model, id.brand) >= 1d ? model : join(id.brand, model);
        }
        return join(id.brand, id.family, model);
    }

    private static double tokenCoverage(String haystack, String needle) {
        String target = canonWords(needle);
        if (target.isEmpty()) {
            return 0d;
        }
        String observed = " " + canonWords(haystack) + " ";
        String[] tokens = target.split(" ");
        int total = 0;
        int found = 0;
        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            total++;
            if (observed.contains(" " + token + " ")) {
                found++;
            }
        }
        return total == 0 ? 0d : (double) found / total;
    }

    private static String canonWords(String value) {
        return safe(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ")
                .trim().replaceAll("\\s+", " ");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static String candidateLabel(Models.Identification id) {
        if (id == null || id.marketReady) {
            return "";
        }
        UniversalRecognitionLadder.State s = UniversalRecognitionLadder.assess(id);
        return (s.level < 4 || s.candidate == null) ? s.level >= 3 ? join(s.brand, s.family) : s.level >= 2 ? s.brand : "" : s.candidate.displayName();
    }

    static int publicConfidence(Models.Identification id) {
        return ConfidencePolicy.identity(id);
    }

    static String publicStatus(Models.Identification id) {
        if (id == null) {
            return "NEED_ANOTHER_PHOTO · ANALISI INCOMPLETA";
        }
        if (id.marketReady) {
            return "CONFIRMED · IDENTITÀ VERIFICATA";
        }
        UniversalRecognitionLadder.State s = UniversalRecognitionLadder.assess(id);
        if (s.finalWithAvailableEvidence) {
            return s.level >= 2 ? "PROBABLE · MIGLIOR RISULTATO DISPONIBILE"
                    : "NEED_ANOTHER_PHOTO · IDENTITÀ NON DETERMINATA";
        }
        return s.level >= 3 ? "PROBABLE · SERVE UNA PROVA DISCRIMINANTE"
                : "NEED_ANOTHER_PHOTO · RICONOSCIMENTO PARZIALE";
    }

    static String publicExplanation(Models.Identification id) {
        if (id == null) {
            return "";
        }
        if (id.marketReady) {
            String summary = nonEmpty(id.verificationSummary) ? id.verificationSummary
                    : "Più prove indipendenti convergono sulla stessa identità senza contraddizioni forti.";
            if ("photo_complete_identity".equalsIgnoreCase(id.modelProof)) {
                return summary + " La conferma riguarda l'identità/versione visibile, non autentica l'esemplare né ne valuta la condizione.";
            }
            return summary;
        }
        UniversalRecognitionLadder.State s = UniversalRecognitionLadder.assess(id);
        return s.finalWithAvailableEvidence ? "Questo è il livello massimo sostenuto dalle foto e dalle fonti già disponibili; gli elementi non verificati restano esplicitamente preliminari." : nonEmpty(id.nextPhotoReason) ? id.nextPhotoReason : "Il sistema mostra solo il livello sostenuto dalle prove disponibili; per arrivare al modello esatto serve la foto discriminante richiesta.";
    }

    static boolean isObservedBrand(Models.Identification id) {
        if (id == null) {
            return false;
        }
        if (BrandBlindPolicy.trustedObservedBrand(id)) {
            return true;
        }
        String e = id.brandEvidence == null ? "" : id.brandEvidence.toLowerCase(Locale.ROOT);
        return e.equals("visible_logo") || e.equals("visible_brand_text") || e.equals("explicit_label") || e.equals("ocr_brand");
    }

    private static boolean workflowFact(String x) {
        return x != null && x.toLowerCase(Locale.ROOT).startsWith("workflow:");
    }

    private static boolean meaningfulFact(String x) {
        if (!meaningful(x)) {
            return false;
        }
        int p = x.indexOf(61);
        String value = p < 0 ? x : p + 1 < x.length() ? x.substring(p + 1) : "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return meaningful(value) && !normalized.equals("none")
                && !normalized.equals("none identified")
                && !normalized.equals("not identified")
                && !normalized.equals("unknown")
                && !normalized.equals("not applicable");
    }

    private static boolean meaningful(String x) {
        return (x == null || x.trim().isEmpty() || "null".equalsIgnoreCase(x.trim())) ? false : true;
    }

    private static void add(List<String> out, String value) {
        if (meaningful(value)) {
            for (String x : out) {
                if (x.equalsIgnoreCase(value)) {
                    return;
                }
            }
            out.add(value);
        }
    }

    private static String fallbackCategory(Models.Identification id) {
        return nonEmpty(id.category) ? id.category : "Oggetto";
    }

    private static boolean nonEmpty(String s) {
        return (s == null || s.trim().isEmpty()) ? false : true;
    }

    private static String join(String... xs) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < xs.length; i++) {
            String x = xs[i];
            if (nonEmpty(x)) {
                String current = canon(x);
                boolean containedLater = false;
                for (int j = i + 1; j < xs.length; j++) {
                    String later = canon(xs[j]);
                    if (!current.isEmpty() && later.length() > current.length()
                            && later.contains(current)) {
                        containedLater = true;
                        break;
                    }
                }
                if (containedLater) {
                    continue;
                }
                if (b.length() > 0) {
                    b.append(' ');
                }
                b.append(x.trim());
            }
        }
        return b.toString();
    }

    private static String canon(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
