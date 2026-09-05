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
    @Test public void insufficientCatalogQualityHasInspectableReason() {
        ImmutableEvidenceLedgerV2 ledger = new ImmutableEvidenceLedgerV2();
        for (String[] pair : new String[][]{{"cardName", "Example creature"}, {"collectorNumber", "12/80"}})
            ledger.append(pair[0], pair[1], EvidenceAtom.EpistemicLevel.OBSERVED,
                EvidenceAtom.Modality.PRIMARY_VISION, "test", 0, "front", "identity label", "", "identity", 95, 90, "test", "");
        IdentityCandidateV2 c = new IdentityCandidateV2("catalog-row", DomainProfileRouterV2.Profile.TCG_CARD, "test");
        c.retrieved = true; c.exactReference = true; c.sourceUrl = "https://catalog.example/row";
        c.sourceRecordId = "row"; c.sourcePageScope = "CHECKLIST_ROW"; c.webSourceQuality = 4;
        c.fields.put("cardName", "Example creature"); c.fields.put("catalogCardNumber", "12/80"); c.fields.put("setName", "Example set");
        CandidateVerifierV2.verify(java.util.Collections.singletonList(c), ledger, c.domain);
        assertFalse(c.disproofPassed);
        assertTrue(c.disproofReason, c.disproofReason.contains("source_quality=4<60"));
    }
    @Test public void presentationDoesNotInventMissingPhotoOrReclassifyProfile() {
        Models.Identification id = new Models.Identification();
        id.finalStateReducerVersion = FinalStateReducerV2.VERSION;
        id.v2Profile = "sealed_trading_card_product"; id.identityStatus = "UNRESOLVED";
        id.canonicalProfile = "authoritative_snapshot";
        assertTrue(IdentityPresentationV2.sealed(id));
        assertFalse(IdentityPresentationV2.electronics(id));
        assertFalse(IdentityPresentationV2.explanation(id).contains("foto"));
        assertEquals("authoritative_snapshot", id.canonicalProfile);
        assertNull(id.normalizedPhotoIdentity);
        id.identityStatus = "CONFLICTED";
        assertFalse(IdentityPresentationV2.explanation(id).contains("resta valida"));
        id.v2Profile = "television_remote_control";
        assertTrue(IdentityPresentationV2.electronics(id));
    }
    @Test public void catalogSymbolLabelAndVisibleAppearanceAreDifferentEvidence() throws Exception {
        ImmutableEvidenceLedgerV2 ledger = new ImmutableEvidenceLedgerV2();
        JSONArray facts = new JSONArray();
        for (String[] pair : new String[][]{{"setSymbol", "Example Set", "SET_IDENTIFIER"},
                {"setSymbolAppearance", "three black leaves", "VISIBLE_SYMBOL"}})
            facts.put(new JSONObject().put("key", pair[0]).put("value", pair[1]).put("role", pair[2])
                    .put("image", 0).put("side", "front").put("location", "right of illustration").put("confidence", 95));
        ObservationExtractorV2.ingestPrimary(new JSONObject().put("category", "tcg_card").put("facts", facts), ledger);
        assertFalse(ledger.hasObserved("setSymbol"));
        assertTrue(ledger.hasObserved("visualSymbol"));
        assertNotNull(ledger.strongest("setSymbol", EvidenceAtom.EpistemicLevel.INFERRED));
    }
    @Test public void configurationPrepositionsDoNotChangeAutographCount() {
        assertTrue(SemanticRelationV3.compatible(SemanticRelationV3.relate("configuration", "1 autograph in every box", "1 autograph per box")));
        assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE, SemanticRelationV3.relate("configuration", "1 autograph in every box", "1 autograph per 3 boxes"));
    }
    @Test public void statisticsLocationOverridesMislabelledReleaseYear() throws Exception {
        ImmutableEvidenceLedgerV2 l = new ImmutableEvidenceLedgerV2();
        JSONObject f = new JSONObject().put("key", "productReleaseYear").put("value", "2001-02").put("role", "release_season")
            .put("image", 0).put("side", "back").put("location", "statistics table, YR row").put("confidence", 99);
        ObservationExtractorV2.ingestFocused(new JSONObject().put("facts", new JSONArray().put(f)), l, DomainProfileRouterV2.Profile.SPORTS_CARD, "crop");
        assertFalse(l.hasObserved("productReleaseYear")); assertTrue(l.hasObserved("statisticsSeason"));
    }
    @Test public void transcriptionAliasesPreserveIdentityAxes() {
        String[][] pairs={{"productLineText","productLine"},{"productLineToken","productLine"},
                {"releaseSeasonText","productReleaseYear"},{"configurationText","configuration"},{"seriesText","subSeries"},
                {"editionMark","firstEditionMark"},{"tcgNumber","collectorNumber"}};
        for(String[] p:pairs)assertEquals(p[0],p[1],TypedFieldNormalizerV2.canonicalField(p[0],""));
        assertEquals("PRESENT",TypedFieldNormalizerV2.normalizeValue("firstEditionMark","Edition 1 logo",""));
    }
    @Test public void observedToolQueryIsNotLostWhenGeneratedQueriesAreEmpty() throws Exception {
        JSONObject payload = new JSONObject().put("queries",new JSONArray());
        java.util.List<String> actual = java.util.Collections.singletonList("remote control sources back numeric keypad");
        assertEquals("",CandidateRetrieverV2.neutralQueryViolation(payload,DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL,
                new java.util.ArrayList<>(),new ImmutableEvidenceLedgerV2(),actual));
    }
    @Test public void observedFirstEditionMarkParticipatesInCandidateComparison() {
        ImmutableEvidenceLedgerV2 l = new ImmutableEvidenceLedgerV2();
        l.append("firstEditionMark","Edition 1 logo",EvidenceAtom.EpistemicLevel.OBSERVED,
            EvidenceAtom.Modality.PRIMARY_VISION,"test",0,"front","left edition mark","","edition",95,90,"test","");
        TypedFieldNormalizerV2.normalize(l);
        assertTrue(l.hasObserved("edition"));
        IdentityCandidateV2 c = new IdentityCandidateV2("wrong-edition",DomainProfileRouterV2.Profile.TCG_CARD,"test");
        c.retrieved=true;c.sourceUrl="https://catalog.example/row";c.webSourceQuality=90;c.fields.put("edition","UNLIMITED");
        CandidateVerifierV2.verify(java.util.Collections.singletonList(c),l,c.domain);
        assertTrue(c.rejected);assertFalse(c.trueConflicts.isEmpty());
    }
    @Test public void reliableSubseriesConflictCannotWinSealedTournament() {
        ImmutableEvidenceLedgerV2 l = new ImmutableEvidenceLedgerV2();
        for (String[] pair : new String[][]{{"manufacturer","Example"},{"productLine","Prism"},
                {"subSeries","Update Series"},{"configuration","1 autograph per box"}})
            l.append(pair[0],pair[1],EvidenceAtom.EpistemicLevel.OBSERVED,
                EvidenceAtom.Modality.LOCAL_OCR,"test",0,"front","printed identity panel","","printed_text",95,90,"test","");
        IdentityCandidateV2 c = new IdentityCandidateV2("different-subseries",DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,"test");
        c.retrieved=true;c.sourceUrl="https://catalog.example/row";c.sourceRecordId="row";
        c.sourcePageScope="PRODUCT_PAGE";c.webSourceQuality=95;
        c.fields.put("manufacturer","Example");c.fields.put("productLine","Prism");
        c.fields.put("subSeries","Sapphire");c.fields.put("configuration","1 autograph per box");
        CandidateVerifierV2.verify(java.util.Collections.singletonList(c),l,c.domain);
        assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE,c.fieldRelations.get("subSeries"));
        assertTrue("reliable identifying contradiction must reject candidate",c.rejected);
        assertFalse(c.trueConflicts.isEmpty());assertFalse(c.disproofPassed);
    }

    @Test public void presentationStatusUsesReducerWithoutInventedClosureOrPhoto() {
        Models.Identification id = new Models.Identification();
        id.identityStatus="UNRESOLVED";id.coreIdentityStatus="UNRESOLVED";
        assertEquals("IDENTIFICAZIONE INCOMPLETA",IdentityPresentationV2.status(id));
        id.coreIdentityStatus="PROBABLE";
        assertEquals("IDENTITÀ DA VERIFICARE",IdentityPresentationV2.status(id));
        id.coreIdentityStatus="CONFIRMED";id.identityStatus="CONFIRMED_WITH_ATTRIBUTE_PENDING";
        assertTrue(IdentityPresentationV2.status(id).contains("ATTRIBUTI DA VERIFICARE"));
        id.identityStatus="CONFLICTED";
        assertEquals("PROVE IDENTIFICATIVE IN CONFLITTO",IdentityPresentationV2.status(id));
    }

    @Test public void catalogSeasonExtendsPrintedReleaseYearWithoutReplacingEvidence() {
        ImmutableEvidenceLedgerV2 l = new ImmutableEvidenceLedgerV2();
        for(String[] pair:new String[][]{{"athlete","Example Athlete"},{"physicalCardNumber","12"},{"productReleaseYear","2009"}})
            l.append(pair[0],pair[1],EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.LOCAL_OCR,
                "test",0,"front","printed label","","identity",95,90,"test","");
        IdentityCandidateV2 c = new IdentityCandidateV2("season-record",DomainProfileRouterV2.Profile.SPORTS_CARD,"test");
        c.retrieved=true;c.disproofPassed=true;c.totalScore=90;c.webSourceQuality=95;
        c.sourceUrl="https://catalog.example/season-record";c.sourceRecordId="season-record";
        c.fields.put("productReleaseYear","2009-10");c.fields.put("manufacturer","Example");
        c.fields.put("productLine","Prism");c.fields.put("athlete","Example Athlete");c.fields.put("catalogCardNumber","12");
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,c.domain,java.util.Collections.singletonList(c),new java.util.ArrayList<>(),"");
        assertTrue(id.title,id.title.startsWith("2009-10 "));
        assertEquals("2009",id.physicalReleaseYear);
        assertEquals("2009-10",id.sourceConfirmedReleaseYear);
    }

    @Test public void configurationComparesQuantitiesWithTheirUnits() {
        assertTrue(SemanticRelationV3.compatible(SemanticRelationV3.relate("configuration",
                "1 autograph in every box", "4 cards per pack, 20 packs per box, 1 autograph per box")));
        assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE,SemanticRelationV3.relate("configuration",
                "4 cards per pack, 20 packs per box", "20 cards per pack, 4 packs per box"));
        assertEquals(SemanticRelationV3.Relation.AMBIGUOUS,SemanticRelationV3.relate("configuration",
                "4 cards per pack", "20 packs per box"));
        assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE,SemanticRelationV3.relate("configuration",
                "1 autograph per box and 3 bonus items", "1 autograph per box and 5 bonus items"));
    }

    @Test public void diagnosticExportRedactsCredentialAndKeepsStructuredEvidence() throws Exception {
        Models.Identification id=new Models.Identification();
        String secret="secret-with-quote-\"-and-backslash-\\";
        id.title="Example";id.webQueries.add("query "+secret);id.v2StagePayloads.add("payload "+secret);
        ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();
        ledger.append("printedLabel","visible "+secret,EvidenceAtom.EpistemicLevel.OBSERVED,
                EvidenceAtom.Modality.LOCAL_OCR,"test",0,"front","center panel","","printed_text",95,90,"test","");
        id.evidenceAtomsV2.addAll(ledger.all());
        JSONObject report=new JSONObject(DiagnosticExportV2.create(id,new Models.Usage(),secret));
        assertEquals("Example",report.getString("title"));
        assertEquals("query [REDACTED]",report.getJSONArray("queries").getString(0));
        assertEquals("payload [REDACTED]",report.getJSONArray("stagePayloads").getString(0));
        JSONObject atom=report.getJSONArray("evidence").getJSONObject(0);
        assertEquals("visible [REDACTED]",atom.getString("rawValue"));
        assertEquals("center panel",atom.getString("region"));
        assertEquals(0,atom.getInt("imageIndex"));
        assertFalse(report.has("api_key"));
    }

    @Test public void sportsPlayerNameAliasMustReachAthleteField() throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        JSONObject fact=new JSONObject().put("key","player_name").put("value","Example Athlete")
                .put("image",0).put("side","front").put("location","nameplate").put("role","subject").put("confidence",99);
        ObservationExtractorV2.ingestPrimary(new JSONObject().put("category","sports_card").put("facts",new JSONArray().put(fact)),l);
        assertTrue(l.hasObserved("athlete"));
    }
    @Test public void collectorRarityGlyphIsSeparateFromNumber() {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        l.append("collectorNumber","12/80 ★",EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.PRIMARY_VISION,
                "test",0,"front","number and rarity area","","collector number and rarity",99,90,"test","");
        TypedFieldNormalizerV2.normalize(l);
        assertEquals("12/80",l.strongest("collectorNumber").normalizedValue);
        assertEquals("80",l.strongest("printedTotal").normalizedValue);
        assertEquals("12/80 ★",l.all().get(0).rawValue);
    }
    @Test public void tcgFranchiseIsNotTheCopyrightManufacturer() throws Exception {
        Models.LocalScan scan=new Models.LocalScan();scan.textByImage.add("ExampleGame");
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestLocal(scan,l);
        JSONObject f=new JSONObject().put("key","visible_text").put("value","ExampleGame").put("role","franchise_text")
                .put("image",0).put("side","front").put("location","rules text").put("confidence",99);
        ObservationExtractorV2.ingestPrimary(new JSONObject().put("category","tcg").put("facts",new JSONArray().put(f)),l);
        assertTrue(l.hasObserved("game"));
        IdentityCandidateV2 c=new IdentityCandidateV2("publisher-record",DomainProfileRouterV2.Profile.TCG_CARD,"test");
        c.fields.put("manufacturer","Example Publisher");c.fields.put("setName","Example Set");
        CandidateVerifierV2.verify(java.util.Collections.singletonList(c),l,c.domain);
        assertFalse("franchise must not be compared with manufacturer",c.fieldRelations.containsKey("manufacturer"));
    }

    @Test public void descriptiveEditionMarkPreservesNegationAndUncertainty() {
        assertEquals("PRESENT",TypedFieldNormalizerV2.normalizeValue("firstEditionMark","EDITIONS 1 circular mark",""));
        assertEquals("ABSENT",TypedFieldNormalizerV2.normalizeValue("firstEditionMark","no first edition mark visible",""));
        assertNotEquals("PRESENT",TypedFieldNormalizerV2.normalizeValue("firstEditionMark","possibly a first edition mark",""));
    }
    @Test public void remoteShortcutLogoIsNotDeviceManufacturer() throws Exception {
        Models.LocalScan scan=new Models.LocalScan();scan.textByImage.add("STREAMAPP");
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestLocal(scan,l);
        JSONObject f=new JSONObject().put("key","brand_mark").put("value","STREAMAPP").put("role","visible_logo")
            .put("image",0).put("side","front").put("location","below colored shortcut keys").put("confidence",99);
        ObservationExtractorV2.ingestPrimary(new JSONObject().put("category","television_remote_control").put("facts",new JSONArray().put(f)),l);
        assertFalse(l.hasObserved("brand"));assertFalse(l.hasObserved("manufacturer"));
        assertNotNull(l.strongest("controlLabel"));
    }
    @Test public void missingDesignDescriptionIsNotContradiction() {
        assertEquals(SemanticRelationV3.Relation.AMBIGUOUS,SemanticRelationV3.relate("numericKeypad",
            "digits 1-9 and 0 in three columns", "not documented"));
        assertEquals(SemanticRelationV3.Relation.AMBIGUOUS,SemanticRelationV3.relate("numericKeypad",
            "digits 1-9 and 0 in three columns", "physical numeric keypad documented; exact arrangement not specified"));
    }
    @Test public void voiceCapabilityIsSeparateFromWording() {
        assertTrue(SemanticRelationV3.compatible(SemanticRelationV3.relate("voiceControl",
            "button visibly labeled VOICE", "Dedicated VOICE control/microphone")));
        assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE,SemanticRelationV3.relate("voiceControl",
            "VOICE button", "no voice control"));
    }
    @Test public void scopedPackagingTranscriptionsReachCanonicalFields() throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        JSONArray fs=new JSONArray();
        for(String[] p:new String[][]{{"sealed_configuration_text","1 autograph in every box","configuration"},
            {"sealed_brand_line_configuration","Example Prism Update Series","product line"}})
            fs.put(new JSONObject().put("key",p[0]).put("value",p[1]).put("role",p[2]).put("image",0)
                .put("side","front").put("location","center product title").put("confidence",99));
        ObservationExtractorV2.ingestPrimary(new JSONObject().put("category","sealed_trading_card_product").put("facts",fs),l);
        assertNotNull(l.strongest("configuration"));assertNotNull(l.strongest("productLine"));
    }

    @Test public void focusedLiteralLogoDoesNotRequireSuccessfulOcr() throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        JSONObject f=new JSONObject().put("key","brand_mark").put("value","ExampleMaker").put("role","manufacturer_brand")
            .put("image",0).put("side","front").put("location","center manufacturer logo").put("confidence",99);
        ObservationExtractorV2.ingestFocused(new JSONObject().put("facts",new JSONArray().put(f)),l,
            DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,"detail");
        assertTrue(l.hasObserved("brand"));
    }
    @Test public void sealedYearAndGenericBoxDoNotProveManufacturerOrLine() {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        for(String[] p:new String[][]{{"productReleaseYear","2025-26"},{"productType","sealed trading card box"}})
            l.append(p[0],p[1],EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.PRIMARY_VISION,"test",0,"front","panel","","printed_text",99,90,"test","");
        IdentityCandidateV2 c=new IdentityCandidateV2("generic",DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,"test");
        c.retrieved=true;c.sourceUrl="https://catalog.example/box";c.webSourceQuality=95;
        c.fields.put("manufacturer","Example");c.fields.put("productLine","Prism");c.fields.put("productReleaseYear","2025-26");
        c.fields.put("productType","sealed trading card box");
        CandidateVerifierV2.verify(java.util.Collections.singletonList(c),l,c.domain);
        assertFalse(c.disproofPassed);
    }

    @Test public void remoteLayoutDoesNotProveCatalogModelCode() {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        l.append("controlLayout","diamond navigation with numeric keypad",EvidenceAtom.EpistemicLevel.OBSERVED,
            EvidenceAtom.Modality.PRIMARY_VISION,"test",0,"front","controls","","layout",99,90,"test","");
        IdentityCandidateV2 c=new IdentityCandidateV2("manual",DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL,"test");
        c.retrieved=true;c.disproofPassed=true;c.totalScore=90;c.webSourceQuality=95;c.sourceUrl="https://catalog.example/manual";
        c.fields.put("manufacturer","Example");c.fields.put("model","TV55ABC");
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,c.domain,java.util.Collections.singletonList(c),new java.util.ArrayList<>(),"");
        assertEquals("",id.model);assertEquals("TO_VERIFY",id.exactModelStatus);
        assertEquals("rear_label_or_model_code",id.requestedPhotoReason);
    }

}
