package com.flipcheck.nativebeta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Deterministic production-path replays for exact hierarchy, disproof and market gating. */
public final class V128ExactCatalogResolutionEndToEndRegressionTest {
    private static JSONObject cases;
    public static void main(String[] args)throws Exception{cases=load();
        sealedResolvesSubSeriesAndFormat();sealedQueriesKeepDistinctiveToken();sealedRejectsOtherFormats();
        sportsParentSubsetAreCompatible();sportsParallelAndPrintRunResolve();marketplaceTextIsNotPhysical();
        tcgExactCandidateWins();tcgWrongSameSubjectIsVetoed();tcgWebIsMandatory();
        catalogNotEvaluatedCannotBeExact();marketWaitsForExactSku();disproofNeedsWeb();
        webFailurePreservesCore();secondPassIsBounded();noFixtureNamesInProduction();
        System.out.println("V128ExactCatalogResolutionEndToEndRegressionTest: PASS (15/15)");}

    private static void sealedResolvesSubSeriesAndFormat()throws Exception{Models.Identification id=run("sealed_exact_format",true);
        require("sealed_trading_card_product".equals(id.canonicalProfile),"sealed profile lost");
        require(id.title.contains("Topps Chrome Basketball Update Series")&&id.title.contains("Hobby Box sigillato"),"exact sealed title wrong: "+id.title);
        require("FORMAT_CATALOG_MATCHED".equals(id.formatStatus)&&"CATALOG_MATCHED".equals(id.exactIdentityStatus),"format/exact states inconsistent");
        require("PASSED".equals(id.disproofStatus)&&id.webContributionScore>0,"catalog/disproof contribution missing");trace("sealed_exact_format",id);}
    private static void sealedQueriesKeepDistinctiveToken()throws Exception{Models.Identification id=run("sealed_exact_format",true);for(String q:id.exactResolutionQueries)require(q.toLowerCase().contains("update"),"query dropped distinctive token: "+q);require(!id.searchQuery.contains("Promotional Athlete"),"featured subjects entered primary query");}
    private static void sealedRejectsOtherFormats()throws Exception{Models.Identification id=run("sealed_exact_format",true);require(id.rejectedCandidates.stream().anyMatch(c->c.hardViolations.toString().contains("CONFIGURATION_CONFLICT")),"Mega alternative not rejected by configuration");}
    private static void sportsParentSubsetAreCompatible()throws Exception{Models.Identification id=run("sports_parent_subset_parallel",true);require(id.catalogVerified,"parent/subset match rejected: "+id.catalogConflicts);require(id.catalogHierarchy.contains("mainSet=Fleer Retro")&&id.catalogHierarchy.contains("subset=Metal Universe"),"catalog hierarchy lost");}
    private static void sportsParallelAndPrintRunResolve()throws Exception{Models.Identification id=run("sports_parent_subset_parallel",true);require(id.title.contains("Fleer Retro")&&id.title.contains("Metal Universe")&&id.title.contains("Bill Sharman")&&id.title.contains("#PM-36"),"sports exact title incomplete: "+id.title);require("Precious Metal Gems".equals(id.sourceConfirmedParallelFamily)&&"Red".equals(id.sourceConfirmedParallelColor)&&"/100".equals(id.sourceConfirmedPrintRun),"parallel hierarchy lost");require("VARIANT_CONFIRMED".equals(id.variantStatus),"variant not confirmed coherently: "+id.variantStatus);trace("sports_parent_subset_parallel",id);}
    private static void marketplaceTextIsNotPhysical()throws Exception{Models.Identification id=run("sports_parent_subset_parallel",false);require(id.factsRejectedWithReason.contains("MARKETPLACE_LISTING_TEXT"),"listing text scope was not excluded");require(!id.physicalSerial.contains("299"),"listing price became physical serial");}
    private static void tcgExactCandidateWins()throws Exception{Models.Identification id=run("tcg_exact_set",true);require(id.title.contains("Pokémon Aquapolis Politoed #H23/H32"),"TCG exact title wrong: "+id.title);require("CATALOG_MATCHED".equals(id.exactIdentityStatus)&&"PASSED".equals(id.disproofStatus),"TCG exact/disproof states wrong");trace("tcg_exact_set",id);}
    private static void tcgWrongSameSubjectIsVetoed()throws Exception{Models.Identification id=run("tcg_exact_set",true);require(id.rejectedCandidates.stream().anyMatch(c->c.cardNumber.equals("12/146")),"wrong same-subject card not rejected");require(!id.title.contains("12/146")&&!id.title.contains("2008"),"wrong candidate leaked into UI");}
    private static void tcgWebIsMandatory()throws Exception{Models.Identification id=run("tcg_exact_set",true);require(id.exactWebResolutionAttempts==1&&!id.exactResolutionQueries.isEmpty(),"exact catalog resolver did not run");require(id.exactResolutionQueries.toString().contains("H23/H32")&&id.exactResolutionQueries.toString().contains("2003"),"Vision/OCR fingerprint missing from query ladder");}
    private static void catalogNotEvaluatedCannotBeExact()throws Exception{Models.Identification id=run("tcg_exact_set",false);require(!"CATALOG_MATCHED".equals(id.exactIdentityStatus)&&id.exactIdentityConfidence<100,"catalog-not-evaluated became exact 100%");}
    private static void marketWaitsForExactSku()throws Exception{Models.Identification id=run("sealed_exact_format",false);require(!id.marketReady&&id.comparablesSummary.contains("sospesi"),"market started without exact format/SKU");}
    private static void disproofNeedsWeb()throws Exception{Models.Identification id=run("tcg_exact_set",false);require("NOT_EXECUTED".equals(id.disproofStatus),"disproof passed with no Web");}
    private static void webFailurePreservesCore()throws Exception{Models.Identification id=run("sealed_exact_format",false);require("CONFIRMED".equals(id.coreIdentityStatus)&&id.title.contains("Update"),"Web failure erased sealed core/token");}
    private static void secondPassIsBounded()throws Exception{JSONObject x=cases.getJSONObject("tcg_exact_set");JSONObject empty=new JSONObject().put("candidates",new JSONArray()).put("comparables",new JSONArray());QueueClient c=new QueueClient(vision(x.getJSONObject("vision")),web(empty,.001),web(x.getJSONObject("web"),.001));Models.Usage u=new Models.Usage();Models.Identification id=IdentificationPipelineV082.identify(local(x),Arrays.asList("data:image/jpeg;base64,AA"),"",c,u);require(c.webCalls==2&&id.exactWebResolutionAttempts==2&&id.catalogVerified,"bounded second resolution did not recover");}
    private static void noFixtureNamesInProduction()throws Exception{String all="";for(java.nio.file.Path p:(Iterable<java.nio.file.Path>)Files.walk(Paths.get("app/src/main/java")).filter(Files::isRegularFile)::iterator)all+=new String(Files.readAllBytes(p),StandardCharsets.UTF_8);require(!all.contains("Bill Sharman")&&!all.contains("Politoed")&&!all.contains("Cooper Flagg")&&!all.contains("Topps")&&!all.contains("Aquapolis")&&!all.contains("H23/H32"),"fixture name/value hardcoded in production");}

