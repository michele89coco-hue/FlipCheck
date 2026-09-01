package com.flipcheck.nativebeta;

/** Regressions from the v0.86 Topps box and Lamine Yamal field tests. */
public final class V087CaptureAndIntegrityRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        Models.Identification box = new Models.Identification();
        box.category = "Basketball trading cards";
        box.categoryKey = "basketball_trading_cards";
        box.photoIdentityName = "Topps Chrome Update Series Basketball";
        box.photoIdentityKind = "composite_markings";
        box.photoIdentityPhysicalBinding = true;
        box.photoIdentityConfidence = 93;
        box.photoIdentityFields.add("manufacturer=Topps");
        box.photoIdentityFields.add("product_line=Topps Chrome");
        box.photoIdentityFields.add("series=Update Series");
        box.photoIdentityFields.add("sport=basketball");
        box.photoIdentityFields.add("format=sealed retail box");
        Models.LocalScan boxOcr = new Models.LocalScan();
        boxOcr.textByImage.add("TOPPS CHROME\nUPDATE SERIES\nBASKETBALL\nIN EVERY BOX");
        box.localScan = boxOcr;
        SealedProductIdentityPolicy.consolidateObservation(box, boxOcr);
        require(SealedProductIdentityPolicy.isSealedRetailProduct(box),
                "format=sealed retail box must override a generic card category");
        require(!CollectibleCardIdentityPolicy.isCard(box),
                "a sealed box must not enter loose-card sanitation");
        require("Topps".equals(box.brand) && BrandBlindPolicy.trustedObservedBrand(box),
                "the locally corroborated Topps marking must reach retrieval");
        require(!box.photoIdentityComplete,
                "without a printed season the box may be searched but not confirmed exact");

        Models.Identification card = new Models.Identification();
        card.category = "sports trading card";
        card.categoryKey = "sports_trading_card";
        card.photoIdentityName = "Panini Obsidian Soccer Lamine Yamal No. 8, serial";
        card.photoIdentityKind = "composite_markings";
        card.photoIdentityPhysicalBinding = true;
        card.photoIdentityConfidence = 96;
        card.photoViews.add("front");
        card.photoViews.add("reverse");
        card.photoIdentityFields.add("manufacturer=Panini");
        card.photoIdentityFields.add("season=2024-25");
        card.photoIdentityFields.add("set=Obsidian Soccer");
        card.photoIdentityFields.add("player=Lamine Yamal");
        card.photoIdentityFields.add("card_number=8");
        card.photoIdentityFields.add("serial_binding=physical_card_surface");
        Models.LocalScan cardOcr = new Models.LocalScan();
        cardOcr.textByImage.add("PANINI\n2024-25 PANINI OBSIDIAN SOCCER\nLAMINE YAMAL\nNO. 8");
        card.localScan = cardOcr;
        CollectibleCardIdentityPolicy.sanitizeObservation(card, cardOcr);
        require(card.photoIdentityFields.stream().noneMatch(
                        x -> x.toLowerCase().startsWith("serial_")),
                "serial binding without serial=x/y must be removed");
        require(!card.photoIdentityName.toLowerCase().contains("serial"),
                "dangling serial text must be removed from the public identity");

        Models.CandidateScore candidate = new Models.CandidateScore();
        candidate.brand = "Panini";
        candidate.family = "Obsidian Soccer";
        candidate.model = "Lamine Yamal No. 8, serial";
        candidate.probableReference = "Supernova #8 serial";
        CollectibleCardIdentityPolicy.applyCandidateGate(card, candidate);
        require(!candidate.model.toLowerCase().contains("serial")
                        && !candidate.probableReference.toLowerCase().contains("serial"),
                "unsupported serial wording must be removed from candidates");

        System.out.println("V087CaptureAndIntegrityRegressionTest: PASS");
    }
}
