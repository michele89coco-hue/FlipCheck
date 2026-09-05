package com.flipcheck.nativebeta;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class V147TargetedTraceTest {
    private EvidenceAtom fact(ImmutableEvidenceLedgerV2 ledger,String field,String value,String role,String location){
        return ledger.append(field,value,EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.PRIMARY_VISION,
                "test",0,"front",location,"",role,98,98,"test","");
    }
    @Test public void sealedEditionAndGenericContainerComposeOnlyWithCompleteConfiguration() throws Exception {
        JSONObject base=new JSONObject().put("candidate_id","row").put("edition","Hobby").put("format","Box");
        JSONObject complete=new JSONObject(base.toString()).put("configuration","20 packs per box; 4 cards per pack; 1 autograph per box");
        JSONObject incomplete=new JSONObject(base.toString()).put("configuration","1 autograph per box");
        IdentityCandidateV2 a=CandidateRetrieverV2.parse(new JSONObject().put("candidates",new JSONArray().put(complete)),DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,new ImmutableEvidenceLedgerV2()).get(0);
        IdentityCandidateV2 b=CandidateRetrieverV2.parse(new JSONObject().put("candidates",new JSONArray().put(incomplete)),DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,new ImmutableEvidenceLedgerV2()).get(0);
        assertEquals("Hobby Box",a.value("commercialFormat"));assertEquals("",a.value("edition"));
        assertEquals("",b.value("commercialFormat"));assertEquals("Hobby",b.value("edition"));
    }
    @Test public void compactPackCardNotationKeepsUnitsAndMatchesObservedGuarantee() {
        String catalog="1 autograph guaranteed per box; 20 packs x 4 cards reported by catalog";
        assertTrue(SemanticRelationV3.completeBoxConfiguration(catalog));
        assertTrue(SemanticRelationV3.compatible(SemanticRelationV3.relate("configuration","1 autograph in every box",catalog)));
        assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE,SemanticRelationV3.relate("configuration","2 autographs in every box",catalog));
    }
    @Test public void remoteRetrievalIncludesLocalizedControlLabelsButNotInferredBrand() {
        ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();
        fact(ledger,"productType","remote control","object type","entire object");
        fact(ledger,"controlLabel","PAIR","control label","upper right button");
        fact(ledger,"controlLabel","TOP PICKS","control label","upper grid");
        fact(ledger,"controlLabel","SOURCES","control label","left of navigation");
        ledger.append("manufacturer","Unproven Brand",EvidenceAtom.EpistemicLevel.INFERRED,EvidenceAtom.Modality.PRIMARY_VISION,"test",0,"front","shape only","","shape hypothesis",40,40,"test","");
        String prompt=CandidateRetrieverV2.prompt(DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL,ledger,new ArrayList<>());
        assertTrue(prompt.contains("controlLabel=PAIR"));assertTrue(prompt.contains("controlLabel=TOP PICKS"));assertTrue(prompt.contains("controlLabel=SOURCES"));
        String observed=prompt.substring(prompt.indexOf("LOCALIZED_OBSERVED_FACTS="),prompt.indexOf("INFERRED_LEADS_NON_BINDING="));
        assertFalse(observed.contains("Unproven Brand"));
    }
    private ImmutableEvidenceLedgerV2 sealedLedger(){
        ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();
        fact(ledger,"brand","Acme","manufacturer logo","center logo");fact(ledger,"productLine","Acme Chrome Update Series","full product line","center title");
        fact(ledger,"productReleaseYear","2025-26","printed season","left edge");fact(ledger,"configuration","1 autograph in every box","printed configuration","bottom banner");return ledger;
    }
    private JSONObject sealedCandidate(String id,String format,String configuration,int quality,int layout,String unknown) throws Exception {
        JSONObject row=new JSONObject().put("candidate_id",id).put("source_url","https://catalog.example/"+id).put("source_record_id",id)
                .put("source_page_scope","SINGLE_RECORD").put("identity_level","VARIANT_OR_FORMAT").put("exact_reference",true)
                .put("source_quality_percent",quality).put("brand","Acme").put("product_line","Acme Chrome Update Series")
                .put("year","2025-26").put("format",format).put("configuration",configuration).put("layout_signature",format+" packaging").put("layout_match",layout);
        if(!unknown.isEmpty())row.put("unknown_fields",new JSONArray().put(unknown));return row;
    }
    @Test public void completeConfigurationAndPackagingMatchBeatIncompleteOrDifferentFormats() throws Exception {
        ImmutableEvidenceLedgerV2 ledger=sealedLedger();JSONArray rows=new JSONArray()
                .put(sealedCandidate("jumbo","Jumbo Box","autograph advertised; exact pack quantities not confirmed",96,88,"pack count not confirmed"))
                .put(sealedCandidate("hobby","Hobby Box","1 autograph guaranteed per box; 20 packs x 4 cards",91,94,"packaging format label"))
                .put(sealedCandidate("sapphire","Sapphire Box","1 autograph per box; 8 packs x 4 cards",96,52,"Sapphire marking not visible"));
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(CandidateRetrieverV2.parse(new JSONObject().put("candidates",rows),DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,ledger),ledger,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT);
        assertEquals("hobby",ranked.get(0).candidateId);assertTrue(ranked.get(0).disproofPassed);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;FinalStateReducerV2.reduce(id,ledger,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,ranked,new ArrayList<>(),"");
        assertEquals("Hobby Box",id.sealedFormat);assertEquals("CONFIRMED",id.commercialFormatStatus);
    }
}
