package com.flipcheck.nativebeta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Production-route replays for semantic normalization, independent proof and rendering. */
public final class V126SemanticDecisionEndToEndRegressionTest {
    private static JSONObject semantic;
    private static JSONObject prior;

    public static void main(String[] args) throws Exception {
        semantic=load("tools/regression/fixtures/v126_semantic_decision_payloads.json");
        prior=load("tools/regression/fixtures/v125_full_pipeline_payloads.json");
        reversedIdentifierNeedsIndependentResolution();
        unresolvedIdentifierConflictCannotBeConfirmed();
        tcgTitleKeepsDescriptionAndVariantSeparate();
        sealedCoreSurvivesMissingFormat();
        featuredSubjectsNeverBecomeProductSubjectOrQuery();
        catalogNumberRemainsSourceAttributed();
        completeVisionUsesOneOrdinaryCall();
        truncatedVisionRetriesOnceAndKeepsOcr();
        incompleteVisionCanRecoverThroughUsefulOcr();
        unavailableWebCannotErasePhotoCore();
        mainIdentityAndVariantStatesAreIndependent();
        unknownSemanticAliasIsDiagnosed();
        invertedCandidatesRemainOpen();
        mismatchedVariantComparableIsExcluded();
        singleHighConfidenceHypothesisCannotConfirmIdentifier();
        hybridProductLineRequiresIndependentCatalogChoice();
        System.out.println("V126SemanticDecisionEndToEndRegressionTest: PASS (16/16)");
    }

    private static void reversedIdentifierNeedsIndependentResolution() throws Exception {
        Models.Identification before=parse(prior.getJSONObject("sports_reversed_number"),new Models.LocalScan());
        String discovery=ProfileQueryBuilder.discovery(before);
        require(!discovery.contains("18"),"discovery query anchored itself to the unverified read: "+discovery);
        JSONObject withAlternative=new JSONObject(prior.getJSONObject("sports_reversed_number").toString());
        withAlternative.getJSONArray("facts").put(fact("card_number","81","card_number","upper-right number box, rotated 180",96));
        ScriptedClient client=new ScriptedClient(ok(withAlternative),null,
                web("https://catalog.example/card81","81","1997-98","",sales("Card #81")),null);
        Models.Identification id=run(client,new Models.LocalScan());
        require("81".equals(id.physicalCardNumber)&&id.cardNumberVerified,"photo alternative plus independent catalog did not resolve 18/81");
        require(!id.title.contains("18")&&id.title.contains("81"),"resolved title retained the false reading: "+id.title);
        require(id.numberHypotheses.contains("value=18")&&id.numberHypotheses.contains("value=81"),"competing readings were not retained");
        require(client.numberCalls==0&&id.discriminativeVisionCount==0,"identifier resolution used a forbidden second Vision");
    }

    private static void unresolvedIdentifierConflictCannotBeConfirmed() throws Exception {
        Models.Identification id=run(new ScriptedClient(ok(prior.getJSONObject("sports_reversed_number")),null,
                web("https://catalog.example/card81","81","1997-98","",sales("Card #81")),null),new Models.LocalScan());
        require("CONFLICTED".equals(id.identityStatus)&&"NUMBER_CONFLICT".equals(id.exactIdentityStatus),"unresolved number conflict was confirmed");
        require("PROBABLE".equals(id.coreIdentityStatus)&&!id.marketReady,"critical number conflict did not declassify public confirmation");
        require(!id.title.contains("18"),"unverified number leaked into main title");
    }

