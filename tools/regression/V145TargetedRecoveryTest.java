package com.flipcheck.nativebeta;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class V145TargetedRecoveryTest {
    private EvidenceAtom fact(ImmutableEvidenceLedgerV2 l,String key,String value) {
        return l.append(key,value,EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.FOCUSED_VISION,
            "test",0,"front","printed lower panel","","printed text",98,95,"test","");
    }
    @Test public void derivedDenominatorMustNotEraseCollectorFromWebQuery() {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();fact(l,"collectorNumber","17/80");
        TypedFieldNormalizerV2.normalize(l);
        assertTrue(CandidateRetrieverV2.prompt(DomainProfileRouterV2.Profile.TCG_CARD,l,new ArrayList<>()).contains("collectorNumber=17/80"));
    }
    @Test public void noisyEarlierDescriptionsMustNotTruncateCriticalQueryFields() {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        for(int i=0;i<24;i++)fact(l,"physicalFeature","decorative drawing background surface descriptive detail "+i);
        fact(l,"cardName","Example Creature");fact(l,"collectorNumber","17/80");fact(l,"edition","FIRST_EDITION");
        TypedFieldNormalizerV2.normalize(l);
        String p=CandidateRetrieverV2.prompt(DomainProfileRouterV2.Profile.TCG_CARD,l,new ArrayList<>());
        assertTrue(p.contains("collectorNumber=17/80"));assertTrue(p.contains("edition=FIRST_EDITION"));
    }
    @Test public void literalSportsMarkRolesRemainObservedWhenCorroborated() throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();fact(l,"printedLabel","Acme NEBULA");
        JSONArray fs=new JSONArray();
        fs.put(new JSONObject().put("key","brand").put("value","Acme").put("image",0).put("side","back").put("location","lower logo").put("role","manufacturer/brand marking").put("confidence",98));
        fs.put(new JSONObject().put("key","productLine").put("value","NEBULA").put("image",0).put("side","front").put("location","lower emblem").put("role","set/product-line mark").put("confidence",98));
        ObservationExtractorV2.ingestPrimary(new JSONObject().put("category","sports_card").put("facts",fs),l);
        assertTrue(l.hasObserved("brand"));assertTrue(l.hasObserved("productLine"));
    }
    @Test public void uncorroboratedPrimaryBrandMarkingStaysInferred() throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        JSONObject f=new JSONObject().put("key","brand").put("value","Acme").put("image",0).put("location","lower logo").put("role","manufacturer/brand marking").put("confidence",98);
        ObservationExtractorV2.ingestPrimary(new JSONObject().put("category","sports_card").put("facts",new JSONArray().put(f)),l);
        assertFalse(l.hasObserved("brand"));
    }
    @Test public void genericSealedDescriptionCannotBecomeConfirmedFormat() {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();fact(l,"commercialFormat","sealed trading card product");fact(l,"brand","Acme");fact(l,"productLine","Prism");fact(l,"productReleaseYear","2024-25");
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,new ArrayList<>(),new ArrayList<>(),"");
        assertEquals("",id.sealedFormat);assertEquals("TO_VERIFY",id.commercialFormatStatus);assertEquals("CONFIRMED",id.coreIdentityStatus);
    }
    @Test public void sportsPrintedBrandSurvivesCompositeCatalogPublisher() {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        fact(l,"brand","Acme");fact(l,"athlete","Player Example");fact(l,"productLine","Nebula");fact(l,"physicalCardNumber","72");
        IdentityCandidateV2 c=new IdentityCandidateV2("record",DomainProfileRouterV2.Profile.SPORTS_CARD,"WEB");
        c.fields.put("manufacturer","Parent/Acme");c.fields.put("athlete","Player Example");c.fields.put("productLine","Nebula");c.fields.put("catalogCardNumber","72");c.fields.put("productReleaseYear","2024-25");
        c.retrieved=true;c.exactReference=true;c.sourceRecordId="base-72";c.sourcePageScope="SINGLE_RECORD";c.webSourceQuality=92;c.sourceUrl="https://catalog.example/base72";
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(Arrays.asList(c),l,DomainProfileRouterV2.Profile.SPORTS_CARD);
        assertFalse(c.rejectionReason,c.rejected);assertTrue(c.disproofPassed);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.SPORTS_CARD,ranked,new ArrayList<>(),"");
        assertEquals("Acme",id.brand);assertEquals("Parent/Acme",id.catalogBrand);
        c.fields.put("manufacturer","Parent/Other");c.rejected=false;
        CandidateVerifierV2.verify(Arrays.asList(c),l,DomainProfileRouterV2.Profile.SPORTS_CARD);
        assertTrue(c.rejected);
    }

}
