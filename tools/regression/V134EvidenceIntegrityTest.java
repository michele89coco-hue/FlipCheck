package com.flipcheck.nativebeta;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Contract tests, not substitutes for installed-APK live validation. */
public class V134EvidenceIntegrityTest {
    @Test public void repeatedNormalizationPreservesDerivedDenominator() {
        ImmutableEvidenceLedgerV2 ledger = new ImmutableEvidenceLedgerV2();
        ledger.append("collectorNumber", "15/64", EvidenceAtom.EpistemicLevel.OBSERVED,
                EvidenceAtom.Modality.PRIMARY_VISION, "test", 0, "front", "lower right corner",
                "", "collector_number", 95, 90, "test", "");
        TypedFieldNormalizerV2.normalize(ledger);
        int size = ledger.all().size();
        for (int i = 0; i < 4; i++) TypedFieldNormalizerV2.normalize(ledger);
        assertEquals("64", ledger.strongest("printedTotal").normalizedValue);
        assertEquals("15/64", ledger.strongest("collectorNumber").normalizedValue);
        assertEquals(size, ledger.all().size());
    }
    @Test public void nonHoloIsNotHolo() {
        for (String value : new String[]{"NON HOLO", "NON_HOLO", "non-holo", "nonholo"})
            assertEquals("NON_HOLO", TypedFieldNormalizerV2.normalizeValue("finish", value, ""));
    }
    @Test public void collectorTotalIsNotCollectorNumber() throws Exception {
        ImmutableEvidenceLedgerV2 ledger = new ImmutableEvidenceLedgerV2();
        JSONObject fact = new JSONObject().put("key", "collector_total").put("value", "64")
                .put("image", 0).put("side", "front").put("location", "lower right corner")
                .put("role", "collector_number_denominator").put("confidence", 95);
        ObservationExtractorV2.ingestPrimary(new JSONObject().put("category", "tcg_card")
                .put("facts", new JSONArray().put(fact)), ledger);
        assertNotNull(ledger.strongest("printedTotal"));
        assertNull(ledger.strongest("collectorNumber"));
    }
    @Test public void jerseyAndGraphicNumberStayDistinct() {
        assertEquals("jerseyNumber", TypedFieldNormalizerV2.canonicalField("jersey_number", ""));
        assertEquals("graphicNumber", TypedFieldNormalizerV2.canonicalField("graphic_number", ""));
    }
    @Test public void canonicalFieldsSurviveRepeatedMapping() {
        for (String field : new String[]{"cardRole", "subSeries", "setSymbol", "evolutionStage", "newDescriptiveField"})
            assertEquals(field, TypedFieldNormalizerV2.canonicalField(
                    TypedFieldNormalizerV2.canonicalField(field, ""), ""));
    }
    @Test public void statisticalScopeOverridesCanonicalYearAlias() {
        for (String field : new String[]{"productReleaseYear", "setSeason", "physical_set_or_release_year"})
            assertEquals("statisticsSeason", TypedFieldNormalizerV2.canonicalField(field, "OBJECT_STATISTIC"));
    }
    @Test public void evolutionStageIsNotCatalogRole() throws Exception {
        assertEquals("evolutionStage", TypedFieldNormalizerV2.canonicalField("cardRole", "evolution_stage"));
        ImmutableEvidenceLedgerV2 ledger = new ImmutableEvidenceLedgerV2();
        JSONObject fact = new JSONObject().put("key", "stage").put("value", "Stage 2")
                .put("image", 0).put("side", "front").put("location", "upper left corner")
                .put("role", "evolution_stage").put("confidence", 95);
        ObservationExtractorV2.ingestPrimary(new JSONObject().put("category", "tcg_card")
                .put("facts", new JSONArray().put(fact)), ledger);
        assertNotNull(ledger.strongest("evolutionStage"));
        assertNull(ledger.strongest("cardRole"));
    }
    @Test public void sharedFamilyDoesNotMakeSiblingLinesEquivalent() {
        assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE,
                SemanticRelationV3.relate("productLine", "Chrome Update", "Chrome Sapphire"));
        assertTrue(SemanticRelationV3.compatible(
                SemanticRelationV3.relate("productLine", "Chrome", "Chrome Basketball")));
        assertTrue(SemanticRelationV3.compatible(SemanticRelationV3.relate("productType",
                "sealed trading-card box", "sealed trading-card product")));
    }
    @Test public void firstEditionMarkHandlesNegativeAndLiteralReadings() {
        assertEquals("PRESENT", TypedFieldNormalizerV2.normalizeValue("firstEditionMark", "Edition 1", ""));
        assertEquals("ABSENT", TypedFieldNormalizerV2.normalizeValue("firstEditionMark", "not present", ""));
        assertEquals("ABSENT", TypedFieldNormalizerV2.normalizeValue("firstEditionMark", "first edition mark absent", ""));
        assertEquals("unknown", TypedFieldNormalizerV2.normalizeValue("firstEditionMark", "unknown", ""));
    }
    @Test public void finishAloneDoesNotConfirmTcgEditionOrVariant() {
        for (String mark : new String[]{"ABSENT", "unknown", ""}) {
            Models.Identification id = reduceEdition(mark);
            assertEquals("TO_VERIFY", id.exactEditionStatus);
            assertEquals("TO_VERIFY", id.variantStatus);
            assertNotEquals("FIRST_EDITION", id.edition);
        }
    }
    @Test public void localizedLiteralEditionClosesEditionAttribute() {
        Models.Identification id = reduceEdition("Edition 1");
        assertEquals("FIRST_EDITION", id.edition);
        assertEquals("CONFIRMED", id.exactEditionStatus);
        assertEquals("CONFIRMED", id.variantStatus);
    }
    private static Models.Identification reduceEdition(String mark) {
        ImmutableEvidenceLedgerV2 ledger = new ImmutableEvidenceLedgerV2();
        ledger.append("finish", "HOLO", EvidenceAtom.EpistemicLevel.OBSERVED,
                EvidenceAtom.Modality.PRIMARY_VISION, "test", 0, "front", "art region",
                "", "finish", 95, 90, "test", "");
        if (!mark.isEmpty()) ledger.append("firstEditionMark", mark, EvidenceAtom.EpistemicLevel.OBSERVED,
                EvidenceAtom.Modality.FOCUSED_VISION, "test", 0, "front", "edition region",
                "", "edition", 95, 90, "test", "");
        TypedFieldNormalizerV2.normalize(ledger);
        Models.Identification id = new Models.Identification(); id.uploadedImageCount = 1;
        FinalStateReducerV2.reduce(id, ledger, DomainProfileRouterV2.Profile.TCG_CARD,
                new java.util.ArrayList<>(), new java.util.ArrayList<>(), "");
        return id;
    }
    @Test public void genericTextUsesItsSemanticRole() throws Exception {
        Models.LocalScan local = new Models.LocalScan(); local.textByImage.add("ACME Prism Update Series");
        ImmutableEvidenceLedgerV2 ledger = new ImmutableEvidenceLedgerV2(); ObservationExtractorV2.ingestLocal(local, ledger);
        JSONArray facts = new JSONArray();
        String[][] rows = {{"Prism", "PRODUCT_LINE", "productLine"}, {"Update Series", "SERIES_TEXT", "subSeries"},
            {"2025/26", "RELEASE_SEASON", "productReleaseYear"}, {"1 autograph in every box", "SEALED_CONFIGURATION", "configuration"}};
        for (String[] row : rows) facts.put(new JSONObject().put("key", "text").put("value", row[0])
            .put("role", row[1]).put("image", 0).put("side", "front").put("location", "center label").put("confidence", 98));
        ObservationExtractorV2.ingestPrimary(new JSONObject().put("category", "sealed_trading_card_product").put("facts", facts), ledger);
        for (String[] row : rows) assertNotNull(row[2], ledger.strongest(row[2]));
        String prompt = CandidateRetrieverV2.prompt(DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT, ledger, new java.util.ArrayList<>());
        assertTrue(prompt.contains("subSeries=Update Series"));
    }
    @Test public void uncorroboratedSubseriesDoesNotBecomePhysicalConstraint() throws Exception {
        ImmutableEvidenceLedgerV2 ledger = new ImmutableEvidenceLedgerV2();
        JSONObject fact = new JSONObject().put("key", "subSeries").put("value", "Imagined series")
            .put("role", "SERIES_TEXT").put("image", 0).put("side", "front").put("location", "center").put("confidence", 99);
        ObservationExtractorV2.ingestPrimary(new JSONObject().put("category", "sealed_trading_card_product").put("facts", new JSONArray().put(fact)), ledger);
        assertFalse(ledger.hasObserved("subSeries"));
        assertNotNull(ledger.strongest("subSeries", EvidenceAtom.EpistemicLevel.INFERRED));
    }
    @Test public void descriptiveListsAreNotAutomaticallyMultipleCatalogRecords() {
        IdentityCandidateV2 c = new IdentityCandidateV2("record", DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL, "test");
        c.fields.put("manufacturer", "Example"); c.fields.put("controlLayout", "Home above Back; numeric keypad below"); c.layoutMatch = 90;
        assertTrue(CatalogConsistencyV3.check(c, c.domain).coherent);
        c.fields.put("manufacturer", "Example OR Alternative");
        assertFalse(CatalogConsistencyV3.check(c, c.domain).coherent);
    }
}