    private static Models.Identification run(String key,boolean useWeb)throws Exception{JSONObject x=cases.getJSONObject(key);QueueClient c=new QueueClient(vision(x.getJSONObject("vision")),useWeb?web(x.getJSONObject("web"),.010):unavailable(),null);return IdentificationPipelineV082.identify(local(x),Arrays.asList("data:image/jpeg;base64,AA"),"",c,new Models.Usage());}
    private static Models.LocalScan local(JSONObject x){Models.LocalScan l=new Models.LocalScan();JSONArray a=x.optJSONArray("ocr");StringBuilder b=new StringBuilder();for(int i=0;a!=null&&i<a.length();i++){if(b.length()>0)b.append('\n');b.append(a.optString(i));}l.textByImage.add(b.toString());return l;}
    private static OpenAiClient.Response vision(JSONObject p){OpenAiClient.Response r=new OpenAiClient.Response();r.payload=p;r.complete=true;r.technicalStatus="COMPLETED";r.usage.visionCalls=1;r.usage.requests=1;r.usage.costUsd=.005;return r;}
    private static OpenAiClient.Response web(JSONObject p,double cost){OpenAiClient.Response r=new OpenAiClient.Response();r.payload=p;r.complete=true;r.technicalStatus="COMPLETED";r.usage.webCalls=1;r.usage.requests=1;r.usage.costUsd=cost;addSource(r,p.optString("source_url"));JSONArray a=p.optJSONArray("candidates");for(int i=0;a!=null&&i<a.length();i++)addSource(r,a.optJSONObject(i).optString("source_url"));return r;}
    private static OpenAiClient.Response unavailable(){OpenAiClient.Response r=new OpenAiClient.Response();r.complete=false;r.parseError="network";r.technicalStatus="NETWORK_ERROR";return r;}
    private static void addSource(OpenAiClient.Response r,String u){if(u==null||u.isEmpty())return;Models.Source s=new Models.Source();s.url=u;s.title="retrieved";r.sources.add(s);}
    private static JSONObject load()throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get("tools/regression/fixtures/v128_exact_catalog_resolution_replays.json")),StandardCharsets.UTF_8));}
    private static void trace(String fixture,Models.Identification id){System.out.println("TRACE fixture="+fixture+" title=\""+id.title+"\" profile="+id.canonicalProfile+" queries="+id.exactResolutionQueries+" catalog="+id.catalogCompatibilityStatus+" hierarchy="+id.catalogHierarchy+" rejected="+id.rejectedCandidates.size()+" disproof="+id.disproofStatus+" states=["+id.coreIdentityStatus+","+id.exactIdentityStatus+","+id.identifierStatus+","+id.variantStatus+","+id.formatStatus+","+id.marketStatus+"] confidence=["+id.coreIdentityConfidence+","+id.exactIdentityConfidence+","+id.identifierConfidence+","+id.variantConfidence+","+id.marketConfidence+"] webScore="+id.webContributionScore+" invariants="+id.consistencyInvariants);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static final class QueueClient extends OpenAiClient{final Response first;final Response[] web;int webCalls;QueueClient(Response first,Response one,Response two){super("test");this.first=first;this.web=new Response[]{one,two};}@Override Response observe(List<String>x,String p){return first;}@Override Response enrichConfirmedIdentity(String p){return web[Math.min(webCalls++,1)];}@Override Response webStage(String s,String p){return enrichConfirmedIdentity(p);}}
}
