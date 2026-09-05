package com.flipcheck.nativebeta;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class V150TargetedTraceTest {
    private EvidenceAtom observed(ImmutableEvidenceLedgerV2 l,String field,String value,String region){
        return l.append(field,value,EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.FOCUSED_VISION,
                "test",0,"front",region,"crop",field,98,95,"test","");
    }
    private IdentityCandidateV2 sealed(String id,String scope,boolean exact,String format,String configuration,String...unknown) {
        IdentityCandidateV2 c=new IdentityCandidateV2(id,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,"WEB");
        c.retrieved=true;c.sourceUrl="https://catalog.invalid/guide";c.sourceRecordId=id+"-record";c.sourcePageScope=scope;
        c.identityLevel="VARIANT_OR_FORMAT";c.webSourceQuality=94;c.exactReference=exact;c.reportedDisproofPassed=true;
        c.fields.put("manufacturer","Example");c.fields.put("productLine","Chrome");c.fields.put("setName","Chrome Update");
        c.fields.put("subSeries","Update");c.fields.put("productReleaseYear","2025-26");c.fields.put("sport","basketball");
        c.fields.put("commercialFormat",format);c.fields.put("configuration",configuration);
        c.unknownFields.addAll(Arrays.asList(unknown));return c;
    }
    private ImmutableEvidenceLedgerV2 sealedLedger(){
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
        observed(l,"manufacturer","Example","front logo");observed(l,"productLine","Chrome","front title");
        observed(l,"subSeries","Update","front subtitle");observed(l,"productReleaseYear","2025-26","side season");
        observed(l,"configuration","1 AUTOGRAPH* IN EVERY HOBBY BOX*","bottom banner");observed(l,"sport","basketball","front imagery");
        return l;
    }
    @Test public void formatAdjectiveDoesNotHideMatchingAutographQuantity(){
        assertTrue(SemanticRelationV3.compatible(SemanticRelationV3.relate("configuration",
                "1 AUTOGRAPH* IN EVERY HOBBY BOX*","4 cards per pack; 20 packs per box; 1 autograph guaranteed per box")));
        assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE,SemanticRelationV3.relate("configuration",
                "1 autograph in every hobby box","4 cards per pack; 20 packs per box; 2 autographs per box"));
    }
    @Test public void completeCompatibleFormatSectionCanBeIsolatedInsideGuide() {
        ImmutableEvidenceLedgerV2 l=sealedLedger();List<IdentityCandidateV2> candidates=new ArrayList<>();
        candidates.add(sealed("h","MULTI_RECORD_PAGE",true,"Hobby Box","20 packs per box; 4 cards per pack; 1 autograph per box"));
        candidates.add(sealed("m","CHECKLIST_ROW",false,"Mega Box","7 packs per box; 6 cards per pack; autograph guarantee not established"));
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(candidates,l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT);
        assertEquals("Hobby Box",ranked.get(0).value("commercialFormat"));assertFalse(ranked.get(0).rejected);assertTrue(ranked.get(0).disproofPassed);
    }
    @Test public void unresolvedMultiFormatGuideStillCannotClaimExactFormat() {
        ImmutableEvidenceLedgerV2 l=sealedLedger();IdentityCandidateV2 row=sealed("x","MULTI_RECORD_PAGE",true,"Hobby Box",
                "20 packs per box; 4 cards per pack; 1 autograph per box","format not isolated");
        IdentityCandidateV2 candidate=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(row)),l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT).get(0);
        assertTrue(candidate.rejected);assertEquals("exact_reference_without_isolated_record",candidate.rejectionReason);
    }
    @Test public void sportsCorporateSuffixUsesConciseEquivalentCatalogBrand(){
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();observed(l,"manufacturer","ExampleBox International","copyright strip");
        observed(l,"productLine","Metal Collection","front logo");observed(l,"athlete","Sample Player","nameplate");
        observed(l,"physicalCardNumber","81","number circle");
        IdentityCandidateV2 c=new IdentityCandidateV2("c",DomainProfileRouterV2.Profile.SPORTS_CARD,"WEB");c.retrieved=true;c.sourceUrl="https://catalog.invalid/card";c.sourceRecordId="card-81";c.sourcePageScope="SINGLE_RECORD";c.exactReference=true;c.webSourceQuality=94;c.layoutMatch=90;
        c.fields.put("manufacturer","ExampleBox");c.fields.put("productLine","Metal Collection");c.fields.put("athlete","Sample Player");c.fields.put("catalogCardNumber","81");c.fields.put("productReleaseYear","1997-98");
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(c)),l,DomainProfileRouterV2.Profile.SPORTS_CARD);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.SPORTS_CARD,ranked,ConflictResolverV2.resolve(l,DomainProfileRouterV2.Profile.SPORTS_CARD),"");
        assertEquals("ExampleBox",id.brand);assertEquals("ExampleBox International",id.observedBrand);assertEquals("ExampleBox",id.catalogBrand);
    }
    @Test public void sportsCompositeCatalogPublisherCannotReplacePhotographedBrand(){
        ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();observed(l,"manufacturer","ExampleBox","copyright strip");
        observed(l,"productLine","Metal Collection","front logo");observed(l,"athlete","Sample Player","nameplate");
        observed(l,"physicalCardNumber","81","number circle");
        IdentityCandidateV2 c=new IdentityCandidateV2("c",DomainProfileRouterV2.Profile.SPORTS_CARD,"WEB");c.retrieved=true;c.sourceUrl="https://catalog.invalid/card";c.sourceRecordId="card-81";c.sourcePageScope="SINGLE_RECORD";c.exactReference=true;c.webSourceQuality=94;
        c.fields.put("manufacturer","Parent Group / ExampleBox");c.fields.put("productLine","Metal Collection");c.fields.put("athlete","Sample Player");c.fields.put("catalogCardNumber","81");c.fields.put("productReleaseYear","1997-98");
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(c)),l,DomainProfileRouterV2.Profile.SPORTS_CARD);
        assertFalse(ranked.get(0).rejected);assertTrue(ranked.get(0).disproofPassed);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.SPORTS_CARD,ranked,new ArrayList<>(),"");
        assertEquals("ExampleBox",id.brand);assertEquals("Parent Group / ExampleBox",id.catalogBrand);
    }
    @Test public void sealedFormatMisplacedInEditionIsCanonicalizedWithoutChangingCardEditions(){
        assertEquals("Hobby Box",CandidateRetrieverV2.sealedEditionContainer("Hobby",""));
        assertEquals("Jumbo Box",CandidateRetrieverV2.sealedEditionContainer("Jumbo","Box"));
        assertEquals("",CandidateRetrieverV2.sealedEditionContainer("First Edition",""));
    }
    @Test public void explicitSealedConfigurationContradictionCannotWin(){
        ImmutableEvidenceLedgerV2 l=sealedLedger();IdentityCandidateV2 c=sealed("j","SINGLE_RECORD",true,"Jumbo Box","11 cards per pack; packs per box not shown");
        c.reportedContradictedFields.add("configuration=observed one autograph per box is not established by this record");
        IdentityCandidateV2 ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(c)),l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT).get(0);
        assertTrue(ranked.rejected);assertEquals("reported_observed_field_conflict",ranked.rejectionReason);
    }
    @Test public void exactHighLayoutFormatMarkCannotReplaceCompletePackCounts(){
        ImmutableEvidenceLedgerV2 l=sealedLedger();observed(l,"visualSymbol","H inside a circular sunburst badge","upper badge");
        IdentityCandidateV2 c=sealed("h","SINGLE_RECORD",true,"Hobby Box","1 autograph per box");c.layoutMatch=88;c.fields.put("setSymbol","H in circular sunburst badge");
        c.unknownFields.add("whether the H badge is format-specific");
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(c)),l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,ranked,new ArrayList<>(),"");
        assertEquals("",id.sealedFormat);
    }
    @Test public void genericLayoutWithoutMatchingFormatMarkRemainsPending(){
        ImmutableEvidenceLedgerV2 l=sealedLedger();IdentityCandidateV2 c=sealed("h","SINGLE_RECORD",true,"Hobby Box","1 autograph per box");c.layoutMatch=92;c.fields.put("setSymbol","H badge");
        c.unknownFields.add("whether the H badge is format-specific");
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(c)),l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,ranked,new ArrayList<>(),"");
        assertEquals("",id.sealedFormat);
    }
    @Test public void completeConfigurationSurvivesAFormatAppearanceQuestion(){
        ImmutableEvidenceLedgerV2 l=sealedLedger();IdentityCandidateV2 c=sealed("h","CHECKLIST_ROW",true,"Hobby Box","4 cards per pack; 20 packs per box; 1 autograph per box");
        c.unknownFields.add("Whether the pictured physical box is Hobby rather than another format");
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(c)),l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,ranked,new ArrayList<>(),"");
        assertEquals("Hobby Box",id.sealedFormat);
    }
    @Test public void hobbySectionWinsWhenJumboAdmitsObservedConfigurationConflict(){
        ImmutableEvidenceLedgerV2 l=sealedLedger();IdentityCandidateV2 hobby=sealed("h","MULTI_RECORD_PAGE",false,CandidateRetrieverV2.sealedEditionContainer("Hobby",""),"1 autograph per box; pack and card quantities not isolated in retrieved record");
        hobby.unknownFields.add("exact packaging badge meaning");IdentityCandidateV2 jumbo=sealed("j","SINGLE_RECORD",true,"Jumbo Box","11 cards per pack; packs per box not shown");
        jumbo.reportedContradictedFields.add("configuration=observed one autograph per box is not established by this record");
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(new ArrayList<>(Arrays.asList(jumbo,hobby)),l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT);
        Models.Identification id=new Models.Identification();id.uploadedImageCount=1;
        FinalStateReducerV2.reduce(id,l,DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT,ranked,new ArrayList<>(),"");
        assertEquals("h",id.candidateWinnerId);assertEquals("Hobby Box",id.sealedFormat);
    }
}