    private static void tcgTitleKeepsDescriptionAndVariantSeparate() throws Exception {
        Models.LocalScan local=new Models.LocalScan();local.textByImage.add("Politoed 110 PV\nCrescita Improvvisa\nRanabalzo\nSpruzza Energia\nH23/H32");
        Models.Identification id=run(new ScriptedClient(ok(semantic.getJSONObject("tcg_semantic")),null,webUnavailable(),null),local);
        require(id.title.contains("Pokémon")&&id.title.contains("Politoed"),"TCG main identity was not reconstructed");
        require(!id.title.contains("Fase 2")&&!id.title.toLowerCase().contains("holographic")&&!id.title.toLowerCase().contains("foil"),"descriptive/variant fields contaminated title: "+id.title);
        require("Fase 2".equals(id.evolutionStage)&&id.finish.toLowerCase().contains("holographic"),"TCG descriptive or variant axes were lost");
        require("H23/H32".equals(id.physicalCollectorNumber)&&id.collectorNumberVerified,"collector marking was not independently verified");
        require("SET_UNRESOLVED".equals(id.exactIdentityStatus)&&"CONFIRMED".equals(id.coreIdentityStatus),"TCG core/exact states were compressed");
    }

    private static void sealedCoreSurvivesMissingFormat() throws Exception {
        Models.Identification id=run(new ScriptedClient(ok(semantic.getJSONObject("sealed_alias_core")),null,webUnavailable(),null),new Models.LocalScan());
        require(id.title.contains("Topps Chrome Update")&&id.title.contains("2025-26")&&id.title.contains("Basketball"),"sealed core degraded to a generic title: "+id.title);
        require(!id.title.contains("Topps Topps"),"sealed title duplicated a brand already contained in the product line: "+id.title);
        require("CONFIRMED".equals(id.coreIdentityStatus)&&"FORMAT_PENDING".equals(id.exactIdentityStatus),"missing format erased sealed core");
        require(id.nextPhotoRequest.isEmpty()&&("NOT_AVAILABLE".equals(id.marketStatus)||"IDENTITY_OR_SKU_PENDING".equals(id.marketStatus)),"sealed core requested an unrelated photo or coupled market");
    }

    private static void featuredSubjectsNeverBecomeProductSubjectOrQuery() throws Exception {
        Models.Identification id=parse(semantic.getJSONObject("sealed_alias_core"),new Models.LocalScan());
        String query=ProfileQueryBuilder.seed(id);
        require(id.featuredSubjects.size()==2&&!id.title.contains("Cooper")&&!id.title.contains("Victor"),"featured people became sealed identity");
        require(!query.contains("Cooper")&&!query.contains("Victor"),"featured people contaminated primary query");
    }

    private static void catalogNumberRemainsSourceAttributed() throws Exception {
        Models.Identification id=run(new ScriptedClient(ok(prior.getJSONObject("sports_catalog_number")),null,
                web("https://catalog.example/curry67","67","2009-10","",new JSONArray()),null),new Models.LocalScan());
        require(id.physicalCardNumber.isEmpty()&&"67".equals(id.sourceConfirmedCatalogNumber),"catalog number impersonated a photo read");
        require(id.title.contains("2009-10")&&!id.title.contains("No. 67"),"catalog number/year provenance was flattened into the title");
        require("CATALOG_MATCHED".equals(id.exactIdentityStatus)&&"CATALOG_MATCHED".equals(id.identifierStatus),"catalog resolution state missing");
    }

    private static void completeVisionUsesOneOrdinaryCall() throws Exception {
        ScriptedClient client=new ScriptedClient(ok(semantic.getJSONObject("sealed_alias_core")),null,webUnavailable(),null);
        Models.Identification id=run(client,new Models.LocalScan());
        require(client.observeCalls==1&&client.retryCalls==0&&id.technicalRetryCount==0,"complete response caused an extra Vision call");
    }

