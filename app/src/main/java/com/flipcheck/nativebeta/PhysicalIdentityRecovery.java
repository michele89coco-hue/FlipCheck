package com.flipcheck.nativebeta;

import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded second visual pass for rich photos whose first schema omitted identity fields. */
final class PhysicalIdentityRecovery {
    private static final double MAX_TOTAL_COST_USD = 0.0200d;
    private static final double RESERVED_VISUAL_PASS_USD = 0.0035d;

    private PhysicalIdentityRecovery() {
    }

    static boolean eligible(Models.Identification id, Models.Usage usage) {
        return TcgPhysicalEditionPolicy.needsFocusedPass(id,usage);
    }

    static String prompt(Models.Identification id) {
        return "FIRST_CATEGORY=" + safe(id.category)
                + "\nFIRST_LABELS=" + clip(id.visibleLabels.toString(), 1000)
                + "\nFIRST_PHYSICAL_FACTS=" + clip(id.visualFacts.toString(), 1200)
                + "\nFIRST_IDENTITY_FIELDS=" + clip(id.photoIdentityFields.toString(), 1000)
                + "\nPHOTO_VIEWS=" + clip(id.photoViews.toString(), 300)
                + "\nDECISIVE_MISSING_FIELD=" + UniversalIdentityClosure.missingDecisiveField(id);
    }

    static boolean apply(Models.Identification id, OpenAiClient.Response response) {
        if (id == null || response == null || !response.complete
                || !safe(response.parseError).isEmpty() || response.payload == null) return false;
        JSONObject p = response.payload;
        if (!p.optBoolean("applicable", false)
                || !p.optBoolean("same_foreground_object", false)
                || !p.optBoolean("physical_binding", false)
                || p.optBoolean("external_watermark", false)
                    && p.optBoolean("identity_obscured", false)
                || !safe(p.optString("contradiction", "")).isEmpty()
                || p.optInt("confidence", 0) < 92) return false;
        JSONArray fields = p.optJSONArray("fields");
        if (fields == null || fields.length() < 3) return false;
        for (int i = 0; i < fields.length(); i++) addOnce(id.photoIdentityFields,
                safe(fields.optString(i, "")));
        JSONArray labels = p.optJSONArray("observed_labels");
        if (labels != null) for (int i = 0; i < labels.length(); i++)
            addOnce(id.visibleLabels, safe(labels.optString(i, "")));
        String name = safe(p.optString("canonical_name", ""));
        if (!name.isEmpty()) id.photoIdentityName = name;
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityOverlayOrWatermark = false;
        id.photoIdentityExternalWatermark = p.optBoolean("external_watermark", false);
        id.photoIdentityIdentityObscured = p.optBoolean("identity_obscured", false);
        id.photoIdentityKind = "composite_markings";
        id.photoIdentityConfidence = Math.max(id.photoIdentityConfidence,
                p.optInt("confidence", 0));
        id.photoIdentityComplete = p.optBoolean("complete", false);
        if(p.optBoolean("ambiguity_resolved",false)){
            id.photoIdentityAmbiguous=false;id.photoAlternativeCount=1;
            id.discriminativeFieldVisible=p.optBoolean("discriminative_field_visible",true);
            id.discriminativeField="";
        }else if(id.photoIdentityAmbiguous){
            id.discriminativeFieldVisible=p.optBoolean("discriminative_field_visible",false);
        }
        try {
            // The focused pass is useful only if its facts re-enter the same immutable
            // ledger consumed by the production normalizer.  Never promote the legacy
            // string array to direct physical evidence: only structured, localized facts
            // emitted by the focused Vision response are eligible for that strength.
            JSONArray focusedFacts=p.optJSONArray("evidence_facts");
            EvidenceLedger.ingestPhotoObservation(id,new JSONObject()
                    .put("evidence_facts",focusedFacts==null?new JSONArray():focusedFacts)
                    .put("fields",fields));
        } catch (Exception ignored) {
            // Compatibility fields have already been retained above; a malformed
            // optional recovery ledger must never erase the first-pass evidence.
        }
        NormalizedPhotoIdentity normalized=PhotographicFactNormalizer.normalize(id,
                "focused_vision_recovery_ingested");
        String category = safe(p.optString("category_key", ""));
        if ("loose_card".equals(category)) {
            boolean sports=!normalized.best(CanonicalFieldKey.TEAM).isEmpty()
                    ||!normalized.best(CanonicalFieldKey.SPORT).isEmpty();
            boolean tcg=!sports&&(!normalized.best(CanonicalFieldKey.HP_OR_PV).isEmpty()
                    ||!normalized.values(CanonicalFieldKey.ATTACK_NAME).isEmpty()
                    ||!normalized.values(CanonicalFieldKey.COLLECTOR_NUMBER_CANDIDATE).isEmpty());
            id.category = sports ? "sports trading card" : tcg ? "trading card game card" : "collectible card";
            id.categoryKey = sports ? "sports_trading_card" : tcg ? "tcg_card" : "collectible_card";
        } else if ("sealed_box".equals(category)) {
            id.category = "sealed trading-card product";
            id.categoryKey = "sealed_products";
        }
        return true;
    }

    private static void addOnce(java.util.List<String> out, String value) {
        if (value.isEmpty()) return;
        String c = canon(value);
        for (String old : out) if (canon(old).equals(c)) return;
        out.add(value);
    }

    private static String canon(String value) {
        return safe(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }

    private static String clip(String value, int max) {
        String x = safe(value);
        return x.length() <= max ? x : x.substring(0, max);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
