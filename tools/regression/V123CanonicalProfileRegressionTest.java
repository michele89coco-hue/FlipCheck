package com.flipcheck.nativebeta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;

/** Production-parser regressions for canonical candidates and profile-specific enrichment. */
public final class V123CanonicalProfileRegressionTest {
    private static JSONObject cases;
    public static void main(String[] args)throws Exception{
        cases=new JSONObject(new String(Files.readAllBytes(Paths.get(
                "tools/regression/fixtures/v123_canonical_profile_cases.json")),StandardCharsets.UTF_8));
        equivalentTcgCandidatesFuse();tcgFingerprintClosesWithoutNumber();materialTcgVariantsStayOpen();
        sportsGenericFoilCannotPromoteRareVariant();physicalSportsVariantIsPreserved();
        sealedTitleAndSubjectsAreSeparated();sealedQueryIsProductOnly();genericObjectRequestIsUseful();
        catalogSourceVisibleWithoutMarket();descriptiveDuplicatesCannotCreateZeroMargin();
        sourceAudit();
        System.out.println("V123CanonicalProfileRegressionTest: PASS (11/11)");
    }
    private static void equivalentTcgCandidatesFuse()throws Exception{Models.Identification id=parse("tcg_equivalent");
        require(id.canonicalCandidateCount==1,"equivalent TCG descriptions were not fused: "+id.candidateCanonicalizationSummary);
        require("CONFIRMED".equals(id.decision)&&id.nextPhotoRequest.isEmpty(),"equivalent descriptions blocked closure");}
    private static void tcgFingerprintClosesWithoutNumber()throws Exception{Models.Identification id=parse("tcg_no_number");
        require(id.physicalCardNumber.isEmpty()&&id.physicalCollectorNumber.isEmpty(),"catalog/HP became collector number");
        require("CONFIRMED".equals(id.identityStatus)&&id.missingNonblockingFields.contains("physical_collector_number"),"unique TCG fingerprint did not close");}
    private static void materialTcgVariantsStayOpen()throws Exception{Models.Identification id=parseOpen("tcg_material_ambiguity");
        require(!id.identityConfirmed&&id.canonicalCandidateCount==2,"materially different TCG variants were fused");
        require(PhotographicIdentityClosure.mayRequestAnotherPhoto(id),"concrete ambiguity did not permit targeted photo");
        require(PhotographicIdentityClosure.targetedPhotoRequest(id).toLowerCase().contains("angolo inferiore"),"collector-number request was not targeted");}
    private static void sportsGenericFoilCannotPromoteRareVariant()throws Exception{Models.Identification id=parse("sports_foil_base");String q=ProfileQueryBuilder.seed(id).toLowerCase();
        require("reflective_foil".equals(id.finish)&&!id.rareVariantPhysicalProof,"generic finish became physical parallel proof");
        require(id.physicalParallel.isEmpty(),"unproved parallel entered physical identity");
        require(!q.contains("reflective")&&!q.contains("parallel")&&!q.contains("serial"),"generic sports finish leaked into rare-variant query: "+q);}
    private static void physicalSportsVariantIsPreserved()throws Exception{Models.Identification id=parse("sports_physical_variant");String q=ProfileQueryBuilder.seed(id);
        require("21".equals(id.physicalCardNumber)&&"2/5".equals(id.physicalSerial),"physical number/serial separation failed");
        require(id.rareVariantPhysicalProof&&"Green Pattern".equals(id.physicalParallel),"localized parallel proof lost");
        require(q.contains("Green Pattern")&&q.contains("2/5"),"proved physical variant absent from query");}
    private static void sealedTitleAndSubjectsAreSeparated()throws Exception{Models.Identification id=parse("sealed_featured_subjects");
        require(id.title.startsWith("2027-28 Maker Six Chrome Update")&&id.title.contains("Hobby Box sigillato"),"physically observed sealed format is missing from exact title: "+id.title);
        require("Hobby Box".equals(id.sealedFormat),"sealed commercial format was not retained separately");
        require(id.featuredSubjects.size()==2&&!id.title.contains("Athlete"),"featured subjects became sealed model/title");}
    private static void sealedQueryIsProductOnly()throws Exception{Models.Identification id=parse("sealed_featured_subjects");String q=ProfileQueryBuilder.seed(id).toLowerCase();
        require(q.contains("sealed")&&q.contains("hobby box"),"sealed profile query missing product format");
        require(!q.contains("raw")&&!q.contains("graded")&&!q.contains("card number")&&!q.contains("athlete"),"sealed query contains single-card axes: "+q);}
    private static void genericObjectRequestIsUseful()throws Exception{Models.Identification id=parseOpen("generic_ambiguous");
        require(!id.identityConfirmed,"brand+type incorrectly closed an ambiguous model");
        require("Centralina irrigazione".equals(id.category),"generic internal category leaked to UI: "+id.category);
        String request=PhotographicIdentityClosure.targetedPhotoRequest(id).toLowerCase();
        require(!request.isEmpty()&&request.contains("targhetta")&&request.contains("codice"),"generic object photo request is not actionable: "+request);}
    private static void catalogSourceVisibleWithoutMarket()throws Exception{Models.Identification id=parse("sealed_featured_subjects");
        JSONObject p=new JSONObject().put("source_grounded",true).put("physical_tuple_coherent",true)
                .put("source_url","https://catalog.example/product").put("source_confirmed_catalog_number","BX-10")
                .put("source_confirmed_variant","").put("source_catalog_title","Catalog product title").put("comparables",new JSONArray())
                .put("candidates",new JSONArray().put(new JSONObject().put("source_url","https://catalog.example/product").put("source_authority","official catalog")
                        .put("brand","Maker Six").put("product_line","Maker Six Chrome Update").put("release_year","2027-28").put("subject","").put("sport","basketball").put("card_number","BX-10").put("format","Hobby Box").put("configuration","12 packs").put("product_type","sealed box")));
        ConfirmedIdentityEnrichment.apply(id,response(p));
        require(SourceClassificationPolicy.count(id,"catalog")==1,"used catalog source is not classified/visible");
        require("INSUFFICIENT_VERIFIED_SOLD".equals(id.marketStatus)&&!id.priceAvailable,"empty market did not remain unavailable");}
    private static void descriptiveDuplicatesCannotCreateZeroMargin()throws Exception{Models.Identification id=parse("tcg_equivalent");
        require(id.canonicalCandidateCount==1&&id.tournamentMargin>0,"descriptive duplicate produced artificial zero margin");}
    private static void sourceAudit()throws Exception{String production=new String(Files.readAllBytes(Paths.get("app/src/main/java/com/flipcheck/nativebeta/IdentificationPipelineV082.java")),StandardCharsets.UTF_8);
        String query=new String(Files.readAllBytes(Paths.get("app/src/main/java/com/flipcheck/nativebeta/ProfileQueryBuilder.java")),StandardCharsets.UTF_8);
        require(production.contains("CandidateCanonicalizer.fromJson")&&production.contains("PhotographicIdentityClosure.apply"),"canonicalization/closure not on production parser path");
        require(query.contains("SEALED_TRADING_CARD_PRODUCT")&&query.contains("rareVariantPhysicalProof"),"profile query guards missing");
        require(!production.matches("(?s).*if\\s*\\([^)]*(Curry|Kobe|Boniface|Dragonite|Machamp|Politoed|Topps).*"),"named fixture hardcode found in production");}
    private static Models.Identification parse(String key)throws Exception{Models.Identification id=parseOpen(key);require(id.closureResult,"closure failed for "+key+": "+id.closureMissingFields);IdentificationEngine.finalizeOutput(id,null);return id;}
    private static Models.Identification parseOpen(String key)throws Exception{Models.Identification id=new Models.Identification();id.localScan=new Models.LocalScan();IdentificationPipelineV082.parsePrimaryObservationAndAttemptClosure(id,cases.getJSONObject(key),id.localScan);return id;}
    private static OpenAiClient.Response response(JSONObject payload){OpenAiClient.Response r=new OpenAiClient.Response();r.payload=payload;r.usage.webCalls=1;add(r,payload.optString("source_url",""));JSONArray a=payload.optJSONArray("comparables");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)add(r,x.optString("source_url",""));}return r;}
    private static void add(OpenAiClient.Response r,String url){if(url==null||url.isEmpty())return;Models.Source s=new Models.Source();s.url=url;s.title="Retrieved source";r.sources.add(s);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