    private static void truncatedVisionRetriesOnceAndKeepsOcr() throws Exception {
        JSONObject partial=new JSONObject().put("content_sufficient",true).put("category","tcg_card").put("views",new JSONArray().put("front"))
                .put("facts",new JSONArray().put(fact("card_name","Politoed","subject","top name",92))).put("identity_hint","")
                .put("candidates",new JSONArray()).put("missing_discriminators",new JSONArray());
        OpenAiClient.Response truncated=ok(partial);truncated.complete=false;truncated.parseError="max_output_tokens";truncated.incompleteReason="max_output_tokens";truncated.technicalStatus="INCOMPLETE_MAX_TOKENS";
        Models.LocalScan local=new Models.LocalScan();local.textByImage.add("Politoed 110 PV\nCrescita Improvvisa\nRanabalzo\nSpruzza Energia\nH23/H32");
        ScriptedClient client=new ScriptedClient(truncated,ok(semantic.getJSONObject("tcg_semantic")),webUnavailable(),null);
        Models.Identification id=run(client,local);
        require(client.retryCalls==1&&id.technicalRetryCount==1&&id.localOcrFactCount>0,"technical truncation did not retry once or lost OCR");
        require(id.identityConfirmed&&!"Oggetto".equals(id.title),"technical recovery produced false no-evidence result");
    }

    private static void incompleteVisionCanRecoverThroughUsefulOcr() throws Exception {
        OpenAiClient.Response invalid=new OpenAiClient.Response();invalid.complete=false;invalid.parseError="invalid json";invalid.technicalStatus="INVALID_JSON";invalid.payload=new JSONObject();
        JSONObject recovery=new JSONObject().put("content_sufficient",true).put("category","tcg_card").put("views",new JSONArray().put("front"))
                .put("facts",new JSONArray().put(fact("game_or_publisher","Game Mark","game","frame",90))
                        .put(fact("card_name","Creature Name","subject","name",94)).put(fact("language","Italiano","language","rules",91)))
                .put("identity_hint","Game Mark Creature Name").put("candidates",new JSONArray().put(new JSONObject().put("brand","Game Mark").put("subject","Creature Name").put("confidence",91)))
                .put("missing_discriminators",new JSONArray());
        Models.LocalScan local=new Models.LocalScan();local.textByImage.add("Creature Name 120 PV\nMossa Prima\nMossa Seconda\nMossa Terza\nH12/H40");
        Models.Identification id=run(new ScriptedClient(invalid,ok(recovery),webUnavailable(),null),local);
        require(id.identityConfirmed&&"tcg".equals(id.canonicalProfile)&&id.fingerprintComponents.contains("attack_names"),"useful OCR did not complete the recovered TCG tuple");
    }

    private static void unavailableWebCannotErasePhotoCore() throws Exception {
        Models.Identification id=run(new ScriptedClient(ok(semantic.getJSONObject("sealed_alias_core")),null,webUnavailable(),null),new Models.LocalScan());
        require(id.identityConfirmed&&"CONFIRMED".equals(id.coreIdentityStatus)&&id.title.contains("Chrome Update"),"Web failure reopened or emptied photo identity");
    }

    private static void mainIdentityAndVariantStatesAreIndependent() throws Exception {
        Models.Identification id=parse(semantic.getJSONObject("tcg_semantic"),new Models.LocalScan());
        require("CONFIRMED".equals(id.coreIdentityStatus)&&!"CONFIRMED".equals(id.exactIdentityStatus),"main and exact states were coupled");
        require("FINISH_OBSERVED".equals(id.variantStatus),"finish/parallel state was not separated");
    }

    private static void unknownSemanticAliasIsDiagnosed() {
        Models.Identification id=new Models.Identification();
        EvidenceLedger.addPhotoFact(id,"unmapped_product_namespace","X","direct_photo_observation",92,0,"front","title","product_family_axis");
        PhotographicFactNormalizer.normalize(id,"unknown_alias_test");ConsistencyInvariantChecker.enforce(id,"unknown_alias_test");
        require(id.factsRejectedWithReason.contains("relevant_alias_rejected")&&"FAIL".equals(id.consistencyInvariants),"unknown relevant alias was silently discarded");
    }

