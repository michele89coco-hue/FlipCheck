package com.flipcheck.nativebeta;

import java.util.Arrays;

/** Regression cases copied from the v0.85 Topps box and Orbit field tests. */
public final class V086SealedProductRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        Models.Identification box = new Models.Identification();
        box.category = "sealed sports trading card box";
        box.categoryKey = "sports_trading_card_box";
        box.photoIdentityComplete = true;
        box.photoIdentityPhysicalBinding = true;
        box.photoIdentityOverlayOrWatermark = false;
        box.photoIdentityConfidence = 93;
        box.photoIdentityKind = "composite_markings";
        box.photoIdentityName = "Topps 2025-26 Chrome Basketball Hobby Box";
        box.photoIdentityFields.add("manufacturer=Topps");
        box.photoIdentityFields.add("season=2025-26");
        box.photoIdentityFields.add("product line=Topps Chrome");
        box.photoIdentityFields.add("sport=Basketball");
        box.photoIdentityFields.add("format=Hobby Box");
        box.photoIdentityFields.add("pack configuration=20 packs per box, 4 cards per pack");
        Models.LocalScan scan = new Models.LocalScan();
        scan.textByImage.add("TOPPS CHROME\nBASKETBALL\n2025-26\n1 AUTOGRAPH EVERY BOX!\n20 PACKS PER BOX\n4 CARDS PER PACK");
        box.localScan = scan;

        SealedProductIdentityPolicy.consolidateObservation(box, scan);
        require("Topps".equals(box.brand),
                "the physically printed manufacturer must survive as the box brand");
        require(BrandBlindPolicy.trustedObservedBrand(box),
                "a local-OCR-corroborated sealed-product manufacturer must be trusted");
        require(IdentificationPipelineV082.firstQueryIsBrandNeutral(box,
                        Arrays.asList("Topps Chrome Basketball 2025-26 hobby box")),
                "an observed sealed-product brand must not trigger the guessed-brand fail-closed gate");
        require(box.searchableLabels.stream().anyMatch(x -> x.equalsIgnoreCase("Topps Chrome")),
                "the observed product line must reach the bounded search context");

        Models.Identification loose = new Models.Identification();
        loose.category = "remote control";
        loose.categoryKey = "remote_control";
        loose.photoIdentityComplete = true;
        loose.photoIdentityPhysicalBinding = true;
        loose.photoIdentityConfidence = 95;
        loose.photoIdentityName = "Sony remote";
        loose.photoIdentityFields.add("manufacturer=Sony");
        Models.LocalScan genericBattery = new Models.LocalScan();
        genericBattery.textByImage.add("ALKALINE LR6 AA");
        SealedProductIdentityPolicy.consolidateObservation(loose, genericBattery);
        require(loose.brand.isEmpty(),
                "sealed-box policy must not promote a brand on a loose remote");

        Models.Identification controller = new Models.Identification();
        controller.category = "irrigation controller";
        controller.categoryKey = "irrigation_controller";
        controller.spatialSignature.add("six station sliders");
        Models.CandidateScore orbit = new Models.CandidateScore();
        orbit.brand = "Orbit";
        orbit.family = "WaterMaster";
        orbit.model = "57004";
        orbit.probableReferenceConfidence = 76;
        orbit.candidateFacts.add("source_exact_reference=true");
        orbit.candidateFacts.add("station_count=6");
        orbit.candidateFacts.add("visual_reference_checked=true");
        orbit.candidateFacts.add("visual_match_confidence=94");
        orbit.candidateFacts.add("disproof_passed=true");
        orbit.candidateFacts.add("exact_identity_supported=false");
        ReferenceScopePolicy.enforceCandidateScope(controller, orbit);
        require(orbit.model.isEmpty() && "57004".equals(orbit.probableReference),
                "unsupported Orbit reference must remain probable, not exact");

        String prompt = IdentificationPipelineV082.multimodalPromptForTest(scan, "");
        require(prompt.contains("SEALED RETAIL PRODUCT INTEGRITY")
                        && prompt.contains("factory-sealed retail/hobby product"),
                "the multimodal contract must distinguish foreground sealed packaging");

        System.out.println("V086SealedProductRegressionTest: PASS");
    }
}
