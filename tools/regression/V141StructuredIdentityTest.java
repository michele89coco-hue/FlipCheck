package com.flipcheck.nativebeta;
import org.junit.Test;
import static org.junit.Assert.*;
public class V141StructuredIdentityTest {
 private ImmutableEvidenceLedgerV2 split(int totalImage){
  ImmutableEvidenceLedgerV2 l=new ImmutableEvidenceLedgerV2();
  l.append("collectorNumber","14",EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.FOCUSED_VISION,"focus",0,"front","bottom right","crop","collector number",95,95,"focus","");
  l.append("printedTotal","70",EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.FOCUSED_VISION,"focus",totalImage,"front","bottom right","crop","set total",95,95,"focus","");
  TypedFieldNormalizerV2.normalize(l);return l;
 }
 @Test public void colocatedCollectorPartsCompose(){assertEquals("14/70",split(0).strongest("collectorNumber").normalizedValue);}
 @Test public void otherImageTotalCannotCompleteCollector(){assertEquals("14",split(1).strongest("collectorNumber").normalizedValue);}
 @Test public void corporateSuffixDoesNotCreateDifferentManufacturer(){
  assertTrue(SemanticRelationV3.compatible(SemanticRelationV3.relate("manufacturer","Example International","Example")));
  assertEquals(SemanticRelationV3.Relation.INCOMPATIBLE,SemanticRelationV3.relate("manufacturer","Example International","Other"));
 }
 private IdentityCandidateV2 candidate(String season,int score){
  IdentityCandidateV2 c=new IdentityCandidateV2(season,DomainProfileRouterV2.Profile.SPORTS_CARD,"catalog");
  c.fields.put("manufacturer","Example");c.fields.put("productLine","Galaxy");c.fields.put("athlete","Player");c.fields.put("catalogCardNumber","12");c.fields.put("productReleaseYear",season);c.fields.put("cardRole","BASE");
  c.retrieved=true;c.disproofPassed=true;c.webSourceQuality=90;c.totalScore=score;return c;
 }
 @Test public void seasonRefinementRequiresSameIsolatedIdentity(){
  IdentityCandidateV2 year=candidate("2021",90),season=candidate("2021-22",89);
  assertTrue(CandidateVerifierV2.seasonRefines(season,year));
  season.fields.put("catalogCardNumber","99");assertFalse(CandidateVerifierV2.seasonRefines(season,year));
 }
}
