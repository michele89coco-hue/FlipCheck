package com.flipcheck.nativebeta;

/** Regression tests for forced verification and close of known commercial cards. */
public final class V112ForcedCardVerificationRegressionTest {
    public static void main(String[] args) {
        sportsFrontBackClosesWithoutChecklistNumber();
        tcgFrontOnlyClosesWithoutCollectorNumber();
        customCardsRemainOpen();
        System.out.println("V112ForcedCardVerificationRegressionTest: PASS");
    }

    private static void sportsFrontBackClosesWithoutChecklistNumber() {
        Models.Identification id = base("sports collectible card", "Panini", "Adrenalyn XL", "Stephen Curry");
        id.photoViews.add("front"); id.photoViews.add("back");
        id.visualFacts.add("manufacturer=Panini");
        id.visualFacts.add("product_line=Adrenalyn XL");
        id.visualFacts.add("subject=Stephen Curry");
        id.visualFacts.add("team=Golden State Warriors");
        id.visualFacts.add("copyright_year=2009");
        id.visibleLabels.add("DEF 72"); id.visibleLabels.add("OFF 90");
        require(CardPhotoTupleClosure.requiresMandatoryVerification(id, firstPass()), "sports card must force second verification");
        require(CardPhotoTupleClosure.canClose(id), "sports front/back tuple must close without checklist number");
        require(CardPhotoTupleClosure.apply(id), "sports tuple closure must apply");
        ConfirmationIntegrityPolicy.enforce(id);
        require(id.marketReady, "integrity gate must retain sports closure");
    }

    private static void tcgFrontOnlyClosesWithoutCollectorNumber() {
        Models.Identification id = base("trading card", "Pokémon", "Base Set", "Mewtwo");
        id.photoViews.add("front");
        id.visualFacts.add("manufacturer=Pokémon");
        id.visualFacts.add("set=Base Set");
        id.visualFacts.add("subject=Mewtwo");
        id.visualFacts.add("finish=holographic");
        id.visibleLabels.add("Mewtwo"); id.visibleLabels.add("Pokémon");
        require(CardPhotoTupleClosure.requiresMandatoryVerification(id, firstPass()), "TCG front must force second verification");
        require(CardPhotoTupleClosure.canClose(id), "known TCG front must close without collector number");
        require(CardPhotoTupleClosure.apply(id), "TCG tuple closure must apply");
        ConfirmationIntegrityPolicy.enforce(id);
        require(id.marketReady, "integrity gate must retain TCG closure");
    }

    private static void customCardsRemainOpen() {
        Models.Identification id = base("trading card", "Unknown Brand", "Custom Set", "Test Hero");
        id.photoViews.add("front");
        id.visualFacts.add("manufacturer=Unknown Brand");
        id.visualFacts.add("set=Custom Set");
        id.visualFacts.add("subject=Test Hero");
        id.visibleLabels.add("custom fan-made");
        require(!CardPhotoTupleClosure.canClose(id), "custom card must never be forced closed");
    }

    private static Models.Identification base(String category, String brand, String series, String subject) {
        Models.Identification id = new Models.Identification();
        id.category = category; id.brand = brand;
        id.visibleLabels.add(brand); id.visibleLabels.add(series); id.visibleLabels.add(subject);
        return id;
    }

    private static Models.Usage firstPass() { Models.Usage u = new Models.Usage(); u.requests = 1; u.webCalls = 1; return u; }
    private static void require(boolean yes, String message) { if (!yes) throw new AssertionError(message); }
}