    private static void invertedCandidatesRemainOpen() throws Exception {
        Models.Identification id=parse(semantic.getJSONObject("sports_inverted_candidates"),new Models.LocalScan());
        require(!id.identityConfirmed&&id.canonicalCandidateCount==2&&id.photoIdentityAmbiguous,"12/21 candidates were self-collapsed");
        require(PhotographicIdentityClosure.targetedPhotoRequest(id).toLowerCase().contains("numero"),"inverted-number ambiguity did not request the number area");
    }

    private static void mismatchedVariantComparableIsExcluded() throws Exception {
        JSONObject old=load("tools/regression/fixtures/v123_canonical_profile_cases.json");
        Models.Identification id=parse(old.getJSONObject("sports_physical_variant"),new Models.LocalScan());
        JSONObject c=comparable("matching card",75).put("variant_specific",true).put("variant_key","Blue Pattern");
        OpenAiClient.Response r=web("https://catalog.example/variant","21","2024-25","Green Pattern",new JSONArray().put(c));
        ConfirmedIdentityEnrichment.apply(id,r);
        require(!id.marketComparables.get(0).included&&id.marketComparables.get(0).reason.contains("VARIANT_MISMATCH"),"wrong physical variant entered the market bucket");
    }

    private static void singleHighConfidenceHypothesisCannotConfirmIdentifier() throws Exception {
        Models.Identification id=parse(semantic.getJSONObject("sports_single_identifier"),new Models.LocalScan());
        require(id.identityConfirmed&&"CONFIRMED".equals(id.coreIdentityStatus),"strong main tuple failed to close");
        require(!id.cardNumberVerified&&"NUMBER_UNRESOLVED".equals(id.exactIdentityStatus),"single Vision hypothesis confirmed a high-risk identifier");
        require(!id.title.contains("18")&&!ProfileQueryBuilder.discovery(id).contains("18"),"unverified identifier contaminated title/discovery");
    }

    private static void hybridProductLineRequiresIndependentCatalogChoice() throws Exception {
        OpenAiClient.Response catalog=web("https://catalog.example/chrome-update","","2025/26","",new JSONArray());
        catalog.payload.put("source_confirmed_product_line","Maker Cards Chrome Update");
        Models.Identification id=run(new ScriptedClient(ok(semantic.getJSONObject("sealed_hybrid_line")),null,catalog,null),new Models.LocalScan());
        require(id.identityConfirmed&&"CONFIRMED".equals(id.coreIdentityStatus),"independent catalog did not resolve the photographed line alternatives");
        require(id.title.contains("Maker Cards Chrome Update")&&!id.title.contains("Hoops"),"hybrid product name survived catalog disproof: "+id.title);
        require(id.canonicalPhotoFields.contains("source.sourceConfirmedProductLine=Maker Cards Chrome Update"),"catalog product-line provenance was lost");
    }

