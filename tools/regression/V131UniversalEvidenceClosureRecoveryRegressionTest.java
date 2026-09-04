package com.flipcheck.nativebeta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;

/** Replays the four v1.30 device traces through the production parser and reducers. */
public final class V131UniversalEvidenceClosureRecoveryRegressionTest {
    private static JSONObject fixtures;private static int passed;
    public static void main(String[] args)throws Exception{
        fixtures=new JSONObject(new String(Files.readAllBytes(Paths.get("tools/regression/fixtures/v131_real_device_replays.json")),StandardCharsets.UTF_8));
        sportsProvenanceAndSemanticScope();tcgCompleteTupleHasNoPhantomConflict();
        sealedFormatDoesNotBlockCore();electronicsRecoveryRejectsShapeOnlyBrand();
        noObjectSpecificProductionHardcode();System.out.println("V131UniversalEvidenceClosureRecoveryRegressionTest: PASS ("+passed+"/5)");
    }
    private static void sportsProvenanceAndSemanticScope()throws Exception{
        Models.Identification id=parse("kobe");id.sourceConfirmedCatalogNumber="81";id.sourceConfirmedReleaseYear="1997-98";
        id.catalogVerified=true;id.webStatus="COMPLETED";id.disproofStatus="PASSED";id.webContributionScore=90;
        EvidenceLedger.addWebCatalogFact(id,"source_confirmed_catalog_number","81",96,"https://catalog.example/81");
        FinalIdentityDecisionEngine.freeze(id,"v131_sports_provenance");
        require("81".equals(id.physicalCardNumber)&&id.physicalCardNumberOrigin.startsWith(EvidenceLedger.PRIMARY_VISION),"physical number provenance missing");
        require("1997-98".equals(id.physicalReleaseYear)&&"1996-97".equals(id.statisticsSeason),"statistics season contaminated release year");
        require(id.numberAgreement&&"PHOTO_PLUS_CATALOG".equals(id.combinedVerification),"photo/catalog agreement not proven");pass();
    }
    private static void tcgCompleteTupleHasNoPhantomConflict()throws Exception{
        Models.Identification id=parse("vileplume");FinalIdentityDecisionEngine.freeze(id,"v131_tcg_complete");
        require("CONFIRMED".equals(id.coreIdentityStatus)&&"PHYSICALLY_VERIFIED".equals(id.exactIdentityStatus),"complete TCG tuple did not close: identity="+id.identityStatus+", core="+id.coreIdentityStatus+", exact="+id.exactIdentityStatus+", identifier="+id.identifierStatus+", conflicts="+id.numberConflicts+", invariants="+id.consistencyInvariantErrors);
        require("CONFIRMED".equals(id.exactEditionStatus)&&"CONFIRMED".equals(id.finishStatus),"edition/finish states were lost");
        require(id.numberConflicts.isEmpty()&&id.documentedConflicts.isEmpty()&&id.blockingReason.isEmpty(),"identical number evidence created a conflict");pass();
    }
    private static void sealedFormatDoesNotBlockCore()throws Exception{
        Models.Identification id=parse("topps");FinalIdentityDecisionEngine.freeze(id,"v131_sealed_core");
        require("CONFIRMED".equals(id.identityStatus)&&"CONFIRMED".equals(id.coreIdentityStatus),"format uncertainty blocked sealed core");
        require("FORMAT_PENDING".equals(id.exactIdentityStatus)&&"TO_VERIFY".equals(id.commercialFormatStatus),"format was not isolated");
        require(id.blockingReason.isEmpty()&&!EvidencePolicy.publicStatus(id).contains("NUMERO"),"sealed product requested card number");pass();
    }
    private static void electronicsRecoveryRejectsShapeOnlyBrand()throws Exception{
        Models.Identification id=parse("remote");Models.Usage usage=new Models.Usage();usage.visionCalls=1;usage.costUsd=.009;
        require("television_remote_control".equals(id.canonicalProfile)&&PhysicalIdentityRecovery.eligible(id,usage),"electronics recovery ladder was not activated");
        OpenAiClient.Response focused=new OpenAiClient.Response();focused.complete=true;focused.payload=new JSONObject()
                .put("applicable",true).put("same_foreground_object",true).put("physical_binding",true)
                .put("overlay_or_watermark",false).put("external_watermark",false).put("identity_obscured",false)
                .put("complete",false).put("ambiguity_resolved",true).put("discriminative_field_visible",true)
                .put("category_key","other").put("canonical_name","Television remote control").put("confidence",97)
                .put("fields",new JSONArray().put("brand=Philips").put("product_type=television remote control").put("control_layout=TV navigation"))
                .put("observed_labels",new JSONArray().put("Philips").put("Netflix").put("Sources"))
                .put("evidence_facts",new JSONArray().put(focusedFact("brand","Philips","brand_logo","lower front logo",98))
                        .put(focusedFact("product_type","television remote control","product_type","full object",99))
                        .put(focusedFact("control_layout","TV navigation and numeric keypad","control_layout","front controls",98))
                        .put(focusedFact("layout_signature","distinctive television remote layout","layout_signature","full object bounds",96)))
                .put("contradiction","");
        require(PhysicalIdentityRecovery.apply(id,focused),"focused electronics facts were not merged");
        PhotographicIdentityClosure.apply(id,"v131_electronics_recovery");FinalIdentityDecisionEngine.freeze(id,"v131_electronics_final");
        require("Philips".equals(id.observedBrand)&&!id.title.contains("LG"),"shape-only candidate overrode located brand mark");
        require("CONFIRMED".equals(id.coreIdentityStatus)&&"TO_VERIFY".equals(id.exactModelStatus),"brand/core and exact model were not separated");pass();
    }
    private static void noObjectSpecificProductionHardcode()throws Exception{
        StringBuilder all=new StringBuilder();for(java.nio.file.Path p:(Iterable<java.nio.file.Path>)Files.walk(Paths.get("app/src/main/java/com/flipcheck/nativebeta"))::iterator)
            if(p.toString().endsWith(".java"))all.append(new String(Files.readAllBytes(p),StandardCharsets.UTF_8));
        String x=all.toString().toLowerCase();require(!x.contains("kobe bryant")&&!x.contains("vileplume")&&!x.contains("cooper flagg"),"fixture-specific production hardcode detected");pass();
    }
    private static Models.Identification parse(String key)throws Exception{Models.Identification id=new Models.Identification();IdentificationPipelineV082.parsePrimaryObservationAndAttemptClosure(id,fixtures.getJSONObject(key),new Models.LocalScan());return id;}
    private static JSONObject focusedFact(String key,String value,String role,String location,int confidence)throws Exception{return new JSONObject().put("key",key).put("value",value).put("evidence_type","focused_visual_observation").put("confidence",confidence).put("image_index",0).put("side","front").put("location",location).put("semantic_role",role);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}private static void pass(){passed++;}
}
