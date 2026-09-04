package com.flipcheck.nativebeta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Replays raw production-shaped Vision payloads through the exact Android parser route. */
public final class V124ProductionAliasEndToEndRegressionTest {
    private static JSONObject cases;

    public static void main(String[] args) throws Exception {
        cases=new JSONObject(new String(Files.readAllBytes(Paths.get(
                "tools/regression/fixtures/v124_production_alias_payloads.json")),StandardCharsets.UTF_8));
        sportsAliasesNormalizeAndClose();
        sportsWebFailurePreservesClosure();
        tcgAliasesBuildCompositeFingerprint();
        tcgWebFailurePreservesClosure();
        tcgUniqueFrontDoesNotRequireLiteralSet();
        materialTcgVariantsStayOpenWithTargetedRequest();
        tcgNeverReceivesDevicePhotoRequest();
        collectorFractionIsCollectorNumberNotSerial();
        localizedNumericCardNumberBindsPhysically();
        HpRatingAndStatisticsNeverBecomeCardNumbers();
        TemporalRolesRemainSeparated();
        sealedProductKeepsFeaturedSubjectsSeparate();
        GenericFoilDoesNotLeakRareVariantTerms();
        genericObjectNeedsPhysicalModelLabel();
        catalogSourceRemainsVisibleWithoutMarketPrice();
        consistencyInvariantsMatchCanonicalFields();
        aliasMapCoversProductionVocabulary();
        sourceRouteAudit();
        photoImportPathIsCrashContained();
        System.out.println("V124ProductionAliasEndToEndRegressionTest: PASS (19/19)");
    }

    private static void sportsAliasesNormalizeAndClose() throws Exception {
        Models.Identification id=parse("sports_alias_payload");
        require("Maker Alpha".equals(id.confirmedBrand),"manufacturer/publisher alias was lost");
        require(id.confirmedFamily.contains("Metal Series"),"set_or_product_line alias was lost");
        require("81".equals(id.physicalCardNumber),"localized card_number did not bind");
        require(id.aliasesConsumed.contains("set_or_product_line→productLine"),"alias telemetry missing");
        require("CONFIRMED".equals(id.identityStatus),"production sports payload did not close");
    }

    private static void sportsWebFailurePreservesClosure() throws Exception {
        Models.Identification id=parse("sports_alias_payload");
        ConfirmedIdentityEnrichment.unavailable(id);
        IdentificationEngine.finalizeOutput(id,null);
        require(id.identityConfirmed&&"CONFIRMED".equals(id.decision),"Web failure reopened sports identity");
        require("81".equals(id.physicalCardNumber)&&id.nextPhotoRequest.isEmpty(),"Web failure erased physical identity or requested a photo");
        require(("NOT_AVAILABLE".equals(id.marketStatus)||"IDENTITY_OR_SKU_PENDING".equals(id.marketStatus))&&!id.marketReady,"market failure/exact-resolution state not separated");
    }

    private static void tcgAliasesBuildCompositeFingerprint() throws Exception {
        Models.Identification id=parse("tcg_alias_payload");
        require(id.aliasesConsumed.contains("attack_name→attackNames")
                &&id.aliasesConsumed.contains("move_name→attackNames"),"move aliases did not enter canonical array");
        require(id.fingerprintComponents.contains("attack_names(3)"),"three attacks absent from fingerprint: "+id.fingerprintComponents);
        require(id.fingerprintScore>=65&&"CONFIRMED".equals(id.identityStatus),"TCG composite fingerprint did not close");
    }

    private static void tcgWebFailurePreservesClosure() throws Exception {
        Models.Identification id=parse("tcg_alias_payload");
        ConfirmedIdentityEnrichment.unavailable(id);
        IdentificationEngine.finalizeOutput(id,null);
        require(id.identityConfirmed&&"CONFIRMED".equals(id.identityStatus),
                "Web failure reopened TCG photographic identity");
        require("H23/H32".equals(id.physicalCollectorNumber)&&id.nextPhotoRequest.isEmpty(),
                "Web failure erased TCG collector identity or requested a photo");
        require(("NOT_AVAILABLE".equals(id.marketStatus)||"IDENTITY_OR_SKU_PENDING".equals(id.marketStatus))&&!id.marketReady,"TCG market failure/exact-resolution suspension was not separated");
    }

