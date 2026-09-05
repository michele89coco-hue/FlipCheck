package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared fail-closed rules for subject inputs and independent physical proof. */
final class EvidenceProofPolicyV3 {
    private EvidenceProofPolicyV3() {}

    static boolean likelyExternalUiBlock(String raw) {
        String text=safe(raw);if(text.length()<120)return false;
        String lower=text.toLowerCase(Locale.ROOT);int lines=text.split("[\\r\\n]+").length;
        boolean webUi=lower.matches("(?s).*(?:https?://|www\\.|[a-z0-9-]+\\.(?:com|org|net|io|app)\\b).*");
        boolean appUi=lower.matches("(?s).*(?:follow-up|gpt-[0-9]|chatgpt|ha lavorato per|run e risultati).*");
        return lines>=6&&webUi&&appUi;
    }

    static List<Integer> subjectImageIndexes(Models.LocalScan scan,int count) {
        List<Integer> kept=new ArrayList<>();if(count<=0)return kept;
        for(int i=0;i<count;i++){
            String text=scan!=null&&i<scan.textByImage.size()?scan.textByImage.get(i):"";
            if(!likelyExternalUiBlock(text))kept.add(i);
        }
        // With no contrasting object view, do not silently discard the user's only input.
        if(kept.isEmpty())for(int i=0;i<count;i++)kept.add(i);
        return kept;
    }

    static Models.LocalScan retainImages(Models.LocalScan source,List<Integer> indexes) {
        if(source==null)return null;Models.LocalScan out=new Models.LocalScan();out.durationMs=source.durationMs;
        for(int newIndex=0;newIndex<indexes.size();newIndex++){
            int oldIndex=indexes.get(newIndex);out.textByImage.add(oldIndex>=0&&oldIndex<source.textByImage.size()?source.textByImage.get(oldIndex):"");
            for(Models.Identifier id:source.identifiers)if(id!=null&&id.imageIndex==oldIndex){
                Models.Identifier kept=new Models.Identifier(id.label,id.value,newIndex,id.origin);out.identifiers.add(kept);
                if("BARCODE".equalsIgnoreCase(id.label)&&!out.barcodes.contains(id.value))out.barcodes.add(id.value);
            }
        }
        return out;
    }

    static boolean legalOwnershipContext(String role,String location) {
        String context=(safe(role)+" "+safe(location)).toLowerCase(Locale.ROOT).replace('_',' ').replace('-',' ');
        return context.matches(".*(?:copyright|rights holder|licensor|licensed|legal notice|properties(?:,? inc)?).*" );
    }

    static boolean independentlyCorroborated(ImmutableEvidenceLedgerV2 ledger,String field,String value) {
        if(ledger==null||safe(value).isEmpty())return false;List<EvidenceAtom> matching=new ArrayList<>();
        for(EvidenceAtom atom:ledger.current(field))if(atom.epistemicLevel==EvidenceAtom.EpistemicLevel.OBSERVED
                &&atom.localized()&&atom.reliable()&&SemanticRelationV3.compatible(
                SemanticRelationV3.relate(field,atom.normalizedValue,value)))matching.add(atom);
        for(int i=0;i<matching.size();i++)for(int j=i+1;j<matching.size();j++){
            EvidenceAtom a=matching.get(i),b=matching.get(j);
            if(a.imageIndex!=b.imageIndex||a.modality!=b.modality&&!a.cropId.equals(b.cropId))return true;
        }
        return false;
    }

    static List<String> stableRemoteControlLabels(ImmutableEvidenceLedgerV2 ledger) {
        List<String> out=new ArrayList<>();if(ledger==null)return out;
        for(EvidenceAtom atom:ledger.current("printedLabel")){
            if(atom.epistemicLevel!=EvidenceAtom.EpistemicLevel.OBSERVED||"UI_OVERLAY".equals(atom.semanticScope))continue;
            String text=" "+safe(atom.rawValue).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim()+" ";
            addIfPresent(out,text,"PAIR"," PAIR ");addIfPresent(out,text,"SETTINGS"," SETTINGS ");
            addIfPresent(out,text,"TOP PICKS"," TOP PICKS ");addIfPresent(out,text,"VOICE"," VOICE "," VOLCE ");
            addIfPresent(out,text,"SOURCES"," SOURCES "," SAURCES ");addIfPresent(out,text,"TV EXIT"," TV EXIT ");
            addIfPresent(out,text,"SUBTITLE"," SUBTITLE ");addIfPresent(out,text,"TEXT"," TEXT ");
            addIfPresent(out,text,"OPTIONS"," OPTIONS "," OPIONS ");addIfPresent(out,text,"HOME"," HOME ");
            addIfPresent(out,text,"BACK"," BACK ");addIfPresent(out,text,"NETFLIX"," NETFLIX ");
        }
        return out;
    }

    private static void addIfPresent(List<String> out,String text,String label,String...forms){
        if(out.contains(label))return;for(String form:forms)if(text.contains(form)){out.add(label);return;}
    }
    private static String safe(String value){return value==null?"":value.trim();}
}
