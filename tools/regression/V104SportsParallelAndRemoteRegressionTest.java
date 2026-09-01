package com.flipcheck.nativebeta;

import org.json.JSONObject;

/** Front/back sports parallel closure and remote-brand false-positive veto. */
public final class V104SportsParallelAndRemoteRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        closeFrontBackParallelWithinBudget();
        rejectUnresolvedParallelConfirmation();
        suppressRemoteBrandFromGenericKeys();
        preserveDirectRemoteVisualMatch();
        System.out.println("V104SportsParallelAndRemoteRegressionTest: PASS");
    }

    private static void closeFrontBackParallelWithinBudget() throws Exception {
        Models.Identification id = doncic();
        Models.CandidateScore c = candidate();
        id.candidates.add(c);
        Models.Usage usage = new Models.Usage();
        usage.requests = 1;
        usage.webCalls = 1;
        usage.costUsd = 0.012;
        require(SportsCardParallelRecovery.eligible(id, usage),
                "front/back sports card with unresolved parallel must receive focused Web recovery");
        Models.Usage blocked = new Models.Usage();
        blocked.requests = 1;
        blocked.webCalls = 1;
        blocked.costUsd = 0.013;
        require(!SportsCardParallelRecovery.eligible(id, blocked),
                "focused recovery must not exceed the 0.025 USD scan ceiling");

        OpenAiClient.Response response = new OpenAiClient.Response();
        response.payload = new JSONObject().put("supported", true)
                .put("same_physical_card", true).put("front_back_match", true)
                .put("card_number_match", true)
                .put("parallel_visually_distinguishable", true)
                .put("exact_parallel_name", "Green Prizm")
                .put("normalized_identity",
                        "2018-19 Panini Prizm Basketball Luka Doncic Green Prizm #280 RC")
                .put("source_url", "https://example.test/checklist/280-green")
                .put("identity_confidence", 97).put("contradiction", "")
                .put("evidence", "front pattern and reverse No. 280 match checklist");
        Models.Source source = new Models.Source();
        source.url = "https://example.test/checklist/280-green";
        response.sources.add(source);
        require(SportsCardParallelRecovery.apply(id, response) && id.marketReady
                        && id.model.contains("Green Prizm") && !id.model.contains("unresolved"),
                "second grounded lookup must replace technical uncertainty with exact parallel");
    }

    private static void rejectUnresolvedParallelConfirmation() {
        Models.Identification id = doncic();
        Models.CandidateScore c = candidate();
        require(CollectibleCardIdentityPolicy.variantUnresolved(id, c),
                "an unresolved sports parallel is not a fully confirmed commercial identity");
    }

    private static void suppressRemoteBrandFromGenericKeys() {
        Models.Identification id = new Models.Identification();
        id.category = "television remote control";
        id.categoryKey = "remote_control";
        id.brand = "Samsung";
        id.brandEvidence = "web_inferred";
        id.controlLabels.add("NETFLIX");
        id.controlLabels.add("HOME");
        id.controlLabels.add("SOURCES");
        Models.CandidateScore c = new Models.CandidateScore();
        c.brand = "Samsung";
        c.family = "BN59-series";
        c.probableReference = "Samsung BN59-series Smart TV remote";
        c.probableReferenceConfidence = 58;
        c.totalScore = 58;
        c.candidateFacts.add("visual_reference_checked=false");
        id.candidates.add(c);
        RemoteCandidateGuard.apply(id);
        require(id.brand.isEmpty() && id.family.isEmpty() && id.model.isEmpty()
                        && c.probableReference.isEmpty()
                        && id.nextPhotoRequest.toLowerCase().contains("vano batterie"),
                "generic controls must not publish Samsung or another guessed remote brand");
    }

    private static void preserveDirectRemoteVisualMatch() {
        Models.Identification id = new Models.Identification();
        id.category = "television remote control";
        id.categoryKey = "remote_control";
        Models.CandidateScore c = new Models.CandidateScore();
        c.brand = "Philips";
        c.probableReference = "Philips remote reference";
        c.totalScore = 92;
        c.candidateFacts.add("visual_reference_checked=true");
        c.candidateFacts.add("visual_match_confidence=94");
        id.candidates.add(c);
        RemoteCandidateGuard.apply(id);
        require(!c.probableReference.isEmpty() && c.totalScore == 92,
                "a real direct reference-image match must survive the remote guard");
    }

    private static Models.Identification doncic() {
        Models.Identification id = new Models.Identification();
        id.category = "sports collectible card";
        id.categoryKey = "sports_collectible_card";
        id.brand = "Panini";
        id.family = "2018-19 Panini Prizm Basketball";
        id.model = "Luka Doncic #280 green reflective; exact parallel name unresolved";
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityConfidence = 97;
        id.photoViews.add("front");
        id.photoViews.add("back");
        id.photoIdentityFields.add("manufacturer=Panini");
        id.photoIdentityFields.add("set=2018-19 Panini Prizm Basketball");
        id.photoIdentityFields.add("player=Luka Doncic");
        id.photoIdentityFields.add("card_number=280");
        id.photoIdentityFields.add("parallel=green reflective");
        id.photoIdentityFields.add("rookie_card=present");
        return id;
    }

    private static Models.CandidateScore candidate() {
        Models.CandidateScore c = new Models.CandidateScore();
        c.brand = "Panini";
        c.family = "2018-19 Panini Prizm Basketball";
        c.model = "Luka Doncic #280 green reflective; exact parallel name unresolved";
        c.probableReference = c.model;
        c.probableReferenceConfidence = 95;
        c.evidence = "card base confirmed; exact parallel name unresolved";
        c.candidateFacts.add("source_grounded=true");
        c.candidateFacts.add("same_entity_role=true");
        c.candidateFacts.add("relationship_only=false");
        c.candidateFacts.add("disproof_passed=true");
        c.candidateFacts.add("exact_reference_complete=true");
        return c;
    }
}