    private static void tcgUniqueFrontDoesNotRequireLiteralSet() throws Exception {
        Models.Identification id=parse("tcg_no_literal_set");
        require(id.normalizedPhotoIdentity.set().isEmpty(),"fixture unexpectedly supplied a photographic set");
        require(id.photoIdentityComplete&&id.canonicalCandidateCount==1&&id.identityConfirmed,
                "unique complete TCG front remained open without literal set");
        require(!id.missingDiscriminativeFields.contains("set"),"nonblocking set appeared as discriminator");
    }

    private static void materialTcgVariantsStayOpenWithTargetedRequest() throws Exception {
        Models.Identification id=parseOpen("tcg_material_variants");
        require(!id.identityConfirmed&&id.canonicalCandidateCount==2,"material TCG variants were incorrectly fused");
        String request=PhotographicIdentityClosure.targetedPhotoRequest(id).toLowerCase();
        require(request.contains("angolo inferiore")&&request.contains("collector"),"TCG request was not collector-area specific: "+request);
    }

    private static void tcgNeverReceivesDevicePhotoRequest() throws Exception {
        Models.Identification id=parseOpen("tcg_material_variants");
        id.nextPhotoRequest="Fotografa MODEL / P/N sulla targhetta";
        ConsistencyInvariantChecker.enforce(id,"test_tcg_request");
        String request=id.nextPhotoRequest.toLowerCase();
        require(!request.contains("model")&&!request.contains("p/n"),"device request survived in TCG profile");
        require(request.contains("collector"),"TCG request was not reconstructed from discriminator");
    }

    private static void collectorFractionIsCollectorNumberNotSerial() throws Exception {
        Models.Identification id=parse("tcg_alias_payload");
        require("H23/H32".equals(id.physicalCollectorNumber),"collector fraction was not physically bound");
        require("H23/H32".equals(id.physicalCardNumber),"collector number missing from compatibility field");
        require(id.physicalSerial.isEmpty(),"collector fraction became specimen serial");
    }

    private static void localizedNumericCardNumberBindsPhysically() throws Exception {
        Models.Identification id=parse("sports_alias_payload");
        require("81".equals(id.physicalCardNumber),"localized numeric card number missing");
        require(id.physicalCardNumberOrigin.startsWith(EvidenceLedger.PRIMARY_VISION+":candidate")&&id.physicalCardNumberOrigin.contains("card_number")&&!id.cardNumberVerified,
                "number provenance is not direct/semantic/localized: "+id.physicalCardNumberOrigin);
    }

    private static void HpRatingAndStatisticsNeverBecomeCardNumbers() throws Exception {
        Models.Identification sports=parse("sports_alias_payload");
        Models.Identification tcg=parse("tcg_alias_payload");
        require(!"77".equals(sports.physicalCardNumber),"graphic rating became card number");
        require(!"110".equals(tcg.physicalCardNumber),"HP/PV became card number");
        require(sports.aliasesConsumed.contains("rating→statistics"),"rating was not mapped to the non-identifying statistics axis");
    }

    private static void TemporalRolesRemainSeparated() throws Exception {
        Models.Identification sports=parse("sports_alias_payload");
        NormalizedPhotoIdentity n=sports.normalizedPhotoIdentity;
        require(n.physicalYear().isEmpty(),"copyright/stat season was promoted to commercial release year");
        require("1998".equals(n.best(CanonicalFieldKey.COPYRIGHT_YEAR)),"copyright year lost");
        require("1997-98".equals(n.best(CanonicalFieldKey.STATISTICAL_SEASON)),"statistical season lost");
        Models.Identification sealed=parse("sealed_alias_payload");
        require("2028/29".equals(sealed.normalizedPhotoIdentity.physicalYear()),"physical product season was not preserved");
    }

