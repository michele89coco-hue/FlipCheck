package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class V153UniversalEvidencePolicyTest {
    private EvidenceAtom observed(ImmutableEvidenceLedgerV2 ledger,String field,String value,String region){
        return ledger.append(field,value,EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.FOCUSED_VISION,
                "test",0,"front",region,"focus",field,96,94,"test","");
    }

    @Test public void externalConversationScreenshotIsNotSubjectEvidence(){
        String ui="14:35\nHa lavorato per 43s\nFLIPCHECK Work\nLa build ha concluso con un risultato tecnico dettagliato\nchatgpt.com\nFollow-up\nGPT-6 Astra\nRun e risultati\nCompilazione e test automatici passano\nOccorre ripetere il run";
        assertTrue(EvidenceProofPolicyV3.likelyExternalUiBlock(ui));
        Models.LocalScan scan=new Models.LocalScan();scan.textByImage.add("KOBE BRYANT\nMETAL UNIVERSE");scan.textByImage.add(ui);
        assertEquals(Arrays.asList(0),EvidenceProofPolicyV3.subjectImageIndexes(scan,2));
        ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestLocal(scan,ledger);
        assertEquals("UI_OVERLAY",ledger.current("printedLabel").get(1).semanticScope);
        assertEquals(EvidenceAtom.EpistemicLevel.INFERRED,ledger.current("printedLabel").get(1).epistemicLevel);
    }

    @Test public void copyrightOwnerCannotBecomeSportsManufacturer() throws Exception {
        JSONObject fact=new JSONObject().put("key","manufacturer").put("value","League Properties, Inc.")
                .put("role","printed label").put("location","right vertical copyright line")
                .put("image",1).put("side","back").put("confidence",98);
        ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();
        ObservationExtractorV2.ingestFocused(new JSONObject().put("facts",new JSONArray().put(fact)),ledger,
                DomainProfileRouterV2.Profile.SPORTS_CARD,"focus");
        assertFalse(ledger.hasObserved("manufacturer"));assertTrue(ledger.hasObserved("rightsHolder"));
        assertEquals("OBJECT_LEGAL",ledger.strongest("rightsHolder",EvidenceAtom.EpistemicLevel.OBSERVED).semanticScope);
    }

    @Test public void remoteRetrievalUsesRareLocalLabelConstellation(){
        Models.LocalScan scan=new Models.LocalScan();scan.textByImage.add("PAIR\nSETTINGS\nTOP PICKS\nVOICE\nSOURCES\nTV EXIT\nHOME\nBACK\nNETFLIX");
        ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestLocal(scan,ledger);
        String prompt=CandidateRetrieverV2.prompt(DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL,ledger,new ArrayList<>());
        assertTrue(prompt.contains("controlLabelSet=PAIR,SETTINGS,TOP PICKS,VOICE,SOURCES,TV EXIT"));
    }

    @Test public void genericRemoteLayoutCannotBeatMissingRareLabels(){
        ImmutableEvidenceLedgerV2 ledger=remoteLedger();IdentityCandidateV2 generic=remoteCandidate("generic","Netflix and home", "circular navigation, numeric keypad", "");
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(generic)),ledger,DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL);
        assertFalse(ranked.get(0).disproofPassed);
    }

    @Test public void matchingRemoteConstellationStillPasses(){
        ImmutableEvidenceLedgerV2 ledger=remoteLedger();IdentityCandidateV2 exact=remoteCandidate("exact","PAIR SETTINGS TOP PICKS VOICE SOURCES TV EXIT", "circular navigation, numeric keypad", "HOME BACK NETFLIX");
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(exact)),ledger,DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL);
        assertTrue(ranked.get(0).disproofPassed);assertEquals("WINNER",ranked.get(0).status);
    }

    @Test public void loneWrongSportsNumberIsCandidateOnlyAndRequestsCloseup(){
        ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();
        observed(ledger,"brand","ExampleBox","rear logo");observed(ledger,"productLine","Metal Universe","front logo");
        observed(ledger,"athlete","Example Player","front nameplate");observed(ledger,"productReleaseYear","1997-98","rear set line");
        observed(ledger,"physicalCardNumber","18","upper-left decorative circle");
        IdentityCandidateV2 candidate=new IdentityCandidateV2("catalog-81",DomainProfileRouterV2.Profile.SPORTS_CARD,"WEB");
        candidate.retrieved=true;candidate.exactReference=true;candidate.sourceRecordId="row-81";candidate.sourcePageScope="CHECKLIST_ROW";candidate.sourceUrl="https://catalog.invalid/81";candidate.sourceAuthority="catalog";candidate.webSourceQuality=90;
        candidate.fields.put("manufacturer","ExampleBox");candidate.fields.put("productLine","Metal Universe");candidate.fields.put("athlete","Example Player");candidate.fields.put("productReleaseYear","1997-98");candidate.fields.put("catalogCardNumber","81");
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(candidate)),ledger,DomainProfileRouterV2.Profile.SPORTS_CARD);
        assertFalse(candidate.rejected);assertFalse(candidate.disproofPassed);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=2;
        FinalStateReducerV2.reduce(id,ledger,DomainProfileRouterV2.Profile.SPORTS_CARD,ranked,new ArrayList<>(),"");
        assertEquals("18",id.cardNumberCandidate);assertEquals("",id.physicalCardNumber);assertFalse(id.identityConfirmed);
        assertTrue(id.nextPhotoRequest.contains("physical_card_number_closeup"));
    }

    private ImmutableEvidenceLedgerV2 remoteLedger(){
        ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();
        observed(ledger,"printedLabel","PAIR SETTINGS TOP PICKS VOICE SOURCES TV EXIT HOME BACK NETFLIX","complete face OCR");
        observed(ledger,"shortcutButtons","PAIR SETTINGS TOP PICKS VOICE SOURCES TV EXIT","upper shortcut rows");
        observed(ledger,"controlLayout","circular navigation, numeric keypad","center topology");
        observed(ledger,"navigationLayout","HOME BACK NETFLIX","navigation row");return ledger;
    }
    private IdentityCandidateV2 remoteCandidate(String id,String shortcuts,String layout,String navigation){
        IdentityCandidateV2 c=new IdentityCandidateV2(id,DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL,"WEB");
        c.retrieved=true;c.exactReference=true;c.sourceRecordId=id;c.sourcePageScope="SINGLE_RECORD";c.sourceUrl="https://catalog.invalid/"+id;c.sourceAuthority="manufacturer";c.webSourceQuality=90;c.layoutMatch=90;
        c.fields.put("manufacturer","Example Electronics");
        c.fields.put("shortcutButtons",shortcuts);c.fields.put("controlLayout",layout);c.fields.put("navigationLayout",navigation);return c;
    }
}
