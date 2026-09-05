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
}
