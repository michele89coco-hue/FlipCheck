package com.flipcheck.nativebeta;

import java.util.*;
import java.nio.file.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class V155SlabEvidenceTest {
    private JSONObject recording()throws Exception{
        return new JSONObject(new String(Files.readAllBytes(Paths.get("tools/regression/fixtures/v155-recorded-slab.json")),java.nio.charset.StandardCharsets.UTF_8));
    }
    private ImmutableEvidenceLedgerV2 ledger(JSONObject recording){
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        ObservationExtractorV2.ingestPrimary(recording.optJSONObject("v154_vision1_observation"),l);
        TypedFieldNormalizerV2.normalize(l);
        ObservationExtractorV2.ingestFocused(recording.optJSONObject("v154_vision2_physical_review"),l,DomainProfileRouterV2.Profile.TCG_CARD,"recorded-review");
        TypedFieldNormalizerV2.normalize(l);return l;
    }
    @Test public void recordedLabelRetainsSetGradeAndCertificationWithoutPollutingCard()throws Exception{
        ImmutableEvidenceLedgerV2 l=ledger(recording());
        assertTrue("Expedition".equalsIgnoreCase(SlabEvidenceV155.value(l,"slabSetName")));
        assertEquals("8",SlabEvidenceV155.value(l,"slabCardNumber"));
        assertEquals("2002",SlabEvidenceV155.value(l,"slabYear"));
        assertEquals("9",SlabEvidenceV155.value(l,"gradingGrade"));
        assertEquals("0014436473",SlabEvidenceV155.value(l,"gradingCertification"));
        assertFalse(l.hasObserved("physicalSerial"));assertFalse(l.hasObserved("brand"));
        assertFalse(l.hasObserved("collectorNumber"));assertFalse(l.hasObserved("physicalCardNumber"));
        assertTrue(SlabEvidenceV155.value(l,"gradingSubgrades").contains("9.5"));
        assertFalse(l.hasObserved("statisticsSeason"));
    }
    @Test public void recordedCatalogNumberMatchesSlabNumeratorWithinSameSet()throws Exception{
        JSONObject r=recording();ImmutableEvidenceLedgerV2 l=ledger(r);
        List<IdentityCandidateV2> candidates=CandidateRetrieverV2.parseReplay(r.optJSONObject("v154_web2_verification"),DomainProfileRouterV2.Profile.TCG_CARD,l);
        IdentityCandidateV2 original=null;for(IdentityCandidateV2 c:candidates)if(c.candidateId.equals("cand_002"))original=c;
        assertNotNull(original);
        CandidateVerifierV2.verify(Arrays.asList(original),l,DomainProfileRouterV2.Profile.TCG_CARD);
        assertEquals(SemanticRelationV3.Relation.PARENT,original.fieldRelations.get("catalogCardNumber"));
        assertTrue(original.disproofReason,original.disproofPassed);
        assertFalse(original.rejected);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.TCG_CARD,Arrays.asList(original),new ArrayList<>(),"");
        assertTrue(id.finalDecisionReason,id.identityConfirmed);
        assertEquals("Cloyster",id.model);assertEquals("Expedition Base Set",id.family);
        assertEquals("8/165",id.sourceConfirmedCatalogNumber);
        assertEquals("",id.physicalCollectorNumber);assertEquals("CATALOG_ONLY",id.combinedVerification);
        assertEquals("LABEL_READ_NOT_AUTHENTICATED",id.gradingStatus);
        assertEquals("0014436473",id.gradingCertification);assertEquals("",id.physicalSerial);
        assertEquals("8/165",new JSONObject(DiagnosticExportV2.create(id,null,"")).getString("cardNumber"));
    }
    private IdentityCandidateV2 candidate(String set,String number){
        IdentityCandidateV2 c=new IdentityCandidateV2("test",DomainProfileRouterV2.Profile.TCG_CARD,"WEB");
        c.fields.put("setName",set);c.fields.put("cardName","Cloyster");c.fields.put("catalogCardNumber",number);return c;
    }
    @Test public void partialNumberDoesNotMatchDifferentSetOrDifferentNumerator()throws Exception{
        ImmutableEvidenceLedgerV2 l=ledger(recording());EvidenceAtom n=SlabEvidenceV155.observed(l,"slabCardNumber");
        assertFalse(SemanticRelationV3.compatible(SlabEvidenceV155.numberRelation(n,candidate("Different Set","8/165"),l)));
        assertFalse(SemanticRelationV3.compatible(SlabEvidenceV155.numberRelation(n,candidate("Expedition","42/165"),l)));
        assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE,SemanticRelationV3.relate("collectorNumber","8/100","8/165"));
        assertFalse(TypedFieldNormalizerV2.equivalent("collectorNumber","8","8/165"));
    }
    @Test public void slabCannotOverrideAnActuallyDifferentCardNumber()throws Exception{
        JSONObject r=recording();ImmutableEvidenceLedgerV2 l=ledger(r);
        l.append("collectorNumber","42/165",EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.FOCUSED_VISION,
                "photo",1,"front","bottom right card print","crop","collector number",99,90,"test","");
        List<IdentityCandidateV2> records=CandidateRetrieverV2.parseReplay(r.optJSONObject("v154_web2_verification"),DomainProfileRouterV2.Profile.TCG_CARD,l);
        CandidateVerifierV2.verify(records,l,DomainProfileRouterV2.Profile.TCG_CARD);
        for(IdentityCandidateV2 c:records)if(c.value("catalogCardNumber").equals("8/165"))assertFalse(c.disproofPassed);
    }
    @Test public void nonHolographicMustNeverNormalizeToHolo(){
        assertEquals("NON_HOLO",TypedFieldNormalizerV2.normalizeValue("finish","Non-Holographic",""));
        assertEquals("NON_HOLO",TypedFieldNormalizerV2.normalizeValue("finish","nonholographic",""));
        assertFalse(SemanticRelationV3.compatible(SemanticRelationV3.relate("finish","Holo","Non-Holographic")));
    }
    @Test public void graderCertificateAndGradeStayOutOfCardIdentifierQuery()throws Exception{
        ImmutableEvidenceLedgerV2 l=ledger(recording());String query=CandidateRetrieverV2.prompt(DomainProfileRouterV2.Profile.TCG_CARD,l,new ArrayList<>());
        assertTrue(query.contains("slabSetName="));assertTrue(query.contains("slabCardNumber=8"));
        assertFalse(query.contains("physicalSerial=0014436473"));assertFalse(query.contains("brand=Beckett"));
    }
    @Test public void multiscopePageIsNotPromotedToIsolatedRecord()throws Exception{
        JSONObject r=recording();ImmutableEvidenceLedgerV2 l=ledger(r);
        List<IdentityCandidateV2> records=CandidateRetrieverV2.parseReplay(r.optJSONObject("v154_web1_discovery"),DomainProfileRouterV2.Profile.TCG_CARD,l);
        CandidateVerifierV2.verify(records,l,DomainProfileRouterV2.Profile.TCG_CARD);
        for(IdentityCandidateV2 c:records)if(c.candidateId.equals("cand-001")){
            assertFalse(c.disproofPassed);assertTrue(c.rejected);
        }
    }
}
