package com.flipcheck.nativebeta;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class V148TargetedTraceTest {
    private JSONObject f(String key,String value,String role,String location) throws Exception {
        return new JSONObject().put("key",key).put("value",value).put("role",role).put("location",location)
                .put("image",0).put("side","front").put("confidence",98);
    }
    private JSONObject payload(String category,JSONObject... facts) throws Exception {
        JSONArray a=new JSONArray();for(JSONObject fact:facts)a.put(fact);return new JSONObject().put("category",category).put("facts",a);
    }
    @Test public void sportsPublisherLogoCannotBecomeSecondAthlete() throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();Models.LocalScan local=new Models.LocalScan();
        local.textByImage.add("PLAYER EXAMPLE 81 EXAMPLEBOX INTERNATIONAL");ObservationExtractorV2.ingestLocal(local,l);
        ObservationExtractorV2.ingestFocused(payload("sports_card",
                f("cardName","Player Example","card subject","center portrait caption"),
                f("cardName","ExampleBox","brand mark","lower center logo")),l,DomainProfileRouterV2.Profile.SPORTS_CARD,"crop");
        assertEquals("Player Example",l.strongest("athlete").rawValue);assertEquals("ExampleBox",l.strongest("brand").rawValue);
        assertTrue(ConflictResolverV2.resolve(l,DomainProfileRouterV2.Profile.SPORTS_CARD).isEmpty());
    }
    @Test public void uncorroboratedSportsLogoRoleDoesNotInventBrand() throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestFocused(payload("sports_card",
                f("cardName","Unseen Publisher","brand mark","lower center logo")),l,DomainProfileRouterV2.Profile.SPORTS_CARD,"crop");
        assertFalse(l.hasObserved("brand"));assertFalse(l.hasObserved("athlete"));assertTrue(l.hasObserved("printedLabel"));
    }
    @Test public void tcgSpeciesDescriptorCannotReplaceLocalizedCardName() throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestPrimary(payload("tcg_card",
                f("cardName","Example Bloom","card name","top center"),
                f("cardName","Flower creature","species descriptor","below artwork")),l);
        assertEquals("Example Bloom",l.strongest("cardName").rawValue);
        assertTrue(l.current("printedLabel").stream().anyMatch(a->a.rawValue.equals("Flower creature")));
        assertTrue(ConflictResolverV2.resolve(l,DomainProfileRouterV2.Profile.TCG_CARD).isEmpty());
    }
    @Test public void matchingNumericKeypadDescriptionsRemainCompatible() {
        assertTrue(SemanticRelationV3.compatible(SemanticRelationV3.relate("numericKeypad",
                "digits 1 through 9 and 0 in a three-column grid","1-9 in three columns; SUBTITLE, 0, TEXT bottom row")));
        assertEquals(SemanticRelationV3.Relation.AMBIGUOUS,SemanticRelationV3.relate("numericKeypad",
                "digits 1 through 9 and 0 in a three-column grid","not fully specified in retrieved record"));
    }
    @Test public void differentNumericKeypadTopologyStillConflicts() {
        assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE,SemanticRelationV3.relate("numericKeypad",
                "digits 1 through 9 and 0 in a three-column grid","six digit keys in two columns"));
    }
    @Test public void physicalContainerDescriptionIsNotCommercialFormat() throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestFocused(payload("sealed_trading_card_product",
                f("brand","Example","manufacturer logo","center logo"),
                f("productLine","Example Update Series","full product line","center title"),
                f("productReleaseYear","2025-26","printed season","side panel"),
                f("commercialFormat","sealed cardboard box","physical container","whole package")),l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,"crop");
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,new ArrayList<>(),new ArrayList<>(),"");
        assertEquals("",id.sealedFormat);assertEquals("TO_VERIFY",id.commercialFormatStatus);
    }
    @Test public void sealedRetrievalDemandsAttributableCompleteFormatRecord() throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        l.append("configuration","1 autograph per box",EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.PRIMARY_VISION,
                "test",0,"front","bottom banner","","printed configuration",98,98,"test","");
        String prompt=CandidateRetrieverV2.prompt(DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,l,new ArrayList<>());
        assertTrue(prompt.contains("use CHECKLIST_ROW only when one independently labelled section"));
        assertTrue(prompt.contains("cards-per-pack or packs-per-box are unconfirmed"));
    }
}
