package com.flipcheck.nativebeta;

/** Read-only presentation decisions from the authoritative V2 profile and state. */
final class IdentityPresentationV2 {
    private IdentityPresentationV2() {}
    static boolean owns(Models.Identification id) {
        return id != null && FinalStateReducerV2.VERSION.equals(id.finalStateReducerVersion);
    }
    static boolean sealed(Models.Identification id) {
        return id != null && "sealed_trading_card_product".equals(id.v2Profile);
    }
    static boolean electronics(Models.Identification id) {
        return id != null && DomainProfileRouterV2.electronics(
                DomainProfileRouterV2.route(id.v2Profile, new ImmutableEvidenceLedgerV2()));
    }
    static String explanation(Models.Identification id) {
        if (id == null) return "Analisi non disponibile.";
        if ("CONFLICTED".equals(id.identityStatus)) return "Le prove identificative sono in conflitto: la conferma è sospesa.";
        if (!id.nextPhotoRequest.isEmpty() && !id.requestedPhotoReason.isEmpty()) return id.nextPhotoRequest;
        if (id.identityConfirmed) return id.verificationSummary;
        if ("TECHNICAL_FAILURE".equals(id.identityStatus)) return "Analisi interrotta da un errore tecnico.";
        return "Le informazioni disponibili non hanno ancora risolto l’identità. Il confronto resta da completare.";
    }
}
