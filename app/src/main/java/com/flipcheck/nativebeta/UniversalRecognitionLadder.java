package com.flipcheck.nativebeta;

import androidx.core.os.EnvironmentCompat;
import com.flipcheck.nativebeta.Models;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class UniversalRecognitionLadder {
    static final int BRAND = 2;
    static final int FAMILY = 3;
    static final int MODEL = 4;
    private static final String NO_MORE_PHOTOS = "workflow:no_more_photos";
    static final int TYPE = 1;
    static final int VARIANT = 5;

    private UniversalRecognitionLadder() {
    }

    static final class State {
        Models.CandidateScore candidate;
        int confidence;
        boolean finalWithAvailableEvidence;
        int level = 1;
        String type = "";
        String brand = "";
        String family = "";
        String model = "";
        String variant = "";
        String reason = "";

        State() {
        }
    }

    static void apply(Models.Identification id) {
        if (id == null) {
            return;
        }
        if(id.finalState!=null)return;
        if (UniversalIdentityClosure.enforceTerminalState(id)) return;
        State s = assess(id);
        if (id.marketReady) {
            id.nextPhotoRequest = "";
            id.nextPhotoReason = "";
            return;
        }
        if (s.finalWithAvailableEvidence) {
            id.nextPhotoRequest = "";
            id.nextPhotoReason = "Risultato finale limitato alle prove già disponibili; nessuna ulteriore foto richiesta.";
            id.photoProtocolReady = true;
            if (s.level >= 3) {
                id.familyConfidence = Math.max(id.familyConfidence, derivedConfidence(id, s.confidence));
            }
            if (s.level >= 4) {
                id.modelConfidence = Math.max(id.modelConfidence, derivedConfidence(id, s.confidence));
                return;
            }
            return;
        }
        id.photoProtocolReady = false;
        id.nextPhotoRequest = nextPhotoRequest(s, id);
        id.nextPhotoReason = nextPhotoReason(s);
        if (s.level >= 3) {
            id.familyConfidence = Math.max(id.familyConfidence, derivedConfidence(id, s.confidence));
        }
        if (s.level >= 4) {
            id.modelConfidence = Math.max(id.modelConfidence, derivedConfidence(id, s.confidence));
        }
    }

    static State assess(Models.Identification id) {
        int margin;
        State s = new State();
        if (id == null) {
            return s;
        }
        s.type = clean(id.category);
        s.confidence = clamp(id.categoryConfidence);
        s.finalWithAvailableEvidence = hasNoMorePhotos(id);
        if (id.marketReady) {
            s.level = 5;
            s.brand = clean(id.brand);
            s.family = clean(id.family);
            s.model = clean(id.model);
            Models.CandidateScore top = top(id);
            s.candidate = top;
            s.variant = variantFact(top);
            if (s.variant.isEmpty()) {
                s.level = 4;
            }
            s.confidence = clamp(Math.max(id.modelConfidence, 90));
            s.reason = "Identità verificata da prove convergenti.";
            return s;
        }
        Models.CandidateScore top2 = top(id);
        Models.CandidateScore second = second(id);
        s.candidate = top2;
        if (top2 == null) {
            margin = 0;
        } else {
            margin = top2.totalScore;
            if (second != null) {
                margin = Math.max(0, margin - second.totalScore);
            }
        }
        int visual = top2 == null ? 0 : UniversalConsistencyGate.retrievalVisualConfidence(top2);
        String observedBrand = BrandBlindPolicy.trustedObservedBrand(id) ? clean(id.brand) : "";
        String consensusBrand = brandConsensus(id);
        if (!observedBrand.isEmpty()) {
            s.level = 2;
            s.brand = observedBrand;
            s.confidence = Math.max(s.confidence, 90);
            s.reason = "Marca leggibile direttamente sull'oggetto.";
        } else if (!consensusBrand.isEmpty() && top2 != null && top2.totalScore >= 70 && !UniversalConsistencyGate.strongCandidateConflict(top2)) {
            s.level = 2;
            s.brand = consensusBrand;
            s.confidence = Math.max(s.confidence, Math.min(84, top2.totalScore));
            s.reason = "Più candidati grounded convergono sulla stessa marca.";
        } else if ("visual_family_recovery".equalsIgnoreCase(clean(id.brandEvidence))
                && !clean(id.brand).isEmpty() && id.brandRoleConfidence >= 80) {
            s.level = 2;
            s.brand = clean(id.brand);
            s.confidence = Math.max(s.confidence, Math.min(84, id.brandRoleConfidence));
            s.reason = "Marca probabile dal controllo visivo selettivo; non è un vincolo fisico letto.";
        }
        if (s.level >= 2
                && "visual_family_recovery".equalsIgnoreCase(clean(id.brandEvidence))
                && !clean(id.family).isEmpty() && id.familyConfidence >= 85) {
            s.level = 3;
            s.family = clean(id.family);
            s.confidence = Math.max(s.confidence, derivedConfidence(id, id.familyConfidence));
            s.reason = "Famiglia probabile dal design fisico; il modello esatto resta non verificato.";
        }
        if (SealedProductIdentityPolicy.hasPhotoTupleFamily(id)) {
            s.brand = clean(id.brand);
            s.family = clean(id.family);
            s.level = Math.max(s.level, 3);
            s.confidence = Math.max(s.confidence,
                    derivedConfidence(id, id.photoIdentityConfidence));
            s.reason = "Famiglia/serie letta come tupla fisica completa sul prodotto sigillato; manca ancora una fonte esatta superstite.";
        }
        if (top2 != null && !UniversalConsistencyGate.strongCandidateConflict(top2)) {
            String tb = clean(top2.brand);
            String tf = clean(top2.family);
            String tm = clean(top2.model);
            boolean brandCompatible = s.brand.isEmpty() || tb.isEmpty() || canon(s.brand).equals(canon(tb));
            if (brandCompatible && familySupported(top2, second, margin, visual, id)) {
                if (s.brand.isEmpty()) {
                    s.brand = tb;
                }
                s.family = tf;
                s.level = Math.max(s.level, 3);
                s.confidence = Math.max(s.confidence, derivedConfidence(id, top2.totalScore));
                s.reason = "Marca/famiglia sostenute da più segnali indipendenti e dalla struttura osservata.";
            }
            if (brandCompatible && modelSupported(top2, second, margin, visual, id)) {
                if (s.brand.isEmpty()) {
                    s.brand = tb;
                }
                if (s.family.isEmpty()) {
                    s.family = tf;
                }
                s.model = tm;
                s.level = Math.max(s.level, 4);
                s.confidence = Math.max(s.confidence, derivedConfidence(id, top2.totalScore));
                s.reason = "Candidato modello forte, ma non ancora verificato definitivamente.";
                String v = variantFact(top2);
                if (!v.isEmpty() && top2.totalScore >= 92 && margin >= 15 && supportChannels(top2, visual) >= 3) {
                    s.variant = v;
                    s.level = 5;
                    s.confidence = Math.max(s.confidence, derivedConfidence(id, top2.totalScore));
                    s.reason = "Modello e versione/variante sono fortemente sostenuti, ma restano preliminari finché non verificati.";
                }
            }
        }
        return s;
    }

    static void finalizeWithoutMorePhotos(Models.Identification id) {
        if (id == null) {
            return;
        }
        if (!hasNoMorePhotos(id)) {
            id.userConfirmedFacts.add(NO_MORE_PHOTOS);
        }
        id.nextPhotoRequest = "";
        id.nextPhotoReason = "Risultato finale limitato alle prove già disponibili; nessuna ulteriore foto richiesta.";
        id.photoProtocolReady = true;
        State s = assess(id);
        if (s.level >= 3) {
            id.familyConfidence = Math.max(id.familyConfidence, derivedConfidence(id, s.confidence));
        }
        if (s.level >= 4) {
            id.modelConfidence = Math.max(id.modelConfidence, derivedConfidence(id, s.confidence));
        }
    }

    static boolean hasNoMorePhotos(Models.Identification id) {
        if (id == null) {
            return false;
        }
        for (String x : id.userConfirmedFacts) {
            if (NO_MORE_PHOTOS.equalsIgnoreCase(clean(x))) {
                return true;
            }
        }
        return false;
    }

    /** Confidence remains evidence-derived; this ladder never injects a stock percentage. */
    private static int derivedConfidence(Models.Identification id,int source){
        int evidence=id==null?0:Math.max(id.categoryConfidence,Math.max(id.familyConfidence,id.mainIdentityConfidence));
        int value=evidence>0?Math.min(clamp(source),clamp(evidence)):clamp(source);
        if(id!=null&&!clean(id.numberConflicts).isEmpty())value=Math.max(0,value-25);
        return value;
    }

    static String levelLabel(State s) {
        if (s == null) {
            return "tipo";
        }
        switch (s.level) {
            case 2:
                return "marca";
            case 3:
                return "famiglia/serie";
            case 4:
                return "modello";
            case 5:
                return "versione/variante";
            default:
                return "tipo di oggetto";
        }
    }

    private static boolean familySupported(Models.CandidateScore top, Models.CandidateScore second, int margin, int visual, Models.Identification id) {
        boolean sameFamily;
        boolean separation;
        boolean structural;
        boolean explicitFamilySupport = factTrue(top, "family_identity_supported");
        if (top == null || empty(top.family) || generic(top.family, id)
                || top.totalScore < (explicitFamilySupport ? 65 : 70)) {
            return false;
        }
        if (second == null || empty(second.family) || !canon(top.family).equals(canon(second.family))) {
            sameFamily = false;
        } else {
            sameFamily = true;
        }
        if (margin < 8 && !sameFamily) {
            separation = false;
        } else {
            separation = true;
        }
        if (!separation) {
            return false;
        }
        int channels = supportChannels(top, visual);
        if (explicitFamilySupport) {
            return channels >= 2 && (visual >= 70
                    || (top.textScore >= 68 && top.layoutScore >= 60 && top.webScore >= 60));
        }
        if (visual < 45 && ((top.layoutScore < 72 || top.textScore < 70) && top.hardMatchWeight < 60)) {
            structural = false;
        } else {
            structural = true;
        }
        if (channels < 2 || !structural) {
            return false;
        }
        return true;
    }

    private static boolean modelSupported(Models.CandidateScore top, Models.CandidateScore second, int margin, int visual, Models.Identification id) {
        if (top == null || empty(top.model) || generic(top.model, id) || top.totalScore < 82
                || !factTrue(top, "exact_reference_complete")
                || !factTrue(top, "exact_identity_supported")) {
            return false;
        }
        if (second != null && margin < 12) {
            return false;
        }
        int channels = supportChannels(top, visual);
        boolean identityAnchor = top.hardMatchWeight >= 60 || visual >= 60 || top.identifierScore >= 85 || (top.textScore >= 88 && top.layoutScore >= 82 && top.webScore >= 82);
        return channels >= 3 && identityAnchor;
    }

    private static int supportChannels(Models.CandidateScore c, int visual) {
        if (c == null) {
            return 0;
        }
        int n = c.identifierScore >= 75 ? 0 + 1 : 0;
        if (c.textScore >= 75) {
            n++;
        }
        if (c.layoutScore >= 72) {
            n++;
        }
        if (c.webScore >= 72) {
            n++;
        }
        if (visual >= 55) {
            n++;
        }
        return c.hardMatchWeight >= 60 ? n + 1 : n;
    }

    private static String brandConsensus(Models.Identification id) {
        if (id == null) {
            return "";
        }
        List<Models.CandidateScore> xs = alive(id, 3);
        if (xs.isEmpty()) {
            return "";
        }
        String b = clean(xs.get(0).brand);
        if (b.isEmpty()) {
            return "";
        }
        if (xs.get(0).totalScore >= 65
                && factTrue(xs.get(0), "brand_identity_supported")) {
            return b;
        }
        if (xs.size() < 2) {
            return "";
        }
        int n = 0;
        for (Models.CandidateScore c : xs) {
            if (c.totalScore >= 55 && canon(b).equals(canon(c.brand))) {
                n++;
            }
        }
        return n >= 2 ? b : "";
    }

    private static String variantFact(Models.CandidateScore c) {
        if (c == null) {
            return "";
        }
        String[] preferred = {"variant", "version", "edition", "revision", "configuration", "colorway", "finish", "capacity", "size", "trim", "submodel"};
        for (String k : preferred) {
            String v = fact(c, k);
            if (!empty(v) && !missing(v)) {
                return v;
            }
        }
        return "";
    }

    private static String nextPhotoRequest(State s, Models.Identification id) {
        boolean hasHard = (id == null || id.hardConstraints.isEmpty()) ? false : true;
        if (CollectibleCardIdentityPolicy.isCard(id)) {
            if (CollectibleCardIdentityPolicy.isTradingCardGame(id)) {
                return "Fotografa meglio il fronte completo della carta, nitido e senza riflessi, includendo numero collezione, bordo dell'illustrazione, riquadro degli attacchi ed eventuale timbro di edizione.";
            }
            return "Fotografa il retro completo della carta, nitido e senza riflessi, "
                    + "includendo codice carta, copyright/set e l'eventuale tiratura; "
                    + "se la tiratura è sul fronte, aggiungi anche un primo piano dei quattro angoli.";
        }
        if (SealedProductIdentityPolicy.isSealedRetailProduct(id)) {
            return "Fotografa lato e retro del box includendo barcode, stagione, formato "
                    + "(Hobby/Mega/Value/Blaster) e configurazione di pacchetti e carte.";
        }
        String category = id == null ? "" : (clean(id.categoryKey) + " "
                + clean(id.category)).toLowerCase(Locale.ROOT);
        if (category.contains("smartphone") || category.contains("mobile phone")
                || category.contains("cell phone")) {
            return "Apri Impostazioni > Info sul telefono e fotografa il codice modello completo; in alternativa fotografa l'etichetta originale della scatola.";
        }
        if (category.contains("irrigation") || category.contains("sprinkler")) {
            return "Fotografa la targhetta sul retro o all'interno dello sportello, "
                    + "includendo MODEL/P-N e una vista completa del pannello e delle stazioni.";
        }
        if (s.level >= 4) {
            return "Se disponibile, aggiungi una seconda vista che mostri un identificatore o un dettaglio discriminante: etichetta/targhetta, MODEL/P/N, codice a barre, retro/lato o particolare distintivo.";
        }
        if (s.level >= 2 || hasHard) {
            return "Se disponibile, fotografa una zona con MODEL, P/N, seriale, codice a barre o altra marcatura; se non c'è, aggiungi retro/lato o un dettaglio distintivo dell'oggetto.";
        }
        return "Se disponibile, aggiungi una seconda vista da un'angolazione diversa includendo logo, etichetta, codici, pannello/controlli o altri dettagli distintivi.";
    }

    private static String nextPhotoReason(State s) {
        return "Il riconoscimento è arrivato fino a " + levelLabel(s) + ". Una seconda foto è facoltativa ma può separare i candidati rimasti e aumentare la precisione.";
    }

    private static Models.CandidateScore top(Models.Identification id) {
        if (id == null) {
            return null;
        }
        for (Models.CandidateScore c : id.candidates) {
            if (c != null && !c.hardRejected && !UniversalConsistencyGate.strongCandidateConflict(c)) {
                return c;
            }
        }
        return null;
    }

    private static Models.CandidateScore second(Models.Identification id) {
        if (id == null) {
            return null;
        }
        int n = 0;
        for (Models.CandidateScore c : id.candidates) {
            if (c != null && !c.hardRejected && !UniversalConsistencyGate.strongCandidateConflict(c) && (n = n + 1) == 2) {
                return c;
            }
        }
        return null;
    }

    private static List<Models.CandidateScore> alive(Models.Identification id, int limit) {
        List<Models.CandidateScore> out = new ArrayList<>();
        if (id == null) {
            return out;
        }
        for (Models.CandidateScore c : id.candidates) {
            if (c != null && !c.hardRejected && !UniversalConsistencyGate.strongCandidateConflict(c)) {
                out.add(c);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    private static String fact(Models.CandidateScore c, String key) {
        if (c == null) {
            return "";
        }
        String str = norm(key) + "=";
        for (String raw : c.candidateFacts) {
            String x = clean(raw);
            int e = x.indexOf(61);
            if (e > 0 && norm(x.substring(0, e)).equals(norm(key))) {
                return x.substring(e + 1).trim();
            }
        }
        return "";
    }

    private static boolean factTrue(Models.CandidateScore c, String key) {
        return "true".equalsIgnoreCase(fact(c, key));
    }

    private static boolean generic(String x, Models.Identification id) {
        String v = canon(x);
        if (v.isEmpty()) {
            return true;
        }
        String cat = canon(id == null ? "" : id.category);
        return (!cat.isEmpty() && (v.equals(cat) || (v.contains(cat) && v.length() < cat.length() + 10))) || v.equals("UNKNOWN") || v.equals("GENERIC") || v.equals("OBJECT") || v.equals("PRODUCT") || v.equals("ITEM");
    }

    private static boolean missing(String x) {
        String v = clean(x).toLowerCase(Locale.ROOT);
        return v.isEmpty() || v.equals(EnvironmentCompat.MEDIA_UNKNOWN) || v.equals("none") || v.equals("n/a") || v.equals("not visible") || v.equals("unclear");
    }

    private static String norm(String s) {
        return clean(s).toLowerCase(Locale.ROOT).replace('-', '_').replace('/', '_').replace(' ', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static String canon(String s) {
        return clean(s).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean empty(String s) {
        return clean(s).isEmpty();
    }

    private static int clamp(int x) {
        return Math.max(0, Math.min(100, x));
    }
}
