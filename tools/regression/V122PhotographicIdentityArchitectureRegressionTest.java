package com.flipcheck.nativebeta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;

/** Generic production-parser regressions for the photographic evidence architecture. */
public final class V122PhotographicIdentityArchitectureRegressionTest {
    private static JSONObject cases;
    public static void main(String[] args)throws Exception{
        cases=new JSONObject(new String(Files.readAllBytes(Paths.get(
                "tools/regression/fixtures/v122_photographic_architecture_cases.json")),StandardCharsets.UTF_8));
        sportsNumber();tcgFront();sportsAlphanumeric();sealedBox();uniqueWithoutBarcode();
        webUnavailableStaysConfirmed();catalogNeverOverwritesPhoto();nonCardNumbersRejected();
        serialSeparated();photoOnlyForConcreteAmbiguity();marketRequiresSourcedHomogeneousSales();
        sourceAudit();
        System.out.println("V122PhotographicIdentityArchitectureRegressionTest: PASS (12/12)");
    }
    private static void sportsNumber()throws Exception{Models.Identification id=parse("sports_numeric");
        require("42".equals(id.physicalCardNumber),"localized sports card number lost");
        require("photographic_identity_closure".equals(id.modelProof)&&"CONFIRMED".equals(id.identityStatus),"sports closure route/status wrong");}
    private static void tcgFront()throws Exception{Models.Identification id=parse("tcg_front");
        require("H7/H40".equals(id.physicalCardNumber),"TCG collector number lost");
        require(id.nextPhotoRequest.isEmpty()&&"CONFIRMED".equals(id.decision),"complete TCG front requested another photo");}
    private static void sportsAlphanumeric()throws Exception{Models.Identification id=parse("sports_alphanumeric");
        require("FC-A7".equals(id.physicalCardNumber)&&!id.cardNumberVerified&&!id.confirmedModel.contains("FC-A7"),"alphanumeric number was not retained as an unverified physical candidate");}
    private static void sealedBox()throws Exception{Models.Identification id=parse("sealed_box");
        require("CONFIRMED".equals(id.decision),"sealed front tuple did not close");
        require(id.physicalCardNumber.isEmpty()&&!id.missingDiscriminativeFields.contains("card"),"sealed product required card number");}
    private static void uniqueWithoutBarcode()throws Exception{Models.Identification id=parse("sports_rating_only");
        require("CONFIRMED".equals(id.decision)&&id.missingNonblockingFields.contains("barcode"),"missing barcode blocked unique photo tuple");}
    private static void webUnavailableStaysConfirmed()throws Exception{Models.Identification id=parse("sports_numeric");
        ConfirmedIdentityEnrichment.unavailable(id);require("CONFIRMED".equals(id.decision)&&id.nextPhotoRequest.isEmpty(),"Web failure reopened identity");
        require(("NOT_AVAILABLE".equals(id.marketStatus)||"IDENTITY_OR_SKU_PENDING".equals(id.marketStatus))&&!id.marketReady,"market failure/exact-resolution suspension not explicit");}
    private static void catalogNeverOverwritesPhoto()throws Exception{Models.Identification id=parse("sports_numeric");
        ConfirmedIdentityEnrichment.apply(id,response(cases.getJSONObject("catalog_conflict")));
        require("42".equals(id.physicalCardNumber)&&"91".equals(id.sourceReportedCatalogNumber)&&id.sourceConfirmedCatalogNumber.isEmpty(),"reported catalog conflict was promoted or photo overwritten");
        require(!id.confirmedModel.contains("91")&&"CONFLICTED".equals(id.overallStatus),"conflicting identifier leaked into the final model/state");}
    private static void nonCardNumbersRejected()throws Exception{Models.Identification id=parse("sports_rating_only");
        require(id.physicalCardNumber.isEmpty(),"rating/stat/generic card_number became physical");
        require(!id.confirmedModel.matches(".*(?:88|91|74).*"),"non-card number leaked into canonical title");}
    private static void serialSeparated()throws Exception{Models.Identification id=parse("sports_serial");
        require("R-9".equals(id.physicalCardNumber)&&"17/99".equals(id.physicalSerial),"serial/card number separation failed");
        require(!id.confirmedModel.contains("R-9")&&!id.confirmedModel.contains("17/99")&&"CONFIRMED".equals(id.variantStatus),"identifier or serial leaked into the main identity title");}
    private static void photoOnlyForConcreteAmbiguity()throws Exception{Models.Identification ambiguous=parseOpen("ambiguous_two");Models.Usage u=new Models.Usage();u.visionCalls=1;
        require(!DiscriminativeVisionPolicy.shouldRun(ambiguous,u),"valid Vision response triggered a forbidden second remote call");
        require(PhotographicIdentityClosure.mayRequestAnotherPhoto(ambiguous),"concrete ambiguity did not produce a targeted user-photo path");
        require(PhotographicIdentityClosure.targetedPhotoRequest(ambiguous).contains("collector number"),"request is not field-targeted");
        Models.Identification single=parseOpen("incomplete_single");require(!DiscriminativeVisionPolicy.shouldRun(single,u),"non-concrete gap triggered Vision 2");
        require(!PhotographicIdentityClosure.mayRequestAnotherPhoto(single),"non-concrete gap requested another photo");}
    private static void marketRequiresSourcedHomogeneousSales()throws Exception{Models.Identification id=parse("sports_numeric");
        ConfirmedIdentityEnrichment.apply(id,response(cases.getJSONObject("market")));
        require(id.priceAvailable&&id.priceSummary.contains("raw/ungraded")&&id.priceSummary.contains("mediana 50"),"homogeneous sold price missing: "+id.priceSummary+" exact="+id.exactIdentityStatus+" verified="+id.cardNumberVerified+" conflicts="+id.numberConflicts+" excluded="+id.excludedComparablesWithReason);
        require(!id.priceSummary.contains("300")&&!id.priceSummary.contains("75"),"graded or active price mixed into primary price");
        int excluded=0;for(Models.MarketComparable c:id.marketComparables)if(!c.included)excluded++;
        require(excluded==2,"unretrieved or mismatched-state comparable was not excluded");}
    private static void sourceAudit()throws Exception{String p=new String(Files.readAllBytes(Paths.get("app/src/main/java/com/flipcheck/nativebeta/IdentificationPipelineV082.java")),StandardCharsets.UTF_8);
        require(p.contains("client.observe(new ArrayList<>(images)"),"initial Vision does not receive all images");
        require(!p.contains("client.recoverPhysicalIdentity(")&&!p.contains("client.verifyPhysicalCardNumber("),"non-technical second Vision remains on production route");
        require(p.contains("NonDestructiveWebEnrichment.apply")&&p.contains("ConfirmedIdentityEnrichment.apply"),"source-only Web merge missing");
        require(!p.contains("POKEMON FRONT IDENTITY")&&!p.contains("COMMERCIAL SMARTPHONE IDENTITY"),"named production exception remains");}
    private static Models.Identification parse(String key)throws Exception{Models.Identification id=parseOpen(key);require(id.closureResult,"closure failed "+key+": "+id.closureMissingFields);return id;}
    private static Models.Identification parseOpen(String key)throws Exception{Models.Identification id=new Models.Identification();id.localScan=new Models.LocalScan();IdentificationPipelineV082.parsePrimaryObservationAndAttemptClosure(id,cases.getJSONObject(key),id.localScan);return id;}
    private static OpenAiClient.Response response(JSONObject payload)throws Exception{JSONObject p=new JSONObject(payload.toString());String number=p.optString("source_confirmed_catalog_number","");
        JSONObject c=new JSONObject().put("source_url",p.optString("source_url","")).put("source_authority","authoritative checklist").put("brand","Maker Alpha").put("product_line","Series Prime").put("release_year","").put("subject","Athlete One").put("team","Club One").put("sport","").put("card_number",number).put("language","").put("hp","").put("evolution_stage","").put("attacks",new JSONArray()).put("copyright_year","").put("layout_signature","").put("finish","").put("edition","").put("printing","").put("parallel","").put("format","").put("configuration","").put("product_type","sports card").put("product_name","Athlete One #"+number);
        p.put("candidates",new JSONArray().put(c));OpenAiClient.Response r=new OpenAiClient.Response();r.payload=p;r.usage.webCalls=1;add(r,p.optString("source_url",""));JSONArray a=p.optJSONArray("comparables");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&!x.optString("source_url","").contains("missing.example"))add(r,x.optString("source_url",""));}return r;}
    private static void add(OpenAiClient.Response r,String url){if(url==null||url.isEmpty())return;Models.Source s=new Models.Source();s.url=url;r.sources.add(s);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
