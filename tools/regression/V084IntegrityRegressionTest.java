package com.flipcheck.nativebeta;

/** Deterministic regressions retained and extended for the v0.85 phone tests. */
public final class V084IntegrityRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        Models.Identification kobe = card("Upper Deck", "SP Authentic",
                "Kobe Bryant", "FC7", "8/23");
        Models.LocalScan kobeOcr = new Models.LocalScan();
        kobeOcr.textByImage.add("KOBE BRYANT / GUARD\nBorn: 8/23/78 Philadelphia\nFC7");
        kobeOcr.textByImage.add("Bryant capped off a season by helping the Lakers to the 1999-2000 NBA Championship");
        kobe.photoIdentityFields.add("1999-2000");
        kobe.localScan = kobeOcr;
        CollectibleCardIdentityPolicy.sanitizeObservation(kobe, kobeOcr);
        require(!kobe.photoIdentityFields.contains("serial=8/23"),
                "DOB 8/23/78 must not become serial 8/23");
        require(!kobe.photoIdentityFields.contains("1999-2000"),
                "a narrative championship season must not become the card release year");
        require("FC7".equals(CollectibleCardIdentityPolicy.observedCardNumber(kobe, kobeOcr)),
                "the physical FC7 card number must survive");

        Models.CandidateScore sibling = candidate("Upper Deck", "SP Authentic First Class",
                "Kobe Bryant FC2", "FC2");
        CollectibleCardIdentityPolicy.applyCandidateGate(kobe, sibling);
        require(sibling.hardRejected,
                "FC7 must hard-reject a visually similar FC2 sibling");

        Models.CandidateScore exact = candidate("Upper Deck", "SP Authentic First Class",
                "Kobe Bryant FC7", "FC7");
        CollectibleCardIdentityPolicy.applyCandidateGate(kobe, exact);
        exact.totalScore = 92;
        require(!exact.hardRejected && CollectibleCardIdentityPolicy.canConfirm(kobe, exact),
                "complete physical card fields plus exact source must be confirmable");

        Models.Identification controller = new Models.Identification();
        controller.category = "irrigation sprinkler controller";
        controller.categoryKey = "irrigation_controller";
        controller.spatialSignature.add("Six vertical station sliders in a horizontal row");
        Models.CandidateScore four = candidate("Orbit", "WaterMaster", "57004", "57004");
        four.candidateFacts.add("station_count=6");
        four.candidateFacts.add("exact_identity_supported=true");
        four.candidateFacts.add("visual_reference_checked=true");
        four.candidateFacts.add("visual_match_confidence=93");
        require(ReferenceScopePolicy.observedStationCount(controller) == 6,
                "the photographed six-station layout must be counted");
        require(ReferenceScopePolicy.candidateStationCount(four) == 6
                        && ReferenceScopePolicy.hardViolation(controller, four).isEmpty()
                        && ReferenceScopePolicy.allowsExactConfirmation(controller, four),
                "Orbit 57004 must use the source-stated six-station count, not its trailing digit");
        Models.CandidateScore opaque = candidate("Orbit", "WaterMaster", "57006", "57006");
        require(ReferenceScopePolicy.candidateStationCount(opaque) == 0,
                "model-number digits must never be decoded as a physical station count");

        Models.Identification panini = card("Panini", "Obsidian Soccer",
                "Lamine Yamal", "8", "8/9");
        CollectibleCardIdentityPolicy.sanitizeObservation(panini, new Models.LocalScan());
        require(!panini.photoIdentityFields.contains("serial=8/9"),
                "an unlocated overlay/listing fraction must not become a card serial");
        Models.CandidateScore printRun = candidate("Panini", "Obsidian Soccer",
                "Supernova #8 /120", "8");
        printRun.probableReference = "Supernova #8 Lamine Yamal /120";
        CollectibleCardIdentityPolicy.applyCandidateGate(panini, printRun);
        require(!printRun.probableReference.contains("/120")
                        && !printRun.model.contains("/120"),
                "a checklist print run must not be copied into the photographed card identity");

        Models.Identification multiPhoto = new Models.Identification();
        multiPhoto.brand = "Philips";
        multiPhoto.brandEvidence = "visible_logo_cross_photo";
        multiPhoto.brandRoleConfidence = 96;
        multiPhoto.brandLabels.add("Philips");
        multiPhoto.photoViews.add("front");
        multiPhoto.photoViews.add("rear battery compartment");
        require(BrandAnchorPolicy.isLocked(multiPhoto),
                "a clear manufacturer logo in a complementary view must remain a brand anchor");
        Models.CandidateScore sony = candidate("Sony", "BRAVIA", "RMT-TX100U", "");
        require(!BrandAnchorPolicy.candidateCompatible(multiPhoto, sony),
                "generic rear battery text must not switch a Philips remote to Sony");

        String prompt = IdentificationPipelineV082.multimodalPromptForTest(new Models.LocalScan(), "");
        require(prompt.contains("FOUR distinct queries") && prompt.contains("up to SIX")
                        && prompt.contains("probable_reference")
                        && prompt.contains("physical card number")
                        && prompt.contains("station/zone/slider count"),
                "v0.84 must preserve one Web Search while diversifying internal retrieval");

        System.out.println("V085IntegrityRegressionTest: PASS");
    }

    private static Models.Identification card(String brand, String set, String subject,
                                               String number, String falseSerial) {
        Models.Identification id = new Models.Identification();
        id.category = "basketball trading card";
        id.categoryKey = "sports_trading_card";
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityOverlayOrWatermark = false;
        id.photoIdentityComplete = true;
        id.photoIdentityConfidence = 96;
        id.photoIdentityKind = "composite_markings";
        id.photoViews.add("front");
        id.photoViews.add("reverse");
        id.photoIdentityFields.add("brand=" + brand);
        id.photoIdentityFields.add("set=" + set);
        id.photoIdentityFields.add("subject=" + subject);
        id.photoIdentityFields.add("card_number=" + number);
        id.photoIdentityFields.add("serial=" + falseSerial);
        id.visibleLabels.add(number);
        return id;
    }

    private static Models.CandidateScore candidate(String brand, String family,
                                                   String model, String number) {
        Models.CandidateScore candidate = new Models.CandidateScore();
        candidate.brand = brand;
        candidate.family = family;
        candidate.model = model;
        candidate.textScore = 94;
        candidate.webScore = 92;
        candidate.candidateFacts.add("source_grounded=true");
        candidate.candidateFacts.add("source_exact_reference=true");
        candidate.candidateFacts.add("exact_reference_complete=true");
        candidate.candidateFacts.add("source_identity_confidence=94");
        candidate.candidateFacts.add("same_entity_role=true");
        candidate.candidateFacts.add("relationship_only=false");
        candidate.candidateFacts.add("disproof_passed=true");
        candidate.candidateFacts.add("physical_card_number=" + number);
        return candidate;
    }
}
