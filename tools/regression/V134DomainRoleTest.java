package com.flipcheck.nativebeta;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class V134DomainRoleTest {
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
