package com.flipcheck.nativebeta;

import android.content.Context;
import android.net.Uri;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Cost-free deterministic input for Android smoke tests. Production execution
 * can reach this class only when both BuildConfig.DEBUG and ci_mock_mode are true.
 */
final class CiMockPipeline {
    private CiMockPipeline() {
    }

    static Models.Identification run(Context context, List<Uri> images) throws Exception {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Mock smoke test requires an image URI");
        }
        // Exercise the same ContentResolver and image encoder used before Vision.
        String encoded = ImageDataEncoder.toDataUrl(context, images.get(0));
        if (!encoded.startsWith("data:image/")) {
            throw new IllegalStateException("Mock fixture could not be encoded");
        }

        Models.LocalScan local = new Models.LocalScan();
        local.textByImage.add("Fixture Publisher Fixture Series Athlete Alpha No. 42");
        Models.Identification id = new Models.Identification();
        id.localScan = local;
        IdentificationPipelineV082.parsePrimaryObservationAndAttemptClosure(
                id, productionShapedObservation(), local);
        IdentificationEngine.finalizeOutput(id, null);
        if (!id.identityConfirmed || !"CONFIRMED".equals(id.coreIdentityStatus)) {
            throw new IllegalStateException("Mock canonical pipeline did not close");
        }
        return id;
    }

    private static JSONObject productionShapedObservation() throws Exception {
        JSONArray facts = new JSONArray()
                .put(fact("manufacturer/publisher", "Fixture Publisher", 1,
                        "back", "publisher line", "manufacturer_publisher"))
                .put(fact("set_or_product_line", "Fixture Series", 1,
                        "back", "set title", "product_line"))
                .put(fact("subject", "Athlete Alpha", 0,
                        "front", "nameplate", "subject"))
                .put(fact("card_number", "42", 1,
                        "back", "No. 42 label", "card_number"));
        JSONObject candidate = new JSONObject()
                .put("category_key", "sports_card")
                .put("brand", "Fixture Publisher")
                .put("family", "Fixture Series")
                .put("model", "Athlete Alpha 42")
                .put("subject", "Athlete Alpha")
                .put("card_number", "42")
                .put("confidence", 96)
                .put("materially_distinct_variant", false)
                .put("evidence", "same physical tuple");
        JSONObject photoIdentity = new JSONObject()
                .put("complete", true)
                .put("canonical_name", "Athlete Alpha card")
                .put("identity_code", "42")
                .put("evidence_kind", "composite_markings")
                .put("physical_binding", true)
                .put("identity_obscured", false)
                .put("identity_ambiguous", false)
                .put("materially_distinct_alternatives", 1)
                .put("missing_discriminative_field", "")
                .put("confidence", 97)
                .put("fields", new JSONArray()
                        .put("manufacturer/publisher=Fixture Publisher")
                        .put("set_or_product_line=Fixture Series")
                        .put("subject=Athlete Alpha")
                        .put("card_number=42"))
                .put("candidates", new JSONArray().put(candidate))
                .put("evidence_facts", facts);
        return new JSONObject()
                .put("observation_valid", true)
                .put("title", "photographed sports card")
                .put("category", "sports trading card")
                .put("category_key", "sports_card")
                .put("category_confidence", 98)
                .put("photo_views", new JSONArray().put("front").put("back"))
                .put("variant_facts", new JSONArray())
                .put("visible_labels", new JSONArray())
                .put("photo_identity", photoIdentity);
    }

    private static JSONObject fact(String key, String value, int image, String side,
                                   String location, String role) throws Exception {
        return new JSONObject().put("key", key).put("value", value)
                .put("evidence_type", "printed_text").put("confidence", 98)
                .put("image_index", image).put("side", side)
                .put("location", location).put("semantic_role", role);
    }
}
