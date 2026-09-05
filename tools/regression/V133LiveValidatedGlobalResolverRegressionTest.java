package com.flipcheck.nativebeta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Deterministic v1.33 checks for candidate isolation, grounding and final-state invariants. */
public final class V133LiveValidatedGlobalResolverRegressionTest {
    private static JSONObject fixtures;
    private static int passed;

    public static void main(String[] args) throws Exception {
        fixtures = new JSONObject(read("tools/regression/fixtures/v132_real_image_replays.json"));
        candidateRowsNeverEnterTheLedgerBeforeSelection();
        multiRecordPagesCannotMasqueradeAsExactRecords();
        semanticHierarchyAcceptsExtensionsButRejectsDifferentPublishers();
        compatibleObservedHierarchyDoesNotCreateAConflict();
        groundedBrandRequiresLiteralCorroboration();
        catalogSymbolClassificationRemainsInference();
        toppsReplayClosesTheCoreAndRejectsTheLookalike();
        kobeReplayKeepsTheFullSeasonAndIsolatesChecklistRows();
        vileplumeReplayRejectsAGenericSetPageAndKeepsTheEdition();
        remoteSearchMustBeginWithoutAHypothesizedBrand();
        inferredRemoteBrandCannotEnterThePublicTitle();
        viewsAndStructuredLedgerSurviveToTheFinalState();
        sportsBaseRoleIsNotAnEdition();
        oneReducerOwnsTheV2Result();
        releaseMetadataAndProductionHardcodeGate();
        System.out.println("V133LiveValidatedGlobalResolverRegressionTest: PASS (" + passed + "/15)");
    }

    private static void candidateRowsNeverEnterTheLedgerBeforeSelection() throws Exception {
        JSONObject f = fixtures.getJSONObject("kobe");
        ImmutableEvidenceLedgerV2 ledger = new ImmutableEvidenceLedgerV2();
        ObservationExtractorV2.Result p = ObservationExtractorV2.ingestPrimary(f.getJSONObject("primary"), ledger);
        TypedFieldNormalizerV2.normalize(ledger);
        JSONObject web = new JSONObject(f.getJSONObject("web").toString());
        JSONArray rows = web.getJSONArray("candidates");
        rows.getJSONObject(0).put("candidate_id", "card-81").put("source_record_id", "base-81")
                .put("source_page_scope", "CHECKLIST_ROW").put("identity_level", "CATALOG_IDENTITY");
        rows.put(new JSONObject(rows.getJSONObject(0).toString()).put("candidate_id", "insert-3")
                .put("source_record_id", "insert-3").put("card_number", "3")
                .put("product_line", "Planet Metal").put("source_url", "https://catalog.example/record/3"));
        rows.put(new JSONObject(rows.getJSONObject(0).toString()).put("candidate_id", "championship-86")
                .put("source_record_id", "championship-86").put("card_number", "86")
                .put("product_line", "Championship").put("source_url", "https://catalog.example/record/86"));
        List<IdentityCandidateV2> parsed = CandidateRetrieverV2.parse(web, DomainProfileRouterV2.Profile.SPORTS_CARD, ledger);
        require(ledger.byLevel(EvidenceAtom.EpistemicLevel.RETRIEVED).isEmpty(), "retrieval polluted the global ledger before selection");
        List<IdentityCandidateV2> ranked = CandidateVerifierV2.verify(parsed, ledger, DomainProfileRouterV2.Profile.SPORTS_CARD);
        IdentityCandidateV2 winner = winning(ranked);
        require(winner != null && "81".equals(winner.value("catalogCardNumber")), "the exact #81 record did not win");
        CandidateRetrieverV2.commitWinner(winner, ledger);
        for (EvidenceAtom atom : ledger.byLevel(EvidenceAtom.EpistemicLevel.RETRIEVED))
            require(!"3".equals(atom.normalizedValue) && !"86".equals(atom.normalizedValue), "a rejected checklist row entered the ledger");
        require(p.views.size() == 2, "front/back views were not parsed");
        pass();
    }

