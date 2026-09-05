package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;

/** Emits only same-field, post-normalization conflicts backed by two strong atoms. */
final class ConflictResolverV2 {
    static final class Conflict {final String field,valueA,valueB;final List<String> evidenceIdsA,evidenceIdsB;final String severity,affectedIdentityLevel,attemptedResolution;
        Conflict(String f,String a,List<String>ea,String b,List<String>eb,String s,String level,String attempt){field=f;valueA=a;evidenceIdsA=ea;valueB=b;evidenceIdsB=eb;severity=s;affectedIdentityLevel=level;attemptedResolution=attempt;}}
    private ConflictResolverV2() {}
    static List<Conflict> resolve(ImmutableEvidenceLedgerV2 ledger,DomainProfileRouterV2.Profile profile){List<Conflict>out=new ArrayList<>();String[]fields={"manufacturer","productLine","setName","cardName","athlete","collectorNumber","physicalCardNumber","productReleaseYear","model","edition"};
        for(String field:fields){if(!identityField(field,profile))continue;List<EvidenceAtom>facts=ledger.current(field);for(int i=0;i<facts.size();i++)for(int j=i+1;j<facts.size();j++){EvidenceAtom a=facts.get(i),b=facts.get(j);SemanticRelationV3.Relation relation=SemanticRelationV3.relate(field,a.normalizedValue,b.normalizedValue);if(!eligible(a)||!eligible(b)||a.epistemicLevel==EvidenceAtom.EpistemicLevel.INFERRED||b.epistemicLevel==EvidenceAtom.EpistemicLevel.INFERRED||TypedFieldNormalizerV2.ambiguous(a.normalizedValue)||TypedFieldNormalizerV2.ambiguous(b.normalizedValue)||SemanticRelationV3.compatible(relation)||relation==SemanticRelationV3.Relation.AMBIGUOUS)continue;
            // Catalog candidates rejected against an observed fact are candidate-level disproof, not a global conflict.
            if(a.epistemicLevel!=EvidenceAtom.EpistemicLevel.OBSERVED||b.epistemicLevel!=EvidenceAtom.EpistemicLevel.OBSERVED)continue;
            List<String>ea=new ArrayList<>(),eb=new ArrayList<>();ea.add(a.id);eb.add(b.id);out.add(new Conflict(field,a.normalizedValue,ea,b.normalizedValue,eb,"CRITICAL",level(field,profile),"typed_normalization_and_source_precedence"));}}
        return dedupe(out);}
    private static boolean identityField(String field,DomainProfileRouterV2.Profile profile){
        if(field.equals("athlete")||field.equals("physicalCardNumber"))return profile==DomainProfileRouterV2.Profile.SPORTS_CARD;
        if(field.equals("cardName")||field.equals("collectorNumber"))return profile==DomainProfileRouterV2.Profile.TCG_CARD;
        return true;
    }
    private static boolean eligible(EvidenceAtom a){return a!=null&&a.reliable()&&(a.epistemicLevel!=EvidenceAtom.EpistemicLevel.OBSERVED||a.localized());}
    private static String level(String field,DomainProfileRouterV2.Profile p){if(field.equals("edition"))return "EXACT_PHYSICAL_IDENTITY";if(field.equals("model"))return "CATALOG_IDENTITY";return "CORE_IDENTITY";}
    private static List<Conflict> dedupe(List<Conflict>in){List<Conflict>out=new ArrayList<>();for(Conflict c:in){boolean found=false;for(Conflict x:out)if(x.field.equals(c.field)&&((x.valueA.equals(c.valueA)&&x.valueB.equals(c.valueB))||(x.valueA.equals(c.valueB)&&x.valueB.equals(c.valueA)))){found=true;break;}if(!found)out.add(c);}return out;}
}
