package com.flipcheck.nativebeta;

import java.util.Locale;

/** Keeps generic finish separate and binds a parallel only from direct physical proof. */
final class PhysicalVariantPolicy {
    private PhysicalVariantPolicy() {}
    static void normalize(Models.Identification id){if(id==null)return;NormalizedPhotoIdentity n=PhotographicFactNormalizer.require(id);
        n.finish=meaningful(first(n.best(CanonicalFieldKey.FINISH),n.best(CanonicalFieldKey.REVERSE_HOLO_STATUS),n.best(CanonicalFieldKey.HOLO_STATUS)));n.parallelColor=meaningful(n.best(CanonicalFieldKey.PARALLEL_COLOR));
        n.physicalParallel="";n.rareVariantPhysicalProof=!n.physicalSerial.isEmpty();
        NormalizedPhotoIdentity.Fact best=null;for(NormalizedPhotoIdentity.Fact f:n.facts(CanonicalFieldKey.PHYSICAL_PARALLEL_CANDIDATE)){
            String role=canon(f.semanticRole),type=canon(f.evidenceType);boolean semantic=role.contains("PARALLEL")||role.contains("VARIANT");
            boolean marker=type.matches(".*(?:PRINT|TEXT|LABEL|STAMP|MARKER|PATTERN|LAYOUT).*");
            if(f.direct()&&f.confidence>=85&&!clean(f.location).isEmpty()&&semantic&&marker&&(best==null||f.confidence>best.confidence))best=f;
            else reject(n,"parallel_not_direct_localized_marker:"+f.originalKey+"="+f.value);
        }
        if(best!=null){n.physicalParallel=meaningful(best.value);n.rareVariantPhysicalProof=!n.physicalParallel.isEmpty();}
        n.physicallyGraded=truth(n.best(CanonicalFieldKey.GRADED));
        id.finish=n.finish;id.parallelColor=n.rareVariantPhysicalProof?n.parallelColor:"";id.physicalParallel=n.physicalParallel;
        id.rareVariantPhysicalProof=n.rareVariantPhysicalProof;id.physicallyGraded=n.physicallyGraded;
        PhotographicFactNormalizer.syncDebug(id,n);
    }
    static boolean canUseVariant(Models.Identification id,String variant){return id!=null&&PhotographicFactNormalizer.require(id).rareVariantPhysicalProof&&!clean(variant).isEmpty();}
    private static void reject(NormalizedPhotoIdentity n,String x){if(!n.rejectedFacts.contains(x))n.rejectedFacts.add(x);}
    private static String meaningful(String x){String v=clean(x).toLowerCase(Locale.ROOT).replace('_',' ');return v.isEmpty()||v.equals("present")||v.equals("visible")||v.equals("true")||v.equals("unknown")||v.equals("unresolved")?"":clean(x);}
    private static boolean truth(String x){String v=clean(x).toLowerCase(Locale.ROOT);return v.equals("true")||v.equals("yes")||v.equals("present")||v.equals("visible");}
    private static String first(String...xs){for(String x:xs)if(!clean(x).isEmpty())return clean(x);return "";}
    private static String canon(String x){return clean(x).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}
    private static String clean(String x){return x==null?"":x.trim();}
}
