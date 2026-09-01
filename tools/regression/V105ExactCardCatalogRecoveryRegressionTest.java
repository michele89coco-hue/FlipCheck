package com.flipcheck.nativebeta;

import org.json.JSONArray;
import org.json.JSONObject;

/** A rich new card must recover from zero first-pass candidates and close exactly. */
public final class V105ExactCardCatalogRecoveryRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        recoverCurryWithZeroCandidates();
        enforceBudgetAndRichEvidence();
        rejectUngroundedOrAmbiguousRecovery();
        System.out.println("V105ExactCardCatalogRecoveryRegressionTest: PASS");
    }

    private static void recoverCurryWithZeroCandidates() throws Exception {
        Models.Identification id = curry();
        require(!CollectibleCardIdentityPolicy.isTradingCardGame(id),
                "a sports Adrenalyn XL card must not become TCG because the product prints TRADING CARD GAME");
        Models.Usage usage = firstUsage(0.0125);
        require(id.candidates.isEmpty() && ExactCardCatalogRecovery.eligible(id, usage),
                "zero grounded candidates must trigger recovery for a rich card");

        OpenAiClient.Response response = exactResponse();
        require(ExactCardCatalogRecovery.apply(id, response),
                "exact catalog response must be applied");
        require(id.marketReady && id.model.contains("Stephen Curry")
                        && id.model.contains("Adrenalyn XL")
                        && id.candidates.size() == 1,
                "Curry front/back identity must close instead of remaining Panini generic");
        ConfirmationIntegrityPolicy.enforce(id);
        require(id.marketReady,
                "final integrity policy must preserve an exact front/back catalog tuple without a printed card number");
    }

    private static void enforceBudgetAndRichEvidence() {
        Models.Identification rich = curry();
        require(!ExactCardCatalogRecovery.eligible(rich, firstUsage(0.0135)),
                "second search must not exceed 0.025 USD projected cost");

        Models.Identification poor = new Models.Identification();
        poor.category = "sports collectible card";
        poor.categoryKey = "sports_collectible_card";
        poor.photoViews.add("front");
        poor.visibleLabels.add("Panini");
        require(!ExactCardCatalogRecovery.eligible(poor, firstUsage(0.010)),
                "an unreadable/generic card must not spend the mandatory recovery call");
    }

    private static void rejectUngroundedOrAmbiguousRecovery() throws Exception {
        Models.Identification id = curry();
        OpenAiClient.Response response = exactResponse();
        response.sources.clear();
        require(!ExactCardCatalogRecovery.apply(id, response),
                "a claimed exact card without the returned grounded source must fail closed");

        response = exactResponse();
        response.payload.put("contradictions", new JSONArray().put("parallel not distinguishable"));
        require(!ExactCardCatalogRecovery.apply(curry(), response),
                "a value-relevant contradiction must prevent confirmation");
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

    private static OpenAiClient.Response exactResponse() throws Exception {
        OpenAiClient.Response response = new OpenAiClient.Response();
        String url = "https://example.test/2009-adrenalyn-xl-stephen-curry";
        response.payload = new JSONObject()
                .put("supported", true).put("same_physical_card", true)
                .put("manufacturer", "Panini")
                .put("set_or_series", "2009 Panini Adrenalyn XL")
                .put("subject", "Stephen Curry")
                .put("card_number", "")
                .put("parallel_or_variant", "base")
                .put("rookie", true).put("language", "English")
                .put("normalized_identity",
                        "2009 Panini Adrenalyn XL Stephen Curry Rookie Card")
                .put("exact_reference_complete", true).put("source_url", url)
                .put("exact_composite_tuple_match", true)
                .put("visual_reference_checked", true)
                .put("visual_match_confidence", 97)
                .put("matched_physical_fields", new JSONArray()
                        .put("Stephen Curry portrait").put("Golden State Warriors uniform")
                        .put("Adrenalyn XL front design").put("DEF 72")
                        .put("OFF 90").put("2009 Panini reverse layout"))
                .put("contradictions", new JSONArray())
                .put("disproof_passed", true).put("identity_confidence", 97)
                .put("evidence", "exact front and back catalog imagery agrees");
        Models.Source source = new Models.Source();
        source.url = url;
        source.title = "2009 Panini Adrenalyn XL Stephen Curry";
        response.sources.add(source);
        return response;
    }

    private static Models.Usage firstUsage(double cost) {
        Models.Usage usage = new Models.Usage();
        usage.requests = 1;
        usage.webCalls = 1;
        usage.costUsd = cost;
        return usage;
    }
}
