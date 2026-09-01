package com.flipcheck.nativebeta;

/** Regression for the reported Curry budget/stat-number failure and universal recovery gate. */
public final class V111UniversalMandatorySecondPassRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        curryUsesMandatoryTextSecondPassAndCloses();
        customCardStaysOpen();
        genericRichObjectGetsSecondPass();
        System.out.println("V111UniversalMandatorySecondPassRegressionTest: PASS");
    }

    private static void curryUsesMandatoryTextSecondPassAndCloses() {
        Models.Identification id = curry();
        CollectibleCardIdentityPolicy.sanitizeObservation(id, id.localScan);
        require(!"77".equals(CollectibleCardIdentityPolicy.observedCardNumber(id, id.localScan)),
                "DEF/OFF game value 77 must not become the collector number");
        Models.Usage usage = new Models.Usage();
        usage.requests = 1; usage.webCalls = 1; usage.costUsd = 0.0158;
        require(ExactCardCatalogRecovery.eligible(id, usage),
                "rich unresolved object must receive a second request inside total budget");
        require(!ExactCardCatalogRecovery.useSecondWeb(usage),
                "at 0.0158 USD the second request must reuse the first source ledger without another paid Web call");
        require(CardPhotoTupleClosure.apply(id) && id.marketReady,
                "known commercial card with front/back tuple must close when catalog retrieval is inconclusive");
        require(id.model.contains("Stephen Curry") && !id.model.contains("#77"),
                "closed identity must preserve subject and must not invent #77");
    }

    private static void customCardStaysOpen() {
        Models.Identification id = curry();
        id.visibleLabels.add("CUSTOM FAN-MADE CARD");
        require(!CardPhotoTupleClosure.canClose(id),
                "custom/unlicensed cards must not be force-confirmed");
    }

    private static void genericRichObjectGetsSecondPass() {
        Models.Identification id = new Models.Identification();
        id.category = "television remote control"; id.brand = "Philips";
        id.photoIdentityName = "Philips remote"; id.photoIdentityPhysicalBinding = true;
        id.photoIdentityFields.add("manufacturer=Philips");
        id.photoIdentityFields.add("product_line=smart TV remote");
        id.visualFacts.add("button=NETFLIX"); id.visualFacts.add("button=SUBTITLE");
        id.visualFacts.add("button=TEXT"); id.visualFacts.add("button=SOURCES");
        id.spatialSignature.add("central navigation ring");
        Models.Usage usage = new Models.Usage(); usage.requests = 1; usage.webCalls = 1; usage.costUsd = 0.014;
        require(ExactCardCatalogRecovery.eligible(id, usage),
                "mandatory second pass must be universal, not card-name-specific");
        require(ExactCardCatalogRecovery.attachImages(id),
                "generic objects may attach one low-detail image for model-specific visual disproof");
    }

    private static Models.Identification curry() {
        Models.Identification id = new Models.Identification();
        id.category = "sports collectible card"; id.categoryKey = "sports_trading_card";
        id.brand = "Panini"; id.brandEvidence = "visible_logo_cross_photo"; id.brandRoleConfidence = 96;
        id.photoViews.add("front"); id.photoViews.add("back");
        id.visibleLabels.add("Stephen Curry"); id.visibleLabels.add("Adrenalyn XL");
        id.visibleLabels.add("PANINI"); id.visibleLabels.add("DEF 72"); id.visibleLabels.add("OFF 90");
        id.visibleLabels.add("77"); id.visibleLabels.add("© 2009 Panini America, Inc.");
        id.visualFacts.add("manufacturer=Panini"); id.visualFacts.add("year=2009");
        id.visualFacts.add("product_line=Adrenalyn XL"); id.visualFacts.add("sport=basketball");
        id.visualFacts.add("subject=Stephen Curry"); id.visualFacts.add("team=Golden State Warriors");
        id.visualFacts.add("card_number=77"); id.visualFacts.add("def=72"); id.visualFacts.add("off=90");
        id.photoIdentityFields.add("manufacturer=Panini"); id.photoIdentityFields.add("year=2009");
        id.photoIdentityFields.add("product_line=Adrenalyn XL"); id.photoIdentityFields.add("subject=Stephen Curry");
        id.photoIdentityFields.add("team=Golden State Warriors"); id.photoIdentityPhysicalBinding = true;
        id.localScan = new Models.LocalScan();
        id.localScan.textByImage.add("STEPHEN CURRY\nDEF 72\nOFF 90\n77\nADRENALYN XL\nPANINI");
        id.localScan.textByImage.add("© 2009 Panini America, Inc.\nTRADING CARD GAME");
        return id;
    }
}
