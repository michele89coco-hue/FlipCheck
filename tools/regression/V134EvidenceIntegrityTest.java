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
}
