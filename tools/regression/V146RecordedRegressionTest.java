package com.flipcheck.nativebeta;
import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Replays actual API responses and OCR text logs; separate from the installed live gate. */
public class V146RecordedRegressionTest {
    @Test public void recordedBuild145PreservesEightGreensAndRepairsFourFailures() throws Exception {
        Path p=Paths.get("tools/regression/fixtures/v146-recorded-build145.json");if(!Files.exists(p))p=Paths.get("../tools/regression/fixtures/v146-recorded-build145.json");
        JSONArray cases=new JSONObject(new String(Files.readAllBytes(p),StandardCharsets.UTF_8)).getJSONArray("cases");assertEquals(12,cases.length());int previousGreen=0;
        for(int i=0;i<cases.length();i++){
            JSONObject r=cases.getJSONObject(i),ps=r.getJSONObject("payloads");String key=r.getString("case"),label=key+"/"+r.getString("mode");
            Models.LocalScan local=new Models.LocalScan();JSONArray blocks=r.getJSONArray("localOcrBlocks");for(int b=0;b<blocks.length();b++)local.textByImage.add(blocks.getJSONObject(b).getString("text"));
            Models.Identification id=UniversalIdentityEngineV2.replay(local,ps.getJSONObject("primary"),ps.optJSONObject("focused"),ps.getJSONObject("web"),new Models.Usage());
            assertEquals(label+"/core","CONFIRMED",id.coreIdentityStatus);assertEquals(label+"/disproof","PASSED",id.disproofStatus);assertTrue(label+"/invariants",id.consistencyInvariantErrors.isEmpty());
            if(key.equals("topps")){
                assertEquals(label,"Topps",id.brand);assertTrue(label,id.title.contains("2025-26")&&id.title.contains("Update"));assertEquals(label,"Hobby Box",id.sealedFormat);assertEquals(label,"CONFIRMED",id.commercialFormatStatus);
            }else if(key.equals("kobe")){
                assertTrue(label,id.title.startsWith("1997-98 "));assertEquals(label,"skybox",id.brand.toLowerCase(Locale.ROOT));assertEquals(label,"81",id.physicalCardNumber);assertTrue(label,id.family.toLowerCase(Locale.ROOT).contains("metal universe"));assertTrue(label,id.edition.isEmpty());
            }else if(key.equals("vileplume")){
                assertTrue(label,id.title.contains("Pokémon Jungle Vileplume #15/64"));assertEquals(label,"FIRST_EDITION",id.edition);assertEquals(label,"HOLO",id.finish);assertEquals(label,"English",id.language);
            }else{
                assertEquals(label,"Philips",id.brand);assertEquals(label,"TO_VERIFY",id.exactModelStatus);assertEquals(label,"rear_label_or_model_code",id.requestedPhotoReason);
            }
            if(r.getString("liveStatus").equals("PASS"))previousGreen++;
        }
        assertEquals(8,previousGreen);
    }
}
