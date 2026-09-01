package com.flipcheck.nativebeta;

import org.json.JSONObject;

/** Field regressions for Doncic, sealed box, remote and Holo preservation. */
public final class V099CalibratedIdentityRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Models.Identification doncic = new Models.Identification();
        doncic.category = "sports collectible card";
        doncic.categoryKey = "sports_collectible_card";
        doncic.brand = "Panini";
        doncic.family = "2018-19 Panini Prizm Basketball";
        doncic.photoIdentityPhysicalBinding = true;
        doncic.photoIdentityConfidence = 97;
        doncic.photoIdentityKind = "composite_markings";
        doncic.photoIdentityName = "2018-19 Panini Prizm Green Prizm Luka Doncic #280";
        doncic.photoViews.add("front");
        doncic.photoViews.add("back");
        doncic.photoIdentityFields.add("manufacturer=Panini");
        doncic.photoIdentityFields.add("set=2018-19 Panini Prizm Basketball");
        doncic.photoIdentityFields.add("subject=Luka Doncic");
        doncic.photoIdentityFields.add("team=Dallas Mavericks");
        doncic.photoIdentityFields.add("card_number=280");
        doncic.photoIdentityFields.add("parallel=Green Prizm");
        doncic.photoIdentityFields.add("rookie_card=present");
        doncic.photoIdentityFields.add("holo_or_foil=green_prismatic_foil");
        doncic.localScan = new Models.LocalScan();
        doncic.localScan.textByImage.add("PRIZM\nRC\nLUKA DONCIC\nNO. 280");
        doncic.localScan.textByImage.add("2018-19 PANINI - PRIZM BASKETBALL\nNO. 280");
        CollectibleCardIdentityPolicy.sanitizeObservation(doncic, doncic.localScan);

        Models.CandidateScore luka = candidate("Panini", "2018-19 Panini Prizm Basketball",
                "#280", "2018-19 Panini Prizm Green Prizm Luka Doncic #280");
        luka.totalScore = 68;
        require(CollectibleCardIdentityPolicy.canConfirm(doncic, luka),
                "front/back Doncic tuple must close despite a low generic layout score");
        CollectibleCardIdentityPolicy.confirm(doncic, luka);
        require(doncic.marketReady && doncic.model.contains("Green Prizm")
                        && doncic.model.contains("RC") && doncic.model.contains("280"),
                "confirmed sports-card identity must preserve parallel, rookie and number");

        Models.Identification box = new Models.Identification();
        box.category = "sealed basketball trading card product";
        box.categoryKey = "sealed_products";
        box.photoIdentityComplete = true;
        box.photoIdentityPhysicalBinding = true;
        box.photoIdentityConfidence = 96;
        box.photoIdentityKind = "composite_markings";
        box.photoIdentityName = "Topps 2025/26 Chrome Update Series Basketball 1-Autograph Box";
        box.photoIdentityCode = "2025/26";
        box.photoIdentityFields.add("manufacturer=Topps");
        box.photoIdentityFields.add("season/year=2025/26");
        box.photoIdentityFields.add("product_line=Topps Chrome Update Series");
        box.photoIdentityFields.add("sport=basketball");
        box.photoIdentityFields.add("configuration=1 autograph per box on average");
        box.photoIdentityFields.add("serial=2025/26");
        box.localScan = new Models.LocalScan();
        box.localScan.textByImage.add("TOPPS CHROME UPDATE SERIES\n2025/26\n1 AUTOGRAPH PER BOX\nNBA");
        SealedProductIdentityPolicy.consolidateObservation(box, box.localScan);
        require(box.photoIdentityFields.stream().noneMatch(
                        x -> x.equalsIgnoreCase("serial=2025/26"))
                        && box.photoIdentityCode.isEmpty(),
                "a season must never survive as a product serial or identity code");
        require(SealedProductIdentityPolicy.canConfirmPhotoTupleWithoutCandidate(box),
                "complete printed sealed-box tuple must close even when retrieval returns no candidate");
        SealedProductIdentityPolicy.confirmPhotoTupleWithoutCandidate(box);
        require(box.marketReady && box.model.contains("2025/26")
                        && box.model.toLowerCase().contains("autograph per box"),
                "sealed identity must preserve season, line and box configuration");

        Models.Identification mewtwo = new Models.Identification();
        mewtwo.category = "Pokemon trading card";
        mewtwo.categoryKey = "pokemon_tcg_card";
        mewtwo.brand = "Pokémon";
        mewtwo.family = "Base Set";
        mewtwo.model = "Mewtwo 10/102";
        mewtwo.photoIdentityFields.add("manufacturer=Pokémon");
        mewtwo.photoIdentityFields.add("set=Base Set");
        mewtwo.photoIdentityFields.add("subject=Mewtwo");
        mewtwo.photoIdentityFields.add("card_number=10/102");
        mewtwo.photoIdentityFields.add("holo=present");
        mewtwo.photoIdentityFields.add("first_edition_stamp=absent");
        mewtwo.photoIdentityFields.add("first_edition_stamp_area_clear=true");
        mewtwo.photoIdentityFields.add("illustration_frame_drop_shadow=absent");
        mewtwo.photoIdentityFields.add("copyright_layout=shadowless");
        mewtwo.photoIdentityFields.add("nintendo_copyright_99=present");
        CollectibleCardIdentityPolicy.normalizeConfirmedIdentity(mewtwo, null);
        require(mewtwo.model.contains("Shadowless") && mewtwo.model.contains("Holo"),
                "Mewtwo final identity must preserve both printing and Holo finish");

        Models.Identification remote = new Models.Identification();
        remote.category = "TV remote control";
        remote.categoryKey = "remote_control";
        remote.photoIdentityPhysicalBinding = true;
        remote.photoIdentityConfidence = 74;
        remote.visibleLabels.add("NETFLIX");
        remote.visibleLabels.add("HOME");
        Models.Usage remoteUsage = new Models.Usage();
        remoteUsage.requests = 1;
        remoteUsage.webCalls = 1;
        remoteUsage.costUsd = 0.011;
        require(!BorderlineIdentityAdjudicator.eligible(remote, remoteUsage),
                "generic remote controls must not receive a paid auto-confirmation without a physical code");

        Models.Identification borderline = doncic;
        borderline.marketReady = false;
        borderline.candidates.add(luka);
        Models.Usage usage = new Models.Usage();
        usage.requests = 1;
        usage.webCalls = 1;
        usage.costUsd = 0.011;
        require(BorderlineIdentityAdjudicator.eligible(borderline, usage),
                "evidence-rich exact-reference near misses may use the cheap no-web adjudicator");
        OpenAiClient.Response response = new OpenAiClient.Response();
        response.payload = new JSONObject()
                .put("supported", true).put("same_entity", true)
                .put("contradiction", false).put("identity_confidence", 96)
                .put("normalized_identity",
                        "Panini 2018-19 Prizm Basketball Luka Doncic #280 Green Prizm RC Dallas Mavericks")
                .put("reason", "all physical fields and the exact retrieved reference agree");
        require(BorderlineIdentityAdjudicator.apply(borderline, response)
                        && borderline.marketReady,
                "successful bounded adjudication must promote only a deterministically eligible identity");

        System.out.println("V099CalibratedIdentityRegressionTest: PASS");
    }

    private static Models.CandidateScore candidate(String brand, String family,
                                                    String model, String probable) {
        Models.CandidateScore c = new Models.CandidateScore();
        c.brand = brand;
        c.family = family;
        c.model = model;
        c.probableReference = probable;
        c.probableReferenceConfidence = 97;
        c.textScore = 99;
        c.layoutScore = 72;
        c.webScore = 96;
        c.evidence = probable + " checklist card number 280";
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
