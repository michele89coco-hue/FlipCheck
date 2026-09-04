package com.flipcheck.nativebeta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Full production-route replays for technical recovery, hierarchy and post-Web conflicts. */
public final class V125HierarchicalRecoveryEndToEndRegressionTest {
    private static JSONObject cases;
    public static void main(String[] args)throws Exception{
        cases=new JSONObject(new String(Files.readAllBytes(Paths.get(
                "tools/regression/fixtures/v125_full_pipeline_payloads.json")),StandardCharsets.UTF_8));
        reversedNumberStaysConflictedWithoutIndependentPhotoSupport();
        catalogNumberAndReleaseSeasonRemainSourceAttributed();
        maxTokensRetriesAndKeepsLocalOcr();
        failedTechnicalRetryNeverBlamesPhoto();
        sealedCoreClosesWithoutCommercialFormat();
        featuredSubjectsNeverEnterSealedQuery();
        incompatibleComparableNumberIsExcluded();
        collectorFractionNeverBecomesSerial();
        serialFractionsRemainSeparateFromCardNumbers();
        numericContextsCannotBecomeCardNumber();
        webFailureCannotReopenPhotoCore();
        relevantUnknownAliasBreaksInvariantPass();
        compactObserverContractStaysSmall();
        System.out.println("V125HierarchicalRecoveryEndToEndRegressionTest: PASS (13/13)");
    }

    private static void reversedNumberStaysConflictedWithoutIndependentPhotoSupport()throws Exception{
        ScriptedClient c=new ScriptedClient(ok(cases.getJSONObject("sports_reversed_number")),null,
                web("https://catalog.example/card81","81","1997-98","",sales("#81",55,65)),number("81"));
        Models.Identification id=run(c,new Models.LocalScan());
        require("18".equals(id.physicalCardNumber)&&!id.cardNumberVerified,"single photo hypothesis was promoted after catalog disagreement");
        require("PROBABLE".equals(id.coreIdentityStatus)&&"NUMBER_CONFLICT".equals(id.exactIdentityStatus),"critical number conflict did not declassify public confirmation");
        require(!id.confirmedModel.contains("18")&&!id.confirmedModel.contains("81"),"contested number leaked into title");
        require(c.numberCalls==0,"valid first Vision triggered a forbidden focused number call");
    }

    private static void catalogNumberAndReleaseSeasonRemainSourceAttributed()throws Exception{
        JSONObject comps=new JSONArray().put(comparable("#NNO",22)).put(comparable("Stephen Curry #67",75)).length()>0
                ?new JSONObject().put("items",new JSONArray().put(comparable("#NNO",22)).put(comparable("Stephen Curry #67",75))):new JSONObject();
        JSONArray arr=comps.getJSONArray("items");
        ScriptedClient c=new ScriptedClient(ok(cases.getJSONObject("sports_catalog_number")),null,
                web("https://catalog.example/curry67","67","2009-10","",arr),null);
        Models.Identification id=run(c,new Models.LocalScan());
        require(id.physicalCardNumber.isEmpty()&&"67".equals(id.sourceConfirmedCatalogNumber),"catalog number impersonated a photo read");
        require("2009-10".equals(id.sourceConfirmedReleaseYear)&&id.title.startsWith("2009-10"),"release-season precision was lost");
        require(id.marketComparables.get(0).reason.contains("NUMBER_MISMATCH"),"#NNO comparable was accepted");
        require(!id.numberHypotheses.contains("value=72")&&!id.numberHypotheses.contains("value=90"),"ratings entered number hypotheses");
    }

    private static void maxTokensRetriesAndKeepsLocalOcr()throws Exception{
        JSONObject partial=new JSONObject().put("content_sufficient",true).put("category","tcg")
                .put("views",new JSONArray().put("front")).put("identity_hint","")
                .put("facts",new JSONArray().put(fact("card_name","Politoed","subject","top name",92)))
                .put("candidates",new JSONArray()).put("missing_discriminators",new JSONArray());
        OpenAiClient.Response truncated=ok(partial);truncated.complete=false;truncated.incompleteReason="max_output_tokens";
        truncated.parseError="max_output_tokens";truncated.technicalStatus="INCOMPLETE_MAX_TOKENS";
        ScriptedClient c=new ScriptedClient(truncated,ok(cases.getJSONObject("tcg_retry_complete")),webUnavailable(),null);
        Models.LocalScan local=new Models.LocalScan();local.textByImage.add("Politoed 110 PV\nCrescita Improvvisa\nRanabalzo\nSpruzza Energia\nH23/H32");
        Models.Identification id=run(c,local);
        require(id.technicalRetryCount==1&&"COMPLETED".equals(id.visionResponseStatus),"max-token response was not technically recovered");
        require(id.localOcrFactCount>=2&&"tcg".equals(id.canonicalProfile),"OCR bootstrap/profile was discarded");
        require("H23/H32".equals(id.physicalCollectorNumber)&&id.identityConfirmed,"recovered TCG front did not close");
        require(id.nextPhotoRequest.isEmpty(),"technical recovery incorrectly requested a photo");
    }

