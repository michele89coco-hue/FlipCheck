package com.flipcheck.nativebeta;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class V151TargetedSealedSelectionTest {
    private EvidenceAtom observed(ImmutableEvidenceLedgerV2 ledger,String field,String value,String region){
        return ledger.append(field,value,EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.FOCUSED_VISION,
                "test",0,"front",region,"crop",field,98,95,"test","");
    }
    private ImmutableEvidenceLedgerV2 ledger(){
        ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();
        observed(ledger,"manufacturer","Example","front logo");
        observed(ledger,"productLine","Chrome Update Series","front title");
        observed(ledger,"productReleaseYear","2025-26","side season");
        observed(ledger,"configuration","1 autograph in every box","bottom banner");
        return ledger;
    }
    private IdentityCandidateV2 candidate(String format,String configuration,String unknown){
        IdentityCandidateV2 candidate=new IdentityCandidateV2("candidate",DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,"WEB");
        candidate.retrieved=true;candidate.exactReference=true;candidate.reportedDisproofPassed=true;
        candidate.sourceUrl="https://catalog.invalid/record";candidate.sourceRecordId="isolated-format";
        candidate.sourcePageScope="CHECKLIST_ROW";candidate.identityLevel="VARIANT_OR_FORMAT";candidate.webSourceQuality=94;
        candidate.fields.put("manufacturer","Example");candidate.fields.put("productLine","Chrome Update Series");
        candidate.fields.put("productReleaseYear","2025-26");candidate.fields.put("commercialFormat",format);
        candidate.fields.put("configuration",configuration);candidate.unknownFields.add(unknown);return candidate;
    }
    @Test public void photographedFormatLabelQuestionDoesNotOverrideCompleteConfiguration(){
        ImmutableEvidenceLedgerV2 ledger=ledger();IdentityCandidateV2 complete=candidate("Hobby Box",
                "4 cards per pack; 20 packs per box; 1 autograph per box","Whether the pictured physical box is Hobby rather than another format");
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(complete)),ledger,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,ledger,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,ranked,new ArrayList<>(),"");
        assertEquals("Hobby Box",id.sealedFormat);assertEquals("CONFIRMED",id.commercialFormatStatus);
    }
    @Test public void sourceLevelFormatUncertaintyStillBlocksTheFormat(){
        ImmutableEvidenceLedgerV2 ledger=ledger();IdentityCandidateV2 uncertain=candidate("Hobby Box",
                "4 cards per pack; 20 packs per box; 1 autograph per box","format not isolated from competing variants");
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(uncertain)),ledger,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,ledger,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,ranked,new ArrayList<>(),"");
        assertEquals("",id.sealedFormat);assertEquals("TO_VERIFY",id.commercialFormatStatus);
    }
    @Test public void namedCompetingFormatStillBlocksTheFormat(){
        ImmutableEvidenceLedgerV2 ledger=ledger();IdentityCandidateV2 uncertain=candidate("Hobby Box",
                "4 cards per pack; 20 packs per box; 1 autograph per box","whether photographed box is hobby or jumbo");
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(uncertain)),ledger,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,ledger,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,ranked,new ArrayList<>(),"");
        assertEquals("",id.sealedFormat);assertEquals("TO_VERIFY",id.commercialFormatStatus);
    }
    @Test public void sealedPromptForbidsIncompleteFormatsFromCompetingAtVariantLevel(){
        String prompt=CandidateRetrieverV2.prompt(DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,ledger(),new ArrayList<>());
        assertTrue(prompt.contains("Do not spend a VARIANT_OR_FORMAT candidate on a format whose cards-per-pack and packs-per-box are absent"));
        assertTrue(prompt.contains("cannot disprove a complete configuration record"));
    }
    @Test public void incompleteNamedFormatIsKeptOnlyAsCoreIdentityLead(){
        IdentityCandidateV2 incomplete=candidate("Jumbo Box","11 cards per pack; packs per box not shown","packs per box unknown");
        CandidateRetrieverV2.demoteUnprovedSealedFormat(incomplete,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT);
        assertEquals("",incomplete.value("commercialFormat"));assertEquals("CORE_IDENTITY",incomplete.identityLevel);
        assertFalse(incomplete.exactReference);assertFalse(incomplete.reportedDisproofPassed);

        IdentityCandidateV2 complete=candidate("Hobby Box","4 cards per pack; 20 packs per box; 1 autograph per box","physical format badge not visible");
        CandidateRetrieverV2.demoteUnprovedSealedFormat(complete,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT);
        assertEquals("Hobby Box",complete.value("commercialFormat"));assertEquals("VARIANT_OR_FORMAT",complete.identityLevel);
        assertTrue(complete.exactReference);
    }
}
