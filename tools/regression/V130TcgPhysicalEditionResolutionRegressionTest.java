package com.flipcheck.nativebeta;

import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;

/** Production-shaped payload replays for physical TCG edition propagation. */
public final class V130TcgPhysicalEditionResolutionRegressionTest {
    private static int passed;
    public static void main(String[] args) throws Exception {
        firstEditionSurvivesToFinalUiState();
        missingEditionTriggersFocusedPass();
        focusedLogoRecoveryRestoresEdition();
        unlimitedCandidateCannotOverridePhysicalFirstEdition();
        absentMarkNeverInventsFirstEdition();
        shadowAndFinishRemainIndependent();
        coreClosesWhileFinishIsUncertain();
        queryLadderCarriesPhysicalEdition();
        noFixtureSpecificProductionBranch();
        System.out.println("V130TcgPhysicalEditionResolutionRegressionTest: PASS ("+passed+"/9)");
    }

    private static void firstEditionSurvivesToFinalUiState() throws Exception {
        Models.Identification id=parse(primary(true));
        FinalIdentityDecisionEngine.freeze(id,"v130_first_edition_final");
        require("FIRST_EDITION".equals(id.edition),"physical edition was lost");
        require("PRESENT".equals(id.firstEditionMark),"first-edition mark was lost");
        require("CONFIRMED".equals(id.exactEditionStatus),"edition state not confirmed");
        require(id.title.contains("1st Edition"),"public title omitted physical edition");pass();
    }

    private static void missingEditionTriggersFocusedPass() throws Exception {
        Models.Identification id=parse(primary(false));Models.Usage usage=new Models.Usage();usage.visionCalls=1;usage.costUsd=.010;
        require(IdentificationPipelineV082.requiresMandatoryCardSecondVision(id,usage),"sharp TCG front did not trigger focused edition pass");pass();
    }

    private static void focusedLogoRecoveryRestoresEdition() throws Exception {
        Models.Identification id=parse(primary(false));OpenAiClient.Response r=new OpenAiClient.Response();
        r.payload=new JSONObject().put("same_card",true).put("front_sufficient",true)
                .put("first_edition_present",true).put("observed_text","1st Edition")
                .put("location","left edge below artwork").put("crop_region","left-middle edition region")
                .put("image_index",1).put("shadow_status","shadowless").put("finish","holo")
                .put("confidence",94).put("reason","visible logo geometry and text");
        require(TcgPhysicalEditionPolicy.mergeFocusedResult(id,r),"focused logo result was not merged");
        FinalIdentityDecisionEngine.freeze(id,"v130_focused_merge");
        require("FIRST_EDITION".equals(id.edition)&&"CONFIRMED".equals(id.exactEditionStatus),"focused edition did not survive final reducer");pass();
    }

    private static void unlimitedCandidateCannotOverridePhysicalFirstEdition() throws Exception {
        Models.Identification id=parse(primary(true));OpenAiClient.Response web=new OpenAiClient.Response();
        web.payload=new JSONObject().put("candidates",new JSONArray().put(candidate("Unlimited")));
        CatalogCandidateMatcher.Result result=CatalogCandidateMatcher.evaluate(id,web);
        require(result.accepted==null&&!result.rejected.isEmpty(),"Unlimited candidate overrode physical First Edition");
        require(result.conflicts.toString().contains("PHYSICAL_EDITION_CONFLICT"),"edition veto reason missing");pass();
    }

    private static void absentMarkNeverInventsFirstEdition() throws Exception {
        Models.Identification id=parse(primary(false));OpenAiClient.Response r=new OpenAiClient.Response();
        r.payload=new JSONObject().put("same_card",true).put("front_sufficient",true)
                .put("first_edition_present",false).put("observed_text","").put("location","edition regions")
                .put("crop_region","all candidate regions").put("image_index",1)
                .put("shadow_status","shadowed").put("finish","unknown").put("confidence",92).put("reason","no physical mark visible");
        TcgPhysicalEditionPolicy.mergeFocusedResult(id,r);
        require(!"FIRST_EDITION".equals(id.edition)&&"ABSENT".equals(id.firstEditionMark),"absent mark invented First Edition");pass();
    }

    private static void shadowAndFinishRemainIndependent() throws Exception {
        Models.Identification id=parse(primary(true));
        require("SHADOWLESS".equals(id.shadowStatus),"shadowless physical axis missing");
        require("HOLO".equals(id.holoStatus),"holo physical axis missing");
        require("FIRST_EDITION".equals(id.edition),"edition axis changed by shadow/finish");pass();
    }