    private static void failedTechnicalRetryNeverBlamesPhoto()throws Exception{
        OpenAiClient.Response fail=new OpenAiClient.Response();fail.complete=false;fail.parseError="invalid";fail.technicalStatus="INVALID_JSON";fail.payload=new JSONObject();
        ScriptedClient c=new ScriptedClient(fail,fail,webUnavailable(),null);
        Models.LocalScan local=new Models.LocalScan();local.textByImage.add("Creature 120 HP\nFirst Move\nSecond Move\nH12/H40");
        Models.Identification id=run(c,local);
        require("TECHNICAL_ERROR".equals(id.decision)&&id.nextPhotoRequest.isEmpty(),"technical failure was shown as bad photo");
        require(!id.category.equals("Oggetto")&&id.localOcrFactCount>0,"strong local OCR was erased");
    }

    private static void sealedCoreClosesWithoutCommercialFormat()throws Exception{
        Models.Identification id=run(new ScriptedClient(ok(cases.getJSONObject("sealed_core_without_format")),null,webUnavailable(),null),new Models.LocalScan());
        require(id.identityConfirmed&&"CONFIRMED".equals(id.coreIdentityStatus),"sealed core was discarded");
        require("FORMAT_PENDING".equals(id.exactIdentityStatus),"format was not isolated as pending exact attribute");
        require(id.title.contains("Chrome Update")&&!id.title.contains("Cooper")&&!id.title.contains("Victor"),"sealed title lost line or used featured subject");
    }

    private static void featuredSubjectsNeverEnterSealedQuery()throws Exception{
        Models.Identification id=parse("sealed_core_without_format");String q=ProfileQueryBuilder.seed(id);
        require(!q.contains("Cooper")&&!q.contains("Victor")&&id.queryFieldsExcluded.contains("featuredSubjects"),"featured subjects contaminated query");
    }

    private static void incompatibleComparableNumberIsExcluded()throws Exception{
        Models.Identification id=parse("sports_reversed_number");id.sourceConfirmedCatalogNumber="81";id.numberConflicts="18!=81";
        OpenAiClient.Response r=web("https://catalog.example/x","81","1997-98","",new JSONArray().put(comparable("Card #81",90)));
        ComparablePricePolicy.apply(id,r.payload,r);require(!id.marketComparables.get(0).included,"conflicting comparable entered market bucket");
    }

    private static void collectorFractionNeverBecomesSerial()throws Exception{Models.Identification id=parse("tcg_retry_complete");
        require("H23/H32".equals(id.physicalCollectorNumber)&&id.physicalSerial.isEmpty(),"collector fraction became serial");}

    private static void serialFractionsRemainSeparateFromCardNumbers()throws Exception{
        Models.Identification id=new Models.Identification();id.categoryKey="sports_card";
        EvidenceLedger.addPhotoFact(id,"card_number","21","vision",95,0,"back","number box","card_number");
        EvidenceLedger.addPhotoFact(id,"physical_serial","2/5","vision",98,0,"front","serial stamp","serial");
        PhotographicFactNormalizer.normalize(id,"test");PhysicalCardNumberPolicy.normalize(id);PhysicalSerialPolicy.normalize(id);
        require("21".equals(id.physicalCardNumber)&&"2/5".equals(id.physicalSerial),"card number/serial axes merged");
    }

    private static void numericContextsCannotBecomeCardNumber()throws Exception{
        Models.Identification id=new Models.Identification();id.categoryKey="sports_card";
        for(String v:new String[]{"12","21","6","9","77","110"})EvidenceLedger.addPhotoFact(id,"card_number",v,"vision",99,0,"front","rating/stat/jersey/hp area","rating");
        PhotographicFactNormalizer.normalize(id,"test");PhysicalCardNumberPolicy.normalize(id);require(id.physicalCardNumber.isEmpty(),"context numbers became card number");
    }

    private static void webFailureCannotReopenPhotoCore()throws Exception{Models.Identification id=parse("sports_reversed_number");ConfirmedIdentityEnrichment.unavailable(id);
        require(id.identityConfirmed&&"CONFIRMED".equals(id.coreIdentityStatus)&&("NOT_AVAILABLE".equals(id.marketStatus)||"IDENTITY_OR_SKU_PENDING".equals(id.marketStatus)),"Web failure reopened core");}

    private static void relevantUnknownAliasBreaksInvariantPass()throws Exception{
        Models.Identification id=new Models.Identification();EvidenceLedger.addPhotoFact(id,"mystery_card_namespace","X","vision",90,0,"front","title","mystery_identity_axis");
        PhotographicFactNormalizer.normalize(id,"test");ConsistencyInvariantChecker.enforce(id,"test");
        require(id.factsRejectedWithReason.contains("relevant_alias_rejected")&&"FAIL".equals(id.consistencyInvariants),"relevant alias was silently discarded");
    }

