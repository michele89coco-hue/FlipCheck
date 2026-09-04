package com.flipcheck.nativebeta;

import java.io.Serializable;

/** Immutable decision snapshot shared by diagnostics and the public renderer. */
final class FinalIdentityState implements Serializable {
    final String title, categoryStatus, familyStatus, coreIdentityStatus, exactIdentityStatus,
            identifierStatus, variantStatus, exactEditionStatus, finishStatus, marketStatus, overallStatus, invariantStatus;
    final int categoryConfidence, familyConfidence, coreIdentityConfidence,
            exactIdentityConfidence, identifierConfidence, variantConfidence, marketConfidence;
    final boolean publicConfirmed;

    FinalIdentityState(Models.Identification id, String title, boolean confirmed) {
        this.title=safe(title); this.categoryStatus=safe(id.categoryStatus);
        this.familyStatus=safe(id.familyStatus); this.coreIdentityStatus=safe(id.coreIdentityStatus);
        this.exactIdentityStatus=safe(id.exactIdentityStatus); this.identifierStatus=safe(id.identifierStatus);
        this.variantStatus=safe(id.variantStatus); this.marketStatus=safe(id.marketStatus);
        this.exactEditionStatus=safe(id.exactEditionStatus); this.finishStatus=safe(id.finishStatus);
        this.overallStatus=safe(id.overallStatus); this.invariantStatus=safe(id.consistencyInvariants);
        this.categoryConfidence=id.categoryConfidence; this.familyConfidence=id.familyConfidence;
        this.coreIdentityConfidence=id.coreIdentityConfidence; this.exactIdentityConfidence=id.exactIdentityConfidence;
        this.identifierConfidence=id.identifierConfidence; this.variantConfidence=id.variantConfidence;
        this.marketConfidence=id.marketConfidence; this.publicConfirmed=confirmed;
    }
    private static String safe(String x){return x==null?"":x.trim();}
}
