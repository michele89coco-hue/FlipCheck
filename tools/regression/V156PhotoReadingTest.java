package com.flipcheck.nativebeta;

import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Production orchestration with recorded responses; never calls the network. */
public class V156PhotoReadingTest {
    private JSONObject recording()throws Exception{
        return new JSONObject(new String(Files.readAllBytes(Paths.get("tools/regression/fixtures/v156-recorded-slab.json")),java.nio.charset.StandardCharsets.UTF_8));
    }
    private static final class RecordedClient extends OpenAiClient {
        final JSONObject recording;final List<String> calls=new ArrayList<>();int web;
        boolean invalidPrimary,omitSources;
        RecordedClient(JSONObject r){super("");recording=r;}
        private Response response(String stage,boolean search)throws Exception{
            Response r=new Response();r.payload=new JSONObject(recording.getJSONObject(stage).toString());
            r.usage.requests=1;r.usage.visionCalls=1;r.usage.webCalls=search?1:0;r.usage.costUsd=search?.015:.004;
            if(search&&!omitSources){
                // Source-binding fixtures are explicit; this does not re-fetch the pages.
                JSONArray candidates=r.payload.getJSONArray("candidates");
                for(int i=0;i<candidates.length();i++){
                    Models.Source s=new Models.Source();s.url=candidates.getJSONObject(i).getString("source_url");r.sources.add(s);
                }
            }
            return r;
        }
        @Override Response observeFullV154(List<String> images,String prompt)throws Exception{
            calls.add("VISION1");Response r=response("v154_vision1_observation",false);
            if(invalidPrimary){r.payload=null;r.complete=false;}return r;
        }
        @Override Response observeReviewV154(List<String> images,String prompt)throws Exception{
            calls.add("VISION2");return response("v154_vision2_physical_review",false);
        }
        @Override Response identityWebSearchV2(List<String> images,String prompt)throws Exception{
            calls.add("WEB"+(++web));
            return response(web==1?"v154_web1_discovery":"v154_web2_verification",true);
        }
        @Override Response observeTechnicalRecovery(List<String> images,String prompt){throw new AssertionError("Unexpected extra paid call");}
    }
    private List<String> images(){return Arrays.asList("recorded-original","recorded-detail");}
    private Models.LocalScan local(){Models.LocalScan l=new Models.LocalScan();l.textByImage.add("");return l;}
    private ImmutableEvidenceLedgerV2 ledger(JSONObject r){
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        ObservationExtractorV2.ingestPrimary(r.optJSONObject("v154_vision1_observation"),l);
        TypedFieldNormalizerV2.normalize(l);
        ObservationExtractorV2.ingestFocused(r.optJSONObject("v154_vision2_physical_review"),l,DomainProfileRouterV2.Profile.TCG_CARD,"recorded-review");
        TypedFieldNormalizerV2.normalize(l);return l;
    }
    @Test public void fullRecordedFourStageRunClosesCatalogWithoutSelectingOneCandidateByHand()throws Exception{
        RecordedClient client=new RecordedClient(recording());Models.Usage usage=new Models.Usage();
        Models.Identification id=UniversalIdentityEngineV2.identify(local(),images(),"",client,usage);
        assertEquals(Arrays.asList("VISION1","WEB1","VISION2","WEB2"),client.calls);
        assertTrue(id.v2CandidateTrace,id.identityConfirmed);
        assertEquals("c1",id.candidateWinnerId);assertEquals("8/165",id.sourceConfirmedCatalogNumber);
        assertEquals("",id.physicalCollectorNumber);assertEquals("HOLO",id.finish);
        assertEquals("CATALOG_ONLY",id.combinedVerification);assertEquals("PASS",id.consistencyInvariants);
        assertEquals("Expedition Base Set",id.family);assertEquals(2,usage.webCalls);
        assertEquals("0014436473",id.gradingCertification);assertEquals("",id.physicalSerial);
    }
    @Test public void photoReadingUsesOneVisionAndShowsSlabWithoutCatalogConfirmation()throws Exception{
        RecordedClient client=new RecordedClient(recording());Models.Usage usage=new Models.Usage();
        Models.Identification id=UniversalIdentityEngineV2.readPhoto(local(),images(),"",client,usage);
        assertEquals(Arrays.asList("VISION1"),client.calls);assertEquals(1,usage.requests);assertEquals(0,usage.webCalls);
        assertEquals("PHOTO_READ",id.identityStatus);assertFalse(id.identityConfirmed);assertFalse(id.marketReady);
        assertFalse(id.catalogVerified);assertEquals("",id.sourceConfirmedCatalogNumber);
        assertTrue(id.title,id.title.contains("Cloyster")&&id.title.contains("EXPEDITION")&&id.title.contains("#8"));
        assertTrue(id.photoReadingSummary.contains("0014436473"));assertTrue(id.photoReadingSummary.contains("9.5"));
        assertTrue(id.photoReadingSummary.contains("80 PV"));assertEquals("2002",id.slabYear);
        EvidencePolicy.apply(id);assertEquals("PHOTO_READ",id.identityStatus);
        assertEquals("DATI LETTI DALLA FOTO",IdentityPresentationV2.status(id));
        JSONObject exported=new JSONObject(DiagnosticExportV2.create(id,usage,""));
        assertTrue(exported.getBoolean("photoReadingOnly"));assertFalse(exported.has("photoInputSignature"));
    }
    @Test public void webVerificationResumesReadingWithOnlyThreeNewCalls()throws Exception{
        RecordedClient client=new RecordedClient(recording());Models.Usage firstUsage=new Models.Usage();
        Models.Identification reading=UniversalIdentityEngineV2.readPhoto(local(),images(),"",client,firstUsage);
        Models.Usage total=new Models.Usage();
        Models.Identification id=UniversalIdentityEngineV2.verifyAfterReading(local(),images(),"",client,total,reading,firstUsage);
        assertEquals(Arrays.asList("VISION1","WEB1","VISION2","WEB2"),client.calls);
        assertTrue(id.v2CandidateTrace,id.identityConfirmed);assertEquals(4,total.requests);assertEquals(2,total.webCalls);
        assertEquals(.038,total.costUsd,.000001);assertEquals(1,firstUsage.requests);
        assertTrue(id.v2RecoveryTrace.contains("vision1_reused"));assertEquals(4,id.v2StagePayloads.size());
    }
    @Test public void changedImagesOrHintsCannotReuseAnUnrelatedPhotoReading()throws Exception{
        RecordedClient client=new RecordedClient(recording());Models.Usage usage=new Models.Usage();
        Models.Identification reading=UniversalIdentityEngineV2.readPhoto(local(),images(),"",client,usage);
        assertNull(PhotoReadingV156.cachedResponse(reading,usage,Arrays.asList("other"),""));
        assertNull(PhotoReadingV156.cachedResponse(reading,usage,images(),"changed hint"));
        assertNotNull(PhotoReadingV156.cachedResponse(reading,usage,images(),""));
        assertNotEquals(PhotoReadingV156.signature(Arrays.asList("ab","c"),""),PhotoReadingV156.signature(Arrays.asList("a","bc"),""));
    }
    @Test public void photoReadingTechnicalFailureCannotStartWebOrExtraVision()throws Exception{
        RecordedClient client=new RecordedClient(recording());client.invalidPrimary=true;
        Models.Usage usage=new Models.Usage();Models.Identification id=UniversalIdentityEngineV2.readPhoto(local(),images(),"",client,usage);
        assertEquals(Arrays.asList("VISION1"),client.calls);assertEquals("TECHNICAL_FAILURE",id.identityStatus);
        assertNull(PhotoReadingV156.cachedResponse(id,usage,images(),""));
    }
    @Test public void combinedSetYearIsNormalizedBeforeAnyWebAndRetainsLiteralEvidence()throws Exception{
        ImmutableEvidenceLedgerV2 l=ledger(recording());
        assertTrue("Expedition".equalsIgnoreCase(SlabEvidenceV155.value(l,"slabSetName")));
        assertEquals("2002",SlabEvidenceV155.value(l,"slabYear"));
        assertTrue(l.all().stream().anyMatch(a->a.field.equals("slabSetName")&&a.rawValue.equals("2002 EXPEDITION")&&a.parentEvidenceId.isEmpty()));
        int before=l.all().size();TypedFieldNormalizerV2.normalize(l);assertEquals(before,l.all().size());
        IdentityCandidateV2 candidate=new IdentityCandidateV2("test",DomainProfileRouterV2.Profile.TCG_CARD,"WEB");
        candidate.fields.put("setName","Expedition Base Set");candidate.fields.put("cardName","Cloyster");candidate.fields.put("catalogCardNumber","8/165");
        assertEquals(SemanticRelationV3.Relation.PARENT,SlabEvidenceV155.numberRelation(SlabEvidenceV155.observed(l,"slabCardNumber"),candidate,l));
    }
    @Test public void yearSplittingIsGeneralAndDoesNotRemoveEmbeddedSetNumbers(){
        for(String[] values:new String[][]{{"1999 Alpha League","Alpha League","1999"},{"2024-25 Premier Collection","Premier Collection","2024-25"},{"Base Set 2","Base Set 2",""},{"Series 2000","Series 2000",""}}){
            ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
            l.append("slabSetName",values[0],EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.PRIMARY_VISION,"photo",1,"front","upper slab label","","set name",99,95,"test","");
            TypedFieldNormalizerV2.normalize(l);
            assertEquals(values[1],SlabEvidenceV155.value(l,"slabSetName"));assertEquals(values[2],SlabEvidenceV155.value(l,"slabYear"));
        }
    }
    @Test public void wrongSetYearNumberAndFinishStillFail()throws Exception{
        for(String[] mutation:new String[][]{{"set_name","Different Set"},{"year","2001"},{"card_number","42/165"},{"finish","Non-Holographic"},{"finish","Reverse Holo"}}){
            JSONObject r=recording();ImmutableEvidenceLedgerV2 l=ledger(r);
            JSONObject row=new JSONObject(r.getJSONObject("v154_web2_verification").getJSONArray("candidates").getJSONObject(0).toString());row.put(mutation[0],mutation[1]);
            List<IdentityCandidateV2> candidates=CandidateRetrieverV2.parse(new JSONObject().put("candidates",new JSONArray().put(row)),DomainProfileRouterV2.Profile.TCG_CARD,l);
            CandidateVerifierV2.verify(candidates,l,DomainProfileRouterV2.Profile.TCG_CARD);assertTrue(Arrays.toString(mutation),candidates.get(0).rejected);
        }
    }
    @Test public void splittingSetYearCannotHideConflictingLabelYears()throws Exception{
        JSONObject r=recording();
        JSONArray facts=r.getJSONObject("v154_vision2_physical_review").getJSONArray("facts");
        for(int i=0;i<facts.length();i++)if(facts.getJSONObject(i).getString("key").equals("slabYear"))facts.getJSONObject(i).put("value","2001");
        RecordedClient client=new RecordedClient(r);
        Models.Identification id=UniversalIdentityEngineV2.identify(local(),images(),"",client,new Models.Usage());
        assertEquals("CONFLICTED",id.identityStatus);assertFalse(id.identityConfirmed);
    }
    @Test public void fullReplayStillRequiresBoundWebSources()throws Exception{
        RecordedClient client=new RecordedClient(recording());client.omitSources=true;
        Models.Identification id=UniversalIdentityEngineV2.identify(local(),images(),"",client,new Models.Usage());
        assertFalse(id.identityConfirmed);assertFalse(id.catalogVerified);assertEquals("",id.sourceConfirmedCatalogNumber);
    }
}
