package com.flipcheck.nativebeta;

/** Regressions copied from the v0.91 field tests. */
public final class V092VisualCueAndSealedClosureRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        Models.Identification mewtwo = baseSetMewtwo();
        Models.CandidateScore pokemon = candidate("Pokémon", "Base Set", "10/102",
                "Mewtwo Base Set 10/102 Holo Unlimited", 98);
        pokemon.totalScore = 96;
        pokemon.candidateFacts.add("source_exact_reference=true");
        pokemon.candidateFacts.add("source_identity_confidence=98");

        mewtwo.photoIdentityFields.add("physical_printing=Unlimited");
        require(CollectibleCardIdentityPolicy.variantUnresolved(mewtwo, pokemon),
                "a naked model edition label must never close Base Set printing");

        mewtwo.photoIdentityFields.add("first_edition_stamp=absent");
        mewtwo.photoIdentityFields.add("first_edition_stamp_area_clear=true");
        mewtwo.photoIdentityFields.add("illustration_frame_drop_shadow=absent");
        mewtwo.photoIdentityFields.add("copyright_layout=shadowless");
        mewtwo.photoIdentityFields.add("nintendo_copyright_99=present");
        require(!CollectibleCardIdentityPolicy.variantUnresolved(mewtwo, pokemon),
                "two or more coherent physical front cues must resolve Shadowless");
        require(CollectibleCardIdentityPolicy.canConfirm(mewtwo, pokemon),
                "Shadowless Mewtwo must close from a clear front photo");
        mewtwo.marketReady = true;
        mewtwo.model = "Mewtwo Base Set 10/102 Holo Unlimited";
        mewtwo.candidates.add(pokemon);
        ConfirmationIntegrityPolicy.enforce(mewtwo);
        require(mewtwo.model.contains("Shadowless") && !mewtwo.model.contains("Unlimited"),
                "confirmed title must replace the wrong edition, not append to it");

        Models.Identification panini = new Models.Identification();
        panini.category = "sealed basketball trading-card box";
        panini.categoryKey = "sealed_basketball_trading_card_box";
        panini.brand = "Panini";
        panini.brandEvidence = "visible_brand_text";
        panini.photoIdentityPhysicalBinding = true;
        panini.photoIdentityComplete = false;
        panini.photoIdentityConfidence = 94;
        panini.photoIdentityName = "Panini One and One Basketball";
        panini.visibleLabels.add("PANINI");
        panini.visibleLabels.add("2023-24 NBA TRADING CARDS");
        panini.visibleLabels.add("ONE AND ONE");
        panini.photoIdentityFields.add("manufacturer=Panini");
        panini.photoIdentityFields.add("season=2023-24");
        panini.photoIdentityFields.add("product_line=One and One Basketball");
        Models.CandidateScore oneAndOne = candidate("Panini", "One and One Basketball", "",
                "Panini 2023-24 Panini One and One Basketball Hobby Box", 98);
        require(SealedProductIdentityPolicy.canConfirmCommercialSku(panini, oneAndOne),
                "a grounded front tuple must close even when Vision omitted complete/format");
        SealedProductIdentityPolicy.confirmCommercialSku(panini, oneAndOne);
        require(panini.marketReady && panini.model.contains("Hobby Box"),
                "Panini One and One must close at the commercial hobby-box level");
        require(panini.model.indexOf("Panini", 1) < 0,
                "brand duplication must be removed even when separated by the season");

        Models.Identification remote = new Models.Identification();
        remote.category = "remote control";
        remote.categoryKey = "remote_control";
        remote.brand = "Philips";
        Models.CandidateScore ykf = candidate("Philips", "TV remote control", "",
                "Philips YKF400-002", 64);
        require(!SealedProductIdentityPolicy.canConfirmCommercialSku(remote, ykf),
                "remote control must remain outside sealed-product closure");

        Models.Identification topps = new Models.Identification();
        topps.category = "sealed products";
        topps.categoryKey = "sealed_trading_card_product";
        topps.brand = "Topps";
        topps.brandEvidence = "physical_package_identity";
        topps.brandRoleConfidence = 98;
        topps.photoIdentityPhysicalBinding = true;
        topps.photoIdentityComplete = true;
        topps.photoIdentityConfidence = 98;
        topps.photoIdentityName = "Topps 2025-26 Chrome Updates Basketball Hobby Box";
        topps.photoIdentityFields.add("manufacturer=Topps");
        topps.photoIdentityFields.add("season=2025-26");
        topps.photoIdentityFields.add("product_line=Chrome Updates");
        topps.photoIdentityFields.add("sport=Basketball");
        topps.photoIdentityFields.add("format=Hobby Box");
        topps.localScan = new Models.LocalScan();
        topps.localScan.textByImage.add("TOPPS CHROME UPDATE SERIES BASKETBALL 2025/26 HOBBY BOX");
        require(SealedProductIdentityPolicy.canConfirmPhotoTupleWithoutCandidate(topps),
                "complete Topps front tuple must survive an empty web candidate list");
        SealedProductIdentityPolicy.confirmPhotoTupleWithoutCandidate(topps);
        require(topps.marketReady && topps.model.contains("2025-26")
                        && topps.model.contains("Hobby Box"),
                "complete front tuple must produce a commercial sealed-box title");

        System.out.println("V092VisualCueAndSealedClosureRegressionTest: PASS");
    }

    private static Models.Identification baseSetMewtwo() {
        Models.Identification id = new Models.Identification();
        id.category = "collectible trading card";
        id.categoryKey = "pokemon_tcg_card";
        id.brand = "Pokémon";
        id.family = "Base Set";
        id.photoIdentityComplete = true;
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityConfidence = 97;
        id.photoIdentityKind = "composite_markings";
        id.photoIdentityName = "Pokémon Base Set Mewtwo Holo 10/102";
        id.photoViews.add("front view");
        id.photoIdentityFields.add("manufacturer=Pokémon");
        id.photoIdentityFields.add("set=Base Set");
        id.photoIdentityFields.add("subject=Mewtwo");
        id.photoIdentityFields.add("card_number=10/102");
        id.localScan = new Models.LocalScan();
        id.localScan.textByImage.add("MEWTWO\n60 HP\n10/102");
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
