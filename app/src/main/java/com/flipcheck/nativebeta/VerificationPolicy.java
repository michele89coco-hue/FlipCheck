package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import org.json.JSONObject;

final class VerificationPolicy {
    private VerificationPolicy() {
    }

    static Decision decide(Models.Identification o, Models.CandidateScore top, JSONObject r) {
        Decision d = new Decision();
        if (o == null || top == null || r == null) {
            return d;
        }
        boolean z = false;
        boolean deterministicMapping = r.optBoolean("deterministic_mapping_proven", false);
        boolean candidateNamedBySource = r.optBoolean("candidate_named_by_source", false);
        boolean noHardCompatibleAlternative = r.optBoolean("no_hard_compatible_alternative", false);
        boolean sourceAuthoritative = r.optBoolean("authoritative_source", false);
        boolean hardCompatible = (top.hardRejected || !top.hardViolations.isEmpty() || top.hardMatches.isEmpty()) ? false : true;
        if (deterministicMapping && candidateNamedBySource && noHardCompatibleAlternative && sourceAuthoritative && hardCompatible) {
            z = true;
        }
        d.deterministicClosure = z;
        d.canOverrideVisualRequest = d.deterministicClosure;
        return d;
    }

    static final class Decision {
        boolean canOverrideVisualRequest;
        boolean deterministicClosure;

        Decision() {
        }
    }
}
