package com.flipcheck.nativebeta;

/** Final fail-closed invariant shared by every confirmation route. */
final class ConfirmationIntegrityPolicy {
    private ConfirmationIntegrityPolicy() {
    }

    static void enforce(Models.Identification id) {
        if (id == null || !id.marketReady) {
            return;
        }
        // Physical card tuple closure has completed the mandatory independent
        // review. Do not reopen it merely because no checklist number was
        // printed/read (or because a visible number is a game statistic).
        if ("physical_card_tuple".equals(id.modelProof)) {
            return;
        }
        Models.CandidateScore top = firstAlive(id);
        CollectibleCardIdentityPolicy.normalizeConfirmedIdentity(id, top);
        boolean missingExactModel = safe(id.model).isEmpty();
        boolean tcgModelIncomplete = CollectibleCardIdentityPolicy.modelIsOnlyCollectorNumber(id);
        boolean tcgNumberMissing = CollectibleCardIdentityPolicy.isTradingCardGame(id)
                && CollectibleCardIdentityPolicy.observedCardNumber(id, id.localScan).isEmpty();
        boolean unresolvedVariant = CollectibleCardIdentityPolicy.variantUnresolved(id, top);
        if (!missingExactModel && !tcgModelIncomplete && !tcgNumberMissing && !unresolvedVariant) {
            return;
        }
        id.marketReady = false;
        id.disproofPassed = false;
        id.modelProof = "none";
        id.modelConfidence = Math.min(84, id.modelConfidence);
        if (CollectibleCardIdentityPolicy.isTradingCardGame(id)) {
            id.nextPhotoRequest = "Fotografa meglio il fronte completo della carta, nitido e senza riflessi, "
                    + "includendo numero collezione, bordo dell'illustrazione, riquadro attacchi "
                    + "ed eventuale timbro di edizione.";
            id.nextPhotoReason = unresolvedVariant
                    ? "La carta di base è riconosciuta, ma la stampa/variante che incide su identità e valore non è ancora distinguibile."
                    : "Manca un numero collezione fisicamente leggibile e collegato alla carta fotografata.";
        } else {
            id.nextPhotoRequest = "Aggiungi una foto della marcatura completa che identifica modello o variante.";
            id.nextPhotoReason = "Una conferma esatta richiede un modello non vuoto e fisicamente collegato all'oggetto.";
        }
        id.verificationSummary = id.nextPhotoReason;
        id.decisionReason = "CONFIRMATION-INTEGRITY v0.90: conferma esatta declassata perché l'identità completa o la variante non è chiusa.";
    }

    private static Models.CandidateScore firstAlive(Models.Identification id) {
        for (Models.CandidateScore c : id.candidates) {
            if (c != null && !c.hardRejected) {
                return c;
            }
        }
        return null;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
