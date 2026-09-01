package com.flipcheck.nativebeta;

/** Regressions copied from the v0.89 Mewtwo, Topps box and camera field tests. */
public final class V090TcgCameraAndConfirmationRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        Models.Identification mewtwo = new Models.Identification();
        mewtwo.category = "collectible trading card";
        mewtwo.categoryKey = "pokemon_tcg_card";
        mewtwo.brand = "Pokémon";
        mewtwo.photoIdentityPhysicalBinding = true;
        mewtwo.photoIdentityConfidence = 97;
        mewtwo.photoIdentityFields.add("manufacturer=Pokémon");
        mewtwo.photoIdentityFields.add("set=Base Set");
        mewtwo.photoIdentityFields.add("subject=Mewtwo");
        mewtwo.photoIdentityFields.add("card_number=10/102");
        mewtwo.visibleLabels.add("10/102");
        mewtwo.localScan = new Models.LocalScan();
        mewtwo.localScan.textByImage.add("MEWTWO\n60 HP\n10/102");
        CollectibleCardIdentityPolicy.sanitizeObservation(mewtwo, mewtwo.localScan);
        require("10/102".equals(CollectibleCardIdentityPolicy.observedCardNumber(
                        mewtwo, mewtwo.localScan)),
                "Pokemon 10/102 must remain the collector/card number");
        require(mewtwo.photoIdentityFields.stream().noneMatch(
                        x -> x.equalsIgnoreCase("serial=10/102")),
                "Pokemon collector number must never become a serial");

        mewtwo.marketReady = true;
        mewtwo.model = "Pokémon Base Set Mewtwo 10/102 Holo";
        mewtwo.modelProof = "exact_catalog";
        mewtwo.visionIdentityReason = "Available view does not reliably distinguish "
                + "Shadowless from Unlimited printing.";
        ConfirmationIntegrityPolicy.enforce(mewtwo);
        require(!mewtwo.marketReady && mewtwo.nextPhotoRequest.contains("fronte"),
                "an unresolved value-relevant TCG printing must request a clearer identity-bearing front");

        Models.Identification sports = new Models.Identification();
        sports.category = "sports trading card";
        sports.categoryKey = "sports_trading_card";
        sports.photoIdentityPhysicalBinding = true;
        sports.photoIdentityFields.add("manufacturer=Panini");
        sports.visibleLabels.add("8/9");
        sports.localScan = new Models.LocalScan();
        sports.localScan.textByImage.add("8/9");
        CollectibleCardIdentityPolicy.sanitizeObservation(sports, sports.localScan);
        require(sports.photoIdentityFields.contains("serial=8/9"),
                "sports-card isolated 8/9 must remain a physical print run");

        Models.Identification box = new Models.Identification();
        box.category = "basketball trading-card sealed product";
        box.categoryKey = "collectible_trading_cards_basketball";
        box.photoIdentityComplete = true;
        box.photoIdentityPhysicalBinding = true;
        box.photoIdentityConfidence = 95;
        box.photoIdentityFields.add("manufacturer=Topps");
        box.photoIdentityFields.add("season=2025-26");
        box.photoIdentityFields.add("product_line=Topps Chrome");
        box.photoIdentityFields.add("series=Update Series");
        box.photoIdentityFields.add("sport=Basketball");
        box.photoIdentityFields.add("format=Hobby Box");
        box.localScan = new Models.LocalScan();
        box.localScan.textByImage.add("Ghrones\nChrome\nUPDATE SERIES\nHOBBY BOX");
        SealedProductIdentityPolicy.consolidateObservation(box, box.localScan);
        SealedProductIdentityPolicy.applyPhotoTupleFallback(box);
        require(SealedProductIdentityPolicy.hasPhotoTupleFamily(box)
                        && box.family.contains("Update Series")
                        && box.family.contains("Hobby Box"),
                "complete sealed tuple must survive a zero-candidate web result at family level");

        Models.Identification phone = new Models.Identification();
        phone.category = "smartphone";
        phone.categoryKey = "smartphone";
        UniversalRecognitionLadder.apply(phone);
        require(phone.nextPhotoRequest.contains("Info sul telefono"),
                "smartphone guidance must request the complete model screen");

        Models.Identification evidence = new Models.Identification();
        evidence.category = "collectible trading card";
        evidence.visualFacts.add("physical_serial_marking=none identified");
        EvidencePolicy.apply(evidence);
        require(evidence.observedEvidence.stream().noneMatch(
                        x -> x.toLowerCase().contains("none identified")),
                "negative internal sentinels must not be shown as observed evidence");

        String prompt = IdentificationPipelineV082.multimodalPromptForTest(
                mewtwo.localScan, "");
        require(prompt.contains("CARD FRACTION SEMANTICS")
                        && prompt.contains("card_number=10/102"),
                "multimodal contract must distinguish TCG collector numbers from serials");

        System.out.println("V090TcgCameraAndConfirmationRegressionTest: PASS");
    }
}
