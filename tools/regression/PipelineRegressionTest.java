package com.flipcheck.nativebeta;

import org.json.JSONObject;

/**
 * Deterministic smoke tests for the most important v0.82 decision invariants.
 * This deliberately avoids network calls and Android UI state.
 */
public final class PipelineRegressionTest {
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClosedSchema(JSONObject schema, String path) {
        if (schema == null) {
            return;
        }
        String type = schema.optString("type", "");
        if ("object".equals(type)) {
            require(schema.has("additionalProperties") && !schema.optBoolean("additionalProperties", true),
                    path + " must set additionalProperties=false");
            JSONObject properties = schema.optJSONObject("properties");
            org.json.JSONArray required = schema.optJSONArray("required");
            require(properties != null && required != null,
                    path + " must declare properties and required");
            java.util.Iterator<String> keys = properties.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                boolean found = false;
                for (int i = 0; i < required.length(); i++) {
                    if (key.equals(required.optString(i))) {
                        found = true;
                        break;
                    }
                }
                require(found, path + "." + key + " must be required");
                requireClosedSchema(properties.optJSONObject(key), path + "." + key);
            }
        } else if ("array".equals(type)) {
            requireClosedSchema(schema.optJSONObject("items"), path + "[]");
        }
    }

    public static void main(String[] args) throws Exception {
        Models.Identification brand = new Models.Identification();
        brand.brand = "Alpha";
        brand.brandEvidence = "visible_brand_text";
        brand.visibleLabels.add("Alpha");
        brand.brandRoleConfidence = 84;
        require(!BrandAnchorPolicy.isLocked(brand), "brand role below 85 must not lock");
        brand.brandRoleConfidence = 90;
        require(!BrandAnchorPolicy.isLocked(brand),
                "a Vision label must not corroborate its own brand claim");
        Models.LocalScan independentBrandOcr = new Models.LocalScan();
        independentBrandOcr.textByImage.add("Alpha");
        brand.localScan = independentBrandOcr;
        require(BrandAnchorPolicy.isLocked(brand),
                "a high-confidence brand with independent local OCR may lock");

        Models.Identification hallucinatedSamsung = new Models.Identification();
        hallucinatedSamsung.brand = "Samsung";
        hallucinatedSamsung.brandEvidence = "visible_logo";
        hallucinatedSamsung.brandRoleConfidence = 99;
        hallucinatedSamsung.title = "Samsung Blu-ray remote";
        hallucinatedSamsung.category = "Blu-ray remote control";
        hallucinatedSamsung.brandLabels.add("Samsung");
        hallucinatedSamsung.visibleLabels.add("Samsung");
        hallucinatedSamsung.searchableLabels.add("Samsung");
        Models.LocalScan philipsRemoteOcr = new Models.LocalScan();
        philipsRemoteOcr.textByImage.add("NETFLIX\nTOP PICKS\nPAIR\nSETTINGS");
        java.util.List<String> philipsFingerprint =
                IdentificationPipelineV082.localDistinctiveControls(philipsRemoteOcr);
        require(philipsFingerprint.contains("NETFLIX")
                        && philipsFingerprint.contains("TOP PICKS"),
                "the Philips OCR controls must reach the multimodal prompt as a soft visual fingerprint");
        require(!IdentificationPipelineV082.localOcrWordCandidates(philipsRemoteOcr)
                        .contains("Samsung"),
                "the Philips OCR fixture must not invent a Samsung word candidate");
        Models.LocalScan orbitWordOcr = new Models.LocalScan();
        orbitWordOcr.textByImage.add("Orbit\nPROGRAM\nCYCLE START");
        require(IdentificationPipelineV082.localOcrWordCandidates(orbitWordOcr)
                        .contains("Orbit")
                        && !IdentificationPipelineV082.localOcrWordCandidates(orbitWordOcr)
                        .contains("PROGRAM"),
                "a plausible OCR manufacturer word may be checked while controls stay quarantined");
        hallucinatedSamsung.localScan = philipsRemoteOcr;
        BrandBlindPolicy.sanitizeBrandEvidence(hallucinatedSamsung, philipsRemoteOcr);
        require(!BrandBlindPolicy.trustedObservedBrand(hallucinatedSamsung)
                        && hallucinatedSamsung.brandLabels.isEmpty()
                        && !hallucinatedSamsung.visibleLabels.contains("Samsung")
                        && "Blu-ray remote control".equals(hallucinatedSamsung.title),
                "the Philips regression fixture must quarantine an uncorroborated Samsung hallucination");
        require(!IdentificationPipelineV082.firstQueryIsBrandNeutral(hallucinatedSamsung,
                        java.util.Arrays.asList("Samsung Blu-ray remote Netflix"))
                        && IdentificationPipelineV082.firstQueryIsBrandNeutral(hallucinatedSamsung,
                        java.util.Arrays.asList("Blu-ray remote TOP PICKS NETFLIX numeric keypad")),
                "a first query containing an uncorroborated brand must fail closed");
        Models.LocalScan orbitPanelOcr = new Models.LocalScan();
        orbitPanelOcr.textByImage.add("STATION\nPROGRAM\nDATE MONTH YEAR\nFIRST WEEK\nSECOND WEEK\n"
                + "INTERVAL\nODD\nEVEN\nWATERING DURATION MINUTES");
        java.util.List<String> orbitFingerprint =
                IdentificationPipelineV082.localDistinctiveControls(orbitPanelOcr);
        require(orbitFingerprint.contains("FIRST WEEK")
                        && orbitFingerprint.contains("SECOND WEEK")
                        && orbitFingerprint.contains("WATERING DURATION MINUTES"),
                "the Orbit panel must preserve its rare multi-label fingerprint instead of first-seen OCR order");
        String boundedPrompt = IdentificationPipelineV082.multimodalPromptForTest(
                orbitPanelOcr, "");
        require(boundedPrompt.contains("FOUR distinct queries")
                        && boundedPrompt.contains("leave model empty")
                        && boundedPrompt.contains("probable_reference")
                        && boundedPrompt.contains("FIRST WEEK")
                        && boundedPrompt.contains("SECOND WEEK"),
                "the resolver must perform bounded query diversification and allow family-level recovery");
        require(IdentificationPipelineV082.modelAtSupportedLevel(
                        "Intermezzo-style remote reference not exposed in retrieved source", false).isEmpty()
                        && "YKF400-002".equals(IdentificationPipelineV082.modelAtSupportedLevel(
                        "YKF400-002", true))
                        && IdentificationPipelineV082.isEvidenceGap(
                        "Exact photographed remote model cannot be established from the source."),
                "evidence disclaimers must never leak into the public model field or count as contradictions");

        OpenAiClient.Response philipsResolution = new OpenAiClient.Response();
        Models.Source philipsManual = new Models.Source();
        philipsManual.url = "https://www.documents.philips.com/manual.pdf";
        philipsResolution.sources.add(philipsManual);
        philipsResolution.payload = new JSONObject()
                .put("resolved_category", "television remote control")
                .put("resolved_brand", "Philips")
                .put("confirmed", false)
                .put("model_proof", "none")
                .put("evidence", "Philips manual matches the family layout")
                .put("next_photo_request", "Photograph the rear model label")
                .put("next_photo_reason", "Exact reference unavailable")
                .put("candidates", new org.json.JSONArray().put(new JSONObject()
                        .put("brand", "Philips")
                        .put("family", "smart-TV full-key remote")
                        .put("model", "Intermezzo-style remote reference not exposed in retrieved source")
                        .put("probable_reference", "YKF400-002")
                        .put("probable_reference_confidence", 72)
                        .put("source_url", philipsManual.url)
                        .put("exact_reference_complete", false)
                        .put("exact_identity_supported", false)
                        .put("source_identity_confidence", 72)
                        .put("same_entity_role", true)
                        .put("relationship_only", false)
                        .put("disproof_passed", true)
                        .put("identifier_score", 0)
                        .put("text_score", 76)
                        .put("layout_score", 88)
                        .put("web_score", 72)
                        .put("visual_reference_checked", true)
                        .put("visual_match_confidence", 84)
                        .put("major_geometry_conflict", false)
                        .put("photo_identity_supported", false)
                        .put("matched_photo_identity_fields", new org.json.JSONArray())
                        .put("matched_distinctive_features", new org.json.JSONArray()
                                .put("NETFLIX").put("TOP PICKS").put("SETTINGS").put("four color keys"))
                        .put("conflicting_distinctive_features", new org.json.JSONArray())
                        .put("candidate_facts", new org.json.JSONArray())
                        .put("contradictions", new org.json.JSONArray().put(
                                "Exact photographed remote model cannot be established from the source."))
                        .put("evidence", "same source-backed family")));
        Models.Identification parsedPhilips = new Models.Identification();
        parsedPhilips.category = "television remote control";
        parsedPhilips.categoryKey = "tv_remote";
        parsedPhilips.categoryConfidence = 99;
        IdentificationPipelineV082.applyResolutionForTest(parsedPhilips, philipsResolution, null);
                require(parsedPhilips.candidates.size() == 1
                        && parsedPhilips.candidates.get(0).model.isEmpty()
                        && "YKF400-002".equals(parsedPhilips.candidates.get(0).probableReference)
                        && PhotoIdentityPolicy.probableReferenceAllowed(
                        parsedPhilips.candidates.get(0), parsedPhilips)
                        && parsedPhilips.candidates.get(0).contradictions.isEmpty()
                        && parsedPhilips.candidates.get(0).candidateFacts.stream()
                        .anyMatch(x -> x.startsWith("evidence_gap="))
                        && "Philips smart-TV full-key remote".equals(
                        EvidencePolicy.publicTitle(parsedPhilips)),
                "the real Philips-shaped resolver payload must become a clean family result, not a prose model");

        OpenAiClient.Response orbitResolution = new OpenAiClient.Response();
        Models.Source orbitManual = new Models.Source();
        orbitManual.url = "https://example.org/orbit-watermaster-manual.pdf";
        orbitResolution.sources.add(orbitManual);
        orbitResolution.payload = new JSONObject()
                .put("resolved_category", "irrigation controller")
                .put("resolved_brand", "Orbit")
                .put("confirmed", false)
                .put("model_proof", "none")
                .put("evidence", "Manual matches the rare labels and six-station layout")
                .put("next_photo_request", "Photograph the model plate")
                .put("next_photo_reason", "Exact suffix unavailable")
                .put("candidates", new org.json.JSONArray().put(new JSONObject()
                        .put("brand", "Orbit")
                        .put("family", "WaterMaster six-station controller")
                        .put("model", "")
                        .put("source_url", orbitManual.url)
                        .put("exact_reference_complete", false)
                        .put("exact_identity_supported", false)
                        .put("source_identity_confidence", 76)
                        .put("same_entity_role", true)
                        .put("relationship_only", false)
                        .put("disproof_passed", true)
                        .put("identifier_score", 0)
                        .put("text_score", 82)
                        .put("layout_score", 86)
                        .put("web_score", 76)
                        .put("visual_reference_checked", false)
                        .put("visual_match_confidence", 0)
                        .put("major_geometry_conflict", false)
                        .put("matched_distinctive_features", new org.json.JSONArray()
                                .put("FIRST WEEK").put("SECOND WEEK")
                                .put("WATERING DURATION MINUTES").put("ODD EVEN"))
                        .put("conflicting_distinctive_features", new org.json.JSONArray())
                        .put("candidate_facts", new org.json.JSONArray()
                                .put("family_identity_supported=false")
                                .put("brand_identity_supported=false"))
                        .put("contradictions", new org.json.JSONArray().put(
                                "The exact photographed controller suffix cannot be established."))
                        .put("evidence", "same source-backed family")));
        Models.Identification parsedOrbit = new Models.Identification();
        parsedOrbit.category = "irrigation controller";
        parsedOrbit.categoryKey = "irrigation_controller";
        parsedOrbit.categoryConfidence = 98;
        IdentificationPipelineV082.applyResolutionForTest(parsedOrbit, orbitResolution, null);
        require(parsedOrbit.candidates.size() == 1
                        && parsedOrbit.candidates.get(0).model.isEmpty()
                        && parsedOrbit.candidates.get(0).totalScore >= 65
                        && parsedOrbit.candidates.get(0).candidateFacts.contains(
                        "family_identity_supported=true")
                        && "Orbit WaterMaster six-station controller".equals(
                        EvidencePolicy.publicTitle(parsedOrbit)),
                "the Orbit-shaped payload must recover the source-backed family from rare label co-occurrence");

        OpenAiClient.Response weakSamsungResolution = new OpenAiClient.Response();
        Models.Source samsungPage = new Models.Source();
        samsungPage.url = "https://www.samsung.com/support/remote";
        weakSamsungResolution.sources.add(samsungPage);
        weakSamsungResolution.payload = new JSONObject()
                .put("resolved_category", "television remote control")
                .put("resolved_brand", "Samsung")
                .put("confirmed", false)
                .put("model_proof", "none")
                .put("evidence", "generic remote page")
                .put("next_photo_request", "another photo")
                .put("next_photo_reason", "no physical match")
                .put("candidates", new org.json.JSONArray().put(new JSONObject()
                        .put("brand", "Samsung")
                        .put("family", "Smart Remote")
                        .put("model", "")
                        .put("source_url", samsungPage.url)
                        .put("exact_reference_complete", false)
                        .put("exact_identity_supported", false)
                        .put("source_identity_confidence", 40)
                        .put("same_entity_role", true)
                        .put("relationship_only", false)
                        .put("disproof_passed", false)
                        .put("identifier_score", 0)
                        .put("text_score", 35)
                        .put("layout_score", 25)
                        .put("web_score", 78)
                        .put("visual_reference_checked", false)
                        .put("visual_match_confidence", 0)
                        .put("major_geometry_conflict", false)
                        .put("matched_distinctive_features", new org.json.JSONArray().put("NETFLIX"))
                        .put("conflicting_distinctive_features", new org.json.JSONArray())
                        .put("candidate_facts", new org.json.JSONArray())
                        .put("contradictions", new org.json.JSONArray())
                        .put("evidence", "generic source")));
        Models.Identification parsedWeakSamsung = new Models.Identification();
        parsedWeakSamsung.category = "television remote control";
        parsedWeakSamsung.categoryConfidence = 99;
        IdentificationPipelineV082.applyResolutionForTest(
                parsedWeakSamsung, weakSamsungResolution, null);
        require(parsedWeakSamsung.brand.isEmpty()
                        && UniversalRecognitionLadder.assess(parsedWeakSamsung).level
                        < UniversalRecognitionLadder.BRAND,
                "a weak web-only Samsung branch must not re-enter through resolved_brand");

        Models.CandidateScore conflict = new Models.CandidateScore();
        conflict.totalScore = 96;
        conflict.layoutScore = 96;
        conflict.candidateFacts.add("geometry_relation=conflict");
        conflict.candidateFacts.add("major_geometry_mismatch=true");
        UniversalConsistencyGate.calibrateCandidate(conflict);
        require(conflict.totalScore <= 20, "geometry conflict must defeat a high score");
        require(UniversalConsistencyGate.strongCandidateConflict(conflict),
                "geometry conflict must be recognized as strong");

        JSONObject related = new JSONObject().put("same_entity_role", false)
                .put("relationship_only", true)
                .put("conflict_level", "none")
                .put("conflict_evidence_confidence", 0);
        require(UniversalConsistencyGate.strongVerificationConflict(related),
                "a related entity must fail verification");

        Models.Identification confirmed = new Models.Identification();
        confirmed.marketReady = true;
        confirmed.model = "X1";
        confirmed.modelConfidence = 92;
        require(EvidencePolicy.publicStatus(confirmed).startsWith("CONFIRMED"),
                "market-ready result must expose CONFIRMED state");

        Models.Identification incomplete = new Models.Identification();
        require(EvidencePolicy.publicStatus(incomplete).startsWith("NEED_ANOTHER_PHOTO"),
                "unsupported identity must request another photo");

        Models.CandidateScore philipsFamily = new Models.CandidateScore();
        philipsFamily.brand = "Philips";
        philipsFamily.family = "smart-TV full-key remote";
        philipsFamily.totalScore = 78;
        philipsFamily.textScore = 76;
        philipsFamily.layoutScore = 88;
        philipsFamily.webScore = 72;
        philipsFamily.candidateFacts.add("brand_identity_supported=true");
        philipsFamily.candidateFacts.add("family_identity_supported=true");
        philipsFamily.candidateFacts.add("visual_reference_checked=true");
        philipsFamily.candidateFacts.add("visual_match_confidence=84");
        philipsFamily.candidateFacts.add("exact_reference_complete=false");
        philipsFamily.candidateFacts.add("exact_identity_supported=false");
        philipsFamily.candidateFacts.add("same_entity_role=true");
        philipsFamily.candidateFacts.add("relationship_only=false");
        Models.Identification philipsFamilyOnly = new Models.Identification();
        philipsFamilyOnly.category = "television remote control";
        philipsFamilyOnly.categoryConfidence = 99;
        philipsFamilyOnly.candidates.add(philipsFamily);
        UniversalRecognitionLadder.State philipsState =
                UniversalRecognitionLadder.assess(philipsFamilyOnly);
        require(philipsState.level == UniversalRecognitionLadder.FAMILY
                        && "Philips".equals(philipsState.brand)
                        && philipsState.model.isEmpty()
                        && UniversalConsistencyGate.retrievalVisualConfidence(philipsFamily) == 84
                        && "Philips smart-TV full-key remote".equals(
                        EvidencePolicy.publicTitle(philipsFamilyOnly)),
                "Philips must surface a clean source-backed family without fabricating a model reference");

        Models.CandidateScore orbitFamily = new Models.CandidateScore();
        orbitFamily.brand = "Orbit";
        orbitFamily.family = "WaterMaster six-station controller";
        orbitFamily.totalScore = 68;
        orbitFamily.textScore = 82;
        orbitFamily.layoutScore = 68;
        orbitFamily.webScore = 76;
        orbitFamily.candidateFacts.add("brand_identity_supported=true");
        orbitFamily.candidateFacts.add("family_identity_supported=true");
        orbitFamily.candidateFacts.add("visual_reference_checked=false");
        orbitFamily.candidateFacts.add("visual_match_confidence=0");
        orbitFamily.candidateFacts.add("exact_reference_complete=false");
        orbitFamily.candidateFacts.add("exact_identity_supported=false");
        orbitFamily.candidateFacts.add("same_entity_role=true");
        orbitFamily.candidateFacts.add("relationship_only=false");
        Models.Identification orbitFamilyOnly = new Models.Identification();
        orbitFamilyOnly.category = "irrigation controller";
        orbitFamilyOnly.categoryConfidence = 98;
        orbitFamilyOnly.candidates.add(orbitFamily);
        UniversalRecognitionLadder.State orbitFamilyState =
                UniversalRecognitionLadder.assess(orbitFamilyOnly);
        require(orbitFamilyState.level == UniversalRecognitionLadder.FAMILY
                        && "Orbit".equals(orbitFamilyState.brand)
                        && orbitFamilyState.model.isEmpty(),
                "a rare label co-occurrence plus source-backed layout must recover the Orbit family without guessing a suffix");

        Models.CandidateScore hostModelWithoutBinding = new Models.CandidateScore();
        hostModelWithoutBinding.brand = "Samsung";
        hostModelWithoutBinding.family = "Blu-ray remote";
        hostModelWithoutBinding.model = "BD-D5100";
        hostModelWithoutBinding.totalScore = 90;
        hostModelWithoutBinding.textScore = 90;
        hostModelWithoutBinding.layoutScore = 90;
        hostModelWithoutBinding.webScore = 90;
        hostModelWithoutBinding.candidateFacts.add("brand_identity_supported=true");
        hostModelWithoutBinding.candidateFacts.add("family_identity_supported=true");
        hostModelWithoutBinding.candidateFacts.add("exact_reference_complete=true");
        hostModelWithoutBinding.candidateFacts.add("exact_identity_supported=false");
        hostModelWithoutBinding.candidateFacts.add("same_entity_role=true");
        hostModelWithoutBinding.candidateFacts.add("relationship_only=false");
        Models.Identification hostIdentity = new Models.Identification();
        hostIdentity.category = "remote control";
        hostIdentity.categoryConfidence = 99;
        hostIdentity.candidates.add(hostModelWithoutBinding);
        require(UniversalRecognitionLadder.assess(hostIdentity).level
                        < UniversalRecognitionLadder.MODEL,
                "a complete host model name must not become the photographed accessory model without identity support");

        JSONObject salvaged = OpenAiClient.salvageCompleteCandidates(
                "{\"candidates\":[{\"model\":\"A1\"},{\"model\":");
        require(salvaged != null && salvaged.getJSONArray("candidates").length() == 1,
                "legacy recovery must retain only complete candidate objects");

        require(!FinalistKiller.shouldRun(new Models.Identification(), new Models.Usage()),
                "the superseded finalist pass must stay disabled");

        Models.Usage budget = new Models.Usage();
        require(UniversalConsistencyGate.visionBudgetAvailable(budget),
                "a fresh scan must allow one Vision call");
        require(UniversalConsistencyGate.discoveryBudgetAvailable(budget),
                "a fresh scan must allow one identification Web Search");
        budget.visionCalls = 1;
        budget.webCalls = 1;
        require(!UniversalConsistencyGate.visionBudgetAvailable(budget),
                "a second Vision call must be blocked");
        require(!UniversalConsistencyGate.discoveryBudgetAvailable(budget)
                        && !UniversalConsistencyGate.verificationBudgetAvailable(budget),
                "a second identification/verification Web Search must be blocked");

        require(SearchEvidenceFilter.isSearchableLiteral("Orbit"),
                "a plausible foreground manufacturer word must remain searchable");
        require(!SearchEvidenceFilter.isSearchableLiteral("14:21"),
                "a clock readout must never steer retrieval");
        require(!SearchEvidenceFilter.isSearchableLiteral("CYCLE START"),
                "a control caption must never become the main query");
        require(SearchEvidenceFilter.isDistinctiveControlLabel("CYCLE START")
                        && SearchEvidenceFilter.isDistinctiveControlLabel("TOP PICKS")
                        && SearchEvidenceFilter.isDistinctiveControlLabel("NETFLIX")
                        && !SearchEvidenceFilter.isDistinctiveControlLabel("HOME")
                        && !SearchEvidenceFilter.isDistinctiveControlLabel("OK"),
                "stable uncommon controls may be co-occurrence fingerprints, while generic controls stay excluded");
        java.util.List<String> remoteControls = SearchEvidenceFilter.distinctiveControls(
                java.util.Arrays.asList("HOME", "BACK", "TOP PICKS", "NETFLIX", "OK"), 4);
        require(remoteControls.contains("TOP PICKS") && remoteControls.contains("NETFLIX")
                        && !remoteControls.contains("HOME") && !remoteControls.contains("OK"),
                "the remote fixture must retain its distinctive printed-control fingerprint");
        require(SearchEvidenceFilter.isSoftOcrLiteral("Orbit")
                        && !SearchEvidenceFilter.isSoftOcrLiteral("ST2AE")
                        && !SearchEvidenceFilter.isSoftOcrLiteral("ROLYTES"),
                "soft OCR should retain plausible mixed-case words without accepting codes or uppercase noise");
        Models.Identification literalSeed = new Models.Identification();
        literalSeed.visibleLabels.add("Orbit");
        literalSeed.visibleLabels.add("14:21");
        literalSeed.visibleLabels.add("CYCLE START");
        String safeSeed = UniversalSearchPlan.literalTextSeed(literalSeed);
        require(safeSeed.contains("Orbit") && !safeSeed.contains("14:21")
                        && !safeSeed.contains("CYCLE START"),
                "literal search seed must retain manufacturer text and drop transient/control text");

        JSONObject orbitObservation = new JSONObject()
                .put("title", "irrigation controller")
                .put("category", "irrigation controller")
                .put("category_key", "irrigation_controller")
                .put("category_confidence", 97)
                .put("family_confidence", 0)
                .put("identity_confidence", 20)
                .put("identity_reason", "Model label not visible")
                .put("brand", "Orbit")
                .put("brand_evidence", "visible_logo")
                .put("brand_role_confidence", 98)
                .put("brand_role_reason", "foreground logo")
                .put("distinctive_terms", new org.json.JSONArray().put("four station sliders"))
                .put("variant_facts", new org.json.JSONArray().put("station_controls=4"))
                .put("visible_labels", new org.json.JSONArray()
                        .put(new JSONObject().put("text", "Orbit").put("type", "brand_logo")
                                .put("entity_role", "foreground_product").put("identity_binding", false))
                        .put(new JSONObject().put("text", "14:21").put("type", "transient_display")
                                .put("entity_role", "foreground_product").put("identity_binding", false))
                        .put(new JSONObject().put("text", "CYCLE START").put("type", "control")
                                .put("entity_role", "foreground_product").put("identity_binding", false)))
                .put("spatial_signature", new org.json.JSONArray().put("display upper left; rotary dial upper right; four sliders below"))
                .put("photo_views", new org.json.JSONArray().put("single front view"))
                .put("visual_fingerprint", "wall-mounted controller with hinged cover, display, rotary dial and four vertical station sliders")
                .put("candidate_hints", new org.json.JSONArray())
                .put("fast_candidates", new org.json.JSONArray())
                .put("family", "")
                .put("model", "");
        Models.Identification orbit = new Models.Identification();
        IdentificationPipelineV082.parseObservation(orbit, orbitObservation);
        require("Orbit".equals(orbit.brand)
                        && "irrigation_controller".equals(orbit.categoryKey)
                        && orbit.searchableLabels.contains("Orbit")
                        && !orbit.searchableLabels.contains("14:21")
                        && !orbit.searchableLabels.contains("CYCLE START"),
                "the Orbit fixture must preserve category/brand and quarantine transient/control text");

        Models.LocalScan noisyCode = new Models.LocalScan();
        noisyCode.textByImage.add("ST2AE");
        noisyCode.identifiers.add(new Models.Identifier("MODEL", "ST2AE", 0,
                "mlkit_ocr_rot90_labeled"));
        Models.Identification noCorroboration = new Models.Identification();
        require(IdentificationPipelineV082.selectPrimary(noisyCode, noCorroboration) == null,
                "a short one-pass OCR code must not become a primary identifier");
        noCorroboration.identifierLabels.add("ST2AE");
        require(IdentificationPipelineV082.selectPrimary(noisyCode, noCorroboration) != null,
                "a short explicitly labelled code may be used only after visual corroboration");

        java.util.List<Models.Identifier> crossLine = LocalVisionEngine.collectIdentifiersForTest(
                "P\nN 57254-33E");
        require(crossLine.stream().noneMatch(x -> "57254-33E".equalsIgnoreCase(x.value)
                        && LocalVisionEngine.isStrongIdentifierLabel(x.label)),
                "OCR tokens on separate rows must never be fabricated into a P/N binding");
        java.util.List<Models.Identifier> sameLine = LocalVisionEngine.collectIdentifiersForTest(
                "P/N 57254-33E");
        require(sameLine.stream().anyMatch(x -> "57254-33E".equalsIgnoreCase(x.value)
                        && "PN".equalsIgnoreCase(x.label)),
                "a genuinely same-line P/N candidate must remain available for visual binding");

        Models.Identification entityBound = new Models.Identification();
        IdentificationPipelineV082.parseObservation(entityBound, new JSONObject()
                .put("visible_labels", new org.json.JSONArray()
                        .put(new JSONObject().put("text", "57254-33E").put("type", "identifier")
                                .put("entity_role", "component_or_insert").put("identity_binding", false))
                        .put(new JSONObject().put("text", "MS23H3125FK/EG").put("type", "identifier")
                                .put("entity_role", "foreground_product").put("identity_binding", true))));
        require(!entityBound.identifierLabels.contains("57254-33E")
                        && entityBound.identifierLabels.contains("MS23H3125FK/EG"),
                "only an identifier visually bound to the foreground product may constrain identity");

        Models.LocalScan explicitCode = new Models.LocalScan();
        explicitCode.textByImage.add("MODEL MS23H3125FK/EG");
        explicitCode.identifiers.add(new Models.Identifier("MODEL", "MS23H3125FK/EG", 0,
                "mlkit_ocr_original_labeled"));
        require(IdentificationPipelineV082.selectPrimary(explicitCode,
                        new Models.Identification()) == null,
                "even a strong OCR model reference must wait for product-level visual binding");
        Models.Identification explicitCodeBound = new Models.Identification();
        explicitCodeBound.identifierLabels.add("MS23H3125FK/EG");
        require(IdentificationPipelineV082.selectPrimary(explicitCode, explicitCodeBound) != null,
                "a strong explicit model reference remains usable after visual entity binding");

        OpenAiClient.Response incompleteVision = new OpenAiClient.Response();
        incompleteVision.complete = false;
        incompleteVision.payload = new JSONObject().put("observation_valid", true);
        require(!IdentificationPipelineV082.observationUsable(incompleteVision),
                "an incomplete structured response must fail closed");
        OpenAiClient.Response completeVision = new OpenAiClient.Response();
        completeVision.payload = new JSONObject().put("observation_valid", true);
        require(IdentificationPipelineV082.observationUsable(completeVision),
                "a completed valid structured observation must be accepted");

        JSONObject observerFormat = OpenAiClient.observerFormatForTest();
        require("json_schema".equals(observerFormat.getString("type"))
                        && observerFormat.getBoolean("strict")
                        && !observerFormat.getJSONObject("schema").getBoolean("additionalProperties"),
                "the primary Vision observation must use a strict closed JSON schema");
        requireClosedSchema(observerFormat.getJSONObject("schema"), "observer");
        JSONObject resolveFormat = OpenAiClient.resolveFormatForTest();
        require("json_schema".equals(resolveFormat.getString("type"))
                        && resolveFormat.getBoolean("strict"),
                "the one-pass Web resolver must use Structured Outputs");
        requireClosedSchema(resolveFormat.getJSONObject("schema"), "resolver");
        JSONObject multimodalFormat = OpenAiClient.multimodalResolveFormatForTest();
        require("json_schema".equals(multimodalFormat.getString("type"))
                        && multimodalFormat.getBoolean("strict"),
                "the v0.82 image+web request must use Structured Outputs");
        requireClosedSchema(multimodalFormat.getJSONObject("schema"), "multimodal");
        JSONObject multimodalCandidate = multimodalFormat.getJSONObject("schema")
                .getJSONObject("properties").getJSONObject("resolution")
                .getJSONObject("properties").getJSONObject("candidates")
                .getJSONObject("items").getJSONObject("properties");
        require(multimodalCandidate.has("visual_reference_checked")
                        && multimodalCandidate.has("visual_match_confidence")
                        && multimodalCandidate.has("major_geometry_conflict")
                        && multimodalCandidate.has("probable_reference")
                        && multimodalCandidate.has("photo_identity_supported")
                        && multimodalCandidate.has("matched_photo_identity_fields"),
                "each candidate must carry explicit photo-to-source geometry evidence");
        JSONObject multimodalLabel = multimodalFormat.getJSONObject("schema")
                .getJSONObject("properties").getJSONObject("observation")
                .getJSONObject("properties").getJSONObject("visible_labels")
                .getJSONObject("items").getJSONObject("properties");
        require(multimodalLabel.has("entity_role") && multimodalLabel.has("identity_binding"),
                "every observed label must carry explicit foreground-entity binding");
        JSONObject multimodalPhotoIdentity = multimodalFormat.getJSONObject("schema")
                .getJSONObject("properties").getJSONObject("observation")
                .getJSONObject("properties").getJSONObject("photo_identity");
        require(multimodalPhotoIdentity.has("additionalProperties")
                        && !multimodalPhotoIdentity.getBoolean("additionalProperties")
                        && multimodalPhotoIdentity.getJSONObject("properties")
                        .has("overlay_or_watermark"),
                "complete photo identity must be schema-locked and explicitly quarantine overlays");
        require(IdentificationPipelineV082.firstQueryIsBrandNeutral(java.util.Arrays.asList(
                        "TV remote control \"TOP PICKS\" \"NETFLIX\" diamond navigation", "site:philips.com remote"))
                        && !IdentificationPipelineV082.firstQueryIsBrandNeutral(java.util.Arrays.asList(
                        "site:lg.com TV remote numeric keypad", "black TV remote")),
                "without a visible brand, the first query must be domain-unrestricted and brand-neutral");

        Models.Identification internalFacts = new Models.Identification();
        Models.CandidateScore laneA = new Models.CandidateScore();
        laneA.model = "First candidate";
        laneA.candidateFacts.add("search_lane=structure");
        laneA.candidateFacts.add("geometry_relation=same");
        Models.CandidateScore laneB = new Models.CandidateScore();
        laneB.model = "Second candidate";
        laneB.candidateFacts.add("search_lane=literal");
        laneB.candidateFacts.add("geometry_relation=compatible");
        internalFacts.candidates.add(laneA);
        internalFacts.candidates.add(laneB);
        require(ClarificationPlanner.plan(internalFacts) == null,
                "retrieval metadata must never become a user clarification");
        require(!ClarificationPlanner.isUserVerifiableKey("search_lane"),
                "search_lane must be rejected fail-closed");
        boolean rejectedInternalPlan = false;
        try {
            ClarificationPlanner.Plan forged = new ClarificationPlanner.Plan(
                    "internal", "search_lane", java.util.Arrays.asList("structure", "literal"));
            ClarificationEngine.refine(internalFacts, forged, "structure", "",
                    new OpenAiClient("offline-test"), new Models.Usage());
        } catch (IllegalArgumentException expected) {
            rejectedInternalPlan = true;
        }
        require(rejectedInternalPlan,
                "the clarification engine must reject a forged internal-metadata plan");

        Models.Identification physicalCodes = new Models.Identification();
        Models.CandidateScore codeA = new Models.CandidateScore();
        codeA.model = "YKF400-002 / 996596000116";
        codeA.candidateFacts.add("exact_reference_complete=true");
        codeA.candidateFacts.add("source_exact_reference=true");
        codeA.candidateFacts.add("exact_identity_supported=true");
        codeA.candidateFacts.add("same_entity_role=true");
        codeA.candidateFacts.add("relationship_only=false");
        Models.CandidateScore codeB = new Models.CandidateScore();
        codeB.model = "996598001054";
        codeB.candidateFacts.add("exact_reference_complete=true");
        codeB.candidateFacts.add("source_exact_reference=true");
        codeB.candidateFacts.add("exact_identity_supported=true");
        codeB.candidateFacts.add("same_entity_role=true");
        codeB.candidateFacts.add("relationship_only=false");
        Models.CandidateScore relatedFamily = new Models.CandidateScore();
        relatedFamily.model = "65PUS8601-series remote";
        physicalCodes.candidates.add(codeA);
        physicalCodes.candidates.add(codeB);
        physicalCodes.candidates.add(relatedFamily);
        ClarificationPlanner.Plan codePlan = ClarificationPlanner.plan(physicalCodes);
        require(codePlan != null && "model_code".equals(codePlan.factKey),
                "distinct code-shaped model references should produce a physical check");
        require(codePlan.options.size() == 2
                        && codePlan.options.contains("YKF400-002 / 996596000116")
                        && codePlan.options.contains("996598001054"),
                "only code-shaped candidate identities should be shown");

        Models.Identification regionalCodes = new Models.Identification();
        Models.CandidateScore regionalA = new Models.CandidateScore();
        regionalA.model = "MS23H3125FK/EG";
        regionalA.candidateFacts.add("exact_reference_complete=true");
        regionalA.candidateFacts.add("source_exact_reference=true");
        regionalA.candidateFacts.add("exact_identity_supported=true");
        regionalA.candidateFacts.add("same_entity_role=true");
        regionalA.candidateFacts.add("relationship_only=false");
        Models.CandidateScore regionalB = new Models.CandidateScore();
        regionalB.model = "MS23F301TAK/ZA";
        regionalB.candidateFacts.add("exact_reference_complete=true");
        regionalB.candidateFacts.add("source_exact_reference=true");
        regionalB.candidateFacts.add("exact_identity_supported=true");
        regionalB.candidateFacts.add("same_entity_role=true");
        regionalB.candidateFacts.add("relationship_only=false");
        Models.CandidateScore incompleteReference = new Models.CandidateScore();
        incompleteReference.model = "MS23DG4504A";
        incompleteReference.candidateFacts.add("exact_reference_complete=false");
        incompleteReference.candidateFacts.add("source_exact_reference=true");
        incompleteReference.candidateFacts.add("exact_identity_supported=true");
        incompleteReference.candidateFacts.add("same_entity_role=true");
        incompleteReference.candidateFacts.add("relationship_only=false");
        regionalCodes.candidates.add(regionalA);
        regionalCodes.candidates.add(regionalB);
        regionalCodes.candidates.add(incompleteReference);
        ClarificationPlanner.Plan regionalPlan = ClarificationPlanner.plan(regionalCodes);
        require(regionalPlan != null
                        && regionalPlan.options.contains("MS23H3125FK/EG")
                        && regionalPlan.options.contains("MS23F301TAK/ZA")
                        && !regionalPlan.options.contains("MS23DG4504A"),
                "regional slash suffixes must survive while incomplete references stay hidden");

        Models.Identification hostPlayerCodes = new Models.Identification();
        Models.CandidateScore hostA = new Models.CandidateScore();
        hostA.model = "BD-D5100";
        hostA.candidateFacts.add("exact_reference_complete=true");
        hostA.candidateFacts.add("source_exact_reference=true");
        hostA.candidateFacts.add("exact_identity_supported=false");
        hostA.candidateFacts.add("same_entity_role=true");
        hostA.candidateFacts.add("relationship_only=false");
        Models.CandidateScore hostB = new Models.CandidateScore();
        hostB.model = "BD-E5900";
        hostB.candidateFacts.add("exact_reference_complete=true");
        hostB.candidateFacts.add("source_exact_reference=true");
        hostB.candidateFacts.add("exact_identity_supported=false");
        hostB.candidateFacts.add("same_entity_role=true");
        hostB.candidateFacts.add("relationship_only=false");
        hostPlayerCodes.candidates.add(hostA);
        hostPlayerCodes.candidates.add(hostB);
        require(ClarificationPlanner.plan(hostPlayerCodes) == null,
                "host-player model numbers must never be offered as codes printed on a remote");

        Models.Identification card = new Models.Identification();
        card.category = "sports trading card";
        card.categoryKey = "sports_trading_card";
        card.categoryConfidence = 99;
        card.localScan = new Models.LocalScan();
        card.localScan.textByImage.add("Lamine Yamal\nSUPER NOVA\nFC BARCELONA");
        card.localScan.textByImage.add("PANINI\n2024-25 PANINI OBSIDIAN SOCCER\nNo. 8\n8/9");
        card.photoIdentityComplete = false;
        card.photoIdentityPhysicalBinding = true;
        card.photoIdentityOverlayOrWatermark = false;
        card.photoIdentityConfidence = 88;
        card.photoIdentityKind = "composite_markings";
        card.photoIdentityName = "";
        card.photoViews.add("front");
        card.photoViews.add("back");
        card.photoIdentityFields.add("Panini");
        card.photoIdentityFields.add("2024-25 Panini Obsidian Soccer");
        card.photoIdentityFields.add("Supernova");
        card.photoIdentityFields.add("Lamine Yamal");
        card.photoIdentityFields.add("FC Barcelona");
        card.photoIdentityFields.add("No. 8");
        card.visibleLabels.addAll(card.photoIdentityFields);
        card.visibleLabels.add("8/9");
        PhotoIdentityPolicy.consolidateObservation(card, card.localScan);
        require(PhotoIdentityPolicy.observationStrong(card)
                        && card.photoIdentityName.contains("Lamine Yamal")
                        && card.photoIdentityCode.isEmpty(),
                "the real front/back card tuple must consolidate before deterministic serial binding");
        OpenAiClient.Response cardResolution = new OpenAiClient.Response();
        Models.Source checklist = new Models.Source();
        checklist.url = "https://example.org/2024-25-panini-obsidian-soccer-checklist";
        checklist.title = "2024-25 Panini Obsidian Soccer checklist";
        checklist.snippet = "Supernova #8 Lamine Yamal FC Barcelona";
        cardResolution.sources.add(checklist);
        cardResolution.payload = new JSONObject()
                .put("resolved_category", "sports trading card")
                .put("resolved_brand", "Panini")
                .put("confirmed", false)
                .put("model_proof", "none")
                .put("evidence", "Checklist and photographed front/back support the same complete tuple")
                .put("next_photo_request", "")
                .put("next_photo_reason", "")
                .put("candidates", new org.json.JSONArray().put(new JSONObject()
                        .put("brand", "Panini")
                        .put("family", "Obsidian Soccer Supernova")
                        .put("model", "Panini Obsidian Soccer Supernova #8")
                        .put("probable_reference", "Supernova #8")
                        .put("probable_reference_confidence", 99)
                        .put("source_url", checklist.url)
                        .put("exact_reference_complete", true)
                        .put("exact_identity_supported", false)
                        .put("source_identity_confidence", 98)
                        .put("same_entity_role", true)
                        .put("relationship_only", false)
                        .put("disproof_passed", true)
                        .put("identifier_score", 98)
                        .put("text_score", 99)
                        .put("layout_score", 72)
                        .put("web_score", 98)
                        .put("visual_reference_checked", false)
                        .put("visual_match_confidence", 0)
                        .put("major_geometry_conflict", false)
                        .put("photo_identity_supported", false)
                        .put("matched_photo_identity_fields", new org.json.JSONArray())
                        .put("matched_distinctive_features", new org.json.JSONArray())
                        .put("conflicting_distinctive_features", new org.json.JSONArray())
                        .put("candidate_facts", new org.json.JSONArray())
                        .put("contradictions", new org.json.JSONArray().put(
                                "The visible 8/9 marking is not established as a standard parallel or serial designation by the retrieved checklist."))
                        .put("evidence", "Checklist lists Supernova #8 as Lamine Yamal, FC Barcelona. Source lists the Supernova insert in the 2024-25 Panini Obsidian Soccer release.")));
        IdentificationPipelineV082.applyResolutionForTest(card, cardResolution, null);
        require(card.marketReady && card.disproofPassed
                        && "photo_complete_identity".equals(card.modelProof)
                        && card.model.contains("Lamine Yamal")
                        && card.model.contains("8/9")
                        && EvidencePolicy.publicStatus(card).startsWith("CONFIRMED"),
                "a complete front/back collectible identity must confirm with its physical 8/9 print run");

        Models.Identification watermarkPhone = new Models.Identification();
        watermarkPhone.photoIdentityComplete = true;
        watermarkPhone.photoIdentityPhysicalBinding = false;
        watermarkPhone.photoIdentityOverlayOrWatermark = true;
        watermarkPhone.photoIdentityConfidence = 99;
        watermarkPhone.photoIdentityKind = "composite_markings";
        watermarkPhone.photoIdentityName = "Samsung Galaxy S24 Ultra";
        watermarkPhone.photoIdentityCode = "SM-S928...";
        watermarkPhone.photoIdentityFields.add("brand=Samsung");
        watermarkPhone.photoIdentityFields.add("model=Galaxy S24 Ultra");
        require(!PhotoIdentityPolicy.observationStrong(watermarkPhone),
                "a phone-name watermark and truncated code must never become physical identity proof");

        Models.Identification phone = new Models.Identification();
        phone.category = "smartphone";
        phone.categoryKey = "smartphone";
        phone.categoryConfidence = 99;
        phone.photoIdentityComplete = false;
        phone.photoIdentityPhysicalBinding = true;
        phone.photoIdentityOverlayOrWatermark = false;
        phone.photoIdentityConfidence = 85;
        phone.photoIdentityKind = "identity_label";
        phone.photoIdentityName = "";
        phone.photoIdentityCode = "";
        phone.photoIdentityFields.add("brand=Samsung");
        phone.photoIdentityFields.add("model=Galaxy S24 Ultra");
        phone.visibleLabels.add("SAMSUNG");
        phone.visibleLabels.add("Galaxy S24 Ultra");
        phone.visibleLabels.add("SM-S928?");
        phone.identifierLabels.add("SM-S928?");
        phone.localScan = new Models.LocalScan();
        phone.localScan.textByImage.add("Galaxy S24 Ultra\nSM S9288DS\nSM-S9288DS\nSM-S9288DS");
        OpenAiClient.Response phoneResolution = new OpenAiClient.Response();
        Models.Source phoneSource = new Models.Source();
        phoneSource.url = "https://www.samsung.com/support/model/SM-S928B/DS";
        phoneSource.title = "Galaxy S24 Ultra SM-S928B/DS support";
        phoneResolution.sources.add(phoneSource);
        phoneResolution.payload = new JSONObject()
                .put("resolved_category", "smartphone")
                .put("resolved_brand", "Samsung")
                .put("confirmed", false)
                .put("model_proof", "none")
                .put("evidence", "Identity screen and official support agree")
                .put("next_photo_request", "")
                .put("next_photo_reason", "")
                .put("candidates", new org.json.JSONArray().put(new JSONObject()
                        .put("brand", "Samsung").put("family", "Galaxy S24 Ultra")
                        .put("model", "SM-S928B/DS")
                        .put("probable_reference", "").put("probable_reference_confidence", 0)
                        .put("source_url", "Samsung Galaxy S24 Ultra support/product information")
                        .put("exact_reference_complete", true)
                        .put("exact_identity_supported", false)
                        .put("source_identity_confidence", 96)
                        .put("same_entity_role", true).put("relationship_only", false)
                        .put("disproof_passed", true)
                        .put("identifier_score", 96).put("text_score", 94)
                        .put("layout_score", 88).put("web_score", 92)
                        .put("visual_reference_checked", false)
                        .put("visual_match_confidence", 0)
                        .put("major_geometry_conflict", false)
                        .put("photo_identity_supported", false)
                        .put("matched_photo_identity_fields", new org.json.JSONArray())
                        .put("matched_distinctive_features", new org.json.JSONArray())
                        .put("conflicting_distinctive_features", new org.json.JSONArray())
                        .put("candidate_facts", new org.json.JSONArray())
                        .put("contradictions", new org.json.JSONArray())
                        .put("evidence", "complete physical identity")));
        IdentificationPipelineV082.applyResolutionForTest(phone, phoneResolution, null);
        require(phone.marketReady && "photo_complete_identity".equals(phone.modelProof)
                        && phone.model.contains("SM-S928B/DS")
                        && phone.photoIdentityCode.equals("SM-S928B/DS")
                        && phone.candidates.get(0).candidateFacts.contains(
                        "ocr_code_binding_confidence=94")
                        && phone.candidates.get(0).candidateFacts.contains(
                        "source_url_recovered=true"),
                "repeated B/8 OCR ambiguity plus the visible code prefix and grounded family must reconcile to the exact physical code");

        Models.Identification oven = new Models.Identification();
        oven.category = "built-in electric oven";
        oven.categoryKey = "built_in_electric_oven";
        oven.photoIdentityKind = "none";
        oven.photoIdentityPhysicalBinding = false;
        oven.visibleLabels.add("ATLANTIC");
        oven.visibleLabels.add("250");
        oven.visibleLabels.add("200");
        oven.photoViews.add("single front view");
        oven.localScan = new Models.LocalScan();
        oven.localScan.textByImage.add("ATLANTIC\n250\n200\n150\n100\n50");
        PhotoIdentityPolicy.consolidateObservation(oven, oven.localScan);
        require(!PhotoIdentityPolicy.observationStrong(oven)
                        && PhotoIdentityPolicy.resolvedCodeBindingConfidence(
                        oven, null, "ATL-OS20-GK", "ATL OS-series") == 0,
                "an oven face with brand and temperature scale but no physical model plate must remain unconfirmed");

        System.out.println("PipelineRegressionTest: PASS");
    }
}
