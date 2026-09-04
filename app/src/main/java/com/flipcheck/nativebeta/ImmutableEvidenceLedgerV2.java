package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Append-only ledger. Normalization appends a linked atom and never edits its parent. */
final class ImmutableEvidenceLedgerV2 {
    private final List<EvidenceAtom> atoms = new ArrayList<>();
    private int sequence;

    EvidenceAtom append(String field, String rawValue, EvidenceAtom.EpistemicLevel requested,
                        EvidenceAtom.Modality modality, String source, int imageIndex,
                        String side, String region, String cropId, String scope,
                        int confidence, int quality, String stage, String sourceUrl) {
        String raw=safe(rawValue), canonicalField=TypedFieldNormalizerV2.canonicalField(field,scope);
        if(raw.isEmpty()||canonicalField.isEmpty())return null;
        EvidenceAtom.EpistemicLevel level=requested;
        boolean location=imageIndex>=0&&(specificRegion(region)||!safe(cropId).isEmpty());
        if(requested==EvidenceAtom.EpistemicLevel.OBSERVED&&!location){
            level=EvidenceAtom.EpistemicLevel.INFERRED;
        }
        String id="EV2-"+String.format(Locale.ROOT,"%05d",++sequence);
        EvidenceAtom atom=new EvidenceAtom(id,canonicalField,raw,raw,level,modality,source,
                imageIndex,side,region,cropId,TypedFieldNormalizerV2.semanticScope(canonicalField,scope),
                confidence,quality,"UniversalIdentityEngineV2/1",stage,sourceUrl,"");
        if(!duplicate(atom))atoms.add(atom);
        return atom;
    }

    EvidenceAtom appendNormalization(EvidenceAtom parent,String normalized,String field,String scope){
        if(parent==null||safe(normalized).isEmpty())return null;
        String canonicalField=TypedFieldNormalizerV2.canonicalField(field,scope);
        for(EvidenceAtom old:atoms)if(old.parentEvidenceId.equals(parent.id)
                &&old.field.equals(canonicalField)&&old.normalizedValue.equalsIgnoreCase(normalized))return old;
        EvidenceAtom atom=new EvidenceAtom("EV2-"+String.format(Locale.ROOT,"%05d",++sequence),
                canonicalField,parent.rawValue,normalized,parent.epistemicLevel,parent.modality,parent.source,
                parent.imageIndex,parent.side,parent.boundingBox,parent.cropId,
                TypedFieldNormalizerV2.semanticScope(canonicalField,scope),parent.confidence,parent.qualityScore,
                "TypedFieldNormalizerV2/1","typed_normalization",parent.sourceUrl,parent.id);
        atoms.add(atom);return atom;
    }

    List<EvidenceAtom> all(){return Collections.unmodifiableList(atoms);}
    List<EvidenceAtom> current(String field){
        String key=TypedFieldNormalizerV2.canonicalField(field,"");List<EvidenceAtom> out=new ArrayList<>();
        Set<String> parents=new LinkedHashSet<>();
        for(EvidenceAtom a:atoms)if(a.field.equals(key)&&!a.parentEvidenceId.isEmpty())parents.add(a.parentEvidenceId);
        for(EvidenceAtom a:atoms)if(a.field.equals(key)&&!parents.contains(a.id))out.add(a);
        return out;
    }
    EvidenceAtom strongest(String field,EvidenceAtom.EpistemicLevel...allowed){
        EvidenceAtom best=null;for(EvidenceAtom a:current(field)){
            if(!allowed(a,allowed))continue;
            if(best==null||strength(a)>strength(best))best=a;
        }return best;
    }
    List<EvidenceAtom> byLevel(EvidenceAtom.EpistemicLevel level){List<EvidenceAtom>out=new ArrayList<>();for(EvidenceAtom a:atoms)if(a.epistemicLevel==level)out.add(a);return out;}
    boolean hasObserved(String field){EvidenceAtom a=strongest(field,EvidenceAtom.EpistemicLevel.OBSERVED);return a!=null&&a.localized();}

    private boolean duplicate(EvidenceAtom incoming){for(EvidenceAtom a:atoms)if(a.field.equals(incoming.field)
            &&a.rawValue.equalsIgnoreCase(incoming.rawValue)&&a.epistemicLevel==incoming.epistemicLevel
            &&a.modality==incoming.modality&&a.imageIndex==incoming.imageIndex
            &&a.boundingBox.equalsIgnoreCase(incoming.boundingBox))return true;return false;}
    private static boolean allowed(EvidenceAtom a,EvidenceAtom.EpistemicLevel[] levels){if(levels==null||levels.length==0)return true;for(EvidenceAtom.EpistemicLevel level:levels)if(a.epistemicLevel==level)return true;return false;}
    private static int strength(EvidenceAtom a){int epistemic=a.epistemicLevel==EvidenceAtom.EpistemicLevel.OBSERVED?30:a.epistemicLevel==EvidenceAtom.EpistemicLevel.RETRIEVED?20:0;return epistemic+a.confidence+a.qualityScore/2+(a.localized()?10:0);}
    private static boolean specificRegion(String raw){String x=safe(raw).toLowerCase(Locale.ROOT).replace('_',' ');return x.length()>=5&&!x.matches("(?:front|back|rear|whole|full|image|photo|unknown|unspecified|entire image|full image|whole image|object|foreground)");}
    private static String safe(String value){return value==null?"":value.trim();}
}
