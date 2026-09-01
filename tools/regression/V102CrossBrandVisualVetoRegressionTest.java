package com.flipcheck.nativebeta;

import org.json.JSONArray;
import org.json.JSONObject;

/** Regression for text/layout-equivalent devices from competing manufacturers. */
public final class V102CrossBrandVisualVetoRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        orbitVisualFamilyMustVetoRainBirdTextCandidate();
        directReferenceImageMustNotBeOverridden();
        inconclusivePassMustHideTextOnlyBrand();
        genericRemoteMustNotSpendSecondPass();
        costCapMustRemainHard();
        System.out.println("V102CrossBrandVisualVetoRegressionTest: PASS");
    }

    private static void orbitVisualFamilyMustVetoRainBirdTextCandidate() throws Exception {
        Models.Identification id = controller();
        Models.CandidateScore rainBird = candidate("Rain Bird", "ISA 400 Series", false, 0);
        rainBird.probableReference = "ISA 406";
        rainBird.probableReferenceConfidence = 86;
        id.candidates.add(rainBird);
        Models.Usage usage = firstUsage(0.012);
        require(VisualBrandFamilyRecovery.eligible(id, usage),
                "rich controller with a text-only cross-brand candidate must be checked");

        require(VisualBrandFamilyRecovery.apply(id, response("Orbit", "WaterMaster", 94)),
                "distinctive Orbit family result must apply");
        require(id.candidates.isEmpty(), "Rain Bird must be removed from surviving candidates");
        require(id.rejectedCandidates.contains(rainBird), "Rain Bird must be recorded as rejected");
        require("Orbit".equals(id.brand) && "WaterMaster".equals(id.family),
                "probable output must retain Orbit WaterMaster");
        require(id.model.isEmpty() && !id.marketReady,
                "visual family recovery must never confirm ISA 406 or any exact model");
        UniversalRecognitionLadder.State ladder = UniversalRecognitionLadder.assess(id);
        require(ladder.level == UniversalRecognitionLadder.FAMILY
                        && "Orbit".equals(ladder.brand)
                        && "WaterMaster".equals(ladder.family),
                "public ladder must show probable Orbit WaterMaster family");
    }

    private static void directReferenceImageMustNotBeOverridden() throws Exception {
        Models.Identification id = controller();
        Models.CandidateScore rainBird = candidate("Rain Bird", "ISA 400 Series", true, 91);
        id.candidates.add(rainBird);
        require(VisualBrandFamilyRecovery.apply(id, response("Orbit", "WaterMaster", 94)),
                "recovery response may still calibrate the probable family");
        require(id.candidates.contains(rainBird) && !rainBird.hardRejected,
                "a real high-confidence reference-image comparison must survive the visual veto");
    }

    private static void genericRemoteMustNotSpendSecondPass() {
        Models.Identification id = controller();
        id.category = "television remote control";
        id.categoryKey = "remote_control";
        id.candidates.add(candidate("Philips", "smart-TV remote control", false, 0));
        require(!VisualBrandFamilyRecovery.eligible(id, firstUsage(0.012)),
                "generic remotes remain excluded from the second visual call");
    }

    private static void inconclusivePassMustHideTextOnlyBrand() {
        Models.Identification id = controller();
        Models.CandidateScore rainBird = candidate("Rain Bird", "ISA 400 Series", false, 0);
        id.candidates.add(rainBird);
        id.brand = "Rain Bird";
        id.brandEvidence = "verified_web";
        id.family = "ISA 400 Series";
        VisualBrandFamilyRecovery.applyInconclusiveGuard(id);
        require(id.brand.isEmpty() && id.family.isEmpty() && id.model.isEmpty(),
                "an inconclusive visual pass must not publish a text-only manufacturer");
        require(rainBird.totalScore == 49
                        && rainBird.candidateFacts.contains("public_brand_family_withheld=true"),
                "the lead may remain internal only below the public threshold");
    }

    private static void costCapMustRemainHard() {
        Models.Identification id = controller();
        id.candidates.add(candidate("Rain Bird", "ISA 400 Series", false, 0));
        require(!VisualBrandFamilyRecovery.eligible(id, firstUsage(0.017)),
                "projected total cost above two cents must block recovery");
    }

    private static Models.Identification controller() {
        Models.Identification id = new Models.Identification();
        id.category = "six-station indoor irrigation controller";
        id.categoryKey = "irrigation_controller";
        id.categoryConfidence = 99;
        id.controlLabels.add("WATERING DURATION MINUTES");
        id.controlLabels.add("DAYS (A)");
        id.controlLabels.add("INTERVAL (B)");
        id.controlLabels.add("RAIN DELAY");
        id.visualFacts.add("station_sliders=6");
        id.visualFacts.add("program_tracks=A,B,A&B");
        id.spatialSignature.add("six vertical green station sliders in center panel");
        id.visualFingerprint = "gray enclosure, hinged lower cover, green six-slider panel";
        return id;
    }

    private static Models.CandidateScore candidate(String brand, String family,
                                                    boolean visualReference, int visualConfidence) {
        Models.CandidateScore c = new Models.CandidateScore();
        c.brand = brand;
        c.family = family;
        c.totalScore = 68;
        c.textScore = 92;
        c.layoutScore = 72;
        c.webScore = 88;
        c.candidateFacts.add("source_grounded=true");
        c.candidateFacts.add("same_entity_role=true");
        c.candidateFacts.add("relationship_only=false");
        c.candidateFacts.add("visual_reference_checked=" + visualReference);
        c.candidateFacts.add("visual_match_confidence=" + visualConfidence);
        return c;
    }

    private static Models.Usage firstUsage(double cost) {
        Models.Usage usage = new Models.Usage();
        usage.requests = 1;
        usage.webCalls = 1;
        usage.costUsd = cost;
        return usage;
    }

    private static OpenAiClient.Response response(String brand, String family, int confidence)
            throws Exception {
        OpenAiClient.Response response = new OpenAiClient.Response();
        response.payload = new JSONObject()
                .put("applicable", true)
                .put("same_foreground_object", true)
                .put("visually_distinguishable", true)
                .put("model_code_visible", false)
                .put("brand", brand)
                .put("family", family)
                .put("confidence", confidence)
                .put("distinctive_cues", new JSONArray()
                        .put("green six-slider panel")
                        .put("gray hinged enclosure"))
                .put("contradiction", "");
        return response;
    }
}
