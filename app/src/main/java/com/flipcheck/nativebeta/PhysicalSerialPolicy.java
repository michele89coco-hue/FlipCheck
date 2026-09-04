package com.flipcheck.nativebeta;

import java.util.Locale;
import java.util.regex.Pattern;

/** Binds x/y as specimen serial only from direct, localized serial evidence. */
final class PhysicalSerialPolicy {
    private static final Pattern FRACTION=Pattern.compile("^[0-9]{1,6}/[0-9]{1,6}$");
    private PhysicalSerialPolicy() {}
    static void normalize(Models.Identification id){if(id==null)return;NormalizedPhotoIdentity n=PhotographicFactNormalizer.require(id);
        n.physicalSerial="";id.physicalSerial="";id.physicalSerialOrigin="";
        NormalizedPhotoIdentity.Fact best=null;for(NormalizedPhotoIdentity.Fact f:n.facts(CanonicalFieldKey.PHYSICAL_SERIAL_CANDIDATE)){
            String value=clean(f.value).replace(" ","");String role=canon(f.semanticRole);String location=first(f.location,n.best(CanonicalFieldKey.SERIAL_LOCATION));
            String binding=canon(n.best(CanonicalFieldKey.SERIAL_BINDING));boolean semantic=role.contains("SERIAL")||role.contains("PRINT RUN")||role.contains("SPECIMEN");
            boolean surface=binding.isEmpty()||binding.contains("CARD SURFACE")||binding.contains("PHYSICAL")||binding.contains("FOREGROUND OBJECT");
            if(f.direct()&&f.confidence>=80&&!location.isEmpty()&&semantic&&surface&&FRACTION.matcher(value).matches()
                    &&(best==null||f.confidence>best.confidence))best=f;
            else reject(n,"serial_not_direct_localized_or_semantic:"+f.originalKey+"="+f.value);
        }
        if(best!=null){String value=clean(best.value).replace(" ","");n.physicalSerial=value;id.physicalSerial=value;
            id.physicalSerialOrigin="photo:direct:"+first(best.location,n.best(CanonicalFieldKey.SERIAL_LOCATION));}
        PhotographicFactNormalizer.syncDebug(id,n);
    }
    private static void reject(NormalizedPhotoIdentity n,String x){if(!n.rejectedFacts.contains(x))n.rejectedFacts.add(x);}
    private static String first(String...xs){for(String x:xs)if(!clean(x).isEmpty())return clean(x);return "";}
    private static String canon(String x){return clean(x).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}
    private static String clean(String x){return x==null?"":x.trim();}
}
