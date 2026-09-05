package com.flipcheck.nativebeta;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class V134DomainRoleTest {
    @Test public void tcgNumericLabelCannotBecomeASecondCardName() throws Exception {
        ImmutableEvidenceLedgerV2 l=extract("tcg_card","cardName","#47","printed card number");
        assertNull(l.strongest("cardName"));
        assertEquals("#47",l.strongest("printedLabel").rawValue);
        assertNotNull(extract("tcg_card","cardName","Porygon2","card name").strongest("cardName"));
    }
    @Test public void sealedSeasonRoleOverridesMisnamedStatisticsField() throws Exception {
        assertNotNull(extract("sealed_trading_card_product","statisticsSeason","2024/25","season marking").strongest("productReleaseYear"));
        assertNull(extract("sports_card","statisticsSeason","2024/25","statistics season").strongest("productReleaseYear"));
    }
    @Test public void focusedFullProductLineRemainsLiteral() throws Exception {
        assertNotNull(extract("sealed_trading_card_product","productLine","Example Prism Update Series","full product line").strongest("productLine",EvidenceAtom.EpistemicLevel.OBSERVED));
    }
    @Test public void compatibleCardContainersDoNotConflict() {
        assertTrue(SemanticRelationV3.compatible(SemanticRelationV3.relate("productType","sealed trading card box","Sealed basketball trading-card product")));
        assertTrue(SemanticRelationV3.compatible(SemanticRelationV3.relate("productType","basketball trading card box","sealed trading card product")));
        assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE,SemanticRelationV3.relate("productType","sealed basketball trading card box","sealed baseball trading card box"));
        assertFalse(SemanticRelationV3.compatible(SemanticRelationV3.relate("productType","sealed trading card box","single trading card")));
    }
    @Test public void unprintedSealedFormatIsOnlyAnInference() throws Exception {
        assertNull(extract("sealed_trading_card_product","productType","blaster box","product type").strongest("productType",EvidenceAtom.EpistemicLevel.OBSERVED));
        assertNotNull(extract("sealed_trading_card_product","commercialFormat","Blaster Box","printed format label").strongest("commercialFormat",EvidenceAtom.EpistemicLevel.OBSERVED));
    }
    @Test public void unresolvedCatalogFormatCannotBePromoted() {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        l.append("configuration","1 autograph per box",EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.FOCUSED_VISION,"test",0,"front","bottom panel","","printed configuration",95,95,"test","");
        IdentityCandidateV2 c=new IdentityCandidateV2("format",DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,"WEB");
        c.fields.put("commercialFormat","Hobby Box");c.fields.put("manufacturer","Example");c.fields.put("productLine","Prism");c.fields.put("productReleaseYear","2024-25");
        c.retrieved=true;c.disproofPassed=true;c.webSourceQuality=95;c.totalScore=90;c.sourceUrl="https://catalog.example/product";
        c.unknownFields.add("whether photographed box is hobby or jumbo SKU");
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,java.util.Arrays.asList(c),new java.util.ArrayList<>(),"");
        assertEquals("",id.sealedFormat);assertEquals("CONFIRMED",id.coreIdentityStatus);
    }
    private ImmutableEvidenceLedgerV2 extract(String profile,String key,String value,String role) throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        JSONObject f=new JSONObject().put("key",key).put("value",value).put("role",role)
            .put("image",0).put("side","front").put("location","lower corner").put("confidence",95);
        ObservationExtractorV2.ingestFocused(new JSONObject().put("facts",new JSONArray().put(f)),l,
            DomainProfileRouterV2.route(profile,l),"test-crop");
        TypedFieldNormalizerV2.normalize(l);return l;
    }
    @Test public void sportsNumberRoleOverridesGenericCardRole() throws Exception {
        assertNotNull(extract("sports_card","cardRole","72","card number").strongest("physicalCardNumber"));
        assertNotNull(extract("sports_card","collectorNumber","72","collector number").strongest("physicalCardNumber"));
    }
    @Test public void sportsLineRoleOverridesCardNameTransport() throws Exception {
        ImmutableEvidenceLedgerV2 l=extract("sports_card","cardName","Metallic Universe","product line mark");
        assertNotNull(l.strongest("productLine",EvidenceAtom.EpistemicLevel.OBSERVED));
        assertNull(l.strongest("cardName"));
    }
    @Test public void sealedFeaturedSubjectsAreNotConflictingAthletes() throws Exception {
        ImmutableEvidenceLedgerV2 l=extract("sealed_trading_card_product","athlete","Player A","featured subject");
        l.append("athlete","Player B",EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.PRIMARY_VISION,"test",0,"front","portrait","","featured subject",95,95,"test","");
        assertTrue(ConflictResolverV2.resolve(l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT).isEmpty());
    }
    @Test public void tcgDescriptiveNumbersDoNotConflictWithCollector() throws Exception {
        ImmutableEvidenceLedgerV2 l=extract("tcg_card","physicalCardNumber","44","index label");
        l.append("physicalCardNumber","14/70",EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.PRIMARY_VISION,"test",0,"front","lower right","","card number",95,95,"test","");
        assertTrue(ConflictResolverV2.resolve(l,DomainProfileRouterV2.Profile.TCG_CARD).isEmpty());
    }
    @Test public void sealedSeasonTranscriptionIsPreserved() throws Exception {
        assertNotNull(extract("sealed_trading_card_product","printedLabel","2024/25","season label").strongest("productReleaseYear"));
    }
    @Test public void localizedBrandAndManufacturerSupportSameSealedCore() {
        for(String brandField:new String[]{"brand","manufacturer"}) {
            ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
            for(String[] f:new String[][]{{brandField,"Example"},{"productLine","Prism Basketball"},{"productReleaseYear","2024-25"}})
                l.append(f[0],f[1],EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.FOCUSED_VISION,"test",0,"front","central printed label","","printed text",95,95,"test","");
            Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
            FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,new java.util.ArrayList<>(),new java.util.ArrayList<>(),"");
            assertEquals("CONFIRMED",id.coreIdentityStatus);
            assertEquals("TO_VERIFY",id.commercialFormatStatus);
        }
    }
    @Test public void sealedCatalogEditionFormatIsNotCardEdition() throws Exception {
        JSONObject row=new JSONObject().put("candidate_id","format-record").put("edition","Hobby Box")
            .put("source_url","https://catalog.example/product").put("source_record_id","record");
        IdentityCandidateV2 c=CandidateRetrieverV2.parse(new JSONObject().put("candidates",new JSONArray().put(row)),
            DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,new ImmutableEvidenceLedgerV2()).get(0);
        assertEquals("Hobby Box",c.value("commercialFormat"));
        assertEquals("",c.value("edition"));
    }
    @Test public void genericObservedBoxDoesNotOverrideVerifiedFormat() {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        l.append("commercialFormat","Box",EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.FOCUSED_VISION,"test",0,"front","bottom label","","printed text",95,95,"test","");
        IdentityCandidateV2 c=new IdentityCandidateV2("format",DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,"WEB");
        c.fields.put("commercialFormat","Hobby Box");c.fields.put("manufacturer","Example");c.fields.put("productLine","Prism");c.fields.put("productReleaseYear","2024-25");
        c.retrieved=true;c.disproofPassed=true;c.webSourceQuality=95;c.totalScore=90;c.sourceUrl="https://catalog.example/product";
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,java.util.Arrays.asList(c),new java.util.ArrayList<>(),"");
        assertEquals("Hobby Box",id.sealedFormat);
    }
}
