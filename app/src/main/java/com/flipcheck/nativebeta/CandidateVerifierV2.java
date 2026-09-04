package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Evidence-derived tournament. Inference-only candidates cannot reach 100. */
final class CandidateVerifierV2 {
    private CandidateVerifierV2() {}
    static List<IdentityCandidateV2> verify(List<IdentityCandidateV2> candidates,ImmutableEvidenceLedgerV2 ledger,DomainProfileRouterV2.Profile profile){List<IdentityCandidateV2>out=new ArrayList<>();if(candidates==null)return out;
        for(IdentityCandidateV2 c:candidates){score(c,ledger,profile);out.add(c);}out.sort(Comparator.comparingInt((IdentityCandidateV2 x)->x.totalScore).reversed());return out;}

    private static void score(IdentityCandidateV2 c,ImmutableEvidenceLedgerV2 ledger,DomainProfileRouterV2.Profile profile){int returnedMatches=c.matchedEvidence.size();int identifiers=0,text=0,logos=0,config=0,conflicts=0,observedCompared=0;
        for(String field:c.fields.keySet()){String candidate=c.value(field);EvidenceAtom observed=ledger.strongest(photoField(field,profile),EvidenceAtom.EpistemicLevel.OBSERVED);if(observed==null&&field.equals("manufacturer"))observed=ledger.strongest("brand",EvidenceAtom.EpistemicLevel.OBSERVED);if(observed==null&&field.equals("manufacturer")&&profile==DomainProfileRouterV2.Profile.TCG_CARD)observed=ledger.strongest("game",EvidenceAtom.EpistemicLevel.OBSERVED);if(observed==null&&field.equals("catalogCardNumber"))observed=ledger.strongest(profile==DomainProfileRouterV2.Profile.TCG_CARD?"collectorNumber":"physicalCardNumber",EvidenceAtom.EpistemicLevel.OBSERVED);if(observed==null)continue;observedCompared++;
            if(TypedFieldNormalizerV2.equivalent(photoField(field,profile),observed.normalizedValue,candidate)){c.matchedEvidence.add(observed.id);if(identifier(field))identifiers+=100;else if(field.equals("manufacturer")){logos+=100;text+=100;}else if(field.equals("configuration")){config+=100;}else text+=100;}
            else {c.trueConflicts.add(field+":"+observed.normalizedValue+"!="+candidate+"@"+observed.id);if(coreField(field,profile))conflicts+=100;}}
        c.observedIdentifierMatch=average(identifiers,countMatches(c,"collectorNumber","physicalCardNumber","catalogCardNumber","model","barcode"));
        c.observedTextMatch=Math.max(average(text,Math.max(1,countNonIdentifierMatches(c))),Math.min(100,returnedMatches*25));c.logoMatch=logos>0?100:0;c.configurationMatch=config>0?100:0;
        c.catalogMatch=c.retrieved&&c.exactReference?Math.max(70,c.webSourceQuality):c.retrieved?Math.min(75,c.webSourceQuality):0;c.contradictionPenalty=Math.min(100,conflicts);c.inferenceOnlyPenalty=c.retrieved?0:Math.max(c.inferenceOnlyPenalty,25);
        int components=c.observedIdentifierMatch*25+c.observedTextMatch*20+c.logoMatch*10+c.layoutMatch*15+c.catalogMatch*15+c.webSourceQuality*10+c.configurationMatch*5;
        c.totalScore=Math.max(0,Math.min(100,components/100-c.contradictionPenalty-c.inferenceOnlyPenalty));
        if(!c.retrieved)c.totalScore=Math.min(c.totalScore,74);if(observedCompared==0&&c.retrieved)c.totalScore=Math.min(c.totalScore,69);
        if(conflicts>0){c.rejected=true;c.rejectionReason="strong_observed_field_conflict";c.totalScore=0;}
        if(c.totalScore==100&&(!c.trueConflicts.isEmpty()||!c.unknownFields.isEmpty()&&!c.exactReference))c.totalScore=99;
    }
    private static String photoField(String field,DomainProfileRouterV2.Profile p){if(field.equals("catalogCardNumber"))return p==DomainProfileRouterV2.Profile.TCG_CARD?"collectorNumber":"physicalCardNumber";return field;}
    private static boolean identifier(String f){return f.endsWith("Number")||f.endsWith("Code")||f.equals("model")||f.equals("barcode");}
    private static boolean coreField(String f,DomainProfileRouterV2.Profile p){if(f.equals("manufacturer")||f.equals("productLine")||f.equals("setName")||f.equals("productReleaseYear"))return true;if(p==DomainProfileRouterV2.Profile.TCG_CARD)return f.equals("cardName")||f.equals("catalogCardNumber");if(p==DomainProfileRouterV2.Profile.SPORTS_CARD)return f.equals("athlete")||f.equals("catalogCardNumber");return f.equals("model");}
    private static int countMatches(IdentityCandidateV2 c,String...fields){int n=0;for(String f:fields)if(c.fields.containsKey(f)&&!c.value(f).isEmpty())n++;return Math.max(1,n);}
    private static int countNonIdentifierMatches(IdentityCandidateV2 c){int n=0;for(String f:c.fields.keySet())if(!identifier(f))n++;return n;}
    private static int average(int total,int count){return count<=0?0:Math.min(100,total/count);}
}
