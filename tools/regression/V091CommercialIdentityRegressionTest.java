package com.flipcheck.nativebeta;

/** Regressions from the v0.90 Topps, Panini, Mewtwo and S24 Ultra field audit. */
public final class V091CommercialIdentityRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        Models.Identification topps = sealed("sealed basketball trading-card product",
                "Topps", "2025-26", "Topps Chrome", "Update Series", "Hobby Box");
        Models.CandidateScore toppsCandidate = candidate("Topps",
                "Chrome Update Series Basketball", "",
                "Topps Topps Chrome Update Series Basketball Hobby Box", 79);
        require(SealedProductIdentityPolicy.isSealedRetailProduct(topps),
                "hyphenated sealed-product category must never route as a loose card");
        require(SealedProductIdentityPolicy.canConfirmCommercialSku(topps, toppsCandidate),
                "complete Topps front tuple plus grounded SKU must close the box");
        SealedProductIdentityPolicy.confirmCommercialSku(topps, toppsCandidate);
        require(topps.marketReady
                        && topps.model.equals("2025-26 Topps Chrome Update Series Basketball Hobby Box"),
                "Topps hobby box must keep the complete printed tuple without duplication");

        Models.Identification panini = sealed("sealed basketball trading-card box",
                "Panini", "2023-24", "One and One Basketball", "", "Hobby Box");
        Models.CandidateScore paniniCandidate = candidate("Panini",
                "One and One Basketball", "",
                "Panini Panini 2023-24 One and One Basketball Hobby Box", 99);
        require(SealedProductIdentityPolicy.canConfirmCommercialSku(panini, paniniCandidate),
                "complete Panini One and One front tuple must close the box");
        SealedProductIdentityPolicy.confirmCommercialSku(panini, paniniCandidate);
        require(panini.model.equals("2023-24 One and One Basketball Hobby Box"),
                "Panini must not collapse the product to the generic Hobby Box format");

        Models.Identification mewtwo = new Models.Identification();
        mewtwo.category = "collectible trading card";
        mewtwo.categoryKey = "pokemon_tcg_card";
        mewtwo.brand = "Pokémon";
        mewtwo.family = "Base Set";
        mewtwo.photoIdentityComplete = true;
        mewtwo.photoIdentityPhysicalBinding = true;
        mewtwo.photoIdentityConfidence = 97;
        mewtwo.photoIdentityKind = "composite_markings";
        mewtwo.photoIdentityName = "Pokémon Base Set Mewtwo Holo 10/102";
        mewtwo.photoViews.add("front view");
        mewtwo.photoIdentityFields.add("manufacturer=Pokémon");
        mewtwo.photoIdentityFields.add("set=Base Set");
        mewtwo.photoIdentityFields.add("subject=Mewtwo");
        mewtwo.photoIdentityFields.add("card_number=10/102");
        mewtwo.localScan = new Models.LocalScan();
        mewtwo.localScan.textByImage.add("MEWTWO\n60 HP\n10/102");
        Models.CandidateScore mewtwoCandidate = candidate("Pokémon", "Base Set",
                "10/102", "Mewtwo Base Set 10/102 Holo", 99);
        mewtwoCandidate.totalScore = 96;
        mewtwoCandidate.candidateFacts.add("source_exact_reference=true");
        mewtwoCandidate.candidateFacts.add("source_identity_confidence=98");
        mewtwoCandidate.evidence = "Base Set Mewtwo is card 10/102";
        require(CollectibleCardIdentityPolicy.variantUnresolved(mewtwo, mewtwoCandidate),
                "Base Set printing must remain open until the front layout resolves it");
        mewtwo.photoIdentityFields.add("first_edition_stamp=absent");
        mewtwo.photoIdentityFields.add("first_edition_stamp_area_clear=true");
        mewtwo.photoIdentityFields.add("illustration_frame_drop_shadow=absent");
        mewtwo.photoIdentityFields.add("copyright_layout=shadowless");
        mewtwo.photoIdentityFields.add("nintendo_copyright_99=present");
        mewtwo.photoIdentityFields.add("physical_printing=Shadowless");
        require(!CollectibleCardIdentityPolicy.variantUnresolved(mewtwo, mewtwoCandidate),
                "physical front-layout proof must resolve Shadowless without a back photo");
        mewtwoCandidate.totalScore = 68;
        require(CollectibleCardIdentityPolicy.canConfirm(mewtwo, mewtwoCandidate),
                "a complete Pokemon front must close despite a low generic tournament score");
        mewtwo.marketReady = true;
        mewtwo.model = "10/102";
        mewtwo.candidates.add(mewtwoCandidate);
        ConfirmationIntegrityPolicy.enforce(mewtwo);
        require(mewtwo.marketReady && mewtwo.model.contains("Mewtwo")
                        && mewtwo.model.contains("Shadowless"),
                "confirmed TCG model must include subject and physical printing, not only 10/102");

        Models.Identification phone = new Models.Identification();
        phone.category = "smartphone";
        phone.categoryKey = "smartphone";
        phone.brand = "Samsung";
        phone.visibleLabels.add("SAMSUNG");
        phone.visibleLabels.add("Galaxy S24 Ultra");
        phone.visibleLabels.add("SM-S928...");
        Models.CandidateScore s24 = candidate("Samsung", "Galaxy S24 Ultra", "",
                "SM-S928B", 67);
        s24.layoutScore = 55;
        s24.textScore = 74;
        s24.webScore = 88;
        s24.candidateFacts.add("visual_match_confidence=94");
        require(CommercialIdentityPolicy.canConfirmPhoneFamily(phone, s24),
                "regional suffix must not block the price-equivalent S24 Ultra identity");
        CommercialIdentityPolicy.confirmPhoneFamily(phone, s24);
        require(phone.marketReady && "Samsung Galaxy S24 Ultra".equals(phone.model),
                "phone must close at commercial hardware-family level");

        System.out.println("V091CommercialIdentityRegressionTest: PASS");
    }

    private static Models.Identification sealed(String category, String brand,
                                                 String season, String line,
                                                 String series, String format) {
        Models.Identification id = new Models.Identification();
        id.category = category;
        id.categoryKey = category.replace(' ', '_');
        id.brand = brand;
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityComplete = true;
        id.photoIdentityConfidence = 95;
        id.photoIdentityKind = "composite_markings";
        id.photoIdentityName = brand + " " + season + " " + line + " "
                + series + " " + format;
        id.photoIdentityFields.add("manufacturer=" + brand);
        id.photoIdentityFields.add("season=" + season);
        id.photoIdentityFields.add("product_line=" + line);
        if (!series.isEmpty()) {
            id.photoIdentityFields.add("series=" + series);
        }
        id.photoIdentityFields.add("sport=Basketball");
        id.photoIdentityFields.add("format=" + format);
        return id;
    }

    private static Models.CandidateScore candidate(String brand, String family,
                                                    String model, String probable,
                                                    int probableConfidence) {
        Models.CandidateScore c = new Models.CandidateScore();
        c.brand = brand;
        c.family = family;
        c.model = model;
        c.probableReference = probable;
        c.probableReferenceConfidence = probableConfidence;
        c.totalScore = 86;
        c.textScore = 91;
        c.layoutScore = 88;
        c.webScore = 84;
        c.candidateFacts.add("source_grounded=true");
        c.candidateFacts.add("same_entity_role=true");
        c.candidateFacts.add("relationship_only=false");
        c.candidateFacts.add("disproof_passed=true");
        return c;
    }
}