    private static void multiRecordPagesCannotMasqueradeAsExactRecords() throws Exception {
        IdentityCandidateV2 candidate = candidate(DomainProfileRouterV2.Profile.TCG_CARD);
        candidate.exactReference = true;
        candidate.sourceRecordId = "page-summary";
        candidate.sourcePageScope = "MULTI_RECORD_PAGE";
        candidate.fields.put("cardName", "Example");
        candidate.fields.put("setName", "Example Set");
        candidate.fields.put("catalogCardNumber", "1/100");
        CatalogConsistencyV3.Result result = CatalogConsistencyV3.check(candidate, candidate.domain);
        require(!result.coherent && result.reason.contains("isolated_record"), "multi-record page accepted as an exact row");
        JSONObject payload = webPayload("sports_card", "Example Player", "81");
        List<IdentityCandidateV2> bound = CandidateRetrieverV2.parse(payload, DomainProfileRouterV2.Profile.SPORTS_CARD, new ImmutableEvidenceLedgerV2());
        Models.Source actual = new Models.Source(); actual.url = "https://catalog.example/row-1?utm_source=test";
        List<Models.Source> toolSources = new ArrayList<>(); toolSources.add(actual);
        CandidateRetrieverV2.bindToolSources(bound, toolSources);
        require(!bound.get(0).rejected, "candidate URL was not bound to an actual Web-tool source");
        List<IdentityCandidateV2> unbound = CandidateRetrieverV2.parse(payload, DomainProfileRouterV2.Profile.SPORTS_CARD, new ImmutableEvidenceLedgerV2());
        CandidateRetrieverV2.bindToolSources(unbound, new ArrayList<>());
        require(unbound.get(0).rejected && unbound.get(0).rejectionReason.contains("web_tool_sources"), "unverified generated URL was accepted");
        pass();
    }

    private static void semanticHierarchyAcceptsExtensionsButRejectsDifferentPublishers() {
        require(SemanticRelationV3.compatible(SemanticRelationV3.relate("productLine", "Chrome", "Topps Chrome Basketball")), "compatible hierarchy rejected");
        require(SemanticRelationV3.relate("manufacturer", "Topps", "Upper Deck") == SemanticRelationV3.Relation.INCOMPATIBLE, "different publishers treated as compatible");
        require(SemanticRelationV3.compatible(SemanticRelationV3.relate("productReleaseYear", "1997", "1997-98")), "release year and full season cannot coexist");
        pass();
    }

    private static void compatibleObservedHierarchyDoesNotCreateAConflict() {
        ImmutableEvidenceLedgerV2 ledger = new ImmutableEvidenceLedgerV2();
        observed(ledger, "productLine", "Chrome", 0);
        observed(ledger, "productLine", "Topps Chrome Basketball", 0);
        require(ConflictResolverV2.resolve(ledger, DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT).isEmpty(), "parent/child labels created a false conflict");
        pass();
    }

    private static void groundedBrandRequiresLiteralCorroboration() throws Exception {
        ImmutableEvidenceLedgerV2 withoutOcr = new ImmutableEvidenceLedgerV2();
        ObservationExtractorV2.ingestPrimary(primaryWithFact("manufacturer", "Upper Deck", "manufacturer_text"), withoutOcr);
        require(!withoutOcr.hasObserved("manufacturer"), "uncorroborated Vision brand became observed");
        Models.LocalScan local = new Models.LocalScan();
        local.textByImage.add("TOPPS CHROME UPDATE SERIES");
        ImmutableEvidenceLedgerV2 withOcr = new ImmutableEvidenceLedgerV2();
        ObservationExtractorV2.ingestLocal(local, withOcr);
        ObservationExtractorV2.ingestPrimary(primaryWithFact("manufacturer", "Topps", "manufacturer_text"), withOcr);
        require(withOcr.hasObserved("manufacturer"), "literal OCR + localized Vision brand was not grounded");
        pass();
    }

    private static void catalogSymbolClassificationRemainsInference() throws Exception {
        ImmutableEvidenceLedgerV2 ledger = new ImmutableEvidenceLedgerV2();
        ObservationExtractorV2.ingestPrimary(primaryWithFact("set_name", "Base Set", "set_symbol"), ledger);
        require(!ledger.hasObserved("setName") && ledger.strongest("setName", EvidenceAtom.EpistemicLevel.INFERRED) != null,
                "a catalog interpretation of a visual symbol became observed");
        pass();
    }

