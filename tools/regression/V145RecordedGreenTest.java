package com.flipcheck.nativebeta;
import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Actual recorded API replies; never used by the production client or live suite. */
public class V145RecordedGreenTest {
    @Test public void previouslyGreenRecordedResponsesRetainTheirResolvedFields() throws Exception {
        Path p=Paths.get("tools/regression/fixtures/v145-recorded-green-contracts.json");
        if(!Files.exists(p))p=Paths.get("../tools/regression/fixtures/v145-recorded-green-contracts.json");
        JSONArray rows=new JSONObject(new String(Files.readAllBytes(p),StandardCharsets.UTF_8)).getJSONArray("cases");
        assertEquals(13,rows.length());
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i),v=r.getJSONObject("payloads"),expected=r.getJSONObject("expected");
            Models.Identification id=UniversalIdentityEngineV2.replay(new Models.LocalScan(),v.getJSONObject("primary"),v.optJSONObject("focused"),v.getJSONObject("web"),new Models.Usage());
            String[] keys={"core","title","brand","edition","finish","language","number","modelStatus","photoReason"};
            String[] values={id.coreIdentityStatus,id.title,id.brand,id.edition,id.finish,id.language,id.physicalCardNumber,id.exactModelStatus,id.requestedPhotoReason};
            for(int k=0;k<keys.length;k++)assertEquals(r.getString("run")+"/"+r.getString("case")+"/"+r.getString("mode")+"/"+keys[k],expected.getString(keys[k]).toLowerCase(Locale.ROOT),values[k].toLowerCase(Locale.ROOT));
            if(!r.getString("remotePrompt").isEmpty()){
                ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.Result a=ObservationExtractorV2.ingestPrimary(v.getJSONObject("primary"),l);TypedFieldNormalizerV2.normalize(l);
                DomainProfileRouterV2.Profile profile=DomainProfileRouterV2.route(a.category,l);ObservationExtractorV2.Result b=ObservationExtractorV2.ingestFocused(v.optJSONObject("focused"),l,profile,"recorded");TypedFieldNormalizerV2.normalize(l);
                // Preserve the recorded query/evidence bytes; only the documented source-selection policy is appended.
                String expectedPrompt=r.getString("remotePrompt").replace("separate from format/SKU.","separate from format/SKU."+CandidateRetrieverV2.REMOTE_ACCESSORY_CONTRACT);
                assertEquals(expectedPrompt,CandidateRetrieverV2.prompt(profile,l,HypothesisGeneratorV2.merge(a.hypotheses,b.hypotheses)));
            }
        }
    }
}
