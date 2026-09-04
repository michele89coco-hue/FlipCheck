package com.flipcheck.nativebeta;

/** Compatibility facade: every legacy gate delegates to the photographic closure authority. */
final class UniversalIdentityClosure {
    static final String ROUTE=PhotographicIdentityClosure.ROUTE;
    private UniversalIdentityClosure() {}
    static boolean canClose(Models.Identification id){return PhotographicIdentityClosure.canClose(id);}
    static boolean apply(Models.Identification id){return PhotographicIdentityClosure.apply(id,"unspecified");}
    static boolean apply(Models.Identification id,String stage){return PhotographicIdentityClosure.apply(id,stage);}
    static boolean isTerminal(Models.Identification id){return PhotographicIdentityClosure.isTerminal(id);}
    static boolean enforceTerminalState(Models.Identification id){return PhotographicIdentityClosure.enforce(id);}
    static boolean mayRequestAnotherPhoto(Models.Identification id){return PhotographicIdentityClosure.mayRequestAnotherPhoto(id);}
    static String missingDecisiveField(Models.Identification id){return PhotographicIdentityClosure.missingDecisiveField(id);}
    static String missingFields(Models.Identification id){return PhotographicIdentityClosure.missingFields(id);}
    static boolean externalWatermarkObscuresIdentity(Models.Identification id){return id!=null&&id.photoIdentityExternalWatermark&&id.photoIdentityIdentityObscured;}
}
