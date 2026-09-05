package com.flipcheck.nativebeta;

import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Actual recorded stages: no invented source, identifier, or successful API answer. */
public class V149RecordedEvidenceTest {
    static JSONObject fact(String key,String value,String role) throws Exception {
        return new JSONObject().put("key",key).put("value",value).put("role",role)
                .put("location","lower identity panel").put("image",0).put("confidence",98);
    }
    static JSONObject payload(JSONObject... facts) throws Exception {
        JSONArray a=new JSONArray();for(JSONObject f:facts)a.put(f);
        return new JSONObject().put("category","tcg").put("content_sufficient",true).put("facts",a);
    }
    @Test public void evolutionCannotSatisfyEditionAndRawMarkMustRemainInspectable() throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        ObservationExtractorV2.ingestFocused(payload(fact("edition","STAGE 2","evolution stage"),
                fact("setName","circular emblem with a numeral","raw set-symbol appearance")),l,DomainProfileRouterV2.Profile.TCG_CARD,"crop");
        TypedFieldNormalizerV2.normalize(l);
        assertFalse(l.hasObserved("edition"));assertTrue(l.hasObserved("evolutionStage"));
        assertTrue(l.hasObserved("visualSymbol"));
        assertTrue(AdaptiveRecoveryPlannerV2.needsEditionInspection(DomainProfileRouterV2.Profile.TCG_CARD,l));
        ObservationExtractorV2.ingestFocused(payload(fact("firstEditionMark","EDITION 1","printed edition stamp")),l,DomainProfileRouterV2.Profile.TCG_CARD,"stamp");
        TypedFieldNormalizerV2.normalize(l);
        assertFalse(AdaptiveRecoveryPlannerV2.needsEditionInspection(DomainProfileRouterV2.Profile.TCG_CARD,l));
        assertEquals("FIRST_EDITION",l.strongest("edition").normalizedValue);
    }
    @Test public void recoveryReservesCatalogBudgetAndDoesNotRunForOtherDomains() {
        Models.Usage u=new Models.Usage();u.costUsd=.006;
        assertTrue(AdaptiveRecoveryPlannerV2.canInspectEditionBeforeWeb(u));
        u.costUsd=.008;assertFalse(AdaptiveRecoveryPlannerV2.canInspectEditionBeforeWeb(u));
        assertFalse(AdaptiveRecoveryPlannerV2.needsEditionInspection(DomainProfileRouterV2.Profile.SPORTS_CARD,new ImmutableEvidenceLedgerV2()));
    }
    @Test public void weakerUnsupportedFocusedLineIsAHypothesisButCorroboratedTextIsNot() throws Exception {
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        ObservationExtractorV2.ingestPrimary(new JSONObject().put("category","sports_card").put("facts",new JSONArray()
                .put(fact("productLine","Cosmic Alloy","product line logo"))),l);
        ObservationExtractorV2.ingestFocused(payload(fact("productLine","Crystal 2000","product line").put("confidence",86)),l,DomainProfileRouterV2.Profile.SPORTS_CARD,"crop");
        assertFalse(l.hasObserved("productLine"));
        Models.LocalScan local=new Models.LocalScan();local.textByImage.add("Crystal 2000");ObservationExtractorV2.ingestLocal(local,l);
        ObservationExtractorV2.ingestFocused(payload(fact("productLine","Crystal 2000","product line").put("confidence",86)),l,DomainProfileRouterV2.Profile.SPORTS_CARD,"crop2");
        assertTrue(l.hasObserved("productLine"));
    }
    @Test public void productionRunsOneAttributeRecoveryWithoutChangingTheIdentityFields() throws Exception {
        EditionClient client=new EditionClient(.003,false);Models.Usage usage=new Models.Usage();
        Models.Identification id=UniversalIdentityEngineV2.identify(new Models.LocalScan(),Arrays.asList("test-image"),"",client,usage);
        assertEquals(2,client.focused);assertEquals(1,client.web);
        assertEquals("Example Bloom",id.model);assertEquals("15/64",id.physicalCollectorNumber);
        assertEquals("FIRST_EDITION",id.edition);assertTrue(usage.costUsd<=.025);
        assertTrue(id.v2CallReasons.contains("v149_focused_physical_edition_inspection"));
    }
    @Test public void productionSkipsRecoveryForKnownEditionOrInsufficientBudget() throws Exception {
        for(EditionClient client:Arrays.asList(new EditionClient(.003,true),new EditionClient(.006,false))){
            Models.Identification id=UniversalIdentityEngineV2.identify(new Models.LocalScan(),Arrays.asList("test-image"),"",client,new Models.Usage());
            assertEquals(1,client.focused);assertEquals(1,client.web);
            assertFalse(id.v2CallReasons.contains("v149_focused_physical_edition_inspection"));
        }
    }
    private static final class EditionClient extends OpenAiClient {
        int focused,web;final double initialCost;final boolean known;
        EditionClient(double cost,boolean known){super("unit-test-no-network");initialCost=cost;this.known=known;}
        private Response response(JSONObject p,double cost,boolean isWeb){Response r=new Response();r.payload=p;r.complete=true;r.usage.costUsd=cost;r.usage.requests=1;if(isWeb)r.usage.webCalls=1;else r.usage.visionCalls=1;return r;}
        @Override Response observe(List<String> images,String prompt) throws Exception {
            JSONObject p=payload(fact("cardName","Example Bloom","card name"),fact("collectorNumber","15/64","collector number"));
            if(known)p.getJSONArray("facts").put(fact("firstEditionMark","EDITION 1","edition stamp"));
            return response(p,initialCost,false);
        }
        @Override Response observeFocusedV2(List<String> images,String prompt) throws Exception {
            focused++;
            if(focused==1)return response(payload(fact("edition","STAGE 2","evolution stage")),.002,false);
            assertTrue(prompt.contains("PHYSICAL EDITION INSPECTION ONLY"));
            return response(payload(fact("firstEditionMark","EDITION 1","printed edition stamp"),
                    fact("cardName","Unrelated name","card name"),fact("collectorNumber","99/99","collector number")),.002,false);
        }
        @Override Response identityWebSearchV2(List<String> images,String prompt) throws Exception {
            web++;return response(new JSONObject().put("candidates",new JSONArray()),.012,true);
        }
    }
    @Test public void actualLiveGreensRemainCorrectAndFailuresAreNotForcedToPass() throws Exception {
        Path path=Paths.get("tools/regression/fixtures/v149-recorded-live.json.gz");
        JSONArray cases;
        try(java.io.InputStream input=new java.util.zip.GZIPInputStream(Files.newInputStream(path))){
            cases=new JSONObject(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getJSONArray("cases");
        }
        int greens=0;
        for(int i=0;i<cases.length();i++){
            JSONObject r=cases.getJSONObject(i);String label=r.getInt("build")+"/"+r.getString("case")+"/"+r.getString("mode");
            ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();Models.LocalScan local=new Models.LocalScan();
            JSONArray blocks=r.getJSONArray("localOcrBlocks");for(int j=0;j<blocks.length();j++)local.textByImage.add(blocks.getString(j));
            ObservationExtractorV2.ingestLocal(local,ledger);
            DomainProfileRouterV2.Profile profile=DomainProfileRouterV2.Profile.GENERIC_OBJECT;
            List<IdentityCandidateV2> candidates=new ArrayList<>();JSONArray stages=r.getJSONArray("stagePayloads");
            for(int j=0;j<stages.length();j++){
                String stage=stages.getString(j),name=stage.substring(0,stage.indexOf('\n'));
                JSONObject p=new JSONObject(stage.substring(stage.indexOf('\n')+1));
                if(name.contains("web"))candidates.addAll(CandidateRetrieverV2.parse(p,profile,ledger));
                else if(name.contains("focused"))candidates.addAll(ObservationExtractorV2.ingestFocused(p,ledger,profile,"recorded-focused").hypotheses);
                else {ObservationExtractorV2.Result primary=ObservationExtractorV2.ingestPrimary(p,ledger);profile=DomainProfileRouterV2.route(primary.category,ledger);candidates.addAll(primary.hypotheses);}
                TypedFieldNormalizerV2.normalize(ledger);
            }
            Models.Identification id=new Models.Identification();id.uploadedImageCount=Math.max(1,local.textByImage.size());
            FinalStateReducerV2.reduce(id,ledger,profile,CandidateVerifierV2.verify(candidates,ledger,profile),ConflictResolverV2.resolve(ledger,profile),"");
            assertTrue(label+id.consistencyInvariantErrors,id.consistencyInvariantErrors.isEmpty());
            boolean expectedGreen="PASS".equals(r.getString("liveStatus"));
            if(expectedGreen){greens++;assertExpectedIdentity(label,r.getString("case"),id);}
            if(r.getInt("build")==148&&r.getString("case").equals("kobe"))assertExpectedIdentity(label,"kobe",id);
            if(r.getInt("build")==148&&r.getString("case").equals("vileplume")&&!expectedGreen){
                assertEquals(label,"CONFIRMED",id.coreIdentityStatus);assertEquals(label,"",id.edition);
                assertEquals(label,"TO_VERIFY",id.exactEditionStatus);
                assertTrue(label,AdaptiveRecoveryPlannerV2.needsEditionInspection(profile,ledger));
            }
            if(r.getInt("build")==148&&r.getString("case").equals("philips")&&!expectedGreen)
                assertNotEquals(label+" weak manual mirrors must not be upgraded","CONFIRMED",id.coreIdentityStatus);
            if(r.getInt("build")==148&&r.getString("case").equals("topps")&&!expectedGreen)
                assertEquals(label+" missing format proof must not be invented","",id.sealedFormat);
            System.out.println(label+" live="+r.getString("liveStatus")+" replay="+id.coreIdentityStatus+" title="+id.title+" edition="+id.edition+" format="+id.sealedFormat);
        }
        assertEquals(14,greens);
    }
    private static void assertExpectedIdentity(String label,String key,Models.Identification id){
        assertEquals(label,"CONFIRMED",id.coreIdentityStatus);assertEquals(label,"PASSED",id.disproofStatus);
        if(key.equals("topps")){assertTrue(label,id.title.contains("Update"));assertEquals(label,"hobby box",id.sealedFormat.toLowerCase(Locale.ROOT));}
        if(key.equals("kobe")){assertTrue(label,id.title.startsWith("1997-98 "));assertTrue(label,id.family.contains("Metal Universe"));assertEquals(label,"81",id.physicalCardNumber);}
        if(key.equals("vileplume")){assertTrue(label,id.title.contains("Vileplume"));assertEquals(label,"FIRST_EDITION",id.edition);assertEquals(label,"HOLO",id.finish);assertEquals(label,"English",id.language);}
        if(key.equals("philips")){assertEquals(label,"Philips",id.brand);assertEquals(label,"TO_VERIFY",id.exactModelStatus);assertEquals(label,"",id.model);}
    }
}