    private static void sealedProductKeepsFeaturedSubjectsSeparate() throws Exception {
        Models.Identification id=parse("sealed_alias_payload");
        require(id.title.contains("Chrome Series")&&id.title.contains("Hobby Box sigillato")&&"Hobby Box".equals(id.sealedFormat),"physically verified sealed format/title separation failed: "+id.title);
        require(id.featuredSubjects.size()==2&&!id.title.contains("Athlete"),"featured subjects contaminated sealed title");
        String query=ProfileQueryBuilder.seed(id).toLowerCase();
        require(!query.contains("athlete")&&!query.contains("raw")&&!query.contains("graded")&&!query.contains("card number"),
                "sealed market seed contains single-card axes: "+query);
    }

    private static void GenericFoilDoesNotLeakRareVariantTerms() throws Exception {
        Models.Identification id=parseExistingV123("sports_foil_base");
        String query=ProfileQueryBuilder.seed(id).toLowerCase();
        require(!id.rareVariantPhysicalProof&&id.physicalParallel.isEmpty(),"finish promoted to rare parallel proof");
        for(String forbidden:new String[]{"precious metal gems","pmg","refractor","gold","numbered","parallel"})
            require(!query.contains(forbidden),"unproved rare variant leaked into query: "+query);
    }

    private static void genericObjectNeedsPhysicalModelLabel() throws Exception {
        Models.Identification id=parseOpen("generic_alias_payload");
        require(!id.identityConfirmed&&"Centralina irrigazione".equals(id.category),"ambiguous generic object was closed or badly presented");
        String request=PhotographicIdentityClosure.targetedPhotoRequest(id).toLowerCase();
        require(request.contains("targhetta")&&request.contains("model")&&request.contains("codice"),"generic targeted request is not useful: "+request);
    }

    private static void catalogSourceRemainsVisibleWithoutMarketPrice() throws Exception {
        Models.Identification id=parse("sports_alias_payload");
        JSONObject payload=new JSONObject().put("source_grounded",true).put("physical_tuple_coherent",true)
                .put("source_url","https://catalog.example/item/alpha").put("source_confirmed_catalog_number","CAT-81")
                .put("source_confirmed_variant","").put("source_catalog_title","Catalog title")
                .put("comparables",new JSONArray()).put("candidates",new JSONArray().put(new JSONObject()
                        .put("source_url","https://catalog.example/item/alpha").put("source_authority","specialist database")
                        .put("brand","Maker Alpha").put("product_line","Metal Series").put("subject","Athlete Alpha").put("card_number","CAT-81")));
        ConfirmedIdentityEnrichment.apply(id,response(payload));
        require(!id.sources.isEmpty(),"retrieved catalog source is not visible");
        require(!id.priceAvailable&&("IDENTITY_UNCONFIRMED".equals(id.marketStatus)||"IDENTITY_OR_SKU_PENDING".equals(id.marketStatus)),"identifier conflict did not block market use");
        require("81".equals(id.physicalCardNumber)&&"CAT-81".equals(id.sourceReportedCatalogNumber)&&id.sourceConfirmedCatalogNumber.isEmpty(),"incompatible catalog number overwrote physical number");
    }

    private static void consistencyInvariantsMatchCanonicalFields() throws Exception {
        Models.Identification sports=parse("sports_alias_payload");
        Models.Identification tcg=parse("tcg_alias_payload");
        require("PASS".equals(sports.consistencyInvariants)&&"PASS".equals(tcg.consistencyInvariants),"invariant checker did not pass");
        require(!sports.missingDiscriminativeFields.contains("set_or_product_line"),"present productLine also marked missing");
        require(!tcg.missingDiscriminativeFields.contains("collector"),"present collectorNumber also marked missing");
    }

