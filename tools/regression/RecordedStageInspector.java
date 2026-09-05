package com.flipcheck.nativebeta;
import org.json.*;
import java.nio.file.*;
import java.util.*;

/** Diagnostic replay of captured API stages. Does not replace local OCR or live tests. */
public final class RecordedStageInspector {
 public static void main(String[] args)throws Exception {
  JSONArray runs=new JSONObject(Files.readString(Path.of(args[0]))).getJSONArray("runs");
  for(int i=0;i<runs.length();i++){
   JSONObject run=runs.getJSONObject(i);JSONArray stages=run.getJSONArray("stagePayloads");
   ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();
   DomainProfileRouterV2.Profile profile=DomainProfileRouterV2.route(run.getString("profile"),ledger);
   List<IdentityCandidateV2> all=new ArrayList<>();
   for(int j=0;j<stages.length();j++){
    String s=stages.getString(j),stage=s.substring(0,s.indexOf('\n'));
    JSONObject payload=new JSONObject(s.substring(s.indexOf('\n')+1));
    if(stage.contains("web"))all.addAll(CandidateRetrieverV2.parse(payload,profile,ledger));
    else if(stage.contains("focused"))ObservationExtractorV2.ingestFocused(payload,ledger,profile,"recorded-focused");
    else ObservationExtractorV2.ingestPrimary(payload,ledger);
    TypedFieldNormalizerV2.normalize(ledger);
   }
   Models.Identification id=new Models.Identification();id.uploadedImageCount=run.getJSONArray("inputs").length();
   FinalStateReducerV2.reduce(id,ledger,profile,CandidateVerifierV2.verify(all,ledger,profile),ConflictResolverV2.resolve(ledger,profile),"");
   System.out.println(run.getString("case")+"/"+run.getString("mode")+": "+id.coreIdentityStatus+" | "+id.title+" | conflicts="+id.v2TrueConflicts);
  }
 }
}
