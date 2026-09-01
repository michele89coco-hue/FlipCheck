package com.flipcheck.nativebeta;

/** Reproduces the real v0.99 device failures without card- or SKU-specific rules. */
public final class V100RealDeviceIdentityRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        loosePokemonCardMustOverrideSealedCategory();
        sportsVariantFactsMustBecomePhysicalIdentity();
        completeFrontBoxMustCloseWithoutWebCandidate();
        genericRemoteMustRemainGeneric();
        System.out.println("V100RealDeviceIdentityRegressionTest: PASS");
    }

    private static void loosePokemonCardMustOverrideSealedCategory() {
        Models.Identification id = new Models.Identification();
        id.category = "Pokémon sealed products";
        id.categoryKey = "sealed_products";
        id.brand = "Pokémon";
        id.photoViews.add("front full card");
        id.visibleLabels.add("Pokémon");
        id.visibleLabels.add("Basic Pokémon");
        id.visibleLabels.add("Mewtwo");
        id.visibleLabels.add("60 HP");
        id.visualFacts.add("holo=present");
        id.visualFacts.add("first_edition_stamp=absent");
        id.visualFacts.add("first_edition_stamp_area_clear=true");
        id.visualFacts.add("illustration_frame_drop_shadow=absent");
        id.visualFacts.add("nintendo_copyright_99=present");
        id.visualFacts.add("copyright_layout=shadowless");
        id.visualFacts.add("physical_printing=Shadowless");
        id.localScan = new Models.LocalScan();
        id.localScan.textByImage.add("MEWTWO 60 HP\nPsychic\nBarrier\n10/102");

        CategoryFactPolicy.apply(id);
        PhysicalIdentityConsolidator.apply(id);
        require(CollectibleCardIdentityPolicy.isCard(id)
                        && "pokemon_tcg_card".equals(id.categoryKey),
                "a loose Pokémon card must override an erroneous sealed-products category");
        CollectibleCardIdentityPolicy.sanitizeObservation(id, id.localScan);

        Models.CandidateScore c = cardCandidate("Pokémon", "Base Set", "Mewtwo 10/102",
                "Pokémon Base Set Mewtwo 10/102");
        id.family = c.family;
        id.model = c.model;
        CollectibleCardIdentityPolicy.prepareForCandidateConfirmation(id, c);
        require(CollectibleCardIdentityPolicy.canConfirm(id, c),
                "the exact Mewtwo front must close after physical category recovery");
        CollectibleCardIdentityPolicy.confirm(id, c);
        require(id.model.contains("Shadowless") && id.model.contains("Holo"),
                "Mewtwo final identity must retain Shadowless and Holo");
    }

    private static void sportsVariantFactsMustBecomePhysicalIdentity() {
        Models.Identification id = new Models.Identification();
        id.category = "sports collectible card";
        id.categoryKey = "sports_collectible_card";
        id.brand = "Panini";
        id.photoViews.add("front");
        id.photoViews.add("back");
        id.visualFacts.add("player=Luka Doncic");
        id.visualFacts.add("team=Dallas Mavericks");
        id.visualFacts.add("season=2018-19");
        id.visualFacts.add("rookie_card=present");
        id.visualFacts.add("parallel=Green Prizm");
        id.visualFacts.add("card_number=280");
        id.visualFacts.add("holo_or_foil=holo");
        id.localScan = new Models.LocalScan();
        id.localScan.textByImage.add("PRIZM RC LUKA DONCIC DALLAS MAVERICKS");
        id.localScan.textByImage.add("2018-19 PANINI PRIZM BASKETBALL NO. 280");

        CategoryFactPolicy.apply(id);
        PhysicalIdentityConsolidator.apply(id);
        CollectibleCardIdentityPolicy.sanitizeObservation(id, id.localScan);
        Models.CandidateScore c = cardCandidate("Panini", "2018-19 Panini Prizm Basketball",
                "#280", "2018-19 Panini Prizm Green Prizm Luka Doncic #280");
        id.family = c.family;
        id.model = c.model;
        CollectibleCardIdentityPolicy.prepareForCandidateConfirmation(id, c);
        require(id.photoIdentityComplete && CollectibleCardIdentityPolicy.canConfirm(id, c),
                "front/back sports variant facts must become a confirmable physical tuple");
        CollectibleCardIdentityPolicy.confirm(id, c);
        require(id.model.contains("Green Prizm") && id.model.contains("RC"),
                "sports identity must retain physical parallel and rookie marker");
    }

    private static void completeFrontBoxMustCloseWithoutWebCandidate() {
        Models.Identification id = new Models.Identification();
        id.category = "sealed basketball trading card product";
        id.categoryKey = "sealed_products";
        id.photoIdentityComplete = true;
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityConfidence = 95;
        id.photoIdentityKind = "composite_markings";
        id.photoIdentityName = "Topps 2025/26 Chrome Update Series Basketball 1 Autograph In Every Box";
        id.photoIdentityFields.add("manufacturer=Topps");
        id.photoIdentityFields.add("season=2025/26");
        id.photoIdentityFields.add("product_line=Chrome Update Series");
        id.photoIdentityFields.add("sport=basketball");
        id.photoIdentityFields.add("configuration=1 Autograph In Every Box");
        id.localScan = new Models.LocalScan();
        id.localScan.textByImage.add("CHROME UPDATE SERIES");
        SealedProductIdentityPolicy.consolidateObservation(id, id.localScan);
        require(SealedProductIdentityPolicy.canConfirmPhotoTupleWithoutCandidate(id),
                "a complete physically bound box tuple needs only one local corroboration");
        SealedProductIdentityPolicy.confirmPhotoTupleWithoutCandidate(id);
        require(id.marketReady && id.model.contains("2025/26")
                        && id.model.toLowerCase().contains("every box"),
                "the printed box configuration must survive in the final identity");
    }

    private static void genericRemoteMustRemainGeneric() {
        Models.Identification id = new Models.Identification();
        id.category = "TV remote control";
        id.categoryKey = "remote_control";
        id.visibleLabels.add("NETFLIX");
        id.visualFacts.add("button=HOME");
        CategoryFactPolicy.apply(id);
        PhysicalIdentityConsolidator.apply(id);
        require("remote_control".equals(id.categoryKey)
                        && !CollectibleCardIdentityPolicy.isCard(id),
                "generic controls must never be reclassified as cards");
    }

    private static Models.CandidateScore cardCandidate(String brand, String family,
                                                        String model, String probable) {
        Models.CandidateScore c = new Models.CandidateScore();
        c.brand = brand;
        c.family = family;
        c.model = model;
        c.probableReference = probable;
        c.probableReferenceConfidence = 98;
        c.textScore = 99;
        c.layoutScore = 72;
        c.webScore = 96;
        c.evidence = probable;
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
