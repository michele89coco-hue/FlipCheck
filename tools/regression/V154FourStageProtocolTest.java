package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises production orchestration with an in-memory client: no network or API key. */
public class V154FourStageProtocolTest {
    static final class FakeClient extends OpenAiClient {
        final List<String> calls=new ArrayList<>();
        final List<String> webPrompts=new ArrayList<>();
        String reviewPrompt="";String category="sports_card";
        boolean failDiscovery,failFinal,noSearchFinal,invalidPrimary;
        FakeClient(){super("");}
        @Override Response observeFullV154(List<String> images,String prompt)throws Exception{
            calls.add("VISION1");Response r=photo(category,"No. 21");
            if(invalidPrimary){r.complete=false;r.payload=null;}return r;
        }
        @Override Response observeTechnicalRecovery(List<String> images,String prompt)throws Exception{
            calls.add("TECHNICAL_RETRY");Response r=new Response();r.complete=false;return r;
        }
        @Override Response observeReviewV154(List<String> images,String prompt)throws Exception{
            calls.add("VISION2");reviewPrompt=prompt;
            assertTrue(images.contains("front"));assertTrue(images.contains("back"));
            Response r=photo(category,"No. 21");
            r.payload.getJSONArray("facts").put(fact("productReleaseYear","2025-26","printed season","bottom product identification line"));return r;
        }
        @Override Response identityWebSearchV2(List<String> images,String prompt)throws Exception{
            int n=webPrompts.size()+1;calls.add("WEB"+n);webPrompts.add(prompt);
            if(n==1&&failDiscovery||n==2&&failFinal)throw new java.net.SocketTimeoutException("test timeout");
            Response r=new Response();r.payload=new JSONObject().put("queries",new JSONArray().put("observed product reference"))
                    .put("candidates",new JSONArray()).put("retrieval_reason","fixture");
            r.usage.requests=1;r.usage.webCalls=n==2&&noSearchFinal?0:1;r.usage.costUsd=.018;return r;
        }
    }
    private static JSONObject fact(String key,String value,String role,String location)throws Exception{
        return new JSONObject().put("key",key).put("value",value).put("role",role).put("location",location)
                .put("image",1).put("side","back").put("confidence",99);
    }
    private static OpenAiClient.Response photo(String category,String number)throws Exception{
        OpenAiClient.Response r=new OpenAiClient.Response();
        r.payload=new JSONObject().put("category",category).put("content_sufficient",true)
                .put("views",new JSONArray().put("front").put("back"))
                .put("facts",new JSONArray().put(fact("physicalCardNumber",number,"printed card number","upper right identifier")))
                .put("candidates",new JSONArray());
        r.usage.requests=1;r.usage.visionCalls=1;r.usage.costUsd=.005;return r;
    }
    private Models.Identification run(FakeClient client,Models.Usage usage)throws Exception{
        Models.LocalScan local=new Models.LocalScan();local.textByImage.add("No. 21");local.textByImage.add("product label");
        return UniversalIdentityEngineV2.identify(local,Arrays.asList("front","back"),"",client,usage);
    }
    @Test public void fourStagesExecuteEvenAfterOldBudgetIsExceeded()throws Exception{
        FakeClient client=new FakeClient();Models.Usage usage=new Models.Usage();Models.Identification id=run(client,usage);
        assertEquals(Arrays.asList("VISION1","WEB1","VISION2","WEB2"),client.calls);
        assertEquals(4,usage.requests);assertEquals(2,usage.webCalls);assertTrue(usage.costUsd>.025);
        assertTrue(client.webPrompts.get(1).contains("productReleaseYear=2025-26"));
        assertTrue(client.webPrompts.get(1).contains("physicalCardNumber=21"));
        assertTrue(client.webPrompts.get(1).contains("FINAL VERIFICATION PASS"));
        assertFalse(client.reviewPrompt.contains("No. 21"));assertFalse(client.reviewPrompt.contains("EXISTING_OBSERVED"));
        assertEquals("COMPLETED",id.webStatus);
        assertEquals(4,id.v2StagePayloads.size());assertTrue(id.v2StagePayloads.get(2).startsWith("v154_vision2"));
    }
    @Test public void discoveryFailureStillAllowsReviewAndFinalSearch()throws Exception{
        FakeClient client=new FakeClient();client.failDiscovery=true;Models.Identification id=run(client,new Models.Usage());
        assertEquals(Arrays.asList("VISION1","WEB1","VISION2","WEB2"),client.calls);
        assertEquals("COMPLETED",id.webStatus);assertEquals("NONE",id.pipelineFailureDomain);
        assertTrue(id.v2RecoveryTrace.contains("TIMEOUT"));
    }
    @Test public void finalFailureCannotBeReportedAsCompleted()throws Exception{
        FakeClient client=new FakeClient();client.failFinal=true;Models.Identification id=run(client,new Models.Usage());
        assertEquals("RETRYABLE_TECHNICAL",id.webStatus);assertTrue(id.pipelineFailureDomain.contains("web2"));
        assertEquals(4,client.calls.size());
    }
    @Test public void finalResponseWithoutSearchIsNotAWebConfirmation()throws Exception{
        FakeClient client=new FakeClient();client.noSearchFinal=true;Models.Identification id=run(client,new Models.Usage());
        assertEquals("RETRYABLE_TECHNICAL",id.webStatus);assertTrue(id.pipelineFailureDomain.contains("NO_SEARCH"));
    }
    @Test public void invalidPrimaryDoesNotLaunchUngroundedSearches()throws Exception{
        FakeClient client=new FakeClient();client.invalidPrimary=true;run(client,new Models.Usage());
        assertEquals(Arrays.asList("VISION1","TECHNICAL_RETRY"),client.calls);assertTrue(client.webPrompts.isEmpty());
    }
    @Test public void sameProtocolCoversElectronicsAndGenericCollectibles()throws Exception{
        for(String category:Arrays.asList("consumer_electronics","other_collectible","tcg","sealed_trading_card_product")){
            FakeClient client=new FakeClient();client.category=category;run(client,new Models.Usage());
            assertEquals(Arrays.asList("VISION1","WEB1","VISION2","WEB2"),client.calls);
        }
    }
    @Test public void numberLabelsNormalizeWithoutCorroborationButPrefixesRemain()throws Exception{
        for(String label:Arrays.asList("No. 21","NO.21","#21","N°21","No 21")){
            ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();
            ObservationExtractorV2.ingestPrimary(photo("sports_card",label).payload,ledger);
            TypedFieldNormalizerV2.normalize(ledger);
            assertEquals("21",ledger.strongest("physicalCardNumber",EvidenceAtom.EpistemicLevel.OBSERVED).normalizedValue);
            assertTrue(TypedFieldNormalizerV2.equivalent("catalogCardNumber",label,"21"));
        }
        assertFalse(TypedFieldNormalizerV2.equivalent("collectorNumber","H23","23"));
        assertFalse(TypedFieldNormalizerV2.equivalent("physicalCardNumber","21a","21"));
        assertFalse(TypedFieldNormalizerV2.equivalent("physicalSerial","2/5","2/15"));
        assertEquals("NO.21",TypedFieldNormalizerV2.normalizeValue("model","NO.21",""));
    }
    @Test public void productSeasonIsNotAStatisticsSeason()throws Exception{
        ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();
        JSONObject payload=new JSONObject().put("facts",new JSONArray()
                .put(fact("statisticsSeason","2025-26","season","bottom set line"))
                .put(fact("statisticsSeason","2024-25","statistics season","career statistics table")));
        ObservationExtractorV2.ingestFocused(payload,ledger,DomainProfileRouterV2.Profile.SPORTS_CARD,"test");
        assertEquals("2025-26",ledger.strongest("productReleaseYear",EvidenceAtom.EpistemicLevel.OBSERVED).normalizedValue);
        assertEquals("2024-25",ledger.strongest("statisticsSeason",EvidenceAtom.EpistemicLevel.OBSERVED).normalizedValue);
    }
    @Test public void finalRecordReplacesDuplicateWithoutBorrowingFields(){
        IdentityCandidateV2 a=new IdentityCandidateV2("first",DomainProfileRouterV2.Profile.SPORTS_CARD,"WEB");
        IdentityCandidateV2 b=new IdentityCandidateV2("last",DomainProfileRouterV2.Profile.SPORTS_CARD,"WEB");
        a.fields.put("catalogCardNumber","No. 21");b.fields.put("catalogCardNumber","21");
        a.sourceUrl="first-source";b.sourceUrl="second-source";
        List<IdentityCandidateV2> records=UniversalIdentityEngineV2.mergeWebRecords(Arrays.asList(a),Arrays.asList(b));
        assertEquals(1,records.size());assertSame(b,records.get(0));assertEquals("second-source",records.get(0).sourceUrl);
    }
}
