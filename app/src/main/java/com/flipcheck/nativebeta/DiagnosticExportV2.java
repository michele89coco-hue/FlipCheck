package com.flipcheck.nativebeta;

import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit allow-list: no preferences, credentials, image bytes or private file paths. */
final class DiagnosticExportV2 {
    private DiagnosticExportV2() {}
    static String create(Models.Identification id, Models.Usage usage, String secret) throws Exception {
        JSONObject out = new JSONObject().put("schema", "flipcheck-diagnostic-v1")
                .put("title", id.title).put("profile", id.v2Profile)
                .put("photoReadingOnly",id.photoReadingOnly).put("photoReadingSummary",id.photoReadingSummary)
                .put("photoReadingConflicts",id.photoReadingConflicts)
                .put("reducer", id.finalStateReducerVersion).put("identityStatus", id.identityStatus)
                .put("webStatus",id.webStatus).put("pipelineFailureDomain",id.pipelineFailureDomain)
                .put("coreIdentityStatus", id.coreIdentityStatus).put("exactIdentityStatus", id.exactIdentityStatus)
                .put("variantStatus", id.variantStatus).put("editionStatus", id.exactEditionStatus)
                .put("finishStatus", id.finishStatus).put("formatStatus", id.commercialFormatStatus)
                .put("modelStatus", id.exactModelStatus).put("marketStatus", id.marketStatus)
                .put("brand", id.brand).put("family", id.family).put("model", id.model)
                .put("cardNumber", id.physicalCardNumber.isEmpty()?id.sourceConfirmedCatalogNumber:id.physicalCardNumber)
                .put("physicalCardNumber",id.physicalCardNumber).put("catalogCardNumber",id.sourceConfirmedCatalogNumber)
                .put("slabCardNumber",id.slabCardNumber).put("slabSetName",id.slabSetName).put("slabYear",id.slabYear)
                .put("gradingCompany",id.gradingCompany).put("gradingGrade",id.gradingGrade)
                .put("gradingSubgrades",id.gradingSubgrades).put("gradingCertification",id.gradingCertification)
                .put("gradingStatus",id.gradingStatus).put("edition", id.edition).put("finish", id.finish)
                .put("format", id.sealedFormat).put("configuration", id.productConfiguration)
                .put("physicalReleaseYear", id.physicalReleaseYear).put("catalogReleaseYear", id.sourceConfirmedReleaseYear)
                .put("requestedPhotoReason", id.requestedPhotoReason).put("requestedPhoto", id.nextPhotoRequest)
                .put("uploadedImageCount", id.uploadedImageCount).put("views", new JSONArray(id.photoViews))
                .put("imagePreparation", id.v2ImagePreparationTrace).put("queries", new JSONArray(id.webQueries))
                .put("candidateTrace", id.v2CandidateTrace).put("rejectedSources", id.retrievedRejectedSources)
                .put("winner", id.candidateWinnerId).put("winnerSource", id.candidateWinnerSource)
                .put("disproof", id.disproofStatus).put("recoveryTrace", id.v2RecoveryTrace)
                .put("invariants", id.consistencyInvariants).put("invariantDetails", new JSONArray(id.consistencyInvariantErrors))
                .put("stagePayloads", new JSONArray(id.v2StagePayloads));
        JSONArray atoms = new JSONArray();
        for (EvidenceAtom a : id.evidenceAtomsV2) atoms.put(new JSONObject()
                .put("id", a.id).put("field", a.field).put("rawValue", a.rawValue)
                .put("normalizedValue", a.normalizedValue).put("level", a.epistemicLevel.name())
                .put("modality", a.modality.name()).put("source", a.source).put("sourceUrl", a.sourceUrl)
                .put("imageIndex", a.imageIndex).put("side", a.side).put("region", a.boundingBox)
                .put("cropId", a.cropId).put("semanticScope", a.semanticScope).put("confidenceScore", a.confidence)
                .put("qualityScore", a.qualityScore).put("stage", a.pipelineStage)
                .put("extractor", a.extractorVersion).put("parentEvidenceId", a.parentEvidenceId));
        out.put("evidence", atoms);
        if (usage != null) out.put("usage", new JSONObject().put("costUsd", usage.costUsd)
                .put("requests", usage.requests).put("visionCalls", usage.visionCalls).put("webCalls", usage.webCalls));
        // Sanitize values before JSON encoding, including secrets with quotes/backslashes.
        scrub(out, secret);
        return out.toString(2);
    }
    private static void scrub(Object node, String secret) throws Exception {
        if (secret == null || secret.isEmpty()) return;
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) { String key = keys.next(); Object value = object.get(key);
                if (value instanceof String) object.put(key, ((String) value).replace(secret, "[REDACTED]"));
                else scrub(value, secret);
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) { Object value = array.get(i);
                if (value instanceof String) array.put(i, ((String) value).replace(secret, "[REDACTED]"));
                else scrub(value, secret);
            }
        }
    }
}