    private static void toppsReplayClosesTheCoreAndRejectsTheLookalike() throws Exception {
        Models.Identification id = replay("topps", fixtures.getJSONObject("topps").getJSONObject("web"));
        require("2025-26 Topps Chrome Basketball Update Series".equals(id.title), "sealed title is incomplete: " + id.title);
        require("CONFIRMED".equals(id.coreIdentityStatus) && "FORMAT_PENDING".equals(id.exactIdentityStatus), "format/SKU blocked the sealed core");
        require(!id.title.contains("Upper Deck") && id.retrievedRejectedSources.contains("upper-deck"), "lookalike source was not isolated and rejected: " + id.retrievedRejectedSources + " trace=" + id.v2CandidateTrace);
        require(!id.candidateWinnerId.isEmpty() && id.disproofPassed, "grounded tournament/disproof did not produce a winner");
        pass();
    }

    private static void kobeReplayKeepsTheFullSeasonAndIsolatesChecklistRows() throws Exception {
        JSONObject web = new JSONObject(fixtures.getJSONObject("kobe").getJSONObject("web").toString());
        JSONArray rows = web.getJSONArray("candidates");
        rows.getJSONObject(0).put("candidate_id", "base-81").put("source_record_id", "base-81")
                .put("source_page_scope", "CHECKLIST_ROW").put("identity_level", "CATALOG_IDENTITY");
        rows.put(new JSONObject(rows.getJSONObject(0).toString()).put("candidate_id", "insert-3").put("source_record_id", "insert-3").put("card_number", "3"));
        rows.put(new JSONObject(rows.getJSONObject(0).toString()).put("candidate_id", "championship-86").put("source_record_id", "championship-86").put("card_number", "86"));
        Models.Identification id = replay("kobe", web);
        require("1997-98 SkyBox Metal Universe Kobe Bryant #81".equals(id.title), "full sports season/title not preserved: " + id.title);
        require("1996-97".equals(id.statisticsSeason) && "1997-98".equals(id.physicalReleaseYear), "statistics contaminated the release season");
        require("81".equals(id.sourceConfirmedCatalogNumber) && !id.v2RetrievedFacts.contains("catalogCardNumber=3") && !id.v2RetrievedFacts.contains("catalogCardNumber=86"), "rejected rows contaminated the winner");
        require("CONFIRMED".equals(id.coreIdentityStatus) && id.tournamentMargin > 0, "sports tournament did not close deterministically");
        pass();
    }

    private static void vileplumeReplayRejectsAGenericSetPageAndKeepsTheEdition() throws Exception {
        JSONObject web = new JSONObject(fixtures.getJSONObject("vileplume").getJSONObject("web").toString());
        JSONArray rows = web.getJSONArray("candidates");
        rows.getJSONObject(0).put("candidate_id", "jungle-15").put("source_record_id", "jungle-15")
                .put("source_page_scope", "CHECKLIST_ROW").put("identity_level", "CATALOG_IDENTITY");
        rows.put(new JSONObject(rows.getJSONObject(0).toString()).put("candidate_id", "generic-base-page")
                .put("set_name", "Base Set").put("source_url", "https://catalog.example/base-set")
                .put("source_record_id", "page").put("source_page_scope", "GENERIC_PAGE")
                .put("source_quality", 98).put("exact_reference", true).put("edition", "Unlimited"));
        Models.Identification id = replay("vileplume", web);
        require("1999 Pokémon Jungle Vileplume #15/64".equals(id.title), "typed catalog consistency selected the wrong family: " + id.title);
        require("FIRST_EDITION".equals(id.edition) && "HOLO".equals(id.finish) && "English".equals(id.language), "physical edition/finish/language were lost");
        require("CONFIRMED".equals(id.coreIdentityStatus) && id.familyConfidence > 0 && !id.title.contains("Base Set") && !id.title.contains("Unlimited"), "generic set page contaminated the final identity");
        pass();
    }

    private static void remoteSearchMustBeginWithoutAHypothesizedBrand() throws Exception {
        JSONObject f = fixtures.getJSONObject("philips");
        ImmutableEvidenceLedgerV2 ledger = new ImmutableEvidenceLedgerV2();
        ObservationExtractorV2.Result primary = ObservationExtractorV2.ingestPrimary(f.getJSONObject("primary"), ledger);
        String biased = CandidateRetrieverV2.neutralQueryViolation(f.getJSONObject("web"), DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL, primary.hypotheses, ledger);
        require(biased.contains("inferred_identity"), "brand-biased first query was not rejected");
        JSONObject neutral = new JSONObject(f.getJSONObject("web").toString()).put("queries", new JSONArray().put("television remote Netflix Sources Info Home Back Text Subtitle numeric keypad button layout"));
        require(CandidateRetrieverV2.neutralQueryViolation(neutral, DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL, primary.hypotheses, ledger).isEmpty(), "neutral layout query was rejected");
        pass();
    }