    private static Models.Identification run(ScriptedClient client,Models.LocalScan local) throws Exception {
        return IdentificationPipelineV082.identify(local, Arrays.asList("data:image/jpeg;base64,AA"),"",client,new Models.Usage());
    }
    private static Models.Identification parse(JSONObject payload,Models.LocalScan local) throws Exception {
        Models.Identification id=new Models.Identification();id.localScan=local;LocalEvidenceBootstrap.apply(id,local);
        IdentificationPipelineV082.parsePrimaryObservationAndAttemptClosure(id,payload,local);IdentificationEngine.finalizeOutput(id,null);return id;
    }
    private static JSONObject load(String path) throws Exception {return new JSONObject(new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8));}
    private static OpenAiClient.Response ok(JSONObject payload){OpenAiClient.Response r=new OpenAiClient.Response();r.payload=payload;r.usage.visionCalls=1;r.usage.requests=1;r.usage.costUsd=.006;return r;}
    private static OpenAiClient.Response webUnavailable(){OpenAiClient.Response r=new OpenAiClient.Response();r.complete=false;r.parseError="network";r.technicalStatus="NETWORK_ERROR";r.payload=new JSONObject();return r;}
    private static OpenAiClient.Response web(String url,String number,String year,String variant,JSONArray comparables) throws Exception {
        String brand=url.contains("curry")?"Panini":url.contains("variant")?"Maker Five":url.contains("chrome-update")?"Maker Cards":"SkyBox";
        String line=url.contains("curry")?"Adrenalyn XL":url.contains("variant")?"Select Line":url.contains("chrome-update")?"Maker Cards Chrome Update":"Metal Universe";
        String subject=url.contains("curry")?"Stephen Curry":url.contains("variant")?"Athlete Five":url.contains("chrome-update")?"":"Kobe Bryant";
        JSONObject candidate=new JSONObject().put("source_url",url).put("source_authority","authoritative checklist").put("brand",brand).put("product_line",line)
                .put("release_year",year).put("subject",subject).put("card_number",number).put("edition",variant).put("product_type",url.contains("chrome-update")?"sealed box":"sports card");
        JSONObject p=new JSONObject().put("source_grounded",true).put("physical_tuple_coherent",true).put("source_url",url)
                .put("source_confirmed_catalog_number",number).put("source_confirmed_release_year",year).put("source_confirmed_variant",variant)
                .put("source_catalog_title","Verified catalog entry").put("candidates",new JSONArray().put(candidate)).put("comparables",comparables);
        OpenAiClient.Response r=new OpenAiClient.Response();r.payload=p;r.usage.webCalls=1;r.usage.requests=1;addSource(r,url);
        for(int i=0;i<comparables.length();i++){JSONObject c=comparables.optJSONObject(i);if(c!=null)addSource(r,c.optString("source_url",""));}return r;
    }
    private static JSONArray sales(String title) throws Exception {return new JSONArray().put(comparable(title,55)).put(comparable(title,65));}
    private static JSONObject comparable(String title,double price) throws Exception {return new JSONObject().put("sale_status","SOLD").put("item_state","RAW")
            .put("condition","raw").put("grading_company","").put("grade","").put("currency","USD").put("price",price)
            .put("source_url","https://market.example/"+Math.abs((title+price).hashCode())).put("title",title).put("date","2026-08-01")
            .put("identity_match",true).put("variant_specific",false).put("variant_key","");}
    private static OpenAiClient.Response number(String value) throws Exception {return ok(new JSONObject().put("same_card",true).put("readings",new JSONArray()
            .put(new JSONObject().put("value",value).put("image",0).put("side","back").put("location","number box").put("orientation",180).put("confidence",97))
            .put(new JSONObject().put("value",value).put("image",0).put("side","back").put("location","number box").put("orientation",0).put("confidence",95))));}
    private static JSONObject fact(String key,String value,String role,String location,int confidence) throws Exception {return new JSONObject().put("key",key).put("value",value)
            .put("image",0).put("side","front").put("location",location).put("role",role).put("confidence",confidence);}
    private static void addSource(OpenAiClient.Response r,String url){if(url==null||url.isEmpty())return;Models.Source s=new Models.Source();s.url=url;r.sources.add(s);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}

    private static final class ScriptedClient extends OpenAiClient {
        final OpenAiClient.Response initial,retry,enrichment,number;int observeCalls,retryCalls,numberCalls;
        ScriptedClient(OpenAiClient.Response initial,OpenAiClient.Response retry,OpenAiClient.Response enrichment,OpenAiClient.Response number){super("test");this.initial=initial;this.retry=retry;this.enrichment=enrichment;this.number=number;}
        @Override Response observe(List<String> images,String prompt){observeCalls++;return initial;}
        @Override Response observeTechnicalRecovery(List<String> images,String prompt){retryCalls++;return retry;}
        @Override Response enrichConfirmedIdentity(String prompt){return enrichment;}
        @Override Response webStage(String stage,String prompt){return enrichment;}
        @Override Response verifyPhysicalCardNumber(List<String> images){numberCalls++;return number;}
    }
}
