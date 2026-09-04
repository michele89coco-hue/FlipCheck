package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;

/** Keeps hypotheses plural and capped below confirmation until independently retrieved. */
final class HypothesisGeneratorV2 {
    private HypothesisGeneratorV2() {}
    static List<IdentityCandidateV2> merge(List<IdentityCandidateV2> primary,List<IdentityCandidateV2> focused){
        List<IdentityCandidateV2>out=new ArrayList<>();add(out,primary);add(out,focused);
        IdentityCandidateV2 unknown=new IdentityCandidateV2("HYPOTHESIS-UNKNOWN",out.isEmpty()?DomainProfileRouterV2.Profile.GENERIC_OBJECT:out.get(0).domain,"UNKNOWN");unknown.inferenceOnlyPenalty=0;unknown.totalScore=0;out.add(unknown);return out;}
    private static void add(List<IdentityCandidateV2>out,List<IdentityCandidateV2>values){if(values==null)return;for(IdentityCandidateV2 x:values){boolean duplicate=false;for(IdentityCandidateV2 old:out)if(TypedFieldNormalizerV2.equivalent("productLine",old.display(),x.display())){duplicate=true;break;}if(!duplicate)out.add(x);}}
}