    private static void compactObserverContractStaysSmall()throws Exception{String schema=OpenAiClient.observerFormatForTest().toString();
        String client=new String(Files.readAllBytes(Paths.get("app/src/main/java/com/flipcheck/nativebeta/OpenAiClient.java")),StandardCharsets.UTF_8);
        require(client.contains("compactObserverFormat()")&&client.contains("maxOutputTokens, boolean retry"),"production observer bypasses compact contract");
        require(schema.length()>0,"legacy test accessor became invalid");}

    private static Models.Identification run(ScriptedClient client,Models.LocalScan local)throws Exception{Models.Usage usage=new Models.Usage();return IdentificationPipelineV082.identify(local,Arrays.asList("data:image/jpeg;base64,AA"),"",client,usage);}
    private static Models.Identification parse(String key)throws Exception{Models.Identification id=new Models.Identification();id.localScan=new Models.LocalScan();IdentificationPipelineV082.parsePrimaryObservationAndAttemptClosure(id,cases.getJSONObject(key),id.localScan);IdentificationEngine.finalizeOutput(id,null);return id;}
    private static OpenAiClient.Response ok(JSONObject payload){OpenAiClient.Response r=new OpenAiClient.Response();r.payload=payload;r.complete=true;r.technicalStatus="COMPLETED";r.usage.visionCalls=1;r.usage.requests=1;r.usage.costUsd=.006;return r;}
    private static OpenAiClient.Response webUnavailable(){OpenAiClient.Response r=new OpenAiClient.Response();r.payload=new JSONObject();r.complete=false;r.parseError="network";r.technicalStatus="NETWORK_ERROR";return r;}
    private static OpenAiClient.Response web(String url,String number,String year,String variant,JSONArray comparables)throws Exception{boolean curry=url.contains("curry");JSONObject candidate=new JSONObject().put("source_url",url).put("source_authority","authoritative checklist").put("brand",curry?"Panini":"SkyBox").put("product_line",curry?"Adrenalyn XL":"Metal Universe").put("release_year",year).put("subject",curry?"Stephen Curry":"Kobe Bryant").put("team",curry?"Golden State Warriors":"").put("sport","Basketball").put("card_number",number).put("edition",variant);
        JSONObject p=new JSONObject().put("source_grounded",true).put("physical_tuple_coherent",true).put("source_url",url).put("source_confirmed_catalog_number",number).put("source_confirmed_release_year",year).put("source_confirmed_variant",variant).put("source_catalog_title","Verified catalog entry").put("candidates",new JSONArray().put(candidate)).put("comparables",comparables).put("evidence","catalog");OpenAiClient.Response r=new OpenAiClient.Response();r.payload=p;r.usage.webCalls=1;r.usage.requests=1;Models.Source s=new Models.Source();s.url=url;r.sources.add(s);for(int i=0;i<comparables.length();i++){JSONObject c=comparables.optJSONObject(i);if(c!=null){Models.Source m=new Models.Source();m.url=c.optString("source_url","");r.sources.add(m);}}return r;}
    private static JSONArray sales(String number,double a,double b)throws Exception{return new JSONArray().put(comparable("Card "+number,a)).put(comparable("Card "+number,b));}
    private static JSONObject comparable(String title,double price)throws Exception{return new JSONObject().put("sale_status","SOLD").put("item_state","RAW").put("condition","raw").put("grading_company","").put("grade","").put("currency","USD").put("price",price).put("source_url","https://market.example/"+Math.abs(title.hashCode())+"-"+price).put("title",title).put("date","2026-08-01").put("identity_match",true).put("variant_specific",false).put("variant_key","").put("exclusion_reason","");}
    private static OpenAiClient.Response number(String value)throws Exception{JSONObject r1=new JSONObject().put("value",value).put("image",0).put("side","back").put("location","upper-right number box").put("orientation",180).put("confidence",96);JSONObject r2=new JSONObject().put("value",value).put("image",0).put("side","back").put("location","upper-right number box").put("orientation",0).put("confidence",94);return ok(new JSONObject().put("same_card",true).put("readings",new JSONArray().put(r1).put(r2)));}
    private static JSONObject fact(String key,String value,String role,String location,int q)throws Exception{return new JSONObject().put("key",key).put("value",value).put("image",0).put("side","front").put("location",location).put("role",role).put("confidence",q);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}

    private static final class ScriptedClient extends OpenAiClient{
        final OpenAiClient.Response initial,retry,enrichment,number;boolean numberPromptWasUnbiased=true;int numberCalls;
        ScriptedClient(OpenAiClient.Response initial,OpenAiClient.Response retry,OpenAiClient.Response enrichment,OpenAiClient.Response number){super("test");this.initial=initial;this.retry=retry;this.enrichment=enrichment;this.number=number;}
        @Override Response observe(List<String> images,String prompt){return initial;}
        @Override Response observeTechnicalRecovery(List<String> images,String prompt){return retry;}
        @Override Response enrichConfirmedIdentity(String prompt){return enrichment;}
        @Override Response verifyPhysicalCardNumber(List<String> images){numberCalls++;return number;}
    }
}