    private static void coreClosesWhileFinishIsUncertain() throws Exception {
        JSONObject p=primary(false);JSONArray facts=p.getJSONArray("facts");
        for(int i=facts.length()-1;i>=0;i--)if("finish".equals(facts.getJSONObject(i).optString("key")))facts.remove(i);
        Models.Identification id=parse(p);FinalIdentityDecisionEngine.freeze(id,"v130_finish_unknown");
        require("CONFIRMED".equals(id.coreIdentityStatus),"uncertain finish blocked TCG core closure");
        require("TO_VERIFY".equals(id.finishStatus),"uncertain finish was not isolated");pass();
    }

    private static void queryLadderCarriesPhysicalEdition() throws Exception {
        Models.Identification id=parse(primary(true));String queries=ProfileQueryBuilder.exactQueries(id).toString();
        require(queries.contains("FIRST_EDITION")&&queries.contains("15/64"),"physical edition/collector missing from query ladder");pass();
    }

    private static void noFixtureSpecificProductionBranch() throws Exception {
        String roots=Files.readString(Path.of("app/src/main/java/com/flipcheck/nativebeta/TcgPhysicalEditionPolicy.java"))
                +Files.readString(Path.of("app/src/main/java/com/flipcheck/nativebeta/TcgEditionCropper.java"));
        require(!roots.toLowerCase().contains("vileplume")&&!roots.contains("15/64")&&!roots.toLowerCase().contains("jungle"),"fixture-specific production hardcode detected");pass();
    }

    private static Models.Identification parse(JSONObject p){Models.Identification id=new Models.Identification();
        IdentificationPipelineV082.parsePrimaryObservationAndAttemptClosure(id,p,new Models.LocalScan());return id;}
    private static JSONObject primary(boolean first) throws Exception {JSONArray f=new JSONArray()
            .put(fact("game","Pokémon","game_or_publisher",96,"top branding"))
            .put(fact("set","Jungle","set",93,"set symbol and layout"))
            .put(fact("card_name","Vileplume","subject",99,"name line"))
            .put(fact("collector_number","15/64","collector_number",96,"lower right"))
            .put(fact("language","English","language",95,"rules text"))
            .put(fact("hp","80 HP","hp",98,"upper right"))
            .put(fact("attack_name","Petal Dance","attack_name",96,"attack box"))
            .put(fact("front_complete","true","complete_identity_bearing_view",98,"full card bounds"));
        if(first)f.put(fact("first_edition_mark","PRESENT","edition_mark",96,"left edge below artwork"))
                .put(fact("edition","FIRST_EDITION","edition",96,"left edge below artwork"))
                .put(fact("shadow_status","shadowless","printing_layout",90,"artwork frame"))
                .put(fact("finish","holo","finish",88,"illustration area"));
        return new JSONObject().put("content_sufficient",true).put("category","tcg")
                .put("views",new JSONArray().put("front")).put("facts",f)
                .put("identity_hint","Pokémon Jungle Vileplume")
                .put("candidates",new JSONArray().put(new JSONObject().put("brand","Pokémon").put("product_line","Jungle")
                        .put("subject","Vileplume").put("year","1999").put("card_number","15/64").put("language","English")
                        .put("edition",first?"FIRST_EDITION":"").put("finish",first?"holo":"").put("format","")
                        .put("material_variant_key",first?"first-edition-holo":"").put("materially_distinct",first).put("confidence",96)))
                .put("missing_discriminators",new JSONArray());}
    private static JSONObject fact(String key,String value,String role,int confidence,String location){return new JSONObject().put("key",key).put("value",value).put("image",0).put("side","front").put("location",location).put("role",role).put("confidence",confidence);}
    private static JSONObject candidate(String edition){return new JSONObject().put("source_url","https://catalog.example/card").put("source_authority","authoritative checklist")
            .put("brand","Pokémon").put("product_line","Jungle").put("main_set","Jungle").put("insert_subset","").put("design_family","").put("sub_series","").put("distinguishing_tokens",new JSONArray())
            .put("release_year","1999").put("subject","Vileplume").put("team","").put("sport","").put("card_number","15/64").put("language","English")
            .put("hp","80 HP").put("evolution_stage","").put("attacks",new JSONArray().put("Petal Dance")).put("copyright_year","1999")
            .put("layout_signature","").put("finish","holo").put("edition",edition).put("printing",edition).put("parallel","").put("parallel_family","").put("parallel_color","").put("print_run","").put("serial_number","")
            .put("format","").put("product_code","").put("package_count","").put("cards_per_pack","").put("autograph_guarantee","").put("memorabilia_guarantee","").put("sealed_status","").put("configuration","").put("product_type","TCG card").put("product_name","catalog card");}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}private static void pass(){passed++;}
}