    private static void inferredRemoteBrandCannotEnterThePublicTitle() throws Exception {
        JSONObject web = new JSONObject(fixtures.getJSONObject("philips").getJSONObject("web").toString());
        JSONArray rows = new JSONArray();
        JSONObject ungrounded = new JSONObject(web.getJSONArray("candidates").getJSONObject(1).toString()).put("brand", "Samsung")
                .put("product_line", "TV remote").put("source_url", "https://example.invalid/generic-support")
                .put("source_page_scope", "GENERIC_PAGE").put("layout_match", 98).put("source_quality", 95)
                .put("matched_observed_fields", new JSONArray().put("controlLayout").put("shortcutButtons").put("numericKeypad"));
        for (String field : new String[]{"control_layout", "shortcut_buttons", "navigation_layout", "numeric_keypad", "voice_control", "layout_signature"}) ungrounded.remove(field);
        rows.put(ungrounded);
        web.put("candidates", rows);
        Models.Identification id = replay("philips", web);
        require(id.brand.isEmpty() && "Telecomando per TV".equals(id.title), "inferred-only brand leaked into title: " + id.title);
        require(!"CONFIRMED".equals(id.coreIdentityStatus) && "rear_label_or_model_code".equals(id.requestedPhotoReason), "safe remote fallback/request is incoherent");
        pass();
    }

    private static void viewsAndStructuredLedgerSurviveToTheFinalState() throws Exception {
        for (String key : new String[]{"topps", "kobe", "vileplume", "philips"}) {
            Models.Identification id = replay(key, fixtures.getJSONObject(key).getJSONObject("web"));
            require(id.uploadedImageCount > 0 && !id.photoViews.isEmpty(), "views=[] for " + key);
            require("STRUCTURED".equals(id.evidenceLedgerStatus) && !id.evidenceLedger.isEmpty(), "structured evidence missing for " + key);
        }
        pass();
    }

    private static void sportsBaseRoleIsNotAnEdition() throws Exception {
        JSONObject payload = webPayload("sports_card", "Example Player", "81").put("queries", new JSONArray().put("neutral checklist query"));
        payload.getJSONArray("candidates").getJSONObject(0).put("edition", "Base Set");
        IdentityCandidateV2 candidate = CandidateRetrieverV2.parse(payload, DomainProfileRouterV2.Profile.SPORTS_CARD, new ImmutableEvidenceLedgerV2()).get(0);
        require("BASE".equals(candidate.value("cardRole")) && candidate.value("edition").isEmpty(), "sports BASE role was rendered as edition");
        pass();
    }

    private static void oneReducerOwnsTheV2Result() throws Exception {
        String engine = read("app/src/main/java/com/flipcheck/nativebeta/UniversalIdentityEngineV2.java");
        String route = read("app/src/main/java/com/flipcheck/nativebeta/IdentificationEngine.java");
        String service = read("app/src/main/java/com/flipcheck/nativebeta/AnalysisForegroundService.java");
        require(route.indexOf("UniversalIdentityEngineV2.enabled()") < route.indexOf("IdentificationPipelineV082.enabled()"), "legacy pipeline precedes V2");
        require(count(engine, "FinalStateReducerV2.reduce") == 1 && !engine.contains("IdentificationEngine.finalizeOutput") && !engine.contains("IdentificationEngine.collectStage"), "V2 delegates final state to legacy code");
        require(service.contains("if (FinalStateReducerV2.VERSION.equals(id.finalStateReducerVersion))") && service.indexOf("UniversalRecognitionLadder.apply(id)") > service.indexOf("} else {"), "installed-APK service applies a legacy closer after the V2 reducer");
        require(read("app/src/main/java/com/flipcheck/nativebeta/FinalStateReducerV2.java").contains("FinalStateReducer/3"), "v1.33 reducer revision is not active");
        pass();
    }

    private static void releaseMetadataAndProductionHardcodeGate() throws Exception {
        String gradle = read("app/build.gradle");
        require(gradle.contains("versionCode 150") && gradle.contains("versionName '1.34-evidence-integrity'"), "current candidate version metadata missing");
        String production = readTree(Paths.get("app/src/main/java/com/flipcheck/nativebeta")).toLowerCase();
        for (String forbidden : new String[]{"topps", "kobe bryant", "vileplume", "philips"}) require(!production.contains(forbidden), "fixture literal in production: " + forbidden);
        pass();
    }

