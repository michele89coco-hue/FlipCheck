package com.flipcheck.nativebeta;

import java.util.Arrays;

/** Regressions copied from the Yamal, Cooper Flagg and Topps field tests. */
public final class V088SerialCompositeAndTitleRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        Models.Identification yamal = card("8/9");
        yamal.photoIdentityName = "Panini Obsidian Soccer Lamine Yamal No. 8, serial";
        yamal.photoIdentityFields.add("card_number=8");
        yamal.visibleLabels.add("NO. 8");
        CollectibleCardIdentityPolicy.sanitizeObservation(yamal, yamal.localScan);
        require(yamal.photoIdentityFields.contains("serial=8/9")
                        && yamal.photoIdentityFields.contains(
                        "serial_binding=physical_card_surface"),
                "an isolated 8/9 on the card must be bound as its physical print run");
        require(yamal.photoIdentityFields.contains("card_number=8"),
                "an explicitly printed No. 8 must remain the card number");

        Models.Identification cooper = card("25/25");
        cooper.photoIdentityName = "Bowman U NOW Cooper Flagg basketball card";
        cooper.photoIdentityFields.add("card_number=L25");
        cooper.controlLabels.add("25/25");
        CollectibleCardIdentityPolicy.sanitizeObservation(cooper, cooper.localScan);
        require(cooper.photoIdentityFields.contains("serial=25/25"),
                "a physical 25/25 must be retained even if Vision called it a control");
        require(cooper.photoIdentityFields.stream().noneMatch(
                        x -> x.equalsIgnoreCase("card_number=L25")),
                "L25 synthesized from 25/25 must be removed");

        Models.Identification season = card("2025/26");
        season.photoIdentityFields.add("season=2025/26");
        CollectibleCardIdentityPolicy.sanitizeObservation(season, season.localScan);
        require(season.photoIdentityFields.stream().noneMatch(
                        x -> x.toLowerCase().startsWith("serial=")),
                "a four-digit season range must not become a serial");

        Models.Identification birth = card("Born: 8/23/78");
        CollectibleCardIdentityPolicy.sanitizeObservation(birth, birth.localScan);
        require(birth.photoIdentityFields.stream().noneMatch(
                        x -> x.toLowerCase().startsWith("serial=")),
                "the first two components of a birth date must not become a serial");

        Models.Identification box = new Models.Identification();
        box.category = "sealed sports trading-card box";
        box.categoryKey = "sports_trading_cards_sealed_box";
        box.photoIdentityComplete = true;
        box.photoIdentityPhysicalBinding = true;
        box.photoIdentityConfidence = 96;
        box.photoIdentityFields.add("manufacturer=Topps");
        box.photoIdentityFields.add("season=2025/26");
        box.photoIdentityFields.add("product_line=Chrome Update Series");
        box.photoIdentityFields.add("category=Basketball");
        box.photoIdentityFields.add("format=Hobby Box");
        Models.LocalScan noisyTopps = new Models.LocalScan();
        noisyTopps.textByImage.add("Ghrones\nSanionio\nChrome");
        box.localScan = noisyTopps;
        SealedProductIdentityPolicy.consolidateObservation(box, noisyTopps);
        require("Topps".equals(box.brand)
                        && SealedProductIdentityPolicy.hasBoundManufacturer(box),
                "a 96% complete sealed tuple may recover a manufacturer missed by local OCR");
        require(IdentificationPipelineV082.firstQueryIsBrandNeutral(box,
                        Arrays.asList("Topps Chrome Update Series Basketball Hobby Box")),
                "the safely bound manufacturer must pass the first-query brand gate");

        Models.Identification weakBox = new Models.Identification();
        weakBox.category = box.category;
        weakBox.categoryKey = box.categoryKey;
        weakBox.photoIdentityComplete = true;
        weakBox.photoIdentityPhysicalBinding = true;
        weakBox.photoIdentityConfidence = 89;
        weakBox.photoIdentityFields.addAll(box.photoIdentityFields);
        SealedProductIdentityPolicy.consolidateObservation(weakBox, noisyTopps);
        require(weakBox.brand.isEmpty(),
                "a low-confidence composite must not promote a manufacturer absent from OCR");

        Models.Identification title = new Models.Identification();
        title.marketReady = true;
        title.brand = "Panini";
        title.family = "2024-25 Panini Obsidian Soccer Supernova";
        title.model = "Panini 2024-25 Obsidian Soccer Supernova No. 8 Lamine Yamal FC Barcelona";
        require(EvidencePolicy.publicTitle(title).equals(title.model),
                "the verified public title must not repeat brand/family already in the model");

        System.out.println("V088SerialCompositeAndTitleRegressionTest: PASS");
    }

    private static Models.Identification card(String ocr) {
        Models.Identification id = new Models.Identification();
        id.category = "sports trading card";
        id.categoryKey = "sports_trading_card";
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityConfidence = 96;
        id.photoViews.add("front");
        id.photoViews.add("reverse");
        id.photoIdentityFields.add("manufacturer=Panini");
        id.photoIdentityFields.add("set=Test Set");
        id.photoIdentityFields.add("player=Test Player");
        Models.LocalScan local = new Models.LocalScan();
        local.textByImage.add(ocr);
        id.localScan = local;
        return id;
    }
}
