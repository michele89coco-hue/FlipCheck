package com.flipcheck.nativebeta;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class V146TraceRepairTest {
    private JSONObject f(String key,String value,String role,String region) throws Exception {
        return new JSONObject().put("key",key).put("value",value).put("role",role).put("location",region).put("image",0).put("side","front").put("confidence",98);
    }
    private ImmutableEvidenceLedgerV2 focused(String category,JSONObject...facts) throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();JSONArray fs=new JSONArray();for(JSONObject f:facts)fs.put(f);
        ObservationExtractorV2.ingestFocused(new JSONObject().put("facts",fs),l,DomainProfileRouterV2.route(category,l),"test");TypedFieldNormalizerV2.normalize(l);return l;
    }
    @Test public void fullFractionInTotalFieldIsStillCollectorEvidence() throws Exception {
        ImmutableEvidenceLedgerV2 l=focused("tcg",f("printedTotal","17/80","set numbering","bottom-right edge"));
        assertEquals("17/80",l.strongest("collectorNumber").normalizedValue);assertEquals("80",l.strongest("printedTotal").normalizedValue);
    }
    @Test public void descriptiveIndexMustNotConflictWithLocatedFraction() throws Exception {
        ImmutableEvidenceLedgerV2 l=focused("tcg",f("collectorNumber","#47","collector number","bottom-right of flavor text box"),f("printedTotal","17/80","set numbering","bottom-right edge"));
        assertEquals("17/80",l.strongest("collectorNumber").normalizedValue);assertEquals("#47",l.strongest("printedLabel").rawValue);
        assertTrue(ConflictResolverV2.resolve(l,DomainProfileRouterV2.Profile.TCG_CARD).isEmpty());
    }
    @Test public void loneNumericCollectorIsNotDiscardedFromRegionAlone() throws Exception {
        ImmutableEvidenceLedgerV2 l=focused("tcg",f("collectorNumber","47","collector number","beside flavor text box"));
        assertTrue(l.hasObserved("collectorNumber"));
    }
    @Test public void twoActualCollectorNumbersStillConflict() throws Exception {
        ImmutableEvidenceLedgerV2 l=focused("tcg",f("collectorNumber","17/80","collector number","bottom-right edge"),f("collectorNumber","18/80","collector number","bottom-right closeup"));
        assertFalse(ConflictResolverV2.resolve(l,DomainProfileRouterV2.Profile.TCG_CARD).isEmpty());
    }
    @Test public void quantityGuaranteeIsConfigurationEvenWhenMiskeyedAsFormat() throws Exception {
        ImmutableEvidenceLedgerV2 l=focused("sealed_trading_card_product",f("commercialFormat","2 AUTOGRAPHS IN EVERY BOX*","packaging format badge text","bottom banner"));
        assertFalse(l.hasObserved("commercialFormat"));assertEquals("2 AUTOGRAPHS IN EVERY BOX*",l.strongest("configuration").rawValue);
    }
    @Test public void fullLineAndSubseriesRoleRetainsLiteralObservation() throws Exception {
        ImmutableEvidenceLedgerV2 l=focused("sealed_trading_card_product",f("productLine","Acme Prism Update Series","full product line and subseries","center-lower product title"));assertTrue(l.hasObserved("productLine"));
    }
    @Test public void genericSportsBrandRoleNeedsLogoLocationAndOcrCorroboration() throws Exception {
        JSONObject p=new JSONObject().put("category","sports_card").put("facts",new JSONArray().put(f("brand","Acme","brand","lower-center logo")));
        ImmutableEvidenceLedgerV2 without=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestPrimary(p,without);assertFalse(without.hasObserved("brand"));
        ImmutableEvidenceLedgerV2 with=new ImmutableEvidenceLedgerV2();Models.LocalScan local=new Models.LocalScan();local.textByImage.add("2020 ACME INTERNATIONAL. PRINTED IN U.S.A.");ObservationExtractorV2.ingestLocal(local,with);ObservationExtractorV2.ingestPrimary(p,with);assertTrue(with.hasObserved("brand"));
    }
    @Test public void unestablishedGuaranteeCannotMatchObservedQuantity() {
        assertEquals(SemanticRelationV3.Relation.AMBIGUOUS,SemanticRelationV3.relate("configuration","1 autograph in every box","11 cards per pack; pack count not shown; 1 autograph per box not established"));
        assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE,SemanticRelationV3.relate("configuration","1 autograph per box","3 autographs per box; packs per box unknown"));
    }
    private Models.Identification sealed(String unknown,boolean completeConfig) throws Exception {
        ImmutableEvidenceLedgerV2 l=focused("sealed_trading_card_product",f("brand","Acme","manufacturer logo","lower center logo"),f("productLine","Acme Prism Update Series","full product line","center title"),f("productReleaseYear","2024-25","printed season","side panel"),f("configuration","2 autographs per box","printed configuration","bottom banner"));
        IdentityCandidateV2 c=new IdentityCandidateV2("row",DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,"WEB");
        c.fields.put("manufacturer","Acme");c.fields.put("productLine","Acme Prism Update Series");c.fields.put("productReleaseYear","2024-25");c.fields.put("commercialFormat","Hobby Box");c.fields.put("configuration",completeConfig?"6 cards per pack; 12 packs per box; 2 autographs per box":"2 autographs per box");c.unknownFields.add(unknown);
        c.retrieved=true;c.exactReference=true;c.sourceRecordId="packaging-row";c.sourcePageScope="SINGLE_RECORD";c.webSourceQuality=92;c.sourceUrl="https://catalog.example/product";
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(Arrays.asList(c),l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;FinalStateReducerV2.reduce(id,l,c.domain,ranked,new ArrayList<>(),"");return id;
    }
    @Test public void missingPhysicalFormatLabelDoesNotUndoFullCatalogConfiguration() throws Exception {
        assertEquals("Hobby Box",sealed("packaging format label is not fully visible",true).sealedFormat);
        assertEquals("",sealed("whether photographed box is hobby or jumbo",true).sealedFormat);
        assertEquals("",sealed("packaging format label is not fully visible",false).sealedFormat);
    }
}
