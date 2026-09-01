package com.flipcheck.nativebeta;

/** Reproduces the real Curry front/back failure reported against v1.05. */
public final class V106DeterministicCardTupleClosureRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        curryClosesWithoutCatalogHit();
        paidCatalogPassHasPriority();
        incompleteCardStillFailsClosed();
        contradictionsStillBlockClosure();
        System.out.println("V106DeterministicCardTupleClosureRegressionTest: PASS");
    }

    private static void curryClosesWithoutCatalogHit() {
        Models.Identification id = curry();
        require(CardPhotoTupleClosure.canClose(id),
                "complete Curry front/back tuple must be independently closable");
        require(CardPhotoTupleClosure.apply(id), "photo tuple closure must apply");
        require(id.marketReady, "Curry must not remain open when the web shortlist is empty");
        require(id.model.contains("Stephen Curry") && id.family.contains("Adrenalyn XL")
                        && id.family.contains("2009") && id.brand.equals("Panini"),
                "public identity must retain player, year, maker and product line");
        require(!id.model.toLowerCase().contains("rookie") && !id.model.contains("#"),
                "photo-only closure must not invent rookie status or a card number");
        ConfirmationIntegrityPolicy.enforce(id);
        require(id.marketReady, "final integrity must preserve a complete sports tuple");
    }

    private static void paidCatalogPassHasPriority() {
        Models.Identification id = curry();
        Models.Usage usage = new Models.Usage();
        usage.requests = 1;
        usage.webCalls = 1;
        usage.costUsd = 0.011;
        require(CardPhotoTupleClosure.shouldReserveCatalogPass(id),
                "rich front/back card should reserve the catalog pass");
        require(!PhysicalIdentityRecovery.eligible(id, usage),
                "a no-web OCR retry must not consume the paid catalog slot first");
        require(ExactCardCatalogRecovery.eligible(id, usage),
                "the exact catalog recovery must remain eligible within 0.025 USD");
    }

    private static void incompleteCardStillFailsClosed() {
        Models.Identification id = new Models.Identification();
        id.category = "sports collectible card";
        id.categoryKey = "sports_collectible_card";
        id.brand = "Panini";
        id.visibleLabels.add("PANINI");
        id.photoViews.add("front");
        require(!CardPhotoTupleClosure.canClose(id),
                "a generic single view without subject/set/year must stay open");
    }

    private static void contradictionsStillBlockClosure() {
        Models.Identification id = curry();
        id.finalContradictions.add("STRONG conflict: photographed player differs from source");
        require(!CardPhotoTupleClosure.canClose(id),
                "a strong contradiction must veto deterministic closure");
    }

    private static Models.Identification curry() {
        Models.Identification id = new Models.Identification();
        id.category = "sports collectible card";
        id.categoryKey = "sports_trading_card";
        id.brand = "Panini";
        id.brandEvidence = "visible_logo_cross_photo";
        id.brandRoleConfidence = 96;
        id.photoViews.add("front");
        id.photoViews.add("back");
        id.photoViews.add("front diagnostic crop");
        id.visibleLabels.add("Stephen Curry");
        id.visibleLabels.add("GUARD");
        id.visibleLabels.add("DEF 72");
        id.visibleLabels.add("OFF 90");
        id.visibleLabels.add("Adrenalyn XL");
        id.visibleLabels.add("TRADING CARD GAME");
        id.visibleLabels.add("PANINI");
        id.visibleLabels.add("© 2009 NBA Properties, Inc.");
        id.visualFacts.add("subject=Stephen Curry");
        id.visualFacts.add("team=Golden State Warriors");
        id.visualFacts.add("year=2009");
        id.visualFacts.add("format=basketball trading card");
        id.visualFacts.add("front_rating_star=77");
        id.visualFacts.add("def=72");
        id.photoIdentityPhysicalBinding = true;
        id.localScan = new Models.LocalScan();
        id.localScan.textByImage.add("STEPHEN CURRY\nGUARD\nDEF 72\nOFF 90\nADRENALYN XL\nPANINI");
        id.localScan.textByImage.add("TRADING CARD GAME\nPANINI\n© 2009 NBA Properties, Inc.");
        return id;
    }
}