    private static Models.Identification replay(String key, JSONObject web) throws Exception {
        JSONObject f = fixtures.getJSONObject(key);
        Models.Usage usage = new Models.Usage();
        usage.visionCalls = f.has("focused") ? 2 : 1;
        usage.webCalls = 1;
        usage.requests = usage.visionCalls + 1;
        usage.costUsd = usage.visionCalls == 2 ? .019d : .015d;
        Models.Identification id = UniversalIdentityEngineV2.replay(new Models.LocalScan(), f.getJSONObject("primary"), f.optJSONObject("focused"), web, usage);
        require(id.estimatedAnalysisCostUsd <= .025d, "budget exceeded in replay " + key);
        require(!id.closureResult || !"UNRESOLVED".equals(id.coreIdentityStatus), "closure/core invariant failed " + key);
        return id;
    }

    private static IdentityCandidateV2 candidate(DomainProfileRouterV2.Profile profile) {
        IdentityCandidateV2 candidate = new IdentityCandidateV2("test", profile, "WEB_IDENTITY");
        candidate.retrieved = true;
        candidate.sourceUrl = "https://catalog.example/page";
        candidate.webSourceQuality = 90;
        return candidate;
    }

    private static JSONObject primaryWithFact(String key, String value, String role) throws Exception {
        return new JSONObject().put("content_sufficient", true).put("category", key.contains("set") ? "tcg_card" : "sealed_trading_card_product")
                .put("views", new JSONArray().put("front"))
                .put("facts", new JSONArray().put(new JSONObject().put("key", key).put("value", value).put("image", 0)
                        .put("side", "front").put("location", "specific visible identity region").put("role", role).put("confidence", 97)))
                .put("candidates", new JSONArray());
    }

    private static JSONObject webPayload(String category, String subject, String number) throws Exception {
        JSONObject candidate = new JSONObject().put("candidate_id", "row-1").put("source_id", "source-1").put("source_title", "Checklist")
                .put("source_record_id", "row-1").put("source_page_scope", "CHECKLIST_ROW").put("identity_level", "CATALOG_IDENTITY")
                .put("brand", "Example").put("product_line", "Example Series").put("set_name", "").put("sub_series", "")
                .put("model", "").put("category", category).put("year", "2020-21").put("subject", subject).put("card_number", number)
                .put("language", "English").put("edition", "").put("card_role", "").put("printed_total", "").put("set_symbol", "")
                .put("copyright_year", "").put("sport", "").put("team", "").put("finish", "").put("format", "").put("configuration", "")
                .put("product_code", "").put("barcode", "").put("source_url", "https://catalog.example/row-1")
                .put("source_authority", "checklist").put("source_quality", 90).put("exact_reference", true).put("disproof_passed", true)
                .put("layout_match", 90).put("matched_observed_fields", new JSONArray()).put("contradicted_observed_fields", new JSONArray()).put("unknown_fields", new JSONArray());
        return new JSONObject().put("queries", new JSONArray()).put("candidates", new JSONArray().put(candidate)).put("retrieval_reason", "test");
    }

    private static IdentityCandidateV2 winning(List<IdentityCandidateV2> ranked) {
        for (IdentityCandidateV2 candidate : ranked) if ("WINNER".equals(candidate.status)) return candidate;
        return null;
    }

    private static void observed(ImmutableEvidenceLedgerV2 ledger, String field, String value, int image) {
        ledger.append(field, value, EvidenceAtom.EpistemicLevel.OBSERVED, EvidenceAtom.Modality.PRIMARY_VISION,
                "test", image, "front", "specific " + field + " region", "", field, 96, 90, "test", "");
    }

    private static int count(String text, String token) {int total = 0, from = 0; while ((from = text.indexOf(token, from)) >= 0) {total++; from += token.length();} return total;}
    private static String read(String path) throws Exception {return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);}
    private static String readTree(Path root) throws Exception {StringBuilder out = new StringBuilder(); try (java.util.stream.Stream<Path> paths = Files.walk(root)) {paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> {try {out.append(read(p.toString()));} catch (Exception e) {throw new RuntimeException(e);}});} return out.toString();}
    private static void require(boolean condition, String message) {if (!condition) throw new AssertionError(message);}
    private static void pass() {passed++;}
}
