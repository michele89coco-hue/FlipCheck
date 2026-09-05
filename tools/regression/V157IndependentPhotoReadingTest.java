package com.flipcheck.nativebeta;

import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class V157IndependentPhotoReadingTest {
    private JSONObject recording()throws Exception{
        return new JSONObject(new String(Files.readAllBytes(Paths.get("tools/regression/fixtures/v157-recorded-kobe.json")),java.nio.charset.StandardCharsets.UTF_8));
    }
    private Models.LocalScan local(JSONObject r)throws Exception{
        Models.LocalScan l=new Models.LocalScan();JSONArray text=r.getJSONArray("local_ocr");
        for(int i=0;i<text.length();i++)l.textByImage.add(text.getString(i));return l;
    }
    private static final class RecordedClient extends OpenAiClient {
        final JSONObject payload;String prompt="";int vision;
        RecordedClient(JSONObject p){super("");payload=p;}
        @Override Response observeFullV154(List<String> images,String text){
            vision++;prompt=text;Response r=new Response();r.payload=payload;
            r.usage.requests=1;r.usage.visionCalls=1;r.usage.costUsd=.00143976;return r;
        }
        @Override Response identityWebSearchV2(List<String> images,String prompt){throw new AssertionError("Unexpected paid web request");}
        @Override Response observeTechnicalRecovery(List<String> images,String prompt){throw new AssertionError("Unexpected extra Vision");}
    }
    private List<String> images(){return Arrays.asList("original-front","original-back");}
    private Models.Identification read(JSONObject r,Models.Usage usage)throws Exception{
        return UniversalIdentityEngineV2.readPhoto(local(r),images(),"",new RecordedClient(r.getJSONObject("v154_vision1_observation")),usage);
    }
    @Test public void recordedKobeKeepsIdentityWithoutPromotingWrongNumberOrSportStatistics()throws Exception{
        Models.Usage usage=new Models.Usage();Models.Identification id=read(recording(),usage);
        assertEquals("PHOTO_READ",id.identityStatus);assertEquals("SkyBox",id.brand);assertEquals("METAL UNIVERSE",id.family);
        assertTrue(id.title,id.title.contains("SkyBox")&&id.title.contains("METAL UNIVERSE")&&id.title.contains("KOBE BRYANT"));
        assertFalse(id.title.contains("46/74"));assertFalse(id.title.contains("81"));
        assertTrue(id.photoReadingSummary.contains("Numero da rileggere: 46/74"));
        assertFalse(id.photoReadingSummary.contains("Numero sulla carta: 46/74"));
        assertTrue(id.photoReadingSummary.contains("Dati fisici atleta: HT:"));assertFalse(id.photoReadingSummary.contains("HP/PV:"));
        assertTrue(id.photoReadingSummary.contains("Anno copyright: 1997"));assertEquals("",id.physicalReleaseYear);
        assertEquals("TO_VERIFY",id.variantStatus);assertEquals("",id.sourceConfirmedCatalogNumber);
        assertEquals("",id.physicalCardNumber);assertFalse(id.identityConfirmed);assertFalse(id.marketReady);
        assertEquals(1,usage.requests);assertEquals(0,usage.webCalls);
    }
    @Test public void initialVisionReceivesImagesWithoutRotatedOcrOrPhoneUiText()throws Exception{
        JSONObject r=recording();RecordedClient client=new RecordedClient(r.getJSONObject("v154_vision1_observation"));
        UniversalIdentityEngineV2.readPhoto(local(r),images(),"",client,new Models.Usage());
        assertEquals(1,client.vision);
        for(String token:Arrays.asList("LOCAL_OCR_HINT_UNTRUSTED","46.l(74","4l74","10:18","METAL/1/"))assertFalse(token,client.prompt.contains(token));
    }
    @Test public void highConfidencePhotoNumberCanBeDisplayedWithoutInventingCatalogProof()throws Exception{
        JSONObject r=recording();JSONArray facts=r.getJSONObject("v154_vision1_observation").getJSONArray("facts");
        for(int i=0;i<facts.length();i++)if(facts.getJSONObject(i).getString("key").equals("physicalCardNumber"))
            facts.getJSONObject(i).put("value","81").put("image",2).put("side","back").put("location","upper left number circle").put("confidence",99);
        Models.Identification id=read(r,new Models.Usage());
        assertTrue(id.title,id.title.contains("#81"));assertTrue(id.photoReadingSummary.contains("Numero letto (da verificare): 81"));
        assertEquals("",id.sourceConfirmedCatalogNumber);assertFalse(id.catalogVerified);assertFalse(id.identityConfirmed);
    }
    @Test public void discordantNumbersDoNotHideStablePhotoIdentityOrPreventReview()throws Exception{
        JSONObject r=recording();JSONArray facts=r.getJSONObject("v154_vision1_observation").getJSONArray("facts");
        JSONObject number=null;for(int i=0;i<facts.length();i++)if(facts.getJSONObject(i).getString("key").equals("physicalCardNumber"))number=facts.getJSONObject(i);
        assertNotNull(number);number.put("value","18").put("confidence",99);
        facts.put(new JSONObject(number.toString()).put("value","81").put("image",2).put("side","back").put("location","upper left card number"));
        Models.Usage usage=new Models.Usage();Models.Identification id=read(r,usage);
        assertEquals("CONFLICTED",id.identityStatus);assertFalse(id.identityConfirmed);assertFalse(id.marketReady);
        assertTrue(PhotoReadingV156.hasReading(id));assertTrue(id.photoReadingSummary.contains("KOBE BRYANT"));
        assertTrue(id.title.contains("METAL UNIVERSE"));assertFalse(id.title.contains("#18"));assertFalse(id.title.contains("#81"));
        assertTrue(id.photoReadingConflicts.contains("18 / 81"));
        assertNotNull(PhotoReadingV156.cachedResponse(id,usage,images(),""));
        assertTrue(new JSONObject(DiagnosticExportV2.create(id,usage,"")).getString("photoReadingConflicts").contains("18 / 81"));
    }
    @Test public void literalProductWordmarkDoesNotDependOnOcrButSymbolGuessesStayInferred()throws Exception{
        for(String[] sample:new String[][]{{"NOVA SERIES","lower right emblem","product line","OBSERVED"},{"Another Set","lower right symbol","inferred symbol family","INFERRED"}}){
            JSONObject f=new JSONObject().put("key","productLine").put("value",sample[0]).put("location",sample[1]).put("role",sample[2])
                    .put("image",1).put("side","front").put("confidence",96);
            ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestPrimary(new JSONObject().put("category","sports_card").put("facts",new JSONArray().put(f)),l);
            assertEquals(sample[3],l.strongest("productLine").epistemicLevel.name());
        }
    }
    @Test public void onlyMakerWordmarkRoleCanDeriveBrandFromDescriptiveLogoFact()throws Exception{
        for(String role:Arrays.asList("manufacturer mark","league mark","copyright owner","grading company")){
            Models.LocalScan local=new Models.LocalScan();local.textByImage.add("ACME INTERNATIONAL");
            ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestLocal(local,l);
            JSONObject f=new JSONObject().put("key","physicalFeature").put("value","ACME logo").put("role",role)
                    .put("location","lower center logo").put("image",1).put("side","back").put("confidence",98);
            ObservationExtractorV2.ingestPrimary(new JSONObject().put("category","sports_card").put("facts",new JSONArray().put(f)),l);
            assertEquals(role,role.equals("manufacturer mark"),l.hasObserved("brand"));
        }
    }
    @Test public void copyrightNormalizationRetainsMultipleYearsAndDoesNotInventReleaseSeason(){
        assertEquals("1997",TypedFieldNormalizerV2.normalizeValue("copyrightYear","1997 League, ©1997 ACME",""));
        assertEquals("1997 / 1998",TypedFieldNormalizerV2.normalizeValue("copyrightYear","1997 / 1998",""));
        assertEquals("copyrightYear",ObservationSemanticsV157.field("productReleaseYear","release year","copyright line",DomainProfileRouterV2.Profile.SPORTS_CARD));
        assertEquals("productReleaseYear",ObservationSemanticsV157.field("productReleaseYear","printed season","product identification line",DomainProfileRouterV2.Profile.SPORTS_CARD));
        assertEquals("hp",ObservationSemanticsV157.field("hp","hit points","upper right",DomainProfileRouterV2.Profile.TCG_CARD));
    }
}