    private static void aliasMapCoversProductionVocabulary() {
        Map<String,CanonicalFieldKey> map=CanonicalFieldKey.aliasMap();
        require(CanonicalFieldKey.fromAlias("manufacturer/publisher")==CanonicalFieldKey.MANUFACTURER,"manufacturer/publisher alias missing");
        require(CanonicalFieldKey.fromAlias("set_or_product_line")==CanonicalFieldKey.PRODUCT_LINE,"set_or_product_line alias missing");
        require(CanonicalFieldKey.fromAlias("attack_name")==CanonicalFieldKey.ATTACK_NAME,"attack_name alias missing");
        require(CanonicalFieldKey.fromAlias("hp/stat")==CanonicalFieldKey.HP_OR_PV,"hp/stat alias missing");
        require(map.size()>=70,"alias vocabulary unexpectedly incomplete: "+map.size());
    }

    private static void sourceRouteAudit() throws Exception {
        String pipeline=read("app/src/main/java/com/flipcheck/nativebeta/IdentificationPipelineV082.java");
        String profile=read("app/src/main/java/com/flipcheck/nativebeta/IdentityProfileEngine.java");
        String query=read("app/src/main/java/com/flipcheck/nativebeta/ProfileQueryBuilder.java");
        require(pipeline.contains("parseObservation(id, observation)")&&pipeline.contains("PhotographicIdentityClosure.apply"),
                "production parser no longer reaches photographic closure");
        require(pipeline.contains("PhotographicFactNormalizer.normalize(id,\"after_focused_vision_merge\")"),
                "focused Vision merge does not rerun normalization");
        require(profile.contains("PhotographicFactNormalizer.require")&&query.contains("IdentityProfileEngine.tuple"),
                "profile/query bypass canonical normalized model");
        String production=pipeline+profile+query+read("app/src/main/java/com/flipcheck/nativebeta/PhotographicFactNormalizer.java");
        require(!production.matches("(?s).*if\\s*\\([^)]*(Kobe|Politoed|SkyBox|Pokémon|Pokemon|Curry|Boniface|Dragonite|Machamp|Topps Chrome Update).*"),
                "named fixture hardcode found in production logic");
    }

    private static Models.Identification parse(String key) throws Exception {
        Models.Identification id=parseOpen(key);
        require(id.closureResult,"closure failed for "+key+": "+id.closureMissingFields);
        IdentificationEngine.finalizeOutput(id,null);
        require("completed".equals(id.normalizationStage)||id.normalizationStage.startsWith("completed:"),"normalization marker missing");
        return id;
    }

    private static void photoImportPathIsCrashContained() throws Exception {
        String activity=read("app/src/main/java/com/flipcheck/nativebeta/MainActivity.java");
        String preview=read("app/src/main/java/com/flipcheck/nativebeta/PreviewBitmapLoader.java");
        require(activity.contains("private void renderPhotosSafely()")
                        && activity.contains("catch (Throwable previewFailure)")
                        && activity.contains("renderPhotosSafely();"),
                "photo import route is not protected from preview/provider failures");
        require(preview.contains("loadThumbnail")&&preview.contains("Math.min(512")
                        &&preview.contains("inSampleSize"),
                "photo preview is no longer bounded");
        require(!activity.matches("(?s).*\\.setImageURI\\(.*"),
                "photo path can decode an unbounded original image on the UI thread");
    }

    private static Models.Identification parseOpen(String key) throws Exception {
        Models.Identification id=new Models.Identification();id.localScan=new Models.LocalScan();
        IdentificationPipelineV082.parsePrimaryObservationAndAttemptClosure(id,cases.getJSONObject(key),id.localScan);
        return id;
    }

    private static Models.Identification parseExistingV123(String key) throws Exception {
        JSONObject old=new JSONObject(read("tools/regression/fixtures/v123_canonical_profile_cases.json"));
        Models.Identification id=new Models.Identification();id.localScan=new Models.LocalScan();
        IdentificationPipelineV082.parsePrimaryObservationAndAttemptClosure(id,old.getJSONObject(key),id.localScan);
        require(id.closureResult,"legacy generic fixture no longer closes: "+key);return id;
    }

    private static OpenAiClient.Response response(JSONObject payload) {
        OpenAiClient.Response r=new OpenAiClient.Response();r.payload=payload;r.usage.webCalls=1;
        Models.Source source=new Models.Source();source.url=payload.optString("source_url","");source.title="Catalog source";r.sources.add(source);return r;
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);
    }

    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
