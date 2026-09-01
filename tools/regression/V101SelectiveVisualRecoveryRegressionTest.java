package com.flipcheck.nativebeta;

import org.json.JSONArray;
import org.json.JSONObject;

/** Real-device omissions recovered by one bounded no-Web visual pass. */
public final class V101SelectiveVisualRecoveryRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        recoverMachampFields();
        recoverSealedBoxTuple();
        preserveSportsSubject();
        rejectRemoteRecovery();
        System.out.println("V101SelectiveVisualRecoveryRegressionTest: PASS");
    }

    private static void recoverMachampFields() throws Exception {
        Models.Identification id = new Models.Identification();
        id.category = "Pokémon trading card";
        id.categoryKey = "pokemon_tcg_card";
        id.photoViews.add("front");
        id.visibleLabels.add("Pokémon");
        id.visibleLabels.add("Machamp");
        id.visibleLabels.add("100 HP");
        id.visibleLabels.add("Seismic Toss");
        id.visualFacts.add("holo=true");
        id.visualFacts.add("first_edition_stamp=present");
        id.localScan = new Models.LocalScan();
        id.localScan.textByImage.add("MACHAMP 100 HP\n8/102\n1ST EDITION");
        Models.Usage usage = firstUsage();
        require(PhysicalIdentityRecovery.eligible(id, usage),
                "a rich incomplete card front must receive the bounded visual recovery");

        OpenAiClient.Response response = recovery("loose_card",
                "Pokémon Base Set Machamp 8/102 Holo 1st Edition Shadowless",
                new String[]{"manufacturer=Pokémon", "set=Base Set", "subject=Machamp",
                        "card_number=8/102", "holo=present",
                        "physical_printing=1st Edition Shadowless",
                        "first_edition_stamp=present", "first_edition_stamp_position=left_below_artwork"});
        require(PhysicalIdentityRecovery.apply(id, response), "card recovery response must merge");
        CollectibleCardIdentityPolicy.sanitizeObservation(id, id.localScan);
        Models.CandidateScore c = candidate("Pokémon", "Base Set", "Machamp 8/102",
                "Pokémon Base Set Machamp 8/102");
        id.brand = "Pokémon";
        id.family = c.family;
        id.model = c.model;
        CollectibleCardIdentityPolicy.prepareForCandidateConfirmation(id, c);
        require(CollectibleCardIdentityPolicy.canConfirm(id, c),
                "recovered Machamp fields must close the exact catalog card");
    }

    private static void recoverSealedBoxTuple() throws Exception {
        Models.Identification id = new Models.Identification();
        id.category = "sealed basketball trading-card product";
        id.categoryKey = "sealed_products";
        id.visibleLabels.add("2025/26");
        id.visibleLabels.add("BASKETBALL");
        id.visibleLabels.add("1 AUTOGRAPH PER BOX");
        id.visualFacts.add("season=2025/26");
        id.visualFacts.add("sport=basketball");
        id.visualFacts.add("sealed_format=hobby");
        id.visualFacts.add("autograph_callout=1 autograph per box");
        id.localScan = new Models.LocalScan();
        id.localScan.textByImage.add("2025/26\nBASKETBALL\n1 AUTOGRAPH PER BOX");
        require(PhysicalIdentityRecovery.eligible(id, firstUsage()),
                "a rich incomplete sealed box must receive visual recovery");

        OpenAiClient.Response response = recovery("sealed_box",
                "Topps 2025/26 Chrome Update Series Basketball Hobby Box",
                new String[]{"manufacturer=Topps", "season=2025/26",
                        "product_line=Chrome Update Series", "sport=basketball",
                        "format=Hobby Box", "configuration=1 autograph per box"});
        require(PhysicalIdentityRecovery.apply(id, response), "box recovery response must merge");
        SealedProductIdentityPolicy.consolidateObservation(id, id.localScan);
        require(SealedProductIdentityPolicy.canConfirmPhotoTupleWithoutCandidate(id),
                "recovered complete box tuple must close without a web candidate");
    }

    private static void preserveSportsSubject() {
        Models.Identification id = new Models.Identification();
        id.category = "sports collectible card";
        id.categoryKey = "sports_collectible_card";
        id.family = "2018-19 Panini Prizm Basketball";
        id.model = "Green Prizm #280";
        id.photoIdentityFields.add("player=Luka Doncic");
        id.photoIdentityFields.add("parallel=Green Prizm");
        id.photoIdentityFields.add("card_number=280");
        id.photoIdentityFields.add("rookie_card=present");
        CollectibleCardIdentityPolicy.normalizeConfirmedIdentity(id, null);
        require(id.model.contains("Luka Doncic") && id.model.contains("Green Prizm")
                        && id.model.contains("#280") && id.model.contains("RC"),
                "sports-card identity must never discard the observed player");
    }

    private static void rejectRemoteRecovery() {
        Models.Identification id = new Models.Identification();
        id.category = "Television remote control";
        id.categoryKey = "remote_control";
        id.visibleLabels.add("NETFLIX");
        id.visibleLabels.add("HOME");
        id.visibleLabels.add("INFO");
        id.visibleLabels.add("SOURCES");
        require(!PhysicalIdentityRecovery.eligible(id, firstUsage()),
                "a generic remote without a physical model code must not spend a second call");
    }

    private static Models.Usage firstUsage() {
        Models.Usage usage = new Models.Usage();
        usage.requests = 1;
        usage.webCalls = 1;
        usage.costUsd = 0.012;
        return usage;
    }

    private static OpenAiClient.Response recovery(String category, String name, String[] fields)
            throws Exception {
        OpenAiClient.Response response = new OpenAiClient.Response();
        response.payload = new JSONObject()
                .put("applicable", true).put("same_foreground_object", true)
                .put("physical_binding", true).put("overlay_or_watermark", false)
                .put("complete", true).put("category_key", category)
                .put("canonical_name", name).put("confidence", 97)
                .put("fields", new JSONArray(fields))
                .put("observed_labels", new JSONArray())
                .put("contradiction", "");
        return response;
    }

    private static Models.CandidateScore candidate(String brand, String family,
                                                    String model, String probable) {
        Models.CandidateScore c = new Models.CandidateScore();
        c.brand = brand;
        c.family = family;
        c.model = model;
        c.probableReference = probable;
        c.probableReferenceConfidence = 98;
        c.textScore = 99;
        c.webScore = 96;
        c.candidateFacts.add("source_grounded=true");
        c.candidateFacts.add("same_entity_role=true");
        c.candidateFacts.add("relationship_only=false");
        c.candidateFacts.add("disproof_passed=true");
        c.candidateFacts.add("source_exact_reference=true");
        c.candidateFacts.add("exact_reference_complete=true");
        c.candidateFacts.add("source_identity_confidence=98");
        return c;
    }
}
